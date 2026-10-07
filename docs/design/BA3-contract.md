# BA3 — confirm terminal sign-in from current account proof

PhoneAgentAccountSource.confirmAgentSignIn(agentId) -> Future<bool> performs a new
CLI status probe, refreshes the rows, and returns true only for signedIn.
It takes no exit code or printed terminal text as input. signedOut/error return
false. Existing PhoneAgentsSource.recheckAgentSignIn(agentId) now uses this same
probe; agentSignInState is inspected only after a completed result.

Frontend: after terminal exit call confirmAgentSignIn (or recheck then check the
explicit inspected signedIn phase), then pop true only after proof. Remove the
terminal's `row.chatSelectable` fallback when consuming this contract, so the
completion rule stays explicit even if row readiness evolves. Exit zero with
signedOut: "Sign-in is not finished yet. Try again." Probe failure: "Sign-in
could not be checked. Try again." Technical enum belongs in Details only.
No UI files changed in BA. Backend confirmation is implemented/tested; frontend
strict completion wiring remains coordinator-owned.

ConnectionController implements this optional domain interface. Frontend sources
that do not implement it retain existing sign-in behavior; never reach a native
bridge or CLI from the UI.

Native dependency: lane BB must implement the private agentAuthProbe method
per BA1-contract.md. Until then Claude retains native status fallback and the
new account-label/logout path is unavailable.
