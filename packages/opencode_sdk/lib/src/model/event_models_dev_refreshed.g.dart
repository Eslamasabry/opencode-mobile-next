// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_models_dev_refreshed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventModelsDevRefreshed _$EventModelsDevRefreshedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventModelsDevRefreshed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventModelsDevRefreshed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventModelsDevRefreshedTypeEnumEnumMap,
        v,
        unknownValue: EventModelsDevRefreshedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$EventModelsDevRefreshedToJson(
  EventModelsDevRefreshed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventModelsDevRefreshedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventModelsDevRefreshedTypeEnumEnumMap = {
  EventModelsDevRefreshedTypeEnum.modelsDevPeriodRefreshed:
      'models-dev.refreshed',
  EventModelsDevRefreshedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
