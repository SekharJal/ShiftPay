package com.sekharjal.shiftpay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sekharjal.shiftpay.calc.Frequency
import com.sekharjal.shiftpay.data.AllowanceItem
import com.sekharjal.shiftpay.data.AppDatabase
import com.sekharjal.shiftpay.data.AppSettings
import com.sekharjal.shiftpay.data.DeductionItem
import com.sekharjal.shiftpay.data.SettingsStore
import com.sekharjal.shiftpay.data.ShiftRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the app's data and is the only place that writes to the database/settings. */
class ShiftPayViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val store = SettingsStore(application)

    /** null until the saved settings have loaded (a split second at start-up). */
    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val shifts: StateFlow<List<ShiftRecord>> =
        db.shiftDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allowances: StateFlow<List<AllowanceItem>> =
        db.allowanceDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val deductions: StateFlow<List<DeductionItem>> =
        db.deductionDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch { store.save(settings) }
    }

    fun saveShift(record: ShiftRecord) {
        viewModelScope.launch { db.shiftDao().upsert(record) }
    }

    fun deleteShift(id: Long) {
        viewModelScope.launch { db.shiftDao().deleteById(id) }
    }

    fun addAllowance(name: String, amount: String, frequency: Frequency) {
        viewModelScope.launch {
            db.allowanceDao().insert(AllowanceItem(name = name, amount = amount, frequency = frequency.name))
        }
    }

    fun deleteAllowance(id: Long) {
        viewModelScope.launch { db.allowanceDao().deleteById(id) }
    }

    fun addDeduction(name: String, amount: String, frequency: Frequency) {
        viewModelScope.launch {
            db.deductionDao().insert(DeductionItem(name = name, amount = amount, frequency = frequency.name))
        }
    }

    fun deleteDeduction(id: Long) {
        viewModelScope.launch { db.deductionDao().deleteById(id) }
    }
}
