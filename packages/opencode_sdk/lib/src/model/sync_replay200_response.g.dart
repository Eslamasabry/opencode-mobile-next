// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_replay200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncReplay200Response _$SyncReplay200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncReplay200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID']);
  final val = SyncReplay200Response(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncReplay200ResponseToJson(
  SyncReplay200Response instance,
) => <String, dynamic>{'sessionID': instance.sessionID};
