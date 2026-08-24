//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tool_state_error_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStateError {
  /// Returns a new [ToolStateError] instance.
  ToolStateError({
    required this.status,

    required this.input,

    required this.error,

    this.metadata,

    required this.time,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolStateErrorStatusEnum.unknownDefaultOpenApi,
  )
  final ToolStateErrorStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final ToolStateErrorTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStateError &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, error, metadata, time],
              [
                other.status,
                other.input,
                other.error,
                other.metadata,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([status, input, error, metadata, time]);

  factory ToolStateError.fromJson(Map<String, dynamic> json) =>
      _$ToolStateErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStateErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolStateErrorStatusEnum {
  @JsonValue(r'error')
  error(r'error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolStateErrorStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
