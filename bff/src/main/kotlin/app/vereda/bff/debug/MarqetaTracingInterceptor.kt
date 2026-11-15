package app.vereda.bff.debug

import app.vereda.trace.TraceEntry
import app.vereda.trace.TraceRedactor
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class MarqetaTracingInterceptor(
    private val buffer: TraceBuffer,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val started = System.nanoTime()
        val atMs = System.currentTimeMillis()
        val incoming = chain.request()
        val correlationId = incoming.header("X-Request-Id").orEmpty()
        val sessionKey = incoming.header(SESSION_HEADER)
        val request = incoming.newBuilder().removeHeader(SESSION_HEADER).build()
        val requestBody = requestBody(request)
        val response = chain.proceed(request)
        val durationMs = (System.nanoTime() - started) / 1_000_000
        val responseBody = runCatching { response.peekBody(MAX_BODY).string() }.getOrDefault("")
        val entry = TraceEntry(
            atMs = atMs,
            correlationId = correlationId,
            hop = HOP,
            method = request.method,
            target = request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty(),
            status = response.code,
            durationMs = durationMs,
            requestHeaders = TraceRedactor.redactHeaders(request.headers.toMap()),
            responseHeaders = TraceRedactor.redactHeaders(response.headers.toMap()),
            requestBody = TraceRedactor.redactBody(requestBody),
            responseBody = TraceRedactor.redactBody(responseBody),
        )
        val key = sessionKey?.takeIf { it.isNotBlank() } ?: correlationId
        buffer.add(key, entry)
        return response
    }

    private fun requestBody(request: okhttp3.Request): String {
        val body = request.body ?: return ""
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.getOrDefault("")
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> =
        names().associateWith { name -> this[name].orEmpty() }

    companion object {
        const val HOP = "bff → marqeta"
        const val SESSION_HEADER = "X-Vereda-Trace-Session"
        private const val MAX_BODY = 64L * 1024L
    }
}
