package app.vereda.bff.spending.domain

import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEvent
import java.math.BigDecimal

enum class SpendWindow {
    DAY,
    WEEK,
    MONTH,
}

data class SpendLimit(
    val cardholderId: CardholderId,
    val controlToken: String,
    val amount: Money,
    val usageLimit: Int,
    val window: SpendWindow,
) {
    init {
        require(usageLimit in 1..100) { "usage limit must be between 1 and 100" }
        require(amount.amount > BigDecimal.ZERO) { "spend limit must be positive" }
        require(amount.currency == Currency.USD) { "spend limit is USD" }
    }

    fun covers(purchase: Money): Boolean = purchase.amount <= amount.amount
}

interface SpendLimitRepository {
    fun find(cardholderId: CardholderId): SpendLimit?

    fun save(limit: SpendLimit)
}

interface SpendControl {
    suspend fun save(userToken: UserToken, limit: SpendLimit)
}

data class SpendLimitSet(
    val cardholderId: CardholderId,
    val amount: Money,
    val usageLimit: Int,
) : DomainEvent
