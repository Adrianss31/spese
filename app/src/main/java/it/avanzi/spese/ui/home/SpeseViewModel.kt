package it.avanzi.spese.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.avanzi.spese.data.CategoryEntity
import it.avanzi.spese.data.ExpenseEntity
import it.avanzi.spese.data.PreferencesRepository
import it.avanzi.spese.data.RecurringEntity
import it.avanzi.spese.data.SavedExpense
import it.avanzi.spese.data.SpesePreferences
import it.avanzi.spese.data.SpeseRepository
import it.avanzi.spese.domain.Frequency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OccurrenceLink(val expenseId: Long, val recurringId: Long, val epochDay: Long)

data class LedgerState(
    val categories: List<CategoryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val recurrings: List<RecurringEntity> = emptyList(),
    val preferences: SpesePreferences = SpesePreferences(),
    val month: YearMonth = YearMonth.now(),
)

@HiltViewModel
class SpeseViewModel @Inject constructor(
    val repository: SpeseRepository,
    val preferences: PreferencesRepository,
    val updates: it.avanzi.spese.data.update.UpdateManager,
) : ViewModel() {
    private val pendingLink = MutableStateFlow<OccurrenceLink?>(null)
    val occurrenceLink: kotlinx.coroutines.flow.StateFlow<OccurrenceLink?> = pendingLink
    fun openOccurrence(expense: Long, recurring: Long, date: Long) {
        pendingLink.value = OccurrenceLink(expense, recurring, date)
    }
    fun consumeOccurrence(link: OccurrenceLink) { pendingLink.compareAndSet(link, null) }

    private val month = MutableStateFlow(YearMonth.now())
    val state = combine(
        repository.categories, repository.expenses, repository.recurrings, preferences.values, month,
        ::LedgerState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())

    init {
        viewModelScope.launch { repository.initializeCategories() }
    }

    fun scheduleUpdates() { viewModelScope.launch { updates.schedule() } }

    fun selectMonth(value: YearMonth) { month.value = value }

    suspend fun save(
        cents: Long,
        category: CategoryEntity,
        description: String,
        date: LocalDate,
        frequency: Frequency?,
        existingRecurringId: Long? = null,
    ): SavedExpense {
        val name = description.trim().ifBlank { category.name }
        val expense = ExpenseEntity(
            amountCents = cents, categoryId = category.id, description = description.trim().ifEmpty { null },
            date = date, time = LocalTime.now(), createdAt = Instant.now(), recurringId = existingRecurringId,
        )
        val recurring = frequency?.let {
            RecurringEntity(
                name = name, amountCents = cents, categoryId = category.id, iconKey = category.iconKey,
                frequency = it, dayOfMonth = date.dayOfMonth.takeUnless { _ -> it == Frequency.WEEKLY },
                dayOfWeek = date.dayOfWeek.takeIf { _ -> it == Frequency.WEEKLY },
                monthOfYear = date.monthValue.takeIf { _ -> it == Frequency.YEARLY },
                anchorDate = date, lastGeneratedDate = date,
            )
        }
        return repository.saveExpense(expense, recurring)
    }
}
