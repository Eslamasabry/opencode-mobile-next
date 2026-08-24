// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_console_list_orgs200_response_orgs_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalConsoleListOrgs200ResponseOrgsInner
_$ExperimentalConsoleListOrgs200ResponseOrgsInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalConsoleListOrgs200ResponseOrgsInner', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'accountID',
      'accountEmail',
      'accountUrl',
      'orgID',
      'orgName',
      'active',
    ],
  );
  final val = ExperimentalConsoleListOrgs200ResponseOrgsInner(
    accountID: $checkedConvert('accountID', (v) => v as String),
    accountEmail: $checkedConvert('accountEmail', (v) => v as String),
    accountUrl: $checkedConvert('accountUrl', (v) => v as String),
    orgID: $checkedConvert('orgID', (v) => v as String),
    orgName: $checkedConvert('orgName', (v) => v as String),
    active: $checkedConvert('active', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalConsoleListOrgs200ResponseOrgsInnerToJson(
  ExperimentalConsoleListOrgs200ResponseOrgsInner instance,
) => <String, dynamic>{
  'accountID': instance.accountID,
  'accountEmail': instance.accountEmail,
  'accountUrl': instance.accountUrl,
  'orgID': instance.orgID,
  'orgName': instance.orgName,
  'active': instance.active,
};
