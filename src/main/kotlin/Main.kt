import java.math.BigDecimal
import java.util.*

suspend fun main() {

    val store = InMemoryEventStore<Event>()

    val accountingHandler = CommandHandler(store, AccountingDomain.decider)

    val accountId = UUID.randomUUID()

    fun query(accountId: UUID) =
        AccountingDomain.decider.replay(Uninitialized, store.readFromStream(accountId.toString()))

    val commands =
        listOf(
            MakeDeposit(BigDecimal.valueOf(42)),
            CreateOnlineAccount(accountId),
            MakeDeposit(BigDecimal.valueOf(1000)),
            Withdraw(BigDecimal.valueOf(500)),
            Withdraw(BigDecimal.valueOf(501))
        )

    commands.forEach { cmd ->
        val result = accountingHandler(accountId, cmd)
        println("$cmd => ${result.fold({ err -> "[ERROR] $err" }, { "[SUCCESS]" })}")
        println("Current State: ${query(accountId)}\n")
    }
}
