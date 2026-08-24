// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_permission_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPermissionAsked _$EventPermissionAskedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventPermissionAsked', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventPermissionAsked(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventPermissionAskedTypeEnumEnumMap,
        v,
        unknownValue: EventPermissionAskedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => PermissionAskedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventPermissionAskedToJson(
  EventPermissionAsked instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventPermissionAskedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventPermissionAskedTypeEnumEnumMap = {
  EventPermissionAskedTypeEnum.permissionPeriodAsked: 'permission.asked',
  EventPermissionAskedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
