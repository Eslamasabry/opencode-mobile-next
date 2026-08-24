// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2Source _$PermissionV2SourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionV2Source', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'messageID', 'callID']);
      final val = PermissionV2Source(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$PermissionV2SourceTypeEnumEnumMap,
            v,
            unknownValue: PermissionV2SourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        messageID: $checkedConvert('messageID', (v) => v as String),
        callID: $checkedConvert('callID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$PermissionV2SourceToJson(PermissionV2Source instance) =>
    <String, dynamic>{
      'type': _$PermissionV2SourceTypeEnumEnumMap[instance.type]!,
      'messageID': instance.messageID,
      'callID': instance.callID,
    };

const _$PermissionV2SourceTypeEnumEnumMap = {
  PermissionV2SourceTypeEnum.tool: 'tool',
  PermissionV2SourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
