// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_revert.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionRevert _$SessionRevertFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionRevert', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['messageID']);
      final val = SessionRevert(
        messageID: $checkedConvert('messageID', (v) => v as String),
        partID: $checkedConvert('partID', (v) => v as String?),
        snapshot: $checkedConvert('snapshot', (v) => v as String?),
        diff: $checkedConvert('diff', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$SessionRevertToJson(SessionRevert instance) =>
    <String, dynamic>{
      'messageID': instance.messageID,
      'partID': ?instance.partID,
      'snapshot': ?instance.snapshot,
      'diff': ?instance.diff,
    };
