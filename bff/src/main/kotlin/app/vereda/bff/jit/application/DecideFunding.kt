package app.vereda.bff.jit.application

import app.vereda.bff.issuing.domain.CardState
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.jit.domain.FundingBook
import app.vereda.bff.jit.domain.FundingMessage
import app.vereda.bff.jit.domain.GatewayReply
import app.vereda.bff.jit.infrastructure.JdbcFundingStore
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.DomainEventPublisher
import app.vereda.bff.spending.domain.SpendLimitRepository
import java.time.Instant
import java.time.ZoneOffset

class DecideFunding(
    private val store: JdbcFundingStore,
    private val cardholders: CardholderRepository,
    private val cards: IssuedCardRepository,
    private val limits: SpendLimitRepository?,
    private val events: DomainEventPublisher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun execute(message: FundingMessage): GatewayReply {
        val cardholder = runCatching { cardholders.findByUserToken(UserToken.of(message.userToken)) }.getOrNull()
        val card = cardholder?.let { cards.findByCardholder(it.id) }
        val active = card?.state == CardState.ACTIVE
        val limit = cardholder?.let { limits?.find(it.id)?.amount?.amount }
        val now = clock()
        val dayStart = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val transition = store.update(message.userToken, CHANNEL, message.token, message.method) { state ->
            FundingBook.gateway(state, message, active, limit, now, dayStart)
        }
        transition.events.forEach(events::publish)
        return transition.reply ?: FundingBook.reply(message, message.amount, FundingBook.TRANSACTION_NOT_PERMITTED, null, null)
    }

    companion object {
        const val CHANNEL = "gateway"
    }
}

class ApplyFundingNotification(
    private val store: JdbcFundingStore,
    private val events: DomainEventPublisher,
) {
    fun execute(message: FundingMessage) {
        val transition = store.update(message.userToken, CHANNEL, message.token, message.method) { state ->
            FundingBook.webhook(state, message)
        }
        transition.events.forEach(events::publish)
    }

    companion object {
        const val CHANNEL = "webhook"
    }
}
