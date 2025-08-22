package com.example.cellularsocks.util

import java.net.InetAddress

object IpAcl {
    fun match(ip: String, cidrOrIp: String): Boolean {
        return if (cidrOrIp.contains("/")) inCidr(ip, cidrOrIp) else ip == cidrOrIp
    }

    fun anyMatch(ip: String, list: List<String>?): Boolean {
        if (list.isNullOrEmpty()) return false
        return list.any { match(ip, it) }
    }

    private fun inCidr(ip: String, cidr: String): Boolean {
        val parts = cidr.split("/")
        if (parts.size != 2) return false
        val base = InetAddress.getByName(parts[0]).address
        val target = InetAddress.getByName(ip).address
        val prefix = parts[1].toIntOrNull() ?: return false
        var bits = prefix
        for (i in base.indices) {
            val mask = if (bits >= 8) 0xFF else if (bits <= 0) 0 else (0xFF shl (8 - bits)) and 0xFF
            if ((base[i].toInt() and mask) != (target[i].toInt() and mask)) return false
            bits -= 8
            if (bits <= 0) break
        }
        return true
    }
} 