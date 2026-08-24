// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_branch_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsBranchUpdatedData _$VcsBranchUpdatedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('VcsBranchUpdatedData', json, ($checkedConvert) {
  final val = VcsBranchUpdatedData(
    branch: $checkedConvert('branch', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$VcsBranchUpdatedDataToJson(
  VcsBranchUpdatedData instance,
) => <String, dynamic>{'branch': ?instance.branch};
