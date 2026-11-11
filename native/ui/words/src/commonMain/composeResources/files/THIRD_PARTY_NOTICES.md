# Third-Party Notices

## About these notices

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

This page covers what the native app ships: the libraries in its Gradle build,
the fonts, icons and native runtimes bundled in the package, and the models it
can download. The full list of libraries with their licences, and the licence
texts, are under **About > All licences**.

The local voice-input feature downloads model files only after the user
chooses a model pack. Model files are stored in app-private storage and are
not bundled with the application.

## Libraries from the Gradle build

The app is written in Kotlin and draws its screens with Compose Multiplatform.
Every library below is resolved by the build from the project's version
catalog (`native/gradle/libs.versions.toml`) or from a module's own build
file, at the version shown under All licences. They are used unmodified,
except where stated.

- Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime,
  kotlinx-io and kotlinx.collections.immutable: JetBrains, Apache 2.0.
- Compose Multiplatform (runtime, foundation, UI, animation, Material 3,
  resources, adaptive navigation), AndroidX Navigation 3, Lifecycle,
  SavedState and NavigationEvent: JetBrains and Google, Apache 2.0.
- AndroidX Core, Activity, Fragment, ProfileInstaller, WorkManager and
  CameraX: Google, Apache 2.0.
- Metro (dependency injection): Zac Sweers, Apache 2.0.
- Ktor client and OkHttp with Okio: JetBrains and Square, Apache 2.0.
- Coil (image loading, SVG bytes only): Coil contributors, Apache 2.0.
- AndroidSVG 1.4: Paul LeBeau, Cave Rock Software Ltd., Apache 2.0. It
  receives static SVG text only and has no external file resolver.
- backdrop 2.0.1 (liquid glass): Kyant, Apache 2.0. A vendored copy lives in
  `native/third_party/backdrop` with its licence and a list of the two marked
  changes. `io.github.kyant0:shapes` 1.2.1 is its dependency.
- SyntaxMP tokenizer 0.3.0 (code colouring): Apache 2.0.
- Apache Commons Compress 1.27.1 (unpacking the built-in Linux archive):
  The Apache Software Foundation, Apache 2.0.
- zxing-cpp 3.1.1 (reading the pairing QR code): Apache 2.0. The camera is
  opened only by the pairing scanner, only while that screen is on top, and no
  frame is stored. No Google ML Kit is used.
- Firebase Crashlytics, Analytics and Performance Monitoring (Firebase BoM
  35.1.0): Google, provided under Google's terms for Firebase. They send data
  only while the matching switch in Settings > Privacy and data is on; see the
  Privacy Policy.

## Bundled components

### Phone project engine

The ARM64 phone engine, confinement launcher and isolated boundary probe are
built from `engine/phone/` and its committed `Cargo.lock`. The packaged Android
asset `aiteam-engine-manifest.json` records executable SHA-256 hashes and an exact
Rust source digest; native launch verifies the executable hashes. Rebuild with
`engine/phone/tool/build-android.sh --stage-android` using the pinned NDK.

The complete locked Android normal-dependency notices, including Rust standard
library attribution, libgit2's GPL v2 text with linking exception and vendored
notices, and SQLite's public-domain dedication, are published with the source in
`LICENSES/Rust-Phone-Engine.txt` of the project repository. The generator
reads the exact local Cargo package sources; these are separate from the library list under All licences. Execution is gated independently of packaging.

### Phosphor icon artwork

The regular, duotone and fill icon fonts are copied unchanged from the official
`phosphor_flutter` 2.1.0 archive, licensed under MIT, Copyright (c) 2020-2021
Phosphor Icons. The licence is kept in `native/third_party/phosphor`, with the
archive and file hashes. The package itself is not a dependency.

### Agent and provider marks

The single-colour logos in `androidApp/src/main/assets/marks/` (agents such as Claude, Codex,
Gemini and Antigravity, and about 100 model-provider ids such as Anthropic, OpenAI and Mistral) are the
monochrome SVGs of lobe-icons by LobeHub, MIT licensed, Copyright (c) 2023
LobeHub. The licence is MIT (see All licences); each file is
unchanged apart from removing its `<title>` element. (`opencode.svg` once also
carried the lighter lower block of OpenCode's two-tone mark at 40% of the same
colour; at 16 to 24 dp that block filled the opening and the mark read as a
solid box, so it is back to the plain single-colour frame.) The app draws them in the
current text colour and the logos remain trademarks of their owners; they are
shown only to name the agent or provider. A provider without a bundled mark
still falls back to its models.dev logo, downloaded by provider name.

