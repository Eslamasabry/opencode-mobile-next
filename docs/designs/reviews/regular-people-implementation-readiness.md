# Implementation readiness review: regular-people-2026-11-09.md

Reviewer: Implementation Readiness (the engineer who writes the code). Mode: REVIEW. Read-only.

**Verdict:** Phase 0 (survey Q1 and events) is close to buildable once four questions are answered. Phase 1 is not: it needs at least ten decisions the draft leaves to the implementer. The draft says itself it is a v1 for review, so these are the items v2 must settle.

## Findings, by severity

### 1. Blocker. No data contracts for the three files that carry the feature (§3.2 item 5, §4.1)
- The pack manifest, `deck.json` and `themes.json` are named, with prose about contents, but no schema. The engineer cannot write the agent skill, `build.js`, the app parser or the viewer without one, and they must agree to the field.
- Needed: JSON schema for each (field names, types, required, max lengths, layout enum, how speaker notes and image slots are written, how RTL is declared per deck or per slide), a version field, what the app does with an unknown layout, and two worked examples (en, ar). Also `outline.json`.
- Freeze these first: `AGENTS.md` (root) productivity rule 4 asks to freeze names, arguments and results before parallel work.

### 2. Blocker. How the app asks the three questions and hands the answers to the agent (§4.1 step 2 to 4)
- "An app-side form in a bottom sheet, not model turns" but the next step is the agent producing the outline. How do the answers reach it: a templated first prompt, an attached file, an `@skill-id` mention, a command with `$ARGUMENTS`? The agent file or skill must then parse it. This is the core contract and is unspecified.
- "The skill tells the agent to stop and ask with the existing question tool": the draft names no tool id or event; the engineer has to find it (`engine/entry` `EntryTypes.kt` / question handling) and learn how a question with two options appears in the transcript and how an answer returns. Specify, and say what happens if a model does not call it (the free-model case §4.4 expects to fail).
- Where in `native/` does the form live (module, screen, ViewModel per native AGENTS rule 3: XUi, XActions, XViewModel, XScreen, XEntries)? Not stated.

### 3. Blocker. How the app knows a deck exists, and where it lives (§4.1 steps 5 to 9, §6.3)
- The "deck card" appears "in the conversation" and "updates in place (a new version; Undo goes back one version)". The draft does not say how the app detects it: a file watcher in Ubuntu, parsing a tool result, a marker line the skill prints, or a pack-specific entry type. The transcript module has one owner (native AGENTS rule 14), so a new entry type is a proposal to that owner.
- Version history: who stores versions (the skill keeps `deck.v1.json`? the app?), how many, how they are listed, whether Undo is an app action or a message to the agent. The deck card's identity (path? id?) is unspecified.
- Files: the viewer needs the pptx/json path inside proot (`/root/My files/Presentations/<name>/`) mapped to something Android can read; share and "Save to Downloads" need a `FileProvider` URI or a copy out of the rootfs. Which Android path is the rootfs, and is it readable by the app process on every supported Android? The draft cites `LocalPdfService.kt` without saying how it reads files today. "My files: inside Ubuntu or app storage" is an open question (§6.3) that §4.1 step 9 treats as decided.

