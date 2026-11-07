# DeepSeek Harness (issue #128) — research and fit

Date: 2026-11-08. Asked by: Rootalith, issue #128. Status: research only, no code.

## Short answer

DeepSeek Harness is real. It is an official DeepSeek project. It can speak ACP, so it could become one more
phone or computer agent through Paseo, like Gemini CLI or Qwen Code. It is not ready to add today. Our
installer pins one checked download per agent, and dsh needs about 90 npm packages at install time. It is
also a developer preview that sends session logs to DeepSeek by default. Recommendation: do not add it now.
Watch it, and revisit when it ships a self-contained build or a stable 1.0.

## What it is

- Name: DeepSeek Harness, command `dsh`. Repo: <https://github.com/deepseek-ai/deepseek-harness>.
- Owner: DeepSeek (deepseek-ai). npm package `@deepseek-ai/dsh`, maintainers `imccyu` and `tianyicui-deepseek`.
- License: MIT.
- Activity: public since about 13 Aug 2026. The repo page showed about 276k stars and 25,848 commits on
  2026-11-07. No GitHub Releases are published; tags exist (for example `dsh-v0.2.1-alpha.1`, 3 Oct 2026).
  Latest npm version: `0.2.2-rc.1`. The README says "developer preview" and warns of breaking changes.
- Language: TypeScript on Node.js, built on Cordis (a plugin framework). The model adapter, tools, session
  log and agent loop are all plugins. Community plugins use the GitHub topic `dsh-plugin`.
- External pull requests to the core are not accepted for now.

## How it runs

- Needs Node.js `>=24.9.0` (npm `engines`). Our pinned Node is v24.21.0, so the version is fine.
- No `os` or `cpu` limits in the npm metadata and no native addons listed, so Linux arm64 inside proot Ubuntu
  should work in principle. This is not tested. The desktop app is macOS and Windows only.
- Ways to run it (profiles):
  - `dsh web` — local web UI on `127.0.0.1:3080`. It refuses `0.0.0.0` unless you set trusted hosts.
  - `dsh --profile headless "job"` — one job, prints the final answer, exits. Has `--json` and `--session-id`.
  - `dsh --profile sdk` — JSON-RPC over stdio, used by the Python SDK.
  - `dsh --profile acp` — Agent Client Protocol server over stdio. Reports call the shipped profile
    "automation-only"; several community bundles (dsh-acp, dsh-acp-plus) extend it with session history,
    steering and plan mode.
- Auth: model access is a plugin. The default is the DeepSeek API (an API key from the DeepSeek platform).
  Ollama and other providers can be plugged in. There is no browser sign-in.
- Sessions: persisted locally (headless and ACP can resume by session id); config lives in `$DSH_HOME`. The
  exact session path is not documented in what we read. **Session logs are uploaded to DeepSeek by default on the official
  API.** This can be turned off in settings or by disabling that plugin.
- Tool approvals: permission presets. New sessions use `workspace-write` (writes limited to the workspace and
  temp; reads and network are not limited). Sandboxing uses bwrap on Linux; bwrap usually does not work in
  proot. The `sdk-minimal` profile runs with full access and no approvals.
- Diffs: the web UI shows tool calls and results. Over ACP, community adapters do not list diff support.
- Plugins: installed with a plugin manager from npm packages or GitHub refs, resolved by pnpm. A plugin is
  ordinary Node code with full access to the user account.
- Safety notice (SAFETY.md): no security audit; can run model-written commands, load third-party plugins,
  touch files, network and credentials. It advises a disposable VM or container.

## How it would fit our app

We drive agents three ways: OpenCode 2 over HTTP, Claude Code and Codex natively through Paseo, and ACP agents
through Paseo (Gemini CLI, Qwen Code, Kilo, Cline, Goose, Antigravity, Oh My Pi, fx).

- **(a) ACP agent through Paseo — the right fit.** `dsh --profile acp` speaks ACP over stdio, which is exactly
  what our `AcpPaseo` route expects. A catalog entry with route `AcpPaseo`, executable `dsh`, launch args
  `["--profile", "acp"]` and sign-in `ApiKeyHost` would make `PaseoConfig` render
  `"dsh":{"extends":"acp","label":"DeepSeek Harness","command":[".../dsh","--profile","acp"]}` with no new
  protocol code. The user would see "DeepSeek Harness" in the agent list, install it on the phone or use it on
  their computer, add a DeepSeek API key on the host, and chat with tool approvals through the existing
  Paseo permission flow.
- **(b) OpenCode plugin or provider — already covered for the models.** DeepSeek models are already a provider
  in OpenCode 2. A person who wants DeepSeek models can pick them today with no app change. That does not give
  them dsh's own plugins or presets.
- **(c) Not a fit — today, for the phone install.** See blockers.

### What is missing (blockers)

