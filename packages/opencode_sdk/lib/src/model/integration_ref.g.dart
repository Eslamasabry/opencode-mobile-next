// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_ref.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationRef _$IntegrationRefFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationRef', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'name']);
      final val = IntegrationRef(
        id: $checkedConvert('id', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationRefToJson(IntegrationRef instance) =>
    <String, dynamic>{'id': instance.id, 'name': instance.name};
