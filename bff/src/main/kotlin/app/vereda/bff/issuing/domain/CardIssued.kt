package app.vereda.bff.issuing.domain

import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.shared.DomainEvent

data class CardIssued(
    val cardholderId: CardholderId,
    val cardToken: CardToken,
    val last4: CardLast4,
) : DomainEvent
