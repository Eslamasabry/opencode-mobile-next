// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_update_request_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionUpdateRequestTime _$SessionUpdateRequestTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionUpdateRequestTime', json, ($checkedConvert) {
  final val = SessionUpdateRequestTime(
    archived: $checkedConvert('archived', (v) => v as num?),
  );
  return val;
});

Map<String, dynamic> _$SessionUpdateRequestTimeToJson(
  SessionUpdateRequestTime instance,
) => <String, dynamic>{'archived': ?instance.archived};
