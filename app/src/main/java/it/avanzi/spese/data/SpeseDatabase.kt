package it.avanzi.spese.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import it.avanzi.spese.domain.ExpenseStatus
import it.avanzi.spese.domain.Frequency
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val monthlyLimitCents: Long? = null,
    val sortOrder: Int,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
    )],
    indices = [Index("categoryId"), Index("date"), Index(value = ["recurringId", "date"], unique = true)],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val categoryId: Long,
    val description: String? = null,
    val date: LocalDate,
    val time: LocalTime? = null,
    val recurringId: Long? = null,
    val status: ExpenseStatus = ExpenseStatus.CONFIRMED,
    val createdAt: Instant,
)

@Entity(
    tableName = "recurrings",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
    )],
    indices = [Index("categoryId")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val categoryId: Long,
    val iconKey: String,
    val frequency: Frequency,
    val dayOfMonth: Int? = null,
    val dayOfWeek: DayOfWeek? = null,
    val monthOfYear: Int? = null,
    val anchorDate: LocalDate,
    val isVariable: Boolean = false,
    val autoRecord: Boolean = true,
    val active: Boolean = true,
    val lastGeneratedDate: LocalDate? = null,
)

class DateConverters {
    @TypeConverter fun date(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun date(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun time(value: Long?): LocalTime? = value?.let(LocalTime::ofNanoOfDay)
    @TypeConverter fun time(value: LocalTime?): Long? = value?.toNanoOfDay()
    @TypeConverter fun instant(value: String?): Instant? = value?.let(Instant::parse)
    @TypeConverter fun instant(value: Instant?): String? = value?.toString()
}

@Dao
interface SpeseDao {
    @Query("SELECT * FROM categories ORDER BY isSystem, sortOrder, id")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun expenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to AND status = 'CONFIRMED' ORDER BY date, id")
    suspend fun confirmedBetween(from: LocalDate, to: LocalDate): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE date = :date AND status = 'CONFIRMED'")
    suspend fun totalOn(date: LocalDate): Long

    @Query("SELECT * FROM recurrings ORDER BY name COLLATE NOCASE, id")
    fun recurrings(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurrings WHERE active = 1 ORDER BY id")
    suspend fun activeRecurrings(): List<RecurringEntity>

    @Query("SELECT * FROM recurrings WHERE id = :id")
    suspend fun recurring(id: Long): RecurringEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: Long): ExpenseEntity?

    @Query("SELECT * FROM categories WHERE isSystem = 1 LIMIT 1")
    suspend fun fallbackCategory(): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE isSystem = 0")
    suspend fun nextCategoryOrder(): Int

    @Upsert suspend fun upsertCategory(category: CategoryEntity): Long
    @Insert suspend fun insertCategories(categories: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertExpense(expense: ExpenseEntity): Long
    @Upsert suspend fun upsertRecurring(recurring: RecurringEntity): Long

    @Query("UPDATE expenses SET amountCents = :cents, status = 'CONFIRMED' WHERE id = :id AND status = 'PENDING'")
    suspend fun confirmExpense(id: Long, cents: Long): Int

    @Query("UPDATE recurrings SET lastGeneratedDate = :date WHERE id = :id")
    suspend fun markGenerated(id: Long, date: LocalDate)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id AND isSystem = 0")
    suspend fun reorderCategory(id: Long, order: Int)

    @Query("UPDATE expenses SET categoryId = :fallbackId WHERE categoryId = :id")
    suspend fun moveExpenses(id: Long, fallbackId: Long)

    @Query("UPDATE recurrings SET categoryId = :fallbackId WHERE categoryId = :id")
    suspend fun moveRecurrings(id: Long, fallbackId: Long)

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("DELETE FROM recurrings WHERE id = :id")
    suspend fun deleteRecurring(id: Long)
}

@Database(
    entities = [CategoryEntity::class, ExpenseEntity::class, RecurringEntity::class],
    version = 1, exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class SpeseDatabase : RoomDatabase() {
    abstract fun dao(): SpeseDao
}
