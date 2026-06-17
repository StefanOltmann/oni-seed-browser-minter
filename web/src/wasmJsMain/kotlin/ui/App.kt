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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import service.ClusterMinter
import service.MinterState
import service.WebClient
import ui.theme.AccentColor
import ui.theme.DarkBackground
import ui.theme.LightText

private val json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

@Composable
fun App() {

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = AccentColor,
            background = DarkBackground,
            surface = DarkBackground,
            onPrimary = Color.White,
            onBackground = LightText,
            onSurface = LightText
        )
    ) {

        var serverUrl by remember { mutableStateOf("http://localhost:8080") }
        var startSeed by remember { mutableStateOf("0") }
        var parallelism by remember { mutableStateOf("15") }
        var worldgenWorkers by remember { mutableStateOf("4") }
        var clusterFilter by remember { mutableStateOf("") }
        var state by remember { mutableStateOf(MinterState()) }
        var runningJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val httpClient = remember { HttpClient(Js) {} }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
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

            ServerUrlField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                enabled = !state.isRunning
            )

            SeedAndParallelismRow(
                startSeed = startSeed,
                onStartSeedChange = { startSeed = it },
                parallelism = parallelism,
                onParallelismChange = { parallelism = it },
                enabled = !state.isRunning
            )

            WorldgenWorkersField(
                value = worldgenWorkers,
                onValueChange = { worldgenWorkers = it },
                enabled = !state.isRunning
            )

            ClusterFilterField(
                value = clusterFilter,
                onValueChange = { clusterFilter = it },
                enabled = !state.isRunning
            )

            ControlRow(
                state = state,
                onStart = {

                    val seed = startSeed.toLongOrNull() ?: return@ControlRow
                    val concurrency = parallelism.toIntOrNull()?.coerceIn(1, 50) ?: 15
                    val workers = worldgenWorkers.toIntOrNull()?.coerceIn(1, 16) ?: 4
                    val filter = clusterFilter.ifBlank { null }

                    val webClient = WebClient(httpClient)
                    val minter = ClusterMinter(webClient, serverUrl, json)

                    runningJob = scope.launch {
                        minter.run(seed, concurrency, workers, filter) { newState ->
                            state = newState
                        }
                    }
                },
                onStop = {
                    runningJob?.cancel()
                    runningJob = null
                }
            )

            StatsRow(state)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WorkerPanel(
                    workers = state.workers,
                    modifier = Modifier.weight(1f)
                )

                LogPanel(
                    entries = state.recentLogs,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
