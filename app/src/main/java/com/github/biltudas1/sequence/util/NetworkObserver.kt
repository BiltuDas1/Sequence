package com.github.biltudas1.sequence.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

enum class NetworkStatus {
    Available, Unavailable, Unstable
}

interface NetworkObserver {
    fun observe(): Flow<NetworkStatus>
}

class ConnectivityObserver(
    context: Context
) : NetworkObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun observe(): Flow<NetworkStatus> {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Timber.i("Network available: $network")
                    launch { send(NetworkStatus.Available) }
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    Timber.w("Network losing: $network (maxMsToLive: $maxMsToLive)")
                    launch { send(NetworkStatus.Unstable) }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Timber.w("Network lost: $network")
                    launch { send(NetworkStatus.Unavailable) }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Timber.w("Network unavailable")
                    launch { send(NetworkStatus.Unavailable) }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val status = if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        val downBandwidth = networkCapabilities.linkDownstreamBandwidthKbps
                        Timber.v("Network capabilities changed: Validated, Bandwidth: ${downBandwidth}Kbps")
                        // if bandwidth is very low, consider it unstable/slow
                        if (downBandwidth in 1..160) {
                            NetworkStatus.Unstable
                        } else {
                            NetworkStatus.Available
                        }
                    } else {
                        Timber.v("Network capabilities changed: Not Validated")
                        // If not validated, it might still be "available" but no actual internet
                        NetworkStatus.Unavailable
                    }
                    launch { send(status) }
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)

            // Initial state
            val currentNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
            val initialStatus = if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    val downBandwidth = capabilities.linkDownstreamBandwidthKbps
                    if (downBandwidth in 1..160) NetworkStatus.Unstable else NetworkStatus.Available
                } else {
                    NetworkStatus.Unstable
                }
            } else {
                NetworkStatus.Unavailable
            }
            trySend(initialStatus)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
    }
}
