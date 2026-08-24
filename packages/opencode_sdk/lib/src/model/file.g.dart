// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

File _$FileFromJson(Map<String, dynamic> json) => $checkedCreate('File', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['path', 'added', 'removed', 'status']);
  final val = File(
    path: $checkedConvert('path', (v) => v as String),
    added: $checkedConvert('added', (v) => (v as num).toInt()),
    removed: $checkedConvert('removed', (v) => (v as num).toInt()),
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$FileStatusEnumEnumMap,
        v,
        unknownValue: FileStatusEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$FileToJson(File instance) => <String, dynamic>{
  'path': instance.path,
  'added': instance.added,
  'removed': instance.removed,
  'status': _$FileStatusEnumEnumMap[instance.status]!,
};

const _$FileStatusEnumEnumMap = {
  FileStatusEnum.added: 'added',
  FileStatusEnum.deleted: 'deleted',
  FileStatusEnum.modified: 'modified',
  FileStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
