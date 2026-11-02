package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.data.ShiftEntry
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.ui.ClockActions
import com.sekharjal.shiftpay.ui.ClockCard
import com.sekharjal.shiftpay.ui.ScreenTitle
import com.sekharjal.shiftpay.ui.formatDateWithDay
import com.sekharjal.shiftpay.ui.formatDuration
import com.sekharjal.shiftpay.ui.formatMoney
import com.sekharjal.shiftpay.ui.formatTime
import com.sekharjal.shiftpay.ui.label

@Composable
fun ShiftsScreen(
    snap: Snapshot,
    clock: ClockActions,
    onAddManual: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(
                title = "Shifts",
                subtitle = "Clock in and out, or tap a past shift to edit it.",
            )
        }
        item { ClockCard(snap = snap, actions = clock) }
        item {
            OutlinedButton(onClick = onAddManual, modifier = Modifier.fillMaxWidth()) {
                Text("Add a past shift manually")
            }
        }
        if (snap.entries.isEmpty()) {
            item {
                Text(
                    "No shifts recorded yet. Tap Start shift when you begin work.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(snap.entries, key = { it.record.id }) { entry ->
            ShiftCard(
                entry = entry,
                snap = snap,
                onClick = { onEdit(entry.record.id) },
                onDelete = { pendingDelete = entry.record.id },
            )
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this shift?") },
            text = { Text("This removes the shift from your records. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShiftCard(entry: ShiftEntry, snap: Snapshot, onClick: () -> Unit, onDelete: () -> Unit) {
    val settings = snap.settings
    val input = entry.input
    val hours = ShiftCalculator.hoursOf(input)
    val earnings = snap.shiftEarnings(input)

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatDateWithDay(input.date, settings.dateFormat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatTime(input.start, settings.use24Hour)} – " +
                        "${formatTime(input.end, settings.use24Hour)} · ${input.type.label()}" +
                        if (input.date in snap.holidayDates) " · Holiday" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val overtimeText =
                    if (hours.overtimeMinutes > 0) " · ${formatDuration(hours.overtimeMinutes)} overtime" else ""
                Text(
                    "${formatDuration(hours.workedMinutes)} worked$overtimeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (earnings.gross.signum() > 0) {
                    Text(
                        "≈ ${formatMoney(earnings.gross, settings.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete shift")
            }
        }
    }
}
