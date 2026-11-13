package app.vereda.bff.experience.application

import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.application.RegisterCardholderCommand
import app.vereda.bff.onboarding.domain.CardholderAlreadyExists
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules

class ScreenQueries(
    private val catalog: ScreenCatalog,
) {
    fun get(screenId: String): app.vereda.sdui.Screen = when (screenId) {
        "bootstrap", "signup" -> catalog.signup()
        else -> throw UnknownScreen(screenId)
    }
}

class SubmitSignup(
    private val register: RegisterCardholder,
    private val catalog: ScreenCatalog,
) {
    suspend fun execute(fields: Map<String, String>): ActionResponse {
        val screen = catalog.signup()
        val errors = FieldRules.errors(screen, fields)
        if (errors.isNotEmpty()) {
            return ActionResponse(fieldErrors = errors)
        }
        return try {
            val cardholder = register.execute(
                RegisterCardholderCommand(
                    firstName = fields["firstName"].orEmpty(),
                    lastName = fields["lastName"].orEmpty(),
                    email = fields["email"].orEmpty(),
                    password = fields["password"].orEmpty(),
                ),
            )
            ActionResponse(
                effects = listOf(
                    ClientEffect(type = "snackbar", message = "Conta criada."),
                    ClientEffect(type = "showScreen", screen = catalog.registered(cardholder)),
                ),
            )
        } catch (_: CardholderAlreadyExists) {
            ActionResponse(fieldErrors = mapOf("email" to "Já existe uma conta com esse e-mail."))
        } catch (_: IllegalArgumentException) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Revise os dados e tente de novo.")),
            )
        } catch (_: MarqetaCallFailed) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Não foi possível criar a conta.")),
            )
        }
    }
}
