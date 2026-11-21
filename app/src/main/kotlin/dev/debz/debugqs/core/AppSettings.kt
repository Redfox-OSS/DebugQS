package dev.debz.debugqs.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a long press on the tile opens. */
enum class LongPressAction(val label: String) {
    DeveloperOptions("Developer options"),
    WirelessDebugging("Wireless debugging"),
    App("DebugQS app"),
}

/** The user's choices, shared by the app and the tile. */
data class Preferences(
    val accent: String = "Emerald",
    val neutrals: String = "Dark",
    val longPress: LongPressAction = LongPressAction.DeveloperOptions,
    /** Turning Developer options off also turns USB and Wireless debugging off. */
    val debuggingFollowsDeveloper: Boolean = false,
    /** Close the tile's dialog right after a switch flips. */
    val dismissAfterToggle: Boolean = false,
    /** Show `ip:port` as the tile's subtitle while Wireless debugging is on. */
    val tileShowsAddress: Boolean = true,
    /** The first-run setup has been finished or skipped. */
    val onboardingDone: Boolean = false,
)

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Preferences> = _flow.asStateFlow()

    // Held as a field: SharedPreferences only keeps weak references to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _flow.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    val current: Preferences get() = _flow.value
    val debuggingFollowsDeveloper: Boolean get() = current.debuggingFollowsDeveloper

    private fun read(): Preferences {
        val d = Preferences()
        return Preferences(
            accent = prefs.getString(KEY_ACCENT, d.accent) ?: d.accent,
            neutrals = prefs.getString(KEY_NEUTRALS, d.neutrals) ?: d.neutrals,
            longPress = prefs.getString(KEY_LONG_PRESS, null)
                ?.let { name -> LongPressAction.entries.firstOrNull { it.name == name } } ?: d.longPress,
            debuggingFollowsDeveloper = prefs.getBoolean(KEY_FOLLOW, d.debuggingFollowsDeveloper),
            dismissAfterToggle = prefs.getBoolean(KEY_DISMISS, d.dismissAfterToggle),
            tileShowsAddress = prefs.getBoolean(KEY_ADDRESS, d.tileShowsAddress),
            onboardingDone = prefs.getBoolean(KEY_ONBOARDED, d.onboardingDone),
        )
    }

    fun update(transform: (Preferences) -> Preferences) {
        val next = transform(current)
        prefs.edit()
            .putString(KEY_ACCENT, next.accent)
            .putString(KEY_NEUTRALS, next.neutrals)
            .putString(KEY_LONG_PRESS, next.longPress.name)
            .putBoolean(KEY_FOLLOW, next.debuggingFollowsDeveloper)
            .putBoolean(KEY_DISMISS, next.dismissAfterToggle)
            .putBoolean(KEY_ADDRESS, next.tileShowsAddress)
            .putBoolean(KEY_ONBOARDED, next.onboardingDone)
            .apply()
        _flow.value = next
    }

    companion object {
        private const val KEY_ACCENT = "accent"
        private const val KEY_NEUTRALS = "neutrals"
        private const val KEY_LONG_PRESS = "long_press"
        private const val KEY_FOLLOW = "debugging_follows_developer"
        private const val KEY_DISMISS = "dismiss_after_toggle"
        private const val KEY_ADDRESS = "tile_shows_address"
        private const val KEY_ONBOARDED = "onboarding_done"

        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
    }
}
