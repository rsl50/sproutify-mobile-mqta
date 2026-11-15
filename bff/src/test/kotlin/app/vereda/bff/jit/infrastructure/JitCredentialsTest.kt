package app.vereda.bff.jit.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JitCredentialsTest {
    private val password = "VeredaJitGateway1!aaaa"
    private val credentials = JitCredentials("vereda", password)

    @Test
    fun acceptsTheMatchingBasicHeader() {
        assertTrue(credentials.matches(basic("vereda", password)))
    }

    @Test
    fun rejectsADifferentPasswordEvenWhenTheLengthMatches() {
        val wrong = "VeredaJitGateway1!aaab"
        assertEquals(password.length, wrong.length)
        assertTrue(!credentials.matches(basic("vereda", wrong)))
    }

    @Test
    fun rejectsAMissingHeaderAndADifferentUser() {
        assertTrue(!credentials.matches(null))
        assertTrue(!credentials.matches(basic("other", password)))
        assertTrue(!credentials.matches("Bearer $password"))
    }

    @Test
    fun passwordPolicyMatchesTheMarqetaRules() {
        assertNull(JitCredentials.policyProblem(password))
        assertTrue(JitCredentials.policyProblem("short")!!.contains("20"))
        assertTrue(JitCredentials.policyProblem("veredajitgateway1!aaaa") != null)
        assertTrue(JitCredentials.policyProblem("VEREDAJITGATEWAY1!AAAA") != null)
        assertTrue(JitCredentials.policyProblem("VeredaJitGatewayaaaaaa") != null)
        assertTrue(JitCredentials.policyProblem("VeredaJitGateway111111") != null)
    }

    private fun basic(user: String, pass: String): String {
        val encoded = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        return "Basic $encoded"
    }
}
