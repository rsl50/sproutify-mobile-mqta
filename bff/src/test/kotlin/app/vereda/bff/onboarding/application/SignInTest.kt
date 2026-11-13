package app.vereda.bff.onboarding.application

import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.InvalidCredentials
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.onboarding.infrastructure.InMemorySessionRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SignInTest {
    @Test
    fun acceptsTheLocalPasswordAndRejectsAWrongOne() {
        val cardholders = InMemoryCardholderRepository()
        cardholders.save(
            Cardholder.register(
                id = CardholderId.newId(),
                name = PersonName("Ana", "Lima"),
                email = Email.parse("ana@example.com"),
                password = Password.create("segredo12"),
                userToken = UserToken.of("usr_1"),
            ),
        )
        val signIn = SignIn(cardholders, InMemorySessionRepository())

        assertNotNull(signIn.execute("ANA@example.com", "segredo12"))
        assertThrows(InvalidCredentials::class.java) {
            signIn.execute("ana@example.com", "outra-senha")
        }
        assertThrows(InvalidCredentials::class.java) {
            signIn.execute("outra@example.com", "segredo12")
        }
    }
}
