import arrow.core.Either
import arrow.core.NonEmptyList

interface EventStore<Event> {
    fun appendToStream(id: String, expectedVersion: Long, events: NonEmptyList<Event>): Either<EventStoreError, Unit>
    fun readFromStream(id: String): List<Event>
}

sealed interface EventStoreError {
    data class AggregateNotFound(val id: String) : EventStoreError
    data class ConcurrencyError(val expectedVersion: Long, val currentVersion: Long) : EventStoreError
}
