# Agent cards — contract v1

Date: 2026-10-07 · Status: behavioral revision accepted by coordinator; **concrete API ready for freeze**.
Plan: [genui-plan-2026-10-07.md](genui-plan-2026-10-07.md).
Review and change ledger: [genui-review-astra-2026-10-07.md](genui-review-astra-2026-10-07.md).
Backend owns this file. Frontend builds from the accepted revision; subsequent changes need both sides' OK.

## 0. Slice and trust boundary

S1 delivers choice, form, confirm, photo and display cards on qualified in-app Ubuntu backends.
File and voice asks move to S2; remote installation and blocking results remain out of S1.
Parsing adapters cover OC1, OC2 and Paseo, but an adapter is not proof that its running agent exposes
this tool. Each backend stays unavailable until its direct-tool name, complete input, registration,
restart recovery and answer path are qualified. OC2 Code Mode requires particular attention (§6).

Cards are untrusted conversation content, not permission requests. Confirming a card sends text to
the agent; it never authorizes shell execution, approves a permission, or bypasses existing safety
controls. A label filter cannot detect all secret solicitations. Never autofill from credentials,
clipboard, environment or account state. The person can still type sensitive text; disclose that
answers are sent to the agent and retained in conversation history. Apply existing kit redaction
when displaying content; do not copy card/answer payloads into logs, diagnostics or notifications.

## 1. Tool (agent → app)

MCP server id **`oc-ui`**, one tool **`show`**. Input = one card (JSON object):

```jsonc
{
  "v": 1,                       // required integer, exactly 1
  "id": "db-choice",            // required, [a-z0-9-]{1,48}; agent label, NOT trusted identity
  "title": "Pick a database",   // required, 1..120 Unicode scalar values
  "body": [ /* Node, 0..40 */ ], // required
  "ask": { /* Ask, optional; absent = report card */ }
}
```

Unknown keys anywhere → unreadable (strict). Wrong types and explicit null for optional fields are
invalid. No coercion, executable expressions, markup rendering, remote resources or dynamic widgets.
Limits apply before normalization: compact UTF-8 JSON ≤ 32,768 bytes, nesting depth ≤ 8 (root = 1),
≤ 4,096 total values/containers. Bounded traversal precedes encoding; cyclic/non-JSON Dart input is
invalid. A streamed input preview is not JSON to parse. String lengths count Unicode scalar values
in both Dart and JS. Reject unpaired surrogates. Plain text defaults to ≤ 2,000 scalars.

For presentation text, remove C0/C1 controls except LF/TAB and remove Unicode bidi formatting controls
(U+061C, U+200E–200F, U+202A–202E, U+2066–2069). Trim labels/titles, reject empty required labels.
Preserve LF/TAB and whitespace in code/text bodies. IDs and URLs must validate as supplied, not be
silently repaired. Use app-owned bidi isolation for displayed agent text.

### Nodes (`body`)

Each node has required `type` plus exactly the fields below; `?` denotes optional.

| type | fields | bounds |
|---|---|---|
| `text` | `text` | ≤ 2,000 |
| `keyValue` | `rows: [{key, value}]` | 0..20 rows; key 1..60, value ≤ 200 |
| `list` | `items: [{text, done?}]`, `style: "bullet"\|"check"` | 0..30 items; `done` bool, default false |
| `table` | `columns: [string]`, `rows: [[string]]` | 1..6 cols, 0..20 rows; each row exactly column count; cells ≤ 80 |
| `chart` | `kind: "bar"\|"line"`, `unit?`, `labels: [string]`, `series: [{name, values: [number]}]` | 1..3 series, 1..30 labels; each values array equals label count; finite numbers; labels/name ≤ 80, unit ≤ 24 |
| `code` | `language?`, `text` | text ≤ 4,000; language ≤ 32; display only |
| `diffStat` | `files: [{path, added, removed}]` | 0..30 files; path ≤ 256, display only; counts integers 0..2^53−1 |
| `progress` | `label`, `value` | label ≤ 120; finite number 0..1 |
| `callout` | `tone: "info"\|"warning"\|"success"`, `text` | text ≤ 500 |
| `link` | `label`, `url` | label 1..120; URL ≤ 2,048; absolute https, nonempty host, no userinfo/whitespace/control chars |

