package it.avanzi.spese.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object ItalianFormats {
    private val locale = Locale.ITALY
    private val day = DateTimeFormatter.ofPattern("EEEE d", locale)
    private val date = DateTimeFormatter.ofPattern("d MMM uuuu", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM uuuu", locale)
    private val clock = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun money(cents: Long): String {
        val euros = BigDecimal.valueOf(cents, 2)
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val sign = if (cents < 0) "−" else ""
        return "$sign${format.format(euros.abs())} €"
    }

    fun dayHeading(value: LocalDate): String = title(day.format(value))

    fun shortDate(value: LocalDate): String = date.format(value)

    fun monthHeading(value: YearMonth): String = title(month.format(value))

    fun time(value: LocalTime): String = clock.format(value)

    private fun title(value: String) = value.replaceFirstChar { it.titlecase(locale) }
}
