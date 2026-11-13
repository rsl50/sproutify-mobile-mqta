package app.vereda.bff.funding.application

import app.vereda.bff.funding.domain.AccountFunded
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEventPublisher

class FundOpeningBalance(
    private val funding: AccountFunding,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(userToken: UserToken): Money {
        val amount = Money.openingBalance
        funding.fund(userToken, amount)
        events.publish(AccountFunded(userToken, amount))
        return funding.availableBalance(userToken)
    }
}
