// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_snapshot.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantSnapshot _$SessionMessageAssistantSnapshotFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantSnapshot', json, ($checkedConvert) {
  final val = SessionMessageAssistantSnapshot(
    start: $checkedConvert('start', (v) => v as String?),
    end: $checkedConvert('end', (v) => v as String?),
    files: $checkedConvert(
      'files',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantSnapshotToJson(
  SessionMessageAssistantSnapshot instance,
) => <String, dynamic>{
  'start': ?instance.start,
  'end': ?instance.end,
  'files': ?instance.files,
};
