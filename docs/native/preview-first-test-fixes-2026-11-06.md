# Native preview first-test repairs — 2026-11-06

Worktree: `scr-merge`, branch `native/screens-merge`. Starting candidate: `e30aa83b8`.

Finish line: repair the reported setup/chat/picker faults and the same source-confirmed kit failures elsewhere.
Non-goals: Flutter changes, compilation, tests, running the app, signing, publishing, pushing or releasing.

## Evidence and remaining initializer issue

This is **source inspection and committed implementation**, not a compile, test, screenshot or device pass.
The coordinator explicitly owns compilation on the build server. No Gradle, Flutter, test runner, app or server was
started. Changed Kotlin was formatted with the already-present ktfmt jar; `git diff --check` was used for whitespace.
Existing port-contract assertions and a direct-phone busy-port regression were updated in source without running them.

**The exact reported `ExceptionInInitializerError` / `NoClassDefFoundError` class remains unconfirmed.** The report did
not include its class name or first causal exception. The shared Model / Project / Agent picker has a definite second
fault: a vertically scrolling body inside `KitSheet`'s vertical scroller, measured with infinite height. That fault is
fixed, but it does not establish the cause of a poisoned JVM initializer. The owner/coordinator still needs to provide
the first `Caused by` exception and named class if that failure persists. No speculative initializer workaround or
blanket exception swallowing was added.

Inspected initialization paths include `NewConversationSheet` (an enum with no executable initializer), `KitSheetKt`
(nullable local factories), `KitPredictiveBackKt` (a no-back lambda), `SheetLayout` (constants), `KitEasing` / `CurveId`
(finite cubic control points), `KitPartGlyphs` (getters), `KitIcons` (lazy vectors), glyph-table parsing,
`KitAssertKt` (`AppLog.tagged`, whose facade buffers safely), kit row/field classes, and kit redaction/bidi/static-SVG
regexes. The wider native scan covered explicit `init`, object/companion declarations, top-level properties and
`require`/`check`/`error`/`kitAssert` calls. Bundled agent-catalog validation and native ownership/security validation
remain intact. No additional *source-confirmed* throwing static initializer was identified.

## CompositionLocal inventory

Nine throwing defaults were found:

| Local | Provider coverage |
| --- | --- |
| `LocalKitAppearance`, `LocalKitColors`, `LocalKitTypography`, `LocalKitTokens`, `LocalKitMotion`, `LocalKitRoles` | Existing `KitTheme` providers cover production windows. |
| `LocalKitPlatform` | Existing app/secondary/proof window providers cover platform reads. |
| `LocalKitRedactor` | Now supplied by the graph's real `KitChatEnvironment` before navigation, including server editor command blocks. Android main/manage-space/widget windows, desktop main/update windows and transcript proof window are covered. |
| `LocalKitSecretSink` | `KitChatEnvironment` now registers field secrets with the same real Redactor, including desktop setup. Existing Android field sink remains safe. |

The remaining locals have real defaults or deliberately nullable optional slots (sheet host, card renderers, terminal,
logo, prompt lift and transition seams). The guards were retained; no identity redactor was introduced.

## Kit-rule and layout findings

- Demo/plain chat, terminal and file-browser bars can exceed the compact limit of two actions. Each now supplies an
  overflow menu with the original callbacks, icons and disabled reasons. Other top bars were inspected; the all-chats
  bar has at most two actions and did not need changing.
- Thirteen sheet bodies had duplicate/unbounded scrolling: new-conversation pickers; chat state handoff, header
  handoff, header session import, inbox and transfer; kept-prompt destinations; Git server tools; server queue;
  Tailscale setup; project workspace; plan details; development-service output. `KitSheet` now owns scrolling.
- Retry states in Chat, Session Note, Session Relations and server commands/tasks supplied `onRetry` without
  `retryLabel`. They now supply the existing localized label.
- Prompt-move destinations, Linux components/storage/idle controls, Git repair and permission settings could disable
  rows without saying why. They now supply a working or unavailable explanation.
- Host revocation/forget and voice-download consent titles violated the confirmation-question rule. They now use
  localized questions. The rule accepts fullwidth `？`, already used by Chinese/Japanese translations.
- Development-service output refresh dropped its callback while refreshing and left the action enabled. It now keeps
  the callback and uses working state.
