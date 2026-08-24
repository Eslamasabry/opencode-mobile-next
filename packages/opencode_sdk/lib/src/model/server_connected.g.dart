// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'server_connected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ServerConnected _$ServerConnectedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ServerConnected', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = ServerConnected(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ServerConnectedTypeEnumEnumMap,
            v,
            unknownValue: ServerConnectedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert('data', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ServerConnectedToJson(ServerConnected instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$ServerConnectedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$ServerConnectedTypeEnumEnumMap = {
  ServerConnectedTypeEnum.serverPeriodConnected: 'server.connected',
  ServerConnectedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
