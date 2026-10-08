# FQ9 pre-release device checklist — offline drivers

Branch `sol/bc-fq9` from `feat/genui-fe` **6a75894466151fa2fc45eef1d489fd82d7dee19a**. [Frozen contract and ownership](contract.md); [CLI](../../../tool/qa/fq9/run.py). Finish line: implement and unit-test four repeatable checklist drivers with a safe APK 2197 run plan. Device qualification is a later gate: the coordinator has deferred device work until APK 2197 is posted and a device slot is booked. No emulator, build, signing, download, push or production-source change was performed for this slice.

The CLI defaults to an **offline plan**, even when a manifest is supplied. Only `--execute` loads artifact tools/ADB and takes the shared `/home/eslam/Storage/tmp/oc-emulator.lock`, nonblocking. A busy device returns `emulator_busy`; it does not queue a 30-minute hold behind another lane. Lock ownership lasts through checks, exact fixture cleanup, normal-app restoration/verification and evidence writing. All processes/output are bounded; reports contain fixed result tokens, validated artifact identities, booleans and numeric checkpoints. Credentials, preferences, transcripts, raw device errors and HTTP responses are never printed or stored in evidence. Private reads stay in bounded memory, including the runtime's own password under its app UID; no Keystore decryption or provider configuration read.

## Four rows and their limits

| Row | Automated driver | Required setup / refusal |
|---|---|---|
| Install over previous APK, keeping data | [Upgrade](../../../tool/qa/fq9/upgrade.py): exact installed baseline bytes/build/version/signer; unchanged UID; update only with `adb install -r -d`; compare selected profiles/preferences, encrypted secure-storage bytes, selected retained HTTP histories and an owned sentinel; remove sentinel. | Previous APK and normal/candidate receipts; previous app already installed, setup and all managed turns idle; seed profiles, sign-in, preference and conversation fixtures in the previous app. Never install a previous APK over the modern shared app just to manufacture a baseline. |
| Published stable 1.2.0 → candidate | Same driver, additional actual `versionName=1.2.0`, reviewed release-asset provenance and signer preflight. | Published APK/hash and coordinator-reviewed release URL; exact stable baseline already installed. Known stable CI signer is `2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC`, local signer is `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`. These differ. If the actual APK 2197 has the local signer and stable has the CI signer, this row is **blocked**, even on a second AVD. A same-signer coordinator-approved candidate is required; this lane never re-signs, uninstalls or clears data. No published artifact was fetched or attested offline. |
| Background survival at 5 and 30 minutes with a live turn | [Background](../../../tool/qa/fq9/background.py): one HOME dwell checks both 300/1800-second checkpoints, uses sleeps no longer than 30 seconds between bounded observations (adapter latency is included in recorded elapsed time), tracks unchanged app PID/start identity and app-UID managed socket, exact FGS plus ongoing notification, actual owned turn and newer persisted fixture calls, resumes the same app conversation, aborts/deletes only the exact owned fixture. | App-managed **OC1 1.18.32** only, a real app-started fixture (not an owned HTTP server), candidate already installed, actual live tool progress and no unrelated activity. Early completion, retry-only/inactive state, permission pause, process/service loss, missing notification or failed resume/cleanup remain failures. Book one uninterrupted 30-minute lock interval before execution. |
| Clean first run | [Fresh](../../../tool/qa/fq9/fresh.py): a separately provisioned, named AVD with user 0 and no package/private app-data directory; install candidate; observe actual English/Arabic first-run question, empty profiles, absent Ubuntu/setup state, and unchanged live app process across the bounded launch checkpoint. Leave the candidate installed. | **Needs a second AVD.** The driver refuses `emulator-5554` and never creates/switches/removes Android users, uninstalls or clears data. A reused dedicated AVD with leftover package/data also refuses; do not wipe it through this driver. Existing screenshots/notes do not qualify a clean run. |

Upgrade passes cover a selected storage/history subset. Whole-preferences XML byte identity would wrongly reject deliberate retirement of legacy keys, so the driver compares `oc.profiles` as logical JSON and a fixed retained preference subset. Encrypted storage retention does **not** prove Keystore decryption or sign-in; raw HTTP history retention does **not** prove candidate UI rendering. Successful automated upgrade reports keep `manualChecksPending:true` and `deviceQualified:false` until the coordinator separately records actual sign-in, chosen preferences and selected history reopening in the candidate app. A missing fixture/schema/protocol precondition fails closed rather than quietly omitting a preservation check. Stable engine versions may differ from current versions; the static-history reader accepts a healthy matching OC1/OC2 major with valid response shape, while the live-survival reader pins OC1 1.18.32.

