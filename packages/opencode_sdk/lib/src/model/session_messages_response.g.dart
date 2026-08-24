// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_messages_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessagesResponse _$SessionMessagesResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessagesResponse', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data', 'cursor']);
  final val = SessionMessagesResponse(
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>).map(SessionMessage.fromJson).toList(),
    ),
    cursor: $checkedConvert(
      'cursor',
      (v) => SessionsResponseCursor.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessagesResponseToJson(
  SessionMessagesResponse instance,
) => <String, dynamic>{
  'data': instance.data.map((e) => e.toJson()).toList(),
  'cursor': instance.cursor.toJson(),
};
