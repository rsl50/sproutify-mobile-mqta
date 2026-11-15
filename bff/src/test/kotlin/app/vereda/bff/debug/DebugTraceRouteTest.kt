package app.vereda.bff.debug

import app.vereda.bff.BffGraph
import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.module
import app.vereda.bff.shared.persistence.VeredaDatabase
import app.vereda.sdui.ScreenJson
import app.vereda.trace.TracePage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DebugTraceRouteTest {
    @Test
    fun tracesLinkTheAppCallToMarqetaAndHideSecrets() = testApplication {
        val marqeta = MockWebServer()
        marqeta.enqueue(json("""{"token":"usr_1","email":"ana@example.com","pan":"4111111111111111"}""", 201))
        marqeta.enqueue(json("""{"data":[{"token":"cp_on","active":true}]}"""))
        marqeta.enqueue(json("""{"token":"card_1","last_four":"4242"}""", 201))
        marqeta.enqueue(json("""{"token":"gpa_1"}""", 201))
        marqeta.enqueue(json("""{"gpa":{"available_balance":100.0,"currency_code":"USD"}}"""))
        marqeta.start()
        val traces = TraceBuffer()
        val api = MarqetaCoreApi.create(
            MarqetaSettings(marqeta.url("/").toString().trimEnd('/'), "app-secret-value", "admin-secret-value"),
            traces = traces,
        )
        val database = VeredaDatabase.open(Files.createTempDirectory("vereda-trace").toFile().resolve("vereda"))
        application { module(BffGraph.create(api, database = database, traces = traces)) }
        try {
            val created = client.post("/actions/signup") {
                header("X-Request-Id", "corr-1")
                contentType(ContentType.Application.Json)
                setBody(
                    """{"fields":{"firstName":"Ana","lastName":"Lima","email":"ana@example.com","password":"segredo12"}}""",
                )
            }
            assertEquals(HttpStatusCode.OK, created.status)
            val parsed = ScreenJson.parseToJsonElement(created.bodyAsText()).jsonObject
            val effects = parsed.getValue("effects") as kotlinx.serialization.json.JsonArray
            val sessionToken = effects.first {
                it.jsonObject.getValue("type").jsonPrimitive.content == "setSession"
            }.jsonObject.getValue("token").jsonPrimitive.content

            val traceResponse = client.get("/debug/traces?since=0") {
                header("X-Session-Token", sessionToken)
            }
            assertEquals(HttpStatusCode.OK, traceResponse.status)
            val body = traceResponse.bodyAsText()
            val page = ScreenJson.decodeFromString(TracePage.serializer(), body)
            assertTrue(page.traces.any { it.correlationId == "corr-1" && it.target.startsWith("/users") })
            assertTrue(page.traces.any { it.hop == "bff → marqeta" })
            assertTrue(body.contains("usr_1"))
            assertTrue(body.contains("card_1") || page.traces.any { it.target.contains("cards") })
            assertFalse(body.contains("segredo12"))
            assertFalse(body.contains("4111111111111111"))
            assertFalse(body.contains("app-secret-value"))
            assertFalse(body.contains("admin-secret-value"))
            assertFalse(body.contains("Basic "))
            val forwarded = marqeta.takeRequest()
            assertEquals("corr-1", forwarded.getHeader("X-Request-Id"))
            assertEquals(null, forwarded.getHeader("X-Vereda-Trace-Session"))
        } finally {
            api.close()
            database.close()
            marqeta.shutdown()
        }
    }

    @Test
    fun debugRouteIsMissingWhenTracesAreOff() = testApplication {
        val api = MarqetaCoreApi.create(
            MarqetaSettings("http://127.0.0.1:9", "app-token", "admin-token"),
        )
        val database = VeredaDatabase.open(Files.createTempDirectory("vereda-trace-off").toFile().resolve("vereda"))
        application { module(BffGraph.create(api, database = database, traces = null)) }
        try {
            val response = client.get("/debug/traces")
            assertEquals(HttpStatusCode.NotFound, response.status)
        } finally {
            api.close()
            database.close()
        }
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
