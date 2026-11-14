package app.vereda.bff.issuing.infrastructure

import app.vereda.bff.issuing.domain.CardLast4
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.issuing.domain.CardToken
import app.vereda.bff.issuing.domain.IssuedCard
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.shared.persistence.VeredaDatabase

class JdbcIssuedCardRepository(
    private val database: VeredaDatabase,
) : IssuedCardRepository {
    override fun findByCardholder(cardholderId: CardholderId): IssuedCard? = database.read { connection ->
        connection.prepareStatement(
            "SELECT token, last_four, product_token FROM issued_card WHERE cardholder_id = ?",
        ).use { statement ->
            statement.setString(1, cardholderId.value)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return@read null
                IssuedCard.issue(
                    cardholderId = cardholderId,
                    token = CardToken.of(rows.getString("token")),
                    last4 = CardLast4.of(rows.getString("last_four").trim()),
                    productToken = CardProductToken.of(rows.getString("product_token")),
                )
            }
        }
    }

    override fun save(card: IssuedCard) {
        database.write { connection ->
            connection.prepareStatement(
                """
                MERGE INTO issued_card (cardholder_id, token, last_four, product_token)
                KEY (cardholder_id)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, card.cardholderId.value)
                statement.setString(2, card.token.value)
                statement.setString(3, card.last4.value)
                statement.setString(4, card.productToken.value)
                statement.executeUpdate()
            }
        }
    }
}
