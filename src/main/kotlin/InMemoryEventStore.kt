import arrow.core.Either
import arrow.core.left
import arrow.core.right

object InMemoryEventStore {

    private var streams: Map<String, List<Pair<Event, Int>>> = mapOf()

    private fun getVersion(stream: List<Pair<Event, Int>>): Int = stream.maxOf { it.second }

    fun appendToStream(streamId: String, expectedVersion: Int, events: List<Event>): Either<Error, Unit> {
        val stream = streams[streamId]
        val streamOrError =
            if (stream == null && expectedVersion == -1) {
                val newStream = listOf<Pair<Event, Int>>()
                streams = streams + (streamId to newStream)
                newStream.right()
            } else if (stream != null && getVersion(stream) == expectedVersion) stream.right()
            else "concurrency conflict".left()

        val newEvents =
            events
                .withIndex()
                .map { eventWithVersion -> eventWithVersion.value to (expectedVersion + eventWithVersion.index + 1) }

        return streamOrError.map { existingEvents ->
            val allEvents = existingEvents + newEvents
            streams = streams + (streamId to allEvents)
        }
    }

    fun readFromStream(streamId: String): Either<Error, List<Event>> =
        (streams[streamId] ?: listOf()).sortedBy { it.second }.map { it.first }.right()
}