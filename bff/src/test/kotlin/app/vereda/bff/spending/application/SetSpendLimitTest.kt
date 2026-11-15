package app.vereda.bff.spending.application

import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.bff.spending.domain.SpendControl
import app.vereda.bff.spending.domain.SpendLimit
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.spending.domain.SpendLimitSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SetSpendLimitTest {
    @Test
    fun storesTheDailyLimitAndPublishesTheEvent() = runBlocking {
        val repository = MemoryLimits()
        val events = RecordingEventPublisher()
        val cardholder = sample()
        val limit = SetSpendLimit(repository, object : SpendControl {
            override suspend fun save(userToken: UserToken, limit: SpendLimit) {
                assertEquals(UserToken.of("usr_1"), userToken)
            }
        }, events).execute(cardholder, "20.00", "3")

        assertEquals(Money.parse("20.00", app.vereda.bff.funding.domain.Currency.USD), limit.amount)
        assertEquals(3, limit.usageLimit)
        assertEquals(limit, repository.find(cardholder.id))
        assertEquals(cardholder.id, (events.recorded().single() as SpendLimitSet).cardholderId)
    }

    @Test
    fun rejectsAUsageOutsideTheAllowedRange() {
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    SetSpendLimit(MemoryLimits(), object : SpendControl {
                        override suspend fun save(userToken: UserToken, limit: SpendLimit) = Unit
                    }, RecordingEventPublisher()).execute(sample(), "20.00", "0")
                }
            }
        }
    }

    private fun sample() = Cardholder.register(
        CardholderId.newId(),
        PersonName("Ana", "Lima"),
        Email.parse("ana@example.com"),
        Password.create("segredo12"),
        UserToken.of("usr_1"),
    )

    private class MemoryLimits : SpendLimitRepository {
        private var stored: SpendLimit? = null
        override fun find(cardholderId: CardholderId) = stored
        override fun save(limit: SpendLimit) {
            stored = limit
        }
    }
}