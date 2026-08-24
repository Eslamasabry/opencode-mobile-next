// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_compacted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionCompacted _$EventSessionCompactedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionCompacted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionCompacted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionCompactedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionCompactedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncStealRequest.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionCompactedToJson(
  EventSessionCompacted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionCompactedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionCompactedTypeEnumEnumMap = {
  EventSessionCompactedTypeEnum.sessionPeriodCompacted: 'session.compacted',
  EventSessionCompactedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
