# BD7 — opt-in private crash diagnostics backend

Finish line: explicit default-OFF consent controls private persisted global
Flutter/native errors and next-launch ANR evidence; records exclude provider
keys before storage and are imported into App diagnostics after restart.
Non-goal: frontend consent screens, live-device certification, signing/release,
uploads, arbitrary exception messages or stack-trace collection.

## Implemented

- `lib/diagnostics/crash_diagnostics.dart`: default-OFF consent, bounded flushed
  atomic ring, fixed error categories, startup import, clear/disable deletion,
  stale ANR filtering, idempotent enable and no-follow file targets.
- `lib/diagnostics/app_diagnostics.dart`: global hooks use class/category-only
  capture and safe previous-handler delegation. No exception value or stack
  string is evaluated by the crash path.
- `lib/main.dart`: opens optional crash storage and installs its guarded callback.
- `NativeCrashStore.kt`: gates the existing application-wide crash handler using
  the same consent file, fixed categories only, deletion while disabled and
  sanitized prior fatal-handler delegation. Final consent epoch recheck narrows
  the native concurrent-disable race; the residual is documented, not called a
  guaranteed cross-runtime lock.
- `CrashDiagnosticsBridge.kt` and `MainActivity.kt`: private startup bridge,
  main-process-only OS ANR timestamp import on API 30+, channel disposal.
- Frontend contract: `docs/design/bd7-contract.md`.
- Privacy note: `docs/privacy/bd7-crash-diagnostics.md`.

## Focused local verification

Pinned toolchain:

```sh
FLUTTER=/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 \
  test/crash_diagnostics_test.dart test/app_diagnostics_test.dart
```

Final snapshot: **18 tests passed** (13 crash tests + 5 affected diagnostic tests)
in 14 seconds. This includes idempotent enable and symlink rejection.
Coordinator's full pinned `flutter analyze --no-pub` checkpoint passed with
**no issues** in 12.3 seconds under the shared machine lock.

Coverage includes default-OFF and old-native-file cleanup; unregistered short
provider keys and pattern-shaped synthetic keys excluded from disk; hostile
`error.toString()` never evaluated; restart import; disable/clear deletion;
timestamp-only ANR/native imports and deduplication; bounded ring/bytes; corrupt
records; storage failures; symlink redirection; and safe global hook delegation.
No real credentials were read, registered or logged in these tests.

The provider-key test was executed against a temporary copy of the backend with
the pre-storage category projection reverted to raw exception serialization.
The exact provider-value exclusion test failed (exit 1, expectation failure).
Production source was verified unchanged, and the temporary mutant was removed.
Only synthetic fixture values were used; mutation output was not dumped.

Pure JVM command (run with a temporary jar/directory, removed afterward):

```sh
kotlinc android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeCrashStore.kt \
  test/native_crash_store_harness.kt -include-runtime -d /tmp/bd7-native-test.jar
java -jar /tmp/bd7-native-test.jar /tmp/bd7-private-store
```

The JVM harness passed default-OFF, private category capture, size bound,
native-file clear, disable behavior and sanitized fatal-handler delegation.
The actual runs used `TemporaryDirectory`, so no jar or private fixture remains.
The final native snapshot was rerun successfully after the consent-epoch check
was added. A temporary native mutant that wrote the raw throwable message
instead of the fixed category failed the harness with
`Private exception value reached disk`. The production native source remained
unchanged; its temporary jars, records and mutant were removed automatically.

The new native bridge compiled successfully (exit 0) directly against Android API 37
(`platforms/android-37.0/android.jar`) and the pinned release-engine Flutter jar.
This checks the helper’s Android/MethodChannel references; it does not establish
a complete APK build, MainActivity lifecycle behavior or on-device delivery.

## Outstanding qualification

No release build, signing, install, device crash, device ANR, screenshot,
publication, push or CI run was performed by this slice. The coordinator owns
the frontend and overall analyzer/detekt checkpoint. Complete opt-in consent UI
and Android process-death/restart/ANR checks remain outstanding.

Native ANR import is OS exit evidence only, not a live watchdog. A native fatal
crash concurrent with the final consent recheck/rename can leave a safe
categorical file after disable; the next disabled read/startup removes it and
never imports it. Failed storage cleanup is an unavailable state, not a proved
deletion. Those limits are explicit in the frontend/privacy contract.
