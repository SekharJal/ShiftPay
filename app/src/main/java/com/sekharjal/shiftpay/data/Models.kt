package com.sekharjal.shiftpay.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// The on-phone database (Room). Dates/times are stored as ISO text
// ("2026-10-31", "22:00") and money as text so nothing is ever a float.
// ---------------------------------------------------------------------------

@Entity(tableName = "shift_records")
data class ShiftRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startTime: String,
    val endTime: String,
    val breakMinutes: Int,
    val breakPaid: Boolean,
    val shiftType: String,
    val overtimeMinutes: Int,
)

@Entity(tableName = "allowances")
data class AllowanceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String,
    val frequency: String,
)

@Entity(tableName = "deductions")
data class DeductionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String,
    val frequency: String,
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shift_records ORDER BY date DESC, startTime DESC")
    fun observeAll(): Flow<List<ShiftRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftRecord): Long

    @Query("DELETE FROM shift_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AllowanceDao {
    @Query("SELECT * FROM allowances ORDER BY id")
    fun observeAll(): Flow<List<AllowanceItem>>

    @Insert
    suspend fun insert(item: AllowanceItem): Long

    @Query("DELETE FROM allowances WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DeductionDao {
    @Query("SELECT * FROM deductions ORDER BY id")
    fun observeAll(): Flow<List<DeductionItem>>

    @Insert
    suspend fun insert(item: DeductionItem): Long

    @Query("DELETE FROM deductions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [ShiftRecord::class, AllowanceItem::class, DeductionItem::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun allowanceDao(): AllowanceDao
    abstract fun deductionDao(): DeductionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shiftpay.db",
                ).build().also { instance = it }
            }
    }
}
