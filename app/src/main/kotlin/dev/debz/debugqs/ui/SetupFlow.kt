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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import dev.redfox.obsidian.components.ActionPillButton
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObSpinner
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.PrimaryButton
import dev.redfox.obsidian.effects.ambientFloat
import dev.redfox.obsidian.effects.breathingGlow
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.CollapseExit
import dev.redfox.obsidian.motion.ExpandEnter
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.motion.RevealColumn
import dev.redfox.obsidian.motion.RevealScope
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObShapes
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
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
    val colors = ObsidianTheme.colors
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
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(ObMotion.gentle()) { dir * it / 12 })
                        .togetherWith(fadeOut(tween(120)))
                },
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
            Box(Modifier.padding(horizontal = ScreenGutter, vertical = ObSpacing.L)) {
                when (step) {
                    SetupStep.Welcome -> PrimaryButton("Get started", onClick = { go(SetupStep.Method) })
                    SetupStep.Method -> PrimaryButton(
                        "Continue",
                        onClick = { go(if (state.hasPermission) SetupStep.Tile else method) },
                    )
                    SetupStep.Computer, SetupStep.Shizuku -> PrimaryButton(
                        text = "Continue",
                        enabled = state.hasPermission,
                        onClick = { go(SetupStep.Tile) },
                    )
                    SetupStep.Tile -> PrimaryButton("Done", onClick = onFinish)
                }
            }
        }
    }
}

@Composable
private fun TopBar(step: SetupStep, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    val colors = ObsidianTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = ObSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            if (onBack != null) HeaderIconButton(dev.redfox.obsidian.R.drawable.ob_ic_back, "Back", onBack, tint = colors.textSecondary)
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
                        if (i <= index) colors.accent else colors.tileHigh, ObMotion.snappy(), label = "dot",
                    )
                    Box(
                        Modifier
                            .size(width = if (i == index) 22.dp else 8.dp, height = 8.dp)
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
                    style = ObsidianTheme.typography.labelStrong,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .pressable(pressedScale = 0.93f, onClick = onSkip)
                        .padding(horizontal = ObSpacing.M, vertical = ObSpacing.M),
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
            .padding(horizontal = ScreenGutter)
            .padding(top = ObSpacing.L, bottom = ObSpacing.L),
        verticalArrangement = Arrangement.spacedBy(ObSpacing.L),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            Text(title, style = ObsidianTheme.typography.screenTitle, color = ObsidianTheme.colors.textPrimary)
            Text(lead, style = ObsidianTheme.typography.body, color = ObsidianTheme.colors.textSecondary)
        }
        content()
    }
}

