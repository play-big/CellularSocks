package com.example.cellularsocks.core

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class UdpAssociate(
    private val bindIp: InetAddress,
    private val natTimeoutMs: Long = 60_000,
    private val onState: (String) -> Unit = {},
    private val bindCellular: ((DatagramSocket) -> Boolean)? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null

    // NAT: clientAddr -> remoteAddr
    private val nat = mutableMapOf<InetSocketAddress, InetSocketAddress>()
    private val lastActive = mutableMapOf<InetSocketAddress, Long>()

    fun start(boundPort: Int): Int {
        val s = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(bindIp, if (boundPort > 0) boundPort else 0))
        }
        // 绑定到蜂窝网络（使外发走蜂窝）
        bindCellular?.invoke(s)
        socket = s
        scope.launch { pump(s) }
        scope.launch { gcLoop() }
        onState("UDP 监听 ${s.localAddress.hostAddress}:${s.localPort}")
        return s.localPort
    }

    private suspend fun pump(s: DatagramSocket) = withContext(Dispatchers.IO) {
        val buf = ByteArray(64 * 1024)
        val pkt = DatagramPacket(buf, buf.size)
        while (!s.isClosed) {
            try {
                s.receive(pkt)
                val src = InetSocketAddress(pkt.address, pkt.port)
                val data = pkt.data.copyOf(pkt.length)
                // 判断是否来自客户端（带 SOCKS5 UDP 包头）或远端回包（裸数据）
                if (isSocksUdpHeader(data)) {
                    val parsed = parseSocksUdp(data) ?: continue
                    nat[src] = InetSocketAddress(parsed.host, parsed.port)
                    lastActive[src] = System.currentTimeMillis()
                    // 转发到远端（裸 UDP）
                    val out = DatagramPacket(parsed.payload, parsed.payload.size, parsed.host, parsed.port)
                    s.send(out)
                } else {
                    // 视为远端回包，找到对应客户端并回送 SOCKS5 UDP 格式
                    val client = nat.entries.firstOrNull { it.value.address == pkt.address && it.value.port == pkt.port }?.key
                    if (client != null) {
                        val resp = buildSocksUdp(pkt.address, pkt.port, data)
                        val out = DatagramPacket(resp, resp.size, client.address, client.port)
                        s.send(out)
                        lastActive[client] = System.currentTimeMillis()
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private suspend fun gcLoop() {
        while (true) {
            delay(natTimeoutMs)
            val now = System.currentTimeMillis()
            val it = lastActive.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (now - e.value > natTimeoutMs) {
                    nat.remove(e.key)
                    it.remove()
                }
            }
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        scope.cancel()
        nat.clear(); lastActive.clear()
    }

    data class UdpPacket(val host: InetAddress, val port: Int, val payload: ByteArray)

    private fun isSocksUdpHeader(data: ByteArray): Boolean = data.size >= 10 && data[2] == 0x00.toByte()

    private fun parseSocksUdp(data: ByteArray): UdpPacket? {
        // RFC1928 UDP: RSV(2)=0x0000, FRAG(1)=0x00, ATYP(1), DST.ADDR, DST.PORT(2), DATA
        if (data.size < 10) return null
        val frag = data[2].toInt()
        if (frag != 0) return null // 不支持分片
        val atyp = data[3].toInt()
        var idx = 4
        val host = when (atyp) {
            0x01 -> InetAddress.getByAddress(data.copyOfRange(idx, idx + 4)).also { idx += 4 }
            0x03 -> {
                val l = data[idx].toInt() and 0xFF; idx += 1
                val name = String(data, idx, l, Charsets.UTF_8); idx += l
                InetAddress.getByName(name)
            }
            0x04 -> InetAddress.getByAddress(data.copyOfRange(idx, idx + 16)).also { idx += 16 }
            else -> return null
        }
        val port = ByteBuffer.wrap(data, idx, 2).short.toInt() and 0xFFFF; idx += 2
        val payload = data.copyOfRange(idx, data.size)
        return UdpPacket(host, port, payload)
    }

    private fun buildSocksUdp(host: InetAddress, port: Int, data: ByteArray): ByteArray {
        val addr = host.address
        val atyp: Byte = if (addr.size == 4) 0x01 else 0x04
        val header = ByteBuffer.allocate(4 + addr.size + 2)
        header.put(0x00).put(0x00).put(0x00).put(atyp)
        header.put(addr)
        header.putShort(port.toShort())
        return header.array() + data
    }
} 