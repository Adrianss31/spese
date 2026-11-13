package it.avanzi.spese.data

import android.app.Application
import androidx.room.Room
import it.avanzi.spese.domain.ExpenseStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SpeseDaoTest {
    private lateinit var database: SpeseDatabase
    private lateinit var repository: SpeseRepository

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SpeseDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = SpeseRepository(database)
    }

    @After fun close() = database.close()

    @Test fun seedsOnceAndTransfersExpensesWhenDeletingCategory() = runBlocking {
        repository.initializeCategories()
        repository.initializeCategories()
        val categories = repository.categories.first()
        assertEquals(6, categories.size)
        val fallback = categories.last()
        val category = categories.first()
        val expense = ExpenseEntity(
            amountCents = 500, categoryId = category.id,
            date = LocalDate.of(2026, 11, 13), createdAt = Instant.EPOCH,
        )
        repository.saveExpense(expense)
        repository.deleteCategory(category.id)
        assertEquals(5, repository.categories.first().size)
        assertEquals(fallback.id, repository.expenses.first().single().categoryId)
        repository.deleteCategory(fallback.id)
        assertEquals(fallback.id, repository.categories.first().last().id)
    }

    @Test fun excludesPendingFromConfirmedRange() = runBlocking {
        repository.initializeCategories()
        val date = LocalDate.of(2026, 11, 13)
        val category = repository.categories.first().first()
        val confirmed = ExpenseEntity(amountCents = 700, categoryId = category.id, date = date, createdAt = Instant.EPOCH)
        repository.saveExpense(confirmed)
        repository.saveExpense(confirmed.copy(amountCents = 999, status = ExpenseStatus.PENDING))
        assertEquals(listOf(700L), database.dao().confirmedBetween(date, date).map { it.amountCents })
    }
    @Test fun catchUpIsIdempotentAcrossConcurrentRuns() = runBlocking {
        repository.initializeCategories()
        val category = repository.categories.first().first()
        val anchor = LocalDate.parse("2024-01-31")
        val today = LocalDate.parse("2024-03-31")
        val rule = RecurringEntity(
            name = "Luce", amountCents = 1500, categoryId = category.id, iconKey = "bolt",
            frequency = it.avanzi.spese.domain.Frequency.MONTHLY, dayOfMonth = 31,
            anchorDate = anchor, isVariable = true,
        )
        repository.saveRecurring(rule)
        repository.saveRecurring(rule.copy(name = "Promemoria", autoRecord = false))
        val engine = RecurringEngine(database)
        val notifications = kotlinx.coroutines.coroutineScope {
            val first = async { engine.generate(today, Instant.EPOCH) }
            val second = async { engine.generate(today, Instant.EPOCH) }
            first.await() + second.await()
        }
        assertEquals(6, notifications.size)
        assertEquals(3, notifications.count { it.pendingExpenseId != null })
        val expenses = repository.expenses.first()
        assertEquals(listOf("2024-03-31", "2024-02-29", "2024-01-31"), expenses.map { it.date.toString() })
        assertEquals(0, database.dao().confirmedBetween(anchor, today).size)
        repository.confirmExpense(expenses.first().id, 1800)
        assertEquals(1800L, database.dao().totalOn(today))
        assertEquals(emptyList<GeneratedOccurrence>(), engine.generate(today, Instant.EPOCH))
    }
}
