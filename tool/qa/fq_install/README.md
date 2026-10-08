# Account-free phone-agent install reproduction

Run one target at a time from a prepared checkout:

```sh
python3 tool/qa/fq_install/run.py codex
python3 tool/qa/fq_install/run.py gemini
python3 tool/qa/fq_install/run.py qwen
python3 tool/qa/fq_install/run.py goose
python3 tool/qa/fq_install/run.py omp-acp
python3 tool/qa/fq_install/run.py fx
```

Accepted IDs are exactly those six. Each invocation acquires the shared
`/home/eslam/Storage/tmp/oc-emulator.lock` itself and refuses a busy lock by default. Add `--wait` to queue behind another lane. It
only addresses `emulator-5554`, expects its x64 Ubuntu runtime, and infers the
repository from this script's location. It generates the catalog from
`catalog.dart` using the pinned Shorebird Dart SDK and this worktree's
`.dart_tool/package_config.json`; it does not run `pub get`. Python 3, Pillow,
ADB, and Android's `apksigner` are prerequisites. The worktree must already
have packages resolved with the pinned Flutter.

Default evidence output is `docs/qa/FQ-install-2026-10-08`; use `--output PATH`
for a separate reproduction directory. Existing files with the same target
name are replaced, so preserve earlier evidence before another run.

The driver starts from APK 2196 and its real Settings → Agents install UI,
tries to cancel after observed partial download bytes, retries, waits for the
native job, runs Check this phone, projects the matching version/gate/auth
facts, probes account-free CLI initialization in an empty HOME, and cleans up
before the next target. If a different app is installed, it verifies the
approved APK's full local signer and restores
`/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk` with `adb install -r -d`.
Restoring or force-stopping requires a known terminal native setup snapshot
and no visible setup/check preflight. An active or unreadable job blocks these
mutations. The app is never uninstalled or cleared. Individual device helpers
have no standalone device CLI; enter through `run.py` so the lock is held.

No account, sign-in, provider key, authentication RPC, model request, or prompt
is supplied. The CLI probe only sends initialization plus `account/read` for
Codex, or `session/new` for ACP agents. An empty session proves protocol startup
without a prompt, not inference or authenticated compatibility. Existing
Claude/shared helper/profile data is retained. Raw child output is bounded to
2 MiB per stream, held privately while classifying, then deleted. Evidence
contains closed status projections and small screenshots; review screenshots
before committing them.

## Limits and caveats

- The app currently exposes no individual phone-agent removal action. Cleanup
  is explicitly **manual QA cleanup**, not an uninstall certification pass. It
  only deletes the selected authored payload/link/staging/lock paths, refuses
  symlinked ancestors or unrelated links, and refuses removal with live target
  PIDs. Existing target installations are also removed for the fresh-install
  scenario; do not use this against an agent you need to preserve.
- Low-storage admission is **policy/unit evidence only**. This driver checks
  800 MB headroom before each install and never fills storage or changes the
  threshold. Shared doubled-download/minimum-space admission does not fully
  model extracted payload peak space.
- Auth probes other than Claude/fx currently return `probeUnsupported`; that is
  unavailable evidence, not proven signed-out. The empty-HOME CLI probe is
  separate from the app profile's auth projection.
- APK2196 can assign whole-group bounds even to an exact Check this phone label;
  the driver uses a small row's center or the observed final-row position in a
  merged group. It expects English copy, the current
  Settings/Agents structure, and the dev emulator's display geometry. A missing
  target captures a screenshot and stops; do not certify from the driver alone.
- Tiny fx downloads may complete before the Cancel tap. The driver records
  `cancel-window-missed` and resets the target before retrying; this is **not**
  a cancellation pass. For that case only, rerun with `fx --wait --slow-download`. It requires the
  observed unlimited network baseline, limits the cancellation download to
  1024 kbps, and restores unlimited speed before retry and in error cleanup.
  Latency is untouched. This uses the [official emulator console](https://developer.android.com/studio/run/emulator-console)
  on the shared lock; no account or disk-filling simulation is involved.
- Native download totals can briefly be zero for chunked responses. A cancel
  attempt requires `0 < done < (reported total or pinned catalog bytes)` and
  records the catalog fallback.
- Check this phone checks all installed rows sequentially. The driver waits for
  later checks to drain before cleanup; slow/shared state can exceed its bounded
  waits. On failure it writes partial evidence and leaves active work intact.
  Inspect the failed run and finish/cancel the app's own job before retrying.
- This helper reproduces install/check/cleanup observations; it does not edit
  the certification matrix or mark capability cells. Use the separate matrix
  generator with reviewed evidence.

The reusable helper's Python syntax and Dart formatting were checked when
added. Its device replay is recorded separately in the QA evidence.
