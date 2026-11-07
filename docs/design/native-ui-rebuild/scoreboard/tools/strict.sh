#!/usr/bin/env bash
# Re-evaluate every order's kept gate output on the PC twice: as gated (DEPENDS_ON honoured per ~/wt/ui/state on the VPS,
# i.e. the VPS log) and strict (every order counted as merged, so DEPENDS_ON waivers no longer hide a difference).
cd /home/eslam/Storage2/wt/rb-score || exit 2
export OC_UI_URL=http://127.0.0.1:9/ui RB_STATE=$PWD/looks-out/alldone
mkdir -p looks-out/strict
for d in looks-out/score/[0-9][0-9]; do
  nn=$(basename "$d")
  rm -rf looks-out/gate && cp -a "$d" looks-out/gate
  python3 -I native/tool/design/rb_gate.py evaluate --order "$nn" > looks-out/strict/$nn.log 2>&1
  # keep tdiff images from the strict pass for orders whose evaluate made them
  cp -n looks-out/gate/*-tdiff.png "$d"/ 2>/dev/null
  echo "$nn $(tail -1 looks-out/strict/$nn.log | cut -c1-120)"
done
rm -rf looks-out/gate
