# the Termux way: the exact inner script of run_daemon, password on stdin
D=$HOME/upgrade-scope-2026-10-31; N=p0112; PORT=16771; RUN=$D/pas/run_termux
export PATH=$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin:$PATH
rm -rf $RUN; mkdir -p $RUN/home $RUN/bin $RUN/proj; cp $D/agentpins/claude_x64 $RUN/bin/claude; chmod +x $RUN/bin/claude
(cd $RUN/proj; git init -q .; echo hi > README.md)
umask 077; head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' > $RUN/secret
export REALHOME=/home/eslam; export HOME=$RUN/fakehome; mkdir -p $HOME
cp $D/termux_daemon_body.sh $RUN/body.sh
PATH=$D/pas/$N/node_modules/.bin:$RUN/bin:$PATH DISABLE_AUTOUPDATER=1 PASEO_DICTATION_ENABLED=false PASEO_VOICE_MODE_ENABLED=false \
 setsid nohup bash -c "$(cat $RUN/body.sh)" oc-paseo $RUN/home 127.0.0.1:$PORT < $RUN/secret > $RUN/daemon.log 2>&1 &
sleep 18
PID=$(ss -ltnp | grep ":$PORT " | grep -o 'pid=[0-9]*' | cut -d= -f2); echo "daemon pid=$PID"
echo "--- config the script wrote"; python3 -c "import json;d=json.load(open('$RUN/home/config.json'));print(json.dumps({k:d.get(k) for k in ('daemon','features')}))" 2>&1 | cut -c1-400
echo "--- log head"; head -c 300 $RUN/daemon.log | sed -E "s/$(cat $RUN/secret)/<hidden>/g"
cd $D/app; export HOME=$REALHOME
P16A_SECRET_FILE=$RUN/secret P16A_REPORT_FILE=$RUN/report.json PROBE_ENDPOINT=ws://127.0.0.1:$PORT/ws PROBE_DIR=$RUN/proj PATH=$RUN/bin:$PATH \
  timeout 100 flutter test tool/qa/pins_paseo_probe_test.dart 2>&1 | grep -E "All tests passed|Some tests failed"
cat $RUN/report.json; echo
echo "--- the OLD command on the same daemon version:"
PATH=$D/pas/$N/node_modules/.bin:$PATH $D/pas/$N/node_modules/.bin/paseo start --foreground --home $RUN/home2 --listen 127.0.0.1:16779 --no-relay --no-web-ui --no-inject-mcp < /dev/null 2>&1 | head -2 | cut -c1-160
[ -n "$PID" ] && kill $PID; sleep 2; echo "left: $(ss -ltn | grep -c ":$PORT ")"
shred -u $RUN/secret
