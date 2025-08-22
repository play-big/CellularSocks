package com.example.cellularsocks.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(private val context: Context) {
    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private var registered = false
    private var listener: NsdManager.RegistrationListener? = null

    fun register(serviceName: String, port: Int) {
        if (registered) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = "_cellularsocks._tcp."
            this.serviceName = serviceName
            this.port = port
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registered = true
                Log.i(TAG, "NSD registered: ${info.serviceName}:${info.port}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registered = false
                Log.i(TAG, "NSD unregistered")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed: $errorCode")
            }
        }
        listener = l
        nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun unregister() {
        val l = listener ?: return
        try { nsd.unregisterService(l) } catch (_: Exception) {}
        registered = false
        listener = null
    }

    companion object { private const val TAG = "NsdHelper" }
} 