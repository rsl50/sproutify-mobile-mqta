package app.vereda.bff.shared

interface DomainEvent

interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

class RecordingEventPublisher : DomainEventPublisher {
    private val events = mutableListOf<DomainEvent>()

    override fun publish(event: DomainEvent) {
        events += event
    }

    fun recorded(): List<DomainEvent> = events.toList()
}
