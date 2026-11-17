package app.vereda.bff.jit.infrastructure

import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.infrastructure.JdbcIssuedCardRepository
import app.vereda.bff.jit.application.ApplyFundingNotification
import app.vereda.bff.jit.application.DecideFunding
import app.vereda.bff.jit.domain.FundingApproved
import app.vereda.bff.jit.domain.FundingMessage
import app.vereda.bff.jit.domain.money
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.JdbcCardholderRepository
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.bff.shared.persistence.VeredaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.nio.file.Files

class JdbcFundingStoreTest {
    @Test
    fun holdSurvivesReopenAndAClearingIsIdempotent() {
        val file = Files.createTempDirectory("vereda-jit").toFile().resolve("vereda")
        val events = RecordingEventPublisher()
        val cardholder = Cardholder.register(
            id = CardholderId.newId(),
            name = PersonName("Ana", "Lima"),
            email = Email.parse("ana@example.com"),
            password = Password.create("segredo12"),
            userToken = UserToken.of("usr_1"),
        )
        VeredaDatabase.open(file).use { database ->
            JdbcCardholderRepository(database).save(cardholder)
            JdbcIssuedCardRepository(database).save(
                IssuedCard.issue(
                    cardholderId = cardholder.id,
                    token = CardToken.of("card_1"),
                    last4 = CardLast4.of("4242"),
                    productToken = CardProductToken.of("cp_jit"),
                    state = CardState.ACTIVE,
                ),
            )
            val store = JdbcFundingStore(database)
            store.open("usr_1", BigDecimal("100.00"))
            val decide = DecideFunding(store, JdbcCardholderRepository(database), JdbcIssuedCardRepository(database), null, events)
            val approved = decide.execute(auth("jit-1"))
            assertEquals(200, approved.status)
            val again = decide.execute(auth("jit-1"))
            assertEquals(200, again.status)
            assertEquals("90.00", store.balances("usr_1").available.toPlainString())
        }
        VeredaDatabase.open(file).use { database ->
            val store = JdbcFundingStore(database)
            assertEquals("100.00", store.balances("usr_1").ledger.toPlainString())
            assertEquals("90.00", store.balances("usr_1").available.toPlainString())
            val notify = ApplyFundingNotification(store, events)
            val clearing = clearing()
            notify.execute(clearing)
            notify.execute(clearing)
            assertEquals("90.00", store.balances("usr_1").ledger.toPlainString())
            assertEquals("90.00", store.balances("usr_1").available.toPlainString())
        }
        assertTrue(events.recorded().filterIsInstance<FundingApproved>().size == 1)
    }

    private fun auth(token: String) = FundingMessage(
        token = token,
        method = "pgfs.authorization",
        userToken = "usr_1",
        amount = BigDecimal("10.00").money(),
        originalToken = null,
        impacted = null,
        timedOut = false,
    )

    private fun clearing() = FundingMessage(
        token = "cap-1",
        method = "pgfs.authorization.capture",
        userToken = "usr_1",
        amount = BigDecimal("10.00").money(),
        originalToken = "jit-1",
        impacted = BigDecimal("-10.00").money(),
        timedOut = false,
    )
}
