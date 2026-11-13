package app.vereda.bff.config

import java.io.File

data class MarqetaSettings(
    val baseUrl: String,
    val applicationToken: String,
    val adminAccessToken: String,
)

object MarqetaSettingsLoader {
    const val DEFAULT_BASE_URL = "https://sandbox-api.marqeta.com/v3"

    fun load(
        env: Map<String, String> = System.getenv(),
        files: List<File> = defaultFiles(),
    ): MarqetaSettings {
        val fileValues = linkedMapOf<String, String>()
        files.filter { it.isFile }.forEach { file ->
            fileValues.putAll(parseKeyValues(file.readText()))
        }
        fun value(key: String): String? = env[key]?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileValues[key]?.trim()?.takeIf { it.isNotEmpty() }

        val applicationToken = value("MARQETA_APPLICATION_TOKEN")
        val adminAccessToken = value("MARQETA_ADMIN_ACCESS_TOKEN")
        val missing = buildList {
            if (applicationToken == null) add("MARQETA_APPLICATION_TOKEN")
            if (adminAccessToken == null) add("MARQETA_ADMIN_ACCESS_TOKEN")
        }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Defina ${missing.joinToString(" e ")} no ambiente ou em bff/local.properties " +
                    "(veja bff/local.properties.example).",
            )
        }
        return MarqetaSettings(
            baseUrl = value("MARQETA_BASE_URL") ?: DEFAULT_BASE_URL,
            applicationToken = applicationToken!!,
            adminAccessToken = adminAccessToken!!,
        )
    }

    fun defaultFiles(): List<File> = listOf(
        File("bff/local.properties"),
        File("bff/.env"),
    )

    internal fun parseKeyValues(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEach
            val separator = line.indexOf('=').takeIf { it >= 0 } ?: line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            var value = line.substring(separator + 1).trim()
            if (value.length >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'")))
            ) {
                value = value.substring(1, value.length - 1)
            }
            values[key] = value
        }
        return values
    }
}
