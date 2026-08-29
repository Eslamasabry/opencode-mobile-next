# OpenCode Mobile (`oc_app`) — public launch readiness audit

**Repository:** `Eslamasabry/oc_app`  
**Audited branch:** `master`  
**Audited commit:** `9e7d5ce5431f8d20b8da028c20e8c71787d29384`  
**Audit date:** 29 August 2026  
**Method:** static source, repository, workflow, release, documentation, privacy, security-boundary, and product review.

> **Verdict: conditional no-go for making the current repository/history public or promoting the current APK as stable.**
>
> The application itself is unusually mature for a pre-public project. Its Android boundaries, Keystore-backed credential design, loopback-only cleartext policy, request-bound notification actions, OAuth state validation, diagnostics redaction, model-download verification, dual-protocol architecture, and fail-closed release script are strong. The no-go is driven by a small number of serious launch-boundary failures that are fixable without redesigning the app.

## 1. Executive scorecard

| Area | Score | Assessment |
|---|---:|---|
| Core Android security architecture | 8.0/10 | Strong manifest/network/backup boundaries and private native components. |
| Operational/deployment security | 4.0/10 | Ubuntu helper defaults can expose an unauthenticated OpenCode server on the LAN. |
| Privacy and local-data lifecycle | 6.0/10 | Passwords and backup handling are good; profile deletion and queued-data retention are incomplete. |
| UI quality | 7.0/10 | Mature Material 3 foundation and strong flagship flows; consistency debt remains. |
| UX quality | 7.2/10 | Excellent recovery and capability handling; hierarchy/discoverability still need convergence. |
| Accessibility | 5.8/10 | Good semantics in several critical flows, but text-scale clamping and missing automated gates remain. |
| Unit/widget test depth | 7.5/10 | Broad behavior-focused suite with historical evidence of 811 passing local tests. |
| End-to-end/release evidence | 4.0/10 | No authoritative green CI evidence on the audited launch commit. |
| Release engineering design | 8.0/10 | Fail-closed release script, signing checks, source synchronization, and Shorebird constraints are excellent. |
| Live release pipeline | 3.5/10 | Current GitHub Actions gate is blocked and therefore not authoritative. |
| Repository/public-history hygiene | 3.5/10 | Internal handoff material, agent session links, WIP history, and unresolved provenance should not be published as-is. |
| Governance/support readiness | 4.5/10 | MIT/privacy/third-party notices exist; security policy, contribution/support process, templates, and vulnerability workflow are missing or incomplete. |
| **Overall public-launch readiness** | **5.6/10** | **Targeted fixes can move the project quickly to a strong public preview.** |

## 2. Hard launch blockers

### P0-01 — Ubuntu helper can expose unauthenticated command execution

**Severity:** Critical  
**Files:**

- `scripts/host/ubuntu-opencode.sh`
- `docs/ubuntu-host.md`
- `lib/ui/screens/host_management_screen.dart`

The helper defaults `OPENCODE_HOSTNAME` to `0.0.0.0`, writes a systemd service without a server password, recommends opening the firewall, and instructs the user to connect via LAN HTTP. An OpenCode server can execute commands and access files permitted to its host account, so this can become unauthenticated remote command execution on a home, office, hotel, coworking, or shared Wi-Fi network.

It also contradicts the mobile client, which correctly rejects non-loopback plain HTTP.

**Required fix**

1. Default to `127.0.0.1`.
2. Require a strong `OPENCODE_SERVER_PASSWORD`; refuse to start without one.
3. Store it in a protected `0600` environment file or systemd credential, never the command line or repository.
4. Remove LAN HTTP and broad `ufw allow <port>/tcp` from the default path.
5. Document HTTPS reverse proxy, authenticated Tailscale/WireGuard, SSH tunnel, or another encrypted tunnel.
6. Validate port and hostname strictly before writing the unit.
7. Pin and verify the OpenCode installer/version instead of relying on mutable `curl | bash` behavior.
8. Add tests that inspect generated service content and reject unsafe combinations.

**Acceptance criteria**

- A fresh install listens only on loopback.
- The service cannot start with an empty password.
- The password is absent from `ps`, journal output, unit text, and copied UI commands.
- Non-loopback HTTP is rejected with a clear explanation.
- Unauthenticated requests return `401`; authenticated requests succeed.

### P0-02 — Server-supplied external form URLs bypass the hardened link policy

**Severity:** High  
**File:** `lib/ui/widgets/form_renderer.dart`

