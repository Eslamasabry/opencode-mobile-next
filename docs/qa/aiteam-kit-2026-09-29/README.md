# AI Team kit — 2026-09-29

Finish line: localized presentation data can drive project and milestone rows,
plan/phase/findings/promotion cards, merge queue, digest, timeline and server
lanes, plus spec draft sections, with readable states and usable controls.
Non-goal: routing, persistence, engine actions or real project execution. Those
are integrated by the screen and engine slices; this slice alone is groundwork.

## Changes

Eleven exported widgets live under `lib/ui/kit/team/`. Constructor contracts are
in [API.md](API.md). Cards share a private implementation to keep their visual
rules consistent without exposing an arbitrary styled-container API. Data and
callbacks are engine-neutral; all words are caller-localized. Timeline folds
beyond ten entries, and spec fields keep the caller's draft controller.

`KitChipTone` adds neutral, attention and danger severity choices. Critical,
Major and Minor remain explicit words; semantic ink is contrast-guarded against
both chip surfaces. Existing callers retain the neutral default.

Stale/loading card actions are suppressed; findings selection is read-only in
those states. Failures use plain caller copy with a red icon. Waiting for a host
uses neutral ink, never needs-you amber. Progress requires actual completed/total
counts. There are no ambient animations or fabricated progress bars.

## Spacing and accessibility

The host owns the single 16 dp page gutter. A row adds zero side padding and
12 dp (`space3`) vertical padding. A card adds 16 dp (`space4`) inner padding,
no outer padding. Card rows therefore start at 32 dp from a 16 dp page rail,
with no further nested panel inset. Row metadata wraps below its title. Titles
wrap, checkbox hit areas are 48 dp, and controls reuse kit keyboard/focus rules.

The responsive frame is caller-owned (`KitScreen.threePane`); these components
stretch to the allotted pane and preserve reading order at 360 dp and 2x text.
Phone 412×915 and wide 1280×800 Android galleries cover light and dark plus
2x text. No colour alone conveys state. No per-second live-region updates.

## Evidence

Before: these components did not exist. The approved design references are
[plan](../aiteam-mockups-2026-09-29/4-plan-card.png),
[findings](../aiteam-mockups-2026-09-29/5-findings-card.png),
[promotion](../aiteam-mockups-2026-09-29/6-promote-card.png), and
[wide overview](../aiteam-mockups-2026-09-29/2b-project-overview-wide.png).
They are dark mockups, not screenshots of prior running components; no light
before image is claimed. Screen-level before/after is owned by the screen slice.

After: committed component galleries under `test/goldens/kit/kit_*` for each
new component, linked below after final validation. Contact sheets are review
aids; original captures remain the golden truth.

## Validation log

- Pinned Flutter 3.47.1 `pub get`: passed.
- First scoped analyzer found a ShapeBorder API mismatch and one braces lint;
  both corrected before the focused passing behavior run.
- Initial focused run: 31 passed, manifest failed because dynamic state names
  could not be statically recognized and new rows needed keyboard coverage.
  Explicit golden calls and keyboard activation cases were then added.
- Initial gallery run: 196 passed / 32 failed. Failures exposed low contrast in
  small danger copy and the Major chip on a light chip surface. Failure copy now
  uses primary text with a red icon; severity ink uses existing `readableOn`.
- Final focused behavior + existing chip/keyboard + manifest: **114 passed**.
- Final full analyzer checkpoint reported one unnecessary test import; removed.
  Final scoped analyzer over team parts, chip, modified chip/keyboard/manifest
  tests and shared test support: **No issues found**.
- Final galleries: **228 scenes covered**. The all-gallery pass recorded
  212 passing cases and 16 error-copy failures. Failure typography was changed
  to the readable medium-weight row-title role (16 dp). The seven affected
  error galleries then passed 14/14 cases. Findings and spec were regenerated
  in full after adding Critical severity evidence and correcting the read-only
  spec gutter: 30/30 gallery cases plus 4/4 affected behavior tests passed.
  No other state rendering changed after its passing capture.
- Every generated image was inspected using per-part contact sheets; the
  regenerated errors, findings and spec were inspected again. The exact 228
  image hashes are in [gallery-manifest.txt](gallery-manifest.txt).
- Formatting and `git diff --check`: passed. Integration gates and the complete
  application suite belong to the coordinator's final combined candidate;
  this slice does not claim that whole-repository gate.

The manifest's allowed directory list gained `lib/ui/kit/team/`, as explicitly
required by the build prompt. All existing state/test/gallery/coverage checks
remain enforced; no new allowlist entries or lowered coverage.

Still requires a device: TalkBack traversal, physical keyboard focus, Android
font fallback, landscape with the IME, real running/reconnect transitions and
actual routing/actions in the integrated screens. No APK was built or signed.


## After image entry points

- [Phone dark plan](../../../test/goldens/kit/kit_plan_card_needs_you_dark.png)
- [Phone light plan](../../../test/goldens/kit/kit_plan_card_needs_you_light.png)
- [Wide dark plan, 2x text](../../../test/goldens/kit/kit_plan_card_text2_1280x800_dark.png)
- [Wide light plan, 2x text](../../../test/goldens/kit/kit_plan_card_text2_1280x800_light.png)
- [Phone dark severity](../../../test/goldens/kit/kit_findings_card_needs_you_dark.png)
- [Phone light severity](../../../test/goldens/kit/kit_findings_card_needs_you_light.png)
- [Phone dark failure](../../../test/goldens/kit/kit_plan_card_error_dark.png)
- [Phone light failure](../../../test/goldens/kit/kit_plan_card_error_light.png)

All commands used the pinned toolchain and `OC_TEST_SLOTS=1`,
`TMPDIR=/home/owner/Storage/tmp/aiteam-build`, and
`tool/qa/machine_lock.sh test|analyze --`. Goldens used
`flutter test --no-pub --concurrency=1 --update-goldens`; the final recovery
pass added `--name '^error '`. See the test and gallery file lists in the
component docs. No localization generator ran in this worktree; all new kit
labels are supplied by callers (timeline's Show all reuses existing l10n).


### Token gate follow-up (2026-09-30)

The coordinator's integration scan caught G17 literal ink endpoints and a G21
implicit border width. Commit `abc745ef` uses `roles.text1` as the readable-ink
endpoint and `KitTokens.hairlineWidth(context)` for the card border. The affected
attention captures were regenerated (24/24 passed), all findings captures plus
existing chip behavior passed (90/90), and the final scoped analyzer was clean.
Every changed capture was inspected again and the gallery hashes refreshed.
These are token-rule fixes; no baseline was expanded.
