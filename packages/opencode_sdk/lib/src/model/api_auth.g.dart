// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'api_auth.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ApiAuth _$ApiAuthFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ApiAuth', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'key']);
      final val = ApiAuth(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ApiAuthTypeEnumEnumMap,
            v,
            unknownValue: ApiAuthTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        key: $checkedConvert('key', (v) => v as String),
        metadata: $checkedConvert(
          'metadata',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(k, e as String),
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ApiAuthToJson(ApiAuth instance) => <String, dynamic>{
  'type': _$ApiAuthTypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'metadata': ?instance.metadata,
};

const _$ApiAuthTypeEnumEnumMap = {
  ApiAuthTypeEnum.api: 'api',
  ApiAuthTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
