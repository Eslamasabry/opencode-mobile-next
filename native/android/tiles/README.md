# :android:tiles

Quick Settings tiles, launcher-shortcut publishing, and the one-shot launch/share extras MainActivity reads. A port of
the Flutter app's `AttentionTileService.kt`, `PauseTileService.kt`, `PinnedSessionShortcuts.kt`,
`LaunchShortcutChannel.kt` and `LaunchShareChannel.capture` (android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile).

## Layout

| Path | What it holds |
|---|---|
| `src/main/kotlin/io/github/eslamasabry/opencode_mobile/` | `AttentionTileService`, `PauseTileService`: copies, original package (the OS stores these FQCNs). |
| `src/main/kotlin/io/github/eslamasabry/oc/android/tiles/` | Native-authored: `TilesComponents`, `AttentionTileCache`, `PauseTileState`, `LaunchActions` (+ `LaunchAction`, `SessionShortcutTap`), `PinnedShortcuts` (+ `PinnedSession`, `ShortcutClear`), `SharedTextIntake` (+ `SharedText`). |
| `src/main/AndroidManifest.xml` | Both tile services with full FQCNs, `BIND_QUICK_SETTINGS_TILE`, the `QS_TILE` filter. Merges into the app automatically. |
| `src/main/res/` | `strings_tiles.xml` (en, ar; verbatim from the Flutter app), `ic_shortcut_connect/new_task/session` (verbatim), `tile_ic_mark` (native-authored). |

## Frozen names

- Services: `io.github.eslamasabry.opencode_mobile.AttentionTileService`, `…PauseTileService`.
- Activity named by FQCN (`setClassName`): `io.github.eslamasabry.opencode_mobile.MainActivity`.
- Extras: `oc.shortcut` (launch action), `oc.shortcut.profile`, `oc.shortcut.session` (pinned session tap).
- Shortcut ids: `oc.session:<profileId>:<sessionId>`.
- Launch-action whitelist: `connect`, `new_task`, `activity`, `usage`, `usage_settings`, `background_settings`,
  `phone_setup`, `phone_setup_done`, `linux`, and `usage_target:(sign_in|add_key|set_up):<provider>:<target>`.
- Preferences file `oc_tiles`: `oc.attentionTile` (`{"pendingCount","owner","updatedAt"}`), `oc.live.running`,
  `oc.live.pausedByUser`, `oc.pinnedShortcuts.<profileId>`.

## Deviations from the Flutter app

- The attention snapshot lives in `oc_tiles`, not `FlutterSharedPreferences`, and names its owner `owner`.
- An unchanged count is re-stamped after an hour (`RESTAMP_AFTER_MS`) so a confirmed count never ages into stale.
- The pause tile never pauses or resumes (no native live mode yet): every tap opens the app with
  `oc.shortcut=background_settings`. Its state comes from `PauseTileState`; with nothing set it is unavailable,
  "Off in Settings".
- Pinned-shortcut records are per profile (`oc.pinnedShortcuts.<profileId>`) so profile deletion clears only its own.

## What the coordinator wires in :androidApp

1. `MODULE_OWNERS`: add a row for `:android:tiles`. `:androidApp` depends on `project(":android:tiles")`.
2. Manifest: nothing for the tiles (merged from this library). The static `shortcuts.xml` references
   `@string/shortcut_connect_short|long`, `@string/shortcut_new_task_short|long`, `@drawable/ic_shortcut_connect`,
   `@drawable/ic_shortcut_new_task` from here; its intents put `oc.shortcut` = `connect` / `new_task`.
3. `MainActivity.onCreate(savedInstanceState)` (and `onNewIntent(intent)`, after `setIntent(intent)`):
   ```kotlin
   LaunchActions.consume(intent)?.let { /* route: Connect, NewTask, Activity, BackgroundSettings, ... */ }
   LaunchActions.consumeSessionShortcut(intent)?.let { tap -> /* open tap.profileId / tap.sessionId */ }
   SharedTextIntake.consume(intent)?.let { shared -> /* composer gets shared.text */ }
   ```
   Each removes what it read, so a recreated activity does not replay it. Consume only when `savedInstanceState`
   is null or the extras are still present (they are gone after the first consume anyway).
4. App wiring:
   - The pending-requests count of the connected profile → `AttentionTileCache.write(context, count, profileId, nowMs)`.
   - Explicit disconnect → `AttentionTileCache.clear(context)` and `PinnedShortcuts.withdrawAll(context)`.
   - Profile deletion → `AttentionTileCache.clearOwner(context, profileId)` and
     `PinnedShortcuts.withdrawProfile(context, profileId)`.
   - Pinned sessions of the connected profile change → `PinnedShortcuts.publish(context, profileId, pins)`.
   - Live mode (later) → `PauseTileState.setRunning` / `setPausedByUser`.

## Checks

```bash
./gradlew :android:tiles:testDebugUnitTest
```

## The in-app updater's Android half (unit P12-PLATFORM)

`oc/android/tiles/updates/AndroidUpdates.kt` binds the `:engine:updates` ports: `AndroidUpdateFeed` (GitHub over
`HttpURLConnection`, resumable `<file>.part` download with Range / 416 restart), `AndroidUpdatePlatform` (ABIs, SHA-256,
`getPackageArchiveInfo` package / versionCode / signer, `canRequestPackageInstalls`, PackageInstaller session) and
`PrefsUpdateStore` (`oc_updates` preferences, app-wide). `AppUpdateStatusReceiver` keeps the Flutter FQCN and is declared
in this module's manifest with `REQUEST_INSTALL_PACKAGES`. `:androidApp` builds `AppUpdates` from them in
`graph/PlatformWiring.kt`.
