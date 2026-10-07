# Agent cards — Astra backend review

Date: 2026-10-07. Branch: `feat/genui-be`.
Source candidate: `ee344676ae2e7105d5f4516cee2570e7feb03fca` (initial tree clean).
Scope: Phase 1 only. No implementation, host/config mutation, test execution, commit, push or release.
Three independent read-only research slices covered protocols, state/recovery, and installation;
Astra combined them with schema/security/answer review. All file references below are to this candidate.

**Finish line for this review:** document the implementable S1 boundary, correct unsupported promises,
and leave a concrete plan/contract revision for the coordinator to accept.
**Non-goal:** implement any backend/UI/native code or imply that an untested runtime is enabled.

## Verdict

**Approve the declarative architecture and non-blocking message answers; do not freeze the original
contract or start Phase 2 unchanged.** The original plan assumes universal live visibility, bounded
history, shared config homes and interchangeable MCP registration that the current source does not
provide. It also lacks enough identity/delivery state for safe list answers and restart recovery.

I edited the plan and contract directly with the proposals listed below. They are explicitly marked
pending coordinator acceptance. The main remaining prerequisites are real direct-MCP fixtures,
bounded Paseo tail qualification, pinned OC2 persistent/direct-tool qualification and agreement on
file/voice deferral and registration ownership. No running backend was qualified by this review.

## Numbered findings

1. **High — server readiness is not a static dialect capability.**
   OC1 keeps `part.tool`; OC2 keeps content `name`; Paseo lowercases unknown names. None of the current
   feature fixtures proves the exact `oc-ui` name. Evidence: `lib/api/models/models_messages.dart:612`,
   `lib/api2/models/models_content.dart:158`, `lib/api2/gateway_mappers.dart:480`,
   `lib/paseo/mappers.dart:160`. Current OC2 docs additionally describe Code Mode wrapping MCP tools.
   **Change:** exact allowlisted names only, expected names explicitly unqualified, default-false
   effective `genUi`, per-runtime readiness, direct-tool qualification before enablement. **Applied C1/P1.**

2. **High — incomplete or unexecuted input can look like a card.**
   OC1 pending state carries raw input, whereas map input is separate
   (`lib/api/models/models_messages.dart:480`). OC2 streaming input is a string
   (`lib/api2/models/models_content.dart:38`); mapped pending previews are not structured calls
   (`lib/api2/gateway_mappers.dart:528`). Its event adapter keeps only 256 tool records
   (`lib/api2/gateway_events.dart:21`), and results can lack name/input after missed start/called events
   (`lib/api2/gateway_events.dart:397`). `ToolState.executed` exists specifically for unexecuted calls
   (`lib/api/models/models_messages.dart:434`).
   **Change:** admit only completed/executed assistant-owned parts with full input; no parsing raw
   previews/output text, and reconcile missing event context rather than declaring permanent failure.
   **Applied C2/P2.**

3. **High — Paseo nested MCP passthrough is conditional, not established end-to-end.**
   Complete nested input survives only when `detail.type == unknown` and `detail.input` is a map
   (`lib/paseo/mappers.dart:181`). Other detail paths discard nested containers. Timeline tool mapping
   uses this result (`lib/paseo/mappers.dart:290`). Existing unknown-detail tests concern another tool,
   not `oc-ui` (`test/paseo_gateway_test.dart:1013`).
   **Change:** require a sanitized real MCP timeline fixture with nested card data and stable call
   identity; do not widen the mapper around a guessed wire shape. **Applied C1/C7/P2.**

4. **High — “all sessions” is not the current observation model.**
   OC global events from elsewhere normally go to attention tracking, not `_onEvent`
   (`lib/state/connection/events.dart:178`). OC2 selected channels are directory filtered while global
   ones are not (`lib/api2/gateway_events.dart:817`). Paseo has scoped known-agent streams
   (`lib/paseo/gateway/events.dart:82`) and a no-op global channel (`lib/paseo/gateway.dart:1149`);
   source explicitly handles absent streams for work started through another gateway
   (`lib/paseo/gateway.dart:470`). OC2 normalized session ID can live on the envelope, unlike code
   reading just `part.sessionID` (`lib/api2/gateway_events.dart:726`,
   `lib/state/connection/session_events.dart:205`).
   **Change:** scoped event ingestion plus global-location deduplication, separate phone-feed
   integration, idle/reconnect reconciliation and truthful incomplete coverage. No universal
   instantaneous detection promise. **Applied C4/P3.**

