# Native preview connection diagnostics — 2026-11-02

Implemented and enabled in native preview10002: failed connections now distinguish network, timeout, certificate, sign-in, consent, invalid address, incompatible/unreadable reply, server/rejected request, storage and unavailable saved secret. Non-null unknown failures use neutral copy. Existing kit Details/Copy shows only generated stage/kind/enum/status diagnostics; server prose, original details, addresses, IDs and credentials never enter that summary. Successful connection/auth/persistence/retry/draft-fence behavior is unchanged.

Candidate branch `native/server-connection-errors-2026-11-02`, base `7ff73263`. Native source ID: `3e2f32bd779f818969c41dccd068029172ec6ce8a314c1a60334e5477b83f483` (matching PC and VPS). This record accompanies the tested implementation commit. [Machine-readable receipts](checks.json).

| Gate | Result |
|---|---|
| PC `:engine:connection:desktopTest` | 9 tests, zero failures/errors/skips |
| PC `:feature:servers:desktopTest` | 14 tests, zero failures/errors/skips |
| PC `:androidApp:assemblePreviewDebug` | successful; version10002 installed over10001 with matching certificate |
| VPS `:quality:test` selecting KonsistRulesTest, GraphSmokeTest, ModuleOwnersTest, ChatWorldReviewTest, ScopeFileTest, KitOnlyPlantedTest | 44 tests, zero failures/errors/skips |

Gradle used pinned JDK21/SDK37.1, one machine-wide build lock, fresh Test execution with result caching disabled, `--no-daemon --console=plain --no-configuration-cache -Pkotlin.incremental=false`, four PC workers / three VPS workers. Native-wide suites, unchanged Flutter/native page flows, full visual/golden qualification, iOS and production migration gates were not rerun. These 67 checks are focused validation, not complete application parity.

## Red-first evidence and review corrections

The engine annotator refusal stub failed all four new tests. The classifier stub failed two of three tests; the Other fallback passed. The first GLM UI tests exercised a test-local wrapper, so the coordinator replaced them with actual ServersScreen tests. Those first real-screen failures were invalid as regression proof because the test harness lacked the mandatory secret sink. The harness was corrected to provide LocalKitSecretSink and a real desktop resource locale, restored after each test; the production secret guard was preserved. Four assertions then failed on the actual old7ff73263 screen, and passed on the final screen. A subsequent architecture run caught that Compose UI test in a presentation-only package; it was moved outside that package without weakening K2, then all final gates passed.

The coordinator also strengthened subtype assertions, corrected unknown/storage copy to avoid invented causes, and annotated stale-scope conversation loading. Original GLM patches and all rejected/final logs remain in machine-local evidence, not counted as completed gates.

## Android/API proof

Own Android15/API35 emulator; owner phone was never driven, screenshotted, cleared or installed through tooling.

- A localhost fixture returning200 with an invalid OpenCode shape reproduced the old misleading network sentence. New10002 shows reply-specific copy; Details starts collapsed and opens to `OpenGateway · UnreadableReply`. The fixture prose sentinel never appears. [Collapsed](green-malformed-collapsed.png), [expanded](green-malformed-details.png).
- A refused emulator-host port shows network-specific copy and `OpenGateway · Network · Refused`; Copy details was invoked. The10.0.2.2 address in this fixture screenshot is Android’s emulator host alias, not a private owner host. [Proof](green-refused-details.png).
- A fresh profile connected to the real authenticated stable2.2.5 PC server; one GLM5.3 prompt produced the expected real reply. Force-stop/restart reconnected and reopened its history with an empty composer, without replay: canonical user probe count remained1. [Reply](green-real-response.png), [restart](green-cold-restored.png), [history receipt](history-after-restart.json).
- The APK update retained the existing emulator profiles. Synthetic fixture profiles were removed through app UI before the positive journey. The persistent user PC backend remains running.

The owner reported the new safe diagnostic `OpenGateway · Network · Refused`. The server was verified listening and authenticated from the PC, and browser authentication had worked on the phone. After checking the saved origin and Android Tailscale app routing, the owner said “that fixed it.” Exactly which check changed the outcome was not specified; do not invent a narrower root cause. Tailscale documents that selected exclusions bypass its VPN: [official Android routing instructions](https://tailscale.com/docs/features/client/android-app-split-tunneling).

## GLM workers and product limits

Six actual VPS OpenCode1.19 workers used `zai-coding-plan/glm-5.3`: independent tests, Linux handoff contract, page-parity inventory, helper/runtime builder, UI/words builder, final source reviewer. All exited0; none ran builds/tests/network probes. Coordinator review corrected defective coverage and several documentation claims. [Execution/patch receipts](glm-workers.json), [bounded final source review](final-glm-review.md).

The APK remains a thin remote server/chat preview. Most native feature modules are not yet bound to it. The next product slice is full-composer integration with real model selection and attachments, preserving exact drafts and acknowledged-send semantics; test counts are not a substitute for that journey. [Reviewed page inventory](page-parity-map.md). Automatic discovery/import of the Flutter-owned Linux has no current cross-package credential contract and is not enabled: [verified boundary and remaining steps](existing-linux-handoff.md). Existing Linux and OC1 databases remain untouched.

English/Arabic copy and real-screen Arabic Details behavior are covered; other locales use default fallback and are not newly qualified. No stored format migration was introduced. No public push, merge, PR, tag, signing-key rotation or release was performed.

## Artifact

`opencode-native-server-errors-10002.apk`, 22,386,235 bytes, SHA-256 `4c1155d98e78d9dc1e23077a1ce012a7d5b5bce5e6a60fe69dd690fdb35267d7`. Certificate SHA-256 `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`, matching previously distributed native previews. APK-only Tailscale download was checked HTTP200 and byte length before delivery. Logs/JUnit/original model patches remain under `/home/eslam/Storage2/wt/native-server-errors-artifacts-20261102`.
