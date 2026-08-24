// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_apply_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsApplyError _$VcsApplyErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsApplyError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = VcsApplyError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$VcsApplyErrorNameEnumEnumMap,
            v,
            unknownValue: VcsApplyErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => VcsApplyErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$VcsApplyErrorToJson(VcsApplyError instance) =>
    <String, dynamic>{
      'name': _$VcsApplyErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$VcsApplyErrorNameEnumEnumMap = {
  VcsApplyErrorNameEnum.vcsApplyError: 'VcsApplyError',
  VcsApplyErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
