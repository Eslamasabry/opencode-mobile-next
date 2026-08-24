// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_file_diff.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsFileDiff _$VcsFileDiffFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsFileDiff', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['file', 'additions', 'deletions']);
      final val = VcsFileDiff(
        file: $checkedConvert('file', (v) => v as String),
        patch_: $checkedConvert('patch', (v) => v as String?),
        additions: $checkedConvert('additions', (v) => v as num),
        deletions: $checkedConvert('deletions', (v) => v as num),
        status: $checkedConvert(
          'status',
          (v) => $enumDecodeNullable(
            _$VcsFileDiffStatusEnumEnumMap,
            v,
            unknownValue: VcsFileDiffStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    }, fieldKeyMap: const {'patch_': 'patch'});

Map<String, dynamic> _$VcsFileDiffToJson(VcsFileDiff instance) =>
    <String, dynamic>{
      'file': instance.file,
      'patch': ?instance.patch_,
      'additions': instance.additions,
      'deletions': instance.deletions,
      'status': ?_$VcsFileDiffStatusEnumEnumMap[instance.status],
    };

const _$VcsFileDiffStatusEnumEnumMap = {
  VcsFileDiffStatusEnum.added: 'added',
  VcsFileDiffStatusEnum.deleted: 'deleted',
  VcsFileDiffStatusEnum.modified: 'modified',
  VcsFileDiffStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
