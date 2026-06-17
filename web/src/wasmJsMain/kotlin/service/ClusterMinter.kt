/*
 * ONI Seed Browser
 * Copyright (C) 2026 Stefan Oltmann
 * https://stefan-oltmann.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package service

import de.stefan_oltmann.oni.model.ClusterType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.measureTimedValue

/*
 * Returns the current time in milliseconds using the JS Date API.
 * Required because kotlin.js Date.now() is not available in WASM.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun currentTimeMs(): Double = js("Date.now()")

/*
 * Represents a single unit of work: a cluster type and seed pair.
 * The producer feeds these into the channel; workers consume them.
 */
private data class WorkItem(
    val clusterType: ClusterType,
    val seed: Long
)

enum class WorkerPhase { IDLE, GENERATING, UPLOADING }

data class WorkerStatus(
    val index: Int,
    val phase: WorkerPhase = WorkerPhase.IDLE,
    val coordinate: String = ""
)

data class MinterState(
    val isRunning: Boolean = false,
    val currentSeed: Long = 0,
    val workers: List<WorkerStatus> = emptyList(),
    val totalUploaded: Long = 0,
    val totalErrors: Long = 0,
    val elapsedMs: Long = 0,
    val recentLogs: List<LogEntry> = emptyList()
)

data class LogEntry(
    val level: Level,
    val seed: Long,
    val coordinate: String,
    val message: String
) {
    enum class Level { INFO, WARN, ERROR }
}

/*
 * Orchestrates cluster generation and upload.
 *
 * Uses a Channel-based work queue where:
 *  - A producer coroutine feeds (ClusterType, seed) pairs into the channel
 *  - N worker coroutines consume from the channel independently
 *  - Each worker uses its own Web Worker from the pool for true CPU parallelism
 *  - Workers do not wait for other workers or for seed boundaries
 *
 * The WorldgenWorkerPool must be initialized before calling run().
 */
