package app.vereda.bff.issuing.application

import app.vereda.bff.issuing.domain.CardStateChanged
import app.vereda.bff.issuing.domain.CardStateControl
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.shared.DomainEventPublisher

class ChangeCardState(
    private val cards: IssuedCardRepository,
    private val control: CardStateControl,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(card: IssuedCard): IssuedCard {
        val requested = card.state.toggled()
        val applied = control.transition(card.token, requested)
        val updated = card.withState(applied)
        cards.save(updated)
        events.publish(CardStateChanged(card.token, card.state, applied))
        return updated
    }
}