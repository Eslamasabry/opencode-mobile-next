# Regular people: who they are, what they want, and how the app helps them

**Status:** elephant draft v1, 2026-11-09. It goes to goldfish reviews next, then a v2, then the owner. Nothing here is approved and nothing gets built from it yet.

**Scope:** native app (`native/`). Flutter is frozen, so nothing here goes there unless the owner asks.

**Finish line (once approved and built):** a person who does not code opens the app, says what they want ("slides about the water cycle for Tuesday"), gets a finished deck on the phone, and shares it to WhatsApp or Drive. They never see the words skill, plugin, MCP or agent.

**Non-goals (this doc):** a hosted service of our own, image generation, a marketplace open to third parties, Flutter changes, on-device models doing the work. Gemma stays the summary-only assistant ([gemma-assistant-2026-10-31.md](../design/gemma-assistant-2026-10-31.md)).

How to read it: **Fact** = checked, with a source. **Guess** = my judgement, to be tested. Anything marked "measure" still needs a number from a phone or the emulator.

---

## 0. What this builds on, and where it disagrees

| Existing doc or rule | What it says | What this doc does with it |
|---|---|---|
| [product-persona.md](../product-persona.md) (Sept 2026) | Main persona: a developer away from the desk. Second: a phone-first builder. Marked "hypotheses to validate". | **Adds** non-coding personas next to it. It does not replace the developer persona. The GA data below suggests the phone-first group is now the larger one. |
| [settings-scope-2026-10-12.md](../design/settings-scope-2026-10-12.md) §11 and decision 10 | "No global Advanced switch"; progressive disclosure per task; warnings and differences stay visible to everyone. | **Keeps it.** §5 proposes a "What you use it for" choice that changes *defaults and words*. It is not a mode that hides things. This is a real tension, so it is decision D5. |
| Phrase chips ([qa/phrase-chips-2026-10-12](../qa/phrase-chips-2026-10-12/README.md)), native `KitPhraseChips` + `Starters.kt` | One chip row above the input: learned phrases plus one starter for the moment. | Everyday starters go **into this row** and into the start area. No new bar (owner rule: no new UI levels). |
| [teaching.md](../ux-system/teaching.md) | Tips at the moment of use; at most twice; replay in Help › Tips. | The first time a person uses "Make a presentation", we offer a short tutorial. Nothing shows at app start. |
| [vision-decisions-2026-10-20.md](../design/vision-decisions-2026-10-20.md) #1, #3 | Permission asks always show the exact command. New conversation asks "What would you like to build?" | The exact command stays visible. Packs pre-allow their own commands, so most asks never appear (§3.5). The start question changes with D5. |
| Telemetry: `native/engine/analytics` (fixed event lists, consent "Send reports to developer", Firebase is one vendor behind `TelemetryVendor`); owner 11-07: "anything custom goes through the analytics SDK" | Only fixed short words leave the phone; nothing is sent without consent. | Survey answers are fixed choice ids through the same layer. Free text never goes to analytics (§2.4). |
| Personal-assistant vision (FactsBook, owner direction 2026-10-11; separate repo) | A personal side of the app, built on the user's own devices. | The "everyday help" persona is who FactsBook is for. Packs are the short-term, concrete half of that idea. |

---

## 1. Who the regular users are

### 1.1 What our analytics say (Fact, GA4 property 563181914, 2026-11-01 → 2026-11-09)

Read with `/home/eslam/Storage2/tools/ga.sh days 7` plus custom reports on `customEvent:*` dimensions. Only people who said yes to "Send reports to developer" are counted. Some rows are the owner's and test devices (for example NX721J). Every count here is small. Use them as directions, not percentages.

**Install-to-reply funnel (unique users):**

| Step | Users | Event |
|---|---|---|
| Opened the app the first time | 35 | `first_open` |
| Started phone setup | 24 | `setup_step_reached` |
| Setup finished | 18 | `setup_done` |
| Got a first reply | 13 | `first_reply` |
| Sent a message that succeeded | 14 | `send_result=ok` |
| Used any tracked feature | 22 | `feature_used` |

So **about one in three people who install the app ever get a reply.** For these users, the biggest problem is getting started, not missing features. Setup failures cluster on `essentials` (11 failures, 2 users).

**Where it runs:** `conn_attempt` by `server_kind` is `phone` for 19 users and `computer` for 1. `connect_computer_chosen` was used by 1 user. Nearly everyone uses This phone.

**Features** (`feature_used.feature`, users): model_switch 7, agent_switch 7, server_add 9, file_attach 2, voice 2, review_open 2. Antigravity was started once (`agent_start`). Nobody used Git.

**Country** (users): Brazil 5, Indonesia 4, India 3, UAE 3, Iran 2, Mexico 2, Russia 2, Vietnam 2, and one each from Algeria, Argentina, Bangladesh, Côte d'Ivoire, Egypt, El Salvador, Paraguay, Peru, Sweden, Syria, Tunisia, UK, Venezuela.
**Language:** English 15, Spanish 7, Portuguese 5, Arabic 2, Russian 2, Vietnamese 2, French 1, Indonesian 1.
**Phones:** mostly Xiaomi/Redmi/POCO (about 13 models), Samsung A-series and S-series, Motorola, Infinix, Realme. Android 12 to 17.
**Storage at setup** (`linux_setup_step.free_space_range`): more than 32 GB free for 10 users, 8–32 GB for 4. `memory_pressure` events came from 4 users.
**Reply speed** (`send_result.first_reply_range`): mostly 2–10 s.

