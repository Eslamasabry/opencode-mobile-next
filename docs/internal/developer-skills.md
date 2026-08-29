# Developer agent skill packs (removed from the repository)

Two Claude Code skill packs used while building the showcase video were
committed under `.claude/skills/`. They have been removed from the repository
and `.claude/skills/` is now gitignored.

## Why they were removed

They are third-party prompt packs. Publishing someone else's work needs their
license, and neither pack arrived with enough to establish one:

| Pack | What it declared | What was missing |
|---|---|---|
| `motion-design` | `license: MIT`, `metadata.author: LottieFiles` | no upstream URL, no version or commit, no copyright line, no MIT text — a license field is a claim, not a grant |
| `remotion-motion-graphics` | nothing | no license, no author, no source at all |

Both were installed from [mcpmarket.com](https://mcpmarket.com) skills. That
marketplace is where to look for the current upstream and terms.

Neither pack is needed to build, test, or run the app or the Remotion project
in `video/` — they are prompt guidance for an AI assistant, consumed at
authoring time and nowhere else. Removing them costs the product nothing, and
inventing a license for someone else's work was never an option.

## Reinstalling them locally

Install from the marketplace into `.claude/skills/`. Git ignores that path, so
a local copy stays local:

```
.claude/skills/motion-design/
.claude/skills/remotion-motion-graphics/
```

## If a skill pack is ever committed again

Record, per pack, before it lands:

- exact upstream URL;
- tag, commit, or version;
- copyright holder;
- SPDX identifier;
- modified or unmodified;
- the required license and notice text, added to `LICENSES/` and
  `THIRD_PARTY_NOTICES.md`.

Anything short of that is an unaccounted third-party directory in a public
repository.
