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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.calc.Frequency
import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.scheduledElapsedMinutes
import com.sekharjal.shiftpay.data.scheduledEnd
import com.sekharjal.shiftpay.data.scheduledStart
import com.sekharjal.shiftpay.data.scheduledWorkedMinutes
import com.sekharjal.shiftpay.data.toMoney
import com.sekharjal.shiftpay.data.toRules
import com.sekharjal.shiftpay.ui.AmountRow
import com.sekharjal.shiftpay.ui.ChoiceRow
import com.sekharjal.shiftpay.ui.DecimalField
import com.sekharjal.shiftpay.ui.PickTimeDialog
import com.sekharjal.shiftpay.ui.ScreenTitle
import com.sekharjal.shiftpay.ui.SectionCard
import com.sekharjal.shiftpay.ui.SectionTitle
import com.sekharjal.shiftpay.ui.formatDateWithDay
import com.sekharjal.shiftpay.ui.formatDuration
import com.sekharjal.shiftpay.ui.formatMoney
import com.sekharjal.shiftpay.ui.formatTime
import com.sekharjal.shiftpay.ui.label
import com.sekharjal.shiftpay.ui.monthTitle
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun ProfileScreen(
    snap: Snapshot,
    onSaveSettings: (AppSettings) -> Unit,
    onAddAllowance: (String, String, Frequency) -> Unit,
    onDeleteAllowance: (Long) -> Unit,
    onAddDeduction: (String, String, Frequency) -> Unit,
    onDeleteDeduction: (Long) -> Unit,
    onAddHoliday: (LocalDate, String, Boolean) -> Unit,
    onSetHolidayPaid: (LocalDate, Boolean) -> Unit,
    onDeleteHoliday: (LocalDate) -> Unit,
) {
    val saved = snap.settings
    // The form restarts from the saved values only when a form setting changes, so adding a holiday
    // or clocking in never wipes what is being typed.
    val formKey = saved.copy(holidaysRaw = "", activeShiftStart = "")
    var name by remember(formKey) { mutableStateOf(saved.name) }
    var payMode by remember(formKey) { mutableStateOf(saved.payMode) }
    var baseSalary by remember(formKey) { mutableStateOf(saved.baseSalary) }
    var hourlyRate by remember(formKey) { mutableStateOf(saved.hourlyRate) }
    var standardHours by remember(formKey) { mutableStateOf(saved.standardHours) }
    var multiplier by remember(formKey) { mutableStateOf(saved.overtimeMultiplier) }
    var overtimeMode by remember(formKey) { mutableStateOf(saved.overtimeMode) }
    var shiftStart by remember(formKey) { mutableStateOf(saved.shiftStart) }
    var shiftEnd by remember(formKey) { mutableStateOf(saved.shiftEnd) }
    var breakText by remember(formKey) { mutableStateOf(saved.shiftBreakMinutes) }
    var breakPaid by remember(formKey) { mutableStateOf(saved.shiftBreakPaid) }
    var holidayMultiplied by remember(formKey) { mutableStateOf(saved.holidayWorkMultiplied) }
    var holidayMultiplierText by remember(formKey) { mutableStateOf(saved.holidayMultiplier) }
    var showHolidayDialog by remember { mutableStateOf(false) }

    var showAllowanceDialog by remember { mutableStateOf(false) }
    var showDeductionDialog by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val draft = saved.copy(
        name = name,
        payMode = payMode,
        baseSalary = baseSalary,
        hourlyRate = hourlyRate,
        standardHours = standardHours,
        overtimeMultiplier = multiplier,
        overtimeMode = overtimeMode,
        shiftStart = shiftStart,
        shiftEnd = shiftEnd,
        shiftBreakMinutes = breakText,
        shiftBreakPaid = breakPaid,
        holidayWorkMultiplied = holidayMultiplied,
        holidayMultiplier = holidayMultiplierText,
    )
    val dirty = draft != saved
    val month = remember { YearMonth.now() }
    // The estimate updates live as the fields change, before saving.
    val preview = remember(draft, snap) {
        Snapshot(draft, snap.records, snap.allowances, snap.deductions).monthResult(month)
    }
    val effectiveRate = ShiftCalculator.effectiveHourlyRate(draft.toRules())
    val currency = saved.currency
    val startTime = draft.scheduledStart()
    val endTime = draft.scheduledEnd()
    val sameShiftTimes = startTime == endTime

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(
            title = "Profile",
            subtitle = "Your pay and your shift. Nothing here assumes any country's labour law.",
        )

        SectionCard {
            SectionTitle("About you")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard {
            SectionTitle("How are you paid?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = payMode == mode,
                        onClick = { payMode = mode },
                        label = { Text(mode.label()) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (payMode == PayMode.MONTHLY) {
                DecimalField("Monthly base salary ($currency)", baseSalary, { baseSalary = it })
                Spacer(Modifier.height(8.dp))
                DecimalField(
                    "Standard working hours per month",
                    standardHours,
                    { standardHours = it },
                    supportingText = if (effectiveRate.signum() > 0) {
                        "Hourly equivalent: ${formatMoney(effectiveRate.setScale(2, java.math.RoundingMode.HALF_UP), currency)}"
                    } else {
                        "Used to turn your salary into an hourly rate for overtime"
                    },
                )
            } else {
                DecimalField("Hourly rate ($currency)", hourlyRate, { hourlyRate = it })
            }

            Spacer(Modifier.height(8.dp))
            DecimalField(
                "Overtime multiplier",
                multiplier,
                { multiplier = it },
                supportingText = "For example 1.5 means time-and-a-half",
            )

            if (payMode == PayMode.MONTHLY) {
                Spacer(Modifier.height(12.dp))
                Text("How is overtime paid?", style = MaterialTheme.typography.titleSmall)
                ChoiceRow(
                    selected = overtimeMode == OvertimeMode.FULL_RATE,
                    label = "Full overtime rate (paid on top of salary)",
                    onClick = { overtimeMode = OvertimeMode.FULL_RATE },
                )
                ChoiceRow(
                    selected = overtimeMode == OvertimeMode.PREMIUM_ONLY,
                    label = "Premium only (hours already in salary)",
                    onClick = { overtimeMode = OvertimeMode.PREMIUM_ONLY },
                )
            }
        }

        SectionCard {
            SectionTitle("Your shift")
            Text(
                "Your normal working hours. When you end a shift after these hours, " +
                    "ShiftPay asks whether the extra time is paid overtime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Start: ${formatTime(startTime, saved.use24Hour)}")
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("End: ${formatTime(endTime, saved.use24Hour)}")
                }
            }
            if (sameShiftTimes) {
                Text(
                    "Start and end time can't be the same.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = breakText,
                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) breakText = v },
                label = { Text("Break (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Break is paid", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = breakPaid, onCheckedChange = { breakPaid = it })
            }
            if (!sameShiftTimes) {
                Text(
                    "Scheduled: ${formatDuration(draft.scheduledElapsedMinutes())} " +
                        "(${formatDuration(draft.scheduledWorkedMinutes())} of paid work after the break)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionCard {
            SectionTitle("Holiday pay")
            Text(
                "A listed Paid holiday you don't work is paid as a normal day. " +
                    "Unpaid holidays earn nothing when not worked, and never get the extra below. " +
                    "If you work on a Paid holiday, your employer may pay:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                selected = !holidayMultiplied,
                label = "Normal pay, no extra",
                onClick = { holidayMultiplied = false },
            )
            ChoiceRow(
                selected = holidayMultiplied,
                label = "A higher rate (multiplier)",
                onClick = { holidayMultiplied = true },
            )
            if (holidayMultiplied) {
                Spacer(Modifier.height(8.dp))
                DecimalField(
                    "Holiday multiplier",
                    holidayMultiplierText,
                    { holidayMultiplierText = it },
                    supportingText = "For example 2 means double pay for the hours you work on a holiday",
                )
            }
        }

        Button(
            onClick = { onSaveSettings(draft.copy(name = draft.name.trim())) },
            enabled = dirty && !sameShiftTimes,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (dirty) "Save profile" else "Saved")
        }

        SectionCard {
            SectionTitle("Allowances")
            if (snap.allowances.isEmpty()) {
                Text(
                    "None yet. Add things like a night allowance or meal allowance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snap.allowances.forEach { item ->
                ItemRow(item.name, item.amount, item.frequency, currency) { onDeleteAllowance(item.id) }
            }
            TextButton(onClick = { showAllowanceDialog = true }) { Text("+ Add allowance") }
        }

        SectionCard {
            SectionTitle("Deductions")
            if (snap.deductions.isEmpty()) {
                Text(
                    "None yet. Add things like insurance or a provident fund amount.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snap.deductions.forEach { item ->
                ItemRow(item.name, item.amount, item.frequency, currency) { onDeleteDeduction(item.id) }
            }
            TextButton(onClick = { showDeductionDialog = true }) { Text("+ Add deduction") }
        }

        SectionCard {
            SectionTitle("Holidays")
            if (snap.holidays.isEmpty()) {
                Text(
                    "None yet. Add the holidays your employer gives, such as Diwali or Republic Day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snap.holidays.forEach { holiday ->
                HolidayRow(
                    name = holiday.name,
                    dateText = formatDateWithDay(holiday.date, saved.dateFormat),
                    paid = holiday.paid,
                    onPaidChange = { onSetHolidayPaid(holiday.date, it) },
                    onDelete = { onDeleteHoliday(holiday.date) },
                )
            }
            TextButton(onClick = { showHolidayDialog = true }) { Text("+ Add holiday") }
        }

        SectionCard {
            SectionTitle("Estimated take-home · ${monthTitle(month)}")
            AmountRow(
                if (payMode == PayMode.MONTHLY) "Base salary" else "Regular pay",
                formatMoney(preview.regularPay, currency),
            )
            AmountRow("Overtime pay", formatMoney(preview.overtimePay, currency))
            if (preview.holidayPay.signum() > 0) {
                AmountRow("Holiday extra pay", formatMoney(preview.holidayPay, currency))
            }
            AmountRow("Allowances", formatMoney(preview.allowances, currency))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AmountRow("Gross", formatMoney(preview.gross, currency), emphasis = true)
            AmountRow(
                "Deductions",
                "- " + formatMoney(preview.deductions, currency),
                amountColor = MaterialTheme.colorScheme.error,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AmountRow("Take-home", formatMoney(preview.net, currency), emphasis = true)
            Text(
                "Based on the ${preview.shiftCount} shift(s) you have logged this month. " +
                    if (payMode == PayMode.MONTHLY) {
                        "Your salary is earned as you work, and reaches the full amount after your standard monthly hours."
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showStartPicker) {
        PickTimeDialog(
            initial = startTime,
            is24Hour = saved.use24Hour,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked: LocalTime ->
                shiftStart = picked.toString()
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        PickTimeDialog(
            initial = endTime,
            is24Hour = saved.use24Hour,
            onDismiss = { showEndPicker = false },
            onConfirm = { picked: LocalTime ->
                shiftEnd = picked.toString()
                showEndPicker = false
            },
        )
    }

    if (showHolidayDialog) {
        AddHolidayDialog(
            dateFormat = saved.dateFormat,
            onDismiss = { showHolidayDialog = false },
            onAdd = { date, holidayName, paid ->
                onAddHoliday(date, holidayName, paid)
                showHolidayDialog = false
            },
        )
    }

    if (showAllowanceDialog) {
        AddItemDialog(
            title = "Add allowance",
            frequencies = listOf(Frequency.PER_SHIFT, Frequency.PER_NIGHT_SHIFT, Frequency.PER_MONTH),
            onDismiss = { showAllowanceDialog = false },
            onAdd = { itemName, amount, freq ->
                onAddAllowance(itemName, amount, freq)
                showAllowanceDialog = false
            },
        )
    }
    if (showDeductionDialog) {
        AddItemDialog(
            title = "Add deduction",
            frequencies = listOf(Frequency.PER_MONTH, Frequency.PER_SHIFT),
            onDismiss = { showDeductionDialog = false },
            onAdd = { itemName, amount, freq ->
                onAddDeduction(itemName, amount, freq)
                showDeductionDialog = false
            },
        )
    }
}

@Composable
private fun ItemRow(
    name: String,
    amount: String,
    frequency: String,
    currency: String,
    onDelete: () -> Unit,
) {
    val frequencyLabel = runCatching { Frequency.valueOf(frequency).label() }.getOrDefault(frequency)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${formatMoney(amount.toMoney(), currency)} · $frequencyLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove $name")
        }
    }
}

@Composable
private fun AddItemDialog(
    title: String,
    frequencies: List<Frequency>,
    onDismiss: () -> Unit,
    onAdd: (String, String, Frequency) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(frequencies.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                DecimalField("Amount", amount, { amount = it })
                Spacer(Modifier.height(8.dp))
                frequencies.forEach { option ->
                    ChoiceRow(
                        selected = frequency == option,
                        label = option.label(),
                        onClick = { frequency = option },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), amount, frequency) },
                enabled = name.isNotBlank() && amount.toMoney().signum() > 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
