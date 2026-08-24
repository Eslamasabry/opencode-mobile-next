// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_server_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpServerNotFoundError _$McpServerNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpServerNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'name', 'message']);
  final val = McpServerNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$McpServerNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: McpServerNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    name: $checkedConvert('name', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$McpServerNotFoundErrorToJson(
  McpServerNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$McpServerNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'name': instance.name,
  'message': instance.message,
};

const _$McpServerNotFoundErrorTagEnumEnumMap = {
  McpServerNotFoundErrorTagEnum.mcpServerNotFoundError:
      'McpServerNotFoundError',
  McpServerNotFoundErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
