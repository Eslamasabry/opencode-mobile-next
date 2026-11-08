# Audit fix B — classes C02, C05, C07, C08, C09 (2026-11-09)

Branch `native/audit-fix-b`, fixing by class the findings of the merge41 audit
(`native/audit-41`, `docs/native/qa/audit-2026-11-09/README.md`). `before/` holds the audit's own renders
(merge41, CI runner fonts) or its evidence sheets; `after/` holds the same capture harness
(`AuditShot`, 412 × 915 dp, density 2.625) run on this branch on the PC (PC fonts, so glyph shapes differ a little).

## C02 Content with no gutter — fixed in the kit

- **Fix:** `KitScreen` keeps the page gutter for every body. `KitScreenEdges.Gutter` is the default: the body sits
  between the 16 dp gutters and on the rail (`LocalKitOnRail`), so kit rows, groups and section labels line up with
  it instead of stepping in a second gutter, as on a sheet. `KitScreenEdges.Bleed` is the explicit opt-out for a body
  that runs edge to edge. The gutter applies once (a nested screen, or a body on a sheet's rail, adds none). Kit parts
  that padded themselves by the gutter (state views, inline states, skeleton rows, tab strips, page states, undo bar,
  FAB, jump pill, filter chips, progress rows) read `kitPageGutter()`, which is 0 inside a gutter body. A band that
  must still meet the window edges (the terminal key bar's ground) uses `kitPageBleed()`.
- **Migration:** every screen that already laid out its own gutters is marked `Bleed`, so none of them moved. These
  screens take the frame's gutter now: server terminal, local terminal, project terminal, Linux development
  services, Development services, Project health, Update this server, Saved prompts, Projects picker, AI Team living
  spec, AI Team task (findings), Provider sign-ins. Also on the gutter: Notices (C07) and the request card's "Needs your
  decision" caption.
- **Guards:** `ui/kit` `KitScreenGutterTest` (LTR and RTL edges, a button straight in the body, Bleed, the gutter
  applied once, rows on the rail); `quality` `ScreenGutterGateTest` (the number of `Bleed` opt-outs only shrinks:
  123).
- **Not mine:** the slash list in the composer is in `BasicComposerHost` (chat-running's file).

## C05 Actions drawn as plain text

- Saved prompts: each prompt is a row (tap restores) with its ⋯ menu — Restore to draft, Words only, Edit, Delete
  (destructive) — as Flutter's `prompt_stash.dart`; no loose "Edit Delete" words.
- Voice typing: Download … again and Delete … are tertiary text actions with the retry and delete glyphs (Flutter
  `voice_ui.dart` `KitActionStack`), not bold rows that read as headings.
- Server editor: Check connection is a secondary button with its glyph; Inspect certificate a glyphed text action.
- AI Team task: Stop task, Pause, Resume, Start again, Check this task, Re-check, Answer and Review the findings carry
  their glyphs. Running on this phone: the row's Stop carries its glyph.
- Not done here (other owners): Home's "Change server" (home-a1), the chat offline banner's "Try again" and the slash
  list (chat-running).
- File viewer: the document tools (Copy contents, Find in file, Show source, Wrap lines) and the file's acts (Open
  with, Save, More file actions) are glyphed text actions a full step apart on the gutter; a binary file shows Open
  with once.

## C07 Raw or technical words

- Notices render through `KitMarkdown` (headings, lists, code names), the page title is not repeated, a bundled
  licence link becomes its words and a web link keeps its address in brackets.
- Notices: the source's 80-column hard wraps join into paragraphs (`unwrapNotices`), so sentences no longer break
  mid-line. Guard: `feature/settings` `NoticesReadingTest`.
- Plain words: the voice engine note (English and Arabic). The review-scope note and the plain-HTTP warning come from
  the shared ARB (`lib/l10n`); editing only the XML is drift, so they wait for an ARB change plus gen-l10n.
- Not done here: Updates "Build 10001", Usage "Timezone: UTC" and ISO dates, "Connection checked · 2.1.1", the
  Settings hub's lowercase server line, Review's "Also changed / 1".

## C08 Text cut without an ellipsis

- Terminal (blocker): the body has its gutters; link buttons wrap onto lines a step apart instead of running together
  in one sideways row; inside the gutter the key bar fits its eight keys (about 44 dp wide, 48 dp tall, never under
  40) instead of scrolling with the last key cut, its band still edge to edge. Outside a gutter body the bar is as
  before (Flutter gate shots unchanged).
- Kit: every `BasicText` with `maxLines` in the kit ends in an ellipsis, and `KitBidi.ltrText` defaults to one when it
  has `maxLines`; `KitTabStrip` fades the edge where more tabs wait (the AI Team board's "Don").

## C09 RTL

- `KitUnits` keeps a number and its unit as one left-to-right isolate (`quantity`, `percent`, `isolate`;
  `kitUnits()` applies it in RTL): Running on this phone now reads "215.0 MB" and "91.8%" in Arabic.
- Usage counts, compact token counts and percents use the phone's digits (`Numbers`), as Conversations and Schedules
  do. Still Western: digits typed into translated labels ("7 أيام") and the ISO date range.
- Chip labels take their own text direction, so a Latin model name in Arabic ellipsizes at its end.
- **Guard:** `ui/kit` `KitUnitsRtlTest` (pure rules plus a measured RTL line: the unit stays right of its number, and
  without the isolate it does not).

## Renders

| Class | Screen | Before | After |
|---|---|---|---|
| C02 | Server terminal | [before](before/terminal__server__connected__solid-light.png) | [after](after/terminal__server__connected__solid-light.png) |
| C02 | Development services (Linux) | [before](before/linux__services__list__solid-light.png) | [after](after/linux__services__list__solid-light.png) |
| C02 | AI Team living spec | [before](before/team__editor__spec__solid-light.png) | [after](after/team__editor__spec__solid-light.png) |
| C02, C05 | Saved prompts | [before](before/prompts__saved-prompts__populated__solid-light.png) | [after](after/prompts__saved-prompts__populated__solid-light.png) |
| C05 | Voice typing | [before](before/voice__voice-settings__model-installed__solid-light.png) | [after](after/voice__voice-settings__model-installed__solid-light.png) |
| C05 | File viewer (Markdown) | [before](before/files__file__markdown__solid-light.png) | [after](after/files__file__markdown__solid-light.png) |
| C05 | File viewer (binary) | [before](before/files__file__binary__solid-light.png) | [after](after/files__file__binary__solid-light.png) |
| C07, C02 | Notices | [before](before/settings__notices__default__solid-light.png) | [after](after/settings__notices__default__solid-light.png) |
| C08 | Terminal links and keys | [before (audit evidence, m37)](before/evidence-C04-text-cut.png) | [after](after/terminal__server__connected-links__solid-dark.png) |
| C08, C02 | Local terminal | [before](before/terminal__local__two-shells__solid-light.png) | [after](after/terminal__local__two-shells__solid-light.png) |
| C09 | Running on this phone (ar) | [before](before/phone__running__grouped-rows__solid-dark-ar.png) | [after](after/phone__running__grouped-rows__solid-dark-ar.png) |
| C09 | Usage (ar) | [before](before/usage__cost__ready-7d__solid-dark-ar.png) | [after](after/usage__cost__ready-7d__solid-dark-ar.png) |

## CI

- Fast lane run 42529225249 (https://github.com/Eslamasabry/opencode-mobile-next/actions/runs/42529225249) on
  `6ca9d9c8c`: compile ok, APK ok, summary FAIL. Two of its new failures were this branch's and are fixed
  (`PromptsScreensTest`, `KitScreenGutterTest`, commit `a55acdee5`), and the ARB drift of two copy keys is reverted
  (`9b6575594`). The rest are not this branch's: `tools/strings` Glossary and Drift on other keys (agents personas,
  Linux services, voice setup), `ui/gate` AlignRules/AlignSpecFile, `core/security` VerbatimCopyReview (ChatPresenter,
  TeamIntroScreen), and the known-noise list.
- Locally (PC, JDK 21): every changed module and its tests compile; `KitScreenGutterTest`, `KitUnitsRtlTest`,
  `KitBidiParityTest`, `ScreenGutterGateTest`, `NoticesReadingTest`, `PromptsScreensTest`, `FilesScreensTest`,
  `VoiceSettingsScreenTest`, `RunningDetailTest` pass; detekt shows no new finding on changed lines.
