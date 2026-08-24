// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2Asked _$PermissionV2AskedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionV2Asked', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = PermissionV2Asked(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PermissionV2AskedTypeEnumEnumMap,
            v,
            unknownValue: PermissionV2AskedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => PermissionV2AskedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionV2AskedToJson(PermissionV2Asked instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PermissionV2AskedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PermissionV2AskedTypeEnumEnumMap = {
  PermissionV2AskedTypeEnum.permissionPeriodV2PeriodAsked:
      'permission.v2.asked',
  PermissionV2AskedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
