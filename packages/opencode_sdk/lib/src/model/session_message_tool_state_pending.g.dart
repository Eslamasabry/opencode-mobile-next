// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_tool_state_pending.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageToolStatePending _$SessionMessageToolStatePendingFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageToolStatePending', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['status', 'input']);
  final val = SessionMessageToolStatePending(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$SessionMessageToolStatePendingStatusEnumEnumMap,
        v,
        unknownValue:
            SessionMessageToolStatePendingStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    input: $checkedConvert('input', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageToolStatePendingToJson(
  SessionMessageToolStatePending instance,
) => <String, dynamic>{
  'status': _$SessionMessageToolStatePendingStatusEnumEnumMap[instance.status]!,
  'input': instance.input,
};

const _$SessionMessageToolStatePendingStatusEnumEnumMap = {
  SessionMessageToolStatePendingStatusEnum.pending: 'pending',
  SessionMessageToolStatePendingStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
