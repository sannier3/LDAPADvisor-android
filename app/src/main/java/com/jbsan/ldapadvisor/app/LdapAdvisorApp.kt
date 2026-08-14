package com.jbsan.ldapadvisor.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LdapAdvisorApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.networkMonitor.start()
        appScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                container.logger.debugEnabled = settings.debugLoggingEnabled
            }
        }
        appScope.launch {
            container.networkMonitor.networkAvailable.collectLatest { available ->
                container.sessionManager.onNetworkAvailabilityChanged(available)
            }
        }
    }

    override fun onTerminate() {
        container.sessionManager.shutdown()
        container.networkMonitor.stop()
        super.onTerminate()
    }
}
