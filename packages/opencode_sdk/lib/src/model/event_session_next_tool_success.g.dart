// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_success.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolSuccess _$EventSessionNextToolSuccessFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolSuccess', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolSuccess(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolSuccessTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextToolSuccessTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextToolSuccessSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolSuccessToJson(
  EventSessionNextToolSuccess instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolSuccessTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolSuccessTypeEnumEnumMap = {
  EventSessionNextToolSuccessTypeEnum.sessionPeriodNextPeriodToolPeriodSuccess:
      'session.next.tool.success',
  EventSessionNextToolSuccessTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
