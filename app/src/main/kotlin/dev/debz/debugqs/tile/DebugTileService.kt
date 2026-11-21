package dev.debz.debugqs.tile

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.debz.debugqs.R
import dev.debz.debugqs.core.AppSettings
import dev.debz.debugqs.core.DevState
import dev.debz.debugqs.core.DevStateMonitor
import dev.debz.debugqs.core.summary
import dev.debz.debugqs.ui.PanelTarget

/**
 * The single DebugQS tile. A tap opens [TileDialog] with all three switches; a long press goes
 * through [TrampolineActivity] to whatever the user picked in settings. The tile is lit while
 * Developer options are on, and its subtitle sums up what's on (or the `adb connect` address).
 */
class DebugTileService : TileService() {
    private var monitor: DevStateMonitor? = null
    private var dialog: Dialog? = null

    private var rendered = false

    override fun onStartListening() {
        rendered = false
        val m = monitor ?: DevStateMonitor(this) { render(it) }.also { monitor = it }
        m.start()
        render(m.state)
    }

    override fun onStopListening() {
        monitor?.stop()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { showPanel() } else showPanel()
    }

    private fun showPanel() {
        dialog?.dismiss()
        dialog = TileDialog(this) { target -> openAndCollapse(target) }.also { showDialog(it) }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAndCollapse(target: PanelTarget) {
        val intent = TrampolineActivity.intent(this, target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, target.ordinal, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: DevState) {
        val tile = qsTile ?: return
        val prefs = AppSettings.get(this).current
        val subtitle = state.wirelessAddress?.takeIf { prefs.tileShowsAddress } ?: state.summary()
        val tileState = if (state.hasPermission && state.developer) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Only push real changes: every updateTile() makes SystemUI redraw the tile.
        if (rendered && tile.subtitle == subtitle && tile.state == tileState) return
        rendered = true
        tile.label = getString(R.string.tile_label)
        tile.subtitle = subtitle
        tile.state = tileState
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        monitor?.stop()
        super.onDestroy()
    }
}