A link is inert until an explicit tap routed through `openExternalLink`. No preview fetch, embedded
browser, attachment download, file opening or command execution is derived from any node.
Charts must handle zero ranges and extreme finite values without overflow; accessibility includes
a plain-text summary. Paths, language names and labels are never instructions to the platform.

### Asks (`ask`)

Each ask has required `kind` plus exactly these fields:

| kind | fields | answer value |
|---|---|---|
| `choice` | `options: [{id, label, detail?}]` (2..8), `multi?: bool` (default false) | `{"choice": [id…]}` |
| `form` | `fields: [Field]` (1..12), `submitLabel?` (≤ 24) | `{"form": {fieldId: value}}` |
| `confirm` | `confirmLabel?`, `cancelLabel?` (≤ 24), `tone?: "normal"\|"danger"` (default normal) | `{"confirm": true\|false}` |
| `photo` | `purpose` (1..200), `max?` (integer 1..4, default 1) | `{"photo": n}` + exactly n image attachments |

Choice/option IDs use `[a-zA-Z0-9_\-]{1,32}`, unique within that ask/field; labels 1..120, detail ≤ 200.
Single choice requires exactly one known ID; multi requires 1..N distinct known IDs.

`Field = {id, label, type: "text"|"multiline"|"number"|"toggle"|"select"|"date", required?, placeholder?,
default?, options?, min?, max?}`. IDs use `[a-zA-Z0-9_]{1,32}`, unique within the form; labels 1..120,
placeholder ≤ 120; `required` defaults false. `options` is required only for select (2..20 distinct
options), forbidden otherwise. `min/max` are finite numbers only for number, with min ≤ max.
Defaults and answers must match the field type and range: text/multiline strings ≤ 2,000, finite
number, bool, known select ID, or real calendar date `YYYY-MM-DD` (no timezone), respectively.
Required text must be nonblank; required toggle false is valid. Optional missing values are omitted,
not null. Extra answer keys are invalid. Missing defaults remain unset, not silently selected.

Reject field/option IDs or labels matching obvious credential solicitations (`password`, `passwd`,
`token`, `secret`, `api_key`, `apikey`, case-insensitive after separator normalization). This is a
heuristic, not a security guarantee; keep the disclosure and no-autofill boundary above.

`file` and `voice` are reserved, unsupported in S1 (unreadable reason `unsupportedAsk`). Photo controls
require prompt-image support and existing camera/gallery support; no new permission/plugin is added.
Only explicitly picked image data is attached, never an agent-supplied URL/path. Enforce 1..ask.max,
10 MiB per image and 20 MiB aggregate before dispatch, plus existing MIME validation. Picker cancel
sends nothing. Capturing a photo does not send it; the person still confirms the answer.

### Admission and tool result

Recognize exact qualified names only. Expected candidates are `oc-ui_show` (OpenCode) and
`mcp__oc-ui__show` (Paseo/Claude); these names have **not** been captured for this feature yet.
Never match suffixes, substrings, output prose, or tool titles. Require an assistant-owned,
non-synthetic tool part, complete structured input, `status == completed`, and `executed == true`.
Pending/running/error/unexecuted parts retain ordinary tool presentation and cannot be answered.
Missing input/name after event loss triggers reconciliation rather than a permanent invalid-card mark.
No reconstructed JSON from `inputJson`/raw deltas is admitted.

`show` validates and returns immediately. Its result says:

`Card accepted for display in OpenCode Mobile. If it asks a question, end your turn and wait for the
person's next message tagged [oc-ui answer <id>]. This call does not return their answer.`

It must not claim the phone displayed the card: no delivery acknowledgment exists. Invalid/disabled
calls return a generic MCP tool error (`isError: true`), without echoing rejected input. The helper
cannot know the host tool-call ID; the app supplies that correlation in its answer.

## 2. Identity, state and answer (app → agent)

Trusted identity is `(profileID, sourceId, directory, workspace, sessionID, messageID, callID)` plus
a content revision derived from validated input. Profile/source/location come from app routing;
message/call IDs come from the transport. None come from the card JSON. Missing IDs preclude actions.
Bind reads/sends to the captured connection generation and server identity; an endpoint edit under
the same profile invalidates persisted candidates/markers for routing and all in-flight callbacks.
A repeated agent `id` is allowed as a label but never replaces an earlier call or matches its answer.

A normal user message uses the existing prompt gateway, with exactly two text lines:

```
[oc-ui answer db-choice] Postgres
{"v":1,"cardId":"db-choice","callId":"call_123","value":{"choice":["postgres"]}}
```

Line 1 has an app-generated single-line summary ≤ 240 scalars. Line 2 is compact JSON ≤ 32,768 UTF-8
bytes; allowed envelope keys are exactly `v/cardId/callId/value`. Validate value against the original
ask at encoding and decoding. Only an authoritative, non-synthetic **user** message after the
matching call, in the same scope/session, can settle it. Never infer settlement from assistant text,
optimistic bubbles or a matching prefix alone. Hide JSON only for a fully validated matching receipt;
otherwise render ordinary text. A tag is correlation, not cryptographic proof of UI authorship.
Photo receipts report submitted image count; on Paseo the server echo is text-only, so they must not
claim the server retained image attachments or that the model inspected them.

Per session, only the newest eligible ask after the latest authoritative user turn is actionable;
earlier unanswered asks become `passedOver`. The first later authoritative user message answers a
card only when its envelope matches; otherwise it passes the card over. Reports do not supersede asks.
Revert, message/part removal or changed tool input invalidates dependent state and triggers reconciliation.

Card states: waiting, answered, passedOver, report, unknown. `unknown` means incomplete/stale history;
it is never equivalent to answered, absent, or actionable waiting. Waiting controls require a known
idle session; while it is running show the card read-only. “End your turn” is advisory, not enforced.
Held/sending/deliveryUnknown are controller delivery states, not transcript facts. A send response
alone is not a transcript receipt. No offline card-answer queue or automatic retry in S1.

`answerGenUi` must resolve the card's owning source (including list rows), revalidate scope/content/
state after asynchronous preparation or Paseo resume, and reject stale/deleted/busy/unsupported cards.
A resumed session may have a new ID; only an established app-owned mapping may retarget the call.
One in-flight answer per trusted identity, shared across chat/list. Hold for the existing 3-second
Undo window using the full identity as key; catch and surface errors rather than relying on
`DelayedAnswers` (which swallows them). Before the actual send, repeat the stale/deletion checks.
On uncertain delivery, reconcile authoritative history; never auto-resend. On definite failure retain
the answer for an explicit retry. Disposal/flush callbacks must not send after deletion or disablement.

## 3. Dart API (concrete types ready for coordinator freeze)

The accepted behavior is unchanged. The declarations below now exist under
`lib/domain/genui/`; `gen_ui.dart` is the sole public import and re-exports
`Part`, `MessageWithParts`, `PromptAttachment`, and `ChatFeedItem` for UI callers.
Constructors copy collections into unmodifiable collections (including table rows).
They are app-side value constructors, not validation entry points: agent input must
pass `genUiFromPart`. Field defaults/form answers accept only JSON scalar values at
the validator boundary. No file/voice variants exist in S1.

`GenUiCard.identity` includes the seven trusted scope/transport components; compare
`revision` separately before dispatch. `GenUiScope` has value equality. Setup
`agents` lists contain verified ready runtimes, including in partial/restart states.
Setup reasons are enums for localized UI, never raw diagnostics. Delivery failures
surface through `ProductException` and `genUiDeliveryFor`.

### `gen_ui_nodes.dart`

