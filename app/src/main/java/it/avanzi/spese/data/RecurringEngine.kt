package it.avanzi.spese.data

import androidx.room.withTransaction
import it.avanzi.spese.domain.ExpenseStatus
import it.avanzi.spese.domain.occurrences
import it.avanzi.spese.domain.spreadMonths
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedOccurrence(
    val recurring: RecurringEntity,
    val date: LocalDate,
    val pendingExpenseId: Long? = null,
)

@Singleton
class RecurringEngine @Inject constructor(private val database: SpeseDatabase) {
    suspend fun generate(today: LocalDate, now: Instant): List<GeneratedOccurrence> = database.withTransaction {
        val dao = database.dao()
        buildList {
            dao.activeRecurrings().forEach { recurring ->
                val from = recurring.lastGeneratedDate?.plusDays(1) ?: recurring.anchorDate
                for (date in recurring.occurrences(from, today)) {
                    if (recurring.autoRecord) {
                        val id = dao.insertExpense(ExpenseEntity(
                            amountCents = recurring.amountCents, categoryId = recurring.categoryId,
                            description = recurring.name, date = date, recurringId = recurring.id,
                            status = if (recurring.isVariable) ExpenseStatus.PENDING else ExpenseStatus.CONFIRMED,
                            createdAt = now, spreadMonths = recurring.spreadMonths(),
                        ))
                        if (recurring.isVariable && id != -1L) add(GeneratedOccurrence(recurring, date, id))
                    } else {
                        add(GeneratedOccurrence(recurring, date))
                    }
                    dao.markGenerated(recurring.id, date)
                }
            }
        }
    }
}
