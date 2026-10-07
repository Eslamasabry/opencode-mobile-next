# BA3 sign-in completion

The controller confirms completion only from a new explicit signedIn CLI
projection. Zero exit, signedOut, malformed/error output and a late older probe
cannot establish success. Regression coverage lives in agent_auth_probe_test
and phone_agents_controller_test (BA3). The terminal frontend still has a row
selectability fallback; remove it per docs/design/BA3-contract.md before full
item completion. No UI file edited here.

Lead checks: affected serial Flutter tests passed; corresponding negative
control failed behaviorally, and production safeguards were restored. Failed
controls are recorded in this directory. Full repository suite not run.
