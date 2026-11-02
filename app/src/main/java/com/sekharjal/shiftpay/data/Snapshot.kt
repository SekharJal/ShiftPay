package com.sekharjal.shiftpay.data

import com.sekharjal.shiftpay.calc.EarningsResult
import com.sekharjal.shiftpay.calc.Frequency
import com.sekharjal.shiftpay.calc.LineItem
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.PayRules
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftInput
import com.sekharjal.shiftpay.calc.ShiftType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

fun ShiftRecord.toInput(): ShiftInput? = runCatching {
    ShiftInput(
        date = LocalDate.parse(date),
        start = LocalTime.parse(startTime),
        end = LocalTime.parse(endTime),
        breakMinutes = breakMinutes,
        breakPaid = breakPaid,
        type = runCatching { ShiftType.valueOf(shiftType) }.getOrDefault(ShiftType.DAY),
        overtimeMinutes = overtimeMinutes,
    )
}.getOrNull()

fun AllowanceItem.toLine(): LineItem? = runCatching {
    LineItem(name, amount.toMoney(), Frequency.valueOf(frequency))
}.getOrNull()

fun DeductionItem.toLine(): LineItem? = runCatching {
    LineItem(name, amount.toMoney(), Frequency.valueOf(frequency))
}.getOrNull()

data class ShiftEntry(val record: ShiftRecord, val input: ShiftInput)

data class DayHours(val day: Int, val regularHours: Float, val overtimeHours: Float)

/**
 * Everything the screens need in one immutable bundle: the settings and the saved
 * data, plus helpers that run the calculation engine over them.
 */
class Snapshot(
    val settings: AppSettings,
    val records: List<ShiftRecord>,
    val allowances: List<AllowanceItem>,
    val deductions: List<DeductionItem>,
) {
    val rules: PayRules = settings.toRules()
    private val allowanceLines: List<LineItem> = allowances.mapNotNull { it.toLine() }
    private val deductionLines: List<LineItem> = deductions.mapNotNull { it.toLine() }
    private val today: LocalDate = LocalDate.now()

    /** The holidays the worker has listed, earliest first. */
    val holidays: List<Holiday> = settings.holidays()
    val holidayDates: Set<LocalDate> = holidays.map { it.date }.toSet()

    /** Shifts that could be read, newest first (the database already sorts them). */
    val entries: List<ShiftEntry> = records.mapNotNull { r -> r.toInput()?.let { ShiftEntry(r, it) } }

    /** False until the user has entered a salary or hourly rate. */
    val payConfigured: Boolean = when (rules.payMode) {
        PayMode.MONTHLY -> rules.baseSalary.signum() > 0
        PayMode.HOURLY -> rules.hourlyRate.signum() > 0
    }

    fun inputsIn(month: YearMonth): List<ShiftInput> =
        entries.map { it.input }.filter { YearMonth.from(it.date) == month }

    fun calculate(
        inputs: List<ShiftInput>,
        includeMonthlyItems: Boolean,
        paidHolidayCount: Int = 0,
    ): EarningsResult = ShiftCalculator.calculate(
        inputs, allowanceLines, deductionLines, rules, includeMonthlyItems, holidayDates, paidHolidayCount,
    )

    /**
     * Holidays in this month that have already passed and were not worked. Each is paid as a
     * normal day. (A holiday later in the month is not counted until it arrives.)
     */
    fun creditedHolidays(month: YearMonth): List<Holiday> {
        val worked = inputsIn(month).map { it.date }.toSet()
        return holidays.filter {
            YearMonth.from(it.date) == month && !it.date.isAfter(today) && it.date !in worked
        }
    }

    fun monthResult(month: YearMonth): EarningsResult =
        calculate(inputsIn(month), true, creditedHolidays(month).size)

    fun shiftEarnings(input: ShiftInput): EarningsResult = calculate(listOf(input), false)

    fun dayHours(month: YearMonth): List<DayHours> {
        val inputs = inputsIn(month)
        return (1..month.lengthOfMonth()).map { day ->
            var regular = 0
            var overtime = 0
            for (input in inputs) {
                if (input.date.dayOfMonth == day) {
                    val h = ShiftCalculator.hoursOf(input)
                    regular += h.regularMinutes
                    overtime += h.overtimeMinutes
                }
            }
            DayHours(day, regular / 60f, overtime / 60f)
        }
    }
}
