package app.vereda.sdui

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCodecTest {
    @Test
    fun roundTripsASignupScreen() {
        val screen = Screen(
            id = "signup",
            title = "Criar conta",
            components = listOf(
                TextComponent(id = "heading", value = "Criar conta", style = "title"),
                TextFieldComponent(
                    id = "email",
                    label = "E-mail",
                    keyboard = "email",
                    validations = listOf(FieldRule(type = "required", message = "Informe o e-mail")),
                ),
                ButtonComponent(
                    id = "submit",
                    label = "Criar conta",
                    action = ComponentAction(type = "submit", endpoint = "actions/signup"),
                ),
            ),
        )

        val json = ScreenJson.encodeToString(screen)
        val decoded = ScreenJson.decodeFromString(Screen.serializer(), json)

        assertEquals(screen, decoded)
        assertTrue(json.contains("\"type\":\"textField\""))
    }

    @Test
    fun unknownComponentFallsBackWithoutDroppingTheType() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "x",
              "title": "t",
              "components": [
                {"type": "flux-capacitor", "id": "fc", "surprise": true}
              ]
            }
        """.trimIndent()

        val screen = ScreenJson.decodeFromString(Screen.serializer(), json)
        val unknown = screen.components.single() as UnknownComponent

        assertEquals("fc", unknown.id)
        assertEquals("flux-capacitor", unknown.typeName)
    }

    @Test
    fun fieldRulesUseMessagesFromTheSchema() {
        val field = TextFieldComponent(
            id = "email",
            label = "E-mail",
            validations = listOf(
                FieldRule("required", "Informe o e-mail"),
                FieldRule("email", "E-mail inválido"),
            ),
        )
        assertEquals("Informe o e-mail", FieldRules.errorFor(field, " "))
        assertEquals("E-mail inválido", FieldRules.errorFor(field, "ana"))
        assertEquals(null, FieldRules.errorFor(field, "ana@example.com"))
    }
}
