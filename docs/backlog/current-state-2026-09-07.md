# Backlog delivery state — 2026-09-07

This is the current delivery ledger. The September 6 epic and innovation
inventories preserve scope and acceptance criteria; their descriptions of
uncommitted or missing source are historical. Neither a checked-in feature nor
a synthetic test proves native installation, provider deployment or release.

Maintainer direction: logical commits directly to `dev`, every message carrying
`[skip ci]`. No PR, CI run, signing, tag, publication or live-server change is
part of this batch. The existing installed signer remains fixed.

## Consolidated baseline

`cc57491` contains the previous staged consolidation. BE-001–BE-011 and
FE-001–FE-011 have implementation records in their queues. Do not reopen those
fixes solely because the original problem description remains in the file.
The [consolidation record](../verification/consolidation-2026-09-07.md)
distinguishes focused passing checks from its interrupted full-suite gate.

## Product work and remaining acceptance

| IDs | Implemented source | Still open |
|---|---|---|
| E1 | Version/signer/source documentation; broken-release warning | Final APK, same-signer upgrade, contiguous device/TalkBack/cross-client journey, skill activation on a populated live catalog, approved publication |
| E2 | Session recovery without an open project, scoped navigation, request guards and durable input recovery | Known source gap: an offline prompt acknowledged by the server can remain on disk if the final queue save fails; durable uncertain-delivery review or proven idempotency is still needed. Physical process-death/camera/cloud-picker/upgrade matrix also remains |
| E3 | Per-credential actions, command/OAuth attempt recovery, explicit recovery after an uncertain start | Final native and supported live-server authentication exercise |
| E4 | Scoped MCP removal and reconnect-safe actions | Live removal/restart verification for the final candidate |
| E5 | Stash file vault, legacy payload migration, recovery, deletion and composer wiring | Final device storage/interruption exercise; source is no longer opt-in groundwork |
| E6 | Supported provider discovery and explicit web search, result review, and editable local source staging in the composer; manual entry remains available | Final supported-provider/native journey; no automatic page fetch or prompt send, and search results are not verified page content |
| E7 | English ARB foundation and ratchet; new plugin, health, quota and handoff controls externalized | Remaining hardcoded strings, reviewed translations, locale selection and RTL/device verification |
| E8 | Existing desktop platform gates and packaging source | Actual Linux/Windows/macOS install/runtime checks on suitable hosts |
| E9 | Existing release/patch scripts | Native-changing batch is not a Dart-only patch; patch promise/eligibility and authorized release validation remain separate |
| E10 | Persona and explicit task hypotheses | Observed user sessions/interviews; synthetic tests cannot replace them |
| E11 | Existing advanced surfaces and explicit hold inventory | Triggered user workflows and supported contracts before any additional endpoint UI |
| E12 | iOS runner, icons, Keychain entitlements, platform gates and CI source | macOS/Xcode compilation and actual Keychain/plugin/device integration; distribution needs signing |
| E13 | Codex/MiniMax collectors, pinned provider-plugin GLM collector, consent/scoped Remaining UI, persisted source/account thresholds, measured USD/token budgets, and independently opted-in quota monitoring wired through controller lifecycle/deletion | Collector deployment, authorized account checks and final native monitor verification; Claude remains unavailable and Gemini lacks a supported collector |
| F1a/b | Foreground read-aloud and reviewed voice conversation, consent and cancellation | Device audio-focus/engine interruptions; ambient voice is a separate unimplemented mode |
| F2-S2 | Read-only location-scoped plugin inventory, event/reconnect refresh, safe source/status rendering | Final live inventory check |
| F2-S1/S3/S4 | Explicit personal plugin-command links with reviewed/revalidated execution and profile-wide cleanup; bounded version-1 bundled task renderer with plain fallback | F6 shortcut integration and final native/live checks; personal links are not discovered plugin ownership |
| F3 | Opt-in cross-profile polling/inbox, current/unknown totals, quiet/Wi-Fi rules, opaque notification routes, source/deletion/reconnect guards | Native foreground-service/notification/device verification; counts cover each profile's last selected location |
| F4-S1 | Verified v1 POSIX attach command, reviewed clipboard, session/location revalidation and metadata fallback | v2 CLI proof, deep-link/QR round trip and native routing; loopback is not remotely reachable |
| F5 | In-app deterministic metadata summary | Per-run outcome evidence, optional privacy-reviewed notification/widget digests; idle is not success |
| F6 | Existing home widget and desktop keyboard shortcuts | Android launcher shortcuts, Quick Settings tile and supported voice-intent routing |
| F7-S1/S2 | Immediate setup progress, existing-install choice, existing-server access before Termux prerequisites, flat terminal, process/version/storage status, opt-in foreground recovery with three persisted attempts and Stop/deletion cancellation | Genuine non-proot auth/tool/SSE/rollback proof and native device verification remain; no native-musl migration claim |
| F8 | Route-owned demo gateway and memory store through production chat/events/permissions/diff; scoped escape controls | Final first-run device comprehension check |
| F9 | Secure connection guidance and existing HTTPS/loopback policy | Per-service authenticated SSE deployment checks, optional discovery/assistance, separately approved phone-server exposure design |
| F10 | Experimental Codex connection/editor/controller and text-chat journey implemented; isolated real CLI authentication/init proof and synthetic Android journey exercised | Final checkpoint: [Codex verification](../verification/codex-connection-2026-09-07.md). Live provider use and physical-device behavior remain unverified; other adapters still require their own proof |

