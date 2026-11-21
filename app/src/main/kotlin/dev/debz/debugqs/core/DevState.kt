package dev.debz.debugqs.core

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Everything the tile, its dialog and the app show. */
data class DevState(
    val hasPermission: Boolean = false,
    val developer: Boolean = false,
    val usb: Boolean = false,
    val wireless: Boolean = false,
    val wifi: WifiState = WifiState(),
    val adbPort: Int? = null,
    val privileged: PrivilegedState = PrivilegedState.Unavailable,
) {
    fun isOn(toggle: DevToggle): Boolean = when (toggle) {
        DevToggle.Developer -> developer
        DevToggle.Usb -> usb
        DevToggle.Wireless -> wireless
    }

    /** `192.168.1.20:37415` once Wireless debugging is on and its port has been found. */
    val wirelessAddress: String?
        get() = if (wireless && wifi.ipv4 != null && adbPort != null) "${wifi.ipv4}:$adbPort" else null
}

/**
 * Watches the three settings, the Wi‑Fi network and the wireless ADB port, and reports a fresh
 * [DevState] on the main thread whenever any of them changes. Call [start] / [stop] with the
 * lifetime of whatever shows the state (the tile while listening, a dialog, a screen).
 */
class DevStateMonitor(context: Context, private val onChange: (DevState) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null

    var state: DevState = read(DevState())
        private set

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private val wifiMonitor = WifiMonitor(appContext) { wifi ->
        update(state.copy(wifi = wifi))
    }
    private var endpoints: List<AdbEndpoint> = emptyList()
    private val portMonitor = AdbPortMonitor(appContext) { found ->
        endpoints = found
        update(state)
    }

    fun start() {
        val resolver = appContext.contentResolver
        DevToggle.entries.forEach {
            resolver.registerContentObserver(Settings.Global.getUriFor(it.key), false, observer)
        }
        wifiMonitor.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { Privileged.state.collect { refresh() } }
            s.launch { DevSettings.permissionTick.drop(1).collect { refresh() } }
        }
        refresh()
    }

    fun stop() {
        appContext.contentResolver.unregisterContentObserver(observer)
        wifiMonitor.stop()
        portMonitor.stop()
        scope?.cancel()
        scope = null
    }

    /** Re-reads everything that has no callback (call when coming back to the foreground). */
    fun refresh() {
        Privileged.refresh()
        // The Wi‑Fi name may have just become visible (location access granted): ask again.
        if (scope != null && state.wifi.connected && state.wifi.ssid == null) {
            wifiMonitor.stop()
            wifiMonitor.start()
        }
        update(read(state))
    }

    private fun read(base: DevState) = base.copy(
        hasPermission = DevSettings.hasPermission(appContext),
        developer = DevSettings.isOn(appContext, DevToggle.Developer),
        usb = DevSettings.isOn(appContext, DevToggle.Usb),
        wireless = DevSettings.isOn(appContext, DevToggle.Wireless),
        privileged = Privileged.state.value,
    )

    private fun update(next: DevState) {
        // The endpoint advertised from this phone's Wi‑Fi address; with a single one, that one.
        val port = if (!next.wireless) null else
            (endpoints.firstOrNull { it.host == next.wifi.ipv4 } ?: endpoints.singleOrNull())?.port
        val fixed = next.copy(adbPort = port)
        if (fixed.wireless && scope != null) portMonitor.start() else portMonitor.stop()
        if (fixed != state) {
            state = fixed
            onChange(fixed)
        }
    }
}
