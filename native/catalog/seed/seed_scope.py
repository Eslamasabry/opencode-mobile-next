"""Seed native/catalog/scope.jsonl (plan 15 Appendix B, plan 02 §3.3 and §3.6).

Usage (from the repo root):
    python3 -I native/catalog/seed/seed_scope.py native/catalog/behaviors.jsonl          # bucket counts (plan 15 §14.1)
    python3 -I native/catalog/seed/seed_scope.py native/catalog/behaviors.jsonl --write  # rewrites native/catalog/scope.jsonl
    python3 -I native/catalog/seed/seed_scope.py native/catalog/behaviors.jsonl --lock   # appends new rows to behaviors.lock

Three layers, applied in order; the last one that speaks wins:
  1. RULES: plan 15 Appendix B verbatim. A first-pass *slice* from the first Flutter source file name.
  2. MODULE_RULES: the proving Gradle module from the source stem (first match), then the row text, then the area.
  3. overrides.jsonl: the hand decisions from reading every row's text (02 §3.3 rule 2). Each override that changes
     scope or adds an amendment carries `decision` and `reason`; ScopeFileTest checks the result.

The script is the reproducible seed only. scope.jsonl is the reviewed truth; later edits go to overrides.jsonl and the
file is regenerated, so the reasoning stays next to the row id.
"""
import collections
import json
import os
import re
import sys

