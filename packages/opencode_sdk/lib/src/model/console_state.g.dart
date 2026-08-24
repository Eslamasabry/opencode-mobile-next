// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'console_state.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConsoleState _$ConsoleStateFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConsoleState', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['consoleManagedProviders', 'switchableOrgCount'],
      );
      final val = ConsoleState(
        consoleManagedProviders: $checkedConvert(
          'consoleManagedProviders',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        activeOrgName: $checkedConvert('activeOrgName', (v) => v as String?),
        switchableOrgCount: $checkedConvert(
          'switchableOrgCount',
          (v) => (v as num).toInt(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ConsoleStateToJson(ConsoleState instance) =>
    <String, dynamic>{
      'consoleManagedProviders': instance.consoleManagedProviders,
      'activeOrgName': ?instance.activeOrgName,
      'switchableOrgCount': instance.switchableOrgCount,
    };
