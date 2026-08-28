//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_v2_capabilities.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelV2Capabilities {
  /// Returns a new [ModelV2Capabilities] instance.
  ModelV2Capabilities({
    required this.tools,

    required this.input,

    required this.output,
  });

  @JsonKey(name: r'tools', required: true, includeIfNull: false)
  final bool tools;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final List<String> input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final List<String> output;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelV2Capabilities &&
            runtimeType == other.runtimeType &&
            equals(
              [tools, input, output],
              [other.tools, other.input, other.output],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tools, input, output]);

  factory ModelV2Capabilities.fromJson(Map<String, dynamic> json) =>
      _$ModelV2CapabilitiesFromJson(json);

  Map<String, dynamic> toJson() => _$ModelV2CapabilitiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
