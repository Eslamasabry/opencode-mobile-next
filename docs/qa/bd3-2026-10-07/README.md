# BD3 — Arabic completeness debt ratchet

Finish line: existing Arabic translation debt passes through an explicitly
approved baseline which may only shrink; new missing keys and stale baseline
entries fail, including a manually grown baseline compared with CI's base.
Non-goal: translations, ARB edits, frontend changes or localization generation.

Implemented:

- `tool/l10n/arabic_missing_baseline.txt`: the coordinator-approved first baseline
  of 3,009 exact missing keys; sorted, unique, with a may-only-shrink header.
- `tool/l10n/check_completeness.py`: rejects new debt, translated/removed stale
  entries, invalid baseline/ARB structure and optional history growth.
- `tool/l10n/update_arabic_baseline.py`: refuses growth before any write; prunes
  resolved/removed entries atomically; requires an existing valid baseline.
- 24 focused CLI tests, including isolated Git history fixtures. Fixture commits
  use a temporary `bd3-fixture` branch with signing and hooks disabled; no project
  branch, commit, signing key or GitHub resource is touched.
- Old allowlist removed; it has no route to bypass new missing keys.

Workflow changes belong to the BD lead. Existing no-argument invocation remains
usable; the lead adds `--base-ref` with the commit gate's PR/push/manual base.

## Verification

```sh
python3 -m unittest discover -s tool/l10n -p 'test_check_completeness.py' -v
python3 tool/l10n/check_completeness.py
python3 tool/l10n/check_completeness.py --base-ref HEAD
python3 tool/l10n/update_arabic_baseline.py
```

Focused tests: 24 passed in 1.163 seconds. They cover existing debt passing, newly missing
messages, translated/unknown/removed stale entries, duplicate/unsorted baseline
keys, wildcards/whitespace/header rejection, absent baseline refusal, shrinking
generator behavior, refusal before pruning/writing when debt grows, byte-identical
no-op, legacy allowlist ineffectiveness, malformed/duplicate/empty ARBs, metadata
exclusion and bounded value-free errors. History fixtures show manual baseline
growth failing, shrink passing, invalid/option-like refs failing, and initial
introduction requiring an exact match to current debt.

Current-source gate exits 0:

```text
Localization completeness: 7249 English, 5266 Arabic, 3009 baseline debt, 0 new missing, 0 stale baseline
Arabic baseline: 3009 -> 3009 missing keys; 0 pruned
```

Baseline snapshot SHA-256:
`6b17aef6d90e721c23c7ca00245052976e66538e66a6f4fc8960379cea0bdc6e`.

The initial-introduction comparison against the current project `HEAD` also
passes: that base revision has no Arabic debt baseline, and the candidate
matches the current missing set exactly. CI uses the selected event base commit
once the coordinator wires the workflow; no CI execution is claimed here.

Three guards were reverted independently in isolated temporary script copies:

1. Removing new-missing-key rejection made
   `test_new_missing_arabic_message_fails` fail (`0 != 1`).
2. Removing the generator's pre-write growth guard made
   `test_generator_refuses_growth_before_pruning_or_writing` fail (`0 != 1`).
3. Removing base-history growth comparison made
   `test_manually_grown_baseline_fails_against_base_commit` fail (`0 != 1`).

Production scripts were verified unchanged; all temporary mutants/fixtures were
removed. The complete original 24-test suite was rerun after those regressions.

No Flutter process, build, localization generation, device session, project
commit, push, signing operation or CI run was performed by this slice. Provider
credentials were not read. This is a regression gate for known debt, not a claim
that Arabic translation coverage is complete.
