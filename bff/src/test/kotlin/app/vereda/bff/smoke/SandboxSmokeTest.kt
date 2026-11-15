package app.vereda.bff.smoke

import app.vereda.bff.config.MarqetaSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class SandboxSmokeTest {
    @Test
    fun skipsWhenKeysAreMissing() {
        val output = capture {
            val code = runSandboxSmoke(env = emptyMap(), files = emptyList()) { error("nao deveria chamar o sandbox") }
            assertEquals(0, code)
        }
        assertTrue(output.contains("ignorado"))
        assertTrue(output.contains("MARQETA_APPLICATION_TOKEN"))
    }

    @Test
    fun reportDoesNotPrintTheTokens() {
        val output = capture {
            val code = runSandboxSmoke(
                env = mapOf(
                    "MARQETA_APPLICATION_TOKEN" to "app-secret-value",
                    "MARQETA_ADMIN_ACCESS_TOKEN" to "admin-secret-value",
                ),
                files = emptyList(),
            ) {
                println("sandboxSmokeTest ok")
                println("card last4=4242")
            }
            assertEquals(0, code)
        }
        assertTrue(output.contains("last4=4242"))
        assertTrue(!output.contains("app-secret-value"))
        assertTrue(!output.contains("admin-secret-value"))
    }

    private fun capture(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }
}
