package app.vereda.bff.jit.infrastructure

import app.vereda.bff.debug.TraceBuffer
import app.vereda.bff.jit.application.ApplyFundingNotification
import app.vereda.bff.jit.application.DecideFunding
import app.vereda.bff.jit.domain.FundingBook
import app.vereda.bff.jit.domain.FundingMessage
import app.vereda.trace.TraceEntry
import app.vereda.trace.TraceRedactor
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText

class JitGateway(
    private val credentials: JitCredentials,
    private val decide: DecideFunding,
    private val notify: ApplyFundingNotification,
    private val traces: TraceBuffer?,
    private val sessionTokens: (String) -> List<String>,
) {
    suspend fun gateway(call: ApplicationCall) = exchange(call, "/jit/gateway", webhook = false)

    suspend fun webhook(call: ApplicationCall) = exchange(call, "/jit/webhook", webhook = true)

    private suspend fun exchange(call: ApplicationCall, path: String, webhook: Boolean) {
        val started = System.nanoTime()
        val atMs = System.currentTimeMillis()
        val authorization = call.request.header("Authorization")
        val requestHeaders = relevantHeaders(call)
        if (!credentials.matches(authorization)) {
            val body = """{"error":"Não autorizado."}"""
            record(atMs, started, path, 401, requestHeaders, "", body, "unauthorized")
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return
        }
        val requestBody = call.receiveText()
        val messages = JitMessageParser.parse(requestBody)
        val correlation = messages.firstOrNull()?.let { it.originalToken ?: it.token }
            ?: call.request.header("x-marqeta-request-trace-id")
            ?: "jit"
        val response = if (webhook) {
            messages.forEach { notify.execute(it) }
            200 to "{}"
        } else if (messages.isEmpty()) {
            val reply = FundingBook.reply(
                FundingMessage("unknown", "pgfs.authorization", "unknown", null, null, null, false),
                java.math.BigDecimal.ZERO,
                FundingBook.INVALID_AMOUNT,
                null,
                null,
            )
            reply.status to reply.body
        } else {
            val reply = decide.execute(messages.first())
            reply.status to reply.body
        }
        record(atMs, started, path, response.first, requestHeaders, requestBody, response.second, correlation, messages)
        call.respondText(response.second, ContentType.Application.Json, HttpStatusCode.fromValue(response.first))
    }

    private fun record(
        atMs: Long,
        started: Long,
        path: String,
        status: Int,
        requestHeaders: Map<String, String>,
        requestBody: String,
        responseBody: String,
        correlation: String,
        messages: List<FundingMessage> = emptyList(),
    ) {
        val buffer = traces ?: return
        val entry = TraceEntry(
            atMs = atMs,
            correlationId = correlation,
            hop = HOP,
            method = "POST",
            target = path,
            status = status,
            durationMs = (System.nanoTime() - started) / 1_000_000,
            requestHeaders = TraceRedactor.redactHeaders(requestHeaders),
            responseHeaders = emptyMap(),
            requestBody = TraceRedactor.redactBody(requestBody),
            responseBody = TraceRedactor.redactBody(responseBody),
        )
        val keys = messages.map { it.userToken }.distinct().flatMap(sessionTokens).distinct()
        val targets = keys.ifEmpty { listOf(correlation) }
        targets.forEach { buffer.add(it, entry) }
    }

    private fun relevantHeaders(call: ApplicationCall): Map<String, String> = buildMap {
        listOf(
            "Authorization",
            "x-marqeta-request-trace-id",
            "x-marqeta-jit-funding-token",
            "x-marqeta-jit-funding-gateway-version",
            "Content-Type",
        ).forEach { name ->
            call.request.header(name)?.let { put(name, it) }
        }
    }

    companion object {
        const val HOP = "marqeta → bff"
    }
}

class JdbcJitSessions(
    private val database: app.vereda.bff.shared.persistence.VeredaDatabase,
) {
    fun tokensFor(userToken: String): List<String> = database.read { connection ->
        connection.prepareStatement(
            """
            SELECT s.token FROM app_session s
            JOIN cardholder c ON c.id = s.cardholder_id
            WHERE c.user_token = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userToken)
            statement.executeQuery().use { rows ->
                generateSequence { if (rows.next()) rows.getString("token") else null }.toList()
            }
        }
    }
}
