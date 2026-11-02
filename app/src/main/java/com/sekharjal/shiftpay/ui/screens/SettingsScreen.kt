package com.sekharjal.shiftpay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.ThemeChoice
import com.sekharjal.shiftpay.ui.ChoiceRow
import com.sekharjal.shiftpay.ui.ScreenTitle
import com.sekharjal.shiftpay.ui.SectionCard
import com.sekharjal.shiftpay.ui.SectionTitle
import com.sekharjal.shiftpay.ui.currencyName
import com.sekharjal.shiftpay.ui.formatDate
import java.time.LocalDate

private val COMMON_CURRENCIES = listOf(
    "INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "AED", "SAR", "QAR", "KWD", "JPY", "CNY", "KRW",
    "PHP", "IDR", "MYR", "THB", "VND", "PKR", "BDT", "LKR", "NPR", "ZAR", "NGN", "KES", "EGP",
    "BRL", "MXN", "CHF", "SEK", "NOK", "DKK", "NZD", "TRY", "PLN",
)

private val DATE_FORMATS = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "d MMM yyyy")

@Composable
fun SettingsScreen(snap: Snapshot, onSaveSettings: (AppSettings) -> Unit) {
    val settings = snap.settings
    var showCurrencyDialog by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(title = "Settings")

        SectionCard {
            SectionTitle("Currency")
            Text(
                "ShiftPay never converts currencies. Pick the one you are paid in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { showCurrencyDialog = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${settings.currency} · ${currencyName(settings.currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = { showCurrencyDialog = true }) { Text("Change") }
            }
        }

        SectionCard {
            SectionTitle("Date format")
            DATE_FORMATS.forEach { pattern ->
                ChoiceRow(
                    selected = settings.dateFormat == pattern,
                    label = formatDate(today, pattern),
                    onClick = { onSaveSettings(settings.copy(dateFormat = pattern)) },
                )
            }
        }

        SectionCard {
            SectionTitle("Time format")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Use 24-hour time", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.use24Hour,
                    onCheckedChange = { onSaveSettings(settings.copy(use24Hour = it)) },
                )
            }
        }

        SectionCard {
            SectionTitle("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.theme == choice,
                        onClick = { onSaveSettings(settings.copy(theme = choice)) },
                        label = {
                            Text(
                                when (choice) {
                                    ThemeChoice.SYSTEM -> "System"
                                    ThemeChoice.LIGHT -> "Light"
                                    ThemeChoice.DARK -> "Dark"
                                },
                            )
                        },
                    )
                }
            }
        }

        SectionCard {
            SectionTitle("Privacy and data")
            Text(
                "ShiftPay works completely offline. There is no account, and your shifts and pay " +
                    "settings are stored only on this phone. The app does not have internet permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Backup and restore will arrive in a later version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "ShiftPay 0.3.0 (test build)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showCurrencyDialog) {
        val options = (listOf(settings.currency) + COMMON_CURRENCIES).distinct()
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Choose currency") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(options) { code ->
                        ChoiceRow(
                            selected = code == settings.currency,
                            label = "$code · ${currencyName(code)}",
                            onClick = {
                                onSaveSettings(settings.copy(currency = code))
                                showCurrencyDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") } },
        )
    }
}
