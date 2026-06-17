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

import kotlinx.coroutines.delay

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsSendMessageWithCoordinate(type: String, coordinate: String): Int =
    js("worldgenSendMessage(type, coordinate)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsSendMessageNoPayload(type: String): Int =
    js("worldgenSendMessage(type, null)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsPollResult(id: Int): JsAny =
    js("worldgenPollResult(id)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetDone(result: JsAny): Boolean =
    js("result.done")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetValue(result: JsAny): String? =
    js("result.value")

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetError(result: JsAny): String? =
    js("result.error")

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun sendMessage(type: String, coordinate: String? = null): String? {

    val id = if (coordinate != null) {
        jsSendMessageWithCoordinate(type, coordinate)
    } else {
        jsSendMessageNoPayload(type)
    }

    while (true) {
        val pollResult = jsPollResult(id)

        if (jsGetDone(pollResult)) {
            val error = jsGetError(pollResult)
            if (error != null) {
                throw Exception(error)
            }
            return jsGetValue(pollResult)
        }

        delay(10)
    }
}

actual val worldgenSupported: Boolean = true

actual suspend fun worldgenInit() {
    sendMessage("init")
}

actual suspend fun worldgenVersion(): String =
    sendMessage("version") ?: ""

actual suspend fun worldgenGenerate(coordinate: String): String =
    sendMessage("generate", coordinate) ?: ""