```dart
/// Immutable presentation values; constructors do not validate agent input.
sealed class GenUiNode {
  const GenUiNode();
}

final class GenUiText extends GenUiNode {
  const GenUiText({required this.text});
  final String text;
}

final class GenUiKeyValueRow {
  const GenUiKeyValueRow({required this.key, required this.value});
  final String key;
  final String value;
}

final class GenUiKeyValue extends GenUiNode {
  GenUiKeyValue({required List<GenUiKeyValueRow> rows})
    : rows = List.unmodifiable(rows);
  final List<GenUiKeyValueRow> rows;
}

enum GenUiListStyle { bullet, check }

final class GenUiListItem {
  const GenUiListItem({required this.text, this.done = false});
  final String text;
  final bool done;
}

final class GenUiList extends GenUiNode {
  GenUiList({required List<GenUiListItem> items, required this.style})
    : items = List.unmodifiable(items);
  final List<GenUiListItem> items;
  final GenUiListStyle style;
}

final class GenUiTable extends GenUiNode {
  GenUiTable({required List<String> columns, required List<List<String>> rows})
    : columns = List.unmodifiable(columns),
      rows = List.unmodifiable(
        rows.map((row) => List<String>.unmodifiable(row)),
      );
  final List<String> columns;
  final List<List<String>> rows;
}

enum GenUiChartKind { bar, line }

final class GenUiChartSeries {
  GenUiChartSeries({required this.name, required List<double> values})
    : values = List.unmodifiable(values);
  final String name;
  final List<double> values;
}

final class GenUiChart extends GenUiNode {
  GenUiChart({
    required this.kind,
    this.unit,
    required List<String> labels,
    required List<GenUiChartSeries> series,
  }) : labels = List.unmodifiable(labels),
       series = List.unmodifiable(series);
  final GenUiChartKind kind;
  final String? unit;
  final List<String> labels;
  final List<GenUiChartSeries> series;
}

final class GenUiCode extends GenUiNode {
  const GenUiCode({this.language, required this.text});
  final String? language;
  final String text;
}

final class GenUiDiffFile {
  const GenUiDiffFile({
    required this.path,
    required this.added,
    required this.removed,
  });
  final String path;
  final int added;
  final int removed;
}

final class GenUiDiffStat extends GenUiNode {
  GenUiDiffStat({required List<GenUiDiffFile> files})
    : files = List.unmodifiable(files);
  final List<GenUiDiffFile> files;
}

final class GenUiProgress extends GenUiNode {
  const GenUiProgress({required this.label, required this.value});
  final String label;
  final double value;
}

enum GenUiCalloutTone { info, warning, success }

final class GenUiCallout extends GenUiNode {
  const GenUiCallout({required this.tone, required this.text});
  final GenUiCalloutTone tone;
  final String text;
}

final class GenUiLink extends GenUiNode {
  const GenUiLink({required this.label, required this.url});
  final String label;

  /// Inert text. Open only through the app's external-link gate.
  final String url;
}
```

### `gen_ui_asks.dart`

```dart
sealed class GenUiAsk {
  const GenUiAsk();
}

final class GenUiOption {
  const GenUiOption({required this.id, required this.label, this.detail});
  final String id;
  final String label;
  final String? detail;
}

final class GenUiChoiceAsk extends GenUiAsk {
  GenUiChoiceAsk({required List<GenUiOption> options, this.multi = false})
    : options = List.unmodifiable(options);
  final List<GenUiOption> options;
  final bool multi;
}

enum GenUiFieldType { text, multiline, number, toggle, select, date }

final class GenUiField {
  GenUiField({
    required this.id,
    required this.label,
    required this.type,
    this.required = false,
    this.placeholder,
    this.defaultValue,
    List<GenUiOption> options = const [],
    this.min,
    this.max,
  }) : options = List.unmodifiable(options);
  final String id;
  final String label;
  final GenUiFieldType type;
  final bool required;
  final String? placeholder;

  /// Only String, num, bool, or null (unset); checked at the trust boundary.
  final Object? defaultValue;
  final List<GenUiOption> options;
  final double? min;
  final double? max;
}

final class GenUiFormAsk extends GenUiAsk {
  GenUiFormAsk({required List<GenUiField> fields, this.submitLabel})
    : fields = List.unmodifiable(fields);
  final List<GenUiField> fields;
  final String? submitLabel;
}

enum GenUiConfirmTone { normal, danger }

final class GenUiConfirmAsk extends GenUiAsk {
  const GenUiConfirmAsk({
    this.confirmLabel,
    this.cancelLabel,
    this.tone = GenUiConfirmTone.normal,
  });
  final String? confirmLabel;
  final String? cancelLabel;
  final GenUiConfirmTone tone;
}

final class GenUiPhotoAsk extends GenUiAsk {
  const GenUiPhotoAsk({required this.purpose, this.max = 1});
  final String purpose;
  final int max;
}
```

### `gen_ui_answers.dart`

```dart
sealed class GenUiAnswer {
  const GenUiAnswer();
}

final class GenUiChoiceAnswer extends GenUiAnswer {
  GenUiChoiceAnswer(List<String> ids) : ids = List.unmodifiable(ids);
  final List<String> ids;
}

final class GenUiFormAnswer extends GenUiAnswer {
  GenUiFormAnswer(Map<String, Object> values)
    : values = Map.unmodifiable(values);

  /// String, num or bool values; absent optional fields are omitted.
  final Map<String, Object> values;
}

final class GenUiConfirmAnswer extends GenUiAnswer {
  const GenUiConfirmAnswer(this.value);
  final bool value;
}

final class GenUiPhotoAnswer extends GenUiAnswer {
  const GenUiPhotoAnswer(this.count);
  final int count;
}
```

