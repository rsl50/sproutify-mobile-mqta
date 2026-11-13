package app.vereda.bff.experience

import app.vereda.bff.BffGraph
import app.vereda.bff.module
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCardDraft
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.sdui.ScreenJson
import io.ktor.client.request.get
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

class SignupRouteTest {
    @Test
    fun bootstrapScreenComesFromTheServer() = testApplication {
        application { module(graph()) }
        val response = client.get("/screens/bootstrap")
        assertEquals(HttpStatusCode.OK, response.status)
        val json = ScreenJson.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("signup", json.getValue("id").jsonPrimitive.content)
        val labels = json.getValue("components").jsonArray.joinToString(" ")
        assertTrue(labels.contains("E-mail"))
        assertTrue(labels.contains("actions/signup"))
    }

    @Test
    fun signupReturnsAServerScreenAndKeepsThePasswordOffTheEnrollmentPort() = testApplication {
        val enrollment = RecordingEnrollment()
        application { module(graph(enrollment)) }
        val response = client.post("/actions/signup") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"fields":{
                  "firstName":"Ana",
                  "lastName":"Lima",
                  "email":"ana@example.com",
                  "password":"segredo12"
                }}
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("showScreen"))
        assertTrue(body.contains("Olá, Ana Lima"))
        assertTrue(body.contains("Final 4242"))
        assertEquals(listOf("ana@example.com"), enrollment.emails)
        assertTrue(enrollment.payloads.none { it.contains("segredo12") })
    }

    private fun graph(enrollment: CardholderEnrollment = RecordingEnrollment()): BffGraph {
        val catalog = ScreenCatalog()
        val events = RecordingEventPublisher()
        val register = RegisterCardholder(
            InMemoryCardholderRepository(),
            enrollment,
            events,
        )
        val issueCard = IssueCard(FixedIssuing(), InMemoryIssuedCardRepository(), events)
        return BffGraph(ScreenQueries(catalog), SubmitSignup(register, issueCard, catalog))
    }

    private class RecordingEnrollment : CardholderEnrollment {
        val emails = mutableListOf<String>()
        val payloads = mutableListOf<String>()

        override suspend fun enroll(name: PersonName, email: Email): UserToken {
            emails += email.value
            payloads += "${name.first}|${name.last}|${email.value}"
            return UserToken.of("usr_1")
        }
    }

    private class FixedIssuing : CardIssuing {
        override suspend fun issueFor(userToken: UserToken): IssuedCardDraft = IssuedCardDraft(
            token = CardToken.of("card_1"),
            last4 = CardLast4.of("4242"),
            productToken = CardProductToken.of("cp_on"),
        )
    }
}
