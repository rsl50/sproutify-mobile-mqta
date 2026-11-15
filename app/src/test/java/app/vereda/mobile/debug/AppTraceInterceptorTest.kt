package app.vereda.mobile.debug

import app.vereda.trace.TraceRedactor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTraceInterceptorTest {
    @Test
    fun recordsTheHopAndMasksTheSessionToken() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"password":"segredo12","user_token":"usr_1"}"""),
        )
        server.start()
        val log = DebugTraceLog()
        val client = OkHttpClient.Builder().addInterceptor(AppTraceInterceptor(log)).build()
        try {
            client.newCall(
                Request.Builder()
                    .url(server.url("/actions/signup"))
                    .header("X-Session-Token", "session-secret")
                    .post("""{"password":"segredo12"}""".toRequestBody("application/json".toMediaType()))
                    .build(),
            ).execute().close()
            val text = log.text.value
            assertTrue(text.contains("[app → bff]"))
            assertTrue(text.contains("POST /actions/signup"))
            assertTrue(text.contains("status 200"))
            assertTrue(text.contains("usr_1"))
            assertFalse(text.contains("session-secret"))
            assertFalse(text.contains("segredo12"))
            assertTrue(text.contains(TraceRedactor.MASK))
            assertTrue(server.takeRequest().getHeader("X-Request-Id")!!.isNotBlank())
        } finally {
            server.shutdown()
        }
    }
}
