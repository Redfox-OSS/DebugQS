package dev.debz.debugqs.ui

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.debz.debugqs.R
import dev.debz.debugqs.core.DevSettings
import dev.debz.debugqs.core.DevState
import dev.debz.debugqs.core.Launch
import dev.debz.debugqs.core.Privileged
import dev.debz.debugqs.core.PrivilegedState
import dev.debz.debugqs.core.summary
import dev.debz.debugqs.tile.DebugTileService
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrCard
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.BrRadio
import dev.redfox.bedrock.components.BrSpinner
import dev.redfox.bedrock.components.BrTag
import dev.redfox.bedrock.components.ButtonSize
import dev.redfox.bedrock.components.ButtonVariant
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.components.TagTone
import dev.redfox.bedrock.effects.aura
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.effects.rememberAura
import dev.redfox.bedrock.effects.rim
import dev.redfox.bedrock.motion.BrDurations
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.RevealColumn
import dev.redfox.bedrock.motion.RevealScope
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import kotlinx.coroutines.delay

/** The screens of the first-run setup, in order. */
enum class SetupStep { Welcome, Method, Computer, Shizuku, Tile }

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"
private const val PLATFORM_TOOLS = "https://developer.android.com/tools/releases/platform-tools"

/**
 * Guided setup: what the app does, how to grant WRITE_SECURE_SETTINGS (a computer with ADB, or
 * Shizuku), then adding the tile. Each checklist item ticks itself off as the phone's state
 * changes, and the grant is detected automatically, so the user never has to guess whether a
 * step worked. Shown on first launch, and again from "Finish setup" while the grant is missing.
 */
@Composable
fun SetupFlow(
    onFinish: () -> Unit,
    startAt: SetupStep = SetupStep.Welcome,
    onBackFromStart: (() -> Unit)? = null,
) {
    val colors = BedrockTheme.colors
    val state by rememberDevState()
    var step by rememberSaveable { mutableStateOf(startAt) }
    var forward by remember { mutableStateOf(true) }
    var method by rememberSaveable { mutableStateOf(SetupStep.Computer) }

    fun go(next: SetupStep) {
        forward = next.ordinal > step.ordinal
        step = next
    }

    val previous: SetupStep? = when (step) {
        SetupStep.Welcome -> null
        SetupStep.Method -> if (startAt == SetupStep.Method) null else SetupStep.Welcome
        SetupStep.Computer, SetupStep.Shizuku -> SetupStep.Method
        SetupStep.Tile -> SetupStep.Method
    }
    BackHandler(enabled = previous != null || onBackFromStart != null) {
        if (previous != null) go(previous) else onBackFromStart?.invoke()
    }

    // There's no broadcast for a permission grant, so check every second while waiting for one.
    val waiting = (step == SetupStep.Computer || step == SetupStep.Shizuku) && !state.hasPermission
    LaunchedEffect(waiting) {
        while (waiting) {
            delay(1_000)
            DevSettings.notifyPermissionMaybeChanged()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(
                step = step,
                onBack = when {
                    previous != null -> { { go(previous) } }
                    onBackFromStart != null -> onBackFromStart
                    else -> null
                },
                onSkip = if (step == SetupStep.Welcome || step == SetupStep.Method) onFinish else null,
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = { if (forward) BrTransitions.push() else BrTransitions.pop() },
                modifier = Modifier.weight(1f),
                label = "setupStep",
            ) { current ->
                RevealScope {
                    when (current) {
                        SetupStep.Welcome -> WelcomeStep()
                        SetupStep.Method -> MethodStep(state, method, onPick = { method = it })
                        SetupStep.Computer -> ComputerStep(state)
                        SetupStep.Shizuku -> ShizukuStep(state)
                        SetupStep.Tile -> TileStep()
                    }
                }
            }
            Box(Modifier.padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.L)) {
                val wide = Modifier.fillMaxWidth()
                when (step) {
                    SetupStep.Welcome -> BrButton("Get started", onClick = { go(SetupStep.Method) }, modifier = wide)
                    SetupStep.Method -> BrButton(
                        "Continue",
                        onClick = { go(if (state.hasPermission) SetupStep.Tile else method) },
                        modifier = wide,
                    )
                    SetupStep.Computer, SetupStep.Shizuku -> BrButton(
                        text = "Continue",
                        enabled = state.hasPermission,
                        onClick = { go(SetupStep.Tile) },
                        modifier = wide,
                    )
                    SetupStep.Tile -> BrButton("Done", onClick = onFinish, modifier = wide)
                }
            }
        }
    }
}

