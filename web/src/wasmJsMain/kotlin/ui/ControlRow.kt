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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import service.minter.MinterState
import ui.theme.AccentColor
import ui.theme.ErrorColor
import ui.theme.LightText

/*
 * Start/Stop button row with live status display.
 *
 * When running, shows the current seed and upload rate (uploads/s).
 * The rate is calculated as totalUploaded / elapsedSeconds, truncated
 * to one decimal place for display.
 */
@Composable
fun ControlRow(
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

            /* Calculate uploads per second, truncated to one decimal */
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
