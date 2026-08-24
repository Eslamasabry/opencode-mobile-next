// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_reasoning_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextReasoningStarted _$EventSessionNextReasoningStartedFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('EventSessionNextReasoningStarted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventSessionNextReasoningStarted(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventSessionNextReasoningStartedTypeEnumEnumMap,
            v,
            unknownValue:
                EventSessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => SyncEventSessionNextReasoningStartedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventSessionNextReasoningStartedToJson(
  EventSessionNextReasoningStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextReasoningStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextReasoningStartedTypeEnumEnumMap = {
  EventSessionNextReasoningStartedTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodStarted:
      'session.next.reasoning.started',
  EventSessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
