# AI Team: Flutter 2.1.5 against native (s26, 2026-11-08)

This table lists every action a person can take on Flutter's AI Team screens and says where it is in native. Native
here means `:feature:team` and `:engine:team` after s23-team, s25-team and s26.

- Flutter source: `origin/release/2.1.5`, `lib/ui/screens/team/**` and the team widgets in `lib/ui/widgets`.
- What counts as an action: every `on*:` handler, a menu item, a confirm, a switch, a submitting field, pull to refresh
  and empty-state actions. A confirm dialog shares a row with the action that opens it. Labels come from `app_en.arb`.
- What "done" means: the action was traced screen → actions interface → view model → `TeamEngine.execute` or `nav`.
  Only the rows changed in s26 were checked by running a test.

Path short forms used below:

| Short form | Full path |
|---|---|
| `F/` | `lib/ui/screens/team/` (Flutter) |
| `W/` | `lib/ui/widgets/` (Flutter) |
| `ui/` | `native/feature/team/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/team/ui/` |
| `pr/` | `.../feature/team/presentation/` |

## Summary

Native scope is the **phone team only**:

- `TeamEngine.refresh` sends any provider other than `PhoneEngine` to `TeamPhase.Computer`. That covers Gas City (the team
  on a computer) and the demo fixture. `TeamPhase.Computer` shows only the `nativeCapabilityTeamDeferred` notice.
- `TeamPhase.Off` and `PhoneOff` have no setup path. `TeamConfig` is read-only, so native cannot turn the team on yet.
- Seven screens are registered: Home, Project, Board, Editor, Servers, Timeline and Task.

The project screens are the phone team's daily flow. Of their 163 actions, 127 are done, 13 are partial and 23 are
missing. The computer team and the turn-on/setup screens (sections I, J, L) are not in native. All team copy already
exists in native `strings_team.xml`.

Changed in s26:
- #17: Pause shows on an interrupted project, beside Resume (`pr/TeamPresent.kt` menu; test `B-TEAM-136`).
- #64: a change the server refused now offers "Refresh task" on the task page (`ui/TeamTaskScreen.kt`; test `B-TEAM-137`).

## Parity table

### A. Team page and project list (`F/team_page.dart`, `F/projects/team_projects_screen.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 1 | team_projects_screen.dart:103 | Top bar "New project defaults" | done | ui/TeamHomeScreen.kt "team-defaults" → openEditor(Defaults) | |
| 2 | team_projects_screen.dart:110 | Top bar "Leave demo" (demo only) | missing | — | No demo in native |
| 3 | team_projects_screen.dart:115 | Top bar "Roles and agents" | done | TeamHomeScreen.kt "team-roles" → openEditor(Roles) | |
| 4 | overview_helpers.dart:422 | Error notice "Try again" | done | TeamHomeScreen.kt failure → retry → engine.refresh | |
| 5 | team_projects_screen.dart:136 | Row "{name}: work was interrupted, tap to resume" | done | HomeRow.Interrupted → RESUME_PROJECT | Gated on resume, as in Flutter |
| 6 | team_projects_screen.dart:142 | Open-request row (editor, milestone, task, resume or answer) | done | pr/TeamPresent.kt:42-56 | A request with no task opens the project in native; Flutter does nothing |
| 7 | overview_helpers.dart:324 | Answer dialog "Answer" / cancel | done | KitInputDialog → ANSWER_REQUEST | |
| 8 | overview_helpers.dart:297 | Milestone confirm "Accept milestone" / Cancel | done | KitConfirm → ACCEPT_MILESTONE | |
| 9 | team_projects_screen.dart:158 | Project row → overview | done | KitProjectRow → openProject | |
| 10 | team_projects_screen.dart:205 | Pinned "New project" | done | bottom → openEditor(Create) | Gated on `can.lanes` |
| 11 | team_projects_screen.dart:210 | Pinned "Give a quick task" | done | bottom → openEditor(Quick) | |
| 12 | team_execution_gate.dart:102 | Blocked line "Turn on AI Team on this phone" | missing | ExecutionLines (notice only) | Needs the phone setup flow (see below) |
| 13 | team_page.dart:181 | Phone team off: "Turn on…", "Review and choose when", "Start again" | missing | `TeamPhase.PhoneOff` offers "Try again" | Same |
| 14 | team_projects_screen.dart:177 | Wide screen: pick a task in the side pane | missing | — | Tablet/desktop three-pane layout |

