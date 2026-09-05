# Complete JSON export — beta 18600

Verified against the pinned OpenCode `0.0.0-beta-18600` binary on an owned
loopback server on 2026-09-05. Machine-readable results are in
[export-beta-18600.json](export-beta-18600.json). No provider run was started;
the fixture server was stopped afterward.

The existing chat export action now offers full server JSON beside loaded
Markdown on v2. V1 keeps its existing Markdown path. JSON uses
`GET /api/session/{sessionID}/export?sanitize=true` by default, without location
query parameters. The server response envelope is preserved byte-for-byte;
the client does not hydrate all messages, decode the exported document, or
re-encode models. The native file picker requires a byte buffer, so this is
not a constant-memory stream-to-disk implementation. The same buffer is passed
to the picker; platform serialization may still copy it.

**Sanitized export is not a backup of the original text.** The pinned server
replaces even ordinary user and assistant text with `[redacted:text:…]`
placeholders. The UI explains this and allows an explicit unredacted export
for backup. Both modes returned the complete two-message fixture; unredacted
export preserved Arabic and Markdown text exactly. The UI does not promise
that every possible secret is detected.

Unauthorized export returned 401. A missing session returned a tagged
`SessionNotFoundError` with 404, which must not disable export for other
sessions. An absent route or unsupported method disables the JSON option for
the current gateway. Byte-response error envelopes are decoded to retain
these distinctions.

Download cancellation and connection/location checks prevent an old request
from opening a file picker after navigation or scope changes. Save remains
visible while format choices scroll. Errors retain the choices for retry;
canceling the native picker does not report success.

Focused checks exercise a 5,001-message byte response, both sanitization
parameters, credentials and absence of location parameters, typed errors,
unsupported capability caching, same-buffer saving, Markdown fallback,
cancel/scope changes, retry, and 411px/320px layouts (1.7x text at 320px).
The 411px render was inspected. This is not native install/device evidence.

Import remains a separate pending product workflow. The live fixture confirmed
that `data.info` plus `data.messages` is the transfer payload and that importing
the same session again returns 409. A future import UI must review the explicit
destination and retain the original file on conflicts; it must not silently
rewrite IDs or replace existing sessions.
