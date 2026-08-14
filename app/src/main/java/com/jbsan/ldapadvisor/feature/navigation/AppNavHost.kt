package com.jbsan.ldapadvisor.feature.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.jbsan.ldapadvisor.ui.ComposeModifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.feature.admin.CopyUserScreen
import com.jbsan.ldapadvisor.feature.admin.CreateGroupScreen
import com.jbsan.ldapadvisor.feature.admin.CreateObjectsViewModel
import com.jbsan.ldapadvisor.feature.admin.CreateOuScreen
import com.jbsan.ldapadvisor.feature.admin.CreateUserScreen
import com.jbsan.ldapadvisor.feature.advisor.AdvisorViewModel
import com.jbsan.ldapadvisor.feature.computers.ComputersViewModel
import com.jbsan.ldapadvisor.feature.connection.ConnectionViewModel
import com.jbsan.ldapadvisor.feature.connection.RootDseViewModel
import com.jbsan.ldapadvisor.feature.connection.SchemaViewModel
import com.jbsan.ldapadvisor.feature.dashboard.DashboardScreen
import com.jbsan.ldapadvisor.feature.dashboard.DashboardViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.ComputerDiagnosticScreen
import com.jbsan.ldapadvisor.feature.diagnostics.ComputerDiagnosticViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.DiagnosticsViewModel
import com.jbsan.ldapadvisor.feature.diagnostics.UserDiagnosticScreen
import com.jbsan.ldapadvisor.feature.diagnostics.UserDiagnosticViewModel
import com.jbsan.ldapadvisor.feature.directory.DirectoryScreen
import com.jbsan.ldapadvisor.feature.directory.DirectoryViewModel
import com.jbsan.ldapadvisor.feature.directory.ObjectDetailsScreen
import com.jbsan.ldapadvisor.feature.directory.ObjectDetailsViewModel
import com.jbsan.ldapadvisor.feature.favorites.FavoritesScreen
import com.jbsan.ldapadvisor.feature.favorites.FavoritesViewModel
import com.jbsan.ldapadvisor.feature.groups.GroupsViewModel
import com.jbsan.ldapadvisor.feature.profiles.ProfileEditScreen
import com.jbsan.ldapadvisor.feature.profiles.ProfileEditViewModel
import com.jbsan.ldapadvisor.feature.profiles.ProfilesScreen
import com.jbsan.ldapadvisor.feature.profiles.ProfilesViewModel
import com.jbsan.ldapadvisor.feature.reports.ReportsViewModel
import com.jbsan.ldapadvisor.feature.raw.RawLdapViewModel
import com.jbsan.ldapadvisor.feature.screens.AboutScreen
import com.jbsan.ldapadvisor.feature.screens.AdvisorScreen
import com.jbsan.ldapadvisor.feature.screens.ComputersScreen
import com.jbsan.ldapadvisor.feature.screens.ConnectionScreen
import com.jbsan.ldapadvisor.feature.screens.DiagnosticsScreen
import com.jbsan.ldapadvisor.feature.screens.GroupsScreen
import com.jbsan.ldapadvisor.feature.screens.HistoryScreen
import com.jbsan.ldapadvisor.feature.screens.LicensesScreen
import com.jbsan.ldapadvisor.feature.screens.OrganizationalUnitsScreen
import com.jbsan.ldapadvisor.feature.screens.PrivacyScreen
import com.jbsan.ldapadvisor.feature.screens.RawLdapScreen
import com.jbsan.ldapadvisor.feature.screens.ReportsScreen
import com.jbsan.ldapadvisor.feature.screens.RootDseScreen
import com.jbsan.ldapadvisor.feature.screens.SchemaScreen
import com.jbsan.ldapadvisor.feature.screens.SearchScreen
import com.jbsan.ldapadvisor.feature.screens.SettingsScreen
import com.jbsan.ldapadvisor.feature.screens.UsersScreen
import com.jbsan.ldapadvisor.feature.search.SearchViewModel
import com.jbsan.ldapadvisor.feature.settings.SettingsViewModel
import com.jbsan.ldapadvisor.feature.users.UserDetailScreen
import com.jbsan.ldapadvisor.feature.users.UserDetailViewModel
import com.jbsan.ldapadvisor.feature.users.UsersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    factory: ViewModelProvider.Factory,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val useRail = LocalConfiguration.current.screenWidthDp >= 600
    // Keep primary tabs always available so quick actions (Users, Search, etc.) never trap the user.
    val isPrimaryRoute = PrimaryTabs.any { route == it || route.startsWith("$it?") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleForRoute(route)) },
                navigationIcon = {
                    if (!isPrimaryRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!useRail) {
                PrimaryNavigationBar(navController, route)
            }
        },
    ) { padding ->
        Row(ComposeModifier.padding(padding).fillMaxSize()) {
            if (useRail) {
                PrimaryNavigationRail(navController, route)
            }
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = ComposeModifier.weight(1f).fillMaxSize(),
            ) {
                composable(Routes.DASHBOARD) {
                    val vm: DashboardViewModel = viewModel(factory = factory)
                    DashboardScreen(
                        viewModel = vm,
                        onConnect = { navController.navigate(Routes.CONNECTION) },
                        onBrowse = { navController.navigatePrimary(Routes.DIRECTORY) },
                        onSearchUser = { navController.navigate(Routes.USERS) },
                        onSearchComputer = { navController.navigate(Routes.COMPUTERS) },
                        onFullDiagnostic = { navController.navigatePrimary(Routes.DIAGNOSTICS) },
                        onLdapSearch = { navController.navigate(Routes.SEARCH) },
                        onProfiles = { navController.navigatePrimary(Routes.PROFILES) },
                    )
                }
                composable(Routes.DIRECTORY) {
                    val vm: DirectoryViewModel = viewModel(factory = factory)
                    DirectoryScreen(vm) { dn -> navController.navigate(Routes.objectDetails(dn)) }
                }
                composable(Routes.DIAGNOSTICS) {
                    val vm: DiagnosticsViewModel = viewModel(factory = factory)
                    val settingsVm: SettingsViewModel = viewModel(factory = factory)
                    DiagnosticsScreen(
                        viewModel = vm,
                        onAdvisor = { navController.navigate(Routes.ADVISOR) },
                        onReports = { navController.navigate(Routes.REPORTS) },
                        onUserDiag = { navController.navigate(Routes.USER_DIAGNOSTIC) },
                        onComputerDiag = { navController.navigate(Routes.COMPUTER_DIAGNOSTIC) },
                        onExportLogs = { settingsVm.exportLogs(context) },
                    )
                }
                composable(Routes.PROFILES) {
                    val vm: ProfilesViewModel = viewModel(factory = factory)
                    ProfilesScreen(
                        viewModel = vm,
                        onCreate = { navController.navigate(Routes.PROFILE_CREATE) },
                        onEdit = { id -> navController.navigate(Routes.profileEdit(id)) },
                        onConnected = { navController.navigatePrimary(Routes.DASHBOARD) },
                    )
                }
                composable(Routes.SETTINGS) {
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(
                        viewModel = vm,
                        onAbout = { navController.navigate(Routes.ABOUT) },
                        onPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onLicenses = { navController.navigate(Routes.LICENSES) },
                        onUsers = { navController.navigate(Routes.USERS) },
                        onGroups = { navController.navigate(Routes.GROUPS) },
                        onComputers = { navController.navigate(Routes.COMPUTERS) },
                        onOus = { navController.navigate(Routes.OUS) },
                        onSearch = { navController.navigate(Routes.SEARCH) },
                        onRawLdap = { navController.navigate(Routes.RAW_LDAP) },
                        onRootDse = { navController.navigate(Routes.ROOT_DSE) },
                        onSchema = { navController.navigate(Routes.SCHEMA) },
                        onHistory = { navController.navigate(Routes.HISTORY) },
                        onFavorites = { navController.navigate(Routes.FAVORITES) },
                        onUserDiag = { navController.navigate(Routes.USER_DIAGNOSTIC) },
                        onComputerDiag = { navController.navigate(Routes.COMPUTER_DIAGNOSTIC) },
                        onExportLogs = { vm.exportLogs(context) },
                    )
                }
                composable(Routes.PROFILE_CREATE) {
                    val vm: ProfileEditViewModel = viewModel(factory = factory)
                    ProfileEditScreen(vm, null) { navController.popBackStack() }
                }
                composable(
                    Routes.PROFILE_EDIT,
                    arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
                ) { entry ->
                    val id = Uri.decode(entry.arguments?.getString("profileId").orEmpty())
                    val vm: ProfileEditViewModel = viewModel(factory = factory)
                    ProfileEditScreen(vm, id) { navController.popBackStack() }
                }
                composable(
                    Routes.OBJECT_DETAILS,
                    arguments = listOf(navArgument("dn") { type = NavType.StringType }),
                ) { entry ->
                    val dn = Uri.decode(entry.arguments?.getString("dn").orEmpty())
                    val vm: ObjectDetailsViewModel = viewModel(factory = factory)
                    ObjectDetailsScreen(
                        viewModel = vm,
                        dn = dn,
                        onEditUser = { userDn -> navController.navigate(Routes.userDetail(userDn)) },
                        onCopyUser = { userDn -> navController.navigate(Routes.copyUser(userDn)) },
                    )
                }
                composable(Routes.SEARCH) {
                    val vm: SearchViewModel = viewModel(factory = factory)
                    SearchScreen(vm) { dn -> navController.navigate(Routes.objectDetails(dn)) }
                }
                composable(Routes.USERS) {
                    val vm: UsersViewModel = viewModel(factory = factory)
                    UsersScreen(
                        viewModel = vm,
                        onOpen = { dn -> navController.navigate(Routes.userDetail(dn)) },
                        onCreate = { navController.navigate(Routes.CREATE_USER) },
                    )
                }
                composable(
                    Routes.USER_DETAIL,
                    arguments = listOf(navArgument("dn") { type = NavType.StringType }),
                ) { entry ->
                    val dn = Uri.decode(entry.arguments?.getString("dn").orEmpty())
                    val vm: UserDetailViewModel = viewModel(factory = factory)
                    UserDetailScreen(
                        viewModel = vm,
                        dn = dn,
                        onCopyUser = { navController.navigate(Routes.copyUser(it)) },
                    )
                }
                composable(Routes.GROUPS) {
                    val vm: GroupsViewModel = viewModel(factory = factory)
                    GroupsScreen(vm, onCreate = { navController.navigate(Routes.CREATE_GROUP) })
                }
                composable(Routes.COMPUTERS) {
                    val vm: ComputersViewModel = viewModel(factory = factory)
                    ComputersScreen(
                        viewModel = vm,
                        onOpenDetails = { dn -> navController.navigate(Routes.objectDetails(dn)) },
                        onComputerDiagnostic = { host -> navController.navigate(Routes.computerDiagnostic(host)) },
                    )
                }
                composable(Routes.OUS) {
                    val vm: SearchViewModel = viewModel(factory = factory)
                    OrganizationalUnitsScreen(
                        viewModel = vm,
                        onOpen = { dn -> navController.navigate(Routes.objectDetails(dn)) },
                        onCreate = { navController.navigate(Routes.CREATE_OU) },
                    )
                }
                composable(Routes.ADVISOR) {
                    val vm: AdvisorViewModel = viewModel(factory = factory)
                    AdvisorScreen(vm)
                }
                composable(Routes.REPORTS) {
                    val vm: ReportsViewModel = viewModel(factory = factory)
                    ReportsScreen(vm)
                }
                composable(Routes.CONNECTION) {
                    val vm: ConnectionViewModel = viewModel(factory = factory)
                    val profilesVm: ProfilesViewModel = viewModel(factory = factory)
                    ConnectionScreen(vm, profilesVm, onProfiles = { navController.navigate(Routes.PROFILES) })
                }
                composable(Routes.ROOT_DSE) {
                    val vm: RootDseViewModel = viewModel(factory = factory)
                    RootDseScreen(vm)
                }
                composable(Routes.SCHEMA) {
                    val vm: SchemaViewModel = viewModel(factory = factory)
                    SchemaScreen(vm)
                }
                composable(Routes.HISTORY) {
                    val vm: AdvisorViewModel = viewModel(factory = factory)
                    HistoryScreen(vm, onAdvisor = { navController.navigate(Routes.ADVISOR) })
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onLicenses = { navController.navigate(Routes.LICENSES) })
                }
                composable(Routes.PRIVACY) { PrivacyScreen() }
                composable(Routes.LICENSES) { LicensesScreen() }
                composable(Routes.RAW_LDAP) {
                    val vm: RawLdapViewModel = viewModel(factory = factory)
                    RawLdapScreen(vm)
                }
                composable(Routes.CREATE_USER) {
                    val vm: CreateObjectsViewModel = viewModel(factory = factory)
                    CreateUserScreen(vm)
                }
                composable(
                    Routes.COPY_USER,
                    arguments = listOf(navArgument("dn") { type = NavType.StringType }),
                ) { entry ->
                    val dn = Uri.decode(entry.arguments?.getString("dn").orEmpty())
                    val vm: CreateObjectsViewModel = viewModel(factory = factory)
                    CopyUserScreen(
                        viewModel = vm,
                        sourceDn = dn,
                        onOpenCreated = { created -> navController.navigate(Routes.userDetail(created)) },
                    )
                }
                composable(Routes.CREATE_GROUP) {
                    val vm: CreateObjectsViewModel = viewModel(factory = factory)
                    CreateGroupScreen(vm)
                }
                composable(Routes.CREATE_OU) {
                    val vm: CreateObjectsViewModel = viewModel(factory = factory)
                    CreateOuScreen(vm)
                }
                composable(Routes.USER_DIAGNOSTIC) {
                    val vm: UserDiagnosticViewModel = viewModel(factory = factory)
                    UserDiagnosticScreen(vm)
                }
                composable(
                    route = "computer_diagnostic?q={q}",
                    arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    val q = Uri.decode(entry.arguments?.getString("q").orEmpty())
                    val vm: ComputerDiagnosticViewModel = viewModel(factory = factory)
                    ComputerDiagnosticScreen(vm, initialQuery = q)
                }
                composable(Routes.COMPUTER_DIAGNOSTIC) {
                    val vm: ComputerDiagnosticViewModel = viewModel(factory = factory)
                    ComputerDiagnosticScreen(vm)
                }
                composable(Routes.FAVORITES) {
                    val vm: FavoritesViewModel = viewModel(factory = factory)
                    FavoritesScreen(vm) { dn -> navController.navigate(Routes.objectDetails(dn)) }
                }
            }
        }
    }
}

