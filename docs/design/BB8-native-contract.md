# BB8 private native authentication bridge

The existing BuiltinLinux `agentAuthProbe` channel accepts exactly
`profileId`, `agentId`, `action`, `script`, and `timeoutSeconds`, and returns the
[BA authentication projection](BA1-contract.md). It uses the existing profile
owner and uid 1000 launch context; no UI hook is needed.

Probe is 10 seconds; logout is 20 seconds and admitted only for Claude/fx. Catalog
IDs, profile syntax, existing nonsymlinked home, action/deadline pairing, exact
profile exports and the SHA256 of the authored Dart script body are checked
before execution. Changes to AgentPhoneScripts must update the native digest
and pass the cross-language actual-script harness; arbitrary shell additions
are rejected. Unsupported catalog status protocols remain probeUnsupported.

Generic setup/run output stays under PhoneAgentCheckOutput. Only this private
opt-in launch discards stderr and captures at most64 KiB ephemeral stdout. One
strict UTF8 flat JSON object with unique known keys is accepted. Native projects
only signedIn, signedOut or error; signedIn alone may have accountDisplayName
(nonblank, at most 160 UTF-16 units, no controls or unmatched surrogate). Error
results have a fixed BA enum and no label. Neither script, output, account label
nor exceptions enter logs, diagnostics, notifications or persistent storage.
Overflow returns invalidResponse even when the process also times out, after
confirmed drainage; unconfirmed drainage always returns hostUnavailable.

One monotonic operation budget starts before private preparation/launch, reserving
2 seconds for exact tree cleanup and 400 ms for pipe joins inside the 10/20-second
channel deadline. Profile deletion blocks fresh private work and confirms cleanup
before proceeding. The owner ledger retains failed cleanup and incomplete starts;
a later retry can confirm retired work without killing an active newer probe.

The native host captures a prelaunch same-UID process inventory and remembers
PID/start identities, including children whose root has exited. It freezes/kills
only individually revalidated identities. Unreadable extant tasks, an unattributed
new same-UID process, missing startup identity, or surviving owned work prevents
a successful drain. Unknown tasks are never signalled. Ownership remains until
actual quiescence is observed. Stale Claude refresh-lock cleanup is permitted
only with trusted ActivityManager/current app tasks and no other same-UID tasks;
a fresh in-memory map does not prove cold-process absence.

No authentication output is inferred from CLI success/provider availability.
The pinned script performs the supported logout and then the same status probe.
Device qualification calls the real merged adapter/channel: Claude probe only,
fx probe, fx-only logout, then fx signedOut. Real Claude is never logged out.
The shared emulator lock spans candidate install, proof and approved 2195 restore.
