# CLAUDE.md

Guidance for Claude Code (and anyone else) working in this repository.

## What this is

DebugQS is an Android app: one Quick Settings tile that opens a panel with Developer options,
USB debugging and Wireless debugging (plus the Wi‑Fi network and `adb connect` address), and a
companion app for settings and first-run setup. It's a single-tile take on
[DevQS](https://github.com/louie-velarde/DevQS) (MIT; see `NOTICE`), built on the
[Bedrock](https://github.com/Redfox-OSS/bedrock) design system. Licensed GPL‑3.0.

Package `dev.debz.debugqs`, min SDK 31, Kotlin + Jetpack Compose + Navigation 3.

| Path | What |
|---|---|
| `app/src/main/kotlin/dev/debz/debugqs/core/` | No UI: settings access, Shizuku/Sui, Wi‑Fi + wireless ADB port discovery, preferences, intents |
| `core/DevSettings.kt` | Reads/writes the three global settings; the permission check |
| `core/DevState.kt` | `DevStateMonitor`: one live `DevState` from content observers, Wi‑Fi callback and NSD |
| `core/Network.kt` | `WifiMonitor` (SSID, IPv4) and `AdbPortMonitor` (`_adb-tls-connect._tcp` via NSD) |
| `core/Privileged.kt` | Shizuku/Sui: self-grant, `pm enable/disable`, `am start`, `cmd wifi status` |
| `tile/` | `DebugTileService`, `TileDialog` (Compose in a `ComponentDialog`), `TrampolineActivity` (long press + links) |
| `ui/` | App screens: `AppRoot` (nav), `HomeScreen`, `SettingsScreen`, `SetupFlow` (first run), `QuickPanel` (the tile's panel), `Shared.kt` (reused pieces) |
| `docs/` | README icon and framed screenshots |

## Commands

```bash
./gradlew assembleDebug                # debug APK
./gradlew check                        # lint + verifyDesignRules
./gradlew assembleRelease              # signed release (needs the signing properties below)
./gradlew -PlocalBedrock build         # build against ../Bedrock (a sibling checkout) instead of the release
```

`adb` lives at `~/Library/Android/sdk/platform-tools/adb` on the maintainer's machine.

## Toolchain (pinned on purpose)

Matches Bedrock 0.2.0: AGP 9.2.1, Kotlin 2.2.10 (built-in Kotlin, Compose compiler plugin),
compile SDK 36.1, min SDK 31 (Bedrock's glass blur needs Android 12). Lint's "newer version
available" warnings are expected; don't upgrade these without upgrading Bedrock.

Shizuku is pinned to 13.1.0, like DevQS: later versions hide `Shizuku.newProcess`, which the
Developer options page swap and the Wireless debugging shortcut rely on.

## Design rules

The UI follows Bedrock (`docs/GUIDE.md` in the Bedrock repo). The essentials:

- Colours and type only from `BedrockTheme.colors` / `BedrockTheme.typography`; no
  `MaterialTheme.*`, no `Color(0x…)`. No `clickable`/ripples: use `Modifier.pressable { }`.
  `verifyDesignRules` (part of `check`) fails the build on these.
- No glows, nothing overshoots: colour lives inside surfaces (accent containers, the welcome
  screen's aura); motion uses `BrMotion` / `BrTransitions`. Sentence case everywhere.
- One `Primary` `BrButton` per screen region; secondary actions are `Tonal` (usually `Small`).
- Grouped rows go in a `ListGroup`, with `divider = true` on all but the last. Use our `ListRow`
  (in `ui/Shared.kt`), not Bedrock's: Bedrock's cuts subtitles to two lines, and settings
  descriptions must wrap.
- Use `CopyButton` for long values (commands, the adb address): it never repeats the value.
- Saved accent and background names may still be Obsidian's (Emerald, Dark, …): `accentFor` /
  `neutralsFor` in `ui/Shared.kt` map them to Bedrock's. Keep that mapping.
- Keep copy short and spacious; the user dislikes dense screens. Check every UI change in a
  screenshot.

## Platform quirks (learned the hard way)

- **No entry animations in the tile dialog.** On One UI the dialog's window can stop getting
  Compose frames after the first one; anything that starts invisible (e.g. `popIn()`, alpha 0)
  stays invisible. The dialog relies on the window's own animation instead.
- **Permission check can go stale.** After USB debugging goes off and on, a long-lived process may
  report WRITE_SECURE_SETTINGS as denied while it's granted. `DevSettings.hasPermission` falls back
  to writing a setting's current value back (a no-op when allowed). Keep that fallback.
- **No broadcast for a permission grant.** `SetupFlow` polls once a second while waiting.
- **Wi‑Fi name** needs location access ("all the time" for the tile, which runs in the background),
  or Shizuku (`cmd wifi status`). Without either it shows "Wi‑Fi (name hidden)".
- **Wireless debugging** on an untrusted network triggers Android's "Allow on this network?"
  prompt; writing `adb_wifi_enabled=1` from the shell gets reverted until it's allowed.
- Samsung's Quick panel sometimes ignores the first injected tap right after it opens; tap again.

## Testing on a real phone over USB

- **Never switch off USB debugging or Developer options on a device connected over USB**: it cuts
  the ADB connection, and the user has to reconnect by hand.
- **Never tap by reused coordinates.** Layouts shift (the panel shrinks when Wireless debugging is
  off). Find the element first (`uiautomator dump`, match its text, use its bounds) and abort if it
  isn't found. Dumping can fail while the screen is animating; retry.
- Record the three settings before testing and restore them after.
- The user may be using the phone at the same time. Don't answer permission prompts (location
  etc.) for them.
- Prefer the emulator for anything destructive or for screenshots.

## Releases

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease`, then install the APK on the emulator and check setup, Home and
   the tile panel (the release build is minified with R8).
3. Commit, push, and `gh release create v<version>` with the APK attached as
   `DebugQS-<version>.apk` and its SHA‑256 in the notes.

**Signing.** The key is `~/.android/keystores/debugqs-release.jks`; its passwords are in
`~/.gradle/gradle.properties` (`DEBUGQS_STORE_FILE`, `DEBUGQS_STORE_PASSWORD`, `DEBUGQS_KEY_ALIAS`,
`DEBUGQS_KEY_PASSWORD`). Every release must be signed with this key, or it won't install over the
previous one. Never commit it; if it's missing, stop and ask rather than generating a new one.
A debug build on a device must be uninstalled before the release one installs (different keys),
which clears the app's data; re-grant WRITE_SECURE_SETTINGS afterwards.

## Screenshots (`docs/screenshots/`)

Take them from the emulator with demo mode on (`settings put global sysui_demo_allowed 1`, then
`am broadcast -a com.android.systemui.demo …` for clock 0930, full battery, no notifications) so
no personal data shows. Frame each at 540×1200 with a ~24 px corner radius (about 4% of the width,
only slightly rounded), a faint hairline edge and transparent corners. The README shows them at
260 px wide, three per row.

## Git

- Don't push, tag or release without being asked.
- Never commit signing keys, `local.properties`, build output or `.claude/settings.local.json`.
