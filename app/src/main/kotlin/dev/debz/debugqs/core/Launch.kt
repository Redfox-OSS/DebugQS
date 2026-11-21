package dev.debz.debugqs.core

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.debz.debugqs.ui.MainActivity

/** Intents for the screens DebugQS links to. */
object Launch {
    private const val SETTINGS_PKG = "com.android.settings"

    private fun Intent.newTask() = addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
    )

    fun appIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Developer options if they're on, otherwise this app's details page (as DevQS does). */
    fun openDeveloperOptions(context: Context) {
        if (DevSettings.isOn(context, DevToggle.Developer) &&
            tryStart(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).newTask())
        ) return
        tryStart(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).newTask()
        )
    }

    /** True when the Wireless debugging page can be opened directly (needs Shizuku / Sui). */
    fun canOpenWirelessDebugging(): Boolean = Privileged.state.value == PrivilegedState.Ready

    /**
     * The Wireless debugging page (pairing codes, paired devices). Android has no public intent
     * for it, so it's started through Shizuku / Sui; otherwise Developer options opens instead.
     */
    fun openWirelessDebugging(context: Context) {
        if (canOpenWirelessDebugging()) {
            try {
                val res = context.packageManager.getResourcesForApplication(SETTINGS_PKG)
                @SuppressLint("DiscouragedApi")
                val titleRes = res.getIdentifier("adb_wireless_settings", "string", SETTINGS_PKG)
                Privileged.execute(
                    "am", "start",
                    "-n", "$SETTINGS_PKG/$SETTINGS_PKG.SubSettings",
                    "--es", ":settings:show_fragment", "$SETTINGS_PKG.development.WirelessDebuggingFragment",
                    "--ei", ":settings:show_fragment_title_resid", titleRes.toString(),
                    "--activity-clear-task", "--activity-task-on-home",
                )
                return
            } catch (_: Exception) {
            }
        }
        openDeveloperOptions(context)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
