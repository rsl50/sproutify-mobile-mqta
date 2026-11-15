package app.vereda.bff.jit

import app.vereda.bff.BffGraph
import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.debug.TraceBuffer
import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.infrastructure.JdbcIssuedCardRepository
import app.vereda.bff.jit.infrastructure.JdbcFundingStore
import app.vereda.bff.jit.infrastructure.JitCredentials
import app.vereda.bff.module
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.JdbcCardholderRepository
import app.vereda.bff.onboarding.infrastructure.JdbcSessionRepository
import app.vereda.bff.shared.persistence.VeredaDatabase
import app.vereda.bff.spending.domain.SpendLimit
import app.vereda.bff.spending.domain.SpendWindow
import app.vereda.bff.spending.infrastructure.JdbcSpendLimitRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.util.Base64

class JitGatewayRouteTest {
    private val password = "VeredaJitGateway1!aaaa"
    private val credentials = JitCredentials("vereda", password)

    @Test
    fun approvesAndLinksTheMarqetaHopWithoutSecrets() = testApplication {
        val world = world()
        application { module(world.graph) }
        val response = client.post("/jit/gateway") {
            header("Authorization", basic(password))
            header("x-marqeta-request-trace-id", "trace-1")
            header("x-marqeta-jit-funding-token", "jit-1")
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-1", "10.00"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"amount\":10.00"))
        assertTrue(body.contains("\"token\":\"jit-1\""))
        assertFalse(body.contains("1.00"))
        assertEquals("90.00", world.store.balances("usr_1").available.toPlainString())
        assertEquals("100.00", world.store.balances("usr_1").ledger.toPlainString())

