package dev.debz.debugqs.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrSwitch
import dev.redfox.bedrock.components.ButtonSize
import dev.redfox.bedrock.components.ButtonVariant
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.theme.BedrockAccent
import dev.redfox.bedrock.theme.BedrockNeutrals
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import kotlinx.coroutines.delay

/** Bedrock with the accent and background the user picked. */
@Composable
fun DebugQsTheme(content: @Composable () -> Unit) {
    val prefs by rememberPreferences()
    BedrockTheme(accent = accentFor(prefs.accent), neutrals = neutralsFor(prefs.neutrals), content = content)
}

/**
 * The Bedrock accent for a saved name. Names saved before the move to Bedrock were Obsidian's;
 * they map to the closest Bedrock family, so an existing choice survives the update.
 */
fun accentFor(name: String): BedrockAccent =
    BedrockAccent.Presets.firstOrNull { it.name == name } ?: when (name) {
        "Crimson" -> BedrockAccent.Red
        "Electric" -> BedrockAccent.Tide
        "Solar" -> BedrockAccent.Sand
        "Luminous" -> BedrockAccent.Iris
        else -> BedrockAccent.Moss // "Emerald", the old default, and anything unknown
    }

/** The Bedrock neutral ramp for a saved name; Obsidian's names (Dark, Darker, AMOLED) map too. */
fun neutralsFor(name: String): BedrockNeutrals =
    BedrockNeutrals.Presets.firstOrNull { it.name == name } ?: when (name) {
        "Darker" -> BedrockNeutrals.Slate
        "AMOLED" -> BedrockNeutrals.Pitch
        else -> BedrockNeutrals.Basalt // "Dark", the old default, and anything unknown
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

/**
 * The three switches, as list rows separated by hairlines. Used by the tile's dialog and the app;
 * put it in a `ListGroup`. A switch that's on lights its icon tile with the accent.
 */
@Composable
fun ToggleRows(
    state: DevState,
    onToggle: (DevToggle, Boolean) -> Unit,
) {
    val colors = BedrockTheme.colors
    DevToggle.entries.forEachIndexed { index, toggle ->
        val on = state.isOn(toggle)
        ListRow(
            title = toggle.title,
            subtitle = state.subtitle(toggle),
            icon = toggle.icon,
            iconTint = if (on) colors.accentSoft else colors.textSecondary,
            iconContainer = if (on) colors.accentContainerStrong else colors.surfaceHigh,
            divider = index < DevToggle.entries.lastIndex,
            onClick = { onToggle(toggle, !on) },
            trailing = { BrSwitch(checked = on, onCheckedChange = { onToggle(toggle, it) }) },
        )
    }
}

/** A label/value line inside an info block. */
@Composable
fun InfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = BedrockTheme.typography.bodyLarge,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = BedrockTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BrSpacing.XXS)) {
            Text(label, style = BedrockTheme.typography.caption, color = colors.textSecondary)
            Text(
                value,
                style = valueStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.size(BrSpacing.M))
            trailing()
        }
    }
}

/**
 * Network name and `ip:port` while Wireless debugging is on, with a copy button for the
 * `adb connect` address. Folds away when Wireless debugging is off. [container] and [shape] let
 * it sit on the page (a card) or inside the tile's panel (a raised, concentric block).
 */
@Composable
fun WirelessInfo(
    state: DevState,
    modifier: Modifier = Modifier,
    container: Color = BedrockTheme.colors.surface,
    shape: Shape = BrShapes.Large,
    onShowName: (() -> Unit)? = null,
) {
    val colors = BedrockTheme.colors
    AnimatedVisibility(state.wireless, modifier, enter = BrTransitions.expandIn, exit = BrTransitions.collapseOut) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(container)
                .border(1.dp, colors.line, shape)
                .padding(BrSpacing.L),
            verticalArrangement = Arrangement.spacedBy(BrSpacing.L),
        ) {
            InfoLine(
                label = "Connected to",
                value = when {
                    !state.wifi.connected -> "No Wi‑Fi"
                    state.wifi.ssid != null -> state.wifi.ssid
                    else -> "Wi‑Fi (name hidden)"
                },
                trailing = if (state.wifi.connected && state.wifi.ssid == null && onShowName != null) {
                    {
                        BrButton(
                            "Show name",
                            onClick = onShowName,
                            variant = ButtonVariant.Tonal,
                            size = ButtonSize.Small,
                        )
                    }
                } else null,
            )
            val address = state.wirelessAddress
            if (address != null) {
                InfoLine(
                    label = "ADB address",
                    value = address,
                    valueStyle = BedrockTheme.typography.figure,
                    trailing = { CopyButton(address, clipLabel = "ADB address") },
                )
            } else {
                InfoLine(
                    label = "ADB address",
                    value = state.wifi.ipv4?.let { "$it · finding port…" } ?: "Waiting for network…",
                    valueStyle = BedrockTheme.typography.figure,
                )
            }
        }
    }
}

