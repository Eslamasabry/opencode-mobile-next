// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_error_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionErrorProperties _$EventSessionErrorPropertiesFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionErrorProperties', json, ($checkedConvert) {
  final val = EventSessionErrorProperties(
    sessionID: $checkedConvert('sessionID', (v) => v as String?),
    error: $checkedConvert(
      'error',
      (v) => v == null ? null : OpencodeSdkRawUnion036.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionErrorPropertiesToJson(
  EventSessionErrorProperties instance,
) => <String, dynamic>{
  'sessionID': ?instance.sessionID,
  'error': ?instance.error?.toJson(),
};
