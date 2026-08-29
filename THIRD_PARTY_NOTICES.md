# Third-Party Notices

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

This file covers everything the application ships: the fonts and native
runtimes bundled in the package, and every Dart package resolved in
`pubspec.lock`. Full license texts are in [`LICENSES/`](LICENSES/), bundled
with the app and readable in **Settings → About and open source notices**.

The local voice-input feature downloads model files only after the user
chooses a model pack. Model files are stored in app-private storage and are
not bundled with the application.

**How this file is verified.** Every version below is read from
`pubspec.lock`, not from the version constraint in `pubspec.yaml` — the two
differ, and this file previously documented `record` 6.2.1 while the app
shipped 7.1.1. Every license identifier is classified from the `LICENSE`
file inside that exact package version in the local pub cache
(`~/.pub-cache/hosted/pub.dev/<name>-<version>/LICENSE`) by matching the
license text itself, not from a package's self-declared metadata and not
from memory. Where a package's `LICENSE` carries no copyright line, the
table says so with a dash rather than inventing an owner.

## Bundled components

### sherpa-onnx

- Component: `sherpa_onnx` Flutter package and native sherpa-onnx runtime
- Version: 1.13.6
- Project: https://github.com/k2-fsa/sherpa-onnx
- Copyright: sherpa-onnx contributors, including Xiaomi Corporation notices in
  the distributed source
- License: Apache License 2.0; see `LICENSES/Apache-2.0.txt`

sherpa-onnx performs the local Whisper decode and supplies the native libraries
for Android ABIs through its `sherpa_onnx_android_*` packages.

### ONNX Runtime

- Component: ONNX Runtime native inference runtime used by sherpa-onnx
- Version provenance: sherpa-onnx 1.13.6 declares ONNX Runtime 1.27.1 in its
  release changelog for the packaged native runtime
- Project: https://github.com/microsoft/onnxruntime
- Copyright: Microsoft Corporation
- License: MIT; see `LICENSES/MIT-ONNX-Runtime.txt`

### record

- Component: `record` Flutter package and its platform implementations
  (`record_android`, `record_ios`, `record_linux`, `record_macos`,
  `record_web`, `record_windows`, `record_platform_interface`)
- Version: 7.1.1 (`pubspec.lock`; the constraint in `pubspec.yaml` is also
  pinned to 7.1.1)
- Project: https://github.com/llfbandit/record
- License: BSD 3-Clause; see `LICENSES/BSD-3-Clause-record.txt`

The license file distributed in the 7.1.1 pub package carries the notice
"Copyright 2022 openapi4j authors. All rights reserved." — an upstream
artifact of the package's own history, not a claim by this project. That file
is reproduced byte for byte, comment markers included, in the license text
referenced above. The same text ships in every `record_*` platform package.

### scrollable_positioned_list

- Component: `scrollable_positioned_list` Flutter package
- Version: 0.3.8
- Project: https://github.com/google/flutter.widgets/tree/master/packages/scrollable_positioned_list
- Copyright: 2018 the Dart project authors, Inc.
- License: BSD 3-Clause; see
  `LICENSES/BSD-3-Clause-scrollable-positioned-list.txt`

This package supplies stable indexed scrolling for long, mixed-height message
timelines.

### JetBrains Mono

- Component: JetBrains Mono font files bundled under `assets/fonts/`
- Version: distributed TTF builds from the upstream repository
- Project: https://github.com/JetBrains/JetBrainsMono
- Copyright: 2020 The JetBrains Mono Project Authors
- License: SIL Open Font License 1.1; see
  `LICENSES/OFL-1.1-JetBrains-Mono.txt`

These font files render code, terminal output, paths, and other monospace
content throughout the application.

### Flutter SDK

- Component: the Flutter framework and engine, and the SDK-sourced packages
  `flutter`, `flutter_localizations`, `flutter_web_plugins`, `sky_engine`
  (and `flutter_test`, which is not shipped)
- Version: 3.47.1, the revision pinned for Shorebird releases
- Project: https://github.com/flutter/flutter
- Copyright: 2014 The Flutter Authors. All rights reserved.
- License: BSD 3-Clause; see `LICENSES/BSD-3-Clause.txt`. The engine
  additionally bundles its own third-party notices, which Flutter surfaces
  through `showLicensePage`.

### OpenAI Whisper and converted ONNX model files

The downloadable INT8 ONNX files are conversions of multilingual OpenAI
Whisper model weights. OpenAI states that Whisper code and model weights are
released under the MIT License. The OpenAI Whisper MIT text and attribution are
in `LICENSES/MIT-OpenAI-Whisper.txt`.

The application does not modify or redistribute model bytes. The converted
encoder, decoder, and tokenizer files are hosted by `csukuangfj` on Hugging
Face and are fetched from immutable repository revisions:

