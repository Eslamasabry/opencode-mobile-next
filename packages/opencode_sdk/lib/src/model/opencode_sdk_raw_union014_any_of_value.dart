//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union014_any_of_value.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion014AnyOfValue {
  /// Returns a new [OpencodeSdkRawUnion014AnyOfValue] instance.
  OpencodeSdkRawUnion014AnyOfValue({
    this.disabled,

    this.command,

    this.environment,

    this.extensions,
  });

  @JsonKey(name: r'disabled', required: false, includeIfNull: false)
  final bool? disabled;

  @JsonKey(name: r'command', required: false, includeIfNull: false)
  final List<String>? command;

  @JsonKey(name: r'environment', required: false, includeIfNull: false)
  final Map<String, String>? environment;

  @JsonKey(name: r'extensions', required: false, includeIfNull: false)
  final List<String>? extensions;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion014AnyOfValue &&
            runtimeType == other.runtimeType &&
            equals(
              [disabled, command, environment, extensions],
              [
                other.disabled,
                other.command,
                other.environment,
                other.extensions,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([disabled, command, environment, extensions]);

  factory OpencodeSdkRawUnion014AnyOfValue.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion014AnyOfValueFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion014AnyOfValueToJson(this);

  String toString() {
    return toJson().toString();
  }
}
