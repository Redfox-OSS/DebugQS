package dev.debz.debugqs.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.rememberHazeState
import dev.debz.debugqs.R
import dev.redfox.obsidian.components.CollapsingHeader
import dev.redfox.obsidian.components.CollapsingHeaderExpandedHeight
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.ObSnackbarHost
import dev.redfox.obsidian.components.ObSnackbarState
import dev.redfox.obsidian.components.rememberHeaderCollapse
import dev.redfox.obsidian.effects.LocalHazeState
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.motion.ObTransitions
import dev.redfox.obsidian.motion.RevealColumn
import dev.redfox.obsidian.motion.RevealScope
import dev.redfox.obsidian.navigation.FloatingNavBar
import dev.redfox.obsidian.navigation.FloatingNavItem
import dev.redfox.obsidian.navigation.NavIconMotion
import dev.redfox.obsidian.navigation.floatingNavClearance
import dev.redfox.obsidian.navigation.rememberCompactNavState
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

enum class Tab(val label: String, @param:DrawableRes val icon: Int, val motion: NavIconMotion) {
    Home("Home", R.drawable.ic_home, NavIconMotion.Pop),
    Settings("Settings", R.drawable.ic_settings, NavIconMotion.Turn),
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
val LocalSnackbars = staticCompositionLocalOf { ObSnackbarState() }

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
    val snackbars = remember { ObSnackbarState() }
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
                .background(ObsidianTheme.colors.background)
                .nestedScroll(compactNav.connection)
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { if (!navigator.back()) onExit() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = {
                    when (val move = navigator.lastMove) {
                        is Move.TabSwitch -> ObTransitions.tab(move.forward)
                        else -> ObTransitions.push()
                    }
                },
                popTransitionSpec = { ObTransitions.pop() },
                predictivePopTransitionSpec = { ObTransitions.predictivePop() },
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
                enter = fadeIn(tween(220)) + slideInVertically(ObMotion.gentle()) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(ObMotion.gentle()) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingNavBar(
                    items = Tab.entries.map { FloatingNavItem(it.icon, it.label, it.motion) },
                    selectedIndex = navigator.tab.ordinal,
                    onSelect = { navigator.select(Tab.entries[it]) },
                    compact = compactNav.compact,
                )
            }
            ObSnackbarHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter),
                bottomPadding = if (navigator.atRoot) floatingNavClearance() else 24.dp,
            )
        }
    }
}

@Composable
private fun Reveal(navigator: Navigator, content: @Composable () -> Unit) {
    RevealScope(enabled = navigator.lastMove != Move.Pop, content = content)
}

/** A tab's root: a collapsing header over a scrolling column whose sections stagger in. */
@Composable
fun TabScreen(title: String, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
        RevealColumn(
            modifier = Modifier
                .glassSource()
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = ScreenGutter)
                .padding(top = top + CollapsingHeaderExpandedHeight + 8.dp, bottom = floatingNavClearance() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            content = content,
        )
        CollapsingHeader(title = title, collapse = rememberHeaderCollapse(scroll))
    }
}

/** A pushed page: a detail header with back over a scrolling column. */
@Composable
fun PushedScreen(title: String, content: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    DetailPage(title = title, onBack = { navigator.back() }) { padding ->
        RevealColumn(
            modifier = Modifier
                .glassSource()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = ScreenGutter),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            content = content,
        )
    }
}
