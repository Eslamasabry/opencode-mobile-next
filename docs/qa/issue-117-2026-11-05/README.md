# Issue #117: curl crashes during built-in Linux setup (2026-11-05)

## Cause, as known
On a Poco M4 Pro with a custom Android 17 ROM, the Ubuntu 24.04 `curl` run under
proot 5.1.107.94 dies with SIGSEGV (exit 139), even for the pinned Node.js archive.
apt/dpkg work on the same phone. The setup script (`oc_download`) did not retry exit
codes of 128 or more, so setup stopped at "Downloading Node.js". Why curl crashes there
is not established; the fix does not depend on it.

## Fix
`oc_download` (lib/builtin/setup/setup_scripts.dart) treats exit >= 128 from curl (also
on the size probe, which is then not repeated) as a crashed tool and tries, in order:
1. The app downloads the file with Android's own networking.
2. `curl --http1.1`.
3. `wget`, if present.
The pinned SHA-256 check always runs afterwards; a mismatch still fails and deletes the file.
If everything fails, Details says "the download tool crashed (signal N)" (plain words).

### Handshake (app download)
- Script prints `::oc fetch <url> <absolute guest path> <sha256>`.
- `SetupRunner` (SetupJob.kt parses the line; SetupAppFetch.kt does the work) downloads
  into the rootfs path: https only, path must stay inside the rootfs, Range-resumable of a
  partial file, 4 attempts, own checksum check, `::oc bytes` progress as usual.
- Answers via marker files next to the file: `<file>.oc-fetch-ack` at once, then
  `<file>.oc-fetch-done` containing `ok` or `fail <plain reason>`.
- A runner that does not know the request never writes the ack; the script gives up
  after `OC_ACK_POLLS` polls (10 s) and moves to curl --http1.1 / wget.

## Tests
- test/setup_scripts_test.dart, group "when curl crashes with a signal" (5 tests, curl
  fake exits 139): app download succeeds and checksum still checked; wrong file from the
  app still fails; app failure falls to `--http1.1`; no app falls to `--http1.1`; all ways
  crash gives the plain-words message and exit 139. Whole file: 21 passed.
- Kotlin SetupAppFetchTest (6 tests: parse, download, Range resume, bad checksum, insecure
  url / path escape, cancel) via `:app:testReleaseUnitTest`: passed.
- test/termux_script_hashes_test.dart: six aiteamVerbScript hashes refreshed (they embed
  the prelude). The setup/agent install test files all pass.
- `flutter analyze lib test/setup_scripts_test.dart`: clean (widgetbook/ has unrelated errors).

## Emulator proof (OC_API37, Android 17, build 4520, local signer 1DE5BF08...)
Rootfs `/usr/bin/curl` replaced by `kill -SEGV $$` (a real SIGSEGV), `/opt/node` removed,
then Update OpenCode run from the app.
- 01: setup running, "Unpacking Node.js" (the download had already been done by the app).
- 02/03: all five steps done, Node.js 24.21.0; Details output reads
  `==> Downloading Node.js 24.21.0`, `[oc] The download tool crashed (signal 11). Trying another way.`,
  `==> The app is downloading this itself`, `==> Unpacking Node.js`.
The real curl was restored afterwards.

## Open
- Not run on the owner's Poco M4 Pro; proot-specific crash was simulated, not reproduced.
- `wget` fallback is untested on a device (the app path comes first).
