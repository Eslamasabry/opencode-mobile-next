// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_capabilities.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalCapabilities _$ExperimentalCapabilitiesFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalCapabilities', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['backgroundSubagents']);
  final val = ExperimentalCapabilities(
    backgroundSubagents: $checkedConvert(
      'backgroundSubagents',
      (v) => v as bool,
    ),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalCapabilitiesToJson(
  ExperimentalCapabilities instance,
) => <String, dynamic>{'backgroundSubagents': instance.backgroundSubagents};
