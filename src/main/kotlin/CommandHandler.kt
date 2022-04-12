import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.computations.either
import java.util.*

data class CommandHandler<State, Command, Event, Error>(
    private val store: EventStore<Event>,
    private val decider: Decider<State, Command, Event, Error>
) {

    suspend operator fun invoke(id: UUID, command: Command): Either<NonEmptyList<Error>, Unit> =
        either {
            val events = store.readFromStream(id.toString())
            val (state, version) = decider.replay(decider.initial, events)
            val newEvents = decider.decide(command, state).bind()
            store.appendToStream(id.toString(), version, newEvents)
        }
}
