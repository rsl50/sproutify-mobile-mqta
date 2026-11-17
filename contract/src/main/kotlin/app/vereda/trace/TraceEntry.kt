package app.vereda.trace

import kotlinx.serialization.Serializable

@Serializable
data class TraceEntry(
    val atMs: Long,
    val correlationId: String,
    val hop: String,
    val method: String,
    val target: String,
    val status: Int,
    val durationMs: Long,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestBody: String = "",
    val responseBody: String = "",
) {
    fun render(): String = buildString {
        appendLine("[$hop] $correlationId")
        appendLine("$method $target")
        appendLine("status $status · $durationMs ms")
        requestHeaders.toSortedMap().forEach { (name, value) -> appendLine("$name: $value") }
        if (requestBody.isNotBlank()) appendLine(requestBody)
        appendLine("---")
        responseHeaders.toSortedMap().forEach { (name, value) -> appendLine("$name: $value") }
        if (responseBody.isNotBlank()) appendLine(responseBody)
    }
}

@Serializable
data class TracePage(
    val traces: List<TraceEntry> = emptyList(),
)
