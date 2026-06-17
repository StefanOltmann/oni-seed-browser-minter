package ui

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ui.theme.AccentColor
import ui.theme.LightText

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LightText,
    unfocusedTextColor = LightText,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = Color.Gray,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = Color.Gray
)
