# Upstream review, 2026-10-11

Scope: what changed upstream since the versions this app pins, and what must change here to move each to its latest stable version. Research only: no app code, build or test was run. Sources: GitHub releases (`gh`), npm registry, nodejs.org, pub.dev, the Flutter release feed, the local Paseo checkout at `/home/owner/Storage/Code/paseo-spike/paseo-src` (tags v0.9.2 and v0.11.2, diffed), and the published `@opencode/protocol` / `@opencode/schema` packages (2.0.10 vs 2.0.26, diffed). Anything not checked is marked **unverified**.

Shipping rule used below: **Patch** = Dart-only, can go as `scripts/release.sh patch` against the exact released version. **Release** = changes assets, native code, `pubspec.yaml` or `jniLibs`, so it needs a new Shorebird release. Pins that live in Dart constants and are downloaded at runtime (agent versions, Node, Paseo, OpenCode) are Dart-only and patchable, but each needs fresh SHA-256 values and a device proof.

## Summary table

| Component | Pinned | Latest stable | Risk | Effort | Ships as |
|---|---|---|---|---|---|
| Ubuntu base rootfs | 24.04.5 (setup_base_script says 24.04.4) | 24.04.5 (24.04 line); 26.04.1 LTS exists | low | S | Patch |
| proot (Termux build) | 5.1.107.94 | 5.1.107.96 (Termux) / proot 5.5.0 upstream | med | S | Release (jniLibs) |
| Node.js (agent host) | v24.21.0 | v24.21.0 (24.x LTS, 2026-09-07) | none | none | none |
| OpenCode 1 | v1.18.32 | v1.19.0 (2026-10-11) | low | S | Patch |
| OpenCode 1 SDK contract | f12e14cf (2026-09-03) | v1.19.0 spec identical | none | none | none |
| `@opencode-ai/sdk` in Paseo lock | 1.14.46 | 1.19.0 (Paseo 0.11.2 still pins 1.14.46) | none | none | none |
| OpenCode 2 (`@opencode/cli`) | 2.0.10 | 2.0.26 (2026-10-08) | med | M | Patch |
| Paseo daemon | 0.9.2 | 0.11.2 (2026-10-09) | high | L | Patch (lock asset: Release) |
| Claude Code | 2.1.283 | 2.1.287 stable / 2.1.296 latest | low | S | Patch |
| Codex | rust-v0.160.0 | rust-v0.162.1 (2026-10-09) | low | S | Patch |
| Gemini CLI | 0.62.0 | 0.63.0 (2026-10-06) | low | S | Patch |
| Qwen Code | 0.24.7 | 0.25.0 | low | S | Patch |
| Goose | v1.53.0 | v1.54.0 (2026-10-08) | low | S | Patch |
| Oh My Pi | v18.5.1 | v18.8.9 (2026-10-10) | med | S | Patch |
| fx | v0.0.12 | v0.0.13 | low | S | Patch |
| Shorebird CLI / Flutter | CLI 1.6.x, Flutter 3.47.1 | CLI 1.6.127; Flutter stable 3.47.7 | med | M | Release |
| Dart packages | see section | see section | low | S-M | Release |

## 1. Built-in Linux

### Ubuntu base rootfs
- Pin: `ubuntu-base-24.04.5` in `lib/termux/scripts/manager_script.dart` (lines ~672-705, SHA-256 per architecture, marker `source=canonical-ubuntu-base-24.04.5`).
- **Inconsistency:** `lib/termux/scripts/setup_base_script.dart` (legacy Termux/proot-distro path, wired through `lib/termux/bridge.dart`) still downloads `ubuntu-base-24.04.4-base-$arch.tar.gz` with its own three checksums. The two paths therefore install different point releases. Fix: either bump the three checksums there to 24.04.5 (values are in `manager_script.dart`) or delete the legacy script if it is dead. Check for a test asserting the filename before editing.
- Latest: `cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/` lists 24.04.3, .4, .5; 24.04.5 is the newest point release, so no bump is needed. A newer LTS (26.04, 26.04.1) is published; moving would change glibc, apt, and the OpenSSL/Node assumptions and needs a full device proof. Recommendation: stay on 24.04 (supported to 2029). Mark 26.04 as a later, separate project.
- Risk low, effort S (consistency fix only). Patch.