@Composable
private fun titleForRoute(route: String): String = when {
    route == Routes.DASHBOARD -> stringResource(R.string.nav_dashboard)
    route == Routes.DIRECTORY -> stringResource(R.string.nav_directory)
    route == Routes.DIAGNOSTICS -> stringResource(R.string.nav_diagnostics)
    route == Routes.PROFILES -> stringResource(R.string.nav_profiles)
    route == Routes.SETTINGS -> stringResource(R.string.nav_settings)
    route == Routes.SEARCH -> stringResource(R.string.nav_search)
    route == Routes.USERS -> stringResource(R.string.nav_users)
    route == Routes.GROUPS -> stringResource(R.string.nav_groups)
    route == Routes.COMPUTERS -> stringResource(R.string.nav_computers)
    route == Routes.OUS -> stringResource(R.string.nav_ous)
    route == Routes.REPORTS -> stringResource(R.string.nav_reports)
    route == Routes.ADVISOR -> stringResource(R.string.nav_advisor)
    route == Routes.CONNECTION -> stringResource(R.string.nav_connection)
    route == Routes.HISTORY -> stringResource(R.string.nav_history)
    route == Routes.ABOUT -> stringResource(R.string.nav_about)
    route == Routes.PRIVACY -> stringResource(R.string.nav_privacy)
    route == Routes.LICENSES -> stringResource(R.string.settings_licenses)
    route == Routes.RAW_LDAP -> stringResource(R.string.nav_raw_ldap)
    route == Routes.ROOT_DSE -> stringResource(R.string.nav_rootdse)
    route == Routes.SCHEMA -> stringResource(R.string.nav_schema)
    route == Routes.CREATE_USER -> stringResource(R.string.nav_create_user)
    route.startsWith("copy_user/") -> stringResource(R.string.nav_copy_user)
    route == Routes.CREATE_GROUP -> stringResource(R.string.nav_create_group)
    route == Routes.CREATE_OU -> stringResource(R.string.nav_create_ou)
    route == Routes.USER_DIAGNOSTIC -> stringResource(R.string.nav_user_diagnostic)
    route == Routes.COMPUTER_DIAGNOSTIC || route.startsWith("computer_diagnostic") -> stringResource(R.string.nav_computer_diagnostic)
    route == Routes.FAVORITES -> stringResource(R.string.nav_favorites)
    route.startsWith("object/") -> stringResource(R.string.nav_object_details)
    route.startsWith("user/") -> stringResource(R.string.nav_user_detail)
    route.startsWith("profile_edit") || route == Routes.PROFILE_CREATE -> stringResource(R.string.nav_profile_edit)
    else -> stringResource(R.string.app_name)
}

