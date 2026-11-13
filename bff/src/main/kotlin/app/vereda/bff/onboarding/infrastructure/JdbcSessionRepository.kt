package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken
import app.vereda.bff.shared.persistence.VeredaDatabase

class JdbcSessionRepository(
    private val database: VeredaDatabase,
) : SessionRepository {
    override fun open(cardholderId: CardholderId): SessionToken {
        val token = SessionToken.newToken()
        database.write { connection ->
            connection.prepareStatement(
                "INSERT INTO app_session (token, cardholder_id) VALUES (?, ?)",
            ).use { statement ->
                statement.setString(1, token.value)
                statement.setString(2, cardholderId.value)
                statement.executeUpdate()
            }
        }
        return token
    }

    override fun cardholderId(token: SessionToken): CardholderId? = database.read { connection ->
        connection.prepareStatement("SELECT cardholder_id FROM app_session WHERE token = ?").use { statement ->
            statement.setString(1, token.value)
            statement.executeQuery().use { rows ->
                if (rows.next()) CardholderId(rows.getString("cardholder_id")) else null
            }
        }
    }

    override fun close(token: SessionToken) {
        database.write { connection ->
            connection.prepareStatement("DELETE FROM app_session WHERE token = ?").use { statement ->
                statement.setString(1, token.value)
                statement.executeUpdate()
            }
        }
    }
}
