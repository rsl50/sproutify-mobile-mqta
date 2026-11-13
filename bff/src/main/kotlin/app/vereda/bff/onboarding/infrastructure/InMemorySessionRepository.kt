package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken
import java.util.concurrent.ConcurrentHashMap

class InMemorySessionRepository : SessionRepository {
    private val sessions = ConcurrentHashMap<String, String>()

    override fun open(cardholderId: CardholderId): SessionToken {
        val token = SessionToken.newToken()
        sessions[token.value] = cardholderId.value
        return token
    }

    override fun cardholderId(token: SessionToken): CardholderId? =
        sessions[token.value]?.let(::CardholderId)

    override fun close(token: SessionToken) {
        sessions.remove(token.value)
    }
}
