// Runs one allowlisted host hook through the runner (native/e2e/runner/hooks.py).
// env: HOOK (name), ARGS (a JSON object as a string, optional).
// Globals from run.py: HOOK_PORT, HOOK_TOKEN. The result lands in output.hook.
var hookArgs = (typeof ARGS === 'undefined' || !ARGS) ? '{}' : ARGS;
var res = http.post('http://127.0.0.1:' + HOOK_PORT + '/hook/' + HOOK, {
  headers: { 'Content-Type': 'application/json', 'X-Hook-Token': HOOK_TOKEN },
  body: hookArgs
});
if (res.status !== 200) {
  throw new Error('hook ' + HOOK + ' failed: ' + res.status + ' ' + res.body);
}
output.hook = json(res.body);
