// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_prompt_admitted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextPromptAdmitted _$EventSessionNextPromptAdmittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextPromptAdmitted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextPromptAdmitted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextPromptAdmittedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextPromptedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextPromptAdmittedToJson(
  EventSessionNextPromptAdmitted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextPromptAdmittedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextPromptAdmittedTypeEnumEnumMap = {
  EventSessionNextPromptAdmittedTypeEnum
          .sessionPeriodNextPeriodPromptPeriodAdmitted:
      'session.next.prompt.admitted',
  EventSessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
