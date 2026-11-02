package com.sekharjal.shiftpay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftInput
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.ShiftRecord
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.activeStart
import com.sekharjal.shiftpay.data.scheduledBreakMinutes
import com.sekharjal.shiftpay.data.scheduledEnd
import com.sekharjal.shiftpay.data.scheduledShiftType
import com.sekharjal.shiftpay.data.scheduledStart
import com.sekharjal.shiftpay.data.scheduledWorkedMinutes
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Everything the clock card can ask the app to do. */
data class ClockActions(
    val onClockIn: () -> Unit,
    val onChangeStart: (LocalDateTime) -> Unit,
    val onDiscard: () -> Unit,
    val onClockOut: (ShiftRecord) -> Unit,
)

/**
 * The Start shift / End shift button. Shows a "clocked out" card with a Start button, or,
 * while a shift is running, a live timer with an End button.
 */
@Composable
fun ClockCard(snap: Snapshot, actions: ClockActions, modifier: Modifier = Modifier) {
    val settings = snap.settings
    val started = settings.activeStart()
    if (started == null) {
        IdleCard(settings, actions.onClockIn, modifier)
    } else {
        ActiveCard(settings, started, actions, modifier)
    }
}

@Composable
private fun IdleCard(settings: AppSettings, onClockIn: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("READY TO WORK", style = MaterialTheme.typography.labelMedium)
            Text(
                "You're clocked out",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Your shift: ${formatTime(settings.scheduledStart(), settings.use24Hour)} – " +
                    formatTime(settings.scheduledEnd(), settings.use24Hour),
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onClockIn,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Start shift", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActiveCard(
    settings: AppSettings,
    started: LocalDateTime,
    actions: ClockActions,
    modifier: Modifier,
) {
    // Ticks every second while this card is on screen, so the timer runs live.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf<ShiftRecord?>(null) }
    var pendingOvertime by remember { mutableIntStateOf(0) }

    val elapsed = if (now.isBefore(started)) Duration.ZERO else Duration.between(started, now)
    val seconds = elapsed.seconds
    val timerText = String.format(
        Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60,
    )
    val tooShort = elapsed.toMinutes() < 1
    val tooLong = elapsed.toHours() >= 24

    val startedLabel =
        if (started.toLocalDate() == LocalDate.now()) {
            formatTime(started.toLocalTime(), settings.use24Hour)
        } else {
            formatDate(started.toLocalDate(), "d MMM") + ", " + formatTime(started.toLocalTime(), settings.use24Hour)
        }

    fun endShift() {
        val s = started.truncatedTo(ChronoUnit.MINUTES)
        val e = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val type = settings.scheduledShiftType()
        val breakMinutes = settings.scheduledBreakMinutes()
        val record = ShiftRecord(
            date = s.toLocalDate().toString(),
            startTime = s.toLocalTime().toString(),
            endTime = e.toLocalTime().toString(),
            breakMinutes = breakMinutes,
            breakPaid = settings.shiftBreakPaid,
            shiftType = type.name,
            overtimeMinutes = 0,
        )
        val worked = ShiftCalculator.hoursOf(
            ShiftInput(
                s.toLocalDate(), s.toLocalTime(), e.toLocalTime(),
                breakMinutes, settings.shiftBreakPaid, type, 0,
            ),
        ).workedMinutes
        val extra = worked - settings.scheduledWorkedMinutes()
        if (extra >= 1) {
            // Worked beyond the scheduled shift: ask whether it is paid overtime.
            pendingRecord = record
            pendingOvertime = extra
        } else {
            actions.onClockOut(record)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("SHIFT IN PROGRESS", style = MaterialTheme.typography.labelMedium)
            Text(
                timerText,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                "Started at $startedLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Your shift: ${formatTime(settings.scheduledStart(), settings.use24Hour)} – " +
                    formatTime(settings.scheduledEnd(), settings.use24Hour),
                style = MaterialTheme.typography.bodySmall,
            )
            if (tooLong) {
                Text(
                    "This shift has been running for over 24 hours. Change the start time, or discard it.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { endShift() },
                enabled = !tooShort && !tooLong,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("End shift", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { showStartPicker = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) { Text("Change start time") }
                TextButton(
                    onClick = { showDiscard = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) { Text("Discard") }
            }
        }
    }

    if (showStartPicker) {
        PickTimeDialog(
            initial = started.toLocalTime(),
            is24Hour = settings.use24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                var candidate = LocalDateTime.of(started.toLocalDate(), picked)
                // A start "in the future" can only mean the previous day.
                if (candidate.isAfter(LocalDateTime.now())) candidate = candidate.minusDays(1)
                showStartPicker = false
                actions.onChangeStart(candidate)
            },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard this shift?") },
            text = { Text("The running shift will be deleted without being saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    actions.onDiscard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep running") } },
        )
    }

    val record = pendingRecord
    if (record != null) {
        AlertDialog(
            onDismissRequest = { pendingRecord = null },
            title = { Text("Overtime?") },
            text = {
                val hourlyNote =
                    if (settings.payMode == PayMode.HOURLY) {
                        "\n\nIf not overtime, the extra time is paid at your normal hourly rate."
                    } else {
                        ""
                    }
                Text(
                    "You worked ${formatDuration(pendingOvertime)} beyond your scheduled shift. " +
                        "Will you be paid overtime for this extra time?$hourlyNote\n\n" +
                        "Tap outside this box to keep your shift running.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRecord = null
                    actions.onClockOut(record.copy(overtimeMinutes = pendingOvertime))
                }) { Text("Yes, paid overtime") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRecord = null
                    actions.onClockOut(record)
                }) { Text("No overtime pay") }
            },
        )
    }
}

/** A small clock dialog for choosing a time of day. Used for shift times throughout the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTimeDialog(
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
