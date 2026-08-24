// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_user.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageUser _$SessionMessageUserFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageUser', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'time', 'text', 'type']);
  final val = SessionMessageUser(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAgentSwitchedTime.fromJson(v as Map<String, dynamic>),
    ),
    text: $checkedConvert('text', (v) => v as String),
    files: $checkedConvert(
      'files',
      (v) => (v as List<dynamic>?)
          ?.map((e) => PromptFileAttachment.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    agents: $checkedConvert(
      'agents',
      (v) => (v as List<dynamic>?)
          ?.map(
            (e) => PromptAgentAttachment.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
    ),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageUserTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageUserTypeEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageUserToJson(SessionMessageUser instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'time': instance.time.toJson(),
      'text': instance.text,
      'files': ?instance.files?.map((e) => e.toJson()).toList(),
      'agents': ?instance.agents?.map((e) => e.toJson()).toList(),
      'type': _$SessionMessageUserTypeEnumEnumMap[instance.type]!,
    };

const _$SessionMessageUserTypeEnumEnumMap = {
  SessionMessageUserTypeEnum.user: 'user',
  SessionMessageUserTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
