# FQ2 — Claude Code on the phone, device evidence (2026-10-07, late)

Emulator `OC_API35` (emulator-5554, x86_64), release APK 2190 = `feat/genui-fe` 281356fb, Claude Code 2.1.283
through Paseo 0.9.2 in the in-app Ubuntu, model Sonnet 5, "Asks first". Not the owner's phone.

| Cell | What was done | Result |
|---|---|---|
| resume | Reopened a 5-hour-old Claude chat (after app reinstalls and two server switches) and asked, without tools, which package it had suggested upgrading | Pass — "I suggested upgrading `react-router-dom` (from v5 to v6)…" ([fq2-claude-resume.jpg](fq2-claude-resume.jpg)) |
| abort | Asked it to run `sleep 90`, pressed Stop while the shell step ran | Pass — the turn ended, Send came back ([fq2-claude-stopped.jpg](fq2-claude-stopped.jpg)); the next prompt answered "ready" ([fq2-claude-usable-after-stop.jpg](fq2-claude-usable-after-stop.jpg)) |
| models | Composer chip shows the Claude model (Sonnet 5 here; Opus 5.5 on the owner's phone) | Pass |

Found: after Stop the turn ends with "2 other steps" and an empty row — it never says it was stopped. Logged for
the frontend (FC lane).
