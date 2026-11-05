package com.sekharjal.shiftpay.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

// ---------------------------------------------------------------------------
// The salary calculation engine.
//
// This file is plain Kotlin: no Android code, no screens. All money is
// BigDecimal (never Float/Double) and is rounded only when a final amount
// is produced, using the currency's own number of decimal places.
// ---------------------------------------------------------------------------

enum class PayMode { MONTHLY, HOURLY }

/** For monthly-salary workers: how overtime is paid. */
enum class OvertimeMode {
    /** Overtime hours are paid separately at the full overtime rate (rate x multiplier). */
    FULL_RATE,

    /** Overtime hours are already covered by salary; only the extra premium (multiplier - 1) is paid. */
    PREMIUM_ONLY,
}

enum class Frequency { PER_SHIFT, PER_NIGHT_SHIFT, PER_MONTH }

enum class ShiftType { DAY, NIGHT, CUSTOM }

data class PayRules(
    val payMode: PayMode,
    val baseSalary: BigDecimal,
    val hourlyRate: BigDecimal,
    val standardMonthlyHours: BigDecimal,
    val overtimeMultiplier: BigDecimal,
    val overtimeMode: OvertimeMode,
    val currencyDigits: Int,
    /** Pay multiplier for hours worked on a holiday. 1 (or less) means holidays are paid like any day. */
    val holidayMultiplier: BigDecimal = BigDecimal.ONE,
    /** Minutes of paid work credited for each listed holiday the worker did not work. */
    val holidayCreditMinutes: Int = 0,
)

data class ShiftInput(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val breakMinutes: Int,
    val breakPaid: Boolean,
    val type: ShiftType,
    /** Overtime entered manually. It is part of the worked time, not extra on top of it. */
    val overtimeMinutes: Int,
)

/** An allowance or a deduction. */
data class LineItem(
    val name: String,
    val amount: BigDecimal,
    val frequency: Frequency,
)

data class ShiftHours(
    val elapsedMinutes: Int,
    val workedMinutes: Int,
    val overtimeMinutes: Int,
) {
    val regularMinutes: Int get() = workedMinutes - overtimeMinutes
}

data class EarningsResult(
    val shiftCount: Int,
    val regularMinutes: Long,
    val overtimeMinutes: Long,
    val regularPay: BigDecimal,
    val overtimePay: BigDecimal,
    val allowances: BigDecimal,
    val deductions: BigDecimal,
    /** Extra pay for working on holidays (the part above normal pay). */
    val holidayPay: BigDecimal = BigDecimal.ZERO,
    /** Holidays that were not worked and are paid as a normal day. Already included in regularPay. */
    val paidHolidayCount: Int = 0,
) {
    val workedMinutes: Long get() = regularMinutes + overtimeMinutes
    val gross: BigDecimal get() = regularPay + overtimePay + holidayPay + allowances
    val net: BigDecimal get() = gross - deductions
}

object ShiftCalculator {
    private val MC = MathContext(20, RoundingMode.HALF_UP)
    private val SIXTY = BigDecimal(60)

    /**
     * Minutes between start and end. If the end is earlier than the start the shift
     * runs past midnight, so 22:00 -> 06:00 is 480 minutes (eight elapsed hours).
     */
    fun elapsedMinutes(start: LocalTime, end: LocalTime): Int {
        val s = start.hour * 60 + start.minute
        val e = end.hour * 60 + end.minute
        return if (e >= s) e - s else e + 24 * 60 - s
    }

    fun hoursOf(shift: ShiftInput): ShiftHours {
        val elapsed = elapsedMinutes(shift.start, shift.end)
        // Unpaid breaks come off the worked time; paid breaks stay in.
        val unpaidBreak = if (shift.breakPaid) 0 else shift.breakMinutes
        val worked = (elapsed - unpaidBreak).coerceAtLeast(0)
        val overtime = shift.overtimeMinutes.coerceIn(0, worked)
        return ShiftHours(elapsed, worked, overtime)
    }

    fun minutesToHours(minutes: Long): BigDecimal = BigDecimal(minutes).divide(SIXTY, MC)

    /** The hourly rate used for overtime. For monthly workers it is salary / standard hours. */
    fun effectiveHourlyRate(rules: PayRules): BigDecimal = when (rules.payMode) {
        PayMode.HOURLY -> rules.hourlyRate
        PayMode.MONTHLY ->
            if (rules.standardMonthlyHours.signum() <= 0) BigDecimal.ZERO
            else rules.baseSalary.divide(rules.standardMonthlyHours, MC)
    }

