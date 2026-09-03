# OpenCode SDK contract provenance

`opencode-openapi-03bba464.json` is an unmodified snapshot of
`packages/sdk/openapi.json` from
[`anomalyco/opencode` commit `03bba464d46f3eddf74195919b1344aa937f7b11`](https://github.com/anomalyco/opencode/tree/03bba464d46f3eddf74195919b1344aa937f7b11).
Its SHA-256 is
`5bbd6493a1a488ef4294889341c896e420f814ecea95822100aaa9f3f95ab2d1`.

The upstream repository at that commit is licensed under the
[MIT License](https://github.com/anomalyco/opencode/blob/03bba464d46f3eddf74195919b1344aa937f7b11/LICENSE)
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
