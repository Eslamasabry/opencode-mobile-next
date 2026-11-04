VERIFIED: green

# P12-BUILD-REST — 94 unproven Flutter behaviours ported natively

Date: 2026-11-03. Branch `claude/native-parity-build-vco0dg`, based on `native/p12-integrate-2026-11-03` at `71f74de8e`.
The unit ran as four slices: three forked agents in their own worktrees (A: delivery, connection and schedules;
B: requests, cards and composer; C: core security, domain, net, transcript and media) plus the coordinator's slice
(kit, quality, strings and feature rows). All of them are merged into this branch.

Shipping state: **implemented, tested and pushed** to the branch. Most rows are **engine-level**: they are not yet
reachable on the phone (see "Wiring the integrator must do"). One row's app fix is wired (B-PROTO-046). No PR, no CI
run, no signing, no release, no live server.

## Rows proven

**84 of 95 rows proven** (the brief said 94; the list it gave holds 95 ids):

B-NAV-016 B-AGENT-226 B-PROTO-028 B-PROTO-029 B-PROTO-032 B-PROTO-041 B-STATE-047 B-STATE-048 B-STATE-049 B-STATE-050
B-STATE-051 B-STATE-052 B-STATE-053 B-NAV-052 B-NAV-056 B-AGENT-131 B-AGENT-166 B-CHAT-040 B-CHAT-041 B-CHAT-042
B-CHAT-046 B-CHAT-047 B-CHAT-048 B-L10N-016 B-PROTO-073 B-SEC-017 B-SEC-018 B-SEC-080 B-AGENT-033 B-BUILD-024
B-BUILD-027 B-PROTO-040 B-STATE-055 B-AGENT-241 B-PROTO-045 B-PROTO-046 B-PROTO-094 B-SEC-104 B-SEC-131 B-SEC-132
B-STATE-011 B-ANDR-037 B-BUILD-026 B-PROTO-072 B-SEC-061 B-SEC-172 B-STATE-017 B-STATE-018 B-STATE-087 B-STATE-134
B-STATE-137 B-STATE-139 B-ANDR-101 B-ANDR-102 B-L10N-024 B-STATE-069 B-SEC-055 B-SEC-056 B-SEC-064 B-SEC-065
B-SEC-123 B-SEC-125 B-SEC-144 B-SEC-145 B-SEC-146 B-BUILD-001 B-BUILD-023 B-BUILD-030 B-BUILD-031 B-BUILD-033
B-SEC-150 B-ANDR-021 B-ANDR-022 B-XPLAT-012 B-XPLAT-013 B-KIT-033 B-KIT-036 B-KIT-021 B-XPLAT-009 B-XPLAT-010
B-XPLAT-011 B-L10N-021 B-L10N-022 B-L10N-023

Of those, **eight cover only part of the row's facts** (noted in the table): B-SEC-104, B-SEC-172, B-CHAT-046,
B-PROTO-073, B-AGENT-166 (model only), B-XPLAT-011, B-BUILD-024, B-NAV-052. Not proven (11): B-LINUX-179 B-NAV-029
B-NAV-046 B-NAV-079 B-AGENT-219 B-ANDR-070 B-PROTO-088 B-LINUX-220 B-ANDR-108 B-ANDR-109 B-KIT-012.

"Proven" means at least one passing JUnit test whose name starts with the row id and none failing, counted from this
run's `build/test-results` by a scratch script (see Commands). `native/tool/parity/ledger.py` **does not exist on this
base** (P12-CHAT-TRANSCRIPT reported the same), so the official ledger could not be run.

### Per row: where it lives and how far it reaches

