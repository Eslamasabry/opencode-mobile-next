# Synthetic Codex journey fixture

This standard-library-only Python fixture binds to loopback and never invokes
a model, account, provider, shell command or external service. The literal
`fixture-token` is a public test value; never use it for a real server.

Start a fresh fixture for each smoke run:

```sh
python3 tool/qa/codex_fixture/fixture_server.py
```

In another terminal:

```sh
python3 tool/qa/codex_fixture/smoke_client.py
adb reverse tcp:4126 tcp:4126
```

The smoke output must report successful initialization/history, a completed
normal turn, accepted approval, completed interrupt, and `true` for bad-auth
and forced-disconnect checks. The fixture starts turn IDs from one; restart it
before rerunning the smoke client.

Add a Codex connection in the app using `ws://127.0.0.1:4126`, folder
`/tmp/ocmn-codex-fixture/project`, and the public test token above.
`OCMN_CODEX_FIXTURE_DIR` and `OCMN_CODEX_FIXTURE_PORT` override these defaults;
set the same values for both scripts and update the app/ADB configuration.

Ordinary prompts produce synthetic streamed text. A prompt containing
`approval` waits for an approval reply. A prompt containing `long-running`
remains busy after its response item completes until interrupted. Touch
`force_disconnect` in the fixture directory to simulate loss; remove it to
permit reconnect. History is retained in memory across socket replacement.

Only method/status metadata is logged, in the fixture directory. Stop the
fixture using Ctrl-C in its owning terminal. Do not expose it remotely.
