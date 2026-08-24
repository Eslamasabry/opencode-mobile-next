// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_diff_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionDiffData _$SessionDiffDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionDiffData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'diff']);
      final val = SessionDiffData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        diff: $checkedConvert(
          'diff',
          (v) => (v as List<dynamic>)
              .map((e) => SnapshotFileDiff.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionDiffDataToJson(SessionDiffData instance) =>
    <String, dynamic>{
      'sessionID': instance.sessionID,
      'diff': instance.diff.map((e) => e.toJson()).toList(),
    };