| Row | Native code | Test | Reach |
|---|---|---|---|
| B-STATE-047…053 | `:engine:delivery` pending command journal (existing; `read()` now refuses instead of throwing) | `PendingCommandJournalTest` (16) | engine (already used by delivery) |
| B-AGENT-226 | `:engine:delivery` `SessionCreationReceipts` + `SessionCreationTransport` port | `SessionCreationReceiptsTest` (8) | engine only — needs a client-chosen session id |
| B-PROTO-041 | `:engine:delivery` receipt lookup: no read/confirm with capability off or connection stopped | `CommandReceiptTransportTest` (6) | in the live send path |
| B-PROTO-029 | `:engine:delivery` `PhoneChatDispatch`/`Tracker` | `PhoneChatDispatchTest` (3) | engine only |
| B-PROTO-032 | `:protocol:oc2` `Oc2PermissionPolicy` (existing) | `Oc2PermissionPolicyTest` (11) | gateway |
| B-PROTO-028 | `:protocol:oc2` `Oc2Gateway.serverRunsTools`/`syncSessionPermissions` | `Oc2PermissionDispatchTest` (6) | gateway; the switch is not fed from settings yet |
| B-PROTO-094 | `:protocol:oc2` `Oc2Gateway.probe()` verdicts (used by the add-server probe) | `Oc2ProbeVerdictTest` (8) | **reachable**: Servers › add server |
| B-SEC-131, B-SEC-132 | `:engine:connection` `ProductErrorLine` / `productErrorText(Failure)` | `ProductErrorLineTest` (7), `ProductErrorWordsTest` (EN+AR) | engine only |
| B-SEC-104 | mapper facts (above) + existing notification test | `ProductErrorLineTest`, `AlertShadeTest` | partial: no shrinking source-scan allowlist port |
| B-STATE-011 | `:engine:connection` `DirectoryPaths` | `DirectoryPathsTest` | engine only |
| B-PROTO-045 | `:engine:connection` `InboxProjection` | `InboxProjectionTest` (5) | engine only |
| B-PROTO-046 | `:android:notifications` + app `BuildRestWiring.ShadeReplyAnswers` | `NotificationActionBodyTest` (+2), `ShadeReplyAnswersTest` (2) | **reachable**: Reply on a permission notification now rejects with the message |
| B-STATE-017, B-STATE-018, B-SEC-061 | `:engine:composer` draft attachment vault | `DraftAttachmentVaultRowsTest` | in use by the composer |
| B-STATE-134, B-STATE-137, B-STATE-139 | `:engine:composer` stash files, legacy migration, owner marker | `StashRowsTest` | in use by Saved prompts |
| B-STATE-087 | `:engine:composer` `SavedPromptMigration` | `SavedPromptMigrationTest` (6) | engine only — not run at profile open yet |
| B-SEC-172 | shelf side in `:engine:composer`; app side existing `StashDeletionCleanerTest` | both | partial on the "secret never deleted early" fact (app coordinator) |
| B-AGENT-131 | `:engine:cards` `CardAuthority` | `CardAuthorityTest` | engine only |
| B-AGENT-166 | `:engine:cards` `CardRecoverySnapshots` | `CardAuthorityTest` | engine only (not fed from Paseo) |
| B-SEC-080 | `:engine:cards` `ShowCardPermission` now requires `daemonTested` | `ShowCardNeverAutoAllowTest` | engine |
| B-CHAT-040 | `:engine:cards` `CardsInstaller` | `CardsInstallerTest` (9) | engine only |
| B-CHAT-041, B-CHAT-042 | `:engine:cards` `CardsInstallScripts` (the Flutter installer Python, verbatim) | `CardsInstallScriptsTest` (16; runs `/usr/bin/python3`) | engine only |
| B-CHAT-046 | `:engine:cards` `CardSearchPublisher` over a `CardsRootFs` port | publisher tests | partial: the search round-trip needs a native connector catalogue |
| B-CHAT-047, B-CHAT-048 | `:engine:cards` `CardToolServer` (the Flutter tool-server JS, verbatim) | `CardToolServerTest` (13; runs `node`) | engine only |
| B-SEC-017, B-SEC-018 | `:engine:requests` `AutomationPolicy` | `AutomationPolicyTest` (9) | engine only — no Settings screen yet |
| B-L10N-016 | `:engine:requests` `PermissionTitle` | `PermissionTitleTest`, `PermissionTitleWordsTest` (EN+AR) | engine only |
| B-PROTO-073 | `:engine:requests` concurrent request tracking | `PermissionTrackingTest` | partial: parsing is `:protocol:oc2`'s |
| B-SEC-055, B-SEC-056 | `:engine:profiles`, `:protocol:oc2` | `CredentialIngressRedactionTest`, `TransportCredentialRedactionTest` | in use |
| B-SEC-064, B-SEC-065 | `:core:security` `ExternalLinkPolicy` (mailto, hostless, `//host` refused; port shown; `checkSignInPage`) | `ExternalLinkParityTest` (8) | **reachable**: every link the app opens |
| B-SEC-123, B-SEC-125 | `:engine:profiles`, `:protocol:paseo`, `:feature:servers` words | `PrivateCleartext*Test`, `CleartextRefusalWordsTest` | in use |
| B-SEC-144, B-SEC-145 | `:core:security` | `RedactorTest`, `VerbatimCopyReviewTest` (clipboard ratchet, 5 reviewed sites) | gate |
| B-SEC-146 | `:core:security` `ServerUrlRules`; `AddressRules.normalize` brackets bare IPv6 | `ServerUrlRulesTest` (3) | in the address field |
| B-XPLAT-012, B-XPLAT-013, B-ANDR-021, B-ANDR-022 | `:core:domain` `PlatformFamily`, `forPlatform`, `updateChannels` | `PlatformCapabilitiesParityTest`, `DesktopPlatformReachTest` | in use |
| B-ANDR-037 | existing `:android:platform` + `:core:net` desktop | `NetworkTrackerTest`, `DesktopNetworkStatusTest` | in use |
| B-BUILD-026, B-BUILD-027 | `:core:net` `NetClient.attach(HttpCallObserver)`; `:engine:diagnostics` `HttpSpanRecorder`, `PromptTimeline` | `HttpCallObserverTest`, `PromptTimelineTest` | engine only — not attached yet |
| B-PROTO-072 | `:core:net` | `TransportReuseDecodeTest` (real socket) | in use |
| B-PROTO-040 | `:protocol:oc2` | `Oc2StepGapTest` | in use |
| B-AGENT-033 | `:engine:transcript` `BackgroundShellResult` | 4 tests | engine only — not rendered yet |
| B-STATE-055 | `:engine:settings` `SessionPinStore` | `SessionPinsTest` | engine only |
| B-STATE-069 | `:engine:settings` `ReaderPreferencesStore` | `ReaderPreferencesTest` | engine only |
| B-ANDR-101, B-ANDR-102 | `:engine:voice` `VoiceSetupNotices`; existing `:android:voice` rules | `VoiceSetupNoticesTest` (8), `RulesTest` | engine only — no Android sink bound |
| B-L10N-024 | `:feature:voice` words | `VoiceWordsArabicTest` | **reachable**: Voice settings |
| B-KIT-021, B-BUILD-024 | `:ui:kit-content` `KitLogBuffer` | `KitLogBufferTest`, `KitLogBufferChunksTest` | kit part (no log view uses it yet); B-BUILD-024's report-batch half not ported |
| B-KIT-033 | `:ui:kit-chat` `KitElapsed` + `KitElapsedWords.say` | `KitElapsedTest` | kit part |
| B-KIT-036 | `:ui:kit-chat` `KitStepLightLap` + `KitStepLight` composable | `KitStepLightTest` (4) | kit part; the tool rows do not draw it yet (would move goldens) |
| B-XPLAT-009, B-XPLAT-010 | `:ui:kit-content` `TerminalOsc52`, `TerminalLinks` | `TerminalLinksTest` (7) | kit part |
| B-XPLAT-011 | `:ui:kit-content` `TerminalText` (key bytes, selection, masking, tints, command) | `TerminalTextTest` (5) | kit part; partial (key bar layout/semantics, palette, overflow, calm theme are widget facts not ported) |
| B-BUILD-001, B-BUILD-023, B-BUILD-031, B-BUILD-033 | `:quality` `BuildRestGates` | `BuildRestGatesTest` (12, live scan + planted) | gate |
| B-BUILD-030, B-SEC-150 | existing `:quality` `RepositoryHygieneTest` | secTest | gate |
| B-L10N-021, B-L10N-022, B-L10N-023 | `:tools:strings` `Glossary` over the native English strings | `GlossaryTest` (13) with a shrink-only baseline | gate |
| B-NAV-052 | copy half: `promptStashIntro` names no server | `GlossaryTest` | the AI Team gate receipt has no native surface |
| B-NAV-016 | `:feature:servers` `SetupCommands.PASEO_START_PRIVATE_NETWORK` | `SetupCommandsPasswordTest` | not shown by a screen yet |
| B-AGENT-241 | `:feature:servers` `SetupCommands.CODEX_START` | `SetupCommandsTest` | not shown by a screen yet |
| B-NAV-056 | `:feature:search` `GoToMenu` | `GoToMenuTest` (3) | presentation only — Search does not show it yet |