Markdown links use a careful policy: restricted schemes, credential rejection, destination disclosure, and confirmation. OpenCode 2 external form fields directly pass a server-provided URI to `launchUrl`. A malicious or compromised server can present `intent:`, `file:`, `content:`, custom application schemes, or misleading destinations. A tap is still required, but the UI labels the action generically as opening in a browser.

**Required fix**

Create one `SafeExternalLink` service used by Markdown, forms, OAuth-adjacent flows, update notices, and future server-controlled links:

- allow HTTPS by default;
- reject embedded credentials;
- warn separately for HTTP, preferably restrict it to loopback;
- block custom schemes unless explicitly product-owned and allow-listed;
- show the effective destination host and purpose;
- return a structured result (`opened`, `blocked`, `noHandler`, `failed`);
- add tests for encoded/malformed/overlong and credential-bearing URLs.

### P0-03 — The current GitHub Actions gate is not functioning

**Severity:** High operational risk  
**File:** `.github/workflows/android-quality.yml`

The workflow design is useful, but the recorded runs are blocked before meaningful jobs execute. A non-running gate cannot support release claims or branch protection.

**Required fix**

> Verified 2026-08-30 against `.github/workflows/android-quality.yml`: five of
> the items originally listed here were already satisfied when this audit was
> written — `workflow_dispatch` (line 21), all three actions pinned to 40-char
> SHAs, `permissions: contents: read`, `concurrency` with
> `cancel-in-progress`, and `:app:lintRelease`. They are struck through below
> so the remaining work is not overstated: the only blocker is account
> eligibility, not the workflow file.

- restore account/runner eligibility;
- ~~add `workflow_dispatch`~~ (already present);
- run the exact launch commit;
- ~~pin actions by immutable SHA~~ (already pinned);
- ~~use least-privilege permissions~~ (already `contents: read`);
- publish test, analysis, lint, and build evidence;
- require stable check names on `master` through branch protection;
- ~~add concurrency cancellation for superseded runs~~ (already present).

The public launch commit must be green for dependency resolution, generated-contract checks, `flutter analyze`, full tests, Android lint, signed release compilation, and provenance/license checks.

### P0-04 — Current public history and root documentation expose internal development material

**Severity:** High reputation/information-exposure risk  
**Areas:** `HANDOFF.md`, commit messages, `.claude/`, internal continuation notes

The repository includes a large engineering handoff with stale release claims, local paths, emulator details, scratchpad references, and agent continuation instructions. Commit history contains WIP instructions, conflict notes, local-only facts, and AI session URLs. The reviewed examples did not expose the actual password, but publishing the full transcript is unnecessary and increases privacy, security, and professionalism risk.

**Recommended publication model**

1. Preserve the existing private repository as the forensic/internal archive.
2. Create a clean public repository or orphan public branch from the reviewed source tree.
3. Use one import commit followed by the public-launch remediation commits.
4. Remove `HANDOFF.md` from the public root.
5. Replace it with concise `DEVELOPMENT.md`, `ARCHITECTURE.md`, and `ROADMAP.md` files.
6. Remove local-only instructions and AI session URLs.
7. Run secret and high-entropy scanning over both the final tree and rewritten history.
8. Sign the public launch tag and publish source/build provenance.

### P0-05 — Bundled developer skill packs have unresolved provenance/licensing

**Severity:** High legal/provenance risk  
**Paths:** `.claude/skills/**`, `THIRD_PARTY_NOTICES.md`, `LICENSES/**`

Developer-only skill packs contain inconsistent or incomplete source/license metadata and are not fully represented in the third-party notice set.

**Preferred fix:** remove developer-agent skill packs from the public product repository unless they are required to build or contribute.

If retained, record for every imported skill:

- exact upstream URL;
- tag/commit/version;
- copyright holder;
- SPDX identifier;
- modified/unmodified status;
- required license and notice text.

Add a CI provenance check that fails on unaccounted third-party directories.

### P0-06 — Removing a server profile does not cascade all local data

**Severity:** High privacy/trust risk  
**Files:** `lib/state/profiles.dart`, `lib/state/offline_queue.dart`, `lib/state/session_drafts.dart`, related stores/UI

`ProfileStore.remove` removes profile metadata, active state, and secure password, but profile-scoped model/agent/variant/location/migration preferences can remain. Queued prompts and attachments associated with that profile can remain, as can drafts or cached widget data. The UI wording and privacy policy create a reasonable expectation that removing the server removes its local data.

**Required design**

Implement one transactional domain operation:

