// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_retried.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextRetried _$EventSessionNextRetriedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextRetried', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextRetried(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextRetriedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextRetriedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextRetriedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextRetriedToJson(
  EventSessionNextRetried instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextRetriedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextRetriedTypeEnumEnumMap = {
  EventSessionNextRetriedTypeEnum.sessionPeriodNextPeriodRetried:
      'session.next.retried',
  EventSessionNextRetriedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
