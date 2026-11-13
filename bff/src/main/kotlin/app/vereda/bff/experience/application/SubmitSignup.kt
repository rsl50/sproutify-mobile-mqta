package app.vereda.bff.experience.application

import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.domain.IssuedCardRepository
import app.vereda.bff.issuing.domain.NoActiveCardProduct
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.application.RegisterCardholderCommand
import app.vereda.bff.onboarding.domain.Cardholder
import app.vereda.bff.onboarding.domain.CardholderAlreadyExists
import app.vereda.bff.onboarding.domain.CardholderRepository
import app.vereda.bff.onboarding.domain.SessionRepository
import app.vereda.bff.onboarding.domain.SessionToken
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules
import app.vereda.sdui.Screen

class SessionRequired : RuntimeException()

class ScreenQueries(
    private val catalog: ScreenCatalog,
    private val sessions: SessionRepository,
    private val cardholders: CardholderRepository,
    private val cards: IssuedCardRepository,
    private val funding: AccountFunding,
) {
    suspend fun get(screenId: String, sessionToken: String?): Screen {
        val cardholder = resolve(sessionToken)
        return when (screenId) {
            "bootstrap" -> if (cardholder == null) catalog.signup() else home(cardholder)
            "signup" -> catalog.signup()
            "signin" -> catalog.signIn()
            "home" -> home(cardholder ?: throw SessionRequired())
            else -> throw UnknownScreen(screenId)
        }
    }

    private fun resolve(sessionToken: String?): Cardholder? {
        if (sessionToken.isNullOrBlank()) return null
        val id = sessions.cardholderId(SessionToken.of(sessionToken)) ?: return null
        return cardholders.findById(id)
    }

    private suspend fun home(cardholder: Cardholder): Screen {
        val card = cards.findByCardholder(cardholder.id) ?: throw SessionRequired()
        val balance = funding.availableBalance(cardholder.userToken)
        return catalog.home(cardholder, card, balance)
    }
}

class SubmitSignup(
    private val register: RegisterCardholder,
    private val issueCard: IssueCard,
    private val fundOpeningBalance: FundOpeningBalance,
    private val sessions: SessionRepository,
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
            val card = issueCard.execute(cardholder)
            val balance = fundOpeningBalance.execute(cardholder.userToken)
            val session = sessions.open(cardholder.id)
            ActionResponse(
                effects = listOf(
                    ClientEffect(type = "setSession", token = session.value),
                    ClientEffect(type = "snackbar", message = "Conta criada."),
                    ClientEffect(type = "showScreen", screen = catalog.home(cardholder, card, balance)),
                ),
            )
        } catch (_: CardholderAlreadyExists) {
            ActionResponse(fieldErrors = mapOf("email" to "Já existe uma conta com esse e-mail."))
        } catch (_: IllegalArgumentException) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Revise os dados e tente de novo.")),
            )
        } catch (_: NoActiveCardProduct) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Não foi possível emitir o cartão.")),
            )
        } catch (_: MarqetaCallFailed) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = "Não foi possível criar a conta.")),
            )
        }
    }
}
