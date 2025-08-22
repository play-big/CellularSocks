package com.example.cellularsocks.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.InetAddresses
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.cellularsocks.MainActivity
import com.example.cellularsocks.R
import com.example.cellularsocks.core.Socks5Server
import com.example.cellularsocks.util.LogBus
import com.example.cellularsocks.util.NsdHelper
import kotlinx.coroutines.*
import java.net.InetAddress

class ProxyForegroundService : LifecycleService() {

    data class Auth(val username: String, val password: String)

    inner class LocalBinder : Binder() { fun getService(): ProxyForegroundService = this@ProxyForegroundService }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var binderCell: CellularBinder
    private var server: Socks5Server? = null
    private var nsd: NsdHelper? = null

    val statsFlow get() = server?.statsFlow

    override fun onCreate() {
        super.onCreate()
        binderCell = CellularBinder(this)
        // 请求蜂窝并给一个初始超时，避免一直悬挂
        binderCell.requestCellular(timeoutMs = 10_000)
        startForeground(NOTIF_ID, buildNotification("准备中"))
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()
    }

    fun startProxy(ip: String, port: Int, auth: Auth?): Boolean {
        if (server != null) return true
        // 预检蜂窝可用性
        if (!binderCell.isAvailable()) {
            updateNotification("蜂窝未就绪，稍后重试或检查移动数据")
            return false
        }
        val listen = InetAddresses.parseNumericAddress(ip)
        val sfProvider = { binderCell.socketFactoryOrNull() }
        val authCore = auth?.let { Socks5Server.Auth(it.username, it.password) }
        server = Socks5Server(
            listen,
            port,
            sfProvider,
            authCore,
            onState = { msg ->
                LogBus.post(msg)
                updateNotification(msg)
            },
            maxSessions = 128,
            allowList = null,
            denyList = null,
            authFailThresholdPerMin = 10,
            tempBlockMinutes = 10,
            bindDatagramCellular = { socket -> binderCell.bindDatagram(socket) }
        )
        scope.launch { server?.serve() }
        updateNotification("监听 $ip:$port（出口：蜂窝）")
        // 注册 NSD（便于局域网发现）
        nsd = NsdHelper(this).also { it.register("CellularSocks", port) }
        return true
    }

    fun stopProxy() {
        server?.shutdown(); server = null
        nsd?.unregister(); nsd = null
        updateNotification("已停止")
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "proxy"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_proxy)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProxy()
        binderCell.release()
        scope.cancel()
    }

    companion object { private const val NOTIF_ID = 1 }
} 