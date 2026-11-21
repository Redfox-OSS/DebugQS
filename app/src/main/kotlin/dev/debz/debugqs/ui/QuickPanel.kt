package dev.debz.debugqs.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.debz.debugqs.R
import dev.debz.debugqs.core.ToggleResult
import dev.debz.debugqs.core.message
import dev.debz.debugqs.core.summary
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.IconTile
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.effects.ObGlows
import dev.redfox.obsidian.effects.glow
import dev.redfox.obsidian.motion.CollapseExit
import dev.redfox.obsidian.motion.ExpandEnter
import dev.redfox.obsidian.motion.RollSwap
import dev.redfox.obsidian.theme.ObShapes
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.delay

/** Where the panel's links go; the tile collapses the shade before opening any of them. */
enum class PanelTarget { App, DeveloperOptions, WirelessDebugging }

/**
 * The tile's dialog: the three switches, the Wi‑Fi network and `adb connect` address while
 * Wireless debugging is on, and links out. Shows the one-time setup until permission is granted.
 */
@Composable
fun QuickPanel(
    onOpen: (PanelTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val state by rememberDevState()
    val prefs by rememberPreferences()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            message = null
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ObSpacing.L)
            .glow(Color.Black, ObShapes.CardLarge, ObGlows.Elevation)
            .clip(ObShapes.CardLarge)
            .background(colors.card)
            .border(1.dp, colors.borderStrong, ObShapes.CardLarge)
            .padding(ObSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(ObSpacing.L),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = R.drawable.ic_tile,
                size = 44.dp,
                background = colors.accentContainerStrong,
                tint = colors.accentLight,
            )
            Spacer(Modifier.width(ObSpacing.M))
            Column(Modifier.weight(1f)) {
                Text("DebugQS", style = ObsidianTheme.typography.title, color = colors.textPrimary)
                AnimatedContent(state.summary(), transitionSpec = { RollSwap }, label = "summary") {
                    Text(it, style = ObsidianTheme.typography.body, color = colors.textSecondary)
                }
            }
            HeaderIconButton(
                icon = R.drawable.ic_settings,
                contentDescription = "Open DebugQS",
                onClick = { onOpen(PanelTarget.App) },
                tint = colors.textSecondary,
            )
            HeaderIconButton(
                icon = R.drawable.ic_close,
                contentDescription = "Close",
                onClick = onDismiss,
                tint = colors.textSecondary,
            )
        }

        if (state.hasPermission) {
            ToggleRows(
                state = state,
                onToggle = { toggle, on ->
                    val result = context.toggle(toggle, on)
                    message = result.message()
                    if (result == ToggleResult.Ok && prefs.dismissAfterToggle) onDismiss()
                },
            )
            WirelessInfo(state, onShowName = { onOpen(PanelTarget.App) })
        } else {
            PermissionSetup(state, compact = true)
        }

        AnimatedVisibility(message != null, enter = ExpandEnter, exit = CollapseExit) {
            Text(
                message.orEmpty(),
                style = ObsidianTheme.typography.labelStrong,
                color = colors.warning,
                modifier = Modifier.padding(horizontal = ObSpacing.XXS),
            )
        }

        // Links out
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.S),
        ) {
            PillButton(
                text = "Developer options",
                icon = R.drawable.ic_open_in_new,
                onClick = { onOpen(PanelTarget.DeveloperOptions) },
            )
            if (state.wireless) {
                PillButton(
                    text = "Pair devices",
                    icon = R.drawable.ic_open_in_new,
                    onClick = { onOpen(PanelTarget.WirelessDebugging) },
                )
            }
        }
    }
}
