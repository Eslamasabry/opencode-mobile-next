// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_file_content.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolFileContent _$ToolFileContentFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolFileContent', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'uri', 'mime']);
      final val = ToolFileContent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ToolFileContentTypeEnumEnumMap,
            v,
            unknownValue: ToolFileContentTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        uri: $checkedConvert('uri', (v) => v as String),
        mime: $checkedConvert('mime', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ToolFileContentToJson(ToolFileContent instance) =>
    <String, dynamic>{
      'type': _$ToolFileContentTypeEnumEnumMap[instance.type]!,
      'uri': instance.uri,
      'mime': instance.mime,
      'name': ?instance.name,
    };

const _$ToolFileContentTypeEnumEnumMap = {
  ToolFileContentTypeEnum.file: 'file',
  ToolFileContentTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
