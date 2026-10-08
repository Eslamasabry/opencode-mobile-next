# BA10 app-side phone-agent removal

Status: **backend implemented and locally verified; no device proof**. The coordinator's reviewed
APK 2197 receipt (path, full APK SHA-256, source revision, default-off Dart
defines, signer verification) has not been delivered. That is the device-run
prerequisite; no emulator operations or build were performed during preparation.

Contract: [BA10-contract.md](../../design/BA10-contract.md). Public UI must expose
“Remove {agent name}”, the contract's confirmation title/body, and “Cancel” /
“Remove”. If the approved APK lacks that action, record partial evidence and
stop; never replace it with shell cleanup or an invented private UI hook.

## Backend evidence

Branch `sol/ba-remove-agent` starts at `feat/genui-fe f0e33d96d`. The contract was
the first commit (`9302b7997`); `ed3d80947` reuses the earlier guarded host and
gateway admission groundwork. No UI, native, account or device changes.

The host now returns `AgentRemovalResult(agentId, freedBytes, alreadyAbsent)`.
Deletion uses the fixed oc setup view; a bounded, exclusive numeric receipt
provides observed allocated bytes through the existing root-view runner. Byte
measurement does not follow links, counts each inode once and excludes hardlinks
retained outside the deleted set. The reader validates and removes its receipt,
including after owner disposal; no arbitrary CLI output reaches the result.
Empty abandoned locks and older catalog launchers are handled; live targets,
authored installers, foreign launchers and nonempty/unsafe locks refuse deletion.
The controller retains accounts, chats and phone-check gates, forwards the result,
and fences replacement owners and overlapping install/removal operations.

Failing-first observations: empty abandoned lock expected exit 0 / actual 16;
older authored launcher expected 0 / actual 17; original host expected two bridge
calls / actual one (no measurement); original controller had nine failed removal
cases (discarded result, partial-install visibility, stale owner and fixed copy).
[Controller negative control](controller-negative-control.log) records the last
set. Restoring the early disposal fence produced the expected one-call/two-call
failure in [host-disposal-negative-control.log](host-disposal-negative-control.log),
then the cleanup fix was restored.

Focused verification through `machine_lock`, pinned Flutter, concurrency 1:

- [focused-tests.log](focused-tests.log): 137 passed across eight existing files,
  including 31 real authored-script cases, 16 host cases and the file-size ratchet.
  A ninth filename was mistyped as `paseo_correlated_send_test.dart`; the sole
  load error was corrected with the actual file below, without rerunning passes.
- [correlated-prompt-tests.log](correlated-prompt-tests.log): seven passed in
  `test/paseo_correlated_prompt_test.dart`.
- [controller-tests.log](controller-tests.log): 100 passed, including ten removal
  cases, in `test/phone_agents_controller_test.dart`.
- [offline-driver-tests.log](offline-driver-tests.log): ten fake-port cases.
- The analyzer initially reported two missing brace lints; these were fixed
  without changing behavior. Final checkpoint: [analyzer-final.log](analyzer-final.log).

Total: 244 affected Flutter tests plus ten offline driver tests; not a full-suite,
APK or device qualification. `phone_agents.dart` remains 1496 lines and the Paseo
gateway remains 1470. Matrix cells have not been promoted without device evidence.

The orchestration module is [proof.py](../../../tool/qa/ba10/proof.py). It takes an
injected, locked device adapter and deliberately has no ADB/subprocess/device
CLI. Offline checks use:

```sh
python3 -m unittest discover -s tool/qa/ba10 -p 'test_*.py' -v
```

Ten fake-device test cases cover the six recipes, public confirmation, retained
Claude state, distinct allocated-byte/free-space measurements, missing UI,
bounded orphan waits, read-only preflight rejection, normal-app restoration,
guard-off receipt validation and exact emulator/lock identity. These are driver
tests, not app-removal or device qualification.

## Device session plan

Run Codex, Gemini CLI, Qwen Code, Goose, Oh My Pi and fx **one at a time**. Each
session must hold `/home/eslam/Storage/tmp/oc-emulator.lock` and address only
`emulator-5554`:

1. Measure `/data` available bytes before any APK replacement/install; require
   800 MB real headroom. Require idle native setup and visible phone checks.
   Require all six authored payload sets absent and no target processes. Do not
   remove existing files to force the preflight to pass.
2. Verify the approved normal APK's complete hash and exact signer
   `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C` at each use;
   reject changed paths/inodes during verification. Restore with
   `adb -s emulator-5554 install -r -d <reviewed-normal-2197.apk>` if needed.
   The normal artifact must have `OC_QA_AGENT_INSTALL_MIN_FREE_BYTES=0` or omit
   it. Never uninstall/clear the app.
3. Recheck storage. Install through Settings → Agents → Install {name}; await
   the real app job and automatic check. Verify exact pin: Codex 0.160.0,
   Gemini CLI 0.62.0, Qwen Code 0.24.7, Goose 1.53.0, Oh My Pi 18.5.1,
   fx 0.0.12. Record only auth enum (`signedOut`, `probeUnsupported`,
   `probeError`). Unsupported auth checks are not evidence of signed-out state.
   Never start sign-in or touch real Claude's account.
4. Capture a closed baseline: Claude Ready and signed in, Claude chat count / a
   digest of chat identifiers, phone-gate digest, shared Node/Paseo presence,
   and target account-home presence. Digests are for nonsecret chat IDs/gate
   metadata only; never read/hash/export credential contents. Read-only inventory
   measures allocated blocks of exactly authored target payload, launcher,
   staging and lock without following links; reject foreign/unsafe paths.
5. Return to Settings → Agents after any automatic sheet closes. Tap exactly
   “Remove {name}”, require “Remove {name} from this phone?” and “This removes
   the installed agent. Your accounts and conversations stay. You can install
   it again.”, then tap the unique “Remove” confirmation. Do not tap another
   agent, sign out, stop helpers or kill processes.
6. Require a refreshed Not installed target row and public “{name} removed.
   Freed {formatted bytes}.” copy. Independently require zero authored allocated
   bytes, no payload/link/staging/lock and no exact target PIDs; compare retained
   Claude/account/chat/gate/shared-host projection with the baseline. Record
   rounded app freed-space copy, independently measured removed allocated bytes
   and `/data` free-space delta as separate facts. A `df` increase alone does
   not prove cleanup or the exact `AgentRemovalResult.freedBytes` receipt.
7. Capture small reviewed JPGs and closed JSON under this directory. In a
   `finally` block restore/verify normal 2197 **inside the same lock**, including
   after a failed run or a guard-artifact session. Failed restoration must
   fail the command after writing evidence and releasing the lock. No next
   agent runs until target absence/process cleanup and restoration are verified.

The injected adapter supplies bounded `ui`, `text`, `tap_node`, `capture`,
read-only `target_inventory`, `agent_state_projection`, `retention_projection`,
`available_storage_bytes`, `require_idle_setup`, real-UI `install_through_app`,
artifact `verify_normal_artifact` / `restore_normal`, `record_result`, and a
`locked_session` context manager for the exact shared lock. These are driver
ports, not additional application APIs. Both artifact operations return exactly
`True` only after full hash/signature verification and, for restoration, the
installed hash/build check. Adapter/receipt delivery and actual
execution remain unverified; this document grants no certification cells.
