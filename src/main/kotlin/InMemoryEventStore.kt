import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right

data class InMemoryEventStore<Event>(private var streams: Map<String, List<Pair<Event, Long>>> = mapOf()) :
    EventStore<Event> {

    private fun getVersion(stream: List<Pair<Event, Long>>): Long = stream.maxOf { it.second }

    override fun appendToStream(
        id: String,
        expectedVersion: Long,
        events: NonEmptyList<Event>
    ): Either<EventStoreError, Unit> {
        val stream = streams[id]
        val streamOrError =
            if (stream == null) {
                if (expectedVersion == -1L) {
                    val newStream = listOf<Pair<Event, Long>>()
                    streams = streams + (id to newStream)
                    newStream.right()
                } else EventStoreError.AggregateNotFound(id).left()
            } else {
                val streamVersion = getVersion(stream)
                if (streamVersion == expectedVersion) stream.right()
                else EventStoreError.ConcurrencyError(expectedVersion, streamVersion).left()
            }

        val newEvents =
            events
                .withIndex()
                .map { eventWithVersion -> eventWithVersion.value to (expectedVersion + eventWithVersion.index + 1) }

        return streamOrError.map { existingEvents ->
            val allEvents = existingEvents + newEvents
            streams = streams + (id to allEvents)
        }
    }

    override fun readFromStream(id: String): List<Event> =
        (streams[id] ?: listOf()).sortedBy { it.second }.map { it.first }
}
