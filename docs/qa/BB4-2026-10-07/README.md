VERIFIED: owned native/OpenCode work leases, including captured setup admission.
Complete BB4 remains pending BA's local-agent turn integration.

# BB4 — independent bounded CPU work leases

Finish line: chat, setup, sign-in and terminal each own an expiring token; the
wake lock exists only while at least one valid token remains. No UI or connection
library edits. Idle server and Paseo helper processes acquire no CPU hold.
[Contract](../../design/BB4-contract.md) records the named chat bridge, states,
bounds and the remaining BA gateway request for local agent turns.

Native compile: [Gradle build](native-work-build.txt),
[result](native-work-result.json):46 focused tests pass (registry19+host22+scope5).
[Restored pure JVM](native-lease-jvm-restored.txt) also passes46. The unchanged
six original native controls are in [pre-review red](before-setup-review-native-lease-jvm-red.txt);
two new scope controls each fail one behavior assertion in [scope red](native-setup-scope-red.txt).
The supplemental runner initially assumed two classes; its corrected generic
argument handling and final46 pass are retained. Exact [owned Gradle cleanup](native-work-owned-processes.json)
has no survivors and this worktree's intermediates are absent.

[Dart restored lease/timing checks](dart-final-restored.txt):32 pass.
[Final production setup harness](dart-setup-work-scoped-final.txt):14 pass.
Eight named-lease Dart guards failed when removed; see `dart-red-*.txt` and
[resumed proof summary](dart-red-restored-resume-summary.txt). Removed whole-job
wrapper and [removed captured worker scope](dart-red-delayed-setup-worker.txt)
fail their intended behavior. Initial public/internal fixture compile failure
is retained as discovery, not a valid red proof. The initial disposal-only
control did not fail because serialized off still drained; it was corrected to
the idle-notification behavior. No false disposal-only red claim is made.

[Final analyzer](analyze-scoped-final.txt) clean13.0s. Earlier capture callback
and native fixture discovery failures remain historical. The setup compile
manifest includes BB9 visibility and the new pure SetupWorkScopes helper.
[Ten final host checks](host-focused-final.txt) pass, and
[removed pre-fixture startup retry](host-start-retry-red.txt) fails its behavior.

[Target](target-work-artifact.json): private nondebuggable release/AOT QA2213,
SHA `78fa434b39dfce3dd4c13c09f118154026aff2aac16aeafd03cc1f016fc0695a`,
signer1DE5, exact pinned release-engine hash, no auxiliary-producer manifest entry.
[Build](target-work-build.txt) and [cleanup](target-work-owned-processes.json).
Only newest APK retained; intermediates removed/exact owned Gradle daemon gone.
[Candidate source hashes](build-candidate.sha256) freeze13 production/acceptance paths.
[Matching runner](runner-work-artifact.json): signer1DE5,
SHA `c144eef4fc09f3cecfa1c4483f1e8e15eb859ec22c032bb99c8f390adc5de441`.
[Runner build](runner-work-build.txt)/[cleanup](runner-work-owned-processes.json) pass.
[Final whole-session device receipt](host-device-work-leases-restore.txt) PASS.


Private device scenario uses the actual NativeWorkLeaseHost/PowerManager,
Linux setup wrapper, private sign-in process launcher with an empty temporary
home, and a real LocalTerminal session. It does not sign into any real account.
The qualified host adapter retains actual native flags and original metadata
comparison. Install, scenario, cleanup and normal2195 in-place restore must all
share the exclusive emulator5554 flock. Five mandatory native flags passed on QA2213, including late readonly installer
after setup revocation and a delayed prepared worker without fresh admission.
Original policy/marker digests, typed metadata and active pointer passed restoration.
Fresh exact kernel ownership and authenticated Connected OC2 were restored on QA2213;
normal2195 was then installed-r-d and its exact signer/hash/version/Main start verified
inside the same lock. Its final [small JPG](restored-normal-2195.jpg) shows connecting,
so no final normal2195 Connected/account claim is made.

No full suite, physical device, browser account round-trip, all-agent CPU wiring,
CI, push or release claim. Native changes need a later approved Shorebird release.

Final review correction: successive installers in a revoked/capped setup job
could acquire a fresh hold. SetupWorkScopes now admits the whole job before
service/worker startup and preserves that scope even when its hold is gone.
Scoped installers never acquire a second token; startup failure and job finally
close the captured owner. A delayed-worker production harness regression and
five pure scope tests cover this lifetime. A new actual device flag requires
revoked setup -> late readonly installer stays without CPU -> delayed worker
cannot readmit. QA2212 pre-review successful receipts are historical only.

Two earlier device attempts failed before the native scenario, and both restored
normal2195. The final pre-review session passed all four old flags and restoration.
The first prearm incorrectly assumed a prior kernel ownership receipt; bootstrap
now stays stopped while the qualified adapter selects OC2, launches MainActivity
and captures its armed baseline. One initial Activity identity replacement can
retry once before any fixture; subsequent instrumentation PID guards are strict.
Eight host checks pass; removed pre-fixture retry fails the selected behavior.
The first screenshot failed; final normal2195 snapshot capture succeeded. Its
image shows startup connecting. Authenticated Connected proof belongs to the
QA candidate before normal2195 installation, not the normal screenshot.

QA2213's first startup attempt never reached acceptance because its recipe stayed
unarmed; original-state restoration and normal2195 reinstall passed. A guarded
pre-fixture explicit Start fallback uses the immutable native receipt from before
upgrade and the existing comparator to require fresh exact kernel ownership, armed
recipe, authenticated health and foreground service. Missing/mismatched prior receipt
refuses admission. Both fallback positive/negative host checks pass and its removed
control fails. This fallback did not need to run in the final successful session.
No subsequent process/instrumentation guard was relaxed.
