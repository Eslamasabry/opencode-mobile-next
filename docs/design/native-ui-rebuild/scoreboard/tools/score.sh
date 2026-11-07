#!/usr/bin/env bash
# Scoreboard driver: per order, fetch + gate cases + evaluate (static skipped via --recheck), outputs kept per order.
cd ~/wt/rb-score || exit 2
export RB_DEV=1 RB_RECHECK=score OC_UI_URL=http://127.0.0.1:9/ui JAVA_HOME=$HOME/jdk21 PATH=$HOME/jdk21/bin:$PATH
export FLUTTER_REPO=$HOME/wt/flutter-ref GLASS_REPO=$HOME/Storage/Code/oc_app
OUT=looks-out/score; mkdir -p $OUT
for NN in "$@"; do
  [ -f $OUT/$NN.done ] && continue
  echo "$(date +%T) start $NN" >> $OUT/progress.log
  native/tool/design/gate_order.sh --recheck $NN > $OUT/$NN.log 2>&1
  rc=$?
  if ! grep -q '^evaluate ' $OUT/$NN.log; then
    echo "== evaluate after gate exit $rc" >> $OUT/$NN.log
    python3 -I native/tool/design/rb_gate.py evaluate --order $NN >> $OUT/$NN.log 2>&1
  fi
  rm -rf $OUT/$NN; cp -a looks-out/gate $OUT/$NN 2>/dev/null
  echo "$(date +%T) end $NN rc=$rc $(grep '^evaluate ' $OUT/$NN.log)" >> $OUT/progress.log
  touch $OUT/$NN.done
done
echo "$(date +%T) ALL DONE" >> $OUT/progress.log
