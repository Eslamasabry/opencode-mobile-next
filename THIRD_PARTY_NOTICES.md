# Third-Party Notices

The local voice-input feature downloads model files only after the user chooses
a model pack. Model files are stored in app-private storage and are not bundled
with the application.

## sherpa-onnx

- Component: `sherpa_onnx` Flutter package and native sherpa-onnx runtime
- Version: 1.13.6
- Project: https://github.com/k2-fsa/sherpa-onnx
- Copyright: sherpa-onnx contributors, including Xiaomi Corporation notices in
  the distributed source
- License: Apache License 2.0; see `LICENSES/Apache-2.0.txt`

sherpa-onnx performs the local Whisper decode and supplies the native libraries
for Android ABIs through its `sherpa_onnx_android_*` packages.

## ONNX Runtime

- Component: ONNX Runtime native inference runtime used by sherpa-onnx
- Version provenance: sherpa-onnx 1.13.6 declares ONNX Runtime 1.27.1 in its
  release changelog for the packaged native runtime
- Project: https://github.com/microsoft/onnxruntime
- Copyright: Microsoft Corporation
- License: MIT; see `LICENSES/MIT-ONNX-Runtime.txt`

## record

- Component: `record` Flutter package
- Version: 6.2.1
- Project: https://github.com/llfbandit/record
- License: BSD 3-Clause; see `LICENSES/BSD-3-Clause-record.txt`

The license file distributed in the 6.2.1 pub package carries the notice
"Copyright 2022 openapi4j authors. All rights reserved." It is reproduced
verbatim in the license file referenced above.

## scrollable_positioned_list

- Component: `scrollable_positioned_list` Flutter package
- Version: 0.3.8
- Project: https://github.com/google/flutter.widgets/tree/master/packages/scrollable_positioned_list
- Copyright: 2018 the Dart project authors, Inc.
- License: BSD 3-Clause; see
  `LICENSES/BSD-3-Clause-scrollable-positioned-list.txt`

This package supplies stable indexed scrolling for long, mixed-height message
timelines.

## OpenAI Whisper and converted ONNX model files

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

## Runtime and model data flow

The `record` plugin supplies PCM16 microphone samples. The application passes
those samples to sherpa-onnx, which executes the converted Whisper ONNX graphs
with ONNX Runtime on CPU. Downloaded model files remain in app-private storage;
captured audio is held only for the active transcription and then discarded.
