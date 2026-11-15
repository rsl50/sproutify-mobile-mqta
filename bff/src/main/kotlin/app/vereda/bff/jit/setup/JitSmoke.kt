package app.vereda.bff.jit.setup

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.config.MarqetaSettingsLoader
import app.vereda.bff.infrastructure.marqeta.MarqetaCallFailed
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.bff.infrastructure.marqeta.screenMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class JitSmokeFailed(message: String) : RuntimeException(message)

fun jitSmokeSkipReason(env: Map<String, String>, files: List<File>): String? {
    val runtime = loadJitRuntime(env, files)
    if (runtime.credentials == null && runtime.passwordProblem != null) {
        return "JIT ignorado: ${runtime.passwordProblem}."
    }
    if (runtime.credentials == null) {
        return "JIT ignorado: defina VEREDA_JIT_BASIC_USER e VEREDA_JIT_BASIC_PASSWORD."
    }
    val url = runtime.publicUrl
    if (url.isNullOrBlank()) {
        return "JIT ignorado: defina VEREDA_PUBLIC_URL com HTTPS (ngrok ou o deploy) e deixe o BFF no ar."
    }
    if (!url.startsWith("https://")) {
        return "JIT ignorado: VEREDA_PUBLIC_URL precisa ser HTTPS."
    }
    return null
}

fun runJitSmoke(
    settings: MarqetaSettings,
    env: Map<String, String> = System.getenv(),
    files: List<File> = MarqetaSettingsLoader.defaultFiles(),
) {
    val skip = jitSmokeSkipReason(env, files)
    if (skip != null) {
        println(skip)
        return
    }
    val runtime = loadJitRuntime(env, files)
    val bff = env["VEREDA_BFF_URL"]?.trimEnd('/') ?: "http://127.0.0.1:8080"
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    val email = "vereda-jit-${System.currentTimeMillis()}@example.com"
    val signup = try {
        send(
            client,
            "$bff/actions/signup",
            """{"fields":{"firstName":"Jit","lastName":"Vereda","email":"$email","password":"segredo12","funding":"jit"}}""",
        )
    } catch (_: Exception) {
        println("JIT ignorado: o BFF não está em $bff. Suba o serviço e o túnel antes do smoke.")
        return
    }
    val root = runCatching { Json.parseToJsonElement(signup).jsonObject }.getOrNull()
    val effects = root?.get("effects")?.jsonArray.orEmpty()
    val session = effects.firstOrNull { it.jsonObject.string("type") == "setSession" }?.jsonObject?.string("token")
    if (session == null) {
        val message = effects.firstOrNull { it.jsonObject.string("type") == "snackbar" }?.jsonObject?.string("message")
        throw JitSmokeFailed("JIT falhou: ${message ?: "cadastro recusado"}.")
    }
    val screen = effects.firstOrNull { it.jsonObject.string("type") == "showScreen" }?.jsonObject?.get("screen")?.toString().orEmpty()
    val last4 = Regex(""""lastFour"\s*:\s*"(\d{4})"""").find(screen)?.groupValues?.get(1) ?: "????"
    if (!screen.contains("Ledger")) {
        throw JitSmokeFailed("JIT falhou: a home não mostrou o ledger.")
    }
    val traces = send(client, "$bff/debug/traces", method = "GET", session = session)
    if (traces.startsWith("404")) {
        throw JitSmokeFailed("JIT falhou: ligue VEREDA_DEBUG_TRACES para localizar o cartão.")
    }
    val cardToken = cardTokenFromTraces(traces)
        ?: throw JitSmokeFailed("JIT falhou: o trace da emissão não trouxe o cartão.")
    val api = MarqetaCoreApi.create(settings)
    try {
        val purchase = kotlinx.coroutines.runBlocking {
            api.simulateAuthorization(cardToken, "5.00", "Vereda JIT")
        }
        println("jitSmoke ok")
        println("jit card last4=$last4")
        println("jit purchase state=${purchase.state}")
    } catch (error: MarqetaCallFailed) {
        throw JitSmokeFailed("JIT falhou: ${error.screenMessage()}")
    } finally {
        api.close()
    }
}

private fun cardTokenFromTraces(body: String): String? {
    val traces = runCatching { Json.parseToJsonElement(body).jsonObject["traces"]?.jsonArray }.getOrNull() ?: return null
    return traces.firstNotNullOfOrNull { entry ->
        val item = entry.jsonObject
        val target = item.string("target").orEmpty()
        val method = item.string("method").orEmpty()
        if (method != "POST" || !target.endsWith("/cards")) return@firstNotNullOfOrNull null
        val response = item.string("responseBody") ?: return@firstNotNullOfOrNull null
        val parsed = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (parsed.string("last_four") == null) return@firstNotNullOfOrNull null
        parsed.string("token")
    }
}

private fun send(
    client: HttpClient,
    url: String,
    body: String? = null,
    method: String = "POST",
    session: String? = null,
): String {
    val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
    if (session != null) builder.header("X-Session-Token", session)
    if (method == "GET") {
        builder.GET()
    } else {
        builder.header("Content-Type", "application/json")
        builder.POST(HttpRequest.BodyPublishers.ofString(body.orEmpty()))
    }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() == 404) return "404"
    return response.body()
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
