// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_file_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsFileStatus _$VcsFileStatusFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsFileStatus', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['file', 'additions', 'deletions', 'status'],
      );
      final val = VcsFileStatus(
        file: $checkedConvert('file', (v) => v as String),
        additions: $checkedConvert('additions', (v) => v as num),
        deletions: $checkedConvert('deletions', (v) => v as num),
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$VcsFileStatusStatusEnumEnumMap,
            v,
            unknownValue: VcsFileStatusStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$VcsFileStatusToJson(VcsFileStatus instance) =>
    <String, dynamic>{
      'file': instance.file,
      'additions': instance.additions,
      'deletions': instance.deletions,
      'status': _$VcsFileStatusStatusEnumEnumMap[instance.status]!,
    };

const _$VcsFileStatusStatusEnumEnumMap = {
  VcsFileStatusStatusEnum.added: 'added',
  VcsFileStatusStatusEnum.deleted: 'deleted',
  VcsFileStatusStatusEnum.modified: 'modified',
  VcsFileStatusStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
