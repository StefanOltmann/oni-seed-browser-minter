import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import de.stefan_oltmann.oni.model.ClusterType
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import service.ClusterGenerator

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

    ComposeViewport(document.getElementById("root")!!) {

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

            var serverUrl by remember { mutableStateOf("http://localhost:8080") }
            var startSeed by remember { mutableStateOf("0") }
            var parallelism by remember { mutableStateOf("15") }
            var currentSeed by remember { mutableLongStateOf(0L) }
            var totalUploaded by remember { mutableLongStateOf(0L) }
            var statusMessage by remember { mutableStateOf("Ready.") }
            var isRunning by remember { mutableStateOf(false) }
            var runningJob by remember { mutableStateOf<Job?>(null) }
            val scope = rememberCoroutineScope()
            val httpClient = remember { HttpClient(Js) {} }

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
                    enabled = !isRunning,
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

                    OutlinedTextField(
                        value = startSeed,
                        onValueChange = { startSeed = it },
                        label = { Text("Start Seed") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isRunning,
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
                        value = parallelism,
                        onValueChange = { parallelism = it },
                        label = { Text("Parallelism") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isRunning,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = AccentColor,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Button(
                        onClick = {

                            if (isRunning) {
                                runningJob?.cancel()
                                isRunning = false
                                statusMessage = "Stopped at seed $currentSeed."
                                return@Button
                            }

                            val seed = startSeed.toLongOrNull() ?: return@Button
                            val concurrency = parallelism.toIntOrNull()?.coerceIn(1, 50) ?: 15

                            isRunning = true
                            totalUploaded = 0L
                            currentSeed = seed

                            runningJob = scope.launch {

                                try {

                                    var seedCursor = seed

                                    while (true) {

                                        currentSeed = seedCursor
                                        statusMessage = "Seed $seedCursor — generating ${ClusterType.entries.size} clusters (concurrency=$concurrency)..."

                                        coroutineScope {

                                            val semaphore = Semaphore(concurrency)

                                            for (clusterType in ClusterType.entries) {

                                                launch {

                                                    semaphore.withPermit {

                                                        val coordinate = "${clusterType.prefix}-$seedCursor-0-0-0"

                                                        try {

                                                            val cluster = ClusterGenerator.generateCluster(coordinate)
                                                            val clusterJson = json.encodeToString(cluster)

                                                            val response = httpClient.post("$serverUrl/upload") {
                                                                contentType(ContentType.Application.Json)
                                                                setBody(clusterJson)
                                                            }

                                                            totalUploaded++

                                                        } catch (ex: Throwable) {

                                                            println("[ERROR] $coordinate: ${ex.message}")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        statusMessage = "Seed $seedCursor done — total uploaded: $totalUploaded"
                                        seedCursor++
                                    }

                                } catch (ex: CancellationException) {

                                    throw ex

                                } catch (ex: Throwable) {

                                    statusMessage = "Fatal error: ${ex.message}"
                                    ex.printStackTrace()

                                } finally {

                                    isRunning = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFCF6679) else AccentColor
                        )
                    ) {
                        Text(if (isRunning) "Stop" else "Start")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Seed: $currentSeed",
                        color = LightText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Uploaded: $totalUploaded",
                        color = LightText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
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
            }
        }
    }
}


