package com.sekharjal.shiftpay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.data.DayHours

/** Stacked bars (regular + overtime hours) for each day of a month, drawn with Compose Canvas. */
@Composable
fun HoursBarChart(days: List<DayHours>, modifier: Modifier = Modifier) {
    val regularColor = MaterialTheme.colorScheme.primary
    val overtimeColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val maxHours = (days.maxOfOrNull { it.regularHours + it.overtimeHours } ?: 0f).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val count = days.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = slot * 0.6f
        days.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2f
            val regularHeight = size.height * (day.regularHours / maxHours)
            val overtimeHeight = size.height * (day.overtimeHours / maxHours)
            drawRect(
                color = regularColor,
                topLeft = Offset(x, size.height - regularHeight),
                size = Size(barWidth, regularHeight),
            )
            drawRect(
                color = overtimeColor,
                topLeft = Offset(x, size.height - regularHeight - overtimeHeight),
                size = Size(barWidth, overtimeHeight),
            )
        }
        drawLine(
            color = axisColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 2f,
        )
    }
}

/** The chart plus its day labels and legend, ready to drop into a card. */
@Composable
fun HoursChartSection(days: List<DayHours>) {
    HoursBarChart(days)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("1", style = MaterialTheme.typography.labelSmall)
        Text(days.size.toString(), style = MaterialTheme.typography.labelSmall)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(MaterialTheme.colorScheme.primary, "Regular hours")
        LegendDot(MaterialTheme.colorScheme.tertiary, "Overtime hours")
    }
}
