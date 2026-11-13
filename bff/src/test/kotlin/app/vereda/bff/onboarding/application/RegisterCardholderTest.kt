package app.vereda.bff.onboarding.application

import app.vereda.bff.onboarding.domain.CardholderAlreadyExists
import app.vereda.bff.onboarding.domain.CardholderEnrollment
import app.vereda.bff.onboarding.domain.CardholderRegistered
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.shared.RecordingEventPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterCardholderTest {
    @Test
    fun enrollsCardholderPublishesEventAndDoesNotSendPassword() = runBlocking {
        val enrollment = RecordingEnrollment()
        val repository = InMemoryCardholderRepository()
        val events = RecordingEventPublisher()
        val useCase = RegisterCardholder(repository, enrollment, events)

        val cardholder = useCase.execute(
            RegisterCardholderCommand("Ana", "Lima", "Ana@Example.com", "segredo12"),
        )

        assertEquals(Email.parse("ana@example.com"), cardholder.email)
        assertEquals(listOf("Ana", "Lima", "ana@example.com"), enrollment.calls.single())
        assertNotEquals("segredo12", cardholder.password.hash)
        assertTrue(cardholder.password.matches("segredo12"))
        val event = events.recorded().single() as CardholderRegistered
        assertEquals(UserToken.of("usr_1"), event.userToken)
        assertEquals(cardholder, repository.findByEmail(cardholder.email))
    }

    @Test
    fun rejectsASecondCardholderWithTheSameEmail() = runBlocking {
        val enrollment = RecordingEnrollment()
        val useCase = RegisterCardholder(InMemoryCardholderRepository(), enrollment, RecordingEventPublisher())
        val command = RegisterCardholderCommand("Ana", "Lima", "ana@example.com", "segredo12")
        useCase.execute(command)
        try {
            useCase.execute(command)
            error("expected duplicate")
        } catch (expected: CardholderAlreadyExists) {
            assertEquals(1, enrollment.calls.size)
        }
    }

    private class RecordingEnrollment : CardholderEnrollment {
        val calls = mutableListOf<List<String>>()

        override suspend fun enroll(name: PersonName, email: Email): UserToken {
            calls += listOf(name.first, name.last, email.value)
            return UserToken.of("usr_1")
        }
    }
}
