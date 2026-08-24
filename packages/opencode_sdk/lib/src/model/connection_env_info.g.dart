// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'connection_env_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConnectionEnvInfo _$ConnectionEnvInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConnectionEnvInfo', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'name']);
      final val = ConnectionEnvInfo(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ConnectionEnvInfoTypeEnumEnumMap,
            v,
            unknownValue: ConnectionEnvInfoTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        name: $checkedConvert('name', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ConnectionEnvInfoToJson(ConnectionEnvInfo instance) =>
    <String, dynamic>{
      'type': _$ConnectionEnvInfoTypeEnumEnumMap[instance.type]!,
      'name': instance.name,
    };

const _$ConnectionEnvInfoTypeEnumEnumMap = {
  ConnectionEnvInfoTypeEnum.env: 'env',
  ConnectionEnvInfoTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
