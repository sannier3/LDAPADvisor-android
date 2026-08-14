package com.jbsan.ldapadvisor.app

import android.content.Context
import com.jbsan.ldapadvisor.core.logging.AppLogger
import com.jbsan.ldapadvisor.core.logging.InMemoryAppLogger
import com.jbsan.ldapadvisor.core.network.NetworkMonitor
import com.jbsan.ldapadvisor.core.security.AndroidKeystoreSecretStore
import com.jbsan.ldapadvisor.core.security.SecretStore
import com.jbsan.ldapadvisor.data.database.LdapAdvisorDatabase
import com.jbsan.ldapadvisor.data.datastore.SettingsDataStore
import com.jbsan.ldapadvisor.data.diagnostics.DiagnosticEngine
import com.jbsan.ldapadvisor.data.diagnostics.DnsDiagnosticService
import com.jbsan.ldapadvisor.data.diagnostics.LdapDiagnosticService
import com.jbsan.ldapadvisor.data.diagnostics.TcpDiagnosticService
import com.jbsan.ldapadvisor.data.dns.AdDiscoveryService
import com.jbsan.ldapadvisor.data.dns.DnsResolver
import com.jbsan.ldapadvisor.data.kerberos.KerberosTicketService
import com.jbsan.ldapadvisor.data.ldap.LdapClientFactory
import com.jbsan.ldapadvisor.data.ldap.SessionManager
import com.jbsan.ldapadvisor.data.report.ReportGenerator
import com.jbsan.ldapadvisor.data.report.ReportSanitizer
import com.jbsan.ldapadvisor.data.repository.CustomCaRepository
import com.jbsan.ldapadvisor.data.repository.FavoritesRepository
import com.jbsan.ldapadvisor.data.repository.HistoryRepository
import com.jbsan.ldapadvisor.data.repository.ProfileRepository
import com.jbsan.ldapadvisor.data.repository.SettingsRepository
import com.jbsan.ldapadvisor.data.tls.ProfileSslSocketFactoryFactory
import com.jbsan.ldapadvisor.data.tls.TlsDiagnosticService
import com.jbsan.ldapadvisor.domain.service.AdvisorEngine

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val logger: AppLogger = InMemoryAppLogger()
    val database: LdapAdvisorDatabase = LdapAdvisorDatabase.build(appContext)
    val secretStore: SecretStore = AndroidKeystoreSecretStore(appContext)
    val settingsDataStore = SettingsDataStore(appContext)
    val settingsRepository = SettingsRepository(settingsDataStore)
    val profileRepository = ProfileRepository(database.profileDao(), secretStore)
    val historyRepository = HistoryRepository(database.diagnosticRunDao(), database.reportMetaDao())
    val customCaRepository = CustomCaRepository(database.customCaDao())
    val favoritesRepository = FavoritesRepository(database.favoriteDnDao(), database.searchHistoryDao())
    val networkMonitor = NetworkMonitor(appContext)

    val sslSocketFactoryFactory = ProfileSslSocketFactoryFactory()
    val ldapClientFactory = LdapClientFactory(
        sslFactoryFactory = sslSocketFactoryFactory,
        customCaDao = database.customCaDao(),
        logger = logger,
    )
    val dnsResolver = DnsResolver()
    val sessionManager = SessionManager(
        clientFactory = ldapClientFactory,
        profileRepository = profileRepository,
        secretStore = secretStore,
        logger = logger,
        kerberosTicketService = KerberosTicketService(logger, dnsResolver),
    )

    val adDiscoveryService = AdDiscoveryService(dnsResolver)
    val tlsDiagnosticService = TlsDiagnosticService(sslSocketFactoryFactory)
    val tcpDiagnosticService = TcpDiagnosticService()
    val dnsDiagnosticService = DnsDiagnosticService(dnsResolver, adDiscoveryService)
    val ldapDiagnosticService = LdapDiagnosticService(sessionManager)
    val advisorEngine = AdvisorEngine()
    val diagnosticEngine = DiagnosticEngine(
        tcpDiagnosticService = tcpDiagnosticService,
        dnsDiagnosticService = dnsDiagnosticService,
        ldapDiagnosticService = ldapDiagnosticService,
        tlsDiagnosticService = tlsDiagnosticService,
        advisorEngine = advisorEngine,
        sessionManager = sessionManager,
    )
    val reportSanitizer = ReportSanitizer()
    val reportGenerator = ReportGenerator(reportSanitizer)
}
