package com.jbsan.ldapadvisor.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes default network availability via ConnectivityManager (API 24+).
 * Reports that a default network is available — not end-to-end Internet reachability.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkAvailable = MutableStateFlow(isCurrentlyAvailable())
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkAvailable.value = true
        }

        override fun onLost(network: Network) {
            _networkAvailable.value = isCurrentlyAvailable()
        }

        override fun onUnavailable() {
            _networkAvailable.value = false
        }
    }

    fun start() {
        val request = NetworkRequest.Builder().build()
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }.recoverCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }
        _networkAvailable.value = isCurrentlyAvailable()
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun isCurrentlyAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }
}
