package app.vereda.bff

import app.vereda.bff.config.MarqetaSettings
import app.vereda.bff.infrastructure.marqeta.MarqetaCoreApi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRouteTest {
    @Test
    fun healthDoesNotRequireALiveSandbox() = testApplication {
        application {
            module(
                MarqetaCoreApi.create(
                    MarqetaSettings(
                        baseUrl = "https://sandbox-api.marqeta.com/v3",
                        applicationToken = "unused-application-token",
                        adminAccessToken = "unused-admin-access-token",
                    ),
                ),
            )
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("vereda-bff"))
    }
}
