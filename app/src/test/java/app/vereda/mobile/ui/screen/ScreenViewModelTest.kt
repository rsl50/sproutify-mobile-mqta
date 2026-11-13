package app.vereda.mobile.ui.screen

import app.cash.turbine.test
import app.vereda.mobile.domain.LoadResult
import app.vereda.mobile.domain.ScreenRepository
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ButtonComponent
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.ComponentAction
import app.vereda.sdui.FieldRule
import app.vereda.sdui.Screen
import app.vereda.sdui.TextComponent
import app.vereda.sdui.TextFieldComponent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelTest {
    private val repository = mockk<ScreenRepository>(relaxed = true)
    private val signup = Screen(
        id = "signup",
        title = "Criar conta",
        components = listOf(
            TextFieldComponent(
                id = "email",
                label = "E-mail",
                keyboard = "email",
                validations = listOf(FieldRule("required", "Informe o e-mail")),
            ),
            ButtonComponent(
                id = "submit",
                label = "Criar conta",
                action = ComponentAction(type = "submit", endpoint = "actions/signup"),
            ),
            ButtonComponent(
                id = "go-signin",
                label = "Já tenho conta",
                style = "text",
                action = ComponentAction(type = "navigate", screenId = "signin"),
            ),
            ButtonComponent(
                id = "mystery",
                label = "?",
                action = ComponentAction(type = "unknown-action"),
            ),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.prepare() } returns Unit
        coEvery { repository.load(any()) } returns LoadResult.Ready(signup)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheBootstrapScreenFromTheServer() = runBlocking {
        val viewModel = ScreenViewModel(repository)
        val state = viewModel.state.value as ScreenUiState.Content
        assertEquals("signup", state.screen.id)
        assertEquals("", state.fields["email"])
    }

    @Test
    fun keepsSchemaErrorsOnTheScreenWithoutCallingTheServer() = runBlocking {
        val viewModel = ScreenViewModel(repository)
        viewModel.onEvent(ScreenUiEvent.ActionRequested("submit"))
        val state = viewModel.state.value as ScreenUiState.Content
        assertEquals("Informe o e-mail", state.fieldErrors["email"])
        coVerify(exactly = 0) { repository.submit(any(), any()) }
    }

    @Test
    fun appliesServerEffectsForSnackbarSessionAndScreen() = runBlocking {
        val home = Screen(id = "home", title = "Início", components = listOf(TextComponent("hello", "Olá")))
        coEvery { repository.submit("actions/signup", any()) } returns ActionResponse(
            effects = listOf(
                ClientEffect(type = "setSession", token = "session-1"),
                ClientEffect(type = "snackbar", message = "Conta criada."),
                ClientEffect(type = "showScreen", screen = home),
            ),
        )
        val viewModel = ScreenViewModel(repository)
        viewModel.effects.test {
            viewModel.onEvent(ScreenUiEvent.FieldChanged("email", "ana@example.com"))
            viewModel.onEvent(ScreenUiEvent.ActionRequested("submit"))
            assertEquals(ScreenUiEffect.Snackbar("Conta criada."), awaitItem())
        }
        coVerify { repository.saveSession("session-1") }
        assertEquals("home", (viewModel.state.value as ScreenUiState.Content).screen.id)
    }

    @Test
    fun navigateAndUnknownActionsStayGeneric() = runBlocking {
        val viewModel = ScreenViewModel(repository)
        viewModel.effects.test {
            viewModel.onEvent(ScreenUiEvent.ActionRequested("mystery"))
            viewModel.onEvent(ScreenUiEvent.ActionRequested("go-signin"))
            assertEquals(ScreenUiEffect.Navigate("signin"), awaitItem())
        }
        assertTrue(viewModel.state.value is ScreenUiState.Content)
    }

    @Test
    fun showsTheFailureMessageFromTheRepository() = runBlocking {
        coEvery { repository.load("home") } throws IllegalStateException("schemaVersion=2")
        val viewModel = ScreenViewModel(repository)
        viewModel.onEvent(ScreenUiEvent.OpenScreen("home"))
        assertEquals("schemaVersion=2", (viewModel.state.value as ScreenUiState.Failed).message)
    }
}