# ---- 1. plan 15 Appendix B, verbatim -------------------------------------------------------------------------------
RULES = [
 (r'factsbook|(^|/)team_|_team(_|$)|aiteam|gascity|od_team|phone_project_engine|phone_engine_|builtin_team|termux_aiteam|phone_team|slice_close_team|slice_team|shared_team|role_cycle|builtin_phone_engine|kit_work_graph', 'AFTER'),
 (r'external_agent|acp_client|session_address|browser_claude_launch', 'DROP'),
 (r'kit_live_background|kit_scenes|kit_states_scenes', 'AFTER'),
 (r'termux_migration', 'S5'),
 (r'termux|setup_termux', 'DROP'),
 (r'desktop_packaging|desktop_window_state|desktop_release_check', 'DESKTOP'),
 (r'ios_remote|ios_runner', 'IOS'),
 (r'release_script|release_metadata|release_blockers|shipped_download|context:day-1-update', 'S6'),
 (r'architecture_boundaries|platform_capabilities|repository_hygiene|vps_build_apk|desktop_platform_gating|kit_map_gate|ui_ledger_coverage', 'S0'),
 (r'kit/kit_(code_block)', 'S3'),
 (r'kit/kit_(diff_view|log_panel|viewer|terminal|chart|qr|level_meter|context_ring|markdown_image)', 'S5'),
 (r'kit/kit_(style|fine_tune|swatch|signal)|kit/style/|appearance_|app_theme|theme_packs', 'S4'),
 (r'kit/kit_(markdown|message|work_line|step_light|step_timeline|ask_line|frost|since|draft_manifest)', 'S2'),
 (r'kit/kit_capability_explainer', 'S3'),
 (r'kit/|kit_motion_app|text_scale|text_size|theme_roles|app_iconography|android_focus|ui_glossary', 'S1'),
 (r'audit2_.*deletion|profile_deletion|default_notice_deletion|byo_host/profile_signer_deletion', 'S5'),
 (r'redaction|credential_ingress|external_link|server_trust|private_network_cleartext|tailscale_profile|api2_transport|api2_sse|api2_dialect|api2_events|api2_gateway_events|api2_gateway_mappers|api2_client|api2_health|server_probe|connection_failure|^network|app_preferences_store|app_locale|locale_format|l10n|native_locale|app_diagnostics|native_crash|crash_diagnostics_startup|profile_store|profile_secure_storage|oc1_profile_upgrade|retired_prefs|product_error_text|audit2_product_error|no_raw_error|recent_errors_plain|connection_sse_wake|reconnect_hardening|connection_interaction|connection_location_restore|in_flow_consent|read_state_consent|mobile_download_consent|interaction_defaults|settings_scope_domain|perf_transport_decode|api2_stable_flags|v2_feature_gating|api2_global_pagination|api2_models|connection_v2_requests|product_repository_server|server_data_widening|redact|no_red_scan', 'S1'),
 (r'codex_|gen_ui|mcp_|connector_search|sv3_|agent_card_view|agent_handoff|paseo_agent_handoff|continue_with|review_handoff|session_handoff|agent_tool_adapter|agent_certification|vision_chat|paseo_genui', 'S5'),
 (r'voice_|natural_voice|read_aloud|setup_voice_component', 'S5'),
 (r'git/|git_link_repair|worktree_inspection|isolated_task|environment_projects|workspace_paths|project_hub|project_dev_server|detected_dev_servers|dev_preview|development_service|listening_ports|linux_manager|linux_reset|local_terminal(?!_signin)|terminal_|xterm_sgr|common_file_preview|static_svg|local_pdf|reader_preferences|wifi_file_share|shared_project_roots|shared_storage_path|byo_host|manage_space|usage_|provider_|quota_|computer_usage|in_app_quota|plugin_|web_search|library_integrations|integration_auth|host_agent_providers|session_import|opencode_history_copy|failed_job_report|shell_output|managed_shell|api2_mcp_scope|api2_provider_workflows|api2_terminal_auth|review_followups|run_result|search_go_to|builtin_folders|builtin_project|phone_storage_folders|paseo_files|paseo_usage|paseo_host_update|paseo_provider|paseo_background_tasks|paseo_archive|demo_isolation|product_repository_projects|product_repository_mcp|tools_screen|connect_method_order|model_library|free_model|chat_free_model|coverage/files_actions|revamp/slice_aisetup|setup_planner|session_skill|markdown_path_link|builtin_performance', 'S5'),
 (r'background_(live|pause|coding_alert|action|signin|shell_result)|notification_|attention|profile_monitor|elsewhere|conversations_elsewhere|widget_|launch_surfaces|launch_session|pinned_session|scheduled_prompt|while_away|reply_watch|first_reply_notify|live_update_chip|settings_|teaching|nudge|gesture_audit|crash_report|crash_diagnostics|report_problem|problem_report|coverage/diag|coverage/settings|about_|session_note|session_export|session_context|context_usage|staged_revert|undo_from_here|message_delete|api2_revert|session_pins|profile_sign_in_reset|in_app_auto_update|update_channel|device_diagnostics|exit_list_focus|chat_background_result|sign_in_foreground_causes|revamp/shared_settings|revamp/slice_p6_6a|android_group_names_native|server_capabilities_screen|search_index|phrase_chips_settings|session_link_native_contract|launch_shortcut_native|share_intent_native', 'S4'),
 (r'paseo_|phone_agent|phone_agents|local_agent|agent_|claude_install|claude_phone_auth|builtin_|phone_server|phone_host_runtime|phone_process|phone_processes|phone_storage|this_phone|setup_|managed_runtime|managed_move|installer_admission|thermal_guard|app_exit|lifecycle_report|leftover_process|running_on_phone|running_agents_strip|opencode_native_install|host_script|phone_setup|phone_own_server_icon|draft_attachments|session_draft|prompt_shelf|saved_prompts|stash_|prompt_photos|phrase_|chat_attachment_reader|office_text|delimited_text|form_request|api2_integration_form|session_auto_approval|automation_policy|automatic_activity|permission_migration|repeated_permission|model_|session_model_scope|prompt_file_mention|capability_flows|background_work|background_tasks|mobile_task_view|pairing_scanner|coverage/phone_agent|revamp/shared_phone|revamp/queued_prompt|queued_prompt|local_terminal_signin|forget_in_app_server|phone_agent_context|workspace|active_context|forget', 'S3'),
 (r'transcript|v2_transcript_rows|turn_stall|chat_|merged_chat_feed|session_inventory|session_tail_cache|session_title|session_selection_sync|session_read_state|return_brief|home_navigation|no_bottom_nav|first_run|offline_queue|command_receipt|pending_command_journal|session_creation_receipts|message_page_transport|api2_phone_chat_dispatch|api2_interaction_gateway|api2_permission_dispatch|api2_session_permission|permission_|tool_card|tool_state|tool_label_surfaces|live_status|perf_|pairing_payload|share_intent|launch_shortcut|session_link|servers_scenes|slice_close_servers|server_profile_reentry|revamp/screen_shell|app_lifecycle|connection_|ui_|product_repository|exit|recent|stuck', 'S2'),
]


def stem_of(source):
    return re.sub(r':\d+$', '', source).replace('test/', '').replace('_test.dart', '')


def slice_for(row):
    if row['area'] == 'MIGR':
        return 'S6'
    stem = stem_of(row['sources'][0])
    for rx, s in RULES:
        if re.search(rx, stem):
            return s
    return 'MANUAL'
