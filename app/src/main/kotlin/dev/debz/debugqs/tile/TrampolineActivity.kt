package dev.debz.debugqs.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import dev.debz.debugqs.core.AppSettings
import dev.debz.debugqs.core.Launch
import dev.debz.debugqs.core.LongPressAction
import dev.debz.debugqs.ui.PanelTarget

/**
 * Invisible hop used by the tile: a long press (QS_TILE_PREFERENCES) opens what the user chose,
 * and the dialog's links come through here so the shade collapses first.
 */
class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_TARGET)?.let { runCatching { PanelTarget.valueOf(it) }.getOrNull() }
            ?: if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
                when (AppSettings.get(this).current.longPress) {
                    LongPressAction.DeveloperOptions -> PanelTarget.DeveloperOptions
                    LongPressAction.WirelessDebugging -> PanelTarget.WirelessDebugging
                    LongPressAction.App -> PanelTarget.App
                }
            } else PanelTarget.App

        when (target) {
            PanelTarget.App -> startActivity(Launch.appIntent(this))
            PanelTarget.DeveloperOptions -> Launch.openDeveloperOptions(this)
            PanelTarget.WirelessDebugging -> Launch.openWirelessDebugging(this)
        }
        finish()
    }

    companion object {
        private const val EXTRA_TARGET = "target"

        fun intent(context: Context, target: PanelTarget): Intent =
            Intent(context, TrampolineActivity::class.java)
                .putExtra(EXTRA_TARGET, target.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