### `gen_ui_types.dart`

```dart
final class GenUiScope {
  const GenUiScope({
    required this.profileID,
    required this.sourceId,
    required this.directory,
    this.workspace,
  });
  final String profileID;
  final String sourceId;
  final String directory;
  final String? workspace;

  @override
  bool operator ==(Object other) =>
      other is GenUiScope &&
      profileID == other.profileID &&
      sourceId == other.sourceId &&
      directory == other.directory &&
      workspace == other.workspace;
  @override
  int get hashCode => Object.hash(profileID, sourceId, directory, workspace);
}

final class GenUiCard {
  GenUiCard({
    required this.scope,
    required this.id,
    required this.title,
    required this.sessionID,
    required this.callID,
    required this.messageID,
    required this.revision,
    required List<GenUiNode> body,
    this.ask,
  }) : body = List.unmodifiable(body);
  final GenUiScope scope;
  final String id;
  final String title;
  final String sessionID;
  final String callID;
  final String messageID;
  final String revision;
  final List<GenUiNode> body;
  final GenUiAsk? ask;

  /// Transport identity; revisions are compared separately before dispatch.
  String get identity => jsonEncode([
    scope.profileID,
    scope.sourceId,
    scope.directory,
    scope.workspace,
    sessionID,
    messageID,
    callID,
  ]);
}

sealed class GenUiParse {
  const GenUiParse();
}

final class GenUiParsed extends GenUiParse {
  const GenUiParsed(this.card);
  final GenUiCard card;
}

final class GenUiUnreadable extends GenUiParse {
  const GenUiUnreadable({required this.reason});
  final GenUiProblem reason;
}

enum GenUiProblem {
  notACard,
  version,
  tooLarge,
  unknownKey,
  badValue,
  secretField,
  unsupportedAsk,
}

enum GenUiCardState { waiting, answered, passedOver, report, unknown }

enum GenUiDeliveryState { idle, held, sending, deliveryUnknown, failed }
```

### `gen_ui_status.dart`

```dart
/// Ready agents, derived from evidence rather than an agent-supplied label.
enum GenUiAgent { claude, openCode1, openCode2 }

/// Stable localization keys. Never holds command, config or exception text.
enum GenUiSetupProblem {
  unsupportedHost,
  runtimeMissing,
  notQualified,
  permissionDenied,
  conflict,
  installationFailed,
  registrationFailed,
  verificationFailed,
  removalFailed,
  storageFailed,
  busy,
}

sealed class GenUiSetupStatus {
  const GenUiSetupStatus();
  List<GenUiAgent> get agents => const [];
}

final class GenUiSetupOff extends GenUiSetupStatus {
  const GenUiSetupOff();
}

final class GenUiSetupUnavailable extends GenUiSetupStatus {
  const GenUiSetupUnavailable({required this.reason});
  final GenUiSetupProblem reason;
}

final class GenUiSetupInstalling extends GenUiSetupStatus {
  const GenUiSetupInstalling();
}

final class GenUiSetupOn extends GenUiSetupStatus {
  GenUiSetupOn({required List<GenUiAgent> agents})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
}

final class GenUiSetupPartial extends GenUiSetupStatus {
  GenUiSetupPartial({required List<GenUiAgent> agents, required this.reason})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
  final GenUiSetupProblem reason;
}

final class GenUiSetupRestartRequired extends GenUiSetupStatus {
  GenUiSetupRestartRequired({required List<GenUiAgent> agents})
    : agents = List.unmodifiable(agents);
  @override
  final List<GenUiAgent> agents;
}

final class GenUiSetupFailed extends GenUiSetupStatus {
  const GenUiSetupFailed({required this.reason});
  final GenUiSetupProblem reason;
}
```

### `gen_ui_codec.dart`

```dart
GenUiParse? genUiFromPart(
  Part part, {
  required GenUiScope scope,
  required String sessionID,
  required String messageID,
}) => throw UnimplementedError();

GenUiCardState genUiStateFor(
  GenUiCard card,
  List<MessageWithParts> transcript, {
  required bool tailComplete,
}) => throw UnimplementedError();

({String summary, Object? value})? genUiAnswerIn(
  MessageWithParts message,
  GenUiCard card,
) => throw UnimplementedError();

String genUiAnswerText(GenUiCard card, GenUiAnswer answer) =>
    throw UnimplementedError();
```

