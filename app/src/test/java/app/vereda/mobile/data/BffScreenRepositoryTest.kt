package app.vereda.mobile.data

import app.vereda.mobile.domain.LoadResult
import app.vereda.sdui.ScreenJson
import app.vereda.sdui.TextFieldComponent
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class BffScreenRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BffScreenRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(ScreenJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BffApi::class.java)
        repository = BffScreenRepository(api, transportMessage = "offline")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesAScreenFromTheBff() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"schemaVersion":1,"id":"signup","title":"Criar conta","components":[
                      {"type":"textField","id":"email","label":"E-mail"}
                    ]}
                    """.trimIndent(),
                ),
        )

        val result = repository.load("bootstrap") as LoadResult.Ready

        assertEquals("signup", result.screen.id)
        assertEquals("E-mail", (result.screen.components.single() as TextFieldComponent).label)
        assertEquals("/screens/bootstrap", server.takeRequest().path)
    }

    @Test
    fun usesTheServerMessageWhenTheCallFails() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message":"Falha interna."}"""),
        )
        try {
            repository.load("bootstrap")
            error("expected failure")
        } catch (error: TransportFailure) {
            assertEquals("Falha interna.", error.message)
        }
        assertTrue(server.requestCount == 1)
    }
}
