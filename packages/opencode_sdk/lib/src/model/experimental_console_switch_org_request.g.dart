// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_console_switch_org_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalConsoleSwitchOrgRequest
_$ExperimentalConsoleSwitchOrgRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ExperimentalConsoleSwitchOrgRequest', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['accountID', 'orgID']);
      final val = ExperimentalConsoleSwitchOrgRequest(
        accountID: $checkedConvert('accountID', (v) => v as String),
        orgID: $checkedConvert('orgID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ExperimentalConsoleSwitchOrgRequestToJson(
  ExperimentalConsoleSwitchOrgRequest instance,
) => <String, dynamic>{
  'accountID': instance.accountID,
  'orgID': instance.orgID,
};
