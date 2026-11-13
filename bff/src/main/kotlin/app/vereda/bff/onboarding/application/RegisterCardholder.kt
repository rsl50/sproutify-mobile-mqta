package app.vereda.bff.onboarding.application

import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderAlreadyExists
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.CardholderRegistered
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.shared.DomainEventPublisher

data class RegisterCardholderCommand(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
)

class RegisterCardholder(
    private val cardholders: CardholderRepository,
    private val enrollment: CardholderEnrollment,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(command: RegisterCardholderCommand): Cardholder {
        val email = Email.parse(command.email)
        val name = PersonName(command.firstName.trim(), command.lastName.trim())
        val password = Password.create(command.password)
        if (cardholders.findByEmail(email) != null) {
            throw CardholderAlreadyExists(email)
        }
        val userToken = enrollment.enroll(name, email)
        val cardholder = Cardholder.register(
            id = CardholderId.newId(),
            name = name,
            email = email,
            password = password,
            userToken = userToken,
        )
        cardholders.save(cardholder)
        events.publish(CardholderRegistered(cardholder.id, email, userToken))
        return cardholder
    }
}
