# BA2: provider readiness fixtures and account row states

Finish line: each provider's captured loading/ready/error snapshot and its CLI
account probe produce a final, actionable phone row. Non-goal: no sign-in,
installation, account creation or certification of unavailable agents.

Provider snapshots describe model/readiness facts per cwd. They never establish
an account's authentication state. `PhoneAgentRuntime.signInPhase` comes from
`AgentAuthProbeResult` only: signedIn -> signedIn, signedOut -> signedOut,
error -> failed. `hostAvailable` describes the native helper process being alive,
not whether a provider's model list is ready. A model-list loading/error snapshot
cannot overwrite an explicit account result with indefinite Checking sign-in.

Captured fixtures under `test/fixtures/host_agent_providers/` record the exact
helper profile, cwd, date, request and SHA-256 of the sanitized snapshot. Unknown
error text and configuration/model payloads are omitted. The fx known sign-in
message is retained only after matching the exact literal substring in its real
error; the unknown surrounding prefix is removed.

The current running helper belongs to profile `1791381364862609`; BA1's signed-in
Claude capture belongs to profile `1790839392073695`. Tests combining the two
exercise the independent contracts and do not claim one same-profile live
journey. BA5 owns the profile-switch migration and its device verification.

Regression coverage uses real captured Claude/fx account results plus separately
labelled synthetic readiness permutations. Synthetic signedIn/signedOut/error
outcomes for all catalog agents exercise presentation states only. They are not
captured accounts or certification evidence. Full BA2 completion remains blocked
until all agents are installed and OW1 supplies the requested account journeys.
