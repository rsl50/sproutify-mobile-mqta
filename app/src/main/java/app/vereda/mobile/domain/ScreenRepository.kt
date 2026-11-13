package app.vereda.mobile.domain

import app.vereda.sdui.ActionResponse
import app.vereda.sdui.Screen

interface ScreenRepository {
    suspend fun prepare()

    suspend fun load(screenId: String): LoadResult

    suspend fun submit(endpoint: String, fields: Map<String, String>): ActionResponse

    suspend fun saveSession(token: String?)
}

sealed interface LoadResult {
    data class Ready(val screen: Screen) : LoadResult

    data class Redirect(val response: ActionResponse) : LoadResult
}
