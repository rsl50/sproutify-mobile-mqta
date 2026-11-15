package app.vereda.bff.experience.application

import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.screenMessage
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.transactions.application.SimulatePurchase
import app.vereda.bff.transactions.domain.MerchantName
import app.vereda.bff.transactions.domain.purchaseAmount
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules

class SubmitSimulatePurchase(
    private val sessions: SessionRepository,
    private val cardholders: CardholderRepository,
    private val cards: IssuedCardRepository,
    private val simulatePurchase: SimulatePurchase,
    private val catalog: ScreenCatalog,
    private val spendLimits: SpendLimitRepository? = null,
) {
    suspend fun execute(sessionToken: String?, fields: Map<String, String>): ActionResponse {
        val token = sessionToken?.takeIf { it.isNotBlank() }?.let(SessionToken::of)
        val cardholderId = token?.let(sessions::cardholderId) ?: throw SessionRequired()
        val cardholder = cardholders.findById(cardholderId) ?: throw SessionRequired()
        val card = cards.findByCardholder(cardholder.id)
            ?: return ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Não há cartão nesta conta.")),
            )
        val errors = FieldRules.errors(catalog.simulatePurchase(), fields)
        if (errors.isNotEmpty()) return ActionResponse(fieldErrors = errors)
        return try {
            val requested = purchaseAmount(fields["amount"].orEmpty())
            val transaction = simulatePurchase.execute(
                cardToken = card.token,
                amount = requested,
                merchant = MerchantName.parse(fields["merchant"].orEmpty()),
            )
            val limit = spendLimits?.find(cardholder.id)
            val declined = transaction.state.equals("DECLINED", ignoreCase = true)
            val message = when {
                declined && limit != null && !limit.covers(requested) ->
                    "Compra recusada: acima do limite de ${limit.amount.format()}."
                declined -> "A Marqeta recusou a compra."
                else -> "Compra simulada."
            }
            ActionResponse(
                effects = listOf(
                    ClientEffect(type = "snackbar", message = message),
                    ClientEffect(type = "navigate", screenId = "home"),
                ),
            )
        } catch (_: IllegalArgumentException) {
            ActionResponse(fieldErrors = mapOf("amount" to "Informe um valor USD maior que zero."))
        } catch (error: MarqetaCallFailed) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = error.screenMessage())),
            )
        }
    }
}