- `csukuangfj/sherpa-onnx-whisper-small` at
  `8f3c18b358db4d1f2fc1eae49d75cd20989e4309`
- `csukuangfj/sherpa-onnx-whisper-base` at
  `bb53ee204431c90d314c1cc08d28d23e5b7927cc`
- `csukuangfj/sherpa-onnx-whisper-tiny` at
  `65176e2deb88badc814a94058666cadccc29b61c`

The application verifies every downloaded file against the byte length and
SHA-256 digest pinned in `lib/voice/model_manifest.dart`. Neither Hugging Face,
OpenAI, nor the sherpa-onnx project endorses this application.

## Package inventory

Every hosted package resolved in `pubspec.lock`, at the exact version
resolved. "Role" is `runtime` for packages reachable from the application's
own dependency graph and `test-only` for packages reached solely through
`dev_dependencies`; test-only packages are not shipped in the APK or the
desktop bundle and are listed for completeness. Some runtime packages are
platform implementations for iOS, macOS, Windows, or web and are therefore
resolved but not compiled into the Android or Linux builds.

License texts: `LICENSES/MIT.txt`, `LICENSES/BSD-3-Clause.txt`,
`LICENSES/Apache-2.0.txt`, `LICENSES/MPL-2.0.txt`. Per-package copyright
lines are in the table.

`opencode_sdk` is generated from this repository's own OpenAPI contracts,
lives in `packages/opencode_sdk/`, and is covered by this project's
[MIT license](LICENSE) — it is not third-party and is not listed below.

