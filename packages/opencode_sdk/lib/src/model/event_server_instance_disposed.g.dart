// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_server_instance_disposed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventServerInstanceDisposed _$EventServerInstanceDisposedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventServerInstanceDisposed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventServerInstanceDisposed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventServerInstanceDisposedTypeEnumEnumMap,
        v,
        unknownValue: EventServerInstanceDisposedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => EventServerInstanceDisposedProperties.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventServerInstanceDisposedToJson(
  EventServerInstanceDisposed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventServerInstanceDisposedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventServerInstanceDisposedTypeEnumEnumMap = {
  EventServerInstanceDisposedTypeEnum.serverPeriodInstancePeriodDisposed:
      'server.instance.disposed',
  EventServerInstanceDisposedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
