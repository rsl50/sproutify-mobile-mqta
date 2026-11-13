package app.vereda.bff.experience.application

import app.vereda.bff.onboarding.application.SignIn
import app.vereda.bff.onboarding.application.SignOut
import app.vereda.bff.onboarding.domain.InvalidCredentials
import app.vereda.bff.onboarding.domain.SessionToken
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules

class SubmitSignIn(
    private val signIn: SignIn,
    private val catalog: ScreenCatalog,
) {
    fun execute(fields: Map<String, String>): ActionResponse {
        val errors = FieldRules.errors(catalog.signIn(), fields)
        if (errors.isNotEmpty()) return ActionResponse(fieldErrors = errors)
        return try {
            val session = signIn.execute(fields["email"].orEmpty(), fields["password"].orEmpty())
            ActionResponse(
                effects = listOf(
                    ClientEffect(type = "setSession", token = session.value),
                    ClientEffect(type = "navigate", screenId = "home"),
                ),
            )
        } catch (_: InvalidCredentials) {
            ActionResponse(fieldErrors = mapOf("password" to "E-mail ou senha não conferem."))
        } catch (_: IllegalArgumentException) {
            ActionResponse(fieldErrors = mapOf("email" to "E-mail inválido."))
        }
    }
}

class SubmitSignOut(
    private val signOut: SignOut,
) {
    fun execute(sessionToken: String?): ActionResponse {
        if (!sessionToken.isNullOrBlank()) {
            signOut.execute(SessionToken.of(sessionToken))
        }
        return ActionResponse(
            effects = listOf(
                ClientEffect(type = "clearSession"),
                ClientEffect(type = "navigate", screenId = "signup"),
            ),
        )
    }
}
