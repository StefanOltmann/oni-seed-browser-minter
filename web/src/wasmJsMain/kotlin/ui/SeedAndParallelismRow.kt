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
