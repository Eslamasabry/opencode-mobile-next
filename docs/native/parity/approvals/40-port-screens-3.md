RESULT: APPROVED WITH FIXES

# Approval — 40-port-screens-3 (Flutter screens, part 3)

Branch `native/p12x/job-40-port-screens-3`; job commits 094fe3a3, 5dcb3949, 71e44db8, 80475413, a5040265 on base
b6670872; P12 integration head b6c30a59 merged in. Report: `docs/native/parity/migration/40-port-screens-3.md`
(67/67 files: ported now 52, not needed 15). Review read the code against `lib/` and plan/01. Nothing was compiled
here (Google Maven is blocked); the build server compiles next. No tests and no APK, per the owner's order.

## Merge with the P12 head

Nine files conflicted. Both sides were kept, with one deliberate consolidation:

1. **Two monitors → one.** ANDR-2 (P12) added `AndroidProfileMonitoring` and this job added `NativeProfileMonitoring`,
   each with its own `ProfileMonitor` loop, alert shade and deletion closer. Together they would poll every enrolled
   server twice and post every alert twice. The job's engine stays: it has Flutter's shared rules, redacted attention
   reads, salted credential identity, the monitor inbox and the opaque-tap route. P12's Servers-list opt-in and status
   now run on it through `NativeServerMonitoring` (`MonitorWiring.kt`, rows from `NativeProfileMonitoring.serverRows`).
   P12's per-server Wi-Fi only and check-in rows were removed, with their strings: Flutter shares these
   (`SharedNotifyRules.applyTo`) and the job ports them in Settings › Notifications, so the rows would have done
   nothing. This settles the "placement differs" item the ANDR-2 approval left open.
2. **PlatformWiring.** The job's widget actions, monitor inbox and quota-alert taps are combined with P12's
   `opencode-mobile://` VIEW links, shared project folders, bounded widget previews/asks and id-keyed server stamps.
   There is one widget publisher: `decorate(...)` → `publish(snapshot, profile, target)`, and the state is marked only
   when the publish is not refused.
3. **Servers.** The job's saved-row/editor flow, the ANDR-2 opt-in, and the B-NAV-040 "This phone" mark (the job
   had dropped it from the rows).
4. **Settings / MainActivity.** The job's pages plus P12's share-sheet service; P12's parked launch routing plus the
   job's terminal slot. `builtinLinux = platform.builtinLinux` was kept: the pages it gates are shipped and registered.

## Findings fixed in review

- **High — editing a saved server dropped its trust** (`ServerEditorScreen.kt`). The address watcher compared against
  state captured before the record had loaded, so filling a saved address cleared the confirmed HTTP origin and the TLS
  pin. HTTP saves were refused and HTTPS servers were saved without their pin.
- **Medium — two writers for the phone's shared-folder list.** `AndroidProjectStorageAccess.push()` and P12's
  `NativeSharedProjectFolders` wrote different root sets, and the last write won. A folder could lose its bind right
  before the confined restart. There is now one writer: `NativeSharedProjectFolders.publish(excluding)`, under a lock.
- **Medium — setup commands not copyable.** Flutter's "Copy command" is now on every start/token/USB/upgrade block;
  the upgrade command is moved into `SetupCommands`.
- **Low:**
  - Server rows now refresh when a graph's capabilities arrive.
  - "Check now" on This phone could stay disabled after an abandoned read.
  - All-files access now falls back to the list page (Flutter).
  - Session pins (`oc.sessionPins.`) are now registered for profile deletion.
  - The widget's server stamps now forget deleted servers.
  - The per-server Notifications switch now shows only while the server is watched.
  - Notifications now has the no-servers row and the Wi-Fi-only detail.
  - The permissions retry row is now labelled "Try again".
- The report was corrected to match: line references, the copy wording, the trust/rows/storage notes, and the
  quota-alert destination (the Plans tab, not a specific fix flow).

## Checked and OK

- **Reachability:** each "ported now" area has a registered route, a visible entry point, a Metro-bound ViewModel and
  real Android bindings. No placeholder, stub, TODO or no-op binding was added; the TODOs present predate the job.
- **Security:**
  - Credentials stay out of UI state and logs.
  - OAuth pages pass `ExternalLinkPolicy` and open through `ExternalLinkOpener`.
  - Provider keys never reach state, widgets or notifications.
  - The only exported activity is `WidgetConfigureActivity`, and it checks widget ownership.
  - The storage permissions match Flutter's manifest.
  - The monitor keeps only a salted credential hash.
  - New per-profile keys are registered for deletion.
- **UI and copy:** kit parts only, with no Material imports in the feature screens. Every referenced string exists in
  English and Arabic.
- **"Not needed" rows:** the AI Team (D6), Termux new setup (D7), hero scenes (D11) and web (D17) rows match plan/01
  §12. Team and FactsBook data stays on disk.

## Left open (not blocking)

- **Dead code:** `PhoneSetupScreen` / `PhoneSetupViewModel` are unreachable after the switch to `SetupJourneyScreen`,
  but their tests still use them.
- **Dead copy:** `ScheduleWords.compatibilityTemplates` is a hand-written copy of the schedule strings used only by
  its test.
- **API 26–29 storage access:** the app-details page opens instead of Flutter's runtime permission dialog (the port
  has no Activity).
- **`oc.usageAlertSeen.v1`:** a shared blob, as in Flutter. A deleted connection's entries go on the next evaluation,
  not at deletion.

Shipping state: merged, reviewed and fixed, committed and pushed to the job branch. Not compiled here, not tested,
not released.