```dart
Future<DeleteProfileResult> deleteProfileAndLocalData(String profileId)
```

It should coordinate:

- profile metadata and active profile state;
- secure password;
- selected location/workspace;
- model, agent, and variant preferences;
- provider refresh/migration flags;
- offline prompts and embedded attachments;
- drafts;
- widget/session snapshots attributable to the profile;
- all future namespaced profile data.

The confirmation should explain what is removed locally and that server/provider data is not deleted. Tests must verify that no profile-owned key or queued payload survives an app restart.

### P0-07 — No fresh, verifiable launch artifact from the cleaned commit

**Severity:** High release-trust requirement

The current README/release references and newest prerelease do not constitute a clean, green public-launch artifact from the remediated commit.

**Required fix**

- cut a new preview only after all P0 gates pass;
- publish SHA-256, signing certificate fingerprint, package ID, version code/name, source commit, minimum Android version, SBOM, and provenance;
- produce arm64 and other useful per-ABI artifacts plus a universal fallback;
- verify upgrade continuity from the prior distributed certificate lineage;
- update README release links mechanically during release;
- perform real-device install, upgrade, background, notification, Termux, and reconnect smoke tests.

## 3. Security review

### 3.1 Strong controls to preserve

- `android:allowBackup="false"`, explicit backup/data-extraction exclusions, and loopback-only cleartext network policy.
- Private native services and action receiver; exported launcher/widget surfaces only where Android requires them.
- Server passwords excluded from serialized profiles and stored through secure storage.
- Keystore recovery path that asks for password re-entry instead of crashing.
- URL validation that rejects credentials, paths, query strings, fragments, and non-loopback HTTP server origins.
- Notification actions bound to exact request IDs and routed through explicit private components.
- Privacy-safe lock-screen notification copy.
- OAuth HTTPS requirement, loopback validation, callback path matching, state comparison, timeout, and cancellation.
- Bounded, memory-only, explicit-send diagnostics with redaction.
- HTTPS-only voice model downloads with redirect checks, exact lengths, pinned revisions, SHA-256 verification, staging, rollback, resume, timeout, and cancellation.
- Release script that requires a clean synchronized branch, explicit signing identity, expected certificate, exact package/version, full analysis/tests, Shorebird dry-run, and explicit `--publish`.

### 3.2 Security improvements

1. Remove the dormant v2 helper that can place Basic-auth material in an `auth_token` URL query. Query credentials leak through logs, proxies, history, screenshots, referrers, and crash traces.
2. Decide and test a consistent IPv6 loopback policy (`::1`) across normalization, validation, Android network security, OAuth, and Termux assumptions.
3. Define password lifetime in memory and avoid copying credentials into long-lived objects unnecessarily.
4. Add a device-security/lock-screen requirement or warning where secure-storage strength materially depends on the device state.
5. Add a threat model for compromised servers, hostile networks, malicious model output, notification replay, OAuth callback confusion, and local Termux compromise.
6. Add merged-manifest assertions so dependency upgrades cannot silently add exported components, cleartext, backup, or broad permissions.
7. Add Android lint and a dependency-vulnerability/SBOM scan to CI.
8. Review the mutable `PendingIntent` used for `RemoteInput`; retain only the minimum extras and test intent tampering/replay.
9. Add size/time limits to every server-controlled payload rendered locally, including forms, Markdown, images, error envelopes, filenames, and notification identifiers.
10. Add release-mode log tests ensuring credentials, authorization headers, prompt bodies, file content, and tokens are absent.

## 4. Privacy and data lifecycle

### 4.1 Offline queue

Queued prompts are stored in `SharedPreferences` as JSON and may include self-contained data URLs for attachments. The 20 MB per-entry cap is useful, but there is no clear total queue limit, entry count cap, expiry, or storage-management screen.

**Required improvements**

- encrypt sensitive queue payloads at rest or move them to a structured local database with an encrypted file strategy;
- set total byte, entry count, and per-profile limits;
- add retention/expiry policy;
- show queued storage usage and provide clear-all/per-profile deletion;
- surface persistence accurately in privacy copy;
- test process death, disk pressure, corrupted entries, partial writes, and profile deletion.

### 4.2 Drafts

Draft text persists across restarts, while attachments do not. This is a reasonable first implementation but must be clear in UI and privacy documentation. Consider a secure attachment staging store with explicit retention instead of silently losing selected files on navigation.

### 4.3 Diagnostics

