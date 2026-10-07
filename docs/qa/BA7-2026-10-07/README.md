# BA7 — silent turn classification

Implemented per-connection timer for main OpenCode and the actual phone-agent
backend. At 45s without target progress, a 5s tick starts one combined read-only
probe capped at 10s. Diagnosis is modelSlow, helperDown, or network/unknown.
The native helper process-status method is existing and independent of the
blocked new auth bridge. No turn is resent, cancelled or finished by diagnosis.

Controller timer tests cover all three classes within 60s, unrelated-session
activity, progress clearing, permission waits and stale idle results. Domain
tests cover timeout, clock/revision fencing and changed transport evidence.
Disabling the timer callback makes all three controller class tests fail;
restoring it passes. See ba7-regression.txt for the failed control.
Frontend contract: ../../design/BA7-contract.md. Public getter turnStallFor(id)
returns safe diagnosis; frontend owns localized kit presentation. No UI edit.
