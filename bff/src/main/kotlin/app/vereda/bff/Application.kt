package app.vereda.bff

import app.vereda.bff.config.MarqetaSettingsLoader
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    val settings = MarqetaSettingsLoader.load()
    val api = MarqetaCoreApi.create(settings)
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(api)
    }.start(wait = true)
}

fun Application.module(api: MarqetaCoreApi) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", service = serviceName()))
        }
    }
    environment.log.info("Marqeta base URL configured: {}", api.baseUrl)
}

@Serializable
data class HealthResponse(val status: String, val service: String)
