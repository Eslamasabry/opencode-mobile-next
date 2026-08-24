//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sessions_response_cursor.dart';
import 'package:opencode_sdk/src/model/session_message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_messages_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessagesResponse {
  /// Returns a new [SessionMessagesResponse] instance.
  SessionMessagesResponse({required this.data, required this.cursor});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<SessionMessage> data;

  @JsonKey(name: r'cursor', required: true, includeIfNull: false)
  final SessionsResponseCursor cursor;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessagesResponse &&
            runtimeType == other.runtimeType &&
            equals([data, cursor], [other.data, other.cursor]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data, cursor]);

  factory SessionMessagesResponse.fromJson(Map<String, dynamic> json) =>
      _$SessionMessagesResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessagesResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
