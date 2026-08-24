// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_apply_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsApplyErrorData _$VcsApplyErrorDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsApplyErrorData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['message', 'reason']);
      final val = VcsApplyErrorData(
        message: $checkedConvert('message', (v) => v as String),
        reason: $checkedConvert(
          'reason',
          (v) => $enumDecode(
            _$VcsApplyErrorDataReasonEnumEnumMap,
            v,
            unknownValue: VcsApplyErrorDataReasonEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$VcsApplyErrorDataToJson(VcsApplyErrorData instance) =>
    <String, dynamic>{
      'message': instance.message,
      'reason': _$VcsApplyErrorDataReasonEnumEnumMap[instance.reason]!,
    };

const _$VcsApplyErrorDataReasonEnumEnumMap = {
  VcsApplyErrorDataReasonEnum.nonGit: 'non-git',
  VcsApplyErrorDataReasonEnum.notClean: 'not-clean',
  VcsApplyErrorDataReasonEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
