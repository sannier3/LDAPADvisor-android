package com.jbsan.ldapadvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jbsan.ldapadvisor.app.LdapAdvisorApp
import com.jbsan.ldapadvisor.domain.model.AppSettings
import com.jbsan.ldapadvisor.feature.navigation.AppNavHost
import com.jbsan.ldapadvisor.feature.settings.SettingsViewModel
import com.jbsan.ldapadvisor.ui.theme.LDAPADvisorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LdapAdvisorApp).container
        val factory = ViewModelFactory(container)
        setContent {
            val settingsVm: SettingsViewModel = viewModel(factory = factory)
            val settings by settingsVm.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            LDAPADvisorTheme(themeMode = settings.themeMode) {
                Surface(modifier = ComposeModifier.fillMaxSize()) {
                    AppNavHost(factory = factory)
                }
            }
        }
    }
}
