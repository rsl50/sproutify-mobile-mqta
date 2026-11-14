package app.vereda.bff.experience

import app.vereda.bff.BffGraph
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignIn
import app.vereda.bff.experience.application.SubmitSignOut
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.experience.application.SubmitSimulatePurchase
import app.vereda.bff.experience.application.SubmitSpendLimit
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCardDraft
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.module
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.application.SignIn
import app.vereda.bff.onboarding.application.SignOut
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.onboarding.infrastructure.InMemorySessionRepository
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.bff.spending.application.SetSpendLimit
import app.vereda.bff.spending.domain.SpendControl
import app.vereda.bff.spending.domain.SpendLimit
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.transactions.application.SimulatePurchase
import app.vereda.bff.transactions.domain.CardTransaction
import app.vereda.bff.transactions.domain.CardTransactionGateway
import app.vereda.bff.transactions.domain.MerchantName
import app.vereda.sdui.ScreenJson
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendLimitRouteTest {
    @Test
    fun purchaseOverTheLimitIsShownAsDeclined() = testApplication {
        val limits = MemoryLimits()
        val gateway = DecliningGateway(limits)
        application { module(graph(limits, gateway)) }
        val created = client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"fields":{"firstName":"Ana","lastName":"Lima","email":"ana@example.com","password":"segredo12"}}""",
            )
        }
        val token = sessionToken(created.bodyAsText())
        val saved = client.post("/actions/spend-limit") {
            header("X-Session-Token", token)
            contentType(ContentType.Application.Json)
            setBody("""{"fields":{"amount":"10.00","usage":"2"}}""")
        }
        assertTrue(saved.bodyAsText().contains("Limite diário de USD 10.00"))

        val purchase = client.post("/actions/simulate-purchase") {
            header("X-Session-Token", token)
            contentType(ContentType.Application.Json)
            setBody("""{"fields":{"amount":"25.00","merchant":"Cafe"}}""")
        }
        assertTrue(purchase.bodyAsText().contains("Compra recusada: acima do limite de USD 10.00."))
    }

    private fun sessionToken(body: String): String {
        val effects = ScreenJson.parseToJsonElement(body).jsonObject.getValue("effects").jsonArray
        return effects.first { it.jsonObject.getValue("type").jsonPrimitive.content == "setSession" }
            .jsonObject.getValue("token").jsonPrimitive.content
    }

    private fun graph(limits: SpendLimitRepository, gateway: CardTransactionGateway): BffGraph {
        val events = RecordingEventPublisher()
        val catalog = ScreenCatalog()
        val cardholders = InMemoryCardholderRepository()
        val sessions = InMemorySessionRepository()
        val cards = InMemoryIssuedCardRepository()
        val funding = object : AccountFunding {
            override suspend fun fund(userToken: UserToken, money: Money) = Unit
            override suspend fun availableBalance(userToken: UserToken) = Money.openingBalance
        }
        return BffGraph(
            screens = ScreenQueries(catalog, sessions, cardholders, cards, funding, spendLimits = limits),
            signup = SubmitSignup(
                RegisterCardholder(
                    cardholders,
                    object : CardholderEnrollment {
                        override suspend fun enroll(name: PersonName, email: Email) = UserToken.of("usr_1")
                    },
                    events,
                ),
                IssueCard(
                    object : CardIssuing {
                        override suspend fun issueFor(userToken: UserToken) = IssuedCardDraft(
                            CardToken.of("card_1"),
                            CardLast4.of("4242"),
                            CardProductToken.of("cp_on"),
                        )
                    },
                    cards,
                    events,
                ),
                FundOpeningBalance(funding, events),
                sessions,
                catalog,
            ),
            signIn = SubmitSignIn(SignIn(cardholders, sessions), catalog),
            signOut = SubmitSignOut(SignOut(sessions)),
            simulatePurchase = SubmitSimulatePurchase(
                sessions,
                cardholders,
                cards,
                SimulatePurchase(gateway, events),
                catalog,
                limits,
            ),
            spendLimit = SubmitSpendLimit(
                sessions,
                cardholders,
                SetSpendLimit(limits, object : SpendControl {
                    override suspend fun save(userToken: UserToken, limit: SpendLimit) = Unit
                }, events),
                catalog,
            ),
        )
    }

    private class MemoryLimits : SpendLimitRepository {
        private var stored: SpendLimit? = null
        override fun find(cardholderId: CardholderId) = stored?.takeIf { it.cardholderId == cardholderId }
        override fun save(limit: SpendLimit) {
            stored = limit
        }
    }

    private class DecliningGateway(
        private val limits: SpendLimitRepository,
    ) : CardTransactionGateway {
        override suspend fun list(userToken: UserToken) = emptyList<CardTransaction>()

        override suspend fun simulatePurchase(
            cardToken: CardToken,
            amount: Money,
            merchant: MerchantName,
        ): CardTransaction {
            val over = limits.find(CardholderId(cardToken.value))
            val state = if (amount.amount > Money.parse("10.00", Currency.USD).amount) "DECLINED" else "PENDING"
            return CardTransaction("tx_1", merchant, amount, state).also { over }
        }
    }
}
