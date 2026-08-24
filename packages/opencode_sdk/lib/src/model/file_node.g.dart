// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_node.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileNode _$FileNodeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileNode', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['name', 'path', 'absolute', 'type', 'ignored'],
      );
      final val = FileNode(
        name: $checkedConvert('name', (v) => v as String),
        path: $checkedConvert('path', (v) => v as String),
        absolute: $checkedConvert('absolute', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FileNodeTypeEnumEnumMap,
            v,
            unknownValue: FileNodeTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        ignored: $checkedConvert('ignored', (v) => v as bool),
      );
      return val;
    });

Map<String, dynamic> _$FileNodeToJson(FileNode instance) => <String, dynamic>{
  'name': instance.name,
  'path': instance.path,
  'absolute': instance.absolute,
  'type': _$FileNodeTypeEnumEnumMap[instance.type]!,
  'ignored': instance.ignored,
};

const _$FileNodeTypeEnumEnumMap = {
  FileNodeTypeEnum.file: 'file',
  FileNodeTypeEnum.directory: 'directory',
  FileNodeTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