# ---- end of Appendix B ----------------------------------------------------------------------------------------------

BUCKET = {  # Appendix B bucket -> (scope, slice, decision, reason) before the per-row read
    'S0': ('switch', 0), 'S1': ('switch', 1), 'S2': ('switch', 2), 'S3': ('switch', 3), 'S4': ('switch', 4),
    'S5': ('switch', 5), 'S6': ('switch', 6),
    'AFTER': ('after-switch', None), 'DROP': ('dropped', None), 'DESKTOP': ('desktop-phase', None),
    'IOS': ('ios-phase', None), 'MANUAL': ('switch', None),
}

# ---- 2. proving module ---------------------------------------------------------------------------------------------
# (stem regex, module). First match wins. Module names follow plan 03 and the plan 15 unit write sets.
MODULE_RULES = [
    # S0 gates and build
    (r'architecture_boundaries|repository_hygiene|kit_map_gate|ui_ledger_coverage|vps_build_apk|no_red_scan|analyzer|file_size_ratchet|final_pass_lock|design_standard', ':quality'),
    (r'platform_capabilities|desktop_platform_gating', ':core:domain'),
    (r'desktop_|ios_', ':desktopApp'),
    (r'release_|shipped_download', ':androidApp'),
    # migration (day-1 update and legacy formats)
    (r'context:day-1-update|oc1_profile_upgrade|retired_prefs|saved_prompts_migration|permission_migration', ':android:migration'),
    (r'termux_migration', ':android:termux'),
    # kit
    (r'kit/kit_(code_block|log_panel|viewer|terminal|chart|qr|level_meter|markdown_image|diff_view)', ':ui:kit-content'),
    (r'kit/kit_(markdown|message|work_line|step_light|step_timeline|ask_line|frost|since|draft_manifest|context_ring)', ':ui:kit-chat'),
    (r'kit/|kit_motion_app|text_scale|text_size|theme_roles|app_iconography|android_focus|ui_glossary|appearance_|app_theme|theme_packs', ':ui:kit'),
    (r'l10n|app_locale|locale_format|native_locale', ':ui:words'),
    # security and net
    (r'redaction|credential_ingress|external_link|server_trust|private_network_cleartext|tailscale_profile|redact', ':core:security'),
    (r'^network|perf_transport_decode', ':core:net'),
    # protocols
    (r'codex_|server_codex', ':protocol:codex'),
    (r'paseo_(gateway|hello|chat_feed_source|tool_names|tool_output|background_decode|payload_use|images|correlated|compaction|midturn|uncertain|idle|stale|title|first_message|model_catalog|native_permissions|native_questions)', ':protocol:paseo'),
    (r'api2_(mcp_scope|provider_workflows|terminal_auth)|web_search', ':protocol:oc2'),
    (r'api2_(interaction_gateway|permission_dispatch|phone_chat_dispatch|session_permission)', ':engine:delivery'),
    (r'api2_revert', ':engine:project'),
    (r'api2_integration_form', ':engine:requests'),
    (r'api2_|v2_feature_gating|server_data_widening|product_repository_server|connection_v2_requests|message_page_transport|message_delete_transport', ':protocol:oc2'),
    # connection
    (r'server_probe|connection_|reconnect_hardening|app_lifecycle|product_error_text|audit2_product_error|no_raw_error|recent_errors_plain|server_profile_reentry', ':engine:connection'),
    # data, settings, profiles, diagnostics
    (r'app_preferences_store|in_flow_consent|read_state_consent|mobile_download_consent|interaction_defaults|settings_scope|teaching|nudge|gesture_audit', ':engine:settings'),
    (r'profile_store|profile_secure_storage|profile_deletion|audit2_.*deletion|default_notice_deletion|forget_in_app_server|profile_sign_in_reset', ':engine:profiles'),
    (r'app_diagnostics|native_crash|crash_|report_problem|problem_report|device_diagnostics|coverage/diag|failed_job_report|app_exit|lifecycle_report|exit_list|recent', ':engine:diagnostics'),
    (r'in_app_auto_update|update_channel', ':android:platform'),
    # entry and platform
    (r'pairing_payload|session_link|launch_shortcut|share_intent|home_navigation|launch_session', ':engine:entry'),
    (r'pairing_scanner', ':ui:kit-content'),
    # chat
    (r'transcript|v2_transcript_rows|turn_stall|tool_card|tool_state|tool_label_surfaces|live_status|perf_memory|perf_trace|perf_work_lists|chat_oc2_step_gap', ':engine:transcript'),
    (r'offline_queue|command_receipt|pending_command_journal|session_creation_receipts|queued_prompt|revamp/queued_prompt', ':engine:delivery'),
    (r'permission_|repeated_permission|form_request|session_auto_approval|automation_policy|vision_chat_approval|paseo_always_allow', ':engine:requests'),
    (r'gen_ui|mcp_chat_card|paseo_genui', ':engine:requests'),
    (r'session_draft|draft_attachments|prompt_shelf|saved_prompts|stash_|prompt_photos|phrase_chips_settings', ':engine:composer'),
    (r'phrase_|prompt_file_mention|chat_attachment_reader', ':engine:composer'),
    (r'office_text|delimited_text|common_file_preview|static_svg|local_pdf|reader_preferences|wifi_file_share|voice_|natural_voice|read_aloud|setup_voice_component', ':engine:media'),
    (r'merged_chat_feed|chat_feed|session_inventory|session_tail_cache|session_title|session_selection_sync|session_read_state|return_brief|perf_session_inventory|elsewhere|first_run|no_bottom_nav|chat_start|chat_server_state|revamp/screen_shell|servers_scenes|slice_close_servers', ':feature:chats'),
    # attention, background, widgets, schedules
    (r'scheduled_prompt', ':engine:schedule'),
    (r'widget_overlay', ':feature:widget-panels'),
    (r'widget_|attention_tile|usage_widget', ':android:widgets'),
    (r'notification_|background_|reply_watch|live_update_chip|launch_surfaces|pinned_session|first_reply_notify|android_group_names|sign_in_foreground|chat_background_result|phone_setup_notification', ':android:background'),
    (r'attention|profile_monitor|while_away|automatic_activity', ':engine:attention'),
    # settings, about, pages
    (r'settings_|coverage/settings|revamp/shared_settings|revamp/slice_p6_6a|search_index|server_capabilities_screen', ':feature:settings'),
    (r'about_', ':feature:about'),
    (r'session_note|session_export|session_context|context_usage|staged_revert|undo_from_here|session_pins|review_followups|review_handoff|run_result', ':engine:project'),
    # runtime, agents
    (r'linux_manager|linux_reset|builtin_folders|builtin_project|builtin_performance|phone_storage_folders|listening_ports|detected_dev_servers|development_service|project_dev_server|manage_space|opencode_history_copy|shared_storage_path|shared_project_roots', ':engine:runtime'),
    (r'setup_|builtin|phone_server|phone_host_runtime|phone_process|phone_storage|thermal_guard|managed_runtime|managed_move|installer_admission|leftover_process|opencode_native_install|host_script|running_on_phone|this_phone|phone_own_server_icon', ':engine:runtime'),
    (r'agent_handoff|paseo_agent_handoff|continue_with|session_handoff|session_import|agent_card_view|agent_certification|agent_tool_adapter', ':engine:agents'),
    (r'paseo_', ':protocol:paseo'),
    (r'phone_agent|local_agent|agent_|claude_|coverage/phone_agent|running_agents_strip|background_work|background_tasks|mobile_task_view|capability_flows|model_|session_model_scope|free_model|chat_free_model|active_context|workspace|e7_workspace', ':engine:agents'),
    # integrations and project
    (r'mcp_|connector_search|sv3_|session_skill|plugin_|provider_|usage_|quota_|computer_usage|in_app_quota|byo_host|host_agent_providers|library_integrations|integration_auth|tools_screen|connect_method_order|setup_planner|revamp/slice_aisetup|product_repository_mcp', ':engine:integrations'),
    (r'git/|git_link_repair|worktree_inspection|isolated_task|environment_projects|workspace_paths|project_hub|dev_preview|local_terminal|terminal_|xterm_sgr|shell_output|managed_shell|markdown_path_link|coverage/files_actions|product_repository_projects|search_go_to|demo_isolation', ':engine:project'),
]
AREA_DEFAULT = {
    'NAV': ':feature:chats', 'CHAT': ':engine:transcript', 'AGENT': ':engine:agents', 'PROTO': ':protocol:oc2',
    'STATE': ':engine:profiles', 'SEC': ':core:security', 'ANDR': ':android:platform', 'LINUX': ':engine:runtime',
    'MEDIA': ':engine:media', 'KIT': ':ui:kit', 'L10N': ':ui:words', 'XPLAT': ':desktopApp', 'BUILD': ':quality',
    'MIGR': ':android:migration',
}


