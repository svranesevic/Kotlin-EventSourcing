import arrow.core.Either
import java.math.BigDecimal
import java.util.*

suspend fun main() {

    val store = EventStore(InMemoryEventStore::appendToStream, InMemoryEventStore::readFromStream)

    val handle = CommandHandling(store)

    fun query(accountId: UUID): Either<Error, Pair<Account, Int>> =
        store
            .readFromStream(accountId.toString())
            .map { events -> Domain.replay(Uninitialized, events) }

    val accountId = UUID.randomUUID()

    val commands =
        listOf(
            MakeDeposit(BigDecimal.valueOf(42)),
            CreateOnlineAccount(accountId),
            MakeDeposit(BigDecimal.valueOf(1000)),
            Withdraw(BigDecimal.valueOf(500)),
            Withdraw(BigDecimal.valueOf(501))
        )

    commands.forEach { cmd ->
        val result = handle(accountId, cmd)
        println("$cmd => ${result.fold({ err -> "[ERROR] $err" }, { "[SUCCESS]" })}")
        println("Current State: ${query(accountId).fold({"invalid"}, {acc -> "$acc"}) }\n")
    }
}