// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'global_upgrade_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GlobalUpgradeRequest _$GlobalUpgradeRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('GlobalUpgradeRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['target']);
  final val = GlobalUpgradeRequest(
    target: $checkedConvert('target', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$GlobalUpgradeRequestToJson(
  GlobalUpgradeRequest instance,
) => <String, dynamic>{'target': instance.target};