**Gaps in our own data (Fact):** `screen_view.screen` is empty or "(not set)" for almost all 1,477 events. We cannot see which screens people open. The native build should fix this before we rely on screen data (§2.5).

### 1.2 What non-developers use AI assistants for (research spike, about 20 minutes, 2026-11-09)

| Finding | Source |
|---|---|
| Three topics make up nearly 80% of ChatGPT conversations: **practical guidance, seeking information, writing**. Non-work use grew from 53% to over 70% of messages. Writing is the biggest work use. | Chatterji et al., *How People Use ChatGPT*, NBER WP 34255, Sept 2025 ([nber.org/papers/w34255](https://www.nber.org/papers/w34255)) |
| 34% of US adults had used ChatGPT (58% under 30). 26% used it to **learn**, and 28% of employed adults used it for **work**. | Pew Research, June 2025 ([pewresearch.org](https://www.pewresearch.org/short-reads/2025/06/25/34-of-us-adults-have-used-chatgpt-about-double-the-share-in-2023/)) |
| In lower-adoption countries (India, Indonesia, Brazil-like income levels), Claude use leans **more toward coding** and toward handing over whole tasks. High-adoption countries use it more for education, science and business. Coding is said to be over 50% of tasks in India (secondary source). | Anthropic Economic Index, Sept 2025 ([anthropic.com](https://www.anthropic.com/research/anthropic-economic-index-september-2025-report)); country figure via [dataconomy.com](https://dataconomy.com/2025/09/17/anthropic-economic-index-reveals-uneven-claude-ai-adoption) |

**What this means for us (Guess):**
- Our users are mostly in exactly the countries where coding share is high. So **we should not drop the coding persona.** Many of our 32 are probably learners and tinkerers.
- The wider market for a phone AI app is writing, practical help and learning. Our app's first screen speaks only to builders ("What would you like to build?", "Find a bug", "Write a Python script").
- People who want to "hand over a whole task" match well with packs that produce a finished file: a deck, a CV, a spreadsheet.
- We don't know yet. That is why the survey (§2) comes before any large build.

### 1.3 Personas

These are hypotheses, shaped by the countries, languages and phones above. They are not interview findings.

**P1 · Ana, 20, university student, Brazil (Portuguese), Redmi Note 13, 6 GB RAM, no paid AI plan.**
- *Wants:* slides for a seminar on Tuesday, summaries of the PDFs her professor posts, help with a written report.
- *Her words:* "fazer slides sobre…", "resumo desse PDF", "trabalho da faculdade", "pra terça".
- *What confuses her today:*
  - setup lists "Node.js", "Python", "OpenCode" and "essentials";
  - new conversation asks "What would you like to build?";
  - with no project open, the starters are "List this folder" and "Find a bug";
  - the model picker shows provider names and ids;
  - a file the agent makes lands in `/root/projects/...`, and she can't find it to send on WhatsApp;
  - a free-model daily limit shows up as an error, not as a plain way forward.

**P2 · Karim, 41, runs a phone-repair shop, Algeria (Arabic and French), Samsung A15.**
- *Wants:* Instagram and Facebook posts for offers, a price list in a sheet, simple invoices, replies to customers.
- *His words:* "اكتب لي منشور عن عرض…", "فاتورة", "جدول أسعار", "Excel".
- *What confuses him:*
  - English-only coding starters;
  - permission cards that show shell commands ("Allow: `python3 make_invoice.py`?") with no plain sentence first;
  - the words agent, server and project;
  - not knowing whether something costs money.

**P3 · Diego, 27, job seeker, Peru (Spanish), Moto G54.**
- *Wants:* a CV fitted to a job ad, a cover letter, practice interview questions, and a PDF he can attach to an email.
- *His words:* "mi currículum", "carta de presentación", "para este trabajo", "en PDF".
- *What confuses him:*
  - pasting his old CV (a .docx) and getting a reply about "reading the file with python";
  - no "Save as PDF";
  - not knowing which of Claude Code, Codex and OpenCode to pick, or why.

**P4 · Rizky, 22, curious tinkerer, Indonesia (English/Indonesian), POCO X6.**
- *Wants:* to learn to code, build a small website or Telegram bot, and try different AIs for free.
- *His words:* "make a website for my band", "how do I…", "free model".
- *What confuses him:*
  - setup failing at `essentials` with no clear way to retry;
  - switching agents without knowing what each needs (sign-in, plan);
  - free-model limits.
- He is close to today's phone-first builder persona, and the current app mostly fits him.

Each persona maps to **one pack** we could offer first:

| Persona | First pack |
|---|---|
| Ana | Presentations; summarise a PDF |
| Karim | Posts; price list / spreadsheet |
| Diego | CV and cover letter |
| Rizky | "Learn to code" starters (today's starters, made friendlier) |

---

## 2. Ask them: an in-app survey

### 2.1 Where and when it appears

**The setup wait (main placement).**
- Phone setup takes minutes (`setup_done` minutes buckets), and it is the one time nearly every new user is waiting and looking at us (24 of 35 reached setup).
- We ask one question there, inside the setup screen's existing card area. It does not block, does not pop up, and does not delay setup.
- The answer **helps the person right away**: it chooses their first starters (§3). That makes it worth answering, not a chore.

**Existing users (already set up).**
- On the first open after the update that ships the survey, the same question shows **once** in the conversations home.
- It sits in the slot the in-app update notice uses (app-updates-two-layers), so no new bar is added.
- It has "Not now". A dismissed survey never comes back by itself.

**The follow-up (Q3–Q5).** It shows once, after the person's **3rd successful reply on a later day than the first**. It sits in the same home slot, and also in Settings › Help › "Tell us what you need".
- Why not "after the 3rd conversation": only 13 people ever got one reply. A rule tied to conversations would reach almost nobody.
- Why "later day": people answer better once they have used the app more than once (Guess).

**Never:**
- during onboarding steps other than the setup wait;
- while a question or permission card is waiting;
- while the keyboard is up;
- more than once per question set.

This follows the [teaching.md](../ux-system/teaching.md) rules.

### 2.2 The questions

Choice ids are what may leave the phone. The labels are localised (en, ar, es, pt first).

**Q1, at the setup wait (pick up to 3):** "What do you want help with?"

| Id | Label |
|---|---|
| `study` | School or study |
| `work_docs` | Work documents: slides, letters, sheets |
| `writing` | Writing and posts |
| `job` | Job search: CV, cover letter |
| `planning` | Planning: trips, budgets, schedules |
| `learn_code` | Learning to code |
| `build_apps` | Building apps or websites |
| `code_work` | Coding I already do for work |
| `exploring` | Just looking around |

**Q2, follow-up, one choice:** "How much do you code?"

| Id | Label |
|---|---|
| `none` | I don't code |
| `little` | I've tried a little |
| `hobby` | For fun or study |
| `job` | It's my job |

**Q3, follow-up, pick up to 2:** "Which would you use first?"

| Id | Label |
|---|---|
| `slides` | Make a presentation |
| `cv` | Write or fix my CV |
| `pdf_summary` | Summarise a PDF |
| `sheet` | Make or fix a spreadsheet |
| `posts` | Write social posts |
| `trip` | Plan a trip |
| `study_help` | Explain a lesson |
| `website` | Build a small website |
| `other` | Something else |

**Q4, follow-up, one choice:** "Do you already pay for an AI?" This tells us how much the free model has to carry.

| Id | Label |
|---|---|
| `none` | No |
| `chatgpt` | ChatGPT |
| `claude` | Claude |
| `gemini` | Gemini |
| `other` | Another one |
| `skip` | Rather not say |

**Q5, follow-up, optional free text:** "Anything you wish it could do?"
- It is kept **only on the phone**. It is never sent to analytics.
- The one way it leaves the phone is the person tapping **"Email this to the developer"**. That opens their own email app with the text filled in, and they can see and edit it before sending.
- See D3 for the alternatives.

### 2.3 How answers are stored

- **Store:** app-wide, not per server: `oc.survey.v1` = `{ q1: [ids], q2: id, q3: [ids], q4: id, q5Draft: text?, askedAt, answeredAt, dismissed }`.
  - Listed in the preferences ledger.
  - Cleared by Privacy and data › "Clear app data on this phone".
  - The deletion sweep for profiles does not touch it, because it is not profile data.
- **Changing answers:** Q1 and Q2 are also the person's **"What you use it for"** setting (§5). They can change them later in Settings › This app.

### 2.4 Privacy and how answers leave the phone

**What is sent:**
- Only choice ids. One event per chosen option: `survey_answer { q: "q1"…"q4", choice: <id>, v: "1" }`, added to the fixed list in `ProductEvents.kt` and PRIVACY.md in the same change (the file's own rule).
- No free text, no timestamps finer than the vendor's day, and no link to a server or conversation.

**Which consent covers it:** this is decision D3. The options:
- **(A)** Send only when "Send reports to developer" is already on. This is simplest and honest, but the answers then come only from people who said yes there.
- **(B)** The survey's own **"Send answers"** button counts as consent for those answers only, once.
  - It goes through the same telemetry layer to a vendor-neutral sink.
  - It must **not** switch on Firebase Analytics collection, because that would also send automatic events like `session_start`.
  - So in practice it needs a second sink that takes one anonymous POST (for example a small endpoint on the owner's VPS, or Aptabase/PostHog, both of which `SinkId` already names).
- **(C)** No telemetry at all. Answers stay on the phone, and a "Share my answers" button sends a fixed-format text by email.

**Recommendation: A for the native release** (zero new infrastructure; most current users already said yes, judging by GA counts). Then **B** once the owner picks a sink destination (plan 16 D16-1 is still open).

### 2.5 How we read the results

1. Register `q` and `choice` as GA custom dimensions. Extend `ga.sh` with a `survey` report: `survey_answer` by `customEvent:q` × `customEvent:choice`, unique users.
2. Join the answers with what people actually do, using new fixed-list events (consent-gated like the rest):
   - `starter_used{starter}`
   - `pack_added{pack}`
   - `pack_result{pack, result: done|stopped|failed}`
   - `file_shared{kind: pptx|pdf|docx|xlsx|other}`

   What people *do* outweighs what they *say*.
3. Fix `screen_view.screen` being empty (§1.1). Without it, we can't see whether people find "What it can do".
4. With about 30–100 answers, report counts, not percentages. Re-read at 2 weeks and 6 weeks.
5. The decision rule: build the next pack from the top Q3 choice that is also in the top half of `starter_used`.

---

## 3. "Things it can do for you", without jargon

### 3.1 What the person sees

**The words.**
- The page is called **"What it can do"**. It lives in Settings › AI, next to Agents, and search finds it with "slides", "CV" and so on.
- Each item is a verb phrase: "Make a presentation", "Write my CV", "Summarise a PDF", "Fix my spreadsheet", "Write posts", "Plan a trip".
- An item that is not added yet shows its size and where it goes: **"Add to This phone · 8 MB"**. This follows "actions name their target".
- An added item shows **"Ready on This phone"** and "Remove from This phone".

**Starters.** Starters appear in the places that already exist:
- **The start area of a new conversation:** today `Starters.suggestions()` gives "List this folder" and "Find a bug" when no project is open.
  - For people whose "What you use it for" includes everyday help, these become the everyday starters they picked in Q1/Q3, up to 4.
  - Coding starters stay as they are inside a code project. A folder with files or Git keeps today's starters.
- **The phrase-chip row:** the "moment's starter" can be an everyday starter on an empty conversation. Learned phrases still lead. No new row is added.

**Starting a starter.** Tapping a starter whose pack isn't added offers **"Add to This phone · 8 MB"** in the same sheet, then carries on. There is one tap to add, and the person never visits a store page first.

**Typing works too.** Once a pack is added, typing "I need slides about X" also works. The agent sees the pack's description and loads it by itself (OpenCode and Claude Code both do this, §3.2).

**The forbidden words.** None of these appear on these surfaces: *skill, plugin, MCP, agent, command, slash, npm, Node, Python*. They still appear under Details, on the developer pages (Settings › Tools) and in Agents, where they name real things.

### 3.2 What a "pack" really is (Fact, sources as of 2026-11-09)

A pack is a folder we publish. It holds:

1. **One skill:** a `SKILL.md` folder. This is the instructions plus scripts.
   - **OpenCode** reads skills from `.opencode/skills`, `~/.config/opencode/skills`, and also `.claude/skills`, `~/.claude/skills` and `.agents/skills`. Its own folders take precedence.
   - OpenCode shows the model each skill's id, name and description, and the model loads one with the `skill` tool. A person can force one with `@skill-id`.
   - Skill permissions are allow, ask or deny per id. ([opencode.ai/docs/skills](https://opencode.ai/docs/skills/))
   - **Claude Code** reads `~/.claude/skills/<name>/SKILL.md` and project `.claude/skills/`, loads a skill by its description or by `/name`, and supports `allowed-tools` and `disable-model-invocation`. ([code.claude.com/docs/en/skills](https://code.claude.com/docs/en/skills))
   - **So one install at `~/.claude/skills/<pack>` is read by both OpenCode and Claude Code** on the same host.
   - Codex, Gemini and Antigravity: not checked in this spike. Packs say "Works with OpenCode and Claude Code" until verified.
2. **Optionally, one OpenCode agent:** `~/.config/opencode/agents/<pack>.md`, with `mode`, `model` and a `permissions` list that can allow `shell` and `edit` for its own folder and commands only. ([opencode.ai/docs/agents](https://opencode.ai/docs/agents/)) It is used to pre-allow the pack's own commands (§3.5).
3. **Optionally, one OpenCode command:** `~/.config/opencode/commands/<pack>.md`, with `$ARGUMENTS`. ([opencode.ai/docs/commands](https://opencode.ai/docs/commands/)) The app already lists server commands. A pack command would show there for developers. Regular users reach it through the starter, never through `/`.
4. **Pinned dependencies:** for example `pptxgenjs` in the pack's own `node_modules`. Nothing is installed system-wide.
5. **A manifest** the app reads (our format):
   - id and version;
   - title, description and the starter's 2–3 questions in each language;
   - which agents it works with;
   - size;
   - dependencies, each with a sha256;
   - the licence and NOTICE files;
   - the file types it makes (for the share buttons).

**Anthropic's document skills.**
- The `docx`, `pdf`, `pptx` and `xlsx` skills in [github.com/anthropics/skills](https://github.com/anthropics/skills) were moved to **Apache 2.0** on **2026-11-06** (commit `2c5958eca`, "Move Office skills to the Apache 2.0 license"). Before that commit they carried a restrictive Anthropic licence.
- **So we may redistribute and modify them from that commit on**, provided we:
  - keep the LICENSE and NOTICE files;
  - mark changed files;
  - do not use Anthropic's marks to name our feature.
- The README calls them "for demonstration and educational purposes", so we own their quality ourselves.
- **But the pptx skill is heavy for a phone.** It uses Node + `pptxgenjs`, `react-icons`/`react`/`sharp`, Python `markitdown[pptx]`, Pillow and lxml, **LibreOffice** (`soffice`) and **Poppler** (`pdftoppm`) for previews.

**Slide tools:**

| Tool | Licence | What it needs |
|---|---|---|
| `pptxgenjs` 4.0.1 | MIT, about 2.6 MB unpacked (npm registry). README lists RTL text support. | Node only |
| `python-pptx` 1.0.2 | MIT (PyPI) | lxml |
| Marp CLI 4.5.1 | MIT. HTML export needs no browser. PDF and PPTX export need Chromium, and its PPTX is image-only by default ([marp.app/docs](https://marp.app/docs)). | Chromium for PDF/PPTX |
| reveal.js 6.0.2 | MIT (npm registry) | HTML/browser; no pptx |

### 3.3 Where packs install

**This phone, which nearly everyone uses (Fact: §1.1).**
- The built-in Ubuntu 24.04 (`ubuntu-base-24.04.5`, "noble") already has the essentials (`curl ca-certificates git openssh-client`), `python3 python3-venv python3-pip`, and pinned Node from nodejs.org.
- Source: `native/engine/runtime/.../setup/registry/PhoneSetupScripts.kt`.
- So Node packages and Python venvs work with no extra system install.
- LibreOffice, Poppler and Chromium are **not** installed.
- Paths inside Ubuntu:
  - the skill at `/root/.claude/skills/<pack>/`;
  - the agent at `/root/.config/opencode/agents/<pack>.md`;
  - dependencies at `/opt/oc-packs/<pack>/`.
- Packs register as removable components next to `essentials`, `python` and `node` (`RemovableComponents.kt`), so This phone › Storage shows their size and can remove them.

**A connected computer or VPS (2 of 32 users): later phase.**
- Writing files to a remote host needs a callable contract we haven't confirmed (an OC2 file-write API, or a shell run with the person's approval).
- AGENTS.md rule 2 says to check feasibility first. Until that is checked, the page says "Available on This phone" and offers nothing for the remote host.

### 3.4 Install in one tap, keep it updated

1. **Tap.** "Add to This phone · 8 MB".
2. **Install.**
   - The app downloads the pack archive from our catalog and checks its sha256.
   - It unpacks the archive into Ubuntu, then installs the pinned dependencies from the pack's own lock file. Network is needed this once.
   - Progress uses the existing setup-job UI (`oc_stage`, percent).
3. **Check.** The app runs the pack's own self-test, for example `node build.js --selftest` making a 1-slide deck. It shows "Ready" only if the self-test passes. If it fails, the person sees a plain sentence and **Try again**, and the log sits under Details (owner rule: no raw errors).
4. **Updates.** These depend on decision D4:
   - **(A)** The catalog is bundled in the app, so packs update when the app updates. This is the safest: what we test is what ships.
   - **(B)** A remote signed catalog. The app checks at most daily when "What it can do" opens. An ed25519 key is pinned in the app.
   - **(C)** Open catalogs, including third-party skill registries.

   Packs are instructions that agents follow with shell access, so a pack is code. **Recommendation: A for the first packs, B later.**
5. **Remove.** "Remove from This phone" deletes the skill, agent and dependency folders. Files the person made stay.

### 3.5 Fewer scary permission cards

- A pack's agent (or its skill's `allowed-tools`) pre-allows only:
  - writing inside its own output folder (`/root/My files/Presentations/<name>/`);
  - running its own build command.
- Anything outside that still asks, with the exact command shown (vision decision 1).
- In everyday use the card leads with a plain sentence: "Save the slides file?". The exact command sits right under it in small mono text, always visible, not hidden behind Details.

---

## 4. Presentations first

### 4.1 The flow, step by step

**1. Start.** The person can start in any of these ways:
- tap the starter **"Make a presentation"** (start area or chip row);
- type "slides about X for Tuesday";
- share a PDF or notes *into* the app with "Make slides from this". This uses the share target that exists today.

**2. Three plain questions.** These are an app-side form in a bottom sheet, not model turns. That makes them fast, free, offline and identical in every agent.
- **What is it about?** The field is prefilled from what they typed or shared.
- **How long do you have?**

  | Choice | Slides |
  |---|---|
  | 5 min | about 5 |
  | 10 min | about 8 |
  | 20 min | about 12 |
  | Not sure | about 8 |

- **Who is it for?** Class / Work / Friends and family / Other. This sets the tone.
- Language defaults to the app language. One line, "Slides in Português · Change", lets them change it.
- Optional: **"Add notes, a PDF or photos"**, using the existing attachments.

**3. Pick a look.**
- 6 themes, shown as real slide thumbnails drawn by the app (§4.3).
- Plain names: Clean, Bold, Calm, Chalkboard, School, Dark.
- Each theme is a palette, a font pair and a few layouts, defined in the pack's `themes.json`.
- Default: Clean. Then **Make my outline**.

**4. Outline review.**
- The agent answers with the outline only: titles and 2–4 bullets per slide, written to `outline.json`.
- The skill tells the agent to stop and ask with the existing question tool: **"Looks good, make the slides"** / **"Change something"**.
- The app shows the outline as a readable list card, not a code block.
- "Change something" puts the cursor in the composer: "Change: ". The person writes "add a slide about pollution", and the agent revises the outline and asks again.

**5. Generation.**
- The agent writes `deck.json`: slides, layout per slide, text, speaker notes, and image slots filled only from the person's attachments.
- Then it runs the pack's `node build.js deck.json`, which makes `deck.pptx` with `pptxgenjs`.
- The existing live line shows progress. If the agent kept a todo list: "Making slides · 4 of 8".

**6. Preview on the phone.**
- A **deck card** appears in the conversation: title, first-slide thumbnail, "8 slides".
- Tapping it opens the **slide viewer**: swipe between slides, pinch to zoom, a "Notes" toggle, landscape full screen. See §4.3 for how it renders.

**7. Edit by chat.**
- Long-press a slide › **"Change this slide"** puts "Slide 3: " in the composer.
- Or the person just types "make slide 3 shorter" or "use the Bold look".
- The agent edits `deck.json` and rebuilds, and the card updates in place (a new version; "Undo" goes back one version).
- Teaching: the first time the viewer opens, the tip "Hold a slide to change it" appears (teaching.md rules).

**8. Export and share** (buttons in the viewer and on the card):
- **"Share as PowerPoint"** (.pptx) and **"Share as PDF"** open the Android share sheet (WhatsApp, Gmail, Drive, WPS).
- **"Save to Downloads"** saves through MediaStore.
- The file names come from the title: `Water cycle.pptx`.
- PDF: see §4.3.

**9. Find it later.**
- Decks live in `My files › Presentations` on This phone.
- **"Your presentations"** lists them on the "Make a presentation" item in What it can do, and on the Files page.

### 4.2 What runs where, and phone limits

| Piece | Runs on | Needs | Notes |
|---|---|---|---|
| Questions, theme picker, outline card, slide viewer, PDF export | the app | nothing new on the host | Works offline once the deck exists |
| Outline and slide text | the agent's model (cloud) | internet | The only part that needs a model |
| `build.js` → `.pptx` | This phone's Ubuntu (Node already installed) | `pptxgenjs` pinned in the pack (about 3 MB plus a few small deps; **measure**) | Measure: time for 8 slides on a 4 GB Redmi (Guess: a few seconds). RAM well under the setup's 1800 MB floor (Guess). |
| Real-PowerPoint render check (LibreOffice → PDF → images) | **not on the phone** by default | LibreOffice headless: hundreds of MB with dependencies (Guess; measure in noble arm64) | Optional "Exact preview" add-on later, or on a computer/VPS where Anthropic's full pptx skill can run (D2) |

**Phone constraints:**
- **Storage:** the pack is about 10 MB and each deck is under 5 MB without photos (Guess). We should not add LibreOffice or Chromium (several hundred MB each) by default.
- **RAM:** the Node build is short-lived. The model runs in the cloud.
- **Offline:** viewing, editing the order, and exporting PDF/PPTX of an existing deck all work offline. Making or changing text needs the model.
- **Background:** a deck takes a few minutes at most. The Android 15 6-hour `dataSync` cap is not a concern.

### 4.3 How the phone shows a deck (decision D2)

There is no built-in Android renderer for `.pptx`. Today the app renders PDF (`PdfRenderer` in an isolated service, `native/androidApp/.../LocalPdfService.kt`). The options:

| Option | How | Good | Bad |
|---|---|---|---|
| **A. One source, drawn by the app (recommended)** | `deck.json` + `themes.json` are the source. The app draws slides with a new kit part (`KitSlide`, kit-only rule). PDF is made **on the phone** with Android's `PdfDocument` from the same drawing. `.pptx` is made by `pptxgenjs` from the same json. | Instant previews; offline; PDF exactly matches what you saw; Arabic shaping is done by Android; no heavy host packages | Two renderers (app and PowerPoint) can differ a little: line breaks and font substitution. The theme set must stay small and tested in both. |
| B. Real render on the host | `.pptx` → LibreOffice → PDF → existing PDF viewer | What you see is close to PowerPoint | Hundreds of MB on the phone; slow under proot; Arabic font setup needed |
| C. HTML slides | Marp/reveal HTML in a WebView, print to PDF | Rich layouts | WebView is a new surface (common-file-previews avoided it); PPTX from Marp needs Chromium and is image-only |

**Recommendation: A**, plus B as an optional "Exact preview" for people on a computer or VPS.

**The A risk to test first:** the same `deck.json` drawn by the app and by PowerPoint/WPS/Google Slides, side by side, in English and Arabic, on 3 themes. This is the owner's "visual match" method: judged by eye, no pixel scores.

### 4.4 Which agent and model

- **Default: OpenCode on This phone.** It is what most users have, and it works with free models (the app already labels "Free model").
  - The pack's agent `slides` uses whatever model the person picked.
  - If that is a free model, the questions sheet says one line: "Free models can be slow or hit a daily limit." When a limit hits, the existing quota-switch flow ([b169-quota-switch.md](b169-quota-switch.md)) offers another model.
- **Claude Code on This phone (through Paseo):** it reads the same skill folder. Better outlines (Guess), but it needs a Claude plan.
- **Feasibility check before any app work (AGENTS.md rule 2):**
  - Install the skill and `build.js` by hand on the emulator's This phone.
  - Run 5 real topics (en, pt, es, ar) through the default free model and one paid model.
  - Judge the decks by eye.
  - If the free model can't follow the "outline, then stop and ask" rule reliably, change the skill (shorter rules, a fixed json schema with examples) before building any UI.

---

## 5. Customisation for regular people

### 5.1 Proposal: "What you use it for", not a "simple mode"

[settings-scope §11](../design/settings-scope-2026-10-12.md) already decided against a global Advanced switch: it hides warnings and saved differences, and Home Assistant's version failed. The proposal below keeps that rule. It changes **defaults and words**. It never hides something the person needs to be safe or to find a feature.

**The setting.** Settings › This app › **What you use it for**, with two options:
- **Everyday help:** writing, school, work files.
- **Coding:** apps, websites, scripts.
- Both can be on. Survey Q1 sets it, and the person can change it any time. The default for people who skip: both on (Guess).

**What changes with Everyday help on (and Coding off):**

| Place | Today | Everyday help |
|---|---|---|
| New conversation question | "What would you like to build?" | "What do you need help with?" |
| Starters with no project | List this folder · Find a bug | Up to 4 of: Make a presentation, Write my CV, Summarise a PDF, Write a post… (from Q1/Q3) |
| Where new chats start | Projects root | **My files** (a plain folder on This phone; Files shows it first) |
| Agent line on new conversation | "Claude Code · on this phone ⌄" | Same line. The sheet adds each agent's plain card line from agent-personas.md ("Any model, free ones too"). |
| Permission card | Command first | Plain sentence first, exact command right under it (always visible) |
| Settings › AI | Model, Providers, Usage, Tools, Agents | **What it can do** first, then the same rows |
| Home chips | Git / Changes chips when a project has them | Unchanged: they are already contextual and simply don't show without a repo |

**What does *not* change:**
- No screen is removed.
- Search finds everything.
- Warnings, overrides and the exact commands stay visible.
- Terminal, Git, Tools and Agents stay reachable where they are today.

This keeps settings-scope SC6/SC20 and the "no new UI levels" rule. Nothing gets a new bar or row level, and the starters reuse the start area and chip row.

### 5.2 Words list (for everyday surfaces)

| Today | Everyday wording | Notes |
|---|---|---|
| skill / plugin / MCP | (none): the verb phrase itself ("Make a presentation") | Developer pages keep the real words |
| server | "This phone" / the computer's name | Already the direction in settings-scope |
| project | "folder" | Only on everyday surfaces |
| agent | the brand name alone (OpenCode, Claude Code) | "Agents" page title stays |
| model | "Model", with a plain tag: Free · Fast · Smart | Plain model name only (vision decision 2026-10-21) |
| session | conversation | Already |
| Errors | One plain sentence and a next step; technical text under Details | Owner rule |

All new copy goes through `:ui:words` with en, ar, es and pt. Spanish and Portuguese are 12 of our 35 users. Today the app ships en and ar, so es and pt are a new localisation job (decision D6).

---

## 6. Phasing, risks and decisions

### 6.1 Phases

| Phase | What | Ships with |
|---|---|---|
| **0. Measure** (small, low risk) | Fix `screen_view.screen`; add the `survey_answer`, `starter_used`, `pack_*` and `file_shared` events to the fixed list and PRIVACY.md; Q1 at the setup wait and once on home; store `oc.survey.v1` | **Native release**, if the release owner agrees it doesn't risk the quality bar. Otherwise the first update. |
| **0.5 Feasibility** (no app code) | Hand-install the presentation skill + `build.js` on the emulator's This phone; run the §4.4 test; draw the A-option comparison (§4.3) | In parallel now; it doesn't touch the app |
| **1. Presentations** | Pack format, the bundled catalog, one-tap install on This phone, questions sheet, theme picker, outline card, `KitSlide` viewer, PDF/PPTX share, My files, everyday starters, "What you use it for" | **First update after the native release** (recommended) |
| **2. More packs** | The top 2–3 from survey Q3 plus usage (likely CV, Summarise a PDF, Spreadsheet); follow-up Q2–Q5; es/pt copy | +2 to 4 weeks after phase 1 (Guess) |
| **3. Beyond This phone** | Install on a computer/VPS once a write contract is verified; optional "Exact preview" with LibreOffice; signed remote catalog; Codex/Gemini support if their skill folders are verified | Later |

### 6.2 Risks

1. **Free-model quality and daily limits:** decks are only as good as the model. The mitigations are the feasibility test, the outline stop, and the existing quota switch.
2. **Preview vs PowerPoint mismatch** (option A): this is the biggest UX risk. Mitigations: few themes, safe fonts, and the side-by-side test before the build.
3. **Arabic and RTL in .pptx:** `pptxgenjs` lists RTL support. How WPS and PowerPoint on phones show it is unmeasured.
4. **The funnel:** only about a third of installs reach a reply. A presentation pack can't help someone who never finishes setup. **Setup reliability (the `essentials` failures) may matter more than any pack.** It should run as a separate, parallel fix.
5. **Supply chain:** packs are code that agents run. Mitigations: a bundled catalog (D4 A), pinned hashes, and a self-test.
6. **Small sample:** 32 users, so the survey gives directions, not truths.
7. **Words drift:** new everyday words must not contradict the settings-scope labels. The goldfish review should check this.
8. **Scope creep against the native quality bar:** the owner's rule is polish over scope. Phase 1 should come after the native release.

### 6.3 Open questions

- Which consent covers survey answers (D3), and where a non-Firebase sink would live.
- Does OC2 offer a file-write call we can use to install packs on a remote host, or must it be an approved shell run?
- Do Codex CLI, Gemini CLI and Antigravity read `~/.agents/skills` or similar? (Not checked.)
- Does LibreOffice headless run acceptably under proot on a 4 GB phone? (Only matters if D2 = B.)
- How fast is `build.js` on a low-end phone? (Measure in phase 0.5.)
- Should "My files" be a folder inside Ubuntu (agents can reach it), or app storage synced in (Android can reach it)? Recommended: inside Ubuntu, with export through share and save.

### 6.4 Decisions for the owner

Each has a recommendation in **bold**.

- **D1. When do presentations ship?**
  - A: in the native release.
  - **B: the first update after the native release, with phase 0.5 feasibility starting now.**
  - C: wait for survey results first.
- **D2. How the phone shows a deck.**
  - **A: one source (`deck.json`) drawn by the app, PDF made on the phone, PPTX by `pptxgenjs`.**
  - B: LibreOffice on This phone for an exact preview.
  - C: HTML slides in a WebView.
- **D3. Survey answers and consent.**
  - **A: send choice ids only when "Send reports to developer" is on (now); move to B once a sink exists.**
  - B: the survey's own Send button is a one-time consent, through a second anonymous sink.
  - C: no telemetry; email only.
  - Free text never goes to analytics in any option.
- **D4. Where packs come from and how they update.**
  - **A: a catalog bundled in the app, updated with app releases.**
  - B: a remote signed catalog.
  - C: open third-party catalogs.
- **D5. Regular vs developer.**
  - A: nothing new; contextual starters only.
  - **B: a "What you use it for" setting (Everyday help / Coding) that changes defaults and words, hides nothing.**
  - C: a full simple mode that hides developer screens. This conflicts with settings-scope decision 10.
- **D6. Languages for the new copy.**
  - A: en + ar only (today's set).
  - **B: en, ar, es, pt for everything in phases 0–1.**
  - C: also add id, ru, vi.
- **D7. The second pack.**
  - A: CV and cover letter.
  - B: Summarise a PDF.
  - C: Spreadsheets.
  - **D: let the survey decide; pick from the top Q3 choice backed by usage.**

---

## Sources

- GA4 property 563181914 via `/home/eslam/Storage2/tools/ga.sh` and custom `runReport` calls, 2026-11-09.
- Chatterji et al., *How People Use ChatGPT*, NBER WP 34255 (2025): https://www.nber.org/papers/w34255
- Pew Research Center, "34% of U.S. adults have used ChatGPT" (2025): https://www.pewresearch.org/short-reads/2025/06/25/34-of-us-adults-have-used-chatgpt-about-double-the-share-in-2023/
- Anthropic Economic Index, September 2025: https://www.anthropic.com/research/anthropic-economic-index-september-2025-report ; secondary: https://dataconomy.com/2025/09/17/anthropic-economic-index-reveals-uneven-claude-ai-adoption
- OpenCode docs: skills https://opencode.ai/docs/skills/ , agents https://opencode.ai/docs/agents/ , commands https://opencode.ai/docs/commands/
- Claude Code skills: https://code.claude.com/docs/en/skills
- Anthropic skills repo and the pptx licence: https://github.com/anthropics/skills , https://github.com/anthropics/skills/blob/main/skills/pptx/LICENSE.txt (commit 2c5958eca, 2026-11-06), https://github.com/anthropics/skills/blob/main/skills/pptx/SKILL.md
- Marp: https://marp.app/docs ; npm registry entries for `pptxgenjs`, `reveal.js` and `@marp-team/marp-cli`; PyPI `python-pptx`.
- Repo: `native/engine/runtime/.../setup/registry/PhoneSetupScripts.kt`, `.../components/RemovableComponents.kt`, `.../setup/SetupPreflight.kt`, `native/engine/composer/.../phrases/Starters.kt`, `native/engine/analytics/...` (`Consent.kt`, `Sinks.kt`, `product/ProductEvents.kt`, `product/TelemetryAnswers.kt`), `native/androidApp/.../telemetry/FirebaseTelemetryVendor.kt`, `native/androidApp/.../LocalPdfService.kt`.
