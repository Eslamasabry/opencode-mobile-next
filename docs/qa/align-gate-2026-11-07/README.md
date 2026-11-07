# Alignment gate: first full run (2026-11-07)

The alignment gate (rules: [native/docs/kit/ALIGN-RULES.md](../../../native/docs/kit/ALIGN-RULES.md)) measured every
FlutterGoldenGate shot that renders today, on branch `native/align-gate` at a61ddb747 (base `native/screens-merge`
5ff3ac113), on the build VPS: `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content` and the nine feature modules with gate
shots (agents, chat, chats, phone, phonesetup, servers, settings, setup, widget-panels). 416 shots were measured
(the phone 412 x 915 dp window, light); 106 of them are screens (`Frame.Screen`), which also get the page rules
(start edges, gaps). Both Gradle runs ended BUILD SUCCESSFUL, with the gate's own self-tests (`AlignRulesTest`,
`AlignReadTest`, `AlignSpecFileTest`) green.

Commands (VPS worktree `~/wt/align-gate`, goldens linked from the coordinator's `looks-gate`):

```
OC_ALIGN_BASELINE=update OC_LAYOUT_BASELINE=update ./gradlew --no-configuration-cache --continue \
  :ui:kit:desktopTest --tests '*GateTest' ...      # every class that calls FlutterGoldenGate.check
python3 -I native/tool/design/align_report.py --dir looks-out/align --top 30
```

(`OC_LAYOUT_BASELINE=update` only so that the layout guard's stale baselines on this base did not stop a test method
before its later shots; those layout baseline rewrites were not kept.)

Every finding below is now in `native/config/align/baseline/<module>.tsv` (118 lines, 11 modules): the gate fails
only on a NEW finding, and `layout_baseline.sh --kind align` (t1) keeps each file from growing. Nothing was fixed in
the screens (builders own them); the checker was tuned once for false positives: layers that scale a scene
(FlutterCapture), right-to-left words in the LTR audit, 1 dp live regions, and `clip` limited to words and icons.

## How to read it

- **Real, and the classes the sweeps found:** squeezed status icons (P-101, 8 shots), sheets' 6/18 dp gaps under
  the header, the search field's 26 dp gap, words at 32 dp in cards and overlays (P-007 class), 72 dp rows among
  64 dp rows, the composer field's 24 dp tall target.
- **Probably by design, needs a kit mark or a rule refinement (not fixed here):** words centred in their own box but
  start-aligned (setup's "#%" at 158 dp, a focused chip in the gallery at 175 dp), the feed timer
  (`kit-feed-timer` at x 0), and content inside a card or panel, where 32 dp = 16 dp page edge + 16 dp panel
  padding. If 32 is meant to be a column inside cards, the kit should mark the card (`kitList`-style) so the gate
  reads edges relative to it; until then they stay in the baseline.

## Results

133 findings (118 distinct baseline keys) in 72 of the 416 shots.

### Counts per rule

| Rule | Findings | Shots |
|---|---:|---:|
| `start-edge` | 53 | 25 |
| `gap-step` | 57 | 38 |
| `icon-label` | 6 | 6 |
| `end-edge` | 0 | 0 |
| `target` | 5 | 5 |
| `ellipsis-frame` | 0 | 0 |
| `clip` | 0 | 0 |
| `icon-size` | 0 | 0 |
| `icon-squeezed` | 8 | 8 |
| `row-height` | 4 | 4 |

### Per module

| Module | Findings | `start-edge` | `gap-step` | `icon-label` | `end-edge` | `target` | `ellipsis-frame` | `clip` | `icon-size` | `icon-squeezed` | `row-height` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| ui-kit | 55 | 13 | 40 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 |
| feature-widget-panels | 31 | 21 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| feature-agents | 15 | 13 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| ui-kit-chat | 12 | 1 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 8 | 1 |
| feature-chat | 6 | 1 | 1 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 |
| ui-kit-content | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| feature-phonesetup | 3 | 1 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| feature-chats | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| feature-settings | 2 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 1 |
| feature-setup | 2 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| feature-phone | 1 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

### Shots ranked by findings

| # | Module | Shot | Findings | Rules |
|---:|---|---|---:|---|
| 1 | feature-widget-panels | `widget_overlay_configure` | 12 | start-edge 7, gap-step 5 |
| 2 | feature-agents | `agent_account_signed_in` | 10 | start-edge 10 |
| 3 | feature-widget-panels | `widget_overlay_project` | 6 | start-edge 5, gap-step 1 |
| 4 | feature-agents | `agent_account_code` | 5 | start-edge 3, gap-step 2 |
| 5 | feature-widget-panels | `widget_overlay_failed` | 4 | start-edge 3, gap-step 1 |
| 6 | ui-kit | `kit_search_field_filtered` | 4 | start-edge 2, gap-step 2 |
| 7 | feature-widget-panels | `widget_overlay_after_reply` | 3 | start-edge 2, gap-step 1 |
| 8 | feature-widget-panels | `widget_overlay_steer` | 3 | start-edge 2, gap-step 1 |
| 9 | feature-widget-panels | `widget_overlay_voice` | 3 | start-edge 2, gap-step 1 |
| 10 | feature-chat | `chat_loading` | 2 | gap-step 1, target 1 |
| 11 | feature-chats | `chats_home_sheet` | 2 | gap-step 2 |
| 12 | feature-phonesetup | `setup_customize` | 2 | gap-step 2 |
| 13 | ui-kit-chat | `kit_work_line_default_text2` | 2 | start-edge 1, icon-squeezed 1 |
| 14 | ui-kit | `kit_dialog_alert` | 2 | start-edge 2 |
| 15 | ui-kit | `kit_search_field_disabled` | 2 | start-edge 1, gap-step 1 |
| 16 | ui-kit | `kit_search_field_empty` | 2 | start-edge 1, gap-step 1 |
| 17 | ui-kit | `kit_sheet_attention` | 2 | gap-step 2 |
| 18 | ui-kit | `kit_sheet_consequences` | 2 | gap-step 2 |
| 19 | ui-kit | `kit_sheet_default` | 2 | gap-step 2 |
| 20 | ui-kit | `kit_sheet_disabled` | 2 | gap-step 2 |
| 21 | ui-kit | `kit_sheet_full_1280x800` | 2 | gap-step 2 |
| 22 | ui-kit | `kit_sheet_half` | 2 | gap-step 2 |
| 23 | ui-kit | `kit_sheet_keyboard_open` | 2 | gap-step 2 |
| 24 | ui-kit | `kit_sheet_with_icon` | 2 | gap-step 2 |
| 25 | ui-kit | `kit_sheet_with_icon_1280x800` | 2 | gap-step 2 |
| 26 | ui-kit | `kit_sheet_with_icon_text2` | 2 | gap-step 2 |
| 27 | ui-kit | `kit_sheet_with_icon_text2_1280x800` | 2 | gap-step 2 |
| 28 | ui-kit | `kit_signal_home` | 2 | start-edge 1, gap-step 1 |
| 29 | ui-kit | `kit_signal_home_ar` | 2 | start-edge 1, gap-step 1 |
| 30 | feature-chat | `chat_find_open` | 1 | start-edge 1 |
| 31 | feature-chat | `chat_load_error` | 1 | target 1 |
| 32 | feature-chat | `chat_transcript_turn` | 1 | target 1 |
| 33 | feature-chat | `chat_transcript_turn_1280x800` | 1 | target 1 |
| 34 | feature-phone | `phone_this_phone_running` | 1 | icon-label 1 |
| 35 | feature-phonesetup | `setup_progress_running` | 1 | start-edge 1 |
| 36 | feature-settings | `settings_hub_loaded` | 1 | start-edge 1 |
| 37 | feature-settings | `settings_notifications_loaded` | 1 | row-height 1 |
| 38 | feature-setup | `setup_progress_failed` | 1 | start-edge 1 |
| 39 | feature-setup | `setup_progress_running` | 1 | start-edge 1 |
| 40 | ui-kit-chat | `kit_composer_idle_empty` | 1 | target 1 |
| 41 | ui-kit-chat | `kit_row_default` | 1 | row-height 1 |
| 42 | ui-kit-chat | `kit_step_glow_fail_flash` | 1 | icon-squeezed 1 |
| 43 | ui-kit-chat | `kit_step_glow_failed` | 1 | icon-squeezed 1 |
| 44 | ui-kit-chat | `kit_step_glow_queued` | 1 | icon-squeezed 1 |
| 45 | ui-kit-chat | `kit_step_glow_stop_fade` | 1 | icon-squeezed 1 |
| 46 | ui-kit-chat | `kit_step_glow_stopped` | 1 | icon-squeezed 1 |
| 47 | ui-kit-chat | `kit_step_glow_waiting` | 1 | icon-squeezed 1 |
| 48 | ui-kit-chat | `kit_step_glow_waiting_breathe` | 1 | icon-squeezed 1 |
| 49 | ui-kit-chat | `kit_step_timeline_collapsed` | 1 | icon-label 1 |
| 50 | ui-kit-content | `kit_qr_too_long` | 1 | icon-label 1 |
| 51 | ui-kit-content | `kit_qr_too_long_1280x800` | 1 | icon-label 1 |
| 52 | ui-kit-content | `kit_qr_too_long_text2` | 1 | icon-label 1 |
| 53 | ui-kit-content | `kit_qr_too_long_text2_1280x800` | 1 | icon-label 1 |
| 54 | ui-kit | `kit_chip_focused` | 1 | start-edge 1 |
| 55 | ui-kit | `kit_icon_button_default_text2` | 1 | start-edge 1 |
| 56 | ui-kit | `kit_icon_button_hover` | 1 | start-edge 1 |
| 57 | ui-kit | `kit_row_default` | 1 | row-height 1 |
| 58 | ui-kit | `kit_row_default_1280x800` | 1 | row-height 1 |
| 59 | ui-kit | `kit_search_field_nomatch` | 1 | gap-step 1 |
| 60 | ui-kit | `kit_search_field_partial` | 1 | gap-step 1 |
| 61 | ui-kit | `kit_search_field_results` | 1 | gap-step 1 |
| 62 | ui-kit | `kit_search_field_typing` | 1 | gap-step 1 |
| 63 | ui-kit | `kit_search_field_typing_1280x800` | 1 | gap-step 1 |
| 64 | ui-kit | `kit_search_field_typing_text2` | 1 | gap-step 1 |
| 65 | ui-kit | `kit_search_field_typing_text2_1280x800` | 1 | gap-step 1 |
| 66 | ui-kit | `kit_sheet_not_dismissible` | 1 | gap-step 1 |
| 67 | ui-kit | `kit_sheet_oneclose` | 1 | gap-step 1 |
| 68 | ui-kit | `kit_sheet_oneclose_1280x800` | 1 | gap-step 1 |
| 69 | ui-kit | `kit_sheet_text250` | 1 | gap-step 1 |
| 70 | ui-kit | `kit_sheet_text250_1280x800` | 1 | gap-step 1 |
| 71 | ui-kit | `kit_signal_chat` | 1 | start-edge 1 |
| 72 | ui-kit | `kit_signal_chat_folded` | 1 | start-edge 1 |

### Top 30 findings (furthest off first)

| # | Module | Shot | Rule | Subject | Measured |
|---:|---|---|---|---|---|
| 1 | ui-kit | `kit_chip_focused` | `start-edge` | "file.txt" [kit-chip-label] | words start at x 175.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 2 | feature-setup | `setup_progress_failed` | `start-edge` | "# of #" | words start at x 163.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 3 | feature-phonesetup | `setup_progress_running` | `start-edge` | "#%" | words start at x 158.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 4 | feature-setup | `setup_progress_running` | `start-edge` | "#%" | words start at x 158.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 5 | feature-widget-panels | `widget_overlay_configure` | `start-edge` | "Fix the sign-in page" | words start at x 144.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 6 | feature-widget-panels | `widget_overlay_configure` | `start-edge` | "Stop" | words start at x 136.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 7 | ui-kit | `kit_search_field_filtered` | `start-edge` | "# results" | words start at x 127.3 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 8 | feature-widget-panels | `widget_overlay_configure` | `start-edge` | "Editing files" | words start at x 124.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 9 | feature-chat | `chat_load_error` | `target` | "Ask OpenCode…" | 352.0 x 24.0 dp, needs 48.0 |
| 10 | feature-chat | `chat_loading` | `target` | "Ask OpenCode…" | 352.0 x 24.0 dp, needs 48.0 |
| 11 | feature-chat | `chat_transcript_turn` | `target` | "Ask OpenCode…" | 352.0 x 24.0 dp, needs 48.0 |
| 12 | feature-chat | `chat_transcript_turn_1280x800` | `target` | "Ask OpenCode…" | 352.0 x 24.0 dp, needs 48.0 |
| 13 | ui-kit | `kit_dialog_alert` | `start-edge` | "Could not add file" | words start at x 84.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 14 | ui-kit-chat | `kit_composer_idle_empty` | `target` | "Ask OpenCode…" | 352.0 x 24.0 dp, needs 48.0 |
| 15 | ui-kit | `kit_icon_button_hover` | `start-edge` | "Retry connection" | words start at x 83.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 16 | feature-widget-panels | `widget_overlay_project` | `start-edge` | "All projects" | words start at x 82.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 17 | feature-widget-panels | `widget_overlay_project` | `start-edge` | "IPTV_King" | words start at x 82.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 18 | feature-widget-panels | `widget_overlay_project` | `start-edge` | "settings-demo" | words start at x 82.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 19 | feature-widget-panels | `widget_overlay_project` | `start-edge` | "oc_app" | words start at x 82.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 20 | ui-kit | `kit_signal_chat` | `start-edge` | "# min # s" [kit-feed-timer] | words start at x 0.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 21 | ui-kit | `kit_signal_chat_folded` | `start-edge` | "# min # s" [kit-feed-timer] | words start at x 0.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 22 | ui-kit | `kit_signal_home` | `start-edge` | "# min" [kit-feed-timer] | words start at x 0.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 23 | ui-kit | `kit_signal_home_ar` | `start-edge` | "# min" [kit-feed-timer] | words start at x 0.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 24 | feature-agents | `agent_account_code` | `start-edge` | "Enter this one-time code on the official" | words start at x 32.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 25 | feature-agents | `agent_account_code` | `start-edge` | "TEST-#" | words start at x 32.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 26 | feature-agents | `agent_account_code` | `start-edge` | "⁦auth.openai.com⁩" | words start at x 32.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 27 | feature-agents | `agent_account_signed_in` | `start-edge` | "Signed in on the host" [kit-row-title] | words start at x 72.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 28 | feature-agents | `agent_account_signed_in` | `start-edge` | "⁨person@example.com⁩" [kit-row-supporting] | words start at x 72.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 29 | feature-agents | `agent_account_signed_in` | `start-edge` | "Signed in with" [kit-row-title] | words start at x 72.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |
| 30 | feature-agents | `agent_account_signed_in` | `start-edge` | "Plan" [kit-row-title] | words start at x 72.0 dp; the page's columns are 16.0, 20.0, 48.0, 52.0, 56.0, 60.0 |


## Start edges per screen (dp: count)

Each screen's distinct start edges of leading words and icons (the page's columns are 16, 20, 48, 52, 56, 60; icons may centre at 30 or 34).

| Module | Shot | Edges |
|---|---|---|
| feature-agents | `agent_account_code` | 16: 5, 20: 1, 32: 3, 60: 1 |
| feature-agents | `agent_account_signed_in` | 16: 6, 32: 6, 72: 4 |
| feature-agents | `agent_account_signed_out` | 16: 6, 20: 1 |
| feature-chat | `chat_find_open` | 32: 1 |
| feature-chats | `chats_home_empty` | 16: 3, 56: 6 |
| feature-chats | `chats_home_empty_1280x800` | 16: 3, 56: 6 |
| feature-chats | `chats_home_mixed` | 16: 15, 56: 3 |
| feature-chats | `chats_home_mixed_1280x800` | 16: 15, 56: 3 |
| feature-chats | `chats_home_sheet` | 16: 15, 20: 1, 56: 3, 60: 9 |
| feature-phone | `phone_this_phone_not_set_up` | 16: 1 |
| feature-phone | `phone_this_phone_running` | 16: 2, 20: 1, 56: 13 |
| feature-phonesetup | `setup_customize` | 16: 5, 20: 8 |
| feature-phonesetup | `setup_progress_running` | 16: 1, 158: 1 |
| feature-phonesetup | `setup_start` | 16: 5 |
| feature-servers | `servers_pairing_scanner_blocked` | 16: 3 |
| feature-servers | `servers_pairing_scanner_denied` | 16: 3 |
| feature-servers | `servers_pairing_scanner_denied_1280x800` | 16: 3 |
| feature-servers | `servers_pairing_scanner_no_camera` | 16: 3 |
| feature-servers | `servers_pairing_scanner_starting` | 16: 2 |
| feature-servers | `servers_servers_loaded` | 16: 1, 56: 6 |
| feature-servers | `servers_servers_loaded_1280x800` | 16: 1, 56: 6 |
| feature-settings | `settings_hub_loaded` | 16: 1, 56: 6, 64: 1 |
| feature-settings | `settings_notifications_loaded` | 16: 4, 56: 19 |
| feature-settings | `settings_privacy_loaded` | 16: 3, 56: 9 |
| feature-settings | `settings_privacy_loaded_1280x800` | 16: 3, 56: 9 |
| feature-setup | `setup_progress_failed` | 16: 1, 163: 1 |
| feature-setup | `setup_progress_running` | 16: 1, 158: 1 |
| feature-widget-panels | `widget_overlay_after_reply` | 32: 2 |
| feature-widget-panels | `widget_overlay_configure` | 32: 4, 124: 1, 136: 1, 144: 1 |
| feature-widget-panels | `widget_overlay_failed` | 32: 2, 72: 1 |
| feature-widget-panels | `widget_overlay_project` | 32: 1, 82: 4 |
| feature-widget-panels | `widget_overlay_steer` | 32: 2 |
| feature-widget-panels | `widget_overlay_voice` | 32: 2 |
| ui-kit | `kit_chip_focused` | 175: 1 |
| ui-kit | `kit_dialog_alert` | 44: 1, 84: 1 |
| ui-kit | `kit_icon_button_default_ar_1280x800` | 56: 1 |
| ui-kit | `kit_icon_button_default_text2` | 32: 1 |
| ui-kit | `kit_icon_button_default_text2_1280x800` | 56: 1 |
| ui-kit | `kit_icon_button_hover` | 83: 1 |
| ui-kit | `kit_nav_phone_no_bar` | 16: 12 |
| ui-kit | `kit_search_field_disabled` | 16: 6, 64: 1 |
| ui-kit | `kit_search_field_empty` | 16: 5, 64: 1 |
| ui-kit | `kit_search_field_filtered` | 16: 5, 28: 1, 127.5: 1 |
| ui-kit | `kit_search_field_nomatch` | 16: 2 |
| ui-kit | `kit_search_field_partial` | 16: 6 |
| ui-kit | `kit_search_field_results` | 16: 6 |
| ui-kit | `kit_search_field_typing` | 16: 5 |
| ui-kit | `kit_search_field_typing_1280x800` | 16: 5 |
| ui-kit | `kit_search_field_typing_text2` | 16: 5 |
| ui-kit | `kit_search_field_typing_text2_1280x800` | 16: 5 |
| ui-kit | `kit_sheet_attention` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_consequences` | 20: 5, 48: 3 |
| ui-kit | `kit_sheet_default` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_disabled` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_discard` | 20: 2 |
| ui-kit | `kit_sheet_full_1280x800` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_half` | 20: 2, 60: 3 |
| ui-kit | `kit_sheet_keyboard_open` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_loading` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_not_dismissible` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_oneclose` | 20: 2, 60: 4 |
| ui-kit | `kit_sheet_oneclose_1280x800` | 20: 2, 60: 4 |
| ui-kit | `kit_sheet_text250` | 20: 2, 60: 4 |
| ui-kit | `kit_sheet_text250_1280x800` | 20: 2, 60: 4 |
| ui-kit | `kit_sheet_with_icon` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_with_icon_1280x800` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_with_icon_text2` | 20: 2, 60: 5 |
| ui-kit | `kit_sheet_with_icon_text2_1280x800` | 20: 2, 60: 5 |
| ui-kit | `kit_signal_chat` | 0: 1, 16: 5 |
| ui-kit | `kit_signal_chat_folded` | 0: 1, 16: 5 |
| ui-kit | `kit_signal_home` | 0: 1, 16: 13 |
| ui-kit | `kit_signal_home_ar` | 0: 1, 16: 10 |
| ui-kit-chat | `kit_composer_idle_empty` | 16: 3 |
| ui-kit-chat | `kit_step_glow_background_t40` | 60: 4 |
| ui-kit-chat | `kit_step_glow_done` | 60: 4 |
| ui-kit-chat | `kit_step_glow_fail_flash` | 60: 4 |
| ui-kit-chat | `kit_step_glow_failed` | 60: 4 |
| ui-kit-chat | `kit_step_glow_finish_spring` | 60: 4 |
| ui-kit-chat | `kit_step_glow_finish_tick` | 60: 4 |
| ui-kit-chat | `kit_step_glow_first_t40` | 60: 4 |
| ui-kit-chat | `kit_step_glow_last_t40` | 60: 4 |
| ui-kit-chat | `kit_step_glow_queued` | 60: 4 |
| ui-kit-chat | `kit_step_glow_reduced` | 60: 4 |
| ui-kit-chat | `kit_step_glow_running_t10` | 60: 4 |
| ui-kit-chat | `kit_step_glow_running_t40` | 60: 4 |
| ui-kit-chat | `kit_step_glow_running_t40_ar_text2` | 60: 4 |
| ui-kit-chat | `kit_step_glow_running_t55` | 60: 4 |
| ui-kit-chat | `kit_step_glow_running_t80` | 60: 4 |
| ui-kit-chat | `kit_step_glow_stalled` | 60: 4 |
| ui-kit-chat | `kit_step_glow_stop_fade` | 60: 4 |
| ui-kit-chat | `kit_step_glow_stopped` | 60: 4 |
| ui-kit-chat | `kit_step_glow_waiting` | 60: 4 |
| ui-kit-chat | `kit_step_glow_waiting_breathe` | 60: 4 |
| ui-kit-chat | `kit_step_timeline_collapsed` | 16: 2 |
| ui-kit-chat | `kit_work_line_default_text2` | 16: 2, 32: 1, 56: 1 |
