# 09. The chat: transcript, streaming, renderers, composer, requests and generative UI

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR; it wins every disagreement), `02` (principles,
success numbers), `03` (module shapes), `04` (`GatewayEvent`, gateways), `05` (stored formats), `06` (kit), `07` (routes,
Back). This section owns everything *inside* the conversation: the transcript model and its engine, the streaming hot
path, the list, the renderers, the composer behaviour, the request cards, review/revert hooks, read-aloud hooks and the
agent-card (generative UI) port.

## For the owner, in five lines

1. The chat becomes one engine that turns server events into a ready-to-draw list off the main thread; the screen only
   paints. New words fade in and the reply grows smoothly at your screen's full refresh rate, without the list jumping.
2. Everything you have today stays as you decided it: prompt, turn, step, notice, one footer per turn, one live line,
   one Stop at the end of the chip row, steer never queue, never send twice, 3-second Undo on answers, cards off by
   default.
3. We write our own Markdown and diff drawing on JetBrains' official parser (no heavy library), use SyntaxMP for code
   colours behind a swap seam, and keep the agent-card contract with your agents byte for byte.
4. Tests come first, from the 124 chat behaviours plus the send, draft and queue behaviours; a recorded 5,000-part
   reply and a 6,000-word stream are the speed tests your phone must pass before the switch.
5. Four small questions for you are in §19; work follows the recommended answer until you reply.

---

## 0. How to read this section

### 0.1 What this section decides, and what it leaves to others

| Decides here | Left to |
|---|---|
| `MessageInfo`, `Part`, `ToolState` and the transcript vocabulary in `:core:model` (04 §11.2 hands them to this section) | wire adapters → 04; stored JSON of drafts, queue, stash → 05 |
| `TranscriptEngine`: event apply, version fences, history merge, display passes, row flattening, 50 ms publish | SSE loop, reconnect timing, event adapter → 04 |
| The live line, the send pipeline, steer/stop rules (`:engine:delivery`) | the queue's stored format and migration → 05 |
| Request engine rules and the one `FormSpec` engine (`:engine:requests`) | notification actions' delivery → the Android surfaces section |
| `ChatUi`/`ChatActions`, the `ComposerHost` slot, the transcript `LazyColumn` | routes, arrivals, Back guard, row→chat transform → 07 |
| Frame-paced reveal, fade, growth, frame-rate vote | kit tokens, `MotionPolicy`, haptic map, widths → 06 |
| Markdown, code, diff, picture and tool-row rendering choices and fallbacks | file viewers (PDF, SVG, CSV, office), gallery internals → the media section |
| Agent-card contract port, admission, receipts, recovery, card renderer | the proot helper's runtime install mechanics → the built-in Linux section (this section fixes the ownership split, §13.8) |
| Chat performance counters and the chat Macrobenchmark journeys | the full budget tables → section 13; flow harness → section 14 |

### 0.2 Tags

**[V]** checked today (2026-11-01) against a primary source: Maven metadata, a published `-sources.jar`, official docs.
**[R]** read in the Flutter source, a map, a research file, a plan section or owner memory. **[U]** unverified; the spike
or test named next to it proves it. **[new]** a rule the Flutter app did not have, added here with its reason.

Checked today for this section [V]:

- `org.jetbrains:markdown` latest **0.7.18** (lastUpdated 2026-10-15); `com.gallatinapps.syntaxmp:syntaxmp-tokenizer`
  latest **0.3.0** (2026-06-04); `io.github.petertrr:kotlin-multiplatform-diff` latest **1.4.0** (2026-09-20).
- `androidx.compose.foundation:foundation-android:1.13.0-beta02` sources: `LazyColumn(…, cacheWindow:
  LazyLayoutCacheWindow = LazyListDefaults.cacheWindow(state), …)`, `LazyListState.requestScrollToItem(index,
  scrollOffset)`, `text/selection/SelectionState.kt`, and `ComposeFoundationFlags.isSmartSelectionEnabled = true` by
  default.
- `androidx.compose.ui:ui-text-android:1.13.0-beta02` sources contain **no `DrawAnnotation`**. The draw-phase-only
  annotation is still unreleased; §8.3 uses the offscreen mask until it ships.

### 0.3 Where the chat code lives

Module rules are the ADR's (§5.2, §6). Package roots follow 03 and 06.

| Module (ADR owner) | Package root | What this section puts there | Starts |
|---|---|---|---|
| `:core:model` (S core) | `io.github.eslamasabry.oc.core.model.transcript`, `.requests`, `.cards`, `.forms` | §2 types, `FormSpec`, card types | 0 |
| `:core:domain` (S core) | `…oc.core.domain.tools`, `…oc.core.domain.errors` | `ToolPresentation` (§2.4), `AgentErrorCause` classifier, `MidTurnPromptGateway` facet (04) | 0 |
| `:core:markdown` (F chat lead) | `…oc.core.markdown` | `StreamingMarkdown`, `Block`, `Inlines`, `PathChipScanner`, `PictureScanner`, `proseForSpeech` (§9.1) | 2 |
| `:engine:transcript` (S chat lead) | `…oc.engine.transcript` (+ `.state`, `.internal`) | §3 | 2 |
| `:engine:delivery` (F) | `…oc.engine.delivery` | §4 | 2 |
| `:engine:requests` (F) | `…oc.engine.requests` (+ `.cards`, `.forms`) | §5, §13 | 3 (see §0.4 C1) |
| `:engine:composer` (F) | `…oc.engine.composer` | §10 rules (drafts, stash, phrases, references) | 3 |
| `:ui:kit-chat` (S kit-chat owner) | `…oc.ui.kit.chat.{transcript,markdown,composer,request,agent,host}` | §6.3 `ComposerHost`, §7–§9 parts, `RevealRegistry`, `KitAgentCard`, `KitForm` | 2 |
| `:ui:kit-content` (S kit-content owner) | `…oc.ui.kit.content.{code,diff}` | `CodeTokenizer`, `KitCodeBlock`, `DiffModel`, `KitDiffView` (§9.2, §9.3) | 3 (plain code block in 2, §17) |
| `:feature:chat` (S chat lead) | `…oc.feature.chat` | §6, §7 | 2 |
| `:feature:chat-composer` (F) | `…oc.feature.chatcomposer` | §10 UI, `FullComposerHost` | 3 |
| `:feature:chat-pages` (F) | `…oc.feature.chatpages` | §11 pages | 4 |

Kit packages use `…oc.ui.kit.chat.*` as 06 §2 says. 03's Konsist allowlist prefix `io.github.eslamasabry.oc.ui.kit.`
already covers them, so no rule changes.

### 0.4 Conflicts found while writing, and how this section handles them

| # | Conflict | Handling |
|---|---|---|
| C1 | ADR §5.2 starts `:engine:requests` in slice 3. 06 (unit KC2) and 07 (C10, C11) put the request card in slice 2, and the daily path cannot run without answering permission asks: the app writes ask-first rules into every OpenCode 2 session (map CHAT-GENUI-45; memory `oc2-stuck-tools-root-cause`). | **Amendment request ADR-09-1 to the coordinator:** create `:engine:requests` in slice 2 with permissions and one-question asks only (§17 unit S2-REQ). Forms, always-allow, cards stay slice 3+. Until §5.2 is amended, S2-REQ is scheduled as the **first** unit of slice 3 and the slice-2 finish line (§17) excludes "answer a permission"; nothing else in this section changes. |
| C2 | ADR §5.2 says `:feature:chat` owns the `ComposerHost` contract, but §6.1 forbids feature → feature dependencies, so `:feature:chat-composer` cannot implement an interface declared in `:feature:chat`. | The slot interface file lives in `:ui:kit-chat` (`…oc.ui.kit.chat.host.ComposerHost`, UI-only types, no engine types), and `MODULE_OWNERS` gets a **file-level** row naming the chat lead as its owner (kit-chat owner reviews). Ownership matches the ADR; only the file's module differs (§6.3). |
| C3 | The map marks the list primitive, Markdown renderer and highlighter as DECIDE. | Decided in §7.1, §9.1, §9.2, each with a fallback and a spike (§16). |
| C4 | genui research says the Flutter form parser ignores OC2 `hidden`; catalog row B-PROTO-022 says hidden fields become preset answers. | The native `FormSpec` adapter honours `hidden` exactly as B-PROTO-022 states, with its own test (§5.5). Whichever Flutter path missed it does not matter once the row is green. |

---

## 1. Rules every chat decision obeys

Each owner rule becomes one mechanism and one check. Reviewers reject a chat change that breaks a row of this table.

| # | Rule (source) | Mechanism in the chat | Check |
|---|---|---|---|
| R1 | Kit only (AGENTS, `design-standard-rule`) | Screens arrange `:ui:kit-chat`/`:ui:kit-content` parts; the feature import allowlist (ADR §6.3 rule 1) | Konsist K1 |
| R2 | Native feel (`android-native-feel`) | Platform ripple via `KitTappable`; stretch overscroll by default (never override `LocalOverscrollFactory`); predictive back from 07; haptics only through `KitHaptic` | Konsist bans `performHapticFeedback`, `clickable` in features |
| R3 | 150–250 ms motion, reduced motion (`micro-animations-wanted`) | All durations from `MotionTokens`; `MotionLevel` Full/Calm/Off decides fade, growth, glide (§8.6) | Konsist bans raw `tween`/`spring` outside the kit |
| R4 | Only named list items animate layout (MOT-5, owner 2026-10-27) | The growing reply, the work-line fold, the step arrival, the row→chat transform. Nothing else in the transcript animates size or placement. | `TranscriptMotionTest` (§15) |
| R5 | One moving thing while a reply runs (MOT-15) | `movingPart(glowOn, level)` in kit-chat: the composer glow travels when on, else the Stop ring turns; under Calm/Off neither | `MovingPartTest` |
| R6 | Keep the green running glow (`composer-green-glow-liked`) | `KitComposerGlow` ported verbatim (Classic default, Soft ring), drawn only in `drawBehind` | screenshot later; owner eye |
| R7 | No glass (`no-glass-rule`) except D10 frost on header and composer while scrolled under | `scrolledUnderTop`/`scrolledUnderBottom` lambdas from the list (§7.6) feed `KitFrost` | 06 frost tests |
| R8 | Plain words, never raw errors (`no-raw-errors-rule`) | `ChatUi` carries `Failure` categories and enums only; `:ui:words` maps them; server text only as `Redacted` in the kit Details fold | B-SEC-104, `NoServerTextInChatUiTest` |
| R9 | Obvious options as visible rows | Footer Copy + More, request answers in place, starters as chips; menus hold only the long tail | review |
| R10 | No new bar or row levels (`no-new-ui-levels`) | Everything over the composer goes into the fixed `KitComposerLayer` slots (§6.4); status folds into the one status line | `ComposerLayerSlotsTest` |
| R11 | Actions name their target (`actions-name-their-target`) | Copy keys carry the target: "Delete this message", "Always allow npm install in neon-breaker?", "Open all changes" | words review |
| R12 | Steer, never queue (`steer-not-queue`) | §4.3 table; no queue control anywhere a runtime steers | B-AGENT-069, `NoQueueChoiceTest` |
| R13 | Hard max widths (`component-max-width-rule`) | Conversation cap 700 dp; prompt bubble ≤ 85 % with 48 dp start inset; composer field ≤ 40 % of window height; request card ≤ 45 % (60 % at 2.5× text) (06 §8) | 06 capacity tests |
| R14 | Frame text ≤ 1.3×, reading text full (`text-scale-cap`) | §9.7 table | 06 text-scale tests |
| R15 | Arabic RTL | Prose `TextDirection.Content`; code, diffs, paths, commands LTR with FSI/PDI isolation; only reading-direction glyphs mirror (B-KIT-001) | `BidiIsolationTest` |
| R16 | Teach hidden gestures (`teach-what-is-hidden`) | Tips `stop-after-step`, `send-later`, `hold-mic` registered with `TeachingRegistry`; each hidden gesture also has a visible path | `ChatTipsRegisteredTest` |
| R17 | Every URL the app did not author goes through `openExternalLink` (AGENTS security) | All Markdown links are `LinkAnnotation.Clickable` → `actions.openLink(url)`; the root `LocalUriHandler` is `ExternalLinkUriHandler`; smart selection off (§7.8) | `LinkPolicyTest`, B-SEC-064 flow |
| R18 | Never echo credentials | Copy transcript and exports go through `Redactor`; card and answer content never stored in journals | B-SEC-094, B-SEC-151 |
| R19 | Gate on capabilities, never on backend kind | Every chat branch reads `ServerCapabilities` or a gateway facet result (`MidTurnPrompt`), never `BackendKind` | Konsist: `BackendKind` not imported in `:feature:chat*`, `:engine:transcript`, `:engine:delivery` |
| R20 | Reconcile by refetch, never replay (AGENTS) | `TranscriptStale`, reconnects and run ends that crossed a drop re-read history (§3.4) | B-CHAT-005, B-PROTO-034 |

---

## 2. The transcript model (`:core:model`)

### 2.1 Vocabulary (frozen 2026-09-20; memory `chat-transcript-turn-model`)

| Word | Meaning | Native type |
|---|---|---|
| prompt | What the person sent: words and attachments. A user-role message that is not a notice and not an inbox item. | `TurnPrompt` (derived) |
| turn | One prompt plus everything the agent did until it handed back. Exactly one footer, absent while it runs. | `Turn` (derived) |
| step | One model call = one assistant message (thought, sentences, tool calls). Step borders are never drawn. | `MessageWithParts` with `role = Assistant` |
| notice | Something that happened during a turn that nobody typed (moved, instructions changed, context added, compaction). Servers file it as role `user`; it never ends a turn. | `Part` of type `Notice`/`Compaction`/`Switch` |
| reply | The prose of a whole turn: what "Copy" copies. | derived string |
| work line | One line per turn that folds every tool call and passing sentence ("Read 3 files · edited 1"). | `WorkLineRow` |
| block | One Markdown block of a reply (paragraph, heading, list item, code, table, picture group, agent block, rule, quote). The unit the list virtualises. | `Block` (`:core:markdown`) |

### 2.2 Types

File: `native/core/model/src/commonMain/kotlin/io/github/eslamasabry/oc/core/model/transcript/Transcript.kt`. No Compose
import: stability comes from `config/compose-stability.conf`, which already marks `…oc.core.model.**` stable (ADR
§9.3). Collections are `kotlinx.collections.immutable` types.

```kotlin
package io.github.eslamasabry.oc.core.model.transcript

public enum class Role { User, Assistant }

public data class Tokens(val input: Long = 0, val output: Long = 0, val reasoning: Long = 0,
                         val cacheRead: Long = 0, val cacheWrite: Long = 0)

public data class MsgTime(val createdMs: Long?, val completedMs: Long?)

/** Dart MessageErrorKind, same seven values and the same fromName/refineFromText rules (models_messages.dart:58). */
public enum class MessageErrorKind { ContextOverflow, OutputLength, ProviderAuth, ContentFilter, Aborted, ModelNotFound, Unknown;
    public companion object {
        public fun fromName(name: String?): MessageErrorKind?          // v1 `name` / v2 `type`; null when blank
        public fun refineFromText(kind: MessageErrorKind?, text: String?): MessageErrorKind?   // "ProviderModelNotFoundError: …", "Not logged in", "OAuth session expired", "invalid api key"
    }
}

public data class MessageInfo(
    val id: String, val sessionId: String, val role: Role,
    val agent: String? = null, val providerId: String? = null, val modelId: String? = null,
    val cost: Double = 0.0, val tokens: Tokens = Tokens(), val time: MsgTime? = null,
    val errorText: String? = null,              // server words; shown only through the Redacted Details fold
    val errorKind: MessageErrorKind? = null,
    val finish: String? = null,                 // raw: stop | length | tool-calls | content-filter | error | …
    val parentId: String? = null,               // explicit parent user message (read-aloud correlation, §12)
)

public enum class PartType { Text, Reasoning, Tool, File, StepStart, StepFinish, Snapshot, Patch,
                            Switch, Notice, Compaction, Unknown }   // Switch/Notice/Compaction = Dart "v2:*" tags

public enum class ToolStatus { Pending, Running, Completed, Error, Cancelled;
    public companion object { public fun parse(raw: String?): ToolStatus }   // unknown → Running (trap T3)
}

public data class ToolOutputFile(val path: String?, val filename: String?, val mime: String?, val url: String?)
public sealed interface ToolSegment { public data class Text(val text: String) : ToolSegment
                                      public data class File(val file: ToolOutputFile) : ToolSegment }

public data class ToolState(
    val status: ToolStatus,
    val title: String? = null,
    val input: JsonObject = EmptyJsonObject,     // kotlinx.serialization JsonObject; complete structured input only
    val inputRaw: String? = null,                // streamed raw input while Pending (never parsed into actions)
    val output: String? = null,
    val outputValue: JsonElement? = null,
    val metadata: JsonObject? = null,
    val outputFiles: ImmutableList<ToolOutputFile> = persistentListOf(),
    val segments: ImmutableList<ToolSegment> = persistentListOf(),   // OC2 ordered text/file interleave
    val startedAtMs: Long? = null, val completedAtMs: Long? = null,
    val pruned: Boolean = false,
    val executedFlag: Boolean = true,            // raw flag; read `ran` instead (trap T2)
) {
    /** Outcome wins over the OC2 beta `executed:false` stamp: completed/error/has time = it ran. */
    public val ran: Boolean get() = executedFlag || status == ToolStatus.Completed || status == ToolStatus.Error ||
        startedAtMs != null || completedAtMs != null
    /** The person's choice, not a failure and not a stop (trap T1). */
    public val declined: Boolean get() = status == ToolStatus.Error && output.declinedText()
}

public data class Part(
    val id: String, val messageId: String, val sessionId: String,
    val type: PartType, val rawType: String,     // rawType keeps "v2:notice", "subtask", unknown names
    val text: String = "",
    val callId: String? = null, val toolName: String? = null, val tool: ToolState? = null,
    val mime: String? = null, val filename: String? = null, val url: String? = null,
    val synthetic: Boolean = false, val ended: Boolean = false,
    val noticeMetadata: ImmutableMap<String, String> = persistentMapOf(),
) {
    /** Exactly Dart Part.isRenderable: not synthetic and (non-blank text/reasoning, or tool, or file).
     *  Notice/Switch/Compaction rows are drawn by their own path (Dart `v2VariantPart`), not through this flag. */
    public val isRenderable: Boolean get() = !synthetic && when (type) {
        PartType.Text, PartType.Reasoning -> text.isNotBlank()
        PartType.Tool, PartType.File -> true
        else -> false
    }
}

public data class MessageWithParts(val info: MessageInfo, val parts: ImmutableList<Part>)

public data class SessionRevertFacts(val messageId: String, val partId: String?, val snapshot: String?,
                                     val files: ImmutableList<String>, val fingerprint: String)
```

`ConversationRef` (profile + session) is 03's type and is reused unchanged.

### 2.3 Facts the model must keep (the traps; each is a test)

| # | Trap | Rule | Row / source |
|---|---|---|---|
| T1 | A declined tool | status `error` + output contains "user declined" or "user rejected permission" → `declined`; renders as the person's choice, never Failed, never Stopped | B-AGENT-186, `test/tool_state_test.dart:5` |
| T2 | OC2 beta `executed:false` on tools that ran | `ran` (outcome wins) | `tool_state_test.dart:36` |
| T3 | Unknown status | `Running`; `cancelled` renders as Stopped | `tool_state_test.dart:58` |
| T4 | Pruned output | `pruned` (v1 `time.compacted`, v2 `time.pruned`): nothing opens | B-CHAT-003 |
| T5 | Output files | only explicit file signals (`filePath`, `outputPath`, attachments; depth ≤ 6, ≤ 8 files); plain URLs and path-like prose are not files; `read` attachments inherit the requested name | B-CHAT-114 |
| T6 | Output whitespace | completed output keeps leading/trailing whitespace; JSON object output is pretty-printed; pending exposes raw input unchanged | B-CHAT-112, B-CHAT-113 |
| T7 | Error kinds | `fromName` + `refineFromText`; `sessionErrorFallbackText` gives fixed words for empty server messages; `errorHeadline` = first non-stack line without `XError:` | map CHAT-GENUI-01, B-CHAT-069 |
| T8 | Sub-agent child id | from `metadata.sessionId`, `sessionID`, `session_id` and legacy spellings | B-CHAT-110 |
| T9 | Variant rows | `v2:switch`, `v2:notice`, `v2:compaction` map to `Switch`/`Notice`/`Compaction`; an unknown `v2:*` becomes a generic notice; an unknown message that is only an id-like word draws nothing | B-CHAT-121 |
| T10 | Instruction events | unexpected ids never create messages; instruction and note text never shown | B-CHAT-097 |

### 2.4 One function words every tool (`:core:domain` `ToolPresentation`)

