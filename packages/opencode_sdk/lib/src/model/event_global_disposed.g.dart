// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_global_disposed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventGlobalDisposed _$EventGlobalDisposedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventGlobalDisposed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventGlobalDisposed(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventGlobalDisposedTypeEnumEnumMap,
            v,
            unknownValue: EventGlobalDisposedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert('properties', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$EventGlobalDisposedToJson(
  EventGlobalDisposed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventGlobalDisposedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventGlobalDisposedTypeEnumEnumMap = {
  EventGlobalDisposedTypeEnum.globalPeriodDisposed: 'global.disposed',
  EventGlobalDisposedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
