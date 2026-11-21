package dev.debz.debugqs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.debz.debugqs.BuildConfig
import dev.debz.debugqs.R
import dev.debz.debugqs.core.AppSettings
import dev.debz.debugqs.core.LongPressAction
import dev.debz.debugqs.core.Privileged
import dev.debz.debugqs.core.PrivilegedState
import dev.debz.debugqs.core.Preferences
import dev.redfox.obsidian.components.AccentSwatchRow
import dev.redfox.obsidian.components.ChoicePill
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObSheet
import dev.redfox.obsidian.components.ObSwitch
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.components.SheetAction
import dev.redfox.obsidian.components.SwitchSize
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianAccent
import dev.redfox.obsidian.theme.ObsidianNeutrals
import dev.redfox.obsidian.theme.ObsidianTheme

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
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
            Text("Accent", style = ObsidianTheme.typography.labelStrong, color = ObsidianTheme.colors.textPrimary)
            AccentSwatchRow(
                selected = ObsidianAccent.Presets.firstOrNull { it.name == prefs.accent } ?: ObsidianAccent.Emerald,
                onSelect = { a -> update { it.copy(accent = a.name) } },
            )
            ObDivider()
            Text("Background", style = ObsidianTheme.typography.labelStrong, color = ObsidianTheme.colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                ObsidianNeutrals.Presets.forEach { ramp ->
                    ChoicePill(ramp.name, selected = ramp.name == prefs.neutrals, onClick = {
                        update { it.copy(neutrals = ramp.name) }
                    })
                }
            }
        }

        Section("Tile") {
            ListRow(
                title = "Long press opens",
                subtitle = prefs.longPress.label,
                icon = R.drawable.ic_touch_app,
                titleStyleLarge = true,
                onClick = { pickLongPress = true },
            )
            ObDivider()
            SwitchRow(
                title = "Show address on the tile",
                subtitle = "ip:port as the tile's subtitle while Wireless debugging is on",
                icon = R.drawable.ic_wifi,
                checked = prefs.tileShowsAddress,
                onChange = { v -> update { it.copy(tileShowsAddress = v) } },
            )
            ObDivider()
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
                titleStyleLarge = true,
                trailing = if (access != WifiNameAccess.Always) {
                    { PillButton("Allow", onClick = askWifiName) }
                } else null,
            )
        }

        Section("Access") {
            ListRow(
                title = "Secure settings",
                subtitle = if (state.hasPermission) "Granted" else "Not granted yet",
                icon = if (state.hasPermission) R.drawable.ic_check_circle else R.drawable.ic_error,
                iconTint = if (state.hasPermission) ObsidianTheme.colors.accentLight else ObsidianTheme.colors.warning,
                titleStyleLarge = true,
                onClick = { navigator.push(Destination.Setup) },
            )
            ObDivider()
            ListRow(
                title = if (Privileged.isSui) "Sui" else "Shizuku",
                subtitle = when (state.privileged) {
                    PrivilegedState.Unavailable -> "Not running (optional)"
                    PrivilegedState.NeedsPermission -> "Running, not allowed yet"
                    PrivilegedState.Ready -> "Connected"
                },
                icon = R.drawable.ic_bolt,
                titleStyleLarge = true,
                trailing = if (state.privileged == PrivilegedState.NeedsPermission) {
                    { PillButton("Allow", onClick = { Privileged.requestPermission() }) }
                } else null,
            )
        }

        Section("About") {
            ListRow(
                title = "DebugQS ${BuildConfig.VERSION_NAME}",
                subtitle = "Developer switches in one Quick Settings tile",
                icon = R.drawable.ic_info,
                titleStyleLarge = true,
            )
            ObDivider()
            ListRow(
                title = "Inspired by DevQS",
                subtitle = "github.com/louie-velarde/DevQS",
                icon = R.drawable.ic_android,
                titleStyleLarge = true,
            )
            ObDivider()
            ListRow(
                title = "Designed with Obsidian",
                subtitle = "github.com/Redfox-OSS/obsidian",
                icon = R.drawable.ic_palette,
                titleStyleLarge = true,
            )
        }
    }

    if (pickLongPress) {
        ObSheet(onDismiss = { pickLongPress = false }, title = "Long press opens") {
            LongPressAction.entries.forEach { action ->
                SheetAction(
                    label = action.label + if (action == prefs.longPress) "  ✓" else "",
                    icon = when (action) {
                        LongPressAction.DeveloperOptions -> R.drawable.ic_code
                        LongPressAction.WirelessDebugging -> R.drawable.ic_terminal
                        LongPressAction.App -> R.drawable.ic_home
                    },
                    onClick = {
                        update { it.copy(longPress = action) }
                        pickLongPress = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
        SectionLabel(title)
        ObCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, icon: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        titleStyleLarge = true,
        onClick = { onChange(!checked) },
        trailing = { ObSwitch(checked = checked, onCheckedChange = onChange, size = SwitchSize.Medium) },
    )
}
