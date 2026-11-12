package com.infobip.napi.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.Inet6Address
import java.net.InetAddress

actual fun createHttpClientEngine(): HttpClientEngine =
    OkHttp.create {
        config {
            followRedirects(false)
            followSslRedirects(false)
            dns(Ipv6PreferringDns)
        }
    }

private object Ipv6PreferringDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        return addresses.sortedByDescending { it is Inet6Address }
    }
}
