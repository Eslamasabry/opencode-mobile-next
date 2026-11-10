# Local integration validation — 2026-10-14

Candidate: `codex/aiteam-oc2`, based on `c4884bc05`, with the implementation in
this commit. No push, Flutter run, APK, emulator, signing or release occurred.

## Rust

From `engine/phone`:

```sh
OC_PHONE_PROOF_ROOT=/home/owner/Storage/Code/oc_app-codex-aiteam-oc2/build/qa/aiteam-oc2-live/proof-root \
OC_ENGINE_TEST_ROOT=/home/owner/Storage/Code/oc_app-codex-aiteam-oc2/build/qa/aiteam-oc2-live/engine-test-root \
../../tool/qa/machine_lock.sh test -- cargo test -j 2
```

Exit 0: **240 passed, 0 failed, 1 ignored**, across the library, integration
binaries and doc tests. The existing ignored test is
`raw_syscalls_proc_aliases_metadata_and_descendants_cannot_reach_private_state`:
it requires an explicit Landlock ABI6 proof run. This result is not Android
filesystem-confinement qualification.

The first run compiled but failed two daemon model fixtures because protocol
discovery preceded their directory-scoped model endpoints. Fixtures now answer
`/api/info` with 404 and pinned `/global/health`, retaining the original model
assertions. Their focused rerun passed (2 tests). The facade's focused rerun
passed (4 tests), including no-network explicit-model and invalid-input checks.
A subsequent full run passed all 93 library tests but stopped at two existing
boundary tests because `OC_PHONE_PROOF_ROOT` was unset. Setting the isolated
artifact directories above made the focused boundary run pass (2 passed,
1 ignored); the final complete run then passed without further source changes.

Rust source fingerprint (SHA-256 of concatenated sorted relative `*.rs` paths
and bytes under `engine/phone`, excluding `target`):
`45877f62f66ea1dd2f3768e18428770957fb8d691fb6a6cab1613a941f282ee5`.
Full local logs are ignored artifacts under `build/qa/aiteam-oc2-live/`.

The relocated OC1 implementation equals its pre-change body byte-for-byte after
removing the added `pub(crate)` visibility modifiers. `git diff --check` passed.
No UI, Android, pubspec, generated SDK or dependency-lock files changed.

## Dart and device checks

Changed Dart files were formatted with the pinned standalone Dart tool.
Flutter tests and analysis were **not run**, per the job constraint. Exact test
files and the remaining emulator journey are listed in the
[integration contract](../../design/aiteam-oc2-contract.md#validation-and-release-boundary).

The live 2.0.29 wire evidence is in [README.md](README.md),
[provider-results.json](provider-results.json) and
[cancel-results.json](cancel-results.json). Only sanitized fixtures were retained;
credential-containing scratch roots were deleted after exact-PID shutdown.
OpenCode 2.0.28 is not enabled or qualified by this result. This native engine
change requires a new Shorebird release, not a Dart patch.

## Install pin follow-up — OpenCode 2.0.29

The app's Termux runtime pin, both manager fallback versions, native in-app
archive pins, and agent catalog now select **2.0.29**, matching the phone engine's
accepted version. Native install expectations and the catalog inventory fixture
were updated. OC1 pins and historical 2.0.28 probe evidence are unchanged.

Downloaded the three packages referenced by the installers into the ignored
worktree scratch directory
`build/qa/aiteam-oc2-pin/downloads-3itqnmif/`. No npm installation or package-file
write outside the worktree occurred. For each package, fetched the exact-version
registry metadata, downloaded `dist.tarball`, computed SHA-512 over the complete
archive, and required its Base64 encoding to equal `dist.integrity`. All three
matched. Computed SHA-256 from those same bytes; checked each archive's
`package/package.json` name/version and the platform binary member
`package/bin/opencode` without extracting or executing it.

| Package (exact registry metadata) | Bytes | SHA-256 | SHA-512 integrity |
|---|---:|---|---|
| [@opencode/cli](https://registry.npmjs.org/@opencode/cli/2.0.29) | 2956 | `08451aeeca503b0ceb1b45a0b8db8227ebe0e5b12017c4a7d37b7f45040cfce1` | `sha512-1ZmbylUtiRvtN8YMLxBEN4u0tqhfKI0AQBPvgdCLYLbB8BS80e6cFnOhzHH6L663o4RVL5bMa/VleLh1hNX+NA==` (matched) |
| [@opencode/cli-linux-arm64](https://registry.npmjs.org/@opencode/cli-linux-arm64/2.0.29) | 96807431 | `0af39e75dbdfacc20f8f531ef35fb4dcc0b44c08084203518614272f232283ae` | `sha512-EsCnvp5lMJNem0YoQJM9PkXY0KrkiugL55M9J2JlImQzKdQnZ4CKutrPAXOnsa889qHhY5tjRpAnD/ZYFzhkRA==` (matched) |
| [@opencode/cli-linux-x64-baseline](https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/2.0.29) | 98038640 | `152c42ff57b15728f39e1d7b68a9c13b0635fe1c5e6dcccdc8b710955e4cef0b` | `sha512-YvDKgdRVZzBoedL22zNQyOrI38n9bElwdCNqYiZ1CLfE7CS6Ni4TulGi3RZgZe+KwJfhoiEjwNuZ0E55raFqGA==` (matched) |

The two platform SHA-256 values above are identical in the native setup pins,
agent catalog, and catalog inventory fixture. The CLI wrapper is installed by
npm, which uses registry integrity; the app does not store a separate wrapper
SHA-256 pin. Scratch verification output is in
`build/qa/aiteam-oc2-pin/download-verification.json`.

A final search of `lib/` and `test/` found no remaining 2.0.28 install pin. Remaining
references are the historical server-screen scenes and their matching assertion,
and a chat-feed comment describing an actual 2.0.28 probe. They do not select an
install version. `git diff --check` passed. Flutter was not run.

### Required maintainer Flutter checks

The generated `test/fixtures/termux_script_hashes.json` remains unchanged and
**must be regenerated before the ordinary hash test can pass**. Run from this
worktree with the pinned Flutter and machine lock:

```sh
WRITE_SCRIPT_HASHES=1 tool/qa/machine_lock.sh test -- \
  ~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency=1 test/termux_script_hashes_test.dart
```

Review and commit the regenerated fixture, then run these affected files
without `WRITE_SCRIPT_HASHES` (serially, with the same pinned Flutter and lock):

```text
test/termux_script_hashes_test.dart
test/termux_scripts_test.dart
test/termux_runtime_switch_test.dart
test/opencode_native_install_test.dart
test/setup_engine_test.dart
test/builtin_linux_test.dart
test/agent_catalog_test.dart
test/coverage/phone_agent_catalog_coverage_test.dart
test/this_phone_plain_failures_test.dart
test/revamp/slice_close_servers_phone_golden_test.dart
```

The last golden uses the current pin in its rendered installed-version state;
inspect any expected version-text change before updating its baseline. The
eight unrun Dart integration test files in the
[AI Team contract](../../design/aiteam-oc2-contract.md#validation-and-release-boundary)
also remain required. No Rust code changed in this follow-up, so the preceding
240-pass Rust result still applies to the engine sources.
