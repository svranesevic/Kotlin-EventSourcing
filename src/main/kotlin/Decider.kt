import arrow.core.*

interface Decider<State, Command, Event, Error> {

    val initial: State

    fun apply(state: State, event: Event): State

    fun decide(command: Command, state: State): ValidatedNel<Error, NonEmptyList<Event>>

    fun replay(state: State, events: List<Event>): Pair<State, Long> =
        events.fold(initial to -1) { (state, version), event ->
            apply(state, event) to version + 1
        }
}

object Update {

    fun <Event, Error> accept(
        event: Event,
        vararg events: Event
    ): ValidatedNel<Error, NonEmptyList<Event>> =
        Valid(NonEmptyList(event, events.toList()))

    fun <Event, Error> reject(
        error: Error,
        vararg errors: Error
    ): ValidatedNel<Error, NonEmptyList<Event>> =
        Invalid(NonEmptyList(error, errors.toList()))

    fun <Event, Error> fromValidated(
        validated: Validated<NonEmptyList<Error>, NonEmptyList<Event>>
    ): ValidatedNel<Error, NonEmptyList<Event>> =
        validated.fold({ errors -> Invalid(errors) }, { events -> Valid(events) })
}
