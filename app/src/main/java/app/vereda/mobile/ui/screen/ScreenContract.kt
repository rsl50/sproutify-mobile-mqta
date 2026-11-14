package app.vereda.mobile.ui.screen

import app.vereda.sdui.Screen

sealed interface ScreenUiState {
    data object Loading : ScreenUiState

    data class Content(
        val screen: Screen,
        val fields: Map<String, String>,
        val fieldErrors: Map<String, String>,
        val submitting: Boolean,
        val refreshing: Boolean = false,
    ) : ScreenUiState

    data class Failed(val message: String) : ScreenUiState
}

sealed interface ScreenUiEvent {
    data object Started : ScreenUiEvent

    data class FieldChanged(val id: String, val value: String) : ScreenUiEvent

    data class ActionRequested(val componentId: String) : ScreenUiEvent

    data class OpenScreen(val screenId: String) : ScreenUiEvent

    data object Retry : ScreenUiEvent

    data object Refresh : ScreenUiEvent
}

sealed interface ScreenUiEffect {
    data class Snackbar(val message: String) : ScreenUiEffect

    data class Navigate(val screenId: String) : ScreenUiEffect
}
