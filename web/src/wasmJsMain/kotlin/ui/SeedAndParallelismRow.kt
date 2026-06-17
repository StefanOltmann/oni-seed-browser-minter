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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeedAndParallelismRow(
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
