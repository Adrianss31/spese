package it.avanzi.spese.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class ItalianFormatsTest {
    @Test fun formatsCentsWithoutFloatingPoint() {
        assertEquals("1.284,50 €", ItalianFormats.money(128450))
        assertEquals("0,01 €", ItalianFormats.money(1))
        assertEquals("0,00 €", ItalianFormats.money(0))
        assertEquals("−0,05 €", ItalianFormats.money(-5))
        assertEquals("−92.233.720.368.547.758,08 €", ItalianFormats.money(Long.MIN_VALUE))
    }

    @Test fun formatsDatesAndTimesInItalian() {
        val date = LocalDate.of(2026, 11, 11)
        assertEquals("Mercoledì 11", ItalianFormats.dayHeading(date))
        assertEquals("11 nov 2026", ItalianFormats.shortDate(date))
        assertEquals("Novembre 2026", ItalianFormats.monthHeading(YearMonth.from(date)))
        assertEquals("08:05", ItalianFormats.time(LocalTime.of(8, 5)))
    }
}
