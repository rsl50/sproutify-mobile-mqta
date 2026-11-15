package app.vereda.bff.spending.application

import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.shared.DomainEventPublisher
import app.vereda.bff.spending.domain.SpendControl
import app.vereda.bff.spending.domain.SpendLimit
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.spending.domain.SpendLimitSet
import app.vereda.bff.spending.domain.SpendWindow

class SetSpendLimit(
    private val limits: SpendLimitRepository,
    private val control: SpendControl,
    private val events: DomainEventPublisher,
) {
    suspend fun execute(cardholder: Cardholder, amountRaw: String, usageRaw: String): SpendLimit {
        val amount = Money.parse(amountRaw.trim(), Currency.USD)
        val usage = usageRaw.trim().toIntOrNull() ?: throw IllegalArgumentException("usage")
        val existing = limits.find(cardholder.id)
        val limit = SpendLimit(
            cardholderId = cardholder.id,
            controlToken = existing?.controlToken ?: cardholder.id.value,
            amount = amount,
            usageLimit = usage,
            window = SpendWindow.DAY,
        )
        control.save(cardholder.userToken, limit)
        limits.save(limit)
        events.publish(SpendLimitSet(cardholder.id, amount, usage))
        return limit
    }
}