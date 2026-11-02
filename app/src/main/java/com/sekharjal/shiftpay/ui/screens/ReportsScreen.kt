package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.calc.EarningsResult
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.ui.AmountRow
import com.sekharjal.shiftpay.ui.HoursChartSection
import com.sekharjal.shiftpay.ui.LegendDot
import com.sekharjal.shiftpay.ui.ScreenTitle
import com.sekharjal.shiftpay.ui.SectionCard
import com.sekharjal.shiftpay.ui.SectionTitle
import com.sekharjal.shiftpay.ui.SplitBar
import com.sekharjal.shiftpay.ui.formatDate
import com.sekharjal.shiftpay.ui.formatDateWithDay
import com.sekharjal.shiftpay.ui.formatDuration
import com.sekharjal.shiftpay.ui.formatMoney
import com.sekharjal.shiftpay.ui.monthTitle
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun ReportsScreen(snap: Snapshot) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableIntStateOf(2) }
    val tabs = listOf("Daily", "Weekly", "Monthly")
    val settings = snap.settings
    val currency = settings.currency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(title = "Reports", subtitle = "Hours and earnings for any month")

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(monthTitle(month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }

        val inputs = snap.inputsIn(month)

        when (tab) {
            0 -> {
                val byDay = inputs.groupBy { it.date }.entries.sortedByDescending { it.key }
                if (byDay.isEmpty()) EmptyMonthNote()
                byDay.forEach { (date, dayShifts) ->
                    val result = snap.calculate(dayShifts, false)
                    PeriodRow(
                        title = formatDateWithDay(date, settings.dateFormat),
                        result = result,
                        currency = currency,
                    )
                }
                if (byDay.isNotEmpty()) PartialNote(snap)
            }

            1 -> {
                val byWeek = inputs
                    .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                    .entries
                    .sortedByDescending { it.key }
                if (byWeek.isEmpty()) EmptyMonthNote()
                byWeek.forEach { (weekStart, weekShifts) ->
                    val result = snap.calculate(weekShifts, false)
                    PeriodRow(
                        title = "Week of ${formatDate(weekStart, "d MMM")} – ${formatDate(weekStart.plusDays(6), "d MMM")}",
                        result = result,
                        currency = currency,
                    )
                }
                if (byWeek.isNotEmpty()) PartialNote(snap)
            }

            else -> {
                val result = snap.monthResult(month)
                SectionCard {
                    SectionTitle("Hours")
                    AmountRow("Total hours worked", formatDuration(result.workedMinutes), emphasis = true)
                    AmountRow("Regular hours", formatDuration(result.regularMinutes))
                    AmountRow("Overtime hours", formatDuration(result.overtimeMinutes))
                    AmountRow("Shifts logged", result.shiftCount.toString())
                }
                SectionCard {
                    SectionTitle("Earnings")
                    SplitBar(
                        parts = listOf(
                            result.regularPay.toFloat() to MaterialTheme.colorScheme.primary,
                            result.overtimePay.toFloat() to MaterialTheme.colorScheme.tertiary,
                            result.allowances.toFloat() to MaterialTheme.colorScheme.secondary,
                        ),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendDot(MaterialTheme.colorScheme.primary, "Regular")
                        LegendDot(MaterialTheme.colorScheme.tertiary, "Overtime")
                        LegendDot(MaterialTheme.colorScheme.secondary, "Allowances")
                    }
                    Column(Modifier.padding(top = 12.dp)) {
                        AmountRow(
                            if (snap.settings.payMode == PayMode.MONTHLY) "Base salary" else "Regular pay",
                            formatMoney(result.regularPay, currency),
                        )
                        AmountRow("Overtime pay", formatMoney(result.overtimePay, currency))
                        AmountRow("Allowances", formatMoney(result.allowances, currency))
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        AmountRow("Gross pay", formatMoney(result.gross, currency), emphasis = true)
                        AmountRow(
                            "Deductions",
                            "- " + formatMoney(result.deductions, currency),
                            amountColor = MaterialTheme.colorScheme.error,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        AmountRow("Take-home", formatMoney(result.net, currency), emphasis = true)
                    }
                }
                SectionCard {
                    SectionTitle("Hours per day")
                    HoursChartSection(snap.dayHours(month))
                }
            }
        }

        Text(
            "PDF and CSV export are planned for a later version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyMonthNote() {
    Text(
        "No shifts logged in this month.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PartialNote(snap: Snapshot) {
    if (snap.settings.payMode == PayMode.MONTHLY) {
        Text(
            "With a monthly salary, each day's pay is your salary shared across your " +
                "standard monthly hours. Monthly allowances and deductions appear in the Monthly tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeriodRow(title: String, result: EarningsResult, currency: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val overtime =
                    if (result.overtimeMinutes > 0) " · ${formatDuration(result.overtimeMinutes)} overtime" else ""
                Text(
                    "${result.shiftCount} shift(s) · ${formatDuration(result.workedMinutes)} worked$overtime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatMoney(result.gross, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
