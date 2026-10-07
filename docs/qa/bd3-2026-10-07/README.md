# BD3 — localization completeness gate

Finish line: the PR gate rejects an English message without an Arabic key,
except for an exact temporary exception documented in the localization TODO.
Non-goal: translating messages, changing UI or generating localization output.

Implemented `tool/l10n/check_completeness.py`, an empty explicit
`missing_arabic_allowlist.json`, 11 CLI regression tests and runner notes.
The workflow integration is owned by the BD lead; use the commands below in
the `checks` job before Flutter generation.

## Verification

```sh
python3 -m unittest discover -s tool/l10n -p 'test_check_completeness.py' -v
python3 tool/l10n/check_completeness.py
```

On 2026-10-07, all 11 tests passed in 0.445 seconds. They cover a newly missing
message, metadata exclusion, a documented exception, undocumented/stale/unknown
and wildcard exceptions, empty messages, duplicate keys, invalid JSON without
value disclosure, a missing source file and an empty source.

The real gate correctly exited 1:

```text
Localization completeness: 7249 English, 5266 Arabic, 0 documented exceptions, 3009 missing
```

Arabic also has 1,026 keys absent from English; those do not exempt the missing
English keys. `docs/localization-todo.md` contains no exact ARB-key exceptions.
Current translation debt therefore blocks this candidate's new gate; no
translations or broad debt allowlist were added.

Inputs at base revision `d46e47f536ac08e5a1f3fdab681ff0b45e1d020d` (SHA-256):

```text
app_en.arb: 361a90b5169a719f0a3175640e0462d9123ecd44de1f98782aa789b1116be281
app_ar.arb: a932be0b6cd18063c3a318ca5a70ee6f1c667ce9f40682c2ac29f06c7d9260bf
localization-todo.md: b3377d9b7bdda7481fa4d01f6b6badbca8d066349edd479283d188a180c6ea6f
```

The missing-message regression was rerun against a temporary copy of the gate
with its set-difference check removed (`missing = []`). The test failed with
`AssertionError: 0 != 1`: the broken gate accepted two English keys and one Arabic
key. The original script was verified unchanged, then the same regression
passed against it. The temporary copy was removed automatically.

No Flutter processes, builds, device sessions, commits, pushes or CI runs were
performed by this slice. Provider credentials were not read.
