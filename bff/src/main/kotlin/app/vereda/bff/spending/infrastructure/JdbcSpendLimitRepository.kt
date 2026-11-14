package app.vereda.bff.spending.infrastructure

import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.shared.persistence.VeredaDatabase
import app.vereda.bff.spending.domain.SpendLimit
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.spending.domain.SpendWindow

class JdbcSpendLimitRepository(
    private val database: VeredaDatabase,
) : SpendLimitRepository {
    override fun find(cardholderId: CardholderId): SpendLimit? = database.read { connection ->
        connection.prepareStatement(
            "SELECT control_token, amount_limit, usage_limit, velocity_window FROM spend_limit WHERE cardholder_id = ?",
        ).use { statement ->
            statement.setString(1, cardholderId.value)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return@read null
                SpendLimit(
                    cardholderId = cardholderId,
                    controlToken = rows.getString("control_token"),
                    amount = Money.parse(rows.getString("amount_limit"), Currency.USD),
                    usageLimit = rows.getInt("usage_limit"),
                    window = SpendWindow.valueOf(rows.getString("velocity_window")),
                )
            }
        }
    }

    override fun save(limit: SpendLimit) {
        database.write { connection ->
            connection.prepareStatement(
                """
                MERGE INTO spend_limit
                  (cardholder_id, control_token, amount_limit, usage_limit, velocity_window)
                KEY (cardholder_id)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, limit.cardholderId.value)
                statement.setString(2, limit.controlToken)
                statement.setString(3, limit.amount.amount.toPlainString())
                statement.setInt(4, limit.usageLimit)
                statement.setString(5, limit.window.name)
                statement.executeUpdate()
            }
        }
    }
}