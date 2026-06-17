package ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.theme.AccentColor
import ui.theme.LightText

@Composable
fun ServerUrlField(
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
