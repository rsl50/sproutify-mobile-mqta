package app.vereda.bff.shared.persistence

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class VeredaDatabase private constructor(
    private val connection: Connection,
) : AutoCloseable {
    fun <T> read(block: (Connection) -> T): T = synchronized(connection) { block(connection) }

    fun <T> write(block: (Connection) -> T): T = synchronized(connection) {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previous
        }
    }

    override fun close() {
        connection.close()
    }

    fun columnNames(table: String): List<String> = read { connection ->
        connection.metaData.getColumns(null, null, table.uppercase(), null).use { rows ->
            generateSequence { if (rows.next()) rows.getString("COLUMN_NAME") else null }.toList()
        }
    }

    companion object {
        fun open(file: File = defaultFile()): VeredaDatabase {
            file.parentFile?.mkdirs()
            val url = "jdbc:h2:file:${file.absolutePath};DB_CLOSE_ON_EXIT=FALSE"
            val connection = DriverManager.getConnection(url, "sa", "")
            val database = VeredaDatabase(connection)
            database.migrate()
            return database
        }

        fun defaultFile(): File {
            val override = System.getenv("VEREDA_DB_PATH")?.trim().orEmpty()
            return if (override.isEmpty()) File("bff/data/vereda") else File(override)
        }

        private fun VeredaDatabase.migrate() {
            write { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS cardholder (
                          id VARCHAR PRIMARY KEY,
                          first_name VARCHAR NOT NULL,
                          last_name VARCHAR NOT NULL,
                          email VARCHAR NOT NULL UNIQUE,
                          password_hash VARCHAR NOT NULL,
                          user_token VARCHAR NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS issued_card (
                          cardholder_id VARCHAR PRIMARY KEY,
                          token VARCHAR NOT NULL,
                          last_four CHAR(4) NOT NULL,
                          product_token VARCHAR NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS app_session (
                          token VARCHAR PRIMARY KEY,
                          cardholder_id VARCHAR NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
