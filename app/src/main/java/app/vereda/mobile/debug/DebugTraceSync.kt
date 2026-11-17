package app.vereda.mobile.debug

import app.vereda.mobile.data.BffApi
import app.vereda.mobile.data.SessionTokenStore

interface DebugTraceSync {
    suspend fun sync()
}

object NoOpDebugTraceSync : DebugTraceSync {
    override suspend fun sync() = Unit
}

class BffDebugSync(
    private val api: BffApi,
    private val sessions: SessionTokenStore,
    private val log: DebugTraceLog,
) : DebugTraceSync {
    private var since = 0L

    override suspend fun sync() {
        val session = sessions.peek()?.takeIf { it.isNotBlank() } ?: return
        val page = runCatching { api.traces(since) }.getOrNull() ?: return
        log.merge(page.traces)
        page.traces.maxOfOrNull { it.atMs }?.let { newest ->
            if (newest >= since) since = newest + 1
        }
    }
}
