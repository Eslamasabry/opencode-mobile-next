# slice-goldens-app — reviewed golden refresh (2026-09-28)

Branch `revamp/slice-goldens-app`, from `feat/phone-setup-v2` at 138f8216.
Scope: `test/goldens/*_golden_test.dart` for chat states and form sheet, folder
browser, phone server screens, phone setup, servers motion, settings, the AI
Team files, theme gallery, This phone remove tools, Work parts and Work tab,
plus `test/golden_harness_test.dart`.

## Result

- First run: 136 passed, **177 golden failures** (97 goldens × dark/light), no other failures.
- **Regressions: 6 goldens.** 2 product fixes, 4 harness fixes where the image was wrong because the test no longer matched the app. 1 kit regression handed off.
- **Intended: 91 goldens** (171 PNGs), refreshed with `--update-goldens`.
- After the refresh the listed files pass: `+313, All tests passed` (serial slot through `tool/qa/machine_lock.sh`).
- `flutter analyze`: clean. `kit_ratchet_test`, `golden_harness_test` and `design_standard_test` pass. `design_standard_test` needed its golden name moved to `team_agent_top` after the G23 rename.

## Regressions found and fixed

| # | Where | What was wrong | Fix |
|---|---|---|---|
| R1 | Work › no project yet (`work_chooser`) | The body named the server by its saved name ("This device (Termux)"), but the pill, Servers and the switcher say "This phone · Termux". The Work context sheet's "On …" line had the same problem. | `lib/ui/screens/workspace_screen.dart` uses `serverDisplayName`. The new test in `test/revamp/screen_work_1_test.dart` fails without the fix. |
| R2 | Settings › This server row (`team_discover_settings`) | The failed health check's plain-words reason was cut off at "Try again, or report t…". | `lib/ui/screens/settings_screen.dart`: the supporting line has 3 lines when the check failed. |
| R3 | `shell_reconnecting`, `work_not_answering` | The connection line was missing. Since 3d64653c it lives in the app status slot above the navigator, and these harnesses did not host that slot. | `work_parts`/`work_tab` harnesses wrap `AppConnectionStatusScope`, the same way main.dart does. |
| R4 | `work_other_servers` | The Inbox badge was gone. The P4.2b attention feed counts only requests from a check with a time, and the fake monitor snapshot had none. | The fixture snapshot sets `checkedAt` and `attentionComplete`. |
| R5 | `team_intro_phone` | The page showed an empty group card and no action. The phone pre-flight never answered in the test, so the page waited on a skeleton. | The golden passes a capable `deviceProbe`. The page now shows "Set up AI Team on this phone". |

Handed off (lane-notes): **KitSkeletonRows is invisible on a light grouped card.** The bars use `surfaceContainer`/`surfaceContainerHigh`, which match the card fill in light. It is seen on the AI Team intro while the pre-flight runs (lib/ui/kit, kit owner).

## For the owner to eyeball

- `chat_permission_sheet`: the command in the request sheet stays on one line and scrolls sideways (KitRequestSheet/KitCodeBlock design, "never truncated"). The end of a long command is behind a fade before you approve. Is that the intent, or should it wrap?
- `team_agent_output`: the watching composer lost its "Goes to fox · Worker through the AI Team" note. The hint "Message fox…" still names who gets the message.
- `shell_shortcuts_help_open`: the grouped rows' trailing keys sit flush on the card's right edge. This was already the case before (not new) and belongs to the kit sheet/row group.
- `team_start_run`: supervision now starts at High, taken from the server's automation policy (P6.1), where it used to be Balanced.

## Contact sheets (before | after)

1. `01-regression-fixes.png`: R1–R5.
2. `02-connection-status.png`: P4.4 one status, Switch server, stopped = Start only.
3. `03-work-ambient.png`: ambient fields and crisp glass, P4.5, P5.5.
4. `04-chat.png`: chat states and form sheet.
5. `05-team-home-agents.png`: AI Team home, agents, intro.
6. `06-team-run-board.png`: start sheet, task conversation, board, scenes.
7. `07-phone-servers-setup.png`: This phone, Servers, Add server, setup progress.
8. `08-setup-settings.png`: setup start pages, Settings, Plugins, gallery.

