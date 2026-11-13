package app.vereda.bff.infrastructure.marqeta

import app.vereda.bff.config.MarqetaSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Anticorruption edge for the Marqeta Core API.
 * Response records stay in this package; bounded contexts map them into domain types.
 */
class MarqetaCoreApi internal constructor(
    private val http: HttpClient,
    private val settings: MarqetaSettings,
) : AutoCloseable {
    val baseUrl: String = settings.baseUrl.trimEnd('/')

    suspend fun createUser(firstName: String, lastName: String, email: String): RemoteUser {
        val response = http.post(endpoint("users")) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserBody(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    active = true,
                ),
            )
        }
        val payload = response.bodyOrThrow<UserResponse>()
        return RemoteUser(token = payload.token, email = payload.email.ifBlank { email })
    }

    suspend fun listCardProducts(): List<RemoteCardProduct> {
        val response = http.get(endpoint("cardproducts")) {
            url {
                parameters.append("count", "25")
            }
        }
        return response.bodyOrThrow<CardProductPage>().data.map {
            RemoteCardProduct(token = it.token, active = it.active)
        }
    }

    suspend fun createCard(userToken: String, cardProductToken: String): RemoteCard {
        val response = http.post(endpoint("cards")) {
            contentType(ContentType.Application.Json)
            setBody(CreateCardBody(userToken = userToken, cardProductToken = cardProductToken))
        }
        val payload = response.bodyOrThrow<CardResponse>()
        return RemoteCard(token = payload.token, lastFour = payload.lastFour)
    }

    suspend fun fundAccount(
        userToken: String,
        amount: String,
        currencyCode: String,
        fundingSourceToken: String,
    ) {
        val response = http.post(endpoint("gpaorders")) {
            contentType(ContentType.Application.Json)
            setBody(
                GpaOrderBody(
                    userToken = userToken,
                    amount = amount,
                    currencyCode = currencyCode,
                    fundingSourceToken = fundingSourceToken,
                ),
            )
        }
        response.requireSuccess()
    }

    suspend fun availableBalance(userToken: String): RemoteBalance {
        val response = http.get(endpoint("balances/$userToken"))
        val gpa = response.bodyOrThrow<BalanceResponse>().gpa
        return RemoteBalance(amount = gpa.availableBalance, currencyCode = gpa.currencyCode)
    }

    override fun close() {
        http.close()
    }

    private fun endpoint(path: String): String = "$baseUrl/${path.trimStart('/')}"

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            throw MarqetaCallFailed(status.value)
        }
    }

    private suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
        requireSuccess()
        return body()
    }

    companion object {
        fun create(settings: MarqetaSettings): MarqetaCoreApi {
            val http = HttpClient(OkHttp) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                }
                defaultRequestAuth(settings)
            }
            return MarqetaCoreApi(http, settings)
        }
    }
}

private fun io.ktor.client.HttpClientConfig<*>.defaultRequestAuth(settings: MarqetaSettings) {
    install(io.ktor.client.plugins.DefaultRequest) {
        basicAuth(settings.applicationToken, settings.adminAccessToken)
    }
}

class MarqetaCallFailed(val statusCode: Int) : RuntimeException("Marqeta call failed with status $statusCode")

data class RemoteUser(val token: String, val email: String)

data class RemoteCardProduct(val token: String, val active: Boolean)

data class RemoteCard(val token: String, val lastFour: String)

data class RemoteBalance(val amount: Double, val currencyCode: String)

@Serializable
internal data class CreateUserBody(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val active: Boolean,
)

@Serializable
internal data class UserResponse(
    val token: String,
    val email: String = "",
)

@Serializable
internal data class CardProductPage(
    val data: List<CardProductItem> = emptyList(),
)

@Serializable
internal data class CardProductItem(
    val token: String,
    val active: Boolean = false,
)

@Serializable
internal data class CreateCardBody(
    @SerialName("user_token") val userToken: String,
    @SerialName("card_product_token") val cardProductToken: String,
)

@Serializable
internal data class CardResponse(
    val token: String,
    @SerialName("last_four") val lastFour: String,
)

@Serializable
internal data class GpaOrderBody(
    @SerialName("user_token") val userToken: String,
    val amount: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("funding_source_token") val fundingSourceToken: String,
)

@Serializable
internal data class BalanceResponse(
    val gpa: GpaBalance,
)

@Serializable
internal data class GpaBalance(
    @SerialName("available_balance") val availableBalance: Double,
    @SerialName("currency_code") val currencyCode: String = "USD",
)
