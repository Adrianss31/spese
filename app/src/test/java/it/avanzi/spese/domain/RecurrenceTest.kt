package it.avanzi.spese.domain

import it.avanzi.spese.data.RecurringEntity
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceTest {
    private val monthly = RecurringEntity(
        name = "Internet", amountCents = 3000, categoryId = 1, iconKey = "wifi",
        frequency = Frequency.MONTHLY, dayOfMonth = 31, anchorDate = LocalDate.parse("2024-01-31"),
    )

    @Test fun clampsToFebruaryThenRestoresOriginalDay() {
        assertEquals(listOf("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30"),
            monthly.occurrences(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-04-30")).map { it.toString() }.toList())
    }

    @Test fun preservesBimonthlyPhaseAcrossYears() {
        val everyTwoMonths = monthly.copy(frequency = Frequency.BIMONTHLY, anchorDate = LocalDate.parse("2025-12-31"))
        assertEquals(listOf("2026-02-28", "2026-04-30"),
            everyTwoMonths.occurrences(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-05-31")).map { it.toString() }.toList())
    }

    @Test fun yearlyLeapDayClampsAndReturnsInLeapYear() {
        val yearly = monthly.copy(frequency = Frequency.YEARLY, dayOfMonth = 29, monthOfYear = 2, anchorDate = LocalDate.parse("2024-02-29"))
        assertEquals(LocalDate.parse("2025-02-28"), yearly.nextOnOrAfter(LocalDate.parse("2024-03-01")))
        assertEquals(LocalDate.parse("2028-02-29"), yearly.nextOnOrAfter(LocalDate.parse("2027-03-01")))
    }

    @Test fun weeklyRespectsSelectedWeekdayAndLowerBound() {
        val weekly = monthly.copy(frequency = Frequency.WEEKLY, dayOfWeek = DayOfWeek.MONDAY)
        assertEquals(LocalDate.parse("2024-02-05"), weekly.nextOnOrAfter(LocalDate.parse("2024-01-01")))
        assertEquals(LocalDate.parse("2024-02-12"), weekly.nextOnOrAfter(LocalDate.parse("2024-02-06")))
    }

    @Test fun futureAnchorDoesNotGenerateEarlierDates() {
        assertEquals(emptyList<LocalDate>(), monthly.occurrences(LocalDate.MIN, LocalDate.parse("2023-12-31")).toList())
    }
}
