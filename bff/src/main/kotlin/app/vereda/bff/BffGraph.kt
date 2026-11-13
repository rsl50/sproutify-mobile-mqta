package app.vereda.bff

import app.vereda.bff.experience.application.ScreenCatalog
import app.vereda.bff.experience.application.ScreenQueries
import app.vereda.bff.experience.application.SubmitSignup
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.issuing.application.IssueCard
import app.vereda.bff.issuing.infrastructure.InMemoryIssuedCardRepository
import app.vereda.bff.issuing.infrastructure.MarqetaCardIssuing
import app.vereda.bff.onboarding.application.RegisterCardholder
import app.vereda.bff.onboarding.infrastructure.InMemoryCardholderRepository
import app.vereda.bff.onboarding.infrastructure.MarqetaCardholderEnrollment
import app.vereda.bff.shared.DomainEvent
import app.vereda.bff.shared.DomainEventPublisher
import org.slf4j.LoggerFactory

class BffGraph(
    val screens: ScreenQueries,
    val signup: SubmitSignup,
) {
    companion object {
        fun create(
            api: MarqetaCoreApi,
            events: DomainEventPublisher = LoggingEventPublisher(),
        ): BffGraph {
            val cardholders = InMemoryCardholderRepository()
            val register = RegisterCardholder(
                cardholders = cardholders,
                enrollment = MarqetaCardholderEnrollment(api),
                events = events,
            )
            val catalog = ScreenCatalog()
            val issueCard = IssueCard(
                issuing = MarqetaCardIssuing(api),
                cards = InMemoryIssuedCardRepository(),
                events = events,
            )
            return BffGraph(
                screens = ScreenQueries(catalog),
                signup = SubmitSignup(register, issueCard, catalog),
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
