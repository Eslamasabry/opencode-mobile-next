# OpenCode SDK contract provenance

`opencode-openapi-f12e14cf.json` is an unmodified snapshot of
`packages/sdk/openapi.json` from
[`anomalyco/opencode` commit `f12e14cf1640cbf0dfb6b1ff425b2daaef459eec`](https://github.com/anomalyco/opencode/tree/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec).
Its SHA-256 is
`00502bd13e9c86f3ca9e765e99a57e06fa9f434ca16f2a714766d1444f8d37f3`.

The upstream repository at that commit is licensed under the
[MIT License](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/LICENSE)
(Copyright 2025 opencode). The SDK is generated with OpenAPI Generator 7.19.0,
which is licensed under the
[Apache License 2.0](https://github.com/OpenAPITools/openapi-generator/blob/v7.19.0/LICENSE).

## Deterministic generation

The reference validation toolchain is Dart 3.13.2 (Flutter 3.47.2), OpenJDK 17, and OpenAPI
Generator 7.19.0. The generator JAR is pinned by SHA-256 in
`opencode-sdk-manifest.json`, and transitive Dart package versions and content
hashes are pinned by `packages/opencode_sdk/pubspec.lock`. Generation reuses
that lockfile with `dart pub get --enforce-lockfile`; it does not write detected
host versions into generated artifacts.

Run `tool/sdk/generate.sh` from any directory. Set `OPENCODE_SDK_OFFLINE=1` to
forbid generator downloads and use only an already verified cached JAR and the
local Dart package cache.
