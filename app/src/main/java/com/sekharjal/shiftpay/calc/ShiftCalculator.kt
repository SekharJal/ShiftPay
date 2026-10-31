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
) {
    val workedMinutes: Long get() = regularMinutes + overtimeMinutes
    val gross: BigDecimal get() = regularPay + overtimePay + allowances
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
     * @param includeMonthlyItems true for a whole-month result: includes the fixed monthly
     *   salary and "per month" allowances/deductions. False for a week or a single day.
     */
    fun calculate(
        shifts: List<ShiftInput>,
        allowances: List<LineItem>,
        deductions: List<LineItem>,
        rules: PayRules,
        includeMonthlyItems: Boolean,
    ): EarningsResult {
        var regularMinutes = 0L
        var overtimeMinutes = 0L
        var nightShifts = 0
        for (shift in shifts) {
            val h = hoursOf(shift)
            regularMinutes += h.regularMinutes
            overtimeMinutes += h.overtimeMinutes
            if (shift.type == ShiftType.NIGHT) nightShifts++
        }

        val digits = rules.currencyDigits
        val rate = effectiveHourlyRate(rules)

        val regularPay = when (rules.payMode) {
            PayMode.HOURLY -> money(minutesToHours(regularMinutes) * rules.hourlyRate, digits)
            PayMode.MONTHLY ->
                if (includeMonthlyItems) money(rules.baseSalary, digits) else zero(digits)
        }

        val multiplier =
            if (rules.payMode == PayMode.MONTHLY && rules.overtimeMode == OvertimeMode.PREMIUM_ONLY) {
                (rules.overtimeMultiplier - BigDecimal.ONE).max(BigDecimal.ZERO)
            } else {
                rules.overtimeMultiplier
            }
        val overtimePay = money(minutesToHours(overtimeMinutes) * rate * multiplier, digits)

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
