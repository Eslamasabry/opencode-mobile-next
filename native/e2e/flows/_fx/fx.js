// Asks a fixture to do something for an [fx] step (contract for S0-FX: POST {FX_CTL}/fx).
// env: TARGET (FX-OC2, FX-OC2-B, fx-proxy, FX-TLS, FX-PASEO, FX-CODEX, FX-GH, FX-GIT, FX-LINUXDL, FX-USAGE,
//      FX-NODE, FX-AUTH, stub-model), ACTION (kebab-case verb), ARGS (JSON object as a string, optional).
// The fixture answers 200 with a JSON object, which lands in output.fxr; anything else fails the step.
var fxArgs = (typeof ARGS === 'undefined' || !ARGS) ? {} : JSON.parse(ARGS);
var res = http.post(FX_CTL + '/fx', {
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ target: TARGET, action: ACTION, args: fxArgs })
});
if (res.status !== 200) {
  throw new Error('fixture ' + TARGET + ' ' + ACTION + ' failed: ' + res.status);
}
output.fxr = json(res.body);
