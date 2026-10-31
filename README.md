# ShiftPay

An offline-first Android app that helps shift workers calculate, record and understand their earnings:
regular hours, overtime, night allowances and deductions. No account, no server, no internet permission.

## Install the latest test build on your phone

1. On your Android phone, open the **Releases** page of this repository and tap **ShiftPay - latest test build**.
2. Download **ShiftPay.apk**.
3. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**, then tap **Install**.

Every time new code is pushed to `main`, GitHub rebuilds the app automatically (about 5-8 minutes) and replaces that release.
New builds install over the old one and keep your saved shifts.

## What is in version 0.1

- **Home** - estimated earnings for the month, regular vs overtime hours, breakdown, hours chart, quick "Add a shift".
- **Shifts** - add, edit, delete shifts. Overnight shifts, paid/unpaid breaks, day/night/custom type, manual overtime.
- **Calculator** - monthly-salary or hourly pay, overtime multiplier, allowances and deductions (per shift, per night shift, per month), live take-home estimate.
- **Reports** - daily, weekly and monthly views with month navigation.
- **Settings** - currency, date format, 12/24-hour time, light/dark theme.

## How the money maths works

All money uses `BigDecimal` (never floating point) and is rounded once, to the currency's own decimal places.
See `app/src/main/java/com/sekharjal/shiftpay/calc/ShiftCalculator.kt` and its unit tests.

- Overnight shift 22:00 -> 06:00 = 8 elapsed hours before breaks. Unpaid breaks are deducted; paid breaks are kept.
- Hourly: regular pay = regular hours x rate; overtime = OT hours x rate x multiplier.
- Monthly: gross = base salary + overtime + allowances. Overtime can be paid at the *full* rate or as a *premium only* (multiplier - 1).
- Overtime hours are entered by hand and count as part of the time worked on that shift.

## Tech

Kotlin, Jetpack Compose, Room (shifts, allowances, deductions), DataStore (settings), Compose Canvas (charts).
