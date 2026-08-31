# Public-release audit — oc_app

Audit date: 2026-08-29. Scope: working tree + full git history of
`Eslamasabry/oc_app` (branch `production/android-release-hardening`, 220
commits across all refs), plus GitHub-side settings, ahead of flipping the
repo from PRIVATE to PUBLIC. History rewriting was explicitly out of scope;
history findings are reported with commit hashes only.

**Bottom line: no secret, key, or credential was found anywhere in the
working tree or in any commit.** The two blockers are licensing and a
gitignore rule that breaks the new README's screenshots — both fixable in
minutes.

---

## BLOCKER — must fix before flipping public

### B1. The project has no license of its own

There is no top-level `LICENSE` file. `LICENSES/` contains only third-party
texts (JetBrains Mono OFL, sherpa-onnx Apache-2.0, ONNX Runtime MIT, OpenAI
Whisper MIT, `record` and `scrollable_positioned_list` BSD-3), all correctly
indexed by `THIRD_PARTY_NOTICES.md`. Nothing states the license of the app's
own code. A public repo without a license is "all rights reserved": the
OpenCode founder — the first invited visitor — could not legally build on,
redistribute, or even fork-and-patch it. Pick a license (MIT or Apache-2.0
are the usual choices and are compatible with every bundled dependency —
see N7), add `LICENSE` at the repo root, and update the License section of
the README (a placeholder is there waiting).

### B2. The README's screenshots are gitignored

`video/.gitignore` ignores `public/shots/*.png` and `*.mp4`. The four stills
the rewritten README references (`video/public/shots/still-workspace.png`,
`still-rec3-end.png`, `still-more-grid.png`, `still-steal-sheet.png`) exist
on disk and show only app surfaces (verified visually safe by filename and
provenance: emulator recordings of this app against a local server), but
they are **not tracked**, so they will 404 the moment the repo is public.
Fix before the flip, either way works:

```bash
git add -f video/public/shots/still-workspace.png \
           video/public/shots/still-rec3-end.png \
           video/public/shots/still-more-grid.png \
           video/public/shots/still-steal-sheet.png
```

or add `!still-*.png` to `video/.gitignore`. (~770 KB total — negligible.)
The large `.mp4` recordings should stay ignored; the showcase videos are
already hosted as release assets.

---

## SHOULD — fix or consciously accept before/at the flip

### S1. Master is stale; PR #1 is still open

The default branch is `master`, but everything the showcase advertises lives
on `production/android-release-hardening`. PR #1 ("Operation Facelift:
previews 1-5 to master") is OPEN. A visitor lands on `master` and sees the
pre-facelift app and the old README. Merge PR #1 (and the newer preview 6/7
work plus this README) into `master` before the flip, or temporarily switch
the default branch. Note: only `master`, `production/android-release-hardening`,
and `v1.0.16+17-patch1` exist on origin — the 20+ local
`worktree-agent-*`/`facelift/*`/`port/*` branches are local-only; do not push
them.

### S2. GitHub Actions is billing-blocked; 35/35 runs failed

`.github/workflows/android-quality.yml` documents it at the top of the file:
every run fails in ~5s with GitHub's account-billing block. Publicly this
looks like a permanently red CI. Either resolve billing at
github.com/settings/billing before the flip, or temporarily disable the
workflow so the Actions tab is quiet rather than red. The workflow itself is
clean (its CI signing key is generated per-job with obvious placeholder
password `ci-not-for-distribution` and the artifact is never distributed).

### S3. `.claude/skills/` republishes third-party prompt packs

Twenty-one tracked files under `.claude/skills/` (motion-design,
remotion-motion-graphics) are internal agent tooling of unclear provenance
and license. They are referenced by `docs/showcase-video-plan.md`, so
removing them breaks those citations, but redistributing someone else's
skill texts in a public repo is a small rights question only the owner can
answer. Review, and either keep knowingly, or remove and soften the plan's
citations later.

### S4. HANDOFF.md — informal, not sensitive; decide its public face

