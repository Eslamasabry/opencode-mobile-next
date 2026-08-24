// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_text_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextTextStarted _$EventSessionNextTextStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextTextStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextTextStarted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextTextStartedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextTextStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextTextStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextTextStartedToJson(
  EventSessionNextTextStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextTextStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextTextStartedTypeEnumEnumMap = {
  EventSessionNextTextStartedTypeEnum.sessionPeriodNextPeriodTextPeriodStarted:
      'session.next.text.started',
  EventSessionNextTextStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
