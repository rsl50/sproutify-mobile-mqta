package app.vereda.bff.jit.setup

import app.vereda.bff.jit.infrastructure.JitCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class JitSandboxSetupTest {
    @Test
    fun skipsWithoutPrintingThePassword() {
        val password = "VeredaJitGateway1!aaaa"
        val output = capture {
            val code = runJitSandboxSetup(
                env = mapOf(
                    "VEREDA_JIT_BASIC_USER" to "vereda",
                    "VEREDA_JIT_BASIC_PASSWORD" to password,
                ),
                files = emptyList(),
            ) { _, _ -> error("nao deveria chamar a Marqeta") }
            assertEquals(0, code)
        }
        assertTrue(output.contains("ignorado"))
        assertFalse(output.contains(password))
    }

    @Test
    fun rejectsAPasswordOutsideTheMarqetaPatternWithoutEchoingIt() {
        val output = capture {
            val reason = setupSkipReason(
                env = mapOf(
                    "MARQETA_APPLICATION_TOKEN" to "app-secret",
                    "MARQETA_ADMIN_ACCESS_TOKEN" to "admin-secret",
                    "VEREDA_JIT_BASIC_USER" to "vereda",
                    "VEREDA_JIT_BASIC_PASSWORD" to "curta",
                    "VEREDA_PUBLIC_URL" to "https://tunnel.example",
                ),
                files = emptyList(),
            )
            println(reason)
        }
        assertTrue(output.contains("20"))
        assertFalse(output.contains("curta"))
        assertFalse(output.contains("admin-secret"))
    }

    @Test
    fun cardProductPayloadUsesTheGatewayFundingSource() {
        val body = cardProductBody("vereda_jit_card", "vereda_jit_gateway", includeIdentity = true)
        assertTrue(body.contains("programgateway_funding_source"))
        assertTrue(body.contains("GATEWAY"))
        assertTrue(body.contains("vereda_jit_gateway"))
        val source = fundingSourceBody(
            "vereda_jit_gateway",
            "https://tunnel.example/jit/gateway",
            JitCredentials("vereda", "VeredaJitGateway1!aaaa"),
            includeIdentity = true,
        )
        assertTrue(source.contains("https://tunnel.example/jit/gateway"))
        assertFalse(source.contains("app-secret"))
    }

    @Test
    fun jitSmokeSkipsWhenThePublicUrlIsMissingAndHidesThePassword() {
        val password = "VeredaJitGateway1!aaaa"
        val reason = jitSmokeSkipReason(
            env = mapOf(
                "VEREDA_JIT_BASIC_USER" to "vereda",
                "VEREDA_JIT_BASIC_PASSWORD" to password,
            ),
            files = emptyList(),
        )
        assertTrue(reason!!.contains("VEREDA_PUBLIC_URL"))
        assertFalse(reason.contains(password))
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
