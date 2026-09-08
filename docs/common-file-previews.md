# Common file previews

Finish line: open a supported file from Files or a chat attachment/tool output, read it locally, and keep the exact original available for explicit Copy, Save and eligible Attach actions. Nothing is sent by viewing. This branch adds CSV/TSV Table/Source, bounded static SVG Image/Source and Android PDF page viewing to the existing text, code, JSON, Markdown and raster-image previews.

Non-goals: executing formulas or scripts, WebViews, remote conversion, fetching embedded resources, editing documents, PDF links/forms/password entry, new file storage or background intake.

## Format contract

| Format | Local reading | Boundary and fallback |
| --- | --- | --- |
| CSV / TSV | Literal cells, numbered columns and rows, horizontal/vertical scrolling, selectable text, Table/Source | UTF-8; 256 KiB input, 200 displayed rows, 32 columns, 4,096 characters per cell. Quoted separators, doubled quotes and multiline cells are supported. Ragged rows stay ragged; no guessed header or total count. Malformed or oversized input shows Source. The row limit shows an explicit prefix notice. |
| SVG | Static paths, shapes, groups, text and local gradients; pinch zoom; Image/Source | 256 KiB, 1,000 elements, depth 32, attribute length 16,384, finite bounded geometry, dimensions up to 16,384. Only a small allowlist reaches the renderer. Script, animation, external resources, images, use, stylesheets, clip/mask, dashed strokes and foreign content are rejected. Source/Save keep the unchanged original. |
| PDF | Page count, Previous/Next, pinch zoom, explicit Cancel/Retry | Android 10+; 10 MiB input; first 200 pages; one page request at a time; longest edge 1,536 and at most 2 million pixels. Raster output is capped at 10 MiB. Password-protected, malformed, unsupported or out-of-limit files retain Save. No text is reconstructed from page images. |

CSV/TSV recognition uses explicit matching MIME or the extension when MIME is missing/generic text/binary. A contradictory explicit MIME does not become a table. Byte-backed textual input uses strict UTF-8 and rejects NUL: undecodable input retains original bytes and Save, without fabricated replacement text.

Files supplies at most 200,000 display characters plus explicit `truncated` and full `originalText` metadata. The old synthetic `... truncated` suffix is removed. A truncated table or SVG is never interpreted as a complete document. Display bounds avoid splitting a UTF-16 surrogate pair. Code/JSON/Markdown source copying uses the original, including spacing and line endings. Export always prefers original bytes; text-only server results export their original text as UTF-8.

Copy failure offers localized Retry. The existing code reader supplies local wrap, selection and full-screen snapshot controls. All new mode/page controls respect inert preview scope and have at least 48 dp targets. Table direction remains stable for data while surrounding controls follow RTL. Files caches its preview value for unchanged content so unrelated rebuilds do not cancel and restart PDF rendering.

## PDF ownership and cleanup

The app-side `oc/local_pdf` bridge creates two empty private temporary files, opens seekable input and output descriptors, then unlinks the paths before writing any PDF bytes through a descriptor. Only descriptors survive; a crash during initial preparation can leave at most an empty temporary file. File write/read and PNG transport run on an IO executor. The non-exported renderer runs under an isolated UID with no app permissions, document path or URL. It receives input/output descriptors over Messenger; replies carry only bounded page metadata. PNG bytes never travel inside a Binder Bundle.

Each render uses a unique `bindIsolatedService` instance, requiring Android 10 / API 29. This deliberately raises the preview platform floor above PdfRenderer's own API 21 floor: an old request's cleanup must not kill the next request's renderer. [Android's instance contract](https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection)) and [PdfRenderer's ownership/threading contract](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) are the relevant native boundaries.

The renderer worker owns its PdfRenderer, page, bitmap and descriptors. Its main-thread watchdog kills only its own isolated process after ten seconds, including a stuck native parse/render. The client has a twelve-second deadline. Successful completion, failure, cancellation or activity/engine disposal unbinds the owned instance and closes client descriptors. Service death becomes a retryable failure. No PID-pattern kills are used in production or authorized by the proof instructions.

The Dart bridge checks page metadata, PNG signature and actual IHDR dimensions before handing a bounded raster to the image codec. Viewer disposal, byte replacement, scope retirement and app backgrounding retire request identities; late replies cannot install an old page. Background return requires explicit Retry. Files' existing original profile/location/transport guards clear the view and cached content on scope change.

## Dependencies and shipping state

Static SVG uses Flutter's maintained `flutter_svg` 2.3.0 with an explicit XML 7.0.1 dependency for validation. Only `SvgPicture.string` receives accepted local input. No dependency is used to fetch or execute document content. Exact resolved transitive versions and license texts are recorded in `THIRD_PARTY_NOTICES.md` from this worktree's own package restore; there is no package-config copying.

Implementation passed the focused Flutter checks and visual inspection recorded in [verification and native proof](qa/common-file-previews/README.md). Widget mocks prove page controls and stale-result handling; they cannot prove native PdfRenderer, Binder transport, service death or cleanup. The source is ready for a reviewable commit, while real native proof remains a shipping requirement in the coordinator-owned final Android visual/touch/integration gate.