The first-run `crashFree` predicate is a narrow visible-launch/process-retention observation; it does not certify future stability or all ANRs. HOME polling is emulator background survival under observation, not physical-device Doze, screen-off, OEM battery policy or the Android 6-hour dataSync cap. OC2 live-turn survival remains unqualified: its HTTP history lags in-flight tools, so this driver refuses to infer survival from execution admission/status alone. These limits do not turn any row into a pass.

## Why a second AVD

Private runtime roots use `context.filesDir`, but whole-runtime user isolation is unqualified: [BuiltinLinux.kt](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt) binds the primary `/storage/emulated/0` alias (SharedProjects policy around line 2115), [MainActivity.kt](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt) and [TermuxSetupRunner.kt](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/TermuxSetupRunner.kt) address `/data/data/com.termux`, and [builtin_linux.dart](../../../lib/builtin/builtin_linux.dart) / [phone_agents_host.dart](../../../lib/builtin/agents/phone_agents_host.dart) use fixed loopback ports. Android advertising secondary-user/profile capacity would not prove those shared resources isolated. The offline planner therefore returns `needs_second_avd` regardless of advertised user support. No claim about the current emulator's maximum-user setting is made.

## Offline commands and inputs

```sh
python3 -m tool.qa.fq9.run --case upgrade
python3 -m tool.qa.fq9.run --case stable
python3 -m tool.qa.fq9.run --case background
python3 -m tool.qa.fq9.run --case fresh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- \
  python3 -m unittest discover -s tool/qa/fq9 -t .
ruff format --check --target-version py310 tool/qa/fq9
ruff check --select F,E9 tool/qa/fq9
```

Provide a reviewed JSON manifest outside the repository. `candidate` and `normal` must be the exact same approved artifact, including path/build/hash/certificate; build 2197 is pinned. `previous` and `stable` are optional, required for their respective rows. Every artifact has exactly these fields (placeholders below must be replaced; this is not an executable receipt):

```json
{
  "apk": "/absolute/path/to/coordinator-posted.apk",
  "build": 2197,
  "version": "1.2.0",
  "sha256": "<actual lowercase 64-hex APK digest>",
  "signer": "<actual lowercase 64-hex certificate digest>",
  "origin": "coordinator-approved"
}
```

Use `previous-approved` for `previous`, `published-stable` for `stable`; no guessed stable build number. When `stable` is present, include top-level `publishedSource`, the reviewed HTTPS GitHub `releases/download/<tag>/<asset>.apk` URL supporting that pinned APK. This receipt records coordinator-reviewed provenance; the driver verifies bytes/package/version/certificate but does not independently fetch the published release. Candidate origin is not inferred from a filename. Signer-incompatible rows refuse before APK tools, lock or ADB. Host verification rechecks hashes after certificate verification and immediately before install; installed APK bytes are pulled for an independent package/certificate/hash check, including normal restoration.

For upgrade history preservation, provide `--session-receipt` with `engine` (`opencode`/`opencode2`), `directory` (a single `/root/projects/<safe-name>`), and `sessions`, a list of up to eight existing `{id,title}` fixtures. Select small settled histories under 200 messages; this is not an all-history export. Keep fixture IDs/names and all private input receipts outside committed evidence. The app-managed server must already be running the receipt's exact engine and healthy on :4097; only that server is forwarded, never replaced or started by the driver. Incompatible/absent runtime history observation blocks automated preservation and needs a separately designed/manual upgrade check; it does not establish data loss.

The background receipt has the same shape but exactly one `{id,title,promptID}` fixture. Title must be `<run-id>-background`; it is unique per run. Create this dedicated conversation and send the following bounded prompt **through the app UI**, in the app-managed OC1 project, using a working selected model:

> FQ9_BACKGROUND_FIXTURE. Run the bash tool with exactly `sleep 120` twenty times sequentially. Use a separate tool call for each sleep. Do not run other commands, tools, parallel calls or file changes. After the twentieth sleep, reply DONE.

Existing permission policy remains intact; resolve any existing policy precondition without changing global saved grants in this driver. Enable background working and its notification permission through the app’s existing opt-in, wait for actual persisted first-tool progress, and keep that conversation visible. Twenty sleeps give 40 minutes total, leaving preparation headroom before the 30-minute dwell. The read-only discovery mode below finds the unique title in the exact project and exports only its session/user-message IDs; it does not start or modify a turn. The driver admits OC1 busy inference gaps between verified fixture calls but rejects retry state, unrelated/stale parents, foreign/synthetic parts, non-fixture commands and newer user prompts. Both checkpoints require newer real fixture calls. It sends no HTTP prompt/heartbeat, changes no model/engine and does not wake/relaunch the app during dwell. On return it verifies the same visible title and owned live/final state. Cleanup revalidates latest-prompt ownership before both abort and delete; a new prompt in the fixture is preserved and reported as a cleanup failure. Keep its receipt for manual recovery rather than deleting evidence of remaining work.

