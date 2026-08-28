# OpenCode command and mobile feature map

Source of truth: OpenCode `dev` at `c2eacd72afc4a4984564c393e15ab30011057269`, refreshed 2026-08-27. The command inventory comes from the TUI keymap, session route, prompt component, system diff plugin, web composer registrations, and the server command registry. It is not inferred from the two default commands returned by `/command`.

## How commands are assembled

OpenCode has two distinct command sources:

1. Client actions are registered by the TUI or web app. They open UI, change local display state, or call a session API. They are not returned by the server's command-list endpoint.
2. Server commands are discovered at runtime. The server always supplies `/init` and `/review`, then adds configured commands, MCP prompts, and skills. This list varies by project, workspace, server version, and configuration.

The mobile launcher combines these sources. Client actions are explicitly mapped to real mobile handlers; server commands remain dynamic and execute through OpenCode's slash-command API. Model names, providers, agents, variants, MCP prompts, configured commands, and skills are never hardcoded.

## Current TUI slash commands

| Command | Aliases | OpenCode behavior | Mobile mapping |
| --- | --- | --- | --- |
| `/sessions` | `/resume`, `/continue` | Switch session | Native: search root sessions across all projects with server-side title filtering, cursor pagination, and optional archived results |
| `/new` | `/clear` | New session | Native: create and open a session |
| `/workspaces` | — | Manage experimental workspaces | Adapted: project, directory, and worktree screen |
| `/models` | `/mo` | Switch model | Native: unified server-backed model picker |
| `/agents` | — | Switch agent | Native: unified model, mode, and agent picker |
| `/mcps` | — | Toggle or inspect MCPs | Adapted: MCP and integrations screen |
| `/variants` | — | Switch model variant | Native: model variants and reasoning effort |
| `/connect` | — | Connect a provider | Adapted: provider and integration authentication |
| `/org` | `/orgs`, `/switch-org` | Switch OpenCode Console organization when available | Native: account-grouped organization picker, server switch, instance disposal, and provider/model reload |
| `/status` | — | View runtime status | Adapted: server health, version, connection, and background mode |
| `/debug` | — | View TUI debug information | Adapted: server status; TUI renderer diagnostics are irrelevant on Flutter |
| `/themes` | `/theme` | Switch TUI theme | Native: follow Android or choose the designed light or dark workspace theme |
| `/help` | — | Open help | Native: searchable command and action map |
| `/exit` | `/quit`, `/q` | Exit the terminal process | Intentionally omitted: Android owns app lifecycle |
| `/share` | — | Share or copy session link | Native |
| `/rename` | — | Rename session | Native |
| `/timeline` | — | Jump to a message | Native: searchable all-message picker with stable indexed anchors |
| `/fork` | — | Fork from the timeline | Native: choose a user prompt, fork at its message ID, and restore its text and files for editing |
| `/compact` | `/summarize` | Compact session context | Native, using the selected server model |
| `/unshare` | — | Disable share link | Native |
| `/undo` | — | Revert the previous prompt and changes | Native |
| `/redo` | — | Restore reverted state | Native |
| `/timestamps` | `/toggle-timestamps` | Toggle message timestamps | Native: app-wide persisted timestamps for visible transcript entries |
| `/thinking` | `/toggle-thinking` | Expand or collapse reasoning | Native: app-wide persisted long-reasoning state; reasoning under two lines remains inline |
| `/copy` | — | Copy transcript | Native Markdown transcript export to clipboard |
| `/export` | — | Export transcript | Native Markdown document save |
| `/editor` | — | Edit the prompt in an external terminal editor | Native: focused full-screen prompt editor with selection and attachment preservation |
| `/skills` | — | Browse skills and insert one | Native skill browser; slash-capable skills also arrive dynamically from the server |
| `/warp` | — | Change experimental workspace for the session | Native: choose Local or a connected workspace and optionally copy working changes |
| `/move` | — | Move a session to another project directory | Native: choose a known project directory and optionally transfer working changes |
| `/diff` | — | Open the system diff viewer plugin | Native session diff viewer |

## Web client slash commands

The OpenCode web client registers a smaller, context-sensitive set. Mobile accepts its singular names as aliases where they differ from the TUI:

- `/model`, `/agent`, `/workspace`, `/open`, `/terminal`, and `/mcp`
- `/new`, `/share`, `/unshare`, `/undo`, `/redo`, `/compact`, `/fork`, and `/export`

Mobile also registers `/files` with the web client's `/open` alias and
`/references` with `/reference` and `/refs` aliases. `/files` opens project
browsing, including direct per-file Working tree review for files reported as
changed by OpenCode, while `/editor` edits the active draft; these are
intentionally separate. Review comments return to the active chat prompt when
available and otherwise copy for use in any chat. The reference action
opens the server-backed reference list and adds the selected item to the active
prompt using the same `@name` directory-part contract as the upstream TUI and
web composer.

## Dynamic server commands

These must always be loaded from the connected OpenCode server:

- `/init`: guided `AGENTS.md` setup
- `/review`: review uncommitted, commit, branch, or pull-request changes
- Commands declared in OpenCode configuration
- MCP prompt commands
- Skills exposed as commands

Selecting a dynamic command inserts it into the composer so arguments can be edited naturally. Sending a recognized dynamic command calls the OpenCode command endpoint with the selected model and variant. Unknown slash-prefixed text remains an ordinary prompt.

## Mobile-native capabilities without slash commands

- Attach local files and images, including local voice transcription
- Mention and browse project files, OpenCode references, agents, MCP resources, and skills
- Preview generated images and files inline; open, download, attach back, and comment on them
- Render Markdown, tables, JSON, code, diffs, and structured tool results appropriately
- Group contiguous tool calls without nested cards; merge contiguous reasoning and assistant text
- Answer permission and question requests, inspect todos, and stop active generations
- Browse projects, workspaces, sessions, archived sessions, files, and persistent PTYs
- Search root sessions across every project and open the exact session location
- Reconcile chats, files, workspaces, terminals, requests, and catalogs after wake
- Opt into foreground live-session mode and receive Shorebird update status
- Update the app-managed local OpenCode server from Settings
- Complete remote MCP OAuth on the phone through a state-validated loopback callback, with manual and cancel fallbacks
- Choose OpenCode's global default shell from the server's live shell inventory, including Automatic reset and terminal-only guidance

## Next command parity work

1. Continue the generated-SDK audit with transport modernization and the highest-impact remaining coding workflow; keep Traycer deferred.