The coordinator-approved second API addition is:

```dart
({String cardId, String summary})? genUiAnswerEnvelope(MessageWithParts message);
```

This validates the bounded user-message envelope without claiming that a particular
card was settled. Card state still requires the scoped, card-specific validator.
The four controller additions supply trusted assistant scope/ownership and cached
part parsing, derive state from authoritative tail coverage, expose only an answered
receipt summary, and undo a held (never already-sent) answer respectively.

### `gen_ui_controller.dart`

```dart
/// Public controller surface. Implemented by ConnectionController integration.
abstract interface class GenUiController {
  GenUiParse? genUiCardForPart(String sessionID, String messageID, Part part);
  GenUiCardState genUiStateForCard(GenUiCard card);
  String? genUiAnswerSummary(GenUiCard card);
  void undoGenUiAnswer(GenUiCard card);
  List<GenUiCard> waitingCardsForSession(String sessionID);
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item);
  Future<void> answerGenUi(
    GenUiCard card,
    GenUiAnswer answer, {
    List<PromptAttachment> attachments = const [],
  });
  bool get genUiEnabled;
  Future<void> setGenUiEnabled(bool on);
  GenUiSetupStatus get genUiStatus;
  GenUiDeliveryState genUiDeliveryFor(GenUiCard card);
}
```

The four codec function bodies are temporary type-stage stubs; implementations replace
only their bodies after this checkpoint. `GenUiController` records the exact surface
that `ConnectionController` will implement. Capability `ServerCapabilities.genUi`
remains default false and is added during controller integration. Waiting cards extend
the existing feed needs-you projection without replacing permission/question/form priority.
No frozen constructor/property/signature is changed without coordinator agreement.

## 4. Discovery, persistence and deletion

Track non-open sessions on every currently observed eligible source/location; include OC global
streams with directory-aware routing/deduplication and the separate phone-agent feed gateways.
Paseo streams are not assumed exhaustive. Idle/session updates, reconnect, app restart and explicit
refresh trigger coalesced tail reconciliation. No polling loop that loads every session's history.

Proposed fixed recovery budget: prioritize persisted pending identities then recent feed rows,
at most 20 sessions per foreground pass, concurrency 1, at most 2 pages × 50 messages/items per
session, 10 seconds per session and 60 seconds per pass. Cancel at scope/generation change; retain
explicit incomplete coverage at every limit/error. Paseo timeline items are not interchangeable
with OC messages. Its current `messagePage` ignores limit and loads all history: add and qualify a
bounded tail read before using it here. A small number of unbounded reads is not a bounded recovery.
Bound response payloads at the transport before decoding (1 MiB per recovery response); a client-side
`take` after fetching unlimited history is insufficient.

A complete newest tail proves state only for a contained card and all its subsequent messages.
Reaching a page limit without that coverage leaves a known candidate unknown. Sessions outside the
budget are not declared card-free. Opening/refreshing a row prioritizes it; expose partial recovery
through the feed's existing incomplete/loading state. Restart recovery requires reconnection;
S1 does not promise offline rendering of full cards.

Persist only schema version + bounded scoped pending identities/revisions for prioritization
(max 100 identities, 64 KiB per profile); no duplicated bodies, form values, image data or secret
material. Use `oc.genui.<what>.<profileId>`. Server transcript remains authoritative.
`SessionTailCache` is not a card cache. Unknown sends need a bounded durable dispatch marker before
network dispatch, containing scope/call/revision and a correlation ID where supported, never values.
Markers survive restart until reconciliation or explicit user resolution; do not silently evict an
unresolved marker to allow a resend (refuse new sends if the marker budget is full).

Deletion invalidates recovery/dispatch generations first, cancels holds, drains writers before the
preference-key sweep, removes session entries and owned temporary attachments, and prevents late
callbacks from recreating state. Disablement immediately blocks local card actions and clears holds.
Shared root OpenCode registration is removed only for the last managed owner; per-profile Claude
registration/helper state is removed with that profile. Never remove another owner's config entry.

## 5. Embedded MCP helper

