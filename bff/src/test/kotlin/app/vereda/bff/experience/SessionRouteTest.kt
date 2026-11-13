package app.vereda.bff.experience

import app.vereda.bff.BffGraph
import app.vereda.bff.module
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignIn
import app.vereda.bff.experience.application.SubmitSignOut
import app.vereda.bff.experience.application.SubmitSignup
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
import app.vereda.sdui.ScreenJson
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRouteTest {
    @Test
    fun sessionRoutesHomeAndSignOutReturnsToSignup() = testApplication {
        application { module(graph()) }
        val created = client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupBody())
        }
        val token = sessionToken(created.bodyAsText())
        val home = client.get("/screens/home") { header("X-Session-Token", token) }
        assertEquals(HttpStatusCode.OK, home.status)
        assertTrue(home.bodyAsText().contains("Sair"))

        val signedOut = client.post("/actions/signout") { header("X-Session-Token", token) }
        assertTrue(signedOut.bodyAsText().contains("clearSession"))

        val after = client.get("/screens/bootstrap")
        assertTrue(after.bodyAsText().contains("\"id\":\"signup\""))
        val guarded = client.get("/screens/home")
        assertEquals(HttpStatusCode.Unauthorized, guarded.status)
        assertTrue(guarded.bodyAsText().contains("signup"))
    }

    @Test
    fun signInUsesTheLocalPasswordOnly() = testApplication {
        application { module(graph()) }
        client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupBody())
        }
        val rejected = client.post("/actions/signin") {
            contentType(ContentType.Application.Json)
            setBody("""{"fields":{"email":"ana@example.com","password":"errada12"}}""")
        }
        assertTrue(rejected.bodyAsText().contains("não conferem"))

        val accepted = client.post("/actions/signin") {
            contentType(ContentType.Application.Json)
            setBody("""{"fields":{"email":"ana@example.com","password":"segredo12"}}""")
        }
        val body = accepted.bodyAsText()
        assertTrue(body.contains("setSession"))
        assertTrue(body.contains("\"screenId\":\"home\""))
        assertTrue(!body.contains("segredo12"))
    }

    private fun sessionToken(body: String): String {
        val effects = ScreenJson.parseToJsonElement(body).jsonObject.getValue("effects").jsonArray
        val setSession = effects.first { it.jsonObject.getValue("type").jsonPrimitive.content == "setSession" }
        return setSession.jsonObject.getValue("token").jsonPrimitive.content
    }

    private fun signupBody() = """
        {"fields":{"firstName":"Ana","lastName":"Lima","email":"ana@example.com","password":"segredo12"}}
    """.trimIndent()

    private fun graph(): BffGraph {
        val catalog = ScreenCatalog()
        val events = RecordingEventPublisher()
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
            screens = ScreenQueries(catalog, sessions, cardholders, cards, funding),
            signup = SubmitSignup(
                RegisterCardholder(cardholders, enrollment, events),
                IssueCard(issuing, cards, events),
                FundOpeningBalance(funding, events),
                sessions,
                catalog,
            ),
            signIn = SubmitSignIn(SignIn(cardholders, sessions), catalog),
            signOut = SubmitSignOut(SignOut(sessions)),
        )
    }
}
