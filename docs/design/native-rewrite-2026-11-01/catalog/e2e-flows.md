# Device flows for the native rewrite

39 cross-app flows, written 2026-11-01 for Maestro (YAML driven by visible text and accessibility labels). Together they prove all **240** behaviors in `behaviors.jsonl` with `portable_e2e = true` (`BEHAVIORS.md` shows, for each, the flow that proves it).

## The rule: Flutter first, native second

1. **Prove each flow on the current Flutter app first** (2.1.0+57, release build from `oc_app-batch1`, emulator API 36). A flow that fails on Flutter is a bad flow or a known Flutter gap: fix the flow, or record the gap in a `KNOWN-FLUTTER-GAPS` line at the top of that flow. Only a flow that is green on Flutter is allowed to judge the native app.
2. **Then run the same file, unchanged, against the native app.** The native app passes when the same visible text and accessibility labels are found. That means the native app reuses the existing strings (`lib/l10n/app_en.arb`, `app_ar.arb`) as its source of truth, and gives every icon-only control the same accessibility label the Flutter app gives it.
3. Steps tagged `[adb]` run on the host through the runner script (`adb` or the emulator console). Steps tagged `[fx]` act on a fixture server. Everything else is a plain Maestro command.
4. The bracketed ids after a step name the catalog rows that step exercises. A step asserts the clauses of a row that a person or a log can observe; clauses that only a unit test can reach (an internal ordering, a bound on a list) stay in the row's native unit test, which is why a row can be both `e2e` and `unit`.
5. `E2E-39` is a bridge flow: it seeds the Flutter app, installs the native APK over it, and asserts on the native app. Its Part B baseline runs on Flutter alone; the rest needs the native build.

### Writing a step as Maestro

| Step wording | Maestro |
|---|---|
| tap "X" | `tapOn: "X"` |
| see "X" / not see "X" | `assertVisible: "X"` / `assertNotVisible: "X"` |
| type "T" into "Field" | `tapOn: "Field"` then `inputText: "T"` |
| wait for "X" | `extendedWaitUntil: {visible: "X", timeout: 30000}` |
| scroll to "X" | `scrollUntilVisible: {element: "X"}` |
| press Home / Back | `pressKey: Home` / `back` |
| open the shade | `swipe: {start: "50%,0%", end: "50%,70%"}` (or `[adb] cmd statusbar expand-notifications`) |
| airplane mode | `setAirplaneMode: enabled` (Android) or `[adb] cmd connectivity airplane-mode enable` |
| clean install | `launchApp: {clearState: true}` |

Example, step 1-2 of E2E-01:

```yaml
appId: io.github.eslamasabry.opencode_mobile
---
- launchApp:
    clearState: true
- assertVisible: "Set up on this phone"
- assertVisible: "Connect to my computer"
- assertVisible: "Pair with a code, or type its address"
- tapOn: "Connect to my computer"
```

Compose note for the native app: Maestro reads accessibility text, so give icon-only controls a `contentDescription` equal to the Flutter semantics label; use `testTagsAsResourceId` only where no stable text exists. Do not rely on resource ids in these flows.

## Environment matrix

| Name | Setup | Used by |
|---|---|---|
| AVD-A (default) | Pixel 8 profile, API 36 Google APIs x86_64, 4 GB RAM, 16 GB data, English, animations on | all flows unless noted |
| AVD-S (small) | same, 1.5 GB RAM | E2E-17 |
| AVD-F (full disk) | same, data partition filled with `fallocate` until under the setup requirement | E2E-17 |
| AVD-T (tablet) | 10-inch tablet profile, keyboard attached | E2E-22, E2E-24 |
| AVD-AR | AVD-A with the system language set to Arabic for the Arabic repeat of E2E-22 and E2E-23 | E2E-22, E2E-23 |
| Phone smoke | ZTE nubia NX721J, Android 16: only E2E-01, 05, 06, 08, 25, 26 as a manual smoke after an emulator pass; read-only adb (`ps`, `dumpsys`, `logcat`), no screen captures of the owner phone | release gate |

Runner contract (one script for both apps): start the fixtures, create/boot the AVD, `adb install` the APK under test, run the flow file, collect the Maestro report and `adb logcat -d` (never print the fixture passwords). Fixture passwords are generated per run and passed as `${SERVER_PASSWORD}`.

## Fixtures to build once (shared by both apps)

| Id | What it is | Contract the flows rely on |
|---|---|---|
| FX-OC2 | A real `opencode2 serve` (HTTP Basic user `opencode`, generated password) with a scripted stub model provider | listens on host `:4097` (emulator `10.0.2.2:4097`); project `app` (git repo with `README.md`, `notes.txt`, `data.csv`, `lib/main.dart`, a binary) and project `other`; request log readable by the runner; ability to drop the event stream, delay or refuse a given endpoint, time out a pending request |
| FX-OC2-B | A second instance of FX-OC2 on another port with its own sessions | two saved servers, same session ids as FX-OC2 for some sessions, one session with a placeholder ISO title and one with leaked tool-call markup |
| FX-TLS | Reverse proxies in front of FX-OC2 | trusted cert, self-signed cert (swappable for a different one), expired cert, a redirector, plain HTTP on a private address, plain HTTP on a public-looking name |
| FX-PASEO | A Paseo daemon with scripted Claude and Pi agents, Claude history on disk, terminals | probe, agent list with models/modes, archive, update (reports versions), terminals that stream |
| FX-CODEX | A Codex app-server stub | health, project scope, thread list, scripted turn with an approval, steer, abort, socket drop |
| FX-NODE | A memory node (FactsBook) stub | pairing code to token, inbox accepts a capture, unreachable on demand |
| FX-GH | GitHub device-flow stub | issues a code, approve/deny/expire on demand, returns a token |
| FX-GIT | A bare repository reachable from the phone | push accepted or rejected |
| FX-LINUXDL | Local mirror of the Ubuntu rootfs, pinned OpenCode and tools | throttle, cut, corrupt checksum on demand |
| FX-USAGE | Stub usage endpoints | Codex and MiniMax readings, expired token, offline |
| FX-AUTH | Sign-in stub for phone agents | accepts the sign-in script, prints a fixed code |

### Stub model magic prompts (FX-OC2)

The stub model answers deterministically so assertions never depend on a real model. Prompt text starting with `e2e:` is a command; anything else is echoed.

| Prompt | Reply |
|---|---|
| `e2e:echo <text>` | `ECHO <text>`, streamed at about 20 words per second |
| `e2e:long` | about 60 seconds of text with headings, a table, a fenced block with a very long line, a file path `lib/main.dart:42`, and these links: `https://example.com/a`, `http://example.org/b`, `javascript:alert(1)`, `intent://x`, `https://user:pw@example.com/` and a 3,000-character URL |
| `e2e:cut` | text that ends because the length limit was reached |
| `e2e:slow-finish` | a 30-second reply with a 6-item task list, then a finished reply `All done.` |
| `e2e:ask-edit` | requests permission to edit `notes.txt`, then edits it |
| `e2e:ask-shell <cmd>` | requests permission to run `<cmd>` |
| `e2e:question` / `e2e:choice` / `e2e:choice4` | one free-text question / a 3-option question / a 4-option question |
| `e2e:secret` | a reply containing the fake key `sk-e2e-FAKE-0123456789abcdef`, which is also registered as a provider key on the server |
| `e2e:files-edit` | edits two files so the changes count shows |

## Flow index

| Flow | Title | Behaviors | Needs |
|---|---|---|---|
| E2E-01 | First launch, welcome screen and first server by typed address | 4 | welcome, clean state |
| E2E-02 | Pair with a code, a scanned QR and several addresses | 5 | camera, clipboard |
| E2E-03 | Connection failures are explained in plain words | 6 | FX-TLS |
| E2E-04 | Transport trust: HTTPS, consent for plain HTTP, certificate pins | 4 | FX-TLS |
| E2E-05 | Send a prompt, watch it stream, stop it, search it, follow links | 9 | long history |
| E2E-06 | Approve, deny and always-allow from inside the app | 6 | asks-first |
| E2E-07 | Answer from the notification shade | 8 | notifications |
| E2E-08 | Needs-you, finished-reply and the ongoing status notification | 10 | notifications, lock screen |
| E2E-09 | Keep running in the background | 2 | notification permission |
| E2E-10 | Network loss, reconnect and never sending twice | 6 | network control |
| E2E-11 | Drafts, saved prompts and queued prompts survive anything | 8 | two servers |
| E2E-12 | Attach photos, files, spreadsheets and mentions | 4 | files on device |
| E2E-13 | Dictate by voice, auto-send and the model download | 3 | microphone, network control |
| E2E-14 | Files, previews, sharing out and exporting a project | 4 | Wi-Fi, linux |
| E2E-15 | Git page, GitHub sign-in, remotes and undo | 7 | linux, FX-GH, FX-GIT |
| E2E-16 | Terminal shells that outlive the screen | 2 | linux |
| E2E-17 | Set up the built-in Linux on a phone: preflight, progress, cancel, resume | 17 | linux, avd-small, avd-full, FX-LINUXDL |
| E2E-18 | The phone's own server: start, crash recovery, stop, honest status | 12 | linux |
| E2E-19 | Remove Linux, free space and reset without losing projects | 4 | linux |
| E2E-20 | Dev servers, ports, projects and processes on the phone | 8 | linux |
| E2E-21 | Conversations list: merged servers, pins, titles, status | 10 | two servers |
| E2E-22 | Find any setting: search, groups, per-server pages | 11 | two servers |
| E2E-23 | Language, text size and look | 6 | Arabic, widget |
| E2E-24 | Command launcher, Go to and the conversation menu | 3 | keyboard |
| E2E-25 | Launcher shortcuts, Quick Settings tile, deep links and share-in | 10 | adb intents |
| E2E-26 | Home-screen widgets | 7 | widgets |
| E2E-27 | Scheduled prompts and the morning summary | 4 | clock, exact alarms |
| E2E-28 | AI Team on the phone | 4 | linux, AI Team |
| E2E-29 | Codex: connect, talk, steer, abort | 5 | FX-CODEX |
| E2E-30 | Claude Code and Pi through Paseo | 7 | FX-PASEO |
| E2E-31 | Agents on this phone: sign-in terminal and running by name | 3 | linux, FX-AUTH |
| E2E-32 | Memory on the phone: install, start, pair, capture, stop | 9 | linux, FX-NODE |
| E2E-33 | Plan usage and subscriptions | 2 | FX-USAGE |
| E2E-34 | Privacy: diagnostics, reports and what never leaves | 7 | clipboard, share sheet |
| E2E-35 | Remove a server and everything stored for it | 2 | two servers |
| E2E-36 | Several servers: what needs me, across everything | 5 | two servers |
| E2E-37 | Providers, models and how to connect them | 5 | catalog delay |
| E2E-38 | Tips and the Make it yours tour | 3 | clean state |
| E2E-39 | Upgrade in place from the Flutter app to the native app | 11 | BRIDGE: Flutter APK then native APK |

