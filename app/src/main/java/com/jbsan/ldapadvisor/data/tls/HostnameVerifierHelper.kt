package com.jbsan.ldapadvisor.data.tls

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

object HostnameVerifierHelper {
    fun defaultVerifier(expectedHostname: String): HostnameVerifier {
        val platform = HttpsURLConnection.getDefaultHostnameVerifier()
        return HostnameVerifier { hostname, session ->
            val candidate = hostname.ifBlank { expectedHostname }
            platform.verify(candidate, session)
        }
    }
}
