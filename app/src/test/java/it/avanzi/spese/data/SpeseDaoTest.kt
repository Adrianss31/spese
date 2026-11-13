package it.avanzi.spese.data

import android.app.Application
import androidx.room.Room
import it.avanzi.spese.domain.ExpenseStatus
import java.time.Instant
import java.time.LocalDate
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
}