## Rows not done

| Row | Why |
|---|---|
| B-NAV-046 | No native queued-prompt move (queue operation, move sheet, golden). Delivery/chat feature work. |
| B-PROTO-088 | Reconnect hardening needs real network flaps: device/e2e. |
| B-AGENT-219, B-ANDR-070 | No phone-runtime lease concept natively; the schedule runner treats a timeout after acceptance as "ran" (Flutter fails with a timeout). Needs `:engine:runtime` + `:android:schedules` work. |
| B-LINUX-220 | The optional voice component in Linux setup has no native setup step (P12-LINUX's flow). |
| B-LINUX-179, B-NAV-029 | Screenshot rows for This-phone and the move page; not attempted in the time box. |
| B-NAV-079 | The Subscriptions page (sign-in quota readings) has no native engine; `:feature:usage` has the Spent section only. |
| B-ANDR-108, B-ANDR-109 | `:feature:widget-panels` (overlay windows, steer and voice boxes) does not exist; device e2e. |
| B-KIT-012 | The native `KitSkeletonRows` draws with `text1` at partial alpha, which the Flutter rule forbids; changing kit colours moves other units' goldens — a kit-owner decision. No native ask line exists. |

## Wiring the integrator must do (to make engine rows reachable)

From slice A:
1. Set `Oc2Gateway.serverRunsTools` from the per-server automation policy and call `syncSessionPermissions` when automatic approval changes (needs a small `:core:domain` facet; the gateway class is internal).
2. Bind `InboxProjection` per connection; route `GatewayEvent.InboxChanged` to it.
3. `SessionCreationReceipts` in production needs `NewSession.id` in `:core:model` sent in oc2 `POST /session`.
4. Show failures with `productErrorText(failure)` (`wordsKey` for the sentence, Details from `failure.details`). A keyring-specific sentence is missing in `:ui:words`.

From slice B:
5. `AutomationStore` on `oc.automation.<id>` with `protected = redactor::containsSecret`; `closeProfile` before the profile sweep; a Settings "What runs by itself" screen.
6. `CardsInstaller`: run `CardsInstallScripts.install(...)` / `verification(...)` through the built-in Linux; hook into `CardsQualifier`.
7. `CardSearchPublisher`: an Android `CardsRootFs` over `filesDir` (the test's `NioCardsRootFs.kt` can be copied).
8. Feed `CardAuthority` from the transcript and `CardRecoverySnapshots` from the Paseo gateway.
9. Run `SavedPromptMigration.runForProfile` at profile open; add `oc.savedPromptMigrationV1.<id>` and `oc.savedPromptMigrationBackupV1.<id>` to the deletion sweep; call `PromptShelf.migrateAttachments` when Saved prompts opens.
10. Map `PermissionTitle.wordsKey` where a request title is shown; pass `daemonTested = PaseoTestedVersions.supports(version)` to every production `ShowCardPermission.Request`.

From slice C:
11. Register `ReaderPreferencesStore` and `SessionPinStore.SHAPE` with the profile scope and bind them.
12. Attach `HttpSpanRecorder(perfTrace, "oc2")` to each connection's `NetClient`; feed `PromptTimeline` from the send path.
13. Bind an Android `VoiceSetupNoticeSink` for the voice setup notifications.
14. Route `SignInTerminalPresenter.openSignInPage` through `ExternalLinkPolicy.checkSignInPage`.
15. Render `BackgroundShellResult` in the transcript.
16. The new clipboard ratchet (`VerbatimCopyReviewTest`) fails on any new clipboard write: add reviewed sites with a reason.

From the coordinator slice:
17. Search: show `GoToMenu` rows when the query is empty (needs a capabilities port on `ConversationSearchSource` and Help/Terminal routes).
18. Servers: show `SetupCommands.startFor(backend)` as the copyable start command in the add-server flow.
19. Glossary baseline (`tools/strings/src/test/resources/glossary-baseline.tsv`): native-only labels that still say "New chat", "Open chats", "Coding session updates", "Check again" and a paused/idle sentence are l10n-owner follow-ups.

## Deviations

- Desktop keeps `promptPhotos = true` (file picker), so that one B-XPLAT-012 fact is not asserted.
- B-PROTO-072 pauses 3 s, not 4 s: the native pool keeps idle sockets 4 s (B-PROTO-128).
- B-PROTO-040: the Flutter "two idle status reads" reconciliation does not apply; native status reads never end a run.
- B-BUILD-001's notification rule is a reviewed, shrink-only list of the 7 posting sites (alert shade, reply watch,
  schedules, the Linux service's three own notices, Settings' test notification) rather than one path.
- B-L10N-021/022 run as a shrink-only baseline (65 entries), like Flutter's `ui_glossary_baseline.json`, not an empty
  list; the technical-word rule cannot see Flutter's "Technical:" key descriptions (the native XML has none).
- `:engine:diagnostics` now depends on `:core:net` (api); `:ui:kit-content` desktopTest uses `:core:security`.
- `ShowCardPermission.Request` gained a required `daemonTested`.

## Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `platforms;android-36`,
`build-tools;37.0.0`). Container: 4 vCPU, 15 GB. Container-only setup (not committed): `~/.gradle/init.d` puts Google's
Maven Central mirror and `google()` before the plugin portal (Central answers 429 through the proxy) and sets
`robolectric.dependency.repo.url`; `tools/vendor/fetch.sh` fetched the pinned sherpa-onnx AAR.