def module_for(row):
    stem = stem_of(row['sources'][0])
    for rx, mod in MODULE_RULES:
        if re.search(rx, stem):
            return mod
    return AREA_DEFAULT[row['area']]


# ---- 3. hand decisions ---------------------------------------------------------------------------------------------
FIELDS = ('id', 'scope', 'slice', 'module', 'decision', 'reason', 'amended', 'amended_at', 'split_from')


def load_overrides(path):
    out = collections.OrderedDict()
    if not os.path.exists(path):
        return out
    for n, line in enumerate(open(path, encoding='utf-8'), 1):
        line = line.strip()
        if not line or line.startswith('//'):
            continue
        o = json.loads(line)
        unknown = set(o) - set(FIELDS) - {'note'}
        if unknown:
            raise SystemExit(f'overrides.jsonl:{n}: unknown fields {sorted(unknown)}')
        out.setdefault(o['id'], {}).update(o)
    return out


def seed(rows, overrides):
    result = []
    for r in rows:
        bucket = slice_for(r)
        scope, sl = BUCKET[bucket]
        line = {'id': r['id'], 'scope': scope, 'slice': sl, 'module': module_for(r) if scope == 'switch' else None}
        o = overrides.get(r['id'], {})
        for k in FIELDS[1:]:
            if k in o:
                line[k] = o[k]
        if line['scope'] != 'switch':
            line['slice'] = None
            line['module'] = o.get('module')
        line['_bucket'] = bucket
        result.append(line)
    return result


