package app.vereda.bff.infrastructure.marqeta

import app.vereda.bff.config.MarqetaSettings
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarqetaCoreApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MarqetaCoreApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MarqetaCoreApi.create(
            MarqetaSettings(
                baseUrl = server.url("/").toString().trimEnd('/'),
                applicationToken = "app-token",
                adminAccessToken = "admin-token",
            ),
        )
    }

    @After
    fun tearDown() {
        api.close()
        server.shutdown()
    }

    @Test
    fun createUserSendsBasicAuthAndOmitsPassword() = runBlocking {
        server.enqueue(json("""{"token":"usr_1","email":"ana@example.com"}""", 201))

        val user = api.createUser("Ana", "Lima", "ana@example.com")

        assertEquals("usr_1", user.token)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/users", recorded.path)
        assertEquals(Credentials.basic("app-token", "admin-token"), recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"first_name\":\"Ana\""))
        assertTrue(body.contains("\"email\":\"ana@example.com\""))
        assertFalse(body.contains("password"))
    }

    @Test
    fun listsCardProducts() = runBlocking {
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

        val products = api.listCardProducts()

        assertEquals(listOf("cp_off", "cp_on"), products.map { it.token })
        assertTrue(products.first { it.token == "cp_on" }.active)
        assertEquals("/cardproducts?count=25", server.takeRequest().path)
    }

    @Test
    fun createsCardAndReadsBalance() = runBlocking {
        server.enqueue(json("""{"token":"card_1","last_four":"4242"}""", 201))
        server.enqueue(json("""{"token":"gpa_1","amount":100}""", 201))
        server.enqueue(json("""{"gpa":{"available_balance":100.0,"currency_code":"USD"}}"""))

        val card = api.createCard("usr_1", "cp_on")
        api.fundAccount("usr_1", "100.00", "USD", "sandbox_program_funding")
        val balance = api.availableBalance("usr_1")

        assertEquals("4242", card.lastFour)
        val fund = server.takeRequest().let { server.takeRequest() }
        assertEquals("/gpaorders", fund.path)
        val fundBody = fund.body.readUtf8()
        assertTrue(fundBody.contains("\"amount\":\"100.00\""))
        assertTrue(fundBody.contains("\"funding_source_token\":\"sandbox_program_funding\""))
        assertEquals("/balances/usr_1", server.takeRequest().path)
        assertEquals(100.0, balance.amount, 0.001)
        assertEquals("USD", balance.currencyCode)
    }

    @Test
    fun failedCallHidesResponseBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error_message":"secret-body-should-not-leak"}"""),
        )

        val error = assertThrows(MarqetaCallFailed::class.java) {
            runBlocking { api.createUser("Ana", "Lima", "ana@example.com") }
        }
        assertEquals(400, error.statusCode)
        assertFalse(error.message!!.contains("secret-body-should-not-leak"))
    }

    private fun json(body: String, code: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
