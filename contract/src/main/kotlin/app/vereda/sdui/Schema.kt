package app.vereda.sdui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SCHEMA_VERSION = 1
const val BOOTSTRAP_SCREEN_ID = "bootstrap"

val ScreenJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}

@Serializable
data class Screen(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val title: String = "",
    val components: List<@Serializable(with = ComponentSerializer::class) Component> = emptyList(),
) {
    fun find(componentId: String): Component? = findIn(components, componentId)

    fun initialFields(): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        fun walk(items: List<Component>) {
            items.forEach { component ->
                when (component) {
                    is TextFieldComponent -> fields[component.id] = component.value
                    is ListComponent -> walk(component.children)
                    else -> Unit
                }
            }
        }
        walk(components)
        return fields
    }
}

private fun findIn(items: List<Component>, componentId: String): Component? {
    items.forEach { component ->
        if (component.id == componentId) return component
        if (component is ListComponent) {
            findIn(component.children, componentId)?.let { return it }
        }
    }
    return null
}

sealed interface Component {
    val id: String
}

@Serializable
data class TextComponent(
    override val id: String,
    val value: String,
    val style: String = "body",
) : Component

@Serializable
data class TextFieldComponent(
    override val id: String,
    val label: String,
    val value: String = "",
    val keyboard: String = "text",
    val validations: List<FieldRule> = emptyList(),
) : Component

@Serializable
data class ButtonComponent(
    override val id: String,
    val label: String,
    val style: String = "primary",
    val action: ComponentAction,
) : Component

@Serializable
data class CardComponent(
    override val id: String,
    val holderName: String,
    val lastFour: String,
    val balanceLabel: String,
    val balanceText: String,
) : Component

@Serializable
data class SpacerComponent(
    override val id: String,
    val size: String = "medium",
) : Component

@Serializable
data class ListComponent(
    override val id: String,
    val children: List<@Serializable(with = ComponentSerializer::class) Component> = emptyList(),
) : Component

data class UnknownComponent(
    override val id: String,
    val typeName: String,
) : Component

@Serializable
data class FieldRule(
    val type: String,
    val message: String,
    val min: Int? = null,
)

@Serializable
data class ComponentAction(
    val type: String,
    val endpoint: String? = null,
    val screenId: String? = null,
)

@Serializable
data class ActionRequest(
    val fields: Map<String, String> = emptyMap(),
)

@Serializable
data class ActionResponse(
    val schemaVersion: Int = SCHEMA_VERSION,
    val fieldErrors: Map<String, String> = emptyMap(),
    val effects: List<ClientEffect> = emptyList(),
)

@Serializable
data class ClientEffect(
    val type: String,
    val message: String? = null,
    val screenId: String? = null,
    val token: String? = null,
    val screen: Screen? = null,
)

@Serializable
data class ErrorBody(
    val message: String = "",
)
