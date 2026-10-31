@file:OptIn(ExperimentalMaterial3Api::class)

package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftInput
import com.sekharjal.shiftpay.calc.ShiftType
import com.sekharjal.shiftpay.data.ShiftRecord
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.toMoney
import com.sekharjal.shiftpay.ui.AmountRow
import com.sekharjal.shiftpay.ui.SectionCard
import com.sekharjal.shiftpay.ui.formatDateWithDay
import com.sekharjal.shiftpay.ui.formatDuration
import com.sekharjal.shiftpay.ui.formatMoney
import com.sekharjal.shiftpay.ui.formatTime
import com.sekharjal.shiftpay.ui.isDecimalInput
import com.sekharjal.shiftpay.ui.label
import com.sekharjal.shiftpay.ui.minutesToHoursText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun ShiftEditorScreen(
    snap: Snapshot,
    shiftId: Long,
    onSave: (ShiftRecord) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val settings = snap.settings
    val existing = remember(shiftId) { snap.records.firstOrNull { it.id == shiftId } }

    var date by remember {
        mutableStateOf(existing?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() } ?: LocalDate.now())
    }
    var start by remember {
        mutableStateOf(existing?.let { runCatching { LocalTime.parse(it.startTime) }.getOrNull() } ?: LocalTime.of(9, 0))
    }
    var end by remember {
        mutableStateOf(existing?.let { runCatching { LocalTime.parse(it.endTime) }.getOrNull() } ?: LocalTime.of(17, 0))
    }
    var breakText by remember { mutableStateOf(existing?.breakMinutes?.toString() ?: "0") }
    var breakPaid by remember { mutableStateOf(existing?.breakPaid ?: false) }
    var type by remember {
        mutableStateOf(existing?.let { runCatching { ShiftType.valueOf(it.shiftType) }.getOrNull() } ?: ShiftType.DAY)
    }
    var overtimeText by remember {
        mutableStateOf(existing?.let { minutesToHoursText(it.overtimeMinutes) } ?: "0")
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val breakMinutes = breakText.toIntOrNull() ?: 0
    val overtimeMinutes = overtimeText.toMoney().multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toInt()
    val input = ShiftInput(date, start, end, breakMinutes, breakPaid, type, overtimeMinutes)
    val hours = ShiftCalculator.hoursOf(input)
    val earnings = snap.shiftEarnings(input)
    val sameTime = start == end
    val overtimeTooLarge = overtimeMinutes > hours.workedMinutes

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (existing == null) "Add shift" else "Edit shift",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date: ${formatDateWithDay(date, settings.dateFormat)}")
        }

        Text("Shift type", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = {
                        type = option
                        // Handy presets when adding a new shift; times can still be changed.
                        if (existing == null) {
                            when (option) {
                                ShiftType.DAY -> { start = LocalTime.of(9, 0); end = LocalTime.of(17, 0) }
                                ShiftType.NIGHT -> { start = LocalTime.of(22, 0); end = LocalTime.of(6, 0) }
                                ShiftType.CUSTOM -> Unit
                            }
                        }
                    },
                    label = { Text(option.label()) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                Text("Start: ${formatTime(start, settings.use24Hour)}")
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                Text("End: ${formatTime(end, settings.use24Hour)}")
            }
        }
        if (sameTime) {
            Text(
                "Start and end time can't be the same.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = breakText,
            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) breakText = v },
            label = { Text("Break (minutes)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Break is paid", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = breakPaid, onCheckedChange = { breakPaid = it })
        }

        OutlinedTextField(
            value = overtimeText,
            onValueChange = { v -> if (isDecimalInput(v)) overtimeText = v },
            label = { Text("Overtime hours") },
            supportingText = { Text("Part of the time worked above, e.g. 1.5") },
            singleLine = true,
            isError = overtimeTooLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (overtimeTooLarge) {
            Text(
                "Overtime can't be longer than the time worked.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard {
            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            AmountRow("Time between start and end", formatDuration(hours.elapsedMinutes))
            AmountRow("Time worked (after unpaid break)", formatDuration(hours.workedMinutes))
            AmountRow("Regular", formatDuration(hours.regularMinutes))
            AmountRow("Overtime", formatDuration(hours.overtimeMinutes))
            if (earnings.gross.signum() > 0) {
                AmountRow(
                    "Estimated earnings",
                    formatMoney(earnings.gross, settings.currency),
                    emphasis = true,
                )
            }
        }

        Button(
            onClick = {
                onSave(
                    ShiftRecord(
                        id = existing?.id ?: 0L,
                        date = date.toString(),
                        startTime = start.toString(),
                        endTime = end.toString(),
                        breakMinutes = breakMinutes,
                        breakPaid = breakPaid,
                        shiftType = type.name,
                        overtimeMinutes = overtimeMinutes,
                    ),
                )
            },
            enabled = !sameTime && !overtimeTooLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save shift")
        }

        if (existing != null) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete shift", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showStartPicker) {
        ClockDialog(
            initial = start,
            is24Hour = settings.use24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { start = it; showStartPicker = false },
        )
    }
    if (showEndPicker) {
        ClockDialog(
            initial = end,
            is24Hour = settings.use24Hour,
            onDismiss = { showEndPicker = false },
            onConfirm = { end = it; showEndPicker = false },
        )
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this shift?") },
            text = { Text("This removes the shift from your records. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(existing.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ClockDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
