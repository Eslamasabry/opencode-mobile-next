// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_diff.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileDiff _$FileDiffFromJson(Map<String, dynamic> json) => $checkedCreate(
  'FileDiff',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const ['path', 'status', 'additions', 'deletions', 'patch'],
    );
    final val = FileDiff(
      path: $checkedConvert('path', (v) => v as String),
      status: $checkedConvert(
        'status',
        (v) => $enumDecode(
          _$FileDiffStatusEnumEnumMap,
          v,
          unknownValue: FileDiffStatusEnum.unknownDefaultOpenApi,
        ),
      ),
      additions: $checkedConvert('additions', (v) => (v as num).toInt()),
      deletions: $checkedConvert('deletions', (v) => (v as num).toInt()),
      patch_: $checkedConvert('patch', (v) => v as String),
    );
    return val;
  },
  fieldKeyMap: const {'patch_': 'patch'},
);

Map<String, dynamic> _$FileDiffToJson(FileDiff instance) => <String, dynamic>{
  'path': instance.path,
  'status': _$FileDiffStatusEnumEnumMap[instance.status]!,
  'additions': instance.additions,
  'deletions': instance.deletions,
  'patch': instance.patch_,
};

const _$FileDiffStatusEnumEnumMap = {
  FileDiffStatusEnum.added: 'added',
  FileDiffStatusEnum.modified: 'modified',
  FileDiffStatusEnum.deleted: 'deleted',
  FileDiffStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
