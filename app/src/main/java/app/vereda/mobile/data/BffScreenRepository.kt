package app.vereda.mobile.data

import app.vereda.mobile.domain.LoadResult
import app.vereda.mobile.domain.ScreenRepository
import app.vereda.sdui.ActionRequest
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.ErrorBody
import app.vereda.sdui.SCHEMA_VERSION
import app.vereda.sdui.ScreenJson
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

class BffScreenRepository(
    private val api: BffApi,
    private val sessions: SessionTokenStore,
    private val transportMessage: String,
) : ScreenRepository {
    override suspend fun prepare() {
        sessions.hydrate()
    }

    override suspend fun saveSession(token: String?) {
        sessions.update(token)
    }

    override suspend fun load(screenId: String): LoadResult = try {
        val screen = api.screen(screenId)
        if (screen.schemaVersion != SCHEMA_VERSION) {
            throw UnsupportedSchema(screen.schemaVersion)
        }
        LoadResult.Ready(screen)
    } catch (error: HttpException) {
        val raw = errorBody(error)
        if (error.code() == 401) {
            decodeAction(raw)?.let { return LoadResult.Redirect(it) }
        }
        throw messageFrom(raw)
    } catch (_: IOException) {
        throw TransportFailure(transportMessage)
    }

    override suspend fun submit(endpoint: String, fields: Map<String, String>): ActionResponse = try {
        api.action(endpoint, ActionRequest(fields))
    } catch (error: HttpException) {
        val raw = errorBody(error)
        decodeAction(raw)?.let { return it }
        throw messageFrom(raw)
    } catch (_: IOException) {
        throw TransportFailure(transportMessage)
    }

    private fun errorBody(error: HttpException): String =
        error.response()?.errorBody()?.string().orEmpty()

    private fun decodeAction(raw: String): ActionResponse? {
        if (raw.isBlank()) return null
        val element = runCatching { ScreenJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        if ("fieldErrors" !in element && "effects" !in element) return null
        return runCatching {
            ScreenJson.decodeFromJsonElement(ActionResponse.serializer(), element)
        }.getOrNull()
    }

    private fun messageFrom(raw: String): TransportFailure {
        val message = runCatching { ScreenJson.decodeFromString(ErrorBody.serializer(), raw) }
            .getOrNull()
            ?.message
            ?.takeIf { it.isNotBlank() }
            ?: transportMessage
        return TransportFailure(message)
    }
}

class UnsupportedSchema(version: Int) : RuntimeException("schemaVersion=$version")

class TransportFailure(message: String) : RuntimeException(message)
