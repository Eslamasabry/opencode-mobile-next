//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'prompt_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PromptSource {
  /// Returns a new [PromptSource] instance.
  PromptSource({required this.start, required this.end, required this.text});

  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final num start;

  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final num end;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PromptSource &&
            runtimeType == other.runtimeType &&
            equals([start, end, text], [other.start, other.end, other.text]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([start, end, text]);

  factory PromptSource.fromJson(Map<String, dynamic> json) =>
      _$PromptSourceFromJson(json);

  Map<String, dynamic> toJson() => _$PromptSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}
