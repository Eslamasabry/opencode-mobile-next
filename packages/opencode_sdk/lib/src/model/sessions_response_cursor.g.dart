// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sessions_response_cursor.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionsResponseCursor _$SessionsResponseCursorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionsResponseCursor', json, ($checkedConvert) {
  final val = SessionsResponseCursor(
    previous: $checkedConvert('previous', (v) => v as String?),
    next: $checkedConvert('next', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SessionsResponseCursorToJson(
  SessionsResponseCursor instance,
) => <String, dynamic>{'previous': ?instance.previous, 'next': ?instance.next};
