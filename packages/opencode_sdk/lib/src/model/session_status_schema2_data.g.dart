// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_schema2_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusSchema2Data _$SessionStatusSchema2DataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionStatusSchema2Data', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'status']);
  final val = SessionStatusSchema2Data(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    status: $checkedConvert('status', (v) => SessionStatus.fromJson(v)),
  );
  return val;
});

Map<String, dynamic> _$SessionStatusSchema2DataToJson(
  SessionStatusSchema2Data instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'status': instance.status.toJson(),
};
