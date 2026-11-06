# OpenUI for rich replies in the native app

Date: 2026-11-06 · Branch: `native/openui-spike` (from `native/upg-newchat` at 679c3d1b5) · Status: research, no code.

**Recommendation: adopt later, narrowly.** Do not add OpenUI to the native app now. Finish porting the `oc-ui`
card contract first. Come back when the revisit conditions in §7 hold, and then add OpenUI Lang only as a second
input syntax for the existing card engine. It must never become a new runtime.

---

## 1. What OpenUI is (read from source, `thesysdev/openui` at 56122b94f, 2026-11-05)

| Part | Package / version | License | What it does |
|---|---|---|---|
| Language + parser + prompt generator | `@openuidev/lang-core` **1.1.0** | MIT (© Thesys Inc.) | lexer, streaming parser, schema-aware materializer, `generateSystemPrompt`, runtime evaluator (state, Query/Mutation) |
| React renderer | `@openuidev/react-lang` **1.1.0** | MIT | `Renderer`, `defineComponent`/`createLibrary` (Zod props), actions, hooks; also a React Native entry |
| Default component library | `@openuidev/react-ui` 0.18.4 | MIT | `Stack`, `Card`, `TextContent`, `Form`, `Input`, `Select`, `Button(s)`, `Tabs`, `Table`/`Col`, `BarChart`… |
| Other renderers | `vue-lang` / `svelte-lang` / `angular-lang` **0.4.2** | MIT | still on OpenUI Lang **0.x**: the first-party ports already lag the 1.0 language |
| A2UI bridge | `@openuidev/a2ui` 0.5.0 | MIT | experimental A2UI v1.0 profile that carries component lists as OpenUI Lang statements |
| Kotlin / Compose | none | — | there is no JVM or Compose implementation |

The npm `lang-core` package runs a `postinstall` script that sends PostHog telemetry unless you opt out
(`OPENUI_TELEMETRY_DISABLED=1` or `DO_NOT_TRACK=1`). Its parser telemetry is opt-in. A Kotlin port has none of this.
If we ever run the OpenUI CLI to build the prompt, set `DO_NOT_TRACK=1`.

### 1.1 The language

The language is line-based, with one statement per line: `identifier = Expression`. `root` is the entry point.
Arguments go by position, in the component's Zod key order, and optional ones can be left off the end. A name can be
used before the line that defines it.

```text
root = Card([title, facts, pick])
title = CardHeader("Pick a database")
facts = TextContent("Both fit the current schema.")
pick = Buttons([pg, lite])
pg = Button("Postgres", @ToAssistant("Use Postgres"), "primary")
lite = Button("SQLite", @ToAssistant("Use SQLite"))
```

The language has these expression types: component call `Type(...)`, string, number, bool, null, array, object and
reference. Version 1.x adds a reactive layer on top: `$state` variables, operators and ternaries, member and index
access, built-in functions (`@Count @Sum @Filter @Sort @Each @Round…`), action steps (`@ToAssistant @OpenUrl @Set @Reset
@Run`, and custom `@Name` actions) and `Query(...)`/`Mutation(...)`. Query and Mutation run **tools from the client
with no model call** (see `architecture.mdx`, "the LLM generates the wiring, the runtime executes it").

### 1.2 Parser and streaming behaviour (`lang-core/src/parser`, 5,055 TS lines in total)

- `stripFences` takes the program out of ```` ```openui-lang ```` fences, and prose outside them stays chat text
  ("inline mode"). `stripComments` removes `//` and `#` comments.
- `autoClose` closes an open string and open brackets, so a half-streamed line still parses.
- `createStreamParser` keeps a watermark. A statement ends at a newline outside brackets, and finished statements
  are cached. Only the last unfinished statement is parsed again on each chunk. `set(fullText)` takes the delta
  only, and starts over when the text was replaced instead of extended.
- Materializing maps positional arguments to named props. It resolves references with a cycle guard and memo, drops
  unknown components and unresolved references from arrays, records `unresolved`/`orphaned` lists, and drops a
  component that lacks a required prop (it becomes a validation error, not a crash).
- Since 1.0, a reply whose `root` line comes last shows nothing until that line arrives. The generated prompt
  therefore says "write `root` first", so the outline appears first and the details fill in.

### 1.3 Component library → system prompt

You define the components (name, description, Zod props) and the actions. `library.toJSONSchema()` turns them into a
spec, and `generateSystemPrompt({library, promptOptions})` (or `npx @openuidev/cli generate`) turns the spec into the
prompt text: syntax rules, component signatures, the action list, hoisting/streaming rules, and the optional
`toolCalls`, `bindings`, `editMode` and `inlineMode` sections. The prompt is plain text that you send yourself. The
language does not depend on a provider. Their Gateway product also repairs output as it streams, and we would not
use it.

