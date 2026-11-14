package app.vereda.bff.experience.application

import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.screenMessage
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken
import app.vereda.bff.spending.application.SetSpendLimit
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules

class SubmitSpendLimit(
    private val sessions: SessionRepository,
    private val cardholders: CardholderRepository,
    private val setSpendLimit: SetSpendLimit,
    private val catalog: ScreenCatalog,
) {
    suspend fun execute(sessionToken: String?, fields: Map<String, String>): ActionResponse {
        val token = sessionToken?.takeIf { it.isNotBlank() }?.let(SessionToken::of)
        val cardholderId = token?.let(sessions::cardholderId) ?: throw SessionRequired()
        val cardholder = cardholders.findById(cardholderId) ?: throw SessionRequired()
        val errors = FieldRules.errors(catalog.spendLimit(null), fields)
        if (errors.isNotEmpty()) return ActionResponse(fieldErrors = errors)
        return try {
            val limit = setSpendLimit.execute(cardholder, fields["amount"].orEmpty(), fields["usage"].orEmpty())
            ActionResponse(
                effects = listOf(
                    ClientEffect(
                        type = "snackbar",
                        message = "Limite diário de ${limit.amount.format()} para ${limit.usageLimit} compras.",
                    ),
                    ClientEffect(type = "navigate", screenId = "home"),
                ),
            )
        } catch (_: IllegalArgumentException) {
            ActionResponse(fieldErrors = mapOf("usage" to "Use de 1 a 100 compras e um valor USD válido."))
        } catch (error: MarqetaCallFailed) {
            ActionResponse(effects = listOf(ClientEffect(type = "snackbar", message = error.screenMessage())))
        }
    }
}