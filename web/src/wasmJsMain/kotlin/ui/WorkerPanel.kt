package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import service.WorkerPhase
import service.WorkerStatus
import ui.theme.LightText
import ui.theme.SuccessColor

@Composable
fun WorkerPanel(workers: List<WorkerStatus>) {

    Text(
        text = "Workers (${workers.count { it.phase != WorkerPhase.IDLE }}/${workers.size} active)",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (worker in workers) {
            WorkerRow(worker)
        }
    }
}

@Composable
private fun WorkerRow(worker: WorkerStatus) {

    val (phaseLabel, phaseColor) = when (worker.phase) {
        WorkerPhase.IDLE -> "" to Color.Gray
        WorkerPhase.GENERATING -> "GEN" to Color(0xFFFFB74D)
        WorkerPhase.UPLOADING -> "UPD" to SuccessColor
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = "#${worker.index}",
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp)
        )

        if (worker.phase != WorkerPhase.IDLE) {

            Text(
                text = phaseLabel,
                color = phaseColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(36.dp)
            )

            Text(
                text = worker.coordinate,
                color = LightText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

        } else {

            Text(
                text = "idle",
                color = Color.DarkGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
