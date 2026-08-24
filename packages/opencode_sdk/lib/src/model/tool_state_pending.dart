//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_state_pending.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolStatePending {
  /// Returns a new [ToolStatePending] instance.
  ToolStatePending({
    required this.status,

    required this.input,

    required this.raw,
  });

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolStatePendingStatusEnum.unknownDefaultOpenApi,
  )
  final ToolStatePendingStatusEnum status;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final Object input;

  @JsonKey(name: r'raw', required: true, includeIfNull: false)
  final String raw;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolStatePending &&
            runtimeType == other.runtimeType &&
            equals(
              [status, input, raw],
              [other.status, other.input, other.raw],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([status, input, raw]);

  factory ToolStatePending.fromJson(Map<String, dynamic> json) =>
      _$ToolStatePendingFromJson(json);

  Map<String, dynamic> toJson() => _$ToolStatePendingToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolStatePendingStatusEnum {
  @JsonValue(r'pending')
  pending(r'pending'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolStatePendingStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
