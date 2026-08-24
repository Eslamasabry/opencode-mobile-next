//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_part.dart';
import 'package:opencode_sdk/src/model/assistant_message.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_prompt200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionPrompt200Response {
  /// Returns a new [SessionPrompt200Response] instance.
  SessionPrompt200Response({required this.info, required this.parts});

  @JsonKey(name: r'info', required: true, includeIfNull: false)
  final AssistantMessage info;

  @JsonKey(name: r'parts', required: true, includeIfNull: false)
  final List<ModelPart> parts;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionPrompt200Response &&
            runtimeType == other.runtimeType &&
            equals([info, parts], [other.info, other.parts]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([info, parts]);

  factory SessionPrompt200Response.fromJson(Map<String, dynamic> json) =>
      _$SessionPrompt200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$SessionPrompt200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
