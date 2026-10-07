# FC4 — sent photos show as thumbnails (2026-10-07)

Finish line: a photo sent with a prompt shows under the prompt as a picture
(tap opens the full view), not as its file name, whatever form the server
hands it back in. Non-goal: pictures a server never returns (an agent on
this phone keeps the text only; after a restart its history has no photos to
show).

## Change
- Kit: `KitMessage.prompt` draws an attachment with `kind: image` and a
  `thumbnail` as a 96 dp square `KitImage` (`KitLayout.promptPhotoSize`,
  cover, tile corners) in a `Wrap`, its own button node "Preview <name>";
  other files stay read-only chips. Spec: `docs/ux-system/kit-api/KitMessage.md`.
- Chat (`chat/message_view.dart`, new `chat/sent_photos.dart`): a sent
  file part becomes a thumbnail when it carries
  - a `data:` URI with a drawable picture type (OpenCode 1; OpenCode 2's
    re-encoded copy, whose type may be only in the URI; the app's own copy
    kept for an agent on this phone): bytes decoded once per part;
  - a `file:///…` URI (OpenCode 2 keeps the file): read through the app's
    own file transport (`fileContent`), cached per server and path; tap
    opens the loading preview instead of "Remote attachment previews are
    not available".
  A web address is never fetched (security rule): it stays a named chip, as
  do SVG pictures and other files.

## Evidence
`tool/capture/fc_chat_2026_10_07_test.dart` (`FC_ONLY=FC4`), sample photos
`sample-photo-1.jpg`, `sample-photo-2.jpg` (generated).
`contact-sheet.jpg`: before (names) · after (thumbnails, the PDF stays a
chip). Full screens: `before-sent-photos.jpg`, `after-sent-photos.jpg`.

## Tests
- `test/sent_photo_thumbnails_test.dart` (4): inline photo → thumbnail that
  opens the preview; re-encoded photo with the type only in the URI; a
  server-kept `file://` photo read through the app and opened; PDF and web
  picture stay named chips. With `message_view.dart` reverted, 3 of 4 fail.
- `test/kit/kit_message_test.dart` 3b: thumbnail size, no name, semantics
  "Preview screenshot.png", tap opens.
