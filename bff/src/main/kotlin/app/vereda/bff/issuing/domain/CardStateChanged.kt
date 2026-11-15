package app.vereda.bff.issuing.domain

import app.vereda.bff.shared.DomainEvent

data class CardStateChanged(
    val cardToken: CardToken,
    val from: CardState,
    val to: CardState,
) : DomainEvent

interface CardStateControl {
    suspend fun transition(cardToken: CardToken, state: CardState): CardState
}