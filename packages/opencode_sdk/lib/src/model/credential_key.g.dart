// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'credential_key.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CredentialKey _$CredentialKeyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CredentialKey', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'key']);
      final val = CredentialKey(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$CredentialKeyTypeEnumEnumMap,
            v,
            unknownValue: CredentialKeyTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        key: $checkedConvert('key', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$CredentialKeyToJson(CredentialKey instance) =>
    <String, dynamic>{
      'type': _$CredentialKeyTypeEnumEnumMap[instance.type]!,
      'key': instance.key,
      'metadata': ?instance.metadata,
    };

const _$CredentialKeyTypeEnumEnumMap = {
  CredentialKeyTypeEnum.key: 'key',
  CredentialKeyTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
