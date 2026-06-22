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

package ui

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import ui.theme.AccentColor
import kotlin.coroutines.resume

/*
 * Hidden file input element ID — must match the element in index.html.
 */
private const val CSV_INPUT_ID = "csv-file-input"

/*
 * CSV coordinate header validation.
 * The first line must start with this prefix to be considered valid.
 */
private const val CSV_HEADER_PREFIX = "coord,cluster_seed,score"

/*
 * JS interop: get element by ID.
 * Follows the same pattern as Worldgen.wasmJs.kt.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsGetElementById(id: String): JsAny? =
    js("document.getElementById(id)")

/*
 * JS interop: trigger a click on an element.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsClickElement(element: JsAny): Unit =
    js("element.click()")

/*
 * JS interop: set the onchange handler on a file input element.
 * When a file is selected, reads it via FileReader and invokes the callback.
 *
 * Follows the same pattern as Worldgen.wasmJs.kt jsSetOnMessage:
 * the callback parameter is passed to js() and referenced directly in the JS string.
 * Single-line JS to avoid webpack source-map-loader parse errors.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun jsSetOnChange(element: JsAny, callback: (String) -> Unit): Unit =
    js("element.onchange = function(e) { var file = e.target.files[0]; if (!file) { return; } var reader = new FileReader(); reader.onload = function(ev) { callback(ev.target.result); }; reader.readAsText(file); }")

/*
 * Parse CSV content and extract coordinate strings.
 *
 * Validates the header and extracts the first column from each data row.
 * Returns null if the header is invalid.
 *
 * @param content Raw CSV file content
 * @return List of coordinate strings, or null if the CSV is invalid
 */
private fun parseCsvCoordinates(content: String): List<String>? {

    if (content.isBlank()) return null

    val lines = content.lines()

    if (lines.isEmpty()) return null

    val header = lines.first().trim()

    if (!header.lowercase().startsWith(CSV_HEADER_PREFIX)) return null

    return lines.drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            /* Extract first column (before the first comma) */
            line.substringBefore(",", "").trim()
        }
        .filter { it.isNotEmpty() }
}

/*
 * Read the selected file from the hidden file input element.
 * Suspends until the FileReader completes.
 *
 * Uses suspendCancellableCoroutine to bridge the async JS FileReader callback
 * to a Kotlin coroutine, following the same pattern as WorldgenWorker.sendMessage().
 *
 * @return File content as a string, or empty string if no file was selected
 */
@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun readFileFromInput(): String =
    suspendCancellableCoroutine { cont ->

        val element = jsGetElementById(CSV_INPUT_ID)

        if (element != null) {
            jsSetOnChange(element) { content -> cont.resume(content) }
            jsClickElement(element)
        } else {
            cont.resume("")
        }
    }

/*
 * Button that triggers a hidden file input to load a CSV file.
 *
 * Parses the CSV content to extract coordinate strings (first column).
 * Validates that the header starts with "coord,cluster_seed,score".
 * Returns the list of coordinates via the [onCoordinatesLoaded] callback.
 *
 * @param enabled Whether the button is interactive
 * @param onCoordinatesLoaded Callback with parsed coordinates, or null on error
 * @param modifier Optional modifier for layout
 */
@Composable
fun CsvUploadButton(
    enabled: Boolean,
    onCoordinatesLoaded: (List<String>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                val content = readFileFromInput()
                onCoordinatesLoaded(parseCsvCoordinates(content))
            }
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColor
        ),
        modifier = modifier
    ) {
        Text("Load CSV")
    }
}
