package app.vereda.bff.debug

import app.vereda.trace.TraceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceBufferTest {
    @Test
    fun keepsTheNewestHundredAndFiltersByTime() {
        val buffer = TraceBuffer(capacity = 2)
        buffer.add("s", entry(1))
        buffer.add("s", entry(2))
        buffer.add("s", entry(3))
        assertEquals(listOf(2L, 3L), buffer.since("s", 0).map { it.atMs })
        assertEquals(listOf(3L), buffer.since("s", 3).map { it.atMs })
    }

    @Test
    fun adoptMovesTracesOntoTheSession() {
        val buffer = TraceBuffer()
        buffer.add("req-1", entry(10, "req-1"))
        buffer.adopt("req-1", "session-1")
        assertTrue(buffer.since("req-1", 0).isEmpty())
        assertEquals("req-1", buffer.since("session-1", 0).single().correlationId)
    }

    private fun entry(at: Long, correlationId: String = "c") = TraceEntry(
        atMs = at,
        correlationId = correlationId,
        hop = "bff → marqeta",
        method = "POST",
        target = "/users",
        status = 201,
        durationMs = 5,
    )
}
