// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_content.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileContent _$FileContentFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileContent', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'content']);
      final val = FileContent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FileContentTypeEnumEnumMap,
            v,
            unknownValue: FileContentTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        content: $checkedConvert('content', (v) => v as String),
        diff: $checkedConvert('diff', (v) => v as String?),
        patch_: $checkedConvert(
          'patch',
          (v) => v == null
              ? null
              : FileContentPatch.fromJson(v as Map<String, dynamic>),
        ),
        encoding: $checkedConvert(
          'encoding',
          (v) => $enumDecodeNullable(
            _$FileContentEncodingEnumEnumMap,
            v,
            unknownValue: FileContentEncodingEnum.unknownDefaultOpenApi,
          ),
        ),
        mimeType: $checkedConvert('mimeType', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'patch_': 'patch'});

Map<String, dynamic> _$FileContentToJson(FileContent instance) =>
    <String, dynamic>{
      'type': _$FileContentTypeEnumEnumMap[instance.type]!,
      'content': instance.content,
      'diff': ?instance.diff,
      'patch': ?instance.patch_?.toJson(),
      'encoding': ?_$FileContentEncodingEnumEnumMap[instance.encoding],
      'mimeType': ?instance.mimeType,
    };

const _$FileContentTypeEnumEnumMap = {
  FileContentTypeEnum.text: 'text',
  FileContentTypeEnum.binary: 'binary',
  FileContentTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

const _$FileContentEncodingEnumEnumMap = {
  FileContentEncodingEnum.base64: 'base64',
  FileContentEncodingEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
