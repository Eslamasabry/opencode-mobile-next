// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_resource.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpResource _$McpResourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpResource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'uri', 'client']);
      final val = McpResource(
        name: $checkedConvert('name', (v) => v as String),
        uri: $checkedConvert('uri', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        mimeType: $checkedConvert('mimeType', (v) => v as String?),
        client: $checkedConvert('client', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$McpResourceToJson(McpResource instance) =>
    <String, dynamic>{
      'name': instance.name,
      'uri': instance.uri,
      'description': ?instance.description,
      'mimeType': ?instance.mimeType,
      'client': instance.client,
    };
