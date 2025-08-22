package com.example.cellularsocks.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address

object NetUtils {
    fun wifiIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.allNetworks
        for (n in networks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            val lp = cm.getLinkProperties(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val ipv4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
                if (ipv4 != null) return (ipv4 as LinkAddress).address.hostAddress
            }
        }
        return null
    }
} 