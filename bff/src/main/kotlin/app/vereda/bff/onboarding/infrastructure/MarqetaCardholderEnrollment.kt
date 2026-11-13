package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken

class MarqetaCardholderEnrollment(
    private val api: MarqetaCoreApi,
) : CardholderEnrollment {
    override suspend fun enroll(name: PersonName, email: Email): UserToken {
        val remote = api.createUser(
            firstName = name.first,
            lastName = name.last,
            email = email.value,
        )
        return UserToken.of(remote.token)
    }
}
