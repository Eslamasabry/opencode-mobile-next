# OpenCode 2 fixtures (`:protocol:oc2`)

Owner: S1-OC2 (plan 15). These are the Flutter client's OpenCode 2 wire captures, copied byte for byte, so the native
port is held to the same inputs as the Dart client.

| Path | What | From |
|---|---|---|
| `flutter/api2/*` | The 24 api2 fixtures (23 JSON bodies + `events.sse`, a captured prompt round-trip on beta-18600) | `test/fixtures/api2/` |
| `flutter/coverage/oc2_events_samples.json` | 16 event cases, 88 envelopes (every session event family) | `test/fixtures/coverage/` |
| `flutter/coverage/oc2_messages_samples.json` | 21 message cases (every message type, tool and error shape) | `test/fixtures/coverage/` |
| `MANIFEST.sha256` | SHA-256 of every file under `flutter/` (`sha256sum` format) | generated |
| `parity/dart-variants.json` | What the **Dart** parsers decoded each fixture to (variant tags and fields). Frozen: the Dart generator left with the Flutter app (2026-11-13) | frozen |

## Checks (in `:protocol:oc2` desktopTest)

- `Oc2FixtureManifestTest`: every file matches its manifest hash and nothing is unlisted.
- `Oc2ParityTest`: every fixture decodes in Kotlin to the same descriptor as `parity/dart-variants.json`
  (the Kotlin side is `ParityDescriber.kt`, a line-for-line twin of the Dart generator).
- `Oc2FixtureDecodeTest`: the fixture assertions of `test/api2_models_test.dart` and `test/api2_events_test.dart`.

## Frozen

Never edit the copies or the golden by hand. They are frozen: the Flutter sources and the Dart generator left the
repository on 2026-11-13 (`docs/native/flutter-removal.md`). A new capture (P-N4: OpenCode 2.1.1 / 2.2.5 on the VPS)
goes into a native-only folder with its own manifest entries and no Dart golden.
