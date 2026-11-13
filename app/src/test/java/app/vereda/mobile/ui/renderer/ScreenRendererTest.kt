package app.vereda.mobile.ui.renderer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.vereda.mobile.ui.screen.ScreenHost
import app.vereda.mobile.ui.screen.ScreenUiState
import app.vereda.mobile.ui.theme.VeredaTheme
import app.vereda.sdui.Screen
import app.vereda.sdui.ScreenJson
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenRendererTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersServerCopyAndUnknownComponents() {
        val screen = ScreenJson.decodeFromString(
            Screen.serializer(),
            """
            {
              "schemaVersion": 1,
              "id": "signup",
              "title": "Criar conta",
              "components": [
                {"type": "text", "id": "heading", "value": "Criar conta", "style": "title"},
                {"type": "textField", "id": "email", "label": "E-mail", "keyboard": "email"},
                {"type": "flux-capacitor", "id": "fc"}
              ]
            }
            """.trimIndent(),
        )
        composeRule.setContent {
            VeredaTheme {
                ScreenHost(
                    state = ScreenUiState.Content(
                        screen = screen,
                        fields = screen.initialFields(),
                        fieldErrors = emptyMap(),
                        submitting = false,
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithText("Criar conta").assertIsDisplayed()
        composeRule.onNodeWithText("E-mail").assertIsDisplayed()
        composeRule.onNodeWithTag("unknown-fc").assertIsDisplayed()
        composeRule.onNodeWithText("flux-capacitor").assertIsDisplayed()
    }
}
