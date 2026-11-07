# Native release runbook (fix-forward)

How a native (Kotlin) build of OpenCode Mobile is released, and how a broken release is fixed. Story E01-S05; the
identity rules it relies on are in [release-identity.md](release-identity.md). The owner's switch day has its own page:
[switch-day.md](switch-day.md).

Native has no Shorebird and no patches (decision D-02). Every fix is a **new release with a higher version code**:
fix forward, never roll back. Android refuses a lower version code over a higher one, and the only way back is an
uninstall that deletes the person's projects.

## What a release is

| Part | Value |
|---|---|
| Package | `io.github.eslamasabry.opencode_mobile` (the `switch` flavour; installs over the Flutter app) |
| Version | `versionName` from `native/version.properties` (`3.0.0`, no suffix) + `versionCode`, at least 10010 |
| Tag | `v<versionName>+<versionCode>`, for example `v3.0.0+10020` (the in-app updater reads the build number from it) |
| Files | `opencode-mobile-<name>-<code>-arm64-v8a.apk`, `…-x86_64.apk`, `…-universal.apk`, `SHA256SUMS` |
| Signer | Public CI `842284B2…1D1C` for GitHub releases; Owner local `1DE5BF08…D60C` for the owner's phone |
| Shape | R8 on (shrink, optimize, obfuscate), resources shrunk, keep rules in `native/androidApp/release-rules.pro` |

Every APK of one release carries the same versionCode. The build checks it (`verifySwitchReleaseIdentity`), and so does
the identity gate.

## The gates

| Gate | Where | Refuses when |
|---|---|---|
| `verifySwitchReleaseIdentity` | Gradle, before packaging the switch release | package is not the Flutter one, versionCode < 10010 or differs between APKs, versionName is not a plain X.Y.Z |
| `verifyProductionLineage` | Gradle, before packaging any release | the signing key is neither Owner local nor Public CI |
| `verifySwitchReleaseStorageManifest`, `verifySwitchReleaseNativeLibraries` | Gradle, `assembleSwitchRelease` | All-files access would reset; an APK lacks its ABI's Linux or speech libraries |
| `lintSwitchRelease`, `testSwitchReleaseUnitTest` | Gradle | release lint or the app's unit tests (including `ReleaseShapeTest`) fail |
| `:quality:test -Poc.mergedManifest=required` | Gradle | a name Android binds to differs from Flutter 2.1.4 (component freeze, E00-S13) |
| `native/tool/release/kept_classes_check.sh` | on the built APKs | R8 renamed or removed a class the freeze names |
| `native/tool/release/identity_gate.sh --expect-signer <sha>` | on the built APKs | wrong package/version, not exactly one signer, or the signer is not the expected lineage |

## Signing

The native build reads the **same file and keys as the Flutter app**: `android/key.properties` with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword` (template: `android/key.properties.example`). `OC_KEY_PROPERTIES` names a
file of the same shape elsewhere (CI writes one in the runner's temp folder). Keep the keystore outside the repository.
Never print, commit or paste a key or password.

Without a complete file the release APKs are left **unsigned** and Gradle says so ("Release signing skipped: …"). The
identity gate then refuses them; sign them with `apksigner` and run the gate again before anything leaves the machine.

`-PocReleaseDebugSigned=true` signs the release with the build machine's debug key. It exists only to prove the build
shape where no key is present (an R8 build that installs on an emulator). The identity gate refuses those APKs, so
they can never be delivered.

## Releasing

1. Write the release notes on master: `docs/releases/v<name>+<code>.md`, first line `# OpenCode Mobile <name>+<code>`.
   `scripts/native-release.sh` (dry run) tells you the code it will take.
2. Dry run, on a clean master equal to `origin/master`:

   ```bash
   scripts/native-release.sh --lineage public          # builds, gates, prints the GO checklist; writes nothing
   scripts/native-release.sh --lineage public --no-build   # only the checks and the plan
   ```

3. When the dry run is clean and the owner has said go:

   ```bash
   scripts/native-release.sh --lineage public --go
   ```

   It takes the next release number with `native/tool/version_code.sh allocate --kind release` (the next multiple of
   10 above every known number: +10 from the last release when no larger build exists), commits the bump, builds,
   runs every gate, writes `native/build/native-release/<tag>/` with the APKs and `SHA256SUMS`, and only when all of
   that passed tags `v<name>+<code>` and pushes master and the tag. Nothing is published.
4. Run the **Native release** workflow by hand (Actions › Native release › Run workflow, input: the tag). It builds
   the same three APKs with the Public CI key, runs the kept-classes and identity gates, and attaches the files to a
   **draft** release with `make_latest=false`. It never runs on push and never publishes.
5. Compare the draft's `SHA256SUMS` with the local one only for the files you expect to match (signing differs between
   lineages, so a local Owner-local build never matches the CI one). Install the matching APK over the previous release
   on one phone and confirm it updates in place.
6. Publish the draft by hand. Mark it latest only when the owner says so.

If a gate fails after the bump commit (step 3), the commit is local and the number is spent: push the bump commit (the
ledger must keep it) and run the release again, which takes the next number. Never reuse or lower a number.

## Fixing a broken release

1. Do not delete the published release while phones may hold it; mark it in its notes as replaced.
2. Fix on a branch, merge to master as usual.
3. Write the new notes (`docs/releases/v<name>+<next>.md`) saying what was fixed, then release again from step 2.
   The new version code is higher, so every phone updates over the broken one.
4. A versionName change is only for a feature release; a fix keeps the name and takes the next code.

## What this runbook never does

- Rotate or substitute a signer, or sign anything with the Legacy CI certificate.
- Publish, mark latest, tag or push without `--go` and the owner's go.
- Lower a version code or reuse one (`native/BUILD_NUMBERS.md` is the ledger).