It is used by the transcript rows, the timeline sheet, find, copy transcript, permission titles, the live line and
notifications (B-CHAT-111), so it sits in `:core:domain` (ADR §4 row 27: shared by several engines). It returns enums
and arguments, never copy; `:ui:words` turns them into words.

```kotlin
package io.github.eslamasabry.oc.core.domain.tools

public enum class ToolKind { Read, List, Glob, Grep, Shell, Edit, Write, Patch, WebFetch, WebSearch, Task, Todo,
    Question, Lsp, Skill, Plan, WorktreeSetup, ConnectorSearch, BackgroundNotice, AgentMessage, Card, Generic }

public data class ToolView(
    val kind: ToolKind,
    val genericWords: String?,            // only for Generic: toolIdWords("mcp__github__create_issue") = "create issue"
    val subject: String?,                 // path | command | pattern | url, shown mono LTR
    val detail: ToolDetail?,              // FromLine(n) | Matches(n) | LineCount(n) | …
    val status: ToolStatusView,           // Running | WaitingForYou | Done | Failed | Stopped | Declined
    val durationMs: Long?,                // words: tenths under a minute, m:ss after (B-CHAT-109)
    val added: Int?, val removed: Int?,
    val childSessionId: String?,          // sub-agent (T8)
)

public object ToolPresentation {
    /** Lower-cased id switch (map CHAT-GENUI-14): read, list, glob, grep, bash|shell, edit, write, patch|apply_patch,
     *  webfetch, websearch, task|subagent, todowrite|todo, question, lsp, plan, worktree_setup, skill,
     *  sendmessage|send_message, toolsearch, exact card names (§13.3), task_notification. Never a raw id as words. */
    public fun of(part: Part, waitingForYou: Boolean): ToolView
    /** Live-line words for a running step ("Running npm test"); named=false when the step's own row is right above. */
    public fun runningPhrase(view: ToolView, named: Boolean): LivePhrase
}
```

`waitingForYou` is passed in by the caller that knows about requests (a blocked call or a running `question` tool says
"Waiting for you", never Running; AUTO-15).

---

## 3. The transcript engine (`:engine:transcript`)

### 3.1 Public API

Files under `native/engine/transcript/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/transcript/`. Signatures
are frozen in `contracts/s2.md` at the start of slice 2.

```kotlin
@SingleIn(ConnectionScope::class)
public class TranscriptEngine @Inject internal constructor(/* gateway, dispatchers, clock, firstPaint, settings, diagnostics */) {
    /** Ref-counted. Keeps at most 3 transcripts warm (LRU); a handle with no holder closes after 60 s [new: bounds memory]. */
    public fun open(ref: ConversationRef): TranscriptHandle
}

public interface TranscriptHandle : AutoCloseable {
    public val state: StateFlow<TranscriptState>
    public suspend fun loadOlder(): Outcome<Unit>
    public fun refreshRecent(reason: RefreshReason)                 // debounced 150 ms (map CHAT-GENUI-03)
    public fun setExpanded(key: FoldKey, open: Boolean)             // §3.9; publishes at once (user action)
    public fun collapseAll()
    public suspend fun materialize(target: JumpTarget): Outcome<Int> // load pages/open folds until the row exists; returns its index
    public fun onBlocksVisible(keys: ImmutableList<String>)         // path-link checks for visible blocks (§9.1)
    public val pathLinks: StateFlow<ImmutableMap<String, PathLinkState>>   // Confirmed | Missing(until) | Unknown; side table
    public val search: TranscriptSearch                             // §3.10
    override fun close()
}

public data class TranscriptState(
    val phase: TranscriptPhase,                       // Loading | Excerpt(ExcerptRows) | Live | Failed(Failure)
    val rows: ImmutableList<TranscriptRow>,           // NEWEST FIRST: index 0 is the bottom of the reverse list (§7.1)
    val index: ImmutableMap<String, Int>,             // message id / part id / call id → row index (jumps, find)
    val run: RunFacts,                                // busy, retrying, lastOutcome, crossedDrop, connects
    val inbox: ImmutableList<InboxPrompt>,            // prompts waiting in the server inbox: never prompt rows (§4.3)
    val older: OlderState,                            // None | Available | Loading | NeedsReload | Exhausted
    val revert: SessionRevertFacts?,                  // staged undo boundary, if any
    val unanswered: UnansweredFacts?,                 // newest prompt with no answer, and why (§3.10)
    val stall: StallVerdict?,                         // §3.10
    val revision: Long,                               // +1 per publish; tests count publishes with it
)
```

The engine exposes no copy, no Compose types, no `android.*` (ADR §9.1). Heavy work (passes, Markdown parsing,
search index) runs on the engine's lane and on `AppDispatchers.default`, never on the main thread.

### 3.2 Inside the engine

- **One lane per connection graph:** `dispatchers.default.limitedParallelism(1)` (ADR §9.5). Every event, history
  result and user command is applied on it, in arrival order. Parsing work that is pure and large (a full Markdown
  parse of a finished 50 KB reply) is computed with `withContext(dispatchers.default)` and comes back as a value.
- **Working model** (`internal class TranscriptModel`, one per open handle):
  - `messages: LinkedHashMap<String, MessageSnapshot>` in server order. A `MessageSnapshot` is immutable and is
    **replaced** on every change, so identity (`===`) says "unchanged" to the turn cache (Flutter's `identities`).
  - `partVersions: HashMap<PartKey, Long>`, `messageVersions: HashMap<String, Long>`, `eventVersion: Long`. Every
    message or part event does `++eventVersion` and stamps it **synchronously** on the lane.
  - `deferredParts: HashMap<String /*messageId*/, MutableList<Part>>` for parts whose message is not loaded yet.
  - `deferredDeltas: HashMap<PartKey, StringBuilder>` for deltas whose part is not loaded yet.
  - **Bounds [new]:** at most 256 deferred keys and 1 MiB of deferred text per handle. On overflow the oldest entry is
    dropped and the handle schedules a recent-history refresh. Flutter had no cap; a hostile or broken stream could grow
    memory without one. `DeferredBoundsTest` covers it.
  - `liveText: HashMap<PartKey, StreamingMarkdown>` for text parts that are receiving deltas (§3.8).
- **Generation fences:** each history load carries `HistoryScope(gatewayEpoch, locationRevision, profileId, sessionId,
  loadGeneration)`. A result whose scope no longer matches is dropped. Each connection generation is a child scope, so
  cancelling it ends stale work (ADR §9.5).

### 3.3 Event apply table

Input: `GatewayEvents.project` (04 §6.5) filtered to the handle's session id. Every row keeps its Flutter test.

| `GatewayEvent` | Apply | Must not be lost |
|---|---|---|
| `MessageUpserted(info)` | Replace the snapshot; flush deferred parts for it. A user message first tries pending-send reconcile (§4.6) in delivery; the transcript just stores it. An assistant message with `errorText` sets `run.lastError`; a newer assistant step clears a stale one. | A late edit to old history is not a new row: if the message is not loaded and is older than the oldest loaded one, ignore it unless it could be in the recent window, then `refreshRecent(LateEdit)`. |
| `PartUpserted(part)` | Replace the part (whole part wins over accumulated deltas for that part); if its message is unknown, put it in `deferredParts`. A text part with an open `StreamingMarkdown` that receives a full text equal to `prefix + more` appends; otherwise full re-parse (§3.8). | Version stamp before anything else. |
| `PartDelta(field = Text \| Reasoning)` | Append to the part's text; if the part is unknown, append to `deferredDeltas`. | Coalesced publish, never coalesced apply. |
| `PartDelta(field = ToolInputRaw)` | Append to the pending tool's `inputRaw` only. Never parse it (cards, §13.3). | — |
| `TranscriptStale` | `refreshRecent(Stale)` | B-CHAT-005 (a lost tool result settles and the chat re-reads) |
| `Notice(…)` | Stored as a notice part (T9, T10) | notices never end a turn |
| `RunStarted` | `run.busy = true`; `run.startedAtMs` if unset | `session.step.started` restores a lost busy (04 §7.6) |
| `RunEnded(outcome)` | `run.busy = false`, `run.lastOutcome = outcome`; if `run.crossedDrop` → `refreshRecent(RunEndedAfterDrop)` | `RunEnded(Failed)` is never followed by an idle that overwrites it (04) |
| `RunRetrying(…)` | `run.retry = RetryFacts(attempt, nextAtMs, tag)` | the retry ticker runs only while retrying (§4.1) |
| `RevertChanged` | update `revert`; a history reset with no staged revert removes messages `>= removedFrom` **at once** and clears deferred maps before reloading | never reveal a stale staged tail |
| `InboxChanged` | maintain `inbox`; a user message whose id is an inbox id is kept out of prompt rows (it joins the newest turn, `9de34551c`) | the optimistic copy is hidden from the transcript |
| `SessionDeleted` | phase `Failed(NotFound)`; stop the stall tracker | — |
| `Connected` after a drop | `run.connects++`; `run.crossedDrop = run.busy`; `refreshRecent(Reconnected)` | reconcile by refetch, never replay (R20) |
| `PermissionAsked/Settled`, `FormCreated/Settled` | not handled here: `:engine:requests` owns them | — |

### 3.4 History: open, older pages, refresh, merge

1. **Open.** `open(ref)` → phase `Loading` → if the connection engine's first-paint cache has an opening excerpt for
   this conversation (`oc.chatTail.<profileId>`, owned by `:engine:connection` per 05 §6.2), publish
   `Excerpt(rows)` at once: read-only text rows, never merged into history (map CHAT-GENUI-04; B-STATE-121). Then read the
   newest page through `ServerGateway.messagePage(sessionId, cursor = null, limit = 100)` (or the staged-revert boundary
   read when `revert != null`, map CHAT-GENUI-03), with `versionAtStart = eventVersion`.
2. **Merge (`mergeHydrated`, a pure function ported from `_mergeHydratedMessages`):** for every hydrated message, keep
   the live copy if `messageVersions[id] > versionAtStart`; parts likewise by `partVersions`; live parts missing from
   the snapshot but newer are appended; messages absent from the snapshot are kept only if they are a pending send, newer
   than the read, or `preserveUnseen` (older-page loads); withdrawn prompts (`oc.withdrawnPrompts.<id>`) are skipped.
3. **Older pages.** `loadOlder()` reads with the saved cursor. A cursor already used (`usedOlderCursors`) or HTTP 400/410
   → `older = NeedsReload` ("Reload" row), never a loop. Pages arrive newest-first and are prepended at the far end of
   the newest-first row list (§7.4).
4. **Refresh triggers** (`refreshRecent`, debounced **150 ms**): resume while watching, inbox delivered, skill changed,
   `TranscriptStale`, reconnect, end of a run that crossed a drop, late edit. A refresh never shows a skeleton or an
   error over a transcript already on screen (map CHAT-GENUI-54).
5. **Reader place.** The engine does not scroll. It publishes; §7.4 keeps the reader's place by key.
6. **Link down while messages are on screen:** the transcript says nothing; the connection line speaks. Never "isn't
   answering" after a trip to the background (owner 2026-10-27).

### 3.5 Publish policy

```kotlin
internal class Publisher(private val clock: Clock, private val scope: CoroutineScope, private val publish: () -> Unit) {
    private var lastPublishMs = Long.MIN_VALUE; private var pending: Job? = null
    /** Called on the lane after every apply. B-CHAT-096 and the publish-count test pin it. */
    fun changed(kind: ChangeKind) {
        val now = clock.nowMs()
        if (kind == ChangeKind.UserAction || now - lastPublishMs >= 50) { pending?.cancel(); pending = null; doPublish(now); return }
        if (pending == null) pending = scope.launch { delay(50 - (now - lastPublishMs)); pending = null; doPublish(clock.nowMs()) }
    }
    private fun doPublish(now: Long) { lastPublishMs = now; publish() }
}
```

- Leading edge: the first change after ≥ 50 ms of quiet publishes at once (the first delta is never late). Trailing
  edge: changes inside the window publish once at its end. Result: ≤ 1 publish per 50 ms per handle (ADR §9.3).
- `ChangeKind.UserAction` (fold toggles, collapse all, jump materialisation) publishes at once.
- **Nobody watching [new]:** when `state.subscriptionCount == 0` (screen gone, `WhileSubscribed(5_000)` expired), the
  handle keeps applying events to the model but skips display passes and row building; the first new subscriber gets one
  full derive. This is the chat's part of S-16 (hidden while an agent streams, ≤ 5 % of a core).
- Every `delay`/`conflate`/`debounce` in this module carries a comment naming its catalog row (ADR §9.5).

### 3.6 Display passes and the per-turn cache

Ported as pure functions in `internal/passes/`, each with its Flutter tests. Never remove a message in a pass;
neutralise it (the 2026-10-28 index bug, `43fabb514`).

| Pass | Ported from | What it does | Rows |
|---|---|---|---|
| `settleStaleLive(messages, busy)` | `lib/domain/transcript_settle.dart` | a running compaction line is hidden (`compactionPassed`, message kept) once anything follows it or the chat is not busy; a running tool reads finished when the same turn wrote words after it or the chat is idle, unless `background:true`; a late `running` cannot revive a finished call; returns the **same list instance** when nothing changed | B-CHAT-116, B-CHAT-117 |
| `markFoldedWork(turn)` (finished turns only) | `_markFoldedWork` | passing words between steps and mid-turn notices fold under the work line; an explanation (> 1 line, a paragraph break or a code block) before the last step stays in view ("the work folds; the prose never hides") | `transcript_settle_test.dart:61,98` |
| `mergeTextParts(parts)` | `_mergeTextParts` | adjacent text parts of one reply merge in **linear** time, tracking "ends with newline" incrementally; first part keeps identity; whitespace-only fragments skipped | B-CHAT-096 (5,000 fragments) |
| `turnActionOwners(turns)` | `_turnActionOwners` | the one message that carries each finished turn's footer: the step that ends the turn if it draws anything, else the last step that does; the running turn has none | `kit_message_test.dart:155` |
| `deriveTurns(messages)` | `transcript_turns.dart` | groups into turns by `isPrompt`/`endsTurn` (never `role != assistant`); inbox ids are not prompts; a prompt's thought heading titles the next step only when its first line is a marked heading | B-CHAT-121 |

**Cache (`TurnCache`):** turns before the newest prompt are kept while every member snapshot is identical (`===`). A
stream flush recomputes only the newest turn. `TranscriptCounters.displayPassMessages` counts messages passed through the
passes; the ported `chat_rebuild_budget_test` asserts ≤ 2 × updates (§15).

### 3.7 Rows: the flattened list

The engine flattens turns into **block-level rows**, newest first. Every row is an immutable data class; unchanged rows
keep their instance across publishes, so the list skips them by identity (ADR §9.3, research rendering-chat §4.4).

```kotlin
public sealed interface TranscriptRow {
    public val key: String              // stable, String, never an index or hashCode
    public val kind: RowKind            // LazyColumn contentType
    public val turnId: String
    public val position: TurnPosition   // First | Middle | Last | Only: spacing and the 700 dp column, no frames
}

public enum class RowKind { Prompt, Prose, Heading, Quote, ListItem, Code, Table, Pictures, AgentBlock, Rule, Thought,
    WorkLine, TimelineStep, Notice, Marker, BackgroundResult, CardCall, AssistantError, Footer, Older, Excerpt }
```

| Row | Key | Built from | Notes |
|---|---|---|---|
| `PromptRow` | `prompt:<messageId>` | user message | text as `Inlines` with path chips counted, attachment refs, `unanswered` mark |
| `BlockRow` (Prose, Heading, Quote, ListItem, Code, Table, Pictures, AgentBlock, Rule) | `b:<partId>:<startOffset>` | `:core:markdown` blocks of an assistant text part | `live = true` only for the tail block of a part receiving deltas; `origin = Streamed \| Loaded` (§8.2) |
| `ThoughtRow` | `thought:<partId>` | reasoning part | folded under its first line unless the global reasoning toggle is on; placeholder thoughts dropped |
| `WorkLineRow` | `work:<turnId>` | all tool parts and folded passing words of a turn | `WorkState` (Running, WaitingForYou, Done, EndedFailed, Stopped), counts, open/closed |
| `TimelineStepRow` | `step:<turnId>:<callId or partId>` | one entry per step when the work line is open | an open work line of 200 calls is 200 lazy rows; "Show N earlier steps" folds the oldest |
| `NoticeRow`, `MarkerRow` | `notice:<partId>`, `marker:<partId>` | notice/switch/compaction parts | marker = hairline-word-hairline; a failed newest compaction offers "Compact again" |
| `BackgroundResultRow` | `bg:<partId>` | background shell/agent results | one line, opens to command/output; protocol text only under Details |
| `CardCallRow` | `card:<callId>` | exact card tool calls (§13.3) | carries only the call facts; the card itself comes from the requests side table (§6.1) |
| `AssistantErrorRow` | `err:<messageId>` | assistant error | kind → fix table (§9.6); quiet when more steps followed |
| `FooterRow` | `footer:<turnId>` | turn owner (§3.6) | Copy, More, meta (model name, time, usage when the timestamps toggle is on), changed files |
| `OlderRow` | `older` | `OlderState` | "Load older" or "Reload"; always the last row (top of the screen) |
| `ExcerptRow` | `excerpt:<n>` | opening excerpt | read-only; replaced wholesale by live rows |

**No copy per publish.** The engine keeps its rows in a `PersistentList` **oldest first** (new rows and the changing
tail sit at the cheap end of the persistent vector) and publishes `rows = NewestFirst(list)`, an O(1) read-only
`ImmutableList` view in `:core:model` (`…core.model.transcript.ReversedImmutableList`, `get(i) = list[size - 1 - i]`). Replacing the tail
block is one O(log n) `set`; a publish never copies 5,000 rows. `TranscriptState.index` stores newest-first indices.
`ReversedViewTest` checks equality, iteration order and that the view's identity changes only when the list does.

The live line, optimistic prompt bubbles and pending skeletons are **not** engine rows: they come from
`:engine:delivery` and are placed by the list (§7.1), so the transcript never waits on delivery state.

### 3.8 Live text → Markdown blocks

```
PartDelta(Text) ─► model.text += delta ─► liveText[part].append(delta)            (lane)
                                          StreamingMarkdown (core:markdown)
                                            • CR/LF normalised across chunk edges
                                            • JetBrains StreamingMarkdownFile.append(…)
                                            • stable children → cached Blocks (instance kept forever)
                                            • unstable tail → fresh tail Block(s)
                                            • open fence: Block.Code(open = true): append lines, skip re-parse until a
                                              line starts with the closing fence; never highlighted while open
                                          ─► publish (≤ 1 / 50 ms) re-emits only the tail BlockRow(s)
```

- A `StreamingMarkdown` lives while its part receives deltas. When the part ends (`PartUpserted` with the full text, or
  the step ends), the engine compares: equal text keeps the blocks; different text (OC2 `session.text.ended` carrying a
  rewritten text, an edited earlier block) runs a full parse on `default` and keeps the keys of unchanged-prefix blocks.
- Finished replies are cached by `(partId, text length, text hash)` in an LRU of 512 parts per handle.
- Counters: `TranscriptCounters.markdownParses` (full parses) and `tailReparses` (unstable-tail re-parses). The ported
  `perf_chat_test` "ten tail updates" asserts 10 tail re-parses and **0** rebuilt settled code or heading blocks.

### 3.9 Fold state (`ExpansionStore`)

- Keys: `work:<turnId>`, `tool:<callId>`, `reasoning:<partId>`, `older-steps:<turnId>`. Kept per handle, so item recycling
  cannot lose them (Flutter `_ExpansionStore`).
- Global toggles (`oc.transcript.reasoningExpanded`, `oc.transcript.timestampsVisible`) come from `:engine:settings`.
  **Flipping a global toggle drops every per-part override made under the old value** (map CHAT-GENUI-13).
- A work line opens by itself only for an unrecovered failure or a produced file. Recovered failures stay folded and
  neutral (memory `chat-transcript-turn-model`).
- A long running turn keeps the newest steps in view and folds older ones under "Show N earlier steps".

### 3.10 Other engine parts (each a port with its tests)

| Part | Ported from | Constants and rules | Rows |
|---|---|---|---|
| `TranscriptSearch` | `lib/domain/transcript_search.dart` | per-message cache; offsets across text, reasoning, tool words (as `ToolPresentation` names them), file names; binary, synthetic and redacted protocol rows excluded; updates on streaming, older-page prepend and deletion; produces `FindMarks` per row key | B-CHAT-115 |
| `TurnStallTracker` | `lib/domain/turn_stall.dart` | 5 s tick; after **45 s** of silence a bounded probe (≤ **10 s**); running tools hold the clock; a waiting request is not a stall; probes carry opaque tokens; only this session's message/tool output is progress; evidence is safe booleans; bounded memory; blank ids ignored. Every **15 s** an open turn re-reads waiting permissions and questions (a lost ask); if that read fails, running tools stop holding the clock off | B-CHAT-118, B-CHAT-119 |
| `StuckCommand` | `stuck_command.dart` | a foreground command still running after **15 min** → `stall = Stuck(command ≤ 40 chars, cd x; dropped, since)`; Send becomes "Stop and send" for agents that run a message after the reply | map CHAT-GENUI-23 |
| `BackgroundTasks` | `lib/domain/background_tasks.dart` | running only while: the starting step said so, no ending notice names it (by tool-use or task id), started < **6 h** ago, the agent process was not seen down since; named by program not folder | B-CHAT-007, 008, 011 |
| `RunResult` | `lib/domain/run_result.dart` | server facts only; newest step decides; changed files only from completed executed edit tools; bounded | B-CHAT-068, 100, 101 |
| `Unanswered` | `_unansweredTurn`, `no_reply_reason.dart` | the newest prompt with no words, steps or error → "Not answered"; reason from `AgentTurnFailureMemory` (`oc.agentTurnFailures.<id>`, redacted, survives restart) | map CHAT-GENUI-52 |
| `ContextShare` | `lib/state/context_usage.dart` | newest reply's tokens / that model's window; unknown if either missing; calm < 70 %, warning 70–94 %, full ≥ 95 %; shown from 50 % | B-CHAT-019, B-CHAT-057 |

### 3.11 Counters and trace sections

`internal object TranscriptCounters` (reset per test; no-ops in release builds through a `const val ENABLED`):
`publishes`, `displayPassMessages`, `markdownParses`, `tailReparses`, `rowsReplaced`. Trace sections (`androidx.tracing`
2.0.5 through `:core:common` `Trace`): `chat.apply`, `chat.derive`, `chat.publish`, `chat.openToFirstTranscript`
(attribute `source = excerpt | live`), `chat.openToFresh`. They feed the in-app Performance report the owner pastes into
issues, with the same names as the Flutter marks (map §7).

---

## 4. A running turn, from the person's side (`:engine:delivery`)

`:engine:delivery` sits above transcript and requests in the engine order (ADR §6.2), so it is where "what is the
agent doing right now" is decided once, for the chat, the notification and the widget alike.

### 4.1 The live line (`ConversationActivity`)

```kotlin
@SingleIn(ConnectionScope::class)
public class ConversationActivity @Inject internal constructor(/* transcript, requests, delivery state, clock */) {
    public fun liveTurn(session: SessionId): StateFlow<LiveTurn?>   // null = no turn running
}

public data class LiveTurn(
    val turnId: String?,                 // null before the server has a turn (local guess)
    val phase: LivePhase,                // Sending | WaitingServer | WaitingFirstWord | Thinking | Working | Writing |
                                         // WaitingForYou | Retrying | Stuck
    val sinceMs: Long,                   // since-time; words come from KitSince
    val slow: Boolean,                   // > 8 s in a starting phase (KitTurn "starting slow")
    val step: ToolView?,                 // Working: the named step (ToolPresentation.runningPhrase)
    val secondary: LiveSecondary?,       // newest printed line of a running command (no <shell_metadata>) | PlanStep(3, 5)
    val retry: RetryFacts?,              // attempt, nextAtMs, cause; countdown only for a future time
    val stopAfterStep: StopAfterStep?,   // Pending(canUndo) while the steering note waits in the inbox
    val joinedWait: JoinedWait?,         // a message sent mid-step: "will be read when …" (dc3915e47)
)
```

Rules (map CHAT-GENUI-08, 23, 40, 50; each a test in `ConversationActivityTest`):

1. `running = transcript.run.busy || delivery.sending || delivery.localTurnSinceMs != null`. The local guess is set the
   moment a send is accepted for dispatch and cleared on idle, failure or Stop.
2. The server's own words win: a finished step that is not `tool-calls`, or has an error, with the conversation idle,
   ends the turn.
3. **A waiting request makes the phase `WaitingForYou`**, never a spinner, never "Thinking…" (AUTO-15). The step that
   asks is not repeated above its card.
4. A provider retry is a condition of the running turn, not a request: phase `Retrying`; "rate limit" only when it is
   one or the server gave no reason; a 1 s ticker runs only while `retry.nextAtMs` is in the future; a stuck "0:00"
   becomes plain "Retrying…".
5. `Stuck` comes from `StuckCommand` (§3.10): the live line names the command and its age with "may be stuck", and the
   one chip-row Stop turns amber and is worded for a stuck step (`chat_composer_region.dart` `_stopRowChip`). A silent
   turn classified by the stall tracker shows a `KitTurnStall` notice with its own Stop and Details actions
   (`turn-stall-stop`, `turn-stall-details`).
6. A `session.idle` without a prior busy clears the local guess.
7. The live line is placed under the reply that runs and above any waiting prompt (§7.1).

### 4.2 The send pipeline (bubble first, never send twice)

```kotlin
@SingleIn(ConnectionScope::class)
public class PromptDelivery @Inject internal constructor(/* gateway pair, transcript, requests, queue store, receipts, clock */) {
    public val pending: StateFlow<ImmutableList<PendingSend>>     // optimistic bubbles + their phase
    /** Returns once the bubble exists and any durable marker is on disk; the rest runs in the connection scope,
     *  so leaving the chat never cancels a send. */
    public suspend fun send(request: SendRequest): Outcome<LocalSendId>
    public suspend fun sendInsteadOfDraft(request: SendRequest): Outcome<LocalSendId>  // chips, Continue, Send again
    public suspend fun stop(session: SessionId, mode: StopMode): Outcome<Unit>         // Now | AfterThisStep
    public suspend fun undoStopAfterStep(session: SessionId): Outcome<Unit>
    public suspend fun checkAgain(id: LocalSendId): Outcome<ReceiptLook>               // looks, never sends
    public suspend fun resend(id: LocalSendId, confirmedByPerson: Boolean): Outcome<LocalSendId>
}

public data class SendRequest(val ref: ConversationRef, val text: String, val mentions: ImmutableList<Mention>,
                              val attachments: ImmutableList<PromptAttachment>, val agentMentions: ImmutableList<AgentMention>,
                              val typedText: String /* what the person typed, before references were folded */)
```

Order (port of `_performSend`; every step is a test in `PromptDeliveryTest`):

| # | Step | Rule | Row |
|---|---|---|---|
| 1 | Route by what the person **typed**: `^/(\S+)(?:\s+(.*))?$` → command; `^!([^\s!][\s\S]*)$` → shell | match before references are folded in (folding first made `/new` + a reference send as chat) | map CHAT-GENUI-31 |
| 2 | Refuse while a staged undo is unresolved | `Outcome.Err(Refused(UndoUnresolved))` → "Resolve the undo before sending" | B-CHAT-106 |
| 3 | Stop first for "Stop and send" agents; fold staged review references (§10.7) | — | B-CHAT-099 |
| 4 | Offline: wait ≤ **10 s** for the transport, then the offline queue, or "draft saved" on servers without a queue | a send after a resume waits for the connection instead of queueing (`4e635b73b`) | B-STATE-039 |
| 5 | Write the **durable dispatch marker** before the composer clears, for queue-capable servers (not for steers or voice-conversation sends) | marker refused by storage → the prompt stays unsent and the words stay | B-STATE-042 |
| 6 | **Bubble first** (`PendingSend` published) unless steering or voice conversation (those stay transport-first: they need the exact message id); composer cleared through `ComposerHost`; follow the live edge; `localTurnSinceMs` set | trace `chat.sendToBubble` (`order = bubble_first \| transport_first`) | map CHAT-GENUI-32 |
| 7 | `awaitActionTransport()`; `waitForSessionSelection(session)`; re-check the gateway epoch | a changed connection dispatches to neither server | B-STATE-119 |
| 8 | Dispatch: `CorrelatedPromptGateway.promptWithMessageId` when `clientPromptMessageID`, else `prompt(...)` with delivery per §4.3 | — | B-PROTO-011, B-PROTO-025 |
| 9 | Accepted: clear the marker, remember in prompt history, learn the phrase chip (§10.4), trace `chat.sendToAck`; if Stop was pressed while on the wire, stop again now | — | map CHAT-GENUI-40 |
| 10 | Failed: remove the bubble; read the turn (a failed request says nothing about a turn already running); if bytes left the device, keep the marker and say "We couldn't confirm whether this was sent" with Check again (never auto-resend); else put text and attachments back, prepended to what was typed since; the reason goes to the status line, not a snackbar | B-STATE-040, B-STATE-043 |

`sendInsteadOfDraft` is the same pipeline but leaves the composer untouched, and a failed chip send never leaves its
words in the box ("pongSay pong", 2026-10-27). The status line's Try again repeats that same send.

### 4.3 Steer, never queue

Decided per conversation from capabilities and the `MidTurnPromptGateway` facet (04 §11.1), never from the backend kind
(R19). Port of `docs/design/steering-contract.md`.

| Situation | Send does | Composer words (`:ui:words`, `strings_chat_composer.xml`) |
|---|---|---|
| Idle | ordinary send, starts a turn | Send |
| Running, `capabilities.promptSteering` | `delivery = Steer` | "Add to this turn"; after acceptance "Added to this turn" (never "Queued") |
| Running, `midTurnPrompt == JoinsTurn` (Paseo Claude Code, Codex, OpenCode, Pi) | normal send, delivery unset (Paseo steers by itself) | "Add to this turn" |
| Running, `midTurnPrompt == RestartsTurn` (Paseo ACP, Copilot) | stop, then send | "Stop and send" + "Stops the current reply and starts again with your message." |
| Running, `midTurnPrompt == null` (agent not loaded yet) | nothing; text stays editable | no promise of either |
| Running, no steering and no inbox (OpenCode-1-style legacy recognition) | after-reply send | "Sends after this reply" (the only place a queue word appears) |
| Offline | offline queue (§4.5) | "Send when back online" |

- There is **no** delivery choice, long-press option, shortcut or remembered setting that selects queue delivery while a
  runtime steers. An old stored `PromptDelivery.queue` value is ignored (`NoQueueChoiceTest`).
- Several plain-text steers still waiting are taken back and merged into one message; steers with files and anything
  the agent already took (HTTP 409) stay as they are.
- A waiting inbox prompt shows **once**, as `KitQueuedMessage` above the composer, with states waiting, sending, not
  confirmed (after 8 s), reached server, failed, afterThisReply, addToThisTurn, contextUpdate. Its menu: Edit (back to
  the composer with "Returned to your draft · Undo"), Cancel (Undo sends it again the same way; asks first only if it
  carries files), Check again, Send again. Its transcript copy is hidden (§3.3).
- Codex: an accepted steer confirms delivery to that turn only; a stale or idle steer fails without starting a turn and
  is never retried as a start (B-AGENT-069, B-AGENT-070). A steer cannot change the running turn's model or effort; the
  choice applies to the next ordinary turn.

### 4.4 Stop

- One Stop, at the trailing end of the composer chip row, for the whole running turn (owner 2026-10-30; reference build
  `_stopRowShown = agentWorking && !subagentReadOnly && !waitsForYou`). There is no Stop on the field button and none on
  the live line; a stuck step turns the same Stop amber. The only other Stop is the action inside a stall diagnosis
  notice (§4.1 rule 5). It is absent while a request waits (the card owns the composer then; Reject is the way to say
  no) and in a read-only sub-agent chat. The map's "moves to the live line" line predates this and is not ported (code
  wins).
