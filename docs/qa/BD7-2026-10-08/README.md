# BD7 — real device crash and ANR qualification

Status: BLOCKED — coordinator APK 2197 has not been posted; host harness ready.

Finish line: real Android app-process fatal crash and OS-classified not-responding
exit appear in Recent app exits and the enabled private crash-report flow after
restart, with the normal app restored inside the emulator lock.

Branch `sol/bd-bd7` starts at `feat/genui-fe` `f0e33d96d`. Native/UI behavior
remains owned by the merged product. No UI, provider, account or conversation
changes are planned. APK2197 will be provided by the coordinator; no replacement
build or device proof is claimed during preparation.

The existing [consent contract](../../design/bd7-contract.md) and
[privacy policy](../../privacy/bd7-crash-diagnostics.md) govern collection.
The current restore designation is normal2196, installed with `-r -d` before
releasing `/home/eslam/Storage/tmp/oc-emulator.lock`.

## Prepared proof

The [driver](../../../tool/qa/bd7_device_crash_smoke.py) preflights the provided
2197 package/version/signer before taking the shared lock. It uses the production
UI: Settings → Report a problem → Crash reports. Turning on consent is a real
switch tap. Original diagnostic artifacts are backed up privately on-device;
no profile, account, provider or conversation files enter that backup. A rollback
copies back the original files/absence while the app is stopped, then the normal
APK is installed with `-r -d` and launched before releasing the lock.

The native Android case uses an actual uncaught JVM fatal error (`am crash` on
the verified exact main PID), exercising the merged native exception handler.
This is Java/Kotlin Android-layer proof, not native C/SIGABRT capture. Android's
[shell implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ActivityManagerShellCommand.java)
provides that crash request. The not-responding case suspends only the verified
main PID, injects a harmless tap in diagnostics and waits for Android's own
ANR dialog. It closes the app through that dialog and requires a real OS ANR
exit; force-stop or SIGKILL cannot qualify as ANR. A finally path resumes only
that same PID/start identity if it still exists.

The driver requires matching main-PID exit reasons, newly captured category-only
records, visible report rows/previews and the displayed numeric exit reason.
It does not manufacture records or read OS traces/descriptions. Screenshot crops
accept fixed diagnostic copy and timestamps only, stay under100KB, and exclude
freeform/account screens. If original consent was on, a private rescue snapshot
is retained because restoring that consent can reimport the real OS ANR on the
next launch; receipts contain only a retention flag.

## Host checks

[Manifest](host-verification.json):46 deterministic offline tests passed for
exit/ring projection, UI navigation/crop privacy, ownership and rollback.
The initial unguarded parser failed two behavioral assertions. Removing process
ownership and screenshot privacy guards each failed a behavioral regression;
exact restored sources passed46 again. One mistyped test selector was discarded
and is not counted as red evidence. XML/private fixture values never enter the
public receipt. Pinned `flutter pub get` ran once; no Dart/native product edit,
APK build, emulator access, full suite, publish or push occurred.

## Device evidence

Pending coordinator APK2197. No crash, ANR, consent journey or normal-app restore
has yet been performed by this slice. No `SEQUENCE COMPLETE` claim is made.

The Android UID ownership regression also failed with that guard removed. A
crash-dialog leaf fixture exposed Python XML element truthiness: the title was
present but evaluated false, so Close app was skipped. Its behavioral assertion
failed before the explicit `is None` fix and passes now. The final46 checks also
cover missing-APK preflight refusing all device work. Product Kotlin/Dart/UI
sources are unchanged, so native/analyzer/build gates are not applicable to
these Python-only preparation changes.
