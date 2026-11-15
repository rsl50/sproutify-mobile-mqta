package app.vereda.bff.debug

import app.vereda.trace.TraceEntry
import java.util.ArrayDeque

class TraceBuffer(
    private val capacity: Int = 100,
) {
    private val byKey = linkedMapOf<String, ArrayDeque<TraceEntry>>()

    fun add(key: String, entry: TraceEntry) = synchronized(byKey) {
        if (key.isBlank()) return
        val queue = byKey.getOrPut(key) { ArrayDeque() }
        queue.addLast(entry)
        while (queue.size > capacity) queue.removeFirst()
    }

    fun adopt(fromKey: String, toKey: String) = synchronized(byKey) {
        if (fromKey.isBlank() || toKey.isBlank() || fromKey == toKey) return
        val moved = byKey.remove(fromKey) ?: return
        val destination = byKey.getOrPut(toKey) { ArrayDeque() }
        moved.forEach(destination::addLast)
        while (destination.size > capacity) destination.removeFirst()
    }

    fun since(key: String, sinceMs: Long): List<TraceEntry> = synchronized(byKey) {
        byKey[key].orEmpty().filter { it.atMs >= sinceMs }
    }

    companion object {
        fun enabled(env: Map<String, String> = System.getenv()): Boolean {
            val raw = env["VEREDA_DEBUG_TRACES"]?.trim()?.lowercase()
            return raw != "false" && raw != "0" && raw != "off"
        }
    }
}
