package app.vereda.bff.issuing.infrastructure

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.domain.NoActiveCardProduct
import app.vereda.bff.onboarding.domain.UserToken
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarqetaCardIssuingTest {
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
    fun mapsTheFirstActiveProductIntoDomainValues() = runBlocking {
        server.enqueue(
            json(
                """
                {"data":[
                  {"token":"cp_off","active":false},
                  {"token":"cp_on","active":true}
                ]}
                """.trimIndent(),
            ),
        )
        server.enqueue(json("""{"token":"card_9","last_four":"4242"}""", 201))

        val draft = MarqetaCardIssuing(api).issueFor(UserToken.of("usr_1"))

        assertEquals("4242", draft.last4.value)
        assertEquals("cp_on", draft.productToken.value)
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("/cards", create.path)
        assertTrue(create.body.readUtf8().contains("\"card_product_token\":\"cp_on\""))
    }

    @Test
    fun failsWhenTheProgramHasNoActiveProduct() {
        server.enqueue(json("""{"data":[{"token":"cp_off","active":false}]}"""))
        assertThrows(NoActiveCardProduct::class.java) {
            runBlocking { MarqetaCardIssuing(api).issueFor(UserToken.of("usr_1")) }
        }
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
