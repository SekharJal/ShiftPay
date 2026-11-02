package com.sekharjal.shiftpay

import com.sekharjal.shiftpay.calc.ShiftType
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.activeStart
import com.sekharjal.shiftpay.data.scheduledShiftType
import com.sekharjal.shiftpay.data.scheduledWorkedMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {

    @Test
    fun day_shift_with_unpaid_break() {
        val s = AppSettings(shiftStart = "09:00", shiftEnd = "17:00", shiftBreakMinutes = "30", shiftBreakPaid = false)
        assertEquals(450, s.scheduledWorkedMinutes())
        assertEquals(ShiftType.DAY, s.scheduledShiftType())
    }

    @Test
    fun paid_break_stays_in_scheduled_time() {
        val s = AppSettings(shiftStart = "09:00", shiftEnd = "17:00", shiftBreakMinutes = "30", shiftBreakPaid = true)
        assertEquals(480, s.scheduledWorkedMinutes())
    }

    @Test
    fun overnight_shift_is_night_and_eight_hours() {
        val s = AppSettings(shiftStart = "22:00", shiftEnd = "06:00")
        assertEquals(480, s.scheduledWorkedMinutes())
        assertEquals(ShiftType.NIGHT, s.scheduledShiftType())
    }

    @Test
    fun active_shift_start_is_read_back() {
        assertNull(AppSettings().activeStart())
        assertNotNull(AppSettings(activeShiftStart = "2026-11-02T09:03:12").activeStart())
        assertNotNull(AppSettings(activeShiftStart = "2026-11-02T09:03").activeStart())
    }

    @Test
    fun bad_saved_values_fall_back_safely() {
        val s = AppSettings(shiftStart = "oops", shiftEnd = "", shiftBreakMinutes = "x")
        assertEquals(480, s.scheduledWorkedMinutes()) // falls back to 09:00-17:00, no break
    }
}
