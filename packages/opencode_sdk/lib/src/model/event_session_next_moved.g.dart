// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_moved.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextMoved _$EventSessionNextMovedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextMoved', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextMoved(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextMovedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextMovedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextMovedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextMovedToJson(
  EventSessionNextMoved instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextMovedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextMovedTypeEnumEnumMap = {
  EventSessionNextMovedTypeEnum.sessionPeriodNextPeriodMoved:
      'session.next.moved',
  EventSessionNextMovedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
