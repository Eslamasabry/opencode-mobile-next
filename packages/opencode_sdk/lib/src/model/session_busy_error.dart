//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_busy_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionBusyError {
  /// Returns a new [SessionBusyError] instance.
  SessionBusyError({
    required this.tag,

    required this.sessionID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionBusyErrorTagEnum.unknownDefaultOpenApi,
  )
  final SessionBusyErrorTagEnum tag;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionBusyError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, sessionID, message],
              [other.tag, other.sessionID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, sessionID, message]);

  factory SessionBusyError.fromJson(Map<String, dynamic> json) =>
      _$SessionBusyErrorFromJson(json);

  Map<String, dynamic> toJson() => _$SessionBusyErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionBusyErrorTagEnum {
  @JsonValue(r'SessionBusyError')
  sessionBusyError(r'SessionBusyError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionBusyErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
