package app.vereda.bff.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MarqetaSettingsLoaderTest {
    @Test
    fun readsTokensFromPropertiesFile() {
        val dir = Files.createTempDirectory("vereda-settings").toFile()
        val properties = File(dir, "local.properties")
        properties.writeText(
            """
            MARQETA_APPLICATION_TOKEN=app-from-file
            MARQETA_ADMIN_ACCESS_TOKEN=admin-from-file
            """.trimIndent(),
        )

        val settings = MarqetaSettingsLoader.load(env = emptyMap(), files = listOf(properties))

        assertEquals("app-from-file", settings.applicationToken)
        assertEquals("admin-from-file", settings.adminAccessToken)
        assertEquals(MarqetaSettingsLoader.DEFAULT_BASE_URL, settings.baseUrl)
    }

    @Test
    fun environmentOverridesFile() {
        val dir = Files.createTempDirectory("vereda-settings").toFile()
        val properties = File(dir, ".env")
        properties.writeText(
            """
            MARQETA_APPLICATION_TOKEN=app-from-file
            MARQETA_ADMIN_ACCESS_TOKEN=admin-from-file
            MARQETA_BASE_URL=https://example.test/v3
            """.trimIndent(),
        )

        val settings = MarqetaSettingsLoader.load(
            env = mapOf(
                "MARQETA_APPLICATION_TOKEN" to "app-from-env",
                "MARQETA_BASE_URL" to "https://sandbox-api.marqeta.com/v3",
            ),
            files = listOf(properties),
        )

        assertEquals("app-from-env", settings.applicationToken)
        assertEquals("admin-from-file", settings.adminAccessToken)
        assertEquals("https://sandbox-api.marqeta.com/v3", settings.baseUrl)
    }

    @Test
    fun missingTokensFailWithoutEchoingValues() {
        val error = assertThrows(IllegalStateException::class.java) {
            MarqetaSettingsLoader.load(env = emptyMap(), files = emptyList())
        }
        assertTrue(error.message!!.contains("MARQETA_APPLICATION_TOKEN"))
        assertTrue(error.message!!.contains("MARQETA_ADMIN_ACCESS_TOKEN"))
    }
}
