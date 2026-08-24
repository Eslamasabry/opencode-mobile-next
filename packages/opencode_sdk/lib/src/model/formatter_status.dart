//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'formatter_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FormatterStatus {
  /// Returns a new [FormatterStatus] instance.
  FormatterStatus({
    required this.name,

    required this.extensions,

    required this.enabled,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'extensions', required: true, includeIfNull: false)
  final List<String> extensions;

  @JsonKey(name: r'enabled', required: true, includeIfNull: false)
  final bool enabled;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FormatterStatus &&
            runtimeType == other.runtimeType &&
            equals(
              [name, extensions, enabled],
              [other.name, other.extensions, other.enabled],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, extensions, enabled]);

  factory FormatterStatus.fromJson(Map<String, dynamic> json) =>
      _$FormatterStatusFromJson(json);

  Map<String, dynamic> toJson() => _$FormatterStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}
