package app.vereda.bff.issuing.application

import app.vereda.bff.issuing.domain.CardFundingMode
import app.vereda.bff.issuing.domain.CardIssued
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardIssuing
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.shared.DomainEventPublisher

class IssueCard(
    private val issuing: CardIssuing,
    private val cards: IssuedCardRepository,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(
        cardholder: Cardholder,
        mode: CardFundingMode = CardFundingMode.GPA,
        productToken: CardProductToken? = null,
    ): IssuedCard {
        cards.findByCardholder(cardholder.id)?.let { return it }
        val draft = if (productToken == null) {
            issuing.issueFor(cardholder.userToken)
        } else {
            issuing.issueOnProduct(cardholder.userToken, productToken)
        }
        val card = IssuedCard.issue(
            cardholderId = cardholder.id,
            token = draft.token,
            last4 = draft.last4,
            productToken = draft.productToken,
            fundingMode = mode,
        )
        cards.save(card)
        events.publish(CardIssued(cardholder.id, card.token, card.last4))
        return card
    }
}
