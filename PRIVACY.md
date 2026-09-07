# Privacy Policy

**Effective date: September 6, 2026**

OpenCode for Android is a client for OpenCode servers. It does not provide a
developer-operated account, advertising, analytics, or crash-reporting service.
The app does not sell personal data.

## Where your data goes

You choose the OpenCode server that the app connects to. Prompts, chat history,
selected attachments, terminal requests, tool approvals, and workspace actions
are sent to that server when needed to perform the action you requested. The
server may then send content to whichever AI provider its owner configured.
Those servers and providers are controlled by you or their respective operators,
not by this app. Their retention and privacy practices apply to the data they
receive.

Use an HTTPS server or an encrypted tunnel. Plain HTTP is accepted only for
loopback addresses used by a server running on the same device.

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

Quota consent and snapshots are kept only in memory for the screen visit;
there is no background polling, quota notification, or quota upload to this
project's developer. Changing provider clears the old snapshot and requires
fresh consent before another supported read. Invalid or missing
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

## Provider logos

The Providers, model catalog and model picker screens show a logo next to each
AI provider. Those images are fetched on demand from Google's public favicon
service (`t1.gstatic.com/faviconV2`) using the provider's website domain, for
example `anthropic.com`. Nothing about you, your server, or your sessions is
sent: the request carries only the domain name and your IP address, the same
as loading any website. Images are cached in memory for the app's lifetime and
never written to disk. If the request fails or you are offline, the app shows
a two-letter monogram instead.

## Permissions

- **Internet:** connect to your server, download local voice models, open links,
  and check for app updates.
- **Microphone:** capture audio only while you use local voice input.
- **Notifications and foreground service:** show the persistent status and
  privacy-safe coding alerts used by optional background mode.
- **Battery optimization exemption:** optional request for long-running coding
  sessions.
- **Termux command permission:** optional control of an on-device OpenCode
  server.

## Contact

For privacy questions or reports, open an issue at
[github.com/Eslamasabry/opencode-mobile-next/issues](https://github.com/Eslamasabry/opencode-mobile-next/issues).
