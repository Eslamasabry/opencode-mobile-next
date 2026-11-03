# Migration proof: Flutter 4501 → native switch 10003, in place (2026-11-03)

**Verdict: FAIL to ship as-is, PASS for the migration itself.** The in-place update keeps every piece of data and
the migration runner is safe under kills and failures, but two things the owner uses are **not in the native build at
all** (built-in Linux and offline voice), and the re-enter flow had a dead end (fixed on this branch, see §4).

Branch `native/migration-proof-2026-11-03` (worktree `/home/eslam/Storage2/wt/migration-proof`), based on
`native/p12-integrate-2026-11-03` at `c6d14e8d6`. Not pushed.

## 1. Setup

| What | Value |
|---|---|
| Emulator | AVD `OC_API35` (API 35, google_apis x86_64, 2 GB RAM, 1080×2400), started `-read-only` on port 5570 so the shared AVD's saved state was never changed; held `/home/eslam/Storage2/wt/emu.lock` throughout. Never `emulator-5584`, never the owner's phone. |
| Flutter app | `app-release-4501.apk` (2.1.0, versionCode 4501), SHA-256 `7eb5e121…000f`, signer `1DE5BF08…D60C` |
| Native app | `:androidApp:assembleSwitchRelease` → zipalign → `apksigner` with the local key (`~/.android/debug.keystore`, the key behind every native preview APK). Package `io.github.eslamasabry.opencode_mobile`, versionCode **10003**, signer SHA-256 **`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`** (checked with `apksigner verify --print-certs`). APK SHA-256 `2824c5a4…2639` (before the fix), `d618a99b…2e18` (with the fix) |
| Kill/delay build | new `qa` build type of `:androidApp` (release + `:android:migration`'s qa `StepInterrupter`, debug-key signed, same signer) — commit `96af122f9` |
| Test servers | two OpenCode 2.2.5 servers on this PC (`127.0.0.1:4311`, `:4312`, own `HOME`/`XDG_*` dirs under the artifacts dir, Basic auth with their per-run passwords, reached from the emulator at `10.0.2.2`). Stopped by exact listener PID at the end. |
| Artifacts (not committed) | `/home/eslam/Storage2/wt/migration-proof-artifacts/` — APKs, logcats `j1…j5-logcat.txt`, prefs before/after, seed tar, journals |

## 2. What was created in the Flutter app (through its UI, plus adb for files)

1. **Two servers with passwords**: "Home box" (`http://10.0.2.2:4311`) and "Work box" (`…:4312`), added through
   *Connect to my computer → Enter the address instead*, plain-HTTP consent given, password typed, renamed in
   *Edit server*.
2. **Chats with history**: "Fix the add function" and "Write a README intro" on Home box, "Second server chat" on
   Work box (each with a prompt, tool calls — read/grep — and a reply stopped by the free model's limit).
3. **A draft with an attachment** in "Fix the add function": text `DRAFT-MP Please review this diagram before
   merging` + a photo from the picker (`scaled_1000000020.png`).
4. **A scheduled prompt** in "Write a README intro": `SCHED-MP Say the word banana`, once, tomorrow 09:00 (Flutter
   armed it as a WorkManager job, ~22 h delay).
5. **A saved prompt** (stash) on the phone server's project: `SAVED-MP Explain the git log in plain words`.
6. **Home-screen widget**: "Conversations" (4×2, `SessionsWideWidgetProvider`, widget id 2), configured "All
   projects". **QS tiles**: Attention and Pause added. Manifest shortcuts `new_task` and `connect` present.
7. **Appearance**: Light, theme Nord, style Solid. **Language**: العربية (in-app). **Notifications**: Finished work
   off, Questions on, Check in off, Quiet hours off, Wi-Fi only on, Home box monitoring on. **Conversation**: show
   time/tokens/cost toggled. **Voice**: Compact speech model downloaded (104 MB), language English.
8. **Built-in Linux**: *On this phone → Set up OpenCode* completed (Ubuntu rootfs 1.1 GB, `ubuntu.ready`), project
   `my-app` created; this made a third saved server "This phone" (`127.0.0.1:4097`, app-made password), which was
   the **active** server at update time.

Result on disk: 79 `flutter.*` prefs keys, 3 `flutter_secure_storage` entries (`pw.<id>` ×3).

## 3. J1 — install over Flutter (`adb install -r`, release build)

Update accepted (4501 → 10003); `firstInstallTime` unchanged; no crash or ANR in the log. Journal: S0 → M6 done
in ~0.7 s, "Update moved your data: 3 servers, 3 saved sign-ins (0 need the password again)".

| Item | Survived? | Evidence |
|---|---|---|
| Updating screen | Yes ("جارٍ تحديث التطبيق…", and "Still updating… / Try moving your data again" after 8 s), seen with the qa delay; in J1 it was too fast to catch | `shots/j2-updating-1.png`, `shots/j3-still-updating.png` |
| Language | Yes: the whole app opens in Arabic | `shots/j1-first-launch-3.png` |
| Servers + passwords | Yes: all 3 listed with names; **Home box and Work box connect without typing a password** (3 secrets re-stored as `pw.<id>` in `oc_secrets_v1`) | `shots/j1-servers.png`, `shots/j1-both-connected.png` |
| Chats and history | Yes: all 3 chats from both servers listed; history (prompt, tool summary, stopped reply) readable | `shots/j1-conversations.png`, `shots/j1-work-chat.png` |
| Draft with attachment | Yes: text and the photo chip back in the composer | `shots/j1-chat-draft.png` |
| Scheduled prompt | Yes: listed (once, tomorrow 09:00, enabled); native re-armed it as its own WorkManager job. **It fires**: with the clock moved to 09:01 and the job forced, the prompt reached the server and a "Scheduled prompt" notification was posted | `shots/j1-schedules.png`, server message list, `dumpsys notification` |
| Home-screen widget | Yes: still placed (id 2, same provider) and fed by the native app (Arabic header, both chats) | `shots/j1-home-widget.png` |
| QS tiles, launcher shortcuts | Yes: both tiles still in `sysui_qs_tiles`; `new_task` and `connect` still published | `dumpsys shortcut` |
| Appearance | Yes: Light · Nord · Solid | `shots/j1-appearance.png` |
| Notification choices | Yes for the app-wide ones (Finished work off, Questions on, Wi-Fi only on, Quiet off) | `shots/j1-notifications.png` |
| Saved prompt | Kept on disk (`oc.promptStash.<phone id>.<project>` unchanged), **not reachable** in the UI because it belongs to the phone server, which cannot run (next row) | prefs diff |
| **Built-in Linux** | **No.** Files are byte-identical (rootfs, `ubuntu.ready`, projects), but the native app says "OpenCode isn't on this phone yet" and "Couldn't read Linux"; the phone server never starts. Cause: `LinuxWiring` binds `AbsentLinuxHost(NotOnThisBuild)`; `:android:linux` is not in the app and the APK has no `libproot.so`/`libtalloc.so`/`libtermux.so` | `shots/j1-this-phone.png`, `shots/j1-linux-page.png` |
| **Voice models** | **No** (files byte-identical). `VoiceWiring` binds `NoOfflineSpeechEngine`; the APK has no `libsherpa-onnx*`/`libonnxruntime.so` | sha256 lists |
| Prefs file | No key removed; changed only by later use (active server, widget list) and by native adopting the schedule (server fingerprint re-keyed `n1:…`, still enabled); added the barrier key and empty schedule stores | `seed-prefs.xml` vs `j1-prefs.xml` |

## 4. Failure paths

| Run | How | Result |
|---|---|---|
| J2 kill in M2 (phase 2) | qa build, `qa-kill-after=M2:1` (after the 1st of 3 secrets) + 12 s delay | Process died with `M2 running, attempt 1`, ledger holds 1 name. Relaunch: M2 attempt 2 → `alreadyMigrated 1, migrated 2`, nothing flagged, v1 complete, app opens (`shots/j2-after-m2-kill-resume.png`) |
| J3 kill after M1 (phase 1) | `qa-kill-after=M1` + 15 s delay | "Still updating" shown at 10 s; died after M1 committed; relaunch completed M2–M6 (`migrated 3`) |
| J2b repeated kills in S0 | `qa-kill-after=S0:partialCreated` | The system restarted the killed process by itself and the second attempt completed: no wedge. (Three crashes in a row could not be forced this way; StepCrashed stays covered by the JVM tests.) |
| Unreadable secret | Work box's FSS ciphertext corrupted (as root), release build | M2 `flagged TypedPassword.BadBase64 ×1`, the other 2 migrated, nothing invented. Sheet: "بعض عمليات تسجيل الدخول تحتاج إليك مجددًا … Work box · أدخل كلمة المرور … سجّل الدخول إلى هذه لاحقًا" (`shots/j4-reentry.png`). Connect says "This app couldn't read the saved password. Enter the password again." (`shots/j4-workbox-connect.png`) |
| Low space | `qa-free-bytes=1000` | Blocked "Your phone is almost full · needs 17 MB", Try again / Open storage settings / Report a problem, `Details · S0 · LowSpace` (`shots/j5-lowspace-blocked.png`). No Start fresh here, by design (plan 05 §8.1) |
| Start fresh (separate run) | Flutter prefs file truncated to 9,000 bytes → `PrefsUnreadable` | Blocked screen with **Start fresh (keeps a copy of your old data)** (`shots/j5-prefs-unreadable.png`) → confirm (`shots/j5-start-fresh-confirm.png`) → the app restarted into first run (`shots/j5-after-start-fresh.png`); old prefs moved to `files/migration/replaced-<at>/`, S0 backup and FSS file kept, journal `source.kind=startFresh`, Linux files untouched |

Rewinds between runs: `adb install -r -d` of 4501 (the system image is debuggable; never uninstalled, so the
Keystore keys stay) + restore of the seeded data dir as root.

## 5. Bugs found

| # | Bug | Where | Status |
|---|---|---|---|
| 1 | **Re-enter dead end.** "Work box · Enter password" opened the Servers page, which has no password field; the only way out was remove + re-add, which gives the server a new id and orphans its schedules, drafts and choices | `feature/gate`, `MigrationWiring` | **Fixed and verified on device** (`6cdab366a`, §7): the row asks for the password in the sheet (KitField Secret, registered with the redactor) and saves it with `ProfileStore.upsert` on the same profile (same id, trust kept, flag cleared). Tests: 3 new presenter tests, 1 binding test, 1 desktop screen test; red before (compile), mutant restoring the old row behaviour → 2 failures. |
| 2 | **Built-in Linux absent** from the native app (`AbsentLinuxHost`, no proot libraries). The phone server, its project and the saved prompt stored for it are unusable after the update; the phone page offers "Set up OpenCode" over an existing install | `:android:linux` not in `:androidApp`; `LinuxWiring` | **Ship blocker**, not fixed (feature work outside the migration write set) |
| 3 | **Offline voice absent** (`NoOfflineSpeechEngine`, no sherpa-onnx). The downloaded model is kept but unusable | `VoiceWiring`, `:android:media` | **Ship blocker**, not fixed |
| 4 | The active server at update time ("This phone") is not reconnected; the first screen says "No conversations yet" until the person connects a server by hand | follows from 2 | open |
| 5 | Scheduled-prompt notifications are in **English** in an Arabic app: `android/schedules` uses `app.getString` (system locale) and ignores `oc.appLocale`, unlike widgets and `:android:notifications` | `android/schedules/ScheduleNotifications.kt` | open (outside the write set) |
| 6 | Flutter's own WorkManager job for the schedule stays queued and fails with `ClassNotFoundException: …ScheduledPromptWorker` when due (logged by WorkManager, no crash, no double run) | migration could cancel Flutter's work on adoption | open, low |
| 7 | Per-server "Monitor this server" (Home box on) has no row in the native Notifications page | `feature/settings` | open, parity |
| 8 | The gate screens draw in the default dark theme, not the person's light theme (prefs are gated until the migration ends) | by design (05 §8.2) | note |

## 6. Checks run

- `:android:migration:testQaUnitTest :engine:migration:desktopTest :feature:gate:desktopTest` on the base: green.
- After the fix: `:feature:gate:desktopTest` 29/29, `:ui:words:desktopTest` 4/4, `:quality:test --tests
  '*KonsistRulesTest*' --tests '*KitOnly*'` 13/13 (K1 found two non-kit imports on the first try; replaced),
  `:androidApp:assembleSwitchRelease :androidApp:assembleSwitchQa` OK.
- Not run: the full `:quality:test`, lint, detekt, the full native suite.

## 7. Fix on device

Build `native-switch-release-10003-fix.apk` (SHA-256 `d618a99b…2e18`, signer `1DE5BF08…D60C`) on a fresh
`OC_API35` run: Flutter 4501 freshly installed, then the seeded data dir restored without the rootfs. A fresh install
has no Flutter Keystore key, so both typed passwords were unreadable (`TypedPassword.KeystoreAliasMissing ×2`; the
phone server's app-made password `renewedByOwner`, not listed).

1. The sheet listed "Home box · أدخل كلمة المرور" and "Work box · أدخل كلمة المرور" (`shots/fix-1-sheet.png`).
2. The Home box row turned the sheet into "كلمة مرور Home box" with Paste, reveal, حفظ (Save) and العودة إلى القائمة
   (Back to the list) (`shots/fix-2-field.png`). After Save, only the Work box row was left (`shots/fix-3-after-save.png`).
3. Saving Work box closed the sheet (`shots/fix-4-sheet-closed.png`). `oc_secrets_v1` then held both `pw.<id>`, no
   `oc.secretReentry.*` key was left, and the profile list still held the same 3 ids (no duplicate server). Home box's
   schedule store was still keyed by its original id.
4. Servers → Connect on both: **متصل (connected)** for Home box and Work box (`shots/fix-5-both-connected.png`).
   No crash or ANR in `fix-logcat.txt`.

## 8. State left behind

- `OC_API35` ran read-only every time: its saved state is unchanged. Stopped. The emulator lock was released and
  handed back to the issue-112 worker.
- Both OpenCode test servers were stopped by their exact listener PIDs (2406151, 2406622). Their data dirs, the APKs,
  logcats and the seed tars stay under `/home/eslam/Storage2/wt/migration-proof-artifacts/`; they hold only the test
  servers' throwaway passwords and no owner data.

## Screenshots (one screen each)

| File | Shows |
|---|---|
| `f02-draft-with-attachment.png` | Flutter: the draft with its photo |
| `f03-scheduled.png` | Flutter: "Scheduled. It is in Scheduled prompts." |
| `f04-notifications-set.png` | Flutter: notification choices |
| `f06-home-widget.png` | Flutter: the placed Conversations widget |
| `f07-arabic-settings.png` | Flutter: Arabic chosen |
| `j2-updating-1.png`, `j3-still-updating.png` | Native: Updating, then Still updating + Try again |
| `j1-first-launch-3.png` | Native first screen (Arabic; no server connected) |
| `j1-servers.png`, `j1-both-connected.png` | All servers kept; both connect without a password |
| `j1-conversations.png`, `j1-work-chat.png`, `j1-chat-draft.png` | Chats, history, the draft with attachment |
| `j1-schedules.png` | The schedule kept |
| `j1-appearance.png`, `j1-notifications.png` | Light · Nord · Solid; notification choices |
| `j1-home-widget.png` | The widget drawn by the native app |
| `j1-this-phone.png`, `j1-linux-page.png` | Linux not found (ship blocker) |
| `j2-after-m2-kill-resume.png` | App open after the M2 kill |
| `j4-reentry.png`, `j4-workbox-connect.png` | Re-enter sheet; the dead end before the fix |
| `j5-lowspace-blocked.png`, `j5-prefs-unreadable.png`, `j5-start-fresh-confirm.png`, `j5-after-start-fresh.png` | Blocked screens and Start fresh |
| `fix-1-sheet.png` … `fix-5-both-connected.png` | The fix on device |
