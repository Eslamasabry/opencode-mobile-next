//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014_any_of_value_any_of.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union014_any_of_value_any_of1.dart';
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
    required this.disabled,

    required this.command,

    this.extensions,

    this.env,

    this.initialization,
  });

  @JsonKey(name: r'disabled', required: true, includeIfNull: false)
  final bool disabled;

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final List<String> command;

  @JsonKey(name: r'extensions', required: false, includeIfNull: false)
  final List<String>? extensions;

  @JsonKey(name: r'env', required: false, includeIfNull: false)
  final Map<String, String>? env;

  @JsonKey(name: r'initialization', required: false, includeIfNull: false)
  final Object? initialization;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion014AnyOfValue &&
            runtimeType == other.runtimeType &&
            equals(
              [disabled, command, extensions, env, initialization],
              [
                other.disabled,
                other.command,
                other.extensions,
                other.env,
                other.initialization,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([disabled, command, extensions, env, initialization]);

  factory OpencodeSdkRawUnion014AnyOfValue.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion014AnyOfValueFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion014AnyOfValueToJson(this);

  String toString() {
    return toJson().toString();
  }
}
