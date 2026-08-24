// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_server_connected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventServerConnected _$EventServerConnectedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventServerConnected', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventServerConnected(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventServerConnectedTypeEnumEnumMap,
        v,
        unknownValue: EventServerConnectedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$EventServerConnectedToJson(
  EventServerConnected instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventServerConnectedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventServerConnectedTypeEnumEnumMap = {
  EventServerConnectedTypeEnum.serverPeriodConnected: 'server.connected',
  EventServerConnectedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
