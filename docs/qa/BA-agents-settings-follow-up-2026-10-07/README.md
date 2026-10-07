# Agents Settings — publish supplied legacy sign-in truth

Finish line: opening Agents on the built-in server displays setup rows and
Check this phone when its host supplies definite inspection facts.
Non-goal: weakening fresh authentication checks, altering capabilities/owners,
or changing UI.

Root cause: 51d8743f changed row refresh to unconditionally probe auth for an
installed agent. The Settings fixture's legacy host port already reports
signedIn but has no typed auth port; refresh instead awaits an unmocked native
status read for up to ten seconds. pumpAndSettle ends with inventory empty.
Owner mapping and phoneAgentsAvailable are correct; BA4 was absent from the
reported failing FE snapshot.

State fix: preserve a nonnull signInPhase from legacy PhoneAgentHostPort.inspect.
When that fact is absent, native fallback remains mandatory. PhoneAgentAuthPort
always refreshes its account facts, even if inspection reports cached signedIn.
The production BuiltinPhoneAgents port remains freshly probed.

Three new regressions in test/phone_agents_legacy_auth_test.dart cover supplied
truth, unknown truth and stale truth from auth-capable ports. The coordinator's
original test/agents_settings_placement_test.dart is unchanged and included in
the serial verification manifest under ../BA7-timer-follow-up-2026-10-07/.

Validation results follow below. No UI/native/device/build/release work.


Regression proof: all three legacy/auth-port tests passed. Reverting only the
state condition to merge 98f48d33 while retaining the new tests failed the known
legacy fact test (native read occurred instead of immediate inventory publication).
`negative-control.txt` records the failure; state implementation was restored.
The unmodified Settings placement test is included in final batch verification.


Final unchanged candidate: 983 tests passed across the shared 28-file affected
manifest, with no skips/failures. The original Settings built-in-server test and
all three new auth-port tests passed. Pinned Flutter machine-locked serial/no-pub;
pinned Dart format language3.10/diff check passed; final analyzer No issues found
(24.3 s). See ../BA7-timer-follow-up-2026-10-07/test-result.json,
test-command.txt and candidate.sha256 for exact evidence. No full-suite claim.