5. **High — last-message recovery misses cards; current Paseo history is unbounded.**
   OC1/OC2 implement bounded pages (`lib/api/opencode_api.dart:535`, `lib/api2/gateway.dart:307`).
   Paseo `messagePage` ignores its limit and calls `messages`, which asks for timeline `limit: 0`
   with a 45-second timeout (`lib/paseo/gateway.dart:507`). The text-tail cache deliberately excludes
   tools (`lib/state/session_tail_cache.dart:8`, `:78`). A card can precede a final assistant message.
   **Change:** bounded newest-tail recovery, candidate identifiers only in prefs, explicit unknown
   coverage, response byte/time/session budgets. Qualify a bounded Paseo read before enabling its
   automatic recovery; trimming an unlimited response is not sufficient. **Applied C4/P3.**

6. **High — bare session/card IDs can route list answers to the wrong owner.**
   A merged row explicitly must not be routed by session ID alone (`lib/domain/chat_feed.dart:103`).
   Phone list sources have separate gateways (`lib/state/connection/phone_agents.dart:778`); opening
   one can resume into a new session ID (`lib/state/connection/phone_agents.dart:1281`). Existing
   permission sends check owner/location/content identity (`lib/state/connection/permissions.dart:653`).
   **Change:** trusted scope + session/message/call identity + content revision; a dedicated feed-item
   lookup; revalidation after source resolution/resume. Put call ID in the answer envelope, and do
   not use agent `id` as the state key. **Applied C3/P4.**

7. **High — Undo and successful HTTP dispatch are not reliable delivery receipts.**
   `DelayedAnswers` flushes on disposal and suppresses uncaught send failures
   (`lib/state/delayed_answers.dart:59`, `:72`, `:82`); lifecycle suspend flushes it too
   (`lib/state/connection/lifecycle.dart:45`). Paseo guards ambiguous sends until history reconciliation
   (`lib/paseo/gateway.dart:575`, `:612`). Correlated prompt IDs explicitly do not guarantee idempotency
   (`lib/domain/server_gateway/interfaces.dart:83`). Existing queue delivery uses persisted markers
   (`lib/state/connection/queue.dart:197`).
   **Change:** one in-flight answer, exact scoped hold key, safe callback fences, durable marker before
   dispatch, explicit sending/unknown/failure states and authoritative-history receipts. No automatic
   answer retry or offline queue in S1. **Applied C3/C4/P4.**

8. **High — Claude and OpenCode do not share a config home or ownership boundary.**
   Native launch sets profile-specific HOME and CLAUDE_CONFIG_DIR
   (`android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:386`).
   Generic setup uses base agent home (`lib/builtin/builtin_linux.dart:367`). OpenCode runs root;
   OC2 isolates its XDG config (`lib/builtin/builtin_linux.dart:885`). Native profile deletion removes
   that profile's agent home (`android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:491`).
   **Change:** register Claude using the exact profile environment; keep helper state within that
   deletion boundary. Track shared OpenCode ownership; deleting/disabling one profile must not erase
   another's working registration. **Applied C5/P5.**

9. **High — runtime registration is not persistence, and config removal is not live removal.**
   OC1 existing add writes config (`lib/api/product_repository/ops_catalog.dart:317`). OC2 only accepts
   runtime scope and documents loss on restart (`lib/api2/gateway_operations/ops_catalog.dart:173`),
   with a different endpoint mapping (`lib/api2/dialect.dart:98`). Refresh may dispose a running
   instance, as documented for providers (`lib/domain/server_gateway/value_types_catalog.dart:363`).
   **Change:** separate desired state, persistent registration, direct-tool visibility and removal;
   add partial/restart-required states; never silently restart active work. Markers stop owned helper
   calls after disablement but are not protection against an agent with filesystem write privileges.
   **Applied C5/P5.**

