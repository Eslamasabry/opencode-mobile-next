RESULT: APPROVED WITH FIXES

# Final approval: 50-blocked-4-SEC

Branch `native/p12x/job-50-blocked-4-SEC`. Reviewed the job's own commits `72985c66`, `adf83635` and `beedc9c9` against
the report `docs/native/parity/migration/50-blocked-4-SEC.md`, the catalog rows, the Flutter reference (`lib/`,
`android/app/.../ByoHostSigner.kt`) and `native/AGENTS.md` / plan/01.

## Merge

`origin/native/p12-head` (`fe2ef020`) is already an ancestor of the job branch (the job's base), so
`git merge --no-ff origin/native/p12-head` was a no-op: no merge commit, no conflicts, and no change to the
`build.gradle.kts` dependency lists.

## Findings

- **B-SEC-012/015/016/017/018 (automation and activity owners): correct.** `AppAutomationPolicies` is the single
  `AutomationPolicyOwner` per profile. Settings (`AutomationSection`, 31 switches with English and Arabic copy),
  monitoring (`MonitorWiring`, `MonitorRuntimeWiring`, `AppAttentionState`) and recovery (`NativePhoneHealing`,
  `NativeAppExitRecovery`, `NativeRuntimeRecovery`) all read or write through it. A missing record still takes the
  consent defaults, the same as the old direct prefs reads, so reconnect and restart stay allowed by default.
  `AppActivityHistory` is the one history writer shared by the Inbox and confirmed restarts. Event and location
  identities are hashed inside `AutomaticActivity.record`. Deletion is reversible: stop admission, then drain, then
  reopen on abort. The erase runs `AFTER_ROW`. Runtime recovery is unbound on drain, erased only after the row is
  gone, and rebound on reopen.
- **B-SEC-022 (built-in folders): correct.** The no-follow `localPath` stays under the app files root. The Projects
  picker has a visible "Built-in Linux folders" row, gated on the profile being the built-in server.
  `PhoneStorageFolders.normalize` now refuses NUL. The linked and not-installed messages are string resources.
- **B-SEC-024/027/028/030/033–037 (private host): correct and reachable.** The HostPanel on the Integrations
  screen is built only from kit parts, and its copy is in English and Arabic. `NativeByoHosts` binds the real
  runner, store, tailnet resolver, loopback HTTP (no proxy, no redirects, capped body) and profile cleanup. Install
  stays refused (`BundleUnavailable`), which matches the blocked rows.
- **B-SEC-029 (Keystore signer): one defect, fixed.** `AndroidHostFiles` gives the runner the *canonical* files root,
  so the agent socket path the runner sends is canonical. `AndroidHostSigner.openAgent` checked that path against the
  *non-canonical* `filesDir`. That logic was copied from the Flutter plugin, where both sides used the
  non-canonical path. On devices where `/data/user/0` is a link to `/data/data`, every agent open would have been
  refused, so saved-host verification, revocation and tunnels could never authenticate. Fix: the signer now resolves
  its root from `filesDir.canonicalFile` and requires it to be its own canonical form, which keeps the "no symlinks
  below app files" guarantee. The agent-protocol subset in `HostAgentProtocol` was checked: identities answer,
  sign-request validation of the session/user/service/algorithm/key, and DER to SSH mpint conversion.
- **B-SEC-065 (sign-in page): correct.** The terminal watcher and the visible button share `requestPage`. An
  `AskFirst` page needs a KitConfirm, using existing English and Arabic strings. Confirming rechecks the same session
  and its link lease, then re-runs `ExternalLinkPolicy` before `ExternalLinkOpener`. A refused page is dropped.
  **Tidy fix:** removing the TODO line left half of a comment in `SignInTerminalScreen.kt`; it is now rewritten as a
  full sentence.
- **Already there (B-SEC-002/003/004/005/056/091): confirmed.** The cited code exists and the app's real bindings
  use it.
- **Blocked (B-SEC-031/032/063/162): justified.** No reviewed OpenCode 2 host bundle has been published
  (`lib/host/byo_host_bundle_pins.dart`). plan/01 D6 and the plan/05 retention milestone govern B-SEC-063. No server
  contract exists for the session address (`sessionAddressHandoff = false`).
- Security and privacy: no regressions found. No secrets reach the UI state (`HostPanelUi` carries the fingerprint
  and public key only). The loopback authorization is registered with the Redactor. No new log lines. No URL bypasses
  the link policy.

## Follow-up (not blocking)

- HostPanel is shown even when the built-in Linux is not installed. Every operation then fails with the generic
  "check the phone's Linux setup" notice. A later pass could gate the panel on `PlatformCapabilities.builtinLinux`
  (native rule 10).
- `ConnectionDelivery` and `AppExitWiring` still read `oc.automation.*` straight from prefs. They only read, and the
  shared owner is still the only writer.

No tests and no APK were run (owner: coding first). The build VPS compiles next.
