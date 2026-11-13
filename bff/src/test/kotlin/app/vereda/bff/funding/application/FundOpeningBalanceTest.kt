package app.vereda.bff.funding.application

import app.vereda.bff.funding.domain.AccountFunded
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.RecordingEventPublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FundOpeningBalanceTest {
    @Test
    fun fundsOneHundredDollarsAndReadsTheAvailableBalance() = runBlocking {
        val funding = RecordingFunding()
        val events = RecordingEventPublisher()
        val balance = FundOpeningBalance(funding, events).execute(UserToken.of("usr_1"))

        assertEquals(Money.openingBalance, funding.funded)
        assertEquals(Money.parse("100.00", app.vereda.bff.funding.domain.Currency.USD), balance)
        val event = events.recorded().single() as AccountFunded
        assertEquals(Money.openingBalance, event.amount)
        assertEquals(UserToken.of("usr_1"), event.userToken)
    }

    private class RecordingFunding : AccountFunding {
        var funded: Money? = null

        override suspend fun fund(userToken: UserToken, money: Money) {
            funded = money
        }

        override suspend fun availableBalance(userToken: UserToken): Money = Money.openingBalance
    }
}