The current design is strong: bounded, memory-only, redacted, and never uploaded automatically. Preserve explicit consent and show the exact redacted payload before send. Add adversarial redaction tests for URL-encoded credentials, JWT-like strings, multiline headers, nested JSON secrets, Windows paths, and Unicode separators.

### 4.4 Privacy policy corrections

The policy should explicitly describe:

- persisted drafts and offline prompts/attachments;
- the durable Termux-side server password copy if retained;
- Shorebird update checks and desktop GitHub release checks;
- exact effect of profile deletion;
- model download hosts and integrity verification;
- how to clear queued/draft/model data separately;
- what remains on remote OpenCode servers and AI providers.

## 5. Architecture and maintainability

### Strengths

- Protocol-neutral gateway isolates v1/v2 behavior.
- Capability flags prevent unsupported actions from becoming dead buttons.
- State and UI have extensive behavior-focused test seams.
- Product error mapping, stale-content retention, queue reconciliation, and lifecycle recovery show careful failure design.
- Theme packs, semantic success extension, shared product states, and common confirmation surfaces provide a sound design foundation.

### Risks and recommendations

1. `ConnectionController` is very large and owns connection lifecycle, sessions, catalog, requests, forms, inbox, drafts, offline queue, notifications, widgets, diagnostics, and profile state. Split it into bounded controllers/repositories with one orchestration layer.
2. Replace profile-scoped `SharedPreferences` blobs with structured storage and explicit migrations; queue/drafts/cached session metadata need indexed ownership and atomic operations.
3. Define typed results for user-facing operations instead of relying on thrown strings.
4. Add a state-machine model for connection, background suspension, reconnect, password rejection, queue flush, and capability transitions.
5. Preserve server truth but define cache ownership and invalidation explicitly.
6. Add contract tests against pinned v1 and v2 server versions, not only captured fixtures.
7. Ensure the v2 location-switch transport fix remains covered by a non-hanging regression fixture.
8. Remove dead helpers and stale documents as part of every release gate.

## 6. UI/UX, design, and accessibility launch findings

A separate dedicated report is in [ui-ux-audit-2026-08-29.md](ui-ux-audit-2026-08-29.md). The public-launch blockers are:

- duplicate global Mission Control/Requests/settings/model surfaces;
- compact composer control density;
- queue/steer hidden behind long press;
- global text scale clamped to 1.0–2.0;
- incomplete semantic token migration;
- residual raw exception strings;
- incomplete localization presented as if the infrastructure were broader than it is;
- no automated accessibility gate for tap targets, labels, and contrast.

Do not redesign from scratch. Run a convergence release around Activity, Workspace, Session, and Output.

## 7. Test and CI plan

### Required automated lanes

1. `flutter analyze` with the pinned release Flutter.
2. Full unit/widget suite, serial or reliably sharded.
3. Android lint on the merged release manifest.
4. Debug and test-signed release build.
5. Generated SDK/contract drift verification.
6. License/provenance verification.
7. Secret/high-entropy scanning of tree and public history.
8. Dependency vulnerability scan and CycloneDX/SPDX SBOM.
9. Accessibility widget tests: Android tap targets, labelled targets, contrast, semantics flows.
10. Golden matrix for default + theme packs, light/dark, narrow/wide, and text-scale variants.
11. Android instrumentation smoke on at least one current API and one older supported API.
12. Reproducible release metadata check: signer, package, version, hashes, provenance.

### Required real-device scenarios

- clean install and first secure connection;
- upgrade from the last public certificate lineage;
- v1 and v2 connection/authentication;
- background foreground-service start/stop and process death;
- permission/question/form notification actions and stale replay;
- offline queue, reconnect, and exact once-only delivery;
- Termux install/start/stop/update on supported architecture;
- model download cancel/resume/corruption;
- large text and TalkBack critical path;
- file attach/preview/review/terminal and lifecycle wake;
- Shorebird update and rollback-safe behavior.

## 8. Public repository governance

Add before opening the repository:

- `SECURITY.md` with supported versions, private reporting route, response expectations, and disclosure policy;
- GitHub private vulnerability reporting;
- `CONTRIBUTING.md` with environment pin, test commands, architecture boundaries, generated-code rules, and PR expectations;
- `SUPPORT.md` distinguishing app, OpenCode server, model provider, Termux, and Shorebird issues;
- issue forms for bug, security redirect, feature request, compatibility report, and release problem;
- PR template with tests, screenshots, accessibility, privacy/security, migrations, and release-impact sections;
- `CODE_OF_CONDUCT.md`;
- public roadmap and support matrix;
- explicit “unofficial/community client” and non-affiliation wording unless formally authorized;
- branch protection, required reviews, signed/reviewed release process, and protected tags.