        val again = client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-1", "10.00"))
        }
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals("90.00", world.store.balances("usr_1").available.toPlainString())

        val traces = client.get("/debug/traces") { header("X-Session-Token", world.session) }.bodyAsText()
        assertTrue(traces.contains("marqeta → bff"))
        assertTrue(traces.contains("jit-1"))
        assertTrue(traces.contains("trace-1"))
        assertTrue(traces.contains("***"))
        assertFalse(traces.contains(password))
        assertFalse(traces.contains("4111111111111111"))
        assertFalse(traces.contains("Basic "))
        world.close()
    }

    @Test
    fun declinesEachRuleWithoutMovingTheLedger() {
        declineCase(opening = "3.00", token = "jit-poor", amount = "10.00", reason = "INSUFFICIENT_FUNDS")
        declineCase(state = CardState.SUSPENDED, token = "jit-card", amount = "10.00", reason = "CARD_NOT_ACTIVE")
        declineCase(limit = "20.00", token = "jit-limit", amount = "25.00", reason = "AMOUNT_LIMIT_EXCEEDED")
    }

    private fun declineCase(
        opening: String = "100.00",
        state: CardState = CardState.ACTIVE,
        limit: String? = null,
        token: String,
        amount: String,
        reason: String,
    ) = testApplication {
        val world = world(opening, state, limit)
        application { module(world.graph) }
        val response = client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(authorization(token, amount))
        }
        assertEquals(402, response.status.value)
        assertTrue(response.bodyAsText().contains(reason))
        assertEquals(opening, world.store.balances("usr_1").available.toPlainString())
        assertEquals(opening, world.store.balances("usr_1").ledger.toPlainString())
        world.close()
    }

    @Test
    fun webhookClearsReversesTimesOutAndCreditsOnce() = testApplication {
        val world = world()
        application { module(world.graph) }
        client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-1", "10.00"))
        }
        val clearing = """
            {"transactions":[{
              "gpa":{"impacted_amount":-10.00},
              "gpa_order":{"jit_funding":{
                "token":"cap-1","method":"pgfs.authorization.capture",
                "user_token":"usr_1","amount":10.00,"original_jit_funding_token":"jit-1"
              }}
            }]}
        """.trimIndent()
        val first = client.post("/jit/webhook") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(clearing)
        }
        val second = client.post("/jit/webhook") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(clearing)
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("90.00", world.store.balances("usr_1").ledger.toPlainString())

        client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-2", "5.00"))
        }
        val timeout = """
            {"gpa_order":{"funding":{"gateway_log":{"timed_out":true}},
              "jit_funding":{"token":"jit-2","method":"pgfs.authorization","user_token":"usr_1","amount":5.00}}}
        """.trimIndent()
        client.post("/jit/webhook") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(timeout)
        }
        assertEquals("90.00", world.store.balances("usr_1").available.toPlainString())

        client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-3", "4.00"))
        }
        val reversal = """
            {"gpa":{"impacted_amount":4.00},
             "gpa_order":{"jit_funding":{
               "token":"rev-1","method":"pgfs.authorization.reversal","user_token":"usr_1",
               "amount":4.00,"original_jit_funding_token":"jit-3"}}}
        """.trimIndent()
        client.post("/jit/webhook") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(reversal)
        }
        assertEquals("90.00", world.store.balances("usr_1").available.toPlainString())

        val refund = """
            {"gpa":{"impacted_amount":2.00},
             "gpa_order_unload":{"jit_funding":{
               "token":"ref-1","method":"pgfs.refund","user_token":"usr_1","amount":2.00}}}
        """.trimIndent()
        client.post("/jit/webhook") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(refund)
        }
        assertEquals("92.00", world.store.balances("usr_1").ledger.toPlainString())
        assertEquals("92.00", world.store.balances("usr_1").available.toPlainString())
        world.close()
    }

    @Test
    fun rejectsABadPasswordAndLeavesTheLedgerAlone() = testApplication {
        val world = world()
        application { module(world.graph) }
        val response = client.post("/jit/gateway") {
            header("Authorization", basic("outra-senha-que-nao-entra"))
            contentType(ContentType.Application.Json)
            setBody(authorization("jit-1", "10.00"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(response.bodyAsText().contains(password))
        assertEquals("100.00", world.store.balances("usr_1").available.toPlainString())
        val webhook = client.post("/jit/webhook") {
            contentType(ContentType.Application.Json)
            setBody("""{"transactions":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, webhook.status)
        world.close()
    }

    @Test
    fun balanceInquiryReturnsTheLedger() = testApplication {
        val world = world(opening = "40.00")
        application { module(world.graph) }
        val inquiry = """
            {"gpa_order":{"jit_funding":{
              "token":"bal-1","method":"pgfs.balanceinquiry","user_token":"usr_1"}}}
        """.trimIndent()
        val response = client.post("/jit/gateway") {
            header("Authorization", basic(password))
            contentType(ContentType.Application.Json)
            setBody(inquiry)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"ledger_balance\":40.00"))
        assertTrue(response.bodyAsText().contains("\"available_balance\":40.00"))
        assertFalse(response.bodyAsText().contains("\"amount\""))
        assertEquals("40.00", world.store.balances("usr_1").ledger.toPlainString())
        world.close()
    }

    @Test
    fun missingCredentialsLeaveTheRouteMissing() = testApplication {
        val database = VeredaDatabase.open(Files.createTempDirectory("vereda-jit").toFile().resolve("vereda"))
        val api = MarqetaCoreApi.create(MarqetaSettings("http://127.0.0.1:9", "app", "admin"))
        application { module(BffGraph.create(api, database = database)) }
        val response = client.post("/jit/gateway") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        database.close()
        api.close()
    }

    private fun world(
        opening: String = "100.00",
        state: CardState = CardState.ACTIVE,
        limit: String? = null,
    ): World {
        val database = VeredaDatabase.open(Files.createTempDirectory("vereda-jit").toFile().resolve("vereda"))
        val cardholder = Cardholder.register(
            id = CardholderId.newId(),
            name = PersonName("Ana", "Lima"),
            email = Email.parse("ana-${System.nanoTime()}@example.com"),
            password = Password.create("segredo12"),
            userToken = UserToken.of("usr_1"),
        )
        JdbcCardholderRepository(database).save(cardholder)
        JdbcIssuedCardRepository(database).save(
            IssuedCard.issue(
                cardholderId = cardholder.id,
                token = CardToken.of("card_1"),
                last4 = CardLast4.of("4242"),
                productToken = CardProductToken.of("cp_jit"),
                state = state,
            ),
        )
        if (limit != null) {
            JdbcSpendLimitRepository(database).save(
                SpendLimit(
                    cardholderId = cardholder.id,
                    controlToken = "limit-1",
                    amount = Money.parse(limit, Currency.USD),
                    usageLimit = 3,
                    window = SpendWindow.DAY,
                ),
            )
        }
        val session = JdbcSessionRepository(database).open(cardholder.id).value
        val store = JdbcFundingStore(database)
        store.open("usr_1", BigDecimal(opening))
        val api = MarqetaCoreApi.create(MarqetaSettings("http://127.0.0.1:9", "app", "admin"))
        val graph = BffGraph.create(
            api,
            database = database,
            traces = TraceBuffer(),
            jitCredentials = credentials,
        )
        return World(database, api, store, graph, session)
    }

    private fun authorization(token: String, amount: String) = """
        {
          "pan": "4111111111111111",
          "password": "$password",
          "amount": 1.00,
          "gpa_order": {"jit_funding": {
            "token": "$token",
            "method": "pgfs.authorization",
            "user_token": "usr_1",
            "amount": $amount
          }}
        }
    """.trimIndent()

    private fun basic(pass: String): String =
        "Basic " + Base64.getEncoder().encodeToString("vereda:$pass".toByteArray())

    private class World(
        val database: VeredaDatabase,
        val api: MarqetaCoreApi,
        val store: JdbcFundingStore,
        val graph: BffGraph,
        val session: String,
    ) {
        fun close() {
            database.close()
            api.close()
        }
    }
}
