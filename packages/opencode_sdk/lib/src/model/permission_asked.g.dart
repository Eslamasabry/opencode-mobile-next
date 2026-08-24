// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionAsked _$PermissionAskedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionAsked', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = PermissionAsked(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PermissionAskedTypeEnumEnumMap,
            v,
            unknownValue: PermissionAskedTypeEnum.unknownDefaultOpenApi,
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
          (v) => PermissionAskedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionAskedToJson(PermissionAsked instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PermissionAskedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PermissionAskedTypeEnumEnumMap = {
  PermissionAskedTypeEnum.permissionPeriodAsked: 'permission.asked',
  PermissionAskedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
