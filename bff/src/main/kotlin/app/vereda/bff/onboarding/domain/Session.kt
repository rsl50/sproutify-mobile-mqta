package app.vereda.bff.onboarding.domain

import java.util.UUID

@JvmInline
value class SessionToken private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "session token is blank" }
    }

    companion object {
        fun of(raw: String): SessionToken = SessionToken(raw.trim())

        fun newToken(): SessionToken = SessionToken(UUID.randomUUID().toString())
    }
}

class InvalidCredentials : RuntimeException()

interface SessionRepository {
    fun open(cardholderId: CardholderId): SessionToken

    fun cardholderId(token: SessionToken): CardholderId?

    fun close(token: SessionToken)
}
