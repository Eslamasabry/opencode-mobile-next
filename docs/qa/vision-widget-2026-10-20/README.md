# Home-screen widget: "A, adaptive" (2026-10-20)

Owner pick (vision page, "Home-screen widget"): **A, adaptive**. One card that
shows the one thing that matters now. Something needs you: that request, with
Allow once only where the widget can answer today, else Open, in the amber
needs-you style. Something running: its title with one live line. Nothing: the
last finished conversation plus a quiet "New conversation" field. The same card
in every size; larger sizes only add rows. Remove the ring widget, the list
widget and raw reply text.

## Audit: element → source → verdict

Written before editing. Sources are the snapshot the app publishes
(`lib/state/connection/widgets.dart` → `lib/background/widget_snapshot.dart`,
key `flutter.oc.widgetSessions`), the native action path
(`WidgetV2Policy.actionEnabled`, `SessionWidgetActions`) and the domain gateway.

| Element in the pick / concept | Real source | Verdict |
| --- | --- | --- |
| Which conversation needs you (the request) | `requestID` + `ask` per row, from `permissionForSession` (OC1/OC2 `Permission.Request`, and any gateway that reports permissions through the domain) | **Build.** |
| Question wording ("Run npm install?") | `ask.kind` + `ask.file` (the command, or the file name), turned into "Run %s?" / "Edit %s?" / "Read %s?" by `WidgetCopy.shortAsk`; else the app's own generic question (`ask.q`) | **Build.** The concept's "Run database migration?" is invented; we use the request's real command. |
| The exact command under the question | `ask.file` (command text for `bash`, file name otherwise), already redacted (`KitRedact`) and capped at 80 code points | **Build.** Shown in mono when the question does not already carry it; Allow is shown only when the command is visible (decision 1: never allow what you cannot read). |
| Allow once on the widget | Live: `oc/background` `widgetAction` → `ConnectionController._handleWidgetAction` → gateway permission reply `once`. Cold: on-demand engine, advertised only for saved OpenCode profiles (`onDemandActions`) | **Build, gated** on `WidgetV2Policy.actionEnabled` (connection state + advertised actions + identity proof), never on the flavor. |
| Open | Activity intent into the chat (`SessionWidgetIntents.chatIntent`) | **Build.** Always present on a request. |
| Reject on the widget | Same action path as Allow | **Left out of the card** (pick: "Allow / Open"; the vision decision lists Reject for Inbox, server sheet and notifications). Reject stays one tap away in the chat. |
| Agent question with options / free text | `PendingQuestion` (`questions` map) | **Build as Open-only.** Not published today: the row looked "working". Now published as `needsYou` + the question text; no answer buttons (decision 1: questions open the chat). |
| Needs-you rows from other projects/servers | `WidgetOtherConversation.needsYou` (Conversations feed) | **Build as Open-only.** Was ranked first but not marked; now marked. No request identity, so no Allow. |
| "MacBook · api-server" (server · project) | Project: `safeWidgetProject` (display name only). Server: `serverDisplayName(profile)` ("This phone", the saved name) — not in the snapshot today; other conversations carry their feed `sourceLabel` | **Build:** add a short `server` display name per row. Never a URL or host:port (`plainServerName`). |
| Running: title + one live line ("Writing index.html · 2 min") | Title: session title. Live line: `_runningToolDetail` → `toolSentence` ("Editing files…", "Running a command…"). Elapsed: client-side `startedAt` | **Adapt.** The widget contract keeps step lines to generic activity labels (no file names on the launcher), so the line reads "Editing files · 2:13". The time counts in the launcher with `Chronometer` only while the connection is live; offline shows no clock. |
| Nothing waits: last finished conversation | Newest row by `updatedAt` (ready) | **Build:** its title and "project · 26 min ago". |
| Quiet "New conversation" field | `newConversationIntent`: voice overlay (`/widgets/voice`) when live and a last project exists; else opens the app | **Build.** The field names its target when a project is known ("New conversation in neon-breaker"). |
| Mic in the field | System speech activity via the overlay; only when connected | **Keep** (existing, real). |
| Larger sizes show more rows | The same snapshot rows (max 6) | **Build:** hairline rows (title + one state line), no inline buttons (one answer place per card). |
| "Needs you" amber mark | Lead state from the row | **Build:** amber dot + amber word once in the header; no amber border (the pick rejects state three times). |
| 2×2 ring "2 need you" | `pendingCount` exists, but a ring implies progress | **Rejected** by the owner; not built. |
| Raw reply text | `preview` (latest assistant prose) | **Removed** from the card (pick: remove raw reply text). Still published for the app's own surfaces. |
| Model name | `model` (display name only) | **Not shown.** Nothing in the pick needs it. |
| Graphite + mint palette | `graphiteDark` / `graphiteLight` roles (`lib/ui/theme_roles.dart`); dark/light from the app's published `appTheme` | **Build:** surface1 card, hairline edge, mint only on Allow once, amber only on needs-you. |
| ViewFlipper of several running turns | Several busy rows | **Removed:** one card shows one thing; larger sizes list the others as rows. |
| Picker entries | Four providers (2×2, 4×2, 4×4 list, 4×1 strip), one renderer | **Adapt:** the list entry is hidden from the picker (`hide_from_picker`) and renders the same card; already-placed widgets keep working. 2×2, 4×2 and 4×1 stay in the picker as sizes of the same card. |
| ZTE launcher traps | `WidgetSizePolicy.currentSize` → one `RemoteViews`; `previewImage` PNGs | **Kept:** one render for the current size; preview PNGs re-rendered from the new layouts (see below). |

