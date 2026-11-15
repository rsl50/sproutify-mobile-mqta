package app.vereda.bff.jit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FundingBookTest {
    private val now = 1_700_000_000_000L
    private val dayStart = now - 1_000

    @Test
    fun approvalPlacesAHoldAndLeavesTheLedgerUntouched() {
        val result = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        assertEquals(200, result.reply?.status)
        assertTrue(result.reply!!.body.contains("\"amount\":10.00"))
        assertEquals(money("100.00"), result.ledger)
        assertEquals(money("90.00"), result.available)
        assertEquals(HoldState.OPEN, result.holds.single().state)
        assertTrue(result.events.single() is FundingApproved)
    }

    @Test
    fun insufficientFundsDoesNotMoveTheLedger() {
        val result = FundingBook.gateway(opened("5.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        assertEquals(402, result.reply?.status)
        assertTrue(result.reply!!.body.contains("INSUFFICIENT_FUNDS"))
        assertEquals(money("5.00"), result.ledger)
        assertEquals(money("5.00"), result.available)
        assertTrue(result.holds.isEmpty())
        assertTrue(result.events.single() is FundingDeclined)
    }

    @Test
    fun suspendedCardDeclinesWithoutAHold() {
        val result = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), false, null, now, dayStart)
        assertTrue(result.reply!!.body.contains("CARD_NOT_ACTIVE"))
        assertEquals(money("100.00"), result.available)
        assertTrue(result.holds.isEmpty())
    }

    @Test
    fun dailyLimitDeclinesWithoutAHold() {
        val result = FundingBook.gateway(
            opened("100.00"),
            auth("jit-1", "30.00"),
            true,
            money("20.00"),
            now,
            dayStart,
        )
        assertTrue(result.reply!!.body.contains("AMOUNT_LIMIT_EXCEEDED"))
        assertEquals(money("100.00"), result.ledger)
        assertTrue(result.holds.isEmpty())
    }

    @Test
    fun repeatedAuthorizationDoesNotOpenASecondHold() {
        val first = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val second = FundingBook.gateway(
            first.state(prior = first.outcome),
            auth("jit-1", "10.00"),
            true,
            null,
            now,
            dayStart,
        )
        assertEquals(200, second.reply?.status)
        assertEquals(1, second.holds.size)
        assertEquals(money("90.00"), second.available)
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun incrementalHoldKeepsTheOriginalToken() {
        val first = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val second = FundingBook.gateway(
            first.state(),
            auth("jit-2", "4.00", original = "jit-1", method = "pgfs.authorization.incremental"),
            true,
            null,
            now + 1,
            dayStart,
        )
        assertEquals("jit-1", second.holds.last().originalToken)
        assertEquals(money("86.00"), second.available)
        assertTrue(second.reply!!.body.contains("\"original_jit_funding_token\":\"jit-1\""))
    }

    @Test
    fun clearingReducesHoldAndLedgerByTheImpactedAmount() {
        val approved = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val cleared = FundingBook.webhook(approved.state(), capture("cap-1", "jit-1", "-10.00"))
        assertEquals(money("90.00"), cleared.ledger)
        assertEquals(money("90.00"), cleared.available)
        assertEquals(HoldState.CLEARED, cleared.holds.single().state)
        assertTrue(cleared.events.single() is TransactionCleared)
    }

    @Test
    fun clearingRetryDoesNotDebitTwice() {
        val approved = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val once = FundingBook.webhook(approved.state(), capture("cap-1", "jit-1", "-10.00"))
        val twice = FundingBook.webhook(once.state(prior = once.outcome), capture("cap-1", "jit-1", "-10.00"))
        assertEquals(money("90.00"), twice.ledger)
        assertTrue(twice.events.isEmpty())
    }

    @Test
    fun reversalReleasesTheHold() {
        val approved = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val reversed = FundingBook.webhook(approved.state(), reversal("rev-1", "jit-1", "10.00"))
        assertEquals(money("100.00"), reversed.ledger)
        assertEquals(money("100.00"), reversed.available)
        assertEquals(HoldState.RELEASED, reversed.holds.single().state)
        assertTrue(reversed.events.single() is HoldReleased)
    }

    @Test
    fun timeoutReleasesTheHold() {
        val approved = FundingBook.gateway(opened("100.00"), auth("jit-1", "10.00"), true, null, now, dayStart)
        val timedOut = FundingBook.webhook(
            approved.state(),
            auth("jit-1", "10.00").copy(timedOut = true),
        )
        assertEquals(money("100.00"), timedOut.available)
        assertEquals(HoldState.RELEASED, timedOut.holds.single().state)
        assertTrue(timedOut.events.single() is HoldReleased)
    }

    @Test
    fun refundCreditsTheLedger() {
        val credited = FundingBook.webhook(opened("100.00"), refund("ref-1", "8.50"))
        assertEquals(money("108.50"), credited.ledger)
        assertEquals(money("108.50"), credited.available)
    }

    @Test
    fun balanceInquiryReturnsBalancesAndDoesNotMoveThem() {
        val result = FundingBook.gateway(
            opened("40.00"),
            auth("bal-1", null, method = "pgfs.balanceinquiry"),
            false,
            null,
            now,
            dayStart,
        )
        assertEquals(200, result.reply?.status)
        assertTrue(result.reply!!.body.contains("\"ledger_balance\":40.00"))
        assertTrue(result.reply!!.body.contains("\"available_balance\":40.00"))
        assertFalse(result.reply!!.body.contains("\"amount\""))
        assertEquals(money("40.00"), result.ledger)
    }

    @Test
    fun usesTheJitAmountRatherThanAnotherAmount() {
        val message = auth("jit-1", "12.50")
        val result = FundingBook.gateway(opened("100.00"), message, true, null, now, dayStart)
        assertEquals(money("12.50"), result.holds.single().amount)
        assertTrue(result.reply!!.body.contains("\"amount\":12.50"))
    }

    private fun opened(amount: String) = FundingBook.open(money(amount))

    private fun auth(
        token: String,
        amount: String?,
        original: String? = null,
        method: String = "pgfs.authorization",
    ) = FundingMessage(
        token = token,
        method = method,
        userToken = "usr_1",
        amount = amount?.let(::money),
        originalToken = original,
        impacted = null,
        timedOut = false,
    )

    private fun capture(token: String, original: String, impacted: String) = FundingMessage(
        token = token,
        method = "pgfs.authorization.capture",
        userToken = "usr_1",
        amount = money(impacted).abs(),
        originalToken = original,
        impacted = money(impacted),
        timedOut = false,
    )

    private fun reversal(token: String, original: String, amount: String) = FundingMessage(
        token = token,
        method = "pgfs.authorization.reversal",
        userToken = "usr_1",
        amount = money(amount),
        originalToken = original,
        impacted = money(amount),
        timedOut = false,
    )

    private fun refund(token: String, amount: String) = FundingMessage(
        token = token,
        method = "pgfs.refund",
        userToken = "usr_1",
        amount = money(amount),
        originalToken = null,
        impacted = money(amount),
        timedOut = false,
    )

    private fun money(amount: String) = BigDecimal(amount).money()
}