### proot
- Pin: Termux `proot_5.1.107.94` plus `libtalloc_2.4.3` and `libandroid-shmem_0.7`, in `tool/builtin_linux/fetch_proot.sh`, installed into `android/app/src/main/jniLibs` (so any change is a native change).
- Latest in the Termux archive: `5.1.107.96` (the `bump(main/proot)` commits for .95 and .96 exist in termux-packages). Upstream proot-me/proot released v5.5.0 on 2026-10-02 (first release since 5.4.0 in 2023), but the app uses the Termux fork patched for Android; do not switch to upstream 5.5.0 without a separate spike.
- Changes needed: update both `.deb` filenames and SHA-256 values for aarch64 and x86_64 in `fetch_proot.sh`; re-run the script with patchelf; confirm the libtalloc/shmem versions did not change (**unverified**; check the Termux `Packages` index); commit the new `.so` files per the repo's convention. Changelog content for .95/.96 is **unverified**; read the termux-packages commits before deciding. The pinned proot is known to work with the emulator proofs, so treat this as optional unless a bug needs it.
- Risk med (core of the built-in Linux, regressions are hard to see), effort S, needs a new Release and the emulator proot proof (`docs/qa`).

### Node.js and apt packages
- Node `v24.21.0` (pinned with SHA-256 for arm64 and x64 in `lib/termux/scripts/local_agents_script.dart` and `lib/builtin/agents/paseo_scripts.dart`) is the current 24.x LTS release (2026-09-07). Newer Node majors exist (v26.11.x is not LTS). No change.
- apt installs (`lib/builtin/builtin_linux.dart` ~line 1287): `curl ca-certificates git openssh-client`, unpinned from Ubuntu. Nothing to review.

## 2. OpenCode 1