1. **Install recipe.** `AgentInstallRecipe` pins one HTTPS artifact per architecture by SHA-256 (`Executable`,
   `TarGz`, `NpmTarGz`). `@deepseek-ai/dsh` is a 76 KB package with 90 dependencies (82 internal
   `@deepseek-ai/dsh-*` packages pinned to the same version, plus Cordis and others). There is no single bundle
   to pin. Gemini CLI and Qwen Code work because they ship a bundled entry file. The honest catalog state is
   `Hidden` with `AgentUnavailableReason.DependencyClosureUnverified`, which hides it anyway.
2. **Freeze rule.** `agent-pins.json` is copied from the released Flutter catalog and changes only with an app
   release (`AgentPinsParityTest`). A new agent needs the agents owner and a release decision.
3. **ACP surface.** The official ACP profile is described as automation-only. Resume, model list and
   permission behaviour are not verified, so it would start as `Limited` with "Can't reopen old chats".
4. **Sandbox.** bwrap rarely works inside proot, so the `workspace-write` limits may not hold on the phone.
5. **Privacy.** Session logs upload to DeepSeek by default. We would have to turn that off in the install
   config and tell the person plainly.

### Which modules would change (later, if we do it)

- `:engine:agents` — new catalog row and pins (`pins/agent-pins.json`, `AgentPinsJson.kt`), a new limitation
  key (for example "Needs a DeepSeek API key on the host"), a sign-in hint in `HostAgentProviders`, and the
  parity and Paseo config golden tests.
- `:engine:agents` phone scripts (`AgentPhoneScripts.kt`) — either a new artifact format for a pinned dependency
  closure (a lockfile-checked `npm ci` or an app-built bundle we host and hash), plus a config patch in
  `$DSH_HOME/cordis.patch.yml` that turns off log upload.
- `:engine:linuxsetup` — nothing if the Node pin stays at 24.x.
- `:ui:words` — English and Arabic copy for the row and limitation (owned by the l10n owner).
- No chat UI change: tool approvals, live updates and streamed text already go through Paseo.

## Effort

- With a self-contained dsh build from DeepSeek: about one day (catalog row, words, tests, emulator check).
- Without one: three to five days to add a pinned dependency-closure install format, check it in proot on arm64,
  and verify resume and permissions. Plus the cost of tracking a preview that breaks compatibility.

## Risks

- Terms and data: the DeepSeek API terms apply to the person's own key. Default log upload sends their code and
  prompts to DeepSeek. Some people and companies cannot allow that.
- Plugin security: plugins are arbitrary Node code from npm or GitHub, with no review. We should never install
  plugins for the person, and we should not offer a plugin store in the app.
- Supply chain: 90 packages per install; any of them could change between versions.
- Stability: developer preview; profile names and the ACP surface may change.
- Sandbox gaps in proot, as above.

## Recommendation

Do not add it now. Keep this note. Revisit when one of these happens: dsh ships a single bundled file or a
binary per platform, or reaches a stable release with a verified ACP profile. Then add it as an `AcpPaseo`
agent, `Limited`, with log upload off by default. Meanwhile, people who want DeepSeek models can use them in
OpenCode 2 today, and people who run dsh on their own computer can try it through any ACP client.

## Sources

- Repo and README: <https://github.com/deepseek-ai/deepseek-harness>
- Safety notice: <https://raw.githubusercontent.com/deepseek-ai/deepseek-harness/HEAD/SAFETY.md>
- npm metadata (version, Node engine, 90 dependencies): <https://registry.npmjs.org/@deepseek-ai/dsh/latest>
- Profiles, permissions, log upload, pricing: <https://www.eesel.ai/blog/deepseek-harness>
- Plugin architecture overview: <https://www.noze.it/en/insights/deepseek-harness-plugins-and-core/>
- Plugin topic: <https://github.com/topic/dsh-plugin>
- Community ACP adapters: <https://github.com/OffloopHQ/dsh-acp>, <https://github.com/AndPuQing/dsh-acp-plus>,
  <https://github.com/dangpangch/dsh-acp>

Some figures (star counts, dates) differ between sources; the ones above were read on 2026-11-07.

## Draft reply to the issue author (not posted)

> Thanks for the suggestion, and for the kind words!
>
> We looked into DeepSeek Harness. It's a good match on paper: its ACP profile (`dsh --profile acp`) is the same
> kind of connection we already use for Gemini CLI, Qwen Code and others, so it could appear as one more agent
> with tool approvals and live sessions.
>
> We're not adding it yet, for three reasons:
> - The app installs each agent from one checked download. dsh is installed as about 90 npm packages, so we
>   can't verify it the same way yet.
> - It's still a developer preview with breaking changes expected.
> - By default it uploads session logs to DeepSeek. We'd want that off before offering it to anyone.
>
> In the meantime, DeepSeek models already work in the app through OpenCode: pick a DeepSeek model in the
> model list after adding your DeepSeek key on the server.
>
> We'll revisit when dsh ships a self-contained build or a stable release. We'll keep this issue open to track it.
