package com.sekharjal.shiftpay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.PayRules
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    // The worker's normal (scheduled) shift. Used by clock-in/clock-out.
    val shiftStart: String = "09:00",
    val shiftEnd: String = "17:00",
    val shiftBreakMinutes: String = "0",
    val shiftBreakPaid: Boolean = false,
    // Set while the worker is clocked in: the moment they started, as ISO text. Empty = clocked out.
    val activeShiftStart: String = "",
    // Working on a holiday: false = paid like a normal day, true = paid with the multiplier below.
    val holidayWorkMultiplied: Boolean = false,
    val holidayMultiplier: String = "2",
    // The worker's holiday list, stored as text lines "2026-11-12|Diwali". Edited only via editHolidays.
    val holidaysRaw: String = "",
)

data class Holiday(val date: LocalDate, val name: String)

fun parseHolidays(raw: String): List<Holiday> =
    raw.lineSequence()
        .mapNotNull { line ->
            val bar = line.indexOf('|')
            if (bar < 0) {
                null
            } else {
                runCatching { Holiday(LocalDate.parse(line.substring(0, bar)), line.substring(bar + 1)) }.getOrNull()
            }
        }
        .sortedBy { it.date }
        .toList()

fun encodeHolidays(list: List<Holiday>): String =
    list.joinToString("\n") { "${it.date}|${it.name.replace('|', ' ').replace('\n', ' ')}" }

fun AppSettings.holidays(): List<Holiday> = parseHolidays(holidaysRaw)

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
    holidayMultiplier = if (holidayWorkMultiplied) holidayMultiplier.toMoney().max(BigDecimal.ONE) else BigDecimal.ONE,
    holidayCreditMinutes = scheduledWorkedMinutes(),
)

// ---- The scheduled shift --------------------------------------------------

fun AppSettings.scheduledStart(): LocalTime =
    runCatching { LocalTime.parse(shiftStart) }.getOrDefault(LocalTime.of(9, 0))

fun AppSettings.scheduledEnd(): LocalTime =
    runCatching { LocalTime.parse(shiftEnd) }.getOrDefault(LocalTime.of(17, 0))

fun AppSettings.scheduledBreakMinutes(): Int = shiftBreakMinutes.toIntOrNull() ?: 0

/** Minutes between the scheduled start and end (an end earlier than the start means past midnight). */
fun AppSettings.scheduledElapsedMinutes(): Int =
    ShiftCalculator.elapsedMinutes(scheduledStart(), scheduledEnd())

/** Scheduled minutes of work: elapsed time minus an unpaid break. */
fun AppSettings.scheduledWorkedMinutes(): Int {
    val unpaidBreak = if (shiftBreakPaid) 0 else scheduledBreakMinutes()
    return (scheduledElapsedMinutes() - unpaidBreak).coerceAtLeast(0)
}

/** Night if the shift runs past midnight or starts late evening / very early morning. */
fun AppSettings.scheduledShiftType(): ShiftType {
    val start = scheduledStart()
    val end = scheduledEnd()
    val pastMidnight = end <= start
    return if (pastMidnight || start.hour >= 20 || start.hour < 5) ShiftType.NIGHT else ShiftType.DAY
}

/** The moment the worker clocked in, or null when clocked out. */
fun AppSettings.activeStart(): LocalDateTime? =
    runCatching { LocalDateTime.parse(activeShiftStart) }.getOrNull()

// ---------------------------------------------------------------------------

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
        val SHIFT_START = stringPreferencesKey("shift_start")
        val SHIFT_END = stringPreferencesKey("shift_end")
        val SHIFT_BREAK_MINUTES = stringPreferencesKey("shift_break_minutes")
        val SHIFT_BREAK_PAID = booleanPreferencesKey("shift_break_paid")
        val ACTIVE_SHIFT_START = stringPreferencesKey("active_shift_start")
        val HOLIDAY_WORK_MULTIPLIED = booleanPreferencesKey("holiday_work_multiplied")
        val HOLIDAY_MULTIPLIER = stringPreferencesKey("holiday_multiplier")
        val HOLIDAYS = stringPreferencesKey("holidays")
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
            shiftStart = p[Keys.SHIFT_START] ?: d.shiftStart,
            shiftEnd = p[Keys.SHIFT_END] ?: d.shiftEnd,
            shiftBreakMinutes = p[Keys.SHIFT_BREAK_MINUTES] ?: d.shiftBreakMinutes,
            shiftBreakPaid = p[Keys.SHIFT_BREAK_PAID] ?: d.shiftBreakPaid,
            activeShiftStart = p[Keys.ACTIVE_SHIFT_START] ?: d.activeShiftStart,
            holidayWorkMultiplied = p[Keys.HOLIDAY_WORK_MULTIPLIED] ?: d.holidayWorkMultiplied,
            holidayMultiplier = p[Keys.HOLIDAY_MULTIPLIER] ?: d.holidayMultiplier,
            holidaysRaw = p[Keys.HOLIDAYS] ?: d.holidaysRaw,
        )
    }

    /** Saves every setting EXCEPT the active clock-in, which has its own setter below. */
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
            p[Keys.SHIFT_START] = s.shiftStart
            p[Keys.SHIFT_END] = s.shiftEnd
            p[Keys.SHIFT_BREAK_MINUTES] = s.shiftBreakMinutes
            p[Keys.SHIFT_BREAK_PAID] = s.shiftBreakPaid
            p[Keys.HOLIDAY_WORK_MULTIPLIED] = s.holidayWorkMultiplied
            p[Keys.HOLIDAY_MULTIPLIER] = s.holidayMultiplier
        }
    }

    /** Changes the holiday list in one step, so two quick edits can never overwrite each other. */
    suspend fun editHolidays(transform: (List<Holiday>) -> List<Holiday>) {
        context.settingsDataStore.edit { p ->
            val current = parseHolidays(p[Keys.HOLIDAYS] ?: "")
            p[Keys.HOLIDAYS] = encodeHolidays(transform(current))
        }
    }

    /** Remembers (or clears, with "") the moment the worker clocked in. Survives closing the app. */
    suspend fun setActiveShift(isoDateTime: String) {
        context.settingsDataStore.edit { p -> p[Keys.ACTIVE_SHIFT_START] = isoDateTime }
    }
}
