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
        val everyTwoMonths = monthly.copy(every = 2, anchorDate = LocalDate.parse("2025-12-31"))
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

    @Test fun quarterlyKeepsPhaseFromAnchor() {
        val quarterly = monthly.copy(every = 3, dayOfMonth = 5, anchorDate = LocalDate.parse("2026-01-05"))
        assertEquals(listOf("2026-01-05", "2026-04-05", "2026-07-05", "2026-10-05"),
            quarterly.occurrences(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")).map { it.toString() }.toList())
        assertEquals(3, quarterly.spreadMonths())
        assertEquals("Trimestrale · il 5", quarterly.scheduleLabel())
    }

    @Test fun everyTwoWeeksFromFirstMatchingWeekday() {
        val biweekly = monthly.copy(frequency = Frequency.WEEKLY, every = 2, dayOfWeek = DayOfWeek.FRIDAY, anchorDate = LocalDate.parse("2026-11-04"))
        assertEquals(listOf("2026-11-06", "2026-11-20", "2026-12-04"),
            biweekly.occurrences(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-10")).map { it.toString() }.toList())
        assertEquals(null, biweekly.spreadMonths())
    }

    @Test fun everyTenDays() {
        val daily = monthly.copy(frequency = Frequency.DAILY, every = 10, anchorDate = LocalDate.parse("2026-11-01"))
        assertEquals(LocalDate.parse("2026-11-21"), daily.nextOnOrAfter(LocalDate.parse("2026-11-12")))
        assertEquals(30.4375 / 10, daily.monthlyFactor(), 1e-9)
    }

    @Test fun everyTwoYearsSpreadsOverTwentyFourMonths() {
        val biennial = monthly.copy(frequency = Frequency.YEARLY, every = 2, dayOfMonth = 10, monthOfYear = 3, anchorDate = LocalDate.parse("2025-03-10"))
        assertEquals(LocalDate.parse("2027-03-10"), biennial.nextOnOrAfter(LocalDate.parse("2025-03-11")))
        assertEquals(24, biennial.spreadMonths())
        assertEquals(1250L, biennial.copy(amountCents = 30000).monthlyCents())
    }

    @Test fun movingTheDayLaterAfterAChargeDoesNotChargeTwiceInTheMonth() {
        // Addebitato il 5 novembre; il nuovo giorno 20 parte dal prossimo addebito scelto (20 dicembre).
        val moved = monthly.copy(dayOfMonth = 20, anchorDate = LocalDate.parse("2026-12-20"), lastGeneratedDate = LocalDate.parse("2026-11-05"))
        assertEquals(emptyList<LocalDate>(),
            moved.occurrences(LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-30")).toList())
        assertEquals(LocalDate.parse("2026-12-20"), moved.nextOnOrAfter(LocalDate.parse("2026-11-06")))
    }
}
