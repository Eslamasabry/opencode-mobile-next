# AI Team actions: Flutter 2.1.5 vs native (s25-team, 2026-11-08)

Source: Flutter `test/fixtures/coverage/team_actions_ledger.json` on origin/master (157 actions: 40 phone-engine
commands, 2 host-front merge calls, 115 Gas City supervisor calls). `native (s23)` was merged before this wave;
`native (s25)` is this branch. Flutter's own "not offered" rows have no screen in Flutter either.

| Family | Action | Flutter | Where in Flutter | Native |
|---|---|---|---|---|
| Engine | acceptMilestone | reachable | Project page › milestone › Accept | native (s23) |
| Engine | acceptPhase | reachable | Task conversation › Accept phase | native (s25): Task › phase card › Accept phase |
| Engine | acknowledgeDigest | reachable | Project page › Since you were away › Mark as read | native (s25): Project › Since you were away › Mark as read |
| Engine | advance | not offered | only the built-in demo project shows this control; a real engine never gets it | n/a (Flutter does not offer it) |
| Engine | answerRequest | reachable | Project page › Needs you › Answer | native (s23) |
| Engine | approvePlan | reachable | Plan editor › Approve plan | native (s25): Plan editor › Approve plan; Task › plan card › Approve plan |
| Engine | approveSpec | reachable | Spec editor › Approve | native (s25): Spec editor › Approve (saves the draft first) |
| Engine | createProject | reachable | Projects › New project | native (s25): Team › New project › Start planning |
| Engine | createQuickTask | reachable | Projects › Quick task | native (s25): Team › Give a quick task |
| Engine | deleteProject | reachable | Project page › Delete project (a visible row; asks first, naming it, what goes and what stays) | native (s25): Project › Delete project (asks, names it, lost/kept) |
| Engine | deleteRole | not offered | no screen deletes a project role; roles are only added and edited | n/a (Flutter does not offer it) |
| Engine | fixFindings | reachable | Task conversation › Fix selected | native (s25): Task › findings › Fix selected |
| Engine | ignoreFinding | reachable | Task conversation › Ignore (asks for a reason) | native (s25): Task › findings › Ignore (asks for a reason) |
| Engine | messageTask | reachable | Task conversation › Send a message | native (s23) |
| Engine | moveTask | reachable | Plan › task › Move to another computer | native (s25): Servers › Move (hand-off note, restart elsewhere); Task › plan card › who |
| Engine | pauseProject | reachable | Project menu › Pause | native (s23) |
| Engine | pauseTask | reachable | Task conversation › Pause | native (s25): Task › Pause |
| Engine | processMergeQueue | reachable | Merges › Merge | native (s25): Project/Task › Merge next (asks first) |
| Engine | promote | reachable | Merges › Promote (asks first, pins both commits) | native (s25): Project/Task › Promote (asks, pins both commits) |
| Engine | recheckTask | reachable | Task conversation › Check again | native (s25): Task › findings › Check again |
| Engine | replan | reachable | Plan editor › Plan again | native (s25): Plan editor › Apply plan |
| Engine | requestSpecChange | reachable | Spec editor › Ask for a change | native (s25): Spec editor › Ask for a change |
| Engine | resolveConflict | reachable | Merges › Resolve conflict | native (s25): Task › conflict › agent / manually / check again |
| Engine | restartTask | reachable | Task conversation › Start task again | native (s25): Task › Start task again |
| Engine | resumeProject | reachable | Project menu › Resume, and the digest's Resume | native (s23) |
| Engine | resumeTask | reachable | Task conversation › Resume | native (s23) |
| Engine | retryPlan | reachable | Project page › plan failed › Ask again | native (s25): Project menu › Ask again; plan failed › Ask again; Plan editor › Ask again |
| Engine | saveRole | reachable | Roles › role › Save changes | native (s25): Roles › role › Save |
| Engine | saveSpecDraft | reachable | Spec editor › Save draft, and the first step of Approve | native (s25): Spec editor › Save draft |
| Engine | simulateConflict | not offered | only the built-in demo project shows this control; a real engine never gets it | n/a (Flutter does not offer it) |
| Engine | simulateManualCommit | not offered | only the built-in demo project shows this control; a real engine never gets it | n/a (Flutter does not offer it) |
| Engine | simulatePlanFailure | not offered | only the built-in demo project shows this control; a real engine never gets it | n/a (Flutter does not offer it) |
| Engine | stopProject | reachable | Project menu › Stop project (asks first, naming the project) | native (s23) |
| Engine | stopTask | reachable | Task conversation › Stop task (asks first, naming the task) | native (s25): Task › Stop task (asks, names it) |
| Engine | undoMerge | not offered | not feasible (checked 2026-10-10) — the phone engine's command list has no undoMerge (only the demo project si | n/a (Flutter does not offer it) |
| Engine | updateDefaults | reachable | Team defaults › Save changes | native (s25): Team › New project defaults › Save |
| Engine | updateServer | not offered | a task can be moved to another computer, but a computer's lane limit is not edited from the phone | native (s25): Servers › Max lanes › Save |
| Engine | updateSettings | reachable | Project › Settings › Save changes | native (s25): Project › Settings › Save |
| Engine | usePlanAsTask | reachable | New project › plan could not be read › Use as one task | native (s25): Plan editor › Use as one task |
| Engine | verifyTask | reachable | Task conversation › Check this task | native (s25): Task › Check this task |
| Front | POST /v0/city/{cityName}/front/merge/{run} | reachable | Task page › Merge (two-step, names the task and the branch) | MISSING: computer team (Gas City) not in native yet |
| Front | POST /v0/city/{cityName}/front/mr/{bead}/approve | reachable | Task page › Approve merge | MISSING: computer team (Gas City) not in native yet |
| GC | DELETE /v0/city/{cityName}/agent/{base} | not offered | workers' roles are edited on the Agents page, which the app keeps itself; it never creates or edits supervisor | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/agent/{dir}/{base} | not offered | workers' roles are edited on the Agents page, which the app keeps itself; it never creates or edits supervisor | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/bead/{id} | not offered | work is changed through update, close and reopen; the app never deletes or edits a bead any other way | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/convoy/{id} | not offered | tasks are started as new work and sent to a worker; the app never builds or edits convoys itself | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/extmsg/adapters | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/extmsg/participants | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/formulas/{name} | not offered | formulas are authored on the computer; the app reads their runs only | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/mail/{id} | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/packs/{name} | not offered | packs are installed on the computer | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/patches/agent/{base} | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/patches/agent/{dir}/{base} | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/patches/provider/{name} | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/patches/rig/{name} | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/provider/{name} | not offered | model providers for the team are set up on the computer; the phone's own model choice is a separate setting | n/a (Flutter does not offer it) |
| GC | DELETE /v0/city/{cityName}/rig/{name} | reachable | Team › Projects › the project › Delete project (asks first, naming it, what goes and what stays) | MISSING: computer team (Gas City) not in native yet |
| GC | DELETE /v0/city/{cityName}/workflow/{workflow_id} | not offered | workflow runs are managed on the computer | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName} | covered elsewhere | the heat guard pauses and resumes the phone's own city on its own, never from a control (test/thermal_guard_te | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/agent/{base} | not offered | workers' roles are edited on the Agents page, which the app keeps itself; it never creates or edits supervisor | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/agent/{dir}/{base} | not offered | workers' roles are edited on the Agents page, which the app keeps itself; it never creates or edits supervisor | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/bead/{id} | not offered | work is changed through update, close and reopen; the app never deletes or edits a bead any other way | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/provider/{name} | not offered | model providers for the team are set up on the computer; the phone's own model choice is a separate setting | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/rig/{name} | not offered | a project's folder and settings are set when the app adds it; from the phone a project is only paused, resumed | n/a (Flutter does not offer it) |
| GC | PATCH /v0/city/{cityName}/session/{id} | not offered | workers are driven through message, stop, force stop, suspend and wake; creating, renaming or closing a sessio | n/a (Flutter does not offer it) |
| GC | POST /v0/city | not offered | the team's city is created, started and removed by the app's own setup, not through the supervisor's calls | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/agent/{base}/{action} | reachable | Agent page › Pause (with Undo) and Resume, for an agent by its own name | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/agent/{dir}/{base}/{action} | reachable | Agent page › Pause (with Undo) and Resume, for an agent inside a project | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/agents | not offered | workers' roles are edited on the Agents page, which the app keeps itself; it never creates or edits supervisor | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/bead/{id}/assign | not offered | work is changed through update, close and reopen; the app never deletes or edits a bead any other way | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/bead/{id}/close | reachable | Board › card Move › Cancel (asks first, naming the card) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/bead/{id}/reopen | reachable | Board › a cancelled card › Put back | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/bead/{id}/update | reachable | Board › card Move: change priority, send back to the backlog, and the first step of Cancel | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/beads | reachable | Start a task, and New work on the board | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/convoy/{id}/add | not offered | tasks are started as new work and sent to a worker; the app never builds or edits convoys itself | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/convoy/{id}/close | reachable | Task page › Stop task (asks first, naming the task) and the gate sheet's Stop | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/convoy/{id}/remove | not offered | tasks are started as new work and sent to a worker; the app never builds or edits convoys itself | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/convoys | not offered | tasks are started as new work and sent to a worker; the app never builds or edits convoys itself | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/events | not offered | events are read, never written; rotating the host's log is host maintenance | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/events/rotate | not offered | events are read, never written; rotating the host's log is host maintenance | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/adapters | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/bind | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/groups | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/inbound | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/outbound | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/participants | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/transcript/ack | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/extmsg/unbind | not offered | outside-chat adapters (messengers) are the host's own setup; the team screens never use them | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/formulas/{name}/preview | not offered | formulas are authored on the computer; the app reads their runs only | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/formulas/{name}/validate | not offered | formulas are authored on the computer; the app reads their runs only | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/mail | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/mail/{id}/archive | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/mail/{id}/mark-unread | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/mail/{id}/read | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/mail/{id}/reply | not offered | the team's internal mail is read by the workers; the app has no inbox | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/maintenance/dolt-gc | not offered | store maintenance belongs to the computer | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/order/{name}/disable | reachable | Team › Projects › the project › Scheduled jobs › the job's switch (on or off; a job's schedule is said in words) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/order/{name}/enable | reachable | Team › Projects › the project › Scheduled jobs › the job's switch (on or off; a job's schedule is said in words) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/order/{name}/run | not offered | only a job set to start from a web request can be run by hand, and it takes inputs of its own; the phone switc | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/packs | not offered | packs are installed on the computer | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/providers | not offered | model providers for the team are set up on the computer; the phone's own model choice is a separate setting | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/rig/{name}/{action} | reachable | Team › Projects › the project › Pause project (asks first, naming it) and Resume project | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/rigs | not offered | projects are added by the app's own setup (it needs the folder on the computer), not through the supervisor's  | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/runs/{run_id}/cancel | reachable | Gate sheet › Stop (asks first, naming the task) for a formula run | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/service/{name}/restart | not offered | host services are restarted on the computer | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/session/{id}/close | not offered | workers are driven through message, stop, force stop, suspend and wake; creating, renaming or closing a sessio | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/session/{id}/kill | reachable | Agent page › Force stop (a secondary entry under Stop, and offered when a Stop did not end the session; asks first, naming the agent) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/session/{id}/messages | reachable | Agent page › Message and Nudge | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/session/{id}/permission-mode | not offered | the supervisor takes a provider-defined value (a free string with no list of allowed ones), so there is no saf | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/session/{id}/rename | not offered | workers are driven through message, stop, force stop, suspend and wake; creating, renaming or closing a sessio | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/session/{id}/respond | reachable | Needs you › the gate sheet's Approve, Deny, option or typed answer (Approve asks first when destructive) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/session/{id}/stop | reachable | Agent page › Stop (asks first, naming the agent and its task) and Restart | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/session/{id}/submit | not offered | workers are driven through message, stop, force stop, suspend and wake; creating, renaming or closing a sessio | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/session/{id}/suspend | reachable | Agent page › Pause, for a worker that has only a session | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/session/{id}/wake | reachable | Agent page › Resume and Restart (Restart asks first, naming the agent) | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/sessions | not offered | workers are driven through message, stop, force stop, suspend and wake; creating, renaming or closing a sessio | n/a (Flutter does not offer it) |
| GC | POST /v0/city/{cityName}/sling | reachable | Gate sheet › Send the work again, and Assign on a task | MISSING: computer team (Gas City) not in native yet |
| GC | POST /v0/city/{cityName}/unregister | not offered | the team's city is removed by the app's own setup, not through the supervisor | n/a (Flutter does not offer it) |
| GC | PUT /v0/city/{cityName}/formulas/{name} | not offered | formulas are authored on the computer; the app reads their runs only | n/a (Flutter does not offer it) |
| GC | PUT /v0/city/{cityName}/patches/agents | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | PUT /v0/city/{cityName}/patches/providers | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |
| GC | PUT /v0/city/{cityName}/patches/rigs | not offered | configuration patches are edited on the computer | n/a (Flutter does not offer it) |

## Totals

- Front missing: 2
- GC missing: 19
- Engine n/a: 6
- GC n/a: 61
- Engine native: 34
