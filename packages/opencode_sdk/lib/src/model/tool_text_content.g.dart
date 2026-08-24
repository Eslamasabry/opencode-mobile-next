// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_text_content.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolTextContent _$ToolTextContentFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolTextContent', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'text']);
      final val = ToolTextContent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ToolTextContentTypeEnumEnumMap,
            v,
            unknownValue: ToolTextContentTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        text: $checkedConvert('text', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ToolTextContentToJson(ToolTextContent instance) =>
    <String, dynamic>{
      'type': _$ToolTextContentTypeEnumEnumMap[instance.type]!,
      'text': instance.text,
    };

const _$ToolTextContentTypeEnumEnumMap = {
  ToolTextContentTypeEnum.text: 'text',
  ToolTextContentTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