- **Tap:** if the send is still waiting (transport, model choice, storage) it is called off and the words come back;
  if the request is on the wire, stop now and again once the server took it. Wait ≤ **5 s** for the transport, haptic
  `Commit`, and `noteStopFromThisApp` **before** the call, so the server's interrupt does not read as another device.
- **Hold** (only where a send steers): "Stop after this step" sends a steering note; the live line reads "Stopping after
  this step · Undo" while the note waits in the inbox; servers without an inbox have nothing to take back, so no Undo.
  The tip `stop-after-step` shows once a reply has run **10 s**, never while a card waits.
- Stop ignores taps for **800 ms** after a send (`MotionTokens.STOP_IGNORE_AFTER_SEND`, fix `49d067f2d`): a double tap
  on Send never stops the turn.
- A stop by the person is never an error, never "Failed" in Inbox or the list (`session_stop.dart`); a stop from
  elsewhere names who or what stopped it from `TurnInterruptReason` (user elsewhere, an hour idle, shutdown with
  Continue, superseded, unknown).

### 4.5 Offline queue, markers and receipts

Stored format, limits and migration are 05's (`oc.offlineQueue`, byte-for-byte dispatch markers). Behaviour rows owned
by `PromptDelivery` + `OfflineQueueStore`: B-STATE-008, 009, 033–046, 062, 066, 119 and flows E2E-10, E2E-11. Rules
the chat surfaces: a send that left the device without a confirmed outcome never flushes on its own, not even after a
restart; "Check again" only looks (command receipt lookup); the queue says what it dropped and why; an entry the
transcript already shows is removed so a second send is not one tap away.

### 4.6 Optimistic bubble reconcile

`PendingSend(localId = "local-<ms>-<µs>", dispatchedMessageId?, text, attachments, createdAtMs, canonicalId?,
requestComplete)` is matched against the transcript by dispatched id, canonical id, or text plus attachment **file
names** (not URL or type: servers answer with their own locations; OC2 re-encodes images). Synthetic server parts are
ignored. The ViewModel calls the pure `PendingReconcile.visiblePending(pending, transcript.rows)` inside its `combine`
so a frame never shows both the bubble and the server copy (§6.2). `promptEchoTextOnly` servers (Paseo) keep the photos
on the bubble.

---

## 5. Requests that wait on the person (`:engine:requests`)

### 5.1 Permission requests

```kotlin
@SingleIn(ConnectionScope::class)
public class RequestsEngine @Inject internal constructor(/* gateway, transcript, settings, clock, stores */) {
    public val pending: StateFlow<ImmutableList<PendingRequest>>     // oldest first, every session of this connection
    public fun forSession(session: SessionId): StateFlow<SessionRequests>   // the card to show + "N more wait"
    public fun answer(id: RequestId, answer: RequestAnswer)             // starts the 3 s hold; returns at once
    public fun undo(id: RequestId)                                       // inside the hold only
    public suspend fun flushHeld(session: SessionId)                     // leaving the chat sends held answers now
    public fun cards(session: SessionId): StateFlow<ImmutableMap<String, CardView>>   // callId → card state (§13); empty while cards are off
}

public sealed interface RequestAnswer {
    public data class Permission(val reply: PermissionReply /* Once | Always | Reject */, val note: String?) : RequestAnswer
    public data class Question(val answers: ImmutableList<ImmutableList<String>>) : RequestAnswer
    public data class Form(val values: ImmutableMap<String, FormValue>) : RequestAnswer
    public data object Decline : RequestAnswer
}
```

| Rule | Detail | Row |
|---|---|---|
| One card at a time, oldest first, inline | never a modal, never takes focus from the composer; "The agent waits until you answer. Nothing is lost." + how many more wait | map CHAT-GENUI-43 |
| Age from first display on this phone | "Needs you · 20 s"; a ledger by request id; reopening does not restart it | map CHAT-GENUI-43 |
| 3 s hold | `DelayedAnswers` window 3 s; collapsed "Allowed · Undo"; leaving the chat sends at once; a second answer during the hold is ignored | map CHAT-GENUI-43 |
| Exactly one reply | notification Allow and in-app tap race → one reply, first decision wins; both share one in-flight write | B-CHAT-074, B-AGENT-031, B-PROTO-048 |
| Receipts | Sending → (request leaves) / Not confirmed yet (after 8 s, Try again) / Not accepted (server words under Details); "Sent" is never "Done" | STATE-10 |
| Timed out on the server | 404 `PermissionNotFoundError` with this request id → card leaves with no error; any other 404 keeps it | B-CHAT-076 |
| Answered elsewhere | gone after refetch → leaves the attention count | B-CHAT-075 |
| Which call it is for | `tool.callID`; else a running `question` tool; else the step still running the very command named | map CHAT-GENUI-43 |
| Lost asks | asks are never replayed: on reconnect `pendingPermissions()` refetch; the stall tracker re-reads every 15 s (§3.10) | B-PROTO-034 |

### 5.2 Wording (`PermissionAsk`)

`PermissionAsk.of(request, project): AskView` in `:engine:requests` returns `verb: AskVerb` (Run, Edit, Create,
Delete, Read, Fetch, Search, Use…), `target`, `project`, `added/removed`, `familyPattern?`; `:ui:words` builds the
sentence. Ported rules (B-CHAT-073, B-CHAT-122, B-CHAT-123): short shell commands appear in the question; long ones by
the family their `always` pattern names ("npm install *") only when the command starts with it, else "a command"; OC2
`apply_patch` lists every path; Delete only when every file is deleted; absolute paths read relative to the project;
outside-the-project asks name the exact path; project names never break at hyphens (word joiners); Arabic says the same
with no leftover English.

### 5.3 Approval modes and "Always allow"

- Modes: Ask first / Auto-approve this conversation / Approve everything (server-wide, confirmed in one sentence).
  "Subagents follow this" while auto-approving. Stored in `oc.autoApprove.<profileId>` (05). Gate:
  `capabilities.permissionRequests`.
- Auto-approval replies **"once" only, never "always"**. A failed automatic reply leaves the request visible with the
  reason.
- The 13B choice: "Always allow" is on the card's Details sheet with a one-step confirm naming its scope first ("Always
  allow npm install in neon-breaker?").
- The third identical ask (permission + exact patterns + `always` scope) invites "Always allow" once per server and
  scope, only where the server keeps grants (`persistentPermissionGrants`; Paseo only with `canAlwaysAllow`); durable
  claim before showing; recorded as accepted only after the server took it; never while auto-approving
  (`oc.permissionConsent.<profileId>`, 256 entries / 128 KiB). B-SEC-147, E2E-06.

### 5.4 Questions

Typed questions, never permissions (B-CHAT-072). One prompt with one answer: options in place, a tap sends, "Something
else" opens a field whose text is kept as that request's draft. One prompt with several answers: "Answer" opens the
request sheet. Longer: the full question sheet with Send pinned above the keyboard that says why it cannot send yet;
Dismiss is confirmed (nobody can restore a dismissed question). Paseo plan approval never restores bypass mode; "keep
planning" denies without interrupt; wrong session or invalid values never send.

### 5.5 One `FormSpec` engine

One model in `:core:model` (`…core.model.forms`) and one renderer `KitForm` in `:ui:kit-chat`. It replaces Flutter's two
renderers (`form_renderer.dart` 1,351 lines and `agent_card_form.dart` 341 lines): **removes** one renderer and its
second set of field bugs.

```kotlin
public data class FormSpec(val id: FormId, val owner: FormOwner, val title: String, val description: String?,
                           val fields: ImmutableList<FieldSpec>, val submitLabel: String?, val revision: String)
public data class FieldSpec(val key: String, val label: String, val description: String?, val kind: FieldKind,
                            val required: Boolean, val hidden: Boolean, val default: FormValue?,
                            val placeholder: String?, val options: ImmutableList<FieldOption>,
                            val min: Double?, val max: Double?, val minLength: Int?, val maxLength: Int?,
                            val pattern: String?, val format: StringFormat?, val whenAll: ImmutableList<FieldCondition>)
public enum class FieldKind { Text, Multiline, Number, Integer, Toggle, Select, MultiSelect, Date, DateTime, ExternalLink }
```

