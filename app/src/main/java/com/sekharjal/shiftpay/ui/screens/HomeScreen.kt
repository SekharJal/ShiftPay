package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.ui.AmountRow
import com.sekharjal.shiftpay.ui.ClockActions
import com.sekharjal.shiftpay.ui.ClockCard
import com.sekharjal.shiftpay.ui.HoursChartSection
import com.sekharjal.shiftpay.ui.ScreenTitle
import com.sekharjal.shiftpay.ui.SectionCard
import com.sekharjal.shiftpay.ui.SectionTitle
import com.sekharjal.shiftpay.ui.SplitBar
import com.sekharjal.shiftpay.ui.StatTile
import com.sekharjal.shiftpay.ui.formatDuration
import com.sekharjal.shiftpay.ui.formatMoney
import com.sekharjal.shiftpay.ui.monthTitle
import java.time.YearMonth

@Composable
fun HomeScreen(snap: Snapshot, clock: ClockActions, onSetupPay: () -> Unit) {
    val currency = snap.settings.currency
    val month = remember { YearMonth.now() }
    val result = remember(snap) { snap.monthResult(month) }
    val days = remember(snap) { snap.dayHours(month) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val greeting = snap.settings.name.trim()
            ScreenTitle(
                title = if (greeting.isEmpty()) "ShiftPay" else "Hi, $greeting",
                subtitle = "Your shift earnings at a glance",
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "${monthTitle(month).uppercase()} · MONTHLY OVERVIEW",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Estimated earnings",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatMoney(result.gross, currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Gross, including overtime and allowances",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Take-home after deductions: ${formatMoney(result.net, currency)}",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item { ClockCard(snap = snap, actions = clock) }

        if (!snap.payConfigured) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Set your salary or hourly rate in your profile to see real earnings.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(onClick = onSetupPay) { Text("Open Profile") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Regular hours", formatDuration(result.regularMinutes), Modifier.weight(1f))
                StatTile("Overtime", formatDuration(result.overtimeMinutes), Modifier.weight(1f))
            }
        }

        item {
            SectionCard {
                SectionTitle("Earnings breakdown")
                SplitBar(
                    parts = listOf(
                        result.regularPay.toFloat() to MaterialTheme.colorScheme.primary,
                        result.overtimePay.toFloat() to MaterialTheme.colorScheme.tertiary,
                        result.allowances.toFloat() to MaterialTheme.colorScheme.secondary,
                    ),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                AmountRow("Regular pay", formatMoney(result.regularPay, currency))
                AmountRow("Overtime", formatMoney(result.overtimePay, currency))
                if (result.holidayPay.signum() > 0) {
                    AmountRow("Holiday extra pay", formatMoney(result.holidayPay, currency))
                }
                AmountRow("Allowances", formatMoney(result.allowances, currency))
                AmountRow(
                    "Deductions",
                    "- " + formatMoney(result.deductions, currency),
                    amountColor = MaterialTheme.colorScheme.error,
                )
                AmountRow("Take-home", formatMoney(result.net, currency), emphasis = true)
                Text(
                    "${result.shiftCount} shift(s) logged this month" +
                        if (result.paidHolidayCount > 0) " · ${result.paidHolidayCount} paid holiday(s)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            SectionCard {
                SectionTitle("Hours worked this month")
                HoursChartSection(days)
            }
        }
    }
}