## Device schedule after APK 2197 is posted

1. Verify the posted artifact receipt and normal certificate; freeze/review the driver commit. Run only when the coordinator authorizes a device slot. Avoid additional host builds/emulator instances while PC memory remains constrained.
2. **Run previous→candidate before another lane updates the shared previous baseline**, if it still exactly matches the reviewed previous APK and seeded fixtures. If already modern, refuse and schedule a separately provisioned AVD with the actual previous app already installed/seeded; never downgrade/reset the shared app to establish a baseline. Stable requires the independently verified same-signer path described above.
3. On candidate 2197, book the 30-minute background interval; prepare the actual app-started fixture first, then execute the driver. Do not release/reacquire the lock halfway through a dwell or mix runs into one checkpoint pair. If another lane currently needs the emulator, defer this row.
4. Fresh first run waits for a separately provisioned clean AVD and enough PC memory. This task does not create or boot that second AVD. Supply its exact serial and AVD name; the driver checks emulator boot identity/name/current user and absence before any install, while still using the shared lock.
5. Review per-case evidence and manual upgrade usability checks. Failed cleanup/restoration invalidates success; stop and preserve the fixture/receipt for exact recovery. All mutating paths restore/verify approved normal 2197 before releasing the lock, without uninstall/clear. A normal APK already matching byte-for-byte is verified and relaunched rather than needlessly killing its managed server; background-only paths verify unchanged normal identity after fixture cleanup.

Future execution examples (the driver acquires the lock internally; **do not wrap it in another flock** on the same path):

```sh
python3 -m tool.qa.fq9.run --case upgrade --execute \
  --manifest /tmp/fq9-reviewed-artifacts.json --session-receipt /tmp/fq9-preserved-history.json \
  --run-id fq9-20261008-upgrade-a
python3 -m tool.qa.fq9.run --case stable --execute \
  --manifest /tmp/fq9-reviewed-same-signer-artifacts.json --session-receipt /tmp/fq9-stable-history.json \
  --serial emulator-5556 --dedicated-avd FQ9_STABLE --run-id fq9-20261008-stable-a
python3 -m tool.qa.fq9.run --case background --execute \
  --manifest /tmp/fq9-reviewed-artifacts.json --discover-turn-receipt /tmp/fq9-live-turn.json \
  --directory /root/projects/fq9-fixture --run-id fq9-20261008-background-a
python3 -m tool.qa.fq9.run --case background --execute \
  --manifest /tmp/fq9-reviewed-artifacts.json --session-receipt /tmp/fq9-live-turn.json \
  --run-id fq9-20261008-background-a
python3 -m tool.qa.fq9.run --case fresh --execute \
  --manifest /tmp/fq9-reviewed-artifacts.json --serial emulator-5556 \
  --dedicated-avd FQ9_FRESH --run-id fq9-20261008-fresh-a
```

The second-AVD serial/name examples are placeholders, not existing-device inventory. Run IDs must be unique; evidence is `<run-id>-<case>.json` and is never overwritten. Discovery produces a separate `<run-id>-background-ready.json` report and `readinessOnly:true`, never survival qualification. CLI exit 0 means the offline plan, receipt readiness or automated driver checks completed; it does not satisfy pending manual upgrade assertions. Background/fresh qualification remains confined to their explicitly stated engine/launch scope. No release matrix is updated by these drivers.

## Local verification

Generated default plans are in [offline-plans.json](offline-plans.json). Signer-fence and newer-prompt cleanup guard-removal counterexamples are recorded in `red-signer-fence.txt` and `red-newer-prompt-cleanup.txt`; the weakening occurred only in isolated test-process memory, with fake ports and unchanged source files. The final offline candidate passed **88 Python tests** serially under `machine_lock`, with clean formatting and static checks. Existing tracked application/QA sources remain unchanged and local document links passed. Focused results and source-preservation checks are recorded in [host-validation.json](host-validation.json) and `BC-status.md`. Unit fixtures use virtual time and injected ports; they never contact ADB, sleep through a dwell, install an APK or call a provider. No device result is recorded for this slice.
