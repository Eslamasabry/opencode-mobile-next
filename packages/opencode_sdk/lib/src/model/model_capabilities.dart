//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_capabilities_input.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union015.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_capabilities.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCapabilities {
  /// Returns a new [ModelCapabilities] instance.
  ModelCapabilities({
    required this.temperature,

    required this.reasoning,

    required this.attachment,

    required this.toolcall,

    required this.input,

    required this.output,

    required this.interleaved,
  });

  @JsonKey(name: r'temperature', required: true, includeIfNull: false)
  final bool temperature;

  @JsonKey(name: r'reasoning', required: true, includeIfNull: false)
  final bool reasoning;

  @JsonKey(name: r'attachment', required: true, includeIfNull: false)
  final bool attachment;

  @JsonKey(name: r'toolcall', required: true, includeIfNull: false)
  final bool toolcall;

  @JsonKey(name: r'input', required: true, includeIfNull: false)
  final ModelCapabilitiesInput input;

  @JsonKey(name: r'output', required: true, includeIfNull: false)
  final ModelCapabilitiesInput output;

  @JsonKey(name: r'interleaved', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion015 interleaved;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCapabilities &&
            runtimeType == other.runtimeType &&
            equals(
              [
                temperature,
                reasoning,
                attachment,
                toolcall,
                input,
                output,
                interleaved,
              ],
              [
                other.temperature,
                other.reasoning,
                other.attachment,
                other.toolcall,
                other.input,
                other.output,
                other.interleaved,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        temperature,
        reasoning,
        attachment,
        toolcall,
        input,
        output,
        interleaved,
      ]);

  factory ModelCapabilities.fromJson(Map<String, dynamic> json) =>
      _$ModelCapabilitiesFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCapabilitiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
