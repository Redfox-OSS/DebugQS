package dev.debz.debugqs.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.debz.debugqs.R
import dev.debz.debugqs.core.Launch
import dev.debz.debugqs.core.message
import dev.debz.debugqs.tile.DebugTileService
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.components.SnackbarResult
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val state by rememberDevState()
    val prefs by rememberPreferences()
    val askWifiName = rememberWifiNameRequest(context) {}
    val tileLabel = stringResource(R.string.tile_label)

    TabScreen("DebugQS") {
        if (!state.hasPermission) {
            ObCard(
                modifier = Modifier.fillMaxWidth(),
                color = colors.accentContainer,
                borderColor = colors.accentBorder,
                onClick = { navigator.push(Destination.Setup) },
            ) {
                ListRow(
                    title = "Finish setup",
                    subtitle = "Grant one permission so DebugQS can flip the switches",
                    icon = R.drawable.ic_lock_open,
                    iconTint = colors.accentLight,
                    titleStyleLarge = true,
                    trailing = { PillButton("Set up", onClick = { navigator.push(Destination.Setup) }) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
            SectionLabel("Switches")
            ObCard(Modifier.fillMaxWidth()) {
                ToggleRows(
                    state = state,
                    onToggle = { toggle, on ->
                        val result = context.toggle(toggle, on)
                        result.message()?.let { msg ->
                            scope.launch {
                                val action = if (!state.hasPermission) "Set up" else null
                                val r = snackbars.show(msg, action)
                                if (action != null && r == SnackbarResult.ActionPerformed) {
                                    navigator.push(Destination.Setup)
                                }
                            }
                        }
                    },
                )
            }
            WirelessInfo(state, onShowName = askWifiName)
        }

        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
            SectionLabel("Quick Settings tile")
            ObCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
                    ListRow(
                        title = "Add the tile",
                        subtitle = "Tap it to open all three switches",
                        icon = R.drawable.ic_add_box,
                        titleStyleLarge = true,
                        trailing = {
                            PillButton("Add", onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                        ComponentName(context, DebugTileService::class.java),
                                        tileLabel,
                                        Icon.createWithResource(context, R.drawable.ic_tile),
                                        context.mainExecutor,
                                    ) { }
                                } else {
                                    scope.launch {
                                        snackbars.show("Edit Quick Settings and drag the DebugQS tile in")
                                    }
                                }
                            })
                        },
                    )
                    ObDivider()
                    ListRow(
                        title = "Long press opens",
                        subtitle = prefs.longPress.label,
                        icon = R.drawable.ic_touch_app,
                        titleStyleLarge = true,
                        onClick = { navigator.select(Tab.Settings) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
            SectionLabel("Shortcuts")
            ObCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
                    ListRow(
                        title = "Developer options",
                        subtitle = if (state.developer) "Open the system page" else "Turn them on to open the page",
                        icon = R.drawable.ic_code,
                        titleStyleLarge = true,
                        onClick = { Launch.openDeveloperOptions(context) },
                    )
                    ObDivider()
                    ListRow(
                        title = "Wireless debugging",
                        subtitle = if (Launch.canOpenWirelessDebugging()) "Pairing codes and paired devices"
                        else "Opens directly with Shizuku or Sui",
                        icon = R.drawable.ic_terminal,
                        titleStyleLarge = true,
                        onClick = { Launch.openWirelessDebugging(context) },
                    )
                }
            }
        }
    }
}