## Flows

### E2E-01 First launch, welcome screen and first server by typed address

**Goal.** A brand-new install reaches a connected, empty Conversations list by typing a bare server address, with no bottom navigation and the right first-run treatment.

**Needs.** FX-OC2 (password-protected fixture server); device with app data cleared.

**Proves.** B-NAV-017, B-NAV-018, B-NAV-024, B-NAV-037 (4).

**Steps.**

1. [adb] clear app data; launch the app. `launchApp` with `clearState: true`.
2. see "Set up on this phone" and "Connect to my computer" (welcome screen); see "Pair with a code, or type its address". Only a device that started on this welcome counts as new: first run ends on the Conversations list after the first connect (step 6) and opens no empty conversation; the same build upgraded over saved servers never shows this welcome (asserted in E2E-39). [B-NAV-017]
3. tap "Connect to my computer": see "Paste pairing code", "Scan pairing code" and the typed-address field on one screen.
4. [adb] put the bare address `10.0.2.2:4097` (no scheme) on the clipboard and paste it into the address field on the welcome flow. see it gain the right scheme (`http://` for a private/LAN address, `https://` for a public host) before testing. [B-NAV-018]
5. type the fixture password into "Server password"; tap "Test connection"; see the connected verdict, then tap "Connect".
6. see the Conversations list as the first screen after connecting (cold start lands on the Conversations list, not on a project or settings page). [B-NAV-024]
7. assert there is no bottom navigation bar on a phone-size screen (no tab strip at the bottom edge; Files and Terminal are reached from inside a project or conversation). [B-NAV-037]
8. [adb] force-stop and relaunch the app; see the Conversations list again (not the welcome screen), still connected.

### E2E-02 Pair with a code, a scanned QR and several addresses

**Goal.** Pairing works from a pasted code, a scanned QR and a code with several addresses, falls through unreachable addresses, and refuses unsafe ones in plain words.

**Needs.** FX-OC2 with `opencode2 pair` output (JSON with `urls`, `username`, `password`); emulator camera set to a virtual scene showing the fixture QR; welcome screen.

**Proves.** B-PROTO-064, B-PROTO-068, B-SEC-124, B-PROTO-069, B-ANDR-049 (5).

**Steps.**

1. On welcome tap "Connect to my computer", then "Paste pairing code". [adb] put the fixture pairing JSON on the clipboard (whitespace around it) and paste. see "Paired with <host>." The username from the code is kept; a code with a blank username connects as the default user. [B-PROTO-064]
2. Repeat with a code whose first two addresses are dead and the third works: see "Paired with <host> — chosen from 3 addresses in the code." (addresses tried in order, falls through refused ones, a host answering 401 still counts as the host, a bare host is normalised). [B-PROTO-068]
3. Repeat with a code whose only address is plain `http://` on a public host: see the app hold it and ask for explicit consent for that origin before any request is sent; tap "Cancel" and confirm nothing was contacted (fixture request log empty). Repeat with a private-LAN `http://` address: it is held until confirmed, then paired. [B-SEC-124]
4. Repeat with a malformed code (not JSON, no password, wrong types): see a plain failure sentence ("That pairing code is the wrong shape…"), never a crash or a raw exception.
5. tap "Scan pairing code"; see the camera preview and the instruction line; point the emulator camera at the fixture QR; see the same result as pasting the code. Scan a QR that is not a pairing code: see a plain refusal with a reason. [B-PROTO-069]
6. [adb] revoke the camera permission (`pm revoke ... CAMERA`), tap "Scan pairing code": see a plain explanation that camera access is off and a way to paste instead; on an emulator without a camera see the "nothing to scan with" explanation and no fake camera answer. [B-ANDR-049]

### E2E-03 Connection failures are explained in plain words

**Goal.** Each way a connection can fail reads as one plain sentence with a way forward; technical text appears only under Details.

**Needs.** FX-OC2; FX-OC2 stopped / wrong password / unknown host / TLS variants from FX-TLS; Tailscale-range address that does not answer.

**Proves.** B-PROTO-094, B-PROTO-095, B-NAV-008, B-NAV-007, B-NAV-009, B-SEC-104 (6).

**Steps.**

1. Add a server with the right address and a wrong password: see a sentence asking for the password (not "401"). With an empty password on a password-protected v2 server: see "needs a password" wording, distinct from "wrong password". [B-PROTO-094]
2. Point the address at a host that is not an OpenCode server (any web page): see "not an OpenCode server". Point at a legacy OpenCode 1 server: see an upgrade instruction. A misspelt host name: see a spelling hint. A refused port: see "is it running?". A black-hole address: see the timeout line pointing to the setup guide. [B-PROTO-095]
3. Point at the FX-TLS server whose certificate changed after trust: see "The server's certificate changed" and a Review action; the app blocks before sending the password. A certificate problem names the certificate; an unreachable remote reads "not reachable" with network checks; a Tailscale address that did not answer says to check Tailscale. [B-NAV-008]
4. Use a loopback address while nothing listens: see a Retry offer that does not assume Termux. A Codex-type server with a missing or rejected token asks for the token; a local Codex refusal points to the listener or tunnel; a remote Codex refusal recommends wss. [B-NAV-007]
5. Tap Test connection three times: after the third failure see the extra line that retrying will not start a server. For the app-managed phone server that does not answer see that it is stopped, and for OpenCode inside the app starting it is the fix. [B-NAV-009]
6. tap "Technical details" on any failure: only there is the raw error shown, verbatim. Assert that no screen text elsewhere contains an exception class name, "SocketException", a stack frame, HTTP status JSON or a protocol tag. [B-SEC-104]

### E2E-04 Transport trust: HTTPS, consent for plain HTTP, certificate pins

**Goal.** Credentials only ever go to an origin the person trusted: HTTPS by default, a per-origin choice for remote HTTP, pins that survive restarts and do not leak to other servers.

**Needs.** FX-TLS: HTTPS server with trusted cert, self-signed cert, expired cert, a redirecting server, a plain-HTTP server on a public-looking name and one on a private address; FX-OC2 behind each.

**Proves.** B-SEC-007, B-SEC-120, B-SEC-153, B-SEC-154 (4).

**Steps.**

1. Add the HTTPS server with a trusted certificate: connects; the password is sent only over TLS (fixture log shows no Basic header on any plaintext hop). [B-SEC-007]
2. Add the plain-HTTP server on a private address with credentials: accepted without a prompt; a bare private address gains https first. Add the public-name HTTP server: see an explicit HTTP consent prompt naming the origin; loopback and https never prompt. [B-SEC-120]
3. Type URLs with embedded credentials, a query or a fragment into the address field: see them refused; a path is ignored for trust; the same host on another port is a separate trust decision; consent granted for port 4097 does not apply to 4098. Try to attach a certificate pin to an HTTP address: refused (a pin cannot be downgraded to HTTP); a SHA-256 pin with colons is accepted, a truncated one is refused. [B-SEC-153]
4. Trust the self-signed server by pinning its fingerprint: connects. Replace its certificate with a different self-signed one: see the blocked-before-password message. The expired certificate and an untrusted certificate show two different messages. The redirecting server is not followed. [B-SEC-007]
5. [adb] force-stop and relaunch, then rebuild the profile list (sign out of other profiles): the public-HTTP choice and the pin are still in force; a second profile on the same host does not inherit the pin; editing the host, port or scheme drops the pin and asks again; a hand-edited wrong-origin pin never restores trust. [B-SEC-154]

### E2E-05 Send a prompt, watch it stream, stop it, search it, follow links

**Goal.** The core chat loop on the fixture server: a streaming reply that is smooth, stoppable, searchable and safe to tap.

**Needs.** FX-OC2 connected; project `app` with files; stub model magic prompts `e2e:echo`, `e2e:long`, `e2e:cut`.

**Proves.** B-CHAT-017, B-CHAT-060, B-CHAT-086, B-CHAT-115, B-SEC-064, B-CHAT-064, B-CHAT-082, B-PROTO-061, B-PROTO-034 (9).

**Steps.**

