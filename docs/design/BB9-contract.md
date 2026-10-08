DONE: native startup integration qualified, with OpenCode2 surviving-installer rollback evidence.

# BB9 — cold-start pinned update recovery integration

Queued 2026-10-08 after BB3 by the coordinator (BB8 transferred to BD2). Read [BC4's exact proposal and rules](/home/eslam/Storage/Code/oc_app-sol-bc/docs/design/BC4-contract.md). Source branch `sol/bc-setup-safety`, commit `6c84e33d`.

Branch integration: BC commit `6c84e33d` was already an ancestor through frontend
merge `cf3e4e86`; the requested merge returned Already up to date. No rebase.
Native recovery uses public Android filesystem APIs and durable exact installer
ownership. Final native69, affected Dart34, host211, removed-fix red/restored green
and analyzer checks pass. Actual next-start OpenCode2 rollback with two surviving
installer identities passes on QA2211. See [current evidence](../qa/BB9-2026-10-07/README.md).

Finish line: an interrupted failed pinned update is restored to its last good version through the actual next app startup, only after proven installer/descendant quiescence and safe stale catalog-lock recovery.
Non-goals: UI/BA connection edits, arbitrary filesystem scanning, credential migration, treating empty new-process maps as quiescence, or helper-only completion claims.

## Missing process visibility

A missing recorded installer or pre-rollback runtime process is not treated as dead
from `/proc` alone. The native planner requires independent signal-zero `ESRCH`
for each absent recorded PID before declaring quiescence. A live PID, permission
failure, unexpected error or PID reuse refuses recovery with the existing plain
recovery error. Visible identities keep their exact PID/start-time proof before
any signal. The same guard applies to saved runtime members drained before
filesystem recovery. No raw exception or process output reaches the user.

The ordinary-context QA producer trusts only the private native-authored export
bound to a fresh preparing fixture and durable writer receipt. Its own child
kernel identities remain mandatory. Actual native Main commit also requires its
own current kernel identity to match the export's Main stamp. This QA component
is absent from the source production manifest; it does not establish orphan
acceptance by itself.

## Required integration

Extend durable native lifecycle ownership to installer launches before allowing BC4's `admitComponentUpdateRecoveryAfterInstallerQuiescence`. Include exact PID/start-time/boot or equivalent lifecycle receipts and every descendant. Handle installer survivors after Android app or PRoot death; missing parent, kill-on-exit, stale setup.json and empty process maps do not prove absence. Drain only exact proven owned processes, then prove quiescence before mutating journals or locks.

Replace the catalog Claude directory lock with a qualified ownership mechanism. Recover legacy `.lock-claude` only after the same writer-quiescence proof. Checks and installs share the lock; unresolved ownership keeps both unavailable. Legacy `.old.<pid>` directories are not valid last-good journals and must not be silently adopted or deleted.

Integrate the proposed native cold recovery and catalog Claude journaling patches while preserving BB3 and existing native safety changes (BB8 is transferred to BD2). Recovery runs once before the first prootCommand of a cold BuiltinLinux instance; configure resets admission/recovery for a new rootfs generation. Do not roll back an in-process live installer before every launch.

Use the fixed shipped targets and command links from BC4. Recover code before links. Keep component_updates.dart's existing/new receipt semantics and one retained .oc-good generation. Native receipt reads are bounded16-byte O_NOFOLLOW with inode/type checks; validate fixed ancestors, unlink symlinks themselves and bound recursive deletion. Preserve the pre-install storage guard and account for candidate plus retained-backup space. Profile deletion leaves shared installed rollback programs intact; component/rootfs removal needs writer quiescence and complete journal cleanup under safe ancestry.

Existing setup states remain authoritative; rollback does not mark a component verified. Fixed copy stays: “A component update could not be restored. Run setup again.”; “Claude could not finish updating. Run setup again.”; “Another Claude install may still be running. Wait and try again.” Raw filesystem/native errors do not become user copy.

## Required qualification evidence

Focused native/catalog journal, ownership, stale-lock and error-projection regressions with removed-fix red proofs and restored green; format and analyzer at the integration checkpoint. Build from the merged branch under the build lock with the existing authorized signer.

Exclusive emulator-5554 lock for short sessions: interrupt pinned Claude/Paseo/OpenCode updates, include an orphan installer and SIGKILL-stale-lock case, then take the actual app through a cold next start. Prove the prior good version is active and its command link agrees, with journals recovered only after installer quiescence. Include failed first install returning absent and refusal for unknown ownership or unsafe/invalid receipts. Restore app/data and runnable in-app OpenCode2; preserve account homes and the real Claude account. Record safe versions/states and exact owned lifecycle evidence under docs/qa/BB9-2026-10-07/README.md when qualification runs. Process-interruption proof does not imply power-loss durability.

Qualification completed 2026-10-08. The final actual OpenCode2 failed activation,
original root/leader survival, native cold drainage, prior-good executable/link,
read-only Verify and fixture cleanup passed in one locked session. Native policy
and typed owner/active-profile metadata were restored and compared. Real QA
Connected restoration passed, followed by verified normal2195 reinstall in the
same lock without clearing data. Separate real interrupted Claude/Paseo downloads,
power-loss durability and all-agent certification remain outside this native
proof; no account credentials were moved. Earlier failed receipts remain historical.

BB3 is committed `2fe344b57`; BB8 is BD2-owned. BB4/BB5/BB7 follow.

Live completion: exact currently tracked native peers may be admitted only into
an ephemeral live drain plan tied to their same Process and immutable launch
nonce. Cold component recovery admits no new peers. Concurrent generic checks
queue at most10s outside the Linux monitor within their original execution
deadline. Pending Stop-to-Start completion uses the same live peer guard while
only the original Stop receipt selects signals. Unknown UID processes, changed
ticks, reader PID reuse and service/session descendants refuse admission.
Busy copy: “Another phone task is running. Wait a moment and try again.”
