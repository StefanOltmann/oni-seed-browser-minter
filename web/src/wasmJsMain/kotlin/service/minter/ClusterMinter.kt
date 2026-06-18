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

package service.minter

import de.stefan_oltmann.oni.model.ClusterType
import de.stefan_oltmann.oni.model.WorldTrait
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import service.ClusterGenerator
import service.WebClient
import service.WorldgenWorkerPool
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

/**
 * Orchestrates cluster generation and upload using a Channel-based work queue.
 *
 * Architecture:
 *  1. A producer coroutine continuously feeds `(ClusterType, seed)` pairs into a [Channel]
 *  2. N worker coroutines (one per CPU core) consume from the channel independently
 *  3. Each worker uses its own Web Worker from the [WorldgenWorkerPool]
 *  4. Multiple Web Workers run `worldgen.generate()` simultaneously in separate threads
 *  5. Workers independently generate and upload — no batching by seed
 *
 * This design achieves true CPU parallelism for generation, with uploads
 * overlapping between workers. The producer never waits for workers to finish.
 *
 * @property webClient HTTP client for uploads with proper status code handling
 * @property serverUrl Base URL of the backend server
 * @property json Kotlinx.serialization Json instance for cluster serialization
 */
class ClusterMinter(
    private val webClient: WebClient,
    private val serverUrl: String,
    private val json: Json
) {

    /* Maximum number of log entries retained in the UI. Older entries are discarded. */
    private val maxLogEntries = 100

    /*
     * Run the minter with the given parameters.
     *
     * Initializes the Web Worker pool, then starts the producer-consumer loop.
     * The loop runs until cancelled by the user or a fatal error occurs.
     *
     * @param startSeed The first seed to process (increments continuously)
     * @param cpuCores Number of CPU cores to use (1:1 mapping to Web Workers)
     * @param clusterFilter Optional exact match filter for cluster type prefix
     * @param remix The remix value for the coordinate (last segment)
     * @param onStateUpdate Callback for state updates (called on recomposition)
     */
    suspend fun run(
        startSeed: Long,
        cpuCores: Int,
        clusterFilter: String? = null,
        remix: String = "0",
        onStateUpdate: (MinterState) -> Unit
    ) {
        val logs = mutableListOf<LogEntry>()
        val workers = MutableList(cpuCores) { WorkerStatus(index = it) }

        var uploaded = 0L
        var skipped = 0L
        var errors = 0L
        var seedCursor = startSeed
        val startTimeMs = currentTimeMs()

        /*
         * Filter cluster types if a filter is provided.
         * Uses exact match — the dropdown provides specific cluster type prefixes.
         * If no filter is set, all 41 cluster types are processed.
         */
        val activeClusterTypes = if (clusterFilter.isNullOrBlank())
            ClusterType.entries
        else
            ClusterType.entries.filter {
                it.prefix.equals(clusterFilter, ignoreCase = true)
            }

        if (activeClusterTypes.isEmpty()) {
            logs.add(LogEntry(LogEntry.Level.ERROR, startSeed, "", "No cluster types match filter: $clusterFilter"))
            onStateUpdate(snapshot(workers, uploaded, skipped, errors, startTimeMs, seedCursor, logs))
            return
        }

        /* Create a snapshot of the current state for the UI */
        fun snapshot(): MinterState = snapshot(workers, uploaded, skipped, errors, startTimeMs, seedCursor, logs)

        /* Add a log entry and trim to maxLogEntries */
        fun addLog(level: LogEntry.Level, coordinate: String, message: String) {
            logs.add(LogEntry(level, seedCursor, coordinate, message))
            if (logs.size > maxLogEntries) logs.removeFirst()
        }

        /* Initialize the Web Worker pool — each worker runs in its own thread */
        WorldgenWorkerPool.initialize(cpuCores)
        addLog(LogEntry.Level.INFO, "", "Initialized $cpuCores Web Workers")
        onStateUpdate(snapshot())

        /*
         * Create a buffered channel for work items.
         * Buffer size is 2x the number of cluster types per seed to keep
         * workers busy without blocking the producer.
         */
        val channel = Channel<WorkItem>(capacity = activeClusterTypes.size * 2)

        try {
            coroutineScope {

                /*
                 * Producer coroutine: continuously feeds work items into the channel.
                 * For each seed, emits all cluster types, then increments the seed.
                 * Runs indefinitely until cancelled — workers are never idle if work exists.
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
                 * Each worker is assigned a dedicated Web Worker for true CPU parallelism.
                 */
                for (i in 0 until cpuCores) {
                    launch(Dispatchers.Default) {
                        for (workItem in channel) {

                            val coordinate = "${workItem.clusterType.prefix}-${workItem.seed}-0-0-$remix"

                            /* Check if cluster already exists on the server */
                            if (webClient.checkExists(serverUrl, coordinate) == true) {
                                skipped++
                                addLog(LogEntry.Level.INFO, coordinate, "Already exists, skipping")
                                workers[i] = WorkerStatus(index = i)
                                onStateUpdate(snapshot())
                                continue
                            }

                            workers[i] = WorkerStatus(
                                index = i,
                                phase = WorkerPhase.GENERATING,
                                coordinate = coordinate
                            )
                            onStateUpdate(snapshot())

                            try {

                                /* Generate the cluster via WASM worldgen using assigned Web Worker */
                                val (cluster, genDuration) = measureTimedValue {
                                    ClusterGenerator.generateCluster(coordinate, workerIndex = i)
                                }

                                workers[i] = WorkerStatus(
                                    index = i,
                                    phase = WorkerPhase.UPLOADING,
                                    coordinate = coordinate
                                )
                                onStateUpdate(snapshot())

                                val startingAsteroid = cluster.asteroids.first()
                                val startingAsteroidTraits = startingAsteroid.getEffectiveWorldTraits()

                                /* Skip unwanted world traits on the starting asteroid. */
                                if (startingAsteroidTraits.contains(WorldTrait.MetalPoor) ||
                                    startingAsteroidTraits.contains(WorldTrait.GeoDormant) ||
                                    startingAsteroidTraits.contains(WorldTrait.BouldersLarge) ||
                                    startingAsteroidTraits.contains(WorldTrait.BouldersMedium) ||
                                    startingAsteroidTraits.contains(WorldTrait.BouldersSmall) ||
                                    startingAsteroidTraits.contains(WorldTrait.BouldersMixed))
                                    continue

                                val clusterJson = json.encodeToString(cluster)

                                /* Upload with proper status code checking */
                                when (val result = webClient.uploadCluster(serverUrl, clusterJson)) {

                                    is service.UploadResult.Success -> {
                                        uploaded++
                                        addLog(
                                            LogEntry.Level.INFO,
                                            coordinate,
                                            "Uploaded (${genDuration.inWholeMilliseconds}ms gen)"
                                        )
                                    }

                                    is service.UploadResult.Failure -> {
                                        errors++
                                        addLog(
                                            LogEntry.Level.ERROR,
                                            coordinate,
                                            "Upload HTTP ${result.statusCode}: ${result.message}"
                                        )
                                    }

                                    is service.UploadResult.Error -> {
                                        errors++
                                        addLog(
                                            LogEntry.Level.ERROR,
                                            coordinate,
                                            "Upload failed: ${result.exception.message ?: result.exception.toString()}"
                                        )
                                    }
                                }

                                workers[i] = WorkerStatus(index = i)
                                onStateUpdate(snapshot())

                            } catch (ex: CancellationException) {
                                /* Re-throw cancellation — do not catch it */
                                throw ex
                            } catch (ex: Throwable) {
                                errors++
                                addLog(
                                    LogEntry.Level.ERROR,
                                    coordinate,
                                    "Generation failed: ${ex.message ?: ex.toString()}"
                                )
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

    /*
     * Create a snapshot of the current minter state for the UI.
     * Converts mutable lists to immutable copies for Compose.
     */
    private fun snapshot(
        workers: List<WorkerStatus>,
        uploaded: Long,
        skipped: Long,
        errors: Long,
        startTimeMs: Double,
        seedCursor: Long,
        logs: List<LogEntry>
    ): MinterState = MinterState(
        isRunning = true,
        currentSeed = seedCursor,
        workers = workers.toList(),
        totalUploaded = uploaded,
        totalSkipped = skipped,
        totalErrors = errors,
        elapsedMs = currentTimeMs().toLong() - startTimeMs.toLong(),
        recentLogs = logs.toList()
    )
}
