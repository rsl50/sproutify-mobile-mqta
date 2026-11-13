package app.vereda.mobile

import app.vereda.bff.BffGraph
import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.module
import app.vereda.bff.shared.persistence.VeredaDatabase
import app.vereda.mobile.data.BffApi
import app.vereda.mobile.data.BffScreenRepository
import app.vereda.mobile.data.SessionHeaderInterceptor
import app.vereda.mobile.data.SessionTokenStore
import app.vereda.mobile.domain.LoadResult
import app.vereda.sdui.CardComponent
import app.vereda.sdui.ScreenJson
import app.vereda.sdui.TextComponent
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.net.ServerSocket
import java.nio.file.Files

class AppAgainstBffTest {
    @Test
    fun appWalksSignupTransactionsAndSimulatedPurchase() = runBlocking {
        val marqeta = MockWebServer()
        marqeta.enqueue(json("""{"token":"usr_1","email":"ana@example.com"}""", 201))
        marqeta.enqueue(json("""{"data":[{"token":"cp_on","active":true}]}"""))
        marqeta.enqueue(json("""{"token":"card_1","last_four":"4242"}""", 201))
        marqeta.enqueue(json("""{"token":"gpa_1"}""", 201))
        marqeta.enqueue(json("""{"gpa":{"available_balance":100.0,"currency_code":"USD"}}"""))
        marqeta.enqueue(
            json(
                """
                {"data":[{"token":"tx_1","amount":8.0,"state":"PENDING","currency_code":"USD",
                  "card_acceptor":{"name":"Cafe"}}]}
                """.trimIndent(),
            ),
        )
        marqeta.enqueue(
            json(
                """
                {"transaction":{"token":"tx_2","amount":8.0,"state":"PENDING","currency_code":"USD",
                  "card_acceptor":{"name":"Cafe"}}}
                """.trimIndent(),
                201,
            ),
        )
        marqeta.enqueue(json("""{"gpa":{"available_balance":92.0,"currency_code":"USD"}}"""))
        marqeta.start()

        val database = VeredaDatabase.open(Files.createTempDirectory("vereda-e2e").toFile().resolve("vereda"))
        val api = MarqetaCoreApi.create(
            MarqetaSettings(marqeta.url("/").toString().trimEnd('/'), "app-token", "admin-token"),
        )
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            module(BffGraph.create(api, database = database))
        }
        server.start(wait = false)
        try {
            val sessions = MemorySession()
            val repository = BffScreenRepository(
                api = Retrofit.Builder()
                    .baseUrl("http://127.0.0.1:$port/")
                    .client(OkHttpClient.Builder().addInterceptor(SessionHeaderInterceptor(sessions)).build())
                    .addConverterFactory(ScreenJson.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(BffApi::class.java),
                sessions = sessions,
                transportMessage = "offline",
            )
            val signupScreen = repository.load("bootstrap") as LoadResult.Ready
            assertEquals("signup", signupScreen.screen.id)

            val created = repository.submit(
                "actions/signup",
                mapOf(
                    "firstName" to "Ana",
                    "lastName" to "Lima",
                    "email" to "ana@example.com",
                    "password" to "segredo12",
                ),
            )
            val token = created.effects.first { it.type == "setSession" }.token
            repository.saveSession(token)
            val home = created.effects.first { it.type == "showScreen" }.screen!!
            assertEquals("4242", home.components.filterIsInstance<CardComponent>().single().lastFour)

            val transactions = repository.load("transactions") as LoadResult.Ready
            assertTrue(
                transactions.screen.components.filterIsInstance<TextComponent>().any { it.value.contains("Cafe") },
            )

            val simulated = repository.submit(
                "actions/simulate-purchase",
                mapOf("amount" to "8.00", "merchant" to "Cafe"),
            )
            assertTrue(simulated.effects.any { it.type == "navigate" && it.screenId == "home" })

            val refreshed = repository.load("home") as LoadResult.Ready
            assertEquals(
                "USD 92.00",
                refreshed.screen.components.filterIsInstance<CardComponent>().single().balanceText,
            )

            val paths = List(marqeta.requestCount) { marqeta.takeRequest().path.orEmpty() }
            assertTrue(paths.any { it.startsWith("/transactions") })
            assertTrue(paths.contains("/simulations/cardtransactions/authorization"))
        } finally {
            server.stop(200, 1_000)
            api.close()
            database.close()
            marqeta.shutdown()
        }
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}

private class MemorySession : SessionTokenStore {
    private var token: String? = null
    override suspend fun hydrate() = Unit
    override fun peek(): String? = token
    override suspend fun update(token: String?) {
        this.token = token
    }
}
