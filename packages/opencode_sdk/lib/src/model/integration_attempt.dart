//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_attempt_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_attempt.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationAttempt {
  /// Returns a new [IntegrationAttempt] instance.
  IntegrationAttempt({
    required this.attemptID,

    required this.url,

    required this.instructions,

    required this.mode,

    required this.time,
  });

  @JsonKey(name: r'attemptID', required: true, includeIfNull: false)
  final String attemptID;

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  @JsonKey(name: r'instructions', required: true, includeIfNull: false)
  final String instructions;

  @JsonKey(
    name: r'mode',
    required: true,
    includeIfNull: false,
    unknownEnumValue: IntegrationAttemptModeEnum.unknownDefaultOpenApi,
  )
  final IntegrationAttemptModeEnum mode;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final IntegrationAttemptTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationAttempt &&
            runtimeType == other.runtimeType &&
            equals(
              [attemptID, url, instructions, mode, time],
              [
                other.attemptID,
                other.url,
                other.instructions,
                other.mode,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([attemptID, url, instructions, mode, time]);

  factory IntegrationAttempt.fromJson(Map<String, dynamic> json) =>
      _$IntegrationAttemptFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationAttemptToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum IntegrationAttemptModeEnum {
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'code')
  code(r'code'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const IntegrationAttemptModeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
