# Native repair recovery and integration — 2026-11-02

Finish line: preserve and integrate the repairs, deliver a separate native preview APK, and prove saved server → connect → create/open conversation → send/stream → restart recovery on an emulator. Production signing, installation on the owner phone, public push, M0 merge and release are outside this preview.

Branch: `native/repair-integration-2026-11-02`, based on M0 `3bb7632ee56b0060e6ab3385748e60906545a8f4`.

## Recovery

The earlier fix runner could not update shared worktree Git metadata. Workers exported commits to bundles instead. Its unconditional forced worktree cleanup then removed two bundles stored under `native/build/`.

Recovered `chat` and `gates` from their temporary Git repositories. Eleven bundles are archived outside disposable worktrees in `/home/eslam/Storage2/wt/native-repair-artifacts-20261102/`, with SHA-256 hashes in `manifest.json`. All bundles passed `git bundle verify`. Their original histories are retained under local `recovered/<unit>` refs. Integration commits apply each verified final scoped diff. Original histories remain available under the recovered refs.

| Repair | Original tip |
|---|---|
| `gates` | `5df00d07cae6c04c35ec1e8f805b08141959729d` |
| `eng` | `9145f788f658e528b80edb9872dd47b31a62acce` |
| `compe` | `fe3ce1e944eed32c39e5aa9d535fe0f843b2a22b` |
| `kita1` | `aa7e9e00e08b1de22afb111197cfdbf0c23ef7cb` |
| `kita2` | `4e40992782e53a57d9a77ad711351622792268f8` |
| `notify` | `d27ed4a965a7e633aa113e7170df0084c2c3fbb9` |
| `widgets` | `8e1f88f456bfd13643ba3e6b505d99274e2e6fe4` |
| `dlv` | `a8f9b492472da07c4af51e47a62ca3865bba4bd5` |
| `chat` | `b36d427a4dfe2abf694b0dcf256ff87280d96a08` |
| `chats` | `1ddc82f5f67d58bb5ba02b770db28d1128f2a115` |
| `req` | `db52364c6f75d7c837e25daccbd359f21dd5cde8` |

## Combined behavior

- One overlapping source file: delivery's `PromptDelivery.kt`. Retained both the delivery lane/scope/review protections and chat's local-message identity.
- Supplied the injected `AppDispatchers` required by the repaired queue in the chat-owned delivery wrapper.
- Found another integration defect: Stop restored an ordinary waiting send without its local ID, so chat could leave its bubble Sending. The regression asserts that the restoration ID equals the admitted ID; chip Stop must still not restore the draft.
- Gate self-tests assumed the coordinator's absolute JDK path. The VPS exposed that dependency; the offline fixture now supplies its own fake JDK instead.

- Registered the 17 previously landed modules requested by their unit reports in `MODULE_OWNERS`. K1 now uses the existing production-source slice; test harnesses may construct framework nodes, while planted forbidden-production imports remain rejected.
- Core-data deletion tests bundle the frozen 188-row preference ledger as a Gradle-tracked test resource. Converter and migration-registry parity tests intentionally compare against the frozen Flutter source; the VPS receives those exact baseline inputs without building Flutter.
- Integrated `recovered/streamfix` (`57d3b9fb`) directly; W3 is not required for that P3 implementation. See `STREAM-FIX-INTEGRATION-2026-11-02.md` for retained correctness guards, chunk seams and the search-cost boundary.

## Validation

Final split-machine verification is complete for code commit `4d744e8b`. The standard VPS broad command exited 1 because one renderer exceeded its unchanged one-minute watchdog. That exact renderer passed fresh on the PC; the remaining quality task passed fresh on the VPS. This is a completed assigned test matrix, not a claim that the original broad VPS command or the full T2/release ladder passed. Focused results:

- Gate harness: 128 checks passed, zero failures; the machine-specific test JDK path was replaced by a fixture JDK.
- Preservation utility: 18 tests passed on the VPS. Seven new review cases were observed red first (six failures and one error), then green. All eleven real repair bundles were also preserved with validated durable receipts on the archive filesystem.
- Stop regression: the unchanged implementation failed the admitted-ID assertion (5 tests, 1 failure; actual ID null). The fix preserves chip Stop behavior and adds a chat-world regression for clearing Sending.
- Focused stream/chunk tests and Android/desktop consumer compilation passed.
- Focused quality tests passed, including the previously timing-out 6,000-word case, module owners, K1 and its planted-import cases; no timeout was relaxed.
- Frozen-source string and migration-reader parity suites passed.
- Build-logic tests passed on the VPS; source unchanged afterward.

