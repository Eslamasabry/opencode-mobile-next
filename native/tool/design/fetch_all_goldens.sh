#!/usr/bin/env bash
# fetch_all_goldens.sh: put every Flutter reference golden the native golden gates compare against into
# looks-gate/flutter/<ref9>/ (with render.tsv), for any checkout: the PC, the VPS or CI. Story E02-S12.
#
#   native/tool/design/fetch_all_goldens.sh            # uses FLUTTER_REPO and GLASS_REPO (see rb_gate.py)
#
# It runs `rb_gate.py --order NN fetch` for every docs/design/native-ui-rebuild/orders/NN.gate.tsv, so the
# pinned refs (rb_gate.py FREF/GREF) are the single source of truth. Already present files are kept.
# Needs: FLUTTER_REPO (a clone of the app repo that has FREF) and GLASS_REPO (one that has GREF); both default
# as in rb_gate.py. Exit 0 only when every order's fetch succeeded.
set -uo pipefail
root="$(git rev-parse --show-toplevel)"
orders="$root/docs/design/native-ui-rebuild/orders"
gate="$root/native/tool/design/rb_gate.py"
[ -d "$orders" ] || { echo "no orders folder at $orders" >&2; exit 2; }
fail=0; n=0
for f in "$orders"/*.gate.tsv; do
  nn="$(basename "$f" .gate.tsv)"
  n=$((n + 1))
  if ! out="$(cd "$root" && RB_PARTS="" python3 "$gate" --order "$nn" fetch 2>&1)"; then
    echo "order $nn: FAILED"; echo "$out" | tail -3 | sed 's/^/  /'; fail=$((fail + 1))
  fi
done
count="$(find "$root/looks-gate/flutter" -name '*.png' 2>/dev/null | wc -l)"
echo "fetch_all_goldens: $n orders, $fail failed, $count reference images in looks-gate/flutter"
[ "$fail" -eq 0 ]
