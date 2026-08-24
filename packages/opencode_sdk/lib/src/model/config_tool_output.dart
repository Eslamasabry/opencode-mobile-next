//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_tool_output.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigToolOutput {
  /// Returns a new [ConfigToolOutput] instance.
  ConfigToolOutput({this.maxLines, this.maxBytes});

  @JsonKey(name: r'max_lines', required: false, includeIfNull: false)
  final int? maxLines;

  @JsonKey(name: r'max_bytes', required: false, includeIfNull: false)
  final int? maxBytes;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigToolOutput &&
            runtimeType == other.runtimeType &&
            equals([maxLines, maxBytes], [other.maxLines, other.maxBytes]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([maxLines, maxBytes]);

  factory ConfigToolOutput.fromJson(Map<String, dynamic> json) =>
      _$ConfigToolOutputFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigToolOutputToJson(this);

  String toString() {
    return toJson().toString();
  }
}
