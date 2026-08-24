// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_api_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelApiAnyOf _$ModelApiAnyOfFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelApiAnyOf', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'package']);
      final val = ModelApiAnyOf(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ModelApiAnyOfTypeEnumEnumMap,
            v,
            unknownValue: ModelApiAnyOfTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        package: $checkedConvert('package', (v) => v as String),
        url: $checkedConvert('url', (v) => v as String?),
        settings: $checkedConvert('settings', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$ModelApiAnyOfToJson(ModelApiAnyOf instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$ModelApiAnyOfTypeEnumEnumMap[instance.type]!,
      'package': instance.package,
      'url': ?instance.url,
      'settings': ?instance.settings,
    };

const _$ModelApiAnyOfTypeEnumEnumMap = {
  ModelApiAnyOfTypeEnum.aisdk: 'aisdk',
  ModelApiAnyOfTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
