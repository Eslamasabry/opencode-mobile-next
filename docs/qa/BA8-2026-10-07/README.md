# BA8 account projection and sign-out

Safe account label is transient in ConnectionController.agentAccount. A
supported CLI logout is followed by the same explicit status command. Logout
exit zero with credentials remaining fails confirmation. Script/controller
fixtures exercise successful logout and unsuccessful confirmation; the host
bridge test checks private profile context and safe projection. No owner's
real account was logged out. Account-label UI and coordinated device logout
remain frontend/device work (docs/design/BA8-contract.md).

Lead checks: affected serial Flutter tests passed; corresponding negative
control failed behaviorally, and production safeguards were restored. Failed
controls are recorded in this directory. Full repository suite not run.