@Composable
private fun TopBar(step: SetupStep, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    val colors = BedrockTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = BrSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            if (onBack != null) {
                BrIconButton(
                    painterResource(dev.redfox.bedrock.R.drawable.br_ic_back),
                    contentDescription = "Back",
                    onClick = onBack,
                    variant = IconButtonVariant.Glass,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // Three dots: access, grant, tile. The welcome screen shows none.
        if (step != SetupStep.Welcome) {
            val index = when (step) {
                SetupStep.Method -> 0
                SetupStep.Computer, SetupStep.Shizuku -> 1
                else -> 2
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val color by animateColorAsState(
                        if (i <= index) colors.accentMain else colors.surfaceHigh, BrMotion.quick(), label = "dot",
                    )
                    val width by animateDpAsState(if (i == index) 22.dp else 8.dp, BrMotion.settle(), label = "dotWidth")
                    Box(
                        Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
            if (onSkip != null) {
                Text(
                    "Skip",
                    style = BedrockTheme.typography.label,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .pressable(pressedScale = 0.94f, onClick = onSkip)
                        .padding(horizontal = BrSpacing.M, vertical = BrSpacing.M),
                )
            }
        }
    }
}

/** Scrolling column for a step's content, with a big title and a short lead. */
@Composable
private fun StepPage(title: String, lead: String, content: @Composable () -> Unit) {
    RevealColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BrSpacing.Gutter)
            .padding(top = BrSpacing.L, bottom = BrSpacing.L),
        verticalArrangement = Arrangement.spacedBy(BrSpacing.L),
    ) {
        Column(
            Modifier.padding(bottom = BrSpacing.S),
            verticalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            Text(title, style = BedrockTheme.typography.title, color = BedrockTheme.colors.textPrimary)
            Text(lead, style = BedrockTheme.typography.body, color = BedrockTheme.colors.textSecondary)
        }
        content()
    }
}

// ── Welcome ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep() {
    val colors = BedrockTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = BrSpacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        // The screen's one hero: the tile's icon on a slowly drifting aura of the accent.
        Box(
            Modifier
                .revealOnEnter(0)
                .size(112.dp)
                .clip(BrShapes.XLarge)
                .aura(rememberAura(colors.accent), drift = true)
                .rim(BrShapes.XLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tile), null, tint = colors.textPrimary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(BrSpacing.XXL))
        Text(
            "DebugQS",
            style = BedrockTheme.typography.display,
            color = colors.textPrimary,
            modifier = Modifier.revealOnEnter(1),
        )
        Spacer(Modifier.height(BrSpacing.S))
        Text(
            "Developer switches, one tile away.",
            style = BedrockTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(2),
        )
        Spacer(Modifier.weight(0.8f))
        Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.XL)) {
            Feature(3, R.drawable.ic_developer_mode, "Toggle Developer options")
            Feature(4, R.drawable.ic_wifi, "Copy your wireless adb address")
            Feature(5, R.drawable.ic_lock_open, "Set up once, that's it")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Feature(index: Int, @DrawableRes icon: Int, title: String) {
    val colors = BedrockTheme.colors
    Row(Modifier.revealOnEnter(index), verticalAlignment = Alignment.CenterVertically) {
        IconTile(painterResource(icon), tint = colors.accentSoft, container = colors.accentContainer)
        Spacer(Modifier.width(BrSpacing.L))
        Text(title, style = BedrockTheme.typography.bodyLarge, color = colors.textPrimary)
    }
}

// ── Choose a method ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MethodStep(state: DevState, method: SetupStep, onPick: (SetupStep) -> Unit) {
    StepPage(
        title = "Give DebugQS access",
        lead = "Android only lets an app change developer settings with a special permission " +
            "(WRITE_SECURE_SETTINGS). You grant it once; it stays until you uninstall.",
    ) {
        if (state.hasPermission) {
            DoneBanner("Already granted", "Nothing to do here. Continue to add the tile.")
        } else {
            MethodCard(
                selected = method == SetupStep.Computer,
                icon = R.drawable.ic_computer,
                title = "With a computer",
                badge = "Recommended",
                body = "Plug the phone in and run one ADB command. About two minutes.",
                onClick = { onPick(SetupStep.Computer) },
            )
            MethodCard(
                selected = method == SetupStep.Shizuku,
                icon = R.drawable.ic_bolt,
                title = "With Shizuku",
                badge = when (state.privileged) {
                    PrivilegedState.Unavailable -> null
                    else -> "Detected"
                },
                body = "No computer needed if Shizuku (or Sui on a rooted phone) is already running.",
                onClick = { onPick(SetupStep.Shizuku) },
            )
        }
    }
}

