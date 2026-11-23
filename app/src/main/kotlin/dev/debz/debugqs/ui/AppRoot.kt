package dev.debz.debugqs.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.rememberHazeState
import dev.debz.debugqs.R
import dev.redfox.bedrock.components.BrLargeTitle
import dev.redfox.bedrock.components.BrToastHost
import dev.redfox.bedrock.components.BrToastState
import dev.redfox.bedrock.components.BrTopBar
import dev.redfox.bedrock.components.rememberTopBarCollapse
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.effects.LocalHazeState
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.motion.BrDurations
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.RevealColumn
import dev.redfox.bedrock.motion.RevealScope
import dev.redfox.bedrock.navigation.BrNavBar
import dev.redfox.bedrock.navigation.BrNavItem
import dev.redfox.bedrock.navigation.brNavClearance
import dev.redfox.bedrock.navigation.rememberCompactNavState
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_home),
    Settings("Settings", R.drawable.ic_settings),
}

sealed interface Destination {
    data class Root(val tab: Tab) : Destination
    data object Setup : Destination
}

sealed interface Move {
    data class TabSwitch(val forward: Boolean) : Move
    data object Push : Move
    data object Pop : Move
}

@Stable
class Navigator {
    val backStack = mutableStateListOf<Destination>(Destination.Root(Tab.Home))
    var lastMove: Move by mutableStateOf(Move.Push)
        private set

    val tab: Tab get() = (backStack.first() as Destination.Root).tab
    val atRoot: Boolean get() = backStack.size == 1

    fun select(target: Tab) {
        if (target == tab) {
            while (backStack.size > 1) back()
            return
        }
        lastMove = Move.TabSwitch(forward = target.ordinal > tab.ordinal)
        backStack.clear()
        backStack.add(Destination.Root(target))
    }

    fun push(destination: Destination) {
        lastMove = Move.Push
        backStack.add(destination)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        lastMove = Move.Pop
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

val LocalNavigator = staticCompositionLocalOf { Navigator() }
val LocalSnackbars = staticCompositionLocalOf { BrToastState() }

@Composable
fun AppRoot(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { dev.debz.debugqs.core.AppSettings.get(context) }
    val prefs by rememberPreferences()
    if (!prefs.onboardingDone) {
        // First launch: the guided setup takes the whole screen, with no nav bar.
        SetupFlow(onFinish = { settings.update { it.copy(onboardingDone = true) } })
        return
    }
    val navigator = remember { Navigator() }
    val snackbars = remember { BrToastState() }
    val hazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalNavigator provides navigator,
        LocalSnackbars provides snackbars,
    ) {
        val compactNav = rememberCompactNavState(resetKey = navigator.backStack.lastOrNull())
        Box(
            Modifier
                .fillMaxSize()
                .background(BedrockTheme.colors.background)
                .nestedScroll(compactNav.connection)
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { if (!navigator.back()) onExit() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = {
                    when (val move = navigator.lastMove) {
                        is Move.TabSwitch -> BrTransitions.tab(move.forward)
                        else -> BrTransitions.push()
                    }
                },
                popTransitionSpec = { BrTransitions.pop() },
                predictivePopTransitionSpec = { BrTransitions.predictivePop() },
                entryProvider = entryProvider {
                    entry<Destination.Root> { key ->
                        Reveal(navigator) {
                            when (key.tab) {
                                Tab.Home -> HomeScreen()
                                Tab.Settings -> SettingsScreen()
                            }
                        }
                    }
                    entry<Destination.Setup> {
                        SetupFlow(
                            onFinish = { navigator.back() },
                            startAt = SetupStep.Method,
                            onBackFromStart = { navigator.back() },
                        )
                    }
                },
            )
            AnimatedVisibility(
                visible = navigator.atRoot,
                // Rises calmly when coming back from a pushed screen; sinks away when leaving.
                enter = slideInVertically(BrMotion.glide(IntOffset.VisibilityThreshold)) { it / 2 } +
                    fadeIn(tween(BrDurations.Enter, easing = BrMotion.Calm)),
                exit = slideOutVertically(BrMotion.settle(IntOffset.VisibilityThreshold)) { it / 2 } +
                    fadeOut(tween(BrDurations.Fade, easing = BrMotion.Calm)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BrNavBar(
                    items = NavItems,
                    selectedIndex = navigator.tab.ordinal,
                    onSelect = { navigator.select(Tab.entries[it]) },
                    compact = compactNav.compact,
                )
            }
            BrToastHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter),
                bottomPadding = if (navigator.atRoot) brNavClearance() else 24.dp,
            )
        }
    }
}

@Composable
private fun Reveal(navigator: Navigator, content: @Composable () -> Unit) {
    RevealScope(enabled = navigator.lastMove != Move.Pop, content = content)
}

private val NavItems = Tab.entries.map { BrNavItem(it.icon, it.label) }

/**
 * A tab's root: a large title that scrolls with the content, under a frosted top bar that takes
 * the title over as you scroll. The sections below it rise in one after another.
 */
@Composable
fun TabScreen(title: String, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val collapse = rememberTopBarCollapse(scroll)
    Box(Modifier.fillMaxSize().background(BedrockTheme.colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource()
                .verticalScroll(scroll)
                .padding(top = topBarClearance(), bottom = brNavClearance()),
        ) {
            BrLargeTitle(title = title, collapse = collapse)
            SectionColumn(content)
        }
        BrTopBar(title = title, collapse = collapse, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** A pushed page: the same large title and frosted bar, with back in the bar. */
@Composable
fun PushedScreen(title: String, content: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    val scroll = rememberScrollState()
    val collapse = rememberTopBarCollapse(scroll)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BrSpacing.XXL
    Box(Modifier.fillMaxSize().background(BedrockTheme.colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource()
                .verticalScroll(scroll)
                .padding(top = topBarClearance(), bottom = bottom),
        ) {
            BrLargeTitle(title = title, collapse = collapse)
            SectionColumn(content)
        }
        BrTopBar(
            title = title,
            collapse = collapse,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = { navigator.back() },
        )
    }
}

/**
 * A page's sections at the gutter, staggering in. Each section opens with a `SectionHeader`,
 * whose own top padding brings the gap between sections to Bedrock's 40dp.
 */
@Composable
private fun SectionColumn(content: @Composable () -> Unit) {
    RevealColumn(
        modifier = Modifier.padding(horizontal = BrSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(BrSpacing.XXL),
        content = content,
    )
}
