package app.vereda.bff.onboarding.infrastructure

import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderId
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.Email
import app.vereda.bff.onboarding.domain.Password
import app.vereda.bff.onboarding.domain.PersonName
import app.vereda.bff.onboarding.domain.UserToken
import app.vereda.bff.shared.persistence.VeredaDatabase
import java.sql.ResultSet

class JdbcCardholderRepository(
    private val database: VeredaDatabase,
) : CardholderRepository {
    override fun findByEmail(email: Email): Cardholder? = database.read { connection ->
        connection.prepareStatement("SELECT * FROM cardholder WHERE email = ?").use { statement ->
            statement.setString(1, email.value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toCardholder() else null }
        }
    }

    override fun findById(id: CardholderId): Cardholder? = database.read { connection ->
        connection.prepareStatement("SELECT * FROM cardholder WHERE id = ?").use { statement ->
            statement.setString(1, id.value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toCardholder() else null }
        }
    }

    override fun save(cardholder: Cardholder) {
        database.write { connection ->
            connection.prepareStatement(
                """
                MERGE INTO cardholder
                  (id, first_name, last_name, email, password_hash, user_token)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, cardholder.id.value)
                statement.setString(2, cardholder.name.first)
                statement.setString(3, cardholder.name.last)
                statement.setString(4, cardholder.email.value)
                statement.setString(5, cardholder.password.hash)
                statement.setString(6, cardholder.userToken.value)
                statement.executeUpdate()
            }
        }
    }
}

private fun ResultSet.toCardholder(): Cardholder = Cardholder.restore(
    id = CardholderId(getString("id")),
    name = PersonName(getString("first_name"), getString("last_name")),
    email = Email.parse(getString("email")),
    password = Password.fromStored(getString("password_hash")),
    userToken = UserToken.of(getString("user_token")),
)
