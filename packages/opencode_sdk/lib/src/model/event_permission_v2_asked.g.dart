// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_permission_v2_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPermissionV2Asked _$EventPermissionV2AskedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventPermissionV2Asked', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventPermissionV2Asked(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventPermissionV2AskedTypeEnumEnumMap,
        v,
        unknownValue: EventPermissionV2AskedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => PermissionV2AskedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventPermissionV2AskedToJson(
  EventPermissionV2Asked instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventPermissionV2AskedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventPermissionV2AskedTypeEnumEnumMap = {
  EventPermissionV2AskedTypeEnum.permissionPeriodV2PeriodAsked:
      'permission.v2.asked',
  EventPermissionV2AskedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
