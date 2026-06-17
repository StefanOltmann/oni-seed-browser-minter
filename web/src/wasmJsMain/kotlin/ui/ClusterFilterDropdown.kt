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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.stefan_oltmann.oni.model.ClusterType
import ui.theme.fieldColors

/*
 * Pre-built list of cluster type names for the dropdown.
 * First entry is "All" (process all cluster types), followed by each prefix.
 */
private val clusterTypeNames: List<String> = listOf("All") + ClusterType.entries.map { it.prefix }

/*
 * Dropdown for selecting a specific cluster type to process.
 *
 * Uses Material3 ExposedDropdownMenuBox. When "All" is selected,
 * the minter processes all 41 cluster types. When a specific type
 * is selected, only that type is processed (exact match).
 *
 * @param selectedClusterType Currently selected cluster type name
 * @param onSelectedChange Callback when a new cluster type is selected
 * @param enabled Whether the dropdown is interactive
 * @param modifier Optional modifier for layout (e.g. weight in a Row)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterFilterDropdown(
    selectedClusterType: String,
    onSelectedChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {

        OutlinedTextField(
            value = selectedClusterType,
            onValueChange = {},
            readOnly = true,
            label = { Text("Cluster Type") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = fieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            clusterTypeNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelectedChange(name)
                        expanded = false
                    }
                )
            }
        }
    }
}
