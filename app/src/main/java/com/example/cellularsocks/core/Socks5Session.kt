package com.example.cellularsocks.core

import com.example.cellularsocks.core.Socks5Server.Auth
import com.example.cellularsocks.core.Pump.bridge
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import javax.net.SocketFactory

class Socks5Session(
    private val client: Socket,
    private val socketFactoryProvider: () -> SocketFactory?,
    private val auth: Auth?,
    private val onState: (String) -> Unit,
    private val onAuthFailed: ((String) -> Unit)? = null,
    private val udpStarter: ((InetAddress, Int) -> Pair<InetAddress, Int>)? = null
) {
    suspend fun handle(): Pair<Long, Long> {
        client.soTimeout = 15_000
        client.tcpNoDelay = true
        client.keepAlive = true
        val inp = client.getInputStream()
        val out = client.getOutputStream()
        try {
            // METHOD NEGOTIATION
            val ver = inp.read(); if (ver != 0x05) return close()
            val nMethods = inp.read(); val methods = ByteArray(nMethods).also { inp.read(it) }
            val needAuth = auth != null
            val chosen: Byte = when {
                !needAuth && methods.contains(0x00) -> 0x00 // no auth
                needAuth && methods.contains(0x02) -> 0x02 // user/pass
                else -> 0xFF.toByte()
            }
            out.write(byteArrayOf(0x05, chosen)); out.flush()
            if (chosen == 0xFF.toByte()) return close()

            if (chosen == 0x02.toByte()) if (!handleUserPass(inp, out)) {
                onAuthFailed?.invoke(client.inetAddress.hostAddress ?: "")
                return close()
            }

            // REQUEST
            val req = parseRequest(inp) ?: return close()
            when (req.cmd) {
                0x01.toByte() -> { // CONNECT
                    val sf = socketFactoryProvider() ?: run { reply(out, 0x01); return close() }
                    val remote = (sf.createSocket() as Socket).apply {
                        soTimeout = 0
                        tcpNoDelay = true
                        keepAlive = true
                        connect(InetSocketAddress(req.host, req.port), 12_000)
                    }
                    replySuccessBound(out, client.localAddress, client.localPort)
                    onState("转发 ${client.inetAddress.hostAddress} → ${req.host}:${req.port}（蜂窝）")
                    return bridge(client, remote)
                }
                0x03.toByte() -> { // UDP ASSOCIATE
                    val starter = udpStarter ?: run { reply(out, 0x07); return close() }
                    val (bndAddr, bndPort) = starter(client.localAddress, 0)
                    // 回复 BND.ADDR/BND.PORT 给客户端
                    replySuccessBound(out, bndAddr, bndPort)
                    onState("UDP 就绪 ${bndAddr.hostAddress}:$bndPort（蜂窝）")
                    // UDP 模式下，TCP 连接保持但无字节转发
                    // 直到客户端关闭
                    while (true) {
                        val b = try { inp.read() } catch (_: Exception) { -1 }
                        if (b == -1) break
                    }
                    return 0L to 0L
                }
                else -> {
                    reply(out, 0x07) // Command not supported
                    return close()
                }
            }
        } catch (e: Exception) {
            onState("会话异常：${e.message}")
            return 0L to 0L
        } finally { try { client.close() } catch (_: Exception) {} }
    }

    private fun handleUserPass(inp: InputStream, out: OutputStream): Boolean {
        if (inp.read() != 0x01) return false
        val ulen = inp.read(); val user = ByteArray(ulen).also { inp.read(it) }.toString(Charsets.UTF_8)
        val plen = inp.read(); val pass = ByteArray(plen).also { inp.read(it) }.toString(Charsets.UTF_8)
        val ok = (auth?.username == user && auth?.password == pass)
        out.write(byteArrayOf(0x01, if (ok) 0x00 else 0x01)); out.flush()
        return ok
    }

    data class Request(val cmd: Byte, val host: String, val port: Int)

    private fun parseRequest(inp: InputStream): Request? {
        val header = ByteArray(4)
        if (inp.read(header) != 4) return null
        val cmd = header[1]
        val atyp = header[3]
        val host = when (atyp.toInt()) {
            0x01 -> InetAddress.getByAddress(inp.readNBytes(4)).hostAddress
            0x03 -> { val l = inp.read(); String(inp.readNBytes(l), Charsets.UTF_8) }
            0x04 -> InetAddress.getByAddress(inp.readNBytes(16)).hostAddress
            else -> return null
        }
        val port = ByteBuffer.wrap(inp.readNBytes(2)).short.toInt() and 0xFFFF
        return Request(cmd, host, port)
    }

    private fun reply(out: OutputStream, rep: Int) {
        out.write(byteArrayOf(0x05, rep.toByte(), 0x00, 0x01, 0,0,0,0, 0,0)); out.flush()
    }

    private fun replySuccessBound(out: OutputStream, bindAddr: InetAddress, bindPort: Int) {
        val addr = bindAddr.address
        val atyp: Byte = if (addr.size == 4) 0x01 else 0x04
        val portBytes = ByteBuffer.allocate(2).putShort(bindPort.toShort()).array()
        out.write(byteArrayOf(0x05, 0x00, 0x00, atyp))
        out.write(addr)
        out.write(portBytes)
        out.flush()
    }

    private fun close(): Pair<Long, Long> { try { client.close() } catch (_: Exception) {}; return 0L to 0L }
} 