10. **Medium — schema bounds and “no secrets” need precise, honest semantics.**
    The existing declarative precedent validates types, aggregate lengths and enums before constructing
    immutable values (`lib/domain/mobile_tool_view.dart:20`). Existing external-link policy also
    refuses userinfo/hostless links and confirms destination (`lib/ui/widgets/external_link.dart:48`).
    Kit redaction includes loaded secrets plus patterns (`lib/ui/kit/kit_redact.dart:7`, `:45`, `:118`).
    The original contract leaves numeric/date/default/array alignment/duplicate-ID rules undefined and
    suggests label matching is enough to exclude secrets.
    **Change:** explicit UTF-8/depth/value/scalar bounds and normalization, field/answer validation,
    link inertness, no autofill, no raw rejected payloads in Details, and secret-filter limitations.
    Cards never replace real tool permission flows. **Applied C2/C6/P6.**

11. **High — universal file answers are unsupported; S1 is too broad as written.**
    Paseo accepts only image data attachments (`lib/paseo/mappers.dart:13`), advertises
    `promptImagesOnly` and text-only echoes (`lib/paseo/gateway/capabilities.dart:6`), and validates every
    prompt through that path (`lib/paseo/gateway.dart:574`). Existing picker code enforces 10 MiB each /
    20 MiB aggregate (`lib/ui/screens/chat/chat_attachments.dart:6`).
    **Change:** retain photo for the cross-backend S1; propose deferring file/voice to S2. Gate photo
    on actual support, enforce counts/MIME/size again at dispatch, and never treat text-only receipts
    as proof the agent inspected an image. Voice deferral is a scope choice, not a proven technical
    impossibility. **Applied C1/C6/P7; coordinator must accept scope change.**

12. **Medium — Dart interface is not ready to freeze.**
    The actual normalized transcript type is `MessageWithParts` with text in `Part`, not the proposed
    `Message` (`lib/api/models/models_messages.dart:632`, `:572`). Chat sending also lives mostly in UI
    rather than a reusable controller API (`lib/ui/screens/chat/chat_send.dart:328`, `:369`). Node and
    ask declarations are placeholders, and no public API represents delivery uncertainty.
    **Change:** correct parser type, export existing normalized types from the domain surface, add
    scoped/feed/delivery APIs, and mark the contract not frozen until concrete constructors are agreed.
    Backend must own headless prompt preparation for list answers. **Applied C3/C7/P4.**

13. **Medium — embedding JS is patch-compatible in principle, not proof of release compatibility.**
    Dart execution entry points exist (`lib/builtin/builtin_linux.dart:352`). Node is installed only
    as part of the agent setup (`lib/builtin/agents/paseo_scripts.dart:12`, `:55`); OpenCode installation
    is standalone (`lib/builtin/setup/components.dart:229`). The release gate rejects native, assets,
    fonts and dependency inputs (`scripts/release.sh:319`).
    **Change:** no dependency/native/assets changes; qualify existing absolute Node or report unavailable;
    compare with the actual released baseline before claiming a Shorebird patch. Root OpenCode must not
    blindly execute an agent-writable helper/runtime; qualify ownership or least-privilege execution.
    **Applied C5/C7/P8.**

14. **High — deletion needs async fences, not just a preference naming convention.**
    Profile deletion drains writers before enumerating keys (`lib/state/connection/deletion.dart:150`);
    scoped key matching is generic (`lib/state/profiles.dart:448`). Session removal explicitly clears
    each request store (`lib/state/connection/sessions.dart:283`), and location reset invalidates state
    generations (`lib/state/connection/lifecycle.dart:478`).
    **Change:** cancel reads/holds, invalidate sends, drain writes and scrub identity/marker/temp-data
    stores at session/profile deletion; reject late callbacks. Keep shared host cleanup ownership-aware.
    **Applied C4/C5/P3/P5.**

