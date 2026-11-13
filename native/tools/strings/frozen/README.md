# Frozen string sources

Byte-for-byte copies of the Flutter app's string sources, taken on 2026-11-13 so the strings pipeline no longer reads
the Flutter folders (`docs/native/flutter-removal.md`):

- `arb/app_*.arb`: `lib/l10n/app_*.arb` (last changed in commit `aa538316148b`).
- `android-res/values{,-ar}/*.xml`: the string files of `android/app/src/main/res` that `AndroidStrings` reads (the
  notification, widget and schedule copy that became `strings_os`).

They never change. They are the "source" side of the drift check (`Drift.Inputs.forRepo`, `DriftTest` B-L10N-010):
the committed `:ui:words` XML must still match what these convert to, except for keys the translation pipeline owns
(`translation-state/`) or that are listed in `drift-reviewed.tsv`. New copy goes into the XML only.
