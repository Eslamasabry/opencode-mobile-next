# Generative UI for the native rewrite (agent cards, forms, A2UI, Remote Compose, MCP Apps)

Topic: `genui`. Written 2026-11-01. Every web source was read on 2026-11-01. The list with URLs is at the end.

Labels (same as the sibling research files):

- **[V]** Verified on 2026-11-01 from a primary source. That means official docs or release notes, Google Maven or
  Maven Central metadata, the project's own GitHub repo or releases, or the published binaries. For `androidx.a2ui` I
  downloaded the AARs and read the class files.
- **[S]** Secondary source, such as a blog, an aggregator or a news article.
- **[U]** Unverified, or my own projection. Check it in slice 0 before relying on it.

Internal evidence, all read-only:

- Area map `/home/eslam/Storage/Code/wt/native-plan/map/chat-genui.md`. Features CHAT-GENUI-20 (agent blocks in Markdown),
  47 (questions), 48 (OpenCode 2 forms) and 70–78 (agent cards).
- The Flutter source `/home/eslam/Storage/Code/wt/oc_app-batch1`:
  - design docs: `docs/design/genui-plan-2026-10-07.md`, `genui-contract-2026-10-07.md`, `genui-review-astra-2026-10-07.md`
  - wire notes: `docs/opencode2-protocol-notes.md`
  - code: `lib/domain/genui/*`, `lib/ui/widgets/form_renderer.dart`, `lib/api2/models/models_inbox.dart`
  - tests: `test/fixtures/genui/*`
- The behaviour catalog `/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl`.
- Owner memory files in `/home/eslam/.claude/projects/-home-eslam-Storage-Code-oc-app/memory/`.

---

## 0. Bottom line

1. **Keep the app's own agent-card contract (`oc-ui` `show`, card v1) as the wire format. Port it to Kotlin
   `commonMain` as pure code. Do not switch to A2UI for the rewrite.**
   - The card contract is already the product contract with agents. It is strict and bounded, and a 182 KB
     accept/reject corpus pins it (`test/fixtures/genui/validation.json`).
   - It already follows the A2UI trust model: a closed catalog, app-drawn components, and no code execution.
   - What A2UI adds is free layout (Row/Column/Tabs/Modal), a data model, functions and, in v1.0, agent-called
     renderer functions. This app does not want any of that. Free layout works against the owner's rules: kit-only,
     simple and natural, no new levels, hard max widths.
2. **A2UI is real, Google-backed and moving fast, but it is not ready to depend on.** [V]
   - The spec is v0.9.1 "current". v1.0 is a release candidate and **breaks** v0.9 catalogs and payloads.
   - Google's Jetpack Compose renderer `androidx.a2ui*` is **1.0.0-alpha02** (2026-10-21). It is **Android-only**
     (no KMP variants), accepts only protocol versions `v0.9`/`v0.9.1`, and pulls `androidx.compose.ui:ui:1.13.0-alpha03`.
   - Plan an optional A2UI adapter (a bounded A2UI subset translated into our card model) as a later slice. Build it
     only when an agent this app supports actually emits A2UI. None does today.
3. **Remote Compose (`androidx.compose.remote` 1.0.0-alpha22) is not for agent cards.** [V]
   - Its documents are binary and are made by Kotlin creation code, so a model cannot write one.
   - It draws its own pixels outside our kit.
   - It is the new engine under Jetpack Glance widgets (I/O 2026), so it belongs in the home-screen-widget plan,
     which comes later.
4. **MCP Apps (`ui://`, HTML in sandboxed iframes, spec 2026-01-26 stable) is out of scope.** [V]
   - It is the opposite of our "no agent HTML/script" rule.
   - On Android it would need a hand-built WebView sandbox.
   - The servers this app talks to do not pass it through: OpenCode 2's MCP client flattens `resource`/`resource_link`
     results to text [V source]. The Claude Code CLI and Codex render no `ui://` views [U].
5. **The structured-input UI the servers really send is forms, questions and elicitation.** Make that one native
   form engine.
   - OpenCode 2 `Form.Info`, which also carries MCP elicitation.
   - ACP v2 elicitation, which is based on MCP 2026-07-28.
   - Codex `mcpServer/elicitation/request` plus its `requestUserInput` tool.
   - Claude Code's AskUserQuestion via Paseo.
   - Today the Flutter app has two separate form renderers (`form_renderer.dart`, 1,351 lines; `agent_card_form.dart`,
     341 lines). The rewrite should have one `FormSpec` engine with an adapter for each source. This is a "judge by
     what it removes" win.
6. **Streaming:** keep "only a completed, executed tool call becomes a card".
   - Use OpenCode 2's `session.tool.input.started` (exact name `oc-ui_show`) to show a non-interactive card skeleton
     at once.
   - Cross-fade into the validated card in 150–250 ms. Use no motion under reduced motion.
   - Never parse partial tool-input JSON into anything a person can act on.
7. **Gap found while reading the code:** OpenCode 2 form fields have `hidden: boolean` (in 2.1.1, the version we pin,
   and in 2.2.5) [V source]. The Flutter app's `Api2FormField.fromJson` ignores it. The native form engine must
   honour it: do not show the field, and send its default unless an answer is supplied.
8. **Slice order:** permissions, questions and forms ship with the first chat slices, because they are server-native
   and used daily. Agent cards come after them as their own slice. They are off by default, qualified only for
   Claude Code, and need the proot helper and its installer.

---

## 1. What "generative UI" means in this app today (the scope to port)

The Flutter app has five ways an agent drives UI. None of them runs agent code, and all of them are drawn from the kit.