1. Open an empty project folder: the starter suggestions offer ways to make something. Open `app` (files and Git history): the starters offer ways to work on the files and include "what changed recently"; in a folder with files but no Git history that starter is absent; while the folder is still loading no starters show; an unfinished starter ends with a space, not an ellipsis; in Arabic the starters are Arabic. [B-CHAT-017]
2. type `e2e:long` and tap Send. see the reply grow word by word without the list jumping; a streaming caret shows at the end of the reply. The reply contains headings, a table and a fenced block with a long line. (What read-aloud skips - fences, tables, link targets - is proven by the unit test.) [B-CHAT-060]
3. While it streams tap "Stop": see the reply end as stopped (not as an error) and the phrase chips stay; the Send control returns. [fx] verify the server saw an abort. Then send `e2e:cut`: the reply ends cut off by length and a "Continue" button appears. A reply that ends with an error notice hides the phrase chips; a stop or a length cut does not. [B-CHAT-086]
4. Open the in-conversation search, type a word that appears in the reply, in a tool output and in a file the agent read: see one match count that includes all three places; keep the search open while a new reply streams and see the count grow; delete a message and see it drop. [B-CHAT-115]
5. In the reply, tap `https://example.com/a`: the safe-link confirmation names the host and opens the browser. Tap the `http://example.org/b` link: opens only after the plain-http warning. Tap `javascript:alert(1)`, `intent://…`, a link with credentials and a very long URL: nothing opens and no browser starts. [B-SEC-064]
6. A path such as `lib/main.dart:42` in the reply is rendered as a file link; tap it: the file viewer opens at line 42. A plain word with a dot that is not a path is not linked. [B-CHAT-064]
7. Long-press a phrase chip above the input and hide it; see it gone and its starter too; open the phrase list and tap "Show hidden phrases" to bring it back. [B-CHAT-082]
8. Open a conversation with more than a few hundred messages (fixture `long-history`): it opens at the newest message; scroll up and see older pages arrive one page at a time, with no jump and no duplicate rows. [B-PROTO-061]
9. [fx] drop the event stream connection mid-reply (close the socket, keep the server): see the status line say it is reconnecting, then the reply continues and no message is duplicated. [B-PROTO-034]

### E2E-06 Approve, deny and always-allow from inside the app

**Goal.** Permission asks read as plain questions, are answered once, and the automatic modes behave exactly as promised.

**Needs.** FX-OC2 in asks-first mode; magic prompts `e2e:ask-edit`, `e2e:ask-shell ls`, `e2e:question`.

**Proves.** B-AGENT-221, B-CHAT-123, B-SEC-147, B-AGENT-225, B-AGENT-224, B-CHAT-076 (6).

**Steps.**

1. Start a new conversation on a fresh server profile: see that nothing is auto-answered; send `e2e:ask-edit`; see a card "Permission needed" asking in plain words whether to edit `notes.txt`; buttons "Allow once" and "Deny". The OpenCode 2 shell/edit asks wait for you. Questions from the agent are never answered automatically. [B-AGENT-221]
2. Check the card wording for each kind in turn (edit, shell, read outside the project, web fetch): each asks its own plain question and names the file or command; an ask about writing outside the project names the file and its folder. [B-CHAT-123]
3. tap "Allow once": the tool runs, exactly one reply is sent and the card settles. Repeat and tap "Deny": the tool is refused and the agent continues. [B-CHAT-123]
4. Trigger the same kind of ask three times: on the third distinct ask see "Always allow" offered once; accept; restart the app; the same kind in the same scope no longer asks; the decline path explains what happens. Corrupt the stored rule file ([adb] on a userdebug emulator with `adb root`, overwrite it with garbage): no always-allow offer appears and the app does not crash. [B-SEC-147]
5. Turn the conversation to automatic approval while an ask is already waiting: the waiting ask is answered (also after a reconnect). A conversation set to ask keeps waiting. Approve-everything also reaches conversations the app has not opened yet. [B-AGENT-225]
6. [fx] block the reply endpoint (server refuses the reply) while tapping "Allow once": the request stays visible and pending with an explanation, not lost; turning approval off mid-flight does not recall a sent reply and later asks appear again. [B-AGENT-224]
7. [fx] time out the request server-side, then tap "Allow once": the card goes away with no error; a different 404 is a real failure and keeps the card. [B-CHAT-076]

### E2E-07 Answer from the notification shade

**Goal.** A request is answered from a notification exactly once, against the exact request, with safe text.

**Needs.** emulator with notifications allowed; FX-OC2; app in the background (home screen); magic prompts `e2e:ask-edit`, `e2e:question`, `e2e:choice`, `e2e:choice4`.

**Proves.** B-SEC-105, B-AGENT-031, B-CHAT-074, B-AGENT-032, B-ANDR-038, B-PROTO-046, B-CHAT-076, B-CHAT-037 (8).

**Steps.**

1. Send `e2e:ask-edit`, press Home. Open the shade. see one notification naming the program/sub-command or the file with the right verb ("edit notes.txt"), subtitle `<server> · <project>`; no secret from a command appears in the title or body. [B-SEC-105]
2. Tap "Allow once" in the shade: the exact pending request is approved, once; open the app and see the card settled. Repeat with "Deny": rejected without a durable grant. Tap the in-app button and the notification button at the same instant ([adb] two taps in parallel): exactly one reply reaches the server and the first decision wins. [B-AGENT-031, B-CHAT-074]
3. Send `e2e:question` (one free-text question): the notification offers a reply field; type an answer and send; the question is answered and the chat shows it. An empty reply is refused before any request. For `e2e:choice` (3 short options) see three option buttons that answer with their label; for `e2e:choice4` (4 options) see only an Open action. A choice-only question never offers the reply field. [B-AGENT-032, B-ANDR-038]
4. On OpenCode 2, reply on a permission ask with text: the request is rejected with that message on that exact request. If the request id is stale (answered elsewhere first), the notification refreshes instead of resolving a different request. [B-PROTO-046]
5. Answer an ask in the app; the notification for it disappears. Answer one on the server after it timed out: the notification Allow is handled with no error. [B-CHAT-076]
6. Open the app with a question waiting: the waiting card is in the feed, and typing a reply clears the attention marker for that conversation. [B-CHAT-037]

### E2E-08 Needs-you, finished-reply and the ongoing status notification

**Goal.** The phone tells the truth about what the agent is doing when the app is away, without leaking content to the lock screen.

**Needs.** emulator with notifications allowed; FX-OC2 and a second saved server FX-OC2-B; magic prompts `e2e:slow-finish`, `e2e:ask-edit`, `e2e:secret`.

**Proves.** B-ANDR-023, B-ANDR-041, B-ANDR-042, B-ANDR-043, B-ANDR-011, B-ANDR-040, B-ANDR-047, B-SEC-149, B-ANDR-026, B-ANDR-033 (10).

**Steps.**

1. Open a new conversation, send a message; the offer to turn on finished-reply notifications appears only after the first reply has completed (never before) and only once. Accept it and allow the system permission. [B-ANDR-023]
2. Send `e2e:slow-finish`, press Home. see one ongoing notification: "1 working" with a real progress bar when the agent has tasks ("5 of 6"), no endless spinner when it has none, long runs shown in hours. Start a second conversation: several chats show as lines with "+N more" after two. [B-ANDR-041, B-ANDR-042]
3. Let the agent ask for permission: the notification switches to a needs-you state naming whose request, offering Open, pluralising for two, beating working (and saying how many still work), never repeating the request text. [B-ANDR-043]
4. Let the reply finish: a notification says "Reply finished", names the chat and its first line; open it and the alert clears; a finished reply raises an alert once whatever the event order; a background error replaces the completion; sub-agent alerts stay quiet. [B-ANDR-011, B-ANDR-040]
5. Pull down the shade while a reply runs: the Stop action stops exactly the chat the status named; after the chat finished, Stop leaves it alone. [B-ANDR-040]
6. Lock the screen ([adb] `input keyevent 26`): the lock-screen version shows only counts, no titles or lines. With two saved servers the app name reads "on <server>"; with one it does not. [B-ANDR-047]
7. Send `e2e:secret` (reply contains a fake provider key): neither the notification, nor the live status, nor a copied log contains it (masked), and the same after an app restart. [B-SEC-149]
8. The "needs you" preset is offered once, remembered after restart, explains interrupted asks, can be changed in Settings, and cannot be answered twice. [B-ANDR-026]
9. Return to the app: outstanding alerts are dismissed. The live status counts running sessions and pending requests, names the freshest busy session, updates on state changes and tool events, its running sentence follows the app language (switch to Arabic and see Arabic copy) and tool sentences never repeat the tool input. [B-ANDR-011, B-ANDR-033]

### E2E-09 Keep running in the background

**Goal.** Opt-in background mode stays connected while the app is away and is honest when Android refuses it.

**Needs.** emulator API 35+ with notification permission denied initially; FX-OC2.

**Proves.** B-ANDR-012, B-ANDR-009 (2).

**Steps.**

1. Open Settings, search "keep running"; turn on the live connection while the notification permission is denied: it stays off and says why ("Allow notifications"); unsupported platforms and an unavailable service leave it off with a reason. [B-ANDR-012]
2. Grant notification permission; turn it on again: the switch turns on only after Android confirms the service started; kill and relaunch the app: the saved choice is restored through the native service. A malformed native status never grants the capability. [B-ANDR-012]
3. With live mode on, send `e2e:slow-finish`, press Home, wait 60 s: the reply completes and the notification says so (the connection stayed active in the background). [B-ANDR-009]

### E2E-10 Network loss, reconnect and never sending twice

**Goal.** Flaky networks lose nothing and duplicate nothing.

**Needs.** FX-OC2 with request log; emulator with network control: `adb shell svc wifi`, `svc data`, `cmd connectivity airplane-mode`.

**Proves.** B-PROTO-088, B-STATE-040, B-STATE-039, B-STATE-038, B-PROTO-045, B-STATE-008 (6).

**Steps.**