@Composable
private fun PrimaryNavigationBar(navController: NavHostController, currentRoute: String) {
    NavigationBar {
        PrimaryTabs.forEach { tab ->
            val label = labelForTab(tab)
            NavigationBarItem(
                selected = isTabSelected(tab, currentRoute),
                onClick = { navController.navigatePrimary(tab) },
                icon = { Icon(iconForTab(tab), contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PrimaryNavigationRail(navController: NavHostController, currentRoute: String) {
    NavigationRail {
        PrimaryTabs.forEach { tab ->
            val label = labelForTab(tab)
            NavigationRailItem(
                selected = isTabSelected(tab, currentRoute),
                onClick = { navController.navigatePrimary(tab) },
                icon = { Icon(iconForTab(tab), contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

private fun iconForTab(tab: String) = when (tab) {
    Routes.DASHBOARD -> Icons.Filled.Home
    Routes.DIRECTORY -> Icons.Filled.AccountTree
    Routes.DIAGNOSTICS -> Icons.Filled.HealthAndSafety
    else -> Icons.Filled.People
}

@Composable
private fun labelForTab(tab: String) = when (tab) {
    Routes.DASHBOARD -> stringResource(R.string.nav_dashboard)
    Routes.DIRECTORY -> stringResource(R.string.nav_directory)
    Routes.DIAGNOSTICS -> stringResource(R.string.nav_diagnostics)
    else -> stringResource(R.string.nav_profiles)
}

private fun isTabSelected(tab: String, currentRoute: String): Boolean {
    if (currentRoute.isBlank()) return tab == Routes.DASHBOARD
    if (currentRoute == tab || currentRoute.startsWith("$tab?")) return true
    // Secondary screens under Directory / Diagnostics / Profiles — not under Dashboard.
    // (Selecting Dashboard while on Users/Search made Home look already active and broke return.)
    if (tab == Routes.DIRECTORY) {
        return currentRoute.startsWith("object/")
    }
    if (tab == Routes.DIAGNOSTICS) {
        return currentRoute in setOf(
            Routes.ADVISOR,
            Routes.REPORTS,
            Routes.USER_DIAGNOSTIC,
            Routes.COMPUTER_DIAGNOSTIC,
        ) || currentRoute.startsWith("computer_diagnostic")
    }
    if (tab == Routes.PROFILES) {
        return currentRoute == Routes.PROFILE_CREATE || currentRoute.startsWith("profile_edit")
    }
    return false
}

/**
 * Switch primary tabs reliably.
 *
 * Returning to Dashboard must [popBackStack] to the start destination: the usual
 * `navigate(start) { popUpTo(start); launchSingleTop; restoreState }` pattern often
 * becomes a no-op when Dashboard is already under secondary screens, which trapped
 * users after Accueil quick actions (Users, Search, Connection, …).
 */
private fun NavHostController.navigatePrimary(route: String) {
    val current = currentDestination?.route
    if (current == route) {
        // Re-tap current primary tab: clear any destinations pushed above it.
        popBackStack(route, inclusive = false)
        return
    }
    if (route == Routes.DASHBOARD) {
        // Always land on Accueil by popping everything above it.
        val popped = popBackStack(Routes.DASHBOARD, inclusive = false)
        if (!popped && current != Routes.DASHBOARD) {
            navigate(Routes.DASHBOARD) {
                launchSingleTop = true
            }
        }
        return
    }
    navigate(route) {
        popUpTo(Routes.DASHBOARD) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
