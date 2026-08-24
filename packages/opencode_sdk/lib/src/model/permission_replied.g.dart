// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionReplied _$PermissionRepliedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionReplied', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = PermissionReplied(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PermissionRepliedTypeEnumEnumMap,
            v,
            unknownValue: PermissionRepliedTypeEnum.unknownDefaultOpenApi,
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
          (v) => PermissionRepliedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionRepliedToJson(PermissionReplied instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PermissionRepliedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PermissionRepliedTypeEnumEnumMap = {
  PermissionRepliedTypeEnum.permissionPeriodReplied: 'permission.replied',
  PermissionRepliedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
