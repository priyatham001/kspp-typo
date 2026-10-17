package com.example.usb

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

private const val TAG = "NetworkDiscoveryHelper"
private const val COMPANION_PORT = 8989

class NetworkDiscoveryHelper(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    suspend fun discoverServers(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val servers = mutableMapOf<String, DiscoveredServer>()

        var multicastLock: WifiManager.MulticastLock? = null
        try {
            multicastLock = wifiManager?.createMulticastLock("replica_kspp_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.d(TAG, "MulticastLock notice: ${e.message}")
        }

        try {
            // 1. Probe Localhost (127.0.0.1) for ADB reverse tunnel
            val localhostServer = probeHost("127.0.0.1", COMPANION_PORT, NetworkConnectionType.USB_ADB)
            if (localhostServer != null) {
                servers["127.0.0.1"] = localhostServer
            }

            // 2. Discover local network interfaces (Wi-Fi, USB Tethering, Hotspot)
            val candidateHosts = mutableListOf<Pair<String, NetworkConnectionType>>()

            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    val name = iface.name.lowercase()
                    val type = when {
                        name.contains("rndis") || name.contains("usb") || name.contains("ncm") -> NetworkConnectionType.USB_TETHER
                        name.contains("ap") || name.contains("hotspot") -> NetworkConnectionType.HOTSPOT
                        else -> NetworkConnectionType.WIFI
                    }

                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (addr.isLoopbackAddress || addr.hostAddress == null) continue
                        val ip = addr.hostAddress ?: continue
                        if (!ip.contains(".")) continue // IPv4 only

                        // Infer gateway (typically .1 in same subnet)
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            val gateway = "$prefix.1"
                            if (gateway != ip) {
                                candidateHosts.add(gateway to type)
                            }
                            // USB Tethering often assigns .2 to PC
                            if (type == NetworkConnectionType.USB_TETHER) {
                                candidateHosts.add("$prefix.2" to type)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error enumerating interfaces: ${e.message}")
            }

            // 3. UDP Broadcast Discovery
            coroutineScope {
                val udpJob = async {
                    sendUdpDiscoveryBroadcast()
                }

                // 4. Concurrently probe candidate hosts (Gateways and known interfaces)
                val probeJobs = candidateHosts.distinctBy { it.first }.map { (host, type) ->
                    async {
                        probeHost(host, COMPANION_PORT, type)
                    }
                }

                val probedResults = probeJobs.awaitAll().filterNotNull()
                for (srv in probedResults) {
                    servers[srv.host] = srv
                }

                val udpResults = udpJob.await()
                for (srv in udpResults) {
                    servers[srv.host] = srv
                }
            }

            // 5. If on Wi-Fi and nothing found yet, do a quick subnet sweep of nearby IPs
            if (servers.isEmpty() || (servers.size == 1 && servers.containsKey("127.0.0.1"))) {
                val wifiIp = getLocalWifiIp()
                if (wifiIp != null) {
                    val parts = wifiIp.split(".")
                    if (parts.size == 4) {
                        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                        val currentLastOctet = parts[3].toIntOrNull() ?: 100
                        // Sweep 10 IPs before and after our own IP + low numbers (1..15)
                        val sweepTargets = (1..15).plus((currentLastOctet - 10)..(currentLastOctet + 10))
                            .filter { it in 1..254 && it != currentLastOctet }
                            .distinct()

                        coroutineScope {
                            val sweepJobs = sweepTargets.map { octet ->
                                async {
                                    probeHost("$prefix.$octet", COMPANION_PORT, NetworkConnectionType.WIFI, timeoutMs = 200)
                                }
                            }
                            val sweepResults = sweepJobs.awaitAll().filterNotNull()
                            for (srv in sweepResults) {
                                servers[srv.host] = srv
                            }
                        }
                    }
                }
            }
        } finally {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error releasing MulticastLock: ${e.message}")
            }
        }

        servers.values.sortedWith(
            compareBy<DiscoveredServer> {
                when (it.connectionType) {
                    NetworkConnectionType.USB_ADB -> 0
                    NetworkConnectionType.USB_TETHER -> 1
                    NetworkConnectionType.WIFI -> 2
                    NetworkConnectionType.HOTSPOT -> 3
                    NetworkConnectionType.MANUAL_IP -> 4
                }
            }.thenBy { it.latencyMs }
        )
    }

    private fun sendUdpDiscoveryBroadcast(): List<DiscoveredServer> {
        val found = mutableListOf<DiscoveredServer>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1200
            }

            val discoverMsg = "{\"type\":\"DISCOVER\",\"client\":\"replica_kspp_android\"}"
            val data = discoverMsg.toByteArray(Charsets.UTF_8)

            // Send to global broadcast and common ports
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(data, data.size, broadcastAddr, COMPANION_PORT)
            socket.send(packet)

            // Also send on port 8988 just in case
            val packet8988 = DatagramPacket(data, data.size, broadcastAddr, 8988)
            socket.send(packet8988)

            val recvBuf = ByteArray(1024)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1200) {
                try {
                    socket.receive(recvPacket)
                    val host = recvPacket.address.hostAddress ?: continue
                    val latency = System.currentTimeMillis() - startTime
                    val srv = DiscoveredServer(
                        name = "replica_kspp-server ($host)",
                        host = host,
                        port = COMPANION_PORT,
                        latencyMs = latency.coerceAtLeast(1L),
                        connectionType = NetworkConnectionType.WIFI
                    )
                    found.add(srv)
                } catch (_: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "UDP broadcast notice: ${e.message}")
        } finally {
            socket?.close()
        }
        return found
    }

    suspend fun probeHost(
        host: String,
        port: Int = COMPANION_PORT,
        type: NetworkConnectionType = NetworkConnectionType.WIFI,
        timeoutMs: Int = 1000
    ): DiscoveredServer? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                val name = when (type) {
                    NetworkConnectionType.USB_ADB -> "USB Companion (ADB Tunnel)"
                    NetworkConnectionType.USB_TETHER -> "USB Tethered Host ($host)"
                    NetworkConnectionType.HOTSPOT -> "Phone Hotspot Host ($host)"
                    NetworkConnectionType.WIFI -> "Wi-Fi PC Companion ($host)"
                    NetworkConnectionType.MANUAL_IP -> "Custom Host ($host)"
                }
                return@withContext DiscoveredServer(
                    name = name,
                    host = host,
                    port = port,
                    latencyMs = latency,
                    connectionType = type,
                    isReachable = true
                )
            }
        } catch (_: Exception) {
            return@withContext null
        }
    }

    private fun getLocalWifiIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (iface.name.lowercase().startsWith("wlan") && iface.isUp) {
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(".") == true) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
