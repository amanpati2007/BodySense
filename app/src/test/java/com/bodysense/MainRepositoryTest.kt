package com.bodysense

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
class MainRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var repository: MainRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        repository = MainRepository(ApplicationProvider.getApplicationContext(), apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `predict returns success response`() = runBlocking {
        // Arrange
        val responseBody = """
            {
                "disease": "heart",
                "risk": 75.5,
                "confidence": 0.9,
                "method": "ml_model"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        // Act
        val result = repository.predict("heart", mapOf("age" to 50f))

        // Assert
        assertTrue(result.isSuccess)
        val prediction = result.getOrNull()!!
        assertEquals("heart", prediction.disease)
        assertEquals(75.5f, prediction.risk)
        assertEquals("ml_model", prediction.method)
    }

    @Test
    fun `predict returns failure on server error`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        // Act
        val result = repository.predict("heart", mapOf("age" to 50f))

        // Assert
        assertTrue(result.isFailure)
    }
}



