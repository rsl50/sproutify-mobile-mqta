package app.vereda.bff.transactions.infrastructure

import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.infrastructure.marqeta.RemoteTransaction
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.transactions.domain.CardTransaction
import app.vereda.bff.transactions.domain.CardTransactionGateway
import app.vereda.bff.transactions.domain.MerchantName
import java.math.BigDecimal
import java.math.RoundingMode

class MarqetaCardTransactions(
    private val api: MarqetaCoreApi,
) : CardTransactionGateway {
    override suspend fun list(userToken: UserToken): List<CardTransaction> =
        api.listTransactions(userToken.value).map { it.toDomain() }

    override suspend fun simulatePurchase(
        cardToken: CardToken,
        amount: Money,
        merchant: MerchantName,
    ): CardTransaction = api.simulateAuthorization(
        cardToken = cardToken.value,
        amount = amount.amount.toPlainString(),
        merchant = merchant.value,
    ).toDomain()

    private fun RemoteTransaction.toDomain(): CardTransaction {
        val scaled = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        val currency = runCatching { Currency.valueOf(currencyCode) }.getOrDefault(Currency.USD)
        val merchantName = merchant.ifBlank { "Estabelecimento" }
        return CardTransaction(
            token = token.ifBlank { "transaction" },
            merchant = MerchantName.parse(merchantName.take(40)),
            amount = Money(scaled, currency),
            state = state.ifBlank { "PENDING" },
        )
    }
}
