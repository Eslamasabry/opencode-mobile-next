// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_message_part_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMessagePartRemoved _$EventMessagePartRemovedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventMessagePartRemoved', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventMessagePartRemoved(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventMessagePartRemovedTypeEnumEnumMap,
        v,
        unknownValue: EventMessagePartRemovedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventMessagePartRemovedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventMessagePartRemovedToJson(
  EventMessagePartRemoved instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMessagePartRemovedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMessagePartRemovedTypeEnumEnumMap = {
  EventMessagePartRemovedTypeEnum.messagePeriodPartPeriodRemoved:
      'message.part.removed',
  EventMessagePartRemovedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
