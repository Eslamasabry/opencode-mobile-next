// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_mode.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigMode _$ConfigModeFromJson(Map<String, dynamic> json) => $checkedCreate(
  'ConfigMode',
  json,
  ($checkedConvert) {
    final val = ConfigMode(
      build: $checkedConvert(
        'build',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
      plan: $checkedConvert(
        'plan',
        (v) =>
            v == null ? null : AgentConfig.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$ConfigModeToJson(ConfigMode instance) =>
    <String, dynamic>{
      'build': ?instance.build?.toJson(),
      'plan': ?instance.plan?.toJson(),
    };
