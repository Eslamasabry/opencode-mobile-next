# Zoom-out review: regular-people-2026-11-09.md

Reviewer: Skeptical Architect (zoom out). Mode: REVIEW. Read-only. Judged from the draft and the cited code under `native/`.

**Verdict:** the root problem is real and the draft says so itself (about one in three installs gets a reply, §1.1, §6.2 item 4), but Phase 1 then builds a large new surface on top of it. Cut Phase 1 to a thin slice and fix the funnel first.

## Findings, by severity

### 1. Blocker. Phase 1 solves a problem the evidence does not show (§1, §6.1, D1)
- Affected scope: §1.1, §1.3, §6.1 Phase 1.
- Every number in §1.1 describes developer-ish use: `model_switch` 7, `agent_switch` 7, `server_add` 9, nobody used Git, 19 of 20 on "This phone". Nothing in GA shows a non-developer. The personas (§1.3) are hypotheses, the draft says so, and §1.2 admits our countries lean toward coding.
- So the pack system, slide viewer, theme picker, PDF/PPTX export and a settings mode are built on a guess, while the only measured fact (a 2/3 drop before first reply, `essentials` failures) is parked as "a separate, parallel fix".
- Simplicity rationale: the cheapest test of the guess is Q1 alone plus the funnel fix. Phase 1 should be gated on the Q1 result, not on D1 timing. D1 option C (wait for the survey) is the YAGNI-correct choice; the draft recommends B.

### 2. Major. The "pack" is a new package manager (§3.2 to §3.5, D4)
- Manifest format, sha256 per dependency, self-test, removable-component registration, signed catalog (D4 B), per-language question text, "works with" matrix. That is a mini app store for a feature that has one pack.
- Reuse check: `native/engine/integrations-catalog/` already has `SessionSkills.kt`, `ConversationSkills.kt`, `CatalogSkill`, `SessionSkillGateway` (skill list and activation) and an MCP catalog. `RemovableComponents.kt` already models `presenceScript`, `removeScript`, `sizeScript`, `dependsOn`, `checkScript`. `PhoneSetupScripts.kt` already installs Node. The draft cites the last two but never mentions the skills engine, and does not say whether a pack is a new thing or a catalog skill with a setup script.
- Recommendation: first version = one `RemovableComponent` ("Presentations") whose setup script copies a skill folder and runs `npm ci`. No manifest, no catalog, no signing. Add a manifest when the second pack exists.

### 3. Major. Two renderers plus a viewer for the deck (§4.3 option A)
- A new kit part `KitSlide`, a second layout engine (Compose drawing to `PdfDocument`), and `pptxgenjs` as the third. The draft itself names the mismatch with PowerPoint as "the biggest UX risk" (§6.2 item 2) and then chooses the design that creates it.
- Simpler: the agent's build step writes both `.pptx` and a PDF from the same source in one place (one renderer, one set of fonts), and the existing `LocalPdfService` / PDF viewer previews the PDF. No new kit part, no `PdfDocument` path, no mismatch between preview and file because the preview is the exported PDF. The draft's own Option B/C rows do not cover "PDF from the build step on the phone", so it was not weighed.
- If a PDF cannot be made from Node without Chromium, say so, and compare honestly. Right now the reader cannot tell.

### 4. Major. Settings mode that "hides nothing" but changes seven defaults (§5, D5)
- §5.1 says it is not a simple mode. The table then changes the new-conversation question, the starters, the start folder, the permission card layout and the Settings › AI order. That is a simple mode in effect, with its own test matrix (2 states × each screen × 5 languages).
- Simpler: derive everything from facts we already have (empty or no project folder → everyday starters; the picked Q1 answers → which starters). Starters already depend on `ChatStartFacts` in `Starters.kt`. No new global setting is needed to reorder four chips. D5 option A plus Q1-driven starters covers the finish line.

### 5. Major. The survey is bigger than its analysis can use (§2)
- Five questions, two sets, three triggers, an email-the-developer flow, a new store, a new GA report. The draft expects 30 to 100 answers (§2.5) and says to report counts, not percentages. Q2 ("how much do you code") and Q4 ("do you pay for an AI") do not drive any decision in §6.4; Q4 asks about competitors' products and is the most privacy-sensitive.
- The follow-up trigger (3rd reply on a later day) reaches almost nobody by the draft's own numbers (13 users ever got a reply).
- Recommendation: ship Q1 only, at the setup wait. Add Q3 only if Q1 shows a non-coding majority. Drop Q4. Drop Q5 and the email flow (a mailto from Help already covers it).

### 6. Major. Altitude drops (weeds) in a design doc (§3.4, §4.1, §4.2)
- Per-step detail such as `node build.js --selftest`, `outline.json`/`deck.json` file names, `/opt/oc-packs/<pack>/`, hour counts and slide counts per minute. Some belong in a spike note, not here. They make the doc look decided when §4.4 says nothing is feasibility-checked yet.
- Reorder: put the §4.4 feasibility test first and let its result fix those details.

### 7. Nit. Anthropic skill licensing story is not needed (§3.2)
- The product ships `pptxgenjs`, not Anthropic's pptx skill (§4.2 says the heavy skill is not for the phone). The licence paragraph and the 2026-11-06 commit are only relevant if the skill is forked. Either say it is forked, or delete the paragraph.

### 8. Nit. Dead-end risk: "My files" folder as the new start folder (§5.1, §6.3)
- Choosing a start folder for everyone with Everyday help changes `directory` semantics that `Starters.kt` keys off (`isProjectsRoot`, `hasProject`). See the design review (finding 3) for the concrete break. From a simplicity view: use the existing folder and a subfolder per deck instead of a new global concept.

## Reuse list the author should check before v2
- `native/engine/integrations-catalog/` (skills and MCP catalog).
- `native/engine/runtime/.../components/RemovableComponents.kt` (install, check, remove, size, dependsOn).
- `native/androidApp/.../LocalPdfService.kt` and the existing PDF viewer.
- `KitPhraseChips` and `Starters.kt` for starters (the draft does reuse these).
- The existing share sheet and attachments.
