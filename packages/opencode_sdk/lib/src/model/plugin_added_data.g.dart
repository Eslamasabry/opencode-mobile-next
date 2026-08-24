// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'plugin_added_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PluginAddedData _$PluginAddedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PluginAddedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id']);
      final val = PluginAddedData(
        id: $checkedConvert('id', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$PluginAddedDataToJson(PluginAddedData instance) =>
    <String, dynamic>{'id': instance.id};
