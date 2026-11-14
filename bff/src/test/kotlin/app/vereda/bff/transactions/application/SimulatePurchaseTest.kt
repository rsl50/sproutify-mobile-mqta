package app.vereda.bff.transactions.application

import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.RecordingEventPublisher
import app.vereda.bff.transactions.domain.CardTransaction
import app.vereda.bff.transactions.domain.CardTransactionGateway
import app.vereda.bff.transactions.domain.MerchantName
import app.vereda.bff.transactions.domain.PurchaseSimulated
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatePurchaseTest {
    @Test
    fun publishesPurchaseSimulated() = runBlocking {
        val gateway = RecordingGateway()
        val events = RecordingEventPublisher()
        val transaction = SimulatePurchase(gateway, events).execute(
            cardToken = CardToken.of("card_1"),
            amount = Money.openingBalance,
            merchant = MerchantName.parse("Padaria"),
        )

        assertEquals("tx_1", transaction.token)
        val event = events.recorded().single() as PurchaseSimulated
        assertEquals(CardToken.of("card_1"), event.cardToken)
        assertEquals(Money.openingBalance, event.amount)
        assertEquals("Padaria", event.merchant.value)
    }

    private class RecordingGateway : CardTransactionGateway {
        override suspend fun list(userToken: UserToken): List<CardTransaction> = emptyList()

        override suspend fun simulatePurchase(
            cardToken: CardToken,
            amount: Money,
            merchant: MerchantName,
        ): CardTransaction = CardTransaction("tx_1", merchant, amount, "PENDING")
    }
}
