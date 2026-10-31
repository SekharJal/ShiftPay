package com.sekharjal.shiftpay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.PayRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Everything the user can configure. Money fields keep exactly what the user typed. */
data class AppSettings(
    val name: String = "",
    val currency: String = defaultCurrencyCode(),
    val payMode: PayMode = PayMode.MONTHLY,
    val baseSalary: String = "",
    val hourlyRate: String = "",
    val standardHours: String = "160",
    val overtimeMultiplier: String = "1.5",
    val overtimeMode: OvertimeMode = OvertimeMode.FULL_RATE,
    val dateFormat: String = "dd/MM/yyyy",
    val use24Hour: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
)

fun defaultCurrencyCode(): String =
    try {
        Currency.getInstance(Locale.getDefault()).currencyCode
    } catch (e: Exception) {
        "USD"
    }

fun currencyDigits(code: String): Int =
    try {
        Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)
    } catch (e: Exception) {
        2
    }

/** Turns what the user typed ("1,500.50", "", "abc") into a safe BigDecimal. */
fun String.toMoney(): BigDecimal =
    this.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO

fun AppSettings.toRules(): PayRules = PayRules(
    payMode = payMode,
    baseSalary = baseSalary.toMoney(),
    hourlyRate = hourlyRate.toMoney(),
    standardMonthlyHours = standardHours.toMoney(),
    overtimeMultiplier = overtimeMultiplier.toMoney(),
    overtimeMode = overtimeMode,
    currencyDigits = currencyDigits(currency),
)

private val Context.settingsDataStore by preferencesDataStore(name = "shiftpay_settings")

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() } ?: default

class SettingsStore(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val CURRENCY = stringPreferencesKey("currency")
        val PAY_MODE = stringPreferencesKey("pay_mode")
        val BASE_SALARY = stringPreferencesKey("base_salary")
        val HOURLY_RATE = stringPreferencesKey("hourly_rate")
        val STANDARD_HOURS = stringPreferencesKey("standard_hours")
        val OT_MULTIPLIER = stringPreferencesKey("ot_multiplier")
        val OT_MODE = stringPreferencesKey("ot_mode")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            name = p[Keys.NAME] ?: d.name,
            currency = p[Keys.CURRENCY] ?: d.currency,
            payMode = p[Keys.PAY_MODE].toEnum(d.payMode),
            baseSalary = p[Keys.BASE_SALARY] ?: d.baseSalary,
            hourlyRate = p[Keys.HOURLY_RATE] ?: d.hourlyRate,
            standardHours = p[Keys.STANDARD_HOURS] ?: d.standardHours,
            overtimeMultiplier = p[Keys.OT_MULTIPLIER] ?: d.overtimeMultiplier,
            overtimeMode = p[Keys.OT_MODE].toEnum(d.overtimeMode),
            dateFormat = p[Keys.DATE_FORMAT] ?: d.dateFormat,
            use24Hour = p[Keys.USE_24_HOUR] ?: d.use24Hour,
            theme = p[Keys.THEME].toEnum(d.theme),
        )
    }

    suspend fun save(s: AppSettings) {
        context.settingsDataStore.edit { p ->
            p[Keys.NAME] = s.name
            p[Keys.CURRENCY] = s.currency
            p[Keys.PAY_MODE] = s.payMode.name
            p[Keys.BASE_SALARY] = s.baseSalary
            p[Keys.HOURLY_RATE] = s.hourlyRate
            p[Keys.STANDARD_HOURS] = s.standardHours
            p[Keys.OT_MULTIPLIER] = s.overtimeMultiplier
            p[Keys.OT_MODE] = s.overtimeMode.name
            p[Keys.DATE_FORMAT] = s.dateFormat
            p[Keys.USE_24_HOUR] = s.use24Hour
            p[Keys.THEME] = s.theme.name
        }
    }
}
