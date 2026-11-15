package app.vereda.bff.smoke

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.config.MarqetaSettingsLoader
import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.infrastructure.marqeta.screenMessage
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    val code = runSandboxSmoke()
    if (code != 0) kotlin.system.exitProcess(code)
}

fun runSandboxSmoke(
    env: Map<String, String> = System.getenv(),
    files: List<File> = MarqetaSettingsLoader.defaultFiles(),
    execute: (MarqetaSettings) -> Unit = ::executeAgainstSandbox,
): Int {
    val settings = try {
        MarqetaSettingsLoader.load(env, files)
    } catch (error: IllegalStateException) {
        println("sandboxSmokeTest ignorado: ${error.message}")
        return 0
    }
    return try {
        execute(settings)
        0
    } catch (error: MarqetaCallFailed) {
        println("sandboxSmokeTest falhou: ${error.screenMessage()}")
        1
    } catch (error: Exception) {
        println("sandboxSmokeTest falhou: ${error.javaClass.simpleName}")
        1
    }
}

private fun executeAgainstSandbox(settings: MarqetaSettings) {
    val api = MarqetaCoreApi.create(settings)
    try {
        runBlocking {
            val email = "vereda-smoke-${System.currentTimeMillis()}@example.com"
            val user = api.createUser("Smoke", "Vereda", email)
            val product = api.listCardProducts().firstOrNull { it.active }
                ?: error("nenhum card product ativo")
            val card = api.createCard(user.token, product.token)
            api.fundAccount(user.token, "100.00", "USD", "sandbox_program_funding")
            val balance = api.availableBalance(user.token)
            val purchase = api.simulateAuthorization(card.token, "5.00", "Vereda Smoke")
            val transactions = api.listTransactions(user.token)
            val state = api.transitionCard(card.token, "SUSPENDED")
            println("sandboxSmokeTest ok")
            println("card last4=${card.lastFour}")
            println("balance=${balance.amount} ${balance.currencyCode}")
            println("purchase state=${purchase.state} amount=${purchase.amount}")
            println("transactions=${transactions.size}")
            println("card state=$state")
        }
    } finally {
        api.close()
    }
}
