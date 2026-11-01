"""Finds string keys for visible text: python3 -I native/e2e/runner/find_key.py "Allow once" ["more text" ...]

Prints exact matches first, then keys whose text contains the words (case-insensitive), at most 12 each.
Use the key as ${output.s.<key>} (exact), ${output.c.<key>} (contains) or ${output.re.<key>} (with placeholders).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from runner import gen_strings  # noqa: E402

table = gen_strings.raw_table("en")
for q in sys.argv[1:]:
    exact = [k for k, v in table.items() if v == q]
    near = [k for k, v in table.items() if q.lower() in v.lower() and k not in exact]
    print(f"== {q!r}")
    for k in exact[:12]:
        print(f"  EXACT {k} = {table[k]!r}")
    for k in sorted(near, key=lambda k: len(table[k]))[:12]:
        print(f"  near  {k} = {table[k][:100]!r}")