A dark-only failure means the light PNG had already been refreshed and reviewed in 6ae40ca8 or an earlier slice, and dark lagged behind. Those are marked as such.

## Every refreshed golden

| Golden | dark % | light % | Verdict | Reason / commit |
|---|---|---|---|---|
| shell_reconnecting | 96.95 | 97.51 | REGRESSION fixed (harness) | Connection line vanished: since 3d64653c it lives in the app status slot above the navigator, which the harness did not host; harness now wraps AppConnectionStatusScope like main.dart. Now says "OpenCode on this phone isn't answering · Restart" (unified status for a phone server) |
| work_team | 91.50 | 92.03 | INTENDED | Solo/Team switch gone (P4.5 one New conversation, a2605a09); row says "Stalled" (P5.5, 2c1c783f); ambient |
| team_discover_work | 91.18 | 91.72 | INTENDED | Solo/Team switch gone (P4.5); ambient |
| work_chooser | 91.12 | 91.68 | REGRESSION fixed | Chooser named the server by its saved name ("This device (Termux)") while the pill says "This phone · Termux"; now serverDisplayName (workspace_screen.dart). Rest: ambient 6ae40ca8, copy P3.11a |
| work_empty | 91.07 | — | INTENDED | dark lagged the reviewed light refresh of 6ae40ca8: ambient field |
| work_not_answering | 89.83 | 90.17 | REGRESSION fixed (harness) | Same status-slot hosting as shell_reconnecting; line now in the slot above the title (P4.4), pill "Offline" after the 8 s deadline (one controller-owned deadline) |
| work_loaded | 79.29 | — | INTENDED | dark: ambient field (6ae40ca8) |
| work_other_servers | 78.19 | 78.64 | REGRESSION fixed (harness) | Inbox badge vanished: the fake monitor snapshot had no checkedAt, which the P4.2b attention feed requires; fixture now sets it. Rest: ambient |
| work_restoring | 76.90 | — | INTENDED | dark: ambient field (6ae40ca8) |
| work_loading | 76.71 | — | INTENDED | dark: ambient field (6ae40ca8) |
| settings_appearance | 75.50 | 75.12 | INTENDED | grouped cards (R4), inline segmented (KIT-24), glass preview |
| team_intro_phone | 55.38 | 55.37 | REGRESSION fixed (harness) | Harness never answered the phone pre-flight, so the page stuck on an empty skeleton card with no action; test now passes a deviceProbe. New: "Set up AI Team on this phone", grouped rows (R4), top bar subtitle |
| team_start_run | 53.27 | 19.70 | INTENDED | Start sheet rebuilt (a7bda280): radios for project, supervision preset from the server policy (P6.1), "Send to the Mayor" |
| team_intro_computer | 49.49 | 49.49 | INTENDED | "Set up AI Team on dev-pc" (actions name their target), grouped rows, top bar subtitle |
| servers_add | 49.00 | 49.00 | INTENDED | Add server as a stepped flow (close-servers) |
| shell_command_palette_open | 42.47 | — | INTENDED | dark lagged reviewed light (6ae40ca8): ambient + sheet header spacing |
| setup_customize | 41.21 | 17.54 | INTENDED | required parts locked, Python the only choice; sheet header |
| chat_find_open | 38.84 | 33.80 | INTENDED | Find excerpt shown only when the match is out of view (23f2d0ce: match within reach is highlighted in place), so no excerpt card and no Jump to latest; ⋯ menu; composer glass |
| settings_privacy | 34.77 | 34.78 | INTENDED | grouped card, Privacy policy row, title |
| setup_progress_log | 33.29 | 12.54 | INTENDED | "Stop setup" names its act; log panel (R3) |
| servers_add_failed | 32.56 | 32.56 | INTENDED | failure inline under the address (close-servers) |
| team_board_move_sheet | 28.52 | 7.52 | INTENDED | Sheet title/close + grouped actions (kit sheet) |
| team_home_loaded_phone | 28.45 | 28.45 | INTENDED | as team_home_loaded |
| team_home_loaded | 28.30 | 28.30 | INTENDED | Gate as the one request card with "Needs your decision" (amber = needs you), Details/Open conversation (P3.4, close-team) |
| team_discover_settings | 25.18 | 24.87 | REGRESSION fixed | "This server" health line cut off at "report t…"; three lines when the check failed (settings_screen.dart). Rest: Settings IA P3.10 groups |
| team_agent_details | 25.16 | 19.54 | INTENDED | P3.6 actions; Session id row |
| shell_shortcuts_help_open | 24.65 | — | INTENDED | dark lagged reviewed light (6ae40ca8); sheet header spacing |
| team_agent_top | 23.82 | 23.82 | INTENDED | P3.6: one action "Open conversation"; last step in plain words |
| team_agent_output | 21.08 | 21.08 | INTENDED | P3.6 live output is the chat watching mode: fold "Ran 5 commands", composer hint "Message fox…" names the recipient (the old "Goes to …" note is gone) — owner may want to eyeball |
| team_agents | 20.89 | 20.89 | INTENDED | Rows named "wolf · Worker" in one group (R4, P3.6), checked-at line |
| phone_running | 20.43 | 20.34 | INTENDED | 39a84e4d rows stop repeating "on this phone"; Move from Termux (a5cc3e80); restart follows the automation policy (6ed0ec26) with its attempts line |
| phone_stopped | 20.42 | 20.34 | INTENDED | as phone_running |
| plugins_server | 20.03 | 20.03 | INTENDED | grouped rows, refresh in the top bar |
| chat_permission_sheet | 20.02 | 20.03 | INTENDED | R3 KitCodeBlock without the empty header band (copy trails the line; the command scrolls sideways); "Always allow these requests" (af07cc7a) |
| team_home_empty | 20.00 | 20.00 | INTENDED | P3.4 one page: grouped rows, Technical details row, ⋯ menu |
| team_run_overview | 17.18 | 17.16 | INTENDED | Now line "Waiting for your answer" in the status slot (P5.1/P4.4) |
| setup_start_termux | 16.20 | 16.20 | INTENDED | Other ways disclosure (kit) |
| chat_disconnected | 12.90 | 15.68 | INTENDED | P4.4 status line in the slot: "Reconnect to Laptop" on its own line; ⋯ menu (P10.1-2); crisp composer glass |
| connection_stopped | 15.67 | 15.67 | INTENDED | Stopped phone server: Start is the one action, no Try again; "Switch server" (74412bb5 P4.4) |
| team_run_work | 15.55 | 15.50 | INTENDED | Outcome stages Planned/Working/In review/Merged and usage words (close-team 47d9d881) |
| team_discover_plugins_phone | 15.32 | 15.32 | INTENDED | Turn on as the primary action; computer route as its own row (team-g17) |
| servers_list | 14.92 | 14.92 | INTENDED | addresses off the rows; Background checks row removed (d0047ca3); "Can't read the saved password" (76c934c6) |
| settings_this_server | 14.37 | 14.36 | INTENDED | AI setup row; update row names its target |
| chat_model_error | 11.88 | 14.26 | INTENDED | "Not answered" mark (23f2d0ce); plain "The server doesn't have this model." (no raw errors) |
| setup_progress_failed | 13.72 | 13.48 | INTENDED | Report this failure |
| phone_card_running | 12.66 | 12.66 | INTENDED | R15 phone server as a row of the one list (5b4d64ef) |
| phone_card_setting_up | 12.39 | 12.39 | INTENDED | R15 |
| phone_card_stopped | 12.14 | 12.14 | INTENDED | R15 |
| chat_empty | 10.31 | 12.08 | INTENDED | ⋯ menu, title on the gutter, crisp composer glass |
| chat_transcript_turn | 9.96 | 12.01 | INTENDED | ⋯ menu, crisp composer glass |
| connection_failed | 11.41 | 11.41 | INTENDED | Unplugged scene (b4c43f93), address kept out of the copy (c274356e), "Switch server" |
| team_board_team_moves | 9.53 | 4.05 | INTENDED | Sheet title/close; duplicate error glyph dropped from the card line |
| setup_start_ready | 9.06 | 9.06 | INTENDED | Other ways disclosure |
| servers_phone | 8.51 | 8.51 | INTENDED | Move your Termux projects offer (a5cc3e80); address off the row |
| setup_start_progress | 6.75 | 6.77 | INTENDED | Other ways disclosure |
| team_home_not_answering | 6.52 | 6.51 | INTENDED | G17: failure icon neutral, not amber; plain copy naming the host |
| chat_form_sheet_1280x800 | 5.72 | — | INTENDED | dark lagged reviewed light: dialog header spacing |
| setup_start_stopped | 5.11 | 5.11 | INTENDED | Other ways disclosure |
| team_home_error | 4.11 | 4.11 | INTENDED | G17 neutral icon; Details disclosure |
| chat_form_sheet | 3.55 | — | INTENDED | dark lagged reviewed light: sheet header spacing |
| chat_form_sendfailed | 3.55 | — | INTENDED | dark lagged light: sheet header spacing |
| team_home_starting | 3.17 | 3.17 | INTENDED | Details disclosure; text |
| setup_welcome_entry | 2.84 | 2.84 | INTENDED | G17 neutral icon, heading size |
| connection_connecting | 2.31 | 2.31 | INTENDED | Details disclosure look (kit) |
| team_scene_waking | 1.41 | — | INTENDED | dark R9 text3 ground line |
| team_scene_rest | 1.30 | — | INTENDED | dark R9 text3 |
| team_scene_board | 1.25 | — | INTENDED | dark R9 text3 |
| team_gate_choice_1280x800 | 0.90 | 0.90 | INTENDED | "Open conversation" (P3.6) |
| team_agent_top_1280x800 | 0.90 | 0.90 | INTENDED | P3.6 |
| team_board_empty | 0.70 | 0.62 | INTENDED | "Give the team a task" (close-team) |
| settings_hub | 0.65 | 0.65 | INTENDED | Model row "Server default" supporting line (P6.6a) |
| team_board_working | 0.60 | 0.60 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_read_only | 0.60 | 0.60 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| connection_not_answering | 0.60 | 0.60 | INTENDED | "Switch server" wording |
| team_discover_scene_relay | 0.44 | — | INTENDED | dark R9 text3 |
| chat_loading | 0.44 | 0.44 | INTENDED | title/menu glyph |
| team_board_done | 0.43 | 0.43 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_cancel_confirm | 0.32 | 0.24 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_run_merged | 0.30 | 0.30 | INTENDED | card edge |
| team_board_backlog | 0.29 | 0.25 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_moving | 0.28 | 0.25 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_gate_run_failed | 0.27 | 0.27 | INTENDED | "Watch the agent" (P3.6) |
| team_board_refused | 0.27 | 0.25 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_ready | 0.27 | 0.25 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_review | 0.24 | 0.25 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| setup_progress_running | 0.25 | 0.17 | INTENDED | "Stop setup" |
| connection_starting | 0.25 | 0.25 | INTENDED | "Switch server" wording |
| gallery_solarized | 0.18 | 0.16 | INTENDED | R9 text3 |
| team_board_error | 0.17 | 0.17 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| gallery_gruvbox | 0.17 | 0.17 | INTENDED | R9 text3 |
| gallery_catppuccin | 0.17 | 0.17 | INTENDED | R9 text3 |
| chat_form_full | 0.17 | — | INTENDED | dark: R9 text3 |
| gallery_opencode | 0.16 | — | INTENDED | dark R9 text3 |
| team_board_working_1280x800 | 0.13 | 0.13 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| team_board_loading | 0.12 | 0.13 | INTENDED | sub-1 % text/tone drift: top bar title, R9 dark text3, duplicate status glyph dropped |
| chat_form_dismiss | 0.06 | — | INTENDED | dark: sheet handle tone |
| chat_form_full_1280x800 | 0.04 | — | INTENDED | dark: R9 text3 |