- Pin `v1.18.32` (2026-09-21) in `lib/builtin/setup/components.dart`, `lib/domain/agent_catalog.dart` (lines ~286-291), `scripts/byo-host/release-pins.json`, `scripts/host/ubuntu-opencode.sh`, and a comment in `lib/builtin/agents/gen_ui_install_scripts.dart:171`. Latest: **v1.19.0**, 2026-10-11 (https://github.com/anomalyco/opencode/releases/tag/v1.19.0). Intermediate: v1.18.33 to .35.
- What changed (from the release notes):
  - 1.18.33: Cloudflare AI Gateway timeouts; MCP browser launch failures reported; **debug configuration output now redacts credentials and sensitive headers** (security improvement, matches our no-echo invariant); Gemini thinking defaults.
  - 1.18.34: namespaced session and parent-session identity headers on model requests; macOS signing only.
  - 1.18.35: xAI tool-result images; stats formatting.
  - 1.19.0: `opencode upgrade --major` upgrades to OpenCode 2; `/update` replaces automatic updates (updates are no longer installed automatically); OpenAI `ultrafast` service tier; **PTY connection origins and tickets validated before the PTY lookup** (security fix; the app's terminal uses PTY, so re-test the local terminal).
- API: I diffed `packages/sdk/openapi.json` at v1.19.0 against `contracts/opencode-openapi-f12e14cf.json`: 188 operations and 472 schemas, identical set (no added or removed paths or schemas). The generated SDK therefore does not need regenerating for 1.19.0. Field-level changes inside existing schemas were not diffed (**unverified**), though the path and schema name sets match.
- New hazard: v1.19.0 advertises OpenCode 2 through `/update` and `upgrade --major`. A phone server that self-updates through the TUI would change protocol family under the app. The app runs `opencode serve` (no TUI), so risk is limited, but add the check "server reports 2.x on an OC1 profile" to the capability probe if not already covered (**unverified** whether `ServerCapabilities` handles it).
- Changes needed: bump the version string and the two SHA-256 values (arm64 `e9841d43...0e26b`, x64-baseline `6334d70d...8438` from the GitHub asset digests) in the four files above (and `release-pins.json` `openCodeVersion`, `publishedAt`, `observedOn`, `appVersions` after the next release is cut); update the `v1.18.32` comment; run `tool/smoke_test.dart` against a live `opencode serve`; check the tests that grep the version.
- Risk low, effort S. **Patch** (runtime download pins). `release-pins.json` is a BYO-host script pin and ships with the repo, not the app.
- `@opencode-ai/sdk` 1.14.46 in `assets/agents/paseo-package-lock.json` is a transitive pin of the Paseo daemon (Paseo 0.11.2 still pins exactly 1.14.46 and `@opencode/client` 2.0.10). Leave it; it moves only with Paseo.

## 3. OpenCode 2 (`@opencode/cli`)

- Pin `2.0.10` (2026-09-19) in `lib/domain/agent_catalog.dart` lines ~311-316 (npm tarballs `@opencode/cli-linux-arm64` and `-x64-baseline`). Latest: **2.0.26** (2026-10-08, npm `latest`), 16 patch releases ahead. The `beta` tag is `0.0.0-beta-19507`; our beta fixture is 18600.
- There are no GitHub release notes for the 2.x line, so I diffed the published `@opencode/protocol` and `@opencode/schema` packages between 2.0.10 and 2.0.26:
  - **Added endpoints:** `GET/POST /api/credential`, `POST /api/pair`, `GET /auth/connect/:code`, `POST /api/integration/:integrationID/connect/external`, `POST /api/vcs/init` (130 to 136 operations; none removed).
  - **Changed:** session create accepts `parentID` (linked child session) and `metadata`; form cancel accepts a `message` query; new `LocationNotFoundError` error type on several operations (session, permission); `session.metadata.updated` event added; `ide.installed` event removed; `question-v1` schema removed; config policy `action` values changed from `provider.use|permission` to `provider.use|tool.use|integration.use`.
  - Wire-level field diffs inside event payloads were not fully compared (**unverified**). Many `.d.ts` files differ (permission, model, project, provider, pty, shell, vcs, session-message).
- Effect on us: `lib/api2/dialect.dart` (stable dialect, derived from the 2.0.4 `/openapi.json`) and `docs/opencode2-protocol-notes.md` are the contract. Changes are mostly additive; the risk is unknown event types and the new error type, which the client must already tolerate (notes say "tolerate unknown event types/fields"). New capability worth adopting: `/api/pair` and `/api/credential` (pairing and credentials from the server, relevant to the QR pairing flow in `mobile_scanner`).
- Changes needed:
  1. Capture the live `/openapi.json` from a 2.0.26 server into `contracts/opencode2-openapi-stable-2.0.26.json` and diff against the 2.0.4-derived expectations; update `docs/research/opencode2-stable-2.0-2026-09-20.md` or add a new note.
  2. Bump version and SHA-256 for both npm tarballs (`npm view @opencode/cli-linux-arm64@2.0.26 dist.integrity`).
  3. Handle `LocationNotFoundError` in the error mapping; ignore or handle `session.metadata.updated`; stop depending on `ide.installed` if referenced.
  4. Run the live proof tool for OC2 (`opencode2 serve`, Basic auth) and the existing dialect unit tests.
- Risk med (weekly releases, wire not fully diffed), effort M, **Patch**.

## 4. Paseo daemon

- Pin `0.9.2` (2026-09-24) in `lib/builtin/agents/paseo_scripts.dart:14`, `lib/termux/scripts/local_agents_script.dart:26`, and `assets/agents/paseo-package-lock.json` (hash-pinned install dir `$version-82d16f9c432d`). Latest: **0.11.2** (2026-10-09). Path: 0.10.0 (09-28), 0.10.1, 0.10.2, 0.10.3 (10-02), 0.11.0 (10-07), 0.11.1, 0.11.2.
- Release highlights from `CHANGELOG.md` at v0.11.2:
  - 0.10.0: OpenCode v2 support (selected automatically from the installed `opencode` version; daemon bundles `@opencode/client` 2.0.10); password checks on relay connections ("a follow-up release will require the password"); reorganised Settings; CLI and desktop connect to a password-protected local daemon without asking.
  - 0.10.1: Claude Sonnet 5.5 (needs Claude Code 2.1.284+). 0.11.1: Claude Haiku 5.5 (needs Claude Code 2.1.293+).
  - 0.10.3: confirmation before a pairing link connects to a new host.
  - 0.11.0: plugin registry (`paseo plugin add owner/slug` now installs from the registry, use `github:owner/repo` for direct GitHub); Muse Code and Antigravity providers; first-class OMP; `agents.providers.<id>.options` in `config.json`; Usage reports (quota windows per account); many ACP fixes (permission requests answered when a turn fails, archived ACP history, context-window usage, stalled relay handshakes now time out).
- Protocol diff, `packages/protocol` v0.9.2 to v0.11.2 (the app's `lib/paseo/` client speaks this): **additive and tolerant**, but with these items:
  - New `hello.auth` field: `{kind:'password',password}` or `{kind:'localCredential',token}`; new server message `hello.rejected` with `reason` in `password_required|incorrect_password|incompatible_protocol` and `accepts:['password']`; new client capability `hello_rejection` and optional `protocolVersion`. The server keeps the header credential (`paseo.bearer.<password>` subprotocol and `Authorization: Bearer`), marked `COMPAT(headerAuth)` to be removed after 2027-03-24. The server now buffers frames until its async hello handler exists. Our `connectPaseoSocket` (transport.dart ~145-180) uses both header forms, so it still works; `hello.rejected` is a new frame the transport must not treat as unknown. Move to `hello.auth` before 2027-03.
  - New request/response family `usage.list_reports.*` and a `usage` capability; `ProviderOptions` widened from JSON to `unknown`; `desktopTrigger` UI hint; plugin source `registry` identity; provider config `options` alias (`providerParams` kept as COMPAT until 2027-03-30).
  - Provider config validation relaxed: custom providers no longer require `extends` or `label` in the same way (unverified effect on our generated config).
- **Biggest finding for this repo: exact-version gates on `'0.9.2'`.** `lib/paseo/transport.dart:400,442`, `lib/paseo/gateway/browser_launch.dart:86`, `lib/paseo/gateway/permissions.dart:190`, `lib/state/connection/gen_ui.dart:447,523,736`, `lib/builtin/agents/gen_ui_install_scripts.dart:77-78` (also requires Claude `2.1.283`), and `helperVersion: "0.9.2"` baked into `lib/domain/agent_tools/agent_certification_snapshot.dart` (several entries, plus `agentVersion` `2.1.283`) all key behavior to the exact string. Upgrading Paseo silently disables bounded frames, browser launch, permission handling and GenUI until those gates are re-certified or changed to a "tested set". Recommend replacing equality with a version-range or set check and re-running the certification that produced the snapshot.
- Other changes needed: regenerate `assets/agents/paseo-package-lock.json` for `@getpaseo/cli@0.11.2` (an asset, so a new Release; also update the `82d16f9c432d` hash and any checksum tests); `PaseoPhoneScripts.version`; `termuxLocalAgentsPins['paseo_version']`; Node floor (Paseo 0.11 still runs on Node 24, **unverified** engines field); the dependency changes (`yaml` 2.9.1, `@opencode/client` 2.0.10; node-pty `1.2.0-beta.15` needs prebuilds for linux-arm64 under proot, **unverified**). Update the user-visible "Paseo daemon 0.9.2" strings and `server_rows.dart` comments.
- Device proof required: daemon start under proot, model lists (Opus 5.5, Sonnet 5.5, Haiku 5.5), permissions, images/screenshots, resume, the `list-before-requests` trap noted in memory, and idle memory (0.11 adds the Usage service and plugin loader; the usage service was moved out of the websocket server, so watch RSS on the phone).
- Risk high (protocol surface plus version gates plus the lock asset), effort L. **Release** (lock asset), then a patch for later pin edits.

## 5. Agents (`lib/domain/agent_catalog.dart`)

Every agent entry needs a new version, URL, and SHA-256 (and the install size for Claude), then the matching certification snapshot entry and a device resume proof. All are Dart-only edits, so **Patch**.

| Agent | Pinned | Latest | Notes |
|---|---|---|---|
| Claude Code | 2.1.283 | 2.1.287 stable, 2.1.296 latest/next channel (downloads.claude.ai) | Take **stable** 2.1.287. Haiku 5.5 needs 2.1.293+ with Paseo 0.11.1, so 2.1.296 only if Haiku 5.5 is wanted. Update `lib/builtin/setup/claude_scripts.dart` (version + manifest checksums), `agent_catalog.dart`, the `gen_ui_install_scripts.dart` gate, and the certification snapshot. Fetch the official release manifest for checksums; do not use the remote install.sh. |
| Codex | rust-v0.160.0 | rust-v0.162.1 (2026-10-09) | 0.161: GPT-6.1 Sol default model, `/mcp login`, filesystem escalation and permission changes (relevant to the app's permission cards), SQLite corruption recovery. 0.162: managed Git worktree tools, `Retry-After` honoring, Linux sandbox fixes (relevant under proot where bubblewrap/landlock is unavailable; test sandbox startup), 0.162.1 fixes a startup failure and a TUI crash. Verify the musl aarch64 asset digest. |
| Gemini CLI | 0.62.0 (npm bundle) | 0.63.0 (2026-10-06); 0.64.0 in preview | Check the `package/bundle/gemini.js` path still exists. ACP behavior not reviewed (**unverified**). |
| Qwen Code | 0.24.7 | 0.25.0 (preview 0.25.1) | Changelog **unverified**. |
| Goose | v1.53.0 | v1.54.0 (2026-10-08) | Changelog **unverified**. Repo is now `aaif-goose/goose`. |
| Oh My Pi (`omp-acp`) | v18.5.1 | v18.8.9 (2026-10-10) | Three minors ahead and daily releases; Paseo 0.11 made OMP a first-class provider (mid-turn steering, Fast mode, extension rows). If the app drives OMP through ACP directly rather than Paseo, retest ACP. Choose a settled version, not the 10-10 release. |
| fx | v0.0.12 | v0.0.13 (2026-10-04) | Pre-1.0; low value. |

Risk low per agent, effort S each; batch them with one device proof. Cross-check: do not move Claude Code past what the pinned Paseo version supports.

## 6. Toolchain

- **Shorebird CLI:** latest 1.6.127 (2026-10-10), same 1.6.x line. Its `bin/internal/flutter.version` points to Flutter fork commit `9a881029...` (dated 2026-10-09), while this repo pins `91f8bd75...` (Flutter 3.47.1, `.github/workflows/android-release.yml` `SHOREBIRD_FLUTTER_VERSION`). So a current CLI would build with a newer engine than the baseline; patches must be built with the same Flutter revision as the release they patch. Flutter stable is now **3.47.7** (2026-10-08; 3.47.2 to 3.47.7 are patch releases). The CLI release does not state which Flutter version it maps to (**unverified**).
- Moving: pick one Shorebird+Flutter pair, update AGENTS.md, CI (`android-release.yml`, `android-device.yml`, the `bd9_flutter_version.json` fixture), and the memory/runbook; the pubspec `sdk: ^3.10.0` does not need to change. Do this only with a new baseline Release (patches against 1.2.0-line releases must keep the old engine). Risk med, effort M, **Release**. Not worth doing until a fix needs it.

## 7. Dart packages (top by importance)

From `pubspec.yaml` versus pub.dev on 2026-10-11:
1. `file_picker` 12.2.0 to **13.1.0** (major; check Android API changes and the `file_picker` platform code the app uses). Med.
2. `dio` ^5.7.0 to 5.11.1 (compatible; lock bump). Low.
3. `xml` 7.0.1 to 7.1.0. Low.
4. `flutter_timezone` 5.1.0 to 5.1.1. Low.
5. `image_picker` 1.2.3 to 1.2.4. Low.
6. `flutter_secure_storage` 11.2.0, `mobile_scanner` 7.4.2, `desktop_drop` 0.8.4, `flutter_riverpod` 3.4.3, `flutter_svg` 2.3.0, `path_provider` 2.1.6, `crypto` 3.0.7: already at latest.
Any pubspec change is a **Release**. I did not run `flutter pub outdated`, so transitive packages and dev dependencies are **unverified**. Recommendation: bundle 1-5 into the next planned Release; do not cut one for them alone.

## Recommended upgrade order

Batch 1 (Patch, low risk, agents only): OpenCode 1 to v1.19.0; Codex to rust-v0.162.1; Claude Code to 2.1.287; Gemini 0.63.0; Qwen 0.25.0; Goose v1.54.0; fx v0.0.13. Fix the 24.04.4 vs 24.04.5 inconsistency in the same patch. One device resume proof for the batch. Keep Claude Code and the Paseo pin compatible (the certification gate currently pins Claude 2.1.283 and Paseo 0.9.2 together, so edit these in batch 3 with Paseo, or relax the gate first).

Batch 2 (Patch, medium): OpenCode 2 to 2.0.26 after capturing its live `/openapi.json`, error and event handling updates, live proof. Oh My Pi to a settled v18.8.x after an ACP retest.

Batch 3 (Release, high): Paseo 0.11.2. First replace the `'0.9.2'` equality gates with a tested-version set, add `hello.rejected` handling (and later `hello.auth`), regenerate the lock asset, update the certification snapshot, run the full device proof (including memory), then cut a Release. Combine with the Dart package updates and the proot 5.1.107.96 bump (jniLibs) in the same Release to spend only one new baseline, but only if the proot proof passes.

Batch 4 (deferred): Ubuntu 26.04, upstream proot 5.5.0, Shorebird/Flutter 3.47.7+, `file_picker` 13 if not taken in batch 3. Each needs its own spike.

Order rationale: batch 1 and 2 are runtime-download pins that can ship as patches without touching the Shorebird baseline; Paseo is the only change touching the app's wire client and it forces the Release, so everything native or pubspec-related should ride that single Release.
