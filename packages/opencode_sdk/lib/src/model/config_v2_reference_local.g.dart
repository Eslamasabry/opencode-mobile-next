// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_v2_reference_local.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigV2ReferenceLocal _$ConfigV2ReferenceLocalFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ConfigV2ReferenceLocal', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['path']);
  final val = ConfigV2ReferenceLocal(
    path: $checkedConvert('path', (v) => v as String),
    description: $checkedConvert('description', (v) => v as String?),
    hidden: $checkedConvert('hidden', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$ConfigV2ReferenceLocalToJson(
  ConfigV2ReferenceLocal instance,
) => <String, dynamic>{
  'path': instance.path,
  'description': ?instance.description,
  'hidden': ?instance.hidden,
};
