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
import service.MinterState
import ui.theme.AccentColor
import ui.theme.ErrorColor
import ui.theme.LightText

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
