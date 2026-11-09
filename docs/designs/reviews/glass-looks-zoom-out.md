# Glass looks v1: zoom-out review (simplicity and scope)

Reviewer: Zoom Out goldfish (Skeptical Architect). Target: `docs/designs/glass-looks-2026-11-09.md`. Read from the doc, the
cited glass code (`FrostPolicy.kt`, `LiquidEdge.kt`, `GlassFinish.kt`, `Preferences.kt`) and the repo's "very artificial"
notes (`docs/ux-system/kit-api/KitFrost.md`, `docs/design/native-rewrite-2026-11-01/map/ui-kit.md` R3).
Verdict: do not build as written. Run gate 0 first and ship the smallest set it justifies.

## Findings, ranked

### 1. Blocker. The root problem is "frosty", and the doc itself says one cheap fix may solve it, yet it builds a whole feature first
Scope: section 1 (causes), section 6 phase 0, section 2 table.
Section 1 names five measurable causes. Cause 1 (floor measured against the look's hard cap, unblurred, so panes thicken to
39-98 % over shader looks) and cause 2 (gloss dropped first in dark mode) are bugs in existing code. Phase 0 admits that
"this alone may answer most of 'too frosty'". The owner asked "why is glass so frosty, not glossy?", and asked for options
as a second thing. Fixing causes 1 and 2 and then judging Glossy is a one-slice job with no new storage, no picker, no
preview field. The picker should be justified only by what is left over after that. Reorder: ship the floor fix and the
gloss fix as the Glass default, then re-ask whether anyone needs four looks.

### 2. Major. Four looks is speculative; the set most likely collapses to two
Scope: section 2, D3.
The doc has no evidence anyone wants Smoked or Clear. The doc's own risk list says Clear "can end up as thick as Glossy"
over busy content, and mockup README says Clear, Glossy and Frosted "look much alike" over the field. Smoked in light mode
makes the app look like a mixed theme (D6). That leaves, defensibly, Glossy (new default) and Frosted (today's switch,
renamed). That is D3 option C. Each extra look multiplies the test matrix (look x pack x light/dark x aurora/shader spots)
and every future glass change. Recommend: plan for two, make the third and fourth opt-in later by data from gate 0, not
the other way round (the doc currently defaults to four and cuts on evidence).

### 3. Major. The moving-field picker is the largest new surface and is not needed to choose between two looks
Scope: section 4, section 5, phase 2 (`KitGlassLookPreview`, `KitLookField`, a picker-local `KitLiquidBudget`).
Three new kit parts, a second recording pipeline, a 30 fps cap, a picker budget, a pause policy and a JankStats gate, to
preview. The existing Appearance page already changes live behind the sheet (the doc says so). Reuse first: tapping a look
in a plain radio list that re-skins the real page behind it (`KitSheet` with option rows) shows the real result over the
user's real background, which is what the owner actually experiences. Contrast with the doc's own admission that the field
exists because the owner's own background "hides the differences" (D2 C) - which means the previews demonstrate a
condition the user never sees. A preview that flatters the difference is a source of the next "artificial" complaint.
Defer the field; ship the list. Add the field only if the list proves insufficient.

### 4. Major. The owner's "very artificial" history is treated as a risk bullet, not a design constraint
Scope: section 7 bullet 2; "Keeps these decisions" block.
The repo records glass removed on 10-26 as "very artificial" (`ui-kit.md` R3, `KitFrost.md`: "no refraction, rim lighting
or liquid look") and then returned with the owner's override ("go full blown glass"). The proposal goes the opposite way
from the 10-26 calm: Glossy raises specular 0.55 to 0.80 (dark), saturation 1.45 to 1.6, adds a lower-end light return and
a bottom shade "so the face reads curved". Those are exactly the effects that read as synthetic. Add an explicit acceptance
check: the owner judges Glossy on the real screens, at three body-text pages, before any picker exists, with Frosted as
the control. Also the safest default for someone who said "artificial" once is today's liquid value, not the new brightest
look. D4 recommends Glossy as default; recommend deferring the default change until after owner sign-off on renders.

### 5. Major. Merge-not-add is right, but the doc keeps both concepts alive
Scope: section 3.
Good: one row replaces one control. But `GlassEdges` stays (Frosted = EdgeHold.Frosted) while `GlassLook` is added, and the
`lensSupported` hold now depends on the look. Two enums, two storage keys, a fallback read, three conversion layers (design
enum, kit enum, stored enum - see `PreferencesChoices.kt`). The simpler end state: delete `GlassEdges` and `oc.glassEdges`,
one `GlassLook` enum, one key. State that explicitly, with a removal date, rather than an indefinite fallback.

### 6. Minor. Altitude drops and over-specified numbers
Scope: section 2 table, section 6 interface line.
A 17-parameter table of per-look values and a proposed constructor signature are implementation detail for a design the
doc says "nothing gets built from". The numbers are "starting points" that the floor will overwrite. Keep the idea column
and four or five levers (tint, blur, gloss, lens, ink) at design level; put the numbers in `GlassLooks.kt` when gate 0 runs.

### 7. Minor. Reuse check: existing parts are mostly reused, one claim needs verifying
Scope: section 5 items 1-2, 4.
`LiveFrameClock`, `rememberLiveShaderBackdrop`, `KitLiquidSource`, `KitSheet`, `LiquidEdge.BUDGET` exist in the code, which
is good. The doc proposes a new "picker-local `KitLiquidBudget`"; the existing claim mechanism is `LiquidEdge.grant`
(`claims`, `BUDGET=5`). Show why a second budget type rather than a scoped call to the same `grant` is needed.

### 8. Nit. Dependency on in-progress work makes the draft hard to evaluate
Scope: "Depends on", section 7 last bullets.
The `GlassLook` spec "is still being written" and the glass files "are being edited by the tint and gloss agent now". A
design that cannot say what exists today versus what it assumes will exist is a plan against a moving base. Name the
commit it assumes.

## What is already simple and right
- Reuses the floor and every hold; no new readability rule (section 2).
- No new UI level; row replaces control (section 3).
- Gate 0 before picker work (section 6) - the best part of the doc; promote it to the whole plan.
