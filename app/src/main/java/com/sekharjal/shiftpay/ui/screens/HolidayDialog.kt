@file:OptIn(ExperimentalMaterial3Api::class)

package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.ui.ChoiceRow
import com.sekharjal.shiftpay.ui.formatDateWithDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Asks for a holiday's name, date, and whether it is a Paid or Unpaid holiday. */
@Composable
fun AddHolidayDialog(
    dateFormat: String,
    onDismiss: () -> Unit,
    onAdd: (LocalDate, String, Boolean) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var name by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add holiday or leave") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (for example Diwali or Sick leave)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${formatDateWithDay(date, dateFormat)}")
                }
                Spacer(Modifier.height(12.dp))
                ChoiceRow(
                    selected = paid,
                    label = "Paid",
                    onClick = { paid = true },
                )
                ChoiceRow(
                    selected = !paid,
                    label = "Unpaid",
                    onClick = { paid = false },
                )
                Text(
                    if (paid) {
                        "A day off is paid as a normal day."
                    } else {
                        "A day off earns nothing. If you work, it is paid as a normal day."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(date, name, paid) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * One line in the holidays list: name and date, a Paid/Unpaid chip (tap it to switch),
 * and a delete button.
 */
@Composable
fun HolidayRow(
    name: String,
    dateText: String,
    paid: Boolean,
    onPaidChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = paid,
            onClick = { onPaidChange(!paid) },
            label = { Text(if (paid) "Paid" else "Unpaid") },
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove $name")
        }
    }
}
