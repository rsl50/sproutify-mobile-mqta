package app.vereda.bff.transactions.domain

import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEvent
import java.math.BigDecimal

@JvmInline
value class MerchantName private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "merchant is blank" }
        require(value.length <= 40) { "merchant is too long" }
    }

    companion object {
        fun parse(raw: String): MerchantName = MerchantName(raw.trim())
    }
}

data class CardTransaction(
    val token: String,
    val merchant: MerchantName,
    val amount: Money,
    val state: String,
)

fun purchaseAmount(raw: String): Money {
    val money = Money.parse(raw.trim(), Currency.USD)
    require(money.amount > BigDecimal.ZERO) { "purchase amount must be positive" }
    require(money.amount <= BigDecimal("1000.00")) { "purchase amount is too high" }
    return money
}

interface CardTransactionGateway {
    suspend fun list(userToken: UserToken): List<CardTransaction>

    suspend fun simulatePurchase(cardToken: CardToken, amount: Money, merchant: MerchantName): CardTransaction
}

data class PurchaseSimulated(
    val cardToken: CardToken,
    val amount: Money,
    val merchant: MerchantName,
    val transactionToken: String,
) : DomainEvent
