# BB5 fresh-session build verification

Source identity is in candidate.json. Build and device states are separate.

- QA app: pinned Flutter release, `--no-pub --build-number=2198 --target-platform=android-x64`, runtime QA flag only. Fresh memory admission 6604 MiB under shared build lock.
- GRADLE_OPTS: `-Dorg.gradle.jvmargs=-Xmx4g -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2`.
- App build exit 0 in 297.2 seconds. Packaging gate passed. Size 65,639,629 bytes.
- Owned Gradle daemon PID3493820 tracked with process start identity and private build environment marker; cleanup verified no remaining owned daemon and no build/app/intermediates. No watchdog abort.
- APK package/version and expected local test signer verified; exact hash in signed-qa-artifacts.json. Not installed during this step.
- Runner plus focused native unit test build started at fresh 7949 MiB with the same caps. Result pending.

No full-suite, device, physical-phone or release claim.

Runner build exited0 in3m58s; all131 focused native tests passed with zero skips. Corrected Kotlin/Java fixture compiled. Runner signer/package verified. Owned daemon3510181 gone and own intermediates absent. No watchdog abort.
