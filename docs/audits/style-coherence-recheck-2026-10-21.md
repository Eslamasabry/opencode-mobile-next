# Style coherence re-check (2026-10-21)

*Auditor only: no app code changed. This re-checks [style-coherence-2026-10-21.md](style-coherence-2026-10-21.md) after the cleanup merged on `int/ui-batch-2026-10-14` at `58d89faa9`: slices S0–S9, the Brutal, Paper and Soft polish, and Brutal and Paper in the Style picker. Branch `audit/style-recheck`. Renders: `/home/eslam/Storage2/launch-footage/audit-styles-2/<screen>/<style>-<mode>.png`, one full phone screen (412×915 dp at 2×) per image. The earlier renders are in `audit-styles/`, and a fresh Solid baseline from the pre-cleanup commit `1adad8012` is in `audit-styles-base/`.*

## 1. Verdict

**The style now reaches the kit.** In the earlier audit, the five Styles reached 30 % of the surface-painting parts. They now reach about 90 %. Of the 22 per-screen findings, 18 are fixed, 1 is improved, 1 is still open, 1 is fixed with a new regression, and 1 was by design. Solid is pixel-identical on every screen in both modes, except the one intended change: multi-line fields gained an edge at rest.

**The cleanup is not finished, though.** It brought in one blocking regression and two visible new problems:

- **N1 (blocks).** In Paper, the chosen model tile turns into a solid block of ink, and its title disappears: the "Default" words are drawn ink-on-ink. This happens in light and in dark.
- **N2 (visible).** In Glass, a chosen card or tile has lost its mint ring. The chosen one now differs from the others only by the check mark and a faint tint. This affects the agent cards, the model tiles and the Welcome cards. Brutal dark has the same weak cue on the model tiles.
- **N3 (visible).** In Paper, the rules between rows run past the end gutter to the edge of the screen. This shows on every list: Conversations, Settings, Inbox, Scheduled and the sheets.

**Verdict: nearly done.** Fix N1 before Paper ships in the picker. N2 and N3 can go in the next pass. Every remaining fix sits in a small set of files (§6).

## 2. Before and after

### Part census

This is the §2 table of the earlier audit, worked through file by file against `58d89faa9`.

