# Local build preparation — 2026-09-07

Historical preparation record. The later combined candidate and its current
verification status are recorded in [consolidation](consolidation-2026-09-07.md).

Candidate: uncommitted `dev` working tree based on `84e1e0f`. No commit, push,
tag, signing-secret access, artifact upload or public release was performed.
All integration work in this cleanup was performed by the lead, without agents.

## Source cleanup

- Generated the missing auth-recovery, connection-help and usage-inspection
  localization APIs; externalized newly introduced literals caught by the
  localization ratchet, without raising its baseline.
- Wired Server attention from Servers through the existing explicit connection
  flow. Wired reviewed web sources into composer text without fetching pages,
  creating remote file references or automatically sending a prompt. Added
  synchronous profile-deletion invalidation to source review.
- Corrected async mounted checks and lint findings in merged source. Preserved
  package versions and the lockfile.
- Updated stash widget fixtures for stable saved-profile identity, isolated
  in-memory file vaults, metadata-only attachments and source retention for
  review references. Bounded transition pumps replace global settlement while
  the modal operation intentionally leaves a busy indicator active.

## Checks actually completed

- Full `flutter analyze --no-pub`: **no issues** after cleanup.
- `git diff --check`: passed. `pubspec.yaml` and `pubspec.lock` unchanged.
- `stash_attachments_test.dart` plus `stash_lifecycle_test.dart`: **54 passed**.
- Localization check: initial ratchet failure fixed by externalization;
  `l10n_coverage_test.dart`: **2 passed** on rerun.
- Six-file affected batch (prompt shelf, platform capabilities, iOS remote
  gating, usage statistics, voice platform, voice controller): **54 passed,
  3 failed**. The three stash widget failures were corrected and passed an
  exact-name filtered rerun (**3 passed**). No claim of a second full batch run.
- No full serial-suite result exists for this candidate. Prior pre-E5 suite
  coverage does not validate later auth/voice/integration changes. Stable
  candidate full-suite, native compilation and device checks remain gates.

Toolchain: upstream Flutter 3.47.2 at `/tmp/opencode/flutter` (not a verified
Shorebird fork), Dart 3.13.2, ARM64 Ubuntu proot. No emulator/device journey was
performed. Flutter doctor did invoke adb discovery and started its daemon;
no device was attached or controlled, and no subsequent device operation ran.

## Artifact attempts and blockers

1. `flutter build apk --release --no-pub` initially failed because the selected
   Java 21 installation lacked the Java compiler.
2. Retried with process-local `JAVA_HOME`/PATH pointing to installed JDK 17.
   Gradle progressed but failed starting its AAPT2 resource compiler daemon.
   The SDK's `build-tools/36.0.0/aapt2 version` independently terminated with
   **Illegal instruction** on this host. This is not Kotlin/Dart compile proof.
3. `flutter build linux --release --no-pub` stopped at **CMake is required**.
   The environment also lacks ninja/GTK development requirements; its available
   compiler is from the surrounding Termux environment.
4. Windows packaging requires the repository's Windows runner; this Linux
   host cannot produce the Windows Flutter application.

**No new APK, Linux bundle or Windows ZIP was produced.** No old artifact or
invented download link was substituted. Release signing configuration was
absent and no production secrets were read. This native/asset-changing batch
is not an eligible Dart-only Shorebird patch.

Next delivery decision: obtain explicit permission to commit/push reviewed
source to `dev` and use the repository's test-signing CI workflow. CI artifacts
remain test builds, not a production Shorebird baseline. Complete the remaining
source/native gates and report actual run/artifact URLs only after they exist.
