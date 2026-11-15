# Unharnessed hosted feasibility contract

Upstream: `fe2cf2815f78463464b91c598fc0c31c0af98f4c`. Never build or run it on an owned computer;
the dedicated workflow uses GitHub-hosted arm64 runners without secrets. An artifact is not a release.

The ACP bridge uses the Linux distribution's `/usr/bin/python3` and its standard library, independently of
the kernel venv. Its index, locks and temporary files stay in the profile-owned agent data directory.
Close unloads a conversation and keeps its mapping. Successful explicit deletion removes that mapping.
Reopen selects the validated saved JSONL before negotiating a fresh child, then translates envelope ids only.
Unknown ids, corrupt indexes and paths outside the agent sessions directory fail closed. No ambient provider
credentials are passed to the child. Pending calls receive a plain error if it exits.

| Catalog row | Behavior |
|---|---|
| B-AGENT-UH-001 | A private atomic index preserves other conversations and rejects missing ids and escaping paths. |
| B-AGENT-UH-002 | Bounded JSON-line framing changes only envelope identity, preserving prompt/tool content. |
| B-AGENT-UH-003 | Close preserves reopen, load restarts on the saved file, history retains the public id, deletion forgets it. |
| B-AGENT-UH-004 | Python input remains intact and is also readable by the helper. |
| B-AGENT-UH-005 | A fake child crash answers pending calls and permits a new initialization. |

The normal `:engine:agents:desktopTest --tests '*UnharnessedAdapterTest*'` task runs the bridge tests with a
fake child only. Real binary/kernel tests run exclusively in the dedicated hosted workflow. The install
bundle includes the exact binary and skills, pinned uv, standalone CPython and a wheelhouse installed with
hash-locked requirements, without network access. All bundle file hashes and component costs are artifacts.

The production entrypoint adds a `command` view of the original `rawInput.code` on execute steps. The original
code, outputs and other fields remain present. This lets the pinned helper retain Python input in its shell-step
mapping. The underlying identity bridge otherwise rewrites only ACP envelope ids. Session-list ids are translated
for known entries; an explicit successful upstream deletion removes the matching mapping. No kernel venv is needed
to run the adapter itself.

| Catalog row | Behavior |
|---|---|
| B-AGENT-UH-010 | An unhosted pin has no install action; never-asks and key/local setup come from the descriptor. |
| B-AGENT-UH-011 | Remote endpoints need a key; local servers may omit it; both need a model. |
| B-AGENT-UH-012 | Readiness needs configured secure credentials or a local server, and removal clears setup. |
| B-AGENT-UH-013 | Runtime provider copies use private files and stdin; clearing preserves saved sessions. |
| B-AGENT-UH-020 | A never-asking agent has a warning, no approval control and no automatic approval sweep. |
| B-AGENT-UH-021 | Captured real ACP code/output become a readable completed step through the app mapper. |
| B-AGENT-UH-022 | A model list cannot make an unconfigured agent selectable. |
| B-AGENT-UH-023 | Removing the agent erases its private runtime in every profile, retaining other agents and projects. |
| B-AGENT-UH-030 | The pending Agents card shows the unavailable download and command policy. |
| B-AGENT-UH-031 | Setup routes to the key/local form. |
| B-AGENT-UH-032 | The form states the required model and offers no subscription login. |
| B-AGENT-UH-033 | The composer states never asks using static text. |
| B-AGENT-UH-034 | A prefilled first message still has the full warning before Start. |
