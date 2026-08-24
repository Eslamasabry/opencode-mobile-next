//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionNotFoundError {
  /// Returns a new [SessionNotFoundError] instance.
  SessionNotFoundError({
    required this.tag,

    required this.sessionID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final SessionNotFoundErrorTagEnum tag;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, sessionID, message],
              [other.tag, other.sessionID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, sessionID, message]);

  factory SessionNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$SessionNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$SessionNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionNotFoundErrorTagEnum {
  @JsonValue(r'SessionNotFoundError')
  sessionNotFoundError(r'SessionNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
