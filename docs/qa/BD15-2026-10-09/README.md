# BD15 — Claude transcript attachment feasibility, 2026-10-09

Finish line: prove the session transcript image-to-ID mapping before implementing
`readAttachment(sessionId, id)`. Non-goals: UI edits, live account/transcript
access, guessing IDs or paths, native builds, emulator, or network use.
Branch: `sol/bd-transcript-attachments`, from `feat/genui-fe`
`bf6596a95d235b58dd7a88f5e81dfb743a5a6f18`. Discovery stayed within the requested
30-minute timebox.

## Result: transcript format compatible, owner-ID mapping unproven

The shipped Claude Agent SDK **can read inline base64 image blocks from a session
JSONL**, retaining `message.content[].source.data` and `media_type`. This was
verified using a synthetic file through its actual `getSessionMessages` reader.
Paseo also sends a person's pictures to Claude in exactly that block shape.

This does **not** establish that the pinned native Claude Code 2.1.283 writer
retained the owner's images, nor that their bare 64-hex markdown targets equal
SHA-256 of those bytes. The package cache contains SDK 0.3.246 and Paseo 0.9.2,
not the pinned native writer's source. No real transcript was read and no Claude
process was launched. Host Claude versions are not the app's pinned version and
were not substituted as evidence.

The exact mapping requested is still unproven, so **no `readAttachment` API,
capability, transcript reader, or UI change was added**. The reported history
images remain unresolved. This is the explicit negative feasibility outcome,
not a completed image-recovery feature.

## What the source actually does

[shipped-source.txt](shipped-source.txt) contains file hashes and numbered
excerpts. Paths in this table are relative to the cached package's
`@getpaseo/server/dist/server/server/` directory.

| Stage | Evidence | Meaning |
| --- | --- | --- |
| Send a person's picture | `agent/providers/claude/agent.js:2706–2760`, `toSdkUserMessage` | Builds `{type: "image", source: {type: "base64", media_type, data}}`. No image ID is generated there. |
| Read persisted history | `agent/providers/claude/agent.js:3876–3889,4016–4043` | Reads the provider session's JSONL and converts its records. Uses `CLAUDE_CONFIG_DIR` and the encoded project directory. |
| Replay a person's picture | `agent/providers/claude/agent.js:484–529,4827–4878` | Extracts text/input strings from user records. Ordinary standalone image blocks are ignored; an image-only user record yields no timeline item. It does not replace these blocks with hash markdown. |
| Replay a tool-returned picture | `agent/providers/claude/agent.js:392–425,4184–4193` | Extracts base64 images nested in `tool_result.content`, then calls the shared image materializer. These are distinct from ordinary pictures sent by the person. |
| Materialize a tool picture | `agent/providers/provider-image-output.js:59–80,128–138` | Hashes decoded image bytes with SHA-256. The returned filename is `<hash>.<extension>` in a private temporary directory; markdown targets the **full file URI**. |
| Replay existing bare-ID markdown | Same user-history converter | Leaves it as text. It neither establishes its provenance nor checks it against image bytes. The fixture verifies this pass-through. |

The prior [BD14 investigation](../BD14-2026-10-09/README.md) established that
there is no daemon ID-to-bytes read route. This follow-up establishes the
additional distinction between user images and tool-result images; it does not
promote the known tool-image filename algorithm into an unproven user-image ID
algorithm. Other SHA-256 uses in the daemon (message receipts, creation keys,
provider snapshots) are not image lookup tables.

## Correct local path and access boundary

For the app's private agent process,
[BuiltinLinux.kt](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt)
sets `HOME=/home/oc/.oc-profiles/<profileId>` and
`CLAUDE_CONFIG_DIR=$HOME/claude`. Therefore its expected transcript location is:

```text
/home/oc/.oc-profiles/<profileId>/claude/projects/<encoded-canonical-cwd>/<claude-session-id>.jsonl
```

