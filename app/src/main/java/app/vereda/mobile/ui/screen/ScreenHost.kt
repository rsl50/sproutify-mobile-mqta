package app.vereda.mobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.vereda.mobile.ui.renderer.ComponentList
import kotlinx.coroutines.launch

@Composable
fun VeredaNavHost(viewModel: ScreenViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "experience") {
        composable("experience") {
            ExperienceRoute(viewModel)
        }
    }
}

@Composable
fun ExperienceRoute(viewModel: ScreenViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScreenUiEffect.Snackbar -> launch { snackbar.showSnackbar(effect.message) }
                is ScreenUiEffect.Navigate -> viewModel.onEvent(ScreenUiEvent.OpenScreen(effect.screenId))
            }
        }
    }
    ScreenHost(state = state, onEvent = viewModel::onEvent, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHost(
    state: ScreenUiState,
    onEvent: (ScreenUiEvent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (state) {
            ScreenUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("loading"))
            }
            is ScreenUiState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .clickable { onEvent(ScreenUiEvent.Retry) }
                    .testTag("retry"),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message, modifier = Modifier.testTag("failure"))
            }
            is ScreenUiState.Content -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onEvent(ScreenUiEvent.Refresh) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("pull-refresh"),
            ) {
                ComponentList(
                    components = state.screen.components,
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
