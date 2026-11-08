# Privacy Policy

**Effective date: November 7, 2026**

OpenCode for Android is a client for OpenCode servers. It does not provide a
developer-operated account or advertising. It can send crash and error reports
and counts of which parts of the app are used and whether they work to the
developer, but only if you turn that on (see "Optional crash reports, usage
and app health" below). The app does not sell personal data.

## Where your data goes

You choose the OpenCode server that the app connects to. Prompts, chat history,
selected attachments, terminal requests, tool approvals, and workspace actions
are sent to that server when needed to perform the action you requested. The
server may then send content to whichever AI provider its owner configured.
Those servers and providers are controlled by you or their respective operators,
not by this app. Their retention and privacy practices apply to the data they
receive.

Use an HTTPS server or an encrypted tunnel. Plain HTTP is accepted for
loopback addresses used by a server running on the same device, and for
private Tailscale addresses (100.64.0.0/10 and `*.ts.net`), where the
tailnet already encrypts and authenticates the connection. It is also
accepted for private network addresses (10.0.0.0/8, 172.16.0.0/12,
192.168.0.0/16, 169.254.0.0/16, IPv6 `fc00::/7` and `fe80::/10`, and
`*.local` names), but only after the app warns that your password and
conversations then travel unencrypted on that network and you choose
"Use it anyway". The choice is stored per server and removed with it. Public
addresses and other host names stay HTTPS-only.

### Optional AI Team plugin

If you turn on the AI Team plugin for a server, the app also reads from a
Gas City supervisor you name (runs, work items, agents, pending decisions,
agent transcripts and estimated usage). Reads send no credentials. The app
keeps a cache of the last snapshot, the event cursor, view preferences and
what it has streamed of agent output, all under keys scoped to that server;
turning the plugin off or deleting the server removes them. Nothing is sent
anywhere except the supervisor you configured.

When the host runs the optional front, the app can also send actions
(answers to decisions, messages and controls for agents, run cancellation,
merge approval and merge). Each action carries a random idempotency key
and is identified on the host by your Tailscale login; the app keeps a
record of each action and its receipt under the same server-scoped keys.
The front never receives a password from the app.

## Optional crash reports, usage and app health

Off by default, and opt-in only. There are two separate switches in
**Settings > Privacy and data**, and a third choice that is asked every time:

1. **Usage and app health** (`usage`): the counts, timings and ranges listed
   in "Usage and app health" below.
2. **Crash reports** (`crashes`): the crash data listed in "Crash reports"
   below.
3. **Problem report**: Settings > Help > "Send a problem report". It sends
   nothing until you have read the whole report and tap **Send**.

On the first start after installing or updating to 2.1.5, a build that can send
these asks once, "Help improve OpenCode Mobile?", with three answers: "Share
both" turns on `usage` and `crashes`, "Not now" leaves both off and the app
does not ask again, and "Choose" opens the two switches. An answer already
given in an earlier 2.1.5 preview carries over: its single "send reports"
yes turns `usage` on, and turns `crashes` on too unless you had turned the
"Offer crash reports" switch off. Either way each switch can be changed at any
time. The two services are run by Google, so Google receives the data under its
own terms: **Google Firebase Crashlytics** for crash data and problem reports,
**Google Analytics for Firebase** for the counts, and **Firebase Performance
Monitoring** for timings (see "Performance" below). Nothing is collected or
sent while a switch is off. Turning `crashes` off discards unsent crash
reports; turning `usage` off resets the analytics data kept on the phone
together with its random install identifier, so turning it on again later
starts afresh. There is no tracking across apps or websites, no advertising
and no account; nothing sent identifies you by name, email or phone number.
The app contains no remote-config code. Every value sent is one of a fixed
list of words, a fixed range, or a number or version with a fixed shape (the
app drops anything else), so no free text, error message, path, URL, host
name, address, prompt, reply, key or token can be sent.

### Crash reports

With `crashes` on, the app sends:

- **Fatal and non-fatal errors**: the kind of error (for example "Invalid
  state") and the code locations (library, line and column) it happened in,
  never its message; for a failure the app handled, a fixed name such as
  `setup_step_failed` with a few short codes (the setup step name, an exit
  code, the pack name of a voice model, the kind of refusal). A crash in the
  app's Android (Java or Kotlin) code carries only the exception class names
  and their code locations.
- **Why Android ended the app last time**: on the next start, one of crash,
  crash_native, anr, low_memory, excessive_resource, signaled,
  user_requested, dependency_died or other, as `exit_reason`.
- **The last steps you took**: breadcrumbs of the form `area.action`, from
  this fixed list: `app.start`, `app.foreground`, `app.background`;
  `nav.open:` and a page kind (the same list as `screen_view`);
  `chat.send`, `chat.stop`, `chat.approve`, `chat.deny`, `chat.tool_wait`;
  `conn.connect`, `conn.drop`, `conn.reconnect`; `linux.step:` and a setup
  step, `linux.fail`; `agent.start:` and an agent, `agent.fail`;
  `update.offer`, `update.install`, `update.fail`; `settings.change:` and a
  settings group.
- **Custom keys**: `app_version` (for example 2.1.5), `build_kind` (store or
  sideload), `server_kind` (phone, computer or none), `engine_state`
  (stopped, starting, ready or failing), `connections` (0, 1, 2-3 or 4+) and
  `oc_server_version` (major and minor only, for example 2.0).
- What Crashlytics adds on its own: phone model, Android version, free memory
  and storage, whether the phone is rooted, a random installation identifier,
  and the time.

### Usage and app health

With `usage` on, the app counts exactly these events, and no others:

| Event | What it carries |
| --- | --- |
| `app_open` | `kind`: cold (the app started) or warm (it came back from the background) |
| `setup_step_reached` | `step`: one of linux, essentials, python, node, opencode, aiteam, voice, start, other |
| `setup_step_failed` | `step` (as above); `kind`: crash, timeout, network, space or other; `compat_mode`: true or false; `node_variant` for the Node.js step: the safe mode this phone kept (armcap, threads, both), none_worked when none worked, or other |
| `setup_done` | `minutes`: a range (under 2, 2 to 5, 5 to 10, 10 to 20, 20 to 40, over 40) |
| `first_reply` | `server_kind`: phone or computer; `seconds`: a range (under 5, 5 to 15, 15 to 30, 30 to 60, 60 to 180, over 180, unknown) |
| `connect_computer_chosen` | nothing |
| `update_offered` | nothing |
| `update_installed` | nothing |
| `screen_view` | `screen`: the kind of page, one of conversations, chat, new_chat, servers, this_phone, setup, privacy, appearance, integrations, settings, files, git, review, team, usage, agents, about, other |
| `feature_used` | `feature`: one of voice, camera, file_attach, agent_switch, model_switch, server_add, git_action, review_open |
| `session_length` | `minutes` the app was in front before going to the background: a range (under 1, 1 to 5, 5 to 15, 15 to 60, over 60) |
| `conn_attempt` | `server_kind`: phone or computer; `result`: ok or fail; `fail_kind`: dns, tls, auth, timeout, refused, server_5xx, protocol, offline or other; `ms_range`: a range (<500, 500-2000, 2000-10000, >10000 milliseconds) |
| `conn_drop` | `server_kind`; `reason`: network_change, server_closed, idle_timeout, background or other; `recovered`: yes or no |
| `send_result` | `result`: ok, fail or queued; `fail_kind`: as `conn_attempt` plus rejected or too_large; `first_reply_range`: <2s, 2-10s, 10-30s, >30s or none |
| `turn_stall` | `stage`: waiting_tool, waiting_approval, streaming or other; `minutes_range`: <1, 1-5, 5-15, 15-60 or >60 |
| `queue_flush` | `sent` and `failed`: a count range (0, 1, 2-5, 6-20, >20) |
| `linux_setup_step` | `step`: download, unpack, essentials, node, opencode, paseo, agents or done; `result`: ok or fail; `ms_range`: <10000, 10000-60000, 60000-300000 or >300000 milliseconds; `exit_kind`: 0, 1, 126, 127, signal or other; `signal`: SIGILL, SIGSEGV, SIGBUS, SIGKILL, other or none; `compat_mode`: none, armcap, threads or both; `free_space_range`: <2, 2-8, 8-32 or >32 (GB) |
| `agent_start` | `agent`: claude_code, codex, gemini, antigravity, opencode or other; `result`: ok or fail; `fail_kind`: not_installed, needs_sign_in, crashed, timeout or other |
| `bg_service_end` | `reason`: os_limit, user_stopped, idle, crashed or other; `battery_exempt`: yes or no |
| `update_result` | `kind`: in_app or patch; `result`: installed, failed or declined; `fail_kind`: download, checksum, signer, version, installer or other |
| `app_start` | `kind`: cold or warm; `ms_range`: <500, 500-1000, 1000-2000, 2000-5000 or >5000 milliseconds |
| `jank` | once per session per kind of page: `screen` (as `screen_view`); `janky_pct_range`: the share of slow frames (<1, 1-5, 5-15 or >15 percent); `refresh_hz`: 60, 90, 120 or other |
| `memory_pressure` | `level`: moderate, low or critical |

It also sets these device properties once, as ranges and words:
`ram_range` (<3, 3-4, 4-6, 6-8, 8-12 or >12 GB), `free_storage_range` (<2,
2-8, 8-32 or >32 GB), `abi` (arm64, arm32 or x86_64), `webview_major` (a
number), `notif_allowed` (yes or no), `server_kind` (phone, computer or both)
and `oc_server_version` (major and minor).

Google Analytics adds on its own a random app-instance identifier, the phone
model, Android version, app version, language, the time, and a coarse location
(country and region) it works out from the connection without keeping the IP
address. It also counts a few events of its own, such as the first open and
the length of a session. The advertising ID and Android ID (SSAID) are never
collected (the app does not even hold the advertising-ID permission), ad
personalisation and ad storage are denied, Analytics does not report screens
by itself (only the page kinds above), and no data is exported to any other
Google product.

### Performance

With `usage` on, the app also measures how long app start, connecting, the
first reply and setup steps take, and how smooth screens are (slow and frozen
frames), through Google's Firebase Performance Monitoring. It never records
addresses or text. Collection is off in the app until you turn `usage` on, and
off again when you turn it off. Only these fixed traces are sent, each with a
duration in milliseconds and attributes that are one word from a fixed list:

| Trace | Attributes |
| --- | --- |
| `app_cold_start` | none |
| `conn_connect` | `server_kind`: phone or computer; `result`: ok or fail |
| `send_first_reply` | `server_kind`: phone or computer |
| `linux_setup_<step>` | `step`: one of download, unpack, essentials, node, opencode, paseo, agents, done; `result`: ok or fail |
| `agent_start` | `agent`: one of claude_code, codex, gemini, antigravity, opencode, other; `result`: ok or fail |

Firebase also records its own app start time and the share of slow and frozen
frames per screen on its own. The app does not use Firebase's network
monitoring: no web request, address, host name or path is traced or sent.
Anything outside the lists above is dropped by the app before it can be sent.

### Problem report

Settings > Help > "Send a problem report" builds one report: the last 500
lines of the app's own short log (the breadcrumbs above, the events sent, the
kind of each error and what the app printed), the custom keys above, the
recent Android process ends, and the device properties above. Before you see
it, the app blanks keys, tokens, URLs, host names, addresses, e-mail
addresses and file paths. You read the whole text, then choose **Send** or
close it; closing sends nothing. It does not depend on the two switches and
is asked every time. A sent report goes to Crashlytics as one report with the
text attached, and you are shown a report code (the first 8 characters of the
install identifier and a counter) that you can quote in a GitHub issue; it is
also shown in Settings > About.

The separate "Offer crash reports" switch is unchanged: it offers to prepare a
report you review and send yourself through GitHub, with no third party.

## Data stored on your device

The app stores server profile metadata, interface preferences, cached server
state, downloaded voice models, unsent drafts and photos awaiting recovery, and
prompts queued while offline. Ordinary draft and pending-photo payloads use
app-private files; their metadata uses preferences. New stash attachment payloads
also use a separate app-private file store. Older inline stash payloads migrate
when Saved prompts is opened; failed migrations retain their original data.
Queued prompts and unmigrated legacy stashes can still include attachments
encoded into preferences. These local stores are not
encrypted separately from the Android app sandbox. Queue entries are bounded by
age, count, and total size. Server passwords are stored with Android secure
storage. Android backup is disabled for this app.

On-device Termux setup writes a server password into the private Termux/Ubuntu
environment so that the local server can restart. Protect access to both apps
and remove that environment when you no longer use it.

On Android, the home-screen widget and the app icon's long-press menu can show
the titles of your recent and pinned sessions, and the Quick Settings tile can
show how many requests are waiting for you. These surfaces show only session
titles or a count — never prompt text, tool input, file names, or server
addresses. Removing a server clears them; disconnecting also withdraws the
long-press entries and the tile count.

You can remove a server profile in the app. You can erase all local data by
clearing the app's storage or uninstalling it. Data retained by an OpenCode
server or AI provider must be deleted through that service.

## Optional remaining-usage collector

Remaining usage is a separately installed server extension, not a built-in
OpenCode endpoint. On each screen visit, the app asks you to confirm that you
installed or trust the collector before it requests the Codex
fixed route (`/ocmn/quota/v1`)
at your saved server's exact origin using that profile's server sign-in. It never
forwards that sign-in to a different origin or follows a redirect.

The operator must authenticate the proxy route and replace the client's
credentials with a dedicated collector-only read token before forwarding to
the private collector. If explicitly configured by its operator, the optional
collector reads one selected Codex OAuth credential file on the server and
contacts the provider's fixed usage endpoint. Provider tokens are not sent to the app,
refreshed by the collector, or written to a new credential store.

The app displays only normalized core account windows, plan information when
reported, reset times and freshness. It does not receive provider tokens or an
inferred account email. Claude subscription collection is unavailable pending
a supported, permitted integration: choosing Claude sends no quota request.
The collector's legacy Claude route returns unsupported without reading Claude
credentials or contacting that provider; obsolete Claude settings are ignored.

Screen-visit quota consent and measured snapshots are kept only in memory.
Changing provider clears the old snapshot and requires fresh consent before
another supported page read. Separately, you can explicitly enable monitoring
for the exact trusted collector and provider account you just reviewed. That
monitoring consent and its rules are saved per server profile and survive app
restart; measured quota snapshots are not saved. Monitoring checks at most
three saved sources per cycle, every five minutes in the foreground or every
fifteen minutes while the existing live background service is active. Larger
source lists rotate over several cycles. Monitoring does not start that service,
and Android may stop it under its background-work limits.

Device quota alerts require a separate opt-in and a fresh reported threshold
reading. Wi-Fi-only reads and quiet hours are optional. An alert records a past
reading, not a guarantee of the current allowance. Monitoring can be disabled
from its settings; removing the server profile removes its saved monitoring
rules. Quota data is not uploaded to this project's developer. Invalid or missing
data is not converted into an estimated allowance. The provider's own privacy
and access policies apply, and its internal usage endpoint may change.

## iOS source preparation

The experimental iOS runner is for remote server control only. Its source uses
the platform secure-storage plugin with Keychain entitlements for server
passwords and declares local-network access to reach a server you select. It
does not enable Android's Termux setup, background service, notifications,
camera, local dictation or incoming-share bridges. Native Keychain, plugin and
privacy-manifest validation remain prerequisites for iOS distribution; a source
scaffold or simulator artifact is not a released iPhone app.

## Pending provider sign-in recovery

For supported servers, the app retains bounded sign-in recovery metadata per
server profile: attempt and integration IDs, method kind, mode, original origin
and project/workspace location, and recovery expiry. It does not save the browser
authorization URL, one-time code, submitted answers or provider tokens. Resume,
check, cancel and local forgetting are explicit actions. Forgetting a local
record or deleting a profile does not cancel a remote sign-in or revoke its
credentials. Failed storage writes are disclosed because they cannot guarantee
recovery after restarting the app.

## App diagnostics

Handled app and startup errors are kept in process memory only, with a maximum
of 20 entries, and disappear when the app process ends. The app redacts
authorization headers, credential-like values, URL credentials and queries,
and long token-like strings. It does not add chat messages or file contents to
these reports.

Diagnostics are never sent automatically. If you explicitly tap **Send** in
App diagnostics, the redacted entries currently shown on that screen are sent
to your selected OpenCode server with the active directory and workspace
context. Copy and Clear remain local actions.

## Microphone and local voice input

Microphone access is requested only when you start voice input. Recorded audio
is transcribed locally on the device with a downloaded speech model. The app
does not upload the recording, and voice input never sends a prompt
automatically. Transcribed text is sent only if you choose to submit it as part
of a prompt.

## Optional read-aloud

On Android, Read reply prose requires confirmation before accessing the system
speech engine. Loaded assistant prose is sent to that separately installed
engine; code blocks and tool details are omitted. Only voices marked offline
are offered, but that metadata is not a guarantee of network isolation: the
engine's own privacy practices apply. The app does not save speech input or
automatically download voices. Playback is foreground-only, can be stopped
explicitly, and is interrupted on backgrounding, chat coverage, scope changes
and audio-focus loss. People nearby may hear the audio.

## Files, terminal access, and Termux

The app reads a local file after you select it through a file or photo picker,
paste it, or explicitly take a photo. Camera permission also supports QR pairing.
Picked photos are kept locally with their original conversation until added to
a draft or discarded, including recovery after an interrupted Android picker.
The app does not request access to your entire photo library. An attachment is
sent to your selected OpenCode server only when you
submit the prompt. Files opened from a workspace are loaded from that server.

If you enable on-device setup, the app uses Termux's explicit `RUN_COMMAND`
permission to install, start, update, and interact with an OpenCode server on
your device. OpenCode can read files and run commands allowed by its host
environment. Treat access to an OpenCode server like terminal access to that
machine.

## Background mode and updates

Optional background mode keeps server events and terminals connected using an
Android foreground service with a persistent notification. It can use more
battery. The optional battery-optimization exemption is requested only after
you choose it in Settings.

While that mode is enabled and the app is backgrounded, OpenCode can also show
generic notifications when a coding session needs permission or an answer,
finishes, or fails. These notifications intentionally omit prompt text, tool
input, filenames, session titles, and server error details from the lock screen.

The app uses Shorebird to check for and download compatible code updates.
Update requests necessarily expose network information such as your IP address
and app/update identifiers to Shorebird. Shorebird's own privacy terms govern
that processing.

## Provider and agent logos

The Providers, model catalog and model picker screens show a logo next to each
AI provider, and the agent screens show one next to each coding agent. The
logos of agents and of the common providers are single-colour marks bundled
inside the app, so showing them makes no network request. For a provider
without a bundled mark, the app downloads its logo from models.dev
(`models.dev/logos`) by provider name, for example `/logos/example-ai.svg`.
Nothing about you, your server, or your sessions is sent: the request carries
only that name and your IP address, the same as loading any website. Downloaded
logos are cached on the phone and refreshed at most weekly; they are removed
with the app's data. If the download fails or you are offline, the app shows a
two-letter monogram instead.

## Permissions

- **Internet:** connect to your server, download local voice models, open links,
  and check for app updates.
- **Microphone:** capture audio only while you use local voice input.
- **Notifications and foreground service:** show the persistent status and
  privacy-safe coding alerts used by optional background mode.
- **Battery optimization exemption:** optional request for long-running coding
  sessions.
- **All files access (optional):** asked only when you open or create a
  project in your phone's shared storage (for example a folder under
  `/sdcard`), after a plain explanation. Android 11+ hides other apps' files
  in shared storage unless it is on. It lets OpenCode inside the app read and
  change files in the folders you open as projects, and nothing else is
  collected or sent because of it. It is never requested at start-up or for
  projects in the app's own project space, and you can turn it off at any
  time in Android Settings, under All files access. On Android 10 and older
  the classic storage permission is asked instead. If the server runs in
  Termux, that is Termux's own storage permission, not this one.
- **Termux command permission:** optional control of an on-device OpenCode
  server.

## Contact

For privacy questions or reports, open an issue at
[github.com/Eslamasabry/opencode-mobile-next/issues](https://github.com/Eslamasabry/opencode-mobile-next/issues).
