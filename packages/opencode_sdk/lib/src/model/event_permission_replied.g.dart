// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_permission_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPermissionReplied _$EventPermissionRepliedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventPermissionReplied', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventPermissionReplied(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventPermissionRepliedTypeEnumEnumMap,
        v,
        unknownValue: EventPermissionRepliedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => PermissionRepliedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventPermissionRepliedToJson(
  EventPermissionReplied instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventPermissionRepliedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventPermissionRepliedTypeEnumEnumMap = {
  EventPermissionRepliedTypeEnum.permissionPeriodReplied: 'permission.replied',
  EventPermissionRepliedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
