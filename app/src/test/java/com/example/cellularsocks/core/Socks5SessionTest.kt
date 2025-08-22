package com.example.cellularsocks.core

import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class Socks5SessionTest {

    @Test
    fun `test method negotiation - no auth`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(0x05, 0x01, 0x00)) // VER=5, NMETHODS=1, METHOD=0
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val session = Socks5Session(
            mockSocket,
            { null },
            null,
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        assertArrayEquals(byteArrayOf(0x05, 0x00), output.toByteArray())
    }

    @Test
    fun `test method negotiation - userpass auth`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(0x05, 0x01, 0x02)) // VER=5, NMETHODS=1, METHOD=2
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val session = Socks5Session(
            mockSocket,
            { null },
            Socks5Server.Auth("user", "pass"),
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        assertArrayEquals(byteArrayOf(0x05, 0x02), output.toByteArray())
    }

    @Test
    fun `test userpass authentication - success`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(
            0x05, 0x01, 0x02, // method negotiation
            0x01, 0x04, 0x75, 0x73, 0x65, 0x72, 0x04, 0x70, 0x61, 0x73, 0x73 // auth: user/pass
        ))
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val session = Socks5Session(
            mockSocket,
            { null },
            Socks5Server.Auth("user", "pass"),
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        val response = output.toByteArray()
        assertArrayEquals(byteArrayOf(0x05, 0x02, 0x01, 0x00), response.take(4).toByteArray())
    }

    @Test
    fun `test userpass authentication - failure`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(
            0x05, 0x01, 0x02, // method negotiation
            0x01, 0x04, 0x75, 0x73, 0x65, 0x72, 0x04, 0x77, 0x72, 0x6f, 0x6e, 0x67 // auth: user/wrong
        ))
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val session = Socks5Session(
            mockSocket,
            { null },
            Socks5Server.Auth("user", "pass"),
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        val response = output.toByteArray()
        assertArrayEquals(byteArrayOf(0x05, 0x02, 0x01, 0x01), response.take(4).toByteArray())
    }

    @Test
    fun `test parse request - IPv4 connect`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(
            0x05, 0x01, 0x00, // method negotiation
            0x05, 0x01, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, 0x04, 0x38 // CONNECT 127.0.0.1:1080
        ))
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))
        `when`(mockSocket.localAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val mockFactory = mock(SocketFactory::class.java)
        val mockRemoteSocket = mock(Socket::class.java)
        `when`(mockFactory.createSocket()).thenReturn(mockRemoteSocket)

        val session = Socks5Session(
            mockSocket,
            { mockFactory },
            null,
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        val response = output.toByteArray()
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00, 0x01), response.take(4).toByteArray())
    }

    @Test
    fun `test parse request - domain connect`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(
            0x05, 0x01, 0x00, // method negotiation
            0x05, 0x01, 0x00, 0x03, 0x09, 0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x68, 0x6f, 0x73, 0x74, 0x04, 0x38 // CONNECT localhost:1080
        ))
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))
        `when`(mockSocket.localAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val mockFactory = mock(SocketFactory::class.java)
        val mockRemoteSocket = mock(Socket::class.java)
        `when`(mockFactory.createSocket()).thenReturn(mockRemoteSocket)

        val session = Socks5Session(
            mockSocket,
            { mockFactory },
            null,
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        val response = output.toByteArray()
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00, 0x01), response.take(4).toByteArray())
    }

    @Test
    fun `test unsupported command`() {
        val mockSocket = mock(Socket::class.java)
        val input = ByteArrayInputStream(byteArrayOf(
            0x05, 0x01, 0x00, // method negotiation
            0x05, 0x02, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, 0x04, 0x38 // BIND 127.0.0.1:1080
        ))
        val output = ByteArrayOutputStream()
        `when`(mockSocket.getInputStream()).thenReturn(input)
        `when`(mockSocket.getOutputStream()).thenReturn(output)
        `when`(mockSocket.inetAddress).thenReturn(InetAddress.getByName("127.0.0.1"))

        val session = Socks5Session(
            mockSocket,
            { null },
            null,
            { },
            null,
            null
        )

        runBlocking { session.handle() }
        val response = output.toByteArray()
        assertArrayEquals(byteArrayOf(0x05, 0x07, 0x00, 0x01), response.take(4).toByteArray())
    }
} 