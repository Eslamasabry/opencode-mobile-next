# Screen review: AI Team (`i1-team-core`, `i2-team-sheets`)

*2026-09-26. Every census image in both areas was reviewed against `docs/design/principles.md`. Source was checked where a still image cannot show the behaviour. Per-page scores and fixes are in [`team.json`](team.json).*

**53 pages:** 9 keep, 39 fix, 5 rethink. Three of the keeps are not rendered (`team-intro`, `embedded-team-card` whose render failed, `embedded-team-discover`). Findings: 3 critical, 43 high, 75 medium, 53 low.

The AI Team home is now much better than the screen the owner called "very ugly". It has one pinned primary, plain task rows and a Now line. The problems below the home are that the engine still speaks through the screens and that states contradict each other.

## The five most important problems

1. **Typed input is lost without warning** (critical, X2). The objective in *Give the team a task* (`start-run-sheet`) and the text in *Message fox* (`team-agent-message-sheet`) live only in each sheet's controller. A swipe-down discards them silently. A refused plan (`embedded-team-planning-card`) offers only Dismiss, so the objective is gone there too. The fix is a per-profile draft, a discard confirmation, and "Edit and send again".
2. **Engine words and addresses above the fold** (high, C3/P2). This is the complaint behind regression rows 5 and 21, and it is still widespread:
   - "Stop shopfront/ gastown.furiosa?" (`team-cycle-stop-confirm-sheet`, critical)
   - The seven stages "Routed · Claimed · Pushed · Handed to merge", "bead w-sync" and worktree paths (`work-sheet`, `embedded-team-cycle-strip`)
   - `http://127.0.0.1:8372`, "gc · bd · dolt" and "Event stream connected" (`embedded-team-phone-section`)
   - "ctx 63%", Provider, Harness and a `.gc/worktrees/polecat-fox` path at the top of the agent screen (`team-agent`)
   - "batch" and "run" in the cancel sheets, where the rest of the app says "task"
3. **Screens contradict themselves** (high, C2):
   - The agent screen says "Working", "waiting on one other step" and "0 tokens · ctx 63%" at the same time (`team-agent`).
   - A step reads "Merged" in its stages and "Review" in its status. A blocked step highlights "Routed" as in progress (`work-sheet`).
   - A task shows "Done" with every stage checked but "1 of 2 steps done" (`team-run`, `embedded-team-merge-section`).
   - The team sheet says "Not available · no AI team found", "On" and "Stopped" together (`embedded-team-phone-section`).
   - Two agents are both called "Worker" (`team-agents`, `team-run-agents-tab`).
4. **Waits and failures without a way forward** (high, P3):
   - "The host has not started an agent yet" has no age and no action (`team-run-overview-tab`, regression row 20).
   - "Still planning" after 31 minutes offers only raw output.
   - An ended live output says "Nothing yet" (`team-agent-output`).
   - A failed task says "Fix the failing tests on the host" and has no primary action (`gate-sheet`).
   - Phone setup shows no time or bytes progress (`team-phone-onboarding-steps`, `-offer`; regression row 19).
   - The host test is a disabled "Checking the address…" button (`team-host-sheet`).
5. **Controls in the wrong place or of the wrong weight** (high, P1/P6/§2):
   - The agent's controls start below the fold (`team-agent`).
   - Merge takes three confirmations (tap, tap again, sheet), and Approve sits under Merge as a second big button (`embedded-team-merge-section`).
   - A merged task still shows disabled Merge and Approve slabs (`team-run-overview-tab`).
   - Stop is drawn green next to frequent actions (`embedded-team-cycle-strip`, `embedded-team-phone-section`).
   - Reassign acts on one tap with no confirmation or undo (`team-agent-reassign-sheet`).

## Recurring patterns

- **The same thing twice.**
  - Needs-you tasks are listed again under Tasks.
  - The discovery and re-offer cards sit above an "AI Team" row that says the same thing (regression row 18 again).
  - Technical details show each value under a label and again under a lowercase raw key.
  - Context 63% appears three times on the agent screen.
- **Status drawn as a button.** The "Unconfirmed" and "Stop run · Unconfirmed" outlined chips squeeze row text into an ellipsis ("Keep drafts in SQLit…") and read as actions.
- **Button alignment varies.** There are right-aligned pairs (discovery card, "Got it"), left-aligned pairs (re-offer card), side-by-side rows (gate run-failed, cycle strip) and bottom-right filled buttons in cards (phone onboarding). Standard §2 says stacked and full width.
- **Stacked sheets.** Examples: run, then work sheet, then how-it-works or stop confirm; home, then gate, then confirm. That makes two or three drag handles and scrims.
- **Motion.** Every "Working" row runs its own spinner (`KitStatusMark` working is a `CircularProgressIndicator`), so the home can run 4 to 12 loops at once. That breaks the rule of one ambient loop per screen. The planning drawing and the merged celebration follow §10. Success has no finished moment.
- **Logs open by default and oversized.** Onboarding "LIVE OUTPUT" and "LAST OUTPUT" panels and the agent live output use large mono text in a box inside a card. Row 16 fixed exactly this for phone setup.

## Quick wins (copy or small layout, one file each)

- Title the stop confirm with the agent's display name, and say "task" instead of "batch" or "run" in the cancel sheets.
- Rewrite waiting rows as "Waiting 34 min for a worker", on one line.
- Replace "Unconfirmed" and "Not accepted" with "Not confirmed yet" and "dev-pc refused it · Answer again". Move the receipt from a trailing chip into the supporting line.
- Remove the Planner row ("Mayor gastown.mayor") from the start sheet.
- Hide Merge and Approve once a task is merged. Drop the armed second tap before the merge sheet.
- Deduplicate the technical-value rows, and drop milliseconds from timestamps.
- Wrap or scroll the adb commands in *Keep it running*, which are currently cut off.
- Replace the ended live output's "Nothing yet" with "This session has ended" and "Open the conversation".
- Show the planner-off direct-task form instead of the dead-end state.
- Add "about 10 minutes the first time" to the phone team offer and the intro.
- Give task list rows a still "working" mark and keep one moving thing per screen.
- Fix the census scene for `embedded-team-card`, the most-seen team surface, which has no current render.
