package app.vereda.bff.onboarding.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CardholderInvariantTest {
    @Test
    fun emailRejectsValuesWithoutADomain() {
        assertFalse(Email.isValid("ana"))
        assertFalse(Email.isValid("ana@localhost"))
        assertThrows(IllegalArgumentException::class.java) { Email.parse(" ") }
        assertTrue(Email.isValid("ana@example.com"))
    }

    @Test
    fun personNameAndUserTokenMustBePresent() {
        assertThrows(IllegalArgumentException::class.java) { PersonName(" ", "Lima") }
        assertThrows(IllegalArgumentException::class.java) { UserToken.of("  ") }
    }

    @Test
    fun passwordIsHashedAndCheckedWithoutKeepingThePlainValue() {
        assertThrows(IllegalArgumentException::class.java) { Password.create("curta") }
        val password = Password.create("segredo12")
        assertFalse(password.hash == "segredo12")
        assertTrue(password.matches("segredo12"))
        assertFalse(password.matches("segredo13"))
    }
}
