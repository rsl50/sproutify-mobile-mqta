package app.vereda.bff.infrastructure.marqeta

import app.vereda.bff.config.MarqetaSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Anticorruption edge for the Marqeta Core API.
 * Response records stay in this package; bounded contexts map them into domain types.
 */
class MarqetaCoreApi internal constructor(
    private val http: HttpClient,
    private val settings: MarqetaSettings,
) : AutoCloseable {
    val baseUrl: String = settings.baseUrl.trimEnd('/')

    suspend fun createUser(firstName: String, lastName: String, email: String): RemoteUser = io {
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
        RemoteUser(token = payload.token, email = payload.email.ifBlank { email })
    }

    suspend fun listCardProducts(): List<RemoteCardProduct> = io {
        val response = http.get(endpoint("cardproducts")) {
            url {
                parameters.append("count", "25")
            }
        }
        response.bodyOrThrow<CardProductPage>().data.map {
            RemoteCardProduct(token = it.token, active = it.active)
        }
    }

    suspend fun createCard(userToken: String, cardProductToken: String): RemoteCard = io {
        val response = http.post(endpoint("cards")) {
            contentType(ContentType.Application.Json)
            setBody(CreateCardBody(userToken = userToken, cardProductToken = cardProductToken))
        }
        val payload = response.bodyOrThrow<CardResponse>()
        RemoteCard(token = payload.token, lastFour = payload.lastFour)
    }

    suspend fun fundAccount(
        userToken: String,
        amount: String,
        currencyCode: String,
        fundingSourceToken: String,
    ) = io {
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

    suspend fun listTransactions(userToken: String): List<RemoteTransaction> = io {
        val response = http.get(endpoint("transactions")) {
            url {
                parameters.append("user_token", userToken)
                parameters.append("count", "25")
                parameters.append("sort_by", "-created_time")
            }
        }
        response.bodyOrThrow<TransactionPage>().data.map { it.toRemote() }
    }

    suspend fun simulateAuthorization(cardToken: String, amount: String, merchant: String): RemoteTransaction = io {
        val response = http.post(endpoint("simulations/cardtransactions/authorization")) {
            contentType(ContentType.Application.Json)
            setBody(
                SimulateAuthorizationBody(
                    cardToken = cardToken,
                    amount = amount,
                    cardAcceptor = CardAcceptorBody(mid = "11111", name = merchant),
                ),
            )
        }
        response.bodyOrThrow<SimulationResponse>().transaction.toRemote()
    }

    suspend fun availableBalance(userToken: String): RemoteBalance = io {
        val response = http.get(endpoint("balances/$userToken"))
        val gpa = response.bodyOrThrow<BalanceResponse>().gpa
        RemoteBalance(amount = gpa.availableBalance, currencyCode = gpa.currencyCode)
    }

    override fun close() {
        http.close()
    }

    private fun endpoint(path: String): String = "$baseUrl/${path.trimStart('/')}"

    private suspend inline fun <T> io(crossinline block: suspend () -> T): T {
        try {
            return block()
        } catch (_: HttpRequestTimeoutException) {
            throw MarqetaCallFailed(408)
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            throw MarqetaCallFailed(408)
        } catch (_: ConnectException) {
            throw MarqetaCallFailed(408)
        }
    }

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
        fun create(
            settings: MarqetaSettings,
            requestTimeoutMs: Long = 15_000,
            connectTimeoutMs: Long = 5_000,
        ): MarqetaCoreApi {
            val http = HttpClient(OkHttp) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = requestTimeoutMs
                    connectTimeoutMillis = connectTimeoutMs
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

fun MarqetaCallFailed.screenMessage(): String = when (statusCode) {
    400, 402, 409, 422 -> "A Marqeta recusou o pedido."
    401, 403 -> "As credenciais do sandbox no BFF foram recusadas."
    404 -> "A Marqeta não encontrou o recurso."
    408 -> "A Marqeta não respondeu a tempo."
    in 500..599 -> "A Marqeta está indisponível."
    else -> "Não foi possível falar com a Marqeta."
}

data class RemoteUser(val token: String, val email: String)

data class RemoteCardProduct(val token: String, val active: Boolean)

data class RemoteCard(val token: String, val lastFour: String)

data class RemoteBalance(val amount: Double, val currencyCode: String)

data class RemoteTransaction(
    val token: String,
    val amount: Double,
    val currencyCode: String,
    val state: String,
    val merchant: String,
)

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

@Serializable
internal data class TransactionPage(
    val data: List<TransactionItem> = emptyList(),
)

@Serializable
internal data class SimulationResponse(
    val transaction: TransactionItem,
)

@Serializable
internal data class TransactionItem(
    val token: String = "",
    val amount: Double = 0.0,
    val state: String = "",
    @SerialName("currency_code") val currencyCode: String = "USD",
    @SerialName("card_acceptor") val cardAcceptor: CardAcceptorResponse? = null,
)

@Serializable
internal data class CardAcceptorResponse(
    val name: String = "",
)

@Serializable
internal data class SimulateAuthorizationBody(
    @SerialName("card_token") val cardToken: String,
    val amount: String,
    @SerialName("card_acceptor") val cardAcceptor: CardAcceptorBody,
)

@Serializable
internal data class CardAcceptorBody(
    val mid: String,
    val name: String,
)

private fun TransactionItem.toRemote(): RemoteTransaction = RemoteTransaction(
    token = token,
    amount = amount,
    currencyCode = currencyCode,
    state = state,
    merchant = cardAcceptor?.name?.ifBlank { "Estabelecimento" } ?: "Estabelecimento",
)
