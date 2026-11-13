package app.vereda.bff

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
import app.vereda.bff.experience.application.SubmitSimulatePurchase
import app.vereda.bff.shared.DomainEvent
import app.vereda.bff.shared.DomainEventPublisher
import org.slf4j.LoggerFactory

class BffGraph(
    val screens: ScreenQueries,
    val signup: SubmitSignup,
    val signIn: SubmitSignIn,
    val signOut: SubmitSignOut,
    val simulatePurchase: SubmitSimulatePurchase,
) {
    companion object {
        fun create(
            api: MarqetaCoreApi,
            events: DomainEventPublisher = LoggingEventPublisher(),
            database: VeredaDatabase = VeredaDatabase.open(),
        ): BffGraph {
            val cardholders = JdbcCardholderRepository(database)
            val sessions = JdbcSessionRepository(database)
            val cards = JdbcIssuedCardRepository(database)
            val funding = MarqetaAccountFunding(api)
            val cardTransactions = MarqetaCardTransactions(api)
            val catalog = ScreenCatalog()
            val register = RegisterCardholder(
                cardholders = cardholders,
                enrollment = MarqetaCardholderEnrollment(api),
                events = events,
            )
            val issueCard = IssueCard(
                issuing = MarqetaCardIssuing(api),
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
                ),
                signup = SubmitSignup(
                    register = register,
                    issueCard = issueCard,
                    fundOpeningBalance = FundOpeningBalance(funding, events),
                    sessions = sessions,
                    catalog = catalog,
                ),
                signIn = SubmitSignIn(SignIn(cardholders, sessions), catalog),
                signOut = SubmitSignOut(SignOut(sessions)),
                simulatePurchase = SubmitSimulatePurchase(
                    sessions = sessions,
                    cardholders = cardholders,
                    cards = cards,
                    simulatePurchase = SimulatePurchase(cardTransactions, events),
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
