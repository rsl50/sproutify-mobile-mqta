package app.vereda.bff.issuing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardLast4Test {
    @Test
    fun acceptsExactlyFourDigits() {
        assertEquals("4242", CardLast4.of("4242").value)
    }

    @Test
    fun rejectsAnythingElse() {
        listOf("", "42", "424", "42424", "abcd", "42 2").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { CardLast4.of(raw) }
        }
        assertThrows(IllegalArgumentException::class.java) { CardToken.of(" ") }
        assertThrows(IllegalArgumentException::class.java) { CardProductToken.of("") }
    }
}
