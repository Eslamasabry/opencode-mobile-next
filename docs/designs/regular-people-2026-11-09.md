# Regular people: first replies, one question, and presentations

**Status:** v2, 2026-11-09. Revised after three goldfish reviews (`reviews/regular-people-*.md`, commit `0073db215`). It goes to the owner next. Nothing is built from it yet.

**Scope:** native app only (`native/`). Flutter is frozen.

**Finish line (phase 1):** a person on This phone taps "Make a presentation", answers three plain questions, approves an outline, sees the slides on the phone, and shares a PDF or PowerPoint file to WhatsApp. They never see the words skill, plugin, MCP or agent.

**Non-goals:**
- a hosted service of our own;
- downloading packs at run time;
- remote hosts (computer, VPS);
- Codex, Gemini and Claude Code for packs;
- image generation;
- a global simple mode;
- free-text survey answers;
- Flutter.

**Labels:** **Fact** = checked, with a source. **Guess** = judgement, to be tested. **Measure** = needs a number from the emulator or a phone before it may appear in the UI.

## Changes from v1

- **Phase 0 is now "install to first reply"**, built from code and GA facts (§2). Presentations are phase 1, behind phase 0 and behind a feasibility spike with pass criteria (§5.1).
- **Packs come from one place:** a folder bundled in the APK assets with its npm dependencies vendored. There is no catalogue, no download and no npm at install time.
  - A pack installs as one `RemovableComponent`.
  - The app sees it through the existing skills route in `engine/integrations-catalog` (`GET /skill`, `POST /session/{id}/skill`).
  - The v1 manifest/catalogue/signing system is gone (§4).
- **Survey cut to one question.**
  - It is sent as one coarse value under DETAILED consent.
  - Q2–Q5, the free text and the email flow are dropped.
  - Consent is mapped to `Consent.kt` explicitly (§3).
- **Schemas added** for `pack.json`, `deck.json` and `themes.json`, with the hand-off from form to agent and how the app finds a deck (§5.3–5.5).
- **Code facts corrected:**
  - the share target is `text/plain` only;
  - "My files" would have counted as a project, so it is dropped;
  - `values-es` and `values-pt` exist but are partial;
  - native `screen_view` already sends a screen name, and the empty values in GA come from Flutter.
- **"What you use it for" reduced** to one stored answer that picks starters. Nothing else changes (D5).
- **Anthropic licence paragraph cut** to one line, because we don't ship that skill (§4.4).
- **v1 personas and the research spike kept short** (§1). The full v1 is in git history (`4d7a182db`).

---

## 1. Who the users are (short)

### 1.1 GA4 facts

Source: property 563181914, 2026-11-01 → 09, Flutter 2.1.5, people with usage consent on.

| Step | Users | Event |
|---|---|---|
| First open | 35 | `first_open` |
| Setup started | 24 | `setup_step_reached` |
| Setup done | 18 | `setup_done` |
| Sent anything | 17 | `send_result` |
| Got a first reply | 13 | `first_reply` |

- `send_result=ok` is 14 users, one more than `first_reply`. Guess: one person's first reply came before they consented, so the funnel isn't strictly nested.
- 13 of 35 is 37%.
- **Where it runs:** 19 people on This phone, 1 on a computer.
- **Features** (users): model_switch 7, agent_switch 7, server_add 9, voice 2, file_attach 2, review_open 2. Git was never used.

**Countries and languages.** Brazil 5, Indonesia 4, India 3 and UAE 3 lead the list. The languages are English 15, Spanish 7, Portuguese 5 and Arabic 2.

**What the data shows and doesn't.** Nothing in GA shows a non-developer: every measured behaviour is developer-ish. The non-coding personas below are **hypotheses**. The one question in §3 tests them.

### 1.2 Research spike (2026-11-09, about 20 minutes)

