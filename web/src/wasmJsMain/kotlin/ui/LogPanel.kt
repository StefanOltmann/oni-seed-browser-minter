package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import service.LogEntry
import ui.theme.ErrorColor
import ui.theme.LightText

@Composable
fun LogPanel(entries: List<LogEntry>) {

    if (entries.isEmpty()) return

    Text(
        text = "Log",
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
