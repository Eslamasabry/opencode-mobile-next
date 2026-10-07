# Arabic message completeness

Run from the repository root, before Flutter localization generation:

```sh
python3 -m unittest discover -s tool/l10n -p 'test_check_completeness.py' -v
python3 tool/l10n/check_completeness.py
```

The gate compares message keys from `lib/l10n/app_en.arb` with
`lib/l10n/app_ar.arb`. ARB metadata (`@message` and `@@locale`) is excluded.
Missing messages exit 1; malformed inputs and invalid exceptions exit 2.
Output contains counts and at most 25 missing key names, never message values.
It also rejects empty message values and duplicate JSON keys.

`missing_arabic_allowlist.json` contains exact temporary exceptions, each with
a reason. The source is `docs/localization-todo.md`: that document currently
excludes hardcoded text and server-provided text from externalization work,
but lists no exceptions for messages already in the English ARB. Therefore
the committed allowlist is empty. Do not turn existing missing translations
into a baseline.

For a new approved temporary exception, document the exact key in backticks
in `docs/localization-todo.md` and add that key and its reason to
`missing_arabic_keys`. Wildcards, undocumented keys, unknown English keys and
already-translated exceptions fail. Remove each exception when translated.

This gate verifies message presence, not translation quality, placeholder or
ICU correctness, rendered RTL behavior, or hardcoded-string coverage. Existing
generation and Flutter checks still apply.
