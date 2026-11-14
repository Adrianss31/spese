package it.avanzi.spese.data

import androidx.room.withTransaction
import it.avanzi.spese.domain.spreadMonths
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SavedExpense(val expenseId: Long, val newRecurringId: Long?)

@Singleton
class SpeseRepository @Inject constructor(private val database: SpeseDatabase) {
    private val dao = database.dao()
    val categories = dao.categories()
    val expenses = dao.expenses()
    val recurrings = dao.recurrings()

    suspend fun initializeCategories() = database.withTransaction {
        if (dao.fallbackCategory() == null) dao.insertCategories(defaultCategories)
    }

    suspend fun saveCategory(category: CategoryEntity) = database.withTransaction {
        val existing = dao.category(category.id)
        dao.upsertCategory(category.copy(
            id = existing?.id ?: 0,
            sortOrder = existing?.sortOrder ?: dao.nextCategoryOrder(),
            isSystem = existing?.isSystem ?: false,
        ))
    }

    suspend fun reorderCategories(ids: List<Long>) = database.withTransaction {
        ids.forEachIndexed { index, id -> dao.reorderCategory(id, index) }
    }

    suspend fun deleteCategory(id: Long) = database.withTransaction {
        val fallback = checkNotNull(dao.fallbackCategory())
        if (id != fallback.id) {
            dao.moveExpenses(id, fallback.id)
            dao.moveRecurrings(id, fallback.id)
            dao.deleteCategory(id)
        }
    }

    suspend fun saveExpense(expense: ExpenseEntity, recurring: RecurringEntity? = null): SavedExpense =
        database.withTransaction {
            val category = dao.category(expense.categoryId) ?: checkNotNull(dao.fallbackCategory())
            val recurringId = recurring?.let { dao.upsertRecurring(it.copy(categoryId = category.id)) }
            val source = recurring ?: expense.recurringId?.let { dao.recurring(it) }
            val expenseId = dao.insertExpense(expense.copy(
                categoryId = category.id,
                recurringId = recurringId ?: expense.recurringId,
                spreadMonths = expense.spreadMonths ?: source?.spreadMonths(),
            ))
            check(expenseId != -1L)
            SavedExpense(expenseId, recurringId)
        }

    suspend fun undo(saved: SavedExpense) = database.withTransaction {
        dao.deleteExpense(saved.expenseId)
        saved.newRecurringId?.let { dao.deleteRecurring(it) }
    }

    /**
     * Le modifiche valgono dagli addebiti successivi. Con [applyToPast] nome e categoria nuovi
     * vengono riportati anche sulle spese già registrate (l'importo mai).
     */
    suspend fun saveRecurring(recurring: RecurringEntity, applyToPast: Boolean = false) = database.withTransaction {
        val category = dao.category(recurring.categoryId) ?: checkNotNull(dao.fallbackCategory())
        val existing = dao.recurring(recurring.id)
        dao.upsertRecurring(recurring.copy(
            categoryId = category.id,
            lastGeneratedDate = existing?.lastGeneratedDate ?: recurring.lastGeneratedDate,
        ))
        if (applyToPast && existing != null) {
            if (existing.categoryId != category.id) dao.recategorizeRecurring(existing.id, category.id)
            if (existing.name != recurring.name) dao.renameRecurring(existing.id, existing.name, recurring.name)
        }
    }

    suspend fun deleteRecurring(id: Long) = dao.deleteRecurring(id)

    suspend fun updateExpense(expense: ExpenseEntity) = database.withTransaction {
        val category = dao.category(expense.categoryId) ?: checkNotNull(dao.fallbackCategory())
        dao.updateExpense(expense.copy(categoryId = category.id))
    }

    suspend fun deleteExpense(id: Long): ExpenseEntity? = database.withTransaction {
        dao.expense(id)?.also { dao.deleteExpense(id) }
    }

    suspend fun restoreExpense(expense: ExpenseEntity) {
        dao.insertExpense(expense)
    }
    suspend fun confirmExpense(id: Long, cents: Long): Boolean {
        require(cents > 0)
        return dao.confirmExpense(id, cents) > 0
    }

    suspend fun totalOn(date: LocalDate) = dao.totalOn(date)

    companion object {
        private val defaultCategories = listOf(
            CategoryEntity(name = "Spesa", colorArgb = 0xFF5EE0B5.toInt(), iconKey = "cart", sortOrder = 0),
            CategoryEntity(name = "Casa", colorArgb = 0xFF7CC4FF.toInt(), iconKey = "home", sortOrder = 1),
            CategoryEntity(name = "Trasporti", colorArgb = 0xFFB9A4FF.toInt(), iconKey = "train", sortOrder = 2),
            CategoryEntity(name = "Ristoranti", colorArgb = 0xFFF5D06B.toInt(), iconKey = "fork", sortOrder = 3),
            CategoryEntity(name = "Svago", colorArgb = 0xFFFF9EC7.toInt(), iconKey = "ticket", sortOrder = 4),
            CategoryEntity(name = "Altro", colorArgb = 0xFFA7AAB3.toInt(), iconKey = "dots", sortOrder = 5, isSystem = true),
        )
    }
}