// ── Welcome ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep() {
    val colors = ObsidianTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = ObSpacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .revealOnEnter(0)
                .ambientFloat()
                .breathingGlow(colors.accent, ObShapes.CardLarge)
                .size(112.dp)
                .clip(ObShapes.CardLarge)
                .background(colors.accentContainer)
                .border(1.dp, colors.accentBorder, ObShapes.CardLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tile), null, tint = colors.accentLight, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(ObSpacing.XXL))
        Text(
            "DebugQS",
            style = ObsidianTheme.typography.numericLarge,
            color = colors.textPrimary,
            modifier = Modifier.revealOnEnter(1),
        )
        Spacer(Modifier.height(ObSpacing.S))
        Text(
            "Developer switches, one tile away.",
            style = ObsidianTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(2),
        )
        Spacer(Modifier.weight(0.8f))
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.XL)) {
            Feature(3, R.drawable.ic_developer_mode, "Toggle Developer options")
            Feature(4, R.drawable.ic_wifi, "Copy your wireless adb address")
            Feature(5, R.drawable.ic_lock_open, "Set up once, that's it")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Feature(index: Int, @DrawableRes icon: Int, title: String) {
    val colors = ObsidianTheme.colors
    Row(Modifier.revealOnEnter(index), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = colors.accentLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(ObSpacing.L))
        Text(title, style = ObsidianTheme.typography.bodyLarge, color = colors.textPrimary)
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

@Composable
private fun MethodCard(
    selected: Boolean,
    @DrawableRes icon: Int,
    title: String,
    badge: String?,
    body: String,
    onClick: () -> Unit,
) {
    val colors = ObsidianTheme.colors
    val border by animateColorAsState(if (selected) colors.accentBorder else colors.border, ObMotion.snappy(), label = "border")
    val fill by animateColorAsState(if (selected) colors.accentContainer else colors.card, ObMotion.snappy(), label = "fill")
    ObCard(Modifier.fillMaxWidth(), color = fill, borderColor = border, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(ObShapes.Tile).background(if (selected) colors.accentContainerStrong else colors.tile),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(icon), null, tint = if (selected) colors.accentLight else colors.textSecondary) }
            Spacer(Modifier.width(ObSpacing.M))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = ObsidianTheme.typography.bodyLarge, color = colors.textPrimary)
                    if (badge != null) {
                        Spacer(Modifier.width(ObSpacing.S))
                        Text(
                            badge.uppercase(),
                            style = ObsidianTheme.typography.caption,
                            color = colors.accentLight,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.accentContainerStrong)
                                .padding(horizontal = ObSpacing.S, vertical = 2.dp),
                        )
                    }
                }
                Text(body, style = ObsidianTheme.typography.body, color = colors.textSecondary)
            }
            Spacer(Modifier.width(ObSpacing.S))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) colors.accent else colors.textDisabled, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.accent else colors.card)
            )
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
                PillButton("Open About phone", icon = R.drawable.ic_open_in_new, onClick = {
                    context.tryStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                })
            }
            ChecklistItem(
                number = 2,
                title = "Turn on USB debugging",
                done = state.usb,
                doneText = "On",
            ) {
                Hint("In Developer options, switch on USB debugging.")
                PillButton("Open Developer options", icon = R.drawable.ic_open_in_new, onClick = {
                    Launch.openDeveloperOptions(context)
                })
            }
            ChecklistItem(
                number = 3,
                title = "Run this on your computer",
                done = state.hasPermission,
                doneText = "Granted",
            ) {
                Hint("Plug the phone in, accept “Allow USB debugging?” on the phone, then run:")
                CommandBox(command)
                Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), verticalAlignment = Alignment.CenterVertically) {
                    CopyButton(command)
                    PillButton("Send to computer", icon = R.drawable.ic_share, onClick = {
                        context.tryStart(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, command),
                                "Send the command",
                            )
                        )
                    })
                }
                Hint("No adb yet? Install Android’s platform-tools on the computer first.")
                PillButton("Get platform-tools", icon = R.drawable.ic_download, onClick = {
                    context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(PLATFORM_TOOLS)))
                })
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
                PillButton("Get Shizuku", icon = R.drawable.ic_download, onClick = {
                    context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD)))
                })
            }
            ChecklistItem(2, "Start Shizuku", done = running, doneText = "Running") {
                Hint("Open Shizuku and start it with Wireless debugging (or root). It walks you through it.")
                if (installed) {
                    PillButton("Open Shizuku", icon = R.drawable.ic_open_in_new, onClick = {
                        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { context.tryStart(it) }
                    })
                }
            }
            ChecklistItem(3, "Allow DebugQS", done = state.hasPermission, doneText = "Granted") {
                if (running) {
                    Hint("Shizuku asks once; DebugQS then grants itself the permission.")
                    PillButton(
                        "Allow",
                        icon = R.drawable.ic_bolt,
                        onClick = { Privileged.requestPermission() },
                        containerColor = ObsidianTheme.colors.accentContainerStrong,
                        contentColor = ObsidianTheme.colors.accentLight,
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
    val colors = ObsidianTheme.colors
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
                    .padding(vertical = ObSpacing.M)
                    .breathingGlow(colors.accent, CircleShape)
                    .clip(CircleShape)
                    .background(colors.accentContainerStrong)
                    .border(1.dp, colors.accentBorder, CircleShape)
                    .padding(horizontal = ObSpacing.XL, vertical = ObSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_tile), null, tint = colors.accentLight)
                Spacer(Modifier.width(ObSpacing.M))
                Column {
                    Text("DebugQS", style = ObsidianTheme.typography.bodyLarge, color = colors.textPrimary)
                    Text(
                        state.summary(),
                        style = ObsidianTheme.typography.body,
                        color = colors.accentLight,
                    )
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActionPillButton(
                text = if (requested) "Add again" else "Add to Quick Settings",
                icon = painterResource(R.drawable.ic_add_box),
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
        ObCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
                ListRow(
                    title = "Tap",
                    subtitle = "Switches, Wi‑Fi and adb address",
                    icon = R.drawable.ic_touch_app,
                    titleStyleLarge = true,
                )
                ListRow(
                    title = "Long press",
                    subtitle = "Developer options (you can change it)",
                    icon = R.drawable.ic_settings,
                    titleStyleLarge = true,
                )
            }
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun Checklist(content: @Composable () -> Unit) {
    ObCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.L)) { content() }
    }
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
    val colors = ObsidianTheme.colors
    val badge by animateColorAsState(if (done) colors.accent else colors.tile, ObMotion.snappy(), label = "badge")
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            AnimatedContent(done, label = "badgeContent") { isDone ->
                if (isDone) {
                    Icon(painterResource(R.drawable.ic_check), "Done", tint = colors.onAccent, modifier = Modifier.size(18.dp))
                } else {
                    Text("$number", style = ObsidianTheme.typography.labelStrong, color = colors.textPrimary)
                }
            }
        }
        Spacer(Modifier.width(ObSpacing.M))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = ObsidianTheme.typography.bodyLarge,
                    color = if (done) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(done) {
                    Text(doneText, style = ObsidianTheme.typography.labelStrong, color = colors.accentLight)
                }
            }
            AnimatedVisibility(!done, enter = ExpandEnter, exit = CollapseExit) {
                Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) { content() }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = ObsidianTheme.typography.body, color = ObsidianTheme.colors.textSecondary)
}

