# BA8 — transient account label and verified logout

PhoneAgentAccountSource.agentAccount(agentId) -> AgentAuthProbeResult? returns the
last CLI projection. Show `accountDisplayName` only with signedIn and a nonempty
name; otherwise use "Signed in". Never persist/log the account label.

PhoneAgentAccountSource.canSignOutAgent(agentId) -> bool advertises a qualified
logout command (currently Claude and fx).
PhoneAgentAccountSource.signOutAgent(agentId) -> Future<void> privately executes
that CLI logout and probes again. It completes only for signedOut; account label
is then cleared. A successful process exit with still-signedIn/error result
throws plain ProductException: "Sign out could not be confirmed. Check the agent
in its terminal and try again." Unknown agent logout offers its terminal.

Frontend owns localized kit-only label and Sign out action. Disable duplicate
logout taps while pending; don't remove/install profiles or erase app secrets
as a replacement for CLI logout. No logout of the owner's installed Claude was
performed for device evidence; fixture/script/controller tests exercise it with
fake credentials. UI account display and a coordinated test-account device
logout remain incomplete.

ConnectionController implements this optional domain interface. Frontend sources
that do not implement it retain existing sign-in behavior; never reach a native
bridge or CLI from the UI.

Native dependency: lane BB must implement the private agentAuthProbe method
per BA1-contract.md. Until then Claude retains native status fallback and the
new account-label/logout path is unavailable.
