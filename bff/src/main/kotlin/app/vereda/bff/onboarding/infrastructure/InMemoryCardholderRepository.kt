package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.Email
import java.util.concurrent.ConcurrentHashMap

class InMemoryCardholderRepository : CardholderRepository {
    private val byId = ConcurrentHashMap<String, Cardholder>()
    private val byEmail = ConcurrentHashMap<String, String>()

    override fun findByEmail(email: Email): Cardholder? = byEmail[email.value]?.let(byId::get)

    override fun findById(id: CardholderId): Cardholder? = byId[id.value]

    override fun save(cardholder: Cardholder) {
        byId[cardholder.id.value] = cardholder
        byEmail[cardholder.email.value] = cardholder.id.value
    }
}
