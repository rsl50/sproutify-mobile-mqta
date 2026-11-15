package app.vereda.bff.jit.domain

import app.vereda.bff.shared.DomainEvent
import java.math.BigDecimal
import java.math.RoundingMode

enum class HoldState {
    OPEN,
    CLEARED,
    RELEASED,
}

data class FundingHold(
    val token: String,
    val originalToken: String?,
    val amount: BigDecimal,
    val remaining: BigDecimal,
    val state: HoldState,
    val createdAt: Long,
)

data class FundingMessage(
    val token: String,
    val method: String,
    val userToken: String,
    val amount: BigDecimal?,
    val originalToken: String?,
    val impacted: BigDecimal?,
    val timedOut: Boolean,
)

data class GatewayReply(
    val status: Int,
    val body: String,
)

data class LedgerState(
    val ledger: BigDecimal,
    val available: BigDecimal,
    val holds: List<FundingHold>,
    val priorOutcome: String?,
)

data class LedgerTransition(
    val ledger: BigDecimal,
    val available: BigDecimal,
    val holds: List<FundingHold>,
    val outcome: String,
    val events: List<DomainEvent>,
    val reply: GatewayReply?,
) {
    fun state(prior: String? = null): LedgerState = LedgerState(ledger, available, holds, prior)
}

data class FundingApproved(
    val userToken: String,
    val jitToken: String,
    val amount: BigDecimal,
) : DomainEvent

data class FundingDeclined(
    val userToken: String,
    val jitToken: String,
    val reason: String,
) : DomainEvent

data class HoldReleased(
    val userToken: String,
    val jitToken: String,
) : DomainEvent

data class TransactionCleared(
    val userToken: String,
    val jitToken: String,
    val amount: BigDecimal,
) : DomainEvent

object FundingBook {
    const val INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
    const val CARD_NOT_ACTIVE = "CARD_NOT_ACTIVE"
    const val AMOUNT_LIMIT_EXCEEDED = "AMOUNT_LIMIT_EXCEEDED"
    const val INVALID_AMOUNT = "INVALID_AMOUNT"
    const val TRANSACTION_NOT_PERMITTED = "TRANSACTION_NOT_PERMITTED"

    private val authorizations = setOf(
        "pgfs.authorization",
        "pgfs.authorization.incremental",
        "pgfs.auth_plus_capture",
    )
    private val captures = setOf(
        "pgfs.authorization.capture",
        "pgfs.force_capture",
        "pgfs.auth_plus_capture",
    )
    private val reversals = setOf(
        "pgfs.authorization.reversal",
        "pgfs.auth_plus_capture.reversal",
    )
    private val refunds = setOf(
        "pgfs.refund",
        "pgfs.refund.authorization",
    )

    fun open(amount: BigDecimal): LedgerState = LedgerState(
        ledger = amount.money(),
        available = amount.money(),
        holds = emptyList(),
        priorOutcome = null,
    )

