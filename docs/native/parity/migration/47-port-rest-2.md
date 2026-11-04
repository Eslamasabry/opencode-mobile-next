# Migration 47 — Flutter platform, Linux and protocol (part 2)

Counts: **already there 17 · ported now 0 · not needed 25 · blocked 0** (42/78 reviewed).

Base: `b6c30a59983a145a4d9cf0bcc3d1ec0295c0d6bc`, branch `p12x/47-port-rest-2`.
Implementation review only; no tests or device qualification. Binding design: `docs/design/native-rewrite-2026-11-01/plan/01-architecture-decision.md` and `03-architecture-modules.md`.

- `lib/network/browser_bindings.dart` — not needed: DOM fetch/WebSocket JS interop; the binding native design targets Android and compiles desktop, with no browser product (01 §1, §7).
- `lib/network/platform_dio.dart` — not needed: Dart conditional adapter selection and Dio stream detection; native Ktor streaming is configured directly in `native/core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/Http.kt:160`.
- `lib/network/platform_dio_io.dart` — not needed: an empty Dio configurator; native owns its OkHttp/Ktor client directly.
- `lib/network/platform_dio_stub.dart` — not needed: an empty fallback for Dart targets with neither IO nor JS; there is no corresponding native target.
- `lib/network/platform_dio_web.dart` — not needed: browser Fetch streaming, CORS and AbortController adapter; native transports use sockets, not DOM APIs.
- `lib/network/platform_websocket_stub.dart` — not needed: unsupported-Dart-target fallback; the native Android/JVM socket implementation replaces conditional Dart selection.
- `lib/network/platform_websocket_web.dart` — not needed: DOM WebSocket adapter and browser-origin error explanations; no browser product is in the native design.
- `lib/network/transport_failure_stub.dart` — not needed: returns false because a non-IO Dart target has no resolver details; native JVM has typed resolver failures.
- `lib/network/transport_failure_web.dart` — not needed: browser-only resolver opacity fallback; native JVM has typed resolver failures.
- `lib/orchestration/adapters/fixture/project_fixture/advance.dart` — not needed: simulated task ticks, fabricated usage/diffs and simulated merge/promotion receipts belong to the AI Team demo; AI Team follows the switch under binding 01 §12 D6, and no fake runtime is ported.

