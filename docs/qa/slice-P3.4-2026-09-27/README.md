# slice-P3.4 — The team page is one page (2026-09-27)

**Finish line.** Every door to the AI Team opens one page. On, it is the team
(Now, what needs you, the tasks in one list by urgency, where it runs, how fast
it runs there, what it spent today, Turn off). Off, the same page says what the
team does and sets it up for this kind of server. The team-plugin-sheet is gone.

**Non-goal.** There is no Now-line engine (P5.1), no task cost (see Codex X52:
the host has no contract for it) and no pool recovery.

Branch `revamp/slice-P3.4`, based on `feat/phone-setup-v2` at `4c81a913`.

## What changed, page by page

### team-home (`lib/ui/screens/team/team_home_screen.dart`)

- **One route.** A new `TeamPage` / `openTeamPage` (`lib/ui/screens/team/team_page.dart`)
  follows the connection. It shows the team while the team is on and the off
  state (team-intro) while it is off. Turning the team on changes the same page
  into the team. There is no hop back and no second page.
- **Pause for heat vs pause by the person (persona row 20, acceptance).** The
  page reads the heat guard's hold through Codex X52's `teamOverview`. The hold
  is matched to this profile, the phone host and the city.
  - **Subtitle.** It shows "On this phone · Cooling down" or "· Stopped to cool
    down". The person's pause is still "· Paused" (suspended agents), and a team
    that is only asleep is not a pause at all.
  - **Line at the top.** The heat guard's line says when the team paused and
    that it carries on by itself: "The phone got hot at 14:02, so the team
    paused. It carries on by itself once the phone has cooled." There is **no
    Resume**, because waking the agents would heat the phone again. The
    person's pause keeps its "The team is paused" line with Resume.
  - **Agents row.** It says "3 agents · resting while the phone cools".
  - **Stopped by Android.** When Android stops the phone's Termux team, the
    subtitle says "Stopped". The "Start the team again" line (P1.7) now scrolls
    with the page, because at 2.5× text it no longer fits a fixed header. The
    page shows nothing that contradicts it: the "not answering, the app keeps
    trying" state P1.7 flagged is gone, as is the loading bar.
- **The team itself, one panel.** It holds three rows:
  - the agents row;
  - a new **"Technical details" row**, whose line is how fast the team runs
    where it runs (the host speed line). It replaces the top bar's info button,
    so the place is said once, in the subtitle;
  - **what it spent today** ("Today · $0.42 est. · 12.4k tokens"). The row
    appears only when the host reports X52 usage evidence. It says when some use
    is unpriced or history is partial. Unknown is never "$0", and it is never a
    task's cost.
