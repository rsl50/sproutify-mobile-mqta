package app.vereda.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.vereda.mobile.data.BffEndpoint
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        BffEndpoint.override = server.url("/").toString()
    }

    @After
    fun tearDown() {
        BffEndpoint.override = null
        server.shutdown()
    }

    @Test
    fun signupFromTheFakeBffShowsTheCard() {
        server.enqueue(json(SIGNUP_SCREEN))
        server.enqueue(json(HOME_ACTION))
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Criar conta").assertIsDisplayed()
            composeRule.onNodeWithTag("firstName").performTextInput("Ana")
            composeRule.onNodeWithTag("lastName").performTextInput("Lima")
            composeRule.onNodeWithTag("email").performTextInput("ana@example.com")
            composeRule.onNodeWithTag("password").performTextInput("segredo12")
            composeRule.onNodeWithTag("submit").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("4242", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bloquear cartão").assertIsDisplayed()
            composeRule.onNodeWithText("Limite de gastos").assertIsDisplayed()
        }
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        val SIGNUP_SCREEN = """
            {"schemaVersion":1,"id":"signup","title":"Criar conta","components":[
              {"type":"text","id":"heading","value":"Criar conta","style":"title"},
              {"type":"textField","id":"firstName","label":"Nome","validations":[{"type":"required","message":"Informe o nome"}]},
              {"type":"textField","id":"lastName","label":"Sobrenome","validations":[{"type":"required","message":"Informe o sobrenome"}]},
              {"type":"textField","id":"email","label":"E-mail","keyboard":"email","validations":[{"type":"required","message":"Informe o e-mail"}]},
              {"type":"textField","id":"password","label":"Senha","keyboard":"password","validations":[{"type":"required","message":"Informe a senha"},{"type":"minLength","message":"Mínimo de 8 caracteres","min":8}]},
              {"type":"button","id":"submit","label":"Criar conta","style":"primary","action":{"type":"submit","endpoint":"actions/signup"}}
            ]}
        """.trimIndent()

        val HOME_ACTION = """
            {"schemaVersion":1,"fieldErrors":{},"effects":[
              {"type":"setSession","token":"session-1"},
              {"type":"showScreen","screen":{
                "schemaVersion":1,"id":"home","title":"Início","components":[
                  {"type":"card","id":"payment-card","holderName":"Ana Lima","lastFour":"4242","balanceLabel":"Saldo disponível","balanceText":"USD 100.00"},
                  {"type":"text","id":"card-state","value":"Cartão ativo","style":"caption"},
                  {"type":"button","id":"toggle-card","label":"Bloquear cartão","style":"text","action":{"type":"submit","endpoint":"actions/card-state"}},
                  {"type":"button","id":"spend-limit","label":"Limite de gastos","style":"text","action":{"type":"navigate","screenId":"spend-limit"}}
                ]
              }}
            ]}
        """.trimIndent()
    }
}
