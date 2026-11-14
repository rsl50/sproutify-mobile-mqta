package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.infrastructure.JdbcIssuedCardRepository
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.persistence.VeredaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JdbcAccountStoreTest {
    @Test
    fun accountAndCardSurviveARestartAndDoNotStoreThePan() {
        val directory = Files.createTempDirectory("vereda-db").toFile()
        val file = directory.resolve("vereda")
        val cardholder = Cardholder.register(
            id = CardholderId.newId(),
            name = PersonName("Ana", "Lima"),
            email = Email.parse("ana@example.com"),
            password = Password.create("segredo12"),
            userToken = UserToken.of("usr_1"),
        )
        val card = IssuedCard.issue(
            cardholderId = cardholder.id,
            token = CardToken.of("card_1"),
            last4 = CardLast4.of("4242"),
            productToken = CardProductToken.of("cp_on"),
        )

        VeredaDatabase.open(file).use { database ->
            JdbcCardholderRepository(database).save(cardholder)
            JdbcIssuedCardRepository(database).save(card)
            JdbcSessionRepository(database).open(cardholder.id).also { token ->
                assertEquals(cardholder.id, JdbcSessionRepository(database).cardholderId(token))
            }
        }

        VeredaDatabase.open(file).use { database ->
            val restored = JdbcCardholderRepository(database).findByEmail(Email.parse("ANA@example.com"))
            assertEquals(cardholder.id, restored?.id)
            assertTrue(restored!!.password.matches("segredo12"))
            assertFalse(restored.password.hash.contains("segredo12"))
            assertEquals(CardLast4.of("4242"), JdbcIssuedCardRepository(database).findByCardholder(cardholder.id)?.last4)
            val cardColumns = database.columnNames("issued_card")
            assertTrue(cardColumns.none { it.equals("pan", ignoreCase = true) })
            assertTrue(database.columnNames("cardholder").none { it.equals("pan", ignoreCase = true) })
            assertTrue(database.columnNames("cardholder").any { it.equals("password_hash", ignoreCase = true) })
        }
    }
}
