# One backend per conversation (2026-10-05)

## Problem

The app was built around one `ConnectionController` talking to one OpenCode
server. Claude Code on the phone was added by **swapping that controller's
gateway**: opening a Claude conversation calls `_paActivateRoute`, which
reconnects the whole controller (`connect(profile)`) to a `PaseoGateway`;
opening an OpenCode conversation reconnects it back (`_paClearRoute`).

Everything the chat screen reads (≈110 controller members: models, approvals,
busy state, capabilities, banner status) therefore belongs to whichever
backend the controller happens to point at. Symptoms seen on the phone:

- the model chip shows the OpenCode server's "build · Z.AI" while Claude's
  own models load (`modelForSession` is OpenCode state until the swap ends);
- the conversations list paints in two waves (OpenCode first, then each
  Claude folder);
- the banner, recovery watchdog, reopen rules and auto-approve each needed a
  special case for "the route", and each broke separately.

## Rule

**A conversation belongs to exactly one backend, and its screen talks only
to that backend's controller.** OpenCode (v1/v2) and Claude Code on this
phone are each one backend with its own controller instance. No controller
ever swaps backends to serve another one's conversation.

## Shape

- `ConnectionController` gains a role: `primary` (today's controller: profile
  services, monitors, notifications, background) or `agent` (a conversation
  backend only: no monitors, widgets, background or profile switching).
  Today's `isIsolated` (demo / watched worker) stays as is.
- The Claude backend is an `agent` controller connected to a **virtual Paseo
  profile** (`ServerBackend.paseo`, loopback daemon, credential from the
  phone agent host). The ordinary Paseo connect path already works for saved
  Paseo servers, so no route code is needed.
  - Its id is `<phoneProfileId>~agents`, never saved in the profile list. Its
    preference keys are `oc.<what>.<phoneProfileId>~agents`, and
    `deleteProfileAndLocalData` sweeps them with the phone profile.
- `PhoneAgentsHub` (new, owned by the primary controller) creates that
  controller on first need. It starts the helper when it is stopped, and it
  hands the controller to whoever opens a Claude conversation. It replaces
  `_phoneAgentRoute`, `_paActivateRoute`, `_paClearRoute`, the route watchdog
  and `phoneAgentRouteName`.
- The chat route for a Claude conversation is wrapped in
  `ProviderScope(overrides: [connProvider.overrideWithValue(agentController)])`,
  the pattern the demo screen already uses. The chat screen code does not
  change: its model chip, approvals, auto-approve, banner and busy state are
  simply Claude's.
- New conversation: once an agent is chosen, the model chip and Start use that
  backend's controller (its own catalog). The `AgentModelChip` side path goes.
- Conversations list: the merged feed reports `loading` until every source
  has answered once (at most 2 s), then paints once.

## Slices

1. **Agent controller + scoped chat.**
   - Finish line: open a Claude conversation and the OpenCode connection is
     not reconnected; the model chip lists only Claude models; approvals,
     auto-approve and the banner come from Claude's own controller; going
     back to an OpenCode conversation is instant.
   - Remove the route swap.
2. **New conversation on the backend's controller** (model chip and Start),
   then delete `AgentModelChip` and `agentModels`.
3. **One-paint conversations list.**
4. **Helper lifecycle in the hub:** start on demand, reconnect through the
   agent controller's normal reconnect, no separate watchdog.
5. (Separate decision.) Replace the Paseo helper with a direct ACP client
   behind the same agent-controller seam.

**Non-goals:** no change to the OpenCode v1/v2 paths and no visual redesign.
The AI Team keeps its own isolated controllers.

## Checks

- Controller tests: a Claude conversation never reconnects the primary.
- Agent controller deletion sweep.
- Merged feed single paint.
- Emulator proof with the real Claude sign-in:
  1. start a conversation and get a reply;
  2. switch to OpenCode and back;
  3. confirm the model chip shows only Claude models;
  4. confirm auto-approve works;
  5. kill the helper, then restart the app.
