# Agent tools on the phone — device evidence (2026-10-07, evening)

Follow-up to [agent-cards-2026-10-07](../agent-cards-2026-10-07/README.md). Natural prompts, real agents,
emulator `OC_API35` (emulator-5554), release APKs signed with the local test key, branch `feat/genui-fe`.
Not the owner's phone.

| # | Scenario (APK) | What happened | Result |
|---|---|---|---|
| 1 | Risky cleanup, Claude Code via Paseo, "Asks first" (2175) | Reopening the chat while it waited on an approval failed ("Couldn't open this conversation"); the list row said Needs you with no request under it | **Bug** → fixed (draft id vs daemon id) |
| 2 | Same, after the fix (2177) | `wc -l` approval under the list row → Allow → "Allowed · Undo" → Running; `rm` approval under the row; reopening the row shows the pending `rm`; Allow → "Deleted all 8 scratch files"; row clears | Pass — [cleanup-2177.jpg](cleanup-2177.jpg) |
| 3 | Photo card (2175) | Card shown; picked photo; Send → "The answer is not confirmed" and nothing reached Claude; the same photo from the composer worked | **Bug** → fixed (same id cause) |
| 4 | Photo card (2177, 2178) | Photo reached Claude and it answered, but the card read "We can't tell yet whether this card was answered" until the run ended | **Bug** → fixed (live receipt) |
| 5 | Photo card (2180) | "Sending your answer…" → receipt "✓ Submitted 1 photo" while Claude still runs → reply with the fix | Pass — [photo-card-2180.jpg](photo-card-2180.jpg) |
| 6 | OpenCode question in a project not open (2177) | Question under the quiz-app row while the app was on my-app; "Go" from the list → "Go · Undo" → Running → done | Pass — [opencode-list-question-2177.jpg](opencode-list-question-2177.jpg) |
| 7 | OpenCode 2.0.10 typed forms under unopened rows (2183) | Two forms in other folders show under their rows; answered (`enabled:false, count:3, weight:2.5, tags:[alpha,beta]`, `note` absent); revised form with Details enabled (`note:"checked"`); per-form drafts; Decline → `cancelled` (server state checked each time) | Pass — [oc2-forms-2183.jpg](oc2-forms-2183.jpg) |

Found and fixed during these runs (each with a test that failed first):

- "Waiting for you · 1 min 17 s" showed the turn's age next to a card saying "waiting less than a minute" → the line reads "Waiting for you…" without a clock or shimmer.
- Claude's tool lookup showed as "toolsearch"; MCP tools as `mcp__server__tool` → "Load tools"; "create issue · github".
- Untitled OpenCode rows showed "New session - 2026-10-07T12:14…" → "New conversation".
- An OpenCode row's question said "The agent" while its approvals say "OpenCode" → "OpenCode".
- A chat's question sheet did not name the asking agent → it does.
- OpenCode 2 rows all showed a Git badge (OC2 gives every folder a project) → capability `projectsAreGitRepositories`.
- A list form's Answer was a primary button on every row → secondary, as list questions.

OpenCode 1 agent cards: the direct tool works (`oc-ui_show`, connected after a server restart, exact accepted
text), but agents can write the whole in-app Ubuntu, including root's OpenCode binary, so cards stay off
for OpenCode until the Landlock agent-launch policy is proven on a device
([oc-gaps](../../verification/oc-gaps-2026-10-07.md)).

Modularity: every phone-tool fact per agent now lives in one adapter
([design](../../design/agent-tool-adapters-2026-10-07.md)).