Earlier unit reports are historical evidence, not a pass of this combined candidate. The native source digest before/after both lanes is `e857d5a0a5e194000734a295d3d710032322a286fe702d247e09205eb2e5be7d`. Cached failed desktop results were encountered after changing host renderer libraries; final qualification forces every Gradle `Test` task to execute and disables its result cache. Interrupted attempts are retained as partial evidence, never a completed gate.

## Boundaries and remaining work

- Heavy local jobs were stopped when requested. The owner subsequently authorized both PC and VPS without CPU restrictions; final app assembly/emulator checks run on the PC and fresh broad checks run on the VPS.
- W3 and other held units, migration/streaming device proofs, Flutter freeze gates, real-server preview integration, physical-device testing, and release qualification remain open. The stream-fix code is integrated here, with only bounded text-row/layout work claimed; complete search derivation is not constant work.
- Request-forms and other interrupted workers' source trees were preserved; their incomplete changes are not included here.

## Runnable preview integration

The original Android entry point included only the slice-0 empty feed and placeholder chat. The preview now includes a real OC2 gateway, persistent profile/Keystore storage, durable delivery storage, authenticated per-profile connection graphs, merged conversation feed, server setup/navigation and the real chat entry. All of this is guarded to the separate `io.github.eslamasabry.opencode_mobile.native` package; it is not production Flutter migration.

- GLM 5.3 ran through OpenCode on the VPS as requested and implemented the preservation helper; the primary review hardened its durability and added regressions.
- Real isolated OC2 beta `0.0.0-beta-19242` and stable `2.2.5` each passed authenticated session creation, a real GLM response, stream parts, interrupt and own-session cleanup. Stable negotiated routes use `/info`, `decision`, `resume` and PATCH rename; only a clean health 404 triggers read-only negotiation. Writes are never replayed.
- Seventeen transport mocks passed with the stable live smoke; the pagination follow-up expands this to nineteen cases. A short session page discards its decorative continuation so a healthy small server does not display a false incomplete-source notice.
- Two credential regressions passed: unreadable credentials stop before the factory; resolved credentials reach only the captured target spec.
- The real emulator caught a chat-entry crash: dialogs were siblings outside the kit host. Two root-screen tests with debug assertions enabled failed first, then passed after one shared `KitSheetHost` enclosed the frame and overlays. The kit assertion remains active.
- Profile deletion now fences the actual delivery queue's admission and marker writes before closing the graph, then checks both the app-wide queue and per-profile journal before sweeping. Unreadable or unresolved work preserves the saved profile.
- The app convention now explicitly targets JVM 17; AGP's Kotlin default 11 could not consume inline APIs from the pinned KMP modules.
- The emulator displayed a real `NATIVE_APP_OK` reply. Restart/update retained the server, authenticated automatically and restored the response. A direct server history read confirmed exactly one matching user prompt after restart and updates. Confirmed server removal cleared the encrypted secret entry (one before, zero after); the password was never present in plaintext preferences.

## Desktop golden environment

Linux Compose resolves generic sans by explicit family aliases. The existing Arabic baselines were recorded with DejaVu Sans; hosts with Noto Sans selected a different font. A private four-face DejaVu 2.37 inventory now pins only the two desktop golden suites, with license, hashes and declared Gradle inputs. Android continues to use its device system Arabic font. All ten Arabic comparisons passed against the original PNGs.

The repaired measured KitRow deliberately moves the notification background-state value below its long title. Captures were written into a temporary directory outside the source tree and visually reviewed. Exactly two English reference PNGs changed; their differing pixels are confined to x16–389/y593–628. The Arabic notification reference is byte-identical. The normal gate never records references and the 0.5% pixel limit/channel slack remain unchanged.

## Preview limits

This is a working server/chat preview, not native parity or a replacement for the installed Flutter app. Android 15 emulator qualification and the two real OC2 runtimes do not qualify physical devices, Android 37, migration, background longevity, notification/widget delivery or releases. The app currently exposes the first conversation page; full history browsing/search pagination, saved drafts, settings/runtime/voice/files screens and the remaining held units are not delivered here. Unsupported transport capabilities remain off. The 6,000-word JVM counter/frame result is not an Android device frame-budget pass, and retained search extraction can still grow with reasoning/tool/file content.

