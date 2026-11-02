# P12-AGENTS — agents on the phone (parity push, 2026-11-03)

Branch: `claude/native-agents-parity-ee753d` (the session's assigned push branch; the brief named
`native/p12-agents-2026-11-03`, which this session may not push to). Base: `native/m1-2026-11-03` at `8a4eee69`.
Write set used: `native/engine/agents/**`, `native/protocol/paseo/**` (merged unit), `native/feature/agents/**`,
one new route file, one new words file pair, `androidApp/.../graph/AgentsWiring.kt`, two lines in
`androidApp/build.gradle.kts`, one `MODULE_OWNERS` row, and a 3-file, 12-line entry on the Servers screen.

**Shipping state (kept apart, AGENTS rule 8):**

| State | What |
|---|---|
| Implemented | Merges ported; Paseo backend bound in the app; Agents hub screen; entry from the Servers page |
| Verified here | Common code only, on a scratch JVM build (below): 231 paseo + 225 agents-engine + 10 hub tests, 0 failures; `AgentsWiring.kt` compiles on JVM with Metro |
| **Not verified here** | Android/Compose compile, the Compose screen test, `:androidApp:assemblePreviewDebug`, `:quality:test`, any device/emulator run |
| Committed / pushed | yes, to the branch above. No PR, no CI, no APK, no release |

## Blocker that shaped this unit: no Google Maven, no Android SDK

