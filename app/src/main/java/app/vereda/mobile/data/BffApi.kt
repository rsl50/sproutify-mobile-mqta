package app.vereda.mobile.data

import app.vereda.sdui.ActionRequest
import app.vereda.sdui.ActionResponse
import app.vereda.sdui.Screen
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface BffApi {
    @GET("screens/{id}")
    suspend fun screen(@Path("id") id: String): Screen

    @POST
    suspend fun action(@Url endpoint: String, @Body body: ActionRequest): ActionResponse
}