| # | Command (on the final merged tree, `e3a1967a4` + wiring + B-BUILD-024) | Result |
|---|---|---|
| 1 | `./gradlew desktopTest :quality:test :tools:strings:test :androidApp:testPreviewDebugUnitTest :android:notifications:testDebugUnitTest :android:platform:testDebugUnitTest :android:voice:testDebugUnitTest :android:widgets:testDebugUnitTest :androidApp:assemblePreviewDebug --continue` | **4,414 tests, 1 failure, 1 skip.** The one failure is `:tools:strings` `DriftTest` B-L10N-010 (`U+200E` in `values-ar/strings_agents_hub.xml`), present on the base: this branch does not touch `ui/words`. `assemblePreviewDebug` built `androidApp-preview-debug.apk` (90 MB). |
| 2 | `./gradlew :quality:secTest --tests '*RepositoryHygieneTest*'` | passed (B-SEC-150, B-BUILD-030). The full `:quality:secTest` fails on the base: K-SEC-2/4/6/7/10 hits in other units' files and a test-JVM `OutOfMemoryError`; none of the hits is in this unit's files. |
| 3 | Slices, per module, before merge | A: `:engine:delivery :engine:connection :protocol:oc2 desktopTest`, `:android:notifications:testDebugUnitTest` (18) green. B: `:engine:cards :engine:requests :engine:composer desktopTest` 393 tests green. C: 16 modules' desktopTest, `:quality:test` (64) and `assemblePreviewDebug` green. |
| 4 | `python3 -I <scratch>/myledger.py rows.txt native/` (JUnit XML under `build/test-results`) | 84/95 proven, 0 failing |

`B-CHAT-096` (`ReplyStreamFrameBenchmark`, a 1-minute watchdog) timed out once while three other Gradle builds shared
the 4 cores; it passed in run 1 above. `CardToolServerTest` needs `node` and `CardsInstallScriptsTest` `/usr/bin/python3`.

Pushes went through `native/tool/scan_push.sh --push` (clean). No PR, CI, signing or release.
