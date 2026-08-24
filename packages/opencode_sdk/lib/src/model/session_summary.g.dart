// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_summary.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionSummary _$SessionSummaryFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionSummary', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['additions', 'deletions', 'files']);
      final val = SessionSummary(
        additions: $checkedConvert('additions', (v) => v as num),
        deletions: $checkedConvert('deletions', (v) => v as num),
        files: $checkedConvert('files', (v) => v as num),
        diffs: $checkedConvert(
          'diffs',
          (v) => (v as List<dynamic>?)
              ?.map((e) => SnapshotFileDiff.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionSummaryToJson(SessionSummary instance) =>
    <String, dynamic>{
      'additions': instance.additions,
      'deletions': instance.deletions,
      'files': instance.files,
      'diffs': ?instance.diffs?.map((e) => e.toJson()).toList(),
    };