1. Send `e2e:slow-finish`; [adb] airplane mode on at mid-reply; see the connection status line in plain words (never a raw error); airplane mode off: the reply continues, no duplicated message. [B-PROTO-088]
2. [adb] switch Wi-Fi to mobile data at the instant a prompt is on the wire (hold the fixture response): after recovery the server has the prompt exactly once; a prompt whose send was cut by the switch is held for review rather than silently re-sent. [B-PROTO-088, B-STATE-040]
3. Go offline and send three prompts: they wait in a queue (visible "Waiting to send" bubble); come back online: they flush once, oldest first, and a count of how many were sent is shown. Prompts for another server stay queued and are counted. [B-STATE-039]
4. Turn the auto-send setting off and repeat: nothing is sent when the network returns; tap the explicit resend on one: it sends and is not counted as automatic. Turning the setting off while prompts wait stops delivery. [B-STATE-038]
5. Queue a prompt, cancel it from the pending bubble: the text returns to the composer. Cancel one the server already delivered: the item just drops (no error). Change the delivery mode of a pending prompt and see the server and the UI agree. [B-PROTO-045]
6. [fx] lose the response to a send: tap Retry; the app looks up the receipt and confirms without sending again; kill the app with a pending command, relaunch: it reconciles on flush and keeps the tab; with no receipt or a lost capability it never resends on its own. [B-STATE-008]

### E2E-11 Drafts, saved prompts and queued prompts survive anything

**Goal.** Nothing the person typed is lost, and nothing unsent is ever evicted or resurrected.

**Needs.** FX-OC2 and FX-OC2-B; a few attachments in the device Downloads folder.

**Proves.** B-STATE-100, B-STATE-102, B-STATE-085, B-CHAT-088, B-STATE-140, B-STATE-088, B-STATE-066, B-STATE-033 (8).

**Steps.**

1. Type a draft with an attached image and leave the conversation: reopen it and see the draft and attachment; an attachment-only draft also persists; empty the draft and see the entry vanish. Two servers with the same session id keep independent drafts. [B-STATE-100]
2. Fill the draft store (fixture limit) and try one more: the new draft is refused with a message and unsent work is not evicted; leaving with a draft that could not be saved tells you. Older drafts show up in "Saved prompts" once, intact. [B-STATE-102]
3. Open Settings > "Saved prompts": delete a prompt and see it vanish at once and persist after restart; tap Undo: the exact text, metadata and bytes return; a refused deletion keeps the prompt and retry works. [B-STATE-085]
4. Stash a prompt (save to stash), force-stop, relaunch: the stash is recovered with its attachments and references, separate from drafts and from other servers; stashing 50 never evicts a saved prompt. [B-CHAT-088, B-STATE-140]
5. Take a photo from the composer, then [adb] kill the app while the camera is open: on return the photo is in the same draft with its text, attachments and location; a failed save keeps it; the photo returns to its own conversation, not another. [B-STATE-088]
6. Queue three prompts offline, then remove the server: the dialog counts them; "Keep" moves them to Saved prompts (survive restart, deletable); the other server's queue is untouched. [B-STATE-066, B-STATE-033]

### E2E-12 Attach photos, files, spreadsheets and mentions

**Goal.** Everything that can go into a prompt arrives intact and bounded.

**Needs.** FX-OC2; files pushed to /sdcard/Download: photo1..3.jpg, data.xlsx, report.docx, notes.txt, name-only stub file.

**Proves.** B-MEDIA-007, B-MEDIA-006, B-CHAT-087, B-ANDR-051 (4).

**Steps.**

1. Tap the attach control, "Photos": the picker is the system gallery; pick two photos (the gallery allows several up to the room left); pick with one slot left: single picker is used; cancel the picker: nothing is left pending. Large photos are scaled down before reading. [B-MEDIA-007]
2. Attach `data.xlsx`: it becomes CSV, one block per sheet; attach `report.docx`: paragraphs; a name-only or unsupported file is refused with a plain message rather than half-read. [B-MEDIA-006]
3. Type `@` and mention `README.md` with a line range; Send; the sent message carries the exact reference and range; delete the mention before sending and no file part is sent; typing a path that merely starts like a mention is not a mention. [B-CHAT-087]
4. Browse to a folder from "Phone storage": `/sdcard` reads as internal storage, Up stops at internal storage, a missing folder is said to be missing. [B-ANDR-051]

### E2E-13 Dictate by voice, auto-send and the model download

**Goal.** Speech-to-text works on the phone, in order, and asks before using mobile data.

**Needs.** FX-OC2; emulator microphone fed from a WAV (`adb emu avd hostmicon` or virtual mic) containing a 3-minute test speech and a 6-second phrase; voice model not installed; network switchable between Wi-Fi and mobile.

**Proves.** B-MEDIA-019, B-MEDIA-010, B-MEDIA-017 (3).

**Steps.**

1. [adb] mobile data only (Wi-Fi off). In the composer tap the microphone: before anything is downloaded see the consent line ("download over mobile data?"). Tap "Cancel": nothing downloads and the cancelled consent cannot authorize a later start. With no mic tap, no work happens at all. [B-MEDIA-019]
2. Go offline, tap the microphone on first use: it is blocked with a plain explanation; reconnect: it retries; switch Wi-Fi to mobile mid-download: it asks again; accept: the pack downloads with progress and verification before it says ready and listening. [B-MEDIA-019]
3. Feed the 3-minute speech: the text appears in order, in 30-second chunks; make one chunk fail (corrupt the WAV at 1:30): recording stops and the text so far stays in the composer. [B-MEDIA-010]
4. Open voice settings: auto-send is off by default. Feed the 6-second phrase with a 3-second pause at the end: nothing is sent and recording continues; turn auto-send on and repeat: recording stops by itself after the pause, says why, a countdown shows in the pill, and the message is sent. [B-MEDIA-017]

### E2E-14 Files, previews, sharing out and exporting a project

**Goal.** Files can be read, shared over Wi-Fi or the share sheet, and exported without credentials.

**Needs.** FX-OC2 with project `app`; Wi-Fi on the emulator; a second device or `adb shell curl` for the Wi-Fi share link; built-in Linux with a project (for the export step).

**Proves.** B-MEDIA-034, B-ANDR-074, B-SEC-099, B-LINUX-102 (4).

**Steps.**

1. Open the project > "Files" and a text file: the preview opens; Save keeps the original bytes. Open `data.csv` as a table.
2. Tap Share on a file, "Share over Wi-Fi": [adb] turn Wi-Fi off first -> see "This phone is not on Wi-Fi"; Wi-Fi on -> a link appears. [adb] `curl` the link: exactly the one file downloads as an attachment; any other path returns 404; POST returns 405; the download counter goes up; stop sharing and the link dies; only a private LAN address counts as Wi-Fi. [B-MEDIA-034]
3. Tap Share (system) on a reply: the chooser opens and this app is not listed as a target. [B-ANDR-074]
4. Export a project (plain): the zip leaves out every credential, including provider keys and tokens wherever the names appear; export private: it adds credentials and OpenCode data but not logs, and the file name says it is private. [B-SEC-099]
5. On built-in Linux: export a project that exists only inside Ubuntu with a Git repository: the export includes the Git objects; names that cannot be framed are skipped. [B-LINUX-102]

### E2E-15 Git page, GitHub sign-in, remotes and undo

**Goal.** Check changes, commit, push and undo from one screen without leaking a token.

**Needs.** Built-in Linux project `app` with Git history and a remote `origin` (FX-GIT bare repo) or a server with remote shell; FX-GH device-flow stub; FX-OC2.

**Proves.** B-NAV-020, B-NAV-023, B-NAV-021, B-SEC-084, B-BUILD-014, B-CHAT-099, B-CHAT-120 (7).

**Steps.**

1. Open a project that is not a repository: the Git page says so as a view (not an error). Open `app`: see loading, then ready; actions show busy while running; one action at a time; a failed refresh keeps the last snapshot. [B-NAV-020]
2. Open a conversation in `app`: the header chip names the branch once; start a conversation in a different project: the chip rebinds; reopen the first chat: the branch shows from the first frame. [B-NAV-023]
3. Edit a file via the agent, open Git, type a message under "Describe what changed" and use "save and send" in one tap: it saves, then sends, then reads once; a failed save stops there; a failed send keeps the save and says so. [B-NAV-021]
4. Tap "Sign in to GitHub": the device code and the verification link appear; [fx] approve in the stub; the account shows; push works. Cancel in the middle and approve late: the late token is ignored. A failed attempt says why and stores nothing. Make the stub reject the token: the account signs out. [B-SEC-084]
5. Add a remote: a name or address with shell metacharacters is refused before anything runs; adding shows it and refuses a duplicate; a remote with credentials in its address is listed without them; clone puts the project beside this one and refuses a taken folder. [B-BUILD-014]
6. In a conversation, comment on lines of a diff (Review): the references appear in the composer as structured quotes; ten at most; a long snippet is truncated with a notice. [B-CHAT-099]
7. Tap "Undo from here" on a prompt: it lists the files the agent edited, once each; on a server that undoes directly it works at once, on a staged-undo server it goes through the staging sheet. [B-CHAT-120]

### E2E-16 Terminal shells that outlive the screen

**Goal.** A shell on the phone keeps running when the screen is closed and shows up again.

**Needs.** Built-in Linux installed; a project folder.

**Proves.** B-LINUX-097, B-LINUX-100 (2).

**Steps.**

1. Project > "Terminal" > "New terminal": a shell opens at the screen's size and is numbered 1; open a second: numbered 2 (open order); [adb] make start fail (stop Linux) -> a plain reason is shown. [B-LINUX-097]
2. Run `sleep 300 &` then close the terminal screen and the app ([adb] force-stop), relaunch and open Terminal: both shells are adopted once if Android still runs them with recent output; run `exit` in one: it is listed as ended; tap "Restart" on it: replaced by a new shell; tap Stop on a running one: it stays listed as ended. [B-LINUX-100]

### E2E-17 Set up the built-in Linux on a phone: preflight, progress, cancel, resume

**Goal.** First-run setup of the on-phone Ubuntu, OpenCode and tools is honest about space, memory, progress, failure and cancel.

**Needs.** emulator AVDs: normal (4 GB / 20 GB free), small (1.5 GB RAM), nearly full disk; FX-LINUXDL local mirror of the rootfs and pinned OpenCode builds, throttleable; no Linux installed.

