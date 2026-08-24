// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2Replied _$PermissionV2RepliedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionV2Replied', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = PermissionV2Replied(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PermissionV2RepliedTypeEnumEnumMap,
            v,
            unknownValue: PermissionV2RepliedTypeEnum.unknownDefaultOpenApi,
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
          (v) => PermissionV2RepliedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionV2RepliedToJson(
  PermissionV2Replied instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$PermissionV2RepliedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$PermissionV2RepliedTypeEnumEnumMap = {
  PermissionV2RepliedTypeEnum.permissionPeriodV2PeriodReplied:
      'permission.v2.replied',
  PermissionV2RepliedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
