//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_status_any_of1_action.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusAnyOf1 {
  /// Returns a new [SessionStatusAnyOf1] instance.
  SessionStatusAnyOf1({
    required this.type,

    required this.attempt,

    required this.message,

    this.action,

    required this.next,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionStatusAnyOf1TypeEnum.unknownDefaultOpenApi,
  )
  final SessionStatusAnyOf1TypeEnum type;

  // minimum: 0
  @JsonKey(name: r'attempt', required: true, includeIfNull: false)
  final int attempt;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'action', required: false, includeIfNull: false)
  final SessionStatusAnyOf1Action? action;

  // minimum: 0
  @JsonKey(name: r'next', required: true, includeIfNull: false)
  final int next;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusAnyOf1 &&
            runtimeType == other.runtimeType &&
            equals(
              [type, attempt, message, action, next],
              [
                other.type,
                other.attempt,
                other.message,
                other.action,
                other.next,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, attempt, message, action, next]);

  factory SessionStatusAnyOf1.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusAnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusAnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionStatusAnyOf1TypeEnum {
  @JsonValue(r'retry')
  retry(r'retry'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionStatusAnyOf1TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
