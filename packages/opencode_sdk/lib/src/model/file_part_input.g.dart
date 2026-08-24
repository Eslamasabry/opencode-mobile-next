// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_part_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FilePartInput _$FilePartInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FilePartInput', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'mime', 'url']);
      final val = FilePartInput(
        id: $checkedConvert('id', (v) => v as String?),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FilePartInputTypeEnumEnumMap,
            v,
            unknownValue: FilePartInputTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        mime: $checkedConvert('mime', (v) => v as String),
        filename: $checkedConvert('filename', (v) => v as String?),
        url: $checkedConvert('url', (v) => v as String),
        source_: $checkedConvert(
          'source',
          (v) => v == null ? null : FilePartSource.fromJson(v),
        ),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$FilePartInputToJson(FilePartInput instance) =>
    <String, dynamic>{
      'id': ?instance.id,
      'type': _$FilePartInputTypeEnumEnumMap[instance.type]!,
      'mime': instance.mime,
      'filename': ?instance.filename,
      'url': instance.url,
      'source': ?instance.source_?.toJson(),
    };

const _$FilePartInputTypeEnumEnumMap = {
  FilePartInputTypeEnum.file: 'file',
  FilePartInputTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