- About 80% of ChatGPT conversations are practical guidance, seeking information or writing ([NBER w34255](https://www.nber.org/papers/w34255)).
- 26% of US adults use ChatGPT to learn ([Pew, 2025](https://www.pewresearch.org/short-reads/2025/06/25/34-of-us-adults-have-used-chatgpt-about-double-the-share-in-2023/)).
- In lower-adoption countries like most of ours, use leans toward coding and toward handing over whole tasks ([Anthropic Economic Index, Sept 2025](https://www.anthropic.com/research/anthropic-economic-index-september-2025-report)).

### 1.3 Personas (hypotheses)

| Persona | Wants | Their words | First thing that confuses them today |
|---|---|---|---|
| Ana, student, Brazil (pt), Redmi | Seminar slides, PDF summaries | "fazer slides sobre…", "pra terça" | "What would you like to build?" and the starters "List this folder" / "Find a bug" |
| Karim, shop owner, Algeria (ar/fr), Samsung A | Posts, price list, invoices | "اكتب لي منشور", "فاتورة" | English coding starters; permission cards that lead with a shell command |
| Diego, job seeker, Peru (es), Moto | CV for a job ad, as a PDF | "mi currículum", "en PDF" | No way to get a file out to an email |
| Rizky, tinkerer, Indonesia (en/id), POCO | Learn to code, a small site | "make a website", "free model" | Setup failing at `essentials`; free-model limits |

---

## 2. Phase 0: from install to a first reply

The only measured problem is that **about two in three installs never get a reply.** Phase 0 fixes what the code and the data can name, and measures the rest. It changes no screens beyond copy.

| # | Leak (Fact) | What the code does today (Fact) | Fix |
|---|---|---|---|
| F1 | 11 of 35 open the app but never start setup | Flutter's `screen_view` sent an empty `screen` (1,477 events). **Native already sends `ProductScreen`** (`TelemetryHealthWiring.kt:140`, `ProductEvents.kt:158`). | No code. Once native ships, read `screen_view` by screen for people with no `setup_step_reached`. |
| F2 | Setup fails at `essentials`: 11 failures, 2 users, all `kind=other` | `ProductFailureKind.of` (`ProductEvents.kt:121`) knows only crash, timeout, network, space and other. The apt prelude (`components/SetupPrelude.kt` `oc_apt_install`/`oc_apt_repair`) already fails with four distinct messages: lists not updated, repair unfinished, packages not installed, dpkg unfinished. They all reach GA as `other`. | 1. Each `oc_apt_failure` call also prints a fixed marker (`::oc fail apt_lists` / `apt_repair` / `apt_install` / `dpkg_unfinished`). Add those four words to `ProductFailureKind`. 2. On a second `apt_lists` failure, retry once with an HTTPS mirror. Guess: plain-HTTP `archive.ubuntu.com` / `ports.ubuntu.com` is blocked or tampered with on some networks. Only add a mirror after the new kinds confirm `apt_lists`. |
| F3 | 18 finish setup, 13 get a reply; `conn_attempt fail_kind=refused` for 5 users | Refused means nothing was listening on the local server's port. | Check the native path from "setup done" to the first connect (`NativePhoneSetup.kt`, Linux lifecycle). After setup, wait for health with a plain "Starting OpenCode on This phone…" line, restart the server once by itself, then show one plain sentence with **Start again**. |
| F4 | Instrumentation | `SetupDone` sends `minutes`, but GA has only a `minutes_range` dimension, so setup times read "(not set)". | Owner-side, in the GA admin: register `minutes` and `kind` dimensions. Dimensions don't backfill. |
| F5 | The first message is hard to write for non-coders | `Starters.suggestions` gives ListFolder/FindBug outside a project (`Starters.kt`) | Phase 0 adds the Q1 answer (§3). §4.5 changes the starters. |

**Acceptance:**
- A fresh install on the emulator (OC_API37 and a 4 GB profile) reaches a first reply on This phone with the default free model, with no manual step.
- A forced `apt_lists` failure (no network during essentials) shows the new kind in the `JsonDebugSink` output, plus one plain sentence with **Try again**.
- After two weeks of native data, re-read the funnel.

**Owner:** the setup/runtime module owner. Phase 0 touches `engine/runtime` scripts, `engine/analytics` (`ProductFailureKind`) and copy in `ui/words`. No kit change.

---

## 3. One question (survey)

### 3.1 What and where

**The question.** "What do you want help with?" The person can pick several:

| Id | Label | Kind |
|---|---|---|
| `study` | School or study | everyday |
| `work_docs` | Work files: slides, letters, sheets | everyday |
| `writing` | Writing and posts | everyday |
| `job` | Job search | everyday |
| `coding` | Coding and building apps | coding |
| `exploring` | Just looking | — |

**Where it shows.** On the setup-wait screen, under the progress. People wait there for minutes. It shows once, it has **Not now**, and it never blocks setup.

**Existing users.** On their first open of a build that has it, the question shows once in the conversations home, in the slot the update notice uses. The app-updates-two-layers notice must be confirmed native before relying on that slot. If the slot doesn't exist, it is **Settings › Chat › What you use it for** only, and we add no new row level.

**What the answer does on the phone.** It picks the person's starters (§4.5), and they can change it in Settings › Chat › **What you use it for**.

**Storage.**
- `oc.useFor` (string set of the ids above) and `oc.useForAsked` (bool).
- App-wide, in the same `KeyValueStore` as the other app-wide answers (like `TelemetryKeys`).
- Cleared by the app's clear-data path.
- Not per profile.

**Mockup brief:** D6.

### 3.2 What leaves the phone, and under which consent

- **Only one coarse word.** The local picks are reduced to one of `everyday` (only everyday picks), `coding` (only `coding`), `both`, or `looking` (only `exploring`, or nothing picked).
- It is sent as one event, `use_for {kind}`, once, and again only if the person changes the answer.
- With ~32 users, the six-way multi-pick plus the country and device model that GA attaches to every event could point at one person. Four values in one event can't.
- Q2–Q5 from v1 are dropped: coding level, paid AI, "which first", free text and email.

**Tier.**
- `Consent.kt` has BASIC (always on, anonymous) and DETAILED (needs `DetailedConsent.Granted`). `use_for` is a statement about the person, so it is **DETAILED, never BASIC**.
- Today the GA path (`TelemetryService` → `FirebaseTelemetryVendor`) is gated by `TelemetryConsent.usage`, the usage half of "Send reports to developer". `use_for` is sent only while `usage` is on. It is registered as DETAILED wherever "What we collect" lists events.
- It is added to `ProductEvent`, with its value set in `ProductEvents.kt`'s allowed map, plus a PRIVACY.md line in the same commit.

**What the results mean.** They come only from people who turned usage on, so they describe that group, not all users. Read them as counts (`ga.sh` plus a `use_for` report; registering the GA dimension is owner-side).

**Decision rule.** Phase 1 goes ahead if `everyday` + `both` is at least a third of the answers after two weeks, or if the owner decides on product grounds (D1).

---

## 4. Packs: what they are and where they come from

### 4.1 A pack is a bundled skill, installed as one component

**Source (decided): the APK assets.**
- Each pack is one archive in `androidApp/src/main/assets/packs/<id>.tar.gz`. It holds the skill folder, an OpenCode agent file, `build.js`, fonts, and **vendored `node_modules`**.
- Its sha256 is checked as a whole.
- No network and no npm at install time.
- It updates when the app updates.

**Size (Fact, measured 2026-11-09):**

| Piece | On disk | Gzipped |
|---|---|---|
| `pptxgenjs` 4.0.1 with its dependencies | 7.8 MB | 1.75 MB |
| Noto Sans + Noto Sans Arabic regular | 0.76 MB | — |

The PDF library (§5.6) is the open size question: `pdfkit` 0.20.2 untrimmed is 26 MB on disk and 6.0 MB gzipped.

**Install** is a `RemovableComponent` like `essentials`, `python` and `node` (`components/RemovableComponents.kt`):

| Field | Value |
|---|---|
| `id` | `presentations` |
| `dependsOn` | `[node]` (Node v24.22.2 pinned, `AgentHostJob.kt:180`) |
| install | Copy the archive from assets into `/var/cache/oc-setup/`, check its sha256, untar to `/opt/oc-packs/presentations/`, link the skill to `/root/.config/opencode/skills/make-presentation/` and the agent to `/root/.config/opencode/agents/presentations.md` |
| `checkScript` | `node /opt/oc-packs/presentations/build.js --selftest`: makes a one-slide deck in a temp folder and prints the version |
| `presenceScript` | `[ -f /opt/oc-packs/presentations/pack.json ]` |
| `sizeScript` | `du -sk` of the two folders and two links |
| `removeScript` | Remove them. Decks the person made stay. |

**What the UI shows.**
- The size comes from `pack.json` (`installedKb`), which is measured at build time. No guessed number ever reaches the UI.
- Removing Node later removes the pack first; `dependsOn` already orders this.

**How the app knows a pack is ready.** It does **not** keep its own list. The existing `SessionSkillGateway.listSkills()` (`GET /skill`, `engine/integrations-catalog/SessionSkills.kt`) lists `make-presentation` on the This phone connection.

### 4.2 `pack.json` (in the archive, read by the app from assets)

```json
{
  "schema": 1,
  "id": "presentations",
  "version": "1.0.0",
  "skill": "make-presentation",
  "agent": "presentations",
  "installedKb": 0,
  "sha256": "<archive sha256, written by the build>",
  "starter": "make_presentation",
  "agents": ["opencode2"],
  "outputs": ["pdf", "pptx"],
  "licences": ["LICENSES/pptxgenjs.txt", "LICENSES/NotoSans-OFL.txt"]
}
```

- `id`, `skill` and `agent` match `[a-z0-9-]{1,40}`.
- `starter` is a `StarterKind` wire word.
- `agents` lists the server kinds the pack is offered on. v1 has only `opencode2` on This phone.
- Copy (titles, questions) lives in `ui/words`, not in the pack.

### 4.3 How a starter reaches the agent

All of this uses existing code.

1. The app builds a `SkillSelection(agent = "presentations", model = <the person's current model>)`. The pack agent is used only when the connection's agent list (the one the agent picker already loads) includes `presentations`. Otherwise it falls back to the default agent, and pre-allows don't apply (§5.7).
2. It runs `SkillConversation.useInNewConversation("make-presentation")`, which creates the session and stages the skill with `POST /session/{id}/skill {skill, resume:false}` (`SkillConversation.kt`, `SessionSkills.kt`).
3. It calls `ports.prompt(sessionId, text, selection)` with the brief (§5.3).
4. The start line names everything: **"OpenCode · Presentations helper · on This phone"**, so the action names its target.

### 4.4 What we don't use

- Anthropic's `pptx` skill moved to Apache 2.0 on 2026-11-06 (anthropics/skills `2c5958eca`). It needs LibreOffice, Poppler and `sharp`, so it doesn't fit the phone. We don't fork it.
- No catalogue, no signing key and no remote updates. A second pack reuses the same shape. A remote source is a later decision, made only when a pack can't wait for an app release.

### 4.5 Starters (concrete change)

**Today** (`engine/composer/phrases/Starters.kt`):
- Outside a project (`!facts.hasProject`, which includes the projects root): `ListFolder, FindBug`.
- In an empty project: four build starters.
- Otherwise: explain, changes, bug, tests.

**Change.**
- Add `StarterKind.MakePresentation`.
- `suggestions(facts, useFor: Set<UseFor>, ready: Set<StarterKind>)`.
  - Outside a project, when `useFor` has any everyday id: `[MakePresentation, ListFolder]`. Later packs come before `ListFolder`.
  - Coding-only and project cases stay as they are.
- `MakePresentation` shows when the pack is ready **or** installable: This phone with Node present and enough free space (preflight rule `SetupPreflight.requiredFreeBytes`).
- The phrase-chip "moment's starter" takes the first suggestion, so no new input is needed in `KitPhraseChips`.
- **No "My files" folder.**
  - Decks go in `/root/projects/presentations/<slug>/`, inside the project space, which needs no "All files access".
  - A conversation opened in that folder counts as a project (`hasProject`). That is right: it already has history, so it shows no starters.

**Tap when not ready:** a sheet asks **"Add presentations to This phone · <installedKb> · works offline once added"** with **Add** / **Not now**. The install progress shows in the same sheet, using the setup job UI. Then the questions follow (§5.2).

**Tests.** Catalogue rows `B-CHAT-0xx`:
- everyday picks lead to `MakePresentation` outside a project;
- coding-only keeps today's list;
- not shown on a computer connection;
- an Arabic label.

---

## 5. Presentations (phase 1)

### 5.1 Feasibility spike first (gates D2 and phase 1)

The spike makes no app changes:
1. Install the archive by hand on the emulator's This phone.
2. Start sessions with `@make-presentation` and the brief from §5.3.

**Topics** (5): the water cycle (pt-BR, class, 10 min); a phone-repair shop's offers (ar, work, 5 min); my CV highlights for a sales job (es, work, 5 min); intro to Python (en, class, 20 min); a trip plan to Bali (id, friends, 10 min).

**Models:**
- the default free model on This phone (`opencode/big-pickle` today);
- one paid model, Sonnet or GPT, through the owner's key.

**Pass** means all of these:

| Check | Bar |
|---|---|
| (a) Outline stop | The agent stops after the outline and asks with the question tool in **≥4 of 5** runs per model |
| (b) Valid deck | A `deck.json` that passes the schema on the first build in **≥4 of 5** |
| (c) Opens cleanly | The `.pptx` opens without a repair prompt in PowerPoint (Android) **and** WPS on a Samsung **and** Google Slides |
| (d) Arabic | The Arabic deck reads right-to-left with joined letters in the PDF **and** in WPS. **This row is go/no-go.** |
| (e) Speed | Build time ≤ 20 s for 8 slides on the 4 GB emulator profile |

**Records** go in `docs/qa/presentations-spike-<date>/README.md`, with one image per slide-viewer screen (audit trail rule).

**If (a) or (b) fails on the free model:** shorten the skill to a fixed json template with two worked examples and run again. If it still fails, phase 1 ships "works best with a paid model", stated on the questions sheet.

### 5.2 Flow

1. **Start.** The person uses one of these routes:
   - **Starter:** tap the "Make a presentation" starter, or the chip.
   - **Typing:** "slides about X" works only in a conversation where the skill is active. Typing it in a general conversation gets the agent's normal answer. Guess: OpenCode may load the skill by its description; we don't promise it.
2. **Three questions.** One bottom sheet (D7):
   - **What is it about?** (text, prefilled from the composer)
   - **How long?** 5 / 10 / 20 min / Not sure, which gives 5 / 8 / 12 / 8 slides.
   - **Who is it for?** Class / Work / Friends and family / Other.
   - A line **"Slides in Português · Change"**.
   - A **look** strip with 4 themes, drawn from `themes.json` swatches: a title and a colour bar, not rendered slides.
   - Optional **Add notes or a PDF**, using the existing composer attachments.
   - The main action is **Make my outline**.
3. **Outline.**
   - The agent writes `deck.json` with `"stage": "outline"`.
   - It asks with OpenCode's `question` tool (event `question.asked`, shown by `engine/requests` `QuestionSheet`) with two options: **"Make the slides"** / **"Change the outline"**.
   - "Change the outline" puts the cursor in the composer.
4. **Slides.**
   - The agent fills `deck.json` (`"stage": "slides"`) and runs `node /opt/oc-packs/presentations/build.js <slug>`, which writes `deck.pdf` and `deck.pptx`.
   - The existing live line shows progress.
5. **Deck card and viewer.** See §5.5 and D2.
6. **Change by chat.**
   - "Make slide 3 shorter" works by itself.
   - Long-press a page in the viewer › **Change slide 3** puts "Slide 3: " in the composer.
   - Each build keeps a version, and the card shows **Back to version 2**, which sends that sentence.
   - The skill restores with `build.js --restore 2`.
7. **Share and save** (on the card and in the viewer):
   - **Share PDF** and **Share PowerPoint**: a new `ACTION_SEND` handoff (§5.5).
   - **Save to phone**: the existing `FileSaver`, `ACTION_CREATE_DOCUMENT`.
   - **Open in another app**: the existing `FileHandoff`, `ACTION_VIEW`.

**Not in phase 1:** sharing a PDF or photo *into* the app. The share target is `text/plain` only today (`AndroidManifest.xml:85`). Adding `application/pdf` and `image/*` needs content-URI copying into Ubuntu, so that is phase 2. Shared *text* already lands in a new conversation's draft and can be used with the starter.

### 5.3 The brief: form answers to agent

The first user message is visible in the transcript. It is plain localized lines followed by a fenced block the skill treats as authoritative:

````text
Make a presentation about the water cycle, 10 minutes, for a class.
```oc-brief
{"schema":1,"topic":"O ciclo da água","minutes":10,"slides":8,"audience":"class",
 "lang":"pt-BR","dir":"ltr","theme":"clean","slug":"o-ciclo-da-agua","attachments":[]}
```
````

**Field rules:**
- `audience`: `class|work|friends|other`.
- `dir`: from `lang`.
- `slug`: made by the app as `[a-z0-9-]{1,60}`, from the topic, deduplicated against the presentations folder listing.
- `attachments`: names relative to the deck folder.

**How attachments get there.** The app uploads composer attachments the way it does today. The skill copies those it may use into `<deck>/attachments/`.

### 5.4 `deck.json` and `themes.json`

**`deck.json`** (in `/root/projects/presentations/<slug>/`):

```json
{
  "schema": 1,
  "stage": "outline | slides",
  "title": "O ciclo da água",
  "lang": "pt-BR",
  "dir": "ltr | rtl",
  "theme": "clean",
  "version": 3,
  "slides": [
    {
      "layout": "title | bullets | two_column | image_left | quote | section | closing",
      "title": "string, max 80",
      "subtitle": "string, max 120, optional",
      "bullets": ["string, max 120", "max 6 items"],
      "left": ["bullets, two_column only"],
      "right": ["bullets, two_column only"],
      "image": "attachments/<name>.jpg|png, optional, image_left only",
      "notes": "speaker notes, max 1500, optional"
    }
  ]
}
```

**What `build.js` checks:**
- It validates against the schema. An unknown `layout` is drawn as `bullets`, and a warning is printed.
- Text over the limits is cut with "…", and build.js says which slide.
- `image` must be a relative path under `attachments/`. URLs, absolute paths and `..` are refused. Images are read as bytes and passed to pptxgenjs as `data:`, so the build makes **no network calls**.
- Output names are always `deck.pdf` / `deck.pptx` inside the slug folder. The display name comes from `title` and is only used when sharing or saving.
- Each build copies the previous `deck.json`/`deck.pdf` to `versions/v<N>/` and keeps the last 10.

**`themes.json`** (in the pack, read by `build.js` and by the app for the look strip):

```json
{
  "schema": 1,
  "themes": [
    {
      "id": "clean",
      "colors": { "bg": "FFFFFF", "text": "1F2328", "accent": "0F9D76", "muted": "6B7280" },
      "fonts": { "latin": "Noto Sans", "arabic": "Noto Sans Arabic" },
      "pptxFonts": { "latin": "Arial", "arabic": "Arial" },
      "sizes": { "title": 36, "body": 20, "notes": 12 }
    }
  ]
}
```

**Fonts.**
- The PDF embeds the bundled Noto files.
- The PPTX names fonts that exist on viewers (`pptxFonts`), because pptxgenjs can't embed fonts.
- So the PDF is the "looks exactly like the preview" file, and the PPTX is the editable one. The spike checks (c) and (d) cover the gap.
- Four themes in v1: clean, bold, calm, chalkboard.

### 5.5 How the app finds a deck and where it lives

**Where it lives.** `/root/projects/presentations/<slug>/` on This phone holds:
- `deck.json`
- `deck.pdf`
- `deck.pptx`
- `versions/`
- `attachments/`

**The marker.** `build.js` ends its output with one line:
`OC_DECK {"schema":1,"slug":"o-ciclo-da-agua","version":3,"slides":8}`

**The deck card.**
- The chat presenter recognizes a completed shell tool step whose command starts with `node /opt/oc-packs/presentations/build.js` and whose output has a valid `OC_DECK` line. It shows a **deck card** for that step: title, "8 slides · version 3", and the actions.
- The card's identity is `(connection, slug)`. A later marker for the same slug updates the same card.
- This is a **proposal to the transcript/chat module owner** (single owner): one recognizer, no new server event.

**Reading files.**
- The card and viewer read `deck.pdf` and `deck.pptx` through the existing server file read (`engine/files` `FileData`).
- The PDF is shown with the existing `PdfPages` / `LocalPdfService` viewer.
- Save uses `FileSaver`; Open with uses `FileHandoff`.
- **Share** is new: an `ACTION_SEND` twin of `FileHandoff`, using the same `open-with/` cache copy and `FileProvider` (`oc_files_paths.xml`).

**"Your presentations."** The Files list of `/root/projects/presentations`. There is no new index.

### 5.6 Preview: which renderer (D2, decided after the spike)

| Option | What draws the preview | New app parts | Size | Risk |
|---|---|---|---|---|
| **A. PDF from the build step** | `build.js` writes `deck.pdf` with pdfkit from `deck.json` and the bundled fonts. The app shows it in the existing PDF viewer. | Deck card, Share handoff | +about 6 MB gzipped untrimmed. Trimming to the Node build only: **measure** | pdfkit's Arabic shaping and bidi: spike row (d) |
| B. The app draws the slides | A new kit `KitSlide` draws `deck.json`; Android `PdfDocument` writes the PDF | Deck card, Share, `KitSlide` (commonMain), PDF export (androidMain only; the desktop target gets no export) | ~0 | A second layout engine to keep in step with build.js; kit owner proposal |
| C. LibreOffice | `.pptx` → PDF on the phone | Deck card, Share | Hundreds of MB (Guess) | Too heavy for This phone |

**Recommendation:**
- **A if spike row (d) passes**: one renderer for preview and PDF, no new kit renderer.
- **B if (d) fails**: Android shapes Arabic correctly.
- C only on a computer, later.

### 5.7 Permissions and trust

**What the pack agent pre-allows.** `presentations.md` uses OpenCode `permissions`:
- `edit` only under `/root/projects/presentations/**`;
- `shell` only for `node /opt/oc-packs/presentations/build.js *`;
- everything else `ask`.

**What doesn't change.**
- This is server-side config. It doesn't change the app's own rules: the app never auto-allows, and `engine/cards` `ShowCardNeverAutoAllowTest` keeps passing unchanged.
- Asks outside the pack still show the exact command (vision decision 1).
- With a non-pack agent (fallback in §4.3), the person gets ordinary permission cards.

**The input path.**
- The brief's `slug` is made by the app, never by the model.
- `build.js` refuses paths outside the deck folder and makes no network calls (§5.4).
- A shared PDF or pasted text can still steer the *content* of a deck. It can't widen what the agent may do.

**Failure lines** (no raw errors; details under **Details**):

| Case | Line | Action |
|---|---|---|
| Self-test failed | "Presentations couldn't start on This phone." | **Try again** |
| Not enough space | "This phone needs <n> MB free for presentations." | **Open storage** |
| Node removed | Same as "not added": the starter offers **Add** again | — |
| Model stopped mid-outline or hit its limit | — | The existing failed-reply fixes (choose another model: B-169) |
| Build failed | "The slides couldn't be made. The outline is kept." | **Try again** (sends "Build the slides again") |

### 5.8 Kit parts and words

**Kit parts.** These are proposals to the kit owner, under the kit-only rule:
- **deck card** (title, a facts line, up to four text actions, no boxed icons);
- **look strip** (swatch rows with a text label; the selected one shows a check, not a pill);
- **segmented choice** for "How long?", if the kit lacks one.

The questions sheet uses the existing kit sheet, field and choice parts. Under B, also `KitSlide`.

**Words.**
- New strings go in `ui/words` `values/` and `values-ar/`. `values-es/` and `values-pt/` exist but are partial (en 13,225 lines matching `<string`; es 2,681; pt 637), so D8 decides es/pt for the new strings only.
- A test scans the everyday surfaces (starter sheet, questions sheet, deck card, What you use it for) for: skill, plugin, MCP, agent, command, npm, Node, Python.

---

## 6. Customisation: "What you use it for" (D5)

- The Q1 answer is the only new setting. It lives in Settings › Chat, as one row.
- It picks starters (§4.5) and nothing else: no change to the new-conversation question, the start folder, permission cards or Settings order.
- This keeps settings-scope decision 10: no global switch, nothing hidden.
- It is app-wide. Starters only change on a This phone connection, because the pack exists only there. On a computer connection, starters stay as they are today.

---

## 7. Phasing

| Phase | Contents | Gate to start | Acceptance | Owner and modules |
|---|---|---|---|---|
| **0** | F1–F5 (§2) and the Q1 question (§3) | Now | §2 acceptance; `use_for` in the debug sink output | runtime/setup owner; analytics; words |
| **0.5** | Presentations spike (§5.1) | Now, in parallel. No app code. | §5.1 pass table recorded | One agent, on the emulator |
| **1** | Pack archive and component; starter change; questions sheet; deck card; Share handoff; renderer per D2 | Phase 0 merged **and** spike passed **and** D1 | On the emulator: starter → add → questions → outline → slides → share PDF to a test app, in en and ar | composer (starters), chat (card: proposal), kit (proposals), androidApp (Share), runtime (component) |
| **2** | Share PDF/photos *into* the app; the second pack (by `use_for` and `starter_used`); es/pt | Phase 1 used by real people for 2 weeks | — | — |
| **3** | Remote hosts; Claude Code via Paseo; a remote pack source | A verified write contract on OC2 remote | — | — |

Phase 1 estimate: Guess, 2–3 weeks of agent work after the spike, mostly the deck card, the renderer and device QA.

**New events.** All DETAILED, gated by `usage`, with PRIVACY.md lines in the same commit:

| Event | Values |
|---|---|
| `use_for` | `kind`: everyday, coding, both, looking |
| `starter_used` | `starter`: StarterKind wire words |
| `pack_added` | `pack`, `result`: ok, fail_space, fail_check |
| `deck_made` | `result`: ok, failed; `slides`: count bucket |
| `file_shared` | `kind`: pdf, pptx |

**Risks:**
- The free model can't follow the outline stop (spike a/b).
- Arabic fails in pdfkit, which pushes D2 to B.
- APK size grows by about 2–8 MB, depending on pdfkit trimming.
- The answers come from consenting users only.
- Phase 0 may show the funnel has causes we can't fix in the app (networks, phones).

**No migration work** (owner: no users yet). If `pack.json` `version` differs from the installed one after an app update, the app re-runs install. Decks stay.

---

## 8. Owner decisions

Recommendations in **bold**. Each visual decision has a mockup brief in §9. D4 from v1 (where packs come from) is settled by the coordinator: APK assets (§4.1).

- **D1. Order.**
  - **A: phase 0 now, the spike now, phase 1 after both.**
  - B: phase 1 in parallel with phase 0.
  - C: phase 1 waits for two weeks of `use_for` data too.
- **D2. Deck preview and PDF** (visual).
  - **A: PDF from the build step, shown in the existing viewer, if Arabic passes.**
  - B: the app draws slides (`KitSlide`).
  - C: no in-app preview; open in another app.
  - Decided after the spike.
- **D3. Survey consent.**
  - **A: one coarse `use_for` event, DETAILED, sent only with usage consent on.**
  - B: keep the answer on the phone only, no event.
  - C: ask more questions (v1).
- **D5. What the answer changes** (visual).
  - **A: starters only.**
  - B: starters plus the new-conversation question and Settings order.
  - C: a full everyday mode.
- **D6. Where the question appears** (visual).
  - **A: under setup progress.**
  - B: as the first setup step, before install starts.
  - C: on home, after the first reply.
- **D7. How the three questions are asked** (visual).
  - **A: one sheet.**
  - B: one question per step.
  - C: the agent asks in chat with question cards.
- **D8. Languages for new strings.**
  - A: en and ar only.
  - **B: en, ar, es, pt.**

## 9. Mockup briefs (for the visual decisions)

Shared rules for every mockup:
- one real phone screen per option, 390 × 844, in the app's current native look;
- flat hairline rows, not cards per row;
- solid mint only for the one main action;
- no boxed icons, no pill badges, plain glyphs only;
- light theme;
- Portuguese copy for Ana, plus one Arabic variant of the recommended option.

**D2: deck preview** (after the deck is made):
- **A. PDF pages.** The existing PDF viewer, full screen: a page with slide 1 of "O ciclo da água" (clean theme, title slide). A thin page line at the top, "1 of 8". The bottom bar has text actions **Share PDF · Share PowerPoint · Save**. A faint hint under the page: "Hold a page to change it".
- **B. App-drawn slides.** A large slide at the top. A horizontal row of 8 small slide thumbnails below it, the current one with a mint outline. A "Notes" fold under the row with two lines of speaker notes. The same three text actions at the bottom.
- **C. Card only.** The chat, with the deck card as the last item: the title "O ciclo da água", a muted line "8 slides · version 3", a first-slide thumbnail at the left edge, and the text actions **Open · Share PDF · Share PowerPoint**. No viewer; Open hands off to WPS.

**D5: what the answer changes** (new conversation on This phone, Ana picked "School or study"):
- **A. Starters only.** Today's start screen, unchanged: "What would you like to build?", the glowing field, "OpenCode · on this phone ⌄". Under the field, two starter rows: **Make a presentation**, **List this folder**.
- **B. Question too.** The same screen, but the heading reads "What do you need help with?" and the starters are **Make a presentation**, **Summarise a PDF** (greyed: "coming later"), **List this folder**.
- **C. Everyday mode.** A simplified start: the heading "What do you need help with?" and three large text rows (Make a presentation / Write something / Ask a question). The agent line is hidden behind "More".

**D6: where the question appears:**
- **A. Under progress.** The setup screen, mid-install: the progress line "Installing Node.js · 3 of 5" at the top, then a hairline. Below it: "While you wait: what do you want help with?", six check rows (School or study … Just looking), and a quiet **Not now** at the bottom. No main action until something is picked; then **Save**.
- **B. First setup step.** Before install starts: the full screen is the question, with the six check rows and the main action **Continue setup** (mint). The setup steps start after it.
- **C. Home after the first reply.** The conversations list with the update-notice slot at the top. It holds "What do you want help with?" and one line "Pick to get better starters", with **Choose** / **Not now** as text actions.

**D7: the three questions:**
- **A. One sheet.** A bottom sheet titled "Make a presentation" with:
  - a field "What is it about?", prefilled "o ciclo da água";
  - a row "How long?" with a segmented 5 / 10 / 20 min / Not sure;
  - a row "Who is it for?" with Class selected;
  - a line "Slides in Português · Change";
  - a look strip of four swatch rows (Clean ✓, Bold, Calm, Chalkboard);
  - the main action **Make my outline**.
- **B. Steps.** The same sheet showing only "How long?" as four full-width rows, with "Step 2 of 3" at the top and **Next**.
- **C. In chat.** The conversation after tapping the starter: the agent's first message "Let's make your slides. How long is your talk?" with an OpenCode question card offering four answer rows (5 / 10 / 20 min / Not sure).
