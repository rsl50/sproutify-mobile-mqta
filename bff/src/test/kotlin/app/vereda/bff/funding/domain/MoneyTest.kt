package app.vereda.bff.funding.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun openingBalanceIsOneHundredUsd() {
        assertEquals("USD 100.00", Money.openingBalance.format())
        assertEquals(Currency.USD, Money.openingBalance.currency)
    }

    @Test
    fun rejectsNegativeOrOverPreciseAmounts() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("-1.00", Currency.USD) }
        assertThrows(ArithmeticException::class.java) { Money.parse("1.234", Currency.USD) }
    }
}
