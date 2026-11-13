package it.avanzi.spese.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    @Test fun startsAtZeroAndReplacesLeadingZero() {
        val empty = AmountInput()
        assertEquals("0", empty.display)
        assertEquals(0L, empty.cents)
        assertEquals("7", empty.press(AmountKey.Seven)?.display)
    }

    @Test fun decimalKeyWorksWithNoDigits() {
        val input = AmountInput().press(AmountKey.Decimal)!!.press(AmountKey.Five)!!
        assertEquals("0,5", input.display)
        assertEquals(50L, input.cents)
        assertNull(input.press(AmountKey.Decimal))
    }

    @Test fun rejectsExtraIntegerAndFractionalDigits() {
        val whole = List(5) { AmountKey.Nine }.fold(AmountInput()) { input, key -> input.press(key)!! }
        assertNull(whole.press(AmountKey.Nine))
        val full = listOf(AmountKey.Decimal, AmountKey.Nine, AmountKey.Nine)
            .fold(whole) { input, key -> input.press(key)!! }
        assertEquals(9_999_999L, full.cents)
        assertNull(full.press(AmountKey.Nine))
    }

    @Test fun deleteCrossesDecimalAndReturnsToEmptyInput() {
        var input = AmountInput().press(AmountKey.Decimal)!!.press(AmountKey.One)!!
        input = input.press(AmountKey.Delete)!!
        assertEquals("0,", input.display)
        input = input.press(AmountKey.Delete)!!
        assertEquals(AmountInput(), input.press(AmountKey.Delete))
    }
}
