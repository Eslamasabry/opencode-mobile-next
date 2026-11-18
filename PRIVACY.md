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

For a plain-words summary of why we collect this, what it never includes and
how we use it, see [About analytics and crash
reports](docs/releases/v2.1.5+63.md#about-analytics-and-crash-reports) in the
2.1.5 release notes.

Off by default, and opt-in only. There are two separate switches in
**Settings > Privacy and data**, and a third choice that is asked every time:

1. **Usage and app health** (`usage`): the counts, timings and ranges listed
   in "Usage and app health" below.
2. **Crash reports** (`crashes`): the crash data listed in "Crash reports"
   below.
3. **Problem report**: Settings > Help > "Send a problem report". It sends
   nothing until you have read the whole report and tap **Send**.

After your first finished reply (never when you first open the app), a build
that can send these asks once, "Help improve OpenCode Mobile?", with three
answers: "Share both" turns on `usage` and `crashes`, "Not now" leaves both off
and the app does not ask again, and "Choose" opens the two switches. If you
close the app before a reply has finished, the question waits for the first
finished reply of the next run. An answer already given in an earlier 2.1.5
preview carries over: its single "send reports"
yes turns `usage` on, and turns `crashes` on too unless you had turned the
"Offer crash reports" switch off. Either way each switch can be changed at any
time. The Google services below are run by Google, which receives the data under its
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
  never its message; for a failure the app handled, one of seven fixed names with
  a few short codes: `setup_step_failed` (the setup step, the kind of error
  and, when the phone closed the app, why), `process_exit` (the `exit_reason`
  below), `voice_model_download_failed` (the kind of problem and the voice
  pack name), `prompt_not_accepted` (whether the server refused the message
  before or after it was sent, the kind of failure and how long it took),
  `net_client_close_failed` (the kind of error, when closing the connection to a
  server failed), `phrase_chips_unreadable` (no code: your learned phrase
  suggestions could not be read and were kept aside) and
  `phone_server_follow_refused` (no code: the phone server could not be told
  that the app is in front, twice in a row). A crash in the app's Android (Java or Kotlin) code
  carries only the exception class names and their code locations.
- **Why Android ended the app last time**: on the next start, one of crash,
  crash_native, anr, low_memory, excessive_resource, signaled,
  dependency_died or other, as `exit_reason`. Stops Android labels `user_requested` are counted only by the usage event below, never sent as non-fatal exceptions.
- **The last steps you took**: breadcrumbs of the form `area.action`, from
  this fixed list: `app.start`, `app.foreground`, `app.background`;
  `nav.open:` and a page kind (the same list as `screen_view`);
  `chat.send`, `chat.stop`, `chat.approve`, `chat.deny`, `chat.tool_wait`;
  `conn.connect`, `conn.drop`, `conn.reconnect`; `linux.step:` and a setup
  step, `linux.fail`; `agent.start:` and an agent, `agent.fail`;
  `update.offer`, `update.install`, `update.fail`; `settings.change:` and a
  settings group.
- **Custom keys**: `app_version` (for example 3.0.0), `build_kind` (store,
  sideload, ci or local), `server_kind` (phone, computer or none),
  `engine_state` (stopped, starting, ready or failing), `connections` (0, 1,
  2-3 or 4+), `oc_server_version` (major and minor only, for example 2.0),
  `report_kind` (crash, error, failure or problem_report) and `report_code`
  (the code shown with a problem report).
- What Crashlytics adds on its own: phone model, Android version, free memory
  and storage, whether the phone is rooted, a random installation identifier,
  and the time.

### Usage and app health

With `usage` on, the app counts exactly these events, and no others. The page "What we collect" in the app lists the same events in plain words, and a test fails the build when the code, that page, the in-app policy and this table stop agreeing.

| Event | What it carries |
| --- | --- |
| `app_open` | `kind`: cold (the app started) or warm (it came back from the background) |
| `setup_step_reached` | `step`: linux, essentials, python, node, opencode, aiteam, voice, start or other; `attempt`: 1, 2 or 3+ |
| `setup_done` | `minutes_range`: how long the whole setup took: under_2, 2_to_5, 5_to_10, 10_to_20, 20_to_40 or over_40; `server_kind`: phone or computer |
| `setup_abandoned` | you left an unfinished setup for 5 minutes or more: `step` (the last one reached): linux, essentials, python, node, opencode, aiteam, voice, start or other; `reason`: background or closed; `minutes_range`: 5-15, 15-60, 60-1440 or >1440 |
| `setup_resumed` | you came back to an unfinished setup: `step`: linux, essentials, python, node, opencode, aiteam, voice, start or other; `minutes_range` (how long you were away): 5-15, 15-60, 60-1440 or >1440 |
| `first_reply` | the first reply measured after you agreed to usage, once per install: `server_kind`: phone or computer; `first_reply_range`: <2s, 2-10s, 10-30s, >30s or none; `after_consent`: yes when you had already seen a reply before you agreed (nothing is sent about that earlier reply), otherwise no |
| `connect_computer_chosen` | nothing |
| `update_offered` | nothing |
| `update_installed` | nothing |
| `star_prompt_shown` | nothing (the one-time ask to star the project on GitHub came into view) |
| `star_prompt_tapped` | nothing (you tapped Star on GitHub) |
| `star_prompt_dismissed` | nothing (you tapped Not now) |
| `screen_view` | `screen`: the kind of page, one of conversations, chat, new_chat, servers, this_phone, setup, privacy, appearance, integrations, settings, files, git, review, team, usage, agents, about or other |
| `feature_used` | `feature`: one of voice, camera, file_attach, agent_switch, model_switch, server_add, git_action, review_open |
| `session_length` | `minutes_range` the app was in front before going to the background: under_1, 1_to_5, 5_to_15, 15_to_60 or over_60; `turns_range` (messages answered meanwhile): 0, 1, 2-5, 6-20 or >20 |
| `journey_milestone` | a first-use step, counted once: `step`: opened, setup_opened, setup_started, setup_done, server_ready, chat_opened, first_prompt or first_reply; `after_range` (how long after you first agreed to usage): under_5m, 5_to_30m, 30m_to_2h, 2h_to_1d, 1_to_3d or over_3d |
| `use_for` | your one answer to "What do you want help with?": `kind`: everyday, coding, both or looking |
| `starter_used` | `starter`: the kind of suggestion you tapped, one of list_folder, find_bug, build_web_page, python_script, node_project, readme, explain_project, what_changed, add_tests or other (never its words) |
| `share_in` | `count`: 0, 1, 2-5, 6-20 or >20; `kind`: image or file (never a name or address) |
| `conn_attempt` | `server_kind`: phone or computer; `result`: ok or fail; `fail_kind`: dns, tls, auth, timeout, refused, server_5xx, protocol, offline or other; `ms_range`: <500, 500-2000, 2000-10000 or >10000 milliseconds |
| `conn_drop` | `server_kind`: phone or computer; `reason`: network_change, server_closed, idle_timeout, background or other; `recovered`: yes or no |
| `send_result` | `result`: ok, fail or queued; `fail_kind`: dns, tls, auth, timeout, refused, server_5xx, protocol, offline, other, rejected, too_large, rate_limit, free_limit, sign_in or model_error; `first_reply_range`: <2s, 2-10s, 10-30s, >30s or none; `agent`: build, plan, general or other; `server_kind`: phone or computer; `input`: text, voice, attachment or voice_attachment |
| `turn_stall` | `stage`: waiting_tool, waiting_approval, streaming or other; `minutes_range`: <1, 1-5, 5-15, 15-60 or >60; `cause`: thinking_silent, tool_running, retrying, stream_dead_refetched, stream_silent or unknown. Silent thinking is named only after a bounded live-status check confirms the run is busy; otherwise the quiet connection is unconfirmed. No reply content is sent |
| `chat_turn` | how a turn ended: `outcome`: done (something to read or a tool shown), no_output (it ended with nothing to show), stopped_by_user, error, connection_lost or approval_timeout; `agent`: build, plan, general or other; `server_kind`: phone or computer; `first_reply_range`: how long until the first text or tool you could see: <2s, 2-10s, 10-30s, >30s or none; `activity_range`: how long until the agent first showed any sign of work, including thinking (same ranges as `first_reply_range`); `ms_range`: <10s, 10-30s, 30-120s, 2-10m or >10m; `tools_range`: 0, 1-3, 4-10, 11-30 or >30; `fail_kind`: provider_auth, rate_limit, free_limit, context_overflow, output_length, content_filter, model_not_found, provider_connection, aborted, server or other; `recovered`: none, yes or no; `follow_up`: none, steer or after_fail |
| `approval` | `kind`: what was asked, one of run, edit, read, web, question or other; `result`: allowed, always, denied, answered or declined (your answer, once the server took it), auto (a saved Auto or Approve everything choice answered), stopped (the run was stopped) or expired (it was never answered while the run went on); `surface` (only with your own answer): where you answered, chat, home, notification or widget; `ms_range` (asked to answered): <10s, 10-30s, 30-120s, 2-10m or >10m. Never the command, file or question |
| `queue_flush` | `sent` and `failed`: a count range (0, 1, 2-5, 6-20 or >20) |
| `linux_setup_step` | `step`: download, unpack, essentials, node, opencode, paseo, agents, start, voice or done; `result`: ok, fail, skipped or cancelled; `attempt`: 1, 2 or 3+; `fail_kind`: network, storage, apt, permission, download, checksum, killed_by_system, timeout or unknown; `ms_range`: <10000, 10000-60000, 60000-300000 or >300000 milliseconds; `exit_kind`: 0, 1, 126, 127, signal or other; `signal`: SIGILL, SIGSEGV, SIGBUS, SIGKILL, other or none; `compat_mode`: none, armcap, threads or both; `free_space_range`: <2, 2-8, 8-32 or >32 (GB) |
| `agent_start` | `agent`: claude_code, codex, gemini, antigravity, opencode or other; `result`: ok or fail; `fail_kind`: not_installed, needs_sign_in, crashed, timeout or other |
| `bg_service_end` | `reason`: os_limit, user_stopped, idle, crashed or other; `battery_exempt`: yes or no |
| `update_result` | `kind`: in_app or patch; `result`: installed, failed or declined; `fail_kind`: download, checksum, signer, version, installer or other |
| `app_start` | `kind`: cold or warm; `ms_range`: <500, 500-1000, 1000-2000, 2000-5000 or >5000 milliseconds |
| `app_exit` | The previous main process exit, at most once per retained exit: `exit_reason`: crash, anr, low_memory, excessive_resource, signaled, user_requested, dependency_died or other; `foreground`: yes or no when Android recorded process importance, omitted when unknown. Native crashes use crash; self-exits, updates and unlisted reasons use other. The exit timestamp, process name and system description are never sent. Its deduplication timestamp stays on this phone, independently of the crash-report marker |
| `jank` | once per session per kind of page: `screen`: as `screen_view` (conversations, chat, new_chat, servers, this_phone, setup, privacy, appearance, integrations, settings, files, git, review, team, usage, agents, about or other); `janky_pct_range`: the share of slow frames (<1, 1-5, 5-15, 15-30 or >30 percent); `frozen_range`: frames that took 700 ms or more (0, 1-2, 3-10 or >10); `refresh_hz`: 60, 90, 120 or other; `late_pct_range`: the share of frames that missed their own deadline (same ranges as `janky_pct_range`); `seen_hz`: the refresh rate most frames were given (same words as `refresh_hz`); in the chat one report for each `interaction`, what was going on while the frames were drawn: streaming (a reply arriving), scrolling, menu (a menu or sheet open), keyboard or idle |
| `memory_pressure` | `level`: moderate, low or critical |

It also sets these device properties once, as ranges and words:
`ram_range` (<3, 3-4, 4-6, 6-8, 8-12 or >12 GB), `free_storage_range` (<2,
2-8, 8-32 or >32 GB), `abi` (arm64, arm32 or x86_64), `webview_major` (a
number), `notif_allowed` (yes or no), `server_kind` (phone, computer or both),
`oc_server_version` (major and minor) and `journey_stage` (how far you are in
first use: the same words as `journey_milestone`).

Google Analytics adds on its own a random app-instance identifier, the phone
model, Android version, screen size, app version, language, the time, and a coarse location
(country and region) it works out from the connection without keeping the IP
address. It also counts a few events of its own, such as the first open and
the length of a session. The advertising ID and Android ID (SSAID) are never
collected (the app does not even hold the advertising-ID permission), ad
personalisation and ad storage are denied, Analytics does not report screens
by itself (only the page kinds above), and no data is exported to any other
Google product. (When both switches are on, Crashlytics may show the app's own
usage events beside a crash report; that stays inside the same Firebase
project.)

Usage reports also go to an analytics server we run ourselves: **Umami, hosted with Railway**.
Umami stores no IP address and no identifier of the person. It uses the connection address once
to work out a coarse location. It keeps an anonymous visitor hash that changes every month; the
hash cannot be turned back into your address or your device. No device or installation ID,
account, conversation, file name, project name, server address or model key is sent to Umami.

Umami receives the same listed events and permitted properties above. A screen becoming visible
is a page view with one fixed address per screen category, such as /home or /conversation;
other actions are named events with that screen address. Each has its own timestamp, the app's
version name and build number, a fixed app hostname and the app language with its region (such as ar-EG), and fixed words and ranges about the
app and the phone, nothing else: the Android main version number (for example 16) (`os_major`), the device class (phone or tablet)
(`device_class`) and the build number. Besides those: `version` and `build`; `channel` (public or test build);
`width_class` (compact, medium or expanded); `text_scale` (smaller, default, larger or largest); `abi` (arm64, arm32,
x86_64 or other); `ram_range` and `free_storage_range` (the same ranges as the Google user properties);
`style` (solid, glass, soft, brutal, paper or signal); `theme_mode` (system, light, dark or saver); `battery_saver`
(yes or no); `install_age` (under 1 day, 1 to 7 days, 7 to 30 days, over 30 days); `agents` (which of codex, opencode and
paseo servers are connected, product names only); and `connection_kind` (phone, computer, remote, or a mix, or none; never
an address). The same list is what "What we collect" shows under "Only to our own server". Before a request leaves the
phone, the app checks its real body against this list and drops any key or value not on it (logging only the key's name).
Taken together, these can tell one phone’s reports from another’s. They never say who you are, and the app sends no name, account or ID with them.
A browser-like User-Agent repeats only the Android main version and the device class.
Nothing else about the device goes to Umami: no screen size or resolution, model, maker, time zone, place,
minor version, or device or installation ID. The build number is also the tag. Google receives the app version automatically and the `app_build` user
property is the build number, set only with usage on and cleared when it is off.

The private queue holds up to 256 events and 256 KiB of encoded event data (300 KiB including
queue framing). Events older than three days are discarded when the app next uses the queue;
they are never sent. Short request timeouts and increasing back-off limit failed requests. Retries happen during normal app use; this queue starts no
background work and sets no alarms. Turning usage off drops the queue and cancels an in-flight
request. Crash reports, problem reports and Performance traces still go only to Google.

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
| `linux_setup_<step>` | `step`: one of download, unpack, essentials, node, opencode, paseo, agents, start, voice, done; `result`: ok or fail |
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
state, downloaded voice models, the optional on-device assistant model, unsent
drafts and photos awaiting recovery, and prompts queued while offline. Ordinary
draft and pending-photo payloads use app-private files; their metadata uses
preferences. New stash attachment payloads
also use a separate app-private file store. Older inline stash payloads migrate
when Saved prompts is opened; failed migrations retain their original data.
Queued prompts and unmigrated legacy stashes can still include attachments
encoded into preferences. These local stores are not
encrypted separately from the Android app sandbox. Queue entries are bounded by
age, count, and total size. Server passwords are stored with Android secure
storage. Android backup is disabled for this app.

In browser mode, profile metadata, preferences and draft metadata stay in this
site's browser storage. Draft, photo and stash attachment bytes use IndexedDB;
profile deletion removes the profile's local payloads. Clearing this site's data
also removes these stores. Browser eviction, private browsing and storage quotas
can prevent retention, so the app reports failed saves rather than claiming the
attachments were kept.

Browser passwords require a secure context (HTTPS or localhost) and writable
site storage. The web secure-storage plugin encrypts passwords with WebCrypto,
but stores its key material alongside ciphertext in localStorage. This is not an
OS keystore and does not protect against scripts executing in this origin or
someone with access to the browser profile. There is no plaintext fallback on
insecure origins. Use a trusted host and browser profile.

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

Microphone access is requested only when you start voice input. Voice input has
two paths:

- **Downloaded speech models:** when a speech model downloaded from
  `huggingface.co` is used, recorded audio is transcribed on the device by that
  model and never leaves the phone; the model download carries only the file's
  address and your IP address, and Hugging Face's own terms apply to it.
- **Android's on-device speech recognizer (fallback):** when a downloaded
  speech model is not used, the app asks Android for its on-device speech
  recognizer (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, Android 12 and
  later) and sets the offline preference
  (`RecognizerIntent.EXTRA_PREFER_OFFLINE`), and never uses Android's default
  speech recognizer; that system recognition service's own privacy terms apply.

The app does not upload the recording, and voice input never sends a prompt on
its own. Transcribed text is sent only if you choose to submit it as part of a
prompt.

## Optional assistant on this phone

Settings › Conversations › Assistant on this phone is off by default, and
nothing downloads until you tap Download. The model (Google's Gemma 4 E4B,
about 3.7 GB, Apache 2.0) is fetched once over HTTPS from huggingface.co at a
fixed revision and checked against a size and SHA-256 built into the app. The
request carries only the file's address and your IP address, the same as
loading any website, and Hugging Face's own privacy terms apply to it. Off
Wi-Fi the app asks before using mobile data.

After that, "Summarize on this phone" in a conversation's menu runs the model
on the phone with no network. Only the words said in that conversation are
handed to it (no tool output, no reasoning), and assistant replies are masked
for secrets the way every copy is. The summary is shown in a sheet and is not
saved; Copy puts it on the clipboard at your request. Nothing you write is sent
anywhere because of this feature.

The model file lives in the app's private storage and the on/off choice in
preferences. Both belong to the phone, not to a server profile, so removing a
server does not touch them. Delete from this phone on the same settings page
removes the file; Clear storage and uninstalling remove everything. Android
backup is disabled, so the model is never copied off the device.

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

- **Internet:** connect to your server, download local voice models and, if you
  ask for it, the on-device assistant model, open links, and check for app
  updates.
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
