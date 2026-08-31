# OpenCode Mobile (`oc_app`) — dedicated UI/UX audit

**Repository:** `Eslamasabry/opencode-mobile`  
**Audited branch:** `master`  
**Audited commit:** `9e7d5ce5431f8d20b8da028c20e8c71787d29384`  
**Audit date:** 29 August 2026  
**Scope:** information architecture, navigation, onboarding, workspace, chat, permissions/forms, files, review, terminal, settings, visual system, accessibility, responsive behavior, interaction discoverability, copy, motion, and launch presentation.

> **UI/UX verdict: strong product underneath an overexposed interface.**
>
> The app is already capable enough for a credible controlled public preview. The main problem is not missing functionality; it is that too much functionality is exposed simultaneously. The next release should converge the experience around three user goals: **start or continue work, supervise active agents, and inspect or change output**.

## 1. Executive scorecard

| Area | Score | Assessment |
|---|---:|---|
| Product usefulness | 8.8/10 | Exceptional mobile coding-agent capability across chat, tools, permissions, files, review, terminal, workspaces, background operation, voice, widgets, and dual protocols. |
| Core chat experience | 7.8/10 | Strong streaming, recovery, tool rendering, drafts, queueing, forms, and context handling; composer density and hidden delivery interactions hold it back. |
| Information architecture | 6.1/10 | Workspace, Mission Control, Requests, More, model selection, and app-bar actions overlap. |
| Navigation clarity | 6.4/10 | Four stable tabs are understandable, but global actions and secondary hubs duplicate one another. |
| Onboarding/first run | 7.1/10 | Major dead ends are fixed; topology, trust, authentication, and setup implementation still arrive too early. |
| Interaction discoverability | 5.9/10 | Long press, swipe, queue/steer, message actions, and advanced controls are powerful but insufficiently visible. |
| Mobile ergonomics | 7.0/10 | Many 44/48 dp improvements and keyboard-safe layouts exist; compact composer and dense app bars still create friction. |
| Visual hierarchy | 6.5/10 | Good Material 3 base and default OpenCode identity; locally styled surfaces and semantic drift weaken coherence. |
| Design-system consistency | 5.8/10 | Token plumbing exists, but call-site migration is unfinished and new features continue to add variants. |
| Accessibility | 6.0/10 | Several critical flows have good semantics; text-scale clamping and lack of automated accessibility gates remain serious. |
| Error/recovery UX | 7.3/10 | Strong for a developer tool, with a few remaining raw exception and inconsistent recovery paths. |
| Responsive/tablet behavior | 7.1/10 | Navigation rail, width caps, split files, adaptive forms, and constrained sheets are good foundations. |
| Desktop UX | 4.6/10 | A working build is not yet a desktop product: pointer affordances, shortcuts, platform gating, packaging, and desktop-specific layouts are incomplete. |
| Public-preview presentation | 7.0/10 | Video, screenshots, and feature depth are strong; the public story should be simpler and task-led. |
| **Overall UI/UX readiness** | **6.8/10** | **Suitable for a controlled preview after focused hierarchy, accessibility, and consistency fixes; not ready to claim polished/stable UX.** |

## 2. Review method

Source review covered the root shell, Workspace, chat/composer/transcript, Files, Review, Terminal, Mission Control, Requests, More, Settings, Servers/onboarding, form rendering, product states, connection banners, theme packs, and prior audit/re-verification documents.

Primary files include:

- `lib/ui/screens/home_screen.dart`
- `lib/ui/screens/workspace_screen.dart`
- `lib/ui/screens/chat_screen.dart`
- `lib/ui/screens/chat/composer.dart`
- `lib/ui/screens/chat/message_view.dart`
- `lib/ui/screens/files_screen.dart`
- `lib/ui/screens/review_workspace.dart`
- `lib/ui/screens/terminal_screen.dart`
- `lib/ui/screens/mission_control_screen.dart`
- `lib/ui/screens/requests_screen.dart`
- `lib/ui/screens/library_screen.dart`
- `lib/ui/screens/settings_screen.dart`
- `lib/ui/screens/servers_screen.dart`
- `lib/ui/widgets/form_renderer.dart`
- `lib/ui/widgets/product_states.dart`
- `lib/ui/widgets/connection_status_banner.dart`
- `lib/ui/app_theme.dart`
- `lib/ui/theme_packs.dart`
- `docs/ui-audit-lenses.md`
- `docs/reverification-report.md`