## 9. Prioritized remediation backlog

### P0 — before repository visibility or public promotion

1. Secure Ubuntu helper defaults and documentation.
2. Harden external form links through a shared safe opener.
3. Restore and pass CI on the exact launch commit.
4. Publish from a clean public history/tree; remove internal handoff/session material.
5. Remove or document every bundled developer skill’s provenance/license.
6. Implement complete profile-data cascade deletion.
7. Cut and verify a fresh artifact from the remediated commit.
8. Run final secret/history scan and resolve every finding.
9. Add security policy/private reporting and core governance files.
10. Correct privacy policy for drafts, queue, Termux credential, updates, and deletion.

### P1 — before calling the app stable

11. Add Android lint and merged-manifest regression assertions.
12. Add SBOM, dependency scanning, and build provenance.
13. Add total offline-queue quota, count cap, expiry, and storage UI.
14. Move durable prompt/draft state to structured profile-owned storage.
15. Remove query-token auth helper and other dead sensitive code.
16. Complete high-risk notification/OAuth adversarial tests.
17. Split the oversized connection controller into bounded domains.
18. Add pinned live contract tests for supported v1/v2 versions.
19. Complete flagship semantic design-token migration.
20. Remove global text-scale clamp and pass 2.5× critical flows.
21. Add automated tap-target/label/contrast tests and TalkBack device smoke.
22. Merge duplicate Activity/Requests global surfaces.
23. Make queue/steer visibly selectable.
24. Normalize remaining raw error paths.
25. Complete localization or explicitly ship English-only.
26. Add arm64/per-ABI artifacts and document compatibility.
27. Test install/upgrade/rollback continuity from prior releases.

### P2 — quality and scale

28. Add tablet master-detail layouts.
29. Create a genuine desktop interaction/platform-support lane before claiming desktop support.
30. Add drag/drop, keyboard shortcuts, pointer context menus, scrollbars, and window persistence for desktop.
31. Add issue telemetry only if privacy-preserving and opt-in; moderated testing is sufficient initially.
32. Add user-facing local storage management and export where safe.
33. Add a release support matrix for Android versions, ABIs, OpenCode versions, Termux sources, and desktop status.
34. Automate README latest-release references.
35. Add signed tags and release attestations.
36. Establish release rollback and security incident playbooks.
37. Schedule periodic third-party notice, provenance, and threat-model review.

## 10. Launch gates

### Gate A — safe to make the repository public

- [ ] Clean public tree/history created.
- [ ] Internal handoff, AI session URLs, scratchpad/local details removed.
- [ ] Secret/high-entropy history scan clean.
- [ ] `.claude` provenance resolved or removed.
- [ ] Ubuntu helper secure.
- [ ] External form-link policy secure.
- [ ] Privacy policy accurate.
- [ ] `SECURITY.md`, contribution/support docs, templates, and private reporting present.
- [ ] License/third-party inventory matches the tree.

### Gate B — safe to publish a public preview APK

- [ ] Gate A passed.
- [ ] Exact commit CI green.
- [ ] New artifact produced only through the controlled release lane.
- [ ] Hash, signer, package/version, SBOM, source commit, and provenance published.
- [ ] Clean install and upgrade smoke passed on real devices.
- [ ] v1/v2 auth, notification actions, background, queue, voice-model, and Termux smoke passed.
- [ ] Preview/experimental limits are explicit.

### Gate C — safe to call stable

- [ ] Profile-data deletion and queue lifecycle complete.
- [ ] Accessibility automated and device gates pass.
- [ ] No unresolved Critical/High security, privacy, data-loss, or interaction issue.
- [ ] Support matrix and incident/rollback process exist.
- [ ] UX convergence pass and usability testing complete.
- [ ] At least one preview cycle produced no systemic upgrade/data-loss/security failure.

## 11. Final recommendation

Keep the current private repository as the internal engineering archive. Build a clean public lineage from the reviewed source tree, apply the P0 remediation set, run the exact commit through restored CI and real-device smoke tests, and publish it explicitly as a preview.

The project does not need more feature breadth before launch. It needs a trustworthy public boundary: safe host setup, one safe link policy, complete local-data deletion, verifiable releases, clean provenance/history, and clear governance. Once those are in place, the application’s existing technical depth can support a compelling open-source public preview.