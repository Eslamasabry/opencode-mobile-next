# Design critic review: regular-people-2026-11-09.md

Reviewer: Design Critic (hostile, constructive). Mode: REVIEW. Read-only. Cross-checked against `native/` and the owner rules (kit only, no new UI levels, actions name their target, no raw errors, privacy: anonymous, opt-in, no PII).

## Findings, by severity

### 1. Blocker. Packs are "bundled" and "downloaded" at the same time; there is no host for them (§3.4, D4, non-goals line 9)
- §3.4 step 2: "downloads the pack archive from our catalog and checks its sha256". D4 A: "a catalog bundled in the app". Non-goals: "a hosted service of our own".
- These cannot all be true. If the archive is in the APK it adds the pack size to every install (and the app is already large); if it is downloaded, something must host it (GitHub release asset, VPS), with availability, cost, and a pinned signing key, which the draft defers to D4 B.
- Also, dependencies come from npm at install time ("installs the pinned dependencies from the pack's own lock file"), so a "bundled" pack still depends on npm and network, and on install scripts of transitive packages. Decide: vendor `node_modules` in the archive (large but offline, hash-checked as a whole) or accept the registry.

### 2. Blocker. Consent and tier are wrong for the survey events (§2.4, D3)
- Code: `native/engine/analytics/.../Consent.kt` has two tiers. BASIC is always on, anonymous and disclosed; DETAILED needs `DetailedConsent.Granted`. The draft says "only when Send reports to developer is on" but never says which tier each new event belongs to, nor that `survey_answer` (a person's stated interests) is not something to put in BASIC.
- The fixed list lives in `ProductEvents.kt` with allowed value sets (see how `screen` is whitelisted at line ~295, ~359). `survey_answer { q, choice }` needs enumerated `q` and `choice` sets, a `v` field not in any existing shape, and a PRIVACY.md change, all in the same commit per that file's rule. The draft names this but not the tier.
- Under option A the people who answer are exactly those who opted into DETAILED. The survey then measures the opt-in population only, and §2.5 step 5's decision rule inherits that bias. Say so, and do not present the results as "our users".
- Q4 ("do you already pay for ChatGPT / Claude / Gemini") plus country and language is a profiling combination; with 32 users it is close to identifying. Apply the same ruling as for Q5: keep on the phone, or drop it.

### 3. Major. "My files" breaks the existing starter logic (§5.1, §6.3, `Starters.kt`)
- `ChatStartFacts.hasProject` is true for any folder name that is not `/root/projects` (`isProjectsRoot`). A new default start folder named "My files" is therefore a project. With zero entries `Starters.suggestions` returns BuildWebPage / PythonScript / NodeProject / Readme: coding starters in the exact case the draft wants everyday ones. With files in it, it returns ExplainProject / FindBug / AddTests.
- The draft says "coding starters stay as they are inside a code project", but gives no rule that tells a "My files" folder from a code project. `StarterKind` is a closed enum; the everyday kinds (Slides, Cv, Summarise, ...) and the rule that picks them are a change to `engine/composer`, not just copy.

### 4. Major. Preview and exported file can disagree, and the test that decides it is after the commitment (§4.3, §6.1)
- Option A is recommended and "the A risk to test first" is described, yet D2 asks the owner to approve A before the test. Phase 0.5 should gate D2, not run in parallel with it.
- Arabic: Android shapes Arabic in the viewer; PowerPoint/WPS render the `.pptx` with their own fonts and bidi. `pptxgenjs` "lists RTL" is a README claim. Persona P2 (Arabic) is one of four. Treat "Arabic deck looks right in WPS on a Samsung" as a go/no-go row, not a risk bullet.
- PDF from `PdfDocument` and the on-screen composable are one drawing only if the same fonts are embedded. `PdfDocument` embeds system fonts it uses; the pack's font pair (§4.1 step 3) must be bundled in the app and in Node's build, or the three outputs differ.

### 5. Major. Trust and prompt-injection surface of packs that auto-run shell (§3.5, §4.1)
- A shared PDF or pasted notes feed a model that has `edit` and `shell` pre-allowed inside "its own output folder" and for "its own build command". Pre-allow by folder is only as strong as path handling: the deck title becomes the file name ("Water cycle.pptx"), titles are model- or user-written, and nothing says names are sanitised or that `..` and absolute paths are refused.
- `build.js deck.json` runs JSON the model wrote against a library that fetches images (pptxgenjs can load URLs). Say how image slots are restricted to attachments and no network.
- The permission card change ("plain sentence first, exact command under it") alters a rule that `vision-decisions-2026-10-20` #1 states and that `engine/cards` tests as "never auto-allow" (`ShowCardNeverAutoAllowTest.kt`). The draft treats pre-allow as free; it must show it does not weaken that test.

### 6. Major. The pre-allow only works for one agent, and the user may not have picked it (§3.2 item 2, §3.5, §4.4)
- The agent file `~/.config/opencode/agents/<pack>.md` pre-allows commands only when that agent is the active one. A starter tapped in a normal conversation runs whichever agent and model the person has selected. The draft says both "the pack's agent `slides` uses whatever model the person picked" and "typing works too, the agent sees the pack's description". These are two different paths with different permissions. Specify which one the starter uses (switch to the pack's agent for that conversation? say so on screen, since "actions name their target").
- Claude Code: the draft lists `allowed-tools` but Claude Code is reached "through Paseo" (a relay); whether the skill is read in that session is not shown.

### 7. Major. Owner UI rules not met in places (§2.1, §4.1, §5.1)
- No new UI levels: the survey occupies "the slot the in-app update notice uses" and the setup screen "existing card area". The setup screen in `native/` is not cited. Confirm the slots exist natively; if not, they are new rows.
- Kit only (native AGENTS rule 2, 14): `KitSlide`, a questions bottom sheet with a duration segmented choice, a theme picker with thumbnails, a deck card, an outline list card and a viewer are at least five kit parts. Rule 14 says the kit has one owner who "proposes, doesn't edit". The draft does not list the kit parts or say who adds them.
- Actions name their target: "Share as PowerPoint" is fine; "Change something" does not name what changes; "Undo" goes back "one version" of what? Name the deck and slide.
- No pill badges: the "Free · Fast · Smart" model tags (§5.2) read as pills. The tag has to be a text line.
- No boxed icons: the draft lists no icons. Good, but the starters need one each; say they are plain glyphs.

### 8. Major. Share target does not exist for PDFs (§4.1 step 1)
- `androidApp/src/main/AndroidManifest.xml` declares the share intent filter for `text/plain` only (line ~84, "text shared from any app lands in a new conversation's draft"). "Share a PDF or notes into the app with Make slides from this. This uses the share target that exists today" is false for PDFs and photos; it needs a new filter (`application/pdf`, `image/*`, `ACTION_SEND_MULTIPLE`), file access via content URIs, and copying into Ubuntu (the agent cannot read a content URI).

### 9. Major. Funnel claims and numbers that disagree (§1.1)
- "35 first opens", "32 users", "22 used any tracked feature" and funnel steps 13 vs 14 (reply vs successful send). `first_reply` 13 and `send_result=ok` 14 means the funnel is not strictly nested; explain. "About one in three get a reply" is 13/35 = 37%.
- "`screen_view.screen` is empty ... the native build should fix this": native already sends `ScreenView` with a whitelisted `ProductScreen` (`engine/analytics/.../ProductEvents.kt`, wired in `androidApp/.../graph/TelemetryHealthWiring.kt`). The gap is probably Flutter-only. Check before listing it as Phase 0 work.

### 10. Major. Localisation claim is out of date (§5.2, D6)
- "Today the app ships en and ar, so es and pt are a new localisation job." `native/ui/words/src/commonMain/composeResources/` already has `values-ar`, `values-es`, `values-pt`, `values-ja`, `values-ru`, `values-zh`. The question is coverage per file, not whether the languages exist. D6 is mis-framed; the real cost is the new strings only.

### 11. Nit. Multi-connection scope (§2.3, §5.1)
- "What you use it for" is app-wide, but it changes where new chats start and what starters show; with several live connections (settings-scope model: owned things live on that thing's page) the start folder is per host. Say which of its effects are per-connection.

### 12. Nit. Failure modes with no plain-words path (§3.4, §4.4)
- Self-test fails, no network at install, disk full, Node component removed later (`dependsOn` exists in `RemovableComponents`, use it), model stops mid-outline, free-model daily limit mid-deck. Each should have one sentence and one way forward; only the first and last are mentioned.
