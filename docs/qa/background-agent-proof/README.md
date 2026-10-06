# Background subagent proof — real OpenCode 2 server, real model

Date 2026-09-10. Closes the P1 item "background-agent verification" from
`docs/qa/oc2-setup/README.md`: a parent session launches a background
child, the app is killed and cold-started, the child's result reaches the
right parent exactly once.

## Setup

| Piece | Value |
|---|---|
| Server | pinned `opencode2 v0.0.0-beta-18600` (`cli-linux-x64` binary), `serve --hostname 127.0.0.1 --port 4097`, `OPENCODE_SERVER_PASSWORD` set, on this PC |
| Project | `/home/eslam/Storage/Code/oc-bg-proof` (scratch git repo, one `calc.py`) |
| Model | `zai-coding-plan/glm-5.3-flash` — the only configured provider whose credentials this server accepted (see findings) |
| Device | Android 14 / API 34 x86_64 emulator `OCMN_UI_Refinement`, app 1.0.39 (40) x86_64 release, `adb reverse tcp:4097` so the app used `http://127.0.0.1:4097` |
| Not covered | physical ARM64 phone (wireless ADB is off on `the owner's phone`), Arabic locale on device |

## Journey and evidence

1. Saved the server from the OpenCode 2 editor and connected (after fix 1 below).
2. New session, model switched in the picker (after fix 3), prompt: *"Use a background subagent to add a function multiply … Reply immediately that you delegated it."*
3. Parent replied within seconds: *"Delegated. A background subagent (session `ses_f7717c0…`) is now adding…"*. The `subagent` tool call carried `background: true` and the child session id in its metadata.
4. ~40 s later the parent showed one **Background result** card — "Add multiply function to calc.py · General · Completed", the child's code block, **Open subagent session** — followed by the parent's own follow-up ("The background subagent finished ✅ …"). [live-result-card.png](live-result-card.png), [events.json](events.json) (107 events, one `session.created` for the child), [parent-messages.json](parent-messages.json).
5. `am force-stop`, relaunch: Workspace listed the parent only (the child is not mixed into the main list); reopening the parent showed exactly **one** result card and the same transcript. [cold-restart-parent.png](cold-restart-parent.png).
6. `calc.py` on disk contains `multiply(a, b)` appended after `add`.

## Findings fixed on the way (each with a failing test first)

1. **Connect failed with a raw cast error** — `fix(connect)`: a profile saved as OpenCode 1 against an OpenCode 2 host gets HTML from `/global/health` (200, `text/html`); the v1 client threw `type 'String' is not a subtype of type 'Map'` and no redetection ran. Now a typed `UnexpectedHealthShape` signal triggers flavor redetection like 401/404/405.
2. **Blank red error banner** — `fix(chat)`: the server emits `session.execution.failed {type: 'provider.auth', message: ''}`; the app showed an empty banner. The mapper forwards the type as the error name and substitutes readable fallback copy. [before](bug-blank-error-banner-before.png).
3. **"This choice is no longer available"** after a successful model save — `fix(models)`: the server stores no-variant as `variant: "default"`, the app compares with `''`. Normalised in the v2 session mapper. [before](bug-picker-selection-gone-before.png).
4. **Completed tool shown as "Not run"** — `fix(tools)`: beta-18600 stamps `executed: false` on tools it plainly ran (provider-side execution flag). The outcome now wins over the flag.

## Observed, not fixed

- The model picker lists some models twice with an identical provider label
  ("GPT-5.6 Sol 1M (OAuth) · OpenAI" ×2, "GPT-5.6 Luna" ×2). Likely two
  provider entries sharing a display name; needs a catalog-level fix.
- Provider credential state on this PC: Anthropic OAuth token invalid, Google
  key invalid, OpenCode Zen insufficient balance, OpenAI OAuth rejected by
  OC2. These are account states, not app defects, but the OpenAI one is what
  exposed finding 2.
