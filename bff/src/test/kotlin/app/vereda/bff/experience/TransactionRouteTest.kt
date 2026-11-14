package app.vereda.bff.experience

import app.vereda.bff.BffGraph
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignIn
import app.vereda.bff.experience.application.SubmitSignOut
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.experience.application.SubmitSimulatePurchase
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
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
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.onboarding.infrastructure.InMemorySessionRepository
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.bff.transactions.application.ListCardTransactions
import app.vereda.bff.transactions.application.SimulatePurchase
import app.vereda.bff.transactions.domain.CardTransaction
import app.vereda.bff.transactions.domain.CardTransactionGateway
import app.vereda.bff.transactions.domain.MerchantName
import app.vereda.bff.transactions.domain.PurchaseSimulated
import app.vereda.sdui.ScreenJson
import io.ktor.client.request.get
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRouteTest {
    @Test
    fun listsTransactionsAndSimulatesAPurchaseFromServerScreens() = testApplication {
        val events = RecordingEventPublisher()
        val gateway = RecordingGateway()
        application { module(graph(events, gateway)) }

        val created = client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"fields":{"firstName":"Ana","lastName":"Lima","email":"ana@example.com","password":"segredo12"}}""",
            )
        }
        val token = sessionToken(created.bodyAsText())
        val listed = client.get("/screens/transactions") { header("X-Session-Token", token) }
        assertTrue(listed.bodyAsText().contains("Padaria da Esquina"))
        assertTrue(listed.bodyAsText().contains("USD 12.50"))

        val simulated = client.post("/actions/simulate-purchase") {
            header("X-Session-Token", token)
            contentType(ContentType.Application.Json)
            setBody("""{"fields":{"amount":"8.00","merchant":"Cafe"}}""")
        }
        val body = simulated.bodyAsText()
        assertTrue(body.contains("Compra simulada."))
        assertTrue(body.contains("\"screenId\":\"home\""))
        assertEquals(CardToken.of("card_1"), gateway.simulatedCard)
        assertTrue(events.recorded().filterIsInstance<PurchaseSimulated>().isNotEmpty())
    }

    private fun sessionToken(body: String): String {
        val effects = ScreenJson.parseToJsonElement(body).jsonObject.getValue("effects").jsonArray
        return effects.first { it.jsonObject.getValue("type").jsonPrimitive.content == "setSession" }
            .jsonObject.getValue("token").jsonPrimitive.content
    }

    private fun graph(events: RecordingEventPublisher, gateway: CardTransactionGateway): BffGraph {
        val catalog = ScreenCatalog()
        val cardholders = InMemoryCardholderRepository()
        val sessions = InMemorySessionRepository()
        val cards = InMemoryIssuedCardRepository()
        val funding = object : AccountFunding {
            override suspend fun fund(userToken: UserToken, money: Money) = Unit
            override suspend fun availableBalance(userToken: UserToken) = Money.openingBalance
        }
        val enrollment = object : CardholderEnrollment {
            override suspend fun enroll(name: PersonName, email: Email) = UserToken.of("usr_1")
        }
        val issuing = object : CardIssuing {
            override suspend fun issueFor(userToken: UserToken) = IssuedCardDraft(
                CardToken.of("card_1"),
                CardLast4.of("4242"),
                CardProductToken.of("cp_on"),
            )
        }
        return BffGraph(
            screens = ScreenQueries(
                catalog,
                sessions,
                cardholders,
                cards,
                funding,
                ListCardTransactions(gateway),
            ),
            signup = SubmitSignup(
                RegisterCardholder(cardholders, enrollment, events),
                IssueCard(issuing, cards, events),
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
            ),
        )
    }

    private class RecordingGateway : CardTransactionGateway {
        var simulatedCard: CardToken? = null

        override suspend fun list(userToken: UserToken): List<CardTransaction> = listOf(
            CardTransaction(
                token = "tx_1",
                merchant = MerchantName.parse("Padaria da Esquina"),
                amount = Money.parse("12.50", app.vereda.bff.funding.domain.Currency.USD),
                state = "PENDING",
            ),
        )

        override suspend fun simulatePurchase(
            cardToken: CardToken,
            amount: Money,
            merchant: MerchantName,
        ): CardTransaction {
            simulatedCard = cardToken
            return CardTransaction("tx_2", merchant, amount, "PENDING")
        }
    }
}
