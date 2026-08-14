package com.jbsan.ldapadvisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jbsan.ldapadvisor.app.AppContainer
import com.jbsan.ldapadvisor.feature.admin.CreateObjectsViewModel
import com.jbsan.ldapadvisor.feature.advisor.AdvisorViewModel
import com.jbsan.ldapadvisor.feature.computers.ComputersViewModel
import com.jbsan.ldapadvisor.feature.connection.ConnectionViewModel
import com.jbsan.ldapadvisor.feature.dashboard.DashboardViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.ComputerDiagnosticViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.DiagnosticsViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.UserDiagnosticViewModel
import com.jbsan.ldapadvisor.feature.directory.DirectoryViewModel
import com.jbsan.ldapadvisor.feature.directory.ObjectDetailsViewModel
import com.jbsan.ldapadvisor.feature.favorites.FavoritesViewModel
import com.jbsan.ldapadvisor.feature.groups.GroupsViewModel
import com.jbsan.ldapadvisor.feature.profiles.ProfileEditViewModel
import com.jbsan.ldapadvisor.feature.profiles.ProfilesViewModel
import com.jbsan.ldapadvisor.feature.raw.RawLdapViewModel
import com.jbsan.ldapadvisor.feature.reports.ReportsViewModel
import com.jbsan.ldapadvisor.feature.connection.RootDseViewModel
import com.jbsan.ldapadvisor.feature.connection.SchemaViewModel
import com.jbsan.ldapadvisor.feature.search.SearchViewModel
import com.jbsan.ldapadvisor.feature.settings.SettingsViewModel
import com.jbsan.ldapadvisor.feature.users.UserDetailViewModel
import com.jbsan.ldapadvisor.feature.users.UsersViewModel

class ViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(container.profileRepository, container.sessionManager, container.historyRepository)
            modelClass.isAssignableFrom(ProfilesViewModel::class.java) ->
                ProfilesViewModel(container.profileRepository, container.sessionManager, container.secretStore, container.adDiscoveryService, container.settingsRepository)
            modelClass.isAssignableFrom(ProfileEditViewModel::class.java) ->
                ProfileEditViewModel(
                    container.profileRepository,
                    container.secretStore,
                    container.settingsRepository,
                    container.adDiscoveryService,
                    container.customCaRepository,
                    container.tcpDiagnosticService,
                )
            modelClass.isAssignableFrom(RawLdapViewModel::class.java) ->
                RawLdapViewModel(container.sessionManager)
            modelClass.isAssignableFrom(DirectoryViewModel::class.java) ->
                DirectoryViewModel(container.sessionManager)
            modelClass.isAssignableFrom(ObjectDetailsViewModel::class.java) ->
                ObjectDetailsViewModel(container.sessionManager, container.favoritesRepository)
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(container.sessionManager, container.favoritesRepository, container.settingsRepository)
            modelClass.isAssignableFrom(UsersViewModel::class.java) ->
                UsersViewModel(container.sessionManager)
            modelClass.isAssignableFrom(UserDetailViewModel::class.java) ->
                UserDetailViewModel(container.sessionManager)
            modelClass.isAssignableFrom(GroupsViewModel::class.java) ->
                GroupsViewModel(container.sessionManager)
            modelClass.isAssignableFrom(ComputersViewModel::class.java) ->
                ComputersViewModel(container.sessionManager)
            modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) ->
                DiagnosticsViewModel(container.diagnosticEngine, container.sessionManager, container.profileRepository, container.historyRepository, container.settingsRepository)
            modelClass.isAssignableFrom(UserDiagnosticViewModel::class.java) ->
                UserDiagnosticViewModel(container.sessionManager)
            modelClass.isAssignableFrom(ComputerDiagnosticViewModel::class.java) ->
                ComputerDiagnosticViewModel(container.sessionManager, container.dnsResolver, container.tcpDiagnosticService)
            modelClass.isAssignableFrom(CreateObjectsViewModel::class.java) ->
                CreateObjectsViewModel(container.sessionManager)
            modelClass.isAssignableFrom(FavoritesViewModel::class.java) ->
                FavoritesViewModel(container.favoritesRepository)
            modelClass.isAssignableFrom(AdvisorViewModel::class.java) ->
                AdvisorViewModel(container.historyRepository)
            modelClass.isAssignableFrom(ReportsViewModel::class.java) ->
                ReportsViewModel(container.reportGenerator, container.historyRepository, container.profileRepository, container.settingsRepository)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container.settingsRepository, container.logger)
            modelClass.isAssignableFrom(ConnectionViewModel::class.java) ->
                ConnectionViewModel(container.sessionManager, container.profileRepository, container.secretStore)
            modelClass.isAssignableFrom(RootDseViewModel::class.java) ->
                RootDseViewModel(container.sessionManager)
            modelClass.isAssignableFrom(SchemaViewModel::class.java) ->
                SchemaViewModel(container.sessionManager)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
