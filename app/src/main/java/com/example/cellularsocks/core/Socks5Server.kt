package com.example.cellularsocks.core

import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory
import com.example.cellularsocks.util.IpAcl

class Socks5Server(
    private val listenIp: InetAddress,
    private val port: Int,
    private val socketFactoryProvider: () -> SocketFactory?,
    private val auth: Auth?,
    private val onState: (String) -> Unit = {},
    private val maxSessions: Int = 128,
    private val allowList: List<String>? = null,
    private val denyList: List<String>? = null,
    private val authFailThresholdPerMin: Int = 10,
    private val tempBlockMinutes: Int = 10,
    private val bindDatagramCellular: ((java.net.DatagramSocket) -> Boolean)? = null
) {
    data class Auth(val username: String, val password: String)
    data class Stats(
        val activeSessions: Int = 0,
        val totalSessions: Long = 0,
        val totalBytes: Long = 0
    )

    @Volatile private var running = true
    @Volatile private var serverSocket: ServerSocket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _statsFlow = kotlinx.coroutines.flow.MutableStateFlow(Stats())
    val statsFlow: kotlinx.coroutines.flow.StateFlow<Stats> = _statsFlow

    private val failWindow = ConcurrentHashMap<String, MutableList<Long>>()
    private val tempBlockedUntil = ConcurrentHashMap<String, Long>()

    suspend fun serve() = withContext(Dispatchers.IO) {
        ServerSocket().use { ss ->
            serverSocket = ss
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(listenIp, port))
            onState("监听 ${listenIp.hostAddress}:$port")
            while (running) {
                val client = try { ss.accept() } catch (e: SocketException) {
                    if (!running) null else null
                } ?: continue

                val srcIp = client.inetAddress.hostAddress

                // ACL 检查
                if (denyList?.let { IpAcl.anyMatch(srcIp, it) } == true ||
                    (allowList != null && !IpAcl.anyMatch(srcIp, allowList))) {
                    try { client.close() } catch (_: Exception) {}
                    onState("拒绝：$srcIp 不满足 ACL")
                    continue
                }

                // 临时拉黑检查
                val now = System.currentTimeMillis()
                val blocked = tempBlockedUntil[srcIp]?.let { it > now } == true
                if (blocked) {
                    try { client.close() } catch (_: Exception) {}
                    onState("拒绝：$srcIp 处于临时封禁")
                    continue
                }

                // 并发上限控制
                if (_statsFlow.value.activeSessions >= maxSessions) {
                    try { client.close() } catch (_: Exception) {}
                    onState("达到最大会话数 $maxSessions，拒绝新连接")
                    continue
                }

                scope.launch {
                    updateStats(activeDelta = +1, sessionDelta = +1, bytesDelta = 0)
                    val bytes = try {
                        Socks5Session(
                            client,
                            socketFactoryProvider,
                            auth,
                            onState,
                            onAuthFailed = { ip -> recordAuthFail(ip) },
                            udpStarter = { bndAddr, bndPort ->
                                val udp = UdpAssociate(bndAddr, onState = onState, bindCellular = bindDatagramCellular)
                                val portActual = udp.start(bndPort)
                                bndAddr to portActual
                            }
                        ).handle()
                    } finally {
                        updateStats(activeDelta = -1, sessionDelta = 0, bytesDelta = 0)
                    }
                    val sum = bytes.first + bytes.second
                    updateStats(activeDelta = 0, sessionDelta = 0, bytesDelta = sum)
                }
            }
        }
        serverSocket = null
    }

    private fun recordAuthFail(ip: String) {
        val now = System.currentTimeMillis()
        val list = failWindow.computeIfAbsent(ip) { mutableListOf() }
        list.add(now)
        // 滑动窗口 60s
        val cutoff = now - 60_000
        list.removeIf { it < cutoff }
        if (list.size >= authFailThresholdPerMin) {
            tempBlockedUntil[ip] = now + tempBlockMinutes * 60_000L
            onState("$ip 鉴权失败过多，临时封禁 ${tempBlockMinutes}min")
            list.clear()
        }
    }

    private fun updateStats(activeDelta: Int, sessionDelta: Long, bytesDelta: Long) {
        val cur = _statsFlow.value
        _statsFlow.value = cur.copy(
            activeSessions = (cur.activeSessions + activeDelta).coerceAtLeast(0),
            totalSessions = cur.totalSessions + sessionDelta,
            totalBytes = cur.totalBytes + bytesDelta
        )
    }

    fun shutdown() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
    }
} 