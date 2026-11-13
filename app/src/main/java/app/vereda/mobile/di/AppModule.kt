package app.vereda.mobile.di

import android.content.Context
import app.vereda.mobile.BuildConfig
import app.vereda.mobile.R
import app.vereda.mobile.data.BffApi
import app.vereda.mobile.data.BffScreenRepository
import app.vereda.mobile.domain.ScreenRepository
import app.vereda.sdui.ScreenJson
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun bffApi(): BffApi {
        val baseUrl = BuildConfig.BFF_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScreenJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BffApi::class.java)
    }

    @Provides
    @Singleton
    fun screenRepository(
        api: BffApi,
        @ApplicationContext context: Context,
    ): ScreenRepository = BffScreenRepository(
        api = api,
        transportMessage = context.getString(R.string.transport_error),
    )
}
