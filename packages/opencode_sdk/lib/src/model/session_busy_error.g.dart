// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_busy_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionBusyError _$SessionBusyErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionBusyError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'sessionID', 'message']);
      final val = SessionBusyError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$SessionBusyErrorTagEnumEnumMap,
            v,
            unknownValue: SessionBusyErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$SessionBusyErrorToJson(SessionBusyError instance) =>
    <String, dynamic>{
      '_tag': _$SessionBusyErrorTagEnumEnumMap[instance.tag]!,
      'sessionID': instance.sessionID,
      'message': instance.message,
    };

const _$SessionBusyErrorTagEnumEnumMap = {
  SessionBusyErrorTagEnum.sessionBusyError: 'SessionBusyError',
  SessionBusyErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
