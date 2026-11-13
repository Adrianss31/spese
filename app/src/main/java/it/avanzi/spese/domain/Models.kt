package it.avanzi.spese.domain

enum class ExpenseStatus { CONFIRMED, PENDING }
enum class Frequency(val monthlyFactor: Double) {
    WEEKLY(52.0 / 12), MONTHLY(1.0), BIMONTHLY(.5), YEARLY(1.0 / 12)
}
enum class UpdateChannel { STABLE, BETA }
