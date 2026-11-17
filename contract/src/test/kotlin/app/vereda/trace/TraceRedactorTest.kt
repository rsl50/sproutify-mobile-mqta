package app.vereda.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRedactorTest {
    @Test
    fun masksAuthorizationSessionPasswordPanCvvAndExpiry() {
        val headers = TraceRedactor.redactHeaders(
            mapOf(
                "Authorization" to "Basic YXBwOnNlY3JldA==",
                "X-Session-Token" to "session-secret",
                "X-Request-Id" to "req-1",
                "Content-Type" to "application/json",
            ),
        )
        assertEquals(TraceRedactor.MASK, headers["Authorization"])
        assertEquals(TraceRedactor.MASK, headers["X-Session-Token"])
        assertEquals("req-1", headers["X-Request-Id"])

        val body = TraceRedactor.redactBody(
            """
            {
              "password": "segredo12",
              "application_token": "app-secret",
              "admin_access_token": "admin-secret",
              "pan": "4111111111111111",
              "cvv": "123",
              "expiration": "12/30",
              "user_token": "usr_1",
              "card_token": "card_1",
              "card_product_token": "cp_on",
              "note": "cartao 5500000000000004",
              "last_four": "0004"
            }
            """.trimIndent(),
        )
        assertFalse(body.contains("segredo12"))
        assertFalse(body.contains("app-secret"))
        assertFalse(body.contains("admin-secret"))
        assertFalse(body.contains("4111111111111111"))
        assertFalse(body.contains("5500000000000004"))
        assertFalse(body.contains("\"123\""))
        assertFalse(body.contains("12/30"))
        assertTrue(body.contains("usr_1"))
        assertTrue(body.contains("card_1"))
        assertTrue(body.contains("cp_on"))
        assertTrue(body.contains("0004"))
        assertTrue(body.contains(TraceRedactor.MASK))
    }

    @Test
    fun resourceTokensAreNotSensitiveKeys() {
        assertFalse(TraceRedactor.isSensitiveKey("user_token"))
        assertFalse(TraceRedactor.isSensitiveKey("card_token"))
        assertFalse(TraceRedactor.isSensitiveKey("token"))
        assertTrue(TraceRedactor.isSensitiveKey("password"))
        assertTrue(TraceRedactor.isSensitiveKey("application_token"))
        assertTrue(TraceRedactor.isSensitiveKey("admin_access_token"))
        assertTrue(TraceRedactor.isSensitiveKey("cvv2"))
    }
}
