// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_model_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextModelSwitched _$EventSessionNextModelSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextModelSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextModelSwitched(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextModelSwitchedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextModelSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextModelSwitchedToJson(
  EventSessionNextModelSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextModelSwitchedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextModelSwitchedTypeEnumEnumMap = {
  EventSessionNextModelSwitchedTypeEnum
          .sessionPeriodNextPeriodModelPeriodSwitched:
      'session.next.model.switched',
  EventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