This is a static product/source review, not moderated usability research. Navigation recommendations should be validated through task testing before a large rewrite.

## 3. The product model users should feel

The code is organized around features and server capabilities. Users should experience three primary objects:

1. **Workspace:** where am I working?
2. **Session:** what is the agent doing, and what does it need from me?
3. **Output:** what changed, and do I approve, refine, or continue it?

### Recommended experience architecture

```text
Workspace
├── Current project/workspace context
├── Continue active sessions
├── Recent sessions
└── Start a prompt

Session
├── Conversation + live tools
├── Needs attention
├── Context / files / changes / review
└── Session actions

Activity
├── Needs attention across sessions
├── Running sessions
└── Recently completed sessions

Explore / More
├── Models & agents
├── Providers & MCP
├── Commands & skills
└── Settings, privacy, diagnostics, setup
```

Mission Control and Requests should become one cross-session **Activity** concept. “Needs attention” is a section/filter with exact resolver surfaces, not a second global destination.

## 4. Highest-priority findings

### UX-P0-01 — Duplicate global control centers

**Severity:** High  
**Files:** `home_screen.dart`, `library_screen.dart`, `mission_control_screen.dart`, `requests_screen.dart`

The root app bar exposes model/agent selection, Mission Control with a pending badge, Pending Requests with the same badge, and overflow actions. More repeats Mission Control, Requests, Models, and Settings. Mission Control already lists pending items, while Requests owns the exact resolver UI.

**Impact**

- Two adjacent badges represent the same count.
- Users must learn the implementation distinction between “Mission Control” and “Pending requests.”
- Narrow app bars sacrifice useful server/page identity.
- Settings and model selection appear in multiple global locations.

**Fix**

Create one **Activity** entry and badge. Activity contains Needs attention, Running, and Recent. Selecting an attention item opens the same permission/question/form resolver used in chat. Keep Settings only in More/Settings. Keep model selection in session/composer context and the More setup card rather than as a permanent global icon.

**Acceptance criteria**

- One global pending badge.
- Attention rows open exact resolvers, not merely the related chat.
- At 360 dp, the app-bar title retains useful identity.
- No duplicate global Settings and model actions.

### UX-P0-02 — Workspace home mixes context, management, health, and sessions

**Severity:** High  
**File:** `workspace_screen.dart`

The screen includes project selection, directory recovery, workspace chips, Worktrees, Managed workspaces, Project health, active sessions, recent sessions, archived sessions, search/refresh, and a quick-ask composer.

**Impact:** the primary task—continue or start work—is visually diluted by management destinations.

**Fix**

Make Workspace session-first:

1. compact context header showing project/workspace/branch;
2. active sessions;
3. recent sessions;
4. quick ask;
5. a single “Manage project” route containing Worktrees, Managed workspaces, Project health, and lower-frequency configuration.

Project/workspace switching should be one coherent context sheet instead of a project row plus separate horizontal workspace chips.

### UX-P0-03 — Compact composer is too control-heavy

**Severity:** High  
**File:** `chat/composer.dart`

The compact row places Commands, model context, attach, voice, text field, and send/stop side by side. The text field becomes the least visually stable element, particularly with large text, narrow phones, or send + stop while a v2 run is active.

**Fix**

- Keep text field and send/stop as the only always-visible controls.
- Put Commands, Attach, and Voice in one leading tools button/sheet.
- Show model/agent/variant as a compact context chip above or within the field header, not another equal icon.
- Keep full-screen editor discoverable but secondary.
- Ensure the text field retains a useful minimum width at 320–360 dp and 2×+ text scale.

### UX-P0-04 — Queue versus steer is gesture-only knowledge

