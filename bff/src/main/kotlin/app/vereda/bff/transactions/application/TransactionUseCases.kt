package app.vereda.bff.transactions.application

import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEventPublisher
import app.vereda.bff.transactions.domain.CardTransaction
import app.vereda.bff.transactions.domain.CardTransactionGateway
import app.vereda.bff.transactions.domain.MerchantName
import app.vereda.bff.transactions.domain.PurchaseSimulated

internal object IdleCardTransactions : CardTransactionGateway {
    override suspend fun list(userToken: UserToken): List<CardTransaction> = emptyList()

    override suspend fun simulatePurchase(
        cardToken: CardToken,
        amount: Money,
        merchant: MerchantName,
    ): CardTransaction = error("transactions are not configured")
}

class ListCardTransactions(
    private val gateway: CardTransactionGateway,
) {
    suspend fun execute(userToken: UserToken): List<CardTransaction> = gateway.list(userToken)
}

class SimulatePurchase(
    private val gateway: CardTransactionGateway,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(cardToken: CardToken, amount: Money, merchant: MerchantName): CardTransaction {
        val transaction = gateway.simulatePurchase(cardToken, amount, merchant)
        events.publish(PurchaseSimulated(cardToken, amount, merchant, transaction.token))
        return transaction
    }
}
