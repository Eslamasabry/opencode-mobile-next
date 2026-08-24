// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'snapshot_file_diff.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SnapshotFileDiff _$SnapshotFileDiffFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SnapshotFileDiff', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['additions', 'deletions']);
      final val = SnapshotFileDiff(
        file: $checkedConvert('file', (v) => v as String?),
        patch_: $checkedConvert('patch', (v) => v as String?),
        additions: $checkedConvert('additions', (v) => v as num),
        deletions: $checkedConvert('deletions', (v) => v as num),
        status: $checkedConvert(
          'status',
          (v) => $enumDecodeNullable(
            _$SnapshotFileDiffStatusEnumEnumMap,
            v,
            unknownValue: SnapshotFileDiffStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    }, fieldKeyMap: const {'patch_': 'patch'});

Map<String, dynamic> _$SnapshotFileDiffToJson(SnapshotFileDiff instance) =>
    <String, dynamic>{
      'file': ?instance.file,
      'patch': ?instance.patch_,
      'additions': instance.additions,
      'deletions': instance.deletions,
      'status': ?_$SnapshotFileDiffStatusEnumEnumMap[instance.status],
    };

const _$SnapshotFileDiffStatusEnumEnumMap = {
  SnapshotFileDiffStatusEnum.added: 'added',
  SnapshotFileDiffStatusEnum.deleted: 'deleted',
  SnapshotFileDiffStatusEnum.modified: 'modified',
  SnapshotFileDiffStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
