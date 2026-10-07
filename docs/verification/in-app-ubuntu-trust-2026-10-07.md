# In-app Ubuntu: agent vs. root isolation — device findings (2026-10-07)

Emulator `OC_API35` (Android 15, kernel `6.6.50-android15-8` GKI), app 1.2.0+2183 and the Landlock QA build of
`f8e231ee` (feat/landlock-qual = feat/genui-fe + Vega's `c383b0b0`), signer prefix `1de5bf08`.

## What was measured

1. **Landlock is not in the kernel.** `/proc/config.gz`: `# CONFIG_SECURITY_LANDLOCK is not set` (it is listed in
   `CONFIG_LSM` but not built). Vega's boundary probe (`AgentToolBoundaryAcceptance`) reports
   `kernelSupported=false, compatibilityFallback=true, protectionPassed=false, qualified=false`; with
   `--require-protection` the host script fails. The Landlock agent-launch policy therefore cannot protect anything
   on this kernel, and Android's GKI kernels are likely the same. Its commit stays off `feat/genui-fe`.
2. **The agent user can modify the whole Ubuntu.** Through the real agent launch arguments (`--change-id=1000:1000`,
   agent `/root` view), `/usr/bin`, `/etc`, `/opt`, `/opt/node/bin/node` and `/opt/opencode/bin/opencode` (root's
   own server binary) are writable. Only `/root` is hidden.
3. **The agent user can read the OpenCode server's environment.** From the same agent view, `/proc/<pid>/environ` of
   the running `opencode serve` is readable (597 bytes) and contains the `OPENCODE_SERVER_PASSWORD` variable (value
   not read out or printed). Same Android UID, so the kernel allows it.

## What it means

PRoot changes what an agent *sees*, not what it *can do*: every process in the in-app Ubuntu runs as the app's one
Android UID. Claude Code, Pi and the other phone agents can already replace OpenCode's binary or use its server
password. This predates agent cards; registering `oc-ui` for OpenCode adds no new path. The strict rule "never run
an agent-writable helper as root" cannot be met on this kernel by any launch policy.

## Options (owner decision)

- **A — Accept one trust zone and say so** (recommended): document that everything in the in-app Ubuntu shares one
  trust zone, keep the cheap defences (hidden `/root`, no secrets in agent profiles), and enable OpenCode agent cards
  with a verifier (MCP status connected + the captured `oc-ui_show` fixture).
- **B — Partial hardening first**: hide the OpenCode server's `/proc` entries from agent launches (as the AI Team
  engine pid already is) and pass the server password by file descriptor instead of environment; still no runtime
  integrity.
- **C — Keep OpenCode cards off** until a kernel boundary exists on phones.

Raw outputs: /home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad/b0-results/landlock-run1.txt, /home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad/b0-results/landlock-direct.txt.
