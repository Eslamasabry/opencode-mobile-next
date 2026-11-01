# usage: paseoprobe2.sh <ver-dir> <port> <tag>   (0.12+ config-based launch)
D=$HOME/upgrade-scope-2026-10-31; N=$1; PORT=$2; TAG=$3
export PATH=$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin:$PATH
RUN=$D/pas/run_$TAG; rm -rf $RUN; mkdir -p $RUN/home $RUN/bin $RUN/proj; cp $D/agentpins/claude_x64 $RUN/bin/claude; chmod +x $RUN/bin/claude
(cd $RUN/proj; git init -q .; echo hi > README.md)
umask 077; head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' > $RUN/secret
PASEO=$D/pas/$N/node_modules/.bin/paseo
export REALHOME=/home/eslam; export HOME=$RUN/fakehome; mkdir -p $HOME
for kv in "daemon.listen 127.0.0.1:$PORT" "daemon.relay.enabled false" "daemon.mcp.injectIntoAgents false" "features.webUi.enabled false"; do set -- $kv; $PASEO daemon config set $1 $2 --home $RUN/home 2>&1 | head -2 | cut -c1-150; done
PASEO_PASSWORD=$(cat $RUN/secret) PASEO_DICTATION_ENABLED=false PASEO_VOICE_MODE_ENABLED=false DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 PATH=$RUN/bin:$PATH \
  setsid nohup $PASEO daemon run --home $RUN/home > $RUN/daemon.log 2>&1 < /dev/null &
sleep 18
PID=$(ss -ltnp | grep ":$PORT " | grep -o 'pid=[0-9]*' | cut -d= -f2); echo "daemon pid=$PID"; echo $PID > $RUN/pid
echo "--- daemon log head"; head -c 500 $RUN/daemon.log | sed -E "s/$(cat $RUN/secret)/<hidden>/g"
cd $D/app
export HOME=$REALHOME
P16A_SECRET_FILE=$RUN/secret P16A_REPORT_FILE=$RUN/report.json PROBE_ENDPOINT=ws://127.0.0.1:$PORT/ws PROBE_DIR=$RUN/proj PATH=$RUN/bin:$PATH \
  timeout 150 flutter test tool/qa/pins_paseo_probe_test.dart 2>&1 | tail -12 | sed -E "s/$(cat $RUN/secret)/<hidden>/g"
echo "--- report"; cat $RUN/report.json 2>/dev/null; echo
[ -n "$PID" ] && kill $PID; sleep 2; echo "left: $(ss -ltn | grep -c ":$PORT ")"