    fun gateway(
        state: LedgerState,
        message: FundingMessage,
        cardActive: Boolean,
        dailyLimit: BigDecimal?,
        nowMs: Long,
        dayStartMs: Long,
    ): LedgerTransition {
        state.priorOutcome?.let { prior ->
            return replay(state, message, prior)
        }
        if (message.method == "pgfs.balanceinquiry") {
            return transition(
                state = state,
                outcome = "BALANCE",
                events = emptyList(),
                reply = reply(message, amount = null, decline = null, ledger = state.ledger, available = state.available),
            )
        }
        if (!cardActive) {
            return decline(state, message, CARD_NOT_ACTIVE)
        }
        if (message.method == "pgfs.authorization.account_verification" ||
            message.method == "pgfs.refund.authorization" ||
            message.method == "pgfs.original.credit.authorization"
        ) {
            return transition(
                state = state,
                outcome = "APPROVED",
                events = emptyList(),
                reply = reply(
                    message,
                    amount = (message.amount ?: BigDecimal.ZERO).money(),
                    decline = null,
                    ledger = null,
                    available = null,
                ),
            )
        }
        if (message.method !in authorizations) {
            return decline(state, message, TRANSACTION_NOT_PERMITTED)
        }
        val amount = message.amount?.money()
            ?: return decline(state, message, INVALID_AMOUNT)
        if (amount.signum() < 0) {
            return decline(state, message, INVALID_AMOUNT)
        }
        val spentToday = state.holds
            .filter { it.state != HoldState.RELEASED && it.createdAt >= dayStartMs }
            .fold(BigDecimal.ZERO) { sum, hold -> sum + hold.amount }
        if (dailyLimit != null && spentToday + amount > dailyLimit.money()) {
            return decline(state, message, AMOUNT_LIMIT_EXCEEDED)
        }
        if (amount > state.available.money()) {
            return decline(state, message, INSUFFICIENT_FUNDS)
        }
        val hold = FundingHold(
            token = message.token,
            originalToken = message.originalToken,
            amount = amount,
            remaining = amount,
            state = if (amount.signum() == 0) HoldState.CLEARED else HoldState.OPEN,
            createdAt = nowMs,
        )
        val holds = if (amount.signum() == 0) state.holds else state.holds + hold
        val settled = settle(state.ledger, holds)
        return transition(
            state = settled,
            outcome = "APPROVED",
            events = listOf(FundingApproved(message.userToken, message.token, amount)),
            reply = reply(message, amount, decline = null, ledger = null, available = null),
        )
    }

    fun webhook(state: LedgerState, message: FundingMessage): LedgerTransition {
        if (state.priorOutcome != null) {
            return transition(state, state.priorOutcome, emptyList(), reply = null)
        }
        if (message.timedOut) {
            return release(state, message, message.amount ?: message.impacted?.abs())
        }
        if (message.method in reversals) {
            return release(state, message, message.impacted?.abs() ?: message.amount)
        }
        if (message.method in captures) {
            return clear(state, message)
        }
        if (message.method in refunds) {
            return credit(state, message)
        }
        return transition(state, "IGNORED", emptyList(), reply = null)
    }

    private fun replay(state: LedgerState, message: FundingMessage, prior: String): LedgerTransition {
        val reply = when {
            message.method == "pgfs.balanceinquiry" ->
                reply(message, amount = null, decline = null, ledger = state.ledger, available = state.available)
            prior == "APPROVED" ->
                reply(message, message.amount?.money() ?: BigDecimal.ZERO.money(), null, null, null)
            prior.startsWith("DECLINED:") ->
                reply(message, message.amount?.money() ?: BigDecimal.ZERO.money(), prior.removePrefix("DECLINED:"), null, null)
            else -> null
        }
        return transition(state, prior, emptyList(), reply)
    }

    private fun decline(state: LedgerState, message: FundingMessage, reason: String): LedgerTransition =
        transition(
            state = state,
            outcome = "DECLINED:$reason",
            events = listOf(FundingDeclined(message.userToken, message.token, reason)),
            reply = reply(
                message,
                amount = message.amount?.money() ?: BigDecimal.ZERO.money(),
                decline = reason,
                ledger = null,
                available = null,
            ),
        )

    private fun clear(state: LedgerState, message: FundingMessage): LedgerTransition {
        val amount = (message.impacted?.abs() ?: message.amount)?.money()
            ?: return transition(state, "IGNORED", emptyList(), reply = null)
        val family = message.originalToken ?: message.token
        val holds = consume(state.holds, family, amount, cleared = true)
        val settled = settle(state.ledger.money() - amount, holds)
        return transition(
            state = settled,
            outcome = "CLEARED",
            events = listOf(TransactionCleared(message.userToken, message.token, amount)),
            reply = null,
        )
    }

