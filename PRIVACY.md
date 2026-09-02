# Privacy Policy

**Effective date: August 28, 2026**

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
state, downloaded voice models, and other data needed to restore your workspace.
Server passwords are stored with Android secure storage. Android backup is
disabled for this app.

You can remove a server profile in the app. You can erase all local data by
clearing the app's storage or uninstalling it. Data retained by an OpenCode
server or AI provider must be deleted through that service.

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

## Files, terminal access, and Termux

The app reads a local file only after you select it through Android's file
picker. An attachment is sent to your selected OpenCode server only when you
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
