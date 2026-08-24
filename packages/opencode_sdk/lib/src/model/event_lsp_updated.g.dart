// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_lsp_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventLspUpdated _$EventLspUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventLspUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventLspUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventLspUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventLspUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert('properties', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$EventLspUpdatedToJson(EventLspUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventLspUpdatedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties,
    };

const _$EventLspUpdatedTypeEnumEnumMap = {
  EventLspUpdatedTypeEnum.lspPeriodUpdated: 'lsp.updated',
  EventLspUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
