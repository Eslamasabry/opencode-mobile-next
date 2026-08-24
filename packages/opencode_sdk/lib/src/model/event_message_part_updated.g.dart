// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_message_part_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMessagePartUpdated _$EventMessagePartUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventMessagePartUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventMessagePartUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventMessagePartUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventMessagePartUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventMessagePartUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventMessagePartUpdatedToJson(
  EventMessagePartUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMessagePartUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMessagePartUpdatedTypeEnumEnumMap = {
  EventMessagePartUpdatedTypeEnum.messagePeriodPartPeriodUpdated:
      'message.part.updated',
  EventMessagePartUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
