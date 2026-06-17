package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import service.ClusterMinter
import service.LogEntry
import service.MinterState
import service.WorkerPhase
import service.WorkerStatus
import ui.theme.AccentColor
import ui.theme.DarkBackground
import ui.theme.DarkSurface
import ui.theme.ErrorColor
import ui.theme.LightText
import ui.theme.SuccessColor

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
            surface = DarkSurface,
            onPrimary = Color.White,
            onBackground = LightText,
            onSurface = LightText
        )
    ) {

        var serverUrl by remember { mutableStateOf("http://localhost:8080") }
        var startSeed by remember { mutableStateOf("0") }
        var parallelism by remember { mutableStateOf("15") }
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

            ControlRow(
                state = state,
                onStart = {

                    val seed = startSeed.toLongOrNull() ?: return@ControlRow
                    val concurrency = parallelism.toIntOrNull()?.coerceIn(1, 50) ?: 15

                    val minter = ClusterMinter(httpClient, serverUrl, json)

                    runningJob = scope.launch {
                        minter.run(seed, concurrency) { newState ->
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

            if (state.isRunning) {
                WorkerPanel(state.workers)
            }

            LogPanel(state.recentLogs)
        }
    }
}

@Composable
private fun ServerUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Server URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        colors = fieldColors()
    )
}

@Composable
private fun SeedAndParallelismRow(
    startSeed: String,
    onStartSeedChange: (String) -> Unit,
    parallelism: String,
    onParallelismChange: (String) -> Unit,
    enabled: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        OutlinedTextField(
            value = startSeed,
            onValueChange = onStartSeedChange,
            label = { Text("Start Seed") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            colors = fieldColors()
        )

        OutlinedTextField(
            value = parallelism,
            onValueChange = onParallelismChange,
            label = { Text("Parallelism") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            colors = fieldColors()
        )
    }
}

@Composable
private fun ControlRow(
    state: MinterState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {

        Button(
            onClick = {
                if (state.isRunning) onStop() else onStart()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) ErrorColor else AccentColor
            )
        ) {
            Text(if (state.isRunning) "Stop" else "Start")
        }

        if (state.isRunning) {

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Seed: ${state.currentSeed}",
                color = LightText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(16.dp))

            val elapsed = state.elapsedMs
            val seconds = elapsed / 1000
            val rate = if (seconds > 0) state.totalUploaded.toDouble() / seconds else 0.0
            val rateStr = (rate * 10).toLong() / 10.0

            Text(
                text = "$rateStr uploads/s",
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsRow(state: MinterState) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {

        StatBadge("Uploaded", state.totalUploaded.toString(), SuccessColor)

        if (state.totalErrors > 0) {
            StatBadge("Errors", state.totalErrors.toString(), ErrorColor)
        }

        if (state.elapsedMs > 0) {
            val seconds = state.elapsedMs / 1000
            StatBadge("Elapsed", "${seconds}s", Color.Gray)
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun WorkerPanel(workers: List<WorkerStatus>) {

    Text(
        text = "Workers (${workers.count { it.phase != WorkerPhase.IDLE }}/${workers.size} active)",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (worker in workers) {
            WorkerRow(worker)
        }
    }
}

@Composable
private fun WorkerRow(worker: WorkerStatus) {

    val (phaseLabel, phaseColor) = when (worker.phase) {
        WorkerPhase.IDLE -> "" to Color.Gray
        WorkerPhase.GENERATING -> "GEN" to Color(0xFFFFB74D)
        WorkerPhase.UPLOADING -> "UPD" to SuccessColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = "#${worker.index}",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp)
        )

        if (worker.phase != WorkerPhase.IDLE) {

            Text(
                text = phaseLabel,
                color = phaseColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp)
            )

            Text(
                text = worker.coordinate,
                color = LightText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

        } else {

            Text(
                text = "idle",
                color = Color.DarkGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun LogPanel(entries: List<LogEntry>) {

    if (entries.isEmpty()) return

    Text(
        text = "Log",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(entries) { entry ->
            LogEntryRow(entry)
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {

    val color = when (entry.level) {
        LogEntry.Level.INFO -> LightText
        LogEntry.Level.WARN -> Color(0xFFFFB74D)
        LogEntry.Level.ERROR -> ErrorColor
    }

    val prefix = when (entry.level) {
        LogEntry.Level.INFO -> ""
        LogEntry.Level.WARN -> "[WARN] "
        LogEntry.Level.ERROR -> "[ERROR]  "
    }

    val location = if (entry.coordinate.isNotEmpty()) "[${entry.coordinate}] " else ""

    Text(
        text = "$prefix$location${entry.message}",
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LightText,
    unfocusedTextColor = LightText,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = Color.Gray,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = Color.Gray
)
