# Device check — APK 2196 (2026-10-08)

Build: release 1.2.0+2196 from `feat/genui-fe` d777082c, signer 1DE5BF08…, emulator-5554 (x64), `adb install -r -d` (data kept).
Before install, emulator /data was 9% free; removed stale test rootfs `/data/local/tmp/p16a-installer-fix` (659 MB, backup on PC `tmp/emulator-p16a-installer-fix-backup-20261008.tar.gz`, file count verified 10,078) → 21% free.

| Check | Result | Evidence |
|---|---|---|
| Conversation list loads on its own after launch (BD2) | Pass — list filled with no pull | 00-list-autoload.jpg |
| Settings › Agents: short Install / Sign in chips, same left edge | Pass | 01-agents-chips.jpg |
| Cards line on OpenCode 2 | Pass — "On for Claude Code" | 01-agents-chips.jpg |
| Switch OC2 → OC1 copy | Pass — "Switching to OpenCode 1…" once; no "isn't answering", no password message; counter counts up (13 → 31 s) | 02-switching.jpg |
| Claude chats stay listed after switch | Pass | — |
| Recent app exits with real Android data (FD1) | Works; follow-up: hide routine "App updated" rows, cap list (fe/exit-list-focus) | 03-exits.jpg |

Not in 2196: BA capability refresh after first helper handshake (merged after build).
