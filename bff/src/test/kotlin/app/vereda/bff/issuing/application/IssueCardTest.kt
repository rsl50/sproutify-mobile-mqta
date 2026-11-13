package app.vereda.bff.issuing.application

import app.vereda.bff.issuing.domain.CardIssued
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCardDraft
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.RecordingEventPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueCardTest {
    @Test
    fun issuesOnceAndPublishesCardIssued() = runBlocking {
        val issuing = FakeIssuing()
        val events = RecordingEventPublisher()
        val useCase = IssueCard(issuing, InMemoryIssuedCardRepository(), events)
        val cardholder = sampleCardholder()

        val first = useCase.execute(cardholder)
        val second = useCase.execute(cardholder)

        assertEquals(CardLast4.of("4242"), first.last4)
        assertTrue(first === second)
        assertEquals(1, issuing.calls)
        val event = events.recorded().single() as CardIssued
        assertEquals(cardholder.id, event.cardholderId)
        assertEquals(CardToken.of("card_1"), event.cardToken)
    }

    private class FakeIssuing : CardIssuing {
        var calls = 0

        override suspend fun issueFor(userToken: UserToken): IssuedCardDraft {
            calls += 1
            assertEquals(UserToken.of("usr_1"), userToken)
            return IssuedCardDraft(
                token = CardToken.of("card_1"),
                last4 = CardLast4.of("4242"),
                productToken = CardProductToken.of("cp_on"),
            )
        }
    }

    private fun sampleCardholder() = Cardholder.register(
        id = CardholderId.newId(),
        name = PersonName("Ana", "Lima"),
        email = Email.parse("ana@example.com"),
        password = Password.create("segredo12"),
        userToken = UserToken.of("usr_1"),
    )
}
