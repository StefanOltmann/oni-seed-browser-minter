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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import service.MinterState
import ui.theme.ErrorColor
import ui.theme.SuccessColor

@Composable
fun StatsRow(state: MinterState) {
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