### 1.4 How stable the spec is

`lang-core` 0.3.0 shipped 2026-09-15, 0.4.0 on 10-10, **1.0.0 on 10-12** (breaking: bare action steps, and replies with
`root` last render blank), 1.0.1 on 10-15, 1.0.2 on 10-27 and **1.1.0 on 11-04** (query chaining, list-wise `@Round`:
"upgrade clients before the server"). That is four minor or major releases in eight weeks, with one break. The repo is
two years old (created 2024-12) and has ~14.9k stars.

## 2. What the app has today

The app has three channels for rich replies. None of them uses OpenUI.

### 2.1 Agent cards: the `oc-ui` MCP tool (the main one)

- **Server feature:** this is an MCP server that **the app itself installs** into the built-in Linux, not a feature of
  any server. It is a zero-dependency Node stdio script embedded in the app (`CardToolServer` + `CardToolJs` in
  `native/engine/cards`; Flutter `genUiServerScript`). It offers the tools `show` and `find_connectors`. Setup
  (`CardsInstaller`/`CardsInstallScripts`) registers it per agent: Claude Code with `claude mcp add --scope user …
  oc-ui` (per profile), OpenCode 1 under `mcp.oc-ui` (local command), and OpenCode 2 under `mcp.servers.oc-ui` with
  `codemode:false`. The OpenCode 1 "generated-UI readiness" check expects 1.19.0. The feature is off by default
  (`oc.genui.enabled.<profileId>`) and qualified for Claude Code first (plan 09 §13.1).
- **Wire:** the agent calls `show` with a strict JSON card v1: `{v:1, id, title, body:[0..40 nodes of 10 types], ask?}`.
  The node types are text, keyValue, list, table, chart, code, diffStat, progress, callout and link. An ask is one of
  choice, form, confirm or photo. The full contract is `docs/design/genui-contract-2026-10-07.md`. The tool returns at
  once and does not claim the card was delivered.
- **Arrival:** the call appears as an ordinary tool part in the transcript. `CardCodec.fromPart` admits a part only
  when its name is exactly `mcp__oc-ui__show` or `oc-ui_show` and the call is completed, executed and non-synthetic,
  with full input and transport ids. `CardValidator` normalizes it strictly: unknown keys make it unreadable, sizes are
  limited (32 KiB, depth 8), control and bidi characters are stripped, and secret-looking fields are refused. The
  revision is the sha256 of the normalized JSON. `CardBoard`/`CardAuthority` work out the card's state from the
  server transcript. `ChatCards` wires it, and `ChatCardSlot` maps `CardNode` → `KitCardNode` and the ask →
  `KitCardChoice`/`KitCardForm`/`KitCardConfirm`/`KitCardPhoto` inside `KitAgentCard` (`ui/kit-chat/.../card`).
- **Answer:** the answer is a normal user message sent through the existing prompt path, with two lines:
  `[oc-ui answer <id>] <summary>` and a JSON envelope `{v, cardId, callId, value}`. It is sent exactly once
  (`CardAnswers`, `CardDispatch`, held answer with Undo).
- **History:** Flutter `lib/domain/genui/*` and `lib/state/connection/gen_ui.dart`, built on `feat/genui-be` and
  `feat/genui-fe` (2026-10-07) and merged into the product line. The native port is `:engine:cards` (P12-CHAT-CARDS,
  slice 5).

### 2.2 Server forms (an OpenCode 2 server feature)

OpenCode 2 sends `form.created`, `form.replied` and `form.cancelled` events, with `GET /form/request` and
`/session/{id}/form/{id}/reply|cancel|state` (`protocol/oc2/.../Oc2Gateway.kt`, gated by `ServerCapabilities.forms`).
MCP elicitation and questions use the same path. `:engine:requests` (`FormSpec`, `FormRules`, `FormDrafts`) drives
`ChatFormSlot` → `KitFormSheetBody` + `KitForm` (`ui/kit-chat/.../form/KitFormSheet.kt`). The server defines these
forms, so OpenUI has nothing to replace here.

### 2.3 Agent blocks in Markdown

