//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sessions_response_cursor.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionsResponseCursor {
  /// Returns a new [SessionsResponseCursor] instance.
  SessionsResponseCursor({this.previous, this.next});

  @JsonKey(name: r'previous', required: false, includeIfNull: false)
  final String? previous;

  @JsonKey(name: r'next', required: false, includeIfNull: false)
  final String? next;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionsResponseCursor &&
            runtimeType == other.runtimeType &&
            equals([previous, next], [other.previous, other.next]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([previous, next]);

  factory SessionsResponseCursor.fromJson(Map<String, dynamic> json) =>
      _$SessionsResponseCursorFromJson(json);

  Map<String, dynamic> toJson() => _$SessionsResponseCursorToJson(this);

  String toString() {
    return toJson().toString();
  }
}
