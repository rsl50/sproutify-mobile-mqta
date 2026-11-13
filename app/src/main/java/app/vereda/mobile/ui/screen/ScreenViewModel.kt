package app.vereda.mobile.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vereda.mobile.domain.LoadResult
import app.vereda.mobile.domain.ScreenRepository
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.BOOTSTRAP_SCREEN_ID
import app.vereda.sdui.ButtonComponent
import app.vereda.sdui.FieldRules
import app.vereda.sdui.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenViewModel @Inject constructor(
    private val repository: ScreenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ScreenUiState>(ScreenUiState.Loading)
    val state: StateFlow<ScreenUiState> = _state.asStateFlow()

    private val _effects = Channel<ScreenUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var currentScreenId: String = BOOTSTRAP_SCREEN_ID

    init {
        onEvent(ScreenUiEvent.Started)
    }

    fun onEvent(event: ScreenUiEvent) {
        when (event) {
            ScreenUiEvent.Started -> {
                viewModelScope.launch {
                    repository.prepare()
                    load(BOOTSTRAP_SCREEN_ID)
                }
            }
            ScreenUiEvent.Retry -> load(currentScreenId)
            ScreenUiEvent.Refresh -> refresh()
            is ScreenUiEvent.OpenScreen -> load(event.screenId)
            is ScreenUiEvent.FieldChanged -> updateField(event.id, event.value)
            is ScreenUiEvent.ActionRequested -> onAction(event.componentId)
        }
    }

    private fun updateField(id: String, value: String) {
        val content = _state.value as? ScreenUiState.Content ?: return
        _state.value = content.copy(
            fields = content.fields + (id to value),
            fieldErrors = content.fieldErrors - id,
        )
    }

    private fun onAction(componentId: String) {
        val content = _state.value as? ScreenUiState.Content ?: return
        val button = content.screen.find(componentId) as? ButtonComponent ?: return
        if (content.submitting) return
        when (button.action.type) {
            "submit", "signOut" -> submit(content, button.action.endpoint, includeFields = button.action.type == "submit")
            "navigate", "refresh" -> button.action.screenId?.let { screenId ->
                viewModelScope.launch { _effects.send(ScreenUiEffect.Navigate(screenId)) }
            }
            else -> Unit
        }
    }

    private fun submit(content: ScreenUiState.Content, endpoint: String?, includeFields: Boolean) {
        if (endpoint.isNullOrBlank()) return
        val errors = if (includeFields) FieldRules.errors(content.screen, content.fields) else emptyMap()
        if (errors.isNotEmpty()) {
            _state.value = content.copy(fieldErrors = errors)
            return
        }
        viewModelScope.launch {
            _state.value = content.copy(submitting = true, fieldErrors = emptyMap())
            try {
                val response = repository.submit(endpoint, if (includeFields) content.fields else emptyMap())
                apply(response, content)
            } catch (error: Exception) {
                _state.value = content.copy(submitting = false)
                _effects.send(ScreenUiEffect.Snackbar(error.message.orEmpty()))
            }
        }
    }

    private fun refresh() {
        val current = _state.value as? ScreenUiState.Content
        viewModelScope.launch {
            if (current != null) {
                _state.value = current.copy(refreshing = true, submitting = false)
            }
            try {
                when (val result = repository.load(currentScreenId)) {
                    is LoadResult.Ready -> show(result.screen)
                    is LoadResult.Redirect -> apply(result.response, fallback = current)
                }
            } catch (error: Exception) {
                if (current != null) {
                    _state.value = current.copy(refreshing = false)
                    _effects.send(ScreenUiEffect.Snackbar(error.message.orEmpty()))
                } else {
                    _state.value = ScreenUiState.Failed(error.message.orEmpty())
                }
            }
        }
    }

    private fun load(screenId: String) {
        currentScreenId = screenId
        viewModelScope.launch {
            _state.value = ScreenUiState.Loading
            try {
                when (val result = repository.load(screenId)) {
                    is LoadResult.Ready -> show(result.screen)
                    is LoadResult.Redirect -> apply(result.response, fallback = null)
                }
            } catch (error: Exception) {
                _state.value = ScreenUiState.Failed(error.message.orEmpty())
            }
        }
    }

    private suspend fun apply(response: ActionResponse, fallback: ScreenUiState.Content?) {
        if (response.fieldErrors.isNotEmpty() && fallback != null) {
            _state.value = fallback.copy(submitting = false, fieldErrors = response.fieldErrors)
        }
        var replacement: Screen? = null
        response.effects.forEach { effect ->
            when (effect.type) {
                "setSession" -> effect.token?.let { repository.saveSession(it) }
                "clearSession" -> repository.saveSession(null)
                "showScreen" -> replacement = effect.screen
                "snackbar" -> effect.message?.let { _effects.send(ScreenUiEffect.Snackbar(it)) }
                "navigate" -> effect.screenId?.let { _effects.send(ScreenUiEffect.Navigate(it)) }
                else -> Unit
            }
        }
        val shown = replacement
        if (shown != null) {
            show(shown)
        } else if (response.fieldErrors.isEmpty() && fallback != null &&
            response.effects.none { it.type == "navigate" }
        ) {
            _state.value = fallback.copy(submitting = false)
        }
    }

    private fun show(screen: Screen) {
        currentScreenId = screen.id
        _state.value = ScreenUiState.Content(
            screen = screen,
            fields = screen.initialFields(),
            fieldErrors = emptyMap(),
            submitting = false,
            refreshing = false,
        )
    }
}
