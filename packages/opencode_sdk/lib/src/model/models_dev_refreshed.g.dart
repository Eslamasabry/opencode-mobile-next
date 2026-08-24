// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'models_dev_refreshed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelsDevRefreshed _$ModelsDevRefreshedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelsDevRefreshed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = ModelsDevRefreshed(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ModelsDevRefreshedTypeEnumEnumMap,
            v,
            unknownValue: ModelsDevRefreshedTypeEnum.unknownDefaultOpenApi,
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
        data: $checkedConvert('data', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ModelsDevRefreshedToJson(ModelsDevRefreshed instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$ModelsDevRefreshedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$ModelsDevRefreshedTypeEnumEnumMap = {
  ModelsDevRefreshedTypeEnum.modelsDevPeriodRefreshed: 'models-dev.refreshed',
  ModelsDevRefreshedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
