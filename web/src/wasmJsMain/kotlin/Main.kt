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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import service.worldgenGenerate
import service.worldgenInit
import service.worldgenVersion
import worldgen.CoordinateUtil
import worldgen.WorldgenMapData
import worldgen.WorldgenMapDataConverter

private val json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

private val DarkBackground = Color(0xFF1A1A2E)
private val DarkSurface = Color(0xFF28283C)
private val LightText = Color(0xFFE0E0E0)
private val AccentColor = Color(0xFF6C63FF)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {

    ComposeViewport(document.body!!) {

        MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = AccentColor,
                background = DarkBackground,
                surface = DarkSurface,
                onPrimary = Color.White,
                onBackground = LightText,
                onSurface = LightText
            )
        ) {

            var coordinate by remember { mutableStateOf("") }
            var serverUrl by remember { mutableStateOf("http://localhost:8080") }
            var statusMessage by remember { mutableStateOf("Ready. Initializing worldgen...") }
            var generatedClusterJson by remember { mutableStateOf<String?>(null) }
            var isGenerating by remember { mutableStateOf(false) }
            var isUploading by remember { mutableStateOf(false) }
            var worldgenReady by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val httpClient = remember { HttpClient(Js) {} }

            LaunchedEffect(Unit) {
                try {
                    statusMessage = "Initializing worldgen WASM module..."
                    worldgenInit()
                    val version = worldgenVersion()
                    worldgenReady = true
                    statusMessage = "Worldgen ready. Version: $version"
                } catch (e: Exception) {
                    statusMessage = "Failed to initialize worldgen: ${e.message}"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "ONI Seed Browser Minter",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Worldgen Cluster Generator & Uploader",
                    fontSize = 16.sp,
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isUploading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = AccentColor,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = coordinate,
                    onValueChange = { coordinate = it },
                    label = { Text("Coordinate (leave empty for random)") },
                    placeholder = { Text("e.g. V-ACT-C-42-0-0-0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isGenerating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = AccentColor,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Button(
                        onClick = {

                            val targetCoordinate = coordinate.ifBlank {
                                CoordinateUtil.generateRandomCoordinate()
                            }

                            scope.launch {
                                isGenerating = true
                                statusMessage = "Generating cluster for $targetCoordinate..."

                                try {
                                    val jsonStr = worldgenGenerate(targetCoordinate)

                                    val worldgenMapData = WorldgenMapData.fromJson(jsonStr)

                                    val cluster = WorldgenMapDataConverter.convert(
                                        mapData = worldgenMapData,
                                        gameVersion = worldgenVersion().substringBefore('+').toInt()
                                    )

                                    generatedClusterJson = json.encodeToString(cluster)
                                    coordinate = cluster.coordinate
                                    statusMessage = "Generated cluster: ${cluster.coordinate} (${cluster.cluster.prefix})"
                                } catch (e: Exception) {
                                    statusMessage = "Generation failed: ${e.message}"
                                    e.printStackTrace()
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = !isGenerating && worldgenReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor
                        )
                    ) {
                        Text(if (isGenerating) "Generating..." else "Generate")
                    }

                    Button(
                        onClick = {

                            val clusterJson = generatedClusterJson ?: return@Button

                            scope.launch {
                                isUploading = true
                                statusMessage = "Uploading to server..."

                                try {
                                    val response = httpClient.post("$serverUrl/upload") {
                                        contentType(ContentType.Application.Json)
                                        setBody(clusterJson)
                                    }

                                    val responseText = response.bodyAsText()
                                    statusMessage = "Server response (${response.status}): $responseText"
                                } catch (e: Exception) {
                                    statusMessage = "Upload error: ${e.message}"
                                } finally {
                                    isUploading = false
                                }
                            }
                        },
                        enabled = !isUploading && generatedClusterJson != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor
                        )
                    ) {
                        Text(if (isUploading) "Uploading..." else "Upload to Server")
                    }
                }

                Text(
                    text = statusMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(12.dp),
                    color = LightText,
                    fontSize = 14.sp
                )

                if (generatedClusterJson != null) {

                    Text(
                        text = "Generated Cluster Data",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    SelectionContainer {
                        Text(
                            text = generatedClusterJson!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = LightText
                        )
                    }
                }
            }
        }
    }
}
