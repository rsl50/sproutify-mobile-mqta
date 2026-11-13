package app.vereda.bff.funding.domain

import app.vereda.bff.onboarding.domain.UserToken
import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency {
    USD,
}

data class Money(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount.scale() == 2) { "money must have two decimal places" }
        require(amount >= BigDecimal.ZERO) { "money cannot be negative" }
    }

    fun format(): String = "${currency.name} ${amount.toPlainString()}"

    companion object {
        val openingBalance: Money = parse("100.00", Currency.USD)

        fun parse(amount: String, currency: Currency): Money {
            val scaled = BigDecimal(amount).setScale(2, RoundingMode.UNNECESSARY)
            return Money(scaled, currency)
        }
    }
}

interface AccountFunding {
    suspend fun fund(userToken: UserToken, money: Money)

    suspend fun availableBalance(userToken: UserToken): Money
}
