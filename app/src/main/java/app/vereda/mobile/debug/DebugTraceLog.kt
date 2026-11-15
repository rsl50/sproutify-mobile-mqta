package app.vereda.mobile.debug

import app.vereda.trace.TraceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class DebugTraceLog(
    private val capacity: Int = 100,
) {
    private val entries = ArrayDeque<TraceEntry>()
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private var clearedAt = 0L

    fun add(entry: TraceEntry) {
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
            publish()
        }
    }

    fun merge(remote: List<TraceEntry>) {
        synchronized(entries) {
            val known = entries.map { it.identity() }.toMutableSet()
            remote.filter { it.atMs >= clearedAt && it.identity() !in known }
                .sortedBy { it.atMs }
                .forEach { entries.addLast(it) }
            while (entries.size > capacity) entries.removeFirst()
            publish()
        }
    }

    fun clear() {
        synchronized(entries) {
            clearedAt = System.currentTimeMillis()
            entries.clear()
            publish()
        }
    }

    private fun publish() {
        _text.value = entries.filter { it.atMs >= clearedAt }
            .joinToString(separator = "\n") { it.render() }
    }

    private fun TraceEntry.identity(): String =
        "$atMs|$hop|$correlationId|$method|$target|$status"
}
