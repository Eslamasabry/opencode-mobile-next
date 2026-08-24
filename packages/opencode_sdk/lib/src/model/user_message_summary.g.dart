// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_message_summary.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserMessageSummary _$UserMessageSummaryFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UserMessageSummary', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['diffs']);
      final val = UserMessageSummary(
        title: $checkedConvert('title', (v) => v as String?),
        body: $checkedConvert('body', (v) => v as String?),
        diffs: $checkedConvert(
          'diffs',
          (v) => (v as List<dynamic>)
              .map((e) => SnapshotFileDiff.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$UserMessageSummaryToJson(UserMessageSummary instance) =>
    <String, dynamic>{
      'title': ?instance.title,
      'body': ?instance.body,
      'diffs': instance.diffs.map((e) => e.toJson()).toList(),
    };