### B. Project overview (`F/projects/screen/overview.dart`, `overview_helpers.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 15 | overview.dart:81 | Menu "Ask again" (slow spec) | done | projectMenu → RETRY_PLAN | |
| 16 | overview.dart:86 | Menu "Resume project" (interrupted) | done | projectMenu → resume | |
| 17 | overview.dart:95 | Menu "Pause project" / "Resume project" | **done (s26)** | pr/TeamPresent.kt menu | Pause now shows on an interrupted project too |
| 18 | overview.dart:106 | Menu "Demo: unreadable plan" | missing | — | Demo only |
| 19 | overview.dart:112 | Menu "Advance demo" | missing | — | Demo only |
| 20 | overview.dart:117 | Menu "Stop project" + confirm | done | askStop/confirmStop → STOP_PROJECT | |
| 21 | overview.dart:142 | Top bar "Open spec" | done | openEditor(Spec) | |
| 22 | overview_helpers.dart:422 | Error notice "Try again" | done | retry | |
| 23 | overview_helpers.dart:39 | Needs-you card action (by kind) | done | TeamProjectScreen.kt NeedsYou | |
| 24 | overview_helpers.dart:386 | Plan failed "Resume" | done | resume | |
| 25 | overview_helpers.dart:392 | Plan failed "Choose the Planner model" | partial | openEditor(Defaults) | Defaults has no model rows (see #146) |
| 26 | overview_helpers.dart:403 | Plan failed "Ask again" | done | retryPlan | |
| 27 | overview_helpers.dart:409 | Plan failed "Approve the spec again" | done | openEditor(Spec) | |
| 28 | overview_helpers.dart:~419 | Plan failed Details fold | done | KitDetailsFold | |
| 29 | overview.dart:173 | Digest "Choose the Planner model" | partial | openEditor(Defaults) | Same as #25 |
| 30 | overview.dart:184 | Digest "Resume" | done | resume | |
| 31 | overview.dart:189 | Digest "Mark as read" | done | ACKNOWLEDGE_DIGEST | |
| 32 | overview.dart:200 | Goal "Open spec" | done | openEditor(Spec) | |
| 33 | overview.dart:205 | Goal "Review plan" | done | openEditor(Plan) | |
| 34 | overview.dart:247 | Milestone row → board filtered | done | openBoard(m.id) | |
| 35 | overview.dart:259 | Inline "Accept milestone" | done | acceptMilestone | |
| 36 | overview.dart:275 | Lanes "Change" | done | openEditor(Settings) | |
| 37 | overview.dart:293 | Lane row → task | done | openTask | |
| 38 | overview.dart:319 | Merge queue item → task | done | openTask | |
| 39 | overview.dart:329 | "Merge checked work into dev" + confirm | done | PROCESS_MERGE_QUEUE | |
| 40 | overview.dart:360 | "Promote dev to main" + confirm | done | promote | |
| 41 | overview.dart:385 | Pages "Board" | done | openBoard | |
| 42 | overview.dart:392 | Pages "Timeline" | done | openTimeline | |
| 43 | overview.dart:409 | Pages "Servers" | done | openServers | |
| 44 | overview.dart:423 | Pages "Project settings" | done | openEditor(Settings) | |
| 45 | overview.dart:431 | Cost row → settings | done | openEditor(Settings) | |
| 46 | overview.dart:444 | "Delete project {project}" + confirm | done | delete, then back | |

### C. Board (`F/projects/screen/board_timeline.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 47 | :84 | "Board" / "Dependencies" | done | ui/TeamBoardScreen.kt showGraph | |
| 48 | :96 | Picker "Milestone" | done | filterMilestone | |
| 49 | :106 | Picker "Repo" | done | filterRepo | |
| 50 | :117 | Picker "Server" | done | filterServer | |
| 51 | :136 | Graph node → task | done | KitWorkGraph → openTask | |
| 52 | :157 | Lane tabs | done | selectColumn | First non-empty lane on open: not checked |
| 53 | :165 | Task card | done | KitTaskCard → openTask | |

### D. Timeline
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 54 | board_timeline.dart:230 | "Everything" / "Recent decisions" / "Merges" / "Problems" | done | TeamTimelineScreen filter | |
| 55 | board_timeline.dart:259 | Row → task | done | openTask | |

### E. Servers (`F/projects/screen/servers_card.dart`, `team_execution_gate.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 56 | servers_card.dart:64 | Task item | done | openTask | |
| 57 | servers_card.dart:72-84 | "Maximum lanes" → "Save changes" | done | UPDATE_SERVER | |
| 58 | servers_card.dart:115 | "Move task · {title}" → note → "Move task" | done | MOVE_TASK | |
| 59 | servers_card.dart:~146 | "Start over elsewhere" / "Wait for the original server" | done | restartElsewhere | |
| 60 | team_execution_gate.dart:166 | Phone server "Turn on AI Team on this phone" | missing | NotReady notice | No "failed" variant; needs setup flow |
| 61 | team_execution_gate.dart:177 | Phone server "Try again" | done | NoAnswer → retry | |
| 62 | servers_card.dart:33 | Blocked line "Turn on AI Team on this phone" | missing | ExecutionLines | Same as #12 |

### F. Task conversation (`F/projects/team_project_conversation.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 63 | :576 | Missing task "Refresh task" | done | TeamTaskScreen retry | |
| 64 | :643 | Error "Refresh task" | **done (s26)** | ui/TeamTaskScreen.kt refusal notice | Offers Refresh task and Cancel |
| 65 | :648 | "Collapse all" | missing | — | Cosmetic |
| 66 | :667 | "Instructions from the team" fold | partial | always open | Cosmetic |
| 67 | :438 | Plan "Approve and start" | done | APPROVE_PLAN | |
| 68 | :449 | Plan "Edit plan" | done | Editor(Plan) | |
| 69 | :452 | Plan "Ask to change" (focuses composer) | missing | — | No inline composer |
| 70 | :457 | Plan "Not yet" | done | back | |
| 71 | :405 | Plan task "Run "{task}" on" sheet | done | MOVE_TASK | |
| 72 | :718 | "Accept phase" | done | ACCEPT_PHASE | |
| 73 | :200 | Request "Send answer" | done | ANSWER_REQUEST | |
| 74 | :205 | Request "Resume" | done | Resume | |
| 75 | :214 | Request "Start task again" | done | Restart | |
| 76 | :221 | "Select open findings" | done | selectOpenFindings | |
| 77 | :240 | Conflict "Resolve with agent" | done | RESOLVE_CONFLICT agent | |
| 78 | :249 | Conflict "I'll resolve" | done | manual | |
| 79 | :259 | Conflict "Re-check resolution" | done | recheck | |
| 80 | :335 | Work log fold | done | KitExpandRow | |
| 81 | :808 | Finding checkbox | done | toggleFinding | |
| 82 | :823 | "Fix selected" | done | FIX_FINDINGS | |
| 83 | :833 | "Re-check task" | done | RECHECK_TASK | |
| 84 | :837 | "Ignore selected finding" + reason | done | IGNORE_FINDING | |
| 85 | :856 | "Check and merge to dev" + confirm | done | TaskConfirm.Merge | Confirm omits the dev count line |
| 86 | :527 | Promote "See changes" (diff) | missing | — | |
| 87 | :544 | "Promote dev to main" + confirm | done | TaskConfirm.Promote | |
| 88 | :556 | Promote "Not yet" | done | back | |
| 89 | :896 | "View changes" | partial | KitMonoText patch | Raw patch, not a diff viewer |
| 90 | :905 | "Pause task" | done | PAUSE_TASK | |
| 91 | :915 | "Resume task" | done | RESUME_TASK | |
| 92 | :921 | "Start task again" | done | RESTART_TASK | |
| 93 | :927 | "Verify task" | done | VERIFY_TASK | |
| 94 | :934 | "Stop task" + confirm | done | STOP_TASK | |
| 95 | :962 | Composer strip "Stop" | partial | action block Stop only | Second path to #94 |
| 96 | :969 | Composer "Message the team…" | partial | dialog → MESSAGE_TASK | Dialog, not inline; no per-task draft |
| 97 | :~945 | Branch Details fold | done | KitDetailsFold | |
| 98 | :609 | Menu "Demo: create a conflict" | missing | — | Demo only |
| 99 | :618 | Menu "Demo: add a manual commit" | missing | — | Demo only |

### G. Project editors (`F/projects/team_project_editors.dart`, `team_project_editors_roles.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 100 | editors.dart:1035 | Close | done | back | |
| 101 | editors.dart:1040 | Primary (Start planning / Start task / Approve spec / Approve and start / Apply updated plan / Save changes) | done | pr/TeamEditorViewModel.save | |
| 102 | editors.dart:1063 | Spec "Save draft" | done | SAVE_SPEC_DRAFT | |
| 103 | editors.dart:711 | Refusal "Try again" | done | save | |
| 104 | editors.dart:708 | Refusal "Make the first commit for me" | missing | — | |
| 105 | editors.dart:1101 | "Start fresh" | done | startFresh | |
| 106 | editors.dart:1109 | "Refresh latest project" + confirm | done | askReload/confirmReload | |
| 107 | editors.dart:110 | Blocked: "Check this phone first?" → "Turn on…" / "Not now" | missing | blocked notice | Needs setup flow |
| 108 | editors.dart:481 | Typing kept as a draft | done | engine.saveDraft | |
| 109 | editors.dart:1145 | Repo "Remove" | done | removeRepo | |
| 110 | editors.dart:1213 | "Choose a folder" | missing | — | Typed path only |
| 111 | editors.dart:1230 | Empty folder "Make the first commit for me" | missing | — | |
| 112 | editors.dart:1241 | "Type a path" | partial | field always shown | |
| 113 | editors.dart:1152 | "Where it runs" | done | chooseServer | |
| 114 | editors.dart:1163 | "Add repo" | done | addRepo | |
| 115 | editors.dart:1188 | Quick task "Role" | done | chooseRole | |
| 116 | editors.dart:1197 | "Plan first" | done | setPlanFirst | |
| 117 | editors.dart:1270 | "Execution mode" | done | setMode | |
| 118 | editors.dart:1282 | "Maximum lanes" | done | field | |
| 119 | editors.dart:1285 | "Only while charging" | done | setCharging | |
| 120 | editors.dart:1295 | "Keep working with the screen off" | done | setScreenOff | |
| 121 | editors.dart:1305 | "Review level" | done | setReview | |
| 122 | editors.dart:1316 | "Budget" | done | setBudget | |
| 123 | editors.dart:1327 | "Per day (USD)" / "Total (USD)" | done | fields | |
| 124 | editors.dart:1341 | "Token limit per task" | done | field | |
| 125 | editors.dart:1343 | "Fix findings automatically" + rounds | done | setAutoFix | |
| 126 | editors.dart:1360 | "Project name" / "Files to read first" | done | fields | |
| 127 | editors.dart:1386 | Spec "Ask to change" | done | REQUEST_SPEC_CHANGE | |
| 128 | editors.dart:1410 | Milestone "Move up" / "Move down" | done | moveMilestone | |
| 129 | editors.dart:1427 | Milestone "Remove" | done | removeMilestone | |
| 130 | editors.dart:1434 | "Add milestone" | done | addMilestone | |
| 131 | editors.dart:1438 | "Version history" | done | toggleHistory | |
| 132 | editors.dart:1478 | Version row | done | KitExpandRow | |
| 133 | editors.dart:1485 | Version diff, open all | missing | text only | |
| 134 | roles.dart:22 | Failed plan "Use as one task" | done | USE_PLAN_AS_TASK | |
| 135 | roles.dart:26 | Failed plan "Ask again" | done | RETRY_PLAN | |
| 136 | roles.dart:34 | "Review gate · risky" | done | setRisky | |
| 137 | roles.dart:53/65/77 | Task "Role" / "Repo" / "Server" | done | setTask* | |
| 138 | roles.dart:90 | "Depends on" | done | toggleDependency | |
| 139 | roles.dart:103 | "Remove task" | done | removeTask | |
| 140 | roles.dart:282 | Role row → edit | done | editRole(id) | |
| 141 | roles.dart:285 | "Add role" | done | editRole(null) | |
| 142 | roles.dart:297 | Role "Model" picker | partial | typed field | No model list |
| 143 | roles.dart:299 | Role "Fallback model" | partial | typed field | |
| 144 | roles.dart:308 | "All roles" | done | allRoles | |
| 145 | editors.dart:1040 | Save role | done | SAVE_ROLE | |
| 146 | roles.dart:214 | Defaults "Models": Planner / Worker / Checker picker (saves at once) | missing | — | |
| 147 | roles.dart:204 | "Ask again" once the planner model is saved | missing | — | |
| 148 | editors.dart:444 | Opens the Planner picker by itself | missing | — | |

### H. Model sheet (`F/team_model_sheet.dart`)
| # | Flutter | Action (English) | Native | Native location | Notes |
|---|---|---|---|---|---|
| 149 | :58 | "Same as this phone's OpenCode" | missing | — | `KitModelSheet` could be reused |
| 150 | :74 | Pick a model | missing | — | |

### I. Team off / intro (`F/team_intro_screen.dart`)
| # | Flutter | Action (English) | Native | Notes |
|---|---|---|---|---|
| 151 | :254 | "Turn on AI Team on this phone" | missing | `TeamPhase.Off` shows a title only |
| 152 | :263 | "Turn on AI Team on {server}" | missing | Computer team |
| 153 | :269 | "Set up AI Team on {server}" | missing | Computer team |
| 154 | :277 | "Enter its address" | missing | Computer team |
| 155 | :287 | "Try AI Team demo" | missing | Demo |
| 156 | :436 | "Run it on a computer" | missing | Computer team |
| 157 | :459 | Install check "Try again" | missing | |

### J. Phone team setup (`F/team_phone_setup_screen.dart`): not in native
| # | Flutter | Action (English) | Native |
|---|---|---|---|
| 158 | :438 | "Turn on AI Team on this phone" | missing |
| 159 | :321 | "Stop OpenCode briefly?" → "Stop and continue" / "Not now" | missing |
| 160 | :407 | "Open app settings" | missing |
| 161 | :413 | "Start again" | missing |
| 162 | :384 | Done "New project" | missing |

### K. Demo page (`F/project_demo_screen.dart`)
| # | Flutter | Action (English) | Native | Notes |
|---|---|---|---|---|
| 163 | :65 | "Try again" | missing | Demo only |

### L. Old team (Gas City, the team on a computer): deferred
Native shows `nativeCapabilityTeamDeferred` ("AI Team is planned after the native switch.") for this provider. None of
these actions exist in native. The strings are already ported.

| # | Flutter | Actions |
|---|---|---|
| 164-182 | team_home_screen.dart | Search tasks, Board, Team settings, Refresh, What's new, Give the team a task, filter, show more, host project row, pull to refresh, check again, technical details, dismiss |
| 183-189 | team_settings_screen.dart | Agents, Workers model, Turn on on this phone, Change address, Technical details, Turn off the AI Team, dismiss notices |
| 190-193 | team_agents_screen.dart | Try again, New role, pull to refresh, role/worker row |
| 194-208 | agent_screen.dart | Refresh, Back, Open conversation, Pause/Nudge/Restart/Stop/Force stop (with confirms), Undo pause, pull to refresh, question row, receipt retry, How, Start again |
| 209-217 | role_screen.dart | Examples, Model, Save/Create role, Give a task, Reset, Delete, working-on row, Open its conversation, earlier task |
| 218-231 | gate_sheet.dart | Try again, How, choice, Send, Approve, Deny, Mark done, Ask the team to fix it, Send again, Open agent's page, Watch, Stop work, Report this failure, blocking work |
| 232-238 | merge_section.dart | Merge, Approve request, Review changes, Approve the merge, Merge into {branch}, Try again, work row |
| 239-249 | start_run_sheet.dart | Objective, project, supervision, Send to the planner, Keep in backlog, role, task fields, Send to an agent, Host guide, Try again, Wake the planner |
| 250 | task_details_sheet.dart | Step |
| 251-258 | team_board_screen.dart, W/team_board_card.dart, W/team_board_move_sheet.dart | Choose project, Give a task, retries, pull to refresh, columns, card open/move/long-press, move choices |
| 259-261 | team_needs_you.dart, work_sheet.dart | Choices, Allow, Deny, answer, details, open conversation, related work, refresh |
| 262-263 | team_rig_screen.dart | Jobs, job switch, receipt retry, Pause/Resume/Delete project |
| 264 | team_migration.dart | Switch to the new AI Team on this phone, Keep the old team, Try demo |
| 265 | team_states.dart | Try again, Start AI Team on this phone, Start again |
| 266 | W/team_host_form.dart, team_now*.dart, team_controls.dart | Test and turn on, Cancel test, Save anyway, address fields, set-up guide, wake, Why?, Hide, retries |

Native has one action Flutter lacks: refusal notices have a "Cancel" dismiss (`dismissRefusal`) on Home, Project, Task
and Servers.

## Missing actions a daily user would hit (not built in s26)

Most frequent first. Each needs work bigger than this wave's fence allows, and items 1 to 3 need new engine contracts.

1. **Turning the phone team back on** (#12, 13, 60, 62, 107, 151, 158-162). Flutter says an app update stops the phone
   team, so every phone-team user hits this after an update.
   - Flutter: `F/team_phone_setup_screen.dart` and `lib/state/phone_team_setup.dart`. The flow runs a safety check, asks
     "Stop and continue" before briefly stopping OpenCode, then starts the engine.
   - Native needs a setup flow, probably in `:engine:runtime` around `BuiltinLinuxBridge.stopServerForPhoneEngineSetup`,
     and a way to write the profile's `orchestration` config, which `TeamConfig` treats as read-only today. It also
     needs a route and actions on Home, Servers and the editor.
2. **Choosing the Planner, Worker and Checker models from a list**, then "Ask again" (#25, 29, 142, 143, 146-150).
   - This is the suggested fix for "plan failed: model not configured". Native opens Defaults, which has no model rows;
     today the only way is Roles › Planner and typing a model name.
   - Native needs a model list source for the phone server, a pick action that sends SAVE_ROLE at once, and RETRY_PLAN
     for another project (`TeamRoute.Editor` would carry `focusPlannerModel` / `retryProjectId`).
3. **"Choose a folder" and "Make the first commit for me"** when creating a project (#104, 110-112).
   - Flutter: `_pickFolder`, `_probeRepo` and `_makeFirstCommit` in `team_project_editors.dart:736-818`.
   - Native would reuse the new-conversation folder browser and `BuiltinScripts.firstCommitScript`.
4. **Seeing changes as a diff** (#86, 89, 133). `:ui:kit-chat` has `KitDiffView`; the team page shows raw patch text.
5. **Messaging inside the task page** (#69, 95, 96). Flutter has an inline composer with a per-task draft and "Ask to
   change"; native uses a dialog.

## Left out on purpose

- **Demo-only actions** (#2, 18, 19, 98, 99, 155, 163): they act on a simulated workspace. Native sends the demo
  provider to `TeamPhase.Computer`.
- **Three-pane layout** (#14): wide windows only.
- **Collapse all and the Instructions fold** (#65, 66): cosmetic; everything is shown.
- **Composer-strip Stop** (#95): a second path to Stop task, which native has.
- **The whole computer team** (#152-154, 156, 164-266): deferred by product decision (`nativeCapabilityTeamDeferred`).
  Anyone whose team runs on a computer would hit every row in section L.
- **Dead code**: `W/team_phone_onboarding.dart` and `W/team_agent_row.dart` have no importers on `release/2.1.5`.
- **Outside the team screens**: `W/builtin_team_section.dart` on This phone ("Add AI Team", "Turn on AI Team for
  {project}", "Start AI Team", "Bring the team's work into {project}", "Turn off AI Team"). Native only shows the setup
  step title. It looks like the only place a phone-team user can turn the team off, so it is missing for them.
