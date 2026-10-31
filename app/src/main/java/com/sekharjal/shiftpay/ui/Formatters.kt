package com.sekharjal.shiftpay.ui

import com.sekharjal.shiftpay.calc.Frequency
import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.ShiftType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

fun formatMoney(amount: BigDecimal, currencyCode: String): String {
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
    try {
        nf.currency = Currency.getInstance(currencyCode)
    } catch (e: Exception) {
        // unknown currency code: keep the device default
    }
    return nf.format(amount)
}

/** 480 -> "8 h", 450 -> "7 h 30 m", 45 -> "45 m". */
fun formatDuration(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        m == 0L -> "$h h"
        h == 0L -> "$m m"
        else -> "$h h $m m"
    }
}

fun formatDuration(totalMinutes: Int): String = formatDuration(totalMinutes.toLong())

fun formatDate(date: LocalDate, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

fun formatDateWithDay(date: LocalDate, pattern: String): String =
    date.format(DateTimeFormatter.ofPattern("EEE, $pattern", Locale.getDefault()))

fun formatTime(time: LocalTime, use24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))

fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

/** 90 -> "1.5", 120 -> "2", 0 -> "0". Used to show overtime minutes as editable hours. */
fun minutesToHoursText(minutes: Int): String =
    if (minutes == 0) {
        "0"
    } else {
        BigDecimal(minutes).divide(BigDecimal(60), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }

fun Frequency.label(): String = when (this) {
    Frequency.PER_SHIFT -> "Per shift"
    Frequency.PER_NIGHT_SHIFT -> "Per night shift"
    Frequency.PER_MONTH -> "Per month"
}

fun ShiftType.label(): String = when (this) {
    ShiftType.DAY -> "Day"
    ShiftType.NIGHT -> "Night"
    ShiftType.CUSTOM -> "Custom"
}

fun PayMode.label(): String = when (this) {
    PayMode.MONTHLY -> "Monthly salary"
    PayMode.HOURLY -> "Hourly wage"
}

fun OvertimeMode.label(): String = when (this) {
    OvertimeMode.FULL_RATE -> "Full overtime rate"
    OvertimeMode.PREMIUM_ONLY -> "Premium only"
}

fun currencyName(code: String): String =
    try {
        Currency.getInstance(code).displayName
    } catch (e: Exception) {
        code
    }
