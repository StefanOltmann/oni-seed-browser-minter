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

package ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Color palette for the dark theme.
 * All UI elements reference these constants for consistent styling.
 */
val DarkBackground = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF28283C)
val LightText = Color(0xFFE0E0E0)
val AccentColor = Color(0xFF6C63FF)
val ErrorColor = Color(0xFFCF6679)
val SuccessColor = Color(0xFF66BB6A)

/*
 * Shared color scheme for text fields across the application.
 * Provides consistent focused/unfocused styling with the accent color.
 */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LightText,
    unfocusedTextColor = LightText,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = Color.Gray,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = Color.Gray
)