**Severity:** High  
**File:** `chat/composer.dart`

On v2, tapping Send while busy steers the current run and long pressing opens queue/steer choices. This is powerful but dangerous: the consequence changes while the icon remains a normal send arrow, and long press has no visible affordance.

**Fix options**

Preferred:

- while busy, show a labelled split control: **Steer now** plus a dropdown for **Queue after run**;
- remember the last explicit choice only if the label remains visible;
- explain the distinction the first time and in the menu;
- provide keyboard and TalkBack access to both choices.

Minimum acceptable preview fix: replace the plain send arrow with a visible delivery menu whenever a run is active.

### UX-P0-05 — Global text-scale clamp is an accessibility workaround, not a solution

**Severity:** High  
**File:** `lib/main.dart`

The app clamps system text scaling to `1.0–2.0`, which prevents users below 1.0 from receiving their chosen size and prevents users above 2.0 from receiving their accessibility setting.

**Fix**

- remove the global clamp;
- repair layouts using wrapping, scroll, adaptive sheet/full-screen presentation, `OverflowBar`, master-detail, and flexible height;
- test at 0.8, 1.0, 1.3, 2.0, and 2.5×;
- allow only narrowly justified local clamps for decorative/non-essential text.

Critical paths that must pass at 2.5×: server setup, composer/send/stop, permission resolver, forms, Activity, Files, Review, Settings, and destructive confirmations.

### UX-P0-06 — Semantic visual roles exist but are not consistently used

**Severity:** High design-system risk  
**Files:** `app_theme.dart`, `theme_packs.dart`, review/terminal/integrations/Termux/session sheets and others

The app has a pack-aware semantic success color and Material color schemes, but multiple screens still use raw green/orange values, `hintColor`, local radii, local hairline alphas, one-off font sizes, and repeated floating-surface recipes.

**Impact:** theme packs feel partially applied and new screens drift instead of inheriting a product language.

**Fix**

Create product tokens and migrate call sites:

- semantic colors: success, warning, information, destructive, muted text, code/diff add/remove;
- spacing scale: 4, 8, 12, 16, 24, 32;
- radii: control, card, sheet, pill;
- type roles: code, caption, supporting, body, title;
- surface roles: inline, raised, floating, overlay;
- motion durations/easing;
- icon vocabulary for send, stop, queue, steer, copy, retry, external link, and connection state.

Flagship migration must include Home, Workspace, Chat, Files, Review, Terminal, Activity, More, Settings, and onboarding before preview.

## 5. Navigation and information architecture

### Current strengths

- Four stable primary destinations give the product a learnable base.
- NavigationRail adaptation is already present for wider layouts.
- More is visually stronger than a flat settings list.
- Capability gating avoids dead destinations.

### Recommended primary navigation

For phone preview:

1. **Workspace** — context and sessions.
2. **Files** — files plus Changes/Review entry.
3. **Terminal** — keep only if research shows frequent direct use; otherwise move to Session/More and promote Activity.
4. **More** — integrations, models, commands, settings, setup.

Activity should be a prominent global destination/action but not duplicated. Two viable models:

- replace Terminal in bottom navigation with Activity and expose Terminal from Session/More; or
- keep Terminal and use one Activity icon/badge in the app bar.

Choose through user testing rather than intuition. Track task frequency in moderated sessions; no analytics SDK is required.

### Root app bar

On phones, limit visible global actions to one contextual/global action plus overflow. Settings, disconnect, and manual refresh do not need permanent visible icons. Pull-to-refresh already provides contextual refresh on many screens.

The title should communicate:

- current server/profile;
- current primary destination or current session;
- connection status without relying on color alone.

## 6. Onboarding and server connection

### What is good

- First-run identity is clear and visually distinctive.
- New profile save now connects, closing the earlier “save but still stuck” problem.
- DNS, timeout, and refused states have more truthful explanations.
- On-device and remote paths are explicit.
- Keystore re-entry has a visible recovery state.

### Improvements

