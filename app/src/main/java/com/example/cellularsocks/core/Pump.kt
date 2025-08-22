package com.example.cellularsocks.core

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

object Pump {
    suspend fun bridge(a: Socket, b: Socket): Pair<Long, Long> = coroutineScope {
        val up = async(Dispatchers.IO) { copyCount(a.getInputStream(), b.getOutputStream()) }
        val down = async(Dispatchers.IO) { copyCount(b.getInputStream(), a.getOutputStream()) }
        launch { up.await(); safeClose(b) }
        launch { down.await(); safeClose(a) }
        val upBytes = up.await(); val downBytes = down.await()
        upBytes to downBytes
    }

    private fun copyCount(inp: InputStream, out: OutputStream): Long {
        val buf = ByteArray(DEFAULT_BUFFER)
        var total = 0L
        while (true) {
            val n = try { inp.read(buf) } catch (_: Exception) { -1 }
            if (n <= 0) break
            try { out.write(buf, 0, n); out.flush() } catch (_: Exception) { break }
            total += n
        }
        return total
    }

    private fun safeClose(s: Socket) {
        try { s.shutdownInput() } catch (_: Exception) {}
        try { s.shutdownOutput() } catch (_: Exception) {}
        try { s.close() } catch (_: Exception) {}
    }

    private const val DEFAULT_BUFFER = 64 * 1024
} 