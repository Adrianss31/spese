package it.avanzi.spese.domain

enum class ExpenseStatus { CONFIRMED, PENDING }

/** Unità della ricorrenza; il numero di unità tra un addebito e l'altro sta in `RecurringEntity.every`. */
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class UpdateChannel { STABLE, BETA }