It is not the shared agent user's default `/home/oc/.claude/projects`. Paseo's
`claude/project-dir.js` canonicalizes the project path, replaces non-alphanumeric
characters with `-`, and applies a length cap and suffix algorithm for long
paths. Do not reproduce this with a simple slash replacement. A Paseo/app session
ID also must not be assumed to be the underlying Claude persistence UUID.

[BuiltinRootfsFolders](../../../lib/builtin/builtin_folders.dart) already locates
the app-owned Ubuntu rootfs and resolves existing directories without following
links. That makes local file access feasible without starting a shell or another
Node process. It is a directory resolver, however, not an existing
session-authorized, bounded transcript-file reader. A future implementation must
validate the final file as well as its ancestors and bind profile, project and
provider session identity before opening anything.

## Reproducible offline check

[probe.mjs](probe.mjs) uses the shipped SDK and actual Paseo session/history
methods, with a fixed 1x1 PNG. It creates a private fixture config/project and a
single synthetic JSONL, reads that session through the SDK, checks outgoing
image shape, checks ordinary user-image replay, checks literal hash pass-through,
and checks the tool-result hash against the materialized bytes. The query factory
throws if invoked; no model/runtime is launched. All fixture files, including
materialized images, stay under one owned temporary directory that is deleted.
No image data or message content is printed in the result.

```sh
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- node \
  docs/qa/BD15-2026-10-09/probe.mjs \
  /home/eslam/Storage/tmp/claude-tmp/oc-paseo-lock-4pyx6dwm/node_modules
```

Exit 0; [probe-result.txt](probe-result.txt). This checks real reader/mapper
behavior with synthetic input, **not the native transcript writer**. No
production fix was made, so there is no failing-first product regression or
Flutter test claim. Probe syntax, documentation links, and staged diff whitespace
were checked. No build, emulator, real account access or network.

## What would unblock the requested reader

Obtain a sanitized paired record from the exact supported runtime: the bare ID
in the emitted Paseo timeline and the corresponding image block in that same
Claude session JSONL, or producer source that proves their relationship. Prove
whether the hash covers decoded bytes, base64 text, or a transformed image, and
whether image bytes remain after history compaction. A synthetic record authored
to match the desired hypothesis cannot establish this.

Once proved, `readAttachment(sessionId, id)` could resolve the current profile's
known provider session, open only its fixed transcript file, stream with total
and per-line limits, decode only supported image blocks, verify the proven hash,
and return capped bytes or null. It must reject unknown IDs, traversal, links,
non-image/malformed/oversized data and stale profile/session results, with no
cross-session scan, persistent copy or raw-content logging. Advertise its
capability only when that local access and identity contract is supported.

[Three-line coordinator contract](../../design/BD15-contract.md).

## Approved phone check — access unavailable

The owner subsequently authorized one read-only check on their connected real
phone. One `adb -s <approved-device> shell` command attempted to enter and list
the expected app-private profile root:

```text
/data/user/0/io.github.eslamasabry.opencode_mobile/files/linux/ubuntu/home/oc/.oc-profiles
```

The command suppressed the directory listing and shell errors; it emitted only
the following result fields:

```text
ids found: unavailable (app-private files not readable via adb)
hashes computed: 0
match: undetermined
```

The shell could not enter/list that directory. This establishes that the
required files were unavailable through this adb access path, not whether the
transcripts exist or which Android restriction prevented access. Per the
owner's stop condition, no further phone access was attempted. `run-as`, root,
alternate access paths, installs, writes, process kills and logins were not
attempted. No message text, account data, picture bytes, IDs or computed hashes
were printed, copied off the phone or committed. There are no hash values to
truncate to 12 characters.

The image-to-ID match was **neither proved nor disproved**. The conditional
implementation prerequisite remains unmet, so the reader/capability stays
unavailable and the existing contract remains accurate. This follow-up changes
documentation only; the diff was checked, with no Flutter tests or builds run.
