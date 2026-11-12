package com.infobip.napi.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import com.infobip.napi.model.domain.CellularResult
import kotlin.coroutines.resume

class AndroidCellularController(context: Context) : CellularController {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    override val supportsAutoCellular: Boolean = true

    override suspend fun switchToCellular(): CellularResult {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val bound = connectivityManager.bindProcessToNetwork(network)
                    if (!bound) unregister()
                    if (continuation.isActive) {
                        continuation.resume(if (bound) CellularResult.SUCCESS else CellularResult.ERROR)
                    }
                }

                override fun onUnavailable() {
                    unregister()
                    if (continuation.isActive) continuation.resume(CellularResult.CELLULAR_UNAVAILABLE)
                }
            }
            activeCallback = callback
            continuation.invokeOnCancellation { unregister() }
            connectivityManager.requestNetwork(request, callback, CELLULAR_TIMEOUT_MILLIS)
        }
    }

    override suspend fun resetToDefault(): Boolean {
        unregister()
        return connectivityManager.bindProcessToNetwork(null)
    }

    private fun unregister() {
        activeCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            activeCallback = null
        }
    }

    private companion object {
        const val CELLULAR_TIMEOUT_MILLIS = 5000
    }
}