/** One way to grant the permission. The chosen card fills with the accent container. */
@Composable
private fun MethodCard(
    selected: Boolean,
    @DrawableRes icon: Int,
    title: String,
    badge: String?,
    body: String,
    onClick: () -> Unit,
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val fill by animateColorAsState(if (selected) colors.accentContainer else colors.surface, BrMotion.quick(), label = "fill")
    val tile by animateColorAsState(if (selected) colors.accentContainerStrong else colors.surfaceHigh, BrMotion.quick(), label = "tile")
    val tint by animateColorAsState(if (selected) colors.accentSoft else colors.textSecondary, BrMotion.quick(), label = "tint")
    BrCard(Modifier.fillMaxWidth(), color = fill, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(painterResource(icon), tint = tint, container = tile, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(BrSpacing.M))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BrSpacing.XXS)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = type.label, color = colors.textPrimary)
                    if (badge != null) {
                        Spacer(Modifier.width(BrSpacing.S))
                        BrTag(badge, tone = TagTone.Accent)
                    }
                }
                Text(body, style = type.body, color = colors.textSecondary)
            }
            Spacer(Modifier.width(BrSpacing.XS))
            BrRadio(selected = selected, onClick = onClick)
        }
    }
}

// ── Grant with a computer ────────────────────────────────────────────────────────────────────────

@Composable
private fun ComputerStep(state: DevState) {
    val context = LocalContext.current
    val command = remember { DevSettings.grantCommand(context) }
    val isSamsung = remember { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }

    StepPage(
        title = "Grant with a computer",
        lead = "Each step ticks itself off. Keep this screen open: it notices the permission the moment it lands.",
    ) {
        Checklist {
            ChecklistItem(
                number = 1,
                title = "Turn on Developer options",
                done = state.developer,
                doneText = "On",
            ) {
                Hint(
                    if (isSamsung) "Settings › About phone › Software information, then tap Build number seven times."
                    else "Settings › About phone, then tap Build number seven times."
                )
                StepButton("Open About phone", R.drawable.ic_open_in_new) {
                    context.tryStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                }
            }
            ChecklistItem(
                number = 2,
                title = "Turn on USB debugging",
                done = state.usb,
                doneText = "On",
            ) {
                Hint("In Developer options, switch on USB debugging.")
                StepButton("Open Developer options", R.drawable.ic_open_in_new) {
                    Launch.openDeveloperOptions(context)
                }
            }
            ChecklistItem(
                number = 3,
                title = "Run this on your computer",
                done = state.hasPermission,
                doneText = "Granted",
            ) {
                Hint("Plug the phone in, accept “Allow USB debugging?” on the phone, then run:")
                CommandBox(command)
                Row(horizontalArrangement = Arrangement.spacedBy(BrSpacing.S), verticalAlignment = Alignment.CenterVertically) {
                    CopyButton(command)
                    StepButton("Send to computer", R.drawable.ic_share) {
                        context.tryStart(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, command),
                                "Send the command",
                            )
                        )
                    }
                }
                Hint("No adb yet? Install Android’s platform-tools on the computer first.")
                StepButton("Get platform-tools", R.drawable.ic_download) {
                    context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(PLATFORM_TOOLS)))
                }
            }
        }
        GrantStatus(state.hasPermission)
        if (!state.hasPermission) Troubleshooting(
            listOf(
                "“device unauthorized”: unplug, plug back in and accept the prompt on the phone.",
                "Xiaomi, Redmi, POCO: also turn on “USB debugging (Security settings)” in Developer options.",
                "“no devices”: try another cable or USB port, and pick File transfer in the USB notification.",
            )
        )
    }
}

