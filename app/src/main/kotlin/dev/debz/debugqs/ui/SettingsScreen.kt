package dev.debz.debugqs.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.debz.debugqs.BuildConfig
import dev.debz.debugqs.R
import dev.debz.debugqs.core.AppSettings
import dev.debz.debugqs.core.LongPressAction
import dev.debz.debugqs.core.Privileged
import dev.debz.debugqs.core.PrivilegedState
import dev.debz.debugqs.core.Preferences
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrDivider
import dev.redfox.bedrock.components.BrRadio
import dev.redfox.bedrock.components.BrSheet
import dev.redfox.bedrock.components.BrSwitch
import dev.redfox.bedrock.components.ButtonSize
import dev.redfox.bedrock.components.ButtonVariant
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.components.SectionHeader
import dev.redfox.bedrock.components.SegmentedControl
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.theme.BedrockAccent
import dev.redfox.bedrock.theme.BedrockNeutrals
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val colors = BedrockTheme.colors
    val settings = remember { AppSettings.get(context) }
    val prefs by rememberPreferences()
    val state by rememberDevState()
    val navigator = LocalNavigator.current
    var pickLongPress by remember { mutableStateOf(false) }
    // Bumped after a permission prompt so the Wi‑Fi name row re-reads its access level.
    var permissionTick by remember { mutableIntStateOf(0) }
    val askWifiName = rememberWifiNameRequest(context) { permissionTick++ }
    val update: ((Preferences) -> Preferences) -> Unit = { settings.update(it) }

    TabScreen("Settings") {
        Section("Appearance") {
            val accent = accentFor(prefs.accent)
            ListRow(title = "Accent", subtitle = accent.name, icon = R.drawable.ic_palette)
            AccentPicker(selected = accent, onSelect = { a -> update { it.copy(accent = a.name) } })
            BrDivider(inset = BrSpacing.L)
            val neutrals = neutralsFor(prefs.neutrals)
            ListRow(title = "Background", subtitle = neutrals.name, icon = R.drawable.ic_smartphone)
            SegmentedControl(
                options = BedrockNeutrals.Presets.map { it.name },
                selectedIndex = BedrockNeutrals.Presets.indexOf(neutrals),
                onSelect = { i -> update { it.copy(neutrals = BedrockNeutrals.Presets[i].name) } },
                modifier = Modifier.padding(start = BrSpacing.L, end = BrSpacing.L, bottom = BrSpacing.L),
            )
        }

        Section("Tile") {
            ListRow(
                title = "Long press opens",
                subtitle = prefs.longPress.label,
                icon = R.drawable.ic_touch_app,
                divider = true,
                chevron = true,
                onClick = { pickLongPress = true },
            )
            SwitchRow(
                title = "Show address on the tile",
                subtitle = "ip:port as the tile's subtitle while Wireless debugging is on",
                icon = R.drawable.ic_wifi,
                checked = prefs.tileShowsAddress,
                divider = true,
                onChange = { v -> update { it.copy(tileShowsAddress = v) } },
            )
            SwitchRow(
                title = "Close after a change",
                subtitle = "Dismiss the tile's dialog once a switch flips",
                icon = R.drawable.ic_check_circle,
                checked = prefs.dismissAfterToggle,
                onChange = { v -> update { it.copy(dismissAfterToggle = v) } },
            )
        }

        Section("Behaviour") {
            SwitchRow(
                title = "Debugging follows Developer options",
                subtitle = "Turning Developer options off also turns off USB and Wireless debugging",
                icon = R.drawable.ic_shield,
                checked = prefs.debuggingFollowsDeveloper,
                onChange = { v -> update { it.copy(debuggingFollowsDeveloper = v) } },
            )
        }

        Section("Wi‑Fi name") {
            val access = remember(permissionTick, state) { context.wifiNameAccess() }
            ListRow(
                title = "Show the network name",
                subtitle = when (access) {
                    WifiNameAccess.None -> if (state.privileged == PrivilegedState.Ready) "Read through Shizuku; or allow location"
                    else "Android needs location access to reveal it"
                    WifiNameAccess.WhileInUse -> "In the app. Allow all the time for the tile"
                    WifiNameAccess.Always -> "Shown in the app and the tile"
                },
                icon = R.drawable.ic_wifi,
                trailing = if (access != WifiNameAccess.Always) {
                    { SmallButton("Allow", onClick = askWifiName) }
                } else null,
            )
        }

        Section("Access") {
            ListRow(
                title = "Secure settings",
                subtitle = if (state.hasPermission) "Granted" else "Not granted yet",
                icon = if (state.hasPermission) R.drawable.ic_check_circle else R.drawable.ic_error,
                iconTint = if (state.hasPermission) colors.accentSoft else colors.caution,
                iconContainer = if (state.hasPermission) colors.accentContainerStrong else colors.caution.copy(alpha = 0.16f),
                divider = true,
                chevron = true,
                onClick = { navigator.push(Destination.Setup) },
            )
            ListRow(
                title = if (Privileged.isSui) "Sui" else "Shizuku",
                subtitle = when (state.privileged) {
                    PrivilegedState.Unavailable -> "Not running (optional)"
                    PrivilegedState.NeedsPermission -> "Running, not allowed yet"
                    PrivilegedState.Ready -> "Connected"
                },
                icon = R.drawable.ic_bolt,
                trailing = if (state.privileged == PrivilegedState.NeedsPermission) {
                    { SmallButton("Allow", onClick = { Privileged.requestPermission() }) }
                } else null,
            )
        }

        Section("About") {
            ListRow(
                title = "DebugQS ${BuildConfig.VERSION_NAME}",
                subtitle = "Developer switches in one Quick Settings tile",
                icon = R.drawable.ic_info,
                divider = true,
            )
            ListRow(
                title = "Inspired by DevQS",
                subtitle = "github.com/louie-velarde/DevQS",
                icon = R.drawable.ic_android,
                divider = true,
            )
            ListRow(
                title = "Designed with Bedrock",
                subtitle = "github.com/Redfox-OSS/bedrock",
                icon = R.drawable.ic_palette,
            )
        }
    }

    // Always composed, so the sheet can slide away when it closes.
    BrSheet(visible = pickLongPress, onDismiss = { pickLongPress = false }, title = "Long press opens") {
        ListGroup(
            modifier = Modifier.selectableGroup(),
            shape = BrShapes.concentric(BrShapes.XLargeRadius, BrSpacing.Gutter),
            color = colors.surfaceRaised,
        ) {
            LongPressAction.entries.forEachIndexed { index, action ->
                val pick = {
                    update { it.copy(longPress = action) }
                    pickLongPress = false
                }
                ListRow(
                    title = action.label,
                    icon = when (action) {
                        LongPressAction.DeveloperOptions -> R.drawable.ic_code
                        LongPressAction.WirelessDebugging -> R.drawable.ic_terminal
                        LongPressAction.App -> R.drawable.ic_home
                    },
                    divider = index < LongPressAction.entries.lastIndex,
                    onClick = pick,
                    trailing = { BrRadio(selected = action == prefs.longPress, onClick = pick) },
                )
            }
        }
    }
}