Closed ```` ```choices ````, ```` ```checklist ```` and ```` ```command ```` fences in assistant text are recognized by
`:core:markdown` `BlockParser` (`AgentBlockType`) and drawn by the kit-chat Markdown. Choices put their text into the
composer. These blocks need no install and no identity. They render only once the fence is closed.

### 2.4 Earlier decisions that apply

ADR 01 and plan 09 §13.1 already turned down A2UI and Remote Compose. The reasons were "pre-1.0, Android-only, breaks
kit-only" and "no executable expressions, no dynamic widgets". The plan said to revisit "only when a supported agent
emits A2UI; then a bounded subset adapter into `AgentCard`, never free layout". OpenUI deserves the same test.

## 3. What OpenUI would add

| Gain | Today | With OpenUI Lang |
|---|---|---|
| **Cards that appear while they stream** | a card appears only after the `show` call completes | the outline appears first and the details fill in, chunk by chunk (§4.3) |
| Composition | one card: a title plus a flat list of nodes plus at most one ask | nested groups, several cards per reply, prose between them (inline mode) |
| Several action buttons | the ask is one of four fixed kinds | `Buttons([...])` with `@ToAssistant(msg, context)` steps, which map onto choice (§4.1) |
| One source of truth for the schema | the JSON schema, validated by hand in JS, Kotlin and Dart (a parity corpus keeps them equal) | one component spec generates the prompt, and the parser checks positional props against it |
| Fewer tokens | compact JSON (already terse) | ~2× fewer than normalized JSON trees in their benchmark. Against our flat card the gain is smaller (not measured) |
| Ecosystem | ours only | prompts and tests from a public spec, and an A2UI bridge |

Without OpenUI we lose nothing that works today. With it we gain streaming display and layout freedom.

## 4. How it would fit

### 4.1 Components mapped 1:1 onto existing kit parts ("kit only")

We would publish **our own small library** and never `openuiLibrary` from react-ui. Each component maps onto a
`KitCardNode` or a card ask that already exists, so no screen draws anything and the kit owner adds nothing in
slice 1. Anything not in this table is unknown: the prompt never advertises it and the parser drops it.

| OpenUI component (our library) | Kit part | Notes |
|---|---|---|
| `Card(children, title?)` (root) | `KitAgentCard` (Report, or Ask when it holds an ask) | one level only, a `Card` inside a `Card` is flattened (one card shape, 720 dp cap) |
| `TextContent(text)` | `KitCardNode.Text` | `size` is ignored. The kit has one reading role |
| `List(items, style?)` / `ListItem(text, done?)` | `KitCardNode.Checklist` | bullet or check |
| `KeyValue(rows)` / `Row(key, value)` | `KitCardNode.KeyValue` | |
| `Table(columns)` / `Col(label, data)` | `KitCardNode.Table` | columns become rows, with the card v1 limits |
| `BarChart`/`LineChart(labels, series, unit?)` | `KitCardNode.Chart` | spoken summary from `ChatCardSlot` as today |
| `Code(text, language?)` | `KitCardNode.Code` | display only |
| `Callout(tone, text)` | `KitCardNode.Callout` | |
| `Progress(label, value)` | `KitCardNode.Progress` | |
| `Button(label, @OpenUrl(url))` | `KitCardNode.Link` | does nothing until tapped, then goes through `ExternalLinkPolicy` → `ExternalLinkOpener` |
| `Buttons([Button(label, @ToAssistant(msg))…])` | `CardAsk.Choice` → `KitCardChoice` | ids taken from position, the answer is the existing envelope |
| `Form(name, buttons, fields)` + `FormControl`/`Input`/`Select`/`Toggle`/`DateInput` | `CardAsk.Form` → `KitCardForm` | field ids from `Input(name)`, secret-name heuristic applies |

The mapping target is the existing `AgentCard` value. The rest of the pipeline stays as it is: validator limits,
revision, `CardAuthority`, answer pipeline, receipts, recovery and deletion. The adapter is just another `fromInput`.

### 4.2 How the agent gets the component prompt

| Option | Reaches | Cost | Verdict |
|---|---|---|---|
| **New `oc-ui` tool `show_ui({v:1, ui:"<OpenUI Lang>"})`, with the generated prompt as its tool description** | every agent where the helper is already installed (Claude Code, OpenCode 1, OpenCode 2) | the description is sent with every request (estimate 1.5–3k tokens). It reuses install, admission and answers | **preferred** |
| MCP `initialize.instructions` from the helper | Claude Code reads it. OpenCode is not verified | small | maybe, as an addition |
| `AGENTS.md` text, or OpenCode `instructions` files | OpenCode only, per project or global | changes the person's files. Other clients show the raw code | no |
| OpenCode plugin (system-prompt transform) | OpenCode 1 only (the OpenCode 2 plugin API differs) | a new install and a new trust surface | no |

Build the prompt from a tiny TS/Zod library file with `@openuidev/cli generate` and check the text into the repo as a
resource. A drift test compares it with the Kotlin component table. The prompt runs with `toolCalls:false`,
`bindings:false` and `inlineMode:false`, so the model is never taught state, queries or expressions.

Why the tool and not prose fences: a tool part carries the trusted identity (session/message/call ids) that the
answer pipeline needs. It keeps raw code out of the OpenCode TUI and web clients, and it is the path the app has
already qualified. Fenced text (channel 2.3) would stream for free, but it has no identity. It also needs the prompt
in every client's system prompt.

### 4.3 Parse the stream, render as it grows

- **OpenCode 2:** `session.tool.input.delta` carries the raw JSON-argument text while the model writes it
  (`docs/opencode2-protocol-notes.md`, `Oc2Event` already decodes it). A tolerant reader takes the partial `ui` string
  value from that JSON, undoing escapes as it goes, and feeds the streaming parser. While the call runs, the card is a
  **display-only preview** with the ask disabled, and new nodes come in on the kit's quick motion (150–250 ms, nothing
  under reduced motion). On `tool.success` the existing admission runs on the completed input. Only then can the card
  be acted on. Deltas never replay, so after a reconnect the card is fetched again, as with any other OpenCode 2 data.
- **Claude Code via Paseo, and OpenCode 1:** it is not yet known whether tool input streams. Until that is proven,
  the card renders on completion, as today.
- The parser runs on the conversation's engine lane with the injected dispatchers (rule 6), not on the UI thread.

## 5. What is needed

1. **`:core:openui`**, a pure Kotlin common module like `:core:markdown`. It is a port of the *static subset* of
   lang-core: lexer, `autoClose`, statement split, expression parser for literals, references, component calls,
   arrays, objects and action calls, `stripFences`, the streaming watermark parser, and positional → named mapping
   with cycle guard and unresolved/orphaned tracking. Tokens outside the subset (`$state`, operators, ternaries,
   `@functions`, `Query`, `Mutation`, `@Set`, `@Reset`, `@Run`, custom actions) parse into an `Unsupported` node. An
   unsupported node in display drops that node, and in an ask makes the card unreadable. Limits are applied before
   parsing (32 KiB like card v1, at most 200 statements, depth 8). The parser never evaluates anything. The
   coordinator has to add a line to `MODULE_OWNERS` (and `settings.gradle.kts`), and the MIT notice goes into
   `THIRD_PARTY_NOTICES.md`/`LICENSES`. Estimate: ~700–900 Kotlin lines plus tests, from the ~1,300 TS lines that
   make up the subset.
2. **`OpenUiCardAdapter`** in `:engine:cards`: element tree → `AgentCard` (§4.1) under every card v1 rule.
   `show_ui` names (`mcp__oc-ui__show_ui`, `oc-ui_show_ui`) go into `CARD_TOOL_NAMES`. The revision is computed over
   the normalized card, so the revision, state and answer stay byte-compatible with v1.
3. **The `show_ui` tool in the helper** (`CardToolJs`). JS validation of a string input is just limits plus a check
   that the program "starts with `root =`". Authoritative validation stays in the app. The Flutter twin needs a twin
   decision: either port it too, or make `show_ui` native-only.
4. **Compose rendering**: none new. `ChatCardSlot` already renders `AgentCard`. The preview state adds a
   "still writing" mode to `KitAgentCard` (kit owner, one flag) and growth motion.

## 6. Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| Spec churn | four minor or major releases in 8 weeks, one break, and the vue/svelte/angular ports are still on 0.x | we own our prompt text and pin "OpenUI Lang 1.1 static subset". Upstream changes reach us only when we choose to resync |
| The direction of upstream | its headline features (Query/Mutation run tools from the client, `$state`, Gateway autofix) are what plan 09 §13 forbids ("no executable expressions, no dynamic widgets") | prompt flags off, unsupported nodes refused, no evaluator ported |
| A loose parser against a strict contract | lang-core quietly drops invalid parts, but card v1 is strict ("unknown keys → unreadable") | loose for the display-only preview, strict at admission: any dropped or unsupported node inside an ask makes the card unreadable, never a quietly weaker ask |
| Security: actions | an `@ToAssistant` could be read as consent | it maps to the existing answer: a normal user message, sent once, that never approves a permission, runs a shell or skips approval. `@OpenUrl` becomes a link that does nothing until tapped, through `ExternalLinkPolicy`. There are no custom actions, so no code runs |
| Security: content | the model's text appears in the UI | only registered components. Card v1 text cleaning (control and bidi characters), bidi isolation, secret-field heuristic, no remote resources, no logging of program text (rule 7) |
| Model reliability | small or free models may write broken programs | spike S0 measures it. Unreadable cards fall back to the existing "could not show" line |
| Token cost | the tool description is sent every turn | keep the library to ~12 components with no examples beyond one. Measure it in S0 |
| Two syntaxes | two ways to write the same card, and two prompts to keep | `show` stays for qualified agents, `show_ui` is opt-in per agent. Retire one later on evidence |
| Flutter parity | the Flutter app is still the product line | `show_ui` only on native, behind a capability flag. Never advertise it where the app cannot render it |

## 7. Recommendation: adopt later

**Not now, because:**

1. It removes nothing. The JSON card contract, its three backend registrations and its qualification all stay. The
   owner's test ("judge an idea by what it removes") fails today.
2. The native card port (`:engine:cards`, slice 5) is the active slice. A second syntax before v1 is in use breaks
   "one active product slice".
3. The language is eight weeks into public versioning, and its own non-React renderers have not caught up with 1.0.
   We would be the only Kotlin implementation, chasing a moving spec.
4. Most of what OpenUI adds (state, queries, mutations, Gateway) we would have to switch off. What is left, a static
   component tree, is close to card v1 written in another syntax.
5. The main user-visible gain, cards that appear while they stream, can be had without OpenUI: show a display-only
   preview from `session.tool.input.delta` of today's JSON card, using the same tolerant parse approach (auto-close
   strings and brackets).

**Revisit when all of these hold:**

- native agent cards v1 have shipped and are qualified on at least one agent,
- `lang-core` has had ~3 months with no breaking change, or publishes a stability policy,
- the owner asks for richer layout (nesting, several cards, tabs) that card v1 cannot express,
- or a supported agent (Claude Code, OpenCode) emits OpenUI Lang by itself.

When they hold, build it as §4–§5 describe: a bounded subset adapter into `AgentCard`, never free layout. That is the
same conclusion the plan reached for A2UI.

**Do next instead (cheap and useful now):** the streaming preview of today's JSON card on OpenCode 2 (slice P below).

## 8. Effort, in slices

| Slice | Size | Finish line | Non-goal |
|---|---|---|---|
| **P: streaming preview of the JSON card (do now)** | S–M | on OpenCode 2, a card appears and grows while `show` is still running, with the ask disabled, and becomes actionable on success | OpenUI |
| S0: feasibility spike | S | for Claude Code and two OpenCode 2 models, given the generated prompt as the `show_ui` description, the valid-program rate and tokens against JSON v1 on 10 fixture cards → go or no-go | any app code |
| S1: `:core:openui` parser | M | streaming-tolerant subset parser with a corpus ported from lang-core tests (MIT), and limits | evaluator, state, queries |
| S2: adapter + names | M | `show_ui` input becomes the same `AgentCard` as the matching `show` input (a fixture pair proves the same revision and answer) | new kit parts |
| S3: helper tool + prompt resource | S | `show_ui` in the helper, the generated prompt checked in, a drift test against the Kotlin table, off by default | changes to the installer |
| S4: streaming preview for `show_ui` | S (after P) | the same preview path, fed by the OpenUI stream parser | Paseo streaming unless proven |
| S5: composition beyond card v1 | L | nested sections, several cards per reply, tabs: a card v2 contract with both sides' OK and new kit parts by the kit owner | free layout |

Total for S0–S4 after the revisit trigger: about one L. S5 is a separate decision.

## Sources

- OpenUI: `packages/lang-core/src/parser/{lexer,statements,parser,materialize,types,ast,builtins,prompt}.ts`,
  `packages/*/package.json`, `packages/lang-core/CHANGELOG.md`, `docs/content/docs/openui-lang/{syntax,overview,
  architecture,actions,system-prompts,renderer,message-protocol,benchmarks}.mdx`, GitHub releases list (dates).
  It was read from a shallow sparse clone, which has since been deleted.
- This repo: `docs/design/genui-contract-2026-10-07.md`; `docs/design/native-rewrite-2026-11-01/plan/09-chat-streaming-genui.md`
  §13; `native/engine/cards/*`; `native/feature/chat/.../cards/{ChatCards,ChatCardSlot}.kt`;
  `native/ui/kit-chat/.../card/KitAgentCard.kt`, `.../form/KitFormSheet.kt`; `native/core/markdown/.../{Blocks,BlockParser}.kt`;
  `native/protocol/oc2/.../Oc2Gateway.kt`, `Oc2Event.kt`; `docs/opencode2-protocol-notes.md`; `lib/domain/genui/*`.