Five agent brands also ship a full-colour file, `<slug>-color.svg` in the same folder (Claude, Gemini, Google for
Antigravity, Qwen and Copilot): the colour SVGs of the same lobe-icons set
(`@lobehub/icons-static-svg` 1.102.0, MIT, Copyright (c) 2023 LobeHub), unchanged apart from
removing the `<title>` and the inline `style`, and fixing the size at 24. The app draws them in their own colours,
without a tint; OpenCode, Codex/OpenAI, Kilo Code, Cline, Goose and Pi have no colour file (or, like OpenCode and
Codex, are single-colour by design, and Codex's colour file sits on a white plate) and stay in the text colour.

### sherpa-onnx

- Component: sherpa-onnx native runtime (Maven `com.k2fsa.sherpa.onnx:sherpa-onnx-android`, kept in `native/third_party/maven`)
- Version: 2.0.1
- Project: https://github.com/k2-fsa/sherpa-onnx
- Copyright: sherpa-onnx contributors, including Xiaomi Corporation notices in
  the distributed source
- License: Apache License 2.0; Apache 2.0 text under All licences

sherpa-onnx performs the local Whisper decode and supplies the native libraries
for Android ABIs.

### ONNX Runtime

- Component: ONNX Runtime native inference runtime used by sherpa-onnx
- Version provenance: sherpa-onnx 2.0.1 packages ONNX Runtime 1.28.2 (the
  libonnxruntime.so in its Android packages reports that version; the 1.13.8
  release changelog records the update from 1.27.1)
- Project: https://github.com/microsoft/onnxruntime
- Copyright: Microsoft Corporation
- License: MIT; MIT text in the voice licence pages under About

### Termux terminal-emulator (the local terminal's PTY)

- Component: `terminal-emulator` and `terminal-view` libraries of Termux, used
  as the published artifacts `com.github.termux.termux-app:terminal-emulator` and `terminal-view`
- Version: v0.118.3 (JitPack build of that release tag)
- Project: https://github.com/termux/termux-app/tree/v0.118.3/terminal-emulator
- Copyright: Fredrik Fornwall and the Termux contributors; derived from Jack
  Palevich's Android Terminal Emulator
- License: Apache License 2.0; Apache 2.0 text under All licences

The terminal on "This phone" starts its shell on a pseudoterminal through
this library's native `libtermux.so` (`termux.c`). The
termux-app repository as a whole is GPLv3; its `LICENSE.md` names
`terminal-emulator` and `terminal-view` as the Apache 2.0 exceptions. No
other Termux code (the app, its extra-keys view, `termux-shared`) is used:
the key bar and the screen are this app's own. The upstream library ships
no NOTICE file of its own.

### Geist and Geist Mono

- Component: Geist and Geist Mono variable font files bundled in the app's resources
- Version: v1.7.2, the `variable/` TTFs of the official release archive
  https://github.com/vercel/geist-font/releases/download/v1.7.2/geist-font-v1.7.2.zip
- Project: https://github.com/vercel/geist-font
- Copyright: 2024 The Geist Project Authors
- License: SIL Open Font License 1.1; the full text is under All licences

Geist renders every word of the interface; Geist Mono renders code,
terminal output, commands, paths and other monospace content.

### OpenAI Whisper and converted ONNX model files

The downloadable INT8 ONNX files are conversions of multilingual OpenAI
Whisper model weights. OpenAI states that Whisper code and model weights are
released under the MIT License. The OpenAI Whisper MIT text and attribution are
in the voice licence pages under About.

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
SHA-256 digest pinned in the app. Neither Hugging Face,
OpenAI, nor the sherpa-onnx project endorses this application.

### Kokoro natural reading voice (optional download)

The optional "Natural voice" for read-aloud downloads Kokoro-82M v1.0
(hexgrad, Apache-2.0), packaged for sherpa-onnx as an int8 ONNX model with its
voices, tokens and an English pronunciation lexicon derived from the misaki
dictionaries (Apache-2.0). The notice and license text are in the voice licence pages under About. The application does not modify or
redistribute the bytes; they are fetched from the immutable revision
`csukuangfj/kokoro-int8-multi-lang-v1_0` at
`57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2` and verified against the byte length
and SHA-256 digest pinned in the app. Arabic is not
offered: sherpa-onnx 2.0 removed espeak-ng, which the published Arabic
Kokoro-runtime models (Nabra-82M, Apache-2.0) need for pronunciation.

## Runtime and model data flow

The app captures PCM16 microphone samples with Android's AudioRecord and passes
them to sherpa-onnx, which executes the converted Whisper ONNX graphs with ONNX
Runtime on CPU. Downloaded model files remain in app-private storage; captured
audio is held only for the active transcription and then discarded.
