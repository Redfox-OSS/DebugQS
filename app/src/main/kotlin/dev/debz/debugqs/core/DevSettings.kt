package dev.debz.debugqs.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The three developer switches DebugQS controls. */
enum class DevToggle(val key: String) {
    Developer(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
    Usb(Settings.Global.ADB_ENABLED),
    Wireless("adb_wifi_enabled"),
}

/** Why a switch couldn't flip. */
enum class ToggleResult { Ok, NoPermission, NeedsWifi, Failed }

/**
 * Reads and writes the global settings behind Developer options, USB debugging and Wireless
 * debugging. Writing needs WRITE_SECURE_SETTINGS, granted once over ADB or by Shizuku / Sui.
 */
object DevSettings {
    private const val SETTINGS_PKG = "com.android.settings"
    private const val DEV_ACTIVITY = "$SETTINGS_PKG/.Settings\$DevelopmentSettings%sActivity"
    private val ALLOWED_ACTIVITY = DEV_ACTIVITY.format("Dashboard")
    private val BLOCKED_ACTIVITY = DEV_ACTIVITY.format("Disabled")

    /** Bumped whenever the permission might have changed, so observers re-check it. */
    private val _permissionTick = MutableStateFlow(0)
    val permissionTick: StateFlow<Int> = _permissionTick

    fun notifyPermissionMaybeChanged() {
        _permissionTick.value++
    }

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    fun hasPermission(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        ) return true
        // Seen on One UI: after USB debugging goes off and on again, a long-lived process keeps
        // reporting "denied" although the grant is back. Ask the settings provider itself by
        // writing a setting's current value back (a no-op when allowed, SecurityException when not).
        return canWriteProbe(context)
    }

    private fun canWriteProbe(context: Context): Boolean = try {
        val key = DevToggle.Developer.key
        val current = Settings.Global.getString(context.contentResolver, key) ?: return false
        Settings.Global.putString(context.contentResolver, key, current)
        true
    } catch (_: SecurityException) {
        false
    }

    fun isOn(context: Context, toggle: DevToggle): Boolean =
        Settings.Global.getString(context.contentResolver, toggle.key) == "1"

    fun set(context: Context, toggle: DevToggle, on: Boolean): ToggleResult {
        if (!hasPermission(context)) return ToggleResult.NoPermission
        if (toggle == DevToggle.Wireless && on && !Network.isWifiConnected(context)) {
            return ToggleResult.NeedsWifi
        }
        if (!put(context, toggle, on)) return ToggleResult.Failed

        if (toggle == DevToggle.Developer) {
            swapDeveloperActivities(on)
            if (!on && AppSettings.get(context).debuggingFollowsDeveloper) {
                put(context, DevToggle.Usb, false)
                put(context, DevToggle.Wireless, false)
            }
        }
        return ToggleResult.Ok
    }

    private fun put(context: Context, toggle: DevToggle, on: Boolean): Boolean = try {
        Settings.Global.putString(context.contentResolver, toggle.key, if (on) "1" else "0")
    } catch (_: SecurityException) {
        false
    }

    /**
     * With Shizuku / Sui, swaps which Settings activity answers "Developer options", so the page
     * opens normally after re-enabling without visiting About phone (what DevQS does too).
     */
    private fun swapDeveloperActivities(on: Boolean) {
        if (on) {
            Privileged.execute("pm", "disable", BLOCKED_ACTIVITY)
            Privileged.execute("pm", "enable", ALLOWED_ACTIVITY)
        } else {
            Privileged.execute("pm", "disable", ALLOWED_ACTIVITY)
            Privileged.execute("pm", "enable", BLOCKED_ACTIVITY)
        }
    }
}
