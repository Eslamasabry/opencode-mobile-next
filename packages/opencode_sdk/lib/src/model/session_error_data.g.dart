// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionErrorData _$SessionErrorDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionErrorData', json, ($checkedConvert) {
      final val = SessionErrorData(
        sessionID: $checkedConvert('sessionID', (v) => v as String?),
        error: $checkedConvert(
          'error',
          (v) => v == null ? null : OpencodeSdkRawUnion034.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionErrorDataToJson(SessionErrorData instance) =>
    <String, dynamic>{
      'sessionID': ?instance.sessionID,
      'error': ?instance.error?.toJson(),
    };
