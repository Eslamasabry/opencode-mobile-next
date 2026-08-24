// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_plugin_added.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventPluginAdded _$EventPluginAddedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventPluginAdded', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventPluginAdded(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventPluginAddedTypeEnumEnumMap,
            v,
            unknownValue: EventPluginAddedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => PluginAddedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventPluginAddedToJson(EventPluginAdded instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventPluginAddedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventPluginAddedTypeEnumEnumMap = {
  EventPluginAddedTypeEnum.pluginPeriodAdded: 'plugin.added',
  EventPluginAddedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
