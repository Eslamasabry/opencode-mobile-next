// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_agent.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigAgent _$ConfigAgentFromJson(Map<String, dynamic> json) => $checkedCreate(
  'ConfigAgent',
  json,
  ($checkedConvert) {
    final val = ConfigAgent(
      plan: $checkedConvert(
        'plan',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      build: $checkedConvert(
        'build',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      general: $checkedConvert(
        'general',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      explore: $checkedConvert(
        'explore',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      title: $checkedConvert(
        'title',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      summary: $checkedConvert(
        'summary',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      compaction: $checkedConvert(
        'compaction',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$ConfigAgentToJson(ConfigAgent instance) =>
    <String, dynamic>{
      'plan': ?instance.plan?.toJson(),
      'build': ?instance.build?.toJson(),
      'general': ?instance.general?.toJson(),
      'explore': ?instance.explore?.toJson(),
      'title': ?instance.title?.toJson(),
      'summary': ?instance.summary?.toJson(),
      'compaction': ?instance.compaction?.toJson(),
    };
