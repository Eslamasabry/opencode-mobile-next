# Post-remediation status — 29 August 2026

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

## What this is

The two audits in this directory reviewed the repository at commit
`9e7d5ce5431f8d20b8da028c20e8c71787d29384`. Work happened afterwards. This
file records, finding by finding, what that work actually closed.

It is **not** a revision of the audits. Their text and their verdicts stand
as written; nothing in
[`public-launch-audit-2026-08-29.md`](public-launch-audit-2026-08-29.md) or
[`ui-ux-audit-2026-08-29.md`](ui-ux-audit-2026-08-29.md) has been edited. A
dated audit that gets quietly amended stops being evidence.

**Method.** Every status below was established from `git log` over the range
`9e7d5ce..` and from the tree as it stands, not from what was discussed or
intended. A finding is Closed only when a commit does the work; "it was
planned", "it came up in review", and "the code looks like it handles that"
are not evidence and are recorded as Open. Where a commit does part of a
finding, the status is Partially closed and the remainder is named.

**Statuses.**

| Status | Meaning |
|---|---|
| **Closed** | A commit does the work the finding asked for. |
| **Partially closed** | Some of it landed; the rest is named explicitly. |
| **Open** | No commit found. Still true. |
| **Accepted risk** | Not fixed, deliberately, with the reason stated. |

**Range.** 30 commits, `9e7d5ce..165acae` on `master`, plus five commits on
`launch/docs-governance` (`4893192`, `658ced0`, `81bec00`, `a73c2c9`,
`ec06627`) which close most of the governance set and are cited below where
they apply.

## Tally

Counted over the itemized entries below, one count per entry:

| | Public launch audit | UI/UX audit | Total |
|---|---|---|---|
| Closed | 24 | 22 | **46** |
| Partially closed | 11 | 15 | **26** |
| Open | 47 | 61 | **108** |
| **Entries tracked** | **82** | **98** | **180** |

