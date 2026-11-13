package app.vereda.bff.funding.infrastructure

import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.onboarding.domain.UserToken
import java.math.BigDecimal
import java.math.RoundingMode

class MarqetaAccountFunding(
    private val api: MarqetaCoreApi,
) : AccountFunding {
    override suspend fun fund(userToken: UserToken, money: Money) {
        api.fundAccount(
            userToken = userToken.value,
            amount = money.amount.toPlainString(),
            currencyCode = money.currency.name,
            fundingSourceToken = SANDBOX_PROGRAM_FUNDING,
        )
    }

    override suspend fun availableBalance(userToken: UserToken): Money {
        val remote = api.availableBalance(userToken.value)
        val currency = Currency.valueOf(remote.currencyCode)
        val amount = BigDecimal.valueOf(remote.amount).setScale(2, RoundingMode.HALF_UP)
        return Money(amount, currency)
    }

    private companion object {
        const val SANDBOX_PROGRAM_FUNDING = "sandbox_program_funding"
    }
}
