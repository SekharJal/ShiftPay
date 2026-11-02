# ShiftPay

An offline-first Android app that helps shift workers calculate, record and understand their earnings:
regular hours, overtime, night allowances and deductions. No account, no server, no internet permission.

## Install the latest test build on your phone

1. On your Android phone, open the **Releases** page of this repository and tap **ShiftPay - latest test build**.
2. Download **ShiftPay.apk**.
3. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**, then tap **Install**.

Every time new code is pushed to `main`, GitHub rebuilds the app automatically (about 4-8 minutes) and replaces that release.
New builds install over the old one and keep your saved shifts.

## What is in version 0.2

The bottom bar has five icon tabs:

- **Home** - estimated earnings for the month, the Start/End shift button, regular vs overtime hours, breakdown, hours chart.
- **Shifts** - **Start shift / End shift** (clock-in / clock-out) with a live timer. The exact time is recorded.
  If you finish after your scheduled hours, the app asks whether the extra time is paid overtime.
  Past shifts can be edited, deleted, or added manually.
- **Reports** - daily, weekly and monthly views with month navigation.
- **Profile** - your name, how you are paid (monthly or hourly), overtime rules, **your shift timing and break**,
  allowances, deductions and a live take-home estimate.
- **Settings** - currency, date format, 12/24-hour time, light/dark theme.

A running shift survives closing the app or restarting the phone. If you forgot to clock in, use **Change start time**.

## Holidays

Add your own holidays in **Profile -> Holidays** (works for any country; nothing is pre-filled).

- A listed holiday you **don't work** is paid as a normal day: your scheduled hours are credited
  (so a monthly salary is not reduced). It counts once the date has arrived.
- If you **work** on a holiday, choose in **Profile -> Holiday pay**: normal pay, or a higher rate (for example 2x).
  The extra is the part above normal pay. Overtime hours on a holiday use the better of the overtime and holiday multipliers, never both stacked.

## How the money maths works

All money uses `BigDecimal` (never floating point) and is rounded once, to the currency's own decimal places.
See `app/src/main/java/com/sekharjal/shiftpay/calc/ShiftCalculator.kt` and its unit tests.

- Overnight shift 22:00 -> 06:00 = 8 elapsed hours before breaks. Unpaid breaks are deducted; paid breaks are kept.
- Hourly: regular pay = regular hours x rate; overtime = OT hours x rate x multiplier.
- Monthly: the salary is earned as you work (salary x regular hours worked / standard monthly hours, capped at the full salary),
  so a month with no shifts earns nothing yet. Gross = earned salary + overtime + allowances.
  Overtime can be paid at the *full* rate or as a *premium only* (multiplier - 1).
- Overtime hours count as part of the time worked on that shift. Extra time marked "no overtime pay" is treated as regular time
  (for hourly workers it is paid at the normal rate).

## Tech

Kotlin, Jetpack Compose, Room (shifts, allowances, deductions), DataStore (settings and the running shift),
Compose Canvas (charts). Font: Plus Jakarta Sans (SIL Open Font License, see `licenses/`).
