package com.sekharjal.shiftpay

import com.sekharjal.shiftpay.calc.OvertimeMode
import com.sekharjal.shiftpay.calc.PayMode
import com.sekharjal.shiftpay.calc.PayRules
import com.sekharjal.shiftpay.calc.ShiftCalculator
import com.sekharjal.shiftpay.calc.ShiftInput
import com.sekharjal.shiftpay.calc.ShiftType
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.Holiday
import com.sekharjal.shiftpay.data.ShiftRecord
import com.sekharjal.shiftpay.data.Snapshot
import com.sekharjal.shiftpay.data.encodeHolidays
import com.sekharjal.shiftpay.data.parseHolidays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class HolidayTest {

    private val holiday = LocalDate.of(2026, 10, 20)

    private fun rules(
        mode: PayMode,
        holidayMultiplier: String = "1",
        base: String = "20000",
        rate: String = "15",
    ) = PayRules(
        mode, BigDecimal(base), BigDecimal(rate), BigDecimal("160"), BigDecimal("1.5"),
        OvertimeMode.FULL_RATE, 2, BigDecimal(holidayMultiplier), 480,
    )

    private fun shift(date: LocalDate, ot: Int = 0) =
        ShiftInput(date, LocalTime.of(9, 0), LocalTime.of(17, 0), 0, false, ShiftType.DAY, ot)

    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    private fun calc(
        r: PayRules,
        shifts: List<ShiftInput>,
        holidays: Set<LocalDate> = emptySet(),
        paidHolidays: Int = 0,
    ) = ShiftCalculator.calculate(shifts, emptyList(), emptyList(), r, true, holidays, paidHolidays)

    @Test
    fun hourly_holiday_not_worked_is_a_paid_day() {
        val result = calc(rules(PayMode.HOURLY), emptyList(), paidHolidays = 1)
        assertMoney("120.00", result.gross) // 8h x 15
    }

    @Test
    fun monthly_holiday_not_worked_counts_as_a_day_of_salary() {
        val result = calc(rules(PayMode.MONTHLY), emptyList(), paidHolidays = 1)
        assertMoney("1000.00", result.gross) // 20000 x 8 / 160
    }

    @Test
    fun monthly_salary_stays_capped_with_paid_holidays() {
        val shifts = (1..20).map { shift(LocalDate.of(2026, 10, it)) }
        val result = calc(rules(PayMode.MONTHLY), shifts, paidHolidays = 2)
        assertMoney("20000.00", result.regularPay)
    }

    @Test
    fun hourly_working_a_holiday_at_double_pay() {
        val result = calc(rules(PayMode.HOURLY, "2"), listOf(shift(holiday)), setOf(holiday))
        assertMoney("120.00", result.regularPay)
        assertMoney("120.00", result.holidayPay) // the extra (2 - 1) x 8h x 15
        assertMoney("240.00", result.gross) // double
    }

    @Test
    fun hourly_working_a_holiday_with_normal_pay_has_no_extra() {
        val result = calc(rules(PayMode.HOURLY, "1"), listOf(shift(holiday)), setOf(holiday))
        assertMoney("0.00", result.holidayPay)
        assertMoney("120.00", result.gross)
    }

    @Test
    fun monthly_working_a_holiday_at_double_pay_adds_the_premium() {
        val result = calc(rules(PayMode.MONTHLY, "2"), listOf(shift(holiday)), setOf(holiday))
        assertMoney("1000.00", result.regularPay) // the day, earned as normal
        assertMoney("1000.00", result.holidayPay) // 8h x 125 x (2 - 1)
        assertMoney("2000.00", result.gross)
    }

    @Test
    fun a_normal_day_is_unaffected_by_the_holiday_multiplier() {
        val other = LocalDate.of(2026, 10, 21)
        val result = calc(rules(PayMode.HOURLY, "2"), listOf(shift(other)), setOf(holiday))
        assertMoney("0.00", result.holidayPay)
        assertMoney("120.00", result.gross)
    }

    @Test
    fun holiday_overtime_uses_the_better_multiplier_not_both() {
        // 8h shift with 2h overtime on a 2x holiday, rate 15, overtime multiplier 1.5:
        // 6h regular 90 + holiday extra 90 + 2h overtime at the better 2x = 60  => 240 (= 8h x 15 x 2)
        val result = calc(rules(PayMode.HOURLY, "2"), listOf(shift(holiday, ot = 120)), setOf(holiday))
        assertMoney("90.00", result.regularPay)
        assertMoney("90.00", result.holidayPay)
        assertMoney("60.00", result.overtimePay)
        assertMoney("240.00", result.gross)
    }

    @Test
    fun holiday_list_round_trips_and_ignores_bad_lines() {
        val list = listOf(Holiday(LocalDate.of(2026, 11, 8), "Diwali | big"), Holiday(LocalDate.of(2026, 1, 26), "Republic Day"))
        val parsed = parseHolidays(encodeHolidays(list) + "\nnot a holiday\n2026-99-99|bad date")
        assertEquals(2, parsed.size)
        assertEquals(LocalDate.of(2026, 1, 26), parsed[0].date) // sorted by date
        assertEquals("Diwali   big", parsed[1].name) // the separator character is replaced
    }

    @Test
    fun only_past_unworked_holidays_in_the_month_are_credited() {
        val settings = AppSettings(
            holidaysRaw = "2020-01-01|New Year\n2020-01-26|Republic Day\n2099-01-01|Far future",
        )
        val worked = ShiftRecord(
            date = "2020-01-26", startTime = "09:00", endTime = "17:00",
            breakMinutes = 0, breakPaid = false, shiftType = "DAY", overtimeMinutes = 0,
        )
        val snap = Snapshot(settings, listOf(worked), emptyList(), emptyList())
        val credited = snap.creditedHolidays(YearMonth.of(2020, 1))
        assertEquals(listOf(LocalDate.of(2020, 1, 1)), credited.map { it.date }) // 26th was worked
        assertEquals(0, snap.creditedHolidays(YearMonth.of(2099, 1)).size) // not yet arrived
    }
}
