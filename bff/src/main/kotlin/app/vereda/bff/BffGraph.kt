package app.vereda.bff

import app.vereda.bff.debug.TraceBuffer
import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignIn
import app.vereda.bff.experience.application.SubmitSignOut
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.funding.application.FundOpeningBalance
import app.vereda.bff.funding.infrastructure.MarqetaAccountFunding
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.infrastructure.JdbcIssuedCardRepository
import app.vereda.bff.issuing.infrastructure.MarqetaCardIssuing
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.application.SignIn
import app.vereda.bff.onboarding.application.SignOut
import app.vereda.bff.onboarding.infrastructure.JdbcCardholderRepository
import app.vereda.bff.onboarding.infrastructure.JdbcSessionRepository
import app.vereda.bff.onboarding.infrastructure.MarqetaCardholderEnrollment
import app.vereda.bff.shared.persistence.VeredaDatabase
import app.vereda.bff.transactions.application.ListCardTransactions
import app.vereda.bff.transactions.application.SimulatePurchase
import app.vereda.bff.transactions.infrastructure.MarqetaCardTransactions
import app.vereda.bff.experience.application.SubmitCardState
import app.vereda.bff.experience.application.SubmitSpendLimit
import app.vereda.bff.spending.application.SetSpendLimit
import app.vereda.bff.spending.infrastructure.JdbcSpendLimitRepository
import app.vereda.bff.spending.infrastructure.MarqetaSpendControl
import app.vereda.bff.experience.application.SubmitSimulatePurchase
import app.vereda.bff.issuing.application.ChangeCardState
import app.vereda.bff.issuing.domain.CardFundingMode
import app.vereda.bff.jit.application.ApplyFundingNotification
import app.vereda.bff.jit.application.DecideFunding
import app.vereda.bff.jit.infrastructure.JdbcFundingStore
import app.vereda.bff.jit.infrastructure.JdbcJitSessions
import app.vereda.bff.jit.infrastructure.JitCredentials
import app.vereda.bff.jit.infrastructure.JitGateway
import app.vereda.bff.issuing.infrastructure.MarqetaCardState
import app.vereda.bff.shared.DomainEvent
import app.vereda.bff.shared.DomainEventPublisher
import org.slf4j.LoggerFactory

class BffGraph(
    val screens: ScreenQueries,
    val signup: SubmitSignup,
    val signIn: SubmitSignIn,
    val signOut: SubmitSignOut,
    val simulatePurchase: SubmitSimulatePurchase,
    val cardState: SubmitCardState? = null,
    val spendLimit: SubmitSpendLimit? = null,
    val traces: TraceBuffer? = null,
    val jit: JitGateway? = null,
) {
    companion object {
        fun create(
            api: MarqetaCoreApi,
            events: DomainEventPublisher = LoggingEventPublisher(),
            database: VeredaDatabase = VeredaDatabase.open(),
            traces: TraceBuffer? = null,
            jitCredentials: JitCredentials? = null,
            cardFunding: CardFundingMode = CardFundingMode.GPA,
            jitProductToken: String = "vereda_jit_card",
        ): BffGraph {
            val cardholders = JdbcCardholderRepository(database)
            val sessions = JdbcSessionRepository(database)
            val cards = JdbcIssuedCardRepository(database)
            val spendLimits = JdbcSpendLimitRepository(database)
            val ledgers = JdbcFundingStore(database)
            val funding = MarqetaAccountFunding(api)
            val cardTransactions = MarqetaCardTransactions(api)
            val catalog = ScreenCatalog(defaultFunding = cardFunding.name.lowercase())
            val register = RegisterCardholder(
                cardholders = cardholders,
                enrollment = MarqetaCardholderEnrollment(api),
                events = events,
            )
            val issueCard = IssueCard(
                issuing = MarqetaCardIssuing(api, reservedJitProduct = jitProductToken),
                cards = cards,
                events = events,
            )
            return BffGraph(
                screens = ScreenQueries(
                    catalog,
                    sessions,
                    cardholders,
                    cards,
                    funding,
                    ListCardTransactions(cardTransactions),
                    spendLimits,
                    ledgers,
                ),
                signup = SubmitSignup(
                    register = register,
                    issueCard = issueCard,
                    fundOpeningBalance = FundOpeningBalance(funding, events),
                    sessions = sessions,
                    catalog = catalog,
                    traces = traces,
                    fundingMode = cardFunding,
                    jitProductToken = jitProductToken,
                    ledgers = ledgers,
                ),
                signIn = SubmitSignIn(SignIn(cardholders, sessions), catalog),
                signOut = SubmitSignOut(SignOut(sessions)),
                simulatePurchase = SubmitSimulatePurchase(
                    sessions = sessions,
                    cardholders = cardholders,
                    cards = cards,
                    simulatePurchase = SimulatePurchase(cardTransactions, events),
                    catalog = catalog,
                    spendLimits = spendLimits,
                ),
                cardState = SubmitCardState(
                    sessions = sessions,
                    cardholders = cardholders,
                    cards = cards,
                    changeCardState = ChangeCardState(cards, MarqetaCardState(api), events),
                ),
                traces = traces,
                jit = jitCredentials?.let { credentials ->
                    JitGateway(
                        credentials = credentials,
                        decide = DecideFunding(ledgers, cardholders, cards, spendLimits, events),
                        notify = ApplyFundingNotification(ledgers, events),
                        traces = traces,
                        sessionTokens = JdbcJitSessions(database)::tokensFor,
                    )
                },
                spendLimit = SubmitSpendLimit(
                    sessions = sessions,
                    cardholders = cardholders,
                    setSpendLimit = SetSpendLimit(spendLimits, MarqetaSpendControl(api), events),
                    catalog = catalog,
                ),
            )
        }
    }
}

private class LoggingEventPublisher : DomainEventPublisher {
    private val logger = LoggerFactory.getLogger("vereda.domain")

    override fun publish(event: DomainEvent) {
        logger.info("domain event {}", event::class.simpleName)
    }
}
