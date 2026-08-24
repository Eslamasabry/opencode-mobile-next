// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_diff.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionDiff _$EventSessionDiffFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventSessionDiff', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionDiff(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionDiffTypeEnumEnumMap,
            v,
            unknownValue: EventSessionDiffTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SessionDiffData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionDiffToJson(EventSessionDiff instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventSessionDiffTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventSessionDiffTypeEnumEnumMap = {
  EventSessionDiffTypeEnum.sessionPeriodDiff: 'session.diff',
  EventSessionDiffTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