1. Lead with intent: **Use OpenCode already running somewhere** or **Run OpenCode on this phone**.
2. Defer topology language such as LAN, loopback, reverse proxy, and Termux bridge until the user selects a path.
3. Add a security summary before connecting remotely: encryption, password, and trust implications.
4. On successful Test, show protocol generation, server version, authentication state, and what Save will do.
5. Do not pre-seed a scheme that makes typed-over addresses unexpectedly retain the wrong protocol.
6. Explain on-device setup time, storage, battery, and what survives app closure in one concise checklist.
7. Make remote setup documentation secure-by-default; do not recommend LAN HTTP.
8. Use one progressive stepper only where steps are genuinely sequential; avoid ornamental progress.

### Suggested first-run copy

**Your coding agent, on this phone**  
Connect to OpenCode on your computer/server, or install a private on-device server.

Actions:

- **Connect to an existing server**
- **Set up on this phone**
- **How it works**

## 7. Chat and session experience

### Strong existing patterns

- streaming updates without per-token full transcript rebuilds;
- stable reversed virtualized list;
- markdown/code/tool rendering and capped previews;
- persistent per-session text drafts;
- offline queue with edit/discard and reconnect confirmation;
- forms, permissions, and questions integrated into session context;
- context-window meter;
- file-path validation before links become interactive;
- one contextual banner priority rather than stacked banners;
- stale-content retention during refresh;
- visible typing/tool activity.

### Recommended improvements

#### 7.1 Session header

Current header exposes Session views, Stop when busy, and Session actions. Replace generic icon taxonomy with one **Inspect session** entry that opens Context, Todos, Relations/Subagents, Changes/Review, and Sharing where supported. Keep Stop visible only when active. Keep rare actions in overflow.

#### 7.2 Message actions

Long press now works, but long-press-only is still weak discoverability. Provide a 44 dp action affordance in the message metadata row on hover/focus/tap context. Add an optional **Select text** mode so fixing long press does not permanently remove partial assistant-text selection.

#### 7.3 Reading while output continues

When users scroll away from latest, show both:

- Jump to latest;
- number of new messages/parts below.

Do not hide history assistance based only on a 30-message threshold; use actual viewport and pending-output state.

#### 7.4 Draft attachments

Text survives navigation; attachments do not. Either persist attachments in a protected staging store with quota/expiry or visually label them “not saved with draft” immediately after selection.

#### 7.5 Model/agent apply scope

Make scope explicit in the picker action:

- **Apply to this session**
- **Use for new sessions on this server**

Do not rely on protocol-specific behavior being inferred from button copy.

## 8. Activity, requests, permissions, questions, and forms

### Recommended unified model

Activity sections:

1. **Needs attention** — permission, question, form.
2. **Running** — active sessions and subagent counts.
3. **Recent** — completed or recently updated sessions.

Each Needs attention item should open the exact resolver component. Global/MCP forms can remain in an Activity subsection that does not pretend they map to a chat.

### Permission UX

- Show plain-language action, affected resources, originating session/tool, and scope.
- Distinguish **Allow once** from **Always allow** visually and in consequence copy.
- For “always,” show where the rule can later be removed.
- Reject-with-message should explain that the text is sent back to the agent.
- Notification actions should mirror in-app terms exactly.

### Question/form UX

The form renderer is technically strong: conditional fields, validation, adaptive sheet/full-screen mode, pinned apply bar, dismissal confirmation, and scroll-to-error.

Remaining improvements:

- safe external-link policy;
- heading/group semantics;
- required/optional state announced consistently;
- full large-text/TalkBack coverage;
- locale-aware date/time display if localization expands;
- clear explanation of global server/MCP versus session requests.

## 9. Files, Changes, and Review

### Files strengths

- Files/Symbols segmentation is capability-aware.
- Search, breadcrumbs, file status, split pane on wide screens, preview, and symbol navigation are strong.
- Refresh preserves content in several paths.

### Main workflow gap

After an agent run completes, the user’s natural question is **What changed?** The product currently separates Files and Review rather than making changed output the obvious completion path.

### Recommended design

Add a **Changes** mode within Files or a highly visible changed-files card:

- changed file count;
- additions/deletions summary;
- files grouped by status;
- tap opens Review directly at that file;
- review comment returns into the originating session’s composer without clipboard as the primary path.

Use a shared pending-prompt handoff store or callback/navigation result so Review → Chat works from every entry point.

### Review workspace

Review is feature-rich, but dense. Improve by:

- separate phone and tablet/desktop toolbars;
- keep file/scope/mode as a compact hierarchy, not equal controls;
- keep hunk navigation available while selection is active;
- add clear reviewed/viewed progress;
- preserve file, scroll, selection, and comment context through refresh;
- make Add to prompt route to the originating session rather than copying when possible;
- use semantic diff colors from the active theme pack.

## 10. Terminal

### Strengths

- Real PTY rather than a fake log viewer.
- Lifecycle/cursor concerns are treated seriously.
- Mobile key row and touch sizing have received attention.

### Improvements

- Decide whether Terminal deserves a permanent primary tab through research.
- Show active shell/session/working directory clearly.
- Provide discoverable reconnect/reset with consequence copy.
- Add configurable special-key row and horizontal overflow behavior.
- On desktop, use native keyboard shortcuts and hide the mobile key row when unnecessary.
- Use semantic terminal success/warning/error colors rather than brightness fallbacks or raw values.

## 11. More and Settings

### More

The visual destination grid is a good improvement. Reduce overlap:

- remove duplicate Activity/Requests destinations after unification;
- keep one active model/agent setup card;
- group Providers + MCP under Integrations if users do not distinguish them early;
- group Commands, Skills, and References around “Capabilities” only if that term tests well; otherwise use user language such as “Commands & skills.”

### Settings

The hub-and-spoke structure is correct. Improvements:

- keep Disconnect in Settings only, with clear unsent/queued/background consequences;
- show server health in human language, with technical detail secondary;
- platform-gate Android-only features everywhere, including routes and entry controls;
- add Local storage: drafts, queued prompts, voice models, caches, and clear actions;
- add an accessibility section only for genuine options, not workarounds that override system settings;
- explain Shorebird and desktop GitHub update behavior transparently.

## 12. Visual design system

### Required token set

```text
Color
  surface.canvas / surface.inline / surface.raised / surface.floating
  text.primary / text.supporting / text.muted
  semantic.success / warning / info / destructive
  diff.add / diff.remove / diff.context

Spacing
  4 / 8 / 12 / 16 / 24 / 32

Radius
  control 12 / card 14–16 / sheet 24 / pill full

Typography
  caption / supporting / body / title / headline / code

Motion
  quick 120–150 ms / standard 180–240 ms / route 300–400 ms
  shared easing and reduced-motion behavior
```

### Migration rules

- no raw `Colors.green/orange/red` for semantic state;
- no `hintColor` as the general muted text role;
- no repeated `AppMono` literals outside the theme/text-style layer;
- no local one-off font sizes on flagship screens unless documented;
- no new surface recipe without a named component/token;
- no icon synonym drift for the same action.

### Visual hierarchy principles

- Reduce borders and nested cards; use spacing and typography before outlines.
- One primary accent per screen; status colors are semantic, not decorative.
- Keep message/tool/file content as the hero, not surrounding chrome.
- Use cards for grouped choices or distinct actionable objects, not every row.
- Keep code and terminal treatment functional and readable rather than visually louder than prose.

## 13. Accessibility plan

### Automated tests

Add Flutter accessibility guideline checks for:

- Android tap targets;
- labelled tap targets;
- text contrast;
- selected screen semantics snapshots.

Run them across default light/dark, narrow/wide, and text-scale variants.

### Critical device tests

With TalkBack and large text:

1. add/test/save/connect server;
2. start a session and send;
3. stop a run;
4. choose queue versus steer;
5. resolve permission;
6. answer question/form;
7. inspect a changed file and add review comment;
8. recover from disconnect/queued send;
9. remove profile and understand local/server deletion.

### Additional requirements

