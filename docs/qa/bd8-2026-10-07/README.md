# BD8 evidence and release budgets

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