// ── Grant with Shizuku ───────────────────────────────────────────────────────────────────────────

@Composable
private fun ShizukuStep(state: DevState) {
    val context = LocalContext.current
    val installed = remember(state) { context.isInstalled(SHIZUKU_PACKAGE) } || Privileged.isSui
    val running = state.privileged != PrivilegedState.Unavailable

    StepPage(
        title = "Grant with Shizuku",
        lead = "Shizuku lets apps use ADB-level access without a computer. DebugQS uses it once to grant itself the permission.",
    ) {
        Checklist {
            ChecklistItem(1, "Install Shizuku", done = installed, doneText = if (Privileged.isSui) "Sui" else "Installed") {
                Hint("Shizuku is free and open source.")
                StepButton("Get Shizuku", R.drawable.ic_download) {
                    context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)))
                }
            }
            ChecklistItem(2, "Start Shizuku", done = running, doneText = "Running") {
                Hint("Open Shizuku and start it with Wireless debugging (or root). It walks you through it.")
                if (installed) {
                    StepButton("Open Shizuku", R.drawable.ic_open_in_new) {
                        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { context.tryStart(it) }
                    }
                }
            }
            ChecklistItem(3, "Allow DebugQS", done = state.hasPermission, doneText = "Granted") {
                if (running) {
                    Hint("Shizuku asks once; DebugQS then grants itself the permission.")
                    // The step's own action, so it takes the accent.
                    BrButton(
                        "Allow",
                        onClick = { Privileged.requestPermission() },
                        size = ButtonSize.Small,
                        icon = painterResource(R.drawable.ic_bolt),
                    )
                } else {
                    Hint("Available once Shizuku is running.")
                }
            }
        }
        GrantStatus(state.hasPermission)
        Hint("Shizuku can stop after a reboot. That's fine: the permission stays granted.")
    }
}

// ── Add the tile ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TileStep() {
    val context = LocalContext.current
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val state by rememberDevState()
    val tileLabel = stringResource(R.string.tile_label)
    var requested by remember { mutableStateOf(false) }

    StepPage(
        title = "Add the tile",
        lead = "Put DebugQS in Quick Settings, so the switches are a swipe away.",
    ) {
        // A preview of the tile itself.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .padding(vertical = BrSpacing.M)
                    .clip(CircleShape)
                    .background(colors.accentContainerStrong)
                    .padding(horizontal = BrSpacing.XL, vertical = BrSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_tile), null, tint = colors.accentSoft)
                Spacer(Modifier.width(BrSpacing.M))
                Column {
                    Text("DebugQS", style = type.label, color = colors.textPrimary)
                    Text(state.summary(), style = type.body, color = colors.accentSoft)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BrButton(
                text = if (requested) "Add again" else "Add to Quick Settings",
                icon = painterResource(R.drawable.ic_add_box),
                variant = ButtonVariant.Tonal,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    requested = true
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, DebugTileService::class.java),
                        tileLabel,
                        Icon.createWithResource(context, R.drawable.ic_tile),
                        context.mainExecutor,
                    ) { }
                },
            )
        } else {
            Hint("Swipe down twice, tap the pencil to edit Quick Settings, and drag DebugQS into place.")
        }
        ListGroup {
            ListRow(
                title = "Tap",
                subtitle = "Switches, Wi‑Fi and adb address",
                icon = R.drawable.ic_touch_app,
                divider = true,
            )
            ListRow(
                title = "Long press",
                subtitle = "Developer options (you can change it)",
                icon = R.drawable.ic_settings,
            )
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun Checklist(content: @Composable () -> Unit) {
    BrCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.L)) { content() }
    }
}

