package app.vereda.mobile.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vereda.mobile.debug.DebugTraceLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugUiState(
    val expanded: Boolean = false,
    val text: String = "",
)

sealed interface DebugUiEvent {
    data object Toggle : DebugUiEvent
    data object Clear : DebugUiEvent
    data object Copy : DebugUiEvent
}

sealed interface DebugUiEffect {
    data class Copy(val text: String) : DebugUiEffect
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val log: DebugTraceLog,
) : ViewModel() {
    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    private val _effects = Channel<DebugUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            log.text.collect { text ->
                _state.update { it.copy(text = text) }
            }
        }
    }

    fun onEvent(event: DebugUiEvent) {
        when (event) {
            DebugUiEvent.Toggle -> _state.update { it.copy(expanded = !it.expanded) }
            DebugUiEvent.Clear -> log.clear()
            DebugUiEvent.Copy -> viewModelScope.launch {
                _effects.send(DebugUiEffect.Copy(_state.value.text))
            }
        }
    }
}