## Research answers

### 1. Tool parts on the three dialects

| Dialect | Source and normalized input | Events / limitation |
|---|---|---|
| OC1 | wire `part.tool`, `part.state.input` → `Part.toolName`, `ToolState.input`; pending `raw` is separate | `message.part.updated`; REST parser same as live; no current `oc-ui` name fixture |
| OC2 | content `name`, `input`, `callID` → same normalized fields | `session.tool.input.started/delta/ended`, `called`, `progress`, `succeeded/failed`; adapter may miss cached name/input; REST tail repairs |
| Paseo | timeline tool `item.name`, `item.callId`, `item.detail.input` for `detail.type: unknown` | emits `message.updated` then `message.part.updated`; lowercases unknown tool name; complete MCP fixture still needed |

Evidence: finding 1–3 plus `lib/api2/events.dart:256`, `lib/paseo/gateway/events.dart:194`, `:247`.
Expected names are `oc-ui_show` and `mcp__oc-ui__show`; no guessing additional aliases. Current OC2
public docs use `<server>_<tool>` for direct names; that is not a capture from pinned 2.0.10.

### 2. Non-open sessions and cheapest bounded restart recovery

OC1/OC2 can deliver non-open-session parts within their observed scopes; cross-project delivery needs
the global path. Paseo can deliver for known scoped agents, but existing code anticipates missing
streams, and it has no global stream. Phone list gateways are independent of the selected chat.
Neither current feed computes needs-you from cards (`lib/state/connection/chat_feed.dart:174`,
`lib/paseo/chat_feed_source.dart:189`).

Cheapest honest recovery: persist only candidate identities, then reconcile newest bounded tails of
candidates and recent feed sessions; stop once the card and all later messages are covered. Proposed
budget is 20 sessions, two pages of 50, one request at a time, 10 seconds/session, 60 seconds/pass,
1 MiB/response. Retain unknown/incomplete coverage instead of clearing candidates. OC APIs already
page; Paseo needs a proven finite-limit path. Do not use its existing `messagePage` for this sweep.
These are proposed product budgets, not measured performance or a claim of exhaustive discovery.

### 3. In-app registration and removal

- Claude currently pins 2.1.283 (`lib/domain/agent_catalog.dart:324`). Use the CLI under the daemon's
  profile environment, not the generic setup home. User-scope config resolves to
  `/home/oc/.oc-profiles/<profileId>/claude/.claude.json`. Prefer `claude mcp add --scope user
  --transport stdio oc-ui -- <absolute-node> <absolute-script>` after an ownership check. The profile
  location follows native env plus official config-dir semantics; it was not inspected on a phone.
- OC1 already supports persistent config writes via project/global PATCH. Use the existing integration
  path; exact live refresh and clean-start loading still require qualification.
- OC2 runtime add is `PUT /api/experimental/mcp/<name>` and lost on restart. Its isolated root config
  needs persistent `mcp.servers` with direct-tool `codemode: false` per current official documentation;
  verify on the pinned 2.0.10 runtime. Do not assume OC1 `mcp.<name>`/`enabled` works on OC2.
- Node is pinned v24.21.0 at `/home/oc/.local/node/bin/node` with Paseo 0.9.2
  (`lib/builtin/agents/paseo_scripts.dart:12`). It is not guaranteed on OpenCode-only installs.
  `lib/domain/mcp_catalog.dart:22` is a registry-to-draft adapter, not an installer; command examples
  there do not prove executables exist.
- Removing config does not evict loaded catalogs. Report runtime removal pending, block app actions,
  disable owned helper calls and defer restart until safe/authorized. Shared OC ownership is distinct
  from per-profile Claude ownership. Do not enable Paseo daemon MCP injection as a workaround; it is
  deliberately disabled (`lib/builtin/agents/phone_agents_host.dart:292`).