Paseo and phone agents: their permission asks reach the widget only when the
live controller reports them through `permissionForSession`; with no live
channel they advertise no cold actions, so the card shows Open. No agent
identity is shown on the card (nothing in the pick needs it), so the persona
registry is not involved.

## What the owner will see, per state

One card in every size. Header: the app mark in a small tile and "OpenCode";
on the right only "● Needs you" in amber (or "Sending…" / "Check the
conversation" after a widget tap). Spare height sits under the header so the
story and its action stay together at the foot; a 4×4 spends it on rows.

| State | Card (2×2 and up) | 4×1 strip | 2×1 |
| --- | --- | --- | --- |
| A permission ask the widget can answer (live, or saved OpenCode profile on demand) | "Run npm install?" (a short command reads in the question; a long one sits under it in mono, whole or not at all), "This phone · neon-breaker", mint **Allow once** + outlined **Open →** | question, amber "Needs you · project", mint **Allow** | amber dot + question |
| An ask it cannot answer (agent question, form, another server's ask, a command too long to show whole, no action proof) | the question, meta, **Open →** alone | question, amber line, no button | amber dot + question |
| Working, live | title, mint dot + "Editing files · 2:13" counting in the launcher, meta, quiet field "Steer while it works…" (mic only where steering works) | title, mint dot + live line, mic | mint dot + title |
| Working, not live | title, grey dot + "Offline · last seen 5 min ago" (a cached turn is never shown as running) | same line | title |
| Nothing waiting | last conversation's title, "This phone · neon-breaker · 26 min ago", quiet field "New conversation in neon-breaker" (+ mic when dictation can start it) | title, "project · 26 min ago", mic | title |
| No snapshot yet | "Open the app to connect"; a tap opens the app | same | same |
| 4×4 (placed before; hidden from the picker now) | the same card, plus up to five hairline rows: title and one state line (amber "Needs you", mint live line, or "project · time"), no buttons | — | — |

Colours: Graphite surface1 card with a 1 dp hairline, mint only on Allow once
(and the live dot), amber only on needs-you, never red. Light follows the
app's published appearance. "Transparent" and "tinted" widget styles still apply.

## How it is checked without an emulator

`WidgetCardRenderTest` (Robolectric 4.17, native graphics, API 35) applies the
real `RemoteViews` from `SessionWidgetRenderer` (layouts + binders, exactly what
the launcher receives) and draws them. It asserts what each state shows (Allow
only with the command visible, Open-only cases, no running clock offline, rows
only on tall cards, RTL), and writes PNGs:

```bash
# QA renders (3x) and the picker preview images (420 dpi, light + night)
WIDGET_RENDER_OUT=/tmp/wrender WIDGET_PREVIEW_OUT=$PWD/android/app/src/main/res \
  gradle -p android :app:testReleaseUnitTest --tests '*WidgetCardRenderTest*'
# Home-screen composites at 412x915 (the card PNGs on an aurora home screen)
TZ=UTC flutter test --dart-define=WIDGET_RENDERS=/tmp/wrender \
  --dart-define=VISION_OUT=<dir> tool/capture/vision_widget_test.dart
```

The previews (`drawable-nodpi`, `drawable-night-nodpi`) are therefore rendered
from the same layouts the widget uses, which is what the ZTE launcher shows in
its picker (it ignores `previewLayout`). The conversation entries no longer
declare `previewLayout`, so there is no second hand-written preview to drift.

Not proven here: launcher behaviour (resize callbacks, ZTE's size reporting),
real fonts of the owner's phone, and the action round trip on a device. The
existing `WidgetSizePolicy.currentSize` single-render path is unchanged.

## Visual passes (against concept A)

1. First native render: card structure right; composite chrome had no font;
   question smaller than the concept; meta small; buttons short.
2. Title 22sp, meta 15sp, 44dp buttons; launcher chrome uses the app font.
   Spare height all fell under the header (big gap above the question, 12dp
   above the buttons).
3. Spare height shared above and below the story; header 14sp with a 24dp tile.
   2×2 header truncated "OpenCode…" beside "Needs you"; 4×4 left a band above the
   buttons; 2×1 showed a grey dot when idle.
4. Narrow cards show mark + status only; rows stretch to share the height; idle
   has no dot. Questions cut to one line while the meta line still had room;
   2× text clipped the header; offline "working" offered steering.
5. Second title line outranks meta; header wraps; a turn not seen live offers
   Open only. At 2× text the header still truncated and "Allow once" filled its
   button.
6. Narrowness measured in text widths, so 2× text gets mark + status and
   "Allow".
