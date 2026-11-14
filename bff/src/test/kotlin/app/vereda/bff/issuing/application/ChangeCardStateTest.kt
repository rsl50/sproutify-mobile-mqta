package app.vereda.bff.issuing.application

import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.CardStateChanged
import app.vereda.bff.issuing.domain.CardStateControl
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.shared.RecordingEventPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCardStateTest {
    @Test
    fun togglesActiveToSuspendedAndPublishesTheEvent() = runBlocking {
        val cards = InMemoryIssuedCardRepository()
        val cardholderId = CardholderId.newId()
        val card = IssuedCard.issue(
            cardholderId,
            CardToken.of("card_1"),
            CardLast4.of("4242"),
            CardProductToken.of("cp_on"),
        )
        cards.save(card)
        val events = RecordingEventPublisher()
        val control = object : CardStateControl {
            override suspend fun transition(cardToken: CardToken, state: CardState): CardState = state
        }

        val updated = ChangeCardState(cards, control, events).execute(card)

        assertEquals(CardState.SUSPENDED, updated.state)
        assertEquals(CardState.SUSPENDED, cards.findByCardholder(cardholderId)?.state)
        val event = events.recorded().single() as CardStateChanged
        assertEquals(CardState.ACTIVE, event.from)
        assertEquals(CardState.SUSPENDED, event.to)
    }
}