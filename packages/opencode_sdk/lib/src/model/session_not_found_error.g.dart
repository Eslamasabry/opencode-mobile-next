// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNotFoundError _$SessionNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'sessionID', 'message']);
  final val = SessionNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$SessionNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: SessionNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$SessionNotFoundErrorToJson(
  SessionNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$SessionNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'sessionID': instance.sessionID,
  'message': instance.message,
};

const _$SessionNotFoundErrorTagEnumEnumMap = {
  SessionNotFoundErrorTagEnum.sessionNotFoundError: 'SessionNotFoundError',
  SessionNotFoundErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