This container's network policy refuses `dl.google.com` (the Android SDK **and** Google Maven: `maven.google.com`
redirects there; mirrors are refused too). Without it the native build cannot even configure (the root build applies
AGP), and Compose/AndroidX cannot be resolved. To keep checking real code, I used a **scratch JVM-only Gradle build
outside the repo** (Maven Central through Google's Central mirror) that compiles the real `commonMain`/`desktopMain`
sources of `:core:{common,model,security,domain,testing,data}`, `:engine:{profiles,connection,agents}`,
`:protocol:paseo`, the `hub/` package of `:feature:agents` and `AgentsWiring.kt`, with explicit API, the plan's
opt-ins and the Metro compiler plugin, and runs their common + desktop tests. The integrator must run the real gates
(listed at the end) on a machine with Google Maven.

## Journeys now working (in code; device run pending)

1. **Claude Code / Pi through Paseo on a computer.** Settings gear → Servers → **Agents** → *Add a computer*:
   address (`ws://` on this device or a private network, or `wss://`), optional daemon password, project folder →
   **Check the connection** (hello + one scoped agent page + runtime list; names the ready agents; saves nothing) →
   **Save and connect** (profile `backend = paseo`, folder in `codexDirectory`, password in secure storage as
   `oc.codexToken.<id>` — the Flutter format) → the computer row shows *Connected* and its ready agents.
2. **Agent conversations in the one Conversations list.** A connected Paseo profile is a live graph in the
   `ConnectionRegistry`; `PreviewRuntime` already lists every live graph's `sessionPage()` into the merged `ChatFeed`,
   so the computer's agents appear beside OpenCode conversations with no new list. `PaseoGateway.sessionPage` lists
   the daemon's agents for that folder with no resumability filter, so agents whose runtime cannot resume are
   listed, not hidden.
3. **Start an agent conversation.** Computer row → **Start conversation** → `createConversation` → the chat opens on
   that computer (the chat screen is the shared `ServerGateway` one). The runtime (Claude Code, Pi, Codex if ready
   there) is chosen by the model picker's provider, which `PaseoGateway.providers()` fills.
4. **Profiles carried over from Flutter.** Any `oc.profiles` row with `"backend":"paseo"` now connects (before this
   unit the registry had no Paseo factory and failed with `Unavailable(backend.Paseo)`).
5. **This phone's agents.** The hub's *On this phone* part says in one line where agents run
   ("Agents run on the built-in server · Not available on this phone yet") — never an empty page — because this app
   build has no phone agent host bound (see "Not done").

## What was built

| Where | What |
|---|---|
| merge `origin/native/paseo-2026-11-01` | `:protocol:paseo` (transport, gateway, mappers, chat feed, requests, work inventory) added unchanged, then ported |
| merge `origin/native/agentse-2026-11-01` | `:engine:agents` catalog/pins, readiness, install, phone check, sign-in model, auth, auto-approval, memories, `AgentHost` seam. **Kept the integrated feed** (`feed/**` of S2-CHATS, used by `:feature:chats`); dropped agentse's older duplicate `ChatFeed`/`MergedChatFeed`/`SavedChatFeed` types |
| port to the flat contract (INTEGRATE-W1) | `Failure.Net.SignInNeeded` → `Failure.SignIn(SignInWhy)`, `ServerBusy` → `Busy(BusyKind)`, `Incompatible.Kind` → `IncompatibleKind`, `Net.{Conflict,NotFound,UnreadableReply}` → flat, `Storage(StorageProblem)` → `StorageRefused`, `Net.Unreachable(Timeout)` → `TimedOut`; `PaseoGatewayFactory` implements `backend` + `connect(spec)` and reads `ConnectionCredentials.secret` |
| `PaseoGatewayFactory.checkConnection` | the pairing check through the same factory/socket the connection uses; always closes what it opened |
| `:feature:agents` `hub/` | `AgentsPorts` (`AgentComputers`, `PhoneAgents`), `AgentsUi`/`AgentsActions`, `AgentsPresenter` (all logic, no Compose), `AgentsViewModel` (Metro `@ViewModelKey`) |
| `:feature:agents` `ui/AgentsScreen.kt`, `AgentsEntries.kt` | kit parts only (`KitScreen`, `KitTopBar`, `KitSectionLabel`, `KitRow`, `KitField`, `KitButton`, `KitNotice`, `KitErrorNotice`); plain line + Details for every failure; password never in saved state |
| `:ui:routes` `AgentsRoute.kt` (new file) | `AgentsRoute.Home` |
| `:ui:words` `strings_agents_hub.xml` (en + ar, new files) | 18 lines; everything else reuses existing translated words (`agentsSectionTitle`, `paseo*`, `servers*`, `codexProjectFolder`, `agentsState*`) |
| `androidApp/.../graph/AgentsWiring.kt` | `OkHttpPaseoSockets` (no proxy, no redirects, 20 s ping, 8 MiB cap, bearer subprotocol + header only when a password exists; 401/403 mapped like the JDK proof socket), `AppPaseoGatewayFactory` (into the `GatewayFactory` set; pins the gateway to the saved folder), `AppAgentComputers`, `AppPhoneAgents` (no host) |
| `androidApp/build.gradle.kts` | `:protocol:paseo`, `:feature:agents` |
| `feature/servers` (smallest reach) | `ServersActions.openAgents()` (default no-op, so existing fakes compile), the VM opens `AgentsRoute.Home`, one Secondary **Agents** button above *Add a server* |
| `MODULE_OWNERS` | `:protocol:paseo  S  backend-owner  slice-3` |

## Catalog rows covered (tests)

| Row | Test | Result here |
|---|---|---|
| B-PROTO-070 | `PaseoCheckConnectionTest` (3, new), `PaseoProbeAndFailureTest` (merged) | pass |
| B-AGENT-160 | `PaseoCheckConnectionTest`, `AgentsPresenterTest`, `AgentsScreenTest` | pass / pass / **not run** |
| B-AGENT-111 | `AgentsPresenterTest` (2), `AgentsScreenTest`, `HostAgentProvidersTest` (merged) | pass / **not run** / pass |
| B-AGENT-153 | `AgentsPresenterTest` (2), `PaseoChatFeedTest` (merged) | pass |
| B-NAV-030 | `AgentsPresenterTest` (2), `AgentsScreenTest`, `ChatFeedTest`/`MergedChatFeedTest` (integrated feed) | pass / **not run** / pass |
| All rows of the S3-PASEO and S3-AGENTSE reports | their merged tests, ported | 231 + 225 pass |

`AgentsScreenTest` (desktopTest, Compose, by visible text incl. Arabic) is written but could not be compiled or run
here.

Still missing: B-AGENT-002/003 (Codex direct account), B-AGENT-005/050 (per-agent phone probes), B-AGENT-014…021,
227, 228 (handoff, S5), B-AGENT-026…028 UI half and the sign-in terminal journey, B-LINUX-1xx phone install/check UI
rows, B-AGENT-401…409 Claude Agent via OpenCode UI.

## Not done (and why)

- **Phone agents in the built-in Linux (install/update state, phone check, sign-in terminals).** The engine side is
  merged (`readiness`, `install`, `check`, `signin`, `AgentHost`), `:android:agenthost` exists, but the app has no
  built-in Linux runtime bound (`:android:linux` is not an app dependency, S3-RTSPLIT not merged, FQCN clash with
  `:android:agenthost`), and `SignInTerminalViewModel` needs a `TerminalHost`. Kept visibly unavailable with a plain
  line. When the Linux unit binds a runtime: implement `AgentHost` over `AndroidAgentHost`, replace `AppPhoneAgents`
  with rows from `AgentReadiness.row`, and add the sign-in route.
- **Agent chip in the composer.** The composer is another unit's; for Paseo the agent is the model picker's
  provider (`PaseoGateway.providers()` + `seedDraftProvider`), so the chip is the picker's provider row there.
- **Claude Agent via OpenCode.** `claudeagent/ClaudeAgentTrial` is merged and tested; it needs the phone server
  (built-in Linux) to run, so no UI yet.
- **SavedChatFeed (`oc.agentFeed` rows).** Dropped with agentse's older feed types; the Paseo feed lists agents live.
  Offline rows for a computer that is away would need a `ChatFeedSource` over `oc.agentFeed` (engine, small).

## Deviations

- Pushed to `claude/native-agents-parity-ee753d`, not `native/p12-agents-2026-11-03` (session branch rule).
- Edited `feature/servers` (3 files, 12 lines) for reachability: Settings is not in the preview app yet and the gear
  opens Servers. When Settings lands, map `SettingsOutside.Agents` → `AgentsRoute.Home` and drop the `builtinLinux`
  gate on that row (Paseo computers need no phone Linux); the Servers button can then go.
- `AppPaseoGatewayFactory` reads the saved folder from `ProfileStore` because `ServerProfile` has no folder field.

## Integrator checklist (run on a machine with Google Maven + SDK)

```
cd native
./gradlew :protocol:paseo:desktopTest :engine:agents:desktopTest :feature:agents:desktopTest \
  :feature:servers:desktopTest :ui:routes:desktopTest :quality:test :androidApp:assemblePreviewDebug
```

Expect possible small Compose API fixes in `AgentsScreen.kt` / `AgentsScreenTest.kt` (written against the kit
signatures in this tree but never compiled). Then on a device: add a computer running `paseo start --no-relay`,
check, save, see its agents in Conversations, start one, send a prompt.
