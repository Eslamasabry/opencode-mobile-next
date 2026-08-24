// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_steal200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncSteal200Response _$SyncSteal200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncSteal200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID']);
  final val = SyncSteal200Response(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncSteal200ResponseToJson(
  SyncSteal200Response instance,
) => <String, dynamic>{'sessionID': instance.sessionID};
