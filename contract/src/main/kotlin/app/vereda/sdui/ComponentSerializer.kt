package app.vereda.sdui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ComponentSerializer : KSerializer<Component> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Component")

    override fun deserialize(decoder: Decoder): Component {
        val input = decoder as? JsonDecoder ?: error("Component JSON expected")
        val element = input.decodeJsonElement().jsonObject
        val type = element["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        return decodeKnown(input.json, type, element)
    }

    override fun serialize(encoder: Encoder, value: Component) {
        val output = encoder as? JsonEncoder ?: error("Component JSON expected")
        val payload = when (value) {
            is TextComponent -> output.json.encodeToJsonElement(TextComponent.serializer(), value)
            is TextFieldComponent -> output.json.encodeToJsonElement(TextFieldComponent.serializer(), value)
            is ButtonComponent -> output.json.encodeToJsonElement(ButtonComponent.serializer(), value)
            is SpacerComponent -> output.json.encodeToJsonElement(SpacerComponent.serializer(), value)
            is ListComponent -> output.json.encodeToJsonElement(ListComponent.serializer(), value)
            is UnknownComponent -> buildJsonObject { put("id", value.id) }
        }
        val typeName = when (value) {
            is TextComponent -> "text"
            is TextFieldComponent -> "textField"
            is ButtonComponent -> "button"
            is SpacerComponent -> "spacer"
            is ListComponent -> "list"
            is UnknownComponent -> value.typeName
        }
        output.encodeJsonElement(JsonObject(payload.jsonObject + ("type" to JsonPrimitive(typeName))))
    }

    private fun decodeKnown(json: kotlinx.serialization.json.Json, type: String, element: JsonObject): Component {
        return when (type) {
            "text" -> json.decodeFromJsonElement(TextComponent.serializer(), element)
            "textField" -> json.decodeFromJsonElement(TextFieldComponent.serializer(), element)
            "button" -> json.decodeFromJsonElement(ButtonComponent.serializer(), element)
            "spacer" -> json.decodeFromJsonElement(SpacerComponent.serializer(), element)
            "list" -> json.decodeFromJsonElement(ListComponent.serializer(), element)
            else -> UnknownComponent(
                id = element["id"]?.jsonPrimitive?.contentOrNull ?: "",
                typeName = type,
            )
        }
    }
}
