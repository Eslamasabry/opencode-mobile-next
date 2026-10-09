# FB1 install diagnosis

Scope: diagnose the retained `fb1-2203-final-api35` AVD only. No full-pass
rerun, setup journey, shared-emulator change, owner-phone access or build.

APK2203 contains arm64-v8a, armeabi-v7a and x86_64 libraries; min SDK26,
target SDK36. The retained AVD uses API35/x86_64. The approved APK hash and
signer remain those in the [original pass](../final-pass-2026-10-09/README.md).

The old FB1 failure stage covered both `adb install` and the subsequent APK
identity check. Generic command execution discarded stderr, so
`fb1_install_failed` alone could not distinguish either failure.

The harness now spools both install output streams privately, caps each at
64 KiB, and exports only return status, a native INSTALL_* error identifier,
numeric native result and confirmed Success line. Unknown text is discarded.
Timeout/transport/output-size failures are fixed tokens. A separate
`installVerificationError` identifies a failed post-install identity check.
The existing dedicated-AVD, absence, signer/hash and lock checks still apply.

[Failing-first tests](tests-red.txt), then [71 focused passing tests](tests-green.txt)
cover error projection, no private text leakage, successful install followed by
identity failure, existing FB1 flow and FQ9 ports. Python compilation, focused
lint/format and diff checks passed. No Flutter or native changes/tests needed.

## Targeted device result

[Install diagnostic](diagnostic.json): **no adb install error reproduced**.
The retained AVD already contained the package before the probe. The original
run had checked clean absence before installation, so its APK install completed;
`fb1_install_failed` also masked the subsequent identity/readback stage.
The original stderr/exception was discarded and cannot be recovered from its
receipt. The exact original transient verification failure remains unknown.

After waiting for the shared lock, one diagnostic reservation booted only the
retained Storage AVD. Guest ABI list was `x86_64,arm64-v8a`; `/data` had
7,227,984 KiB available. `adb -s emulator-5556 install -r .../oc-2203.apk`
returned exit 0 and the exact `Success` line, with no native INSTALL_* error.
Immediate readback verified build2203, version1.2.0, the approved SHA256 and local
signer. This evidence does not support an ABI, storage or signature rejection.
It is a reinstall diagnostic, **not a fresh-install or first-reply pass**.

The diagnostic AVD was stopped before releasing the lock. No app launch,
onboarding, setup, provider calls, shared-emulator mutation or full-pass rerun.
All host admission checks passed (6 GiB available RAM, 16 GiB Storage, 2 GiB
root, KVM). The first polling waiter was stopped before acquiring the lock;
the actual probe used `flock -w 3600` on an inherited descriptor.

Harness fix: `7465f5d79`. Keep the original final-pass failure intact; the next
coordinator-run fresh FB1 attempt will retain the native install outcome and a
separate verification code if this transient failure recurs. No app change or
blind retry was added from an unproven cause.