The open source features above remain backlog, not external blockers or silently
completed work. Native-host, account, participant and publication prerequisites
are called out separately so they do not turn a partial feature into a completion
claim. Do not mark the entire backlog or the release ready from this batch.

The subsequent all-backlog agent assignment is tracked in
[the execution queue](execution-all-2026-09-07.md). Its first F3/F7/E13/F2 source
wave has passing focused checks and a clean integration analyzer; that is
separate from the previous batch's complete-suite result and from a release.

## This batch's finish lines

- Plugin inspection: open Library → Plugins, read safe source/status, refresh,
  survive a location/reconnect change. No plugin execution or installation.
- MiniMax quota: explicitly configured collector → authenticated app consent →
  correctly attributed general-pool percentages and resets. No PAYG-as-quota,
  inferred count conversion, provider credentials on the phone or deployment.
- Managed-server status: explicit check on Servers → honest last-observed
  process/version → existing setup controls. No automatic restart/install.
- Computer handoff: review a supported, quoted attach command → revalidate →
  copy without password. Unsupported connections retain metadata fallback.
- Demo: real chat send → streamed reply → edit decision → review/finish →
  reset/exit, without touching the real connection, preferences or native I/O.

Focused checks and final candidate evidence belong in
[the batch verification record](../verification/backlog-2026-09-07.md).

## Reconciliation against the transferred source

Checked against `b392bde` on September 7. E6 search, E13 independent quota
monitoring and F7 existing-server access are integrated. Earlier assignment
and audit labels remain historical; they must not enqueue those features again.
The Codex checkpoint is enabled source with incomplete final verification.

The next runnable major feature remains **F6-S1a: Connect/New task Android
launcher actions**, after the Codex checkpoint. **F5 per-run outcome evidence**
remains implementation work; an idle session is not a successful run. E2's
accepted-send persistence risk remains a separate correctness requirement.
Physical-device, supported-provider, platform-host and release checks retain
their actual prerequisites rather than blocking unrelated source work.

Keep these acceptance differences explicit before expanding those slices:

- F6-S2 describes an attention-inbox tile, while GitHub #34 requests a
  background pause/resume tile. These are different actions; F6-S1a does not
  require choosing between them.
- F1's reviewed voice conversation does not complete GitHub #35's optional
  hold-to-record/VAD auto-send request. Ambient voice remains a separate slice.
- Arabic is explicitly named by GitHub #19. E7 still needs externalization,
  locale selection, translation review and RTL verification; no language is
  currently enabled beyond English.
- The 42 open GitHub issues include implemented workflows. Reconcile each
  issue's residual acceptance before closure; open status is not proof of
  missing code. No issue was closed or changed by this local verification.
