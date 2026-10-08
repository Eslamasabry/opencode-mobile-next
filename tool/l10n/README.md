# Arabic message completeness ratchet

Run from the repository root, before Flutter localization generation:

```sh
python3 -m unittest discover -s tool/l10n -p 'test_check_completeness.py' -v
python3 tool/l10n/check_completeness.py
```

The gate compares message keys from `lib/l10n/app_en.arb` and `app_ar.arb`
with `arabic_missing_baseline.txt`. The approved initial baseline records the
current 3,009 missing Arabic keys; **the baseline may only shrink**. ARB metadata
(`@message` and `@@locale`) is excluded.

The gate exits 1 if a new English message lacks Arabic, or if a baseline key
was translated/removed but not pruned. Malformed inputs, duplicate ARB keys,
empty messages, an absent baseline, duplicate/unsorted baseline keys, wildcards
or missing policy header exit 2. Output contains counts and at most 25 names
per error category, never message values.

After translating or removing old messages, prune the baseline:

```sh
python3 tool/l10n/update_arabic_baseline.py
```

The generator requires an existing valid baseline and refuses **all new missing
keys before any write**, even when stale keys also need pruning. It updates
only a shrinking baseline, using a temporary file and atomic replacement.
An unchanged baseline is left byte-identical. There is no bootstrap/reset flag.
The first baseline was generated separately under the coordinator's explicit
approval; this command cannot add a new baseline or reset existing debt.

CI must also supply the event's base commit to prevent a manual baseline edit
from admitting new missing keys:

```sh
python3 tool/l10n/check_completeness.py --base-ref "$BASE_COMMIT"
```

Use the same selected PR/push/manual base as the commit gate. `--base-ref`
verifies the reference to a commit ID, then reads the canonical baseline path
from that revision via `git show` with structured arguments. Any candidate
baseline key absent from that base baseline fails. The checkout needs the base
commit locally (CI fetch depth 0). Missing/invalid refs fail closed.

When the base revision has no baseline file, the initial introduction is
accepted only if the candidate baseline exactly matches current missing keys.
That exception applies to this coordinator-approved first introduction;
reviewers should not approve later removal/reintroduction to reset the ratchet.
No-argument local checks compare current ARBs against the current baseline;
the generator enforces local shrinking, and `--base-ref` enforces history in CI.

The old exact-key allowlist has been removed. It cannot exempt newly missing
keys; new English messages must receive Arabic in the same change. The
hardcoded/server text exclusions in `docs/localization-todo.md` concern text
externalization, not exemptions from this ARB debt ratchet.

This gate verifies message presence, not translation quality, placeholder/ICU
correctness, rendered RTL behavior or hardcoded-string coverage. Existing
generation and Flutter checks still apply.

## Partly translated locales (es, ja, pt, ru, zh)

Spanish, Japanese, Brazilian Portuguese (`pt`), Russian and Simplified Chinese
(`zh`) carry the first-run screens and the chat core only
(`docs/l10n/fg5-key-set.md`). Every other message falls back to English at run
time: gen-l10n writes the English text into each locale's class for a key its
ARB lacks, so nothing crashes and no key is "missing" at build time. Arabic's
gate above would force a translation of every new English message into five
more languages, so these locales get a different gate:

```sh
python3 -m unittest discover -s tool/l10n -p 'test_check_partial_locales.py' -v
python3 tool/l10n/check_partial_locales.py [--base-ref "$BASE_COMMIT"]
```

* `partial_locales_core_keys.txt` lists the core messages; every one must be
  translated in all five ARBs. The list **may only grow** (`--base-ref` fails
  a key the base revision listed and this one dropped), so the translated
  floor is a ratchet, and English debt (messages outside the list) can only
  shrink as keys are added to it. Add a key there only after translating it
  in all five.
* Every translated message keeps English's ICU arguments and plural/select
  structure; keys English lacks, empty or duplicate messages and a wrong
  `@@locale` fail. Output is counts and key names, never message text.
* To make one of these locales complete (Arabic style, new English keys
  required), translate the rest, put every English key in the list and move
  the language to `check_completeness.py`'s pattern.

Japanese and Chinese goldens need glyphs the test engine lacks:
`tool/l10n/subset_cjk_fixtures.py` rebuilds the two Noto Sans CJK subsets in
`test/fixtures/fonts/` after translations add characters.