**Proves.** B-LINUX-210, B-LINUX-211, B-STATE-030, B-LINUX-201, B-LINUX-227, B-ANDR-050, B-LINUX-199, B-LINUX-195, B-LINUX-203, B-LINUX-229, B-LINUX-163, B-LINUX-164, B-LINUX-165, B-LINUX-245, B-LINUX-014, B-LINUX-220, B-LINUX-113 (17).

**Steps.**

1. Welcome > "Set up on this phone": the preflight passes on the normal AVD and shows the size and time. On the small AVD (under 2 GB) it is refused naming both numbers; at about 2-3 GB it is allowed with a "slow phone" note; at 4 GB and above there is no note. [B-LINUX-210]
2. On the nearly full disk: setup is blocked and says exactly how many bytes to free; with exactly the required space it passes; the slow-phone note is checked after the space check. [B-LINUX-211]
3. On a normal AVD over mobile data with a download over the threshold: ask consent first; Wi-Fi: no prompt. [B-STATE-030]
4. Start setup and watch: the bar never moves backwards when a new stage begins; no estimate shows in the first 10 seconds; bytes and percent are real; the setup uses the pinned upstream builds. [B-LINUX-201, B-LINUX-227]
5. Press Home during download: a setup notification shows numeric progress; tap it: the app opens on the setup screen (also from cold start). On finish the notification says whether it finished. [B-ANDR-050]
6. Cancel at 40 percent: the native job stops, finished components stay; nothing native is handed over while still checking. Reopen: the interrupted job is shown, "Continue setup" resumes, skipping components whose checks pass with their version. [B-LINUX-199, B-LINUX-195]
7. [fx] cut the network at the download: the failure names the part and the cause ("no internet"), not an exit code; [adb] `kill -9` the installer: "interrupted", not a later failure mislabelled; fill the disk mid-way: a full-storage message with how to resume; corrupt the checksum: a checksum message. [B-LINUX-203]
8. Break the environment on purpose (revoke storage permission, stop Linux): the problem is named with one cause, one fix, shown before any command runs; the permission request asks Android directly and a permanent "no" is remembered with an explanation; after a storage reset the first screen explains what to do. [B-LINUX-229]
9. When a server is already running: the setup asks first ("stop OpenCode?") and nothing is stopped before "Yes"; answer "No": nothing changes and it ends in plain words; with nothing running there is no question. [B-LINUX-163, B-LINUX-164, B-LINUX-165]
10. Finish: a fresh install has an in-app server profile with a new password; an existing install keeps its password; an already right server stays connected. [B-LINUX-245]
11. Open "Add tools" while the previous job ended failed: it still starts; make the start break: the job fails with its text kept and "Continue" starts it again to done. [B-LINUX-014]
12. Choose the optional voice component: off by default, checked before Linux exists, installs once with bytes shown after Linux, fails its row in plain words with Continue retrying; cancel stops the download; kill the app mid-download and it resumes. [B-LINUX-220]
13. Run the smoke check from the setup page: the real pinned OpenCode runs on the host. [B-LINUX-113]

### E2E-18 The phone's own server: start, crash recovery, stop, honest status

**Goal.** The in-app server starts when the app opens, heals once after a crash, never lies about running, and never kills what the person stopped.

**Needs.** Built-in Linux with OpenCode installed (E2E-17 done); emulator with adb.

**Proves.** B-NAV-041, B-LINUX-151, B-LINUX-152, B-LINUX-010, B-ANDR-008, B-ANDR-003, B-NAV-012, B-STATE-025, B-LINUX-240, B-LINUX-250, B-ANDR-025, B-LINUX-037 (12).

**Steps.**

1. Settings > "This phone": the server is named "This phone" everywhere; two saved servers that read the same are told apart by port (and by secure/insecure when the host matches). [B-NAV-041]
2. Stop the server from the page; kill and relaunch the app: it stays stopped when the person stopped it explicitly; with "restart on phone" off it stays stopped; with restart on and not explicitly stopped, opening the app starts it, waits for the first resume, and connects. [B-LINUX-151]
3. [adb] make the start fail fast once: the app tries once more then leaves the reason; a timeout is not retried; a crash restart counts against a budget but opening the app still starts a stopped server even past an exhausted budget. [B-LINUX-151]
4. Start a hung server (accepts connections, never answers) and one that rejects our password: at launch each is replaced; a young booting one is waited for; a healthy server whose connect failed is simply reconnected. [B-LINUX-152]
5. [adb] `am force-stop` the app while the server and a conversation run: relaunch; the status says the app was stopped without claiming Android did it; the exit notice never says it is starting again when auto-restart is off; with it on, recovery restarts the server exactly once with one notice and no duplicate starts. [B-LINUX-010, B-ANDR-008]
6. Open the exit list in Diagnostics: entries read "Today", "Yesterday", then dates; only abnormal exits are marked as problems; a normal stop or an app update is silent; the newest first. [B-ANDR-003, B-NAV-012]
7. After a restart the recovery report waits for an authenticated health answer before claiming recovery; a stopped or uninstalled server is never probed; remote and deleted profiles are not checked. [B-STATE-025]
8. [adb] kill the server process; open This phone: the server is shown stopped (not running from saved state); with the permission denied no command is sent; an unknown port is never scanned. [B-LINUX-240, B-LINUX-250]
9. Start the in-app server for the first time: the app asks once about battery exemption and maker auto-start; refuse: after restart it remembers the refusal with a plain explanation; it does not ask again for other changes. [B-ANDR-025]
10. Tap Start on a server that is not running: the in-app server is recognised only with its address and Ubuntu installed; starting writes the password, starts the profile runtime and waits; cancelling the confirmation never starts it; kill the server process and the page reports that it exited; a refused start without details reads as one sentence. [B-LINUX-037]

### E2E-19 Remove Linux, free space and reset without losing projects

**Goal.** Destructive actions name their target, keep projects by default, and survive being killed halfway.

**Needs.** Built-in Linux with two projects, an agent, and cache data.

**Proves.** B-LINUX-089, B-LINUX-247, B-LINUX-029, B-LINUX-093 (4).

**Steps.**

1. Settings > "This phone" > storage: categories show real sizes; tap "Free up space": only safe paths (caches yes, projects and credentials never) are listed; after cleaning the freed space is the drop between readings, never negative; files in use today are kept; old agent versions are removed only when the current one is known. [B-LINUX-089, B-LINUX-247]
2. Run a scan, cancel it midway: it stops; the summary shows the last total. [B-LINUX-247]
3. Remove the in-app Linux: projects are kept unless you ask otherwise; "delete everything" requires typing the exact confirmation; a near-match does not delete. [B-LINUX-029]
4. Reset Linux: a marker is saved before erasing; [adb] kill the app during the erase; relaunch: it finishes erasing and sets up again; kill it after erasing: it only sets up; a Linux installed in the meantime ends the reset quietly; the old Reply speed reading is gone. [B-LINUX-093]

### E2E-20 Dev servers, ports, projects and processes on the phone

**Goal.** What is running on the phone is listed in plain words, belongs to the right project, and can be stopped safely.

**Needs.** Built-in Linux with projects: one with a `package.json`, one with a `Cargo.toml`, a hidden and a `node_modules` folder; a started `python3 -m http.server 8123`.

**Proves.** B-LINUX-016, B-LINUX-148, B-LINUX-087, B-LINUX-180, B-LINUX-143, B-LINUX-058, B-NAV-043, B-NAV-044 (8).

**Steps.**

1. Project browser: lists folders with a git mark, sorted; hidden entries and files are hidden; navigate in and up to the root; on a fresh install without a projects folder it lists nothing; with the server stopped it works without starting Ubuntu. [B-LINUX-016]
2. Project scan: a folder with a marker is a project of the right kind; code-marker repositories keep their kind and have git; Android, node_modules, hidden and build folders are skipped and never entered; five levels deep is the limit; links are not followed; the root itself is not a project. [B-LINUX-148]
3. Settings > "Running on this phone": the python server on port 8123 appears even though Android hides socket tables; a server named only by the PORT variable appears with its log file offered. [B-LINUX-087]
4. Rows use plain names for kernel process names and name a command by what it runs, not where it lives. Processes are grouped in a tree under their parent; there is no Stop on the server roots or the app. [B-LINUX-180, B-LINUX-143]
5. In "Running on this phone" tap Stop on a server shell: the server is asked and the row disappears; stopping a program kills only that process; if the stop fails the row stays and says so. [B-LINUX-058]
6. Project hub: the dev server line shows nothing when unknown, says a found server once and keeps it when a later look fails, treats a program listening inside the project as its dev server and ignores programs outside the project. [B-NAV-043]
7. Project hub rows follow the connected server, list tools in a fixed order, and each tool opens its existing screen. [B-NAV-044]

### E2E-21 Conversations list: merged servers, pins, titles, status

**Goal.** One list across servers that sorts by what needs you, routes each row to its own server and shows clean titles.

**Needs.** FX-OC2 and FX-OC2-B each with several sessions; a session titled with an ISO timestamp placeholder and one with leaked `<tool_call>` markup (fixture).

**Proves.** B-NAV-030, B-NAV-011, B-NAV-025, B-NAV-032, B-NAV-065, B-NAV-066, B-NAV-067, B-CHAT-012, B-CHAT-020, B-PROTO-096 (10).

**Steps.**