- **Menu (the old sheet's switches).**
  - Refresh (useful on a PC, which has no pull gesture).
  - Change address (not for the team inside the app).
  - The phone Termux team's own controls: keep it running, stop, remove. They
    are reachable in every state of the page, including when the team does not
    answer.
  - Turn off. It asks first. For the team inside the app it first calls
    `BuiltinTeam.turnOff()`. If that fails, the team stays on and the page says
    so in plain words. Once the team is off, the page is the off state.
  - When the page is opened on its own (from a task's conversation), Change
    address and Turn off go back to the first route, because the team that page
    showed is gone.
- **Needs you card.** It is inset by the gutter less the needs-you ring, so its
  edge lines up with the rows (leftover-units addition).
- **Not answering (the wording P1.7 left to this slice).** The title is now
  "The team isn't answering" and the body says what to check:
  - the phone: "The app keeps trying while the team starts on this phone.";
  - a computer: "The app keeps trying. Check that dev-pc is on and online."

  This is shared by the team screens and the board through `teamScreenState` /
  `teamNotAnsweringBody`.

### team-intro (`lib/ui/screens/team/team_intro_screen.dart`)

- It is now the team page's off state (merge-into:team-home). It no longer pops
  after Set up, Turn on or Enter its address.
- The top bar says "Off" once (leftover-units addition: one state word; the page
  itself says what turning on does).
- **On a computer:**
  - "Enter its address" is offered both when a team was found and when it was
    not.
  - When nothing was found, a notice says why in plain words: "No AI Team found
    on Workstation" plus the probe's product sentence ("No answer from this
    address…", "This server doesn't run an AI team yet…", "…is starting…").
  - For this, `TeamDiscovery.miss` was added (leftover-units addition). It keeps
    the most telling non-found verdict. No raw host text is shown.

### team-plugin-sheet: removed (merged into team-home)

- `TeamPluginSheet` and its row sheet are deleted from `plugins_screen.dart`.
  - Plugins' **AI Team** row opens the team page (on or off).
  - On OpenCode inside the app, the second row is either "See the team's
    tasks", which opens the team page, or "A team on a computer", which opens
    the address form directly.
- Its jobs now live on the team page:
  - status: subtitle plus page state;
  - identity and access: Technical details;
  - host speed: the "Technical details" row;
  - Turn on: the off state;
  - Add manually / Change: Enter its address / Change address;
  - Refresh, Turn off, "On this phone": the menu.
- The old event-stream "seq" line and the engine's name ("AI Team · Gas City")
  are gone from the page. "Gas City" is only in Technical details.
- Shared write logic was pulled into `lib/ui/widgets/team_switch.dart`
  (`saveTeamHost`, `editTeamAddress`, `turnOffTeam`).
- The strings the sheet alone used were deleted from `app_en.arb` and
  `app_ar.arb`: 18 keys, including the `teamUiStatus*` and `teamUiEventStream*`
  sets.

### Routes kept (reachability)

These doors all call `openTeamPage` now:

- Settings › AI Team;
- the search entries `settings-ai-team` and `ai-team`;
- Plugins;
- New conversation's Team while the team is off (`workspace_screen.dart`).

`openTeamIntro` is gone.

The chat lane still pushes `TeamHomeScreen` directly from a task's conversation
(`team_conversation_view.dart`, not edited). That page resolves the app
connection and the heat guard from the provider scope, so it has the same menu
and heat behaviour.

### Ledger

- `docs/design/ui-ledger/parts` now match the code:
  - team-plugin-sheet is removed;
  - the three old team-home tab pages are merged into team-home, with their
    elements moved;
  - the new menu items and rows are added;
  - `team_page.dart` is listed as not a page;
  - `reachedFrom` entries are retargeted.
- `ledger.json` was rebuilt.
- `check_ui_ledger.py` still reports errors that were there before this slice
  (stale line numbers elsewhere). It reports none for team-home, team-intro,
  plugins or `team_page.dart`.

## Acceptance

| Acceptance | Where it is shown |
|---|---|
| team-intro and team-plugin-sheet routes point to team-home | `test/team_page_test.dart` "one page, on or off" (off → the same page becomes the team; Turn off → the same page is off); `test/team_plugins_screen_test.dart` (the Plugins row opens the page; Change address and Turn off come from its menu); `test/team_discover_test.dart` (Turn on turns the same page into the team; Settings › AI Team); `test/revamp/screen_library_3_test.dart` |
| heat pause is distinct from person pause (row 20) | `test/team_page_test.dart` group "a pause for heat is not the person's pause": the person's pause shows Paused and Resume; the heat guard's pause shows Cooling down, its line and no Resume; the heat guard's stop shows Stopped to cool down; another team's hold changes nothing |

## Tests

New:

- **`test/team_page_test.dart`**: 14 tests, all pass.
  - One page on and off, with the reason nothing was found.
  - Turn off and Change address from the menu.
  - The team inside the app: its stop runs first, and a failure keeps the team
    on and says so.
  - Opened on its own, the page goes back to the start after Turn off.
  - The pause for heat vs the person's pause.
  - Today's spend: shown, unpriced, and nothing reported.
  - The speed row opens Technical details.
  - The not-answering body per host.
- **`test/revamp/slice_p34_golden_test.dart`**: 8 goldens.

Changed:

- `team_discover_test`, `team_plugins_screen_test`, `team_plugins_layout_test`,
  `team_phone_onboarding_test` (Plugins → page → menu; the killed page says
  nothing contradictory at 9 s; its subtitle), `team_home_test`,
  `team_home_stable_layout_test`, `team_board_test`, `team_design_standard_test`,
  `revamp/screen_library_3_test`, plus their goldens.
- Goldens regenerated only where they failed because of this slice:
  - `test/goldens/team_home_loaded{,_phone}_{light,dark}`;
  - `test/revamp/goldens/team_home_{loaded,loaded_1280x800,search}_{light,dark}`;
  - `team_v2_intro_termux{,_1280x800}_light`;
  - `settings_team_plugin_sheet_off_*` became `settings_team_page_off_*`.

**Pre-existing failures.** I ran the same 40 files on the base commit in a
separate worktree (`git worktree`, not stash):

- **Base:** 132 tests fail across them. Among them: kit ratchet G17/G21,
  glossary G11/G28, ledger and search coverage, `team_controls`,
  `team_agent_screen`, the host-kind chips in `team_plugins_screen`, the
  320dp tips sheet, the `screen_team_1` and `team_discover` intro goldens, the
  gesture-audit evidence, and others.
- **After this slice:** `flutter test --no-pub -j 4` over those 40 files, with
  the pinned Flutter, gives 476 passed and 130 failed. Every one of the 130 is
  in the base list. One of them only has a new name: team_home_test's "host
  one phrase… behind Technical details". **This slice adds no failure.**
- **Ledger rebuild.** Rebuilding `ledger.json` also picked up page removals
  from earlier slices that had not rebuilt it (manage-project and others). So
  the gesture-audit row for the deleted `manage_project_screen.dart` was
  removed from `gesture-audit.json` and `.md`.
- `flutter analyze`: no issues.
- `dart format --language-version=3.10`: clean.

## What still needs a device

Proof on the emulator was not run in this slice. It needs these captures:

1. Team off (in-app and Termux): the same page turns into the team after
   Set up / Turn on.
2. Team on.
3. Paused by heat, using a real thermal hold (`adb shell cmd thermalservice
   override-status 3`). Check that the heat guard's line shows, with no Resume,
   and that it clears when the phone cools.
4. The Termux team stopped by Android: Start the team again from the page, with
   no "not answering" under it.
5. Turn off on the team inside the app: the supervisor is really stopped.
6. Today's spend on a host that reports `/usage` evidence.

## Images

| | Before | After |
|---|---|---|
| Team on (phone) | ![](before/team_home_light.png) | ![](after/team_home_light.png) |
| Team on (1280x800) | ![](before/team_home_1280x800_light.png) | ![](after/team_home_1280x800_light.png) |
| Team on this phone | ![](before/team_home_on_phone_light.png) | ![](after/team_page_on_phone_light.png) |
| Team on this phone (1280x800) | — | ![](after/team_page_on_phone_1280x800_light.png) |
| Paused by the heat guard (before: nothing said it) | ![](before/team_home_on_phone_light.png) | ![](after/team_page_heat_paused_light.png) |
| Paused by the heat guard (1280x800) | — | ![](after/team_page_heat_paused_1280x800_light.png) |
| Stopped by the heat guard | — | ![](after/team_page_heat_stopped_light.png) |
| The page's menu (the old sheet's switches) | ![](before/plugins_ai_team_sheet_off_light.png) | ![](after/team_page_menu_light.png) |
| Plugins › AI Team, team off | ![](before/plugins_ai_team_sheet_off_light.png) | ![](after/plugins_ai_team_row_opens_page_off_light.png) |
| Team off, a computer where nothing answered | ![](before/team_intro_computer_light.png) | ![](after/team_page_off_light.png) |
| Team off (1280x800) | ![](before/team_intro_computer_1280x800_light.png) | ![](after/team_page_off_1280x800_light.png) |
| Stopped by Android (before: "not answering" under the line) | ![](before/team_home_killed_light.png) | covered by `team_phone_onboarding_test` (no golden: it needs the Termux bridge) |

## Shipping states

- Implemented: yes.
- Committed: yes, locally on `revamp/slice-P3.4`.
- Verified: by the tests and goldens listed above, not on a device.
- Enabled, deployed, released: no.
