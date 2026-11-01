# Fixture servers (FX-*)

Fake servers for the device flows (`docs/design/native-rewrite-2026-11-01/catalog/e2e-flows.md`,
plan 14 §4.4). They are shared by the Flutter reference app and the native app. They are Python 3.10+
standard library only, run with `python3 -I`, and need the `git` and `openssl` CLIs (FX-GIT, FX-TLS).
FX-OC2 and FX-PASEO also wrap real binaries (`opencode2` 2.1.1, `paseo` 0.11.2) when they are installed.

## Run

```sh
F=native/e2e/fixtures
python3 -I $F/fxctl.py list                                  # the fixtures that exist
python3 -I $F/fxctl.py start  --run-dir "$OUT" FX-OC2 FX-OC2-B FX-TLS
python3 -I $F/fxctl.py status --run-dir "$OUT"
python3 -I $F/fxctl.py stop   --run-dir "$OUT"               # all of them, each by exact PID
```

- Ports come from [`ports.json`](ports.json) (the one list). Override with `--port FX-GH:http=18701`.
- Everything binds `127.0.0.1`; the emulator reaches it as `10.0.2.2`.
- Per-run passwords (`FX_OC2_PASSWORD`, `FX_OC2_B_PASSWORD`, `FX_GIT_PASSWORD`) are generated into
  `$OUT/secrets.env` (mode 0600) unless set in the environment. They are never printed. The runner
  passes them to Maestro with `-e` and scrubs them from every output.
- **Stopping is by exact PID only.** `fxctl` checks `/proc/<pid>/cmdline` and the start time against
  the PID file before it signals; children a fixture started (opencode2, git daemon, paseo, PTY
  shells) are stopped by that fixture, by PID, and again by `fxctl` if one outlived it. Never use
  `pkill -f` on the PC: it also matches the owner's live server.

## Run-dir layout

| Path | What |
|---|---|
| `pids/<ID>.json` | PID, start time, script and run dir of what was started |
| `logs/<ID>.log` | the server's stdout/stderr (secrets scrubbed) |
| `<ID>/ready.json` | written once it listens: ports, URLs, seeded ids, fingerprints (no secrets) |
| `<ID>/requests.jsonl` | request log: method, path without query, status, auth **scheme**, transport, fault |
| `<ID>/children.json` | exact PIDs of the processes the fixture started |

## Control API (every fixture, on its `ctl` port)

| Call | Does |
|---|---|
| `GET /health`, `GET /state`, `GET /log` | pid and ports; fixture state; the request log |
| `POST /fault {path, method?, action, ms?, status?, count?}` | `delay`, `refuse`, `hang` (until `POST /release`), `drop`, `cut` (end a stream after ms) |
| `POST /faults/clear`, `POST /release`, `POST /log/clear` | |
| `POST /reset` | faults, log and the fixture's own state back to the seed |

Fixture-specific actions are listed in each server's docstring:

| Id | Script | What | Main controls |
|---|---|---|---|
| FX-OC2 | `servers/fx_oc2.py` | real `opencode2 serve` with isolated data root, projects `app`/`other`, seeded sessions, the `e2e:` stub model (`servers/stub_model.py`), and a front proxy with request log and faults; `/ocmn/quota/*` goes to FX-USAGE | `drop-streams`, `lose-response`, `prompt`, `model-speed` |
| FX-OC2-B | `servers/fx_oc2_b.py` | second FX-OC2: shared session ids, ISO-placeholder and leaked-markup titles, project `web` | as FX-OC2 |
| FX-TLS | `servers/fx_tls.py` | per-run self-signed A/B, expired and wrong-name certs, optional trusted cert, redirector, plain HTTP on a private and a public-looking name, all in front of FX-OC2 | `swap-cert`, `set-redirect`, `set-upstream` |
| FX-PASEO | `servers/fx_paseo.py` | real Paseo daemon with fake `claude`/`pi` agents and Claude history on disk, or a stub daemon | `drop-socket`, `set-version`, `terminal-emit`, `archive-agent` |
| FX-CODEX | `servers/fx_codex.py` | Codex app-server twin of `tool/qa/codex_fixture` + steer | `drop-socket`, `refuse-connections`, `health`, `fail-method` |
| FX-GH | `servers/fx_gh.py` | GitHub device flow, `/user`, `releases/latest` | `approve`, `deny`, `expire`, `slow-down`, `reject-tokens`, `set-release` |
| FX-GIT | `servers/fx_git.py` | bare `app.git` over `git daemon` and smart HTTP | `reject-push`, `refs` |
| FX-LINUXDL | `servers/fx_linuxdl.py` | mirror of the pinned rootfs/OpenCode/Node/Claude downloads at `/<host>/<path>`, with Range | `throttle`, `cut`, `corrupt`, `offline` |
| FX-USAGE | `servers/fx_usage.py` | `/ocmn/quota/v1[/provider]` snapshots | `set {provider, mode}` |
| FX-AUTH | `servers/fx_auth.py` | sign-in shims for phone agents; fixed code `FXAU-4242` | `approve`, `deny`, `expire` |

Flows read literal values (`${output.fx.<name>}`) from [`fixture-data.json`](fixture-data.json);
`tests/test_fixture_data.py` keeps it equal to the servers.

## Self-tests

```sh
python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures
```

About 40 s. FX-OC2 runs against `tests/fake_opencode2.py` and FX-PASEO in stub mode when the real
binaries are absent.