1. Conversations list: rows sort attention first, then running, then recent; two sessions with the same id on different servers are two rows and each opens its own server's conversation. [B-NAV-030]
2. Project choices are ordered by update time (stable ties, unknown dates last); with the ordering unavailable the app does not contact the server for it; an empty server stays empty; a failing server is a failure, not empty. [B-NAV-011]
3. Open entry points that used to open the Inbox (a notification, the status badge): each opens Conversations with the filter applied. [B-NAV-025]
4. "New conversation" with two backends: the chooser needs an explicit source and keeps the agent; with one source it starts directly; duplicate names and a missing default are refused; a failed start keeps its cause under Details. [B-NAV-032]
5. Pin a conversation: it sorts first; unpin restores the order; a pinned conversation older than the first page is still reachable after fetching once; a failed pinned read offers retry without losing the pin. [B-NAV-065]
6. Titles: a server placeholder title loses its ISO timestamp; real titles are untouched; blank falls back to the screen's own wording; leaked tool-call/model markup is cut from titles (also half-cut markers); a list never shows the markup. [B-NAV-066, B-NAV-067]
7. A conversation that finished after it was last opened shows as finished-unseen; open it or let it run and the mark clears; tap Stop in the chat: it shows "Stopped" until the next prompt or reopening. [B-CHAT-012]
8. Conversation menu > "Continue with…": pick another agent; the source gets a link line naming the agent and the new conversation a link line naming the title. [B-CHAT-020]
9. Conversation menu > export: the downloaded file is complete, redacted by default, with no location; on a server without export the option is remembered as absent without repeated requests. [B-PROTO-096]

### E2E-22 Find any setting: search, groups, per-server pages

**Goal.** Settings are findable by words, both languages and typos; the structure follows how many servers are live.

**Needs.** FX-OC2 and FX-OC2-B; emulator in English; one OpenCode 1 profile if available.

**Proves.** B-NAV-068, B-NAV-069, B-NAV-071, B-NAV-072, B-NAV-070, B-NAV-073, B-NAV-057, B-NAV-059, B-NAV-058, B-NAV-081, B-NAV-050 (11).

**Steps.**

1. Settings with one server: see one list of groups. Add a second server: see per-server pages with rows lined up, overrides shown, a way back, and a server page that keeps to its own server. [B-NAV-068]
2. On a server page change two values, then tap "Use my default": every difference the page shows resets, including read sharing. [B-NAV-069]
3. Tap "Search settings" and type: `backg` (prefix) finds Background; `notifcations` (typo) and `notifiactions` (swapped letters) find Notifications; `الإشعارات` finds the same row and a mixed query such as `notifications إشعارات` still works; a literal match ranks above a fuzzy one and equal matches keep their order; a word from the acceptance wording of a row (for example "alerts") finds it; legacy aliases from earlier versions still work. [B-NAV-071]
4. Search for features this phone lacks (a desktop-only row): not listed; `crash` still opens diagnostics; no alias leads to a dead end. [B-NAV-072]
5. Tap a result: the page opens, scrolls to the row and highlights it; repeat in Arabic and in a tablet-size window. [B-NAV-070, B-NAV-073]
6. Open the global search / command palette (desktop shortcut or in-app search): every screen and tab is found by its title; each result names the page that holds it; a title match ranks above a keyword match. [B-NAV-057, B-NAV-059]
7. On the in-app Linux server search "phone": one "Running on this phone" result; on a backend without catalog or project tools those results are absent; OpenCode 2 keeps the catalog; AI setup is found by name when shared. [B-NAV-058]
8. On a server that cannot serve a tile, health section, git init or settings row: it is hidden; on OpenCode 1 the v2-only features are hidden. [B-NAV-081]
9. Search "background checks": it lands where each server's checks live; the profile monitor no longer has a Background checks line; the Changes count shows on the project hub; the background notification line is plain and short. [B-NAV-050]

### E2E-23 Language, text size and look

**Goal.** Arabic (RTL), text size and visual style are personal choices that persist and reach native text too.

**Needs.** emulator in English; FX-OC2 connected; notification shade and a widget on the home screen.

**Proves.** B-L10N-008, B-L10N-001, B-L10N-009, B-L10N-013, B-KIT-059, B-KIT-040 (6).

**Steps.**

1. Settings > Language: the list contains only shipped languages and matches the saved-choice set; each language names itself (العربية). Choose Arabic: the app flips to RTL and everything reads Arabic; kill and relaunch: still Arabic; remove the override: it follows the system language. [B-L10N-008, B-L10N-001]
2. [adb] set the system locale list (emulator: `setprop persist.sys.locale` and restart, or Settings > System > Languages) to a language the app does not ship, such as Swedish, alone: the app reads English, not Arabic; set the list to Swedish then Japanese: the app reads Japanese (the next device language is tried before English); a shipped language is found by code, by region and by script (for example Traditional Chinese). [B-L10N-009]
3. Post a notification and place the widget while the app is Arabic: native text refreshes at once to Arabic, with localized agent titles, fallbacks and time labels; switch back and it returns to English. [B-L10N-013]
4. Settings > "Text size": reads "Default" until chosen; choose "Smallest", then the largest: it applies at the app root and in Settings; the main screens (Conversations, a chat, Settings) still work at Smallest; kill and relaunch: the choice stays. [B-KIT-059]
5. Settings > Appearance > Style: pick Soft, tune corners/density, then pick Brutal: the fine-tune choices reset (Saver too); pick Soft again and tune: choices persist and restore exactly across restart; a new style clears them. [B-KIT-040]

### E2E-24 Command launcher, Go to and the conversation menu

**Goal.** The fastest way around: type to go, with shortcuts that match the server.

**Needs.** FX-OC2 with 5 recent conversations; tablet or desktop-size window for the keyboard part.

**Proves.** B-NAV-056, B-NAV-049, B-NAV-051 (3).

**Steps.**

1. Open the launcher (search button; Ctrl+K on a keyboard): with an empty field see places first, then the last three conversations; typing filters; rows follow server capabilities, never the product name. [B-NAV-056]
2. Type a command by its label, then by a hint or keyword in different case: all find it; conversations appear only as search results (matched by project and title); "shortcuts" help lists the keys. [B-NAV-049]
3. Open the conversation menu: "Go to" and "Do" actions depend on the server's capabilities; the command sheet lists the same. [B-NAV-051]

### E2E-25 Launcher shortcuts, Quick Settings tile, deep links and share-in

**Goal.** Every way Android can send the person into the app lands once, in the right place, with only ids on the wire.

**Needs.** FX-OC2 with 6 pinned conversations; [adb] `am start -a android.intent.action.VIEW -d <link>` and `am start -a android.intent.action.SEND`.

**Proves.** B-ANDR-030, B-ANDR-027, B-ANDR-028, B-ANDR-071, B-ANDR-073, B-NAV-064, B-NAV-061, B-NAV-062, B-ANDR-075, B-NAV-005 (10).

**Steps.**

1. Pin 6 conversations. Long-press the launcher icon: the dynamic shortcuts show at most four, with titles only (long titles cut); change nothing and reopen: no republish; disconnect the server: the shortcuts and the Quick Settings tile are withdrawn; suspending keeps the last state; the tile carries only the activity count. [B-ANDR-030]
2. Tap a pinned-session shortcut while the app is closed: the app opens that conversation exactly once. Tap another while the app is running: it opens too; if two taps arrive, the newer wins; a stale cold-start shortcut is not opened after a newer live one. [B-ANDR-027]
3. Tap each static shortcut (connect, background settings, widget taps) cold and warm: each opens its screen once; an unknown action does nothing. [B-ANDR-028]
4. Cold-start from a session link `[adb] am start -d <session link>`: a launching link is consumed once and delivered only when the app is ready; a malformed or oversized link never becomes pending; with credentials in the link nothing is stored. A newer valid link replaces an older one. [B-ANDR-071, B-ANDR-073, B-NAV-064]
5. Open a session link on a phone that does not know the host: the app keeps the link through "Add server" and sign-in and then lands on the session; with two saved servers matching the origin it asks which; a mismatched server never reuses secrets. [B-NAV-061]
6. Share a conversation link with "Include this server's address": the option appears only when both gates allow it; the receiving phone follows the address flow. [B-NAV-062]
7. [adb] share text from another app into this app (cold, then while running, then twice quickly): the text opens a new conversation draft once; an empty share is ignored; a newer live share wins over a pending cold-start share and is never delivered twice; with no connection it says "Connect to a server and the shared text opens in a new conversation". [B-ANDR-075]
8. Tap a notification: it opens the typed destination once, in the right profile; tap it again from the shade: nothing happens twice. [B-NAV-005]

### E2E-26 Home-screen widgets

**Goal.** The widget answers and acts without ever holding a secret, and acts once.

**Needs.** FX-OC2 and FX-OC2-B; widgets placed: Conversations, Status, Usage; magic prompts `e2e:ask-edit`, `e2e:slow-finish`.

**Proves.** B-ANDR-022, B-ANDR-103, B-ANDR-104, B-ANDR-106, B-NAV-005, B-ANDR-109, B-NAV-077 (7).

**Steps.**

1. Place the Conversations widget: it shows at most twelve sessions with busy flags and the model name, never a raw id; unchanged data causes no redraw. After a profile deletion the widget clears. [B-ANDR-022]
2. Send `e2e:ask-edit`: the widget shows the question with Allow/Deny and no raw fields; tap Allow on the widget while the app is not running: the exact pending request is approved, the real status is read after the reply, then the widget closes; if the status route is down, the widget says disconnected and the answered request is not replayed. [B-ANDR-103, B-ANDR-104]
3. Tap Stop on the widget during `e2e:slow-finish`: only that run stops; tap it twice quickly: dispatched once; a finished run cannot be stopped; an old run cannot stop its replacement; an unsupported action is rejected before connecting. [B-ANDR-106]
4. Tap Reply on the widget: the chat opens ready to type. Tap Open: the chat opens without focusing the field. Use the widget overlay: the style window, the steer box and the voice box work from the overlay. [B-NAV-005, B-ANDR-109]
5. Usage widget: tap a provider row: OpenCode sign-in for that server, agent sign-in for Claude on this phone, "Read usage from" for set-up rows; GLM and MiniMax ask for a key, others sign in; a deleted server opens nothing wrong. [B-NAV-077]

