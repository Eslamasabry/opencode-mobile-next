# BA4 — first-helper resume refresh (2026-10-08)

Finish line: the first observed certified helper updates Claude capability rows
before refresh completes; delayed helper retries publish the same result without
another screen read. Unknown and mismatched helper versions remain unverified.
Non-goal: changing certification evidence, granting from a release constant, or
changing BD2's first-load connection work.

Candidate: `sol/ba-fixes-3`, based on `9d18cb5c9`; production edits are limited to
`phone_agents.dart` and `phone_agents_check.dart`. No other connection file, UI,
native code, dependency, persisted format, or certification snapshot is changed.

## Root cause and fix

The first host inspection ran before `_paSyncSources()` connected Paseo, so its
capability projection had no observed helper version. The published rows were
never re-inspected after that handshake. A row refresh now compares observations
before inspection and after synchronization; a changed observation causes one
fresh host inspection without another source synchronization. Standalone retry
synchronization refreshes rows when it learns a different observation as well.
The re-inspection retains the existing host/disposal owner fence and re-reads
qualification and auth. It does not grant capabilities to an old Ready snapshot.
The observation/projection helper lives beside existing phone-check publication
code, keeping `phone_agents.dart` within the 1500-line ratchet (1479 lines).

## Regression evidence

[Negative control](negative-control.log): restoring both production files to
`9d18cb5c9` while retaining the five new tests exits 1: four failures, one pass.
The actual regressions fail because certified Claude still lacks resume, a gate
changed during handshake stays Ready, and the retry never republishes resume.
The mismatched-version case also detects the missing second scan; the unknown
version control remains closed and passes. Both production files were restored
in a `finally` block before running the candidate checks.

The new tests model an arm64 host with production-style connect-on-open and
host capability projection. They exercise certified `0.9.2`, missing version,
uncertified `0.9.3`, phone-gate changes during handshake, and a timed source retry.
They assert the public row's capability and resume copy, not only inspect inputs.

## Candidate validation

[Focused tests](focused-tests.log): **121 passed**, five files, serial under the
shared lock using the pinned Shorebird Flutter:

```bash
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 \
  test/phone_agents_controller_test.dart test/agent_certification_test.dart \
  test/phone_agents_test.dart test/agents_settings_placement_test.dart \
  test/file_size_ratchet_test.dart --reporter expanded
```

Log trailing whitespace is trimmed; test results and diagnostic text are unchanged.

Changed Dart files were formatted with `--language-version=3.10`.
`git diff --check` passed. [Analyzer checkpoint](analyzer.log): **No issues found**
(22.5 s), under `tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze
--no-pub`. No fresh APK/device proof or
full-suite claim belongs to this follow-up. The prior APK 2195 device evidence remains in
[the earlier follow-up](../BA-2195-follow-up-2026-10-08/README.md).
