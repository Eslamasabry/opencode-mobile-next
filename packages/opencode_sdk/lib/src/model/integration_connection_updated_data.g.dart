// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_connection_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationConnectionUpdatedData _$IntegrationConnectionUpdatedDataFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('IntegrationConnectionUpdatedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['integrationID']);
      final val = IntegrationConnectionUpdatedData(
        integrationID: $checkedConvert('integrationID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationConnectionUpdatedDataToJson(
  IntegrationConnectionUpdatedData instance,
) => <String, dynamic>{'integrationID': instance.integrationID};
