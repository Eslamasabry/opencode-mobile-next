# S0-FX — fixture servers (report, 2026-11-01)

Unit row: plan 15 §4.2 `S0-FX`. Write set: `native/fixtures/**` (except `native/fixtures/oc2/**`) and
`native/e2e/fixtures/**`; this report. Branch `native/fx-2026-11-01`. Plan sections used: 14 §4.4–4.5, the
"Fixtures to build once" and "Stub model magic prompts" parts of `catalog/e2e-flows.md`.

## Status

| State | |
|---|---|
| Implemented | all 10 fixtures: FX-OC2 (+ stub model), FX-OC2-B, FX-TLS, FX-PASEO, FX-CODEX, FX-GH, FX-GIT, FX-LINUXDL, FX-USAGE, FX-AUTH |
| Verified | 157 self-tests green twice in this container (Linux, Python 3.13.18, OpenSSL 3.0.13, git 2.43.0) |
| Not verified | FX-OC2 against a real `opencode2` 2.1.1, FX-PASEO against a real `paseo` 0.11.2 (neither is installed here; tested against `tests/fake_opencode2.py` and the stub daemon); FX-TLS `trusted` listener (needs the PC's tailscale cert); any flow on an emulator |
| Committed / pushed | yes, `native/fx-2026-11-01`, every commit `[skip ci]` |

Finish line ("start, stop and log as e2e-flows says; started and stopped by exact PID"): met for every fixture
in this container. It still needs one run on the PC with the real `opencode2` and `paseo` (open problem 1).

## Stack: Python 3 standard library

The brief asked for "the simplest stack (Kotlin/JVM or Node)". I chose **Python 3.10+ stdlib** instead:

- plan 14 §4.4 already names Python for these servers (`stub_model.py`, `fx_proxy.py`, "Python `ssl` reverse
  proxies") and §4.5 makes the runner `run.py` Python stdlib run with `-I`, so one language serves runner and
  fixtures;
- the two existing twins it says to extend (`tool/qa/issue87/mock_llm.py`, `tool/qa/codex_fixture/`) are Python;
- no build, no Gradle (so no catalog or `settings.gradle.kts` change), no dependency download; `ssl`, `http.server`,
  `asyncio`, `pty` and `subprocess` cover TLS, SSE, WebSocket, terminals and CGI.

Recorded as a deviation from the brief, not from the plan.

## What was built (`native/e2e/fixtures/`)

| Path | What |
|---|---|
| `fxctl.py` | `start / stop / status / list`. Writes `pids/<ID>.json` (pid, start time, script, run dir), waits for `<ID>/ready.json` with the same pid, logs to `logs/<ID>.log`. `stop` signals only a PID whose `/proc/<pid>/cmdline` and start time match the record (PID reuse is left alone), SIGTERM then SIGKILL; then the fixture's own `children.json` PIDs. Per-run passwords go to `secrets.env` (0600), never printed. |
| `fxlib/core.py` | Shared plumbing: ports from `ports.json` with overrides, metadata-only request log (method, path without query, status, auth **scheme**, transport), secret scrubber, fault rules (`delay`, `refuse`, `hang` + `release`, `drop`, `cut`), control API (`/health`, `/state`, `/log`, `/fault`, `/reset`, fixture actions), child tracking (`spawn`, `adopt`) and stop by exact PID. |
| `ports.json` | the one list of ports (plan 14 §4.4 values; FX-PASEO 4310, FX-USAGE 8703, FX-AUTH 8704; control ports added). |
| `fixture-data.json` | literal values flows read as `${output.fx.<name>}`; `test_fixture_data.py` keeps it equal to the servers. |
| `servers/*.py` | the ten fixtures; each docstring is its contract (flags, env, control actions, ready.json). |
| `tests/` | stdlib `unittest` self-tests; `fxtest.FixtureCase` starts a fixture on free ports through `fxctl`, and teardown asserts its PID and every child PID are gone. |
| `README.md` | how to run, run-dir layout, control API, per-fixture table. |

Per fixture, briefly (details in each server's docstring):

- **FX-OC2** — launcher for `opencode2 serve` (`FX_OC2_BIN`, password via `OPENCODE_PASSWORD` env only) with an
  isolated HOME/XDG root, projects `app` (Git; `README.md`, `notes.txt`, `data.csv`, `lib/main.dart` ≥ 42 lines,
  a binary) and `other`, asks-first permissions, the fake provider key registered, seeded sessions (fixed `ses_`
  ids; `long-history` 400 messages and `perf-history` via `/api/session/import`). A front proxy on 4097 forwards
  byte for byte (SSE flushed per chunk, WebSocket upgrades tunnelled), logs, applies faults, `drop-streams`,
  `lose-response` (forward, then never answer: E2E-10 "never twice"), and routes `/ocmn/quota/*` to FX-USAGE.
  Missing binary: fails in seconds with exit 2 and a plain message.
- **stub_model.py** — OpenAI-compatible, deterministic: every `e2e:` prompt of e2e-flows plus `perf:stream-6000`,
  `perf:stream-short`, `perf:tools-200`, `perf:history-5000` (a seeded session). Tool-call shapes are data
  (`TOOL_SHAPES`), chosen by what the server offers. Logs command names, never prompt text. `FX_STUB_SPEED`.
- **FX-OC2-B** — same launcher on 4098: shared ids with FX-OC2 (different titles), ISO-placeholder and leaked
  `<tool_call>` titles, an extra project `web`.
- **FX-TLS** — per-run certs (A/B swappable, expired 2020–2021, wrong name) made with `openssl ca -selfsign`;
  optional trusted cert; redirector; plain HTTP on a private and a public-looking name (`oc-e2e.example.net`).
  Logs `basic_on_plaintext` per request (E2E-04) and failed handshakes; ready.json has the DER SHA-256 pins.
- **FX-PASEO** — real daemon launcher (`paseo daemon run --home`, isolated HOME, fake `claude`/`pi` first on PATH,
  Claude history JSONL on disk, a byte proxy so `drop-socket` works) or `--mode stub`: a daemon stub with agents,
  providers, scripted Claude/Pi turns, steer, cancel, archive (broadcast), Claude import, update with versions, and
  real PTY terminals streaming binary frames.
- **FX-CODEX** — twin of `tool/qa/codex_fixture/fixture_server.py` on fxlib, plus `turn/steer`
  (`lib/codex/gateway.dart:528-590`), turns that survive a socket drop, `/readyz` `/healthz`, and controls
  `drop-socket`, `refuse-connections`, `health`, `fail-method`.
- **FX-GH** — device flow (`/login/device/code`, `/login/oauth/access_token` with pending, slow_down, denied,
  expired, refresh), `/user` (401 on a rejected token), `releases/latest`. Its token is `FX_GIT_PASSWORD`, so
  sign-in then push to FX-GIT works.
- **FX-GIT** — bare `app.git` over `git daemon` (child, exact PID) and smart HTTP (`git http-backend`); HTTP push
  needs the per-run password; `reject-push` installs a refusing pre-receive hook.
- **FX-LINUXDL** — mirror at `/<host>/<path>` of the real URLs, Range/206/416/If-Range, ETag; `throttle`, `cut`,
  `corrupt` (one flipped byte, stable across resumes), `offline`; manifest hashed at start. Pins copied from
  `BuiltinLinux.kt:4490-4499`, `setup_base_script.dart:11,23`, `components.dart:249-262,374-381`,
  `local_agents_script.dart:22-28`, `claude_scripts.dart:5-9,38`.
- **FX-USAGE** — `/ocmn/quota/v1[/minimax|glm|claude]` snapshots that pass `ProviderQuotaSnapshot.fromJson`'s
  rules; per provider `ok`, `stale` (reset passed), `expired` (authRequired), `unconfigured`, `unavailable`,
  `offline`, `unsupported`, `denied`; Basic password checked against the OC2 passwords.
- **FX-AUTH** — serves per-agent shims (`/shim/<agent>`) that answer `--version` offline and, for login, print the
  fixed code `FXAU-4242` and wait for `approve` / `deny` / `expire`.

## Catalog rows

The fixtures own no catalog row; they make rows observable in the flows. Self-tests are named after the row they
serve where one fits (`test_B_SEC_084_…`, `test_B_NAV_079_…`, `test_B_AGENT_073_…`, `test_B_AGENT_129_…`,
`test_B_LINUX_125_…`, `test_B_MIGR_012_…`), otherwise `test_FX_<ID>_…`. Rows whose flow steps depend on a fixture
behaviour built here: B-SEC-007 / B-SEC-124 (plaintext log, FX-TLS + FX-OC2 log), B-NAV-008 (cert change, swap),
B-PROTO-088 / B-STATE-040 (`lose-response`), B-PROTO-061 (`long-history`), B-SEC-084, B-BUILD-014 (FX-GH, FX-GIT),
B-NAV-079, B-AGENT-073 (FX-USAGE), B-AGENT-129, B-LINUX-125 (FX-AUTH), B-LINUX-119 (FX-LINUXDL cut and resume),
B-MIGR-012 (`releases/latest`), and the E2E-29/30 rows through FX-CODEX/FX-PASEO.

## Commands and results

```sh
python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures
# Ran 157 tests in 35.4 s  OK   (second run: 157 in 36.0 s, OK)
```

| File | Tests |
|---|---|
| test_fxctl.py (launcher, faults, scrubber) | 16 |
| test_stub_model.py | 28 |
| test_fx_oc2.py (FX-OC2, FX-OC2-B against the fake server) | 29 |
| test_fx_tls.py | 13 |
| test_fx_git.py | 6 |
| test_fx_linuxdl.py | 8 |
| test_fx_codex.py | 11 |
| test_fx_paseo.py (stub mode) | 16 |
| test_fx_gh.py | 9 |
| test_fx_usage.py | 9 |
| test_fx_auth.py | 6 |
| test_fixture_data.py | 6 |

No fixture process remained after the runs (checked by PID). No Android SDK was installed: this unit does not
need Android or Gradle. Before pushing, `git grep` over `native/e2e/fixtures` found no private address or real email;
the only `user:pw@example.com` is the e2e-flows test link.

## Resolved versions

Python 3.13.18 (code targets 3.10+), OpenSSL 3.0.13, git 2.43.0. Pins the fixtures assume: `opencode2` 2.1.1,
Paseo 0.11.2 (plan 14 §4.4), Codex app-server as `tool/qa/codex_fixture` (reports `0.153.4`).

## Deviations from the plan

1. Python instead of Kotlin/JVM or Node (above).
2. The fixtures live in `native/e2e/fixtures/` (plan 14 §4.1); nothing was written to `native/fixtures/`.
3. FX-LINUXDL throttles and cuts itself instead of through `fx_proxy.py`; **fx-proxy is not built** (not in this
   unit's list; plan 14 §4.4 still names it for E2E-05 s9, 10, 13, 17).
4. Each fixture has a separate control port (`ctl`) in `ports.json`; plan 14 shows only the service ports.
5. FX-USAGE is reached through the FX-OC2 front (`/ocmn/quota/*`), because the app reads quota from the saved
   server's own origin (`lib/quota/provider_quota_client.dart:175-187`).
6. FX-AUTH is a shim the runner installs over the agent binary inside built-in Linux (an `[adb]` hook), since the
   real CLIs would contact the real providers.
7. FX-NODE is not built (after the switch, D6).

## Catalog requests

None (no Gradle module, no library).

## Requests to other owners

- **S0-FLOWS / runner:** read `secrets.env`; map `oc-e2e.example.net` to the PC for FX-TLS `plain_public`; install
  the FX-AUTH shims (`curl http://10.0.2.2:8704/shim/<agent>`); fill the FX-LINUXDL mirror and `manifest.json`.
- **QaHooks owner:** confirm `downloadBaseUrl` maps `https://H/P` to `<base>/H/P` (FX-LINUXDL layout) and that
  `githubBaseUrl` serves both `github.com` and `api.github.com` paths from one origin (FX-GH does).

## Open problems

1. **Real binaries untested.** On the PC, start FX-OC2 with `opencode2` 2.1.1 and FX-PASEO with `paseo` 0.11.2 once.
   Unverified [U] points to check then: the opencode2 config keys (top-level `model`, top-level `permissions`
   ruleset, permission names `edit`/`write`), the tool-call shapes for `edit`, `bash` and especially
   `question` (OC2 turned questions into forms; if no model-callable tool exists, `e2e:question`/`choice`/`choice4`
   cannot work as written), the `/api/session/import` body and whether it keeps given ids, Host rewriting
   in the front proxy; Paseo real mode as a whole, Pi's event names and version, some response names.
2. E2E-37's second provider "connected but not loaded" is not seeded.
3. Codex `/readyz` `/healthz` paths, steer error codes and the re-sent approval after reconnect are [U].
4. FX-TLS does not tunnel WebSocket upgrades (FX-OC2's front does); trusted listener untested.
5. fx-proxy (deviation 3) needs an owner.