| Package | Version | License | Copyright line in the package's LICENSE | Role |
|---|---|---|---|---|
| `android_file_picker` | 1.0.2 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `args` | 2.7.0 | BSD-3-Clause | Copyright 2013, the Dart project authors | runtime |
| `async` | 2.13.1 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `boolean_selector` | 2.1.2 | BSD-3-Clause | Copyright 2016, the Dart project authors | runtime |
| `characters` | 1.4.1 | BSD-3-Clause | Copyright 2019, the Dart project authors | runtime |
| `clock` | 1.1.2 | Apache-2.0 | — | runtime |
| `code_assets` | 2.0.0 | BSD-3-Clause | Copyright 2025, the Dart project authors | runtime |
| `collection` | 1.19.1 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `convert` | 3.1.2 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `cross_file` | 0.3.5+5 | BSD-3-Clause | Copyright 2013 The Flutter Authors | test-only |
| `crypto` | 3.0.7 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `cupertino_icons` | 1.0.9 | MIT | Copyright (c) 2016 Vladimir Kharlampidi | runtime |
| `dbus` | 0.7.15 | MPL-2.0 | — | runtime |
| `dio` | 5.11.0 | MIT | Copyright (c) 2018 Wen Du (wendux) | runtime |
| `dio_web_adapter` | 2.2.1 | MIT | Copyright (c) 2018 Wen Du (wendux) | runtime |
| `dynamic_color` | 1.9.0 | Apache-2.0 | — | runtime |
| `equatable` | 2.0.7 | MIT | Copyright (c) 2024 Felix Angelov | runtime |
| `fake_async` | 1.3.3 | Apache-2.0 | — | test-only |
| `ffi` | 2.2.0 | BSD-3-Clause | Copyright 2019, the Dart project authors | runtime |
| `ffi_leak_tracker` | 0.1.2 | BSD-3-Clause | Copyright (c) 2026, Halil Durmus | runtime |
| `file` | 7.0.1 | BSD-3-Clause | Copyright 2017, the Dart project authors. All rights reserved | runtime |
| `file_picker` | 12.1.1 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `file_picker_darwin` | 1.0.3 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `file_picker_linux` | 1.0.1 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `file_picker_platform_interface` | 3.1.0 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `file_picker_web` | 3.0.2 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `fixnum` | 1.1.1 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `flutter_lints` | 6.0.0 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | test-only |
| `flutter_riverpod` | 2.6.1 | MIT | Copyright (c) 2020 Remi Rousselet | runtime |
| `flutter_secure_storage` | 10.0.0 | BSD-3-Clause | Copyright 2017 German Saprykin | runtime |
| `flutter_secure_storage_darwin` | 0.2.0 | BSD-3-Clause | Copyright 2025 Julian Steenbakker | runtime |
| `flutter_secure_storage_linux` | 3.0.2 | BSD-3-Clause | Copyright 2017 German Saprykin | runtime |
| `flutter_secure_storage_platform_interface` | 2.0.3 | BSD-3-Clause | Copyright 2017 German Saprykin | runtime |
| `flutter_secure_storage_web` | 2.1.1 | BSD-3-Clause | Copyright 2017 German Saprykin | runtime |
| `flutter_secure_storage_windows` | 4.2.2 | BSD-3-Clause | Copyright 2017 German Saprykin | runtime |
| `highlight` | 0.7.0 | MIT | Copyright (c) 2019 Rongjian Zhang | runtime |
| `hooks` | 2.2.0 | BSD-3-Clause | Copyright 2025, the Dart project authors | runtime |
| `http` | 1.6.0 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `http_parser` | 4.1.2 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `intl` | 0.20.3 | BSD-3-Clause | Copyright 2013, the Dart project authors | runtime |
| `jni` | 1.0.3 | BSD-3-Clause | Copyright 2022, the Dart project authors | runtime |
| `jni_flutter` | 1.0.2 | BSD-3-Clause | Copyright 2026, the Dart project authors | runtime |
| `jni_util` | 1.0.0 | BSD-3-Clause | Copyright 2026, the Dart project authors | runtime |
| `json_annotation` | 4.12.0 | BSD-3-Clause | Copyright 2017, the Dart project authors. All rights reserved | runtime |
| `leak_tracker` | 11.0.2 | BSD-3-Clause | Copyright 2022, the Dart project authors | test-only |
| `leak_tracker_flutter_testing` | 3.0.10 | BSD-3-Clause | Copyright 2022, the Dart project authors | test-only |
| `leak_tracker_testing` | 3.0.2 | BSD-3-Clause | Copyright 2022, the Dart project authors | test-only |
| `lints` | 6.1.0 | BSD-3-Clause | Copyright 2021, the Dart project authors | test-only |
| `logging` | 1.3.0 | BSD-3-Clause | Copyright 2013, the Dart project authors | runtime |
| `matcher` | 0.12.20 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `material_color_utilities` | 0.13.0 | Apache-2.0 | — | runtime |
| `meta` | 1.19.0 | BSD-3-Clause | Copyright 2016, the Dart project authors | runtime |
| `mime` | 2.0.0 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `objective_c` | 9.6.0 | BSD-3-Clause | Copyright 2024, the Dart project authors | runtime |
| `package_config` | 3.0.0 | BSD-3-Clause | Copyright 2019, the Dart project authors | runtime |
| `package_info_plus` | 10.2.1 | BSD-3-Clause | Copyright 2017 The Chromium Authors. All rights reserved | runtime |
| `package_info_plus_platform_interface` | 4.1.0 | BSD-3-Clause | Copyright 2017 The Chromium Authors. All rights reserved | runtime |
| `path` | 1.9.1 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `path_provider` | 2.1.6 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `path_provider_android` | 2.3.1 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `path_provider_foundation` | 2.6.0 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `path_provider_linux` | 2.2.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `path_provider_platform_interface` | 2.1.3 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `path_provider_windows` | 2.3.0 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `petitparser` | 7.0.2 | MIT | Copyright (c) 2006-2024 Lukas Renggli | runtime |
| `platform` | 3.1.6 | BSD-3-Clause | Copyright 2017, the Dart project authors. All rights reserved | runtime |
| `plugin_platform_interface` | 2.1.8 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `pub_semver` | 2.2.0 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `quiver` | 3.2.2 | Apache-2.0 | — | runtime |
| `record` | 7.1.1 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_android` | 2.1.2 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_ios` | 2.1.1 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_linux` | 2.1.1 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_macos` | 2.1.1 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_platform_interface` | 2.1.0 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_use` | 1.1.1 | BSD-3-Clause | Copyright 2024, the Dart project authors | runtime |
| `record_web` | 2.1.2 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `record_windows` | 2.2.3 | BSD-3-Clause | Copyright 2022 openapi4j authors. All rights reserved | runtime |
| `riverpod` | 2.6.1 | MIT | Copyright (c) 2020 Remi Rousselet | runtime |
| `screen_retriever` | 0.2.2 | MIT | Copyright (c) 2022-2024 LiJianying <lijy91@foxmail.com> | runtime |
| `screen_retriever_linux` | 0.2.2 | MIT | Copyright (c) 2022-2024 LiJianying <lijy91@foxmail.com> | runtime |
| `screen_retriever_macos` | 0.2.2 | MIT | Copyright (c) 2022-2024 LiJianying <lijy91@foxmail.com> | runtime |
| `screen_retriever_platform_interface` | 0.2.2 | MIT | Copyright (c) 2022-2024 LiJianying <lijy91@foxmail.com> | runtime |
| `screen_retriever_windows` | 0.2.2 | MIT | Copyright (c) 2022-2024 LiJianying <lijy91@foxmail.com> | runtime |
| `scrollable_positioned_list` | 0.3.8 | BSD-3-Clause | Copyright 2018 the Dart project authors, Inc. All rights reserved | runtime |
| `shared_preferences` | 2.5.5 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `shared_preferences_android` | 2.4.27 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `shared_preferences_foundation` | 2.5.6 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `shared_preferences_linux` | 2.4.1 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `shared_preferences_platform_interface` | 2.4.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `shared_preferences_web` | 2.4.3 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `shared_preferences_windows` | 2.4.1 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `sherpa_onnx` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_android_arm64` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_android_armeabi` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_android_x86` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_android_x86_64` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_ios` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_linux` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_macos` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_web` | 1.13.6 | Apache-2.0 | — | runtime |
| `sherpa_onnx_windows` | 1.13.6 | Apache-2.0 | — | runtime |
| `shorebird_code_push` | 2.0.7 | MIT | — | runtime |
| `source_span` | 1.10.2 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `stack_trace` | 1.12.1 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `state_notifier` | 1.0.0 | MIT | Copyright (c) 2020 Remi Rousselet | runtime |
| `stream_channel` | 2.1.4 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `string_scanner` | 1.4.1 | BSD-3-Clause | Copyright 2014, the Dart project authors | runtime |
| `term_glyph` | 1.2.2 | BSD-3-Clause | Copyright 2017, the Dart project authors | runtime |
| `test_api` | 0.7.12 | BSD-3-Clause | Copyright 2018, the Dart project authors | runtime |
| `typed_data` | 1.4.0 | BSD-3-Clause | Copyright 2015, the Dart project authors | runtime |
| `url_launcher` | 6.3.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `url_launcher_android` | 6.3.32 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `url_launcher_ios` | 6.4.1 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `url_launcher_linux` | 3.2.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `url_launcher_macos` | 3.2.5 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `url_launcher_platform_interface` | 2.3.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `url_launcher_web` | 2.4.3 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `url_launcher_windows` | 3.1.5 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `uuid` | 4.6.0 | MIT | Copyright (c) 2021 Yulian Kuncheff | runtime |
| `vector_math` | 2.4.2 | BSD-3-Clause | Copyright 2013 The Flutter Authors | runtime |
| `vm_service` | 15.3.0 | BSD-3-Clause | Copyright 2015, the Dart project authors | test-only |
| `web` | 1.1.1 | BSD-3-Clause | Copyright 2023, the Dart project authors | runtime |
| `win32` | 6.4.0 | BSD-3-Clause | Copyright (c) 2024, Halil Durmus | runtime |
| `window_manager` | 0.5.2 | MIT | Copyright (c) 2022-present LiJianying <lijy91@foxmail.com> | runtime |
| `windows_file_picker` | 1.0.2 | MIT | Copyright (c) 2018 Miguel Ruivo | runtime |
| `xdg_directories` | 1.1.0 | BSD-3-Clause | Copyright 2013 The Flutter Authors. All rights reserved | runtime |
| `xml` | 7.0.1 | MIT | Copyright (c) 2006-2026 Lukas Renggli | runtime |
| `xterm` | 4.0.0 | MIT | Copyright (c) 2020 xuty | runtime |
| `yaml` | 3.1.3 | MIT | Copyright (c) 2014, the Dart project authors | runtime |
| `zmodem` | 0.0.6 | MIT | Copyright (c) 2023 xuty | runtime |

