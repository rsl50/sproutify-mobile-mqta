package app.vereda.bff.onboarding.domain

import app.vereda.bff.shared.DomainEvent

data class CardholderRegistered(
    val cardholderId: CardholderId,
    val email: Email,
    val userToken: UserToken,
) : DomainEvent
