// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_modalities.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueModalities
_$ProviderConfigModelsValueModalitiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderConfigModelsValueModalities', json, (
      $checkedConvert,
    ) {
      final val = ProviderConfigModelsValueModalities(
        input: $checkedConvert(
          'input',
          (v) => (v as List<dynamic>?)
              ?.map(
                (e) => $enumDecode(
                  _$ProviderConfigModelsValueModalitiesInputEnumEnumMap,
                  e,
                ),
              )
              .toList(),
        ),
        output: $checkedConvert(
          'output',
          (v) => (v as List<dynamic>?)
              ?.map(
                (e) => $enumDecode(
                  _$ProviderConfigModelsValueModalitiesOutputEnumEnumMap,
                  e,
                ),
              )
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderConfigModelsValueModalitiesToJson(
  ProviderConfigModelsValueModalities instance,
) => <String, dynamic>{
  'input': ?instance.input
      ?.map((e) => _$ProviderConfigModelsValueModalitiesInputEnumEnumMap[e]!)
      .toList(),
  'output': ?instance.output
      ?.map((e) => _$ProviderConfigModelsValueModalitiesOutputEnumEnumMap[e]!)
      .toList(),
};

const _$ProviderConfigModelsValueModalitiesInputEnumEnumMap = {
  ProviderConfigModelsValueModalitiesInputEnum.text: 'text',
  ProviderConfigModelsValueModalitiesInputEnum.audio: 'audio',
  ProviderConfigModelsValueModalitiesInputEnum.image: 'image',
  ProviderConfigModelsValueModalitiesInputEnum.video: 'video',
  ProviderConfigModelsValueModalitiesInputEnum.pdf: 'pdf',
  ProviderConfigModelsValueModalitiesInputEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};

const _$ProviderConfigModelsValueModalitiesOutputEnumEnumMap = {
  ProviderConfigModelsValueModalitiesOutputEnum.text: 'text',
  ProviderConfigModelsValueModalitiesOutputEnum.audio: 'audio',
  ProviderConfigModelsValueModalitiesOutputEnum.image: 'image',
  ProviderConfigModelsValueModalitiesOutputEnum.video: 'video',
  ProviderConfigModelsValueModalitiesOutputEnum.pdf: 'pdf',
  ProviderConfigModelsValueModalitiesOutputEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