### 4. Major. Install, check and remove need an executable spec (§3.3, §3.4)
- Which component entry? Give the `RemovableComponents.kt` fields: id, `presenceScript`, install step, `sizeScript`, `removeScript`, `checkScript`, `dependsOn = [NODE]`. The draft says "register next to essentials, python and node" but not the values.
- The install script: which Node version does `PhoneSetupScripts.kt` pin and does it meet `pptxgenjs` 4.0.1; `npm ci` or vendored `node_modules`; arm64 build of any native dep (the pptx skill uses `sharp`; the draft's build uses only `pptxgenjs`, say so); behaviour offline; resumable download; what "Try again" re-runs.
- "Add to This phone · 8 MB" and "about 10 MB / about 3 MB" are Guess. The UI string takes its number from `sizeScript` at runtime or from the manifest; say which. Never show a Guess.
- Where does the archive come from (also design review finding 1)? Without that, the downloader cannot be written.

### 5. Major. Starter selection logic (§3.1, §5.1)
- Concrete change list is missing. Needs: new `StarterKind` values; a rule in `Starters.suggestions` or a wrapper that takes the Q1/Q3 answers; what `ChatStartFacts` says for the "My files" folder (see design review finding 3); `:ui:words` strings; where the Q1 answers are read from (the `oc.survey.v1` store, which engine owns it?). Also "up to 4 of" and the order. Tests per native rule 11 need catalogue rows `B-AREA-NNN`; none are proposed.
- The phrase-chip "moment's starter" change: which file in `engine/composer/phrases/` and does `KitPhraseChips` need a new input?

### 6. Major. Survey storage, triggers and events (§2.1 to §2.4)
- `oc.survey.v1` JSON blob: which store (what the other app-wide preferences use), its place in "the preferences ledger" (name the file), and the "Clear app data on this phone" path that must clear it (confirm that path exists natively).
- Triggers: "3rd successful reply on a later day than the first" needs a counter and a first-reply day in storage; the draft's store schema (`askedAt`, `answeredAt`, `dismissed`) has neither. "First open after the update that ships the survey" needs a stored last-seen version.
- Events: add exact wire names, value sets, and tier. `survey_answer { q, choice, v }`, `starter_used`, `pack_added`, `pack_result`, `file_shared` do not exist in `ProductEvents.kt`; the file whitelists values per event (it has `ProductScreen` etc.), so each needs an enum and a PRIVACY.md line in the same commit. Tier (BASIC vs DETAILED) is not stated. "One event per chosen option" with Q1 up to 3 choices means three events: is that within the analytics rate and queue limits?
- Email flow (Q5): which Intent, what prefilled address, what text. Address of the developer is not given; do not hardcode it in code review without owner approval.
- GA side (§2.5): custom dimension registration is manual in GA4 and `ga.sh` lives outside the repo. Mark it owner-side.

### 7. Major. `KitSlide`, slide viewer and PDF export (§4.3, A)
- No API for the kit part: inputs (a `Deck` model? where does it live and who parses `deck.json`), theme tokens, RTL, text-fit rules (overflow, auto-shrink, max lines), image slots, notes. Without text-fit rules the preview and the PPTX cannot match.
- `PdfDocument` is Android-only; the kit is KMP with a desktop target. Say what the desktop target does (no export, or a stub) and the module split (`commonMain` drawing, `androidMain` export).
- Fonts: which six font pairs; how they are bundled in the app (size) and in Node (`pptxgenjs` font names refer to installed fonts); fallback for Arabic.
- Gestures (pinch, swipe, long-press to change) and landscape full screen need the standard kit gesture components; list them.

### 8. Major. The feasibility spike has no pass criteria (§4.4)
- "Run 5 real topics ... judge by eye" lacks the topics, the models, and the bar. Define: the 5 topics (list them with languages), which free model and which paid model, what counts as pass (outline stops and asks; valid `deck.json` first try in N of 5; opens in PowerPoint and WPS without repair), and what the fallback is if it fails ("shorter rules and a fixed json schema" is a direction, not a step).
- Phase 0.5 should record results under `docs/qa/<feature>-<date>/README.md` per the owner's audit-trail rule; say so.

### 9. Major. Phases do not match the repo's process (§6.1)
- Missing per phase: owning module(s), worktree/branch, which shared modules need a proposal to their owner (kit, words, transcript), acceptance (a user journey that passes on the emulator), and the focused checks (native AGENTS rule 12). Phase 0 says "Native release, if the release owner agrees". The engineer needs a yes or no before starting.
- Estimates: "+2 to 4 weeks after phase 1" is marked Guess; Phase 1 has no estimate at all but contains about eight work items (pack format, catalog, installer, form, theme picker, outline card, viewer, export, My files, starters, setting).

### 10. Nit. Words and copy not specified (§3.1, §5.2)
- Copy is listed in prose only. Give the key names or at least the full list of new strings (sheet titles, errors, tip text, the "Free models can be slow" line), and mark which are needed in the first phase. Spanish and Portuguese resource directories already exist under `ui/words`; say which files need new strings.
- Forbidden-word check (§3.1): propose a test that scans everyday surfaces for the banned words, otherwise it will drift.

### 11. Nit. Rollback and migration (§2.3, §3.4)
- Nothing on what happens if a user installs a pack, then the app updates and the pack version differs from the bundled catalog; or downgrade; or the survey store changes (`v1` is named, but there is no `v2` rule). Per "no users yet" the owner may accept none; write that down.

## What is ready
- Reuse points are identified and exist: `Starters.kt`, `KitPhraseChips`, `RemovableComponents.kt`, `PhoneSetupScripts.kt`, `LocalPdfService.kt`, `engine/analytics` with `SinkId`.
- The decisions D1 to D7 are framed with recommendations; once D2, D3 and D4 are settled, findings 1 to 4 can be written down in v2.
