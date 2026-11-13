package app.vereda.bff.onboarding.application

import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.InvalidCredentials
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken

class SignIn(
    private val cardholders: CardholderRepository,
    private val sessions: SessionRepository,
) {
    fun execute(emailRaw: String, password: String): SessionToken {
        val email = Email.parse(emailRaw)
        val cardholder = cardholders.findByEmail(email) ?: throw InvalidCredentials()
        if (!cardholder.password.matches(password)) throw InvalidCredentials()
        return sessions.open(cardholder.id)
    }
}

class SignOut(
    private val sessions: SessionRepository,
) {
    fun execute(token: SessionToken) {
        sessions.close(token)
    }
}
