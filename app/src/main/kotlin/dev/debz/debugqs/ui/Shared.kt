package dev.debz.debugqs.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.debz.debugqs.R
import dev.debz.debugqs.core.AppSettings
import dev.debz.debugqs.core.DevSettings
import dev.debz.debugqs.core.DevState
import dev.debz.debugqs.core.DevStateMonitor
import dev.debz.debugqs.core.DevToggle
import dev.debz.debugqs.core.Preferences
import dev.debz.debugqs.core.Privileged
import dev.debz.debugqs.core.PrivilegedState
import dev.debz.debugqs.core.title
import dev.redfox.obsidian.components.CopyPill
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObSwitch
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SwitchSize
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.CollapseExit
import dev.redfox.obsidian.motion.ExpandEnter
import dev.redfox.obsidian.platform.copyToClipboard
import dev.redfox.obsidian.theme.ObShapes
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianAccent
import dev.redfox.obsidian.theme.ObsidianNeutrals
import dev.redfox.obsidian.theme.ObsidianTheme

/** Obsidian with the accent and background the user picked. */
@Composable
fun DebugQsTheme(content: @Composable () -> Unit) {
    val prefs by rememberPreferences()
    val accent = ObsidianAccent.Presets.firstOrNull { it.name == prefs.accent } ?: ObsidianAccent.Emerald
    val neutrals = ObsidianNeutrals.Presets.firstOrNull { it.name == prefs.neutrals } ?: ObsidianNeutrals.Dark
    ObsidianTheme(accent = accent, neutrals = neutrals, content = content)
}

@Composable
fun rememberPreferences(): State<Preferences> {
    val context = LocalContext.current
    return remember { AppSettings.get(context) }.flow.collectAsState()
}

/** Live [DevState], watched while the screen (or dialog) is started; re-read on resume. */
@Composable
fun rememberDevState(): State<DevState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(DevState()) }
    DisposableEffect(lifecycleOwner) {
        val monitor = DevStateMonitor(context) { state.value = it }
        state.value = monitor.state
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { started = true; monitor.start() }
                Lifecycle.Event.ON_RESUME -> monitor.refresh()
                Lifecycle.Event.ON_STOP -> if (started) { started = false; monitor.stop() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (started) monitor.stop()
        }
    }
    return state
}

@get:DrawableRes
val DevToggle.icon: Int
    get() = when (this) {
        DevToggle.Developer -> R.drawable.ic_developer_mode
        DevToggle.Usb -> R.drawable.ic_usb
        DevToggle.Wireless -> R.drawable.ic_wifi
    }

/** Subtitle under each switch. */
fun DevState.subtitle(toggle: DevToggle): String = when (toggle) {
    DevToggle.Developer -> if (developer) "On" else "Off"
    DevToggle.Usb -> if (usb) "On" else "Off"
    DevToggle.Wireless -> when {
        wireless -> "On · " + (wifi.ssid ?: "Wi‑Fi")
        !wifi.connected -> "Needs Wi‑Fi"
        else -> "Off · " + (wifi.ssid ?: "Wi‑Fi connected")
    }
}

/** The three switches, as rows separated by hairlines. Used by the tile's dialog and the app. */
@Composable
fun ToggleRows(
    state: DevState,
    onToggle: (DevToggle, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
        DevToggle.entries.forEachIndexed { index, toggle ->
            if (index > 0) ObDivider()
            val on = state.isOn(toggle)
            ListRow(
                title = toggle.title,
                subtitle = state.subtitle(toggle),
                icon = toggle.icon,
                iconTint = if (on) ObsidianTheme.colors.accentLight else ObsidianTheme.colors.textSecondary,
                titleStyleLarge = true,
                onClick = { onToggle(toggle, !on) },
                trailing = {
                    ObSwitch(
                        checked = on,
                        onCheckedChange = { onToggle(toggle, it) },
                        size = SwitchSize.Medium,
                    )
                },
            )
        }
    }
}

/** A label/value line inside an info block. */
@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val colors = ObsidianTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = ObsidianTheme.typography.caption, color = colors.textTertiary)
            Text(
                value,
                style = ObsidianTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.size(ObSpacing.M))
            trailing()
        }
    }
}

/**
 * Network name and `ip:port` while Wireless debugging is on, with a copy pill for the
 * `adb connect` address. Folds away when Wireless debugging is off.
 */
