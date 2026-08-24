// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_api_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelApiAnyOf1 _$ModelApiAnyOf1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelApiAnyOf1', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'settings']);
      final val = ModelApiAnyOf1(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ModelApiAnyOf1TypeEnumEnumMap,
            v,
            unknownValue: ModelApiAnyOf1TypeEnum.unknownDefaultOpenApi,
          ),
        ),
        url: $checkedConvert('url', (v) => v as String?),
        settings: $checkedConvert('settings', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ModelApiAnyOf1ToJson(ModelApiAnyOf1 instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$ModelApiAnyOf1TypeEnumEnumMap[instance.type]!,
      'url': ?instance.url,
      'settings': instance.settings,
    };

const _$ModelApiAnyOf1TypeEnumEnumMap = {
  ModelApiAnyOf1TypeEnum.native_: 'native',
  ModelApiAnyOf1TypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
