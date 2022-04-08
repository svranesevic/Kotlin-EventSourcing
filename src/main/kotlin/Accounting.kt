import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import java.math.BigDecimal
import java.util.*

typealias Error = String

sealed interface Account
object Uninitialized : Account {
    override fun toString(): String = "Uninitialized"
}

data class OnlineAccount(val accountId: UUID, val balance: BigDecimal) : Account

sealed interface Command
data class CreateOnlineAccount(val accountId: UUID) : Command
data class MakeDeposit(val depositAmount: BigDecimal) : Command
data class Withdraw(val withdrawalAmount: BigDecimal) : Command

sealed interface Event
data class OnlineAccountCreated(val accountId: UUID) : Event
data class DepositMade(val accountId: UUID, val depositAmount: BigDecimal) : Event
data class MoneyWithdrawn(val accountId: UUID, val withdrawalAmount: BigDecimal) : Event


data class EventStore(
    val appendToStream: (String, Int, List<Event>) -> Either<Error, Unit>,
    val readFromStream: (String) -> Either<Error, List<Event>>
)

object Domain {

    fun apply(account: Account, event: Event): Account =
        when {
            account is Uninitialized && event is OnlineAccountCreated ->
                OnlineAccount(event.accountId, BigDecimal.valueOf(0))

            account is OnlineAccount && event is DepositMade ->
                OnlineAccount(account.accountId, account.balance + event.depositAmount)

            account is OnlineAccount && event is MoneyWithdrawn ->
                OnlineAccount(account.accountId, account.balance - event.withdrawalAmount)

            else -> account
        }

    fun replay(initial: Account, events: List<Event>): Pair<Account, Int> =
        events.fold(initial to -1) { (state, version), event ->
            apply(state, event) to version + 1
        }

    fun decide(cmd: Command, state: Account): Either<Error, List<Event>> =
        when {
            state is Uninitialized && cmd is CreateOnlineAccount -> handleCreation(cmd)
            state is OnlineAccount -> decideOnlineAccount(cmd, state)
            else -> "invalid operation $cmd on current state $state".left()
        }

    private fun handleCreation(cmd: CreateOnlineAccount): Either<Error, List<Event>> =
        listOf(OnlineAccountCreated(cmd.accountId)).right()

    private fun decideOnlineAccount(cmd: Command, state: OnlineAccount): Either<Error, List<Event>> =
        when (cmd) {
            is MakeDeposit ->
                if (cmd.depositAmount <= BigDecimal.ZERO) "deposit amount must be positive".left()
                else listOf(DepositMade(state.accountId, cmd.depositAmount)).right()

            is Withdraw ->
                if (cmd.withdrawalAmount <= BigDecimal.ZERO) "withdrawal amount must be positive".left()
                else if (state.balance - cmd.withdrawalAmount < BigDecimal.ZERO) "overdraft not allowed".left()
                else listOf(MoneyWithdrawn(state.accountId, cmd.withdrawalAmount)).right()

            else -> "invalid operation $cmd on current state $state".left()
        }
}

object CommandHandling {

    operator fun invoke(store: EventStore): suspend (UUID, Command) -> Either<Error, Unit> {
        val handler: suspend (UUID, Command) -> Either<Error, Unit> = { id, cmd ->
            either {
                val events = store.readFromStream(id.toString()).bind()
                val (state, version) = Domain.replay(Uninitialized, events)
                val newEvents = Domain.decide(cmd, state).bind()
                store.appendToStream(id.toString(), version, newEvents)
            }
        }

        return handler
    }
}