    /**
     * @param includeMonthlyItems true for a whole-month result: includes the "per month"
     *   allowances/deductions. False for a week or a single day. (A monthly salary is
     *   always earned in proportion to the hours worked, so it needs no such flag.)
     * @param holidayDates every PAID holiday the worker has listed (unpaid holidays are left out by
     *   the caller). A shift on one of these dates is paid with the holiday multiplier (if the
     *   worker chose one).
     * @param paidHolidayCount listed holidays in this period that were NOT worked. Each one is
     *   paid as a normal day, i.e. the worker's scheduled hours are credited.
     */
    fun calculate(
        shifts: List<ShiftInput>,
        allowances: List<LineItem>,
        deductions: List<LineItem>,
        rules: PayRules,
        includeMonthlyItems: Boolean,
        holidayDates: Set<LocalDate> = emptySet(),
        paidHolidayCount: Int = 0,
    ): EarningsResult {
        var regularMinutes = 0L
        var overtimeMinutes = 0L
        var holidayRegularMinutes = 0L
        var holidayOvertimeMinutes = 0L
        var nightShifts = 0
        for (shift in shifts) {
            val h = hoursOf(shift)
            regularMinutes += h.regularMinutes
            overtimeMinutes += h.overtimeMinutes
            if (shift.type == ShiftType.NIGHT) nightShifts++
            if (shift.date in holidayDates) {
                holidayRegularMinutes += h.regularMinutes
                holidayOvertimeMinutes += h.overtimeMinutes
            }
        }

        val digits = rules.currencyDigits
        val rate = effectiveHourlyRate(rules)

        // A holiday that was not worked is a normal paid day: credit the scheduled hours.
        val creditMinutes = paidHolidayCount.toLong() * rules.holidayCreditMinutes
        val payableRegularMinutes = regularMinutes + creditMinutes

        val regularPay = when (rules.payMode) {
            PayMode.HOURLY -> money(minutesToHours(payableRegularMinutes) * rules.hourlyRate, digits)
            // A monthly salary is earned as the worker works: salary x (regular hours / standard
            // monthly hours), never more than the full salary. No shifts means nothing earned yet.
            PayMode.MONTHLY -> {
                val standardMinutes = rules.standardMonthlyHours * SIXTY
                if (standardMinutes.signum() <= 0) {
                    zero(digits)
                } else {
                    val earned = (rules.baseSalary * BigDecimal(payableRegularMinutes)).divide(standardMinutes, MC)
                    money(earned.min(rules.baseSalary), digits)
                }
            }
        }

        // Working on a holiday: the hours are already inside regularPay at the normal rate, so the
        // extra for a "2x" holiday is the part above normal, (multiplier - 1) x rate.
        val holidayMultiplier = rules.holidayMultiplier.max(BigDecimal.ONE)
        val holidayExtra = holidayMultiplier - BigDecimal.ONE
        val holidayPay = money(minutesToHours(holidayRegularMinutes) * rate * holidayExtra, digits)

        // Overtime. On a holiday the better of the two multipliers is used, not both stacked.
        val premiumOnly = rules.payMode == PayMode.MONTHLY && rules.overtimeMode == OvertimeMode.PREMIUM_ONLY
        fun overtimeFactor(multiplier: BigDecimal): BigDecimal =
            if (premiumOnly) (multiplier - BigDecimal.ONE).max(BigDecimal.ZERO) else multiplier

        val normalOvertimeMinutes = overtimeMinutes - holidayOvertimeMinutes
        val holidayOvertimeMultiplier =
            if (holidayMultiplier > BigDecimal.ONE) rules.overtimeMultiplier.max(holidayMultiplier)
            else rules.overtimeMultiplier
        val overtimePay = money(
            minutesToHours(normalOvertimeMinutes) * rate * overtimeFactor(rules.overtimeMultiplier) +
                minutesToHours(holidayOvertimeMinutes) * rate * overtimeFactor(holidayOvertimeMultiplier),
            digits,
        )

        val allowanceTotal = itemsTotal(allowances, shifts.size, nightShifts, includeMonthlyItems, digits)
        val deductionTotal = itemsTotal(deductions, shifts.size, nightShifts, includeMonthlyItems, digits)

        return EarningsResult(
            shiftCount = shifts.size,
            regularMinutes = regularMinutes,
            overtimeMinutes = overtimeMinutes,
            regularPay = regularPay,
            overtimePay = overtimePay,
            allowances = allowanceTotal,
            deductions = deductionTotal,
            holidayPay = holidayPay,
            paidHolidayCount = paidHolidayCount,
        )
    }

    private fun itemsTotal(
        items: List<LineItem>,
        shiftCount: Int,
        nightShiftCount: Int,
        includeMonthlyItems: Boolean,
        digits: Int,
    ): BigDecimal {
        var total = zero(digits)
        for (item in items) {
            val units = when (item.frequency) {
                Frequency.PER_SHIFT -> shiftCount
                Frequency.PER_NIGHT_SHIFT -> nightShiftCount
                Frequency.PER_MONTH -> if (includeMonthlyItems) 1 else 0
            }
            total += money(item.amount * BigDecimal(units), digits)
        }
        return total
    }

    private fun money(value: BigDecimal, digits: Int): BigDecimal =
        value.setScale(digits, RoundingMode.HALF_UP)

    private fun zero(digits: Int): BigDecimal = BigDecimal.ZERO.setScale(digits)
}