## Final evidence and commands

- Code: `4d744e8be6fd79cb529d8298ad728752e612543d`, local branch only. Native source ID above remained identical before and after the VPS checks and at final local verification. No further native-source edits followed.
- VPS final inventory: **3,470 tests, 3,469 passed, zero failures, one explicit live-server skip**. This includes 20 build-logic tests. The opt-in live test passed separately with zero skips against both real OC2 servers. The inventory names every result file and skipped case: [vps-test-inventory.json](../verification/2026-11-02/vps-test-inventory.json).
- Two rendering classes run fresh on the PC: `ReplyStreamFrameBenchmark` and `TranscriptStreamUiTest`, one test each, zero failures/skips, unchanged one-minute limits. The frame benchmark preserves all **5,439 frames per shape**, with chunked p95 **3.82 ms** and maximum **448 laid-out characters/frame**. [Measured output](../verification/2026-11-02/stream-frames.json). These are JVM timings, not Android frame qualification.
- Additional fresh PC checks: 19 live-gateway mocks, two chat-host regressions and two Android platform tests passed. Server ViewModel seven tests, routes roundtrip and credential-source two tests also passed and are included in the broader lane.
- APK: `opencode-native-preview-20261102.apk`, **22,617,713 bytes**, SHA-256 `08445466e86c58b30bb589155c79bbc2aab4d26d54f036870255ba070178678d`. Signature verified; signer SHA-256 `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`. Preview package `io.github.eslamasabry.opencode_mobile.native`, version `3.0.0-dev+10000`.
- [App proof](../verification/2026-11-02/app-proof.json), [restored real chat](../verification/2026-11-02/native-chat.png), [restored saved server](../verification/2026-11-02/native-servers.png). Android status-bar contrast was corrected by supplying the real Activity window: DecorView's context is not necessarily an Activity wrapper.
- All local builds, emulator and isolated beta/stable test servers finished. Copied provider auth files and temporary server login files were removed. Owner servers and phone were untouched.

The broad VPS command (JDK 21, Android SDK 37.1; private Skiko runtime libraries) was:

```bash
./gradlew --no-daemon --console=plain --no-configuration-cache \
  --no-parallel --max-workers=4 -Poc.test.forks=1 -Pkotlin.incremental=false \
  --init-script <fresh-test-init> \
  allTests compileKotlinDesktop :desktopApp:compileKotlin :quality:test \
  :android:linux:testDebugUnitTest :android:platform:testDebugUnitTest \
  :android:notifications:testDebugUnitTest :android:widgets:testDebugUnitTest \
  :android:widgets:testWidgetIngressConsumer :android:voice:testDebugUnitTest \
  :android:agenthost:testDebugUnitTest :android:camera:testDebugUnitTest \
  :android:media:testDebugUnitTest :tools:strings:test \
  :tools:flutter-migration-readers:test :androidApp:compilePreviewDebugKotlin --continue
```

The external init forces every `Test` task to execute and disables result caching; it assigns `ReplyStreamFrameBenchmark` to the PC. The broad VPS run's sole failure was `TranscriptStreamUiTest`'s one-minute watchdog; its original failure XML and log are retained. The quality task was then rerun with that renderer assigned to the PC, producing zero failures. `./gradlew -p build-logic test` also ran with forced fresh Test tasks. All source IDs match; no incompatible candidate results were combined.

PC rendering commands, both with the same fresh-Test init and unchanged candidate:

```bash
./gradlew :ui:kit-chat:desktopTest --tests '*ReplyStreamFrameBenchmark*'
./gradlew :quality:test --tests '*TranscriptStreamUiTest*'
./gradlew :androidApp:assemblePreviewDebug
```

Full logs, original failure evidence, exact launch scripts, JUnit XML and validated recovery bundles are archived outside disposable worktrees in `/home/eslam/Storage2/wt/native-repair-artifacts-20261102/`. VPS access reused the owner's private build-server environment and key-only SSH; no host address or key is recorded here. Compiler warnings remain (deprecated Compose APIs, unused graph input and a redundant cast); `oc.werror` remained at the preview default. No warning suppression or performance threshold change was introduced.
