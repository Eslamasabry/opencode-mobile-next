//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/api_error.dart';
import 'package:opencode_sdk/src/model/retry_part_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'retry_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class RetryPart {
  /// Returns a new [RetryPart] instance.
  RetryPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.attempt,

    required this.error,

    required this.time,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: RetryPartTypeEnum.unknownDefaultOpenApi,
  )
  final RetryPartTypeEnum type;

  // minimum: 0
  @JsonKey(name: r'attempt', required: true, includeIfNull: false)
  final int attempt;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final APIError error;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final RetryPartTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is RetryPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, attempt, error, time],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.attempt,
                other.error,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        messageID,
        type,
        attempt,
        error,
        time,
      ]);

  factory RetryPart.fromJson(Map<String, dynamic> json) =>
      _$RetryPartFromJson(json);

  Map<String, dynamic> toJson() => _$RetryPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum RetryPartTypeEnum {
  @JsonValue(r'retry')
  retry(r'retry'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const RetryPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
