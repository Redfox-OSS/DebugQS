package dev.debz.debugqs.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.ButtonSize
import dev.redfox.bedrock.components.ButtonVariant
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.components.SectionHeader
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val colors = BedrockTheme.colors
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val state by rememberDevState()
    val prefs by rememberPreferences()
    val askWifiName = rememberWifiNameRequest(context) {}
    val tileLabel = stringResource(R.string.tile_label)

    TabScreen("DebugQS") {
        if (!state.hasPermission) {
            ListGroup(color = colors.accentContainer) {
                ListRow(
                    title = "Finish setup",
                    subtitle = "Grant one permission so DebugQS can flip the switches",
                    icon = R.drawable.ic_lock_open,
                    iconTint = colors.accentSoft,
                    iconContainer = colors.accentContainerStrong,
                    onClick = { navigator.push(Destination.Setup) },
                    trailing = {
                        BrButton(
                            "Set up",
                            onClick = { navigator.push(Destination.Setup) },
                            size = ButtonSize.Small,
                        )
                    },
                )
            }
        }

        Column {
            SectionHeader("Switches")
            ListGroup {
                ToggleRows(
                    state = state,
                    onToggle = { toggle, on ->
                        val result = context.toggle(toggle, on)
                        result.message()?.let { msg ->
                            scope.launch {
                                val action = if (!state.hasPermission) "Set up" else null
                                val acted = snackbars.show(msg, action)
                                if (action != null && acted) {
                                    navigator.push(Destination.Setup)
                                }
                            }
                        }
                    },
                )
            }
            WirelessInfo(state, Modifier.padding(top = BrSpacing.M), onShowName = askWifiName)
        }

        Column {
            SectionHeader("Quick Settings tile")
            ListGroup {
                ListRow(
                    title = "Add the tile",
                    subtitle = "Tap it to open all three switches",
                    icon = R.drawable.ic_add_box,
                    divider = true,
                    trailing = {
                        BrButton(
                            "Add",
                            variant = ButtonVariant.Tonal,
                            size = ButtonSize.Small,
                            onClick = {
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
                            },
                        )
                    },
                )
                ListRow(
                    title = "Long press opens",
                    subtitle = prefs.longPress.label,
                    icon = R.drawable.ic_touch_app,
                    chevron = true,
                    onClick = { navigator.select(Tab.Settings) },
                )
            }
        }

        Column {
            SectionHeader("Shortcuts")
            ListGroup {
                ListRow(
                    title = "Developer options",
                    subtitle = if (state.developer) "Open the system page" else "Turn them on to open the page",
                    icon = R.drawable.ic_code,
                    divider = true,
                    chevron = true,
                    onClick = { Launch.openDeveloperOptions(context) },
                )
                ListRow(
                    title = "Wireless debugging",
                    subtitle = if (Launch.canOpenWirelessDebugging()) "Pairing codes and paired devices"
                    else "Opens directly with Shizuku or Sui",
                    icon = R.drawable.ic_terminal,
                    chevron = true,
                    onClick = { Launch.openWirelessDebugging(context) },
                )
            }
        }
    }
}
