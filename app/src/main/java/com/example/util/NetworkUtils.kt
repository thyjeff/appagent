package com.example.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "127.0.0.1"
    }

    fun getNetworkInterfacesSummary(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        val name = when {
                            iface.name.startsWith("wlan") -> "Wi-Fi (${iface.name})"
                            iface.name.startsWith("tailscale") -> "Tailscale VPN"
                            iface.name.startsWith("tun") || iface.name.startsWith("wg") -> "WireGuard/VPN"
                            iface.name.startsWith("rmnet") || iface.name.startsWith("ccmni") -> "Cellular"
                            else -> iface.displayName
                        }
                        list.add(Pair(name, host))
                    }
                }
            }
        } catch (ignored: Exception) {}
        return list
    }
}
