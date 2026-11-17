package app.vereda.bff.jit.infrastructure

import app.vereda.bff.jit.domain.AccountLedger
import app.vereda.bff.jit.domain.FundingHold
import app.vereda.bff.jit.domain.HoldState
import app.vereda.bff.jit.domain.LedgerState
import app.vereda.bff.jit.domain.LedgerTransition
import app.vereda.bff.jit.domain.money
import app.vereda.bff.shared.persistence.VeredaDatabase
import java.math.BigDecimal
import java.sql.Connection

class JdbcFundingStore(
    private val database: VeredaDatabase,
) : AccountLedger {
    override fun balances(userToken: String): LedgerState = database.read { connection ->
        load(connection, userToken, channel = "", token = "", method = "")
    }

    override fun open(userToken: String, amount: BigDecimal): LedgerState = database.write { connection ->
        val existing = load(connection, userToken, channel = "", token = "", method = "")
        if (accountExists(connection, userToken)) {
            existing
        } else {
            val opened = app.vereda.bff.jit.domain.FundingBook.open(amount)
            saveAccount(connection, userToken, opened.ledger, opened.available)
            opened
        }
    }

    fun update(
        userToken: String,
        channel: String,
        token: String,
        method: String,
        transform: (LedgerState) -> LedgerTransition,
    ): LedgerTransition = database.write { connection ->
        val state = load(connection, userToken, channel, token, method)
        val transition = transform(state)
        saveAccount(connection, userToken, transition.ledger, transition.available)
        replaceHolds(connection, userToken, transition.holds)
        saveOutcome(connection, channel, token, method, transition.outcome)
        transition
    }

    private fun accountExists(connection: Connection, userToken: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM jit_account WHERE user_token = ?").use { statement ->
            statement.setString(1, userToken)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun load(
        connection: Connection,
        userToken: String,
        channel: String,
        token: String,
        method: String,
    ): LedgerState {
        var ledger = BigDecimal.ZERO.money()
        var available = BigDecimal.ZERO.money()
        connection.prepareStatement(
            "SELECT ledger_balance, available_balance FROM jit_account WHERE user_token = ?",
        ).use { statement ->
            statement.setString(1, userToken)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    ledger = BigDecimal(rows.getString("ledger_balance")).money()
                    available = BigDecimal(rows.getString("available_balance")).money()
                }
            }
        }
        val holds = mutableListOf<FundingHold>()
        connection.prepareStatement(
            """
            SELECT token, original_token, amount, remaining, state, created_at
            FROM jit_hold WHERE user_token = ? ORDER BY created_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userToken)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    holds += FundingHold(
                        token = rows.getString("token"),
                        originalToken = rows.getString("original_token"),
                        amount = BigDecimal(rows.getString("amount")).money(),
                        remaining = BigDecimal(rows.getString("remaining")).money(),
                        state = HoldState.valueOf(rows.getString("state")),
                        createdAt = rows.getLong("created_at"),
                    )
                }
            }
        }
        val prior = connection.prepareStatement(
            "SELECT outcome FROM jit_seen WHERE channel = ? AND token = ? AND method = ?",
        ).use { statement ->
            statement.setString(1, channel)
            statement.setString(2, token)
            statement.setString(3, method)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString("outcome") else null }
        }
        return LedgerState(ledger, available, holds, prior)
    }

    private fun saveAccount(connection: Connection, userToken: String, ledger: BigDecimal, available: BigDecimal) {
        connection.prepareStatement(
            """
            MERGE INTO jit_account (user_token, ledger_balance, available_balance)
            KEY (user_token) VALUES (?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userToken)
            statement.setString(2, ledger.money().toPlainString())
            statement.setString(3, available.money().toPlainString())
            statement.executeUpdate()
        }
    }

    private fun replaceHolds(connection: Connection, userToken: String, holds: List<FundingHold>) {
        connection.prepareStatement("DELETE FROM jit_hold WHERE user_token = ?").use { statement ->
            statement.setString(1, userToken)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            INSERT INTO jit_hold (token, user_token, original_token, amount, remaining, state, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            holds.forEach { hold ->
                statement.setString(1, hold.token)
                statement.setString(2, userToken)
                statement.setString(3, hold.originalToken)
                statement.setString(4, hold.amount.toPlainString())
                statement.setString(5, hold.remaining.toPlainString())
                statement.setString(6, hold.state.name)
                statement.setLong(7, hold.createdAt)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun saveOutcome(connection: Connection, channel: String, token: String, method: String, outcome: String) {
        connection.prepareStatement(
            """
            MERGE INTO jit_seen (channel, token, method, outcome)
            KEY (channel, token, method) VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, channel)
            statement.setString(2, token)
            statement.setString(3, method)
            statement.setString(4, outcome)
            statement.executeUpdate()
        }
    }
}