| Source | Adapter | Answer encoder | Slice |
|---|---|---|---|
| OpenCode 2 `Form.Info` (incl. MCP elicitation with `sessionID: "global"` and the `question` tool's `metadata.kind = "question"`) | `Oc2FormAdapter` | `replyForm`: 400 `FormInvalidAnswerError` keeps the form open with an error notice; 409 `FormAlreadySettledError` closes and says once it was answered on another device | 3 |
| Agent card `form` ask | `CardFormAdapter` | the two-line card answer (§13.4) | 5 |
| ACP v2 elicitation (`form` first, `url` only once the host-consent row exists) | `AcpElicitationAdapter` | accept / decline / cancel + content | with the backend |
| Codex `mcpServer/elicitation/request` | `CodexElicitationAdapter` | accept / decline / cancel | 5 |

Rules: `hidden` fields are never shown and send their default (B-PROTO-022); `when` conditions all must hold, an
inactive field is neither required nor sent, its draft is kept; the revision covers every constraint and ignores key
order (B-CHAT-028); sheet height = content for ≤ 4 declared fields, full height for 5+ or a description over ~140 chars,
an end-side sheet on wide windows, the count ignores `when` so the sheet never jumps; never auto-presented; drafts per
form (`oc.draft.<target>.<profileId>`, B-STATE-024); credential-like fields (OpenCode's
`/password|passphrase|secret|token|api[_ -]?key|credential|private[_ -]?key/i`) use the sensitive field, never autofill
from clipboard, account or phone; external-link fields open only through `openExternalLink` after a tap, naming the host.

### 5.6 Surfaces outside the chat

The same `RequestsEngine` serves the Conversations row (secondary buttons, no request slot), Inbox and the
notification actions (Allow / Deny / RemoteInput reply bound to the exact `requestID`). Notification copy carries counts
and a generic tool sentence from `ToolPresentation`, never prompts, commands, paths or outputs (B-SEC-105). The Android
side (`CodingActionReceiver`, the short foreground service) belongs to the Android surfaces section.

---

## 6. The chat screen (`:feature:chat`)

### 6.1 `ChatUi` and `ChatActions`

`presentation/ChatUi.kt` (no Compose imports, Konsist K2). The rows are the engine's list, passed through untouched; every
other per-row fact is a **side table** keyed by row key or id, so a change in requests or delivery never copies the
5,000-row list.

```kotlin
public data class ChatUi(
    val header: ChatHeaderUi,                         // title, project line, git chip, context ring, menu items
    val status: ChatStatusUi?,                        // the one status line (most urgent first, §6.5)
    val phase: TranscriptPhase,
    val rows: ImmutableList<TranscriptRow>,           // newest first, identity-stable
    val live: LiveTurn?,                              // §4.1
    val pending: ImmutableList<PendingSendUi>,        // optimistic bubbles not yet reconciled (§4.6)
    val waitingCalls: ImmutableSet<String>,           // call ids blocked on a request → "Waiting for you"
    val cards: ImmutableMap<String, CardUi>,          // callId → card view (§13.7)
    val findMarks: FindMarksUi?,                      // per-row ranges + "3 of 12"
    val pathLinks: ImmutableMap<String, PathLinkState>, // path → Confirmed | Missing | Unknown (§9.1)
    val request: RequestCardUi?,                      // the one card over the composer
    val nudge: NudgeUi?, val tip: TipUi?,
    val motion: MotionLevel, val reasoningOpen: Boolean, val timestampsVisible: Boolean,
    val capabilities: ChatCapabilitiesUi,             // the flags the screen reads, never the backend kind
    val arrival: ChatArrivalUi?,                      // land on request / failure / menu action, acknowledged (§6.7)
    val readOnlyReason: ReadOnlyReason?,              // sub-agent, watching
)

public interface ChatActions {
    fun openLink(url: String); fun openPath(path: String, line: Int?)
    fun toggleFold(key: FoldKey); fun collapseAll()
    fun loadOlder(); fun jumpTo(target: JumpTarget); fun blocksVisible(keys: ImmutableList<String>)
    fun copyReply(turnId: String); fun messageAction(target: MessageRef, action: MessageAction)
    fun answer(id: RequestId, answer: RequestAnswer); fun undoAnswer(id: RequestId); fun openRequestDetails(id: RequestId)
    fun answerCard(identity: CardKey, answer: CardAnswerDraft); fun undoCard(identity: CardKey)
    fun insertChoice(text: String)                    // ```choices block → composer
    fun stop(mode: StopMode); fun undoStopAfterStep(); fun showLiveSteps()   // live-line tap: opens the running work line with "Stop after this step" under it
    fun find(query: String?); fun findNext(); fun findPrevious()
    fun retrySend(id: LocalSendId); fun checkAgain(id: LocalSendId)
    fun arrivalShown(id: Long); fun nudgeShown(id: String); fun leaveRequested()
}
```

### 6.2 `ChatViewModel`

- One per chat entry (Nav3 per-entry ViewModel through `metrox-viewmodel-compose`). It opens the transcript handle in
  `init` and closes it in `onCleared`.
- `state = combine(handle.state, activity.liveTurn(s), delivery.pending, requests.forSession(s), requests.cards(s),
  handle.search.marks, handle.pathLinks, settings.chatPrefs, connection.capabilities) { … }.stateIn(viewModelScope,
  WhileSubscribed(5_000), ChatUi.Loading)` (03 §14.3).
- The `combine` lambda is O(changed side tables), never O(rows): rows pass through by reference; `waitingCalls` is a set
  built from `requests` (≤ tens of entries); `PendingReconcile.visiblePending` looks only at the newest turn.
- Kotlin 2.4 explicit backing fields, no copy strings, no `Channel` events (03 §14.4). "Navigate after create" and
  arrivals are state fields the UI acknowledges.
- The ViewModel never parses Markdown, never builds `AnnotatedString`s, never sorts rows.

### 6.3 The `ComposerHost` slot (conflict C2)

```kotlin
// :ui:kit-chat, package io.github.eslamasabry.oc.ui.kit.chat.host — file owned by the chat lead (MODULE_OWNERS row)
public interface ComposerHost {
    /** Draws the composer for one conversation inside KitComposerLayer's composer slot. */
    @Composable public fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier)
}
public data class ComposerTarget(val profileId: String, val sessionId: String?)     // plain ids; no engine types
public class ComposerChrome(                                     // what the chat hands the composer
    public val busy: () -> Boolean,                             // read in draw/layout only
    public val requestWaiting: () -> Boolean,                   // the card owns the composer: no glow, no Stop
    public val onSent: () -> Unit,                              // follow the live edge, lift the bubble
    public val insertTextRequests: Flow<String>,                // agent ```choices taps
)
```

- **Slice 2:** `:feature:chat` binds `BasicComposerHost` (`@ContributesBinding(AppScope::class)`): `KitComposer` v1 with
  `TextFieldState`, Send, Stop in the chip row, steer per §4.3, model chip read-only. No drafts yet (§17 S2 gap).
- **Slice 3:** `:feature:chat-composer` contributes `FullComposerHost` with
  `@ContributesBinding(AppScope::class, replaces = [BasicComposerHost::class])` [U: Metro `replaces` is P1's check; if
  absent, the slice-3 unit deletes `BasicComposerHost` in the same merge]. The slice-3 contract names
  `BasicComposerHost` under "what this slice deletes".
- The chat entry gets `ComposerHost` injected into its entry installer and calls `composerHost.Composer(…)` inside the
  layer. `:feature:chat` never imports `:feature:chat-composer`.

### 6.4 What sits over the composer (`KitComposerLayer` slots)

One kit part fixes the order, most urgent at the top, so no screen can add a level (R10). Height publishes through
`KitBottomInset` so the last message, the Jump pill and the Undo bar stay clear.

| Slot (top → bottom) | Owner | Shown when |
|---|---|---|
| `findBar` | chat | find is open |
| `request` | chat (`RequestCardUi`) | a request waits; shares the height left over the composer, capped at 45 % of the window (60 % at 2.5× text), scrolls; a sliver too short for question + answers anchors at the bottom so "Allow once" is under the finger |
| `queued` | composer | an inbox prompt waits (`KitQueuedMessage`) |
| `nudge` / `tip` | chat | one nudge at a time; yields to any card |
| `phrases` | composer | §10.4 rules |
| `chips` | composer | model, approval, agent features, tasks, then Stop at the trailing end while running |
| `composer` | composer | always (read-only reason inside the pill when set) |

### 6.5 Status line, connection states and first paint

- **One status line** under the top bar, most urgent first (map CHAT-GENUI-53): watching, send error (Try again /
  Choose another model / Sign in / Continue elsewhere), prompt error (hidden when the same error is already on a
  message), queued drafts, staged undo (Review / Put back / Keep) or "undone", sub-agent "n of m" with parent link,
  shared-by-link banner, free-model caption, "No model signed in · Sign in", Auto-approve note, the first-run
  notification question. All `KitStatusLine`; tags `chat-status-<id>`.
- **First load:** a loading bar under the top bar plus `KitSkeletonTranscript` — never an empty-state text — unless an
  opening excerpt exists, which is drawn instead (S-11 first paint). A permission card is pinned to the bottom of the
  skeleton and error states too.
- **Could not load:** `KitStateView` with the unplugged-cable icon, which step failed (redacted), Try again, Report a
  problem one tap away. An agent's chat says its helper "isn't answering", not "reconnecting".
- **Composer-local outcomes** (queued, staged, already present) use the composer note: a live region that wraps whole
  (2–3 lines, also at 200 % text), stays until the next change or a tap, never on a timer.

### 6.6 Message actions, find, timeline, menus

- One action list per message for every way in (footer More, prompt long-press, right-click, Shift+F10, TalkBack custom
  actions): Copy, Fork from here, Read aloud (hidden when the reply has no prose), Undo from here (prompt; offered while
  busy with the reason), Delete message (last, confirmed by the message leaving with an Undo bar), Error details, Retry
  / Send again. Copy of an agent-card answer shows the summary, never the `[oc-ui answer …]` JSON (`15d71b52e`).
- Copy reply = the turn's adjacent assistant prose, notices in the middle skipped, verbatim (the reply is the person's
  to paste), confirmed by the kit tick + announcement. Copy transcript = Markdown with the person's prompts verbatim and
  everything else through `Redactor` (SEC-13).
- Find: docked under the top bar; "3 of 12"; "searching N loaded · search all" only when history is partly unloaded;
  prose matches are marked in place in the draw phase (`KitFindMark`, wash .18 passive / .38 active, never a text
  colour); hidden matches get an excerpt above their turn naming where. Esc clears then closes; F3 / Shift+F3; closes
  when the location changes.
- Timeline sheet: newest-first searchable list; a row jumps (carrying the words into find); a prompt row has Fork.
- Conversation menu: built from capabilities by the same `SessionMenuOffer` the list uses (07 C06).

### 6.7 Read state, arrivals, announcements

- **Read acknowledgement** (`ReadState.markRead(ref, idle)`) fires only when `phase == Live`, no load error, and the
  list is at the live edge (`!awayFromLatest`) — owned by the visible transcript, never by an inventory. Not for
  watched or demo chats (B-CHAT-012, B-STATE-116).
- **Arrivals** (07 §2.5): `LandOnRequest(id)` scrolls to and washes the card once (`KitArrival chat-request-<id>`);
  `LandOnFailure` scrolls to and marks the newest failed turn; `MenuAction(a)` runs once after the first history. Each
  is a state field with an id; the screen calls `arrivalShown(id)`.
- **TalkBack:** one announcement per turn when it ends ("Reply finished", the failure reason in plain words, or
  "Waiting for you"), only with a screen reader on and this chat in front, through `AccessibilityAnnouncer` in the kit
  (`announceForAccessibility` on Android). Never per streamed word, never `liveRegion` on streaming text.
- **Run finished:** haptic `Done` once (skipped under Off); the first reply of a first run asks the notification
  question once; one-time nudges (approvals on the 3rd identical ask, review after edits, leave-and-be-told after 60 s
  of waiting, compact at ≥ 80 % context, pin on 2nd project) through one nudge slot.

---

## 7. The transcript list

### 7.1 Shape

**Decision (map CHAT-GENUI-09 DECIDE):** one `LazyColumn(reverseLayout = true)` over block-level rows, newest first.
It replaces `scrollable_positioned_list` and Flutter's pinned-count workaround: key anchoring keeps the reader's place
for free (research rendering-chat §4.2). Fallback if spike SP-1 (§16) fails on the 5,000-part fixture: one item per
turn with an inner non-lazy `Column` for short turns and block items only for turns over 40 blocks.

`feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt` (sketch; every part
used is a kit part, so the feature allowlist holds):

```kotlin
@Composable
internal fun ChatTranscript(ui: ChatUi, actions: ChatActions, list: TranscriptListState, modifier: Modifier) {
    KitTranscriptSelection(selectable = ui.phase is TranscriptPhase.Live) {          // one SelectionContainer (§7.8)
        KitTranscriptList(                                                           // kit wrapper over LazyColumn
            state = list.lazy,                                                       // cacheWindow ahead 1.0, behind 0.5 (§7.7)
            reverseLayout = true,
            contentPadding = list.padding,                                           // header clearance / composer clearance (§7.6)
            modifier = modifier,
        ) {
            ui.live?.let { live ->                                                   // no Stop here (§4.4); a tap shows the steps
                item(key = "live", contentType = "live") { KitTurnLive(live, ui.motion, onShowSteps = actions::showLiveSteps) }
            }
            items(ui.pending, key = { "pending:${it.localId}" }, contentType = { RowKind.Prompt }) { KitMessagePending(it, actions) }
            items(ui.rows, key = { it.key }, contentType = { it.kind }) { row -> ChatRow(row, ui, actions) }
        }
    }
}
```

Bottom-to-top order (index 0 is the bottom): live line → optimistic prompt bubbles → engine rows (newest first) → the
`older` row. The live line is a real item only while a turn runs; it is never an empty placeholder.

### 7.2 Keys and content types

Keys are the row keys of §3.7 plus `live` and `pending:<localId>`. They are Strings (saveable), never indexes or
`hashCode()`. `contentType` is the `RowKind` enum, so a paragraph item reuses a paragraph composition. Never call
`indexOf` inside `items`; jumps use `TranscriptState.index` (built on the lane).

### 7.3 Following the live edge

```kotlin
// kit-chat: …ui.kit.chat.transcript.TranscriptListState (hoisted per chat entry, rememberSaveable for `following`)
public class TranscriptListState internal constructor(public val lazy: LazyListState, private val density: Density) {
    public var following: Boolean by mutableStateOf(true)            // true at open and after the person's own send
    public val awayFromLatest: Boolean by derivedStateOf {           // → Jump pill; read only by the pill (no screen recomposition)
        lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > with(density) { AWAY_DP.dp.roundToPx() }
    }
    internal companion object { const val AWAY_DP = 480 }            // Flutter's 480 px threshold, now in dp [new: density-safe]
}
```

1. At the live edge, growth of the bottom item keeps its bottom glued to the viewport and pushes older rows up. That is
   the follow, at zero cost.
2. A **new item at index 0** (the next block, a tool row, the live line, a pending bubble) is not shown by itself: the
   list keeps its anchor on the old first item [V `requestScrollToItem` KDoc]. While `following`, the list host calls
   `lazy.requestScrollToItem(0)` in the same frame as the insert:
   `LaunchedEffect(Unit) { snapshotFlow { ui.newestKey }.collect { if (list.following) list.lazy.requestScrollToItem(0) } }`.
   The new tail block grows from zero height (§8.4), so the hop is invisible.
3. The person's drag (`lazy.interactionSource` drag start) sets `following = false`. Reaching the live edge again, the
   Jump pill, or the person's own send sets it back.
4. Sending from far up jumps to the newest end first (`890aa83a6`), then aligns above the composer padding, not flush
   under the composer (`8c614c58d`). Under Full motion the last stretch (≤ 1 viewport) glides with `animateScrollToItem`;
   longer distances jump first; under Off it jumps.
5. Distance is always measured from the newest end. Nested horizontal scrollers (code, tables, diffs) never count as
   transcript scroll (map CHAT-GENUI-09).

### 7.4 Older history and reloads keep your place

- Older pages are appended at the far end of the newest-first list, so they land above the reader, and the key anchor
  keeps the view still. No pinned count is needed.
- A recent-history merge that replaces rows keeps the same keys for unchanged rows. If the reader's first visible key
  disappears (message removed), the list goes to the latest quietly, with no "History refreshed" note (`f614d6a3b`).
- A reload whose anchor was captured before a send never pulls the view back after the send's jump (Flutter
  `_latestJumps`): the list host compares a jump generation before restoring.

### 7.5 Jumping to a message or a step

1. `actions.jumpTo(target)` → ViewModel → `handle.materialize(target)`: loads older pages if needed and opens the step's
   fold, then returns the row index.
2. `lazy.scrollToItem(index, offsetPx)`, where the offset centres the row in the viewport between the header and the
   composer clearances.
3. Under Full motion, a final `animateScrollBy` of the measured remainder; under Off, nothing more.
4. The step flashes once for ~1 s (`KitPace.quick` fade, draw phase).

`animateScrollToItem` across thousands of unmeasured rows is the known jank source; it is never used for long jumps.

### 7.6 Insets, frost and the composer

- `contentPadding.top` = the frosted header's clearance; `contentPadding.bottom` = `KitBottomInset` (the composer layer's
  measured height) + 16 dp gutter.
- When the bottom padding grows (a chip row, a tip, a request card) and the list sat at the live edge, the list scrolls
  the missing distance after two frames (24 dp slack), so the newest words never hide under the card.
- Frost (D10): `scrolledUnderTop = { lazy.canScrollForward }` (older content under the header) and
  `scrolledUnderBottom = { lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > 0 }`. Both are lambdas
  read in the draw phase by `KitFrost`, so scrolling never recomposes the header or composer.
- IME: the composer rides the keyboard with `imePadding()`; `imeNestedScroll` is a `JETPACK_AHEAD` item only after the
  nubia spike (06 §6).

### 7.7 Prefetch and item animation

- `cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1.0f, behindFraction = 0.5f)` on `LazyColumn` (the 1.13
  parameter [V]; on CMP 1.12.2 fallback, pass it to `rememberLazyListState`). "Ahead" is older history while scrolling
  up. Tuned by the fling journey J3 (§14.3).
- Rows must be cheap to compose: every heavy input (blocks, tokens, decoded images) is prepared off the main thread and
  cached; a prefetched code block is `BasicText(prebuilt)`.
- No zero-size rows except a newly inserted tail block for at most its reveal start (§8.2). Pictures reserve their size
  from known dimensions or a fixed 4:3 placeholder.
- `Modifier.animateItem(fadeInSpec = KitMotion.quickEnter /* 150 ms, enter */, placementSpec = null, fadeOutSpec = KitMotion.exit /* 06 EXIT token */)`
  on every row. `placementSpec` is **null by default**: a placement spring on rows above a growing reply makes them lag
  behind the text. It switches to the kit's standard spatial spec (250 ms) only while a person-initiated fold change runs (R4's named
  exceptions).

### 7.8 Selection and copy

- One `SelectionContainer` around the list (`KitTranscriptSelection`), so a selection runs across blocks and turns.
  Selected lazy items stay pinned while scrolled off [V KDoc].
- `DisableSelection` on prompt bubbles (long-press = the prompt menu), work lines, tool rows, chips, buttons and the
  live line.
- The live (streaming) block: selection **off** until Compose 1.13 stable ships the "selection constrained during
  concurrent text updates" fix; then on, via the 06 `JETPACK_AHEAD` row "Compose 1.13 selection-while-streaming fix".
- **Smart selection off [new, security]:** `ComposeFoundationFlags.isSmartSelectionEnabled = false` in
  `OcApplication.onCreate` before the first composition [V flag, default true]. The system's "Open" action on a
  selected URL would bypass `openExternalLink` (R17). Owner: coordinator (`:androidApp`); test `SmartSelectionOffTest`.
- "Select all" only covers composed rows; whole-reply copy is the footer's Copy (parity), not select-all.

### 7.9 Accessibility order

TalkBack reads a `reverseLayout` list newest first unless told otherwise. Following Stream Chat's fix (research
rendering-chat §4.7): `isTraversalGroup = true` on the header, list and composer regions with explicit
`traversalIndex` (header 0, list 1, composer 2); inside the list each row gets `traversalIndex` equal to its age order
and a matching `zIndex`. Each Markdown block is its own semantics node (Flutter needed a semantics boundary for a
5,000-paragraph reply). Spike SP-6 proves the order with TalkBack on the emulator before slice 2 ends.

### 7.10 Keyboard

`Modifier.onPreviewKeyEvent` on the list host: PageUp/PageDown scroll one viewport, Home goes to the oldest loaded,
End to the live edge. Ctrl/Cmd+F opens find, F3/Shift+F3 navigate, Esc stops a reply when nothing else is open. The
bindings table is 07 §9; this section implements the handlers.

---

## 8. Streaming reveal, paced by frames

### 8.1 Pipeline

```
network (SSE)        ─► :protocol:* adapter ─► GatewayEvent.PartDelta              (04)
engine lane          ─► model.append ─► StreamingMarkdown tail ─► publish ≤ 1 / 50 ms (§3.5)
ViewModel            ─► ChatUi.rows (tail BlockRow replaced, settled rows untouched)
kit-chat, per frame  ─► RevealRegistry: shown (word-snapped)  → layout only when a word lands (8–20 × / s)
                        fresh runs [start, end, born]         → draw only (DstOut mask)        (§8.3)
                        ReplyGrow spring on the live block    → layout of that item only        (§8.4)
                        preferredFrameRate(High) while any of the above is active               (§8.5)
```

Data shape does most of the work: a publish touches one block; a frame touches one mask.

### 8.2 `RevealRegistry` and the clock

`:ui:kit-chat` `…ui.kit.chat.transcript.RevealRegistry`, remembered once per chat entry (outside the lazy items, so
recycling never loses it):

```kotlin
public class RevealRegistry internal constructor(private val motion: () -> MotionLevel) {
    /** Created the first time a row with live = true and origin = Streamed composes. Loaded rows never animate. */
    public fun stateFor(key: String, textLength: () -> Int, predecessor: String?): RevealState
    internal fun forget(key: String)        // when shown == length and no fade runs remain
}

