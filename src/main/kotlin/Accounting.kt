import Update.accept
import Update.reject
import arrow.core.NonEmptyList
import arrow.core.ValidatedNel
import java.math.BigDecimal
import java.util.*

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

object AccountingDomain {

    val decider =
        object : Decider<Account, Command, Event, String> {

            override val initial: Account
                get() = Uninitialized

            override fun apply(state: Account, event: Event): Account =
                when {
                    state is Uninitialized && event is OnlineAccountCreated ->
                        OnlineAccount(event.accountId, BigDecimal.valueOf(0))

                    state is OnlineAccount && event is DepositMade ->
                        OnlineAccount(state.accountId, state.balance + event.depositAmount)

                    state is OnlineAccount && event is MoneyWithdrawn ->
                        OnlineAccount(state.accountId, state.balance - event.withdrawalAmount)

                    else -> state
                }

            override fun decide(command: Command, state: Account): ValidatedNel<String, NonEmptyList<Event>> =
                when {
                    state is Uninitialized && command is CreateOnlineAccount -> handleCreation(command)
                    state is OnlineAccount -> decideOnlineAccount(command, state)
                    else -> reject("invalid operation $command on current state $state")
                }
        }

    private fun handleCreation(cmd: CreateOnlineAccount): ValidatedNel<String, NonEmptyList<Event>> =
        accept(OnlineAccountCreated(cmd.accountId))

    private fun decideOnlineAccount(
        cmd: Command,
        state: OnlineAccount
    ): ValidatedNel<String, NonEmptyList<Event>> =
        when (cmd) {
            is MakeDeposit ->
                if (cmd.depositAmount <= BigDecimal.ZERO) reject("deposit amount must be positive")
                else accept(DepositMade(state.accountId, cmd.depositAmount))

            is Withdraw ->
                if (cmd.withdrawalAmount <= BigDecimal.ZERO) reject("withdrawal amount must be positive")
                else if (state.balance - cmd.withdrawalAmount < BigDecimal.ZERO) reject("overdraft not allowed")
                else accept(MoneyWithdrawn(state.accountId, cmd.withdrawalAmount))

            else -> reject("invalid operation $cmd on current state $state")
        }
}
