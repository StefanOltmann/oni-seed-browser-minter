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

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsCreateWorker(): JsAny =
    js("new Worker(new URL('./worldgen.worker.mjs', import.meta.url), { type: 'module' })")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsPostMessageWithCoordinate(worker: JsAny, id: Int, type: String, coordinate: String): Unit =
    js("worker.postMessage({ id: id, type: type, payload: { coordinate: coordinate } })")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsPostMessageNoPayload(worker: JsAny, id: Int, type: String): Unit =
    js("worker.postMessage({ id: id, type: type, payload: null })")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsSetOnMessage(worker: JsAny, callback: (JsAny) -> Unit): Unit =
    js("worker.onmessage = function(event) { callback(event.data) }")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetId(data: JsAny): Int =
    js("data.id")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetType(data: JsAny): String =
    js("data.type")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetResult(data: JsAny): String? =
    js("data.result ?? null")

@Suppress("UNUSED", "UnusedParameter") // false positive
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetError(data: JsAny): String =
    js("data.error ?? 'Unknown error'")

/*
 * A single Web Worker instance for worldgen.
 *
 * Each worker has its own:
 *  - JS Worker instance (runs in its own thread)
 *  - ID counter for correlating requests with responses
 *  - Callback map for pending requests
 *  - Message listener
 *
 * This enables true CPU parallelism: multiple workers can run
 * worldgen.generate() simultaneously in separate threads.
 */
class WorldgenWorker {

    @OptIn(ExperimentalWasmJsInterop::class)
    private val worker: JsAny = jsCreateWorker()

    private var nextId = 0
    private val pendingCallbacks = mutableMapOf<Int, (Result<String?>) -> Unit>()
    private var isListening = false

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun ensureListening() {
        jsSetOnMessage(worker) { data ->

            val id = jsGetId(data)
            val type = jsGetType(data)

            val callback = pendingCallbacks.remove(id) ?: return@jsSetOnMessage

            if (type == "error")
                callback(Result.failure(Exception(jsGetError(data))))
            else
                callback(Result.success(jsGetResult(data)))
        }
    }

    /*
     * Send a message to this worker and wait for the response.
     * Each worker handles requests independently, so multiple
     * workers can process requests in parallel.
     */
    @OptIn(ExperimentalWasmJsInterop::class)
    suspend fun sendMessage(type: String, coordinate: String? = null): String? =
        suspendCancellableCoroutine { cont ->
            if (!isListening) {
                ensureListening()
                isListening = true
            }
            val id = nextId++
            pendingCallbacks[id] = { result ->
                result.fold(
                    onSuccess = { cont.resume(it) },
                    onFailure = { cont.resumeWithException(it) }
                )
            }
            if (coordinate != null)
                jsPostMessageWithCoordinate(worker, id, type, coordinate)
            else
                jsPostMessageNoPayload(worker, id, type)
        }
}

actual val worldgenSupported: Boolean = true

/*
 * Pool of Web Workers for parallel worldgen.
 *
 * Each coroutine worker gets assigned a worker from the pool.
 * The pool size determines how many worldgen jobs can run truly
 * in parallel (each in its own Web Worker thread).
 *
 * Initialize with the desired number of workers before starting
 * the minter. Each worker must be initialized (init + version)
 * before it can generate clusters.
 */
object WorldgenWorkerPool {

    private val workers = mutableListOf<WorldgenWorker>()
    private var initialized = false

    /*
     * Create and initialize the worker pool.
     * Must be called once before starting generation.
     */
    suspend fun initialize(poolSize: Int) {
        if (initialized) return

        workers.clear()
        repeat(poolSize) {
            workers.add(WorldgenWorker())
        }

        /* Initialize all workers in parallel */
        for (worker in workers) {
            worker.sendMessage("init")
        }

        initialized = true
    }

    /*
     * Get a worker by index (round-robin if index >= pool size).
     */
    fun getWorker(index: Int): WorldgenWorker {
        require(workers.isNotEmpty()) { "Worker pool not initialized" }
        return workers[index % workers.size]
    }

    val size: Int get() = workers.size
}

/*
 * Generate a cluster using a specific worker from the pool.
 */
actual suspend fun worldgenInit() {
    /* No-op: initialization is handled by WorldgenWorkerPool */
}

actual suspend fun worldgenVersion(): String {
    /* Delegate to the first worker to get version */
    return WorldgenWorkerPool.getWorker(0).sendMessage("version") ?: ""
}

actual suspend fun worldgenGenerate(coordinate: String): String {
    /* This is called by ClusterGenerator — but we need worker assignment.
     * The actual generation is done via WorldgenWorker directly in ClusterGenerator. */
    return WorldgenWorkerPool.getWorker(0).sendMessage("generate", coordinate) ?: ""
}
