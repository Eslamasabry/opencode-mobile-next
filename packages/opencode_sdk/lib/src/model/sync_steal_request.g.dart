// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_steal_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncStealRequest _$SyncStealRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncStealRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['sessionID']);
      final val = SyncStealRequest(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SyncStealRequestToJson(SyncStealRequest instance) =>
    <String, dynamic>{'sessionID': instance.sessionID};
