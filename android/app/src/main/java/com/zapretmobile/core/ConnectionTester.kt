// android/app/src/main/java/com/zapretmobile/core/ConnectionTester.kt
package com.zapretmobile.core

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

object ConnectionTester {
    private const val TAG = "ConnectionTester"
    private val TEST_HOSTS = listOf(
        "dns.google" to 443,
        "cloudflare-dns.com" to 443,
        "1.1.1.1" to 53
    )

    fun testConnectivity(timeoutMs: Long = 3000): Boolean {
        for ((host, port) in TEST_HOSTS) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                socket.close()
                Log.d(TAG, "✅ Connected to $host:$port")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "❌ Failed $host:$port - ${e.message}")
            }
        }
        return false
    }

    fun testWithProxy(proxyHost: String = "127.0.0.1", proxyPort: Int = 1080): Boolean {
        // Тест через SOCKS5 прокси (упрощённо)
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 2000)
            socket.close()
            Log.d(TAG, "✅ Proxy reachable at $proxyHost:$proxyPort")
            true
        } catch (e: Exception) {
            Log.w(TAG, "❌ Proxy unreachable: ${e.message}")
            false
        }
    }
}
