# What's new sheet: update the table each release

After an update the app opens a "What's new in X" sheet once (never on a
fresh install). Its lines come from `whatsNewByVersion` in
`lib/state/whats_new.dart`.

Each release, before tagging:

1. Add an entry keyed by the release version (`'2.3.0'`), three to five
   plain, short lines about what the person can now do.
2. Add the strings to `lib/l10n/app_en.arb` and `app_ar.arb`
   (`whatsNew<version>…` keys) and run `flutter gen-l10n`.
3. A version with no entry opens nothing after the update; About › What's
   new then shows the newest entry.

## What to connect at merge

Every "Show me" target is one line in `lib/ui/screens/whats_new_places.dart`
(`WhatsNewPlaces`).

- **Updates** (`updatesAnchor`, `updatesPage`): connected. Show me opens
  Settings › This app › Updates and highlights its Check now row
  (`updates-check-now`); Android only, like the page.
- **Paste images** (`pasteImageAnchor`): connected to the message box
  (`kit-composer-text`), which holds both the long-press paste menu and +.
  Needs `openChat`.
- **Hold to talk** (`holdToTalkAnchor`): the mic, `kit-composer-mic`.
  Needs `openChat`.
- **`openChat`** (not wired yet): both composer steps need a chat to highlight in. Assign
  `WhatsNewPlaces.openChat` (in `lib/main.dart`, where a chat can be
  opened) to a function that opens the latest or a new chat and returns a
  context under its message box. While it is null those two steps have no
  "Show me".
- **Agents** points at Settings › Agents (`agents-hub-list`); nothing to do.
