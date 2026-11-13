package app.vereda.mobile.ui.renderer

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.vereda.mobile.ui.theme.VeredaTheme
import app.vereda.sdui.CardComponent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PaymentCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsBalanceLast4AndIsoProportionsFromTheSchema() {
        val card = CardComponent(
            id = "payment-card",
            holderName = "Ana Lima",
            lastFour = "4242",
            balanceLabel = "Saldo disponível",
            balanceText = "USD 100.00",
        )
        composeRule.setContent {
            VeredaTheme {
                PaymentCardFace(card, Modifier.width(317.2.dp))
            }
        }
        composeRule.onNodeWithText("Saldo disponível").assertIsDisplayed()
        composeRule.onNodeWithText("USD 100.00").assertIsDisplayed()
        composeRule.onNodeWithText("4242", substring = true).assertIsDisplayed()
        val bounds = composeRule.onNodeWithTag("payment-card").getBoundsInRoot()
        val ratio = (bounds.right - bounds.left).value / (bounds.bottom - bounds.top).value
        assertTrue("ratio=$ratio", ratio > 1.55f && ratio < 1.62f)
    }
}
