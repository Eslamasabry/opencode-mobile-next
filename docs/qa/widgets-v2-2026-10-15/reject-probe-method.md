# Scratch server Reject probes

`tool/qa/widget_reject_probe.py` starts a private loopback server with new XDG
config, data, cache, and state directories under a mode-700 `/tmp` directory.
It uses the public `opencode/big-pickle` model, creates one scratch session with
all permissions set to ask, requests `sleep 50`, waits for a real pending tool
permission, rejects that exact request, and samples the server status every
250 ms. This exercises a model-generated tool request, not a synthetic pending
permission or an evaluator-only check. No application or emulator participates.

The retained script's prompts are exactly:

- OC2: `Use the shell tool to run exactly: sleep 50.`
- OC1: `Use the bash tool to run exactly: sleep 50.`

The initial `reject-probe-oc2.json` and `reject-probe-oc1.json` probes also told
the model to finish after a rejection. Their transcript outcomes are retained,
but the `*-repeat.json` runs remove that instruction and are the primary status
evidence. The original OC2 transcript records an unexecuted shell tool with
`aborted` / `The user declined this tool call`, and an assistant step with
`aborted` / `Step interrupted`.

Run with already available standalone binaries (the binary argument is required;
the script never installs packages):

```sh
python3 tool/qa/widget_reject_probe.py \
  --binary /path/to/opencode-2.0.29 \
  --flavor oc2 --port 4097 --output /tmp/reject-oc2.json
python3 tool/qa/widget_reject_probe.py \
  --binary /path/to/opencode-1.19.0 \
  --flavor oc1 --port 4098 --output /tmp/reject-oc1.json
```

The host's PATH `opencode2` and `opencode` launchers were unusable because their
postinstall scripts had not run. For this check the official
`@opencode/cli-linux-x64@2.0.29` npm tarball was downloaded and extracted under
`/tmp`, then its standalone binary was executed directly. OC1 used the existing
`/tmp/relengine-oc1-1190/opencode` binary. No package manager installation ran and
no `/home/owner` package files changed.

OC2 authenticates with Basic user `opencode`; the generated password is parsed
from a private server log and never included in output. OC1 uses a fresh random
server password supplied only through its child environment. No provider auth
is copied. Status evidence contains no session IDs, project paths, prompts,
credentials, or raw config responses. Each server is stopped through its own
`Popen` process handle: TERM, bounded wait, then exact-PID KILL if necessary.
The script checks the requested port is free before launch.

The evidence is server-runtime truth for these versions, public model, and
requests. It does not establish widget rendering, arbitrary model behavior,
long-running continuations, or emulator/device qualification. The first idle
read is an upper bound on convergence, not an exact event timestamp.
