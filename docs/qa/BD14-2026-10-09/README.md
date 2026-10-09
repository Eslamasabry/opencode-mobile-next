# BD14 — Paseo attachment IDs, 2026-10-09

Finish line: establish the shipped attachment addressing/retrieval contract;
implement `readAttachment(id)` only if the daemon can resolve that ID.
Non-goals: UI changes, filesystem searches/path guesses, live account access,
daemon changes, builds or emulator work.
Base: `feat/genui-fe` at `8ac793bfc0ee1eb3fbf71f54487a19ed2968c5eb`.
Branch: `sol/bd-paseo-attachments`.

## Result: no bare-ID retrieval contract

The shipped Paseo 0.9.2 protocol/daemon has **no attachment-ID read RPC or HTTP
route**. It can serve an image whose complete filesystem path is known, but
cannot resolve a bare 64-hex value into that path. No gateway/state method or
capability was added: an always-null method or an adapter that guesses paths
would not fix the reported history. The owner-visible issue remains unresolved.

The exact producer of the owner's `![Image](<64-hex>)` links is **not established**
by this offline inspection. The source does generate 64-hex image filename stems,
but its renderer emits a full path with an extension, not that bare-ID shape.
We did not inspect the owner's conversation or assume its bytes still exist.

## Source findings

The app pins 0.9.2 in
[paseo_scripts.dart](../../../lib/builtin/agents/paseo_scripts.dart).
[shipped-source.txt](shipped-source.txt) preserves numbered excerpts and full-file
SHA-256 hashes from the existing local package cache, including the server's
package version and export paths. No package download was needed.

| Source in `@getpaseo/server/dist/server/server/` | Finding |
| --- | --- |
| `agent/providers/provider-image-output.js:59–80,112–144` | `materializeProviderImage` hashes decoded image bytes with SHA-256 and writes `<hash>.<extension>` in a lazily created `os.tmpdir()/paseo-attachments-<random>` directory. The directory is mode 0700 and files 0600. Only the in-process directory pointer is retained. Rendering converts an absolute Unix path into a `file://` markdown target. The image recognizer requires the attachment directory, hash **and extension**. |
| `agent/providers/claude/agent.js:484–529,4110–4143,4177–4199` | User-history extraction retains text/string input; it does not expose standalone image bytes. Tool-result images use the materializer/renderer above. Existing string markdown can pass through without becoming a retrievable attachment reference. |
| `file-upload/index.js:14–31,143–160` | General file uploads use `upload_<UUID>` IDs, store beneath `paseoHome/uploads/<id>/<filename>`, and return the complete path in `uploaded_file`. This is a different ID scheme from the reported 64-hex value. |
| `session/files/workspace-files-session.js:312–358` | A download token is issued from an explicit `cwd` and `path`, after obtaining file metadata. There is no ID-to-path lookup. |
| `file-explorer/service.js:71–110,579–599` | The file reader can return image/base64 content for a known path; lexical and canonical path checks enforce containment in the supplied root. It does not search attachment directories or append guessed extensions. |
| `bootstrap.js:485–558` | HTTP download is `/api/files/download?token=…`, after the daemon auth middleware. The token is consumed to obtain an existing file entry. It is not the image content hash. No attachment/image-ID route is registered. |

In `@getpaseo/protocol/dist/messages.js`, `ImageAttachmentSchema` carries base64
`data` and `mimeType` on sending. `UploadedFileAttachmentSchema` carries both ID
and path. `FileExplorerRequestSchema` uses `cwd`, optional `path`, mode, and an
optional `maxBytes`; `FileDownloadTokenRequestSchema` requires `cwd` and `path`.
The inbound union has no attachment/image read operation. Inspecting the daemon
dispatch and HTTP registrations found no alternate ID resolver.

The web client's IndexedDB attachment store is also not such a resolver: it
stores client-side blobs under local keys, and the web client's generated IDs
use `att_…`. These browser-local bytes are not available to our Flutter client
through a daemon RPC.

## Verification

[probe.mjs](probe.mjs) imports the actual shipped schemas, image helper and file
reader. It checks version 0.9.2; enumerates inbound file operations; rejects
invented attachment-read messages; creates a tiny fixture image; verifies its
SHA-256 filename and full-path markdown; reads the image by complete path; and
confirms that the bare hash does not resolve. It deletes only the private
temporary directory that its own materializer invocation created.

Executed serially through the shared test lock:

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- node \
  docs/qa/BD14-2026-10-09/probe.mjs \
  /home/eslam/Storage/tmp/claude-tmp/oc-paseo-lock-4pyx6dwm/node_modules
```

Result: exit 0; [probe-result.txt](probe-result.txt). This is an offline
feasibility probe, not a Flutter regression or live-server test. No production
code changed, so no failing-first product test, Flutter analyzer or suite was
run. Documentation diff and relative links were checked. No build, emulator,
real daemon, credentials or account data were used.

## Coordinator handoff

[Three-line contract](../../design/BD14-contract.md). To resolve old bare-ID
history safely, a daemon contract must map a reference scoped to its
conversation/profile to retained bytes, or history must preserve a usable
structured file reference. A future reader must enforce a byte cap and supported
image types, return null for unknown/unavailable data, and expose availability
through a capability. A hash alone does not reveal the random directory,
extension, ownership or whether the file survived. Do not guess those from
markdown or enable general file browsing as a workaround.