| How the part takes the style | Before (82 parts) | After (82 parts) | After, with 2 newly counted parts (84) |
|---|---|---|---|
| a. Through the resolver (`KitSurfaceLook.decorate` / `kitSurfaceBox` / `kitSurfaceMaterial` / `KitSurface.*`) | 25 (30 %) | **74 (90 %)** | 74 (88 %) |
| b. Partly | 22 (27 %) | **4 (5 %)** | 4 |
| c. Ignores it | 35 (43 %) | **4 (5 %)** | 6 |
| d. Correctly exempt | ~20 | ~20 (now written in G49's exempt list) | |

**Moved from b to a (20 parts):** KitField, KitAskField, KitStartBox, KitChoiceCards, the composer suggestion chips and panel, the agent-strip chip, KitTabSwitcher, the queued bubble (which now shares `kitPromptBubbleDecoration`), KitTerm, KitTip, the viewer sheet, `showKitFramedSheet`, and the code surfaces through the new `kitCodeSurface` (code block, details fold, log panel, the command box in the request card and question, terminal output). Also the diff file list, the prominent search field, and the overflow menu, which is now `showKitMenu`.

**Moved from c to a (29 parts):** agent choice, choice tiles, Undo, the floating action, the consequences inset (now `KitSurface.inset`), the icon tiles on sheets, dialogs, empty states and the step light, the composer tray and mic/send circles, the status tag, the row's chosen fill, count badge and swipe panel, KitDivider (the `divider` role), KitTappable and the icon button (the `stateLayer` role), the switch (the `toggle` role), the sidebar, the terminal key bar and caps, the terminal key pill, the wheel band, board lanes, team cards, the diff header bands and hunk bar, the Style picker samples (`KitSurfaceLook.resolve`) and the mock chat.

**Still b (4 parts):**
- the tooltip (its corners follow Corners, its fill does not);
- the step slider (`kit_step_slider.dart:170–227`);
- the header fill of the mini table (`kit_mini_table.dart:170`);
- the header fill of the markdown table (`chat/kit_markdown.dart:747`).

The rules of the last two already use the `divider` role.

**Still c (4 + 2 minor parts):**
- the skeleton transcript (`kit_skeleton_transcript.dart:37,59`);
- image frames and the placeholder (`kit_image.dart:306,1366`);
- carousel dots (`kit_carousel.dart:125–157`);
- window-row bars (`kit_window_row.dart:90,100`);
- newly counted: the viewer's image placeholder (`kit_viewer_bodies.dart:251`) and the file body ground (`kit_viewer_build.dart:531`, `detailsSurface`, which is opaque by contract F7 but has no edge from the style).

### Root causes

| Root cause | Before | After |
|---|---|---|
| R4: `style == solid` early-outs, so Fine-tune under Solid misses the part | 13 sites | **7 sites**: `kit_menu.dart:509`, `kit_floating_panel.dart:40`, `kit_fact_row.dart:190`, `kit_dialog.dart:227,287`, `kit_jump_pill.dart:412`, `kit_sheet_modal.dart:20` |
| R5: style names inside parts | 4 sites | **0** |

### Gate G49

The S0 baseline was 226 raw-decoration hits in 94 files. It is now 190 hits in 88 files.

The count drops slowly by design: a part passes its own Solid decoration as `solid:` to `decorate`. About 54 of the 190 hits are such `solid:` arguments. The other ~136 are focus rings, indicator dots, previews and the residual parts above, not unstyled surfaces.

### Share of parts that look right, per style

This is the static count, before the visual regressions in §4.

| Style | Before | After |
|---|---|---|
| Solid | 100 % | 100 % (pixel-checked, §3.2) |
| Glass | ~30 % | ~90 % |
| Soft | ~30 % | ~90 % |
| Brutal | ~30 % (less on Home, V1) | ~88 % |
| Paper | ~57 % | ~92 % (the corner-only parts pass in print) |

## 3. Visual re-check

### 3.1 Matrix

| What | Set | Images |
|---|---|---|
| Every screen in all five styles × light and dark | 39 screens, 34 from before + 5 slice extras (folder browser, term bubble, tip bubble, composer suggestions, technical transcript) | 390 |
| Saver (Glass chosen) | 4 screens | 4 |
| High contrast (Glass, Brutal) | 4 screens each | 8 |
| Fine-tune Sharp + Flat + Compact + Edges off (Solid dark, Soft light) | 4 screens each | 8 |
| Arabic RTL (Solid dark, Brutal light) | 5 screens each | 10 |
| **Total** | | **420** |

I looked at every image, in before/after contact sheets, and zoomed into each suspect region. The earlier audit rendered the second mode only for its 4-screen matrix, so most light/dark-flipped renders here have no earlier image to compare with; they were checked on their own.

**Passes:**
- Saver forces Solid on true black with no live background.
- High contrast turns Glass into Solid.
- RTL mirrors Brutal's hard shadow and the styled switch: off sits at the start, on the right.
- No red anywhere; destructive actions are neutral black.
- Amber appears only for "needs you". Brutal's needs-you tag is now an amber pill, and Soft's amber-tinted card face (V21) is gone.
- One solid-mint main action per screen.
- No blur outside Glass: `blursBehind` is set only by the Glass spec.

### 3.2 Solid: unchanged

Solid was pixel-diffed against `1adad8012`, rendered with the same harness. All 39 screens in dark and light are byte-identical except:

- **Report a problem:** the multi-line field gained its edge at rest. This is the intended change (WCAG 1.4.11).
- **AI Team task and question:** the clock in the fixture, at 0.1 % of the image. Not a style change.

Solid's 4-screen matrix and RTL are identical to the earlier audit's renders.

**Solid + Fine-tune does move now, as R4 intended.** Two changes need an owner's eye:

- the needs-you card gains a 1 dp amber ring at Edges › Off (N7);
- the platform switch becomes the kit's styled switch, with a bigger thumb.

### 3.3 Earlier findings

Status key: **fixed** / **improved** / **open**.

| # | Screen | Style | Status | Now |
|---|---|---|---|---|
| V1 | Conversations | B | **fixed** | Row groups, chips, the server pill and the + cast hard shadows; the page ground no longer strips them |
| V2 | New conversation | G S B P | **fixed**, see N2 | The agent cards take every style; in Glass the chosen card lost its ring |
| V3 | Chat question, AI Team question | G S B P | **fixed** | The answers sit on the card's face; there is no second pane |
| V4 | Git, reader, folder browser, timeline and commands sheets | G S B P | **fixed** | Glass has a rim and a clear route, Brutal an ink sheet, Paper square corners, Soft a ground-matched sheet |
| V5 | Model picker | G S B P | **fixed in G, S and B; regressed in P (N1)** | |
| V6 | Undo bar | G S B P | **fixed** | Translucent in Glass, ink and shadow in Brutal, a box with an underlined Undo in Paper |
| V7 | Fields (Rename, Report a problem) | G S B P | **fixed** | A well in Soft, ink in Brutal, a box in Paper, a rim in Glass; the edge reaches 3:1 at rest |
| V8 | Inbox and permission command box | P S B | **fixed** | Visible in every style |
| V9 | Confirm sheet | G S B P | **fixed** | The icon tile and the consequences inset take the style |
| V10 | Conversations + | S B P | **fixed** | Ink and a hard shadow in Brutal; square in Paper |
| V11 | Prompt tools sheet, abutting groups | G B S | **open** (improved in P) | Glass: doubled rims 2 dp apart. Brutal: doubled ink, and the upper group's shadow falls into the lower one. Paper: groups are unframed, so there is no doubling |
| V12 | Switches | S B P | **fixed** | Styled switch: raised in Soft, ink in Brutal, square in Paper |
| V13 | Review changes | G S B P | **improved** | File header bands take the style. The "… 40 lines above" gap bar is still a white strip in S, B and P, and it breaks the code frame (N5) |
| V14 | Terminal key bar | G S B P | **fixed** | Ink caps with shadows in Brutal, square caps in Paper |
| V15 | Preview "Ask for a change…" | G S B P | **fixed** | |
| V16 | Settings search, Paper | P | **fixed** | Square |
| V17 | Start box halo, Paper | P | **fixed** | No glow |
| V18 | Composer mic/send and tray circles | S B P | **fixed** | |
| V19 | Appearance swatches and mock bubble | B P | **fixed** | Paper swatches are square and the mock bubble is ruled; Brutal keeps its soft corners by spec |
| V20 | Tertiary links | B P | **fixed** | Underlined ("Just show me", "Reject", "Cancel", "Undo", "Show all") |
| V21 | Needs-you card face, Soft | S | **fixed** | No amber fill |
| V22 | Live background with Brutal/Paper set directly | B P | by design | The picker path (`offWhenChosen`) was not rendered |

## 4. New problems, ranked

| # | Severity | Screen | Style | What the user sees | Where |
|---|---|---|---|---|---|
| N1 | **blocks** | Model picker (every tile list on `KitChoiceTiles`) | P, light and dark | The chosen tile is a solid ink block. The "Default" title is invisible (ink on ink) and the model name reads at about 1.5:1 | `kit_choice_tiles.dart:153–232`: the words never ask `KitSurfaceLook.wordsOn`, while Paper inverts any chosen control (`kit_style_paper.dart:45`, `_inverted`) |
| N2 | visible | New conversation, Model picker, Welcome | G (and B dark on model tiles) | The chosen card has no ring. Only the check mark and a faint lift set it apart (it was a 2 dp mint ring) | Glass spec `selected` answer (`style/kit_style_glass.dart`); Brutal dark `selected` on `control` (`style/kit_style_brutal.dart`) |
| N3 | visible | Conversations, Settings, Inbox, Scheduled, Git sheet, menus | P | Row rules run to the screen edge, past the 16 dp end gutter; the section rules stop at the gutter | `kit_row.dart:1211–1217` and `kit_sliver_row_group.dart:110`: the bare-group `reach` widens both sides, and the row dividers are not inset back at the end |
| N4 | visible | Composer suggestions (`/`) | G S B P | The suggestion panel is a second surface inside the composer box. In Brutal every row is its own ink box, so 2 dp lines double between rows | `chat/kit_composer_chips.dart:1065` (panel) and `:1319–1335` (row highlight on `stateLayer`) |
| N5 | visible | Review changes | B P (S white strip) | The code frame starts below the "… lines above" bar and runs along the screen edges; the bar itself is a raw white strip | `diff/kit_diff_rows.dart:163,249,382`; `diff/kit_diff_chrome.dart:899–935` |
| N6 | minor | Technical transcript (mini table) | B (G, P) | The header row sits outside the table's ink frame, as a separate band | `kit_mini_table.dart:170` (raw `surface2` header); `chat/kit_markdown.dart:747` is the same pattern |
| N7 | minor | Chat question with Solid + Fine-tune | Solid + any knob | The needs-you card gains a 1 dp amber ring at Edges › Off, which it does not have at Solid's defaults; the switch thumb grows | `kit_request_shape.dart` (S9 `needsYou.ring` default), `kit_row_parts.dart:571` |
| N8 | minor | Sheets with Glass chosen under Saver or high contrast | G → Solid | The page behind the sheet is dimmed less than before (a 2–3.6 % pixel change, all in the scrim area); the sheet itself is Solid | Probably the scrim follows the chosen style rather than the effective one (`kit_sheet_modal.dart:20,45`, `tokens.scrim`). Not traced further |
| N9 | minor | Preview ask field | B | The mic circle's hard shadow overlaps the field's ink edge | `kit_ask_field.dart:437` |
| N10 | watch | Every light screen | B P | The warm paper ground is stirred from `attentionFill`, the amber token. It reads as cream, not amber, but it ties the page colour to the needs-you colour | `style/kit_style_brutal.dart:157`, `style/kit_style_paper.dart:89`. Owner call on "amber only for needs you" |

These are still there from before, minor and not new:

- **Brutal "Today" vs "Earlier".** The first section label on Conversations is sans and the second is mono (`monoLabels` applies only to the label and caption roles, `kit_text.dart:445`).
- **KitMenu's divider** stays a hairline in Brutal and Paper (`kit_menu.dart:464`).

## 5. Coherence now

**Glass:**
- Sheets, Undo, tips and terms are now translucent with a rim. Floating and sheet surfaces are consistent.
- Code stays opaque, which is correct.
- The one gap is the chosen state (N2).
- See the samples in §7 for how far the blur reads with the live background on.

**Soft:**
- One language throughout: raised groups, wells for fields, code and commands, a raised styled switch, pressed-in selection.
- What is left: the abutting groups (V11) and the nested suggestion panel (N4).

**Brutal:**
- Ink, hard shadows and mirrored RTL hold on every screen, Conversations included.
- Weak points: abutting groups (V11), boxes inside boxes in the composer suggestions (N4), and the mini-table header (N6).

**Paper:**
- The best result. Lists are unframed and parted by rules, fields and controls are ink boxes, links are underlined, and the FAB, search and swatches are square.
- Must fix: the inverted chosen tile (N1) and the rule overrun (N3).

## 6. Remaining fixes, grouped by owner file

No two groups share a file, so they can run in parallel.

| # | Files (sole owner) | Fix | Closes | Size |
|---|---|---|---|---|
| F1 | `kit_choice_tiles.dart` | Words via `KitSurfaceLook.wordsOn(role: control, selected: …)`. Audit the other parts that pass `selected:` with no `wordsOn` (`kit_agent_choice.dart`, `kit_choice_cards.dart`, `kit_wheel.dart`, `terminal_key_bar.dart`); they are safe today only because Paper does not invert their role | N1 | S |
| F2 | `style/kit_style_glass.dart`, `style/kit_style_brutal.dart` | A visible chosen cue: a ring or a 3:1 tint step on `selected` for content and control | N2 | S |
| F3 | `kit_row.dart`, `kit_sliver_row_group.dart`, `kit_divider.dart` | Inset the row dividers at the end by `reach`; add a gap (or a merged face) between abutting groups at `gapBefore: 0` | N3, V11 | M |
| F4 | `chat/kit_composer_chips.dart` | Panel on the composer's face (the nesting rule); the row highlight only while active | N4 | S |
| F5 | `diff/kit_diff_rows.dart`, `diff/kit_diff_chrome.dart` | Gap bar on `code`/`content`; one code frame around bar and lines | N5, V13 | M |
| F6 | `kit_mini_table.dart`, `chat/kit_markdown.dart` | Header fill through `decorate` inside the frame | N6, 2 b parts | S |
| F7 | `kit_menu.dart`, `kit_floating_panel.dart`, `kit_fact_row.dart`, `kit_dialog.dart`, `kit_jump_pill.dart`, `kit_sheet_modal.dart` | `style == solid` → `KitSurfaceLook.isPlain`; menu divider on the `divider` role; scrim from the effective style | R4 (7 sites), N8 | S |
| F8 | `kit_request_shape.dart`, `kit_row_parts.dart` | Decide whether Solid + Fine-tune may add the needs-you ring and swap the switch; if not, key both on the knob that changed | N7 | S, owner decision first |
| F9 | `kit_ask_field.dart` | No hard shadow on a control nested in a field | N9 | S |
| F10 | `kit_skeleton_transcript.dart`, `kit_image.dart`, `kit_carousel.dart`, `kit_window_row.dart`, `kit_step_slider.dart`, `kit_viewer_bodies.dart`, `kit_viewer_build.dart` | The last minor c/b parts through `decorate` (or into the G49 exempt list with a reason) | 6 c, 1 b | M |
| — | `style/kit_style_brutal.dart`, `style/kit_style_paper.dart` | Owner decision: keep the amber-derived warm paper, or use a neutral warm token | N10 | owner |

## 7. Image index

One screen per image. Before is `audit-styles/`, after is `audit-styles-2/`.

1. **Brutal Conversations, V1 fixed.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/conversations/brutal-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/conversations/brutal-light.png`. The hard shadows are back on the groups, chips, pill and +.
2. **New conversation in Brutal, V2 fixed.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/new-conversation/brutal-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/new-conversation/brutal-light.png`. The agent cards are now Brutal.
3. **Question card in Soft, V3 fixed.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/chat-question-card/soft-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/chat-question-card/soft-light.png`. There is no second raised pane inside the card.
4. **Git sheet in Brutal, V4 fixed.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/git-sheet/brutal-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/git-sheet/brutal-light.png`. An ink sheet instead of the Material slab.
5. **Report a problem in Paper, V7 fixed.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/report-problem/paper-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/report-problem/paper-light.png`. The text box has an ink box at rest.
6. **Model picker in Paper, regression N1.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/model-picker-sheet/paper-light.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/model-picker-sheet/paper-light.png`. The chosen tile is an ink block with its title gone.
7. **New conversation in Glass, regression N2.** Before: `/home/eslam/Storage2/launch-footage/audit-styles/new-conversation/glass-dark.png`. After: `/home/eslam/Storage2/launch-footage/audit-styles-2/new-conversation/glass-dark.png`. The chosen card lost its mint ring.

### Glass samples for the owner

Live background on, one full screen each, in `/home/eslam/Storage2/launch-footage/glass-samples/`:

- **Conversations:** `conversations-glass-dark.png`, `conversations-glass-light.png`.
- **Chat with a question card:** `chat-question-card-glass-dark.png`, `chat-question-card-glass-light.png`.
- **Git sheet over a chat:** `git-sheet-glass-dark.png`, `git-sheet-glass-light.png`.
  - The chat behind it shows only its header, so the blur reads against the live background alone.
  - In dark, the glow shows faintly through the sheet. In light, the sheet is a frosted grey pane under a grey scrim.
- **Model picker over a chat:** `model-picker-glass-dark.png`, `model-picker-glass-light.png`.

What the owner will see:
- Panes and sheets are translucent, with a lit rim.
- Rows inside them stay legible.
- Code stays opaque.
- The scrim behind sheets mutes most of the background. That is the main limit on how "glassy" a sheet can look.

## Method and limits

**Static census.** Each part in the earlier §2 table was re-read at `58d89faa9` for its paint path. G49 counts come from `test/kit_ratchet_baseline.json` here and at `1403d4615` (S0).

**Renders.** The harness is `tool/capture/audit/run_style_audit.sh` (unchanged method). The `all` set now also runs the slice extras `style_s1_extras_test.dart`, `style_s4_fields_test.dart` and `style_s6_transcript_test.dart`, and `sort_renders.py` files them. The Git-over-chat Glass sample comes from the new `glass_samples_test.dart`: the Git capture in `vision_diff_git_test.dart` opens the sheet over an empty page, so its blur shows nothing.

**Solid baseline.** The same harness at `1adad8012`, in a detached worktree. The five slice extras were copied there to compare them too, and they are byte-identical.

**Not done:**
- The Style picker path for Brutal and Paper (`offWhenChosen`, V22).
- Tablet width and the desktop sidebar (static only).
- 320 dp at 2× text.

**Known harness quirk.** `audit undo` reports a pending-timer failure after its image is written, as before; the image is complete.
