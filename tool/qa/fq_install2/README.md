# Install-cert2 offline and locked drivers

No device operation is the default. Obtain the coordinator's actual normal APK
2197 receipt before execution. The receipt records absolute APK path, version,
SHA-256, source commit and the reviewed build's only relevant Dart define:

```json
{
  "normal": {
    "apk": "/home/eslam/Storage/tmp/oc-apk-share/oc-2197.apk",
    "build": 2197,
    "sha256": "<64 lowercase hex from the actual APK>",
    "sourceRevision": "<40 lowercase hex from its actual build>",
    "dartDefines": {}
  }
}
```

The placeholders deliberately fail validation. Use a local receipt with the
real reviewed facts; never infer build options from a filename. Low-space needs
a separate `guard` artifact with the same receipt fields and
`dartDefines: {"OC_QA_AGENT_INSTALL_MIN_FREE_BYTES": 8589934592}`. Normal's flag
must be absent/zero. See [backend/frontend contract](../../../docs/design/BA-install2-contract.md).
A receipt is build provenance supplied by the coordinator, not a cryptographic
proof of a compile flag; the runner verifies APK bytes and signer separately.

```sh
python3 tool/qa/fq_install2/run.py fx --case launch --manifest /tmp/actual-receipt.json
# Only after reviewed APK delivery:
python3 tool/qa/fq_install2/run.py fx --case launch --manifest /tmp/actual-receipt.json --execute --wait
python3 tool/qa/fq_install2/run.py fx --case uninstall --manifest /tmp/actual-receipt.json --execute --wait
python3 tool/qa/fq_install2/run.py fx --case low-storage --manifest /tmp/actual-receipt.json --execute --wait
```

Accepted targets: Codex (`codex`), Gemini CLI (`gemini`), Qwen Code (`qwen`), Goose
(`goose`), Oh My Pi (`omp-acp`), fx. One target per command. Execution acquires
`/home/eslam/Storage/tmp/oc-emulator.lock`, only uses emulator-5554, checks actual
free space first (800 MB minimum), verifies signer `1DE5BF08…`, rejects active
or unreadable setup/checks, and rejects another installed target. It installs
through Settings → Agents when needed and measures the target read-only. It
never signs in, logs out Claude, removes the app, clears data, deletes an agent
via shell or fills storage. Only the exact target's future **Remove <name>**
product action/confirmation can qualify removal.

Results are bounded closed JSON under `docs/qa/FQ-install2-2026-10-08/`. Raw
native/provider output never enters evidence. Screenshots are small JPGs and
must be inspected before commit. UI copy/navigation is English and requires
actual action semantics matching the contract; missing entrypoints fail closed.

`launch.py` checks app-side signed-out rejection and a way forward. It explicitly
reports `daemonLaunchObserved:false`; it sends neither login nor a prompt.
Do not turn picker/draft evidence into a full daemon-launch pass. That path
needs a separately reviewed app QA entrypoint or real auth/smoke prerequisites.
`uninstall.py` has no manual-deletion fallback. It requires verified pinned
payloads, no exact target processes, app removal, zero leftovers and a freshly
Not installed row. `bytesFreed` is the observed change in target allocated bytes;
`freeSpaceDeltaBytes` is separately measured device free-space change, which can
include unrelated background writes.

`low_storage.py` requires healthy actual storage and a clean target. A flagged
candidate taps the real install action, observes a fresh native storage refusal
and zero download/target mutation, then restores the normal APK and retries the
app install. The native snapshot persists only a guard declaration in params,
not component `requiredFreeBytes`; offline host tests verify actual dispatched
specs. Device facts keep these forms of evidence distinct. Generic UI failure
copy is partial; visible plain storage advice is required for a pass.

Each execution restores the normal APK in `finally` if setup is terminal and
no visible preparation is active. Unsafe restoration is blocked and recorded;
do not interrupt another lane's job. If an operation times out, inspect/finish
or cancel the app's owned job before retrying. Missing removal UI leaves the
target installed and blocks the next agent: it is a dependency, not a cleanup
pass. App-side removal and storage copy are coordinator UI work. No device
qualification is claimed by the offline tests or plan mode.

Offline tests:

```sh
python3 -m unittest discover -s tool/qa/fq_install2 -p 'test_*.py' -v
```

The existing matrix generator accepts reviewed complete reports; these drivers
produce observations, never alter certification cells or grant capabilities.
