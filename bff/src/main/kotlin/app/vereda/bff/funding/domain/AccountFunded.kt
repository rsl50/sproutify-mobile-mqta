package app.vereda.bff.funding.domain

import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEvent

data class AccountFunded(
    val userToken: UserToken,
    val amount: Money,
) : DomainEvent
