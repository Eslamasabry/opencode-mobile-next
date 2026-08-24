//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'model_capabilities_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ModelCapabilitiesInput {
  /// Returns a new [ModelCapabilitiesInput] instance.
  ModelCapabilitiesInput({
    required this.text,

    required this.audio,

    required this.image,

    required this.video,

    required this.pdf,
  });

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final bool text;

  @JsonKey(name: r'audio', required: true, includeIfNull: false)
  final bool audio;

  @JsonKey(name: r'image', required: true, includeIfNull: false)
  final bool image;

  @JsonKey(name: r'video', required: true, includeIfNull: false)
  final bool video;

  @JsonKey(name: r'pdf', required: true, includeIfNull: false)
  final bool pdf;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ModelCapabilitiesInput &&
            runtimeType == other.runtimeType &&
            equals(
              [text, audio, image, video, pdf],
              [other.text, other.audio, other.image, other.video, other.pdf],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([text, audio, image, video, pdf]);

  factory ModelCapabilitiesInput.fromJson(Map<String, dynamic> json) =>
      _$ModelCapabilitiesInputFromJson(json);

  Map<String, dynamic> toJson() => _$ModelCapabilitiesInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}
