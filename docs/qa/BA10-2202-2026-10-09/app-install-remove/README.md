# fx app install and removal on APK 2202

Coordinator-authorized continuation of the [partial-payload observation](../README.md).
The existing **Install fx** path repaired the leftover successfully. Afterward,
**Remove fx** completed through the app and displayed **“fx removed. Freed 13 MB.”**
This proves the fx install/removal paths on 2202; it does not qualify the other
five agents or rerun the complete BD16 batch.

## Device evidence

Only `emulator-5554`, under `flock -w 3600 /home/eslam/Storage/tmp/oc-emulator.lock`
for the entire session through final normal-APK verification. No shell deletion,
account sign-in/out, app uninstall/data clear, build, or owner-phone operation.

1. [Before](before.json): the same partial fx payload, 45,056 allocated bytes,
   invalid pin and authored launcher present; no target process, staging or lock.
2. Settings > Agents > Install fx > Install fx dispatched a fresh app setup.
   [Native completion](install-terminal.json) reports `agent-fx` done with
   5,482,652 downloaded bytes, valid pin/link and 12,591,104 allocated bytes.
   The live sheet then exposed Remove fx. No account action was needed.
3. [Before removal](before-removal.json): captured allocated size and retention
   immediately before the destructive action. The unique Remove fx was pressed
   only after [the app's confirmation](confirmation.jpg) explicitly promised to
   retain accounts and conversations.
4. [App result](removal.json), [screenshot](removed.jpg): **13 MB freed**. The
   observer initially missed the result's LTR isolate; [direct observation](removal-observation.json)
   confirmed the same success. Only the observer was interrupted; the app operation
   was not cancelled. The native allocated target size before removal was
   **12,591,104 bytes**, recorded separately from the rounded UI result.
   [Measurements](measurement.json) also record the distinct `/data` free-space
   delta of 12,554,240 bytes; that noisy delta is not the removal receipt.
5. [After](after.json) records zero target allocated bytes and no payload,
   launcher, staging, install lock or target processes. The 27 Claude chat IDs,
   phone-check digest, two Claude account homes, target-home counts, and shared
   Node/Paseo presence match the pre-removal projection. The signed-in UI boolean
   remained false; this is retention evidence, not authentication qualification.
6. [Normal APK](normal-restore.json) and [continuation](continuation.json) record
   final 2202 identity and fresh safety checks. The APK was already the approved
   2202, so no replacement was needed. The emulator lock was released afterward.

## Partial-state gap and driver repair

The earlier claim that no clean reinstall was available is **not supported**:
2202's app Install recovered this exact leftover. The separate visibility gap
is real: a failing version check makes the partial payload look absent, so its
setup sheet lacks Remove after the old setup job is no longer active.
[Three-line contract](../../../design/BA10-partial-payload-contract.md) gives the
coordinator the required durable inventory and presentation behavior. The
current host API supplies only version-valid `installed`; no guessed payload
fact, UI change, or partial backend scaffold was added.

The QA driver now recognizes the real KitConfirm title/body as exact separate
lines even when they share a semantics node, and strips the four Unicode
isolate controls from app copy. Exact target action and confirmation guards
remain intact. [Both regressions fail with the production fix reverted](confirmation-revert-proof.txt);
[76 focused driver tests pass](driver-tests.txt) using `OC_TEST_SLOTS=2
machine_lock.sh test`, one command at a time. Ruff F/E9 and diff checks pass.
No Flutter/native source changed or required testing.