One zero-dependency Node script embedded as a Dart string. Use only existing Dart/native execution
entry points; no native, asset, dependency, manifest or permission edits. Qualify an already available
absolute Node executable; an OpenCode-only installation may lack it and must report unavailable.
No implicit npm/npx install, network listener, callback URL or tool-time download.

Implement MCP initialization/version negotiation, initialized notification, ping, tools/list and
tools/call with UTF-8 newline-delimited JSON-RPC; stdout contains protocol only. Bound each input
frame to 64 KiB before JSON decoding; generic protocol/tool errors do not echo input. Validation in
JS and Dart uses a common fixture corpus to prove identical acceptance and normalization. Read an
app-owned enabled marker on each call so an already-running helper stops accepting disabled calls.
No arbitrary filesystem access: only that fixed marker is read at tool time. No credentials/env
values are returned. Stdio does not require a separate app authentication token.

## 6. Install and effective capability

Use fixed app-authored paths and arguments, strict profile IDs, atomic writes, restrictive permissions,
symlink refusal and an ownership manifest. Do not execute an agent-writable helper/runtime as root:
qualify a least-privilege invocation or appropriately owned executable/helper for the OpenCode path.
The private agent Node path is a discovered candidate, not blanket authorization for root execution.
Never interpolate agent input into a shell script. Preserve
unrelated settings/credentials; do not print config contents or raw CLI output. Refuse an unowned
`oc-ui` collision. Serialize installation changes, rollback only owned writes, and report partial
failure honestly. Do not restart active agents or dispose a running OpenCode instance silently.

- **Claude:** execute the pinned `claude mcp add --scope user --transport stdio oc-ui --
  <absolute-node> <absolute-script>` under the same profile environment as its daemon: uid 1000 (`oc`),
  `HOME=/home/oc/.oc-profiles/<profileId>` and `CLAUDE_CONFIG_DIR=$HOME/claude`. The effective user config
  is `$CLAUDE_CONFIG_DIR/.claude.json`, not root's config or base `/home/oc/.claude.json`. Use the CLI
  for config semantics; guard name ownership first. Qualify removal and new-session loading on the
  pinned CLI. Place profile helper/state inside its existing deletion boundary.
- **OC1:** root-owned persistent `mcp.<name>` uses `type: local`, `command` argv, `enabled`. Existing
  app integration supports config PATCH (including global), with runtime refresh semantics to verify.
  Prefer the established gateway/config path; do not invent an endpoint or assume live reload.
- **OC2:** root-owned isolated config is under `/root/.oc-opencode2/config/opencode`. Current public
  docs describe `mcp.servers.<name>`, `disabled`, and `codemode: false` for direct tools. Prove these
  against the pinned binary before enabling. Existing runtime add is
  `PUT /api/experimental/mcp/<name>`, location-scoped and lost at restart; it is not persistence.

Desired on defaults true only for an eligible managed installation; effective capability remains
false until helper self-check, persisted registration, direct-tool discovery and dialect qualification
succeed. Track readiness per runtime/source; partial success does not enable all agents. Configuration
removal and loaded-tool removal are distinct. Off blocks this owner's local actions immediately,
removes owned registration where possible, and reports restartRequired/partial until runtime state
is verified. Other owners can keep the shared OpenCode registration; say so rather than promise it
vanished globally. A helper marker is an operational disable control, not a security boundary against
an agent already permitted to edit files in the same Ubuntu account.

## 7. Errors and verification

Backend returns stable safe problem/status codes or `ProductException` with fixed plain words;
frontend localizes through ARB. No raw tool/JSON/exception/config/command output as copy; Details may
show only the `GenUiProblem` name. Test failures must not dump credential-bearing fixtures.

Required focused coverage: JS/Dart validation parity and boundary corpus; tool mapping fixtures for
each dialect; partial/error/unexecuted and result-only arrivals; cross-source ID collisions; newest
ask/typed reply/receipt/revert; busy gating and stale resume; duplicate tap/undo/failure/unknown send;
restart with stale identities and dispatch markers; bounded recovery and partial coverage; profile/
session deletion during sends and reads; ownership-preserving install/disable/rollback and both feed
projections. No real config or host mutation in tests. A subprocess test must exercise the embedded
helper's actual MCP framing, initialization and calls. Phone qualification remains distinct from
unit tests and from patch compatibility against an exact released baseline.
