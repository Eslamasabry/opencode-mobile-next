# Agent personas

The registry is the single source: `lib/domain/agent_persona.dart` (identity,
mark, maker; `AgentPersona.of(idOrKey)` maps every id, alias and icon key,
unknown ids to `other`) and `lib/ui/agents/agent_persona_text.dart` (words from
`lib/l10n/app_en.arb` / `app_ar.arb`, keys `agentPersona<Agent>Strength|About|Needs`
and `agentMaker*`). This table mirrors it; edit the ARB files, then update here.
Brand names are not translated and live in `agent_persona_text.dart`.

| Agent | Maker | Card line (at most 28 characters) | About | Needs |
| --- | --- | --- | --- | --- |
| Claude Code | by Anthropic | Careful with big changes | Plans before it edits; good for larger changes across files. | A Claude plan or an Anthropic key |
| Codex | by OpenAI | Fast edits and reviews | Quick at focused fixes and reviewing changes. | A ChatGPT plan or an OpenAI key |
| Gemini | by Google | Reads a lot at once | Handles long files and wide questions well. | A Google account or a Gemini key |
| Kilo | open source | Your choice of provider | One agent that works with many model providers. | A key from any supported provider |
| Cline | open source | Step by step, asks first | Works in small steps and checks in with you. | A key from any supported provider |
| OpenCode | open source | Any model, free ones too | Works with any provider, and has free models to start. | Nothing to start; add a key for more models |

Anything else (Qwen, Goose, Pi, Copilot, a server's own agent) is `other`: it
keeps its own mark when the app bundles one, shows the name the connection
gave, and has no card line.

## Where personas are used

- Start screen (`lib/ui/screens/chats/new_chat_screen.dart`): `KitAgentChoice`
  cards, the quiet `KitAgentLine`, the "Start with ..." button.
- Not yet (the sweep to follow): see the list in the round-2 report; every
  place that shows an agent's name or mark through `agentIcon`/`agentMark`
  (`lib/ui/screens/agents/agents_text.dart`) or a server string.