@Composable
fun WirelessInfo(state: DevState, modifier: Modifier = Modifier, onShowName: (() -> Unit)? = null) {
    val context = LocalContext.current
    AnimatedVisibility(state.wireless, modifier, enter = ExpandEnter, exit = CollapseExit) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ObShapes.Card)
                .background(ObsidianTheme.colors.cardInner)
                .border(1.dp, ObsidianTheme.colors.border, ObShapes.Card)
                .padding(ObSpacing.L),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.M),
        ) {
            InfoLine(
                label = "Connected to",
                value = when {
                    !state.wifi.connected -> "No Wi‑Fi"
                    state.wifi.ssid != null -> state.wifi.ssid
                    else -> "Wi‑Fi (name hidden)"
                },
                trailing = if (state.wifi.connected && state.wifi.ssid == null && onShowName != null) {
                    { PillButton("Show name", onClick = onShowName) }
                } else null,
            )
            val address = state.wirelessAddress
            if (address != null) {
                Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
                    Text("ADB ADDRESS", style = ObsidianTheme.typography.caption, color = ObsidianTheme.colors.textTertiary)
                    CopyPill(value = address, onCopy = { v -> context.copyToClipboard("ADB address", v) }, label = "Copy")
                }
            } else {
                InfoLine(
                    label = "ADB address",
                    value = state.wifi.ipv4?.let { "$it · finding port…" } ?: "Waiting for network…",
                )
            }
        }
    }
}

/** A shell command in a monospace, sideways-scrolling box. */
@Composable
fun CommandBox(command: String, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(ObShapes.Small)
            .background(colors.cardInner)
            .border(1.dp, colors.border, ObShapes.Small)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ObSpacing.M, vertical = ObSpacing.M),
    ) {
        Text(
            command,
            style = ObsidianTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            color = colors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** How to grant WRITE_SECURE_SETTINGS: the ADB command, or Shizuku / Sui when it's running. */
@Composable
fun PermissionSetup(state: DevState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val command = remember { DevSettings.grantCommand(context) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
        Text(
            if (compact) "One-time setup: run this from a computer with USB debugging on."
            else "DebugQS changes protected system settings, so Android needs to grant it " +
                "WRITE_SECURE_SETTINGS once. Connect the phone to a computer with USB debugging on, then run:",
            style = ObsidianTheme.typography.body,
            color = colors.textSecondary,
        )
        CommandBox(command)
        Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), verticalAlignment = Alignment.CenterVertically) {
            CopyButton(command, label = "Copy command")
            if (state.privileged != PrivilegedState.Unavailable) {
                PillButton(
                    text = if (Privileged.isSui) "Grant with Sui" else "Grant with Shizuku",
                    icon = R.drawable.ic_bolt,
                    onClick = { Privileged.requestPermission() },
                    containerColor = colors.accentContainerStrong,
                    contentColor = colors.accentLight,
                )
            }
        }
    }
}

/** A small status dot: accent when on, muted when off. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(modifier.size(8.dp).clip(CircleShape).background(if (on) colors.accent else colors.textDisabled))
}

fun Context.toggle(toggle: DevToggle, on: Boolean) = DevSettings.set(this, toggle, on)

/** Small "Copy" pill for long values (CopyPill would repeat the whole value); flips to "Copied". */
@Composable
fun CopyButton(value: String, label: String = "Copy") {
    val context = LocalContext.current
    var copied by remember(value) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_800)
            copied = false
        }
    }
    PillButton(
        text = if (copied) "Copied" else label,
        icon = if (copied) R.drawable.ic_check else R.drawable.ic_content_copy,
        onClick = {
            context.copyToClipboard("Command", value)
            copied = true
        },
        containerColor = if (copied) ObsidianTheme.colors.accentContainerStrong else ObsidianTheme.colors.tile,
        contentColor = if (copied) ObsidianTheme.colors.accentLight else ObsidianTheme.colors.textPrimary,
    )
}

/**
 * Obsidian's list row (icon tile, title, subtitle, trailing slot), but the subtitle wraps instead
 * of being cut to one line, so settings descriptions can always be read in full.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = ObsidianTheme.colors.textSecondary,
    @Suppress("UNUSED_PARAMETER") titleStyleLarge: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    val colors = ObsidianTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.985f, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            dev.redfox.obsidian.components.IconTile(icon = icon, tint = iconTint)
            Spacer(Modifier.size(ObSpacing.M))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ObsidianTheme.typography.bodyLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = ObsidianTheme.typography.body, color = colors.textSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(ObSpacing.M))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}
