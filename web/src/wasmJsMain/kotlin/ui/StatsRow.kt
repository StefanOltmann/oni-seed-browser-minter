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
