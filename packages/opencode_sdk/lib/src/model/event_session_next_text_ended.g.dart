// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_text_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextTextEnded _$EventSessionNextTextEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextTextEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextTextEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextTextEndedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextTextEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextTextEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextTextEndedToJson(
  EventSessionNextTextEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextTextEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextTextEndedTypeEnumEnumMap = {
  EventSessionNextTextEndedTypeEnum.sessionPeriodNextPeriodTextPeriodEnded:
      'session.next.text.ended',
  EventSessionNextTextEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
