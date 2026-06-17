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

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * JS interop functions for Web Worker communication.
 * These call into JavaScript to create and communicate with Web Workers.
 * The worldgen.worker.mjs file handles the actual worldgen logic.
 */

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
 *
 * The worker protocol uses message IDs to correlate requests with responses.
 * Each request gets a unique ID, and the response includes the same ID.
 */
class WorldgenWorker {

    /* The underlying JS Web Worker instance */
    @OptIn(ExperimentalWasmJsInterop::class)
    private val worker: JsAny = jsCreateWorker()

    /* Counter for unique request IDs */
    private var nextId = 0

    /* Map of pending request IDs to their continuation callbacks */
    private val pendingCallbacks = mutableMapOf<Int, (Result<String?>) -> Unit>()

    /* Whether the onmessage listener has been set up */
    private var isListening = false

    /*
     * Set up the onmessage listener for this worker.
     * Called once on first message send.
     * Routes responses to the correct callback based on message ID.
     */
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
     * Send a message to this worker and suspend until the response arrives.
     *
     * Uses suspendCancellableCoroutine to bridge the callback-based JS API
     * to Kotlin coroutines. Each message gets a unique ID for correlation.
     *
     * @param type Message type (e.g. "init", "version", "generate")
     * @param coordinate Optional coordinate for generate requests
     * @return The result string from the worker, or null for init/version
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

/*
 * Returns the number of CPU cores available on the machine.
 * Falls back to 4 if navigator.hardwareConcurrency is not available.
 * Used to default the worker count to (cores - 1).
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsHardwareConcurrency(): Int = js("navigator.hardwareConcurrency || 4")

val hardwareConcurrency: Int by lazy { jsHardwareConcurrency() }

actual val worldgenSupported: Boolean = true

/*
 * Pool of Web Workers for parallel worldgen.
 *
 * Each coroutine worker gets assigned a worker from the pool via getWorker(index).
 * The pool size determines how many worldgen jobs can run truly in parallel
 * (each in its own Web Worker thread).
 *
 * Initialize with the desired number of workers before starting the minter.
 * All workers are initialized in parallel (init message sent to each).
 */
object WorldgenWorkerPool {

    private val workers = mutableListOf<WorldgenWorker>()
    private var initialized = false

    /*
     * Create and initialize the worker pool.
     * Must be called once before starting generation.
     * Creates N Web Workers and sends "init" to each.
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
     * Get a worker by index.
     * Uses modulo to wrap around if index >= pool size.
     */
    fun getWorker(index: Int): WorldgenWorker {
        require(workers.isNotEmpty()) { "Worker pool not initialized" }
        return workers[index % workers.size]
    }

    /* Number of workers in the pool */
    val size: Int get() = workers.size
}

/*
 * Expect declarations for worldgen operations.
 * The wasmJs implementation delegates to WorldgenWorkerPool.
 */

actual suspend fun worldgenInit() {
    /* No-op: initialization is handled by WorldgenWorkerPool */
}

actual suspend fun worldgenVersion(): String {
    /* Delegate to the first worker to get version */
    return WorldgenWorkerPool.getWorker(0).sendMessage("version") ?: ""
}

actual suspend fun worldgenGenerate(coordinate: String): String {
    /*
     * This function is called by ClusterGenerator, but the actual generation
     * is done via WorldgenWorkerPool.getWorker(workerIndex).sendMessage("generate", ...).
     * This fallback uses worker 0 for backward compatibility.
     */
    return WorldgenWorkerPool.getWorker(0).sendMessage("generate", coordinate) ?: ""
}
