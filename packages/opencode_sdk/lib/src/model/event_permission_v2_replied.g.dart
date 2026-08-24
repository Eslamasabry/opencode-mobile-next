// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_permission_v2_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPermissionV2Replied _$EventPermissionV2RepliedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventPermissionV2Replied', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventPermissionV2Replied(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventPermissionV2RepliedTypeEnumEnumMap,
        v,
        unknownValue: EventPermissionV2RepliedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => PermissionV2RepliedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventPermissionV2RepliedToJson(
  EventPermissionV2Replied instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventPermissionV2RepliedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventPermissionV2RepliedTypeEnumEnumMap = {
  EventPermissionV2RepliedTypeEnum.permissionPeriodV2PeriodReplied:
      'permission.v2.replied',
  EventPermissionV2RepliedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
