# R1 Report a problem: desktop renders, 2026-11-09

Branch `native/report-r1` (from `native/telemetry` + `native/screens-merge41`, landed on `native/next`). The owner's
pick R1 (mockups in `/home/owner/Storage2/mockups/2026-11-09/R1-*-glass-RECOMMENDED.png`): one flow for the old
Report a problem and Settings › Help › Send a problem report.

Drawn by `ReportProblemR1Test` (`:feature:settings:desktopTest -Poc.r1Renders=true`) on the desktop Skiko raster at
412 × 915 dp, density 2.5 (the mockups' 1030 × 2288 px). Desktop has no liquid glass, so Glass draws its
fallback here; the phone draws the liquid panes.

| State | Solid dark | Solid light | Glass dark | Glass light |
|---|---|---|---|---|
| Form (a crash 47 min ago) | r1-form-solid-dark.png | r1-form-solid-light.png | r1-form-glass-dark.png | r1-form-glass-light.png |
| What's included sheet | r1-sheet-solid-dark.png | r1-sheet-solid-light.png | r1-sheet-glass-dark.png | r1-sheet-glass-light.png |
| Report sent | r1-sent-solid-dark.png | r1-sent-solid-light.png | r1-sent-glass-dark.png | r1-sent-glass-light.png |

Also: `r1-form-noconfig-solid-dark.png` (a build without the Firebase config: Copy text is the primary, one plain
line says why) and `r1-form-ar-glass-dark.png` (Arabic, right to left).

## Against the mockups

- Same structure: question with Optional, the field with its full hint, the "What's included ›" line naming the crash
  time, one primary, the quiet Copy text / Post publicly on GitHub line; the sheet lists words, last close (amber),
  app, phone, connection without its address, recent problems, then "Never sent"; the sent page has the check, title,
  thank-you line, the report code with its copy icon, a hairline and Done.
- Differences: the primary is the pack's accent (green in the default pack, not the mockup's yellow); the memory is
  the contract's range (`8-12 GB`), never an exact size; the quiet line is the kit's start-aligned tertiary row
  rather than a centred one with a dot; the sheet has the kit's Close.

## Removed from the screen

Share report, Copy, Send as a GitHub issue, Everything this phone kept (its page, route and presenter), the Details
fold, Keep crash categories on this phone, Preview crash report, Clear saved evidence, Refresh exit history, and the
separate Send a problem report row. The last unexpected close always comes from ApplicationExitInfo.
