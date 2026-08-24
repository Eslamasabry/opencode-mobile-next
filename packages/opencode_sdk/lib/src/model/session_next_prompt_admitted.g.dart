// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_prompt_admitted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextPromptAdmitted _$SessionNextPromptAdmittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextPromptAdmitted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextPromptAdmitted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextPromptAdmittedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    durable: $checkedConvert(
      'durable',
      (v) => v == null
          ? null
          : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextPromptedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextPromptAdmittedToJson(
  SessionNextPromptAdmitted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextPromptAdmittedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextPromptAdmittedTypeEnumEnumMap = {
  SessionNextPromptAdmittedTypeEnum.sessionPeriodNextPeriodPromptPeriodAdmitted:
      'session.next.prompt.admitted',
  SessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
