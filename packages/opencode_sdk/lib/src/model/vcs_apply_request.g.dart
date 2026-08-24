// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_apply_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsApplyRequest _$VcsApplyRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsApplyRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['patch']);
      final val = VcsApplyRequest(
        patch_: $checkedConvert('patch', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'patch_': 'patch'});

Map<String, dynamic> _$VcsApplyRequestToJson(VcsApplyRequest instance) =>
    <String, dynamic>{'patch': instance.patch_};
