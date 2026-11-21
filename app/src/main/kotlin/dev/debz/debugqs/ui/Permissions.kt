package dev.debz.debugqs.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** How much the app may know about the Wi‑Fi network's name. */
enum class WifiNameAccess { None, WhileInUse, Always }

fun Context.wifiNameAccess(): WifiNameAccess {
    fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return when {
        !granted(Manifest.permission.ACCESS_FINE_LOCATION) -> WifiNameAccess.None
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> WifiNameAccess.Always
        else -> WifiNameAccess.WhileInUse
    }
}

/**
 * Android only reveals the Wi‑Fi name to apps with location access. "While in use" covers the
 * app; the tile's dialog runs in the background, so it needs "Allow all the time".
 * Returns a request that asks for the next missing level.
 */
@Composable
fun rememberWifiNameRequest(context: Context, onResult: () -> Unit): () -> Unit {
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return {
        when (context.wifiNameAccess()) {
            WifiNameAccess.None -> foreground.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            WifiNameAccess.WhileInUse -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            WifiNameAccess.Always -> onResult()
        }
    }
}