/** "Waiting for permission…" with a spinner, then a lit "Permission granted" banner. */
@Composable
private fun GrantStatus(granted: Boolean) {
    AnimatedContent(granted, label = "grant") { ok ->
        if (ok) {
            DoneBanner("Permission granted", "DebugQS can now flip the switches.")
        } else {
            val colors = ObsidianTheme.colors
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ObShapes.Card)
                    .background(colors.cardInner)
                    .border(1.dp, colors.border, ObShapes.Card)
                    .padding(ObSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ObSpinner(size = 20.dp)
                Spacer(Modifier.width(ObSpacing.M))
                Text("Waiting for permission…", style = ObsidianTheme.typography.bodyLarge, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DoneBanner(title: String, body: String) {
    val colors = ObsidianTheme.colors
    ObCard(Modifier.fillMaxWidth(), color = colors.accentContainer, borderColor = colors.accentBorder) {
        ListRow(
            title = title,
            subtitle = body,
            icon = R.drawable.ic_check_circle,
            iconTint = colors.accentLight,
            titleStyleLarge = true,
        )
    }
}

@Composable
private fun Troubleshooting(items: List<String>) {
    val colors = ObsidianTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
        Text(
            if (open) "Hide troubleshooting" else "Command not working?",
            style = ObsidianTheme.typography.labelStrong,
            color = colors.accentLight,
            modifier = Modifier
                .clip(CircleShape)
                .pressable(pressedScale = 0.95f, onClick = { open = !open })
                .padding(vertical = ObSpacing.S, horizontal = ObSpacing.XXS),
        )
        AnimatedVisibility(open, enter = ExpandEnter, exit = CollapseExit) {
            ObCard(Modifier.fillMaxWidth(), color = colors.cardInner, borderColor = colors.border) {
                Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
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
