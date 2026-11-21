package dev.debz.debugqs.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network as AndroidNetwork
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.util.concurrent.Executors

/** The Wi‑Fi network the phone is on, as far as the app is allowed to know. */
data class WifiState(
    val connected: Boolean = false,
    /** Null when unknown: the name needs location access (or Shizuku). */
    val ssid: String? = null,
    val ipv4: String? = null,
)

object Network {
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    internal fun ipv4(lp: LinkProperties?): String? =
        lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress

    internal fun cleanSsid(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == WifiManagerUnknownSsid) return null
        return raw.removeSurrounding("\"").takeIf { it.isNotBlank() }
    }

    private const val WifiManagerUnknownSsid = "<unknown ssid>"
    private val SSID_PATTERN = Regex("""connected to "(.+?)"""")

    /** `cmd wifi status` through Shizuku: the SSID without location access. Blocking. */
    internal fun ssidViaShell(): String? =
        Privileged.runCommand("cmd", "wifi", "status")?.let { SSID_PATTERN.find(it)?.groupValues?.get(1) }
}

/** Follows the current Wi‑Fi network: connected or not, its name and the phone's IPv4 on it. */
class WifiMonitor(context: Context, private val onChange: (WifiState) -> Unit) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val shell = Executors.newSingleThreadExecutor()

    private var state = WifiState()
    private var current: AndroidNetwork? = null
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: AndroidNetwork, caps: NetworkCapabilities) {
            current = network
            val ssid = Network.cleanSsid((caps.transportInfo as? WifiInfo)?.ssid)
            post(state.copy(connected = true, ssid = ssid ?: state.ssid))
            if (ssid == null && Privileged.state.value == PrivilegedState.Ready) {
                shell.execute {
                    val viaShell = Network.ssidViaShell()
                    if (viaShell != null) main.post { if (current == network) post(state.copy(ssid = viaShell)) }
                }
            }
        }

        override fun onLinkPropertiesChanged(network: AndroidNetwork, lp: LinkProperties) {
            current = network
            post(state.copy(connected = true, ipv4 = Network.ipv4(lp)))
        }

        override fun onLost(network: AndroidNetwork) {
            if (network == current) {
                current = null
                post(WifiState())
            }
        }
    }

    private fun post(next: WifiState) {
        main.post {
            if (next != state) {
                state = next
                onChange(next)
            }
        }
    }

    fun start() {
        if (registered || cm == null) return
        registered = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, callback, main)
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}

/** One advertised wireless ADB endpoint. */
data class AdbEndpoint(val host: String, val port: Int)

/**
 * Finds the port Wireless debugging listens on, the way DevQS does: browse for the
 * `_adb-tls-connect._tcp` service. Every resolved endpoint is kept, so the caller can match it
 * against this phone's Wi‑Fi address whenever either one becomes known.
 */
class AdbPortMonitor(context: Context, private val onChange: (List<AdbEndpoint>) -> Unit) :
    NsdManager.DiscoveryListener {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var discovering = false
    private val endpoints = linkedMapOf<String, AdbEndpoint>()

    fun start() {
        if (discovering || nsd == null) return
        discovering = true
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this) }
            .onFailure { discovering = false }
    }

    fun stop() {
        if (!discovering) return
        discovering = false
        runCatching { nsd?.stopServiceDiscovery(this) }
        main.post {
            endpoints.clear()
            onChange(emptyList())
        }
    }

    override fun onServiceFound(info: NsdServiceInfo) = resolve(info, attempt = 0)

    private fun resolve(info: NsdServiceInfo, attempt: Int) {
        @Suppress("DEPRECATION")
        nsd?.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = resolved.host?.hostAddress ?: return
                main.post {
                    endpoints[resolved.serviceName] = AdbEndpoint(host, resolved.port)
                    onChange(endpoints.values.toList())
                }
            }

            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                // Only one resolve may run at a time (per process on older Androids): try again shortly.
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && attempt < 8 && discovering) {
                    main.postDelayed({ resolve(info, attempt + 1) }, 250L * (attempt + 1))
                }
            }
        })
    }

    override fun onServiceLost(info: NsdServiceInfo) {
        main.post {
            if (endpoints.remove(info.serviceName) != null) onChange(endpoints.values.toList())
        }
    }

    override fun onDiscoveryStarted(serviceType: String) = Unit
    override fun onDiscoveryStopped(serviceType: String) = Unit
    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        discovering = false
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

    private companion object {
        const val SERVICE_TYPE = "_adb-tls-connect._tcp"
    }
}
