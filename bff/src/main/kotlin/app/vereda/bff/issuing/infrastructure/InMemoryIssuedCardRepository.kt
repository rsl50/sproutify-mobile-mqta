package app.vereda.bff.issuing.infrastructure

import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.onboarding.domain.CardholderId
import java.util.concurrent.ConcurrentHashMap

class InMemoryIssuedCardRepository : IssuedCardRepository {
    private val byCardholder = ConcurrentHashMap<String, IssuedCard>()

    override fun findByCardholder(cardholderId: CardholderId): IssuedCard? = byCardholder[cardholderId.value]

    override fun save(card: IssuedCard) {
        byCardholder[card.cardholderId.value] = card
    }
}
