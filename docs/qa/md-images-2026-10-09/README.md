# Markdown pictures in replies (2026-10-09)

Owner screenshot of a Claude Code chat: a reply with `![Image](...)` showed a
literal `!` and an underlined link "Image", three lines of "!Image". The in-house
Markdown parser (`lib/ui/kit/chat/kit_markdown.dart`) knew links, not pictures.

Finish line: Markdown pictures never show raw syntax; each is a kit part.
Non-goal: a gallery, editing, downloading, loading web pictures.

## Change
- `lib/ui/kit/chat/kit_markdown_image.dart` (new, part of `kit_markdown.dart`):
  the scanner (`KitMarkdownImage`) and the tiles. Spec: `docs/ux-system/kit-api/KitMarkdown.md` "Pictures".
  - server file (path or `file:`), confirmed by the existing path validation: a
    96 dp thumbnail with its name when the host can read the bytes
    (`KitMarkdownFileLinks.readImage`), else a `KitChip` "Image · name". Tap
    opens the existing file viewer (`open`).
  - http(s): chip "Image · host", never loaded; tap goes through
    `openExternalLink` (confirmation names the host).
  - `data:`, other schemes, unreadable paths: chip with the description, not openable.
  - empty alt or the word "Image": the app's word (`kitMarkdownImage`, en + ar).
  - picture-only lines (blank lines between them too) form one wrapping group;
    a picture inside a sentence is a link-like run; a picture still being
    written on a streaming reply's last line is held back.
  - `proseForSpeech` and table measuring read the alt text, not the target.
- Host: `MarkdownFileLinks.readImage` forwards to the kit; the chat screen
  supplies it (`_readPathImage` -> `_loadPathImage` in `chat/chat_files.dart`,
  the same `fileContent` transport as FC4's sent-photo thumbnails, 8 MiB cap).

## Evidence
- Goldens (DPR 3, phone): `test/goldens/kit/kit_markdown_images_{reply,reply_ar,inline,unread}_{dark,light}.png`
  (the `reply_ar` pair is Arabic, right to left).
- Tests (all green, serial, `OC_TEST_SLOTS=1`):
  `test/kit/kit_markdown_image_test.dart` (24: scanner, tiles, taps, selection copy, decode),
  `test/reply_picture_tiles_test.dart` (the real chat screen: listing, file read, thumbnail, viewer),
  `test/goldens/kit/kit_markdown_images_golden_test.dart` (8, with G5),
  plus the existing `kit_markdown`, `markdown_*`, `desktop_pointer`, `read_aloud`,
  `kit_ratchet`, `file_size_ratchet`, `golden_harness`, `ui_glossary`, `l10n_coverage` tests.
- With the picture rendering switched off, 12 of the 13 widget tests fail (the isolated-preview test still passes: it asserts there is nothing to tap).

## Notes
- The reply's own Copy action stays verbatim Markdown (a reply is the person's to paste); selection inside the reply reads the tile words.
- G5 samples a 14 px plain chip reading just "Image" too faintly in light theme, so the golden's `data:` chip carries a longer description; the empty-alt wording is covered by the unit tests and by the named chips in `_unread`.
- State: implemented, enabled, verified locally, committed on `fe/md-images`; not built into an APK, not pushed.