### E2E-27 Scheduled prompts and the morning summary

**Goal.** A prompt can be scheduled, paused, resumed and deleted, fires on time, tells you plainly, and never collides with a person typing.

**Needs.** FX-OC2; emulator with controllable clock (`adb shell date`) and exact-alarm permission granted.

**Proves.** B-STATE-089, B-NAV-054, B-STATE-091, B-ANDR-070 (4).

**Steps.**

1. Settings > "Scheduled prompts" > add: choose a time a minute ahead; edit it; pause it; resume it; delete it; each action updates the Android alarm ([adb] `dumpsys alarm`); a mutation error shows an actionable message. Saving across profiles is refused. [B-STATE-089]
2. Let one fire: a notification appears; tap it: the conversation it created opens if reached, otherwise the history opens; delete the schedule, tap an old notification: nothing opens. [B-NAV-054]
3. Turn the morning digest on; run two schedules overnight ([adb] advance the clock): at the next local morning the summary counts only completed runs in the window, appears once, and tapping it opens the history; with the digest off nothing appears. [B-STATE-091, B-NAV-054]
4. While a scheduled run leases the phone runtime, try to send from the composer: the send is blocked with a reason and the typed text stays; when the lease ends, send works. [B-ANDR-070]

### E2E-28 AI Team on the phone

**Goal.** Turn the AI Team on, watch the setup states, be told about gates, turn it off without losing tools.

**Needs.** built-in Linux installed; FX-OC2 in-app server; AI Team component available from FX-LINUXDL.

**Proves.** B-LINUX-172, B-LINUX-173, B-AGENT-276, B-AGENT-042 (4).

**Steps.**

1. Open AI Team: the setup page shows its states in order (checking, a stop question when a server is running, failure, ready); while the check runs, execution is gated. [B-LINUX-172]
2. [fx] make the engine start hang and a reply never end: the check ends in a failure with a reason instead of waiting forever; progress shows while it runs; interrupted work offers "Resume". [B-LINUX-173]
3. Give the team a task that reaches a gate: the board shows the gate row and capability; one notification per gate appears with fixed copy and ids only; answering dismisses it; with the app in the foreground nothing is posted. [B-AGENT-276]
4. Turn the AI Team off: reopen the app, it stays off and the installed tools stay; "Stop" for now still allows recovery; "Remove" stops it and runs the remove script; "Start" re-enables a team that was off; recovery queued after removal stays off. [B-AGENT-042]

### E2E-29 Codex: connect, talk, steer, abort

**Goal.** A Codex app-server behaves like a first-class conversation partner.

**Needs.** FX-CODEX (Codex app-server stub with scripted turns); FX-OC2 for the handoff step.

**Proves.** B-AGENT-060, B-AGENT-067, B-AGENT-069, B-AGENT-068, B-AGENT-057 (5).

**Steps.**

1. Add server > Codex: enter the address and token; see the probe check health and project scope then close; a failed scoped read or unhealthy server is not shown as verified; a wrong token says it is rejected. [B-AGENT-060]
2. Open a project, start a new thread, send a prompt, see streaming; send to an existing thread; approve a tool ask; abort a turn; [fx] drop the socket mid-turn: the app recovers the authoritative state without duplicating. [B-AGENT-067]
3. While a turn is busy send a default message: it steers the active turn; while idle it starts a turn; choose explicit steer: targets the authoritative turn without resuming it; explicit queue and an unsupported attachment never go out. [B-AGENT-069]
4. Open the project hub on Codex: no Project tab tools are offered. [B-AGENT-068]
5. Open the conversation menu > "Continue with…" > Claude: a reviewable draft appears and nothing is changed; with Codex as the destination the option is unavailable without a qualified file writer. [B-AGENT-057]

### E2E-30 Claude Code and Pi through Paseo

**Goal.** Agents that live behind a Paseo daemon list, resume, import, update and show terminals.

**Needs.** FX-PASEO (Paseo daemon with scripted Claude and Pi agents, Claude history on disk); FX-OC2.

**Proves.** B-PROTO-070, B-AGENT-146, B-AGENT-164, B-AGENT-180, B-AGENT-174, B-AGENT-167, B-AGENT-159 (7).

**Steps.**

1. Add server > "Claude Code or Pi": bad input never opens a socket; a verified daemon names its ready runtimes. [B-PROTO-070]
2. Start a conversation: before it starts, the daemon lists what the chosen agent offers (models, modes); a choice made first is sent when the conversation starts; a running agent's long model id shows as the list's own id. [B-AGENT-146]
3. Open an old conversation: history shows the merged timeline and the live stream continues; delete archives it; archive it on another device: the session disappears here; [fx] drop the socket: the app reconnects and rereads the agent list. [B-AGENT-164]
4. Resume a conversation from the list: the header title equals the row title. [B-AGENT-180]
5. Import Claude Code conversations: this project's conversations are listed newest first; import one: it opens with its history; a daemon refusal is a failure message, not a conversation. [B-AGENT-174]
6. Settings > Host > "Update Paseo": ordered progress phases appear without the screen ending; when versions match it says already up to date; a failure returns a reason rather than a crash; after the restart the app reconnects by itself. [B-AGENT-167]
7. Open Terminal in the project: it lists this folder's terminals by title; open one (screen replays, then streams); type and resize reach it; a split multi-byte character arrives whole; rename and close by id; closing an ended terminal is not an error; an exited shell ends the stream; an unopenable terminal is a plain failure. [B-AGENT-159]

### E2E-31 Agents on this phone: sign-in terminal and running by name

**Goal.** A phone-hosted agent can sign in and be run from the terminal.

**Needs.** built-in Linux with Claude Code and Codex installed from the catalog; FX-AUTH (sign-in stub).

**Proves.** B-AGENT-129, B-LINUX-125, B-LINUX-119 (3).

**Steps.**

1. Agents > Claude Code on this phone > "Sign in": a visible terminal opens running exactly the sign-in script and nothing else; do the same for every installed phone agent; Codex's folder exists before any agent process starts. [B-AGENT-129, B-LINUX-125]
2. In Terminal type `claude --version` (agent by name): it runs as the agent user. [adb] interrupt an agent download midway, run cleanup, resume: the partial download survives extraction cleanup and resumes. [B-LINUX-119]

### E2E-32 Memory on the phone: install, start, pair, capture, stop

**Goal.** The personal memory node is installed and kept running without surprising the person, and notes are never lost offline.

**Needs.** built-in Linux installed; FX-NODE (memory node stub) for remote pairing.

**Proves.** B-LINUX-066, B-NAV-014, B-LINUX-067, B-LINUX-068, B-LINUX-069, B-PROTO-057, B-SEC-069, B-NAV-013, B-AGENT-098 (9).

**Steps.**

1. Chats home > Memory row: without built-in Linux it says unsupported; with Linux and no build it says ready to install; stopped when a vault exists; running when the service runs. The Memory page shows its state and actions. [B-LINUX-066, B-NAV-014]
2. Tap "Run memory on this phone": it installs, creates the vault, starts, then pairs with the owner key; an installed program is not reinstalled; kill the install: the failure is classified (no build / install with reason / start with log / pairing) and "Start" retries pairing only; double-tap does nothing twice. [B-LINUX-067]
3. Stop: data kept and the node stopped; Restart: pairing kept; Remove: data deleted, back to ready-to-install. [B-LINUX-068]
4. Stop the node, relaunch the app: it stays stopped (Stop is remembered); tap Start: remembered no more; relaunch with it stopped but not by you: it is started automatically while Linux is still starting after an update, retrying busy or failed first looks, showing "starting" the whole time; a memory this app is not paired with is left alone. [B-LINUX-069]
5. Pair with a remote node: the code becomes a token without an Authorization header; a refused code is a plain failure; node info needs no credential. A public or credential-bearing node address is refused (private addresses only, default port applied). [B-PROTO-057, B-SEC-069]
6. Open a Thing: the actions name their target; "Add something" exists; a reminder is one-off unless the words say it repeats and how; the Status page opens. [B-NAV-013]
7. Capture a note while the node is unreachable: it is kept on the phone and counted once; bring the node back: it is sent automatically with the same key; a full outbox refuses the newest note; a note held in the node's own queue is not counted as lost; discard removes a phone note locally and a node note at the node. [B-AGENT-098]

### E2E-33 Plan usage and subscriptions

**Goal.** The person can see what is left on their plans only if they opt in.

**Needs.** FX-OC2 saved server, FX-OC2-B; usage endpoints stubbed (FX-USAGE) with Codex and MiniMax readings.

**Proves.** B-AGENT-073, B-NAV-079 (2).

**Steps.**

1. Settings > Usage: nothing is read until you turn plan-usage reading on for a server; turning on stores a per-profile setting and reads at once; turning off removes it and forgets the answer; it applies to saved OpenCode servers only (not In-app Ubuntu or Paseo); a server asking for a password is skipped; deleting the server sweeps the setting. [B-AGENT-073]
2. Subscriptions page states: nothing signed in shows only the add list; In-app Codex is one readable card; an unreadable PC sign-in shows "Set up"; once read it shows numbers; offline shows stale numbers and flags a passed reset; an expired sign-in shows "Sign in again". [B-NAV-079]

### E2E-34 Privacy: diagnostics, reports and what never leaves

**Goal.** Nothing a person would not want shared can leave in a report, a log, a link or a screen.

**Needs.** FX-OC2 with the stub secret `sk-e2e-FAKE-0123456789abcdef` registered as a provider key and echoed by `e2e:secret`; GitHub reachable or a fake browser.

**Proves.** B-SEC-040, B-NAV-045, B-SEC-041, B-SEC-128, B-SEC-050, B-SEC-129, B-STATE-072 (7).

**Steps.**

