// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_add_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpAddRequest _$McpAddRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpAddRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'config']);
      final val = McpAddRequest(
        name: $checkedConvert('name', (v) => v as String),
        config: $checkedConvert(
          'config',
          (v) => OpencodeSdkRawUnion056.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$McpAddRequestToJson(McpAddRequest instance) =>
    <String, dynamic>{
      'name': instance.name,
      'config': instance.config.toJson(),
    };
