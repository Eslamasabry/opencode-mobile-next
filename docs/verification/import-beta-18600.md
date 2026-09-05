# Conversation import — beta 18600

Cycle 15 adds **More → Import conversation** for v2. V1 does not advertise the
action. Users choose a JSON export, review its title and message-record count,
then select a destination on the connected server. The current location is the
initial choice; project roots, worktrees and managed workspaces can be selected
without changing the active chat location before import.

The reader accepts the export's `data` envelope or bare `SessionTransfer.Data`.
It incrementally decodes UTF-8/JSON, validates identifying session/message
structure and duplicate message IDs, and preserves nested protocol fields for
server validation. It does not reconstruct messages through UI models, rewrite
IDs, drop unknown message types, or silently use the source file's location.
Malformed files never enable submission. Mobile imports have an explicit
128 MiB limit, enforced from both file metadata and bytes read; larger files
are rejected without upload or truncation and need a desktop/server transfer.

The review identifies redacted placeholders, archived status and required
parent IDs. Sanitized text cannot be restored by import. The source file stays
untouched; an acknowledged import cannot be submitted twice. Connection and
location checks run again after lifecycle recovery before sending the request.
A scope change during a successful request retains the confirmed result but
does not open it on the newly selected server. Unknown network outcomes tell
the user to check All sessions before retrying; no automatic import retry runs.

The adapter sends `POST /api/session/import` with explicit body
`location.directory` and optional `location.workspaceID`, without location
query parameters. Conflicts, parent-not-found, invalid payload and authorization
failures retain the file for review. Missing parent 404s do not mark the endpoint
unsupported. Successful imports can open the returned session location.

## Live evidence

[Machine-readable evidence](import-beta-18600.json) records an owned two-server
exercise against binary SHA-256
`29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`.

- Exported an existing fixture from source server A, imported the unchanged
  `info`/`messages` into a fresh project on server B with explicit destination.
- B assigned the destination project/directory while preserving the session ID,
  both message IDs, Arabic user text and assistant content.
- A's export was byte-identical before and after the transfer.
- Reimport returned 409; unauthenticated import returned 401.
- Child-before-parent returned tagged `SessionNotFoundError`/404. Importing the
  parent and retrying the same child then succeeded with the relationship intact.
- No provider run was started. Both owned server processes were stopped.

Fifteen focused tests cover streamed envelopes, structural rejection, wire body,
typed failures, retained review/conflict state, successful opening, scope change
during wake, oversize rejection, and 411px/320px layouts with enlarged text.
The 411px render was inspected. The test font set lacks Arabic glyphs; actual
Arabic font rendering and native file-picker/device checks remain release work,
while Unicode transfer is verified independently by the wire and live fixture.

## Development APK download correction

The GitHub Actions artifact link required a GitHub session and failed for the
user. Its verified, unchanged APK was published as a development prerelease:

[Direct APK — dev-06447b6](https://github.com/Eslamasabry/opencode-mobile-next/releases/download/dev-06447b6/opencode-mobile-dev-06447b6.apk)

Anonymous download returned HTTP 200, APK content type, correct ZIP/APK header
and 186,199,352-byte length. The release asset digest matches the downloaded APK:
`2E04BC4674E6AE0954A70D0A8AC1D30DD717805BD62C28B943850E2B919A9827`.
Package `io.github.eslamasabry.opencode_mobile`, version `1.0.34` (35), min SDK 24,
ARM64/ARMv7/x86_64, signer `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`.
This is the CI test signer, not the public release signer. This prerelease does
not update release-signed installations and does not include cycles 14–15.
It does not establish stable v1 readiness.