    private fun release(state: LedgerState, message: FundingMessage, amount: BigDecimal?): LedgerTransition {
        val family = message.originalToken ?: message.token
        val releasing = amount?.money()
        val before = openRemaining(state.holds, family)
        val holds = if (releasing == null) {
            state.holds.map { hold ->
                if (inFamily(hold, family) && hold.state == HoldState.OPEN) {
                    hold.copy(remaining = BigDecimal.ZERO.money(), state = HoldState.RELEASED)
                } else {
                    hold
                }
            }
        } else {
            consume(state.holds, family, releasing, cleared = false)
        }
        val settled = settle(state.ledger, holds)
        val released = before > openRemaining(holds, family)
        return transition(
            state = settled,
            outcome = "RELEASED",
            events = if (released) listOf(HoldReleased(message.userToken, family)) else emptyList(),
            reply = null,
        )
    }

    private fun credit(state: LedgerState, message: FundingMessage): LedgerTransition {
        val amount = (message.impacted?.abs() ?: message.amount)?.money()
            ?: return transition(state, "IGNORED", emptyList(), reply = null)
        val settled = settle(state.ledger.money() + amount, state.holds)
        return transition(settled, "CREDITED", emptyList(), reply = null)
    }

    private fun consume(
        holds: List<FundingHold>,
        family: String,
        amount: BigDecimal,
        cleared: Boolean,
    ): List<FundingHold> {
        var left = amount.money()
        return holds.map { hold ->
            if (left.signum() == 0 || hold.state != HoldState.OPEN || !inFamily(hold, family)) {
                hold
            } else {
                val take = hold.remaining.min(left).money()
                left = (left - take).money()
                val remaining = (hold.remaining - take).money()
                hold.copy(
                    remaining = remaining,
                    state = when {
                        remaining.signum() > 0 -> HoldState.OPEN
                        cleared -> HoldState.CLEARED
                        else -> HoldState.RELEASED
                    },
                )
            }
        }
    }

    private fun inFamily(hold: FundingHold, family: String): Boolean =
        hold.token == family || hold.originalToken == family

    private fun openRemaining(holds: List<FundingHold>, family: String): BigDecimal =
        holds.filter { it.state == HoldState.OPEN && inFamily(it, family) }
            .fold(BigDecimal.ZERO) { sum, hold -> sum + hold.remaining }

    private fun settle(ledger: BigDecimal, holds: List<FundingHold>): LedgerState {
        val open = holds.filter { it.state == HoldState.OPEN }
            .fold(BigDecimal.ZERO) { sum, hold -> sum + hold.remaining }
        val ledgerScaled = ledger.money()
        return LedgerState(
            ledger = ledgerScaled,
            available = (ledgerScaled - open).money(),
            holds = holds,
            priorOutcome = null,
        )
    }

    private fun transition(
        state: LedgerState,
        outcome: String,
        events: List<DomainEvent>,
        reply: GatewayReply?,
    ): LedgerTransition = LedgerTransition(
        ledger = state.ledger.money(),
        available = state.available.money(),
        holds = state.holds,
        outcome = outcome,
        events = events,
        reply = reply,
    )

    fun reply(
        message: FundingMessage,
        amount: BigDecimal?,
        decline: String?,
        ledger: BigDecimal?,
        available: BigDecimal?,
    ): GatewayReply {
        val fields = mutableListOf(
            "\"token\":${quote(message.token)}",
            "\"method\":${quote(message.method)}",
            "\"user_token\":${quote(message.userToken)}",
        )
        message.originalToken?.let { fields += "\"original_jit_funding_token\":${quote(it)}" }
        amount?.let { fields += "\"amount\":${it.money().toPlainString()}" }
        decline?.let { fields += "\"decline_reason\":${quote(it)}" }
        if (ledger != null && available != null) {
            fields += "\"balances\":{\"USD\":{\"currency_code\":\"USD\"," +
                "\"ledger_balance\":${ledger.money().toPlainString()}," +
                "\"available_balance\":${available.money().toPlainString()}," +
                "\"pending_credits\":0.00}}"
        }
        val status = if (decline == null) 200 else 402
        return GatewayReply(status, "{\"jit_funding\":{${fields.joinToString(",")}}}")
    }

    private fun quote(value: String): String =
        kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), value)
}

internal fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