- New-conversation agent selection now uses kit cards, led by Add an agent, with large vector marks, selected checks
  and a single column at large text. It uses existing setup navigation and actual server agent choices. The matte
  cards replace the iconless/noisy Agent and empty Agents rows. The reference four-line prompt area is retained.

## Phone-server address contract

Native defaults to **4098**, preview to **4100**; Flutter's **4097** is no longer considered the native owned server.
Foreground startup checks the preferred port, tries the bounded native fallback range `4100..4199`, persists the
selected port in app-private recovery preferences and updates the same owner profile URL. Scripts, health probes,
owned-server classifiers, native engine metadata, cold/idle/scheduled reconstruction and setup all use that selection.
Cold restoration keeps the saved address. Saved development services reserve that exact port. Bind classification reads
only up to 8 KiB appended after the current invocation's log offset, reset before a new attempt. Setup and direct This
phone controls carry the typed port fact. A bind collision is presented as the localized sentence
“Another OpenCode server is already using port N on this phone.” Raw `start_failed` / `start_unavailable` journal values
map to plain fallback copy. No external listener is stopped or adopted.

Coordinator checks still needed: compile the candidate, open every repaired picker/sheet, setup editor command step,
demo chat, normal chat error states, busy maintenance rows and permission settings; check Arabic/Chinese/Japanese
confirmations; check large text/card layout; keep Flutter's 4097 listener running while starting native/preview; occupy
preferred native ports and check fallback/profile/restart agreement; provoke a bind race and verify the plain sentence.
Obtain the first causal picker stack trace for the unresolved initializer report.

## File-by-file change list

Paths are repository-relative; each row names the final behavior change. Test-source changes are unexecuted.