@Stable public class RevealState internal constructor(...) {
    public var shown: Int by mutableIntStateOf(0); internal set          // prefix laid out (layout read)
    internal val runs: SnapshotStateList<FreshRun>                       // [start, end, bornNanos] (draw read)
    internal var frameNanos: Long by mutableLongStateOf(0L)              // draw read only
    public val active: Boolean get() = shown < length() || runs.isNotEmpty()
}

internal object RevealPace {
    const val BASE_CPS = 45f           // characters per second when no backlog (demo value, owner felt it)
    const val DRAIN_S = 0.25f          // a backlog drains in ~250 ms: the reveal never lags the network by more
    const val FADE_NS = 250_000_000L   // KitMotion.standard: each word fades over 250 ms
    const val MAX_DT_S = 0.05f         // a stalled frame never reveals a burst
}
```

The clock (one `LaunchedEffect(state)` per live block, ported from the demo's `RevealLoop` with its two faults fixed:
no whole-reply re-parse per frame, no per-frame spans):

```kotlin
internal suspend fun RevealState.drive(text: () -> CharSequence, predecessorDone: () -> Boolean,
                                       motion: () -> MotionLevel) {
    snapshotFlow { active && predecessorDone() }.collectLatest { busy ->
        if (!busy) return@collectLatest                      // parked: no frames scheduled while idle (battery)
        var last = 0L; var carry = 0f
        while (true) {
            val keepGoing = withFrameNanos { now ->
                val t = text()
                if (motion() == MotionLevel.Off) { revealAll(t.length); runs.clear(); return@withFrameNanos false }
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(RevealPace.MAX_DT_S); last = now
                carry += maxOf(RevealPace.BASE_CPS, (t.length - shown) / RevealPace.DRAIN_S) * dt
                val want = shown + carry.toInt(); carry -= carry.toInt()
                val snapped = t.wordEndAtOrAfter(want).coerceAtMost(t.length)          // whole words only
                if (snapped > shown) reveal(snapped, now, fade = motion() != MotionLevel.Off)   // layout change
                frameNanos = now                                                          // draw-only invalidation
                runs.removeAll { now - it.bornNanos > RevealPace.FADE_NS }
                active
            }
            if (!keepGoing) break
        }
    }
}
```

- A new tail block waits for its predecessor in the same part (`predecessorDone`) so words never appear out of order.
  While waiting it is a zero-height item; it grows from zero (§8.4). This is the only zero-height row.
- When a block settles (the part moved on), it keeps its `RevealState` until it catches up, then the registry forgets it.
- History rows (`origin = Loaded`), reopened chats and rows that scroll back into view after the reveal finished draw
  at full length immediately.
- The visible prefix is `inlines.toAnnotated(styles).subSequence(0, localShown)`, rebuilt only when `shown` changes
  (whole words). Tail blocks are short, so this is O(tail).

### 8.3 Fresh words fade in the draw phase

Every `SpanStyle` change is layout-affecting in `TextAnnotatedStringNode` (research rendering-chat §6.1), so the fade
never changes spans. Kit modifier `Modifier.kitFreshFade(layout, state)` on the live block only:

```kotlin
internal fun Modifier.kitFreshFade(layout: () -> TextLayoutResult?, state: RevealState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }      // live block only; removed when settled
        .drawWithCache {
            val path = Path()
            onDrawWithContent {
                drawContent()
                val tl = layout() ?: return@onDrawWithContent
                val now = state.frameNanos                                       // read here = draw-only
                for (r in state.runs) {
                    val hide = 1f - KitMotion.enterEasing.transform(((now - r.bornNanos).toFloat() / RevealPace.FADE_NS).coerceIn(0f, 1f))
                    if (hide <= 0f) continue
                    path.reset(); path.addPath(tl.getPathForRange(r.start, r.end))
                    drawPath(path, Color.Black.copy(alpha = hide), blendMode = BlendMode.DstOut)
                }
            }
        }
```

- `DstOut` lowers the destination alpha, so it works on any background, including the live backgrounds.
- **Migration trigger:** when a stable Compose UI release ships `DrawAnnotation` (verified absent in 1.13.0-beta02
  today), the kit-chat owner moves the fade and the find marks onto it and drops the offscreen layer. Add a
  `JETPACK_AHEAD.md` row when it appears in a Jetpack release before CMP.
- `TextMotion.Static` on transcript text (rows move by whole pixels); `TextMotion.Animated` only on the sent-bubble lift.
- `LineBreak.Simple` on reply text (the Compose default on Android [V research]); never `LineBreak.Paragraph`, never a
  strategy switch when a block settles (that reflows once at the end, "dancing" text).

### 8.4 Smooth growth

- `Modifier.animateContentSize(animationSpec = KitMotion.replyGrow /* spring(dampingRatio = 1f, stiffness = 700f) */,
  alignment = Alignment.TopStart)` on the live block only (06 §4.8 token; Flutter `KitMotion.replyGrow`, ζ ≈ 1.0,
  ≈ 210 ms a line).
- Shrinks are taken at once (a rewrap that shortens never animates).
- Fallback if spike SP-3 shows clipped descenders or shrink animation: a 40-line `LayoutModifierNode` in kit-chat that
  animates only growth with an `Animatable` and keeps a descender allowance (port of `_KitMdGrow`, `2d9a65b45`).

### 8.5 Frame-rate vote

`Modifier.preferredFrameRate(FrameRateCategory.High)` on the live block **only while** `RevealState.active` or the grow
animation runs; removed when the reply settles. On adaptive-refresh devices views default to a "Normal" (about 60 Hz)
vote, and an alpha-only fade may not raise it by itself [V research]. If CMP 1.13 does not export the modifier in
`commonMain`, it is a `KitPlatform.frameRateHint(active)` seam implemented in kit-chat `androidMain` (`JETPACK_AHEAD`
row) [U: SP-3 checks]. Diagnostics logs `Display.hasArrSupport()` once per process (owner phone unknown).

### 8.6 Motion levels in the chat

`MotionLevel` comes from `resolveMotion(...)` in `:ui:design` (06 §4.8): system animator scale 0, the person's Off or
Saver → Off; battery saver, severe heat or Calm → Calm; else Full.

| Motion | Full | Calm | Off |
|---|---|---|---|
| Word reveal pace | clock (§8.2) | clock | words appear with each publish (still ≤ 1 / 50 ms) |
| Fresh-word fade | 250 ms | 250 ms (fade is not travel) | none |
| Reply growth | `replyGrow` spring | jump | jump |
| Follow glide after send | glide ≤ 1 viewport | jump | jump |
| Work-line fold, step arrival | 250 ms | fade only | none |
| Sent-bubble lift | 250 ms rise from 0.92 (`lift` spring) | fade in place | none |
| Stream caret | breathes once a second | still | still |
| Live-line light sweep | 2.2 s sweep | still | still |
| Composer glow vs Stop ring (MOT-15) | glow travels if on, else ring turns | neither | neither |
| Haptics | Send, Done, Commit, LongPress (never on scroll, stream or plain taps) | same | Done skipped |

### 8.7 Never animated

Rows above the growing reply (placement), the transcript's scroll during streaming at the live edge (it is glued, not
animated), text colour, find marks moving, anything off screen (tickers stop off screen, MOT-5).

---

## 9. Renderers

### 9.1 Markdown

**Decision:** parser = `org.jetbrains:markdown` **0.7.18** (`StreamingMarkdownFile`, GFM flavour) [V]; renderer = our
own kit block parts (a port of `KitMarkdown`'s ~3.2k lines). mikepenz `multiplatform-markdown-renderer` 0.46.0 is a
reading reference only: path chips, agent blocks, find marks, the fade, picture groups and kit-only styling would
override nearly every component.

**Fallback (if spike SP-2 fails):** port Flutter's own block parser (`kit_markdown.dart`, 1,363 lines) to pure Kotlin
in `:core:markdown` behind the same `StreamingMarkdown` API. The renderer does not change.

`:core:markdown` (03 §10.8 names) — `native/core/markdown/src/commonMain/kotlin/io/github/eslamasabry/oc/core/markdown/`:

```kotlin
public class StreamingMarkdown(private val partId: String) {
    public fun append(chunk: String): BlockDelta          // normalises CR/LF across chunks; open-fence fast path
    public fun replaceAll(text: String): BlockDelta       // non-append edits: full parse, unchanged-prefix keys kept
    public val blocks: ImmutableList<Block>
}
public data class BlockDelta(val stable: ImmutableList<Block>, val tail: ImmutableList<Block>)

public sealed interface Block { public val key: String; public val start: Int; public val end: Int
    public data class Prose(override val key: String, override val start: Int, override val end: Int, val inlines: Inlines) : Block
    public data class Heading(/*…*/ val level: Int, val inlines: Inlines) : Block           // h1–h2 headline role, h3–h6 row-title role
    public data class Quote(/*…*/ val children: ImmutableList<Block>) : Block               // visible rail
    public data class ListItem(/*…*/ val ordered: Boolean, val number: Int?, val depth: Int, val inlines: Inlines) : Block
    public data class Code(/*…*/ val info: String?, val text: String, val open: Boolean, val kind: CodeKind) : Block
    public data class Table(/*…*/ val header: ImmutableList<Inlines>, val aligns: ImmutableList<Align>, val rows: ImmutableList<ImmutableList<Inlines>>) : Block
    public data class PictureGroup(/*…*/ val pictures: ImmutableList<Picture>) : Block
    public data class AgentBlock(/*…*/ val type: AgentBlockType /* Choices | Checklist | Command */, val items: ImmutableList<AgentItem>) : Block
    public data class Rule(/*…*/) : Block
}
public data class Inlines(val text: String, val spans: ImmutableList<InlineSpan>)
public data class InlineSpan(val start: Int, val end: Int, val kind: SpanKind)   // Bold, Italic, Strike, Code, Link(url), PathCandidate(path, line)
public object PathChipScanner { public fun looksLikeFilePath(s: String): Boolean; public fun stripLineSuffix(s: String): Pair<String, Int?> }
public object PictureScanner { /* B-CHAT-059 rules */ }
public fun proseForSpeech(text: String): String     // drops fences, tables, link targets, rules, heading/quote/list marks, alt text; throws over 131,072 chars
```

Rules carried over (each from `kit_markdown_test` 23, `kit_markdown_image_test` 25, `markdown_path_link_test` 9,
`markdown_reading_test` 16, `markdown_agent_blocks_test` 5, `markdown_streaming_test` 3 — turned into input → expected
`Block` fixtures, logic tests now even though they lived in widget files):

- Ordered lists start at their first item's number, nest by 2 or 4 spaces; an indented line under an item stays in it.
- HTML comment **blocks** are hidden; mid-line comments are not.
- Path chips: `looksLikeFilePath` = multi-segment, no spaces, no scheme, anchored (`/`, `~/`, `./`) or ending in an
  extension ≤ 9 chars, length 4–300; only a trailing `:123` is stripped (B-CHAT-064). A chip becomes a link **only** when
  the engine confirmed the file exists: one directory listing validates every path in that folder; a confirmed file
  stays confirmed; a miss holds for **20 s**; a transient failure forgets both (map CHAT-GENUI-15). Needs
  `capabilities.fileBrowsing`.
- Pictures: a stored attachment id never shows as a name ("Image" instead); web addresses are **never fetched**;
  `file:///abs` reads through the app's file transport; ≤ **8 MiB** per read, else a named chip that still opens the
  viewer. The chat's Coil 3.6.3 `ImageLoader` has **no network fetcher** (no `coil-network-*` artifact on the chat's
  classpath) — the privacy rule is enforced by the dependency graph [new]; custom `Fetcher`s for `data:` and
  `file:` through the gateway, cache keys `part:<message>:<index>`, `path:<profile>:<path>`, `tool:<identity>`.
