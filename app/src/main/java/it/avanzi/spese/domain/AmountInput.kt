package it.avanzi.spese.domain

import java.math.BigDecimal

enum class AmountKey(val digit: Char? = null) {
    One('1'), Two('2'), Three('3'), Four('4'), Five('5'), Six('6'),
    Seven('7'), Eight('8'), Nine('9'), Decimal, Zero('0'), Delete,
}

class AmountInput private constructor(private val value: String) {
    constructor() : this("")

    val display: String get() = value.ifEmpty { "0" }
    val isEmpty: Boolean get() = value.isEmpty()
    val cents: Long get() = BigDecimal(display.replace(',', '.')).movePointRight(2).longValueExact()

    /** Returns null when the key is incompatible with the input limits. */
    fun press(key: AmountKey): AmountInput? = when (key) {
        AmountKey.Delete -> AmountInput(value.dropLast(1))
        AmountKey.Decimal -> if (',' in value) null else AmountInput("$display,")
        else -> appendDigit(requireNotNull(key.digit))
    }

    private fun appendDigit(digit: Char): AmountInput? {
        val decimal = value.indexOf(',')
        return when {
            decimal >= 0 && value.length - decimal > 2 -> null
            decimal < 0 && value.length >= 5 -> null
            value == "0" -> AmountInput(digit.toString())
            else -> AmountInput(value + digit)
        }
    }

    override fun equals(other: Any?): Boolean = other is AmountInput && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        fun restore(display: String): AmountInput {
            require(display.matches(Regex("(?:0|[1-9][0-9]{0,4})(?:,[0-9]{0,2})?|")))
            return AmountInput(display)
        }
    }

    fun save(): String = value
}
