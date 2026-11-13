package app.vereda.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.vereda.mobile.ui.screen.ScreenHost
import app.vereda.mobile.ui.screen.ScreenUiState
import app.vereda.mobile.ui.theme.VeredaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShellSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shellShowsLoadingIndicator() {
        composeRule.setContent {
            VeredaTheme {
                ScreenHost(state = ScreenUiState.Loading, onEvent = {})
            }
        }
        composeRule.onNodeWithTag("loading").assertIsDisplayed()
    }
}
