package app.vereda.mobile.ui.debug

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun DebugPanel(viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DebugUiEffect.Copy -> launch {
                    clipboard.setClipEntry(
                        androidx.compose.ui.platform.ClipEntry(ClipData.newPlainText("vereda-debug", effect.text)),
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("debug-panel")) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { viewModel.onEvent(DebugUiEvent.Toggle) }, modifier = Modifier.testTag("debug-toggle")) {
                Text(if (state.expanded) "Debug ▾" else "Debug ▸")
            }
            Row {
                TextButton(onClick = { viewModel.onEvent(DebugUiEvent.Clear) }, modifier = Modifier.testTag("debug-clear")) {
                    Text("Limpar")
                }
                TextButton(onClick = { viewModel.onEvent(DebugUiEvent.Copy) }, modifier = Modifier.testTag("debug-copy")) {
                    Text("Copiar")
                }
            }
        }
        if (state.expanded) {
            Text(
                text = "app → bff · bff → marqeta · marqeta → bff",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            SelectionContainer {
                Text(
                    text = state.text.ifBlank { "Nenhuma troca ainda." },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("debug-text"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
