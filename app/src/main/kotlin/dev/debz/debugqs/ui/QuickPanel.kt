package dev.debz.debugqs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.debz.debugqs.R
import dev.debz.debugqs.core.ToggleResult
import dev.debz.debugqs.core.message
import dev.debz.debugqs.core.summary
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.ButtonSize
import dev.redfox.bedrock.components.ButtonVariant
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.effects.floatShadow
import dev.redfox.bedrock.effects.glass
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.SwapText
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
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
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val state by rememberDevState()
    val prefs by rememberPreferences()
    var message by remember { mutableStateOf<String?>(null) }
    // Blocks inside the panel follow its corners, inset by its padding.
    val inner = BrShapes.concentric(BrShapes.XLargeRadius, BrSpacing.Gutter)

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            message = null
        }
    }

    // A floating surface, so it takes Bedrock's one neutral shadow. No haze in this window:
    // glass falls back to a solid surface with the lit rim, like Bedrock's own dialogs.
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BrSpacing.L)
            .floatShadow(BrShapes.XLarge)
            .glass(BrShapes.XLarge, tint = colors.surface)
            .padding(BrSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(BrSpacing.L),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                painterResource(R.drawable.ic_tile),
                tint = colors.accentSoft,
                container = colors.accentContainerStrong,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(BrSpacing.M))
            Column(Modifier.weight(1f)) {
                Text("DebugQS", style = type.headline, color = colors.textPrimary)
                SwapText(state.summary(), type.body, colors.textSecondary)
            }
            BrIconButton(
                painterResource(R.drawable.ic_settings),
                contentDescription = "Open DebugQS",
                onClick = { onOpen(PanelTarget.App) },
                variant = IconButtonVariant.Plain,
                tint = colors.textSecondary,
            )
            BrIconButton(
                painterResource(R.drawable.ic_close),
                contentDescription = "Close",
                onClick = onDismiss,
                variant = IconButtonVariant.Plain,
                tint = colors.textSecondary,
            )
        }

        if (state.hasPermission) {
            ListGroup(shape = inner, color = colors.surfaceRaised) {
                ToggleRows(
                    state = state,
                    onToggle = { toggle, on ->
                        val result = context.toggle(toggle, on)
                        message = result.message()
                        if (result == ToggleResult.Ok && prefs.dismissAfterToggle) onDismiss()
                    },
                )
            }
            WirelessInfo(
                state,
                container = colors.surfaceRaised,
                shape = inner,
                onShowName = { onOpen(PanelTarget.App) },
            )
        } else {
            PermissionSetup(state, compact = true)
        }

        AnimatedVisibility(message != null, enter = BrTransitions.expandIn, exit = BrTransitions.collapseOut) {
            Text(
                message.orEmpty(),
                style = type.label,
                color = colors.caution,
                modifier = Modifier.padding(horizontal = BrSpacing.XXS),
            )
        }

        // Links out
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
            verticalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            BrButton(
                text = "Developer options",
                onClick = { onOpen(PanelTarget.DeveloperOptions) },
                variant = ButtonVariant.Tonal,
                size = ButtonSize.Small,
                icon = painterResource(R.drawable.ic_open_in_new),
            )
            if (state.wireless) {
                BrButton(
                    text = "Pair devices",
                    onClick = { onOpen(PanelTarget.WirelessDebugging) },
                    variant = ButtonVariant.Tonal,
                    size = ButtonSize.Small,
                    icon = painterResource(R.drawable.ic_open_in_new),
                )
            }
        }
    }
}
