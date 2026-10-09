# BB7 — protected boot and package replacement restoration

**PASS on Android 15 (API 35), emulator-5554.** Normal 2202 restored and the
whole-session emulator lock released. Local implementation; no push or release.

Base: `049b7147a`, after requested no-rebase frontend merge `ea262e0ca`.
Production source and native fixture: [candidate-2201.json](candidate-2201.json).
All 98 captured native/fixture/manifest files remained unchanged through both
signed builds and the device sequence. No UI changes or stored-format migration.

## Actual device results

| System event / saved policy | Observed before verification instrumentation | Native verification |
| --- | --- | --- |
| Real reboot, wanted and idle off | One owned, authenticated server; foreground service; no Activity or helpers; one attempt | PASS |
| Real reboot, idle enabled | Stopped for full 65-second observation; no Activity/helper; zero attempts | PASS |
| Real reboot, native policy disabled | Stopped for full observation; no Activity/helper; zero attempts | PASS |
| Real reboot, person Stop | Stopped for full observation; no Activity/helper; zero attempts | PASS |
| Real reboot, saved system-timeout revocation | Stopped; timeout reason retained; zero attempts | PASS |
| Actual same-signer install-r, QA2201 → QA2202 | Old PID/start identities gone; unchanged rootfs; one owned healthy replacement; no Activity/helper; one attempt | PASS |

[device-session.txt](device-session.txt) retains the full staged outcomes, including
failed host preflights and their corrections. No synthetic broadcasts were used.
The timeout case exercises persisted revocation, **not** an actual OS quota expiry.
Concurrent Stop/update races and platform dispatch denial are covered by focused
host/JVM/service tests, not claimed as additional physical-device scenarios.

## Normal restoration

[normal-restoration.json](normal-restoration.json) records exact normal APK hash,
version 2202, approved unchanged certificate, authenticated healthy OpenCode2,
owned server only, actual management Start/Connected, idle default OFF, unchanged
rootfs and absence of BB5/BB7 fixture and temporary files. The existing profile
and account storage stayed in place. No uninstall, data clear, credential copy,
helper restart or external link opening occurred. The private original-preference
snapshot was deleted only after these checks; the lock was then released.

QA artifacts: [initial target and runner](artifacts-2201.json),
[higher-version target](artifacts-2202.json), [resource and cleanup receipt](builds.json).
Only the newest APKs were retained; APKs were not copied elsewhere.

## Focused verification

- [Integrated release JVM](integrated-unit-summary.json): **99 passed** across
  event admission, recovery budget, ownership, recipe and idle state.
- [Service callbacks](service-final-green.txt): **24 passed**, including foreground
  promotion before restoration, dispatch/foreground denial, Stop and stale-ticket
  settlement. [Candidate hashes](service-candidate.json).
- [Core red/green](core-verification.md): initial failing-first tests and removed
  idle, ticket, boot and matching-pending-drain guards fail as expected.
- [Final combined Python checks](host-combined-final.txt): **90 passed** across
  BB7, device-proven BB5 and final-pass installation compatibility.
- [Host red/green](host-verification.md): observation ordering, strict private
  state, metadata restoration, exact localized UI actions and transport repairs.
- [Analyzer](analyzer-final.txt): no issues. The first run exposed the merged BB5
  generated-source import; fixed in `049b7147a`, then clean.
- Both target builds and instrumentation compilation passed under shared build
  locking, fresh >=6 GiB admission, 4 GiB Gradle heap, in-process Kotlin, two workers
  and the exact-owned-process memory watchdog. All owned daemons exited and
  intermediates were removed.

Only focused checks were run. The coordinator owns the full integration suite.
The Android rules, boundaries and policy contract are in
[BB7-contract.md](../../design/BB7-contract.md).

## Retained host failures and repairs

1. Initial normal server was healthy but its saved ownership receipt was stale.
   Strict preflight refused before QA installation; actual Stop/Start established
   a new fully owned baseline. No ownership check was weakened.
2. Real reboot reset ADB diagnostic access to shell UID2000. Re-established root
   transport without launching app code, then resumed observation of that same
   reboot. The driver now performs this transport step after boot completion.
3. Android froze the correctly denied cached process (`do_freezer_trap`), stalling
   verification attachment. After the independent observation was saved,
   nonsticky unfreeze of the exact PID/start identity allowed native verification.
   The host enforces observation/cleanup phase and identity checks. No global
   freezer setting, sticky exemption, service or Activity is used.
4. Final normal startup displayed a pending external-link confirmation. Exact
   Cancel dismissed it; actual Start/Connected then passed. The driver now cancels
   only a sheet with both exact localized Open link and Copy link controls.
   No URL was opened or copied.

Each host repair has executable failing-first coverage. These interruptions are
not counted as successful device events. No physical-device, real-account turn,
full-suite, publication or unrestricted background-lifetime claim is made.
