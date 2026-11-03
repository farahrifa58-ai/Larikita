package com.example.domain.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentNetwork())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var onNetworkAvailableCallback: (() -> Unit)? = null

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_isOnline.value
                    _isOnline.value = true
                    if (wasOffline) {
                        onNetworkAvailableCallback?.invoke()
                    }
                }

                override fun onLost(network: Network) {
                    _isOnline.value = checkCurrentNetwork()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        } catch (_: Exception) {}
    }

    fun setOnNetworkAvailable(callback: () -> Unit) {
        onNetworkAvailableCallback = callback
    }

    private fun checkCurrentNetwork(): Boolean {
        return try {
            val network = connectivityManager?.activeNetwork ?: return false
            val cap = connectivityManager.getNetworkCapabilities(network) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
