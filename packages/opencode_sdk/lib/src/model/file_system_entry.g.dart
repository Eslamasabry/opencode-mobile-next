// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_system_entry.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileSystemEntry _$FileSystemEntryFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileSystemEntry', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['path', 'type']);
      final val = FileSystemEntry(
        path: $checkedConvert('path', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FileSystemEntryTypeEnumEnumMap,
            v,
            unknownValue: FileSystemEntryTypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$FileSystemEntryToJson(FileSystemEntry instance) =>
    <String, dynamic>{
      'path': instance.path,
      'type': _$FileSystemEntryTypeEnumEnumMap[instance.type]!,
    };

const _$FileSystemEntryTypeEnumEnumMap = {
  FileSystemEntryTypeEnum.file: 'file',
  FileSystemEntryTypeEnum.directory: 'directory',
  FileSystemEntryTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
