// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'revert_state.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RevertState _$RevertStateFromJson(Map<String, dynamic> json) =>
    $checkedCreate('RevertState', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['messageID']);
      final val = RevertState(
        messageID: $checkedConvert('messageID', (v) => v as String),
        partID: $checkedConvert('partID', (v) => v as String?),
        snapshot: $checkedConvert('snapshot', (v) => v as String?),
        diff: $checkedConvert('diff', (v) => v as String?),
        files: $checkedConvert(
          'files',
          (v) => (v as List<dynamic>?)
              ?.map((e) => FileDiff.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$RevertStateToJson(RevertState instance) =>
    <String, dynamic>{
      'messageID': instance.messageID,
      'partID': ?instance.partID,
      'snapshot': ?instance.snapshot,
      'diff': ?instance.diff,
      'files': ?instance.files?.map((e) => e.toJson()).toList(),
    };