/** A shell command in a monospace, sideways-scrolling box. */
@Composable
fun CommandBox(command: String, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(BrShapes.Small)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.line, BrShapes.Small)
            .horizontalScroll(rememberScrollState())
            .padding(BrSpacing.M),
    ) {
        Text(
            command,
            style = BedrockTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
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
    val colors = BedrockTheme.colors
    val command = remember { DevSettings.grantCommand(context) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BrSpacing.M)) {
        Text(
            if (compact) "One-time setup: run this from a computer with USB debugging on."
            else "DebugQS changes protected system settings, so Android needs to grant it " +
                "WRITE_SECURE_SETTINGS once. Connect the phone to a computer with USB debugging on, then run:",
            style = BedrockTheme.typography.body,
            color = colors.textSecondary,
        )
        CommandBox(command)
        Row(horizontalArrangement = Arrangement.spacedBy(BrSpacing.S), verticalAlignment = Alignment.CenterVertically) {
            CopyButton(command, label = "Copy command")
            if (state.privileged != PrivilegedState.Unavailable) {
                BrButton(
                    text = if (Privileged.isSui) "Grant with Sui" else "Grant with Shizuku",
                    onClick = { Privileged.requestPermission() },
                    size = ButtonSize.Small,
                    icon = painterResource(R.drawable.ic_bolt),
                )
            }
        }
    }
}

/** A small status dot: accent when on, muted when off. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    Box(modifier.size(8.dp).clip(CircleShape).background(if (on) colors.accentMain else colors.textDisabled))
}

fun Context.toggle(toggle: DevToggle, on: Boolean) = DevSettings.set(this, toggle, on)

private fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** A small "Copy" button for long values (it never repeats the value); flips to "Copied". */
@Composable
fun CopyButton(value: String, label: String = "Copy", clipLabel: String = "Command") {
    val context = LocalContext.current
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }
    BrButton(
        text = if (copied) "Copied" else label,
        onClick = {
            context.copyToClipboard(clipLabel, value)
            copied = true
        },
        variant = ButtonVariant.Tonal,
        size = ButtonSize.Small,
        icon = painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_content_copy),
    )
}

/**
 * Bedrock's list row (icon tile, title, subtitle, trailing slot, hairline under the text), but
 * the subtitle wraps in full instead of being cut to two lines, so settings descriptions can
 * always be read. Stack rows in a `ListGroup`, with [divider] on all but the last.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    iconTint: Color = BedrockTheme.colors.textSecondary,
    iconContainer: Color = BedrockTheme.colors.surfaceHigh,
    divider: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val dividerColor = colors.divider
    // The hairline starts where the text starts, not under the icon.
    var textStart by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(pressedScale = 0.99f, onClick = onClick) else Modifier)
            .heightIn(min = if (subtitle != null) 64.dp else 54.dp)
            .drawBehind {
                if (divider) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(dividerColor, Offset(textStart + BrSpacing.L.toPx(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(horizontal = BrSpacing.L, vertical = BrSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.M),
    ) {
        if (icon != null) {
            IconTile(painterResource(icon), tint = iconTint, container = iconContainer)
        }
        Column(Modifier.weight(1f).onPlaced { textStart = it.positionInParent().x }) {
            Text(title, style = type.label, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.size(BrSpacing.XXS))
                Text(subtitle, style = type.caption, color = colors.textSecondary)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (chevron) {
            Icon(
                painterResource(dev.redfox.bedrock.R.drawable.br_ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
