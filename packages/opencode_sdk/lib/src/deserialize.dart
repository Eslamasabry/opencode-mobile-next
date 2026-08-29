import 'package:opencode_sdk/src/model/api_error.dart';
import 'package:opencode_sdk/src/model/api_error_data.dart';
import 'package:opencode_sdk/src/model/agent.dart';
import 'package:opencode_sdk/src/model/agent_color.dart';
import 'package:opencode_sdk/src/model/agent_config.dart';
import 'package:opencode_sdk/src/model/agent_part.dart';
import 'package:opencode_sdk/src/model/agent_part_input.dart';
import 'package:opencode_sdk/src/model/agent_part_source.dart';
import 'package:opencode_sdk/src/model/agent_v2_info.dart';
import 'package:opencode_sdk/src/model/api_auth.dart';
import 'package:opencode_sdk/src/model/app_log_request.dart';
import 'package:opencode_sdk/src/model/app_skills200_response_inner.dart';
import 'package:opencode_sdk/src/model/assistant_message.dart';
import 'package:opencode_sdk/src/model/assistant_message_path.dart';
import 'package:opencode_sdk/src/model/assistant_message_time.dart';
import 'package:opencode_sdk/src/model/assistant_message_tokens.dart';
import 'package:opencode_sdk/src/model/attachment_config.dart';
import 'package:opencode_sdk/src/model/auth.dart';
import 'package:opencode_sdk/src/model/bad_request_error.dart';
import 'package:opencode_sdk/src/model/bad_request_error_data.dart';
import 'package:opencode_sdk/src/model/catalog_updated.dart';
import 'package:opencode_sdk/src/model/command.dart';
import 'package:opencode_sdk/src/model/command_executed.dart';
import 'package:opencode_sdk/src/model/command_executed_data.dart';
import 'package:opencode_sdk/src/model/command_v2_info.dart';
import 'package:opencode_sdk/src/model/compaction_part.dart';
import 'package:opencode_sdk/src/model/config.dart';
import 'package:opencode_sdk/src/model/config_agent.dart';
import 'package:opencode_sdk/src/model/config_command_value.dart';
import 'package:opencode_sdk/src/model/config_compaction.dart';
import 'package:opencode_sdk/src/model/config_enterprise.dart';
import 'package:opencode_sdk/src/model/config_experimental.dart';
import 'package:opencode_sdk/src/model/config_mode.dart';
import 'package:opencode_sdk/src/model/config_providers200_response.dart';
import 'package:opencode_sdk/src/model/config_skills.dart';
import 'package:opencode_sdk/src/model/config_tool_output.dart';
import 'package:opencode_sdk/src/model/config_v2_experimental_policy.dart';
import 'package:opencode_sdk/src/model/config_v2_reference_git.dart';
import 'package:opencode_sdk/src/model/config_v2_reference_local.dart';
import 'package:opencode_sdk/src/model/config_watcher.dart';
import 'package:opencode_sdk/src/model/conflict_error.dart';
import 'package:opencode_sdk/src/model/connection_credential_info.dart';
import 'package:opencode_sdk/src/model/connection_env_info.dart';
import 'package:opencode_sdk/src/model/connection_info.dart';
import 'package:opencode_sdk/src/model/console_state.dart';
import 'package:opencode_sdk/src/model/content_filter_error.dart';
import 'package:opencode_sdk/src/model/context_overflow_error.dart';
import 'package:opencode_sdk/src/model/context_overflow_error_data.dart';
import 'package:opencode_sdk/src/model/credential_key.dart';
import 'package:opencode_sdk/src/model/credential_o_auth.dart';
import 'package:opencode_sdk/src/model/credential_value.dart';
import 'package:opencode_sdk/src/model/effect_http_api_error_bad_request.dart';
import 'package:opencode_sdk/src/model/effect_http_api_error_forbidden.dart';
import 'package:opencode_sdk/src/model/effect_http_api_error_internal_server_error.dart';
import 'package:opencode_sdk/src/model/event.dart';
import 'package:opencode_sdk/src/model/event_catalog_updated.dart';
import 'package:opencode_sdk/src/model/event_command_executed.dart';
import 'package:opencode_sdk/src/model/event_file_edited.dart';
import 'package:opencode_sdk/src/model/event_file_watcher_updated.dart';
import 'package:opencode_sdk/src/model/event_global_disposed.dart';
import 'package:opencode_sdk/src/model/event_installation_update_available.dart';
import 'package:opencode_sdk/src/model/event_installation_updated.dart';
import 'package:opencode_sdk/src/model/event_integration_connection_updated.dart';
import 'package:opencode_sdk/src/model/event_integration_updated.dart';
import 'package:opencode_sdk/src/model/event_lsp_updated.dart';
import 'package:opencode_sdk/src/model/event_mcp_browser_open_failed.dart';
import 'package:opencode_sdk/src/model/event_mcp_tools_changed.dart';
import 'package:opencode_sdk/src/model/event_message_part_delta.dart';
import 'package:opencode_sdk/src/model/event_message_part_removed.dart';
import 'package:opencode_sdk/src/model/event_message_part_updated.dart';
import 'package:opencode_sdk/src/model/event_message_removed.dart';
import 'package:opencode_sdk/src/model/event_message_updated.dart';
import 'package:opencode_sdk/src/model/event_models_dev_refreshed.dart';
import 'package:opencode_sdk/src/model/event_permission_asked.dart';
import 'package:opencode_sdk/src/model/event_permission_replied.dart';
import 'package:opencode_sdk/src/model/event_permission_v2_asked.dart';
import 'package:opencode_sdk/src/model/event_permission_v2_replied.dart';
import 'package:opencode_sdk/src/model/event_plugin_added.dart';
import 'package:opencode_sdk/src/model/event_project_directories_updated.dart';
import 'package:opencode_sdk/src/model/event_project_updated.dart';
import 'package:opencode_sdk/src/model/event_pty_created.dart';
import 'package:opencode_sdk/src/model/event_pty_deleted.dart';
import 'package:opencode_sdk/src/model/event_pty_exited.dart';
import 'package:opencode_sdk/src/model/event_pty_updated.dart';
import 'package:opencode_sdk/src/model/event_question_asked.dart';
import 'package:opencode_sdk/src/model/event_question_rejected.dart';
import 'package:opencode_sdk/src/model/event_question_replied.dart';
import 'package:opencode_sdk/src/model/event_question_v2_asked.dart';
import 'package:opencode_sdk/src/model/event_question_v2_rejected.dart';
import 'package:opencode_sdk/src/model/event_question_v2_replied.dart';
import 'package:opencode_sdk/src/model/event_reference_updated.dart';
import 'package:opencode_sdk/src/model/event_server_connected.dart';
import 'package:opencode_sdk/src/model/event_server_instance_disposed.dart';
import 'package:opencode_sdk/src/model/event_server_instance_disposed_properties.dart';
import 'package:opencode_sdk/src/model/event_session_compacted.dart';
import 'package:opencode_sdk/src/model/event_session_created.dart';
import 'package:opencode_sdk/src/model/event_session_deleted.dart';
import 'package:opencode_sdk/src/model/event_session_diff.dart';
import 'package:opencode_sdk/src/model/event_session_error.dart';
import 'package:opencode_sdk/src/model/event_session_error_properties.dart';
import 'package:opencode_sdk/src/model/event_session_idle.dart';
import 'package:opencode_sdk/src/model/event_session_next_agent_switched.dart';
import 'package:opencode_sdk/src/model/event_session_next_compaction_delta.dart';
import 'package:opencode_sdk/src/model/event_session_next_compaction_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_compaction_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_context_updated.dart';
import 'package:opencode_sdk/src/model/event_session_next_model_switched.dart';
import 'package:opencode_sdk/src/model/event_session_next_moved.dart';
import 'package:opencode_sdk/src/model/event_session_next_prompt_admitted.dart';
import 'package:opencode_sdk/src/model/event_session_next_prompted.dart';
import 'package:opencode_sdk/src/model/event_session_next_reasoning_delta.dart';
import 'package:opencode_sdk/src/model/event_session_next_reasoning_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_reasoning_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_retried.dart';
import 'package:opencode_sdk/src/model/event_session_next_revert_cleared.dart';
import 'package:opencode_sdk/src/model/event_session_next_revert_committed.dart';
import 'package:opencode_sdk/src/model/event_session_next_revert_staged.dart';
import 'package:opencode_sdk/src/model/event_session_next_shell_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_shell_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_step_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_step_failed.dart';
import 'package:opencode_sdk/src/model/event_session_next_step_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_synthetic.dart';
import 'package:opencode_sdk/src/model/event_session_next_text_delta.dart';
import 'package:opencode_sdk/src/model/event_session_next_text_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_text_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_called.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_failed.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_input_delta.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_input_ended.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_input_started.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_progress.dart';
import 'package:opencode_sdk/src/model/event_session_next_tool_success.dart';
import 'package:opencode_sdk/src/model/event_session_status.dart';
import 'package:opencode_sdk/src/model/event_session_updated.dart';
import 'package:opencode_sdk/src/model/event_todo_updated.dart';
import 'package:opencode_sdk/src/model/event_tui_command_execute.dart';
import 'package:opencode_sdk/src/model/event_tui_command_execute_properties.dart';
import 'package:opencode_sdk/src/model/event_tui_command_execute_schema2.dart';
import 'package:opencode_sdk/src/model/event_tui_command_execute_schema2_properties.dart';
import 'package:opencode_sdk/src/model/event_tui_prompt_append.dart';
import 'package:opencode_sdk/src/model/event_tui_prompt_append_schema2.dart';
import 'package:opencode_sdk/src/model/event_tui_session_select.dart';
import 'package:opencode_sdk/src/model/event_tui_session_select_schema2.dart';
import 'package:opencode_sdk/src/model/event_tui_toast_show.dart';
import 'package:opencode_sdk/src/model/event_tui_toast_show_schema2.dart';
import 'package:opencode_sdk/src/model/event_vcs_branch_updated.dart';
import 'package:opencode_sdk/src/model/event_workspace_failed.dart';
import 'package:opencode_sdk/src/model/event_workspace_ready.dart';
import 'package:opencode_sdk/src/model/event_workspace_status.dart';
import 'package:opencode_sdk/src/model/event_workspace_status_properties.dart';
import 'package:opencode_sdk/src/model/event_worktree_failed.dart';
import 'package:opencode_sdk/src/model/event_worktree_ready.dart';
import 'package:opencode_sdk/src/model/experimental_capabilities.dart';
import 'package:opencode_sdk/src/model/experimental_console_list_orgs200_response.dart';
import 'package:opencode_sdk/src/model/experimental_console_list_orgs200_response_orgs_inner.dart';
import 'package:opencode_sdk/src/model/experimental_console_switch_org_request.dart';
import 'package:opencode_sdk/src/model/experimental_control_plane_move_session_request.dart';
import 'package:opencode_sdk/src/model/experimental_project_copy_generate_name200_response.dart';
import 'package:opencode_sdk/src/model/experimental_project_copy_generate_name_request.dart';
import 'package:opencode_sdk/src/model/experimental_workspace_adapter_list200_response_inner.dart';
import 'package:opencode_sdk/src/model/experimental_workspace_create_request.dart';
import 'package:opencode_sdk/src/model/experimental_workspace_warp_request.dart';
import 'package:opencode_sdk/src/model/file.dart';
import 'package:opencode_sdk/src/model/file_content.dart';
import 'package:opencode_sdk/src/model/file_content_patch.dart';
import 'package:opencode_sdk/src/model/file_content_patch_hunks_inner.dart';
import 'package:opencode_sdk/src/model/file_diff.dart';
import 'package:opencode_sdk/src/model/file_edited.dart';
import 'package:opencode_sdk/src/model/file_edited_data.dart';
import 'package:opencode_sdk/src/model/file_node.dart';
import 'package:opencode_sdk/src/model/file_part.dart';
import 'package:opencode_sdk/src/model/file_part_input.dart';
import 'package:opencode_sdk/src/model/file_part_source.dart';
import 'package:opencode_sdk/src/model/file_part_source_text.dart';
import 'package:opencode_sdk/src/model/file_source.dart';
import 'package:opencode_sdk/src/model/file_system_entry.dart';
import 'package:opencode_sdk/src/model/file_watcher_updated.dart';
import 'package:opencode_sdk/src/model/file_watcher_updated_data.dart';
import 'package:opencode_sdk/src/model/find_text200_response_inner.dart';
import 'package:opencode_sdk/src/model/find_text200_response_inner_path.dart';
import 'package:opencode_sdk/src/model/find_text200_response_inner_submatches_inner.dart';
import 'package:opencode_sdk/src/model/forbidden_error.dart';
import 'package:opencode_sdk/src/model/formatter_status.dart';
import 'package:opencode_sdk/src/model/global_disposed.dart';
import 'package:opencode_sdk/src/model/global_event.dart';
import 'package:opencode_sdk/src/model/global_health200_response.dart';
import 'package:opencode_sdk/src/model/global_session.dart';
import 'package:opencode_sdk/src/model/global_upgrade_request.dart';
import 'package:opencode_sdk/src/model/image_attachment_config.dart';
import 'package:opencode_sdk/src/model/installation_update_available.dart';
import 'package:opencode_sdk/src/model/installation_updated.dart';
import 'package:opencode_sdk/src/model/installation_updated_data.dart';
import 'package:opencode_sdk/src/model/integration_attempt.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of1.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of1_time.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of2.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of2_time.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of3.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of3_time.dart';
import 'package:opencode_sdk/src/model/integration_attempt_status_any_of_time.dart';
import 'package:opencode_sdk/src/model/integration_attempt_time.dart';
import 'package:opencode_sdk/src/model/integration_connection_updated.dart';
import 'package:opencode_sdk/src/model/integration_connection_updated_data.dart';
import 'package:opencode_sdk/src/model/integration_env_method.dart';
import 'package:opencode_sdk/src/model/integration_info.dart';
import 'package:opencode_sdk/src/model/integration_key_method.dart';
import 'package:opencode_sdk/src/model/integration_method.dart';
import 'package:opencode_sdk/src/model/integration_o_auth_method.dart';
import 'package:opencode_sdk/src/model/integration_ref.dart';
import 'package:opencode_sdk/src/model/integration_select_prompt.dart';
import 'package:opencode_sdk/src/model/integration_select_prompt_options_inner.dart';
import 'package:opencode_sdk/src/model/integration_text_prompt.dart';
import 'package:opencode_sdk/src/model/integration_updated.dart';
import 'package:opencode_sdk/src/model/integration_when.dart';
import 'package:opencode_sdk/src/model/invalid_cursor_error.dart';
import 'package:opencode_sdk/src/model/invalid_request_error.dart';
import 'package:opencode_sdk/src/model/llm_tool_content.dart';
import 'package:opencode_sdk/src/model/lsp_status.dart';
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/location_info_project.dart';
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/lsp_updated.dart';
import 'package:opencode_sdk/src/model/mcp_status.dart';
import 'package:opencode_sdk/src/model/mcp_status_connected.dart';
import 'package:opencode_sdk/src/model/mcp_status_disabled.dart';
import 'package:opencode_sdk/src/model/mcp_status_failed.dart';
import 'package:opencode_sdk/src/model/mcp_status_needs_auth.dart';
import 'package:opencode_sdk/src/model/mcp_status_needs_client_registration.dart';
import 'package:opencode_sdk/src/model/mcp_add_request.dart';
import 'package:opencode_sdk/src/model/mcp_auth_callback_request.dart';
import 'package:opencode_sdk/src/model/mcp_auth_remove200_response.dart';
import 'package:opencode_sdk/src/model/mcp_auth_start200_response.dart';
import 'package:opencode_sdk/src/model/mcp_browser_open_failed.dart';
import 'package:opencode_sdk/src/model/mcp_browser_open_failed_data.dart';
import 'package:opencode_sdk/src/model/mcp_local_config.dart';
import 'package:opencode_sdk/src/model/mcp_o_auth_config.dart';
import 'package:opencode_sdk/src/model/mcp_remote_config.dart';
import 'package:opencode_sdk/src/model/mcp_resource.dart';
import 'package:opencode_sdk/src/model/mcp_server_not_found_error.dart';
import 'package:opencode_sdk/src/model/mcp_tools_changed.dart';
import 'package:opencode_sdk/src/model/mcp_tools_changed_data.dart';
import 'package:opencode_sdk/src/model/mcp_unsupported_o_auth_error.dart';
import 'package:opencode_sdk/src/model/message.dart';
import 'package:opencode_sdk/src/model/message_aborted_error.dart';
import 'package:opencode_sdk/src/model/message_not_found_error.dart';
import 'package:opencode_sdk/src/model/message_output_length_error.dart';
import 'package:opencode_sdk/src/model/message_part_delta.dart';
import 'package:opencode_sdk/src/model/message_part_delta_data.dart';
import 'package:opencode_sdk/src/model/message_part_removed.dart';
import 'package:opencode_sdk/src/model/message_part_updated.dart';
import 'package:opencode_sdk/src/model/message_removed.dart';
import 'package:opencode_sdk/src/model/message_updated.dart';
import 'package:opencode_sdk/src/model/model.dart';
import 'package:opencode_sdk/src/model/model_api.dart';
import 'package:opencode_sdk/src/model/model_api_any_of.dart';
import 'package:opencode_sdk/src/model/model_api_any_of1.dart';
import 'package:opencode_sdk/src/model/model_capabilities.dart';
import 'package:opencode_sdk/src/model/model_capabilities_input.dart';
import 'package:opencode_sdk/src/model/model_cost.dart';
import 'package:opencode_sdk/src/model/model_cost_experimental_over200_k.dart';
import 'package:opencode_sdk/src/model/model_cost_tier.dart';
import 'package:opencode_sdk/src/model/model_cost_tiers_inner.dart';
import 'package:opencode_sdk/src/model/model_cost_tiers_inner_tier.dart';
import 'package:opencode_sdk/src/model/model_part.dart';
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:opencode_sdk/src/model/model_v2_capabilities.dart';
import 'package:opencode_sdk/src/model/model_v2_info.dart';
import 'package:opencode_sdk/src/model/model_v2_info_limit.dart';
import 'package:opencode_sdk/src/model/model_v2_info_request.dart';
import 'package:opencode_sdk/src/model/model_v2_info_time.dart';
import 'package:opencode_sdk/src/model/model_v2_info_variants_inner.dart';
import 'package:opencode_sdk/src/model/models_dev_refreshed.dart';
import 'package:opencode_sdk/src/model/move_session_destination.dart';
import 'package:opencode_sdk/src/model/move_session_error.dart';
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:opencode_sdk/src/model/not_found_error.dart';
import 'package:opencode_sdk/src/model/not_found_error_data.dart';
import 'package:opencode_sdk/src/model/o_auth.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union001.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of1.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of10.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of11.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of12.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of13.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of14.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of15.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of16.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of17.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of18.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of19.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of2.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of20.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of21.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of22.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of23.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of24.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of25.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of26.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of27.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of28.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of29.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of3.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of30.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of31.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of32.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of33.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of34.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of35.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of36.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of37.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of38.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of39.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of4.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of40.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of41.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of42.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of43.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of44.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of45.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of45_properties.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of45_properties_error.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of46.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of47.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of48.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of49.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of5.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of50.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of51.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of52.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of53.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of54.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of55.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of56.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of57.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of58.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of59.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of6.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of60.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of61.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of62.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of63.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of64.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of65.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of66.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of67.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of67_properties.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of67_properties_command.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of68.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of69.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of7.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of70.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of71.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of72.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of73.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of74.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of75.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of76.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of77.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of78.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of79.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of8.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of80.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of81.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of82.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of83.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of84.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of85.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of86.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of87.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of9.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union003.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union004.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union005.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union006.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union006_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union006_any_of_field.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union007.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union008.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union009.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union010.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union011.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union012.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union012_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union013.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union013_any_of_value.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014_any_of_value.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014_any_of_value_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014_any_of_value_any_of1.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union015.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016_any_of1.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016_any_of_when.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union017.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union018.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union019.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union020.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union021.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union022.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union023.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union024.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union025.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union026.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union027.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union028.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union029.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union030.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union031.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union032.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union033.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union034.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union035.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union036.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union037.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union038.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union039.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union040.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union041.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union041_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union041_any_of1.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union042.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union043.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union044.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union045.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union046.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union047.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union048.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union049.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union050.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union051.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union052.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union053.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union054.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union055.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union056.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union057.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union058.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union059.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union060.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union061.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union062.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union063.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union064.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union065.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union066.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union067.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union068.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union069.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union070.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union071.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union072.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union073.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union074.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union075.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union076.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union077.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union078.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union079.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union080.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union081.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union082.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union083.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union084.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union085.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union086.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union087.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union088.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union089.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union090.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union091.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union092.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union093.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union094.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union095.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union096.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union097.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union098.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union099.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union100.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union101.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union102.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union103.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union104.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union105.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union106.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union107.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union108.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union109.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union110.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union111.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union112.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union113.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union114.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union115.dart';
import 'package:opencode_sdk/src/model/output_format.dart';
import 'package:opencode_sdk/src/model/output_format1.dart';
import 'package:opencode_sdk/src/model/output_format1_any_of.dart';
import 'package:opencode_sdk/src/model/output_format1_any_of1.dart';
import 'package:opencode_sdk/src/model/output_format_json_schema.dart';
import 'package:opencode_sdk/src/model/output_format_text.dart';
import 'package:opencode_sdk/src/model/patch_part.dart';
import 'package:opencode_sdk/src/model/path.dart';
import 'package:opencode_sdk/src/model/permission_asked.dart';
import 'package:opencode_sdk/src/model/permission_asked_data.dart';
import 'package:opencode_sdk/src/model/permission_config.dart';
import 'package:opencode_sdk/src/model/permission_config_any_of.dart';
import 'package:opencode_sdk/src/model/permission_not_found_error.dart';
import 'package:opencode_sdk/src/model/permission_replied.dart';
import 'package:opencode_sdk/src/model/permission_replied_data.dart';
import 'package:opencode_sdk/src/model/permission_reply_request.dart';
import 'package:opencode_sdk/src/model/permission_request.dart';
import 'package:opencode_sdk/src/model/permission_request_tool.dart';
import 'package:opencode_sdk/src/model/permission_respond_request.dart';
import 'package:opencode_sdk/src/model/permission_rule.dart';
import 'package:opencode_sdk/src/model/permission_rule_config.dart';
import 'package:opencode_sdk/src/model/permission_saved_info.dart';
import 'package:opencode_sdk/src/model/permission_v2_asked.dart';
import 'package:opencode_sdk/src/model/permission_v2_asked_data.dart';
import 'package:opencode_sdk/src/model/permission_v2_replied.dart';
import 'package:opencode_sdk/src/model/permission_v2_replied_data.dart';
import 'package:opencode_sdk/src/model/permission_v2_request.dart';
import 'package:opencode_sdk/src/model/permission_v2_rule.dart';
import 'package:opencode_sdk/src/model/permission_v2_source.dart';
import 'package:opencode_sdk/src/model/plugin_added.dart';
import 'package:opencode_sdk/src/model/plugin_added_data.dart';
import 'package:opencode_sdk/src/model/project.dart';
import 'package:opencode_sdk/src/model/project_commands.dart';
import 'package:opencode_sdk/src/model/project_copy_copy.dart';
import 'package:opencode_sdk/src/model/project_copy_error.dart';
import 'package:opencode_sdk/src/model/project_copy_error_data.dart';
import 'package:opencode_sdk/src/model/project_directories_inner.dart';
import 'package:opencode_sdk/src/model/project_directories_updated.dart';
import 'package:opencode_sdk/src/model/project_directories_updated_data.dart';
import 'package:opencode_sdk/src/model/project_icon.dart';
import 'package:opencode_sdk/src/model/project_not_found_error.dart';
import 'package:opencode_sdk/src/model/project_summary.dart';
import 'package:opencode_sdk/src/model/project_time.dart';
import 'package:opencode_sdk/src/model/project_update_request.dart';
import 'package:opencode_sdk/src/model/project_updated.dart';
import 'package:opencode_sdk/src/model/project_updated_data.dart';
import 'package:opencode_sdk/src/model/prompt.dart';
import 'package:opencode_sdk/src/model/prompt_agent_attachment.dart';
import 'package:opencode_sdk/src/model/prompt_file_attachment.dart';
import 'package:opencode_sdk/src/model/prompt_input.dart';
import 'package:opencode_sdk/src/model/prompt_input_file_attachment.dart';
import 'package:opencode_sdk/src/model/prompt_source.dart';
import 'package:opencode_sdk/src/model/provider.dart';
import 'package:opencode_sdk/src/model/provider_aisdk.dart';
import 'package:opencode_sdk/src/model/provider_api_model.dart';
import 'package:opencode_sdk/src/model/provider_auth_authorization.dart';
import 'package:opencode_sdk/src/model/provider_auth_error.dart';
import 'package:opencode_sdk/src/model/provider_auth_error1.dart';
import 'package:opencode_sdk/src/model/provider_auth_error1_data.dart';
import 'package:opencode_sdk/src/model/provider_auth_error_data.dart';
import 'package:opencode_sdk/src/model/provider_auth_method.dart';
import 'package:opencode_sdk/src/model/provider_config.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_cost.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_cost_context_over200k.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_limit.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_modalities.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_provider.dart';
import 'package:opencode_sdk/src/model/provider_config_models_value_variants_value.dart';
import 'package:opencode_sdk/src/model/provider_config_options.dart';
import 'package:opencode_sdk/src/model/provider_list200_response.dart';
import 'package:opencode_sdk/src/model/provider_native.dart';
import 'package:opencode_sdk/src/model/provider_not_found_error.dart';
import 'package:opencode_sdk/src/model/provider_oauth_authorize_request.dart';
import 'package:opencode_sdk/src/model/provider_oauth_callback_request.dart';
import 'package:opencode_sdk/src/model/provider_request.dart';
import 'package:opencode_sdk/src/model/provider_v2_info.dart';
import 'package:opencode_sdk/src/model/pty.dart';
import 'package:opencode_sdk/src/model/pty_create_request.dart';
import 'package:opencode_sdk/src/model/pty_created.dart';
import 'package:opencode_sdk/src/model/pty_created_data.dart';
import 'package:opencode_sdk/src/model/pty_deleted.dart';
import 'package:opencode_sdk/src/model/pty_deleted_data.dart';
import 'package:opencode_sdk/src/model/pty_exited.dart';
import 'package:opencode_sdk/src/model/pty_exited_data.dart';
import 'package:opencode_sdk/src/model/pty_forbidden_error.dart';
import 'package:opencode_sdk/src/model/pty_not_found_error.dart';
import 'package:opencode_sdk/src/model/pty_shells200_response_inner.dart';
import 'package:opencode_sdk/src/model/pty_ticket_connect_token.dart';
import 'package:opencode_sdk/src/model/pty_update_request.dart';
import 'package:opencode_sdk/src/model/pty_update_request_size.dart';
import 'package:opencode_sdk/src/model/pty_updated.dart';
import 'package:opencode_sdk/src/model/question_asked.dart';
import 'package:opencode_sdk/src/model/question_asked_data.dart';
import 'package:opencode_sdk/src/model/question_info.dart';
import 'package:opencode_sdk/src/model/question_not_found_error.dart';
import 'package:opencode_sdk/src/model/question_option.dart';
import 'package:opencode_sdk/src/model/question_rejected.dart';
import 'package:opencode_sdk/src/model/question_rejected_schema2.dart';
import 'package:opencode_sdk/src/model/question_rejected_schema2_data.dart';
import 'package:opencode_sdk/src/model/question_replied.dart';
import 'package:opencode_sdk/src/model/question_replied_schema2.dart';
import 'package:opencode_sdk/src/model/question_replied_schema2_data.dart';
import 'package:opencode_sdk/src/model/question_reply_request.dart';
import 'package:opencode_sdk/src/model/question_request.dart';
import 'package:opencode_sdk/src/model/question_tool.dart';
import 'package:opencode_sdk/src/model/question_v2_asked.dart';
import 'package:opencode_sdk/src/model/question_v2_asked_data.dart';
import 'package:opencode_sdk/src/model/question_v2_info.dart';
import 'package:opencode_sdk/src/model/question_v2_option.dart';
import 'package:opencode_sdk/src/model/question_v2_rejected.dart';
import 'package:opencode_sdk/src/model/question_v2_replied.dart';
import 'package:opencode_sdk/src/model/question_v2_replied_data.dart';
import 'package:opencode_sdk/src/model/question_v2_reply.dart';
import 'package:opencode_sdk/src/model/question_v2_request.dart';
import 'package:opencode_sdk/src/model/question_v2_tool.dart';
import 'package:opencode_sdk/src/model/range.dart';
import 'package:opencode_sdk/src/model/range_start.dart';
import 'package:opencode_sdk/src/model/reasoning_part.dart';
import 'package:opencode_sdk/src/model/reference_git_source.dart';
import 'package:opencode_sdk/src/model/reference_info.dart';
import 'package:opencode_sdk/src/model/reference_local_source.dart';
import 'package:opencode_sdk/src/model/reference_source.dart';
import 'package:opencode_sdk/src/model/reference_updated.dart';
import 'package:opencode_sdk/src/model/resource_source.dart';
import 'package:opencode_sdk/src/model/retry_part.dart';
import 'package:opencode_sdk/src/model/retry_part_time.dart';
import 'package:opencode_sdk/src/model/revert_state.dart';
import 'package:opencode_sdk/src/model/server_config.dart';
import 'package:opencode_sdk/src/model/server_connected.dart';
import 'package:opencode_sdk/src/model/service_unavailable_error.dart';
import 'package:opencode_sdk/src/model/session.dart';
import 'package:opencode_sdk/src/model/session_active.dart';
import 'package:opencode_sdk/src/model/session_busy_error.dart';
import 'package:opencode_sdk/src/model/session_command_request.dart';
import 'package:opencode_sdk/src/model/session_command_request_parts_inner.dart';
import 'package:opencode_sdk/src/model/session_compacted.dart';
import 'package:opencode_sdk/src/model/session_create_request.dart';
import 'package:opencode_sdk/src/model/session_create_request_model.dart';
import 'package:opencode_sdk/src/model/session_created.dart';
import 'package:opencode_sdk/src/model/session_deleted.dart';
import 'package:opencode_sdk/src/model/session_diff.dart';
import 'package:opencode_sdk/src/model/session_diff_data.dart';
import 'package:opencode_sdk/src/model/session_durable_event.dart';
import 'package:opencode_sdk/src/model/session_error.dart';
import 'package:opencode_sdk/src/model/session_error_data.dart';
import 'package:opencode_sdk/src/model/session_error_unknown.dart';
import 'package:opencode_sdk/src/model/session_fork_request.dart';
import 'package:opencode_sdk/src/model/session_history.dart';
import 'package:opencode_sdk/src/model/session_idle.dart';
import 'package:opencode_sdk/src/model/session_init_request.dart';
import 'package:opencode_sdk/src/model/session_input_admitted.dart';
import 'package:opencode_sdk/src/model/session_message.dart';
import 'package:opencode_sdk/src/model/session_message200_response.dart';
import 'package:opencode_sdk/src/model/session_message_agent_switched.dart';
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:opencode_sdk/src/model/session_message_assistant.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_reasoning.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_snapshot.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_text.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_tool.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_tool_provider.dart';
import 'package:opencode_sdk/src/model/session_message_assistant_tool_time.dart';
import 'package:opencode_sdk/src/model/session_message_compaction.dart';
import 'package:opencode_sdk/src/model/session_message_model_switched.dart';
import 'package:opencode_sdk/src/model/session_message_shell.dart';
import 'package:opencode_sdk/src/model/session_message_shell_time.dart';
import 'package:opencode_sdk/src/model/session_message_synthetic.dart';
import 'package:opencode_sdk/src/model/session_message_system.dart';
import 'package:opencode_sdk/src/model/session_message_tool_state_completed.dart';
import 'package:opencode_sdk/src/model/session_message_tool_state_error.dart';
import 'package:opencode_sdk/src/model/session_message_tool_state_pending.dart';
import 'package:opencode_sdk/src/model/session_message_tool_state_running.dart';
import 'package:opencode_sdk/src/model/session_message_user.dart';
import 'package:opencode_sdk/src/model/session_messages200_response_inner.dart';
import 'package:opencode_sdk/src/model/session_messages_response.dart';
import 'package:opencode_sdk/src/model/session_next_agent_switched.dart';
import 'package:opencode_sdk/src/model/session_next_compaction_delta.dart';
import 'package:opencode_sdk/src/model/session_next_compaction_ended.dart';
import 'package:opencode_sdk/src/model/session_next_compaction_started.dart';
import 'package:opencode_sdk/src/model/session_next_context_updated.dart';
import 'package:opencode_sdk/src/model/session_next_model_switched.dart';
import 'package:opencode_sdk/src/model/session_next_moved.dart';
import 'package:opencode_sdk/src/model/session_next_prompt_admitted.dart';
import 'package:opencode_sdk/src/model/session_next_prompted.dart';
import 'package:opencode_sdk/src/model/session_next_reasoning_delta.dart';
import 'package:opencode_sdk/src/model/session_next_reasoning_delta_data.dart';
import 'package:opencode_sdk/src/model/session_next_reasoning_ended.dart';
import 'package:opencode_sdk/src/model/session_next_reasoning_started.dart';
import 'package:opencode_sdk/src/model/session_next_retried.dart';
import 'package:opencode_sdk/src/model/session_next_retry_error.dart';
import 'package:opencode_sdk/src/model/session_next_revert_cleared.dart';
import 'package:opencode_sdk/src/model/session_next_revert_committed.dart';
import 'package:opencode_sdk/src/model/session_next_revert_staged.dart';
import 'package:opencode_sdk/src/model/session_next_shell_ended.dart';
import 'package:opencode_sdk/src/model/session_next_shell_started.dart';
import 'package:opencode_sdk/src/model/session_next_step_ended.dart';
import 'package:opencode_sdk/src/model/session_next_step_failed.dart';
import 'package:opencode_sdk/src/model/session_next_step_started.dart';
import 'package:opencode_sdk/src/model/session_next_synthetic.dart';
import 'package:opencode_sdk/src/model/session_next_text_delta.dart';
import 'package:opencode_sdk/src/model/session_next_text_delta_data.dart';
import 'package:opencode_sdk/src/model/session_next_text_ended.dart';
import 'package:opencode_sdk/src/model/session_next_text_started.dart';
import 'package:opencode_sdk/src/model/session_next_tool_called.dart';
import 'package:opencode_sdk/src/model/session_next_tool_failed.dart';
import 'package:opencode_sdk/src/model/session_next_tool_input_delta.dart';
import 'package:opencode_sdk/src/model/session_next_tool_input_delta_data.dart';
import 'package:opencode_sdk/src/model/session_next_tool_input_ended.dart';
import 'package:opencode_sdk/src/model/session_next_tool_input_started.dart';
import 'package:opencode_sdk/src/model/session_next_tool_progress.dart';
import 'package:opencode_sdk/src/model/session_next_tool_success.dart';
import 'package:opencode_sdk/src/model/session_not_found_error.dart';
import 'package:opencode_sdk/src/model/session_prompt200_response.dart';
import 'package:opencode_sdk/src/model/session_prompt_async_request.dart';
import 'package:opencode_sdk/src/model/session_prompt_async_request_model.dart';
import 'package:opencode_sdk/src/model/session_prompt_request.dart';
import 'package:opencode_sdk/src/model/session_prompt_request_model.dart';
import 'package:opencode_sdk/src/model/session_revert.dart';
import 'package:opencode_sdk/src/model/session_revert_request.dart';
import 'package:opencode_sdk/src/model/session_share.dart';
import 'package:opencode_sdk/src/model/session_shell200_response.dart';
import 'package:opencode_sdk/src/model/session_shell_request.dart';
import 'package:opencode_sdk/src/model/session_status.dart';
import 'package:opencode_sdk/src/model/session_status_any_of.dart';
import 'package:opencode_sdk/src/model/session_status_any_of1.dart';
import 'package:opencode_sdk/src/model/session_status_any_of1_action.dart';
import 'package:opencode_sdk/src/model/session_status_any_of2.dart';
import 'package:opencode_sdk/src/model/session_status_schema2.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:opencode_sdk/src/model/session_summarize_request.dart';
import 'package:opencode_sdk/src/model/session_summary.dart';
import 'package:opencode_sdk/src/model/session_time.dart';
import 'package:opencode_sdk/src/model/session_tokens.dart';
import 'package:opencode_sdk/src/model/session_tokens_cache.dart';
import 'package:opencode_sdk/src/model/session_update_request.dart';
import 'package:opencode_sdk/src/model/session_update_request_time.dart';
import 'package:opencode_sdk/src/model/session_updated.dart';
import 'package:opencode_sdk/src/model/session_v2_info.dart';
import 'package:opencode_sdk/src/model/session_v2_info_time.dart';
import 'package:opencode_sdk/src/model/sessions_response.dart';
import 'package:opencode_sdk/src/model/sessions_response_cursor.dart';
import 'package:opencode_sdk/src/model/skill_v2_directory_source.dart';
import 'package:opencode_sdk/src/model/skill_v2_embedded_source.dart';
import 'package:opencode_sdk/src/model/skill_v2_info.dart';
import 'package:opencode_sdk/src/model/skill_v2_source.dart';
import 'package:opencode_sdk/src/model/skill_v2_url_source.dart';
import 'package:opencode_sdk/src/model/snapshot_file_diff.dart';
import 'package:opencode_sdk/src/model/snapshot_part.dart';
import 'package:opencode_sdk/src/model/step_finish_part.dart';
import 'package:opencode_sdk/src/model/step_start_part.dart';
import 'package:opencode_sdk/src/model/structured_output_error.dart';
import 'package:opencode_sdk/src/model/structured_output_error_data.dart';
import 'package:opencode_sdk/src/model/subtask_part.dart';
import 'package:opencode_sdk/src/model/subtask_part_input.dart';
import 'package:opencode_sdk/src/model/symbol.dart';
import 'package:opencode_sdk/src/model/symbol_location.dart';
import 'package:opencode_sdk/src/model/symbol_source.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_removed.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_removed_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_removed_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_updated.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_updated_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_message_part_updated_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_message_removed.dart';
import 'package:opencode_sdk/src/model/sync_event_message_removed_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_message_removed_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_message_updated.dart';
import 'package:opencode_sdk/src/model/sync_event_message_updated_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_message_updated_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_created.dart';
import 'package:opencode_sdk/src/model/sync_event_session_created_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_created_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_deleted.dart';
import 'package:opencode_sdk/src/model/sync_event_session_deleted_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_agent_switched.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_agent_switched_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_agent_switched_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_compaction_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_context_updated.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_context_updated_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_context_updated_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_model_switched.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_model_switched_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_model_switched_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_moved.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_moved_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_moved_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_prompt_admitted.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_prompt_admitted_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_prompted.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_prompted_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_prompted_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_retried.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_retried_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_retried_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_cleared.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_cleared_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_cleared_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_committed.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_committed_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_committed_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_staged.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_staged_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_staged_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_shell_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_failed.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_failed_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_failed_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_step_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_synthetic.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_synthetic_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_text_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data_provider.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_failed.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_failed_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_failed_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_ended.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_ended_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_ended_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_started.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_started_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_started_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_progress.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_progress_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_progress_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_success.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_success_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_success_sync_event_data.dart';
import 'package:opencode_sdk/src/model/sync_event_session_updated.dart';
import 'package:opencode_sdk/src/model/sync_event_session_updated_sync_event.dart';
import 'package:opencode_sdk/src/model/sync_history_list200_response_inner.dart';
import 'package:opencode_sdk/src/model/sync_replay200_response.dart';
import 'package:opencode_sdk/src/model/sync_replay_request.dart';
import 'package:opencode_sdk/src/model/sync_replay_request_events_inner.dart';
import 'package:opencode_sdk/src/model/sync_steal200_response.dart';
import 'package:opencode_sdk/src/model/sync_steal_request.dart';
import 'package:opencode_sdk/src/model/text_part.dart';
import 'package:opencode_sdk/src/model/text_part_input.dart';
import 'package:opencode_sdk/src/model/text_part_time.dart';
import 'package:opencode_sdk/src/model/todo.dart';
import 'package:opencode_sdk/src/model/todo_updated.dart';
import 'package:opencode_sdk/src/model/todo_updated_data.dart';
import 'package:opencode_sdk/src/model/tool_file_content.dart';
import 'package:opencode_sdk/src/model/tool_list_item.dart';
import 'package:opencode_sdk/src/model/tool_part.dart';
import 'package:opencode_sdk/src/model/tool_state.dart';
import 'package:opencode_sdk/src/model/tool_state_completed.dart';
import 'package:opencode_sdk/src/model/tool_state_completed_time.dart';
import 'package:opencode_sdk/src/model/tool_state_error.dart';
import 'package:opencode_sdk/src/model/tool_state_error_time.dart';
import 'package:opencode_sdk/src/model/tool_state_pending.dart';
import 'package:opencode_sdk/src/model/tool_state_running.dart';
import 'package:opencode_sdk/src/model/tool_state_running_time.dart';
import 'package:opencode_sdk/src/model/tool_text_content.dart';
import 'package:opencode_sdk/src/model/tui_command_execute.dart';
import 'package:opencode_sdk/src/model/tui_command_execute_data.dart';
import 'package:opencode_sdk/src/model/tui_control_next200_response.dart';
import 'package:opencode_sdk/src/model/tui_execute_command_request.dart';
import 'package:opencode_sdk/src/model/tui_prompt_append.dart';
import 'package:opencode_sdk/src/model/tui_select_session_request.dart';
import 'package:opencode_sdk/src/model/tui_session_select.dart';
import 'package:opencode_sdk/src/model/tui_show_toast_request.dart';
import 'package:opencode_sdk/src/model/tui_toast_show.dart';
import 'package:opencode_sdk/src/model/unauthorized_error.dart';
import 'package:opencode_sdk/src/model/unknown_error.dart';
import 'package:opencode_sdk/src/model/unknown_error1.dart';
import 'package:opencode_sdk/src/model/unknown_error_data.dart';
import 'package:opencode_sdk/src/model/user_message.dart';
import 'package:opencode_sdk/src/model/user_message_model.dart';
import 'package:opencode_sdk/src/model/user_message_summary.dart';
import 'package:opencode_sdk/src/model/user_message_time.dart';
import 'package:opencode_sdk/src/model/v2_agent_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_command_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_credential_update_request.dart';
import 'package:opencode_sdk/src/model/v2_event.dart';
import 'package:opencode_sdk/src/model/v2_fs_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_health_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_attempt_complete_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_attempt_status200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_key_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_oauth200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_connect_oauth_request.dart';
import 'package:opencode_sdk/src/model/v2_integration_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_integration_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_model_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_permission_request_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_permission_saved_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_project_copy_create_request.dart';
import 'package:opencode_sdk/src/model/v2_project_copy_remove_request.dart';
import 'package:opencode_sdk/src/model/v2_provider_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_provider_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_connect_token200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_create200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_pty_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_question_request_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_reference_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_active200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_context200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_create200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_create_request.dart';
import 'package:opencode_sdk/src/model/v2_session_events200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_message200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_create200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_create200_response_data.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_create_request.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_get200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_permission_reply_request.dart';
import 'package:opencode_sdk/src/model/v2_session_prompt200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_prompt_request.dart';
import 'package:opencode_sdk/src/model/v2_session_question_list200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_revert_stage200_response.dart';
import 'package:opencode_sdk/src/model/v2_session_revert_stage_request.dart';
import 'package:opencode_sdk/src/model/v2_session_switch_agent_request.dart';
import 'package:opencode_sdk/src/model/v2_session_switch_model_request.dart';
import 'package:opencode_sdk/src/model/v2_skill_list200_response.dart';
import 'package:opencode_sdk/src/model/vcs_apply200_response.dart';
import 'package:opencode_sdk/src/model/vcs_apply_error.dart';
import 'package:opencode_sdk/src/model/vcs_apply_error_data.dart';
import 'package:opencode_sdk/src/model/vcs_apply_request.dart';
import 'package:opencode_sdk/src/model/vcs_branch_updated.dart';
import 'package:opencode_sdk/src/model/vcs_branch_updated_data.dart';
import 'package:opencode_sdk/src/model/vcs_file_diff.dart';
import 'package:opencode_sdk/src/model/vcs_file_status.dart';
import 'package:opencode_sdk/src/model/vcs_info.dart';
import 'package:opencode_sdk/src/model/well_known_auth.dart';
import 'package:opencode_sdk/src/model/workspace.dart';
import 'package:opencode_sdk/src/model/workspace_create_error.dart';
import 'package:opencode_sdk/src/model/workspace_event_connection_status.dart';
import 'package:opencode_sdk/src/model/workspace_failed.dart';
import 'package:opencode_sdk/src/model/workspace_ready.dart';
import 'package:opencode_sdk/src/model/workspace_status.dart';
import 'package:opencode_sdk/src/model/workspace_status_data.dart';
import 'package:opencode_sdk/src/model/workspace_warp_error.dart';
import 'package:opencode_sdk/src/model/worktree.dart';
import 'package:opencode_sdk/src/model/worktree_create_input.dart';
import 'package:opencode_sdk/src/model/worktree_error.dart';
import 'package:opencode_sdk/src/model/worktree_failed.dart';
import 'package:opencode_sdk/src/model/worktree_ready.dart';
import 'package:opencode_sdk/src/model/worktree_ready_data.dart';
import 'package:opencode_sdk/src/model/worktree_remove_input.dart';
import 'package:opencode_sdk/src/model/worktree_reset_input.dart';

