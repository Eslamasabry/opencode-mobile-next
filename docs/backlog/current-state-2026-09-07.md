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
| E2 | Session recovery without an open project, scoped navigation, request guards and durable input recovery | Physical process-death/camera/cloud-picker/upgrade matrix and any findings it produces |
| E3 | Per-credential actions, command/OAuth attempt recovery, explicit recovery after an uncertain start | Final native and supported live-server authentication exercise |
| E4 | Scoped MCP removal and reconnect-safe actions | Live removal/restart verification for the final candidate |
| E5 | Stash file vault, legacy payload migration, recovery, deletion and composer wiring | Final device storage/interruption exercise; source is no longer opt-in groundwork |
| E6 | Reviewed web sources can be added to composer text without automatic fetch/send | Search-provider integration remains separate from manual source selection |
| E7 | English ARB foundation and ratchet; new plugin, health, quota and handoff controls externalized | Remaining hardcoded strings, reviewed translations, locale selection and RTL/device verification |
| E8 | Existing desktop platform gates and packaging source | Actual Linux/Windows/macOS install/runtime checks on suitable hosts |
| E9 | Existing release/patch scripts | Native-changing batch is not a Dart-only patch; patch promise/eligibility and authorized release validation remain separate |
| E10 | Persona and explicit task hypotheses | Observed user sessions/interviews; synthetic tests cannot replace them |
| E11 | Existing advanced surfaces and explicit hold inventory | Triggered user workflows and supported contracts before any additional endpoint UI |
| E12 | iOS runner, icons, Keychain entitlements, platform gates and CI source | macOS/Xcode compilation and actual Keychain/plugin/device integration; distribution needs signing |
| E13 | Codex collector, supported MiniMax Subscription Key collector, consent/scoped Remaining UI and consumption grouping | Collector deployment and authorized account checks; Claude supported integration, GLM/Gemini contracts, optional budgets and alerts |
| F1a/b | Foreground read-aloud and reviewed voice conversation, consent and cancellation | Device audio-focus/engine interruptions; ambient voice is a separate unimplemented mode |
| F2-S2 | Read-only location-scoped plugin inventory, event/reconnect refresh, safe source/status rendering | Final live inventory check |
| F2-S1/S3/S4 | Existing commands, tools and forms are reusable primitives | Proven plugin-to-command discovery and a versioned declarative mobile-renderer pilot; inspection does not execute or install plugins |
| F3 | Saved-server attention navigation and honest active-cache observations | Cross-profile monitoring, unified live totals and per-profile notification/quiet-hour rules; inactive profiles remain unknown |
| F4-S1 | Verified v1 POSIX attach command, reviewed clipboard, session/location revalidation and metadata fallback | v2 CLI proof, deep-link/QR round trip and native routing; loopback is not remotely reachable |
| F5 | In-app deterministic metadata summary | Per-run outcome evidence, optional privacy-reviewed notification/widget digests; idle is not success |
| F6 | Existing home widget and desktop keyboard shortcuts | Android launcher shortcuts, Quick Settings tile and supported voice-intent routing |
| F7-S1/S2 | Immediate setup progress, existing-install choice, flat terminal, explicit managed process/version status and lifetime guidance | Termux storage telemetry, opt-in bounded crash recovery, genuine non-proot auth/tool/SSE/rollback proof; no native-musl migration claim |
| F8 | Route-owned demo gateway and memory store through production chat/events/permissions/diff; scoped escape controls | Final first-run device comprehension check |
| F9 | Secure connection guidance and existing HTTPS/loopback policy | Per-service authenticated SSE deployment checks, optional discovery/assistance, separately approved phone-server exposure design |
| F10 | Protocol research inventory | Separate pinned Codex/pi/ACP transport/auth/reconnect proofs, then one usable backend; no speculative adapter is enabled |

The open source features above remain backlog, not external blockers or silently
completed work. Native-host, account, participant and publication prerequisites
are called out separately so they do not turn a partial feature into a completion
claim. Do not mark the entire backlog or the release ready from this batch.

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