- `lib/termux/git_shell.dart` — not needed: External-Termux Git execution is deliberately retired with ADR 01 D7 migrate-out-only; the replacement phone Git shell runs in the built-in Ubuntu with real profile fencing at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/BuiltinGitShell.kt:27`, selected at `GitWiring.kt:174` (no Termux host management).
- `lib/termux/scripts/aiteam_script.dart` — not needed: AI Team script installation, detached verbs, logs and projects belong to the deferred AI Team and retired Termux host paths (ADR 01 D6/D7); migrate-out never starts or installs a team.
- `lib/termux/scripts/install_and_serve_script.dart` — not needed: Starts/repairs a managed Termux server and stores its credential there; ADR 01 D7 and plan 07 X1 retire that host-management journey while retaining migration only.
- `lib/termux/scripts/local_agents_script.dart` — not needed: Installs, starts, signs into and removes Claude/Paseo inside external Termux Ubuntu; ADR 01 D7 retires Termux as runtime host, while phone agents use the built-in Linux host.
- `lib/termux/scripts/project_scripts.dart` — not needed: Creates folders and arms recovery for a managed Termux server; ADR 01 D7/plan 07 X1 retire Termux host management, and migrate-out only reads existing folders.
- `lib/termux/scripts/restart_script.dart` — not needed: Restarts/stops/reclaims ownership of a managed Termux server; ADR 01 D7/plan 07 X1 deliberately remove these management actions.
- `lib/termux/scripts/script_support.dart` — not needed: Constants and shell quoting for the retired Termux host scripts; migration owns its separate transport and does not need the server-manager wrapper.
- `lib/termux/scripts/setup_base_script.dart` — not needed: Bootstraps a new Termux Ubuntu container; ADR 01 D7/plan 07 X1 exclude new Termux installations, keeping read-only source inspection and migrate-out instead.
- `lib/termux/scripts/status_scripts.dart` — not needed: Reports managed Termux installation/setup/storage/diagnostics state; ADR 01 D7 removes its setup and running pages, with migrate-out using its own inspection contract.
- `lib/termux/scripts/tools_script.dart` — not needed: Storage scan/cache cleanup and process management target external Termux; those pages are explicitly dropped by plan 07 F17/X1 under ADR 01 D7, while built-in Linux tools remain separate.

- `lib/orchestration/adapters/fixture/project_fixture/apply.dart` — not needed: simulated AI Team commands, fabricated commits/checks and demo task transitions; AI Team is deferred by binding 01 §12 D6.
- `lib/orchestration/adapters/fixture/project_fixture/helpers.dart` — not needed: demo plan generation, synthetic findings, simulated review/merge requests and fixture bookkeeping for deferred AI Team (01 §12 D6).
- `lib/orchestration/adapters/fixture/project_fixture/seed.dart` — not needed: canned servers, roles, projects, spend and timeline for a simulated AI Team workspace; no real native runtime may be seeded with this data (01 §12 D6).
- `lib/platform/browser_context_io.dart` — not needed: the constant secure-context answer is Dart conditional-import plumbing; native Android storage has no browser-origin security context (ADR §1, §7.3).
- `lib/platform/browser_context_web.dart` — not needed: window.isSecureContext serves Flutter web, a platform outside the native rewrite's Android/desktop/iOS design (ADR §1).

- `lib/update/app_release.dart` — already there: `native/engine/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/updates/AppRelease.kt:22` parses published releases, selects ABI/universal APKs, summarizes notes and reads SHA256SUMS.
- `lib/update/app_update_controller.dart` — already there: `native/engine/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/updates/AppUpdates.kt:81` restores the update finding, throttles checks, downloads/cancels/verifies/installs and resumes after install permission; the Shorebird-only patch phase ends with Flutter per ADR 01.
- `lib/update/app_update_platform.dart` — already there: `native/android/tiles/src/main/kotlin/io/github/eslamasabry/oc/android/tiles/updates/AndroidUpdates.kt:150` reads APK identity/signers/ABIs and installation permission, and streams PackageInstaller outcomes directly without a Flutter channel.
- `lib/update/app_update_source.dart` — already there: `native/android/tiles/src/main/kotlin/io/github/eslamasabry/oc/android/tiles/updates/AndroidUpdates.kt:67` fetches the release/feed, resumes partial downloads with Range, restarts on 416 and honors cancellation; `native/engine/updates/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/updates/AppUpdates.kt:274` checks release-file trust before downloading.
- `lib/voice/automatic_pack.dart` — already there: `native/engine/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/voice/models/PackSupport.kt:66` keeps an eligible installed selection, otherwise picks the highest supported physical-RAM tier and refuses unknown RAM/no microphone.
- `lib/voice/natural_voice_ui.dart` — already there: `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/VoiceSettingsScreen.kt:321` offers phone/natural voices with verified-download progress, cancellation and removal confirmation; `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/presentation/VoiceSettingsPresenter.kt:321` wires the choices to persistent model commands.
- `lib/voice/voice_settings_parts.dart` — already there: `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/VoiceSettingsScreen.kt:167` shows transcription language and opt-in auto-send; `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/VoiceSettingsScreen.kt:372` offers all language choices with follow-app/detect explanations and persistent engine actions.
- `lib/paseo/gateway/provider_import.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoProviderImport.kt:51` validates/sorts bounded Claude conversation imports and reads imported agents; `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ProtocolsWiring.kt:257` supplies the computer import page.
- `lib/paseo/gateway/providers.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoGateway.kt:897` keeps draft/provider/model/thinking selections, canonical model aliases, and successful model changes if thinking fails; `:1025` retries warming provider snapshots without caching loading results.
- `lib/paseo/gateway/questions.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/requests/PaseoRequestBook.kt:467` validates provider-owned native questions/plans, bounded choices and answer keys, and sends exact implement/reject actions with stale-revision/epoch guards through the gateway question API.
- `lib/paseo/gateway/subagents.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoSubAgents.kt:64` handles child upsert/timeline/remove, nested parent links and late tool links; `:169` reads child histories and `PaseoGateway.kt:664` refuses replies to read-only subagents.
- `lib/paseo/gateway/terminals.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoTerminals.kt:54` implements scoped list/create/rename/resize/close and `:97` subscribes before slot assignment, restores visible output, decodes split UTF-8 and closes on disconnect; `PaseoOperations.kt:54` exposes the real terminal gateway.
- `lib/paseo/gateway/uncertain_start.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoUncertainStart.kt:32` reconciles an unanswered first send by bounded agent/timeline reads and exact first-message correlation, adopting once or releasing an unused draft without resending.
- `lib/paseo/gateway/workspace_base.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoWorkspace.kt:45` fences responses against location changes, strips daemon failure copy and normalizes relative paths for the wired workspace operations.
- `lib/paseo/gateway/worktrees.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoWorkspace.kt:220` lists/creates/removes daemon worktrees, validates checkout failures and names ready worktrees; `PaseoOperations.kt:41` exposes those operations and leaves unsupported reset unavailable.
- `lib/paseo/gateway/background_tasks.dart` — already there: `native/protocol/paseo/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/paseo/gateway/PaseoBackgroundTasks.kt:151`: runtime-gap, running-task and failed-turn facts feed `PaseoGatewayFeedPort.kt:62`; stream/history observations update the book.
- `lib/platform/clipboard_image.dart` — already there: `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidComposerFiles.kt:146` wires the real clipboard reader; `AndroidComposerFiles.kt:456` checks declared image MIME cheaply and reads only on paste, returning a typed oversized-image failure with bounded IO.

## Compile-only checkpoints

Batch 1: documentation only; Gradle skipped because this commit changes no Kotlin or Gradle file. No tests written or executed. No APK, push or release.

Batch 2: ten retired Termux-path dispositions; documentation only, so Gradle skipped.

Batch 3: seven existing update/voice implementations and five fixture/browser dispositions; documentation only, so Gradle skipped.

Batch 4: existing Paseo and clipboard behavior; restores seven omitted batch-3 evidence lines so the ledger counts match its rows. Documentation only; Gradle skipped.
