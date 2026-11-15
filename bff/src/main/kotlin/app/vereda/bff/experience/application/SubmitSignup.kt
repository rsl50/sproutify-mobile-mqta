package app.vereda.bff.experience.application

import app.vereda.bff.debug.TraceBuffer
import app.vereda.bff.debug.TraceScope
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.domain.AccountFunding
import app.vereda.bff.funding.domain.Currency
import app.vereda.bff.funding.domain.Money
import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.issuing.domain.CardFundingMode
import app.vereda.bff.issuing.domain.CardProductToken
import app.vereda.bff.jit.domain.AccountLedger
import app.vereda.bff.jit.domain.LedgerState
import app.vereda.bff.infrastructure.marqeta.screenMessage
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
import app.vereda.bff.spending.domain.SpendLimitRepository
import app.vereda.bff.transactions.application.IdleCardTransactions
import app.vereda.bff.transactions.application.ListCardTransactions
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.FieldRules
import app.vereda.sdui.Screen
import java.math.BigDecimal
import java.math.RoundingMode

class SessionRequired : RuntimeException()

class ScreenQueries(
    private val catalog: ScreenCatalog,
    private val sessions: SessionRepository,
    private val cardholders: CardholderRepository,
    private val cards: IssuedCardRepository,
    private val funding: AccountFunding,
    private val transactions: ListCardTransactions = ListCardTransactions(IdleCardTransactions),
    private val spendLimits: SpendLimitRepository? = null,
    private val ledgers: AccountLedger? = null,
) {
    suspend fun get(screenId: String, sessionToken: String?): Screen {
        val cardholder = resolve(sessionToken)
        return when (screenId) {
            "bootstrap" -> if (cardholder == null) catalog.signup() else home(cardholder)
            "signup" -> catalog.signup()
            "signin" -> catalog.signIn()
            "home" -> home(cardholder ?: throw SessionRequired())
            "transactions" -> catalog.transactions(
                transactions.execute((cardholder ?: throw SessionRequired()).userToken),
            )
            "simulate-purchase" -> {
                if (cardholder == null) throw SessionRequired()
                catalog.simulatePurchase(limitLine(cardholder))
            }
            "spend-limit" -> {
                if (cardholder == null) throw SessionRequired()
                catalog.spendLimit(limitLine(cardholder))
            }
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
        if (card.fundingMode == CardFundingMode.JIT) {
            val balances = ledgers?.balances(cardholder.userToken.value) ?: LedgerState(
                ledger = BigDecimal.ZERO,
                available = BigDecimal.ZERO,
                holds = emptyList(),
                priorOutcome = null,
            )
            return catalog.home(
                cardholder,
                card,
                displayMoney(balances.available),
                limitLine(cardholder),
                ledgerLine(balances),
            )
        }
        val balance = funding.availableBalance(cardholder.userToken)
        return catalog.home(cardholder, card, balance, limitLine(cardholder))
    }

    private fun limitLine(cardholder: Cardholder): String? {
        val limit = spendLimits?.find(cardholder.id) ?: return null
        return "Limite diário: ${limit.amount.format()} · até ${limit.usageLimit} compras"
    }
}

class SubmitSignup(
    private val register: RegisterCardholder,
    private val issueCard: IssueCard,
    private val fundOpeningBalance: FundOpeningBalance,
    private val sessions: SessionRepository,
    private val catalog: ScreenCatalog,
    private val traces: TraceBuffer? = null,
    private val fundingMode: CardFundingMode = CardFundingMode.GPA,
    private val jitProductToken: String? = null,
    private val ledgers: AccountLedger? = null,
) {
    suspend fun execute(fields: Map<String, String>): ActionResponse {
        val screen = catalog.signup()
        val errors = FieldRules.errors(screen, fields)
        if (errors.isNotEmpty()) {
            return ActionResponse(fieldErrors = errors)
        }
        return try {
            val mode = when (fields["funding"]?.trim()?.lowercase()) {
                null, "" -> fundingMode
                "gpa" -> CardFundingMode.GPA
                "jit" -> CardFundingMode.JIT
                else -> return ActionResponse(
                    effects = listOf(ClientEffect(type = "snackbar", message = "Use gpa ou jit.")),
                )
            }
            val product = if (mode == CardFundingMode.JIT) {
                jitProductToken?.takeIf { it.isNotBlank() }?.let(CardProductToken::of)
                    ?: return ActionResponse(
                        effects = listOf(ClientEffect(type = "snackbar", message = "Produto JIT não configurado.")),
                    )
            } else {
                null
            }
            if (mode == CardFundingMode.JIT && ledgers == null) {
                return ActionResponse(
                    effects = listOf(ClientEffect(type = "snackbar", message = "Ledger JIT indisponível.")),
                )
            }
            val cardholder = register.execute(
                RegisterCardholderCommand(
                    firstName = fields["firstName"].orEmpty(),
                    lastName = fields["lastName"].orEmpty(),
                    email = fields["email"].orEmpty(),
                    password = fields["password"].orEmpty(),
                ),
            )
            val card = issueCard.execute(cardholder, mode, product)
            val balance = if (mode == CardFundingMode.JIT) {
                displayMoney(ledgers!!.open(cardholder.userToken.value, Money.openingBalance.amount).available)
            } else {
                fundOpeningBalance.execute(cardholder.userToken)
            }
            val session = sessions.open(cardholder.id)
            kotlin.coroutines.coroutineContext[TraceScope]?.let { traces?.adopt(it.requestId, session.value) }
            ActionResponse(
                effects = listOf(
                    ClientEffect(type = "setSession", token = session.value),
                    ClientEffect(type = "snackbar", message = "Conta criada."),
                    ClientEffect(
                        type = "showScreen",
                        screen = if (mode == CardFundingMode.JIT) {
                            val balances = ledgers?.balances(cardholder.userToken.value)
                            catalog.home(
                                cardholder,
                                card,
                                balance,
                                ledgerLine = balances?.let(::ledgerLine),
                            )
                        } else {
                            catalog.home(cardholder, card, balance)
                        },
                    ),
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
        } catch (error: MarqetaCallFailed) {
            ActionResponse(
                effects = listOf(ClientEffect(type = "snackbar", message = error.screenMessage())),
            )
        }
    }
}

internal fun displayMoney(amount: BigDecimal): Money {
    val scaled = amount.setScale(2, RoundingMode.HALF_UP)
    return if (scaled.signum() < 0) Money.parse("0.00", Currency.USD) else Money(scaled, Currency.USD)
}

internal fun ledgerLine(state: LedgerState): String =
    "Ledger USD ${state.ledger.setScale(2, RoundingMode.HALF_UP).toPlainString()}" +
        " · disponível USD ${state.available.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
