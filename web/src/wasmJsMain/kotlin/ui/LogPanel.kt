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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import service.minter.LogEntry
import ui.theme.ErrorColor
import ui.theme.LightText

/*
 * Log panel showing recent minter activity.
 *
 * Auto-scrolls to the bottom when new entries are added.
 * Entries are color-coded: INFO = light gray, WARN = amber, ERROR = red.
 * Each entry shows [LEVEL] [coordinate] message.
 * Empty coordinate means it's a global message (not cluster-specific).
 *
 * @param entries List of LogEntry objects from MinterState.recentLogs
 * @param modifier Optional modifier for layout (e.g. weight in a Row)
 */
@Composable
fun LogPanel(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier
) {

    Column(modifier = modifier) {

        Text(
            text = "Log (${entries.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        val listState = rememberLazyListState()

        /* Auto-scroll to latest entry when new entries are added */
        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) {
                listState.animateScrollToItem(entries.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(entries) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

/*
 * Single log entry row with level-based coloring and prefix.
 */
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