/** A step's secondary action: a small tonal button with an icon. */
@Composable
private fun StepButton(text: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    BrButton(
        text,
        onClick = onClick,
        variant = ButtonVariant.Tonal,
        size = ButtonSize.Small,
        icon = painterResource(icon),
    )
}

/** A numbered step that turns into a check once [done]; its instructions fold away then. */
@Composable
private fun ChecklistItem(
    number: Int,
    title: String,
    done: Boolean,
    doneText: String,
    content: @Composable () -> Unit,
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val badge by animateColorAsState(if (done) colors.accentMain else colors.surfaceHigh, BrMotion.quick(), label = "badge")
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            AnimatedContent(
                done,
                transitionSpec = {
                    fadeIn(tween(BrDurations.Swap, easing = BrMotion.Calm)) togetherWith fadeOut(tween(BrDurations.Fade))
                },
                label = "badgeContent",
            ) { isDone ->
                if (isDone) {
                    Icon(painterResource(R.drawable.ic_check), "Done", tint = colors.onAccent, modifier = Modifier.size(18.dp))
                } else {
                    Text("$number", style = type.figure, color = colors.textPrimary)
                }
            }
        }
        Spacer(Modifier.width(BrSpacing.M))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BrSpacing.S)) {
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = type.label,
                    color = if (done) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    done,
                    enter = fadeIn(tween(BrDurations.Enter, easing = BrMotion.Calm)),
                    exit = fadeOut(tween(BrDurations.Fade)),
                ) {
                    Text(doneText, style = type.label, color = colors.accentSoft)
                }
            }
            AnimatedVisibility(!done, enter = BrTransitions.expandIn, exit = BrTransitions.collapseOut) {
                Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.S)) { content() }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = BedrockTheme.typography.body, color = BedrockTheme.colors.textSecondary)
}

/** "Waiting for permission…" with a spinner, then a "Permission granted" banner in the accent. */
@Composable
private fun GrantStatus(granted: Boolean) {
    AnimatedContent(
        granted,
        transitionSpec = { BrTransitions.swap },
        label = "grant",
    ) { ok ->
        if (ok) {
            DoneBanner("Permission granted", "DebugQS can now flip the switches.")
        } else {
            val colors = BedrockTheme.colors
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(BrShapes.Large)
                    .background(colors.surface)
                    .border(1.dp, colors.line, BrShapes.Large)
                    .padding(BrSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrSpinner(size = 20.dp)
                Spacer(Modifier.width(BrSpacing.M))
                Text("Waiting for permission…", style = BedrockTheme.typography.bodyLarge, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DoneBanner(title: String, body: String) {
    val colors = BedrockTheme.colors
    ListGroup(color = colors.accentContainer) {
        ListRow(
            title = title,
            subtitle = body,
            icon = R.drawable.ic_check_circle,
            iconTint = colors.accentSoft,
            iconContainer = colors.accentContainerStrong,
        )
    }
}

@Composable
private fun Troubleshooting(items: List<String>) {
    val colors = BedrockTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.S)) {
        Text(
            if (open) "Hide troubleshooting" else "Command not working?",
            style = BedrockTheme.typography.label,
            color = colors.accentSoft,
            modifier = Modifier
                .clip(CircleShape)
                .pressable(pressedScale = 0.95f, onClick = { open = !open })
                .padding(vertical = BrSpacing.M, horizontal = BrSpacing.XXS),
        )
        AnimatedVisibility(open, enter = BrTransitions.expandIn, exit = BrTransitions.collapseOut) {
            BrCard(Modifier.fillMaxWidth(), color = colors.surfaceRaised) {
                Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.M)) {
                    items.forEach { Hint("• $it") }
                }
            }
        }
    }
}

private fun Context.tryStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun Context.isInstalled(pkg: String): Boolean = try {
    packageManager.getPackageInfo(pkg, 0)
    true
} catch (_: Exception) {
    false
}
