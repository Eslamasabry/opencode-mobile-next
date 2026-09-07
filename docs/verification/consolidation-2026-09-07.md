# Consolidated mobile candidate — 2026-09-07

Finish line: preserve and integrate the pending mobile batch, complete the setup
feedback/reuse slice, validate one source candidate, and leave a clean committed
worktree with a single reviewable PR to `dev`.

Non-goals: new OC2/musl installation adapters, public release/tag, `master`
advancement, device installation, or changes to the live Termux session server.

## Preserved input and ownership

The original worktree was based on `84e1e0f`: 61 modified tracked files,
33 untracked status entries, nothing staged, +7,274/-946 tracked lines. The
complete changed/untracked file set was saved to
`/tmp/ocmn-pre-consolidation-2026-09-07.tar.gz` before creating
`codex/consolidated-mobile`. A fresh fetch confirmed `origin/dev` remained at
`84e1e0f`.

The focused session fix from `4cac369`/PR #77 is included, alongside the original
stash, attention/handoff, auth, web-source, usage/quota, voice, iOS and platform
work. Its earlier 1,827-test result does not cover this combined candidate.

Work was split with single ownership: setup screen and integration gates by the
lead; terminal/installer/inventory and capture harness; auth integration fixes;
and native/stash review plus voice regression tests. Only the lead ran tests,
serially. All evidence uses synthetic data unless explicitly stated otherwise.

## Completed integration changes

- Setup immediately shows its current phase and elapsed time while native calls
  are pending. A flat full-height log uses readable semantic colors; normal,
  large-text and delayed-launch behavior have focused coverage.
- A bounded, read-only probe identifies managed Ubuntu and installed OpenCode.
  Users may reuse the installed command, run the pinned Ubuntu installation, or
  connect an existing v1/v2 server. No unverified OC2/musl installer is exposed.
- npm explicitly requires the matching Ubuntu binary package and includes
  optional dependencies, retaining version pinning, retries and temporary-cache
  cleanup. Foreground lifecycle output makes failures visible.
- The published 1.18.29 wrapper tries the glibc package before musl. The reported
  EBADPLATFORM is a fallback symptom; the first failure cannot be established
  from that excerpt. [Registry metadata](https://registry.npmjs.org/opencode-linux-arm64/1.18.29)
  confirms the glibc ARM64 package exists;
  the already installed binary returned 1.18.29 using isolated HOME/XDG paths.
  No live server/global installation was changed by this investigation.
- OAuth instructions/device codes are displayed transiently again. An uncertain
  start without an attempt ID has an explicit scoped local dismissal; this
  neither launches another sign-in nor cancels the server attempt. Existing
  gateway errors cannot reliably distinguish confirmed rejection from uncertain
  dispatch, so no automatic retry was introduced.
- Added speech consent, engine access, background/disposal cancellation and
  unsent-conversation persistence tests. Native speech behavior still requires
  platform validation.

## Accessibility, privacy and migration

Setup uses labelled actions, 48-pixel copy targets, large-text scrolling, and
textual messages alongside color. Elapsed updates do not repeatedly interrupt
screen readers. Synthetic screenshots:
[light](../qa/setup-progress/light.png),
[dark](../qa/setup-progress/dark.png),
[choices](../qa/setup-progress/choices.png).

Device codes, browser authorization URLs and provider credentials are not added
to recovery preferences. Existing stash migration preserves original metadata
on write failures and keeps file cleanup ownership through profile deletion;
see [stash verification](stash-attachments-2026-09-06.md) and
[lifecycle evidence](stash-lifecycle-2026-09-06.md).

## Verification ledger

Toolchain: installed Flutter 3.47.2 / Dart 3.13.2 at `/tmp/opencode/flutter`
(upstream, not a verified Shorebird fork). Dependencies resolved without a
lockfile change. The Android quality workflow is the approved path for a
replacement APK, using the installed stable CI certificate:
`2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`.

Development checks are separate from the final gate. Setup/installer checks,
voice regressions, localization and three capture cases have passed focused
runs; the quota collector passed all 36 synthetic Node tests. Initial fixture
failures and the interrupted fake-time auth test are not counted as passes.
Integration regressions were then resolved, including repository replacement
after wake and offscreen test controls. The full analyzer is clean after lint cleanup. The complete serial and native
candidate gates remain in progress.

The final source revision, full serial manifest/results and native CI results
will be appended only when completed. No prior candidate's results will be
combined with this source snapshot.