Current primary sources consulted (public semantics, not installed-runtime qualification):
[Claude MCP quickstart](https://code.claude.com/docs/en/mcp-quickstart),
[Claude MCP reference](https://code.claude.com/docs/en/mcp),
[OpenCode MCP](https://opencode.ai/docs/mcp-servers/),
[OpenCode V2 MCP](https://dev.opencode.ai/v2/docs/mcp-servers/).

### 4. Non-blocking message answers versus blocking

**Keep non-blocking for v1.** All three already expose normal prompt dispatch
(`lib/domain/server_gateway/interfaces.dart:54`). Blocking would require a new durable answer channel,
correlation, authentication, timeout/cancellation and app/process-death recovery. Stdio alone does
not provide the app with such a reply channel. OC2's forms are a separate capability, not a portable
solution (`lib/state/connection/forms_inbox.dart:9`).

The compromise is explicit: the tool cannot know whether the app displayed the card; the model may
continue despite instructions. Send card answers only while the session is known idle. OC2 otherwise
steers an active run (`lib/domain/server_gateway/interfaces.dart:49`). Provide a truthful receipt from
history and do not confuse “please delete” with a real permission grant. Keep the composer usable.
MCP transport uses newline-delimited JSON-RPC and tool failures use `isError`:
[MCP stdio](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports),
[MCP tool errors](https://modelcontextprotocol.io/specification/2025-06-18/server/tools).

## Exact edit ledger

All items below are **applied to the working-tree documents**, not implemented or committed.

| ID | Contract edits | Corresponding plan edits |
|---|---|---|
| C1 / P1,P7 | §0 adds qualified S1 scope; §1 reserves file/voice; §6 separates desired on from default-false effective readiness | Revised finish line/non-goals/readiness and S1/S2; removed unsupported competitor assertion |
| C2 / P2,P6 | §1 defines byte/depth/value/scalar/type bounds, normalization, row/series alignment, exact-name and completed/executed admission, safe tool-result wording | Streaming/error parts retain tool rows; domain/helper steps use full input and truthful display claims |
| C3 / P4 | §2 adds trusted scope/call/revision, exact v1 answer envelope, authoritative matching, newest-ask rule, idle gating, delivery state; §3 fixes MessageWithParts, adds GenUiScope/revision/identity/feed lookup/delivery API and unknown state | State table, routing/Undo rules and headless answer ownership rewritten |
| C4 / P3 | §4 specifies scoped/global/phone discovery, fixed recovery budgets, incomplete coverage, identity-only persistence, durable uncertainty markers, cancellation/write draining | Replaces “last message/all sessions” with bounded reconciliation and both feed paths |
| C5 / P5,P8 | §5 specifies bounded stdio helper/Node prerequisite; §6 specifies profile Claude env, root OC1/isolated OC2 config, runtime endpoint, ownership, collision/rollback, per-runtime and restart statuses | Installer, deletion and patch-boundary responsibilities corrected |
| C6 / P6,P7 | §0/§1/§2 specify no-autofill/secret-filter limitations, safe inert links, field/answer validation, photo count/MIME/size and text-only-echo limits | Security rules, photo journey and kit scope corrected; raw rejected payload Details removed |
| C7 / P8 | §3 marks declarations proposed until concrete constructors agreed; §7 adds protocol subprocess, parity, event-loss, source/race/recovery/deletion/install tests and separate release qualification | Adds ownership/freeze checkpoint, test ladder, explicit Phase 1 boundary and outstanding qualification list |

**Outstanding decisions before Phase 2:** coordinator accepts the revised S1 scope, identity/envelope,
recovery/unknown semantics and shared-registration ownership; frontend/backend freeze concrete typed
constructors. Runtime evidence can be gathered during authorized implementation, but an unqualified
backend must remain unavailable. No speculative alias, unlimited recovery or adapter workaround is
an acceptable substitute. All other direct edits are supported corrections or explicit proposed limits.

## Verification and handoff

Documentation-only validation: `git diff --check`; local Markdown link/reference existence and source
line-range checks. No Flutter/Node/native tests ran, consistent with the review-only phase. Working-tree
changes are limited to this review and the two design documents. No `COMMIT_MSG.txt` is needed until
Phase 2. **REVIEW DONE — awaiting the coordinator's go before implementation.**
