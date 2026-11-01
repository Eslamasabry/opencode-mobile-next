# native/catalog

The parity catalog for the native rewrite (plan 02 §3.6, plan 14). The coordinator owns `scope.jsonl`; the
test-writer lead owns `behaviors.jsonl` and hands out ids.

| File | What it is | Edited how |
|---|---|---|
| `behaviors.jsonl` | The catalog rows. Lines 1–1,544 are a byte copy of `docs/design/native-rewrite-2026-11-01/catalog/behaviors.jsonl`. Later lines are top-up rows (02 §3.5), each with `sources_commit`. | **Append only.** Never edit, renumber or remove a line. A changed behaviour is an `amended` text in `scope.jsonl`; a removed one is `scope: dropped`. |
| `behaviors.lock` | `id<TAB>sha256(line)` for every committed row, in order. | Append only: `python3 -I native/catalog/seed/seed_scope.py native/catalog/behaviors.jsonl --lock` after appending rows. |
| `scope.jsonl` | One line per row: `scope`, `slice`, `module`, `decision`, `reason`, `amended`, `amended_at`, `split_from`. | Generated. Edit `seed/overrides.jsonl`, then run `seed_scope.py … --write`. |
| `seed/seed_scope.py` | Plan 15 Appendix B (verbatim) for the first-pass slice, a module map, then `overrides.jsonl`. | Code. |
| `seed/overrides.jsonl` | The hand decisions from reading every row's text, the top-up rows' scope, and the coordinator's amendments (the last line for an id wins per field). Lines starting with `//` are comments. | Coordinator. |
| `seed/topup_inputs.py` | Lists the logic tests the top-up must read (uncited, new, changed). | Code. |
| `topup-notes.jsonl` | For every top-up input test: the row it became, the row that already covers it, the row it amends, or why it is not a row. | Append at each top-up. |

`ScopeFileTest` in `:quality` fails when an id is missing or repeated, a switch row lacks a slice or module, a moved-out
row lacks `decision` or `reason`, an amendment lacks `decision` or `amended_at`, an id is malformed, or a committed
catalog line changed. `:quality:test` does not list `catalog/**` as a task input yet, so run it with `--rerun` after
changing only catalog files.

List one unit's rows (plan 15 §3.4):

```bash
jq -r 'select(.scope=="switch" and .slice==2 and .module==":engine:transcript") | .id' native/catalog/scope.jsonl
```
