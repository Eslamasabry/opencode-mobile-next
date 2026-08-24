// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_project_copy_generate_name200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalProjectCopyGenerateName200Response
_$ExperimentalProjectCopyGenerateName200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalProjectCopyGenerateName200Response', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['name']);
  final val = ExperimentalProjectCopyGenerateName200Response(
    name: $checkedConvert('name', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalProjectCopyGenerateName200ResponseToJson(
  ExperimentalProjectCopyGenerateName200Response instance,
) => <String, dynamic>{'name': instance.name};
