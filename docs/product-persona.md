# Target user and product priorities

Working product hypothesis, September 2026. Grounded in the repository's
Android/remote-server/Termux positioning and the owner's feedback about composer
UX, visual quality, confusing branches/builds and access to the latest APK.
These are design assumptions to validate with users, not interview findings.

## Primary persona: the developer away from the desk

A solo developer or small-team builder already using OpenCode on a computer or
server. They understand projects, code and models, but use the phone in short,
interrupted sessions. They want to start a task, see whether work is progressing,
unblock the agent, review a change and leave the next instruction.

Their product job: **Keep coding work moving from my phone, understand what
changed, and stay in control without losing my place.**

Likely constraints: one-handed input, a keyboard taking half the screen, variable
connectivity, interruptions, multiple projects and long agent runs. Demographics
and preferred model/provider are not required to design for these constraints.

## What they need, in priority order

| Job | Product behavior | Evidence needed before calling it ready |
|---|---|---|
| Return to the right work | Clear current server/project, recent conversations, pins, and an obvious view of work needing attention | Resume from a cold start and a notification into the correct conversation; distinguish stale cached state from live state |
| Give a useful instruction | Comfortable multiline composer; predictable Send/Stop; paste, voice and screenshot/file input; deliberate model/agent selection | Draft text and attachments survive the promised interruption/recovery paths; no duplicate sends; queued, sending, sent and failed states are distinguishable |
| Understand progress | Concise running status, pending question/permission, completion and failure; expandable tool detail | Reconnection and cross-client changes reconcile; completion and unread state agree with the server |
| Unblock work confidently | Explain what is requested, show the relevant command/files, offer clear answers and preserve the current draft | Approve or answer once; already-resolved requests reconcile; a changed project cannot redirect an action |
| Review and steer | Outcome summary, affected files, readable phone diffs, useful test/error output, and an easy follow-up instruction | Review real changed content, retain selection during refresh, and preserve comments/drafts through navigation |
| Install and keep using it | One understandable download destination, visible version/build identity, accurate update notes and predictable in-place upgrades | Public APK downloads without Actions access; package/version/signer are verified; install/upgrade preserves the promised local data |

The core repeat journey is: **Open → see what needs attention → inspect enough
detail → answer or steer → return later to a clear result.** First use adds
installation, pairing, provider readiness and a successful first prompt.

## Secondary persona: the phone-first builder

Uses Termux to run OpenCode on the same Android device. Shares the primary jobs
but also needs understandable installation progress, start/stop/recovery of the
local server, and clear explanations of battery/background and storage behavior.
Keep this setup route discoverable; show its maintenance controls when relevant
to the selected connection. Desktop remains a supported parity/platform concern,
while the primary interaction design is driven by the Android phone.

## UI implications

- Make the current project and the next useful action easy to identify.
- Keep the composer focused on writing, attachment review and the action that
  matches the current run state. Put occasional actions in a labeled menu.
- Use compact summaries with expandable detail for tool activity, context,
  cost and advanced server operations. Preserve full access to those features.
- Explain failures with a concrete recovery action and preserve work already
  entered. Distinguish unavailable capability from disconnected or stale state.
- Keep navigation and action labels consistent. Expose implementation terms
  only where users need them to choose correctly.
- Check the real phone flows with keyboard, large text, interrupted connectivity
  and assistive technology; visual decoration alone does not establish usability.

## Consequences for the remaining work

1. Audit the primary journey as a whole: install/pair, composer and attachment
   recovery, attention/notifications, approve/answer, review, follow-up and resume.
   Fix observed breaks before adding more primary-screen controls.
2. Resolve known daily-use gaps: camera/gallery input, attachment persistence
   and large-payload stash behavior, and live cross-client state consistency.
   Verify current behavior before deciding the implementation.
3. Keep full feature parity in scope. Sequence remaining provider credential
   management, MCP/workspace/VCS operations and persistent terminals by the
   user job they enable; expose advanced controls in the relevant destination.
4. Web search is a supporting input flow: query, review sources and deliberately
   attach selected results. Resume its implementation after the primary-journey
   review establishes that it is the next most useful work. No composer web-search
   implementation was started before this prioritization checkpoint.
5. Context inspection helps diagnose lost context; skill activation helps reuse
   instructions. Both stay accessible as supporting tools. Their presence does
   not replace the core-flow release evidence.
6. Complete the signed candidate, device/RTL/accessibility verification and
   public release gates in [release readiness](v1-release-readiness.md).

This ordering changes priority, not the requested scope of complete feature
parity and UI/UX polish. Existing implementation and API matrices remain evidence
sources; an endpoint checklist alone is insufficient to judge the experience.

## Validate the hypothesis

Observe a few representative users doing the repeat journey on their own phones.
Record where they hesitate, lose input, misunderstand state or return to the
computer. Ask what triggered opening the app and what outcome they needed.
Confirm whether remote-agent use or phone-first Termux use is more important to
the intended audience, then revise the priority order from those observations.