def main(argv):
    path = argv[1]
    here = os.path.dirname(os.path.abspath(__file__))
    rows = [json.loads(l) for l in open(path, encoding='utf-8') if l.strip()]
    overrides = load_overrides(os.path.join(here, 'overrides.jsonl'))
    missing = [i for i in overrides if i not in {r['id'] for r in rows}]
    if missing:
        raise SystemExit(f'overrides name unknown ids: {missing[:10]}')
    lines = seed(rows, overrides)
    by, area, e2e = collections.Counter(), collections.defaultdict(collections.Counter), collections.Counter()
    rowmap = {r['id']: r for r in rows}
    for l in lines:
        k = l['scope'] if l['scope'] != 'switch' else f"S{l['slice']}" if l['slice'] is not None else 'MANUAL'
        by[k] += 1
        area[k][rowmap[l['id']]['area']] += 1
        e2e[k] += rowmap[l['id']]['portable_e2e']
    for k in sorted(by):
        print(k, by[k], 'e2e', e2e[k], dict(sorted(area[k].items())))
    print('total', sum(by.values()), 'appendix-b buckets', dict(collections.Counter(l['_bucket'] for l in lines)))
    if '--write' in argv:
        out = os.path.join(os.path.dirname(os.path.abspath(path)), 'scope.jsonl')
        with open(out, 'w', encoding='utf-8') as f:
            for l in lines:
                d = {k: l.get(k) for k in FIELDS if k in ('id', 'scope', 'slice', 'module') or l.get(k) is not None}
                f.write(json.dumps(d, ensure_ascii=False, separators=(',', ':')) + '\n')
        print('wrote', out)


def append_lock(path):
    """behaviors.lock is append-only too: existing entries are never rewritten, so an edited row stays caught."""
    import hashlib
    lock_path = os.path.join(os.path.dirname(os.path.abspath(path)), 'behaviors.lock')
    lines = [l.rstrip('\n') for l in open(path, encoding='utf-8') if l.strip()]
    old = [l for l in open(lock_path, encoding='utf-8').read().splitlines() if l and not l.startswith('#')] \
        if os.path.exists(lock_path) else []
    with open(lock_path, 'a', encoding='utf-8') as f:
        if not old:
            f.write('# id<TAB>sha256 of the committed behaviors.jsonl line, in file order. Append-only (ScopeFileTest).\n')
        for line in lines[len(old):]:
            f.write(json.loads(line)['id'] + '\t' + hashlib.sha256(line.encode('utf-8')).hexdigest() + '\n')
    print('lock entries', len(old), '->', len(lines))


if __name__ == '__main__':
    if '--lock' in sys.argv:
        append_lock(sys.argv[1])
    main(sys.argv)
