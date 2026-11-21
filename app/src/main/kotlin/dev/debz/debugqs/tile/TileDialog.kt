package dev.debz.debugqs.tile

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import dev.debz.debugqs.R
import dev.debz.debugqs.ui.DebugQsTheme
import dev.debz.debugqs.ui.PanelTarget
import dev.debz.debugqs.ui.QuickPanel

/**
 * The dialog the tile opens over the Quick Settings shade. A [ComponentDialog] gives the
 * ComposeView the lifecycle and saved-state owners it needs, even inside a TileService.
 */
class TileDialog(context: Context, private val onOpen: (PanelTarget) -> Unit) :
    ComponentDialog(context, R.style.Theme_DebugQS_TileDialog) {

    init {
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DebugQsTheme {
                    QuickPanel(
                        onOpen = { target ->
                            dismiss()
                            onOpen(target)
                        },
                        onDismiss = ::dismiss,
                    )
                }
            }
        }
        setContentView(view)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }
}