| # | Channel | Source | Map id | Port stance |
|---|---|---|---|---|
| 1 | **Agent cards**: the `oc-ui` MCP server with tools `show` and `find_connectors`. Card v1 has title, 0–40 body nodes of 10 types, at most one ask (choice, form, confirm or photo), and an optional connector suggestion. | An app-owned Node stdio helper in the in-app Ubuntu, registered with Claude Code (qualified) and OpenCode 2 (registered, not qualified) | CHAT-GENUI-70…78 | KEEP the contract, PORT the code |
| 2 | **OpenCode 2 forms** (`Form.Info`: string, number, integer, boolean, multiselect and external fields, `when` conditions). MCP elicitation also arrives as forms with `sessionID: "global"`. | OC2 server events `form.created/replied/cancelled` plus REST | CHAT-GENUI-48 | KEEP |
| 3 | **Questions** (Paseo/Claude Code AskUserQuestion; OC2 `question` tool → forms with `metadata.kind = "question"` [V source]) | Paseo, OC2 | CHAT-GENUI-47 | KEEP |
| 4 | **Agent blocks in Markdown** (```` ```choices ````, ```` ```checklist ````, ```` ```command ````), built only for closed fences | any model text | CHAT-GENUI-20 | KEEP (cheap, the owner values it) |
| 5 | **Task lists** (`MobileTaskView` from todos) | tool parts | CHAT-GENUI-25 | KEEP |

Facts about the Flutter implementation that the rewrite must carry over (details are in the map):

- **Size of the card code:** domain about 1,500 lines, state about 2,300, UI about 1,700, Linux helper 690. The Dart
  validator has a JS twin that ships inside the helper, and one shared corpus proves the two agree. A Kotlin port makes
  a third implementation on the same corpus.
- **Admission:** only exact tool names count (`mcp__oc-ui__show` for Claude Code, `oc-ui_show` for OpenCode 2). The
  part must be assistant-owned and non-synthetic, with status `completed`, `executed == true` and complete structured
  input. A streamed input preview is never parsed.
- **Answers** are a normal user message: a one-line `[oc-ui answer <id>] <summary>` followed by a compact JSON envelope
  `{v, cardId, callId, value}`. An answer is never a permission grant.
- **Receipt state machine** (waiting, answered, passed over, report, unknown), a 3 s Undo hold, and send-exactly-once
  journaling. Recovery is bounded and never wakes a sleeping agent: at most 20 sessions per pass and 2 pages each.
- The feature is **off by default** (`oc.genui.enabled.<profileId>`).
- **The Flutter app does not use Flutter's GenUI SDK** (`genui` is not in `pubspec.yaml`) [V]. Nothing from that SDK
  needs migrating.
- **Catalog coverage:** about 31 behaviour rows in the catalog cite card, question and form test files, about 209
  source references in total. The biggest sources are `gen_ui_install_test` (39), `gen_ui_state_test` (33),
  `gen_ui_controller_test` (22), `paseo_native_questions_test` (19) and `gen_ui_recovery_coverage_test` (16).

---

## 2. Version snapshot (as of 2026-11-01)

| Thing | Latest | Date | Status | Source |
|---|---|---|---|---|
| A2UI spec | **v0.9.1** "Current"; **v1.0** "Candidate" (formerly draft 0.10) | roadmap page "last updated June 2026"; v1.0 spec commits up to 2026-10-30 | v1.0 is breaking versus v0.9 | a2ui.org roadmap [V]; `google/A2UI` `specification/v1_0/README.md` [V] |
| `androidx.a2ui:a2ui-model`, `a2ui-engine` | **1.0.0-alpha02** | 2026-10-21 (alpha01 2026-09-23) | alpha, Android-only AAR, minSdk 24 | release notes + Google Maven group index + `.module` [V] |
| `androidx.a2ui.compose:compose-runtime`, `compose-ui`, `compose-ui-testing` | **1.0.0-alpha02** | 2026-10-21 | alpha. Depends on `androidx.compose.ui:ui:1.13.0-alpha03` and `runtime:1.13.0-alpha03`; compileSdk 37.1 | release notes + `.module` [V] |
| `androidx.compose.remote:*` (Remote Compose) | **1.0.0-alpha22** | 2026-10-21 (alpha01 2025-12-17) | alpha, 12 artifacts incl. `remote-player-compose`, `remote-player-view`, `remote-creation-*`, `remote-testing` | release notes + Google Maven group index [V] |
| `androidx.compose.remote.foundation:foundation` | 1.0.0-alpha05 | 2026-10-21 | alpha | release notes [V] |
| Jetpack Glance | 1.2.0 stable; 1.3.0-alpha02 | 2026-08-26; 2026-07-01 | 1.3 alphas add Remote Compose snap scrolling and Wear widgets | Glance release notes [V] |
| Flutter `genui` (labs.flutter.dev) | 0.10.4 | 2026-09-29 | 0.x | pub.dev API [V] |
| Dart `a2ui_core` | 0.4.0 | 2026-10-28 | 0.x | pub.dev API [V] |
| A2UI Python agent SDK / core | a2ui-agent-sdk 0.9.0 / a2ui-core 0.4.0 | 2026-10-17 | 0.x | `google/A2UI` releases [V] |
| `uny/a2ui-compose` (community, KMP) | v0.3.0 | 2026-09-26 | 7 stars. Targets A2UI v1.0 | GitHub API + README [V] |
| `mikepenz/A2CUI` (community, KMP, A2UI v0.9 + AG-UI) | v0.1.0-a02 | 2026-04-24 (last push 2026-09-10) | "EXPERIMENTAL!", 3 stars | GitHub API + README [V] |
| MCP Apps (`io.modelcontextprotocol/ui`) | spec **2026-01-26** Stable; SDK `@modelcontextprotocol/ext-apps` **v2.0.4** | 2026-10-27 | stable spec, HTML only | ext-apps repo + spec file [V] |
| MCP core spec | **2026-07-28** | 2026-07-28 | stable (tasks moved to an extension) | GitHub release page [S] |
| ACP | v1.14.0; schema v1.28.0; schema v2.0.0-alpha.11 (v2 has elicitation form + url) | 2026-10-30 | v2 alpha | GitHub releases + `docs/protocol/v2/elicitation.mdx` [V] |
| OpenCode | **v2.2.5** (we pin **2.1.1**) | 2026-10-30 | stable | GitHub releases [V]; `lib/termux/bridge.dart` [V] |
| Cash App Redwood | 0.19.0 | 2025-11-06 (last push 2026-01-09) | looks stalled | GitHub API [V] |
| Cash App Zipline | 1.28.0 | 2026-09-29 | active | GitHub API [V] |
| DivKit (Yandex) | 33.9.0 | 2026-10-27 | active, has `com.yandex.div:compose` | GitHub API [V]; Maven Central listing [S] |
| vercel-labs/json-render | v0.21.3 | 2026-10-24 | active, JS/TS only (has a React Native renderer, no Kotlin) | GitHub API + README [V] |
| AG-UI | rolling `release/2026-10-30` | 2026-10-30 | active. Kotlin SDK is community (`com.agui:kotlin-client` 0.2.1 [S]) | GitHub API [V]; CopilotKit docs [S] |
| MCP-UI (pre-MCP-Apps) | client v7.1.1 | 2026-05-09 | superseded by MCP Apps | GitHub API [V] |

---

## 3. A2UI (Google): protocol and renderers

### 3.1 What it is [V]

A2UI is "a framework-agnostic standard for declaring UI intent" (Google Developers Blog, v0.9 post, 2026-04-17).
Its parts:

- **Wire format.** The agent sends a stream of JSON envelopes. Each one carries `version` and exactly one message key.
  - v0.9 has `createSurface`, `updateComponents`, `updateDataModel` and `deleteSurface`.
  - v1.0 adds `callRendererFunction` and `agentFunctionResponse`.
  - The renderer sends back `action`, `error` and, in v1.0, `callAgentFunction` and `rendererFunctionResponse`.
  - Framing is JSONL, WebSocket frames or SSE events, and delivery must be reliable and in order.
- **Component model.** Components form a **flat adjacency list**. Each has an `id` and a `component` type, and children
  are referenced by id. A component with id `root` must exist. Updates that arrive before `root` are buffered, and
  invalid references are skipped. This is what lets a surface build up progressively.
- **Data model.** Data binding uses JSON Pointer (RFC 6901), with relative paths inside list templates.
  - Two-way binding of inputs stays local to the device. Data goes to the agent only through an `action`, or when
    `sendDataModel` is set.
  - Missing data resolves to `undefined` during streaming, and renderers must cope with that.
- **Catalogs.** A `catalogId` names an agreed component and function vocabulary.
  - The renderer advertises `supportedCatalogIds` (and inline catalogs) in transport metadata.
  - v0.9 renamed "Standard" to **Basic**, which has 18 components: Text, Image, Icon, Video, AudioPlayer, Row, Column,
    List, Card, Tabs, Divider, Modal, Button, CheckBox, TextField, DateTimeInput, ChoicePicker and Slider.
  - Basic functions are `required`, `regex`, `length`, `numeric`, `email`, `formatString`, `formatNumber`,
    `formatCurrency`, `formatDate`, `pluralize`, `openUrl`, `and`, `or` and `not`.
- **Trust model.** Functions are called by name, never as code. The catalog is the trust boundary. v1.0 adds runtime
  checks:
  - `allowedCallers` takes `rendererOnly`, `agentOnly` or `rendererOrAgent`. `rendererOnly` is the default.
  - `requiresUserActivation` is set on `openUrl`, so `openUrl` runs only from a user gesture.
  - An unknown or disallowed call is rejected with `INVALID_FUNCTION_CALL`.
  - Catalogs never fetch a `$ref` over the network.
  - Accessibility attributes (`label`, `description`, `live`, `hidden`) are required plumbing.

### 3.2 v0.9 → v1.0 (candidate) changes that matter [V, `specification/v1_0/docs/evolution_guide.md`]

- **Breaking.** Data bindings and calls move to `@path` / `@call` / `@index` keys. `updateDataModel.value` is now
  required, and `null` deletes. v0.9 catalogs cannot be used with v1.0 runtimes. The MIME type becomes
  `application/a2ui+json`. "client"/"server" are renamed "renderer"/"agent".
- **New.**
  - Mixable catalogs per surface, plus `allowedParents`/`allowedChildren` composition rules with a reserved `Surface`
    root.
  - `createSurface` can carry the whole UI and the data model in one message.
  - `Action.event.userMessage`: a human-readable string sent with the action.
  - `ValidationResult` checks.
  - Catalog `instructions` (prompt guidance for the model).
  - UAX #31 identifier rules.
  - `theme`/`primaryColor` removed: "separate layout from branding".
- **Agent-initiated renderer functions** (`callRendererFunction`) are new attack surface. A safe renderer registers
  only `rendererOnly` functions.

### 3.3 A2UI over MCP [V, `docs/public/guides/a2ui_over_mcp.md`]

- Templates are MCP resources (`a2ui://…`, `application/a2ui+json`).
- Tools point at their template with `_meta.ui.resourceUri` (the same key MCP Apps uses) and return `updateDataModel` in
  an `EmbeddedResource`, plus a text fallback.
- Catalogs are negotiated at MCP `initialize` or in per-message metadata.
- **This assumes the UI host is the MCP client.** In this app, the **agent** (Claude Code or OpenCode) is the MCP
  client of `oc-ui`. The phone app only sees tool parts in the transcript. So the A2UI-over-MCP handshake and
  `resources/read` cannot run in the app. We would still need our own helper server and transcript admission. The
  transport part of A2UI saves us nothing.

### 3.4 Renderers

**Google's Jetpack Compose renderer (`androidx.a2ui`, `androidx.a2ui.compose`).** I downloaded the 1.0.0-alpha02 AARs
from Google Maven and inspected them [V]:

- **Not KMP.** Each artifact publishes only Android `release` variants. There is no `jvm`, `iosArm64` or `desktop`
  variant. It cannot sit in `commonMain`.
- **Accepts protocol versions `v0.9` and `v0.9.1` only.** `A2uiJsonMessageParser` has `SUPPORTED_VERSIONS` and rejects
  others with "Unsupported protocol version". The basic catalog id is `https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json`.
  So v1.0 payloads will not parse until a later alpha.
- **Design-system agnostic.** `A2uiBasicCatalogV1` takes one implementation per component interface
  (`A2uiBasicCatalogV1.Button`, `.Text`, …). Each component implements
  `TypedContent(scope, …typed props…, accessibility, checks, modifier)`, so **we would supply kit composables**.
  - The release notes mention `materialA2uiBasicCatalogV1` / `MaterialA2uiBasicCatalogV1Defaults`, but I did not find
    them in the four published AARs. They may live in an artifact that is not published yet [U].
  - `compose-ui` has no `material3` dependency.
- **Entry points:**
  - `A2uiMessageProcessor(catalogs, actionInterceptors)` has `activeSurfaces: StateFlow<List<A2uiSurfaceModel>>`,
    `outboundEvents: Flow<A2uiClientToServerMessage>`, `processMessage(...)` and `processError(...)`.
  - `A2uiComponent(model, modifier)`.
  - `A2uiCatalog(id, components, functions, schema, …)`.
  - `A2uiReadinessEvaluator`: a component can say it is not ready until its required props resolve. This helps avoid
    half-drawn frames while streaming.
  - Reactivity is Compose snapshot state (`SnapshotStateSparseList`).
- **`openUrl` default is `IntentUrlOpener`.** Adopting the library would require overriding `Functions.openUrl` so
  links go through `openExternalLink`. Otherwise this is a security-invariant break (AGENTS.md).
- **No input bounds.** I found no size or depth limit constants in the parser classes (string scan) [V for absence in
  strings; behaviour U]. The host must bound input before handing it over.
- **Dependency drag.** `compose-ui` requires `androidx.compose.ui:ui:1.13.0-alpha03`.
  - With CMP 1.12.2 (Jetpack 1.12.1), Gradle would upgrade the Android Compose UI to a 1.13 alpha.
  - With CMP 1.13.0-beta01 (Jetpack 1.13.0-beta01) it resolves cleanly [U: check `dependencyInsight`].
  - It also needs compileSdk 37.1, and `a2ui-model` pulls `androidx.core:core-i18n`.

**Community KMP renderers.**

- **`uny/a2ui-compose`** 0.3.0 (Apache-2.0, Maven Central `dev.ynagai.a2ui`) [V repo]:
  - Targets v1.0 only.
  - Ships core, compose and material3 modules for Android, JVM, iOS, macOS and web.
  - Bounds payload size and depth, reduces Markdown links to labels, and does no image fetching in core.
  - It is one person's project with 7 stars, and it says Compose UI tests do not run on Android.
- **`mikepenz/A2CUI`** 0.1.0-a02 [V repo]:
  - A2UI v0.9 over AG-UI, with a 15-component Material3 catalog, a KSP codegen for custom components and an
    Adaptive Cards mapper.
  - It says itself "EXPERIMENTAL!". The README says its coordinates are "planned, not yet published".
  - Useful as reading, not as a dependency.
- The README of `uny/a2ui-compose` claims the official web renderers (`@a2ui/react` 0.10.2, `@a2ui/angular` 0.10.5,
  `@a2ui/lit` 0.10.3) are already on the v1.0 line [S, not checked].

**Other renderers.**

- Flutter `genui` 0.10.4 (official, "Stable" on the roadmap, 0.x on pub.dev).
- Lit, Angular and React (stable). Lynx (stable). SwiftUI (alpha).
- The roadmap page still lists Jetpack Compose as "Planned". The page predates the 2026-09-23 androidx alpha.

### 3.5 Fit for this app

| Question | Answer |
|---|---|
| Does an agent we support emit A2UI? | **No.** Searching the OpenCode code for `a2ui` gives no hits [V GitHub code search, default branch]. Claude Code and Codex have no A2UI support that I found [U]. A2UI's own list of agent integrations marks Claude Agent SDK and OpenAI Agent SDK "Proposed" [V roadmap]. |
| Would A2UI replace `oc-ui` cards? | Only by translation. We would still need our helper MCP server, exact-name admission, transcript-based answers and receipts, and bounded recovery. A2UI defines none of these, because it assumes a live action channel to the agent. Ours is the conversation itself. |
| Does A2UI's layout freedom help? | It hurts. Agent-chosen Row/Column/Tabs/Modal trees break "kit-only", "no new levels", "obvious options as rows" and "component hard max widths". Our card is a fixed shape: eyebrow, title, a vertical list of nodes and one ask slot. The model chooses content, never layout. |
| Can it be KMP? | Not with `androidx.a2ui` today (Android-only). Only the small community libraries are KMP. |
| Stability | Spec v1.0 is a candidate that breaks v0.9. The androidx renderer is alpha02 on v0.9. Too early to anchor a rewrite on. |
| What is worth copying | Vocabulary and ideas, not the dependency: closed catalogs with `instructions` for the model; `userMessage` on actions (it matches our one-line answer summary); `requiresUserActivation` for anything that leaves the app; readiness gating; required accessibility attributes; UAX #31 identifiers; never fetching `$ref`. |

---

## 4. Remote Compose (`androidx.compose.remote`)

**What it is [V].**

- The release notes call it "a framework to create UI for remote surfaces". Creation libraries (`remote-creation-*`,
  including a `-jvm` artifact) capture Compose-like code into a **document**. Players (`remote-player-compose`,
  `remote-player-view`, `remote-player-core`) draw that document.
- Alpha21 added DEFLATE-compressed documents. Alpha07/08 added RTL reversal, `LayoutDirection` awareness and
  non-linear font scaling. Alpha14 added custom components for the Compose player. Default minSdk is 24 (alpha17).
- It runs touch, animation and expressions (`RemoteState`, `RemoteFloat`, `animateRemoteFloat`,
  `RemoteTimeVariables` in alpha22) inside the player, so the host process does not have to wake.
- At Google I/O 2026 (May 2026) Google said it is the engine for Glance widgets on phone and Wear, replacing the
  RemoteViews/XML and ProtoLayout split. Android 16+ gets the new features; older versions get static fallbacks [S:
  Android Authority 2026-05-22]. Glance 1.3.0-alpha01 adds "snap scrolling support when remote compose is used" [V].

**Fit:**

- **Agent cards: no.**
  - A model cannot write a binary document. Something on the server would have to run Kotlin creation code, and our
    in-app Ubuntu has no JVM in the agent path.
  - The document would carry its own drawing, so it would bypass the kit: kit styles, the "Style" schools, accent and
    text-size caps. It would also defeat our strict JSON validation corpus.
  - Every API is still alpha (22 alphas in 10 months, with renames such as `RemoteBitmap` → `RemoteImageBitmap` in
    alpha13).
- **Home-screen widgets: likely yes, later.** This belongs in the widgets research, not here. The note for that plan:
  Glance 1.3 is alpha, and the owner's ZTE launcher has widget quirks (`zte-launcher-widget-traps.md`).

---

## 5. Other server-driven UI options for Compose

| Option | What | Status | Fit here |
|---|---|---|---|
| **DivKit** (Yandex) | Mature JSON SDUI with layouts, actions, variables and expressions. Android (View + `com.yandex.div:compose`), iOS, web and Flutter. | 33.9.0, 2026-10-27, very active [V]. License shows as `NOASSERTION` on GitHub's API [V]. Check the exact license before any use [U]. | **No.** It is far bigger than our 10 node types and 4 asks. Its expression language and actions widen the attack surface, and it has its own look outside the kit. |
| **Redwood** (Cash App) | Kotlin schema → widget protocol, usually paired with Zipline (downloaded Kotlin/JS code). | 0.19.0 (2025-11-06), last push 2026-01-09: stalled [V]. A fork, Keliver 0.3.1 (2026-07-27), claims "production-hardened" [S]. | **No.** Its model is shipping *code* (Zipline). We forbid agent code. |
| **json-render** (Vercel Labs) | Zod catalogs, a flat spec, streaming JSONL of RFC 6902 JSON Patch ("SpecStream"), and many renderers including React Native. | v0.21.3, 2026-10-24, about 18.9k stars [V]. No Kotlin renderer [V package list]. | **No dependency.** Good reference for "patches over JSONL" streaming. |
| **AG-UI** (CopilotKit) | Event protocol for agent↔frontend: `TOOL_CALL_START/ARGS/END`, `STATE_SNAPSHOT/DELTA` (JSON Patch), `CUSTOM`. A2UI rides on it. | Rolling releases, latest `release/2026-10-30` [V]. The Kotlin SDK is community-made (`com.agui:kotlin-client` 0.2.1) [S]. | **No.** None of our servers speak it. Our protocol layer already has OC2, Paseo, ACP and Codex. |
| **Remote Compose** | See §4. | alpha22 | Widgets later, not cards. |
| **MCP-UI** | The pre-standard of MCP Apps. | client v7.1.1 (2026-05), superseded [V]. | No. |

Conclusion: our card vocabulary is small and stable, and it is already specified down to byte limits. A
hand-written, exhaustive `when` over a sealed node type in Kotlin is about 200–400 lines of renderer on top of kit
parts. That is less code than any SDUI dependency, with zero version risk. This is the "what does it remove" answer.

---

## 6. MCP Apps (`ui://`) and MCP elicitation

### 6.1 MCP Apps [V, `ext-apps` repo and `specification/2026-01-26/apps.mdx`]

- **Declaration.** The extension id is `io.modelcontextprotocol/ui`. Resources use the `ui://` scheme with MIME type
  `text/html;profile=mcp-app`. A tool links to its view through `_meta.ui.resourceUri`, and
  `_meta.ui.visibility` is `model` and/or `app`.
- **Rendering.** "All View content MUST be rendered in sandboxed iframes with restricted permissions." A web host must
  also put a sandbox proxy on a separate origin.
  - CSP comes from `_meta.ui.csp` (`connectDomains`, `resourceDomains`, `frameDomains`, `baseUriDomains`). If it is
    missing, the default is restrictive (`default-src 'none'`).
  - Permissions (camera, microphone, geolocation, clipboardWrite) map to iframe `allow`.
- **Bridge.** JSON-RPC over postMessage:
  - View → host: `ui/initialize`, `ui/open-link`, `ui/message`, `ui/request-display-mode`, `ui/update-model-context`,
    and passthrough `tools/call` and `resources/read`.
  - Host → view: `ui/notifications/tool-input`, `tool-input-partial` (streaming), `tool-result`, `tool-cancelled`,
    `host-context-changed`, `ui/resource-teardown`.
  - Display modes are inline, fullscreen and pip.
- **Platform.** It is HTML only. Other content types and external URLs are deferred. Mobile appears only as a
  `platform` value with `safeAreaInsets`. There is **no native or WebView guidance**.
- **Hosts listed:** ChatGPT, Claude, VS Code, Goose, Postman, MCPJam and others.

**Fit: out of scope.**

- It contradicts the card contract: "no agent HTML, script, URL loading or styling ever runs in the app".
- It would mean a WebView in the transcript. That is heavy and not native-feeling, and it lies outside the kit's theme,
  text-size caps and RTL handling.
- On Android, a WebView is not a sandboxed iframe. We would have to rebuild origin separation, CSP and message checks
  ourselves.
- The agents we host do not forward `ui://` resources to us anyway. OpenCode's `toCallToolResult` turns
  `resource_link` into its URI text and `resource` into its text [V source `packages/core/src/mcp/client.ts`].
- If a user ever needs one: offer a row "Open <app name> in browser" that goes through `openExternalLink`. Only
  consider this if a served URL exists, which MCP Apps does not provide today [U].

### 6.2 Elicitation is the UI that does reach us [V unless marked]

- **MCP 2026-07-28** elicitation has **form** mode (restricted JSON Schema, non-sensitive data only) and **URL** mode
  (out-of-band, e.g. OAuth).
- **OpenCode 2.**
  - When an elicitation handler is wired, its MCP client advertises `elicitation: {form: {}, url: {}}`
    (`packages/core/src/mcp/client.ts`). MCP elicitations become `Form.Info` with the temporary
    `sessionID: "global"` owner, per the comments in `form.ts` and `packages/server/src/api.ts`.
  - Its own `question` tool also creates forms (`metadata.kind: "question"`).
  - Its ACP adapter forwards forms as ACP elicitation only when the client advertises form elicitation. It skips forms
    with credential-like fields (`/password|passphrase|secret|token|api[_ -]?key|credential|private[_ -]?key/i`),
    `when` conditions or `external` fields. An unshown question is cancelled with a message so the model continues
    instead of hanging.
  - The Form field union in 2.1.1 and 2.2.5 is: `string` (format email|uri|date|date-time, min/maxLength, pattern,
    placeholder, default, options, custom), `number`, `integer`, `boolean`, `multiselect`, `external`. Every field also
    has `key`, `title`, `description`, `required`, **`hidden`** and `when`. `State` is pending,
    answered(answer) or cancelled(message?) [V `packages/schema/src/form.ts`].
- **ACP v2** (schema 2.0.0-alpha.11, 2026-10-30) has `capabilities.elicitation.{form,url}`. "Clients MUST clearly
  identify the Agent requesting information … For URL mode, Clients MUST display the target host and obtain consent
  before navigating to it."
- **Claude Code.** MCP elicitation since 2.1.76 (March 2026), "form fields or browser URL" [S changelog via search].
  Its AskUserQuestion tool reaches us through Paseo questions [V app code].
- **Codex.** App-server v2 `mcpServer/elicitation/request` with accept, decline or cancel. A separate
  `requestUserInput` model tool [S mirror of the openai/codex commit].

**Consequence:** the native app needs one form engine (§9.4) fed by four adapters: OC2 `Form.Info`, ACP elicitation,
Codex elicitation and card `form` asks. URL-mode items become one row naming the host. It opens through
`openExternalLink` only after the person taps it (actions name their target).

---

## 7. How agent servers expose UI artifacts (summary table)

| Server (version we pin) | Structured UI it sends | How the native app should treat it |
|---|---|---|
| **OpenCode 2** (2.1.1; latest 2.2.5) | `Form.Info` (questions, MCP elicitation, integration auth forms); permissions; `todo` tool parts; `tui.toast.show` (ignored); tool input streams as `session.tool.input.started/delta/ended`, then `tool.called` (parsed input) [V notes]. No MCP Apps, no A2UI [V code search]. | Form engine; permission card; task chip; card skeleton on `tool.input.started` for exact `oc-ui_show`. |
| **Claude Code via Paseo** (Claude Code 2.1.294 pinned) | AskUserQuestion questions, plan approval, permissions; MCP elicitation (CLI dialog; not seen through Paseo [U]); our `oc-ui` cards (qualified). | Question card or sheet; cards. |
| **Codex** (app-server) | Approvals, `mcpServer/elicitation/request`, `requestUserInput` [S]. | Approval card; form engine. |
| **ACP agents** (v1.14; v2 alpha) | Permission requests, tool-call content (content, diff, terminal) [S]; v2 elicitation form/url [V]. | Advertise `elicitation.form` (and `url` only once the host-consent row exists); form engine. |
| **Pi / Oh My Pi** | `ask_user` questions answered through a header (no MCP) [V map]. | Question card. |

---

## 8. Rendering model-generated UI safely: rules for the native app

These are the existing contract (map CHAT-GENUI-70…74), written as Kotlin-level rules, plus points from A2UI v1.0 and
MCP Apps.

1. **A closed vocabulary.** `sealed interface CardNode` and `sealed interface CardAsk` with exhaustive `when` in the
   renderer, so a new node type fails compilation until the kit has a part for it. Unknown `type`/`kind`/key →
   `Unreadable(reason)`, never a placeholder. Be stricter than A2UI's "skip invalid references" and A2CUI's
   `FallbackPlaceholder`.
2. **Check bounds before trusting anything.**
   - Compact UTF-8 ≤ 32,768 bytes, depth ≤ 8, ≤ 4,096 values.
   - Count strings in Unicode **scalar values** and reject unpaired surrogates. Cumulative string bytes ≤ 32,768.
   - The JSON arrives already decoded inside the SSE event, so measure on a compact re-encode of the `input`
     `JsonElement`.
   - Also bound the SSE frame size in `core:net`, because a hostile frame could be deeply nested before we ever see
     it. Whether kotlinx.serialization 1.12 stays safe on very deep nesting is [U]. Test it with a 10,000-level
     corpus case.
3. **No coercion, no repair.** Wrong types and explicit `null` are invalid. IDs and URLs are validated as supplied.
   Links must be absolute `https` with a host, no userinfo, no control characters and no backslash, port ≤ 65,535.
4. **Text hygiene.**
   - Strip C0/C1 controls except LF/TAB, and strip the bidi controls (U+061C, U+200E–200F, U+202A–202E, U+2066–2069).
   - **Bidi-isolate** every agent string when drawing it. In Compose, use `TextDirection.Content` per text run, or
     wrap in FSI/PDI. Code, paths and numbers stay LTR.
5. **Nothing leaves the app without a tap.**
   - Links open only through `openExternalLink` from a user gesture. This mirrors A2UI's
     `requiresUserActivation: true` on `openUrl`.
   - No remote images in cards: the contract forbids URL images.
   - Photo asks use the existing picker and size limits (1–4 images, 10 MiB each, 20 MiB total).
6. **Answers are conversation, not authority.** A confirm card never approves a permission or runs a tool. Answers are
   validated against the **original** ask when encoded and when read back. Never resend after an uncertain delivery.
7. **No credential collection.** Reject ids or labels matching `password|passwd|token|secret|apikey` (our rule). For
   forms from servers, use OpenCode's broader pattern (§6.2) to decide whether a field should go to a sensitive
   input. Never autofill from clipboard, account or phone.
8. **Plain words.** An unreadable card shows one fixed sentence, with only the stable reason enum under Details, never
   the agent's payload (the no-raw-errors rule). The helper never echoes input in MCP errors.
9. **Accessibility.**
   - Every kit part already maps to semantics. A chart carries a plain-text summary: kind, series, points, highest
     value. A2UI v1.0 makes this normative too ("Plumb Accessibility Attributes").
   - Card title, body and answer text are **reading text**, which scales fully. Eyebrow, buttons and receipt rows are
     **frame text**, capped at 1.3x.
10. **Layout is the app's.**
    - One card shape at the kit's hard max width (from the kit-v2 width table; do not invent a value).
    - The card lives in the reply, never inside a folded work line.
    - The "Needs you" state folds into the existing Conversations row (no new levels).
    - Choices are visible rows, never a dropdown. For a form `select` with many options, use rows up to a small count
      and a picker sheet above it; the threshold is the kit's call [U].

---

## 9. Recommendations for this app (decisive)

### 9.1 Module placement (fits `architecture-stacks.md` §6)

| Module | Holds |
|---|---|
| `:core:model` | `AgentCard`, `CardNode`, `CardAsk`, `CardAnswer`, `CardIdentity`, `FormSpec`, `FieldSpec` (plain, `@Immutable`) |
| `:engine:requests` (or `:engine:cards`, owner's call) | Card codec and validator, revision hash, receipt state machine (`genUiStateFor` port), answer journal, recovery and coverage loop, `FormSpec` adapters |
| `:protocol:oc2`, `:protocol:paseo`, `:protocol:acp`, `:protocol:codex` | Admission inputs: exact tool names, completed or executed parts, `tool.input.started` hints, form or elicitation events. They hand protocol-neutral facts to the engine. |
| `:ui:kit` | `KitAgentCard` (full, receipt, passedOver, unreadable, skeleton), node parts (text, keyValue, checklist, table, chart, code, diffStat, progress, callout, link), ask controls, `KitForm` (one field engine) |
| `:feature:chat:impl` | Arranges cards in the transcript and wires the answer actions |
| `:android:linux` | The `oc-ui` helper assets (`server.cjs`, JS validator twin), installer and verifier scripts, run in proot. Kotlin already owns the rootfs (`BuiltinLinux.kt`). |

### 9.2 Tests first (owner's plan)

- **Port the corpus.**
  - Copy `test/fixtures/genui/validation.json` (182 KB) and the three captured runtime fixtures
    (`runtime_paseo_claude_tool.json`, `runtime_opencode1_tool.json`, `runtime_paseo_paging.json`) into
    `:engine:requests` `commonTest` resources.
  - Write the corpus runner **before** the validator. It asserts that accept/reject and the normalised output equal the
    corpus.
  - Keep a JVM test that also runs the JS twin under Node against the same corpus. That keeps the helper honest after
    Dart is gone.
- **Order the logic ports** by the catalog's ~31 genui behaviour rows:
  - codec and types (`gen_ui_codec_test`, `gen_ui_types_test`)
  - state machine (`gen_ui_state_test` 33)
  - controller and journal (`gen_ui_controller_test` 22)
  - recovery and coverage (`gen_ui_recovery_coverage_test` 16, `gen_ui_history_test` 10, `paseo_genui_recovery_snapshot_test` 7)
  - show permission (`gen_ui_show_permission_test` 13)
  - helper server (`gen_ui_server_test` 13)
  - installer (`gen_ui_install_test` 39)
  - questions (`paseo_native_questions_test` 19)
  - forms (`form_request_test`)
- Widget tests (`agent_card_view_test`, `agent_card_chat_test`, `form_renderer_test`, `kit_chart_test`) come later, as
  the owner said. When they do, use Compose UI tests or Roborazzi.

### 9.3 Slices

1. **Requests v1** (with the first chat slice): permission card, question card or sheet, the `FormSpec` engine with the
   OC2 `Form.Info` adapter including `hidden` and `when`, and the URL-field row. Finish line: answer an OC2 form, a
   Paseo question and a permission from chat **and** from the Conversations row and the notification.
2. **Agent blocks in Markdown** (```` ```choices ````/checklist/command): part of the Markdown port (see
   `rendering-chat.md`).
3. **Agent cards v1:** validator (corpus-green) → card UI (kit) → answers, Undo, journal → recovery and coverage → the
   proot helper install, verify and disable flow → the Conversations-row slot. Finish line = the Flutter contract's
   (map §F). Keep **off by default** and **Claude-Code-qualified only** until OC2 qualification is proven on the
   device.
4. **Connector suggestions** (`connector` field, `find_connectors`): a separate later slice, as the map already says.
5. **Elicitation adapters:** ACP v2 (`elicitation.form` first, then `url`) and Codex, when those backends land.
6. **Optional: A2UI ingestion adapter.** Only if a supported agent emits A2UI. Translate a bounded subset into
   `AgentCard` (sketch in §10.5). Never adopt free layout. Re-evaluate `androidx.a2ui` when it is at least beta,
   supports v1.0 and either has KMP variants or our iOS plan accepts an Android-only path.

### 9.4 One form engine (removes about 340+ lines and a second set of field bugs)

- One `FormSpec` model and one `KitForm`:
  - field kinds: text, multiline, number, integer, toggle, select, multiselect, date, date-time, external-link
  - conditions: `when` (all must hold; an inactive field is neither required nor sent; its draft is kept)
  - per-field validation (required, min/max, pattern, length, real calendar dates)
- One adapter per source, each with its own answer encoder:

  | Source | Answer encoder |
  |---|---|
  | OC2 `Form.Info` | `POST …/form/{id}/reply {answer}`; 400 keeps the form open; 409 says "answered on another device" |
  | ACP / MCP elicitation schema | `accept` / `decline` / `cancel` + content |
  | card `form` ask | our two-line answer message |
  | Codex elicitation | accept / decline / cancel |

- The sheet-or-card presentation rules stay as they are in map CHAT-GENUI-48: content-height sheet up to 4 fields, full
  height above that, an end-side sheet on wide windows, never auto-presented, and drafts kept per form.

### 9.5 Streaming and motion

- **Skeleton.**
  - On `session.tool.input.started` with name `oc-ui_show` (OC2), or the Paseo equivalent if Paseo streams tool starts
    [U], insert a `KitAgentCard.Skeleton` in the reply: the card shell, the agent eyebrow, and no buttons.
  - Do not parse `tool.input.delta`.
  - On `tool.called` + success with a valid card, cross-fade to the card in 150–250 ms, keeping the height change smooth
    (`animateContentSize`). Under reduced motion, or with the app's "Calm" setting, swap instantly.
  - On failure or invalid input, the skeleton becomes the one-line "unreadable" row. Deltas are ephemeral, so after a
    reconnect there is no skeleton, only the final card.
- **Native feel.**
  - Choice rows use a kit ripple. Answering gives a confirm haptic through the kit haptics seam.
  - The receipt collapses with a 150–250 ms size and fade change.
  - Predictive back inside the form sheet follows the kit sheet's back handling. Remember the demo lesson: disable
    container transforms during the gesture.
- **LazyColumn hygiene.** Use the card's stable key `CardIdentity` and `contentType = "agentCard"`. Card models are
  immutable, so only the small delivery state changes.

### 9.6 Things to decide with the owner (do not decide silently)

- **"Steer, never queue" against cards.** Today, card controls stay disabled while the session is busy, and the card
  says why. On OC2, an answer could instead be delivered as a **steer** into the running turn (inbox `PATCH
  {delivery}`).
  - That fits the owner's steer rule.
  - But the tool result tells the agent to "end your turn and wait".
  - Options: (a) keep disabled-until-idle (current contract); (b) allow a steer answer on OC2 only.
  - My recommendation is (a) for v1, with the reason sentence, and revisit (b) after device tests [U].
- **Generating the helper's validator.** Should the JS twin be generated from the Kotlin validator (Kotlin/JS, Node
  target) to keep one source? Recommendation: keep the hand-written JS twin plus the shared corpus in v1, because it is
  proven, has zero dependencies and is small. Measure Kotlin/JS bundle size later [U].
- **Card charts in RTL.** Should the bar or line order mirror? There is no rule in the map. Ask, and default to the
  kit chart's current behaviour.

---

## 10. Code-level notes (Kotlin sketches; names are proposals)

### 10.1 Model (`:core:model`, commonMain)

```kotlin
@Immutable data class CardIdentity(            // trusted parts only; never the agent's `id`
    val profileId: String, val sourceId: String, val directory: String, val workspace: String?,
    val sessionId: String, val messageId: String, val callId: String,
)

@Immutable data class AgentCard(
    val identity: CardIdentity,
    val revision: String,                      // sha256 of canonical (schema-ordered) JSON
    val label: String,                         // agent-supplied id; display/correlation only
    val title: String,
    val body: List<CardNode>,                  // 0..40
    val ask: CardAsk?,                         // mutually exclusive with connector
    val connector: ConnectorSuggestion?,
)

sealed interface CardNode {
    data class Text(val text: String) : CardNode                                  // <= 2,000 scalars
    data class KeyValue(val rows: List<KeyValueRow>) : CardNode                    // 0..20
    data class Checklist(val items: List<ListItem>, val style: ListStyle) : CardNode
    data class Table(val columns: List<String>, val rows: List<List<String>>) : CardNode
    data class Chart(val kind: ChartKind, val unit: String?, val labels: List<String>, val series: List<Series>) : CardNode
    data class Code(val language: String?, val text: String) : CardNode            // display only
    data class DiffStat(val files: List<FileStat>) : CardNode
    data class Progress(val label: String, val value: Double) : CardNode          // finite 0..1
    data class Callout(val tone: CalloutTone, val text: String) : CardNode
    data class Link(val label: String, val url: SafeHttpsUrl) : CardNode
}

sealed interface CardAsk {
    data class Choice(val options: List<ChoiceOption>, val multi: Boolean) : CardAsk   // 2..8
    data class Form(val spec: FormSpec, val submitLabel: String?) : CardAsk            // same engine as OC2 forms
    data class Confirm(val confirmLabel: String?, val cancelLabel: String?, val danger: Boolean) : CardAsk
    data class Photo(val purpose: String, val max: Int) : CardAsk                      // 1..4
}

sealed interface CardParse {
    data class Parsed(val card: AgentCard) : CardParse
    data class Unreadable(val reason: UnreadableReason) : CardParse   // notACard|version|tooLarge|unknownKey|badValue|secretField|unsupportedAsk
}
```

### 10.2 Admission and bounded parse (`:engine:requests`)

```kotlin
private val CARD_TOOL_NAMES = setOf("mcp__oc-ui__show", "oc-ui_show")   // exact; never suffix/substring

fun admit(part: ToolPartFacts, scope: CardScope): CardParse? {
    if (part.name !in CARD_TOOL_NAMES) return null                       // ordinary tool row
    if (!part.assistantOwned || part.synthetic) return null
    if (part.status != ToolStatus.Completed || !part.executed) return null   // pending/running stay tool rows
    val input = part.input ?: return null                                // missing after event loss -> reconcile, not invalid
    val compact = CompactJson.encode(input)                              // limits are defined on compact UTF-8
    if (compact.utf8Size() > 32_768) return CardParse.Unreadable(TooLarge)
    return CardValidator.validate(input, scope)                          // depth<=8, <=4,096 values, scalar counts,
}                                                                        // exact keys, no coercion, bidi/control strip
```

`CardValidator` must reproduce the Dart normaliser exactly, because the revision hash depends on it. Build the canonical
map in **schema order** and hash `sha256(compactJson(normalized))`. Write the corpus runner first (§9.2).

### 10.3 Renderer (`:ui:kit` + `:feature:chat:impl`)

```kotlin
@Composable
fun CardBody(nodes: List<CardNode>, modifier: Modifier = Modifier) = Column(modifier) {
    nodes.forEach { node ->
        when (node) {                                  // exhaustive: new node type = compile error until the kit has a part
            is CardNode.Text      -> KitReadingText(node.text, direction = TextDirection.Content)   // full text scaling
            is CardNode.KeyValue  -> KitKeyValueRows(node.rows)
            is CardNode.Checklist -> KitChecklist(node.items, node.style)
            is CardNode.Table     -> KitTable(node.columns, node.rows)       // horizontal scroll inside max width
            is CardNode.Chart     -> KitChart(node.kind, node.labels, node.series, node.unit) // + spoken summary
            is CardNode.Code      -> KitCodeBlock(node.text, node.language)  // always LTR
            is CardNode.DiffStat  -> KitDiffStat(node.files, maxShown = 30)
            is CardNode.Progress  -> KitProgressRow(node.label, node.value)
            is CardNode.Callout   -> KitCallout(node.tone, node.text)        // success = plain ok tone; amber = needs you
            is CardNode.Link      -> KitLinkRow(node.label, onOpen = { openExternalLink(node.url) })
        }
    }
}
```

### 10.4 Answer encoding (unchanged wire)

```
[oc-ui answer db-choice] Postgres
{"v":1,"cardId":"db-choice","callId":"call_123","value":{"choice":["postgres"]}}
```

- The summary line is at most 240 scalars, has no TAB and is stripped of control characters.
- The envelope keys are exactly `v/cardId/callId/value`. `value` is one of `choice`, `form`, `confirm` or `photo`.
- A2UI v1.0's `Action.event.userMessage` is the same idea as our summary line. If the A2UI adapter (§10.5) ever lands,
  that is the field to map to.

### 10.5 Future A2UI subset adapter (only if needed; `:protocol:a2ui`, commonMain, no androidx.a2ui dependency)

- **Input:** one complete A2UI v1.0 `createSurface` with inline `components` and `dataModel`. No streaming, and no
  `callRendererFunction` (reject it).
- **Accept** only a vertical tree. Flatten `Column`, `Card` and `List` with static children.

| A2UI component | Maps to |
|---|---|
| `Text` | `CardNode.Text` (the variant becomes the eyebrow/title rule) |
| `Divider` | dropped |
| `ChoicePicker` | `CardAsk.Choice` |
| `TextField` / `CheckBox` / `DateTimeInput` / `Slider` | `FormSpec` fields |
| one `Button` with `action.event` | the form's submit, or `Confirm` |
| `Image` / `Video` / `AudioPlayer` / `Icon(url)` / `Tabs` / `Modal` / `Row` with more than one interactive child | `Unreadable(unsupported)` |

- **Functions:** only `required`, `regex`, `length`, `numeric` and `email`, mapped to `FieldSpec` checks. `openUrl` maps
  to a Link row and still goes through `openExternalLink`. Any other function is unreadable.
- Run every result through the same `CardValidator` bounds. The adapter produces a card, so it can never widen what a
  card can do.

---

## 11. Unknowns (check in slice 0 or before the slice that needs it)

1. Whether kotlinx.serialization 1.12 parses pathologically deep JSON without a stack overflow, and how big an SSE frame
   `core:net` should allow. Add corpus cases.
2. Whether Paseo forwards tool-input-start events for Claude Code, so a skeleton can show there too, or only completed
   tool calls.
3. Whether Claude Code's MCP elicitation dialogs reach the app through Paseo at all, or are CLI-only. If they never
   reach us, an `oc-ui` server that elicits would hang. The helper does not elicit today. Keep it that way.
4. OpenCode 2 card qualification. In the Flutter app it is "registered, not qualified". It needs device proof on
   2.1.1, and again on any 2.2.x pin. OC 2.2.x also splits permissions across integrations, MCP, skills and plugins
   (owner brief 2026-10-31). Re-check that the exact `oc-ui_show` permission rule still applies.
5. Whether the `androidx.a2ui` Material catalog (`MaterialA2uiBasicCatalogV1Defaults`) exists in an unpublished
   artifact, and when v1.0 support and KMP variants arrive. Re-check at beta.
6. The exact license of DivKit (GitHub API says `NOASSERTION`). This only matters if anyone proposes it.
7. Claude Code CLI and Codex do not render MCP Apps `ui://` views in any way that reaches a remote client. I found no
   source either way. I treated this as "no", based on OpenCode's verified behaviour and the CLI nature of the
   others.
8. RTL ordering for card charts. This is an owner/kit decision.
9. The kit-v2 hard max width for cards and for the form sheet. Use the existing table. I did not invent a number.
10. Whether to steer answers into a running OC2 turn (§9.6).

---

## 12. Sources (all read 2026-11-01)

A2UI:

- Google Developers Blog, "What's new in A2UI v0.9" (2026-04-17): https://developers.googleblog.com/a2ui-v0-9-generative-ui/ [V]
- A2UI roadmap (page says last updated June 2026): https://a2ui.org/roadmap/ [V]
- A2UI v0.9 specification: https://a2ui.org/specification/v0.9-a2ui/ [V]
- google/A2UI repo (releases up to cli-v0.1.0 on 2026-10-30; spec dirs v0_8, v0_9, v0_9_1, v1_0): https://github.com/google/A2UI [V]
  - v1.0 README ("candidate for becoming stable"): https://github.com/google/A2UI/blob/main/specification/v1_0/README.md
  - v0.9 → v1.0 evolution guide: https://github.com/google/A2UI/blob/main/specification/v1_0/docs/evolution_guide.md
  - v1.0 protocol (security boundaries, actions, accessibility): https://github.com/google/A2UI/blob/main/specification/v1_0/docs/a2ui_protocol.md
  - A2UI over MCP guide: https://github.com/google/A2UI/blob/main/docs/public/guides/a2ui_over_mcp.md

androidx A2UI:

- Release notes, androidx.a2ui (alpha01 2026-09-23, alpha02 2026-10-21): https://developer.android.com/jetpack/androidx/releases/a2ui [V]
- Release notes, androidx.a2ui.compose: https://developer.android.com/jetpack/androidx/releases/a2ui-compose [V]
- Google Maven group indexes and binaries I inspected:
  - https://dl.google.com/android/maven2/androidx/a2ui/group-index.xml
  - https://dl.google.com/android/maven2/androidx/a2ui/compose/group-index.xml
  - the `*-1.0.0-alpha02.module` and `.aar` files under those paths [V]

Community and Flutter renderers:

- uny/a2ui-compose README (v0.3.0, 2026-09-26): https://github.com/uny/a2ui-compose [V]
- mikepenz/A2CUI README (v0.1.0-a02, 2026-04-24): https://github.com/mikepenz/A2CUI [V]
- klibs.io A2CUI listing: https://klibs.io/package/dev.mikepenz.a2cui/a2cui-agui [S]
- Flutter genui on pub.dev (0.10.4, 2026-09-29): https://pub.dev/packages/genui [V]
- pub.dev API, genui, a2ui_core and genui_a2a: https://pub.dev/api/packages/genui [V]

Remote Compose and widgets:

- Remote Compose release notes (alpha22 2026-10-21): https://developer.android.com/jetpack/androidx/releases/compose-remote [V]
- Remote Compose group index: https://dl.google.com/android/maven2/androidx/compose/remote/group-index.xml [V]
- Remote Compose Foundation release notes: https://developer.android.com/jetpack/androidx/releases/compose-remote-foundation [V]
- Glance release notes: https://developer.android.com/jetpack/androidx/releases/glance [V]
- Android Authority, widgets overhaul with Remote Compose (2026-05-22): https://www.androidauthority.com/android-widgets-overhaul-remote-compose-3670122/ [S]

MCP:

- MCP Apps repo (spec 2026-01-26 Stable; SDK v2.0.4 on 2026-10-27): https://github.com/modelcontextprotocol/ext-apps [V]
- MCP Apps spec file: https://raw.githubusercontent.com/modelcontextprotocol/ext-apps/main/specification/2026-01-26/apps.mdx [V]
- MCP 2026-07-28 release: https://github.com/modelcontextprotocol/specification/releases/tag/2026-07-28 [S via search summary]
- MCP-UI: https://github.com/MCP-UI-Org/mcp-ui [V]

ACP:

- ACP v2 elicitation doc: https://github.com/agentclientprotocol/agent-client-protocol/blob/main/docs/protocol/v2/elicitation.mdx [V]
- ACP releases (v1.14.0, schema v2.0.0-alpha.11 on 2026-10-30): https://github.com/agentclientprotocol/agent-client-protocol/releases [V]

OpenCode (anomalyco/opencode, releases up to v2.2.5 on 2026-10-30): https://github.com/anomalyco/opencode [V]

- `packages/schema/src/form.ts`, also read at tag v2.1.1: https://github.com/anomalyco/opencode/blob/main/packages/schema/src/form.ts
- `packages/cli/src/acp/elicitation.ts`: https://github.com/anomalyco/opencode/blob/main/packages/cli/src/acp/elicitation.ts
- `packages/core/src/mcp/client.ts`: https://github.com/anomalyco/opencode/blob/main/packages/core/src/mcp/client.ts

Claude Code and Codex:

- Claude Code changelog (MCP elicitation in 2.1.76): https://code.claude.com/docs/en/changelog [S via search summary]
- Codex app-server MCP elicitation (commit #13425, mirror): https://forge.lthn.ai/core/core-agent-ide/commit/926b2f19e8c2a4c01b3a87bccd8ef8a1c23b22ab [S]
- Codex elicitation write-up (2026-04-10): https://codex.danielvaughan.com/2026/04/10/mcp-elicitation-codex-cli/ [S]

Other server-driven UI frameworks:

- Cash App Redwood (0.19.0, 2025-11-06; last push 2026-01-09): https://github.com/cashapp/redwood [V]
- Cash App Zipline (1.28.0, 2026-09-29): https://github.com/cashapp/zipline [V]
- Keliver (fork of Redwood) listing: https://klibs.io/project/waliasanchit007/keliver [S]
- DivKit (33.9.0, 2026-10-27): https://github.com/divkit/divkit [V]
- DivKit Compose artifact: https://central.sonatype.com/artifact/com.yandex.div/compose [S]
- vercel-labs/json-render (v0.21.3, 2026-10-24): https://github.com/vercel-labs/json-render [V]
- AG-UI (release/2026-10-30): https://github.com/ag-ui-protocol/ag-ui [V]
- AG-UI Kotlin SDK docs: https://docs.copilotkit.ai/ag-ui/sdk/kotlin/overview [S]

Internal (read-only):

- `/home/eslam/Storage/Code/wt/native-plan/map/chat-genui.md`
- `/home/eslam/Storage/Code/wt/oc_app-batch1/docs/design/genui-plan-2026-10-07.md`
- `/home/eslam/Storage/Code/wt/oc_app-batch1/docs/design/genui-contract-2026-10-07.md`
- `/home/eslam/Storage/Code/wt/oc_app-batch1/docs/opencode2-protocol-notes.md`
- `/home/eslam/Storage/Code/wt/oc_app-batch1/lib/api2/models/models_inbox.dart` (the `hidden` gap)
- `/home/eslam/Storage/Code/wt/oc_app-batch1/lib/termux/bridge.dart` (OpenCode pin 2.1.1)
- `/home/eslam/Storage/Code/wt/oc_app-batch1/test/fixtures/genui/`
- `/home/eslam/Storage/Code/wt/native-plan/research/architecture-stacks.md`
- `/home/eslam/Storage/Code/wt/native-plan/research/kmp-cmp-status.md`
- `/home/eslam/Storage/Code/wt/native-plan/research/rendering-chat.md`