Re-read in full. It contains: Shorebird release IDs, APK SHA-256s, local
absolute paths under the maintainer's home directory, machine quirks
("background tasks get SIGKILLed on this box"), agent-workflow notes, and
pinned-toolchain lore. No
credentials, no personal data beyond the GitHub username already in the repo
URL. It is genuinely useful engineering history, but it reads as an internal
scratchpad and is the file most likely to raise eyebrows ("agents built
this?" is a story the owner should choose to tell deliberately). Options:
keep as-is (honest), retitle/introduce it as an engineering log, or move the
release-runbook parts into docs/ and trim. No safety need to change it.

### S5. Release-notes wording pass

The seven pre-releases from 2026-08-28 are candid ("treat this feature as
the one to bang on hardest", "three widget tests unverified"). That honesty
is a feature, but re-read the notes of the releases you expect visitors to
open (v1.0.27+28-preview.7 especially, since the README links its video
assets) once with public eyes. The APK signing note there ("signer matches
the public lineage 1de5bf08…") is correct and should stay — it is the
verification anchor for sideloaders.

---

## NOTE — reviewed, no action required

- **N1. Secret scan (working tree) — clean.** Patterns checked across all
  tracked and untracked files (excluding `.git`, `build/`, `.dart_tool/`,
  `node_modules`, `video/out`): provider key formats (`sk-…`, `ghp_…`,
  `github_pat_`, `AIza…`, `AKIA…`, `xox…`, JWTs, PEM blocks) — zero hits;
  `password=`/`storePassword`/`keyPassword`/`OPENCODE_SERVER_PASSWORD`
  occurrences are all placeholders (`your-secret`,
  `replace-with-a-strong-secret`), test fixtures (`fixture-store-password`,
  `pw`, `hunter2` in the diagnostics *redaction* test — it exists to prove
  secrets get scrubbed), CI's non-secret `ci-not-for-distribution`, or code
  that reads the value from Keystore/env. `docs/opencode2-protocol-notes.md`
  re-verified: it documents the v2 password *mechanism* and contains no
  actual password; captured sample events carry truncated IDs only.
- **N2. Keystores/env — clean.** `android/key.properties` does not exist and
  is gitignored (`android/.gitignore` also ignores `**/*.jks`,
  `**/*.keystore`); only `android/key.properties.example` (placeholders) is
  tracked. No `.env`, `.pem`, `.p12`, keystore, or debug.keystore anywhere
  in the tree or in history.
- **N3. Git history — clean.** `git log --all --diff-filter=A --name-only`
  surfaced no suspicious filename ever added (the only "credential"/"secret"
  matches are generated SDK model classes like
  `packages/opencode_sdk/lib/src/model/credential_key.dart`). `git log -S`
  probes: `sk-ant` — zero commits; `password=` (c17f4f6, da0459a, 05c3c33),
  `storePassword=` (da0459a, e6f290d, 2e7aa2e), `OPENCODE_SERVER_PASSWORD=`
  (1d82ff1, 05c3c33, ab2ffc6) — each diff inspected; all introduce the
  benign fixtures/examples/docs listed in N1. No history rewriting needed.
- **N4. Hosts/IPs/emails — clean.** All IPs are localhost, emulator
  loopback (`10.0.2.2`), or RFC1918/CGNAT *examples* in tests and hint text
  (`192.168.1.x`, `100.64.0.10`, `box.tail1234.ts.net`). No real private
  hostname. No email address appears in any tracked file; PRIVACY.md's
  contact is the GitHub issues page. Commit authorship is uniformly
  `Ralph TUI Agent <agent@ralph-tui.local>` — no personal email in history
  at all (if the owner *wants* attribution, that is the opposite problem
  and out of scope here).
- **N5. Local paths — resolved.** Absolute paths under the maintainer's home
  directory used to appear in HANDOFF.md,
  `docs/opencode2-protocol-notes.md`, and `test/fixtures/api2/*.json`
  (captured server payloads). Cosmetic only, but every tracked occurrence has
  since been rewritten to the neutral placeholder `/home/dev/projects/oc_app`.
- **N6. `shorebird.yaml` app_id and certificate fingerprints** are public by
  design (Shorebird documents the app_id as shareable; the APK SHA-256 and
  signer fingerprint in README/releases are verification anchors).
- **N7. Dependency licenses — no copyleft.** Direct deps are MIT/BSD-3/
  Apache-2.0 throughout (dio, flutter_riverpod, xterm, highlight,
  file_picker, sherpa_onnx, record, scrollable_positioned_list,
  shorebird_code_push, dynamic_color, window_manager, …). JetBrains Mono is
  OFL-1.1 with the full text in `LICENSES/` and a THIRD_PARTY_NOTICES entry
  — a font bundled under OFL does not restrict the app's license choice.
- **N8. Hygiene.** TODO/FIXME density in `lib/`: exactly one. `.gitignore`
  coverage verified for `build/`, `.dart_tool/`, `*.iml`,
  `.claude/worktrees/`, `video/node_modules/`, `video/out/`, keystores and
  `key.properties`. `incoming-photo.jpg` (untracked test asset) is excluded
  only via local `.git/info/exclude` — harmless (it is untracked), but a
  root `.gitignore` entry would be tidier. The 644 KB
  `marketing/opencode-mobile-demo/opencode-mobile-outreach.mp4` is the only
  committed binary of note; acceptable.
- **N9. `video/public/shots/` recordings** (untracked, stay untracked):
  filenames map to app-surface flows only — first-run, quick-ask, live-run,
  picker, more-hub, settings, files, review, terminal, themes,
  mission-control, plus the five stills. All were recorded on a clean
  emulator against a local `opencode serve`; the one real prompt shown is
  "Explain this project's test strategy" about this repo itself.

---

## GitHub-side checklist before the flip (report only — nothing changed)

1. **Licensing**: land `LICENSE` (B1) and the screenshot fix (B2) first.
2. **Branches / PR #1**: merge the facelift line to `master` or switch the
   default branch (S1); do not push local agent branches.
3. **Actions**: fix billing or disable the workflow so the public Actions
   tab is not a wall of red (S2). Consider branch protection on `master`
   (require the quality gate once it actually runs).
4. **Releases**: the sideload-certificate APK channel is established and
   fine; skim pre-release notes wording (S5); confirm every asset you want
   public is intentional (current assets: preview APKs + two showcase mp4s).
5. **Repo settings**: description is good; add topics (flutter, android,
   opencode, termux, coding-agent); decide Issues (already on) and
   Discussions; consider a social-preview image (e.g. `still-workspace.png`).
6. **After the flip**: the two showcase-video release-asset URLs in the
   README become world-readable automatically; no action needed.
