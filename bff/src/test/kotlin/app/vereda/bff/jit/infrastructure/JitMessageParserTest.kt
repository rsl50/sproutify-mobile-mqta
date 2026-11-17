package app.vereda.bff.jit.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitMessageParserTest {
    @Test
    fun readsTheAmountFromJitFundingAndNotTheTopLevelAmount() {
        val message = JitMessageParser.parse(
            """
            {
              "amount": 99.00,
              "gpa": {"impacted_amount": -4.50},
              "gpa_order": {
                "funding": {"gateway_log": {"timed_out": false}},
                "jit_funding": {
                  "token": "jit-1",
                  "method": "pgfs.authorization",
                  "user_token": "usr_1",
                  "amount": 4.5,
                  "original_jit_funding_token": "jit-0"
                }
              }
            }
            """.trimIndent(),
        ).single()
        assertEquals("4.50", message.amount?.toPlainString())
        assertEquals("-4.50", message.impacted?.toPlainString())
        assertEquals("jit-0", message.originalToken)
        assertTrue(!message.timedOut)
    }

    @Test
    fun readsAWebhookBatchAndATimeoutFlag() {
        val messages = JitMessageParser.parse(
            """
            {"transactions":[{
              "gpa_order": {
                "funding": {"gateway_log": {"timed_out": true}},
                "jit_funding": {"token":"jit-1","method":"pgfs.authorization","user_token":"usr_1","amount":"10.00"}
              }
            }]}
            """.trimIndent(),
        )
        assertEquals(1, messages.size)
        assertTrue(messages.single().timedOut)
    }
}
