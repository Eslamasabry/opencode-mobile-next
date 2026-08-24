// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_message.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserMessage _$UserMessageFromJson(Map<String, dynamic> json) => $checkedCreate(
  'UserMessage',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const ['id', 'sessionID', 'role', 'time', 'agent', 'model'],
    );
    final val = UserMessage(
      id: $checkedConvert('id', (v) => v as String),
      sessionID: $checkedConvert('sessionID', (v) => v as String),
      role: $checkedConvert(
        'role',
        (v) => $enumDecode(
          _$UserMessageRoleEnumEnumMap,
          v,
          unknownValue: UserMessageRoleEnum.unknownDefaultOpenApi,
        ),
      ),
      time: $checkedConvert(
        'time',
        (v) => UserMessageTime.fromJson(v as Map<String, dynamic>),
      ),
      format: $checkedConvert(
        'format',
        (v) => v == null ? null : OutputFormat.fromJson(v),
      ),
      summary: $checkedConvert(
        'summary',
        (v) => v == null
            ? null
            : UserMessageSummary.fromJson(v as Map<String, dynamic>),
      ),
      agent: $checkedConvert('agent', (v) => v as String),
      model: $checkedConvert(
        'model',
        (v) => UserMessageModel.fromJson(v as Map<String, dynamic>),
      ),
      system: $checkedConvert('system', (v) => v as String?),
      tools: $checkedConvert(
        'tools',
        (v) =>
            (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as bool)),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$UserMessageToJson(UserMessage instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'role': _$UserMessageRoleEnumEnumMap[instance.role]!,
      'time': instance.time.toJson(),
      'format': ?instance.format?.toJson(),
      'summary': ?instance.summary?.toJson(),
      'agent': instance.agent,
      'model': instance.model.toJson(),
      'system': ?instance.system,
      'tools': ?instance.tools,
    };

const _$UserMessageRoleEnumEnumMap = {
  UserMessageRoleEnum.user: 'user',
  UserMessageRoleEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