1. Open a server's configuration screen: no password or token is displayed in the clear; change the secret: it appears masked, labelled by the name it sits under. [B-SEC-040]
2. Run `e2e:secret`, cause an error: Settings > Diagnostics > "Recent errors" titles each error by source and kind in plain words. [B-NAV-045]
3. Settings > Diagnostics > "Report a problem": the report preview stays off until consent; with consent it shows exactly the safe text; no fake key, bearer token, password, URL credential or server address appears in the preview, the copied text, the JSON, the failed-job report, the timing report or the device log. [B-SEC-041, B-SEC-128]
4. Tap "Share report": the system share sheet opens with exactly the previewed text, nothing written locally; [adb] unavailable share: the user gets a fixed plain outcome. [B-SEC-050]
5. Tap "Report a problem" to GitHub: a short report is prefilled whole in the link, opened through the safe-link prompt; a long report goes to the clipboard and the form says to paste; a very long description copies the whole report. [B-SEC-129]
6. Clear captured evidence: memory, the committed snapshot and any pending write are erased; restart the app; old trace evidence does not reappear. [B-STATE-072]

### E2E-35 Remove a server and everything stored for it

**Goal.** Forgetting a server leaves nothing behind and touches nothing else.

**Needs.** FX-OC2 and FX-OC2-B each with drafts, saved prompts, pins, learned phrases, trust pins and queued prompts.

**Proves.** B-STATE-060, B-LINUX-070 (2).

**Steps.**

1. Create per-server state on both servers (draft, saved prompt, pin, phrase chip, certificate pin, queued prompt, schedule). Remove FX-OC2: the dialog names the server and what leaves with it. [B-STATE-060]
2. After removal [adb] force-stop and relaunch: nothing for FX-OC2 returns (no draft, prompt, pin, phrase, trust, queue, schedule, widget row, shortcut); FX-OC2-B is whole. [B-STATE-060]
3. Repeat on the in-app Linux server profile: forgetting the active in-app server profile removes it and leaves nothing behind. [B-LINUX-070]

### E2E-36 Several servers: what needs me, across everything

**Goal.** One place says what is waiting on any server and where to go, and background checks stay polite.

**Needs.** FX-OC2 and FX-OC2-B each with a waiting permission ask and a finished conversation; FX-OC2-B in a different project.

**Proves.** B-NAV-002, B-NAV-010, B-AGENT-077, B-NAV-082, B-ANDR-059 (5).

**Steps.**

1. Conversations list > the "Needs you" filter: requests from all servers appear in urgency order with stable ties, each routed to its server; opening one lands in that server's conversation; check-ins are never counted as requests. [B-NAV-002]
2. "Conversations elsewhere" lists other projects only (not the one being viewed), running first, then by recency; attention from other projects is tallied per project, answering clears it and switching into a project hands it back to the app. [B-NAV-010, B-AGENT-077]
3. Return after being away: the while-away feed shows only unread, idle, unblocked root conversations as finished work, newest first; acknowledge one: only that run is hidden; an empty feed says it is empty. [B-NAV-082]
4. Turn on check-in reminders: none appear in the foreground, during quiet hours, or when off; a long run reminds once; turning the rule off retires only check-ins; the same session in another workspace starts a new observation. [B-ANDR-059]

### E2E-37 Providers, models and how to connect them

**Goal.** The model chip and picker are never blank, never mixed up between providers, and tell the truth when a provider is not loaded.

**Needs.** FX-OC2 with two providers, one connected but not loaded; model catalog slow to load (fixture delay).

**Proves.** B-NAV-006, B-CHAT-066, B-NAV-034, B-AGENT-209, B-AGENT-172 (5).

**Steps.**

1. Open Providers > connect a provider: the connect methods are ordered headless (device code) first, key methods in between and the browser flow last; each hint says where its flow finishes. [B-NAV-006]
2. Open a conversation while the model catalog is loading ([fx] delay it): the chip already names the model from its id; once loaded it shows the catalog name; a previously seen name wins until then. [B-CHAT-066]
3. Open the picker: it leads with the current model and its provider family; a provider-bound session does not inherit the global provider; the not-loaded notice reads naturally for one provider and for many. [B-NAV-034]
4. Pick a model of the not-loaded provider: "not loaded yet" shows only before a reload was tried, the reload runs, and the notice names the running replies a reload waits for. [B-AGENT-209]
5. Set a thinking level on a draft, switch model, send: the level reaches create; on an existing session the level applies after the model; "default" resets it; another provider's model is rejected for a provider-bound draft; a rejection keeps the draft. [B-AGENT-172]

### E2E-38 Tips and the Make it yours tour

**Goal.** Hidden gestures are taught once, at the right time, and can be replayed.

**Needs.** fresh install with tips reset; FX-OC2.

**Proves.** B-NAV-038, B-NAV-039, B-NAV-074 (3).

**Steps.**

1. Use the app for the first time: no tip shows before the first reply; after it, a tip appears once and never again when shown or dismissed; two tips never show at once, and the refused one is not consumed; unreadable tip state counts as none shown. [B-NAV-038]
2. Approve three asks of one kind: the "always allow" tip appears; finish a run that changed files: the "review" tip; wait one minute with notifications ready: the "leave the app" tip; a conversation at 80 percent context: the "compact" tip; none appear with automatic approvals, no changes, notifications unreachable or an early end; another card above the composer holds tips back. [B-NAV-039]
3. Settings > Help > "Tips": the list replays tips; "Show tips again" resets all; the "Make it yours" tour starts from Help. [B-NAV-074]

### E2E-39 Upgrade in place from the Flutter app to the native app

**Goal.** The day-one update: install the native build over the Flutter build and find everything exactly as it was.

**Needs.** the last Flutter release APK and the native APK, signed with the same key (SHA-256 1DE5BF08...D60C); FX-OC2, built-in Linux with a project, a running schedule; Part A (Seed) and Part C (Assert) are the same two Maestro subflows on both apps.

**Proves.** B-MIGR-004, B-MIGR-005, B-NAV-017, B-MIGR-006, B-MIGR-007, B-MIGR-008, B-MIGR-009, B-MIGR-010, B-MIGR-011, B-MIGR-013, B-MIGR-012 (11).

**Steps.**

1. Part A on the Flutter app: connect FX-OC2 with a password; set Arabic and Smallest text, style Soft with a tweak; turn on finished-reply notifications; save a draft with an attachment, a saved prompt, a stash, and an offline queued prompt; pin two conversations; accept an always-allow rule; pin a certificate; set up built-in Linux with a project and start the in-app server; add a scheduled prompt and place a widget and two shortcuts.
2. Part B (baseline): run Part C on the Flutter app to prove the assertions themselves.
3. [adb] `adb install -r native.apk` (never uninstall): the install succeeds in place because package name and certificate match. [B-MIGR-004]
4. Launch the native app. It may show a short "updating" state but asks nothing and needs no sign-in. Part C: the Conversations list is shown, not the welcome screen, and no first-run guide or first-reply notification question appears: an upgraded device is never walked through first run. [B-MIGR-005, B-NAV-017]
5. Part C: language Arabic, text Smallest, style Soft with the tweak, notification choices and tips state as set; nothing reset to defaults. [B-MIGR-005]
6. Part C: FX-OC2 is connected with no password prompt; the pin is still in force. [B-MIGR-006]
7. Part C: the draft with its attachment, saved prompt, stash, pins, always-allow rule and queued prompt are all there; the queued prompt flushes once when online. [B-MIGR-007]
8. Part C: the in-app Linux and its project and server are there without reinstall; the server starts. [B-MIGR-008]
9. Part C: attachments, voice model and reports from the old documents directory are readable and not re-downloaded. [B-MIGR-009]
10. [adb] kill the native app during the first launch of a fresh upgrade and relaunch: the migration resumes, nothing is duplicated, and the Flutter source data is still present until verified. [B-MIGR-010]
11. Part C: no password or token appears in `adb logcat`, the diagnostics report or any notification during the migration. [B-MIGR-011]
12. Part C: the schedule still fires, the widget redraws, the pinned shortcuts and Quick Settings tile are republished. [B-MIGR-013]
13. Open Settings > About: a new native release notice still appears through the in-app update line and opens the install flow; code push no longer applies. [B-MIGR-012]

**Note.** Parts A and C are authored once and proven on Flutter (step 2); only steps 3 to 13 need the native build.

## Coverage

Rows proven by at least one flow, per area: AGENT 24, ANDR 32, BUILD 1, CHAT 17, KIT 2, L10N 4, LINUX 43, MEDIA 6, MIGR 10, NAV 53, PROTO 13, SEC 18, STATE 17.

3 rows are asserted in more than one flow (they sit on shared paths such as notifications or widgets).

### Not covered by a device flow, by design

- Wire contracts, parsers, stored-format readers and redaction tables: native contract and unit tests prove them faster and more exactly (the 14 demoted rows are listed in `BEHAVIORS.md` Appendix B).
- Everything that needs a different device class than the matrix above (32-bit-only CPUs, other Android makers' battery rules, the Android 15 six-hour foreground-service cap). These need manual or lab runs.
- Rendering quality: the 7,967 widget tests and the screenshot rows come in the later widget pass; streaming smoothness needs frame-timing benchmarks, not Maestro.

### Things to confirm on the first Flutter run

The wording of a few labels is quoted from the English ARB and may have moved: confirm each on the first Flutter run and fix the flow, not the app.

- Pairing and connect: "Connect to my computer", "Paste pairing code", "Scan pairing code", "Test connection", "Connect", "Server password".
- Chat: "Allow once", "Always allow", "Deny", "Permission needed", "Reply finished", "Stop", "Continue".
- Settings and tools: "Settings", "Search settings", "Use my default", "Text size", "Saved prompts", "Scheduled prompts", "Terminal", "New terminal", "Free up space", "Technical details".
- Steps that name a control by role only (for example the attach control, the pin action, the export action) must be given their exact label from the first Flutter run; record it next to the step.
