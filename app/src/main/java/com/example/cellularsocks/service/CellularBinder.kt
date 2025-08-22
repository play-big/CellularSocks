package com.example.cellularsocks.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import javax.net.SocketFactory
import java.net.DatagramSocket

class CellularBinder(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile var cellularNetwork: Network? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            cellularNetwork = network
            Log.i(TAG, "CELLULAR available: $network")
        }
        override fun onLost(network: Network) {
            if (cellularNetwork == network) cellularNetwork = null
            Log.w(TAG, "CELLULAR lost; re-requesting…")
            // 自动重试请求蜂窝网络
            scope.launch { delay(500); safeRequestCellular(INITIAL_TIMEOUT_MS) }
        }
    }

    fun requestCellular(timeoutMs: Long = INITIAL_TIMEOUT_MS) {
        safeRequestCellular(timeoutMs)
    }

    private fun safeRequestCellular(timeoutMs: Long) {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.requestNetwork(req, callback, timeoutMs.toInt())
        } catch (e: Exception) {
            Log.e(TAG, "requestNetwork failed: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = cellularNetwork != null

    fun bindDatagram(socket: DatagramSocket): Boolean {
        val n = cellularNetwork ?: return false
        return try { n.bindSocket(socket); true } catch (e: Exception) { false }
    }

    fun release() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        cellularNetwork = null
        scope.cancel()
    }

    fun socketFactoryOrNull(): SocketFactory? = cellularNetwork?.socketFactory

    companion object {
        private const val TAG = "CellularBinder"
        private const val INITIAL_TIMEOUT_MS = 10_000L
    }
} 