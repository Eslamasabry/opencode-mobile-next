//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sessions_response_cursor.dart';
import 'package:opencode_sdk/src/model/session_v2_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sessions_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionsResponse {
  /// Returns a new [SessionsResponse] instance.
  SessionsResponse({required this.data, required this.cursor});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<SessionV2Info> data;

  @JsonKey(name: r'cursor', required: true, includeIfNull: false)
  final SessionsResponseCursor cursor;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionsResponse &&
            runtimeType == other.runtimeType &&
            equals([data, cursor], [other.data, other.cursor]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data, cursor]);

  factory SessionsResponse.fromJson(Map<String, dynamic> json) =>
      _$SessionsResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SessionsResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
