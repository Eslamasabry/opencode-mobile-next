// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_experimental.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigExperimental _$ConfigExperimentalFromJson(Map<String, dynamic> json) =>
    $checkedCreate(
      'ConfigExperimental',
      json,
      ($checkedConvert) {
        final val = ConfigExperimental(
          disablePasteSummary: $checkedConvert(
            'disable_paste_summary',
            (v) => v as bool?,
          ),
          batchTool: $checkedConvert('batch_tool', (v) => v as bool?),
          openTelemetry: $checkedConvert('openTelemetry', (v) => v as bool?),
          primaryTools: $checkedConvert(
            'primary_tools',
            (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
          ),
          continueLoopOnDeny: $checkedConvert(
            'continue_loop_on_deny',
            (v) => v as bool?,
          ),
          mcpTimeout: $checkedConvert(
            'mcp_timeout',
            (v) => (v as num?)?.toInt(),
          ),
          policies: $checkedConvert(
            'policies',
            (v) => (v as List<dynamic>?)
                ?.map(
                  (e) => ConfigV2ExperimentalPolicy.fromJson(
                    e as Map<String, dynamic>,
                  ),
                )
                .toList(),
          ),
        );
        return val;
      },
      fieldKeyMap: const {
        'disablePasteSummary': 'disable_paste_summary',
        'batchTool': 'batch_tool',
        'primaryTools': 'primary_tools',
        'continueLoopOnDeny': 'continue_loop_on_deny',
        'mcpTimeout': 'mcp_timeout',
      },
    );

Map<String, dynamic> _$ConfigExperimentalToJson(ConfigExperimental instance) =>
    <String, dynamic>{
      'disable_paste_summary': ?instance.disablePasteSummary,
      'batch_tool': ?instance.batchTool,
      'openTelemetry': ?instance.openTelemetry,
      'primary_tools': ?instance.primaryTools,
      'continue_loop_on_deny': ?instance.continueLoopOnDeny,
      'mcp_timeout': ?instance.mcpTimeout,
      'policies': ?instance.policies?.map((e) => e.toJson()).toList(),
    };
