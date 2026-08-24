// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_input_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolInputDelta _$SessionNextToolInputDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolInputDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolInputDelta(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolInputDeltaTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolInputDeltaTypeEnum.unknownDefaultOpenApi,
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
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SessionNextToolInputDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolInputDeltaToJson(
  SessionNextToolInputDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolInputDeltaTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolInputDeltaTypeEnumEnumMap = {
  SessionNextToolInputDeltaTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodDelta:
      'session.next.tool.input.delta',
  SessionNextToolInputDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