final _regList = RegExp(r'^List<(.*)>$');
final _regSet = RegExp(r'^Set<(.*)>$');
final _regMap = RegExp(r'^Map<String,(.*)>$');

ReturnType deserialize<ReturnType, BaseType>(
  dynamic value,
  String targetType, {
  bool growable = true,
}) {
  switch (targetType) {
    case 'String':
      return '$value' as ReturnType;
    case 'int':
      return (value is int ? value : int.parse('$value')) as ReturnType;
    case 'bool':
      if (value is bool) {
        return value as ReturnType;
      }
      final valueString = '$value'.toLowerCase();
      return (valueString == 'true' || valueString == '1') as ReturnType;
    case 'double':
      return (value is double ? value : double.parse('$value')) as ReturnType;
    case 'APIError':
      return APIError.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'APIErrorData':
      return APIErrorData.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'Agent':
      return Agent.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'AgentColor':
      return AgentColor.fromJson(value) as ReturnType;
    case 'AgentConfig':
      return AgentConfig.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'AgentPart':
      return AgentPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'AgentPartInput':
      return AgentPartInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AgentPartSource':
      return AgentPartSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AgentV2Info':
      return AgentV2Info.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ApiAuth':
      return ApiAuth.fromJson(value) as ReturnType;
    case 'AppLogRequest':
      return AppLogRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AppSkills200ResponseInner':
      return AppSkills200ResponseInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AssistantMessage':
      return AssistantMessage.fromJson(value) as ReturnType;
    case 'AssistantMessagePath':
      return AssistantMessagePath.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AssistantMessageTime':
      return AssistantMessageTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AssistantMessageTokens':
      return AssistantMessageTokens.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'AttachmentConfig':
      return AttachmentConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Auth':
      return Auth.fromJson(value) as ReturnType;
    case 'BadRequestError':
      return BadRequestError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'BadRequestErrorData':
      return BadRequestErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CatalogUpdated':
      return CatalogUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Command':
      return Command.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'CommandExecuted':
      return CommandExecuted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CommandExecutedData':
      return CommandExecutedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CommandV2Info':
      return CommandV2Info.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CompactionPart':
      return CompactionPart.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Config':
      return Config.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ConfigAgent':
      return ConfigAgent.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ConfigCommandValue':
      return ConfigCommandValue.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigCompaction':
      return ConfigCompaction.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigEnterprise':
      return ConfigEnterprise.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigExperimental':
      return ConfigExperimental.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigMode':
      return ConfigMode.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ConfigProviders200Response':
      return ConfigProviders200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigSkills':
      return ConfigSkills.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ConfigToolOutput':
      return ConfigToolOutput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigV2ExperimentalPolicy':
      return ConfigV2ExperimentalPolicy.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigV2ReferenceGit':
      return ConfigV2ReferenceGit.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigV2ReferenceLocal':
      return ConfigV2ReferenceLocal.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConfigWatcher':
      return ConfigWatcher.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConflictError':
      return ConflictError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConnectionCredentialInfo':
      return ConnectionCredentialInfo.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConnectionEnvInfo':
      return ConnectionEnvInfo.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ConnectionInfo':
      return ConnectionInfo.fromJson(value) as ReturnType;
    case 'ConsoleState':
      return ConsoleState.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ContentFilterError':
      return ContentFilterError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ContextOverflowError':
      return ContextOverflowError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ContextOverflowErrorData':
      return ContextOverflowErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CredentialKey':
      return CredentialKey.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'CredentialOAuth':
      return CredentialOAuth.fromJson(value) as ReturnType;
    case 'CredentialValue':
      return CredentialValue.fromJson(value) as ReturnType;
    case 'EffectHttpApiErrorBadRequest':
      return EffectHttpApiErrorBadRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EffectHttpApiErrorForbidden':
      return EffectHttpApiErrorForbidden.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EffectHttpApiErrorInternalServerError':
      return EffectHttpApiErrorInternalServerError.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'Event':
      return Event.fromJson(value) as ReturnType;
    case 'EventCatalogUpdated':
      return EventCatalogUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventCommandExecuted':
      return EventCommandExecuted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventFileEdited':
      return EventFileEdited.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventFileWatcherUpdated':
      return EventFileWatcherUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventGlobalDisposed':
      return EventGlobalDisposed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventInstallationUpdateAvailable':
      return EventInstallationUpdateAvailable.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventInstallationUpdated':
      return EventInstallationUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventIntegrationConnectionUpdated':
      return EventIntegrationConnectionUpdated.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventIntegrationUpdated':
      return EventIntegrationUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventLspUpdated':
      return EventLspUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMcpBrowserOpenFailed':
      return EventMcpBrowserOpenFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMcpToolsChanged':
      return EventMcpToolsChanged.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMessagePartDelta':
      return EventMessagePartDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMessagePartRemoved':
      return EventMessagePartRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMessagePartUpdated':
      return EventMessagePartUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMessageRemoved':
      return EventMessageRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventMessageUpdated':
      return EventMessageUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventModelsDevRefreshed':
      return EventModelsDevRefreshed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPermissionAsked':
      return EventPermissionAsked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPermissionReplied':
      return EventPermissionReplied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPermissionV2Asked':
      return EventPermissionV2Asked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPermissionV2Replied':
      return EventPermissionV2Replied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPluginAdded':
      return EventPluginAdded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventProjectDirectoriesUpdated':
      return EventProjectDirectoriesUpdated.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventProjectUpdated':
      return EventProjectUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPtyCreated':
      return EventPtyCreated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPtyDeleted':
      return EventPtyDeleted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPtyExited':
      return EventPtyExited.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventPtyUpdated':
      return EventPtyUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionAsked':
      return EventQuestionAsked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionRejected':
      return EventQuestionRejected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionReplied':
      return EventQuestionReplied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionV2Asked':
      return EventQuestionV2Asked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionV2Rejected':
      return EventQuestionV2Rejected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventQuestionV2Replied':
      return EventQuestionV2Replied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventReferenceUpdated':
      return EventReferenceUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventServerConnected':
      return EventServerConnected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventServerInstanceDisposed':
      return EventServerInstanceDisposed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventServerInstanceDisposedProperties':
      return EventServerInstanceDisposedProperties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionCompacted':
      return EventSessionCompacted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionCreated':
      return EventSessionCreated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionDeleted':
      return EventSessionDeleted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionDiff':
      return EventSessionDiff.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionError':
      return EventSessionError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionErrorProperties':
      return EventSessionErrorProperties.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionIdle':
      return EventSessionIdle.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextAgentSwitched':
      return EventSessionNextAgentSwitched.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextCompactionDelta':
      return EventSessionNextCompactionDelta.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextCompactionEnded':
      return EventSessionNextCompactionEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextCompactionStarted':
      return EventSessionNextCompactionStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextContextUpdated':
      return EventSessionNextContextUpdated.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextModelSwitched':
      return EventSessionNextModelSwitched.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextMoved':
      return EventSessionNextMoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextPromptAdmitted':
      return EventSessionNextPromptAdmitted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextPrompted':
      return EventSessionNextPrompted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextReasoningDelta':
      return EventSessionNextReasoningDelta.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextReasoningEnded':
      return EventSessionNextReasoningEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextReasoningStarted':
      return EventSessionNextReasoningStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextRetried':
      return EventSessionNextRetried.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextRevertCleared':
      return EventSessionNextRevertCleared.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextRevertCommitted':
      return EventSessionNextRevertCommitted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextRevertStaged':
      return EventSessionNextRevertStaged.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextShellEnded':
      return EventSessionNextShellEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextShellStarted':
      return EventSessionNextShellStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextStepEnded':
      return EventSessionNextStepEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextStepFailed':
      return EventSessionNextStepFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextStepStarted':
      return EventSessionNextStepStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextSynthetic':
      return EventSessionNextSynthetic.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextTextDelta':
      return EventSessionNextTextDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextTextEnded':
      return EventSessionNextTextEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextTextStarted':
      return EventSessionNextTextStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextToolCalled':
      return EventSessionNextToolCalled.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextToolFailed':
      return EventSessionNextToolFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionNextToolInputDelta':
      return EventSessionNextToolInputDelta.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextToolInputEnded':
      return EventSessionNextToolInputEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextToolInputStarted':
      return EventSessionNextToolInputStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextToolProgress':
      return EventSessionNextToolProgress.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventSessionNextToolSuccess':
      return EventSessionNextToolSuccess.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventSessionStatus':
      return EventSessionStatus.fromJson(value) as ReturnType;
    case 'EventSessionUpdated':
      return EventSessionUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTodoUpdated':
      return EventTodoUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiCommandExecute':
      return EventTuiCommandExecute.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiCommandExecuteProperties':
      return EventTuiCommandExecuteProperties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventTuiCommandExecuteSchema2':
      return EventTuiCommandExecuteSchema2.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventTuiCommandExecuteSchema2Properties':
      return EventTuiCommandExecuteSchema2Properties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventTuiPromptAppend':
      return EventTuiPromptAppend.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiPromptAppendSchema2':
      return EventTuiPromptAppendSchema2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiSessionSelect':
      return EventTuiSessionSelect.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiSessionSelectSchema2':
      return EventTuiSessionSelectSchema2.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventTuiToastShow':
      return EventTuiToastShow.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventTuiToastShowSchema2':
      return EventTuiToastShowSchema2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventVcsBranchUpdated':
      return EventVcsBranchUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventWorkspaceFailed':
      return EventWorkspaceFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventWorkspaceReady':
      return EventWorkspaceReady.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventWorkspaceStatus':
      return EventWorkspaceStatus.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventWorkspaceStatusProperties':
      return EventWorkspaceStatusProperties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'EventWorktreeFailed':
      return EventWorktreeFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'EventWorktreeReady':
      return EventWorktreeReady.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ExperimentalCapabilities':
      return ExperimentalCapabilities.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ExperimentalConsoleListOrgs200Response':
      return ExperimentalConsoleListOrgs200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalConsoleListOrgs200ResponseOrgsInner':
      return ExperimentalConsoleListOrgs200ResponseOrgsInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalConsoleSwitchOrgRequest':
      return ExperimentalConsoleSwitchOrgRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalControlPlaneMoveSessionRequest':
      return ExperimentalControlPlaneMoveSessionRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalProjectCopyGenerateName200Response':
      return ExperimentalProjectCopyGenerateName200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalProjectCopyGenerateNameRequest':
      return ExperimentalProjectCopyGenerateNameRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalWorkspaceAdapterList200ResponseInner':
      return ExperimentalWorkspaceAdapterList200ResponseInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalWorkspaceCreateRequest':
      return ExperimentalWorkspaceCreateRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ExperimentalWorkspaceWarpRequest':
      return ExperimentalWorkspaceWarpRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'File':
      return File.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FileContent':
      return FileContent.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FileContentPatch':
      return FileContentPatch.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileContentPatchHunksInner':
      return FileContentPatchHunksInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileDiff':
      return FileDiff.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FileEdited':
      return FileEdited.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FileEditedData':
      return FileEditedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileNode':
      return FileNode.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FilePart':
      return FilePart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FilePartInput':
      return FilePartInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FilePartSource':
      return FilePartSource.fromJson(value) as ReturnType;
    case 'FilePartSourceText':
      return FilePartSourceText.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileSource':
      return FileSource.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'FileSystemEntry':
      return FileSystemEntry.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileWatcherUpdated':
      return FileWatcherUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FileWatcherUpdatedData':
      return FileWatcherUpdatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FindText200ResponseInner':
      return FindText200ResponseInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FindText200ResponseInnerPath':
      return FindText200ResponseInnerPath.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'FindText200ResponseInnerSubmatchesInner':
      return FindText200ResponseInnerSubmatchesInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ForbiddenError':
      return ForbiddenError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'FormatterStatus':
      return FormatterStatus.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'GlobalDisposed':
      return GlobalDisposed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'GlobalEvent':
      return GlobalEvent.fromJson(value) as ReturnType;
    case 'GlobalHealth200Response':
      return GlobalHealth200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'GlobalSession':
      return GlobalSession.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'GlobalUpgradeRequest':
      return GlobalUpgradeRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ImageAttachmentConfig':
      return ImageAttachmentConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'InstallationUpdateAvailable':
      return InstallationUpdateAvailable.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'InstallationUpdated':
      return InstallationUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'InstallationUpdatedData':
      return InstallationUpdatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationAttempt':
      return IntegrationAttempt.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationAttemptStatus':
      return IntegrationAttemptStatus.fromJson(value) as ReturnType;
    case 'IntegrationAttemptStatusAnyOf':
      return IntegrationAttemptStatusAnyOf.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf1':
      return IntegrationAttemptStatusAnyOf1.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf1Time':
      return IntegrationAttemptStatusAnyOf1Time.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf2':
      return IntegrationAttemptStatusAnyOf2.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf2Time':
      return IntegrationAttemptStatusAnyOf2Time.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf3':
      return IntegrationAttemptStatusAnyOf3.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOf3Time':
      return IntegrationAttemptStatusAnyOf3Time.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptStatusAnyOfTime':
      return IntegrationAttemptStatusAnyOfTime.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationAttemptTime':
      return IntegrationAttemptTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationConnectionUpdated':
      return IntegrationConnectionUpdated.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationConnectionUpdatedData':
      return IntegrationConnectionUpdatedData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationEnvMethod':
      return IntegrationEnvMethod.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationInfo':
      return IntegrationInfo.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationKeyMethod':
      return IntegrationKeyMethod.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationMethod':
      return IntegrationMethod.fromJson(value) as ReturnType;
    case 'IntegrationOAuthMethod':
      return IntegrationOAuthMethod.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationRef':
      return IntegrationRef.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationSelectPrompt':
      return IntegrationSelectPrompt.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationSelectPromptOptionsInner':
      return IntegrationSelectPromptOptionsInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'IntegrationTextPrompt':
      return IntegrationTextPrompt.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationUpdated':
      return IntegrationUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'IntegrationWhen':
      return IntegrationWhen.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'InvalidCursorError':
      return InvalidCursorError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'InvalidRequestError':
      return InvalidRequestError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'LLMToolContent':
      return LLMToolContent.fromJson(value) as ReturnType;
    case 'LSPStatus':
      return LSPStatus.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'LayoutConfig':
    case 'LocationInfo':
      return LocationInfo.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'LocationInfoProject':
      return LocationInfoProject.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'LocationRef':
      return LocationRef.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'LogLevel':
    case 'LspUpdated':
      return LspUpdated.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'MCPStatus':
      return MCPStatus.fromJson(value) as ReturnType;
    case 'MCPStatusConnected':
      return MCPStatusConnected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MCPStatusDisabled':
      return MCPStatusDisabled.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MCPStatusFailed':
      return MCPStatusFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MCPStatusNeedsAuth':
      return MCPStatusNeedsAuth.fromJson(value) as ReturnType;
    case 'MCPStatusNeedsClientRegistration':
      return MCPStatusNeedsClientRegistration.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'McpAddRequest':
      return McpAddRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpAuthCallbackRequest':
      return McpAuthCallbackRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpAuthRemove200Response':
      return McpAuthRemove200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpAuthStart200Response':
      return McpAuthStart200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpBrowserOpenFailed':
      return McpBrowserOpenFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpBrowserOpenFailedData':
      return McpBrowserOpenFailedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpLocalConfig':
      return McpLocalConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpOAuthConfig':
      return McpOAuthConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpRemoteConfig':
      return McpRemoteConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpResource':
      return McpResource.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'McpServerNotFoundError':
      return McpServerNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpToolsChanged':
      return McpToolsChanged.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpToolsChangedData':
      return McpToolsChangedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'McpUnsupportedOAuthError':
      return McpUnsupportedOAuthError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Message':
      return Message.fromJson(value) as ReturnType;
    case 'MessageAbortedError':
      return MessageAbortedError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessageNotFoundError':
      return MessageNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessageOutputLengthError':
      return MessageOutputLengthError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessagePartDelta':
      return MessagePartDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessagePartDeltaData':
      return MessagePartDeltaData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessagePartRemoved':
      return MessagePartRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessagePartUpdated':
      return MessagePartUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessageRemoved':
      return MessageRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MessageUpdated':
      return MessageUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Model':
      return Model.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ModelApi':
      return ModelApi.fromJson(value) as ReturnType;
    case 'ModelApiAnyOf':
      return ModelApiAnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelApiAnyOf1':
      return ModelApiAnyOf1.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelCapabilities':
      return ModelCapabilities.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelCapabilitiesInput':
      return ModelCapabilitiesInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelCost':
      return ModelCost.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ModelCostExperimentalOver200K':
      return ModelCostExperimentalOver200K.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ModelCostTier':
      return ModelCostTier.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelCostTiersInner':
      return ModelCostTiersInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelCostTiersInnerTier':
      return ModelCostTiersInnerTier.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelPart':
      return ModelPart.fromJson(value) as ReturnType;
    case 'ModelRef':
      return ModelRef.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ModelV2Capabilities':
      return ModelV2Capabilities.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelV2Info':
      return ModelV2Info.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ModelV2InfoLimit':
      return ModelV2InfoLimit.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelV2InfoRequest':
      return ModelV2InfoRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelV2InfoTime':
      return ModelV2InfoTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelV2InfoVariantsInner':
      return ModelV2InfoVariantsInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ModelsDevRefreshed':
      return ModelsDevRefreshed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MoveSessionDestination':
      return MoveSessionDestination.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MoveSessionError':
      return MoveSessionError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'MoveSessionErrorData':
      return MoveSessionErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'NotFoundError':
      return NotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'NotFoundErrorData':
      return NotFoundErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OAuth':
      return OAuth.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion001':
      return OpencodeSdkRawUnion001.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion002':
      return OpencodeSdkRawUnion002.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf':
      return OpencodeSdkRawUnion002AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf1':
      return OpencodeSdkRawUnion002AnyOf1.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf10':
      return OpencodeSdkRawUnion002AnyOf10.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf11':
      return OpencodeSdkRawUnion002AnyOf11.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf12':
      return OpencodeSdkRawUnion002AnyOf12.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf13':
      return OpencodeSdkRawUnion002AnyOf13.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf14':
      return OpencodeSdkRawUnion002AnyOf14.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf15':
      return OpencodeSdkRawUnion002AnyOf15.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf16':
      return OpencodeSdkRawUnion002AnyOf16.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf17':
      return OpencodeSdkRawUnion002AnyOf17.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf18':
      return OpencodeSdkRawUnion002AnyOf18.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf19':
      return OpencodeSdkRawUnion002AnyOf19.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf2':
      return OpencodeSdkRawUnion002AnyOf2.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf20':
      return OpencodeSdkRawUnion002AnyOf20.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf21':
      return OpencodeSdkRawUnion002AnyOf21.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf22':
      return OpencodeSdkRawUnion002AnyOf22.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf23':
      return OpencodeSdkRawUnion002AnyOf23.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf24':
      return OpencodeSdkRawUnion002AnyOf24.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf25':
      return OpencodeSdkRawUnion002AnyOf25.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf26':
      return OpencodeSdkRawUnion002AnyOf26.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf27':
      return OpencodeSdkRawUnion002AnyOf27.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf28':
      return OpencodeSdkRawUnion002AnyOf28.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf29':
      return OpencodeSdkRawUnion002AnyOf29.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf3':
      return OpencodeSdkRawUnion002AnyOf3.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf30':
      return OpencodeSdkRawUnion002AnyOf30.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf31':
      return OpencodeSdkRawUnion002AnyOf31.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf32':
      return OpencodeSdkRawUnion002AnyOf32.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf33':
      return OpencodeSdkRawUnion002AnyOf33.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf34':
      return OpencodeSdkRawUnion002AnyOf34.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf35':
      return OpencodeSdkRawUnion002AnyOf35.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf36':
      return OpencodeSdkRawUnion002AnyOf36.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf37':
      return OpencodeSdkRawUnion002AnyOf37.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf38':
      return OpencodeSdkRawUnion002AnyOf38.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf39':
      return OpencodeSdkRawUnion002AnyOf39.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf4':
      return OpencodeSdkRawUnion002AnyOf4.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf40':
      return OpencodeSdkRawUnion002AnyOf40.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf41':
      return OpencodeSdkRawUnion002AnyOf41.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf42':
      return OpencodeSdkRawUnion002AnyOf42.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf43':
      return OpencodeSdkRawUnion002AnyOf43.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf44':
      return OpencodeSdkRawUnion002AnyOf44.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf45':
      return OpencodeSdkRawUnion002AnyOf45.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf45Properties':
      return OpencodeSdkRawUnion002AnyOf45Properties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf45PropertiesError':
      return OpencodeSdkRawUnion002AnyOf45PropertiesError.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf46':
      return OpencodeSdkRawUnion002AnyOf46.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf47':
      return OpencodeSdkRawUnion002AnyOf47.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf48':
      return OpencodeSdkRawUnion002AnyOf48.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf49':
      return OpencodeSdkRawUnion002AnyOf49.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf5':
      return OpencodeSdkRawUnion002AnyOf5.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf50':
      return OpencodeSdkRawUnion002AnyOf50.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf51':
      return OpencodeSdkRawUnion002AnyOf51.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf52':
      return OpencodeSdkRawUnion002AnyOf52.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf53':
      return OpencodeSdkRawUnion002AnyOf53.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf54':
      return OpencodeSdkRawUnion002AnyOf54.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf55':
      return OpencodeSdkRawUnion002AnyOf55.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf56':
      return OpencodeSdkRawUnion002AnyOf56.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf57':
      return OpencodeSdkRawUnion002AnyOf57.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf58':
      return OpencodeSdkRawUnion002AnyOf58.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf59':
      return OpencodeSdkRawUnion002AnyOf59.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf6':
      return OpencodeSdkRawUnion002AnyOf6.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf60':
      return OpencodeSdkRawUnion002AnyOf60.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf61':
      return OpencodeSdkRawUnion002AnyOf61.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf62':
      return OpencodeSdkRawUnion002AnyOf62.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf63':
      return OpencodeSdkRawUnion002AnyOf63.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf64':
      return OpencodeSdkRawUnion002AnyOf64.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf65':
      return OpencodeSdkRawUnion002AnyOf65.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf66':
      return OpencodeSdkRawUnion002AnyOf66.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf67':
      return OpencodeSdkRawUnion002AnyOf67.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf67Properties':
      return OpencodeSdkRawUnion002AnyOf67Properties.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf67PropertiesCommand':
      return OpencodeSdkRawUnion002AnyOf67PropertiesCommand.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf68':
      return OpencodeSdkRawUnion002AnyOf68.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf69':
      return OpencodeSdkRawUnion002AnyOf69.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf7':
      return OpencodeSdkRawUnion002AnyOf7.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf70':
      return OpencodeSdkRawUnion002AnyOf70.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf71':
      return OpencodeSdkRawUnion002AnyOf71.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf72':
      return OpencodeSdkRawUnion002AnyOf72.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf73':
      return OpencodeSdkRawUnion002AnyOf73.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf74':
      return OpencodeSdkRawUnion002AnyOf74.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf75':
      return OpencodeSdkRawUnion002AnyOf75.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf76':
      return OpencodeSdkRawUnion002AnyOf76.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf77':
      return OpencodeSdkRawUnion002AnyOf77.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf78':
      return OpencodeSdkRawUnion002AnyOf78.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf79':
      return OpencodeSdkRawUnion002AnyOf79.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf8':
      return OpencodeSdkRawUnion002AnyOf8.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf80':
      return OpencodeSdkRawUnion002AnyOf80.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf81':
      return OpencodeSdkRawUnion002AnyOf81.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf82':
      return OpencodeSdkRawUnion002AnyOf82.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf83':
      return OpencodeSdkRawUnion002AnyOf83.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf84':
      return OpencodeSdkRawUnion002AnyOf84.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf85':
      return OpencodeSdkRawUnion002AnyOf85.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf86':
      return OpencodeSdkRawUnion002AnyOf86.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf87':
      return OpencodeSdkRawUnion002AnyOf87.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion002AnyOf9':
      return OpencodeSdkRawUnion002AnyOf9.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion003':
      return OpencodeSdkRawUnion003.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion004':
      return OpencodeSdkRawUnion004.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion005':
      return OpencodeSdkRawUnion005.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion006':
      return OpencodeSdkRawUnion006.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion006AnyOf':
      return OpencodeSdkRawUnion006AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OpencodeSdkRawUnion006AnyOfField':
      return OpencodeSdkRawUnion006AnyOfField.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion007':
      return OpencodeSdkRawUnion007.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion008':
      return OpencodeSdkRawUnion008.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion009':
      return OpencodeSdkRawUnion009.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion010':
      return OpencodeSdkRawUnion010.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion011':
      return OpencodeSdkRawUnion011.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion012':
      return OpencodeSdkRawUnion012.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion012AnyOf':
      return OpencodeSdkRawUnion012AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OpencodeSdkRawUnion013':
      return OpencodeSdkRawUnion013.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion013AnyOfValue':
      return OpencodeSdkRawUnion013AnyOfValue.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion014':
      return OpencodeSdkRawUnion014.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion014AnyOfValue':
      return OpencodeSdkRawUnion014AnyOfValue.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion014AnyOfValueAnyOf':
      return OpencodeSdkRawUnion014AnyOfValueAnyOf.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion014AnyOfValueAnyOf1':
      return OpencodeSdkRawUnion014AnyOfValueAnyOf1.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion015':
      return OpencodeSdkRawUnion015.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion016':
      return OpencodeSdkRawUnion016.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion016AnyOf':
      return OpencodeSdkRawUnion016AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OpencodeSdkRawUnion016AnyOf1':
      return OpencodeSdkRawUnion016AnyOf1.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion016AnyOfWhen':
      return OpencodeSdkRawUnion016AnyOfWhen.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion017':
      return OpencodeSdkRawUnion017.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion018':
      return OpencodeSdkRawUnion018.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion019':
      return OpencodeSdkRawUnion019.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion020':
      return OpencodeSdkRawUnion020.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion021':
      return OpencodeSdkRawUnion021.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion022':
      return OpencodeSdkRawUnion022.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion023':
      return OpencodeSdkRawUnion023.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion024':
      return OpencodeSdkRawUnion024.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion025':
      return OpencodeSdkRawUnion025.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion026':
      return OpencodeSdkRawUnion026.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion027':
      return OpencodeSdkRawUnion027.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion028':
      return OpencodeSdkRawUnion028.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion029':
      return OpencodeSdkRawUnion029.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion030':
      return OpencodeSdkRawUnion030.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion031':
      return OpencodeSdkRawUnion031.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion032':
      return OpencodeSdkRawUnion032.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion033':
      return OpencodeSdkRawUnion033.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion034':
      return OpencodeSdkRawUnion034.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion035':
      return OpencodeSdkRawUnion035.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion036':
      return OpencodeSdkRawUnion036.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion037':
      return OpencodeSdkRawUnion037.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion038':
      return OpencodeSdkRawUnion038.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion039':
      return OpencodeSdkRawUnion039.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion040':
      return OpencodeSdkRawUnion040.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion041':
      return OpencodeSdkRawUnion041.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion041AnyOf':
      return OpencodeSdkRawUnion041AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OpencodeSdkRawUnion041AnyOf1':
      return OpencodeSdkRawUnion041AnyOf1.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'OpencodeSdkRawUnion042':
      return OpencodeSdkRawUnion042.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion043':
      return OpencodeSdkRawUnion043.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion044':
      return OpencodeSdkRawUnion044.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion045':
      return OpencodeSdkRawUnion045.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion046':
      return OpencodeSdkRawUnion046.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion047':
      return OpencodeSdkRawUnion047.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion048':
      return OpencodeSdkRawUnion048.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion049':
      return OpencodeSdkRawUnion049.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion050':
      return OpencodeSdkRawUnion050.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion051':
      return OpencodeSdkRawUnion051.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion052':
      return OpencodeSdkRawUnion052.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion053':
      return OpencodeSdkRawUnion053.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion054':
      return OpencodeSdkRawUnion054.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion055':
      return OpencodeSdkRawUnion055.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion056':
      return OpencodeSdkRawUnion056.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion057':
      return OpencodeSdkRawUnion057.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion058':
      return OpencodeSdkRawUnion058.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion059':
      return OpencodeSdkRawUnion059.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion060':
      return OpencodeSdkRawUnion060.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion061':
      return OpencodeSdkRawUnion061.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion062':
      return OpencodeSdkRawUnion062.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion063':
      return OpencodeSdkRawUnion063.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion064':
      return OpencodeSdkRawUnion064.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion065':
      return OpencodeSdkRawUnion065.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion066':
      return OpencodeSdkRawUnion066.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion067':
      return OpencodeSdkRawUnion067.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion068':
      return OpencodeSdkRawUnion068.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion069':
      return OpencodeSdkRawUnion069.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion070':
      return OpencodeSdkRawUnion070.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion071':
      return OpencodeSdkRawUnion071.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion072':
      return OpencodeSdkRawUnion072.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion073':
      return OpencodeSdkRawUnion073.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion074':
      return OpencodeSdkRawUnion074.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion075':
      return OpencodeSdkRawUnion075.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion076':
      return OpencodeSdkRawUnion076.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion077':
      return OpencodeSdkRawUnion077.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion078':
      return OpencodeSdkRawUnion078.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion079':
      return OpencodeSdkRawUnion079.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion080':
      return OpencodeSdkRawUnion080.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion081':
      return OpencodeSdkRawUnion081.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion082':
      return OpencodeSdkRawUnion082.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion083':
      return OpencodeSdkRawUnion083.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion084':
      return OpencodeSdkRawUnion084.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion085':
      return OpencodeSdkRawUnion085.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion086':
      return OpencodeSdkRawUnion086.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion087':
      return OpencodeSdkRawUnion087.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion088':
      return OpencodeSdkRawUnion088.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion089':
      return OpencodeSdkRawUnion089.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion090':
      return OpencodeSdkRawUnion090.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion091':
      return OpencodeSdkRawUnion091.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion092':
      return OpencodeSdkRawUnion092.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion093':
      return OpencodeSdkRawUnion093.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion094':
      return OpencodeSdkRawUnion094.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion095':
      return OpencodeSdkRawUnion095.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion096':
      return OpencodeSdkRawUnion096.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion097':
      return OpencodeSdkRawUnion097.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion098':
      return OpencodeSdkRawUnion098.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion099':
      return OpencodeSdkRawUnion099.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion100':
      return OpencodeSdkRawUnion100.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion101':
      return OpencodeSdkRawUnion101.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion102':
      return OpencodeSdkRawUnion102.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion103':
      return OpencodeSdkRawUnion103.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion104':
      return OpencodeSdkRawUnion104.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion105':
      return OpencodeSdkRawUnion105.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion106':
      return OpencodeSdkRawUnion106.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion107':
      return OpencodeSdkRawUnion107.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion108':
      return OpencodeSdkRawUnion108.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion109':
      return OpencodeSdkRawUnion109.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion110':
      return OpencodeSdkRawUnion110.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion111':
      return OpencodeSdkRawUnion111.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion112':
      return OpencodeSdkRawUnion112.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion113':
      return OpencodeSdkRawUnion113.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion114':
      return OpencodeSdkRawUnion114.fromJson(value) as ReturnType;
    case 'OpencodeSdkRawUnion115':
      return OpencodeSdkRawUnion115.fromJson(value) as ReturnType;
    case 'OutputFormat':
      return OutputFormat.fromJson(value) as ReturnType;
    case 'OutputFormat1':
      return OutputFormat1.fromJson(value) as ReturnType;
    case 'OutputFormat1AnyOf':
      return OutputFormat1AnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OutputFormat1AnyOf1':
      return OutputFormat1AnyOf1.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OutputFormatJsonSchema':
      return OutputFormatJsonSchema.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'OutputFormatText':
      return OutputFormatText.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PatchPart':
      return PatchPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'Path':
      return Path.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PermissionAction':
    case 'PermissionActionConfig':
    case 'PermissionAsked':
      return PermissionAsked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionAskedData':
      return PermissionAskedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionConfig':
      return PermissionConfig.fromJson(value) as ReturnType;
    case 'PermissionConfigAnyOf':
      return PermissionConfigAnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionNotFoundError':
      return PermissionNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionReplied':
      return PermissionReplied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRepliedData':
      return PermissionRepliedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionReplyRequest':
      return PermissionReplyRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRequest':
      return PermissionRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRequestTool':
      return PermissionRequestTool.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRespondRequest':
      return PermissionRespondRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRule':
      return PermissionRule.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionRuleConfig':
      return PermissionRuleConfig.fromJson(value) as ReturnType;
    case 'PermissionSavedInfo':
      return PermissionSavedInfo.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2Asked':
      return PermissionV2Asked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2AskedData':
      return PermissionV2AskedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2Effect':
    case 'PermissionV2Replied':
      return PermissionV2Replied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2RepliedData':
      return PermissionV2RepliedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2Reply':
    case 'PermissionV2Request':
      return PermissionV2Request.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2Rule':
      return PermissionV2Rule.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PermissionV2Source':
      return PermissionV2Source.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PluginAdded':
      return PluginAdded.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PluginAddedData':
      return PluginAddedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PolicyEffect':
    case 'Project':
      return Project.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ProjectCommands':
      return ProjectCommands.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectCopyCopy':
      return ProjectCopyCopy.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectCopyError':
      return ProjectCopyError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectCopyErrorData':
      return ProjectCopyErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectDirectoriesInner':
      return ProjectDirectoriesInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectDirectoriesUpdated':
      return ProjectDirectoriesUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectDirectoriesUpdatedData':
      return ProjectDirectoriesUpdatedData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProjectIcon':
      return ProjectIcon.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ProjectNotFoundError':
      return ProjectNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectSummary':
      return ProjectSummary.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectTime':
      return ProjectTime.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ProjectUpdateRequest':
      return ProjectUpdateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectUpdated':
      return ProjectUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectUpdatedData':
      return ProjectUpdatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProjectVcs':
    case 'Prompt':
      return Prompt.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PromptAgentAttachment':
      return PromptAgentAttachment.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PromptFileAttachment':
      return PromptFileAttachment.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PromptInput':
      return PromptInput.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PromptInputFileAttachment':
      return PromptInputFileAttachment.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PromptSource':
      return PromptSource.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'Provider':
      return Provider.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ProviderAISDK':
      return ProviderAISDK.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderApiModel':
      return ProviderApiModel.fromJson(value) as ReturnType;
    case 'ProviderAuthAuthorization':
      return ProviderAuthAuthorization.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderAuthError':
      return ProviderAuthError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderAuthError1':
      return ProviderAuthError1.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderAuthError1Data':
      return ProviderAuthError1Data.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderAuthErrorData':
      return ProviderAuthErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderAuthMethod':
      return ProviderAuthMethod.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderConfig':
      return ProviderConfig.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderConfigModelsValue':
      return ProviderConfigModelsValue.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderConfigModelsValueCost':
      return ProviderConfigModelsValueCost.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigModelsValueCostContextOver200k':
      return ProviderConfigModelsValueCostContextOver200k.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigModelsValueLimit':
      return ProviderConfigModelsValueLimit.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigModelsValueModalities':
      return ProviderConfigModelsValueModalities.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigModelsValueProvider':
      return ProviderConfigModelsValueProvider.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigModelsValueVariantsValue':
      return ProviderConfigModelsValueVariantsValue.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderConfigOptions':
      return ProviderConfigOptions.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderList200Response':
      return ProviderList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderNative':
      return ProviderNative.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderNotFoundError':
      return ProviderNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderOauthAuthorizeRequest':
      return ProviderOauthAuthorizeRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderOauthCallbackRequest':
      return ProviderOauthCallbackRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'ProviderRequest':
      return ProviderRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ProviderV2Info':
      return ProviderV2Info.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Pty':
      return Pty.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PtyCreateRequest':
      return PtyCreateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyCreated':
      return PtyCreated.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PtyCreatedData':
      return PtyCreatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyDeleted':
      return PtyDeleted.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PtyDeletedData':
      return PtyDeletedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyExited':
      return PtyExited.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'PtyExitedData':
      return PtyExitedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyForbiddenError':
      return PtyForbiddenError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyNotFoundError':
      return PtyNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyShells200ResponseInner':
      return PtyShells200ResponseInner.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyTicketConnectToken':
      return PtyTicketConnectToken.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyUpdateRequest':
      return PtyUpdateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyUpdateRequestSize':
      return PtyUpdateRequestSize.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'PtyUpdated':
      return PtyUpdated.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'QuestionAsked':
      return QuestionAsked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionAskedData':
      return QuestionAskedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionInfo':
      return QuestionInfo.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'QuestionNotFoundError':
      return QuestionNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionOption':
      return QuestionOption.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRejected':
      return QuestionRejected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRejectedSchema2':
      return QuestionRejectedSchema2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRejectedSchema2Data':
      return QuestionRejectedSchema2Data.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionReplied':
      return QuestionReplied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRepliedSchema2':
      return QuestionRepliedSchema2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRepliedSchema2Data':
      return QuestionRepliedSchema2Data.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionReplyRequest':
      return QuestionReplyRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionRequest':
      return QuestionRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionTool':
      return QuestionTool.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'QuestionV2Asked':
      return QuestionV2Asked.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2AskedData':
      return QuestionV2AskedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Info':
      return QuestionV2Info.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Option':
      return QuestionV2Option.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Rejected':
      return QuestionV2Rejected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Replied':
      return QuestionV2Replied.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2RepliedData':
      return QuestionV2RepliedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Reply':
      return QuestionV2Reply.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Request':
      return QuestionV2Request.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'QuestionV2Tool':
      return QuestionV2Tool.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Range':
      return Range.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'RangeStart':
      return RangeStart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ReasoningPart':
      return ReasoningPart.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ReferenceGitSource':
      return ReferenceGitSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ReferenceInfo':
      return ReferenceInfo.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ReferenceLocalSource':
      return ReferenceLocalSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ReferenceSource':
      return ReferenceSource.fromJson(value) as ReturnType;
    case 'ReferenceUpdated':
      return ReferenceUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ResourceSource':
      return ResourceSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'RetryPart':
      return RetryPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'RetryPartTime':
      return RetryPartTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'RevertState':
      return RevertState.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ServerConfig':
      return ServerConfig.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ServerConnected':
      return ServerConnected.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ServiceUnavailableError':
      return ServiceUnavailableError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Session':
      return Session.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionActive':
      return SessionActive.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionBusyError':
      return SessionBusyError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionCommandRequest':
      return SessionCommandRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionCommandRequestPartsInner':
      return SessionCommandRequestPartsInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionCompacted':
      return SessionCompacted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionCreateRequest':
      return SessionCreateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionCreateRequestModel':
      return SessionCreateRequestModel.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionCreated':
      return SessionCreated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionDeleted':
      return SessionDeleted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionDiff':
      return SessionDiff.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionDiffData':
      return SessionDiffData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionDurableEvent':
      return SessionDurableEvent.fromJson(value) as ReturnType;
    case 'SessionError':
      return SessionError.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionErrorData':
      return SessionErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionErrorUnknown':
      return SessionErrorUnknown.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionForkRequest':
      return SessionForkRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionHistory':
      return SessionHistory.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionIdle':
      return SessionIdle.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionInitRequest':
      return SessionInitRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionInputAdmitted':
      return SessionInputAdmitted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessage':
      return SessionMessage.fromJson(value) as ReturnType;
    case 'SessionMessage200Response':
      return SessionMessage200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageAgentSwitched':
      return SessionMessageAgentSwitched.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageAgentSwitchedTime':
      return SessionMessageAgentSwitchedTime.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageAssistant':
      return SessionMessageAssistant.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageAssistantReasoning':
      return SessionMessageAssistantReasoning.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageAssistantSnapshot':
      return SessionMessageAssistantSnapshot.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageAssistantText':
      return SessionMessageAssistantText.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageAssistantTool':
      return SessionMessageAssistantTool.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageAssistantToolProvider':
      return SessionMessageAssistantToolProvider.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageAssistantToolTime':
      return SessionMessageAssistantToolTime.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageCompaction':
      return SessionMessageCompaction.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageModelSwitched':
      return SessionMessageModelSwitched.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageShell':
      return SessionMessageShell.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageShellTime':
      return SessionMessageShellTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageSynthetic':
      return SessionMessageSynthetic.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageSystem':
      return SessionMessageSystem.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessageToolStateCompleted':
      return SessionMessageToolStateCompleted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageToolStateError':
      return SessionMessageToolStateError.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageToolStatePending':
      return SessionMessageToolStatePending.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageToolStateRunning':
      return SessionMessageToolStateRunning.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessageUser':
      return SessionMessageUser.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionMessages200ResponseInner':
      return SessionMessages200ResponseInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionMessagesResponse':
      return SessionMessagesResponse.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextAgentSwitched':
      return SessionNextAgentSwitched.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextCompactionDelta':
      return SessionNextCompactionDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextCompactionEnded':
      return SessionNextCompactionEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextCompactionStarted':
      return SessionNextCompactionStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionNextContextUpdated':
      return SessionNextContextUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextModelSwitched':
      return SessionNextModelSwitched.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextMoved':
      return SessionNextMoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextPromptAdmitted':
      return SessionNextPromptAdmitted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextPrompted':
      return SessionNextPrompted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextReasoningDelta':
      return SessionNextReasoningDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextReasoningDeltaData':
      return SessionNextReasoningDeltaData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionNextReasoningEnded':
      return SessionNextReasoningEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextReasoningStarted':
      return SessionNextReasoningStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextRetried':
      return SessionNextRetried.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextRetryError':
      return SessionNextRetryError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextRevertCleared':
      return SessionNextRevertCleared.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextRevertCommitted':
      return SessionNextRevertCommitted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextRevertStaged':
      return SessionNextRevertStaged.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextShellEnded':
      return SessionNextShellEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextShellStarted':
      return SessionNextShellStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextStepEnded':
      return SessionNextStepEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextStepFailed':
      return SessionNextStepFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextStepStarted':
      return SessionNextStepStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextSynthetic':
      return SessionNextSynthetic.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextTextDelta':
      return SessionNextTextDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextTextDeltaData':
      return SessionNextTextDeltaData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextTextEnded':
      return SessionNextTextEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextTextStarted':
      return SessionNextTextStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolCalled':
      return SessionNextToolCalled.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolFailed':
      return SessionNextToolFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolInputDelta':
      return SessionNextToolInputDelta.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolInputDeltaData':
      return SessionNextToolInputDeltaData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionNextToolInputEnded':
      return SessionNextToolInputEnded.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolInputStarted':
      return SessionNextToolInputStarted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolProgress':
      return SessionNextToolProgress.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNextToolSuccess':
      return SessionNextToolSuccess.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionNotFoundError':
      return SessionNotFoundError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionPrompt200Response':
      return SessionPrompt200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionPromptAsyncRequest':
      return SessionPromptAsyncRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionPromptAsyncRequestModel':
      return SessionPromptAsyncRequestModel.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SessionPromptRequest':
      return SessionPromptRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionPromptRequestModel':
      return SessionPromptRequestModel.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionRevert':
      return SessionRevert.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionRevertRequest':
      return SessionRevertRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionShare':
      return SessionShare.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionShell200Response':
      return SessionShell200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionShellRequest':
      return SessionShellRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatus':
      return SessionStatus.fromJson(value) as ReturnType;
    case 'SessionStatusAnyOf':
      return SessionStatusAnyOf.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusAnyOf1':
      return SessionStatusAnyOf1.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusAnyOf1Action':
      return SessionStatusAnyOf1Action.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusAnyOf2':
      return SessionStatusAnyOf2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusSchema2':
      return SessionStatusSchema2.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusSchema2Data':
      return SessionStatusSchema2Data.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionStatusSchema2Durable':
      return SessionStatusSchema2Durable.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionSummarizeRequest':
      return SessionSummarizeRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionSummary':
      return SessionSummary.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionTime':
      return SessionTime.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SessionTokens':
      return SessionTokens.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionTokensCache':
      return SessionTokensCache.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionUpdateRequest':
      return SessionUpdateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionUpdateRequestTime':
      return SessionUpdateRequestTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionUpdated':
      return SessionUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionV2Info':
      return SessionV2Info.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionV2InfoTime':
      return SessionV2InfoTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionsResponse':
      return SessionsResponse.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SessionsResponseCursor':
      return SessionsResponseCursor.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SkillV2DirectorySource':
      return SkillV2DirectorySource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SkillV2EmbeddedSource':
      return SkillV2EmbeddedSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SkillV2Info':
      return SkillV2Info.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SkillV2Source':
      return SkillV2Source.fromJson(value) as ReturnType;
    case 'SkillV2UrlSource':
      return SkillV2UrlSource.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SnapshotFileDiff':
      return SnapshotFileDiff.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SnapshotPart':
      return SnapshotPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'StepFinishPart':
      return StepFinishPart.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'StepStartPart':
      return StepStartPart.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'StructuredOutputError':
      return StructuredOutputError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'StructuredOutputErrorData':
      return StructuredOutputErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SubtaskPart':
      return SubtaskPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SubtaskPartInput':
      return SubtaskPartInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Symbol':
      return Symbol.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SymbolLocation':
      return SymbolLocation.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SymbolSource':
      return SymbolSource.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'SyncEventMessagePartRemoved':
      return SyncEventMessagePartRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventMessagePartRemovedSyncEvent':
      return SyncEventMessagePartRemovedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventMessagePartRemovedSyncEventData':
      return SyncEventMessagePartRemovedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventMessagePartUpdated':
      return SyncEventMessagePartUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventMessagePartUpdatedSyncEvent':
      return SyncEventMessagePartUpdatedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventMessagePartUpdatedSyncEventData':
      return SyncEventMessagePartUpdatedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventMessageRemoved':
      return SyncEventMessageRemoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventMessageRemovedSyncEvent':
      return SyncEventMessageRemovedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventMessageRemovedSyncEventData':
      return SyncEventMessageRemovedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventMessageUpdated':
      return SyncEventMessageUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventMessageUpdatedSyncEvent':
      return SyncEventMessageUpdatedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventMessageUpdatedSyncEventData':
      return SyncEventMessageUpdatedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionCreated':
      return SyncEventSessionCreated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventSessionCreatedSyncEvent':
      return SyncEventSessionCreatedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventSessionCreatedSyncEventData':
      return SyncEventSessionCreatedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionDeleted':
      return SyncEventSessionDeleted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventSessionDeletedSyncEvent':
      return SyncEventSessionDeletedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventSessionNextAgentSwitched':
      return SyncEventSessionNextAgentSwitched.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextAgentSwitchedSyncEvent':
      return SyncEventSessionNextAgentSwitchedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextAgentSwitchedSyncEventData':
      return SyncEventSessionNextAgentSwitchedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextCompactionEnded':
      return SyncEventSessionNextCompactionEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextCompactionEndedSyncEvent':
      return SyncEventSessionNextCompactionEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextCompactionEndedSyncEventData':
      return SyncEventSessionNextCompactionEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextCompactionStarted':
      return SyncEventSessionNextCompactionStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextCompactionStartedSyncEvent':
      return SyncEventSessionNextCompactionStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextCompactionStartedSyncEventData':
      return SyncEventSessionNextCompactionStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextContextUpdated':
      return SyncEventSessionNextContextUpdated.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextContextUpdatedSyncEvent':
      return SyncEventSessionNextContextUpdatedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextContextUpdatedSyncEventData':
      return SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextModelSwitched':
      return SyncEventSessionNextModelSwitched.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextModelSwitchedSyncEvent':
      return SyncEventSessionNextModelSwitchedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextModelSwitchedSyncEventData':
      return SyncEventSessionNextModelSwitchedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextMoved':
      return SyncEventSessionNextMoved.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventSessionNextMovedSyncEvent':
      return SyncEventSessionNextMovedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventSessionNextMovedSyncEventData':
      return SyncEventSessionNextMovedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextPromptAdmitted':
      return SyncEventSessionNextPromptAdmitted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextPromptAdmittedSyncEvent':
      return SyncEventSessionNextPromptAdmittedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextPrompted':
      return SyncEventSessionNextPrompted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextPromptedSyncEvent':
      return SyncEventSessionNextPromptedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextPromptedSyncEventData':
      return SyncEventSessionNextPromptedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextReasoningEnded':
      return SyncEventSessionNextReasoningEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextReasoningEndedSyncEvent':
      return SyncEventSessionNextReasoningEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextReasoningEndedSyncEventData':
      return SyncEventSessionNextReasoningEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextReasoningStarted':
      return SyncEventSessionNextReasoningStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextReasoningStartedSyncEvent':
      return SyncEventSessionNextReasoningStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextReasoningStartedSyncEventData':
      return SyncEventSessionNextReasoningStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRetried':
      return SyncEventSessionNextRetried.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventSessionNextRetriedSyncEvent':
      return SyncEventSessionNextRetriedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncEventSessionNextRetriedSyncEventData':
      return SyncEventSessionNextRetriedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertCleared':
      return SyncEventSessionNextRevertCleared.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertClearedSyncEvent':
      return SyncEventSessionNextRevertClearedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextRevertClearedSyncEventData':
      return SyncEventSessionNextRevertClearedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertCommitted':
      return SyncEventSessionNextRevertCommitted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertCommittedSyncEvent':
      return SyncEventSessionNextRevertCommittedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextRevertCommittedSyncEventData':
      return SyncEventSessionNextRevertCommittedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertStaged':
      return SyncEventSessionNextRevertStaged.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextRevertStagedSyncEvent':
      return SyncEventSessionNextRevertStagedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextRevertStagedSyncEventData':
      return SyncEventSessionNextRevertStagedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextShellEnded':
      return SyncEventSessionNextShellEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextShellEndedSyncEvent':
      return SyncEventSessionNextShellEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextShellEndedSyncEventData':
      return SyncEventSessionNextShellEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextShellStarted':
      return SyncEventSessionNextShellStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextShellStartedSyncEvent':
      return SyncEventSessionNextShellStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextShellStartedSyncEventData':
      return SyncEventSessionNextShellStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepEnded':
      return SyncEventSessionNextStepEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepEndedSyncEvent':
      return SyncEventSessionNextStepEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextStepEndedSyncEventData':
      return SyncEventSessionNextStepEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepFailed':
      return SyncEventSessionNextStepFailed.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepFailedSyncEvent':
      return SyncEventSessionNextStepFailedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextStepFailedSyncEventData':
      return SyncEventSessionNextStepFailedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepStarted':
      return SyncEventSessionNextStepStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextStepStartedSyncEvent':
      return SyncEventSessionNextStepStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextStepStartedSyncEventData':
      return SyncEventSessionNextStepStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextSynthetic':
      return SyncEventSessionNextSynthetic.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextSyntheticSyncEvent':
      return SyncEventSessionNextSyntheticSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextTextEnded':
      return SyncEventSessionNextTextEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextTextEndedSyncEvent':
      return SyncEventSessionNextTextEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextTextEndedSyncEventData':
      return SyncEventSessionNextTextEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextTextStarted':
      return SyncEventSessionNextTextStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextTextStartedSyncEvent':
      return SyncEventSessionNextTextStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextTextStartedSyncEventData':
      return SyncEventSessionNextTextStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolCalled':
      return SyncEventSessionNextToolCalled.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolCalledSyncEvent':
      return SyncEventSessionNextToolCalledSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolCalledSyncEventData':
      return SyncEventSessionNextToolCalledSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolCalledSyncEventDataProvider':
      return SyncEventSessionNextToolCalledSyncEventDataProvider.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolFailed':
      return SyncEventSessionNextToolFailed.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolFailedSyncEvent':
      return SyncEventSessionNextToolFailedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolFailedSyncEventData':
      return SyncEventSessionNextToolFailedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolInputEnded':
      return SyncEventSessionNextToolInputEnded.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolInputEndedSyncEvent':
      return SyncEventSessionNextToolInputEndedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolInputEndedSyncEventData':
      return SyncEventSessionNextToolInputEndedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolInputStarted':
      return SyncEventSessionNextToolInputStarted.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolInputStartedSyncEvent':
      return SyncEventSessionNextToolInputStartedSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolInputStartedSyncEventData':
      return SyncEventSessionNextToolInputStartedSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolProgress':
      return SyncEventSessionNextToolProgress.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolProgressSyncEvent':
      return SyncEventSessionNextToolProgressSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolProgressSyncEventData':
      return SyncEventSessionNextToolProgressSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolSuccess':
      return SyncEventSessionNextToolSuccess.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionNextToolSuccessSyncEvent':
      return SyncEventSessionNextToolSuccessSyncEvent.fromJson(value)
          as ReturnType;
    case 'SyncEventSessionNextToolSuccessSyncEventData':
      return SyncEventSessionNextToolSuccessSyncEventData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncEventSessionUpdated':
      return SyncEventSessionUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncEventSessionUpdatedSyncEvent':
      return SyncEventSessionUpdatedSyncEvent.fromJson(value) as ReturnType;
    case 'SyncHistoryList200ResponseInner':
      return SyncHistoryList200ResponseInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncReplay200Response':
      return SyncReplay200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncReplayRequest':
      return SyncReplayRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncReplayRequestEventsInner':
      return SyncReplayRequestEventsInner.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'SyncSteal200Response':
      return SyncSteal200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'SyncStealRequest':
      return SyncStealRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TextPart':
      return TextPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'TextPartInput':
      return TextPartInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TextPartTime':
      return TextPartTime.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'Todo':
      return Todo.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'TodoUpdated':
      return TodoUpdated.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'TodoUpdatedData':
      return TodoUpdatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolFileContent':
      return ToolFileContent.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolListItem':
      return ToolListItem.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ToolPart':
      return ToolPart.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'ToolState':
      return ToolState.fromJson(value) as ReturnType;
    case 'ToolStateCompleted':
      return ToolStateCompleted.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStateCompletedTime':
      return ToolStateCompletedTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStateError':
      return ToolStateError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStateErrorTime':
      return ToolStateErrorTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStatePending':
      return ToolStatePending.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStateRunning':
      return ToolStateRunning.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolStateRunningTime':
      return ToolStateRunningTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'ToolTextContent':
      return ToolTextContent.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiCommandExecute':
      return TuiCommandExecute.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiCommandExecuteData':
      return TuiCommandExecuteData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiControlNext200Response':
      return TuiControlNext200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiExecuteCommandRequest':
      return TuiExecuteCommandRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiPromptAppend':
      return TuiPromptAppend.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiSelectSessionRequest':
      return TuiSelectSessionRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiSessionSelect':
      return TuiSessionSelect.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiShowToastRequest':
      return TuiShowToastRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'TuiToastShow':
      return TuiToastShow.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'UnauthorizedError':
      return UnauthorizedError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'UnknownError':
      return UnknownError.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'UnknownError1':
      return UnknownError1.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'UnknownErrorData':
      return UnknownErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'UserMessage':
      return UserMessage.fromJson(value) as ReturnType;
    case 'UserMessageModel':
      return UserMessageModel.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'UserMessageSummary':
      return UserMessageSummary.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'UserMessageTime':
      return UserMessageTime.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2AgentList200Response':
      return V2AgentList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2CommandList200Response':
      return V2CommandList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2CredentialUpdateRequest':
      return V2CredentialUpdateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2Event':
      return V2Event.fromJson(value) as ReturnType;
    case 'V2FsList200Response':
      return V2FsList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2HealthGet200Response':
      return V2HealthGet200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2IntegrationAttemptCompleteRequest':
      return V2IntegrationAttemptCompleteRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2IntegrationAttemptStatus200Response':
      return V2IntegrationAttemptStatus200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2IntegrationConnectKeyRequest':
      return V2IntegrationConnectKeyRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2IntegrationConnectOauth200Response':
      return V2IntegrationConnectOauth200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2IntegrationConnectOauthRequest':
      return V2IntegrationConnectOauthRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2IntegrationGet200Response':
      return V2IntegrationGet200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2IntegrationList200Response':
      return V2IntegrationList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2ModelList200Response':
      return V2ModelList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2PermissionRequestList200Response':
      return V2PermissionRequestList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2PermissionSavedList200Response':
      return V2PermissionSavedList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2ProjectCopyCreateRequest':
      return V2ProjectCopyCreateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2ProjectCopyRemoveRequest':
      return V2ProjectCopyRemoveRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2ProviderGet200Response':
      return V2ProviderGet200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2ProviderList200Response':
      return V2ProviderList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2PtyConnectToken200Response':
      return V2PtyConnectToken200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2PtyCreate200Response':
      return V2PtyCreate200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2PtyGet200Response':
      return V2PtyGet200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2PtyList200Response':
      return V2PtyList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2QuestionRequestList200Response':
      return V2QuestionRequestList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2ReferenceList200Response':
      return V2ReferenceList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionActive200Response':
      return V2SessionActive200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionContext200Response':
      return V2SessionContext200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionCreate200Response':
      return V2SessionCreate200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionCreateRequest':
      return V2SessionCreateRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionEvents200Response':
      return V2SessionEvents200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionGet200Response':
      return V2SessionGet200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionMessage200Response':
      return V2SessionMessage200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionPermissionCreate200Response':
      return V2SessionPermissionCreate200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPermissionCreate200ResponseData':
      return V2SessionPermissionCreate200ResponseData.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPermissionCreateRequest':
      return V2SessionPermissionCreateRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPermissionGet200Response':
      return V2SessionPermissionGet200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPermissionList200Response':
      return V2SessionPermissionList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPermissionReplyRequest':
      return V2SessionPermissionReplyRequest.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionPrompt200Response':
      return V2SessionPrompt200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionPromptRequest':
      return V2SessionPromptRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionQuestionList200Response':
      return V2SessionQuestionList200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionRevertStage200Response':
      return V2SessionRevertStage200Response.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'V2SessionRevertStageRequest':
      return V2SessionRevertStageRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionSwitchAgentRequest':
      return V2SessionSwitchAgentRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SessionSwitchModelRequest':
      return V2SessionSwitchModelRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'V2SkillList200Response':
      return V2SkillList200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsApply200Response':
      return VcsApply200Response.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsApplyError':
      return VcsApplyError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsApplyErrorData':
      return VcsApplyErrorData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsApplyRequest':
      return VcsApplyRequest.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsBranchUpdated':
      return VcsBranchUpdated.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsBranchUpdatedData':
      return VcsBranchUpdatedData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsFileDiff':
      return VcsFileDiff.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'VcsFileStatus':
      return VcsFileStatus.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'VcsInfo':
      return VcsInfo.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'WellKnownAuth':
      return WellKnownAuth.fromJson(value) as ReturnType;
    case 'Workspace':
      return Workspace.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'WorkspaceCreateError':
      return WorkspaceCreateError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorkspaceEventConnectionStatus':
      return WorkspaceEventConnectionStatus.fromJson(
            value as Map<String, dynamic>,
          )
          as ReturnType;
    case 'WorkspaceFailed':
      return WorkspaceFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorkspaceReady':
      return WorkspaceReady.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorkspaceStatus':
      return WorkspaceStatus.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorkspaceStatusData':
      return WorkspaceStatusData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorkspaceWarpError':
      return WorkspaceWarpError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'Worktree':
      return Worktree.fromJson(value as Map<String, dynamic>) as ReturnType;
    case 'WorktreeCreateInput':
      return WorktreeCreateInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeError':
      return WorktreeError.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeFailed':
      return WorktreeFailed.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeReady':
      return WorktreeReady.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeReadyData':
      return WorktreeReadyData.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeRemoveInput':
      return WorktreeRemoveInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    case 'WorktreeResetInput':
      return WorktreeResetInput.fromJson(value as Map<String, dynamic>)
          as ReturnType;
    default:
      RegExpMatch? match;

      if (value is List && (match = _regList.firstMatch(targetType)) != null) {
        targetType = match![1]!; // ignore: parameter_assignments
        return value
                .map<BaseType>(
                  (dynamic v) => deserialize<BaseType, BaseType>(
                    v,
                    targetType,
                    growable: growable,
                  ),
                )
                .toList(growable: growable)
            as ReturnType;
      }
      if (value is Set && (match = _regSet.firstMatch(targetType)) != null) {
        targetType = match![1]!; // ignore: parameter_assignments
        return value
                .map<BaseType>(
                  (dynamic v) => deserialize<BaseType, BaseType>(
                    v,
                    targetType,
                    growable: growable,
                  ),
                )
                .toSet()
            as ReturnType;
      }
      if (value is Map && (match = _regMap.firstMatch(targetType)) != null) {
        targetType = match![1]!.trim(); // ignore: parameter_assignments
        return Map<String, BaseType>.fromIterables(
              value.keys as Iterable<String>,
              value.values.map(
                (dynamic v) => deserialize<BaseType, BaseType>(
                  v,
                  targetType,
                  growable: growable,
                ),
              ),
            )
            as ReturnType;
      }
      break;
  }
  throw Exception('Cannot deserialize');
}
