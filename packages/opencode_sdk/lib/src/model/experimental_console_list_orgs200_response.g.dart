// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_console_list_orgs200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalConsoleListOrgs200Response
_$ExperimentalConsoleListOrgs200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ExperimentalConsoleListOrgs200Response', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['orgs']);
      final val = ExperimentalConsoleListOrgs200Response(
        orgs: $checkedConvert(
          'orgs',
          (v) => (v as List<dynamic>)
              .map(
                (e) => ExperimentalConsoleListOrgs200ResponseOrgsInner.fromJson(
                  e as Map<String, dynamic>,
                ),
              )
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ExperimentalConsoleListOrgs200ResponseToJson(
  ExperimentalConsoleListOrgs200Response instance,
) => <String, dynamic>{'orgs': instance.orgs.map((e) => e.toJson()).toList()};
