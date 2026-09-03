//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union015_any_of_value_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion015AnyOfValueAnyOf1 {
  /// Returns a new [OpencodeSdkRawUnion015AnyOfValueAnyOf1] instance.
  OpencodeSdkRawUnion015AnyOfValueAnyOf1({
    required this.command,

    this.extensions,

    this.disabled,

    this.env,

    this.initialization,
  });

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final List<String> command;

  @JsonKey(name: r'extensions', required: false, includeIfNull: false)
  final List<String>? extensions;

  @JsonKey(name: r'disabled', required: false, includeIfNull: false)
  final bool? disabled;

  @JsonKey(name: r'env', required: false, includeIfNull: false)
  final Map<String, String>? env;

  @JsonKey(name: r'initialization', required: false, includeIfNull: false)
  final Object? initialization;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion015AnyOfValueAnyOf1 &&
            runtimeType == other.runtimeType &&
            equals(
              [command, extensions, disabled, env, initialization],
              [
                other.command,
                other.extensions,
                other.disabled,
                other.env,
                other.initialization,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([command, extensions, disabled, env, initialization]);

  factory OpencodeSdkRawUnion015AnyOfValueAnyOf1.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion015AnyOfValueAnyOf1FromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion015AnyOfValueAnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}
