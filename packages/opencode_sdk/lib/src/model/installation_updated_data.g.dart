// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'installation_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

InstallationUpdatedData _$InstallationUpdatedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('InstallationUpdatedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['version']);
  final val = InstallationUpdatedData(
    version: $checkedConvert('version', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$InstallationUpdatedDataToJson(
  InstallationUpdatedData instance,
) => <String, dynamic>{'version': instance.version};
