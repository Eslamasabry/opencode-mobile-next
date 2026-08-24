// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsInfo _$VcsInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsInfo', json, ($checkedConvert) {
      final val = VcsInfo(
        branch: $checkedConvert('branch', (v) => v as String?),
        defaultBranch: $checkedConvert('default_branch', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'defaultBranch': 'default_branch'});

Map<String, dynamic> _$VcsInfoToJson(VcsInfo instance) => <String, dynamic>{
  'branch': ?instance.branch,
  'default_branch': ?instance.defaultBranch,
};
