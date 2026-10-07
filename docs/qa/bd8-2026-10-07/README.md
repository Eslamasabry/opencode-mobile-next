# BD8 evidence and release budgets

**Current workflow policy after coordinator review:** APK size checks remain
blocking. Cold-start receipts are optional evidence: absence, failed optional
verification, failed copying or artifact upload do not block release. The
mandatory cold-start workflow descriptions below record the earlier checkpoint;
this update supersedes those claims. The standalone receipt verifier and
measurement tool retain their existing validation behavior.

Finish line: release upload rejects growth over **5 MiB** vs the nearest previous ancestor release tag's exact published APK asset, and rejects missing/stale cold Activity launch evidence or a median over **3000 ms**. Non-goal: claiming Flutter first-frame, usable-server startup or physical-phone performance from Android Activity timing.

`check_apk_size.py` records candidate hash/bytes, baseline tag/bytes, delta/budget and outcome. The previous asset must match its versioned filename, be unique/nonempty and belong to a published stable release. Missing historical evidence fails closed; no guessed baseline. Quality build records the same metric, though its existing upstream Flutter engine differs from Shorebird release and is not substituted for actual release measurements.

The release tag path first makes a Shorebird dry-run APK, validates size and matching-payload cold-start receipt, then uses the existing upload command. It rechecks the produced upload APK before draft/publication. This adds a dry-run build to authorized tag runs; no Shorebird command was executed in this task. Manual release dry-run remains without publication, and no record is invented for it.

Measure an exact installed candidate under a short exclusive device session:

```sh
flock /home/eslam/Storage/tmp/oc-emulator.lock python3 tool/release/cold_start.py \
  --apk build/app/outputs/flutter-apk/app-release.apk \
  --output docs/releases/vVERSION-cold-start.json
```

The tool uses only emulator-5554, checks installed APK SHA-256 equals the local candidate, then force-stops/launches that exact package three times. It records `am start -W` TotalTime; it does not assert Flutter readiness. `verify_cold_start.py` recomputes the median and compares the actual release executable payload digest. `apk_payload.py` includes every manifest, dex, resource, asset, native library and service entry; it excludes only top-level JAR signing metadata (APK v2/v3 signatures are outside ZIP entries). Duplicate ZIP entries are refused. The measurement tool still requires the local test APK byte hash to match the installed APK. This permits the mandated test signer on the dev emulator without changing either test or production signers. It never substitutes signature validation: the existing release certificate check remains required. If any executable content differs, obtain a new measurement of the matching candidate. The Activity timing is qualification of equivalent contents with a test certificate, not a claim of a production APK installed on the emulator.

19 Python tests passed, including equivalent contents with different signing metadata, changed service metadata, wrong/unpublished baseline, absent/ambiguous assets, threshold boundary, nonfinite budget, stale APK receipt, fabricated median, wrong device, invalid samples and launch parsing. Removed growth rejection and executable-payload receipt matching: regressions failed; restored and passed. A real Android randomized install-path fixture also fails without tilde support and passes with it. YAML parse and all bash-block syntax checks passed. No real APK was built, copied, installed, signed or measured here. Thus no current candidate size/startup numbers exist; real qualification remains BLOCKED.

Read-only emulator session confirms stable installed versionCode 2187, while this branch pubspec is 1.2.0+52. There is no android/key.properties. A permitted `install -r` cannot update the installed app with the lower build number; never uninstall/downgrade or rotate its signer. Coordinator must provide a compatible candidate and approved test signing setup. The current release also lacks the required matching-payload startup receipt, so the new release gate deliberately blocks until real evidence exists.

## Optional receipt workflow review fix

The release build's two required receipt-verification calls were removed.
`Record optional cold-start receipt` checks
`docs/releases/v${BUILD_NAME}+${BUILD_NUMBER}-cold-start.json`: when present it
copies the exact bytes to `build/release-metrics/cold-start.json` and performs
best-effort verification; when absent it emits a warning and returns success.
Copy/verification failures emit warnings. Both record and artifact-upload steps
use `continue-on-error: true`; an absent upload file is ignored. The upload
retains the JSON as `release-cold-start-${{ github.sha }}` for 90 days. Both APK
size checks and their build-step failure behavior are unchanged. No changes were
made to `scripts/release.sh` or the signing/release/publication commands.

`python3 -m unittest discover -s tool/release -p 'test_optional_cold_start_workflow.py' -v`
passed **7 tests** in 0.179 seconds. The standard-library-only test reads the actual workflow source,
executes only its receipt shell block in temporary directories with a fake
`python3`, and checks missing/present receipt, copied bytes, verification failure,
copy failure, nonblocking artifact configuration and unchanged blocking APK size
calls. It also parses every shell block with `bash -n`, without executing them.
Full YAML parsing was performed separately with local PyYAML; the test adds no
PyYAML dependency to CI's release-test discovery. YAML parse and diff whitespace
checks passed; `actionlint` is unavailable.

The original mandatory verifier command was extracted from the project's
previous committed workflow into an isolated workflow fixture. The exact
missing-receipt regression failed (`1 != 0`). The current source was verified
unchanged, the old-block fixture removed, and all seven tests passed again.
No signing, Shorebird command, release/API call, build, device session, CI run
or project commit occurred during this review fix.
