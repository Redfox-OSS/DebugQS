package dev.debz.debugqs.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.util.concurrent.Executors

/** Whether Shizuku / Sui is running, and whether it has let us in. */
enum class PrivilegedState { Unavailable, NeedsPermission, Ready }

/**
 * Shizuku / Sui access. With it the app can grant itself WRITE_SECURE_SETTINGS, hide the
 * "Developer options are not available" screen, and open the Wireless debugging page directly
 * (all the same tricks DevQS uses).
 */
object Privileged {
    private const val TAG = "DebugQS"
    private const val REQUEST_CODE = 748

    private val worker = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(PrivilegedState.Unavailable)
    val state: StateFlow<PrivilegedState> = _state.asStateFlow()

    val isSui: Boolean get() = runCatching { Sui.isSui() }.getOrDefault(false)

    private lateinit var appContext: Context

    fun start(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky {
            refresh()
            grantSelfIfPossible()
        }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { requestCode, result ->
            if (requestCode == REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) {
                grantSelfIfPossible()
            }
            refresh()
        }
    }

    fun refresh() {
        _state.value = when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> PrivilegedState.Unavailable
            Shizuku.isPreV11() -> PrivilegedState.Unavailable
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> PrivilegedState.Ready
            else -> PrivilegedState.NeedsPermission
        }
    }

    /** Asks Shizuku / Sui for access; on success the app grants itself WRITE_SECURE_SETTINGS. */
    fun requestPermission() {
        refresh()
        when (_state.value) {
            PrivilegedState.NeedsPermission -> Shizuku.requestPermission(REQUEST_CODE)
            PrivilegedState.Ready -> grantSelfIfPossible()
            PrivilegedState.Unavailable -> Unit
        }
    }

    private fun grantSelfIfPossible() {
        if (_state.value != PrivilegedState.Ready) return
        if (DevSettings.hasPermission(appContext)) return
        execute("pm", "grant", appContext.packageName, Manifest.permission.WRITE_SECURE_SETTINGS) {
            DevSettings.notifyPermissionMaybeChanged()
        }
    }

    /** Runs a shell command as shell/root through Shizuku, off the main thread. */
    fun execute(vararg command: String, then: (() -> Unit)? = null) {
        if (_state.value != PrivilegedState.Ready) return
        worker.execute {
            runCommand(*command)
            then?.invoke()
        }
    }

    /** Blocking; returns stdout, or null if Shizuku isn't ready. */
    fun runCommand(vararg command: String): String? {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null
        return try {
            @Suppress("DEPRECATION")
            val process = Shizuku.newProcess(command, null, null)
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            out
        } catch (e: Exception) {
            Log.e(TAG, "Unable to execute ${command.joinToString(" ")}", e)
            null
        }
    }
}
