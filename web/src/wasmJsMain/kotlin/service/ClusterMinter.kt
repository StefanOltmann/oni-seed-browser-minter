package service

import de.stefan_oltmann.oni.model.ClusterType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalWasmJsInterop::class)
private fun currentTimeMs(): Double = js("Date.now()")

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

class ClusterMinter(
    private val httpClient: HttpClient,
    private val serverUrl: String,
    private val json: Json
) {

    private val maxLogEntries = 100

    suspend fun run(
        startSeed: Long,
        parallelism: Int,
        onStateUpdate: (MinterState) -> Unit
    ) {
        val logs = mutableListOf<LogEntry>()
        val workers = MutableList(parallelism) { WorkerStatus(index = it) }

        var uploaded = 0L
        var errors = 0L
        var seedCursor = startSeed
        val startTimeMs = currentTimeMs()

        fun snapshot(): MinterState = MinterState(
            isRunning = true,
            currentSeed = seedCursor,
            workers = workers.toList(),
            totalUploaded = uploaded,
            totalErrors = errors,
            elapsedMs = currentTimeMs().toLong() - startTimeMs.toLong(),
            recentLogs = logs.toList()
        )

        fun addLog(level: LogEntry.Level, coordinate: String, message: String) {
            logs.add(LogEntry(level, seedCursor, coordinate, message))
            if (logs.size > maxLogEntries) logs.removeFirst()
        }

        try {
            while (true) {

                addLog(LogEntry.Level.INFO, "", "Seed $seedCursor — ${ClusterType.entries.size} clusters, parallelism=$parallelism")
                onStateUpdate(snapshot())

                coroutineScope {

                    val semaphore = Semaphore(parallelism)
                    val slotAllocator = Mutex()
                    var nextSlotIndex = 0

                    for (clusterType in ClusterType.entries) {

                        launch {

                            var slotIndex = 0

                            slotAllocator.withLock {
                                slotIndex = nextSlotIndex % parallelism
                                nextSlotIndex++
                            }

                            semaphore.withPermit {

                                val coordinate = "${clusterType.prefix}-$seedCursor-0-0-0"

                                workers[slotIndex] = WorkerStatus(
                                    index = slotIndex,
                                    phase = WorkerPhase.GENERATING,
                                    coordinate = coordinate
                                )
                                onStateUpdate(snapshot())

                                try {

                                    val (cluster, genDuration) = measureTimedValue {
                                        ClusterGenerator.generateCluster(coordinate)
                                    }

                                    workers[slotIndex] = WorkerStatus(
                                        index = slotIndex,
                                        phase = WorkerPhase.UPLOADING,
                                        coordinate = coordinate
                                    )
                                    onStateUpdate(snapshot())

                                    val clusterJson = json.encodeToString(cluster)

                                    measureTime {
                                        try {
                                            httpClient.post("$serverUrl/upload") {
                                                contentType(ContentType.Application.Json)
                                                setBody(clusterJson)
                                            }

                                            uploaded++
                                            addLog(LogEntry.Level.INFO, coordinate, "Uploaded (${genDuration.inWholeMilliseconds}ms gen)")

                                        } catch (ex: ClientRequestException) {
                                            errors++
                                            addLog(LogEntry.Level.ERROR, coordinate, "Upload HTTP ${ex.response.status}: ${ex.message}")
                                        } catch (ex: ServerResponseException) {
                                            errors++
                                            addLog(LogEntry.Level.ERROR, coordinate, "Upload HTTP ${ex.response.status}: ${ex.message}")
                                        } catch (ex: Exception) {
                                            errors++
                                            addLog(LogEntry.Level.ERROR, coordinate, "Upload failed: ${ex.message ?: ex.toString()}")
                                        }
                                    }

                                    workers[slotIndex] = WorkerStatus(index = slotIndex)
                                    onStateUpdate(snapshot())

                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Throwable) {
                                    errors++
                                    addLog(LogEntry.Level.ERROR, coordinate, "Generation failed: ${ex.message ?: ex.toString()}")
                                    workers[slotIndex] = WorkerStatus(index = slotIndex)
                                    onStateUpdate(snapshot())
                                }
                            }
                        }
                    }
                }

                addLog(LogEntry.Level.INFO, "", "Seed $seedCursor complete — uploaded: $uploaded, errors: $errors")
                onStateUpdate(snapshot())

                seedCursor++
            }

        } catch (ex: CancellationException) {
            addLog(LogEntry.Level.WARN, "", "Stopped by user")
            onStateUpdate(snapshot())
            throw ex
        } catch (ex: Throwable) {
            addLog(LogEntry.Level.ERROR, "", "Fatal error: ${ex.message ?: ex.toString()}")
            onStateUpdate(snapshot())
        } finally {
            for (i in workers.indices) workers[i] = WorkerStatus(index = i)
            onStateUpdate(snapshot().copy(isRunning = false))
        }
    }
}
