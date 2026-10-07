# Agent cards — contract v1

Date: 2026-10-07 · Plan: [genui-plan-2026-10-07.md](genui-plan-2026-10-07.md) · Owner of this file:
backend (Astra). Frontend builds only from this file. Changes after the freeze need both sides' OK.

## 1. Tool (agent → app)

MCP server id **`oc-ui`**, one tool **`show`**. Input = one card (JSON object):

```jsonc
{
  "v": 1,                       // required, exactly 1
  "id": "db-choice",            // required, [a-z0-9-]{1,48}, unique within the conversation
  "title": "Pick a database",   // required, 1..120 chars
  "body": [ /* Node, 0..40 */ ],
  "ask": { /* Ask, optional; absent = report card */ }
}
```

Unknown keys anywhere → the card is unreadable (strict). Total serialized input ≤ 32 KB. All strings
are plain text, trimmed, control characters removed, ≤ 2,000 chars unless stated.

### Nodes (`body`)

| type | fields | bounds |
|---|---|---|
| `text` | `text` | ≤ 2,000 |
| `keyValue` | `rows: [{key, value}]` | ≤ 20 rows; key ≤ 60, value ≤ 200 |
| `list` | `items: [{text, done?}]`, `style: "bullet"\|"check"` | ≤ 30 items |
| `table` | `columns: [string]`, `rows: [[string]]` | ≤ 6 cols, ≤ 20 rows, cell ≤ 80 |
| `chart` | `kind: "bar"\|"line"`, `unit?`, `labels: [string]`, `series: [{name, values: [number]}]` | ≤ 3 series, ≤ 30 points, finite numbers |
| `code` | `language?`, `text` | ≤ 4,000 |
| `diffStat` | `files: [{path, added, removed}]` | ≤ 30 files, ints ≥ 0 |
| `progress` | `label`, `value` (0..1) | |
| `callout` | `tone: "info"\|"warning"\|"success"`, `text` | ≤ 500 |
| `link` | `label`, `url` (https only) | opened only via `openExternalLink` |

### Asks (`ask`)

| kind | fields | answer value |
|---|---|---|
| `choice` | `options: [{id, label, detail?}]` (2..8), `multi?: bool` | `{"choice": [id…]}` |
| `form` | `fields: [Field]` (1..12), `submitLabel?` (≤ 24) | `{"form": {fieldId: value}}` |
| `confirm` | `confirmLabel?`, `cancelLabel?`, `tone?: "normal"\|"danger"` | `{"confirm": true\|false}` |
| `photo` | `purpose` (≤ 200), `max?` (1..4) | `{"photo": n}` + n image attachments |
| `file` | `purpose`, `max?` (1..4) | `{"file": n}` + n file attachments |
| `voice` | `purpose` | `{"voice": "<transcribed text>"}` |

`Field = {id, label, type: "text"|"multiline"|"number"|"toggle"|"select"|"date", required?, placeholder?,
default?, options?: [{id,label}] (select, 2..20), min?, max? (number)}`. Field ids `[a-zA-Z0-9_]{1,32}`.
Ids or labels that look like secrets (`password`, `token`, `secret`, `api_key`, `apikey`, case-insensitive)
→ unreadable card.

### Tool result (server → agent, immediate)

`Shown to the person as card "<id>". Their answer arrives as their next message, starting with
[oc-ui answer <id>]. End your turn now and wait.` (Report cards: `Shown to the person.`)

## 2. Answer (app → agent)

A normal user message through the existing prompt path:

```
[oc-ui answer db-choice] Postgres
{"choice":["postgres"]}
```

Line 1: tag + human summary (what the person sees in the receipt). Line 2: compact JSON answer value.
Attachments ride along as normal prompt attachments. The app hides the JSON line in the transcript and
shows the receipt instead.

## 3. Dart API (frozen names)

```dart
// lib/domain/genui/gen_ui.dart  (backend)
sealed class GenUiNode {}            // GenUiText, GenUiKeyValue, GenUiList, GenUiTable, GenUiChart,
                                     // GenUiCode, GenUiDiffStat, GenUiProgress, GenUiCallout, GenUiLink
sealed class GenUiAsk {}             // GenUiChoiceAsk, GenUiFormAsk, GenUiConfirmAsk,
                                     // GenUiPhotoAsk, GenUiFileAsk, GenUiVoiceAsk
class GenUiField {}                  // id, label, type (GenUiFieldType), required, placeholder,
                                     // defaultValue, options, min, max
class GenUiCard {
  final String id, title, sessionID, callID;
  final String? messageID;
  final List<GenUiNode> body;
  final GenUiAsk? ask;
}
sealed class GenUiParse {}           // GenUiParsed(card) | GenUiUnreadable(reason: GenUiProblem)
enum GenUiProblem { notACard, version, tooLarge, unknownKey, badValue, secretField }
enum GenUiCardState { waiting, answered, passedOver, report }

GenUiParse? genUiFromPart(Part part, {required String sessionID}); // null = not an oc-ui part
GenUiCardState genUiStateFor(GenUiCard card, List<MessageWithParts> transcript);
({String summary, Object? value})? genUiAnswerIn(Message message, String cardId);

sealed class GenUiAnswer {}          // GenUiChoiceAnswer(ids), GenUiFormAnswer(values),
                                     // GenUiConfirmAnswer(bool), GenUiPhotoAnswer, GenUiFileAnswer,
                                     // GenUiVoiceAnswer(text)
String genUiAnswerText(GenUiCard card, GenUiAnswer answer); // the 2-line message

// ConnectionController (backend, single-owner)
List<GenUiCard> waitingCardsForSession(String sessionID);
Future<void> answerGenUi(GenUiCard card, GenUiAnswer answer,
    {List<PromptAttachment> attachments = const []});   // throws ProductException
bool get genUiEnabled; Future<void> setGenUiEnabled(bool on);  // installs/removes registration
GenUiSetupStatus get genUiStatus;    // off | installing | on(agents) | failed(reason)
// ServerCapabilities.genUi : bool  (server has the oc-ui tool registered)
// ChatFeedItem: status needsYou when a card waits (no new field)
```

Frontend may add nothing to `lib/domain` or `lib/state`; missing API = ask the backend.

## 4. Errors and copy

Backend throws `ProductException` with plain words; frontend shows them via existing patterns. No
raw tool/JSON/exception text as copy; Details may show the `GenUiProblem` name.
