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

The normal `:engine:agents:desktopTest --tests '*UnharnessedAdapterTest*'` task runs the bridge tests with a
fake child only. Real binary/kernel tests run exclusively in the dedicated hosted workflow. The install
bundle includes the exact binary and skills, pinned uv, standalone CPython and a wheelhouse installed with
hash-locked requirements, without network access. All bundle file hashes and component costs are artifacts.
