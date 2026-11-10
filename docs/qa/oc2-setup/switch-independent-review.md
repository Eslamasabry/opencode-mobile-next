# Independent review: managed OC1/OC2 switch

Reviewed 2026-09-09T18:40:14.859154+00:00 at owner worktree `/home/owner/Storage/Code/oc_app-oc2-managed-switch`, base881e0d1 plus uncommitted switch changes. Source-only bounded review: no tests, processes, installations, credential/config reads or source edits by reviewer. Owner's final corrections inspected after feedback; this is not a runtime/test pass.

**Result: identified source issues corrected; no remaining blocking finding in the reviewed scope.** Final owner checks and actual Termux switch/revert remain separate acceptance gates. Pinned18600 Linux host isolation/auth proof is recorded in [upstream-feasibility.md](upstream-feasibility.md); it is not Android proof.

| ID | Finding sent to owner/root | Final correction reviewed | State |
|---|---|---|---|
| OCSW-REV-01 | Journal was deleted before `write_state ready`, losing transition metadata if ready persistence failed or process died between writes. | `bridge.dart:1661–1666` writes authenticated ready first, sets setup success, then performs nonfatal journal cleanup. Journal now records operationID (`:1738–1743`); `status():1976–1983` finishes cleanup only for matching ready operation and target marker, after normal tracked-process validation. | Corrected in source; focused failure-boundary tests belong to owner. |
| OCSW-REV-02 | Return trusted an edited/stale profile password and could overwrite preserved runtime authentication. | `bridge.dart:1784–1787` compares staged credential against existing preserved `password-$target` before stopping the server; mismatch refuses the operation. `termux_setup_screen.dart:330–332` returns an existing target profile unchanged. No credential bytes logged/read by reviewer. | Corrected in source. |
| OCSW-REV-03 | Initial OC1→OC2 could proceed with no previous local profile credential, then discover Return unavailable. | `termux_setup_screen.dart:388–392` requires a nonempty previous-runtime profile credential before transport preparation or dispatch, and requires OC1 target credentials on return. | Corrected in source. |
| OCSW-REV-04 | Newly added bare return inside timeout helper's Future<bool> was not a valid result. | `_recoverPersistedSetupAfterTimeout` returns true at the passive-ready branch. Same passive/remote-active connection guard is now also present in ordinary `_refreshStatus`. | Corrected in source; analyzer gate not run by reviewer. |

## Reviewed preserved boundaries

- **Data paths:** `bridge.dart:898–919` puts switched OC2 under `/root/.oc-opencode2` via four XDG variables, explicit OPENCODE_CONFIG_DIR and absolute OPENCODE_DB; conflicting OPENCODE_CONFIG/CONTENT are removed. This matches exact published util18600 support and independently executed pinned host DB proof. Shared project files/config are disclosed, and no automatic history/provider credential migration is attempted.
- **Pre-existing first-run OC2:** empty/default data-mode stays on existing default roots; attempted OC1 return is refused before stop (`bridge.dart:1779–1783`). Only an OC1-owned installation opting into OC2 receives isolated mode (`:1802–1808`). Existing default-data OC2 is not silently moved to an empty isolated database.
- **Crash ownership:** prior credential copied before the journal and only when no journal already exists; an interrupted target-password write cannot be relabeled as the previous runtime. Target preparation precedes bounded stop; runtime marker and target password commit precede launch, with journal retained across both boundaries. Status emits actual runtime marker while transition is pending rather than treating package-staging state as slot ownership.
- **Process targeting:** existing `server_process`, `stop_verified_server_process` and `group_is_managed_tree` require exact runner arguments, PID start-time identity and descendant group ownership before terminating the managed process tree (`bridge.dart:1132–1166`). No broad name/pattern kill was introduced. Port4096 reuse follows a bounded released-port check; a distinct port is not required by this one-server design.
- **Recovery and profiles:** `connection.dart:2175+` refuses known in-flight/pending managed work, persists recovery disablement, checks again, durably clears remembered local connection then disconnects it. Remote connections remain separate. `_redetectFlavor` does not mutate a saved managed profile into the other runtime. Existing profile IDs, drafts and credentials are preserved.
- **Failure/Return UI:** switch operation IDs reject stale previous-ready snapshots; pending journal offers explicit Retry/Return and suppresses automatic recovery/setup shortcuts. Missing/mismatched credentials fail with a recovery explanation rather than generate/rotate an old runtime password. This remains a refusal path if the user's saved credential is already stale; automatic secret recovery is not implemented or claimed.

## Evidence limits

Owner's shell tests mock server start/stop and package install. They are useful behavioral coverage but cannot prove Android/proot execution, real process-tree termination or actual provider/session preservation. Reviewer ran none. Exact final test/analyzer/native results must be linked by coordinator after owner commits. This review authorizes no live mutation, credential migration, signing or release.

Source fingerprints at this review boundary (later source edits require reviewing affected findings, not restarting a broad audit):

```json
{
  "lib/termux/bridge.dart": "f0ba0d1501f725912c9724182894dcc0223171c772695946fbe400abcb1a4f8a",
  "lib/state/connection.dart": "1e83c78a0fd3e0e8f49bc9ae383584b1e5847e51adce3d1da4b2f703712035ec",
  "lib/ui/screens/termux_setup_screen.dart": "5998f817a88311905eb6b40eddf5889438e92347119daacf16dacb43c562c25e"
}
```
