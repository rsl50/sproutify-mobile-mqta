package app.vereda.bff.jit

import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.domain.CardFundingMode
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCardDraft
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.jit.domain.AccountLedger
import app.vereda.bff.jit.domain.FundingBook
import app.vereda.bff.jit.domain.LedgerState
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.onboarding.infrastructure.InMemorySessionRepository
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.sdui.ScreenJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class JitSignupTest {
    @Test
    fun jitSignupOpensTheLedgerAndDoesNotCallGpa() = runBlocking {
        val funding = RecordingFunding()
        val issuing = RecordingIssuing()
        val cards = InMemoryIssuedCardRepository()
        val cardholders = InMemoryCardholderRepository()
        val sessions = InMemorySessionRepository()
        val events = RecordingEventPublisher()
        val ledger = MemoryLedger()
        val catalog = ScreenCatalog(defaultFunding = "jit")
        val signup = SubmitSignup(
            register = RegisterCardholder(
                cardholders,
                object : CardholderEnrollment {
                    override suspend fun enroll(name: PersonName, email: Email) = UserToken.of("usr_jit")
                },
                events,
            ),
            issueCard = IssueCard(issuing, cards, events),
            fundOpeningBalance = FundOpeningBalance(funding, events),
            sessions = sessions,
            catalog = catalog,
            fundingMode = CardFundingMode.JIT,
            jitProductToken = "vereda_jit_card",
            ledgers = ledger,
        )
        val created = signup.execute(
            mapOf(
                "firstName" to "Ana",
                "lastName" to "Lima",
                "email" to "ana@example.com",
                "password" to "segredo12",
                "funding" to "jit",
            ),
        )
        val body = ScreenJson.encodeToString(created)
        assertFalse(funding.called)
        assertEquals("vereda_jit_card", issuing.product)
        assertTrue(body.contains("Ledger USD 100.00"))
        assertTrue(body.contains("USD 100.00"))
        val session = created.effects.first { it.type == "setSession" }.token!!
        val home = ScreenQueries(catalog, sessions, cardholders, cards, funding, ledgers = ledger)
            .get("home", session)
        assertTrue(ScreenJson.encodeToString(home).contains("Ledger USD 100.00"))
        assertEquals("100.00", ledger.balances("usr_jit").available.toPlainString())
    }

    private class RecordingFunding : AccountFunding {
        var called = false
        override suspend fun fund(userToken: UserToken, money: Money) {
            called = true
        }
        override suspend fun availableBalance(userToken: UserToken): Money = error("saldo GPA")
    }

    private class RecordingIssuing : CardIssuing {
        var product: String? = null
        override suspend fun issueFor(userToken: UserToken): IssuedCardDraft = error("produto GPA")
        override suspend fun issueOnProduct(userToken: UserToken, productToken: CardProductToken): IssuedCardDraft {
            product = productToken.value
            return IssuedCardDraft(CardToken.of("card_jit"), CardLast4.of("4242"), productToken)
        }
    }

    private class MemoryLedger : AccountLedger {
        private val rows = mutableMapOf<String, LedgerState>()
        override fun balances(userToken: String): LedgerState = rows.getValue(userToken)
        override fun open(userToken: String, amount: BigDecimal): LedgerState {
            val opened = FundingBook.open(amount)
            rows[userToken] = opened
            return opened
        }
    }
}
