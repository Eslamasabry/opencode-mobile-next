// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'plugin_added.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PluginAdded _$PluginAddedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PluginAdded',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = PluginAdded(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$PluginAddedTypeEnumEnumMap,
          v,
          unknownValue: PluginAddedTypeEnum.unknownDefaultOpenApi,
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
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert(
        'data',
        (v) => PluginAddedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$PluginAddedToJson(PluginAdded instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$PluginAddedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$PluginAddedTypeEnumEnumMap = {
  PluginAddedTypeEnum.pluginPeriodAdded: 'plugin.added',
  PluginAddedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
