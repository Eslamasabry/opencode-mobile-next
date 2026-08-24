// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationInfo _$IntegrationInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationInfo', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'name', 'methods', 'connections'],
      );
      final val = IntegrationInfo(
        id: $checkedConvert('id', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String),
        methods: $checkedConvert(
          'methods',
          (v) => (v as List<dynamic>).map(IntegrationMethod.fromJson).toList(),
        ),
        connections: $checkedConvert(
          'connections',
          (v) => (v as List<dynamic>).map(ConnectionInfo.fromJson).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationInfoToJson(IntegrationInfo instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'methods': instance.methods.map((e) => e.toJson()).toList(),
      'connections': instance.connections.map((e) => e.toJson()).toList(),
    };