- status cannot rely on color alone;
- heading semantics for major sections and form groups;
- focus order follows visual order;
- modal dismissal restores focus;
- all gesture actions have visible/button alternatives;
- reduced motion preserves state clarity;
- desktop focus indicator is clearly visible;
- controls remain operable at 2.5× text.

## 14. Copy and content design

### Vocabulary recommendations

| Current concept | Recommended wording |
|---|---|
| Mission Control | Activity — running sessions and requests |
| Pending requests | Needs attention |
| Remote machine (LAN) | Connect to your computer or server |
| Session views | Inspect session |
| Session actions | More session actions |
| Use model and mode | Apply to this session / Use for new sessions |
| Health unavailable | Couldn’t reach the server health check · Check again |
| Nothing in flight | No sessions are running |

### Destructive action template

Every confirmation should state:

1. what is removed;
2. where it is removed from;
3. whether server data remains;
4. whether it can be undone.

Example:

> Remove this server from the phone? Its saved address, password, drafts, queued prompts, and local preferences will be deleted. Projects and chats stored on the server will not be changed.

### Error language

- Lead with the failed user goal.
- Provide one concrete next step.
- Keep redacted technical detail secondary/copyable.
- Never display raw exception class prefixes or response bodies by default.
- Use one retry term: **Try again**.

## 15. Responsive and desktop strategy

### Phone

- primary actions in the lower half;
- text field remains dominant with keyboard open;
- dense app bars collapse into one action + overflow;
- no control below 48 dp unless a larger semantic wrapper provides the target;
- avoid nested vertical scroll traps.

### Foldable/tablet

Use master-detail for:

- Workspace sessions;
- Activity + resolver;
- Files + viewer;
- Review file list + diff;
- Settings categories + detail.

Keep conversation text at readable measure instead of stretching the phone layout.

### Desktop

Treat as experimental until it gains:

- complete platform gating for Android/Termux/voice behaviors;
- right-click context menus;
- drag-and-drop attachments;
- command palette and Esc behavior;
- resizable panels;
- native scrollbars;
- window-state persistence;
- keyboard traversal and visible focus;
- packaging and CI for declared platforms.

A NavigationRail and successful compile alone do not constitute desktop UX.

## 16. Perceived performance and trust

### Existing strengths

- streaming caret and live tool ticker;
- stale-content retention on refresh;
- explicit queued bubbles;
- foreground connection notification;
- server-truth-based Activity/Mission Control state;
- bounded inline tool content.

### Improvements

- never invent progress percentages; use real server stages only;
- for setup/downloads show current stage, downloaded size, and resumability;
- distinguish locally queued, server-admitted pending, and actively delivered prompts;
- show queue-flush confirmation globally, not only in an open chat;
- preserve scroll, selection, and context during refresh/capability changes;
- provide visible “new output below” count while reading history.

## 17. Implementation plan

### Phase 1 — public-preview UX blockers

| ID | Work | Acceptance result |
|---|---|---|
| UX-001 | Merge Mission Control and Requests into Activity | One badge/destination; exact resolver from every attention row. |
| UX-002 | Simplify root app bar | Maximum two visible actions; readable title at 360 dp. |
| UX-003 | Simplify compact composer | Text field stays dominant; secondary tools in one menu. |
| UX-004 | Replace long-press-only steer/queue | Both modes visible, labelled, keyboard/TalkBack accessible. |
| UX-005 | Remove global text-scale clamp | Critical flows pass at 0.8–2.5×. |
| UX-006 | Create/migrate product tokens | No raw semantic colors/hintColor/ad hoc core tokens on flagship screens. |
| UX-007 | Add automated accessibility tests | Tap targets, labels, contrast, and semantic flows pass. |
| UX-008 | Harden form external links | HTTPS policy, host confirmation, unsafe schemes blocked. |
| UX-009 | Normalize raw errors | No raw `error.toString()` in default UI. |
| UX-010 | Correct localization/public copy | Complete extraction or explicit English-only preview status. |

### Phase 2 — workflow convergence

