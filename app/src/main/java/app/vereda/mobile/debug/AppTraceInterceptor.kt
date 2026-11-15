package app.vereda.mobile.debug

import app.vereda.trace.TraceEntry
import app.vereda.trace.TraceRedactor
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.util.UUID

class AppTraceInterceptor(
    private val log: DebugTraceLog,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val existing = chain.request().header("X-Request-Id")?.takeIf { it.isNotBlank() }
        val correlationId = existing ?: UUID.randomUUID().toString()
        val request = chain.request().newBuilder()
            .header("X-Request-Id", correlationId)
            .build()
        if (request.url.encodedPath.endsWith("/debug/traces")) {
            return chain.proceed(request)
        }
        val started = System.nanoTime()
        val atMs = System.currentTimeMillis()
        val requestBody = requestBody(request)
        val response = try {
            chain.proceed(request)
        } catch (error: Exception) {
            log.add(
                entry(
                    atMs = atMs,
                    correlationId = correlationId,
                    request = request,
                    requestBody = requestBody,
                    status = 0,
                    durationMs = elapsed(started),
                    responseHeaders = emptyMap(),
                    responseBody = "",
                ),
            )
            throw error
        }
        log.add(
            entry(
                atMs = atMs,
                correlationId = correlationId,
                request = request,
                requestBody = requestBody,
                status = response.code,
                durationMs = elapsed(started),
                responseHeaders = response.headers.names().associateWith { response.headers[it].orEmpty() },
                responseBody = runCatching { response.peekBody(MAX_BODY).string() }.getOrDefault(""),
            ),
        )
        return response
    }

    private fun entry(
        atMs: Long,
        correlationId: String,
        request: okhttp3.Request,
        requestBody: String,
        status: Int,
        durationMs: Long,
        responseHeaders: Map<String, String>,
        responseBody: String,
    ) = TraceEntry(
        atMs = atMs,
        correlationId = correlationId,
        hop = HOP,
        method = request.method,
        target = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: ""),
        status = status,
        durationMs = durationMs,
        requestHeaders = TraceRedactor.redactHeaders(
            request.headers.names().associateWith { request.headers[it].orEmpty() },
        ),
        responseHeaders = TraceRedactor.redactHeaders(responseHeaders),
        requestBody = TraceRedactor.redactBody(requestBody),
        responseBody = TraceRedactor.redactBody(responseBody),
    )

    private fun requestBody(request: okhttp3.Request): String {
        val body = request.body ?: return ""
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.getOrDefault("")
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    companion object {
        const val HOP = "app → bff"
        private const val MAX_BODY = 64L * 1024L
    }
}
