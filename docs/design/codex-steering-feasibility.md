# Direct Codex steering feasibility

Checked 2026-10-14 for app-server **0.153.4**, the version pinned by
`lib/codex/gateway.dart`. Steering exists and is callable without opting into
experimental APIs. The app can add input to the active turn without interrupting
it or creating a replacement turn.

## Evidence and scope

The checkout's `lib/codex/` has a handwritten JSON-RPC adapter; it does not vendor
an app-server schema. Before this change its prompt path called `turn/start` and
its cancel path called `turn/interrupt`. The exact official npm package
[`@openai/codex@0.153.4`](https://registry.npmjs.org/@openai%2fcodex/0.153.4)
was already installed in the isolated scratch prefix
`/home/owner/Storage/tmp/agent-handoff-codex-01534`. Its own
`app-server generate-ts` / `generate-json-schema` output was inspected, not a
newer system installation or an inferred endpoint.

Relevant generated definitions:

- `ClientRequest.ts`: public request union includes `turn/steer`.
- `v2/TurnSteerParams.ts`: required active-turn precondition and input shape.
- `v2/TurnSteerResponse.ts`: accepted turn identity.
- `v2/UserInput.ts`: the same text input shape as `turn/start`.
- `v2/TurnStartParams.ts`: comments also acknowledge that `turn/start` can steer
  an already-active turn. Its input lacks the explicit expected-turn guard,
  so the app uses `turn/steer` for deliberate steering.
- `v2/TurnStartedNotification.ts` / `TurnCompletedNotification.ts`: thread and
  turn identities used to track the active turn.

SHA-256 evidence:

| Artifact | SHA-256 |
| --- | --- |
| Exact linux-x64 Codex executable | `56ef98ab4032d317ab26e9b5e5a175650717351edb16ed9cde0cb6d1734d62da` |
| Generated `TurnSteerParams.ts` | `47cdc50a4b0b350c00d7b921beb6b620f63642684f4b48a1e574089e8f1690be` |
| Generated `TurnSteerResponse.ts` | `b77bae04f63621580d71aecab4c19c9dcd8d9147be1c4b07fe809dcaf584212b` |

## Callable request

After the normal `initialize` / `initialized` exchange, send:

```json
{
  "id": 6,
  "method": "turn/steer",
  "params": {
    "threadId": "thread-id",
    "expectedTurnId": "currently-active-turn-id",
    "input": [
      {"type": "text", "text": "Please also check the parser.", "text_elements": []}
    ]
  }
}
```

Success is `{"id":6,"result":{"turnId":"currently-active-turn-id"}}`.
`clientUserMessageId` is optional. Model, reasoning effort, working directory,
sandbox and approval-policy overrides are absent from this method. Steering
therefore preserves the running turn's settings. The adapter remains text-only;
the protocol's other `UserInput` variants do not imply app attachment support.

`expectedTurnId` is mandatory. The server rejects input if that identity no
longer names the active turn. The app must not replace it with a newer identity
and retry: that could steer different work than the user intended. Do not fall
back to `turn/start`, an interrupt, a queue, or a reconnect-and-replay after a
steering failure. A lost acknowledgement leaves delivery uncertain.

## Isolated real-binary probe

The exact binary was launched via `app-server --listen stdio://` with:

- an empty scratch `CODEX_HOME` and scratch `XDG_CONFIG_HOME`;
- a scratch project, an ephemeral thread, `approvalPolicy: never`, and a
  read-only sandbox;
- an explicitly configured synthetic provider using a loopback HTTP Responses
  fixture, with `requires_openai_auth=false`;
- an allowlisted child environment containing no provider credentials;
- `initialize.capabilities.experimentalApi: false`.

No owner server, account, credential store, model subscription, or real model
was used. Only the child process created by the probe was terminated.
The initialization response identified app-server `0.153.4`.

| Request / state | Observed response |
| --- | --- |
| `turn/steer` on an idle newly created thread | `-32600`, `no active turn to steer` |
| `turn/start` with synthetic text | `turn.status: inProgress` and a turn ID |
| `turn/steer` with a different `expectedTurnId` | `-32600`, `expected active turn id … but found …` |
| `turn/steer` with the active ID and synthetic text | Success with **the same** turn ID |
| `turn/steer` with empty `input: []` | `-32600`, `input must not be empty` |
| Explicit `turn/interrupt`, solely to clean up the probe | Success; `turn/completed` reported `interrupted` |
| `turn/steer` after that completion | `-32600`, `no active turn to steer` |

The probe proves method admission, active-turn identity preservation, and
precondition/error behavior on the real pinned executable. It does not qualify
when a live model consumes the added input, provider output, or phone UI behavior.
The probe interrupted immediately after admission; no claim is made that the
synthetic steering text reached a model-generated answer.

Local scratch evidence is in `/home/owner/Storage/tmp/codex-steer-01534`:
`probe.py`, `results.json`, and `notifications.json`. The scratch project and
isolated Codex home also live there; none is an owner project. Artifact hashes:

- `probe.py`: `185ded18004a42ba573fca49ccba3535420e7420aedee2efd3b2564871131ba5`
- `results.json`: `834bb3ac94732b007446f127f3dcdce74b111d63ae8e16aa3ff64f966bf2b259`

See [steering-contract.md](steering-contract.md) for the domain/UI contract and
runtime capability matrix. Flutter was not run for this feasibility check.
