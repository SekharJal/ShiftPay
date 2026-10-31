package com.sekharjal.shiftpay

import com.sekharjal.shiftpay.calc.Frequency
import com.sekharjal.shiftpay.calc.LineItem
import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.PayRules
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftInput
import com.sekharjal.shiftpay.calc.ShiftType
import org.junit.Assert.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class ShiftCalculatorTest {

    private fun rules(
        mode: PayMode = PayMode.HOURLY,
        base: String = "0",
        rate: String = "15",
        std: String = "160",
        mult: String = "1.5",
        otMode: OvertimeMode = OvertimeMode.FULL_RATE,
    ) = PayRules(mode, BigDecimal(base), BigDecimal(rate), BigDecimal(std), BigDecimal(mult), otMode, 2)

    private fun shift(
        day: Int,
        start: String,
        end: String,
        breakMinutes: Int = 0,
        paid: Boolean = false,
        ot: Int = 0,
        type: ShiftType = ShiftType.DAY,
    ) = ShiftInput(
        LocalDate.of(2026, 10, day), LocalTime.parse(start), LocalTime.parse(end),
        breakMinutes, paid, type, ot,
    )

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun overnight_shift_counts_eight_elapsed_hours() {
        assertEquals(480, ShiftCalculator.elapsedMinutes(LocalTime.of(22, 0), LocalTime.of(6, 0)))
    }

    @Test
    fun unpaid_break_is_deducted_and_paid_break_is_kept() {
        val unpaid = ShiftCalculator.hoursOf(shift(1, "09:00", "17:00", breakMinutes = 30, paid = false))
        val paid = ShiftCalculator.hoursOf(shift(1, "09:00", "17:00", breakMinutes = 30, paid = true))
        assertEquals(450, unpaid.workedMinutes)
        assertEquals(480, paid.workedMinutes)
    }

    @Test
    fun hourly_example_from_the_product_spec() {
        // $15/hour, 40 regular hours + 5 overtime hours at 1.5x = $600 + $112.50 = $712.50
        val shifts = (1..5).map { shift(it, "09:00", "17:00") } +
            shift(6, "10:00", "15:00", ot = 300)
        val result = ShiftCalculator.calculate(shifts, emptyList(), emptyList(), rules(), true)
        assertMoney("600.00", result.regularPay)
        assertMoney("112.50", result.overtimePay)
        assertMoney("712.50", result.gross)
    }

    @Test
    fun monthly_salary_with_full_rate_overtime() {
        // 20000 / 160h = 125 per hour; 12h x 125 x 1.5 = 2250
        val r = rules(PayMode.MONTHLY, base = "20000")
        val shifts = listOf(shift(1, "08:00", "20:00", ot = 720))
        val result = ShiftCalculator.calculate(shifts, emptyList(), emptyList(), r, true)
        assertMoney("20000.00", result.regularPay)
        assertMoney("2250.00", result.overtimePay)
        assertMoney("22250.00", result.gross)
    }

    @Test
    fun monthly_salary_with_premium_only_overtime() {
        // Only the 0.5 premium: 12h x 125 x 0.5 = 750
        val r = rules(PayMode.MONTHLY, base = "20000", otMode = OvertimeMode.PREMIUM_ONLY)
        val shifts = listOf(shift(1, "08:00", "20:00", ot = 720))
        val result = ShiftCalculator.calculate(shifts, emptyList(), emptyList(), r, true)
        assertMoney("750.00", result.overtimePay)
        assertMoney("20750.00", result.gross)
    }

    @Test
    fun monthly_salary_is_not_counted_in_a_weekly_result() {
        val r = rules(PayMode.MONTHLY, base = "20000")
        val result = ShiftCalculator.calculate(listOf(shift(1, "09:00", "17:00")), emptyList(), emptyList(), r, false)
        assertMoney("0.00", result.regularPay)
    }

    @Test
    fun allowances_and_deductions_follow_their_frequency() {
        val shifts = listOf(
            shift(1, "22:00", "06:00", type = ShiftType.NIGHT),
            shift(2, "22:00", "06:00", type = ShiftType.NIGHT),
            shift(3, "09:00", "17:00"),
        )
        val allowances = listOf(
            LineItem("Night", BigDecimal("100"), Frequency.PER_NIGHT_SHIFT),
            LineItem("Meal", BigDecimal("50"), Frequency.PER_SHIFT),
            LineItem("Travel", BigDecimal("1000"), Frequency.PER_MONTH),
        )
        val deductions = listOf(LineItem("Tax", BigDecimal("200"), Frequency.PER_MONTH))
        val month = ShiftCalculator.calculate(shifts, allowances, deductions, rules(), true)
        assertMoney("1350.00", month.allowances) // 200 + 150 + 1000
        assertMoney("200.00", month.deductions)
        val week = ShiftCalculator.calculate(shifts, allowances, deductions, rules(), false)
        assertMoney("350.00", week.allowances) // monthly items excluded
        assertMoney("0.00", week.deductions)
    }

    @Test
    fun money_is_rounded_once_to_the_currency_digits() {
        // 7 minutes at 10/hour = 1.1666... -> 1.17
        val result = ShiftCalculator.calculate(
            listOf(shift(1, "09:00", "09:07")), emptyList(), emptyList(), rules(rate = "10"), true,
        )
        assertMoney("1.17", result.regularPay)
    }
}
