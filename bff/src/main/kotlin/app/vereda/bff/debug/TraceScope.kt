package app.vereda.bff.debug

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TraceScope(
    val requestId: String,
    val sessionKey: String?,
) : AbstractCoroutineContextElement(TraceScope) {
    companion object Key : CoroutineContext.Key<TraceScope>
}