### Notes on individual entries

- **`dbus` (MPL-2.0)** is reached only through `flutter_secure_storage_linux`
  and is therefore present in the Linux desktop build, not the Android APK.
  It is used unmodified; MPL-2.0 source for it is available from the package
  on pub.dev. Its `LICENSE` is reproduced in full at `LICENSES/MPL-2.0.txt`.
- **`record` and every `record_*` package** ship the openapi4j-attributed
  BSD-3-Clause notice described above.
- **Apache-2.0 and MPL-2.0 packages** distribute the unmodified license text
  with no per-package copyright line, so the copyright column shows a dash.
  Attribution for those packages is the package name and version in this
  table.
- **`shorebird_code_push`** distributes the plain MIT text with no copyright
  line of its own.

## Runtime and model data flow

The `record` plugin supplies PCM16 microphone samples. The application passes
those samples to sherpa-onnx, which executes the converted Whisper ONNX graphs
with ONNX Runtime on CPU. Downloaded model files remain in app-private storage;
captured audio is held only for the active transcription and then discarded.

## Regenerating this file

The inventory is derived from `pubspec.lock` and the pub cache. When
dependencies change, re-derive it rather than editing rows by hand: for each
hosted entry in `pubspec.lock`, read
`~/.pub-cache/hosted/pub.dev/<name>-<version>/LICENSE`, classify the license
from the text, and take the copyright line from that same file. A package
with no `LICENSE` in its published archive must be checked on its pub.dev
page before it is listed.
