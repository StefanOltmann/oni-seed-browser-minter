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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import service.hardwareConcurrency
import ui.theme.AccentColor
import ui.theme.LightText

/*
 * Slider for selecting the number of CPU cores for worldgen.
 *
 * Displays a label ("CPU Cores") and the current value (e.g. "7 / 8").
 * Range: 1 to hardwareConcurrency (detected from navigator.hardwareConcurrency).
 * Defaults to (hardwareConcurrency - 1) to leave one core for the UI.
 */
@Composable
fun CpuCoresSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CPU Cores",
                color = LightText,
                fontSize = 14.sp
            )

            Text(
                text = "$value / $hardwareConcurrency",
                color = AccentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..hardwareConcurrency.toFloat(),
            steps = hardwareConcurrency - 2,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AccentColor,
                activeTrackColor = AccentColor,
                inactiveTrackColor = LightText.copy(alpha = 0.3f)
            )
        )
    }
}