- Agent blocks (```` ```choices ````, ```` ```checklist ````, ```` ```command ````) are built only for **closed** fences:
  choices = tappable rows that insert the text into the composer (`actions.insertChoice`), checklist = read-only with
  done items muted (not struck through), command = "Run on your computer" lines with a 48 dp copy button.
- Links: every link span becomes `LinkAnnotation.Clickable(tag = url)` whose listener calls `actions.openLink(url)`; the
  ViewModel asks `:engine:project` `DevPreview.claim(url)` for `localhost`/`127.0.0.1`/`0.0.0.0`/`::1` with a port, else
  `openExternalLink` (R17, B-CHAT-025–027). Credentials in a URL → not ours, refused.
- RTL: prose follows content direction; inline code, paths and numbers are FSI…PDI isolated; code is always LTR.
- A reply ending in code, a table or pictures shows no caret on a line of its own.

`:ui:kit-chat` parts (06 §7.8 names): `KitMarkdownProse`, `KitMarkdownHeading`, `KitMarkdownQuote`,
`KitMarkdownListItem`, `KitMarkdownTable` (one horizontal scroller of measured columns; inline-code cells
`softWrap = false`; scroll inside the 700 dp cap), `KitPictureGroup`, `KitAgentBlock`, `KitStreamCaret` (takes no
width), `KitFindMark`. Inline → `AnnotatedString` is built in `remember(block, styles)`: once per block instance, a
total function (no throws) proven by `InlineAnnotatedTotalityTest` over the fixture corpus plus 10,000 random inputs,
because an exception in composition kills the process (ADR §9.6).

### 9.2 Code blocks

**Decision:** `CodeTokenizer` seam in `:ui:kit-content` common; first implementation **SyntaxMP 0.3.0**
(`com.gallatinapps.syntaxmp:syntaxmp-tokenizer`, Apache-2.0, pure common Kotlin, 39 languages) [V]. Never a WebView
highlighter.

```kotlin
public interface CodeTokenizer {
    public fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken>   // pure, off main thread
}
public data class CodeToken(val start: Int, val end: Int, val role: CodeRole)  // Keyword, String, Comment, Number, Type, Function, Punctuation, Meta, Addition, Deletion, Plain
public enum class CodeLanguage { /* the ~45 Flutter aliases mapped to SyntaxMP grammars */ }
```

- Alias table ported from `kit_code_block.dart` (dart, js/jsx, ts/tsx, py, rb, sh/bash/shell/zsh/console, json,
  yaml/yml, toml/ini, html/xml/svg, css, scss, sql, go, rust/rs, kotlin/kt, java, swift, c/h/cc/cpp, cs, php,
  diff/patch, gradle, dockerfile, makefile, proto, md). Missing SyntaxMP grammars map: scss → css, svg → xml,
  console → bash, patch → diff, gradle → kotlin.
- **Source over 20,000 chars stays plain.** Token cache: LRU of **48** entries keyed `(language, source hash)`,
  theme-independent; colours from the kit's code roles (a theme change never re-tokenises).
- An open fence is never highlighted (§3.8). A closed fence is tokenised once on `default` and cached.
- In the transcript: capped at **12 lines** with "Open full output"; `$` prompts drawn before each command and outside
  the copied text; continuation lines (heredoc body, trailing `\` `|` `&&`, open quotes or brackets, for/if bodies,
  comments) get none (B-CHAT-055). Copy copies the source, never the display; a failed copy keeps a snapshot for Try
  again. Commands and code scroll sideways with an edge fade; output wraps on compact windows; wrapping breaks only at
  spaces and punctuation, never inside identifiers. `.fill` mode (full reader) is a lazy list per line with find marks.
- **Benchmark gate (spike SP-4, half a day):** 45 aliases × 3 real agent outputs from the owner's transcripts (redacted),
  SyntaxMP vs Highlights 1.1.0 vs kotlin-textmate 0.3.0; correctness by eye on the emulator + tokenise time per 20k
  chars. If SyntaxMP is visibly wrong on shell, JSON, TS, Kotlin or diff: use kotlin-textmate on Android/JVM behind the
  same interface (grammars as assets; it adds jcodings ~1.7 MB, so the S-14 size gate is re-checked) and SyntaxMP for
  other targets later. Last fallback: plain text with the alias table (no colour) — the chat still works.

### 9.3 Diffs

**Decision:** port our own (`kit_diff_model.dart` 653 lines + rows/chrome/markdown parts) into `:ui:kit-content`
`…content.diff`, pure model in common. No third-party diff library: the LCS with its cell-limit fallback is ported with
its tests; `kotlin-multiplatform-diff` 1.4.0 (Myers, no unified-diff support) is the named fallback only if the LCS
fallback block looks wrong on real diffs.

- Unified-patch parser: `\ No newline at end of file` is a note on the line above; `diff --git`, `---`, `+++` preambles
  dropped; hunk headers read "Lines 12–18"; 3 context lines; gap bars reveal unchanged lines per tap; per-file facts
  cached per immutable `DiffFile` instance.
- Inline in a tool step: ≤ 12 lines, one file, plain `Column` of rows (not lazy), wrap choice on the step's long-press.
- Full page ("Changes in this reply", `ProjectRoute.Diff` per 07): `LazyColumn` rows inside **one** horizontal scroller;
  content width computed once (`gutter + monoAdvance × maxLineLength`, measured with `TextMeasurer` on "0"); the gutter
  stays put by countering the scroll with `Modifier.offset { IntOffset(hScroll.value, 0) }`; split view from the expanded
  width class.
- Syntax colour: tokenise the whole old and new file text once, slice tokens per line (per-line tokenising breaks
  multi-line strings); 4.5:1 contrast kept on add and remove rows.
- Always LTR (`LocalLayoutDirection provides Ltr` around code and diff).
- Line selection → Comment / Add to prompt / Copy lines is our own row-range selection with an action bar, not
  `SelectionContainer`.

### 9.4 Pictures and the gallery

Every transcript picture opens one swipeable full-screen gallery in transcript order (Telephoto `zoomable` 0.19.0 +
Coil). Edit opens the markup editor (pen, 4 colours, Undo, Clear, crop; result at the picture's own resolution, ≤ 9 MiB
re-encode); Send puts the picture into the composer as `…-edited.png`, never sends. Owned by the media section; the chat
only opens it with the ordered keys.

### 9.5 Tool rows, the work line and the timeline

- `KitToolRow(view: ToolView, opened: Boolean, …)`: kind glyph, plain title, mono LTR subject, detail, status, duration,
  ± counts, body when opened. Body per kind (map CHAT-GENUI-14): shell = the step's command box (command, first lines,
  "Show all", exit code, "Run this command again"); edit/write/patch = `KitDiffView` capped at 12 lines + "Open all
  changes"; read = line count heading, no "(no output)" box; web fetch = address; sub-agent = "Delegated to explore ·
  Done" opening the child conversation; todo = one line "Updated tasks"; question = asked + answered in words; card call
  = the card (§13.7) or one quiet "Show card"; connector search = one row per connector.
- Output is cut **before** it reaches the row: 12 lines + "Open full output" (reader) + copy of the whole text (PERF-2).
- A step "interrupted" reads Stopped, not an error. A failed step offers one neutral Retry (10A); shell failure offers
  "Run this command again"; never both. Error text is in the tool's own words under Details; no danger colour.
- `KitWorkLine`: running / waitingForYou / done / endedFailed / stopped; opens in place (250 ms fold, R4) to
  `TimelineStepRow`s with the rail, dots for sentences, tiles for tool calls and the live step light (a `drawWithCache`
  sweep, no recomposition).

### 9.6 Notices, markers, errors and the footer

- `KitNotice` one line that opens to its text; failures in words, never red. Compaction words use host details when
  known; a failed newest compaction offers "Compact again".
- `AssistantErrorRow` keyed on the server's typed error (`agent_error_text.dart` → `AgentErrorCause` in `:core:domain`;
  words in `:ui:words` `strings_chat_errors.xml`):

| Kind | First fix | Then |
|---|---|---|
| Context overflow | Compact (asks first) | Details |
| Provider auth | Open providers / "Sign in with Claude Code" for a phone agent | Details |
| Output length | Continue | — |
| Model not found | Choose another model; "Use X and resend" when the server named one it has | Details |
| Usage limit / no credit | Another model | Details |
| Content filter | — (no handoff) | Details |
| Lost connection mid-turn | Continue, unless the agent went on | Details |
| Stopped by the person | none: "You stopped this reply." (not an error) | — |

  An error followed by more steps is a quiet line in the story, not an alarm. "Continue with another agent" is a quiet
  second way out of a provider failure, never opened by itself.
- Footer (`KitTurnFooter`): Copy beside More; meta = model name from the catalog (never `provider/model` or a raw id,
  readable fallback from the id), time, tokens + cost (3 decimals, "essentially free" below) only when the timestamps
  toggle is on; the turn's changed files (each one tap from its contents, then "All files"); "You stopped this reply."
  or who stopped it; Continue after a length cut.

### 9.7 Text scale, width and direction per part

| Part | Text role | Scale | Cap (06 §8.1) | Direction |
|---|---|---|---|---|
| Reply prose, headings, lists, quotes, tables | reading | full system scale | Conversation 700 dp | content |
| Prompt bubble | reading | full | ≤ 85 % of the column, 48 dp start inset | content |
| Code, commands, diffs, paths | reading mono | full | List 960 dp inside its own scroller | LTR always |
| Thoughts, notices | reading (muted) | full | 700 dp | content |
| Tool row title/status, work line, footer meta, live line | frame | ≤ 1.3× | 700 dp | content; subjects LTR |
| Composer field | frame | ≤ 1.3× | 700 dp; height ≤ 40 % of window | content |
| Chips, Stop, Jump pill | frame | ≤ 1.3× | Chip 320 dp | content |
| Request card title and summary | reading; buttons frame | full / ≤ 1.3× | 700 dp; height ≤ 45 % (60 % at 2.5×) | summary LTR |
| Agent card title/body | reading; eyebrow, buttons, receipt frame | full / ≤ 1.3× | Reading 720 dp | agent strings isolated |

---

## 10. The composer (`:feature:chat-composer`, `:engine:composer`)

The kit part is `KitComposer(state: KitTextState, …)` (06 §7.8, `typealias KitTextState = TextFieldState`). The spec
is `docs/ux-system/kit-api/KitComposer.md` and `KitComposerChips.md` in the Flutter tree (frozen API, every state); the
2026-10-30 owner change overrides them: Stop is at the end of the chip row, the field shows only Send or the mic, and
there is no delivery segment (R12).

### 10.1 Field and keyboard

- `TextFieldState` in the UI owns text, selection and IME composition (ADR §9.2). The UI calls
  `actions.onDraftChanged(text, selectionUtf16)`; autosave is debounced **600 ms** (`:engine:composer` `Drafts`);
  `/` and `@` detection runs at once. Mention ranges are UTF-16 offsets (same as the wire, map CHAT-GENUI-29).
- One editor structure: the field grows from 1 to 8 lines, then scrolls inside its height budget (≤ 40 % of the window);
  the focused field is never re-parented (a re-parent drops the IME).
- Keyboard images: `Modifier.contentReceiver` accepts only inline image bytes; URI-only commits are ignored; each image
  becomes an attachment through the same limits (§10.6).
- Keys: Ctrl/Cmd+Enter sends; with a fine pointer Enter sends and Shift+Enter is a newline; on touch Enter is a newline;
  Esc closes suggestions, else leaves voice mode, else unfocuses. Text is always kept. Desktop arrow keys browse sent
  prompts, keeping selection and composing text (B-CHAT-089).
- After a send: the phone unfocuses the field; a wide window with a hardware keyboard keeps focus.

### 10.2 Controls and states

States (KitComposer.md): idle empty (mic), idle with text (Send), sending, busy empty, busy with text, busy cannot send
yet, offline ("Send when back online"), read-only (reason in the pill; Send, + and mic hidden), voice phases (§10.9).

- Send is the accent circle; Send fires on **lift** when it has a long-press menu (`ee0815e9f`), so the hold can open
  "Send later" (§10.9).
- While a request waits: no glow, no Stop; the card owns the moment (memory `chat-kit-decisions-2026-10-02`).
- The glow: `KitComposerGlow` Classic (default) / Soft ring, colours One/Two, speed Slow/Normal/Fast from
  `oc.effectsActivityGlow` (absent reads as on) and `oc.effectsGlow*` (B-CHAT-006); one painter, one draw layer, ticks
  only while a reply runs; MOT-15 decides between it and the Stop ring.
- A failed send shows in the pill's top row in neutral words with Try again and a Details icon (decision 03 → neutral).

### 10.3 The chip row (one line, no new level)

Order: **model chip**, **approval chip**, **agent features chip**, **tasks chip**, then **Stop** at the trailing end
while a reply runs (it never comes and goes mid-row). A window too short keeps the chips behind "+".

| Chip | Shows | Rules | Rows |
|---|---|---|---|
| Model | catalog name; readable name from the id while loading (a previously seen name first); agent only when it differs from the server default and there is a real choice; effort only when a real choice, as words ("High"); "No model signed in" before the first send with nothing signed in; ellipsis | never `provider/model` or a raw id; runtime-prefixed ids (`kilo/google/x`) match `google/x`; "new" mark for a model released < 30 days; the chosen model applies to **this conversation only**; send reads the selection after `waitForSessionSelection` | B-CHAT-066, B-CHAT-067, B-KIT-057 |
| Approval | filled shield "Auto-approve" / outline "Asks first" / "Auto-approve paused" (disconnected) | opens the approvals sheet (07 C13) | map CHAT-GENUI-27 |
| Agent features | "Fast" lit when on, or the agent's name with a settings icon | per-conversation switches read from and saved to the server; a reopened chat shows its chip from the first frame (last-known cache) | map CHAT-GENUI-27 |
| Tasks | "Plan 3/5" | opens the Tasks sheet; progress = completed / tracked, cancelled ignored | B-CHAT-065 |
| Stop | neutral circle with a ground square; amber when stuck | §4.4 | map CHAT-GENUI-40 |

### 10.4 Phrase chips and starters

Pure rules in `:engine:composer` `PhraseChips` and `PhraseMoment` (ported first: good test seeds).

- Stored `oc.phraseChips.<profileId>` (`{v:1, hidden:[…], entries:{key:{t,n,l,m:{moment:{n,l}}}}}`) and the device-wide
  switch `oc.phraseChips.enabled` (default on). Limits: phrase ≤ 40 chars, 50 entries, 100 hidden, a phrase counts after
  **2** sends, ≤ **4** chips, half-life **14 days**.
- Never recorded: long, multi-line, commands, shell, mentions, with files, secret-like (detector or any 20+ char run
  without spaces), voice-conversation sends (B-CHAT-078).
- The moment (B-CHAT-085, B-CHAT-086): busy → working; last turn cut off by length → stopped-partway + "Continue"; stops
  by the person, errors and interruptions are classified; a failed edit is not a change; only the last turn counts.
- The moment's starter leads the row ("Continue"; "Try again" in full right after Continue), shows alone when nothing is
  learned, is not repeated when learned (B-CHAT-081).
- Hidden while: empty chat, typing or files staged, a request waits, under an error notice, no room, switch off, and
  while the agent works.
- Empty chat: project name + muted facts ("24 items · Git · 3 changes") and starters: empty folder → ways to make
  something; files → ways to work on them; "what changed recently" only with Git commits; none while loading or for no
  project; unfinished sentences end with a space, not an ellipsis; Arabic starters in Arabic. Starters fill the
  composer, never send (B-CHAT-017).
- A chip tap sends through `sendInsteadOfDraft` (§4.2); a long-press hides it ("Show hidden phrases" restores).

### 10.5 The "+" tray

"+" opens in place: Photos, Camera, File as round buttons attached to the box, and More = a sheet with this
conversation's settings first (model, Auto-approve, plan, send running work to the background), then Attach, Voice,
commands, saved prompts, reuse a prompt, project file, prompt editor, shell. A tool that cannot run stays in place and
says why; a server that takes no files has no tray and the sheet says why. The sheet returns one tool and the composer
runs it **after** the sheet closed (a tool that opens its own sheet never races the dismissal).

### 10.6 Attachments

| Rule | Value | Row |
|---|---|---|
| Count and size | ≤ **5** per prompt, ≤ **10 MiB** each, ≤ **20 MiB** total | map CHAT-GENUI-29 |
| Images | png, jpeg (`image/jpg` → jpeg), gif, webp keep their type; picked photos scaled to **2048 px**, quality **85** (a 12 MP photo × every step overflowed the server's WebSocket queue) | B-MEDIA-007 |
| PDF | `application/pdf` keeps its type | B-CHAT-009 |
| Text-like | text MIME or a known extension (md, txt, log, html, css, csv, json, xml, yaml, toml, source files…) sent as `text/plain`; NUL byte or invalid UTF-8 → not text | B-CHAT-009 |
| Office | `.xlsx` → CSV per sheet, `.docx` → paragraphs, encrypted/binary `.xls/.doc` refused, done off the main thread | B-MEDIA-006 |
| Unknown binary | refused with a plain sentence | B-CHAT-009 |
| Reading | streaming read keeping at most limit + 1 bytes; oversized picker data refused without copying; stale metadata never trusted for file paths | B-CHAT-010 |
| Camera recovery | a recovery record is committed **before** the picker launches (Android may kill the app while the camera is open); restored to its own conversation's draft at start | B-STATE-088, B-MEDIA-008 |
| Per backend | `promptAttachments` / `promptImagesOnly` / `promptEchoTextOnly` capabilities | map CHAT-GENUI-29 |

The ViewModel orchestrates: `ActivityResults` (`:android:platform`, ADR §7.3) → `:engine:media` `AttachmentLimits` /
`OfficeText` → `:engine:composer` `AttachmentVault` (`draft-attachments-v1/`, 256 MiB vault, 32 MiB per draft, 05).

### 10.7 Mentions, commands and review references

- `@` lists project files beside sub-agents (debounced search, no spinner or error row in the strip). A pick writes
  `@lib/server.dart ` and stages the file tied to that text; deleting the text sends no file part; a path that only
  starts like a mention does not count (B-CHAT-087). `@agent` mentions use the boundary rules `[\s\(\[\{"']` before and
  `[\s\.,!\?;:\)\}\]"']` after, longest names first. Paseo takes the `@path` text only.
- `/` lists the app's actions and the server's own commands (where `slashCommands`); a command the server cannot run
  stays listed with its reason; `/word` to an agent without commands sends nothing and says so. `!command` runs in the
  conversation's shell. Navigation targets per 07 "C-slash".
- Review references (`ReviewReferences` in `:engine:composer`): staged from Files, Changes and Review as chips; max 10
  per session; folded into the prompt on send as structured Markdown; one send-wide snippet budget (~24k chars, whole
  lines, "truncated" stated); fences longer than any backtick run inside (B-CHAT-098, B-CHAT-099).

### 10.8 Drafts, saved prompts, history, the editor

- Drafts: per server and conversation, with attachments; saved on a pause in typing (600 ms), on app pause, on leaving,
  after sends; a full store (50) **refuses** new drafts rather than evicting unsent work; a refused write tells the
  person when they leave (B-STATE-100–102). Back leaves at once when the words are on disk (07 §3.4 guard). An untouched
  brand-new conversation is deleted after you leave it (best effort; Back never waits on the server).
- Saved prompts (stash, 50 per server), "Reuse a prompt" (text only), the full-screen editor (an overlay sharing the
  same `TextFieldState`, Done pinned above the keyboard, Close with changes asks once): rows B-CHAT-088, B-STATE-085–087,
  B-STATE-136, B-STATE-141. Formats are 05's.

### 10.9 Send later and voice hooks

- **Hold Send** → "Send later or repeat" sheet (`SheetKey.Schedule(null, ref)`, 07 C27) seeded from the composer text;
  saved → the draft leaves the box and a note says so. Tip `send-later`.
- **Mic:** tap = dictation, hold = push-to-talk (drag far = "let go to cancel"), "Send when I stop talking" waits **3 s**
  in the pill with Cancel and then uses the one send path unless anything changed. Voice phases in the pill:
  starting, listening, transcribing, waitingReply, speakingReply, replyReady, paused, micDenied, failed. The mic never
  records behind another page; leaving stops it and keeps the words. Tip `hold-mic`. Runtime, models and permissions
  belong to the media section; the composer only shows phases from `VoiceController` and inserts words at the selection
  (B-MEDIA-024).

### 10.10 Teaching

`TeachingRegistry` entries contributed by the chat features: `stop-after-step` (hold Stop), `send-later` (hold Send),
`hold-mic` (hold the mic). Each at most twice, one tip at a time, never amber, never while a question waits or the
keyboard covers its anchor, replayable in Help › Tips; each gesture also has a visible path (the live-line tap shows
"Stop after this step"; the + sheet has "Send later").

---

## 11. Review, revert and changed files

The model is `:engine:project` `ReviewModel` (ADR §5.2); pages are `:feature:chat-pages` (07 C05, C31–C36). What the
chat itself must do:

- **Undo from here** (prompt action, `/undo`, "Undo last prompt", "Undo all"): two shapes by capability, never by server
  name (`sessionRevert`, `supportsStagedRevert`):
  - **Staged:** the prompt and everything after are hidden while the person reviews; "Put files back too"; the Review
    the undo page shows the exact count of messages that follow (across pages and kinds, excluding the hidden prompt;
    zero is legitimate; unknown is never shown as zero), the files (each opens its diff), "Put everything back" and
    "Delete the hidden messages", each asked first and run inside the question (B-CHAT-103–106).
  - **One-step:** the sheet is the honest confirm: how many messages go, that files go back, files edited after the
    prompt (first seen, once, from completed edit tools); "Undo now" runs inside the sheet (B-CHAT-120).
- While an undo is unresolved: the staged-undo status line (Review / Put back / Keep) and Send refuses (§4.2 step 2).
  Undone rows stay hidden on every server until final.
- Every revert mutation re-reads the session just before; mutations are exclusive; a connection swap mid-preflight
  dispatches to neither server; an ambiguous commit reconciles (reload history even if GET fails) before retry.
- A finished turn's changed files come from `RunResult` (§3.10); Changes opens `ProjectRoute.Review(scope =
  Session(ref))`.

---

## 12. Read-aloud hooks

The engine is `:engine:media` `ReadAloud` (App scope) over `SystemTts` / `PcmPlayer`; the chat supplies text and scope.

- **Read aloud** in a reply's actions calls `readAloud.read(ReadRequest(scope = ref, messageIds, prose =
  proseForSpeech(reply)))`. Hidden when the reply has no prose (`337db92bb`). Consent asked once app-wide, then the voice
  choice sheet only for "Choose voice" or when no voice speaks the language (`oc.readAloudConsent`).
- Reading stops when the chat route is covered, the app leaves the foreground, or the scope changes (server or session
  switch). Foreground only (media research: Android 17 silences background audio). Never falls back to copy or export.
- **Voice conversation** (Listen → send → maybe read → Listen): "Speak replies" is per conversation, never persisted,
  granted after consent + voice choice; Exit, a scope change or a lifecycle pause revokes it; paused while a request,
  question or form waits.
- **Exactly-once automatic reading** (`VoiceReplyWatch` in `:engine:media`, reading transcript and delivery state): bound
  to the app-authored message id sent on dispatch (`CorrelatedPromptGateway`), the speech scope and a voice epoch;
  plays only after `requestComplete`; a reply counts only if it is an assistant message whose explicit `parentId` is
  that user message (order alone cannot tell another client's reply); if it cannot be tied with certainty →
  `ReviewNeeded` and reading stays one tap away, never a guess (B-MEDIA-009).

---

## 13. Generative UI (agent cards)

### 13.1 What ships, and what does not

| Channel | Stance | Slice |
|---|---|---|
| Agent cards: app-owned MCP server `oc-ui`, tools `show` and `find_connectors`, card v1 (title, 0–40 body nodes of 10 types, ≤ 1 ask of choice/form/confirm/photo, optional connector) | **Keep the contract byte for byte; port the code** (genui research §0.1) | 5 |
| OpenCode 2 forms, MCP elicitation, questions | §5.4, §5.5 | 3 |
| Agent blocks in Markdown | §9.1 | 2 |
| Task lists from todos | §10.3 | 3 |
| Connector suggestions + `find_connectors` | §13.9 | 5 (own unit) |
| A2UI (spec v0.9.1, v1.0 candidate; `androidx.a2ui` 1.0.0-alpha02, Android-only) | **Not adopted.** Revisit only when a supported agent emits A2UI; then a bounded subset adapter into `AgentCard` (genui research §10.5), never free layout | — |
| Remote Compose (`androidx.compose.remote` alpha22) | **Not for cards** (binary documents a model cannot write; draws outside the kit). Widgets later. | — |
| MCP Apps (`ui://` HTML in sandboxed iframes) | **Out**: agent HTML/script contradicts the card contract; our agents flatten `ui://` resources to text anyway | — |

Cards are untrusted conversation content, not permission requests: answering sends a normal user message; it never
authorises shell execution, approves a permission or bypasses safety. No model call, no markup, no remote resources, no
dynamic widgets, no executable expressions. **Off by default** (`oc.genui.enabled.<profileId>`); qualified for Claude
Code only until OpenCode 2 qualification is proven on the device.

### 13.2 Model (`:core:model` `…core.model.cards`)

```kotlin
public data class CardIdentity(            // trusted parts only; never the agent's `id`
    val profileId: String, val sourceId: String, val directory: String, val workspace: String?,
    val sessionId: String, val messageId: String, val callId: String)

public data class AgentCard(val identity: CardIdentity, val revision: String /* sha256 of canonical JSON */,
                            val label: String /* agent id: display and correlation only */, val title: String,
                            val body: ImmutableList<CardNode>, val ask: CardAsk?, val connector: ConnectorSuggestion?)

public sealed interface CardNode {
    public data class Text(val text: String) : CardNode                               // ≤ 2,000 scalars
    public data class KeyValue(val rows: ImmutableList<KeyValueRow>) : CardNode        // 0..20; key 1..60, value ≤ 200
    public data class Checklist(val items: ImmutableList<ListItem>, val style: ListStyle) : CardNode   // 0..30, bullet|check
    public data class Table(val columns: ImmutableList<String>, val rows: ImmutableList<ImmutableList<String>>) : CardNode // 1..6 cols, 0..20 rows
    public data class Chart(val kind: ChartKind, val unit: String?, val labels: ImmutableList<String>, val series: ImmutableList<Series>) : CardNode
    public data class Code(val language: String?, val text: String) : CardNode         // ≤ 4,000, display only
    public data class DiffStat(val files: ImmutableList<FileStat>) : CardNode          // 0..30
    public data class Progress(val label: String, val value: Double) : CardNode        // finite 0..1
    public data class Callout(val tone: CalloutTone, val text: String) : CardNode      // info|warning|success, ≤ 500
    public data class Link(val label: String, val url: SafeHttpsUrl) : CardNode
}
public sealed interface CardAsk {
    public data class Choice(val options: ImmutableList<ChoiceOption>, val multi: Boolean) : CardAsk   // 2..8
    public data class Form(val spec: FormSpec) : CardAsk                                               // the §5.5 engine, 1..12 fields
    public data class Confirm(val confirmLabel: String?, val cancelLabel: String?, val danger: Boolean) : CardAsk
    public data class Photo(val purpose: String, val max: Int) : CardAsk                               // 1..4
}
public sealed interface CardParse {
    public data class Parsed(val card: AgentCard) : CardParse
    public data class Unreadable(val reason: UnreadableReason) : CardParse   // NotACard, Version, TooLarge, UnknownKey, BadValue, SecretField, UnsupportedAsk
}
```

### 13.3 Admission and validation (`:engine:requests` `…requests.cards`)

```kotlin
internal val CARD_TOOL_NAMES = setOf("mcp__oc-ui__show", "oc-ui_show")   // exact; never suffix, substring or title match

internal fun admit(part: Part, info: MessageInfo, scope: CardScope): CardParse? {
    if (part.type != PartType.Tool || part.toolName !in CARD_TOOL_NAMES) return null      // ordinary tool row
    if (info.role != Role.Assistant || part.synthetic) return null
    val tool = part.tool ?: return null
    if (tool.status != ToolStatus.Completed || !tool.ran) return null                     // pending/running stay tool rows
    if (tool.input.isEmpty() || part.callId.isNullOrEmpty()) return null                  // missing after event loss → reconcile, not invalid
    return CardValidator.validate(tool.input, scope)                                      // never from inputRaw
}
```

`CardValidator` reproduces the Dart normaliser exactly, because the revision hash depends on it:

- Compact UTF-8 of the input ≤ **32,768 bytes**, depth ≤ **8**, ≤ **4,096** values and containers; strings counted in
  Unicode scalar values (unpaired surrogates rejected); single strings ≤ 65,536 UTF-16 units / 32,768 scalars;
  cumulative string bytes ≤ 32,768; cycles invalid (B-CHAT-029).
- Unknown keys anywhere → `UnknownKey`; wrong types and explicit `null` → `BadValue`; no coercion, no repair. `ask` and
  `connector` are mutually exclusive. `file` and `voice` asks → `UnsupportedAsk`.
- Text hygiene: strip C0/C1 controls except LF/TAB and the bidi controls (U+061C, U+200E–200F, U+202A–202E,
  U+2066–2069); trim labels and titles; preserve whitespace in code and text bodies.
- Links: absolute **https** only, a host, no userinfo, no whitespace, control characters or backslash, port ≤ 65,535.
- `password|passwd|token|secret|apikey` in an id or label after separator normalisation → `SecretField`.
- Canonical map built in **schema order**; `revision = sha256(compactJson(normalized))`, independent of key order
  (B-CHAT-031). Identity = the 7 trusted parts, never the agent's `id`.
- kotlinx.serialization depth safety on hostile input is [U]: the corpus gets a 10,000-level case; the SSE frame cap
  (16 MiB, 04 §6) bounds it before we ever see it.

**Test-first:** copy `test/fixtures/genui/validation.json` (182 KB) and the runtime fixtures
(`runtime_paseo_claude_tool.json`, `runtime_opencode1_tool.json`, `runtime_paseo_paging.json`) into
`engine/requests/src/commonTest/resources/genui/`. `CardCorpusTest` (written before the validator) asserts accept/reject
and the normalised output for every case. `CardJsTwinCorpusTest` (JVM, VPS) runs the helper's JavaScript validator under
Node against the same corpus, so the JS twin stays honest after Dart is gone; it needs `node` ≥ 20 on the VPS (added to
02 §10's start-up checklist) and fails, never skips, when Node is missing on T2.

### 13.4 State machine and receipts

States: **Waiting** (newest eligible ask after the latest authoritative user turn; actionable only when the session is
idle), **Answered** (receipt), **PassedOver** ("Not answered"), **Report** (no ask), **Unknown** (incomplete or stale
history: never treated as answered, absent or actionable). Delivery states: Idle, Held, Sending, DeliveryUnknown,
Failed.

Answer wire format (unchanged):

```
[oc-ui answer db-choice] Postgres
{"v":1,"cardId":"db-choice","callId":"call_123","value":{"choice":["postgres"]}}
```

- Line 1: app-made summary ≤ 240 scalars, regex `^\[oc-ui answer ([a-z0-9-]{1,48})\] (.+)$`, no TAB, controls stripped.
  Choice = labels joined ", "; form = "Submitted N fields"; confirm = confirmLabel / "Confirmed" or cancelLabel /
  "Declined"; photo = "Submitted N photo(s)".
- Line 2: compact JSON ≤ 32,768 bytes, keys exactly `v`, `cardId`, `callId`, `value`; `value` is exactly one of
  `{choice:[ids]}` (1..8 distinct), `{form:{fieldId: string|number|bool}}` (≤ 12; strings ≤ 2,000 scalars; optional
  absent fields omitted, never null), `{confirm: bool}`, `{photo: n}` (1..4). Validated against the **original** ask at
  encode and decode (real calendar dates, known select ids, numbers within min/max, required text non-blank, extra keys
  invalid) (B-CHAT-032).
- Settlement (`CardStates.of(transcript, scope)`, port of `genUiStateFor`): only an authoritative, non-synthetic user
  message after the call, same scope and session, with a validating envelope, settles a card; the first later real user
  message either answers it or passes it over; reports never supersede asks; a newer ask passes over an older unanswered
  one; duplicate message ids, wrong session, repeated identity or changed revision → Unknown; an incomplete tail →
  Unknown. The JSON line is hidden only for a fully validated matching receipt. Revert, message or part removal and
  changed tool input invalidate dependent state and trigger reconciliation (B-CHAT-031, B-CHAT-052).

### 13.5 Answer pipeline (send exactly once)

Tap → **held 3 s** with Undo, keyed by full identity (the card collapses to the answer: "SQLite", "Run it"; Undo
restores the choice, form values or photos from the per-card draft) → re-check (stale, deleted, busy, unsupported,
disabled) → send through `PromptDelivery` (§4.2) → **only the authoritative echo makes a receipt**; an acknowledgement
alone stays "unconfirmed". Duplicate taps send once; a busy session refuses; late admission or source replacement never
sends an old answer; uncertain delivery persists **without values** in `oc.genui.journal.<profileId>` (routing metadata
only) and blocks resend after restart; a definite failure keeps the answer for an explicit retry; session or profile
deletion fences late writes; corrupt persisted state fails closed (B-CHAT-050, B-CHAT-051). Photo answers validate the
matching attachments before dispatch (1..max images, 10 MiB each, 20 MiB total); no attachments ride non-photo answers
(B-CHAT-053).

### 13.6 Recovery and coverage (never wake a sleeping agent)

After a restart or reconnect a waiting card must come back: bounded reads (pages of 50, newest first, returned
oldest-first), **20 sessions per pass, 2 pages each**, incomplete recovery flagged not hidden, a hard budget per pass,
new hints coalesced into one deferred follow-up, failed reads retried with growing delay. **Never read a sleeping
stored agent in the background** (on Paseo a history read loads the agent and starts a provider process: the
`mayLoad` guard; answering a card and opening a chat still read it). Coverage (`oc.genui.covered.<profileId>`,
watermarks only, 200 most recent sessions) skips conversations whose recent history had no card, never covering a
running turn, a truncated tail, a superseded source, a changed prompt, or any read that held a card (B-CHAT-043,
B-CHAT-044, B-CHAT-052; incidents `e8e5bf602`, `902399baa`, `e25778df3`).

### 13.7 Renderer and the streaming skeleton

- `ChatUi.cards[callId]` is a `CardUi`: `Skeleton(agentName)`, `Full(card, state, delivery, draft)`, `Receipt`,
  `PassedOver`, `Unreadable(reason)`. The `CardCallRow` composable reads it by key, so a delivery change touches one row.
- **Skeleton:** when the transcript shows a pending or running tool part whose name is exactly a card name (OC2
  `session.tool.input.started` → `PartUpserted(pending)`, 04 §7.6), the card shell shows at once (eyebrow, no buttons).
  The streamed input is never parsed. On completion with a valid card it cross-fades to the card over `KitMotion.standard` (250 ms) with
  `animateContentSize` for the height (Off: swap at once); on failure or invalid input it becomes the one-line
  unreadable row. After a reconnect there is no skeleton, only the final card (deltas are volatile).
- `KitAgentCard` (`:ui:kit-chat` `…kit.chat.agent`): eyebrow "Claude Code asks" (frame text), title and body (reading
  text), ask slot; a card with an ask wears the needs-you tone, a report is neutral; one shape at the Reading cap (720 dp);
  lives in the reply, never inside a folded work line.
- Body renderer: one exhaustive `when` over `CardNode` (a new node type fails compilation until the kit has a part):

```kotlin
@Composable
public fun KitCardBody(nodes: ImmutableList<CardNode>, onLink: (SafeHttpsUrl) -> Unit, modifier: Modifier = Modifier) =
    Column(modifier) {
        nodes.forEach { node ->
            when (node) {
                is CardNode.Text      -> KitReadingText(node.text, isolate = true)
                is CardNode.KeyValue  -> KitKeyValue(node.rows)
                is CardNode.Checklist -> KitChecklist(node.items, node.style)
                is CardNode.Table     -> KitMiniTable(node.columns, node.rows)            // scrolls inside the cap
                is CardNode.Chart     -> KitChart(node.kind, node.labels, node.series, node.unit) // + spoken summary (B-CHAT-054)
                is CardNode.Code      -> KitCodeBlock(node.text, node.language)           // LTR, display only
                is CardNode.DiffStat  -> KitDiffStat(node.files, shown = 30)              // "and N more"
                is CardNode.Progress  -> KitProgressRow(node.label, node.value)
                is CardNode.Callout   -> KitCallout(node.tone, node.text)                 // success = ok tone; amber only for needs-you
                is CardNode.Link      -> KitLinkRow(node.label, host = node.url.host, onOpen = { onLink(node.url) })
            }
        }
    }
```

- Ask controls: choice rows are visible rows (single tap answers; multi has Send); form = `KitForm` (§5.5; required
  fields hold Send with the reason shown after touch; nothing autofilled; ASCII digits for numbers; the form says once
  that the agent sees what is typed); confirm pair (danger tone); photo (camera/gallery within limits). Controls wait
  for the session to be idle and say why ("Send is off because the agent is still working"). The composer hint says the
  answer can also be typed; a typed reply passes the card over.
- Every agent string is bidi-isolated; links go through `openExternalLink` after a tap, showing the host; no remote
  images exist in the contract.
- Haptic `Send` on an answer; the receipt collapses with a `KitMotion.quick` (150 ms) size + fade change.
- List slot: a waiting card also appears under its Conversations row (`:feature:chats`, 07 C40) from the same
  `CardStates`; Undo from either surface drops the held answer on both (B-CHAT-037).

### 13.8 Setup: who owns the helper

The ADR puts runtime scripts in `:engine:runtime` as resources with checksums and phone agents in `:engine:agents`
(both above `:engine:requests`). So:

| Piece | Home |
|---|---|
| `server.cjs` (zero-dependency Node stdio helper, JS validator twin), `install.py`, `verify.py`, `owners.json` handling | `:engine:runtime` resources `runtime/genui/*` with SHA-256 (`RuntimeScripts`), copied verbatim from `lib/builtin/agents/gen_ui_server.dart` and `gen_ui_install_scripts.dart` |
| Install, verify, disable orchestration; Claude Code registration (`claude mcp add --scope user --transport stdio oc-ui -- <node> <helper>` in the profile's `CLAUDE_CONFIG_DIR`, exact rule `mcp__oc-ui__show`); OpenCode 2 `mcp.servers.oc-ui` in `/root/.oc-opencode2/config/opencode/opencode.json` (JSONC-safe, owned entries only) | `:engine:agents` `CardsSetup` |
| "Are cards usable for this agent here?" | interface `CardsAvailability` declared in `:engine:requests` (the consumer), implemented by `CardsSetup` with `@ContributesBinding(ConnectionScope::class)` |
| Setup status words (`Off`, `Unavailable(reason)`, `Installing`, `On(agents)`, `Partial`, `RestartRequired`, `Failed`; reasons as enums) | `:ui:words` `strings_chat_cards.xml` |

Rules kept: a registration alone is never "ready" (status On only after the MCP `initialize` self-check); a pending
enable cannot override a newer disable; disabling blocks card actions before removal finishes; unmanaged profiles
reject setup; profile deletion removes the profile's Claude home and attempts registration removal best effort
(B-CHAT-034–036, B-CHAT-040–042, B-CHAT-047, B-CHAT-049). A qualified exact `show` request is allowed once without an
approval card and rechecked per request.

### 13.9 Connector suggestions

A card may carry `connector: {catalogId "owner/name", reason ≤ 500}`: a display-only suggestion rendered as the
connector card with one primary **Connect** and in-place progress (suggested, connecting, needsAuthentication,
authorizing, checkingTools, toolsReady, connectedReadinessUnknown, failed, unavailable); OAuth through
`openExternalLink`; manual-code fallback; "Tools ready" only when the runtime confirms; every failure one fixed sentence.
No Connect where the runtime cannot connect from chat (Claude Code) or for listings needing keys or local packages.
`find_connectors` searches the cached catalogue through the app bridge with bounded, validated queries and responses
(B-CHAT-033, B-CHAT-046, B-CHAT-048, B-CHAT-124). Own unit in slice 5, after cards v1.

---

## 14. Performance budgets

### 14.1 Gate numbers (from 02 §4; section 13 may only tighten them)

| # | Journey | Target | Measured by |
|---|---|---|---|
| S-9 | Stream a 6,000-word reply with 3 code fences at the live edge | `frameOverrunMs` p95 ≤ 0 ms, p99 ≤ 4 ms on NX721J at 120 Hz; a tail update on a 200-block reply re-lays out **1** block | J2 + `BlockRelayoutCounterTest` |
| S-10 | Fling a 5,000-part transcript | same as S-9 | J3 |
| S-11 | Open a 300-turn chat (warm) | first transcript ≤ 300 ms (`chat.openToFirstTranscript`) | J1 |
| S-12 | Jank while streaming | ≤ 1 % janky app-layer frames on NX721J | Perfetto FrameTimeline on J2 |
| S-13 | Keyboard open, sheet open, list → chat transform | p95 overrun ≤ 0 ms, p90 frame < 8.3 ms | J4, J5 |
| S-16 | Hidden while a phone agent streams | app ≤ 5 % of one core | §14.5 + `/proc` delta |
| S-20 | PSS with the 5,000-part transcript open while streaming | ≤ 0.6 × Flutter R's PSS | `dumpsys meminfo`, median of 3 |

### 14.2 Chat counters (unit level, T1 on the VPS; ported from the Flutter perf tests)

| Counter test (`:engine:transcript` unless noted) | Setup | Assertion | Flutter source |
|---|---|---|---|
| `PublishCountTest` | 100 deltas in one burst, then 10 spaced 60 ms apart | burst → ≤ 2 publishes; spaced → ≤ 10; first delta published with no delay | `perf_chat_test.dart` (`stream_flushes ≤ tokens`) |
| `OtherConversationQuietTest` | events for another session at 20 / s for 10 s | 0 publishes on this handle; 0 recompositions of the chat screen (Compose test, `:feature:chat`) | `chat_rebuild_budget_test.dart:132` |
| `NewestTurnOnlyTest` | 1,000-turn history, 10 deltas into the newest turn | `displayPassMessages ≤ 20` | `chat_rebuild_budget_test.dart:104` |
| `TailUpdateTest` (`:core:markdown` + engine) | prefix "## Plan / **this** / dart fence" + 10 tail updates | 10 tail re-parses, 0 full parses, settled code and heading `Block` instances identical (`===`) | `perf_chat_test.dart` "ten tail updates" |
| `LinearMergeTest` | 1,000 and 5,000 fragments, fragmented and not | merged output identical; a counting builder probe copies ≤ 2 × the total text length (linear, not the old quadratic ~15×). Counts, never wall-clock timings, as in Flutter | B-CHAT-096, `docs/qa/codex-perf-2026-09-28/chat.md` |
| `BlockRelayoutCounterTest` (`:ui:kit-chat`, Compose host test) | 200-block reply, tail update | exactly 1 `BlockRow` measured (counting `Modifier.layout` probe in a test build) | S-9 |
| `RevealDrawOnlyTest` (`:ui:kit-chat`) | live block revealing for 1 s at 120 Hz test clock | text re-measures only on word landings (≤ 20), never per frame | §8.3 |
| `TranscriptRecompositionTest` (`:feature:chat`) | 5,000-part transcript, 50 deltas | recompositions of settled rows = 0 with `compose-stability.conf` (ADR P4) | ADR P4 |

### 14.3 Macrobenchmark journeys (`:android:benchmark`, release-like `benchmark` build, `CompilationMode.Partial()`)

The fixture server is FX-OC2 from the flows lane (catalog `e2e-flows.md`) running on the PC; the emulator reaches it at
`10.0.2.2`. This section adds two stub-model prompts and one seeded session to FX-OC2 (owner: the flows lane, section 14):

- `perf:stream-6000` — streams a fixed 6,000-word reply with 3 fenced code blocks (one 400 lines), a table and two
  lists, as text deltas of 1–12 characters at a jittered 40–90 deltas per second, identical on every run (seeded).
- `perf:history-5000` — a seeded session of 1,000 turns with 5,000 parts (the Flutter fixture's two shapes, one
  fragmented reply of 5,000 parts and 1,000 plain messages).
- `perf:tools-200` — one turn with 200 tool calls (open work line).

| Journey | Steps | Metrics |
|---|---|---|
| J1 open chat | cold-ish: kill → open the app → tap the 300-turn conversation | `TraceSectionMetric("chat.openToFirstTranscript")`, `StartupTimingMetric` excluded |
| J2 stream | open an empty chat, send `perf:stream-6000`, wait for idle | `FrameTimingMetric`, `TraceSectionMetric("chat.derive")`, Perfetto FrameTimeline |
| J3 fling | open `perf:history-5000`, fling up 10 times, fling down 10 times | `FrameTimingMetric` |
| J4 keyboard and sheet | open chat, focus the composer (IME), open the + sheet, close, open the request details sheet | `FrameTimingMetric` |
| J5 fold and jump | open `perf:tools-200`, open the work line, fold it, jump to a step from find | `FrameTimingMetric` |

The same journeys generate the Baseline and Startup Profiles ("open chat + fling + stream", worth about 30 % faster code
on first runs; sideloaded users get the profile only after background dexopt, testing-perf §0.8). Emulator numbers are
relative (≤ 10 % worse than the last green); absolute numbers only on NX721J with the owner's OK.

### 14.4 Memory bounds

| What | Bound |
|---|---|
| Open transcript handles | LRU of 3 warm per connection; closed 60 s after the last holder |
| Finished-reply block cache | 512 parts per handle |
| Deferred parts and deltas | 256 keys and 1 MiB per handle (§3.2) |
| Code token cache | 48 entries (theme-independent) |
| Decoded images | Coil memory cache `maxSizePercent(0.15)`; disk cache only for `file:`/`data:` decodes, 64 MiB, in `cacheDir` |
| Tool output in rows | first 12 lines + count; the full text stays in the model, not copied into rows |
| Search index | per message; dropped with the handle |
| Reveal registry | one entry per revealing block; forgotten when done |

### 14.5 Hidden and background

- Handles with no subscriber apply events but skip derivation (§3.5). The chat screen's `WhileSubscribed(5_000)` lets
  derivation stop 5 s after the app leaves the screen.
- No ticker runs while hidden: the retry countdown, the caret breath, the step light, the live-line sweep and the glow
  stop with the composition; the stall tracker's 5 s tick runs only while a turn is open and the handle is held.
- The 15 s permission re-read and the stall probes run only for open turns of held handles.
- The short "watching this reply" foreground service (ADR §9.5) is started by `:engine:attention` / `:android:background`
  when the app goes to the background with a turn running; the chat engine does nothing extra.

---

## 15. Tests

### 15.1 Order (test-first, the owner's plan)

The test-writer lane turns the rows into failing `commonTest`s one slice ahead (ADR §10.3). Port order inside the chat:

1. Pure functions with the most rows and no I/O: `ToolPresentation`, error classification, `PhraseChips` /
   `PhraseMoment`, `ContextShare`, `RunResult`, `BackgroundTasks`, `TurnStallTracker`, `Redactor` use in copy transcript,
   `PermissionAsk`, review-reference formatting, attachment MIME and limits, card codec / state / answers.
2. `:core:markdown` fixtures (input → expected `Block`s) from the six Markdown test files.
3. `TranscriptEngine` integration tests with `FakeServerGateway` scripted events (`:core:testing`), including the
   5,000-fragment fixture and the publish counters.
4. `PromptDelivery` and `RequestsEngine` integration tests (send order, never-twice, exactly-once replies).
5. Compose host tests for the counters in §14.2 and the catalog's screenshot-level chat rows (Roborazzi on the VPS):
   B-CHAT-004, 016, 056, 061, 062, 063, 123. The 7,967 widget tests stay for later (owner); nothing else UI-level now.

### 15.2 Rows → module → test class

Test names start with the row id (ADR §6.3 rule 12), e.g. `` `B-CHAT-117 a late running report cannot revive a finished call`() ``.

| Rows | Module | Test class |
|---|---|---|
| B-CHAT-110, 111, 109, B-AGENT-186 | `:core:domain` | `ToolPresentationTest` |
| B-CHAT-112, 113, 114, 121, 069, 071, 097 | `:core:model` / `:engine:transcript` | `TranscriptModelTest`, `V2RowsTest` |
| B-CHAT-005, 096, 116, 117, B-PROTO-061, B-STATE-121 | `:engine:transcript` | `ApplyEventsTest`, `HistoryMergeTest`, `SettleTest` |
| B-CHAT-115 | `:engine:transcript` | `TranscriptSearchTest` |
| B-CHAT-118, 119 | `:engine:transcript` | `TurnStallTrackerTest` |
| B-CHAT-007, 008, 011 | `:engine:transcript` | `BackgroundTasksTest` |
| B-CHAT-068, 100, 101 | `:engine:transcript` | `RunResultTest` |
| B-CHAT-019, 057, 102 | `:engine:transcript` | `ContextShareTest` |
| B-CHAT-059, 060, 064 + Markdown fixtures | `:core:markdown` | `BlockParseTest`, `StreamingMarkdownTest`, `PathChipScannerTest`, `PictureScannerTest`, `ProseForSpeechTest` |
| B-CHAT-055, 058 | `:ui:kit-content` (common, pure) | `CommandLineStartsTest`, `DiffModelTest` |
| B-STATE-008, 009, 033–046, 119, B-AGENT-069, 070, 317, B-PROTO-025, 045 | `:engine:delivery` | `PromptDeliveryTest`, `SteerTest`, `OfflineQueueTest`, `ReceiptsTest` |
| map CHAT-GENUI-08/40/50 rules | `:engine:delivery` | `ConversationActivityTest` |
| B-CHAT-073, 074, 075, 076, 122, B-AGENT-031, 221, B-PROTO-048, B-SEC-147 | `:engine:requests` | `PermissionAskTest`, `ExactlyOnceReplyTest`, `AlwaysAllowInvitationTest` |
| B-CHAT-072, B-PROTO-022, 044, B-CHAT-028 | `:engine:requests` | `QuestionsTest`, `FormSpecTest`, `Oc2FormAdapterTest` |
| B-CHAT-029–053 | `:engine:requests` (`cards`) + `:engine:agents` (`CardsSetup` rows 034–036, 040–042, 047, 049) | `CardCorpusTest`, `CardJsTwinCorpusTest`, `CardStatesTest`, `CardAnswersTest`, `CardJournalTest`, `CardRecoveryTest`, `CardsSetupTest` |
| B-CHAT-077–086, 017, 018, 087–089, 098, 099, B-STATE-016, 100–102, 136, 138, 141 | `:engine:composer` | `PhraseChipsTest`, `PhraseMomentTest`, `StartersTest`, `MentionsTest`, `DraftsTest`, `StashTest`, `ReviewReferencesTest` |
| B-CHAT-009, 010, B-MEDIA-006–008 | `:engine:media` (+ `:engine:composer` vault) | `AttachmentLimitsTest`, `OfficeTextTest`, `PhotoRecoveryTest` |
| B-CHAT-103–106, 120, 001–003 | `:engine:project` | `StagedRevertTest`, `UndoFromHereTest`, `ActiveContextTest` |
| B-CHAT-025–027 | `:engine:project` | `DevPreviewTest` |
| B-MEDIA-009 + voice reply watch | `:engine:media` | `ReadAloudTest`, `VoiceReplyWatchTest` |
| B-CHAT-006 | `:engine:settings` | `GlowChoiceTest` |
| B-CHAT-012 | `:engine:connection` | `ReadStateTest` |
| B-CHAT-004, 016, 056, 061, 062, 063, 123 | `:ui:kit-chat` / `:ui:kit-content` | Roborazzi `*ScreenshotTest` (light/dark, en/ar) |

### 15.3 Fixtures to port

| Flutter | Native | Owner |
|---|---|---|
| `test/fixtures/genui/validation.json` + `runtime_*.json` | `engine/requests/src/commonTest/resources/genui/` | cards unit |
| `test/perf_chat_test.dart` shapes (1,000/5,000 parts, fragmented or not) | `core/testing` `TranscriptFixtures.history(count, fragmented)` | test-writer lane |
| `test/chat_live_events_test.dart` scripted events | `core/testing` `WireFixtures.load("oc2/2.1.1/…sse")` replayed through `FakeServerGateway` | test-writer lane |
| Markdown test inputs (`kit_markdown_test` 23, `kit_markdown_image_test` 25, `markdown_path_link_test` 9, `markdown_reading_test` 16, `markdown_agent_blocks_test` 5, `markdown_streaming_test` 3) | `core/markdown/src/commonTest/resources/md/*.md` + expected `*.blocks.json` | `:core:markdown` unit |
| Permission wording corpus incl. Arabic (`permission_card_words_test.dart:23`, `vision_chat_approval_test.dart`) | `engine/requests` `PermissionAskCorpus.kt` | S2-REQ |
| A recorded real OC2 2.1.1 stream of a long reply with tools and code (new; recorded once from the phone's server, redacted) | `core/testing` `oc2/2.1.1/long-reply.sse` | S2-TX (Opus) |

### 15.4 Device flows that touch the chat

E2E-05 (stream, stop, find, links), E2E-06 (approve, deny, always allow), E2E-07 (answer from the shade), E2E-10
(network loss, never twice), E2E-11 (drafts, saved, queued), E2E-12 (attachments, mentions), E2E-15 (undo), E2E-21
(conversation list states), E2E-29 (Codex steer), E2E-30 (Claude Code and Pi via Paseo), E2E-36 (several servers),
E2E-37 (models). Written first against the Flutter release APK by the flows lane; select by visible text.

### 15.5 Zero-cost preparation for the later widget tests

Test tags equal the Flutter `ValueKey` strings where they exist (ADR §9.1). The chat ones the kit and features must keep
(read from `lib/ui` today): `chat-composer-field`, `chat-composer-surface`, `chat-send-button`, `chat-stop-button`,
`kit-composer-send`, `kit-composer-mic`, `kit-composer-stop-chip`, `kit-composer-stop-ring`, `kit-composer-status-strip-row`,
`composer-tools-button`, `composer-tools-sheet`, `composer-tool-attach`, `composer-tool-camera`, `composer-tool-gallery`,
`composer-send-later`, `composer-stop-after-step`, `composer-voice-button`, `composer-returned-undo`,
`composer-cleared-undo`, `jump-to-latest`, `earlier-messages-pill`, `chat-load-older`, `chat-older-history`,
`chat-loading`, `chat-load-error`, `chat-load-retry`, `chat-opening-excerpt`, `chat-title`, `chat-context-ring`,
`chat-git-chip`, `chat-project-chip`, `chat-collapse-all`, `chat-error-details`, `chat-send-error-try-again`,
`chat-send-error-details`, `chat-turn-files`, `chat-turn-all-files`, `chat-background-work`, `chat-status-revert-review`,
`chat-status-undo-put-back`, `work-group`, `work-group-header`, `work-group-steps`, `message-menu-copy`,
`message-stopped`, `kit-request-card`, `kit-request-row`, `permission-card-review`, `question-sheet`, `form-sheet`,
`form-submit`, `form-cancel`, `transcript-find-bar`, `transcript-find-input`, `transcript-find-next`,
`transcript-find-previous`, `timeline-sheet`, `timeline-search`, `turn-stall`, `turn-stall-stop`, `agent-card-send`,
`agent-card-confirm`, `agent-card-unreadable`, `voice-mode`, `voice-mode-allow-mic`. Screens take `(ChatUi,
ChatActions)` so a later test drives them with canned state.

---

## 16. Spikes (each time-boxed, each with a fallback)

ADR slice-0 proofs P3 (transcript with a recorded 5,000-part stream) and P4 (stability) cover SP-1, SP-3 and SP-7 in
their first form; the rest run at the start of the unit that needs them, on a throwaway branch `spike/chat-<n>`.

| # | Question | Who / box | Acceptance | Fallback |
|---|---|---|---|---|
| SP-1 | Block items vs turn items on the 5,000-part fixture | Opus, 2 days (inside P3) | J3 fling and J2 stream meet S-9/S-10 on the emulator relative to Flutter R; 1 block re-laid out per tail update | turn items with block items only for turns > 40 blocks (§7.1) |
| SP-2 | JetBrains `StreamingMarkdownFile` on real agent output: setext headings, lazy list continuation, a table whose header arrives before its delimiter row, a 400-line open fence | Codex, 1 day | output equals the Flutter fixtures; tail re-parse with the open-fence wrapper ≤ 1 ms per append on the emulator for the 400-line fence | port Flutter's parser (§9.1) |
| SP-3 | Draw-only fade, `animateContentSize` growth (descenders, shrink), `preferredFrameRate` export in CMP 1.13 common | Opus, 1 day (inside P3) | `RevealDrawOnlyTest` green; no clipped descenders in Roborazzi at 1.0/2.0 text; frame-rate vote visible in Perfetto | `LayoutModifierNode` grow (§8.4); `KitPlatform.frameRateHint` seam (§8.5) |
| SP-4 | Highlighter: SyntaxMP vs Highlights vs kotlin-textmate | Sonnet, ½ day | correct colours on shell, JSON, TS, Kotlin, diff for 45 aliases × 3 outputs; ≤ 20 ms per 20k chars on the emulator | kotlin-textmate on Android (size re-check), then plain |
| SP-5 | Selection: across 3 blocks and one screen of scroll, copied text order in a reverse list, live block on 1.13-beta, smart selection off | Sonnet, 1 day | order correct; no crash in 1,000 select-while-stream iterations on 1.13 | selection off on the live block until 1.13 stable |
| SP-6 | TalkBack order with `traversalIndex` + `zIndex` on a reverse list | Sonnet, ½ day, emulator with TalkBack | prompts read oldest→newest within the viewport; header, list, composer in that order | per-turn semantics merge |
| SP-7 | Insert at index 0 while following: `requestScrollToItem(0)` + grow-from-zero looks continuous | Opus, inside P3 | no visible hop in a 240 fps screen recording of J2 on the emulator | keep the tail block's key stable across splits (one live item per part) |

---

## 17. Build order: units per slice

**What the chat rewrite removes** (the simplify rule; slice contracts list these under "deletes"):
`scrollable_positioned_list` and the pinned-count workaround; the Dart `highlight` package; the custom grow `RenderBox`;
paint-only span copying for the fade; the second form renderer; the delivery segment (queue choice) wherever a runtime
steers; seven per-screen pending-intent paths for chat entry (07 `EntryRouter`); the demo's per-frame re-parse and
per-frame spans.

Model choice follows ADR §10.3 and owner memory (`subagent-model-choice`, `codex-backend-claude-frontend`): Codex builds
core/engine logic from the contract; Claude Sonnet builds kit and features; Opus takes the transcript engine, the list
and the reveal.

| Unit | Slice | Model | Module(s) | Finish line | Non-goal | Depends on |
|---|---|---|---|---|---|---|
| S0-P3 | 0 | Opus | spike branch | a recorded 5,000-part stream runs through a throwaway `TranscriptEngine` and block `LazyColumn` with the draw-phase fade; publish counts match the Flutter fixture; numbers recorded (SP-1, SP-3, SP-7) | production code | ADR P1 |
| S2-MODEL | 2 | Codex | `:core:model`, `:core:domain` | §2 types and `ToolPresentation` with their rows green | wire adapters | contract s2 |
| S2-MD | 2 | Codex | `:core:markdown` | streaming blocks, scanners, `proseForSpeech` green on the ported fixtures (SP-2 first) | any UI | S2-MODEL |
| S2-TX | 2 | Opus | `:engine:transcript` | events → newest-first rows with fences, merge, passes, 50 ms publish; §14.2 engine counters green | the list | S2-MODEL, S2-MD, 04 OC2 adapter |
| S2-DLV | 2 | Codex | `:engine:delivery` | send pipeline §4.2 steps 1–10 (no offline queue persistence yet), steer table, Stop, live line | drafts, attachments | S2-TX |
| S2-KC | 2 | Opus + Sonnet | `:ui:kit-chat` | 06 unit KC2 parts + `RevealRegistry`, `kitFreshFade`, `KitTranscriptList`, `TranscriptListState`, `ComposerHost` | code colours, diff page | 06 K1 |
| S2-CHAT | 2 | Opus | `:feature:chat` | the chat screen with the list, follow, jump, find, status line, message actions, `BasicComposerHost`; J1–J3 recorded on the emulator | composer extras | S2-TX, S2-DLV, S2-KC |
| S2-REQ (C1) | 2 if ADR-09-1 is accepted, else first of 3 | Codex + Sonnet | `:engine:requests`, request card in `:feature:chat` | answer a permission and a one-question ask in the chat, exactly once, with the 3 s hold | forms, always allow, notifications | S2-TX |
| S3-COMP-E | 3 | Codex | `:engine:composer`, offline queue in `:engine:delivery` | drafts, vault, stash, history, phrase chips, references, offline queue with markers green | UI | S2-DLV, 05 |
| S3-COMP-UI | 3 | Sonnet | `:feature:chat-composer` | `FullComposerHost` replaces the basic one: chips row, tray, attachments, mentions, commands, editor, saved prompts | voice runtime | S3-COMP-E |
| S3-FORMS | 3 | Codex + Sonnet | `:engine:requests`, `:ui:kit-chat` `KitForm` | `FormSpec` + OC2 adapter (`hidden`, `when`), question sheet, approval modes, always-allow invitation | card forms | S2-REQ |
| S3-CODE | 3 | Sonnet | `:ui:kit-content` | `CodeTokenizer` + SyntaxMP (SP-4 first), `KitCodeBlock` full, inline `KitDiffView` | diff page | S2-KC |
| S3-WORK | 3 | Sonnet | `:ui:kit-chat` | open timeline rows, step light, background work line, running work sheet | — | S2-KC |
| S4-PAGES | 4 | Codex + Sonnet | `:engine:project` review model, `:feature:chat-pages` | undo from here (both shapes), Review the undo, context pages, export, note, relations | Files hub | S2-CHAT |
| S5-CARDS-E | 5 | Codex | `:engine:requests` `cards` | corpus-green validator, states, answers, journal, recovery (B-CHAT-029–053 minus setup) | install | S3-FORMS |
| S5-CARDS-SET | 5 | Codex (runtime owner reviews) | `:engine:agents` `CardsSetup`, `:engine:runtime` assets | install, verify, disable for Claude Code on the emulator's built-in Linux; status words | OpenCode 2 qualification | slice-3 runtime |
| S5-CARDS-UI | 5 | Sonnet | `:ui:kit-chat` `KitAgentCard`, `:feature:chat`, `:feature:chats` list slot | a qualified Claude Code card shows, answers once with Undo, recovers after restart | connector card | S5-CARDS-E |
| S5-CONNECT | 5 | Codex + Sonnet | `:engine:integrations`, kit | connector card + `find_connectors` | — | S5-CARDS-UI |
| S5-DIFF | 5 | Sonnet | `:ui:kit-content` | diff page (unified/split), line selection actions | — | S3-CODE |
| S6-PERF | 6 | Sonnet (perf lane) | `:android:benchmark` | S-9…S-13, S-16, S-20 met on NX721J (owner OK); profiles regenerated | new features | all |

**Slice-2 finish line (chat part):** on the preview flavor, the owner opens a conversation on the phone's OpenCode 2
server, sends a prompt, watches a reply with code stream smoothly, stops it, sends while a reply runs (it adds to the
turn), scrolls up through older history without a jump, finds a word, and J1–J3 numbers are recorded; plus "answers a
permission" if ADR-09-1 is accepted. **Slice-2 known gaps (preview only, closed in slice 3):** drafts are not saved,
attachments are not offered, code has no colours, forms are not shown.

---

## 18. Acceptance checks for this section's work

The chat is done for the switch when every line below is true on the release build:

1. Every in-scope chat row of §15.2 has a passing native test, named after its row (S-1).
2. E2E-05, 06, 07, 10, 11, 12, 15, 21, 29, 30, 36, 37 are green on the native release build after being green on Flutter
   R (S-2).
3. §14.1 numbers are met on NX721J; §14.2 counters are green at T1.
4. No chat file imports `material3`, `LocalContext`, raw `clickable`, raw `tween`/`spring`, `BackendKind` (Konsist).
5. No server or exception text reaches a visible label: `NoServerTextInChatUiTest` scans `ChatUi` producers for `String`
   fields fed from `Failure.details` or `errorText` (only `Redacted` crosses).
6. `LinkPolicyTest`: every link path in the chat (Markdown, cards, notices, footers) ends in `openExternalLink` or the
   dev-preview claim; `SmartSelectionOffTest` green.
7. `NoQueueChoiceTest`: with a steering runtime and a running turn, no composer state offers a queue choice; an old
   stored `queue` value is ignored.
8. The upgrade journey (ADR §10.2) shows a Flutter draft with an attachment, an unconfirmed send marker, saved prompts,
   phrase chips and card journal entries intact after the switch; the unconfirmed send is still unconfirmed and is not
   sent (map §3 migration notes).
9. TalkBack (SP-6 method) hears one announcement per finished turn and reads the transcript oldest to newest.
10. At 200 % system text in Arabic on a 320 dp width: reply text scales fully, chips and the composer stay at 1.3×, no
    chip cuts mid-word, code stays LTR (06 capacity tests at 280/412/1600 dp).
11. The owner's phone test of the chat passes and their words on smoothness are recorded with the date (S-27).

---

## 19. Decisions

### 19.1 For the owner (work follows the recommended answer until you reply)

| # | Question | Options | Recommended |
|---|---|---|---|
| Q09-1 | When a card asks while the agent is still working, may your answer go in straight away (added to the running turn) instead of waiting? | (a) wait until the agent is idle, with the reason shown, as today; (b) on OpenCode 2 only, send it into the running turn | **(a)** for the switch; try (b) after device tests. The tool already tells the agent "end your turn and wait". |
| Q09-2 | In Arabic, should card charts run right to left? | (a) keep today's chart exactly; (b) mirror the bar order | **(a)**, ported verbatim from `kit_chart.dart`. |
| Q09-3 | In the Changes view, highlight the changed words inside a changed line? | (a) no, as today; (b) yes, later | **(a)** for the switch (not parity); ask again after. |
| Q09-4 | After selecting text in a reply, offer "Add to message" in the selection bar? | (a) no, as today (Copy only); (b) yes, a visible action | **(a)** for the switch. |

### 19.2 For the coordinator (needed before the slice named)

| # | Request | Needed by |
|---|---|---|
| ADR-09-1 | Amend ADR §5.2: `:engine:requests` starts in slice 2 (permissions and one-question asks); the rest stays slice 3+ (§0.4 C1) | contract s2 freeze |
| MO-09-1 | `MODULE_OWNERS` file-level row: `ui/kit-chat/…/host/ComposerHost.kt` → chat lead (§0.4 C2) | slice 2 |
| AA-09-1 | `OcApplication.onCreate`: `ComposeFoundationFlags.isSmartSelectionEnabled = false` before the first composition (§7.8) | slice 2 |
| VPS-09-1 | Node ≥ 20 on the build VPS for `CardJsTwinCorpusTest` (02 §10 checklist) | slice 5 |
| FX-09-1 | FX-OC2 gains `perf:stream-6000`, `perf:history-5000`, `perf:tools-200` (§14.3) | slice 2 |

---

## 20. Sources

- Plan: `01-architecture-decision.md` (§4 rows 19–20, §5.2, §6, §7.3, §9.1–§9.6, §10.3, §11 P3/P4), `02-principles-scope.md`
  (§4 S-1…S-27, §6.9, §7.13), `03-architecture-modules.md` (§2, §10.8, §11.2, §14.3–§14.4, the route sketch),
  `04-protocols-networking.md` (§6.5–§6.6, §7.6, §11.1–§11.2), `05-data-migration.md` (§6.2 tables, Appendix keys),
  `06-ui-kit-design-system.md` (§4.8 motion tokens and MOT rules, §7.8 chat parts, §8.1 caps, §6 `JETPACK_AHEAD`),
  `07-screens-navigation.md` (§2.5 arrivals, §3.4 Back guard, §5 C01–C41, §9 keys).
- Map: `../map/chat-genui.md` (CHAT-GENUI-01…81, §3 data, §6 checklist, §7 performance, §8 risks).
- Research: `../research/rendering-chat.md` (§0–§11), `../research/genui.md` (§0, §6–§10), `../research/motion-native.md`
  (§0), `../research/testing-perf.md` (§0–§1), `../research/media.md` (§0, read aloud).
- Catalog: `../catalog/BEHAVIORS.md` (CHAT 124 rows; STATE, PROTO, AGENT, MEDIA, SEC, KIT rows named above),
  `../catalog/e2e-flows.md` (E2E-05…37).
- Flutter source (read-only, `/home/eslam/Storage/Code/wt/oc_app-batch1`, 2.1.0+57): `lib/ui/screens/chat/*`,
  `lib/ui/kit/chat/*`, `lib/domain/models/models_messages.dart`, `lib/domain/transcript_settle.dart`,
  `lib/domain/turn_stall.dart`, `lib/domain/genui/*`, `test/perf_chat_test.dart`, `test/chat_rebuild_budget_test.dart`,
  `docs/design/steering-contract.md`, `docs/ux-system/kit-api/{KitTurn,KitComposer,KitRequestCard,KitWorkLine,KitQueuedMessage}.md`,
  `docs/ux-system/revamp/STANDARDS.md` (MOT-5, MOT-15).
- Owner memory: `chat-transcript-turn-model`, `chat-kit-decisions-2026-10-02`, `steer-not-queue`,
  `composer-green-glow-liked`, `micro-animations-wanted`, `no-raw-errors-rule`, `teach-what-is-hidden`,
  `no-new-ui-levels`, `actions-name-their-target`, `text-scale-cap`, `component-max-width-rule`, `android-native-feel`,
  `no-glass-rule`, `oc2-stuck-tools-root-cause`, `dev-freeze-native-rewrite-2026-11-01`, `subagent-model-choice`,
  `codex-backend-claude-frontend`.
- Checked today [V]: `https://repo1.maven.org/maven2/org/jetbrains/markdown/maven-metadata.xml` (0.7.18),
  `…/com/gallatinapps/syntaxmp/syntaxmp-tokenizer/maven-metadata.xml` (0.3.0),
  `…/io/github/petertrr/kotlin-multiplatform-diff/maven-metadata.xml` (1.4.0),
  `https://dl.google.com/android/maven2/androidx/compose/ui/ui-text/maven-metadata.xml` (1.13.0-beta02 latest),
  `foundation-android-1.13.0-beta02-sources.jar` (`LazyDsl.kt` `cacheWindow`, `LazyListState.requestScrollToItem`,
  `SelectionState.kt`, `ComposeFoundationFlags.isSmartSelectionEnabled = true`),
  `ui-text-android-1.13.0-beta02-sources.jar` (no `DrawAnnotation`).

