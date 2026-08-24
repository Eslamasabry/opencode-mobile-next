// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'vcs_apply200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

VcsApply200Response _$VcsApply200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('VcsApply200Response', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['applied']);
      final val = VcsApply200Response(
        applied: $checkedConvert('applied', (v) => v as bool),
      );
      return val;
    });

Map<String, dynamic> _$VcsApply200ResponseToJson(
  VcsApply200Response instance,
) => <String, dynamic>{'applied': instance.applied};
