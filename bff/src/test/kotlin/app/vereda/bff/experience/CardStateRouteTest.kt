package app.vereda.bff.experience

import app.vereda.bff.BffGraph
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitCardState
import app.vereda.bff.experience.application.SubmitSignIn
import app.vereda.bff.experience.application.SubmitSignOut
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.experience.application.SubmitSimulatePurchase
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.application.ChangeCardState
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.CardStateControl
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
import app.vereda.bff.transactions.application.SimulatePurchase
import app.vereda.bff.transactions.application.IdleCardTransactions
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
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStateRouteTest {
    @Test
    fun homeOffersBlockAndTheActionSuspendsTheCard() = testApplication {
        val events = RecordingEventPublisher()
        application { module(graph(events)) }
        val created = client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"fields":{"firstName":"Ana","lastName":"Lima","email":"ana@example.com","password":"segredo12"}}""",
            )
        }
        val token = sessionToken(created.bodyAsText())
        assertTrue(created.bodyAsText().contains("Cartão ativo"))
        assertTrue(created.bodyAsText().contains("Bloquear cartão"))

        val blocked = client.post("/actions/card-state") { header("X-Session-Token", token) }
        assertTrue(blocked.bodyAsText().contains("Cartão suspenso."))

        val home = client.get("/screens/home") { header("X-Session-Token", token) }
        assertTrue(home.bodyAsText().contains("Cartão suspenso"))
        assertTrue(home.bodyAsText().contains("Desbloquear cartão"))
        assertTrue(events.recorded().any { it is app.vereda.bff.issuing.domain.CardStateChanged })
    }

    private fun sessionToken(body: String): String {
        val effects = ScreenJson.parseToJsonElement(body).jsonObject.getValue("effects").jsonArray
        return effects.first { it.jsonObject.getValue("type").jsonPrimitive.content == "setSession" }
            .jsonObject.getValue("token").jsonPrimitive.content
    }

    private fun graph(events: RecordingEventPublisher): BffGraph {
        val catalog = ScreenCatalog()
        val cardholders = InMemoryCardholderRepository()
        val sessions = InMemorySessionRepository()
        val cards = InMemoryIssuedCardRepository()
        val funding = object : AccountFunding {
            override suspend fun fund(userToken: UserToken, money: Money) = Unit
            override suspend fun availableBalance(userToken: UserToken) = Money.openingBalance
        }
        return BffGraph(
            screens = ScreenQueries(catalog, sessions, cardholders, cards, funding),
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
                SimulatePurchase(IdleCardTransactions, events),
                catalog,
            ),
            cardState = SubmitCardState(
                sessions,
                cardholders,
                cards,
                ChangeCardState(
                    cards,
                    object : CardStateControl {
                        override suspend fun transition(cardToken: CardToken, state: CardState) = state
                    },
                    events,
                ),
            ),
        )
    }
}
