package com.bodysense

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BodySenseApplication : Application() {

    /** Application-scoped coroutine scope. Lives as long as the process. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Global network connectivity monitor. */
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize network monitor first — used by ViewModel immediately
        networkMonitor = NetworkMonitor(this)

        // Warm up networking and database on a background thread to reduce first-screen latency.
        applicationScope.launch(Dispatchers.IO) {
            try {
                NetworkModule.apiService           // Eagerly initialize Retrofit service
                com.bodysense.data.AppDatabase.getDatabase(this@BodySenseApplication)
            } catch (_: Exception) {
                // Warmup failures are non-fatal — real errors are reported when the user interacts
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        networkMonitor.unregister()
    }
}
