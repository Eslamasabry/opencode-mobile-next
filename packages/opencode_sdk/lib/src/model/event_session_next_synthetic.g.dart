// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_synthetic.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextSynthetic _$EventSessionNextSyntheticFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextSynthetic', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextSynthetic(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextSyntheticTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextSyntheticTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$EventSessionNextSyntheticToJson(
  EventSessionNextSynthetic instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextSyntheticTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextSyntheticTypeEnumEnumMap = {
  EventSessionNextSyntheticTypeEnum.sessionPeriodNextPeriodSynthetic:
      'session.next.synthetic',
  EventSessionNextSyntheticTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