Three of those entries are groups rather than single findings, and each is
counted once: the eleven real-device scenarios (PL-DEV-01…11), the ten P2
backlog items (§9 #28–37), and the nine desktop items (UX-DESK-01…09). At
the level of individual findings the Open column is therefore about 135, not
108. Five findings are additionally recorded as **Accepted risk** at the end
of this document; four of those are also counted above as Open or Partially
closed, because an accepted risk is still an open finding.

The shape matters more than the count. Every finding the audits named a
**launch blocker for UX** is closed. Of the seven **hard launch blockers**
in the public-launch audit, four are closed, one is partially closed, and
two — CI evidence and a verified launch artifact — are open and cannot be
closed from inside this repository. The large Open column is mostly the P1
and P2 backlog, which the audits scoped to "before calling the app stable"
and "quality and scale", not to going public.

## The headline verdicts, revisited

The audit index still says: *public launch — conditional no-go; UI/UX —
suitable for a controlled public preview after a focused convergence pass*.

**That remains accurate, and this document does not overturn it.** The
convergence pass the UI/UX audit asked for happened. The public-launch
conditions have not all been met: CI has never run, no artifact has been cut
and verified from a remediated commit, the privacy policy is still wrong
about drafts and queued prompts, and the history/secret-scan work was never
done. Those are the conditions, and they are still conditions.

---

# Part A — Public launch readiness audit

## A1. Hard launch blockers (§2)

### P0-01 — Ubuntu helper exposed an unauthenticated shell to the network
**Closed.** `c2a8770` (*Stop the Ubuntu helper from exposing a shell to the
network*) changed the default bind to loopback, made a wider bind opt-in,
generated a password into a `0600` env file that systemd reads — so it never
appears in unit text, `ps`, or the journal — validated port and hostname, and
replaced the "open your firewall" advice with adb reverse, an SSH forward, or
Tailscale. `165acae` brought `docs/ubuntu-host.md` and the in-app **Ubuntu
host management** screen in line, dropping the LAN-IP and `ufw` instructions.

*Remainder, tracked as Open:* the finding also asked that the OpenCode
installer be pinned or verified rather than piped from a mutable URL
(`curl | bash`), and that tests inspect the generated unit and reject unsafe
combinations. Neither commit does either.

### P0-02 — v2 form links bypassed the link policy
**Closed.** `7836a6d` (*Route every server-supplied link through one hardened
opener*) lifted the policy out of `markdown.dart` into
`lib/ui/widgets/external_link.dart`: https opens after a confirmation naming
the host, plain http only behind a separate insecure warning, embedded
credentials and every other scheme refused, over-long URLs rejected, and a
structured outcome returned so a refusal can say so. The form card now names
the real destination host, and the desktop update check no longer trusts a
GitHub-supplied `html_url`.

### P0-03 — CI cannot run
**Open.** `.github/workflows/android-quality.yml` is untouched since the
audit (`git log 9e7d5ce.. -- .github/workflows/android-quality.yml` is
empty). Its header still records 35/35 runs failing in ~5 s with
`runner_id 0` on a GitHub account-level billing block. No commit can fix
this; it is an owner-side billing action. Until it runs, **no release from
this repository is backed by CI evidence** — the gates have been run
locally. `81bec00` makes the README say that instead of implying CI runs
them, and `SECURITY.md` (`ec06627`) lists it as known so nobody re-reports
it. Neither is a fix.

### P0-04 — Internal material in the public tree and history
**Partially closed.**

- *Closed:* `8fa83fb` moved the 135 KB working log out of the repository
  root, and `658ced0` removed it from the tree entirely — the six durable
  facts it was the only home for moved to `CONTRIBUTING.md`. `81bec00`
  removed the README link. Git history keeps the file; no history was
  rewritten, deliberately (see Accepted risk below).
- *Open:* the publication model itself. No clean public repository or orphan
  branch, no secret/high-entropy scan of tree **and history**, no signed
  launch tag or provenance, and the `DEVELOPMENT.md`/`ARCHITECTURE.md`
  /`ROADMAP.md` split the finding suggested was not done (the README and
  `CONTRIBUTING.md` carry that content instead, which is a defensible
  substitute but not the thing that was asked for).
- *Accepted risk:* the finding asked for AI session URLs to be removed from
  commit messages. Every commit in the remediation range still carries a
  `Claude-Session:` trailer — including the commits that close other
  findings, and including these. Removing them means rewriting published
  history. That trade was not taken.

### P0-05 — Bundled developer skill packs had no provenance or licensing
**Closed.** `8fa83fb` untracked the two mcpmarket.com skill packs, gitignored
`.claude/skills/`, recorded their provenance and the SPDX bar a future pack
must clear in `docs/internal/developer-skills.md`, and added the hygiene test
that pairs every file in `LICENSES/` with a notice and every notice reference
with a file.

*Caveat:* the finding asked for this to be a **CI** provenance check. It is a
repo test, and CI does not run (P0-03).

### P0-06 — Removing a server profile left its data behind
**Closed.** `e957246` (*Delete a server's local data when the server is
removed*) made deletion one operation, `deleteProfileAndLocalData`, with a
key-shape sweep so later-added `oc.<what>.<profileId>` keys are caught by
default and app-wide keys never are. Drafts gained a profile id and
unattributable legacy drafts go with the deletion; in-memory queue and draft
caches are dropped; the widget snapshot is cleared after the republishing
notification. The confirmation now counts the queued prompts and drafts at
stake and states that nothing is deleted on the server or at the providers.

*Not confirmed:* the typed `DeleteProfileResult` return shape the finding
sketched.

### P0-07 — No verified launch artifact from a remediated commit
**Open.** `1c7f7df` bumps the version to `1.0.29+30` and the
`v1.0.29+30-preview.9` release exists, but the finding asked for published
hashes, signer verification, an SBOM, build provenance, per-ABI artifacts,
upgrade-continuity testing, and a device smoke run. None of that is in the
range. The release notes state plainly that CI did not evidence the build.

## A2. Security controls to preserve (§3.1)
**Not findings.** Twelve controls the audit asked to keep: `allowBackup=false`
with a loopback-only cleartext policy, private native services, secure-storage
passwords, Keystore recovery, URL validation, request-ID-bound notification
actions, lock-screen copy, OAuth state/loopback/callback checks, redacted
memory-only diagnostics, verified voice-model downloads, and the fail-closed
release script. Nothing in the range weakens any of them, and nothing adds a
regression guard either.

## A3. Security improvements (§3.2)

| ID | Finding | Status |
|---|---|---|
| PL-SEC-01 | Dormant v2 helper can put Basic-auth material in an `auth_token` query string | **Open** — dead code still present (backlog #15) |
| PL-SEC-02 | No decided, tested IPv6 loopback (`::1`) policy across normalization, validation, network security config, OAuth, Termux | **Open** |
| PL-SEC-03 | Password lifetime in memory undefined; credentials copied into long-lived objects | **Open** |
| PL-SEC-04 | No device-lock requirement or warning where secure-storage strength depends on device state | **Open** |
| PL-SEC-05 | No threat model document | **Open** — `SECURITY.md` (`ec06627`) states the trust model in prose, which is not a threat model |
| PL-SEC-06 | No merged-manifest assertions, so a dependency upgrade could silently add exported components, cleartext, backup, or broad permissions | **Open** |
| PL-SEC-07 | No Android lint and no dependency-vulnerability/SBOM scan in CI | **Open** — `.github/dependabot.yml` (`ec06627`) adds weekly pub and github-actions update PRs, which is dependency *currency*, not vulnerability scanning or an SBOM |
| PL-SEC-08 | Mutable `PendingIntent` for `RemoteInput` unreviewed; extras not minimized; no tamper/replay test | **Open** |
| PL-SEC-09 | No size/time limits on server-controlled payloads rendered locally | **Partially closed** — `7836a6d` rejects over-long URLs. Forms, markdown, images, error envelopes, filenames, and notification IDs are unbounded |
| PL-SEC-10 | No release-mode log tests proving credentials, headers, prompt bodies, file content, and tokens are absent | **Open** |

## A4. Privacy and data lifecycle (§4)

| ID | Finding | Status |
|---|---|---|
| PL-PRIV-01 | Offline queue persists prompts and data-URL attachments in `SharedPreferences` with only a 20 MB per-entry cap — no total or count limit, no expiry, no storage-management screen, no at-rest encryption, no process-death or corruption tests | **Partially closed** — `e957246` makes queued prompts and their embedded attachments die with the profile. Quota, expiry, encryption, storage UI, and the durability tests are all Open (backlog #13) |
| PL-PRIV-02 | Draft text survives restart but attachments silently do not, and nothing says so | **Partially closed** — `7287ab6` labels staged attachments *"Attachments are not saved with your draft."* at selection time, which is the alternative the audit sanctioned (§7.4). The secure staging store with retention was not built |
| PL-PRIV-03 | Diagnostics redaction has no adversarial tests and no pre-send display of the exact redacted payload | **Open** |
| PL-PRIV-04 | Privacy policy omits persisted drafts, queued prompts and their attachments, the durable Termux-side password copy, Shorebird and desktop update checks, the exact effect of profile deletion, model-download hosts and integrity, and how to clear each store | **Open** — `PRIVACY.md` is untouched since the audit. `e957246` corrected the in-app confirmation copy but not the policy it contradicts. This is the largest remaining honesty gap in the tree |

## A5. Architecture and maintainability (§5)

| ID | Finding | Status |
|---|---|---|
| PL-ARCH-01 | `ConnectionController` owns connection lifecycle, sessions, catalog, requests, forms, inbox, drafts, offline queue, notifications, widgets, diagnostics, and profile state | **Open** (backlog #17) |
| PL-ARCH-02 | Profile-scoped `SharedPreferences` blobs should be structured storage with explicit migrations and atomic ownership | **Open** — `e957246` improved key-shape sweeping inside `SharedPreferences`; it did not migrate the storage (backlog #14) |
| PL-ARCH-03 | User-facing operations rely on thrown strings rather than typed results | **Partially closed** — `7836a6d` returns a structured link outcome, `e957246` adds one transactional delete, `7287ab6` funnels 14 raw-throw call sites. No general refactor |
| PL-ARCH-04 | No state-machine model for connection, background suspension, reconnect, password rejection, queue flush, capability transitions | **Open** |
| PL-ARCH-05 | Cache ownership and invalidation not defined against server truth | **Open** |
| PL-ARCH-06 | No contract tests against pinned live v1/v2 servers | **Open** (backlog #18) |
| PL-ARCH-07 | v2 location-switch transport fix has no non-hanging regression fixture | **Open** |
| PL-ARCH-08 | Dead helpers and stale documents should be removed at every release gate | **Partially closed** — `8fa83fb` and `658ced0` (handoff), `7287ab6` (three duplicated `_showError` helpers), `a73c2c9` (notices rebuilt from `pubspec.lock` with a test that fails when they drift). No release-gate rule exists |

## A6. UI/UX blockers named in §6
Cross-references into Part B; statuses there.

| ID | Finding | UX ref | Status |
|---|---|---|---|
| PL-UX-01 | Duplicate global control surfaces | UX-P0-01 | **Closed** — `d815a7e` |
| PL-UX-02 | Compact composer density | UX-P0-03 | **Closed** — `7166edd` |
| PL-UX-03 | Queue/steer hidden behind long press | UX-P0-04 | **Closed** — `c80652b` |
| PL-UX-04 | Text scale clamped to 1.0–2.0 | UX-P0-05 | **Closed** — `81d1718` |
| PL-UX-05 | Incomplete semantic token migration | UX-P0-06 | **Closed** — `6bba267` |
| PL-UX-06 | Residual raw exception strings | UX-009 | **Closed** — `7287ab6` |
| PL-UX-07 | Localization presented as broader than it is | UX-010 | **Partially closed** — the code is unchanged and still English-only, but `81bec00` now states that in the README's Status section rather than leaving it to be discovered |
| PL-UX-08 | No automated accessibility gate | UX-007 | **Closed** — `f24643e`, extended by `6ce8a07` and `d815a7e` |

## A7. Test and CI plan (§7)

**Automated lanes.** Eleven of twelve are Open for the same reason: CI does
not run (P0-03), so a lane cannot be added to it.

| ID | Lane | Status |
|---|---|---|
| PL-CI-01 | `flutter analyze` on the pinned Flutter | **Open** in CI; run locally on every change |
| PL-CI-02 | Full suite, serial or reliably sharded | **Open** in CI; run locally (912 tests, serial) |
| PL-CI-03 | Android lint on the merged release manifest | **Open** |
| PL-CI-04 | Debug and test-signed release builds | **Open** |
| PL-CI-05 | Generated SDK / contract drift verification | **Open** in CI; the workflow encodes it |
| PL-CI-06 | License and provenance verification | **Partially closed** — `8fa83fb` and `a73c2c9` make it a repo test that fails on drift in either direction; it is not a CI job |
| PL-CI-07 | Secret / high-entropy scan of tree and history | **Open** |
| PL-CI-08 | Dependency vulnerability scan and CycloneDX/SPDX SBOM | **Open** — `ec06627` adds Dependabot, which is not this |
| PL-CI-09 | Accessibility widget tests | **Closed** — `f24643e` (`test/accessibility_guidelines_test.dart`, three guidelines across nine surfaces in light and dark), `6ce8a07` (populated Workspace, Manage project), `d815a7e` (Activity). `81d1718` adds `test/text_scale_overflow_test.dart` at 2.5× |
| PL-CI-10 | Golden matrix across theme packs, brightness, width, text scale | **Open** (= UX-204) |
| PL-CI-11 | Android instrumentation smoke on current and older API levels | **Open** |
| PL-CI-12 | Reproducible release metadata check | **Open** |

**Real-device scenarios (PL-DEV-01…11).** All eleven **Open**: clean install
and first secure connection; upgrade across the certificate lineage; v1 and
v2 connect/auth; foreground-service start/stop and process death;
notification actions and stale replay; offline queue reconnect and once-only
delivery; Termux install/start/stop/update; model download cancel, resume,
corruption; large text with TalkBack on the critical path; attach, preview,
review, terminal across lifecycle wake; Shorebird update and rollback. No
device-run evidence exists in the range.

## A8. Public repository governance (§8)

| ID | Item | Status |
|---|---|---|
| PL-GOV-01 | `SECURITY.md` — supported versions, private reporting, response expectations, disclosure policy | **Closed** — `ec06627` |
| PL-GOV-02 | GitHub private vulnerability reporting enabled | **Open** — owner-side repository setting. `SECURITY.md` links the advisory form and gives a fallback if it is not enabled, but this cannot be closed from the tree |
| PL-GOV-03 | `CONTRIBUTING.md` | **Closed** — `8fa83fb`, extended by `658ced0` |
| PL-GOV-04 | `SUPPORT.md` separating app / server / provider / Termux / Shorebird | **Closed** — `ec06627` |
| PL-GOV-05 | Issue forms with a security redirect | **Closed** — `ec06627`: `bug_report.yml`, `feature_request.yml`, and a `config.yml` that disables blank issues and puts the private advisory form first |
| PL-GOV-06 | PR template | **Closed** — `ec06627` |
| PL-GOV-07 | `CODE_OF_CONDUCT.md` | **Partially closed** — `ec06627` adds Contributor Covenant 2.1, but its contact is a `CONDUCT-CONTACT-TODO` placeholder marked as owner action. Until a real contact is filled in, the document promises a process nobody can start |
| PL-GOV-08 | Public roadmap and support matrix | **Open** |
| PL-GOV-09 | Explicit "unofficial / community client" wording | **Closed** — `4893192` puts the non-affiliation statement at the top of the README and on both tabs of the in-app About screen; `ec06627` repeats it in `SECURITY.md`, `SUPPORT.md`, and both issue forms; a hygiene test asserts it on every public entry point |
| PL-GOV-10 | Branch protection, required reviews, signed release process | **Open** — owner-side. `ec06627` adds `CODEOWNERS`, which enforces nothing without branch protection and says so |
| PL-GOV-11 | Protected tags | **Open** — owner-side |

## A9. Prioritized remediation backlog (§9)

**P0 (1–10).**

| # | Item | Status |
|---|---|---|
| 1 | Secure Ubuntu helper defaults and docs | **Closed** — `c2a8770`, `165acae` |
| 2 | Harden external form links | **Closed** — `7836a6d` |
| 3 | Restore and pass CI on the launch commit | **Open** |
| 4 | Publish from a clean tree and history | **Partially closed** — `8fa83fb`, `658ced0` |
| 5 | Document or remove bundled skill provenance | **Closed** — `8fa83fb` |
| 6 | Complete profile-data cascade deletion | **Closed** — `e957246` |
| 7 | Cut and verify a fresh artifact | **Open** |
| 8 | Final secret and history scan | **Open** |
| 9 | Security policy, private reporting, core governance files | **Closed** — `ec06627` (except PL-GOV-02 and the CoC contact) |
| 10 | Correct the privacy policy | **Open** |

**P1 (11–27).**

| # | Item | Status |
|---|---|---|
| 11 | Android lint and merged-manifest assertions | **Open** |
| 12 | SBOM, dependency scanning, build provenance | **Open** |
| 13 | Offline-queue quota, count cap, expiry, storage UI | **Open** |
| 14 | Structured profile-owned storage for prompts and drafts | **Open** |
| 15 | Remove the query-token auth helper and other dead sensitive code | **Open** |
| 16 | Notification and OAuth adversarial tests | **Open** |
| 17 | Split `ConnectionController` | **Open** |
| 18 | Pinned live contract tests | **Open** |
| 19 | Complete the semantic token migration | **Closed** — `6bba267` |
| 20 | Remove the text-scale clamp; pass 2.5× | **Closed** — `81d1718` |
| 21 | Automated a11y tests plus TalkBack device smoke | **Partially closed** — `f24643e` et al.; device smoke Open |
| 22 | Merge duplicate global surfaces | **Closed** — `d815a7e` |
| 23 | Make queue/steer visibly selectable | **Closed** — `c80652b` |
| 24 | Normalize raw error paths | **Closed** — `7287ab6` |
| 25 | Complete localization or ship explicit English-only | **Partially closed** — declared English-only in the README (`81bec00`); no in-app statement, no extraction |
| 26 | Per-ABI artifacts and documented compatibility | **Open** |
| 27 | Install/upgrade/rollback continuity testing | **Open** |

**P2 (28–37).** All **Open** except where noted: tablet master-detail (28);
a genuine desktop lane before claiming desktop support (29) — *partially*,
in that `81bec00` now calls the desktop build experimental rather than
claiming support, which is the honest half of the item; desktop interaction
layer (30); opt-in issue telemetry (31); local storage management and export
(32); release support matrix (33); automated README release references (34);
signed tags and attestations (35); rollback and incident playbooks (36);
periodic notice, provenance, and threat-model review (37) — *partially*, in
that `a73c2c9` regenerated the notices and added a test that forces the
review whenever `pubspec.lock` moves.

## A10. Launch gates (§10)

**Gate A — repository public.** Clean tree ✅ (`8fa83fb`, `658ced0`);
clean history ❌; secret scan ❌; `.claude` provenance ✅ (`8fa83fb`); Ubuntu
helper secure ✅ (`c2a8770`, `165acae`); external link policy ✅ (`7836a6d`);
privacy policy accurate ❌; `SECURITY.md`, support docs, and issue templates
✅ (`ec06627`) with private reporting itself ❌ (owner-side); third-party
inventory matches the tree ✅ (`8fa83fb`, `a73c2c9`).

**Gate B — preview APK.** Every box ❌. No CI, no verified artifact, no
device evidence.

**Gate C — stable.** Profile-data deletion ✅ (`e957246`); queue lifecycle ❌;
automated accessibility ✅ (`f24643e`) and device accessibility ❌; UX
convergence ✅ for the named blockers; usability testing ❌; preview-cycle
evidence ❌.

---

# Part B — UI/UX audit

## B1. Highest-priority findings (§4)

### UX-P0-01 — Duplicate global control centers
**Closed.** `d815a7e` (*Unify Mission Control and Requests into one Activity
surface*) created `lib/ui/screens/activity_screen.dart` with **Needs
attention** / **Server requests** / **Running** / **Recently completed**,
where each attention row opens the exact resolver instead of dropping the
user into a chat. One badge, on one destination. Both duplicate app-bar icons
are gone; the More hub dropped both cards. `/activity` and `/requests` both
resolve, so existing notification deep links still land. Nothing named
"Mission Control" remains in `lib/`.

### UX-P0-02 — Workspace diluted by project management
**Closed.** `5aa0fc0` (*Make Workspace session-first behind one Manage
project route*) reordered the screen to context header → active sessions →
recent → archived, put worktrees, managed workspaces, project health, and
project switching behind a single **Manage project** entry, and replaced the
project row plus workspace chip strip with one context sheet. `6ce8a07`
added the populated Workspace and Manage project to the accessibility gate.

### UX-P0-03 — Compact composer control density
**Closed.** `7166edd` (*Collapse the composer's secondary tools behind one
affordance*) put Commands, Attach, and Voice behind one leading tools button
opening a sheet of labelled 48 dp rows, and turned the model/agent control
into an outlined context chip. At 2.5× text on a 360 dp phone the prompt
field went from 82 dp of 340 dp to nearly the full composer width in
portrait. `composer_layout_test` pins the anatomy.

### UX-P0-04 — Queue vs steer hidden behind a long press
**Closed.** `c80652b` replaced the hidden gesture with two labelled chips —
*While running: Steer / Queue* — reachable by keyboard and TalkBack, each
with a consequence hint, shown only while a run is active. The long press
survives as a shortcut that updates the visible selection.

### UX-P0-05 — Global text-scale clamp
**Closed.** `81d1718` turned the 1.0–2.0 clamp into a ceiling at
`AppTheme.maxTextScale` (2.5), so small scales pass through unchanged. The
form-renderer header scrolls with its fields and apply-bar buttons stack;
`test/text_scale_overflow_test.dart` renders nine surfaces at 2.5× on 360 dp.

### UX-P0-06 — Incomplete semantic token migration
**Closed.** `6bba267` closed all fifteen Lens 4 findings across roughly 224
call sites: `AppStatusTone` and `statusColor()` for raw green/orange,
`mutedOf()` for 44 `hintColor` sites, `hairline()` for 24, 33 one-off font
sizes onto the type scale, ten off-grid radii onto 12/14, `AppIcons` with 38
references, title roles, `SectionLabel`, and 66 `AppMono` literals onto
`monoFamily`. The review workspace's hardcoded diff hexes now derive from the
active pack.

*Remainder, tracked as Open:* motion durations and easing were explicitly out
of scope (= UX-205).

## B2. Navigation and IA (§5)

| ID | Finding | Status |
|---|---|---|
| UX-NAV-01 | Phone nav should be Workspace / Files / Terminal-or-Activity / More | **Closed** — `d815a7e` adopted model 1; Terminal moved to More and keeps its in-session entry |
| UX-NAV-02 | Decide Terminal-vs-Activity through user testing, not intuition | **Open** — the decision was made without recorded testing |
| UX-NAV-03 | At most one contextual action plus overflow in the root app bar | **Closed** — `d815a7e` |
| UX-NAV-04 | App-bar title should convey server/profile, destination, and connection status without relying on color alone | **Open** |

## B3. Onboarding and server connection (§6)

| ID | Finding | Status |
|---|---|---|
| UX-ONB-01 | Lead with intent, not topology | **Open** |
| UX-ONB-02 | Defer LAN / loopback / proxy / bridge language until a path is chosen | **Open** |
| UX-ONB-03 | Security summary before connecting remotely | **Open** |
| UX-ONB-04 | After a successful Test, show generation, version, auth state, and what Save does | **Open** |
| UX-ONB-05 | Do not pre-seed a scheme that survives being typed over | **Open** |
| UX-ONB-06 | One checklist for on-device setup time, storage, battery, persistence | **Open** |
| UX-ONB-07 | Make remote setup docs secure by default; stop recommending LAN HTTP | **Closed** — `c2a8770`, `165acae`, and `81bec00` for the README's own connection section |
| UX-ONB-08 | Steppers only where steps are genuinely sequential | **Open** |
| UX-ONB-09 | Suggested first-run copy | **Open** |

## B4. Chat and session experience (§7)

| ID | Finding | Status |
|---|---|---|
| UX-CHAT-01 | Session header exposes three overlapping entries; collapse into one "Inspect session" | **Open** |
| UX-CHAT-02 | Message actions are long-press-only and need a 44 dp affordance plus an optional select-text mode | **Partially closed** — `f24643e` raised the affordance from 32×28 to 44×44. Select-text mode Open; the same commit removed `SelectableText` from permission-sheet resource rows in favour of a visible Copy button |
| UX-CHAT-03 | Show jump-to-latest *and* a new-message count; stop gating history help on a 30-message threshold | **Open** (= UX-104) |
| UX-CHAT-04 | Draft attachments are silently lost | **Partially closed** — `7287ab6` took the labelling option; the staging store was not built |
| UX-CHAT-05 | Model/agent apply scope must be explicit, not inferred from protocol-specific button copy | **Open** (= UX-108) |

## B5. Activity, requests, permissions, questions, forms (§8)

| ID | Finding | Status |
|---|---|---|
| UX-ACT-01 | Unified Activity with three sections, each row opening its resolver | **Closed** — `d815a7e` |
| UX-PERM-01 | Show plain-language action, resources, originating session/tool, and scope | **Open** |
| UX-PERM-02 | Distinguish "Allow once" from "Always allow" visually and in copy | **Open** |
| UX-PERM-03 | Say where an "always" rule can later be removed | **Open** |
| UX-PERM-04 | Reject-with-message should say the text goes back to the agent | **Open** |
| UX-PERM-05 | Notification actions must mirror in-app terms exactly | **Open** |
| UX-FORM-01 | Safe external-link policy in forms | **Closed** — `7836a6d` |
| UX-FORM-02 | Heading and group semantics in forms | **Open** |
| UX-FORM-03 | Required/optional announced consistently | **Open** |
| UX-FORM-04 | Full large-text and TalkBack coverage for forms | **Partially closed** — `81d1718` repaired the header and apply bar at 2.5×; `f24643e` put the form renderer in the a11y gate. TalkBack device coverage Open |
| UX-FORM-05 | Locale-aware date/time if localization expands | **Open** — moot while English-only |
| UX-FORM-06 | Explain global server/MCP requests versus session requests | **Partially closed** — `d815a7e` separates them into a *Server requests* subsection; the explanation itself is not written |

## B6. Files, Changes, and Review (§9)

| ID | Finding | Status |
|---|---|---|
| UX-FILE-01 | After a run, "what changed?" has no obvious path | **Closed** — `2031adc` adds a standing changed-files card above the tree with count and ± totals, a Changes sheet grouped by VCS status that opens Review at a file, and *Review all changes* |
| UX-FILE-02 | A review comment must reach the originating session's composer without the clipboard | **Closed** — `ec8dad3` adds `ReviewHandoffStore` with structured per-session references (path, line label, diff snippet, comment, scope, counts) and stages them from the comment sheet, the selection bar, and the whole-file toolbar; `1b0ba4a` renders them as removable chips that fold into the prompt at send; `2031adc` adds the Files-side staging. Clipboard remains as a fallback |
| UX-REV-01 | Separate phone and tablet/desktop Review toolbars | **Open** — mitigated only in that `ec8dad3` made the selection bar fit 360 dp at large text |
| UX-REV-02 | File/scope/mode as a hierarchy, not three equal controls | **Open** |
| UX-REV-03 | Keep hunk navigation available while a selection is active | **Open** |
| UX-REV-04 | Clear reviewed/viewed progress | **Open** — a `review-viewed-progress` counter exists, but it already existed at the audited commit (`b52421b`, before `9e7d5ce`), so the audit made this recommendation with it in place. Nothing in the range changed it |
| UX-REV-05 | Preserve file, scroll, selection, and comment context through refresh | **Open** |
| UX-REV-06 | "Add to prompt" should route to the session, not copy | **Closed** — `ec8dad3`, `1b0ba4a`, `2031adc` |
| UX-REV-07 | Semantic diff colors from the active theme pack | **Closed** — `6bba267` |

## B7. Terminal (§10)

| ID | Finding | Status |
|---|---|---|
| UX-TERM-01 | Decide by research whether Terminal deserves a permanent tab | **Partially closed** — `d815a7e` decided (it does not, and moved it to More), but without the research the finding asked for; see UX-NAV-02 |
| UX-TERM-02 | Show active shell, session, and working directory clearly | **Open** |
| UX-TERM-03 | Discoverable reconnect/reset with consequence copy | **Open** |
| UX-TERM-04 | Configurable special-key row and horizontal overflow behavior | **Open** |
| UX-TERM-05 | Native shortcuts on desktop; hide the mobile key row | **Open** |
| UX-TERM-06 | Semantic terminal colors instead of brightness fallbacks | **Closed** — `6bba267` |

## B8. More and Settings (§11)

| ID | Finding | Status |
|---|---|---|
| UX-MORE-01 | Remove the duplicate Activity/Requests destinations from More | **Closed** — `d815a7e` |
| UX-MORE-02 | Keep one active model/agent setup card | **Open** |
| UX-MORE-03 | Group Providers and MCP under Integrations if users do not distinguish them | **Open** |
| UX-MORE-04 | Group Commands/Skills/References under user language, if it tests well | **Open** |
| UX-SET-01 | Disconnect in Settings only, with unsent/queued/background consequences | **Open** |
| UX-SET-02 | Server health in human language, technical detail secondary | **Open** |
| UX-SET-03 | Platform-gate Android-only features everywhere, routes included | **Open** |
| UX-SET-04 | Local storage section for drafts, queued prompts, voice models, caches | **Open** (= backlog #32; also blocks PL-PRIV-01) |
| UX-SET-05 | An accessibility section only for genuine options | **Open** — `81d1718` removed the system-overriding clamp, which was the reason not to add workaround settings |
| UX-SET-06 | Explain Shorebird and desktop update behavior transparently | **Open** — `7836a6d` hardened the desktop update link without changing disclosure |

## B9. Visual design system (§12)

| ID | Finding | Status |
|---|---|---|
| UX-VIS-01 | Required token set: surface, text, semantic, diff, spacing, radii, type, motion | **Partially closed** — `6bba267` delivered all of it except motion tokens (= UX-205) |
| UX-VIS-02 | No raw `Colors.green/orange/red` for semantic state | **Closed** — `6bba267` |
| UX-VIS-03 | No `hintColor` as the muted-text role | **Closed** — `6bba267`, 44 sites |
| UX-VIS-04 | No repeated `AppMono` literals outside the theme layer | **Closed** — `6bba267`, 66 literals |
| UX-VIS-05 | No local one-off font sizes on flagship screens | **Closed** — `6bba267`, 33 sites; three 10 px micro-badges kept deliberately |
| UX-VIS-06 | No new surface recipe without a named component or token | **Open** — no enforcement mechanism exists |
| UX-VIS-07 | No icon synonym drift | **Closed** — `6bba267`, `AppIcons` with 38 references |
| UX-VIS-08 | Fewer borders and nested cards; prefer spacing and typography | **Open** |
| UX-VIS-09 | One primary accent per screen; status color semantic, not decorative | **Partially closed** — `6bba267` |
| UX-VIS-10 | Content is the hero, not chrome | **Partially closed** — `7166edd` for the composer |
| UX-VIS-11 | Cards only for grouped choices and actionable objects | **Open** |
| UX-VIS-12 | Code and terminal treatment functional, not louder than prose | **Open** |

## B10. Accessibility (§13)

| ID | Finding | Status |
|---|---|---|
| UX-A11Y-01 | Automated tap-target, label, and contrast checks across brightness, width, and text scale | **Partially closed** — `f24643e` covers three guidelines across nine surfaces in light and dark, and caught two real defects (a 32×28 dp message-actions target and long-press-only `SelectableText` resource rows). `6ce8a07` and `d815a7e` extended the surface list; `81d1718` covers text scale in a separate test. Narrow/wide variants and semantics snapshots are Open |
| UX-A11Y-02 | Nine TalkBack and large-text device tests | **Open** — no device evidence |
| UX-A11Y-03 | Status must not rely on color alone | **Open** |
| UX-A11Y-04 | Heading semantics for sections and form groups | **Open** |
| UX-A11Y-05 | Focus order follows visual order | **Open** |
| UX-A11Y-06 | Modal dismissal restores focus | **Open** |
| UX-A11Y-07 | Every gesture action has a visible alternative | **Closed** — `f24643e` (permission-sheet copy) and `c80652b` (queue/steer chips) |
| UX-A11Y-08 | Reduced motion preserves state clarity | **Open** |
| UX-A11Y-09 | Visible desktop focus indicator | **Open** |
| UX-A11Y-10 | Controls operable at 2.5× text | **Closed** — `81d1718`, with `7166edd` and `ec8dad3` sized for 360 dp at 2.5× |

## B11. Copy and content design (§14)

| ID | Finding | Status |
|---|---|---|
| UX-COPY-01 | Eight vocabulary renames | **Partially closed** — `d815a7e` delivers *Mission Control → Activity* and *Pending requests → Needs attention*. The other six (Remote machine (LAN), Session views, Session actions, Use model and mode, Health unavailable, Nothing in flight) are Open |
| UX-COPY-02 | Destructive-action template | **Closed** — `e957246`: the remove-server confirmation counts the queued prompts and drafts at stake, names what else goes, and says nothing is deleted on the server or at the providers |
| UX-COPY-03 | Lead error copy with the failed user goal | **Partially closed** — `7287ab6`'s `productErrorText` funnel |
| UX-COPY-04 | One concrete next step | **Partially closed** — `7287ab6` |
| UX-COPY-05 | Redacted technical detail secondary and copyable | **Open** |
| UX-COPY-06 | Never show raw exception prefixes or response bodies | **Closed** — `7287ab6`, 14 call sites plus three deleted duplicate helpers |
| UX-COPY-07 | One retry term | **Partially closed** — `7287ab6` |

## B12. Responsive and desktop (§15)

| ID | Finding | Status |
|---|---|---|
| UX-RESP-01 | Primary phone actions in the lower half | **Open** |
| UX-RESP-02 | Text field stays dominant with the keyboard open | **Closed** — `7166edd` |
| UX-RESP-03 | Dense app bars collapse to one action plus overflow | **Closed** — `d815a7e` |
| UX-RESP-04 | No control below 48 dp without a larger semantic wrapper | **Partially closed** — `f24643e` enforces it in the gate for the surfaces the gate covers |
| UX-RESP-05 | Avoid nested vertical scroll traps | **Open** |
| UX-RESP-06 | Foldable and tablet master-detail | **Open** (= UX-201) |
| UX-RESP-07 | Readable measure for conversation text on wide layouts | **Open** |
| UX-DESK-01…09 | Platform gating, context menus, drag-and-drop, command palette, resizable panels, native scrollbars, window persistence, keyboard traversal, packaging and CI | **Open**, all nine. `81bec00` now calls the desktop build experimental instead of claiming support, which addresses the *claim* but none of the work |

## B13. Perceived performance and trust (§16)

| ID | Finding | Status |
|---|---|---|
| UX-PERF-01 | Never invent progress percentages | **Open** |
| UX-PERF-02 | Show stage, downloaded size, resumability for setup and downloads | **Open** |
| UX-PERF-03 | Distinguish locally queued, server-admitted, and actively delivered prompts | **Open** — `c80652b` makes the delivery *mode* explicit, not the three states |
| UX-PERF-04 | Global queue-flush confirmation, not only inside an open chat | **Open** |
| UX-PERF-05 | Preserve scroll, selection, and context through refresh | **Partially closed** — `7287ab6` fixed the reasoning toggle overwriting per-part expansion state |
| UX-PERF-06 | Visible "new output below" count | **Open** (= UX-104) |

## B14. Implementation plan (§17)

**Phase 1 — public-preview blockers.** UX-001 ✅ `d815a7e`; UX-002 ✅ for
actions (`d815a7e`), title readability Open; UX-003 ✅ `7166edd`; UX-004 ✅
`c80652b`; UX-005 ✅ `81d1718`; UX-006 ✅ `6bba267` (motion excluded);
UX-007 ✅ `f24643e`, `6ce8a07`, `d815a7e`; UX-008 ✅ `7836a6d`; UX-009 ✅
`7287ab6`; UX-010 ⚠️ declared English-only in the README (`81bec00`), no
extraction and no in-app statement.

**Phase 2 — workflow convergence.** UX-101 ✅ `5aa0fc0`; UX-102 ✅ `2031adc`;
UX-103 ✅ `ec8dad3` + `1b0ba4a`; UX-104 ❌; UX-105 ❌; UX-106 ⚠️ labelled, not
persisted (`7287ab6`); UX-107 ✅ `5aa0fc0`; UX-108 ❌.

**Phase 3 — polished/stable.** UX-201 ❌; UX-202 ❌; UX-203 ❌; UX-204 ❌;
UX-205 ❌; UX-206 ❌. None started.

## B15. Launch UX gate (§19)

**Controlled public preview.** One Activity entry and badge ✅; composer text
width ✅; queue/steer discoverable ✅; text scale honored to 2.5× ✅;
automated tap-target, label, and contrast checks ✅; safe server-link policy
✅; no raw exception strings ✅; flagship token migration ✅; first-run
security and trust copy ❌; English-only stated in public docs ✅
(`81bec00`).

Nine of ten. The one open box is first-run copy (UX-ONB-03).

**Polished / stable.** Workspace hierarchy validated with users ❌;
Files → Changes → Review → Prompt coherent ✅; TalkBack device path ❌;
golden matrix ❌; tablet master-detail ❌; desktop claims match support ✅
(by lowering the claim, `81bec00`); no unresolved high-severity issue ⚠️ —
all six UX-P0s are closed, but public-launch P0-03, P0-04, and P0-07 remain.

## B16. Usability testing (§18)
**Open.** Ten tasks and nine measures are specified; none were run. This also
blocks UX-NAV-02, UX-TERM-01's evidence, UX-206, and the Gate C usability box.

---

## Accepted risks

Five things are not being fixed, deliberately. Recording them here so they
are decisions rather than oversights.

1. **AI session URLs stay in commit history** (P0-04 item 6). Every commit in
   the remediation range, and every commit in this one, carries a
   `Claude-Session:` trailer. Removing them means rewriting published
   history, which breaks every existing clone and every commit hash cited in
   this document. The trailers disclose that the work was AI-assisted, which
   is true; they were judged not worth a history rewrite.
2. **The engineering log stays in history.** `658ced0` removed
   `docs/internal/handoff.md` from the tree, not from history. Same reason.
   Anything genuinely sensitive in it would need the history rewrite above;
   it was reviewed and contains local paths and stale claims, not secrets.
3. **CI cannot be fixed from the tree** (P0-03). The block is a GitHub
   account-level billing state. The response was to stop claiming CI runs
   rather than to pretend otherwise.
4. **Three 10 px micro-badges** survive the type-scale migration
   (UX-VIS-05), kept deliberately by `6bba267`.
5. **English-only ships** rather than blocking on extraction (UX-010,
   UX-203). Declared in the README instead of implied.

## What has to happen before "public launch" is honest

Short list, in order:

1. Fix the GitHub Actions billing block and get one green run on the launch
   commit (P0-03).
2. Correct `PRIVACY.md` for drafts, queued prompts and their attachments, the
   Termux-side password copy, update checks, and what profile deletion
   actually removes (PL-PRIV-04). It is the only document left in the tree
   that says something untrue about user data.
3. Fill in the code-of-conduct contact (PL-GOV-07) and enable GitHub private
   vulnerability reporting (PL-GOV-02). Both are owner-side and take minutes.
4. Run a secret and high-entropy scan over tree and history, and resolve
   whatever it finds (P0-04, PL-CI-07).
5. Cut an artifact from the remediated commit and publish its hashes, signer,
   and provenance (P0-07).
6. Pin or verify the OpenCode installer in `scripts/host/ubuntu-opencode.sh`
   instead of piping a mutable URL (P0-01 remainder).

Items 3 and 6 are small. Item 1 is a payment. Items 2, 4, and 5 are real
work.
