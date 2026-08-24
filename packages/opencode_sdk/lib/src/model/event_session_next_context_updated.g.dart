// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_context_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextContextUpdated _$EventSessionNextContextUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextContextUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextContextUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextContextUpdatedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextContextUpdatedToJson(
  EventSessionNextContextUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextContextUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextContextUpdatedTypeEnumEnumMap = {
  EventSessionNextContextUpdatedTypeEnum
          .sessionPeriodNextPeriodContextPeriodUpdated:
      'session.next.context.updated',
  EventSessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