class ClusterMinter(
    private val webClient: WebClient,
    private val serverUrl: String,
    private val json: Json
) {

    private val maxLogEntries = 100

    /*
     * Run the minter with the given parameters.
     *
     * @param startSeed The first seed to process (increments continuously)
     * @param cpuCores Number of CPU cores to use for generation (1:1 mapping to Web Workers)
     * @param clusterFilter Optional prefix filter for cluster types
     * @param onStateUpdate Callback for state updates (called on recomposition)
     */
    suspend fun run(
        startSeed: Long,
        cpuCores: Int,
        clusterFilter: String? = null,
        onStateUpdate: (MinterState) -> Unit
    ) {
        val logs = mutableListOf<LogEntry>()
        val workers = MutableList(cpuCores) { WorkerStatus(index = it) }

        var uploaded = 0L
        var errors = 0L
        var seedCursor = startSeed
        val startTimeMs = currentTimeMs()

        /*
         * Filter cluster types if a filter is provided.
         * Otherwise, process all cluster types.
         * The filter is an exact match on the cluster type prefix.
         */
        val activeClusterTypes = if (clusterFilter.isNullOrBlank()) {
            ClusterType.entries
        } else {
            ClusterType.entries.filter {
                it.prefix.equals(clusterFilter, ignoreCase = true)
            }
        }

        if (activeClusterTypes.isEmpty()) {
            logs.add(LogEntry(LogEntry.Level.ERROR, startSeed, "", "No cluster types match filter: $clusterFilter"))
            onStateUpdate(snapshot(workers, uploaded, errors, startTimeMs, seedCursor, logs))
            return
        }

        fun snapshot(): MinterState = snapshot(workers, uploaded, errors, startTimeMs, seedCursor, logs)

        fun addLog(level: LogEntry.Level, coordinate: String, message: String) {
            logs.add(LogEntry(level, seedCursor, coordinate, message))
            if (logs.size > maxLogEntries) logs.removeFirst()
        }

        /* Initialize the Web Worker pool for true CPU parallelism */
        WorldgenWorkerPool.initialize(cpuCores)
        addLog(LogEntry.Level.INFO, "", "Initialized $cpuCores Web Workers")
        onStateUpdate(snapshot())

        /*
         * Create a buffered channel for work items.
         * Buffer size is large enough to keep workers busy without blocking the producer.
         */
        val channel = Channel<WorkItem>(capacity = activeClusterTypes.size * 2)

        try {
            coroutineScope {

                /*
                 * Producer coroutine: continuously feeds work items into the channel.
                 * Feeds all cluster types for the current seed, then increments the seed.
                 * Never stops — runs until cancelled.
                 */
                launch(Dispatchers.Default) {
                    var currentSeed = startSeed
                    while (true) {
                        for (clusterType in activeClusterTypes) {
                            channel.send(WorkItem(clusterType, currentSeed))
                        }
                        currentSeed++
                        /* Update seedCursor for display — snapshot will pick it up */
                        seedCursor = currentSeed
                    }
                }

                /*
                 * Worker coroutines: consume work items from the channel.
                 * Each worker runs independently — no waiting for other workers.
                 * Each worker uses its own Web Worker from the pool.
                 */
                for (i in 0 until cpuCores) {
                    launch(Dispatchers.Default) {
                        for (workItem in channel) {
                            val coordinate = "${workItem.clusterType.prefix}-${workItem.seed}-0-0-0"

                            workers[i] = WorkerStatus(
                                index = i,
                                phase = WorkerPhase.GENERATING,
                                coordinate = coordinate
                            )
                            onStateUpdate(snapshot())

                            try {
                                /* Generate the cluster via WASM worldgen using assigned worker */
                                val (cluster, genDuration) = measureTimedValue {
                                    ClusterGenerator.generateCluster(coordinate, workerIndex = i)
                                }

                                workers[i] = WorkerStatus(
                                    index = i,
                                    phase = WorkerPhase.UPLOADING,
                                    coordinate = coordinate
                                )
                                onStateUpdate(snapshot())

                                val clusterJson = json.encodeToString(cluster)

                                /* Upload with proper status code checking */
                                when (val result = webClient.uploadCluster(serverUrl, clusterJson)) {
                                    is UploadResult.Success -> {
                                        uploaded++
                                        addLog(LogEntry.Level.INFO, coordinate, "Uploaded (${genDuration.inWholeMilliseconds}ms gen)")
                                    }
                                    is UploadResult.Failure -> {
                                        errors++
                                        addLog(LogEntry.Level.ERROR, coordinate, "Upload HTTP ${result.statusCode}: ${result.message}")
                                    }
                                    is UploadResult.Error -> {
                                        errors++
                                        addLog(LogEntry.Level.ERROR, coordinate, "Upload failed: ${result.exception.message ?: result.exception.toString()}")
                                    }
                                }

                                workers[i] = WorkerStatus(index = i)
                                onStateUpdate(snapshot())

                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Throwable) {
                                errors++
                                addLog(LogEntry.Level.ERROR, coordinate, "Generation failed: ${ex.message ?: ex.toString()}")
                                workers[i] = WorkerStatus(index = i)
                                onStateUpdate(snapshot())
                            }
                        }
                    }
                }
            }

        } catch (ex: CancellationException) {
            addLog(LogEntry.Level.WARN, "", "Stopped by user")
            onStateUpdate(snapshot())
            throw ex
        } catch (ex: Throwable) {
            addLog(LogEntry.Level.ERROR, "", "Fatal error: ${ex.message ?: ex.toString()}")
            onStateUpdate(snapshot())
        } finally {
            channel.close()
            for (i in workers.indices) workers[i] = WorkerStatus(index = i)
            onStateUpdate(snapshot().copy(isRunning = false))
        }
    }

    private fun snapshot(
        workers: List<WorkerStatus>,
        uploaded: Long,
        errors: Long,
        startTimeMs: Double,
        seedCursor: Long,
        logs: List<LogEntry>
    ): MinterState = MinterState(
        isRunning = true,
        currentSeed = seedCursor,
        workers = workers.toList(),
        totalUploaded = uploaded,
        totalErrors = errors,
        elapsedMs = currentTimeMs().toLong() - startTimeMs.toLong(),
        recentLogs = logs.toList()
    )
}