| File | Change |
| --- | --- |
| [native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt](../../native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt) | Persist/select a free native port and restore the saved port; classify startup bind conflicts. |
| [native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipe.kt](../../native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipe.kt) | Reconstruct restoration command with selected native port. |
| [native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineNative.kt](../../native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneEngineNative.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServerPortBusy.kt](../../native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ServerPortBusy.kt) | Typed app-authored port collision exception without server output. |
| [native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipeTest.kt](../../native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeServerRecipeTest.kt) | Update native-port contract expectations in source; not run. |
| [native/android/termux/src/main/kotlin/io/github/eslamasabry/oc/android/termux/MigrationProfileSwitcher.kt](../../native/android/termux/src/main/kotlin/io/github/eslamasabry/oc/android/termux/MigrationProfileSwitcher.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/build.gradle.kts](../../native/androidApp/build.gradle.kts) | Set native 4098 and preview 4100 defaults. |
| [native/androidApp/src/benchmark/kotlin/io/github/eslamasabry/opencode_mobile/proof/StreamProofActivity.kt](../../native/androidApp/src/benchmark/kotlin/io/github/eslamasabry/opencode_mobile/proof/StreamProofActivity.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ManageSpaceActivity.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/ManageSpaceActivity.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/OcApplication.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/OcApplication.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetOverlayActivity.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetOverlayActivity.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AgentsHubWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AgentsHubWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidAppGraph.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidAppGraph.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/GitWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/GitWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/InteractiveScheduleAdmission.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/InteractiveScheduleAdmission.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxVoiceWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxVoiceWiring.kt) | Use the selected port in scripts/probes; update owner URL before launch; carry busy-port failure. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PhoneSetupWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PhoneSetupWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ProjectScanWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ProjectScanWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ProjectStorageWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ProjectStorageWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/QuotaWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/QuotaWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/RuntimeOwnerWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/RuntimeOwnerWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SchedulesWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SchedulesWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SetupWiring.kt](../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SetupWiring.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/androidApp/src/test/kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxVoiceWiringTest.kt](../../native/androidApp/src/test/kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxVoiceWiringTest.kt) | Update native-port contract expectations in source; not run. |
| [native/androidApp/src/test/kotlin/io/github/eslamasabry/opencode_mobile/graph/SetupWiringTest.kt](../../native/androidApp/src/test/kotlin/io/github/eslamasabry/opencode_mobile/graph/SetupWiringTest.kt) | Update native-port contract expectations in source; not run. |
| [native/desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/DesktopAppGraph.kt](../../native/desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/DesktopAppGraph.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/Main.kt](../../native/desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/Main.kt) | Provide graph-owned real redactor/secret sink at window root. |
| [native/engine/integrations-quota/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/InAppQuotaCollector.kt](../../native/engine/integrations-quota/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/InAppQuotaCollector.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/integrations-quota/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/InAppQuotaGatewayTest.kt](../../native/engine/integrations-quota/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/InAppQuotaGatewayTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/integrations-quota/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/QuotaCollectorHostTest.kt](../../native/engine/integrations-quota/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/integrations/quota/QuotaCollectorHostTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/lifecycle/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/lifecycle/PhoneOwnServer.kt](../../native/engine/lifecycle/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/lifecycle/PhoneOwnServer.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/lifecycle/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/lifecycle/PhoneOwnServerTest.kt](../../native/engine/lifecycle/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/lifecycle/PhoneOwnServerTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/linuxsetup/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/linuxsetup/SetupFailures.kt](../../native/engine/linuxsetup/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/linuxsetup/SetupFailures.kt) | Carry busy-port fact and map raw startup markers to plain words. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinLinuxBridge.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinLinuxBridge.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/devservers/ListeningPorts.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/devservers/ListeningPorts.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/messages/SetupMessages.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/messages/SetupMessages.kt) | Carry busy-port fact and map raw startup markers to plain words. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/procs/ListeningPorts.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/procs/ListeningPorts.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/services/kept/KeptServices.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/services/kept/KeptServices.kt) | Reserve the exact selected native port against saved development services. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupContract.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupContract.kt) | Carry busy-port fact and map raw startup markers to plain words. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupEngine.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupEngine.kt) | Carry busy-port fact and map raw startup markers to plain words. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupPlanning.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/setup/SetupPlanning.kt) | Carry busy-port fact and map raw startup markers to plain words. |
| [native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/switching/ManagedLocalServer.kt](../../native/engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/switching/ManagedLocalServer.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinRecoveryBridgeTest.kt](../../native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinRecoveryBridgeTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinScriptsTest.kt](../../native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/bridge/BuiltinScriptsTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/procs/ListeningPortsTest.kt](../../native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/procs/ListeningPortsTest.kt) | Update native-port contract expectations in source; not run. |
| [native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/switching/ManagedRuntimeSwitchTest.kt](../../native/engine/runtime/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/runtime/switching/ManagedRuntimeSwitchTest.kt) | Update native-port contract expectations in source; not run. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/handoff/StateHandoffSheet.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/handoff/StateHandoffSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/HandoffSheet.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/HandoffSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/SessionImportSheet.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/ui/SessionImportSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/inbox/ui/ChatInboxSlot.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/inbox/ui/ChatInboxSlot.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/notes/SessionNoteScreen.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/notes/SessionNoteScreen.kt) | Supply required localized retry label. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/queuemove/QueueMoveSheet.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/queuemove/QueueMoveSheet.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/relations/ui/SessionRelationsScreen.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/relations/ui/SessionRelationsScreen.kt) | Supply required localized retry label. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/sessiontransfer/ui/SessionTransferSheet.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/sessiontransfer/ui/SessionTransferSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt](../../native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatScreen.kt) | Overflow for extra bar actions; localized load-error retry label. |
| [native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/NewConversationScreen.kt](../../native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/NewConversationScreen.kt) | Agent cards and Add entry; remove duplicate picker scroll; retain reference prompt space. |
| [native/feature/devservices/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/devservices/ui/DevServicesScreen.kt](../../native/feature/devservices/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/devservices/ui/DevServicesScreen.kt) | Sheet-owned output scrolling; retain refresh callback with working state. |
| [native/feature/files/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/files/ui/FolderScreen.kt](../../native/feature/files/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/files/ui/FolderScreen.kt) | Overflow menu for actions beyond the bar limit. |
| [native/feature/git/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/git/servertools/GitServerToolsPanel.kt](../../native/feature/git/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/git/servertools/GitServerToolsPanel.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/git/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/git/ui/GitRepairPanel.kt](../../native/feature/git/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/git/ui/GitRepairPanel.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/integrations/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/integrations/ui/HostPanel.kt](../../native/feature/integrations/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/integrations/ui/HostPanel.kt) | Use question titles for revoke/forget confirmations. |
| [native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/manager/LinuxPorts.kt](../../native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/manager/LinuxPorts.kt) | Use the exact selected native address for ownership, routing, probing or restoration. |
| [native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxComponentsPanel.kt](../../native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxComponentsPanel.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxIdlePanel.kt](../../native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxIdlePanel.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxLocalStoragePanel.kt](../../native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxLocalStoragePanel.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxSetupPanel.kt](../../native/feature/linux/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/linux/ui/LinuxSetupPanel.kt) | Render localized busy-port startup sentence. |
| [native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/PhoneSources.kt](../../native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/PhoneSources.kt) | Carry a typed startup busy-port fact to the direct phone page. |
| [native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/ThisPhoneAnswer.kt](../../native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/presentation/ThisPhoneAnswer.kt) | Show the busy-port answer while stopped; clear that presentation once running. |
| [native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/ui/PhoneWords.kt](../../native/feature/phone/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phone/ui/PhoneWords.kt) | Render the direct phone busy-port answer with the localized sentence. |
| [native/feature/phone/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/phone/PhoneServerStateTest.kt](../../native/feature/phone/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/phone/PhoneServerStateTest.kt) | Add source regression for busy-port answer and running-state precedence; not run. |
| [native/feature/phonesetup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phonesetup/ui/PhoneSetupScreen.kt](../../native/feature/phonesetup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phonesetup/ui/PhoneSetupScreen.kt) | Render localized busy-port startup sentence. |
| [native/feature/phonesetup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phonesetup/ui/SetupJourneyScreen.kt](../../native/feature/phonesetup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/phonesetup/ui/SetupJourneyScreen.kt) | Render localized busy-port startup sentence. |
| [native/feature/plans/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/plans/ui/PlansScreen.kt](../../native/feature/plans/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/plans/ui/PlansScreen.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt](../../native/feature/projects/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/projects/ui/ProjectWorkspaceSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt](../../native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/KeptPromptsScreen.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/ServerListsScreens.kt](../../native/feature/prompts/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/prompts/ui/ServerListsScreens.kt) | Supply required localized retry label. |
| [native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/ServerQueueScreen.kt](../../native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/ServerQueueScreen.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/TailscaleSetupSheet.kt](../../native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/TailscaleSetupSheet.kt) | Use sheet-owned scrolling; replace unbounded lazy workspace list with keyed sections where applicable. |
| [native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/PermissionSettingsScreen.kt](../../native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/PermissionSettingsScreen.kt) | Supply reasons for disabled working/unavailable rows. |
| [native/feature/setup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/setup/ui/SetupScreen.kt](../../native/feature/setup/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/setup/ui/SetupScreen.kt) | Render localized busy-port startup sentence. |
| [native/feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/ui/TerminalScreen.kt](../../native/feature/terminal/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/terminal/ui/TerminalScreen.kt) | Overflow menu for actions beyond the bar limit. |
| [native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/KitChatEnvironment.kt](../../native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/KitChatEnvironment.kt) | Provide chat redactor/work context and register field secrets with the same Redactor. |
| [native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitAgentChoice.kt](../../native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/choice/KitAgentChoice.kt) | Responsive matte kit agent cards with vector icons and selected semantics. |
| [native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitConfirm.kt](../../native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/modal/KitConfirm.kt) | Accept fullwidth question marks in localized confirmation titles. |
| [native/ui/words/src/commonMain/composeResources/values-ar/strings_common.xml](../../native/ui/words/src/commonMain/composeResources/values-ar/strings_common.xml) | Localized busy-port sentence. |
| [native/ui/words/src/commonMain/composeResources/values-ar/strings_composer.xml](../../native/ui/words/src/commonMain/composeResources/values-ar/strings_composer.xml) | Localized voice-setup consent question. |
| [native/ui/words/src/commonMain/composeResources/values-ar/strings_host_security.xml](../../native/ui/words/src/commonMain/composeResources/values-ar/strings_host_security.xml) | Localized revoke/forget confirmation questions. |
| [native/ui/words/src/commonMain/composeResources/values-zh/strings_composer.xml](../../native/ui/words/src/commonMain/composeResources/values-zh/strings_composer.xml) | Localized voice-setup consent question. |
| [native/ui/words/src/commonMain/composeResources/values/strings_common.xml](../../native/ui/words/src/commonMain/composeResources/values/strings_common.xml) | Localized busy-port sentence. |
| [native/ui/words/src/commonMain/composeResources/values/strings_composer.xml](../../native/ui/words/src/commonMain/composeResources/values/strings_composer.xml) | Localized voice-setup consent question. |
| [native/ui/words/src/commonMain/composeResources/values/strings_host_security.xml](../../native/ui/words/src/commonMain/composeResources/values/strings_host_security.xml) | Localized revoke/forget confirmation questions. |
