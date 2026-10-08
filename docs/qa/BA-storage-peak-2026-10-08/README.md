# Agent install storage peak

Candidate branch `sol/ba-storage-peak`, from `feat/genui-fe f0e33d96d`.
Finish line: the actual installer admission includes catalog-known extracted or
copied payload bytes alongside the retained download. Changing storage readings,
download consent, unknown-size guesses and APK/device work are outside this slice.

The prior install evidence records Codex x64's 109,304,578-byte download and
289,101,384-byte extracted payload: both coexist during extraction, already
398,405,962 bytes before filesystem/scratch overhead. Goose's largest supported
download is 94,681,336 bytes and largest known payload is 298,665,904 bytes.
The old doubled-download/300 MB floor understates both peaks.

New shared admission keeps the old floor and doubled-download bound, and also
requires download plus known payload plus an explicit 64 MiB scratch reserve.
Arithmetic saturates at signed 64-bit maximum. Unknown/nonpositive payload sizes
keep the existing fallback; these bounds do not invent extraction evidence.
The host carries the largest known download and payload across supported CPUs
to the target component so restore needs no extra architecture read. Shared
dependencies and download/consent metadata retain their existing values.

Backend-only; coordinator explicitly holds APK builds and batches device proof
when APK 2197 exists. No emulator, real-account or matrix changes.

Verification (pinned Flutter, each command separately through `machine_lock`):

- [Failing-first host cases](host-negative-control.log): Codex expected
  465,514,826 / actual 300,000,000; Goose expected 460,456,104 / actual 300,000,000.
- [83 focused tests](focused-tests.log): `setup_preflight_test.dart`,
  `setup_engine_test.dart`, `phone_agents_host_test.dart`, concurrency 1, passed.
  Covers exact thresholds, signed-long overflow, unknown-size fallback, metadata
  copying, actual engine specs and target-only host requirements.
- [Scoped analyzer](analyzer.log): seven relevant files, no issues (2.8 s).
- Pinned Dart format, language 3.10, and `git diff --check` passed.

The OMP host expectation rises from 561,073,088 to 628,181,952 because its
downloaded executable and copied payload coexist and now include the shared
64 MiB reserve. This is an intentional admission change, not a new download
size or a device-qualified low-storage result.

Contract: [BA-storage-peak-contract.md](../../design/BA-storage-peak-contract.md).
Ready for coordinator merge; full-suite and device checks remain coordinator
integration gates.
