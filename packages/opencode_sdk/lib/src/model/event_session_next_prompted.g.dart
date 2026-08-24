// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_prompted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextPrompted _$EventSessionNextPromptedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextPrompted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextPrompted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextPromptedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextPromptedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$EventSessionNextPromptedToJson(
  EventSessionNextPrompted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextPromptedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextPromptedTypeEnumEnumMap = {
  EventSessionNextPromptedTypeEnum.sessionPeriodNextPeriodPrompted:
      'session.next.prompted',
  EventSessionNextPromptedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
