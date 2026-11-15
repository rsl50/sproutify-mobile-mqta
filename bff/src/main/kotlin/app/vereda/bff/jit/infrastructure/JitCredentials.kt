package app.vereda.bff.jit.infrastructure

import java.security.MessageDigest
import java.util.Base64

class JitCredentials(
    val username: String,
    val password: String,
) {
    fun matches(authorizationHeader: String?): Boolean {
        val presented = decode(authorizationHeader)
        val expected = "$username\u0000$password".toByteArray(Charsets.UTF_8)
        val same = MessageDigest.isEqual(digest(presented ?: ByteArray(0)), digest(expected))
        return presented != null && same
    }

    private fun decode(header: String?): ByteArray? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        if (!trimmed.startsWith("Basic ", ignoreCase = true)) return null
        val decoded = try {
            Base64.getDecoder().decode(trimmed.substring(6).trim())
        } catch (_: IllegalArgumentException) {
            return null
        }
        val text = decoded.toString(Charsets.UTF_8)
        val colon = text.indexOf(':')
        if (colon < 0) return null
        val user = text.substring(0, colon)
        val pass = text.substring(colon + 1)
        return "$user\u0000$pass".toByteArray(Charsets.UTF_8)
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private const val SYMBOLS = "@#\$%!^&*()\\_+~`-=[],;:'\"./<>?"

        fun policyProblem(password: String): String? = when {
            password.length !in 20..50 ->
                "a senha JIT precisa ter de 20 a 50 caracteres"
            password.none { it.isLowerCase() } || password.none { it.isUpperCase() } ->
                "a senha JIT precisa ter letra maiúscula e minúscula"
            password.none { it.isDigit() } ->
                "a senha JIT precisa ter um número"
            password.none { it in SYMBOLS } ->
                "a senha JIT precisa ter um símbolo aceito pela Marqeta"
            else -> null
        }
    }
}