| ID | Work | Acceptance result |
|---|---|---|
| UX-101 | Redesign Workspace around context + sessions | Sessions visible before management tools. |
| UX-102 | Add Changes path in Files | Completion to changed-file review in one tap. |
| UX-103 | Shared Review → prompt handoff | Clipboard is never the primary route back to chat. |
| UX-104 | New-output count while reading history | User knows unseen output exists. |
| UX-105 | Optional assistant text-selection mode | Partial copy without breaking message actions. |
| UX-106 | Persist/stage attachment drafts | Navigation does not silently lose selected files. |
| UX-107 | Unified project/workspace selector | One coherent context control and management route. |
| UX-108 | Explicit request/model permission scopes | Consequences are unambiguous. |

### Phase 3 — polished/stable

| ID | Work | Acceptance result |
|---|---|---|
| UX-201 | Tablet master-detail layouts | Workspace, Activity, Files, Review, and Settings use width productively. |
| UX-202 | Desktop interaction layer | Keyboard, pointer, context menus, drag/drop, resizing, platform gating. |
| UX-203 | Complete localization or formal English-only v1 | Full extraction/pseudolocale or documented limitation. |
| UX-204 | Golden visual matrix | Theme packs, brightness, widths, and text scales verified. |
| UX-205 | Motion-token migration | Consistent durations/easing and reduced-motion parity. |
| UX-206 | Moderated usability rounds | First prompt, supervision, permission, review, and recovery validated. |

## 18. Usability test plan

Recruit a mix of:

- experienced OpenCode users;
- developers new to OpenCode;
- users relying on large text or TalkBack;
- tablet/hardware-keyboard/desktop-oriented users.

### Core tasks

1. Connect securely to an existing server.
2. Set up on-device OpenCode and explain time/storage/battery implications.
3. Start a session in the intended project/workspace.
4. Change model/agent for only the current session.
5. While a run is active, choose Queue rather than Steer.
6. Resolve a permission from Activity.
7. Find what changed and add a review comment to the prompt.
8. Recover from a dropped connection with a queued prompt.
9. Find a previous session from another project.
10. Remove a server and explain what was deleted locally versus remotely.

### Measures

- secure-connection completion rate;
- time to first prompt;
- queue/steer comprehension;
- time to resolve pending request;
- time from completion to changed-file review;
- navigation backtracks;
- destructive-action comprehension;
- TalkBack completion without sighted assistance;
- lightweight post-task usability score.

## 19. Launch UX gate

### Safe for a controlled public preview

- [ ] One global Activity/attention entry and badge.
- [ ] Compact composer retains useful text width at narrow/large-text states.
- [ ] Queue and steer are visibly discoverable.
- [ ] Text scale is no longer globally overridden; critical flows work at 2.5×.
- [ ] Automated tap-target, label, and contrast checks pass.
- [ ] Server-controlled links use the safe confirmation policy.
- [ ] No raw exception strings in default flows.
- [ ] Flagship semantic color/type/spacing migration is complete.
- [ ] First-run security/trust copy is clear.
- [ ] Public documentation calls the preview English-only if localization remains partial.

### Safe to call polished/stable

- [ ] Workspace hierarchy validated with new and experienced users.
- [ ] Files → Changes → Review → Prompt is one coherent path.
- [ ] TalkBack critical path passes on device.
- [ ] Golden matrix passes across default/theme packs, light/dark, widths, and scales.
- [ ] Tablet layouts use master-detail patterns.
- [ ] Desktop claims match actual pointer/keyboard/platform support.
- [ ] No unresolved high-severity interaction, accessibility, or data-loss issue.

## 20. Final recommendation

Do not redesign the application from scratch. The difficult engineering and many hard interaction details already exist.

Build a **convergence release** that:

1. collapses duplicate global surfaces into Activity;
2. makes Workspace session-first;
3. simplifies the compact composer;
4. exposes queue/steer and message actions visibly;
5. makes Changes/Review the natural completion path;
6. finishes semantic design-token migration;
7. removes accessibility clamps and adds automated/device gates;
8. validates the new hierarchy through real tasks.

The target is not a less powerful product. It is a product where power appears at the moment it is needed rather than all at once.