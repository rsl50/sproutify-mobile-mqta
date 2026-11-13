package app.vereda.mobile.ui.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.vereda.mobile.ui.screen.ScreenUiEvent
import app.vereda.mobile.ui.screen.ScreenUiState
import androidx.compose.ui.Alignment
import app.vereda.sdui.ButtonComponent
import app.vereda.sdui.CardComponent
import app.vereda.sdui.Component
import app.vereda.sdui.ListComponent
import app.vereda.sdui.SpacerComponent
import app.vereda.sdui.TextComponent
import app.vereda.sdui.TextFieldComponent
import app.vereda.sdui.UnknownComponent

@Composable
fun ComponentList(
    components: List<Component>,
    state: ScreenUiState.Content,
    onEvent: (ScreenUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        components.forEach { component ->
            RenderComponent(component, state, onEvent)
        }
    }
}

@Composable
fun RenderComponent(
    component: Component,
    state: ScreenUiState.Content,
    onEvent: (ScreenUiEvent) -> Unit,
) {
    when (component) {
        is TextComponent -> Text(
            text = component.value,
            style = when (component.style) {
                "title" -> MaterialTheme.typography.headlineMedium
                "caption" -> MaterialTheme.typography.bodyMedium
                else -> MaterialTheme.typography.bodyLarge
            },
            color = if (component.style == "caption") {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(component.id),
        )
        is TextFieldComponent -> OutlinedTextField(
            value = state.fields[component.id].orEmpty(),
            onValueChange = { onEvent(ScreenUiEvent.FieldChanged(component.id, it)) },
            label = { Text(component.label) },
            supportingText = state.fieldErrors[component.id]?.let { message -> { Text(message) } },
            isError = component.id in state.fieldErrors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType(component.keyboard)),
            visualTransformation = if (component.keyboard == "password") {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(component.id),
        )
        is ButtonComponent -> {
            val onClick = { onEvent(ScreenUiEvent.ActionRequested(component.id)) }
            if (component.style == "text") {
                TextButton(
                    onClick = onClick,
                    enabled = !state.submitting,
                    modifier = Modifier.testTag(component.id),
                ) {
                    Text(component.label)
                }
            } else {
                Button(
                    onClick = onClick,
                    enabled = !state.submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(component.id),
                ) {
                    if (state.submitting && component.action.type == "submit") {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .testTag("submitting"),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(component.label)
                    }
                }
            }
        }
        is CardComponent -> PaymentCardFace(component)
        is SpacerComponent -> Spacer(Modifier.height(spacerHeight(component.size)))
        is ListComponent -> ComponentList(component.children, state, onEvent)
        is UnknownComponent -> Text(
            text = component.typeName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.testTag("unknown-${component.id}"),
        )
    }
}

@Composable
fun PaymentCardFace(card: CardComponent, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .testTag(card.id)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B3A4B), Color(0xFF0E6B5C)),
                ),
            )
            .padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Text(card.balanceLabel, color = Color(0xFFD5E4EA))
            Text(
                card.balanceText,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.testTag("${card.id}-balance"),
            )
        }
        Text(
            text = "•••• •••• •••• ${card.lastFour}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .testTag("${card.id}-last4"),
        )
        Text(
            text = card.holderName,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

internal const val CARD_ASPECT_RATIO = 1.586f

private fun keyboardType(keyboard: String): KeyboardType = when (keyboard) {
    "email" -> KeyboardType.Email
    "password" -> KeyboardType.Password
    "number" -> KeyboardType.Number
    else -> KeyboardType.Text
}

private fun spacerHeight(size: String) = when (size) {
    "small" -> 8.dp
    "large" -> 32.dp
    else -> 16.dp
}
