package com.bodysense

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() = runTest {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(UiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `reset sets state to idle`() = runTest {
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.predict("heart", mapOf("age" to 50f))
        
        viewModel.reset()
        assertEquals(UiState.Idle, viewModel.uiState.value)
    }
}


