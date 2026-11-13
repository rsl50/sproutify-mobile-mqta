package app.vereda.bff.funding.infrastructure

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.onboarding.domain.UserToken
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarqetaAccountFundingTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MarqetaCoreApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MarqetaCoreApi.create(
            MarqetaSettings(server.url("/").toString().trimEnd('/'), "app-token", "admin-token"),
        )
    }

    @After
    fun tearDown() {
        api.close()
        server.shutdown()
    }

    @Test
    fun fundsThroughTheSandboxProgramAndMapsAvailableBalance() = runBlocking {
        server.enqueue(json("""{"token":"gpa_1"}""", 201))
        server.enqueue(json("""{"gpa":{"available_balance":100.0,"currency_code":"USD"}}"""))
        val funding = MarqetaAccountFunding(api)

        funding.fund(UserToken.of("usr_1"), Money.openingBalance)
        val balance = funding.availableBalance(UserToken.of("usr_1"))

        val order = server.takeRequest()
        assertEquals("/gpaorders", order.path)
        val body = order.body.readUtf8()
        assertTrue(body.contains("\"amount\":\"100.00\""))
        assertTrue(body.contains("\"currency_code\":\"USD\""))
        assertTrue(body.contains("\"funding_source_token\":\"sandbox_program_funding\""))
        assertEquals("/balances/usr_1", server.takeRequest().path)
        assertEquals("USD 100.00", balance.format())
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
