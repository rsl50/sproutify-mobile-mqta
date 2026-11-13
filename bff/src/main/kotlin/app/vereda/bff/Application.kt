package app.vereda.bff

import app.vereda.bff.config.MarqetaSettingsLoader
import app.vereda.bff.experience.application.SessionRequired
import app.vereda.bff.experience.application.UnknownScreen
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import app.vereda.sdui.ActionRequest
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ClientEffect
import app.vereda.sdui.ErrorBody
import app.vereda.sdui.ScreenJson
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

fun main() {
    val settings = MarqetaSettingsLoader.load()
    val api = MarqetaCoreApi.create(settings)
    LoggerFactory.getLogger("vereda").info("Marqeta base URL configured: {}", settings.baseUrl)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(BffGraph.create(api))
    }.start(wait = true)
}

fun Application.module(graph: BffGraph) {
    install(ContentNegotiation) {
        json(ScreenJson)
    }
    install(StatusPages) {
        exception<SessionRequired> { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ActionResponse(
                    effects = listOf(
                        ClientEffect(type = "clearSession"),
                        ClientEffect(type = "navigate", screenId = "signup"),
                    ),
                ),
            )
        }
        exception<UnknownScreen> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorBody("Tela desconhecida."))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled request failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("Falha interna."))
        }
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", service = serviceName()))
        }
        get("/screens/{id}") {
            call.respond(
                graph.screens.get(
                    screenId = call.parameters["id"].orEmpty(),
                    sessionToken = call.request.header("X-Session-Token"),
                ),
            )
        }
        post("/actions/signup") {
            val body = call.receive<ActionRequest>()
            call.respond(graph.signup.execute(body.fields))
        }
        post("/actions/signin") {
            val body = call.receive<ActionRequest>()
            call.respond(graph.signIn.execute(body.fields))
        }
        post("/actions/signout") {
            call.respond(graph.signOut.execute(call.request.header("X-Session-Token")))
        }
        post("/actions/simulate-purchase") {
            val body = call.receive<ActionRequest>()
            call.respond(
                graph.simulatePurchase.execute(
                    sessionToken = call.request.header("X-Session-Token"),
                    fields = body.fields,
                ),
            )
        }
    }
}

@Serializable
data class HealthResponse(val status: String, val service: String)
