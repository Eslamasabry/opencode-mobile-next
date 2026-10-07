# BA5 — phone-agent ownership across OpenCode protocol switches

Finish line: switch OpenCode 1 → 2 → 1 without replacing the Ubuntu agent home,
phone checks, account or conversation list. No OpenCode history merge or UI changes.

The existing `PhoneAgentsSource.agentSignInProfileId` now returns the stable
Ubuntu agent owner rather than the selected server profile. Terminal callers must
use that value. Host/sign-in/backend/cache use the same owner. The OpenCode
profile continues to own its own server settings and history.

Migration on ProfileStore.load/upsert writes `oc.phoneAgentOwner.<profileId>`
for in-app Ubuntu profiles (OpenCode backend, exact http://127.0.0.1:4097).
Existing checked homes take priority, then cached conversations, then creation
order. A persisted mapping remains authoritative through restart. Credential
files stay in their existing home and are never copied into preferences.
Termux :4096 and remote profiles are excluded. Multiple populated legacy homes
are not merged; their separate histories need explicit recovery if required.

Deleting one protocol profile retains the shared agent owner while another
in-app profile remains. Deleting the last removes the owner home, secure host
secret and shared agent preferences. Original owner rows may be deleted first;
the remaining alias still owns cleanup. Native bridge receives the stable owner
ID; no Kotlin change is required.

Copy: no new surface needed. Existing "Phone check needed" and sign-in status
should remain unchanged after a protocol switch. Failure to persist migration
is a typed storage failure; never show raw errors.


## Checked-agent migration follow-up

Finish line: every saved legacy phone check remains available after sharing the
agent owner across OpenCode versions. Non-goal: merging histories, moving account
files, or manufacturing phone checks.

`ProfileStore.load/upsert` now adopts missing gate entries from the other in-app
Ubuntu profiles, including installs whose owner aliases were already migrated.
The selected owner's entries take precedence. Runtime `inspect` still checks the
full pinned fingerprint against the current architecture, agent artifact, helper
and Node version before treating any adopted gate as qualified.

`oc.phoneAgentGateMigration.<owner>` is a string-list receipt of consumed donor
profile IDs. Donors are processed once, including empty or malformed gate maps.
This prevents a failed later phone-check retry from recovering old proof after
restart. Malformed receipts and refused writes produce the existing typed storage
failure. Gates persist before receipts; refused writes reload preferences before
retry because SharedPreferences updates its cache before platform persistence.

Receipts are retained with the shared owner when its protocol profile is deleted
and removed when the final alias is deleted. Remote and Termux profiles cannot
contribute checks. Legacy homes, account files and donor maps stay untouched.
The existing Phone check needed / Sign in needed copy remains sufficient.
