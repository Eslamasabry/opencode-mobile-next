// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'resource_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ResourceSource _$ResourceSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ResourceSource', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['text', 'type', 'clientName', 'uri'],
      );
      final val = ResourceSource(
        text: $checkedConvert(
          'text',
          (v) => FilePartSourceText.fromJson(v as Map<String, dynamic>),
        ),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ResourceSourceTypeEnumEnumMap,
            v,
            unknownValue: ResourceSourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        clientName: $checkedConvert('clientName', (v) => v as String),
        uri: $checkedConvert('uri', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ResourceSourceToJson(ResourceSource instance) =>
    <String, dynamic>{
      'text': instance.text.toJson(),
      'type': _$ResourceSourceTypeEnumEnumMap[instance.type]!,
      'clientName': instance.clientName,
      'uri': instance.uri,
    };

const _$ResourceSourceTypeEnumEnumMap = {
  ResourceSourceTypeEnum.resource: 'resource',
  ResourceSourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
