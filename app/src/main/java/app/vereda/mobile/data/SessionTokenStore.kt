package app.vereda.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import okhttp3.Response

private val Context.veredaSessionDataStore by preferencesDataStore(name = "vereda_session")

interface SessionTokenStore {
    suspend fun hydrate()

    fun peek(): String?

    suspend fun update(token: String?)
}

class DataStoreSessionTokenStore(
    context: Context,
) : SessionTokenStore {
    private val dataStore = context.veredaSessionDataStore

    @Volatile
    private var cached: String? = null

    override suspend fun hydrate() {
        cached = dataStore.data.first()[KEY]
    }

    override fun peek(): String? = cached

    override suspend fun update(token: String?) {
        cached = token
        dataStore.edit { prefs ->
            if (token == null) prefs.remove(KEY) else prefs[KEY] = token
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("session_token")
    }
}

class SessionHeaderInterceptor(
    private val store: SessionTokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.peek()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("X-Session-Token", token).build()
        }
        return chain.proceed(request)
    }
}
