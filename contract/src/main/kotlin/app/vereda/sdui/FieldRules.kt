package app.vereda.sdui

import java.math.BigDecimal

object FieldRules {
    fun errors(screen: Screen, fields: Map<String, String>): Map<String, String> {
        val found = linkedMapOf<String, String>()
        fun walk(items: List<Component>) {
            items.forEach { component ->
                when (component) {
                    is TextFieldComponent -> {
                        val value = fields[component.id] ?: component.value
                        errorFor(component, value)?.let { found[component.id] = it }
                    }
                    is ListComponent -> walk(component.children)
                    else -> Unit
                }
            }
        }
        walk(screen.components)
        return found
    }

    fun errorFor(field: TextFieldComponent, value: String): String? {
        field.validations.forEach { rule ->
            val failed = when (rule.type) {
                "required" -> value.isBlank()
                "email" -> !isEmail(value)
                "minLength" -> value.length < (rule.min ?: 0)
                "money" -> !isPositiveMoney(value)
                else -> false
            }
            if (failed) return rule.message
        }
        return null
    }

    fun isEmail(value: String): Boolean {
        val trimmed = value.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return false
        val domain = trimmed.substring(at + 1)
        val dot = domain.indexOf('.')
        return dot > 0 && dot < domain.lastIndex
    }

    private fun isPositiveMoney(value: String): Boolean {
        val amount = value.trim().toBigDecimalOrNull() ?: return false
        return amount.scale() <= 2 && amount > BigDecimal.ZERO
    }
}