/** A titled group of settings rows on one card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionHeader(title)
        ListGroup(content = content)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    icon: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    divider: Boolean = false,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        divider = divider,
        onClick = { onChange(!checked) },
        trailing = { BrSwitch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    BrButton(text, onClick = onClick, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
}

/** The six accents as round swatches; the chosen one gets a calm ring. */
@Composable
private fun AccentPicker(selected: BedrockAccent, onSelect: (BedrockAccent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = BrSpacing.S, end = BrSpacing.S, bottom = BrSpacing.M)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BedrockAccent.Presets.forEach { accent ->
            AccentSwatch(
                color = accent.main,
                name = accent.name,
                selected = accent == selected,
                onClick = { onSelect(accent) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One accent choice: a round swatch. When chosen, a 2dp ring eases in around it while the swatch
 * settles a little smaller inside the ring. No pop, no glow.
 */
@Composable
private fun AccentSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = BedrockTheme.colors.textPrimary
    val ring = animateFloatAsState(if (selected) 1f else 0f, BrMotion.settle(), label = "swatchRing")
    Box(
        modifier
            .height(52.dp)
            .pressable(role = Role.RadioButton, pressedScale = 0.94f, haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .semantics {
                contentDescription = "$name accent"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { alpha = ring.value }
                .border(2.dp, ringColor.copy(alpha = 0.85f), CircleShape)
        )
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    val s = 1f - 0.16f * ring.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(color)
        )
    }
}
