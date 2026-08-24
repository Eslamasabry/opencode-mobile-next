// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileSource _$FileSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileSource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['text', 'type', 'path']);
      final val = FileSource(
        text: $checkedConvert(
          'text',
          (v) => FilePartSourceText.fromJson(v as Map<String, dynamic>),
        ),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FileSourceTypeEnumEnumMap,
            v,
            unknownValue: FileSourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        path: $checkedConvert('path', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$FileSourceToJson(FileSource instance) =>
    <String, dynamic>{
      'text': instance.text.toJson(),
      'type': _$FileSourceTypeEnumEnumMap[instance.type]!,
      'path': instance.path,
    };

const _$FileSourceTypeEnumEnumMap = {
  FileSourceTypeEnum.file: 'file',
  FileSourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
