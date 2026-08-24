//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_input_admitted.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_prompt200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPrompt200Response {
  /// Returns a new [V2SessionPrompt200Response] instance.
  V2SessionPrompt200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SessionInputAdmitted data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPrompt200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionPrompt200Response.fromJson(Map<String, dynamic> json) =>
      _$V2SessionPrompt200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionPrompt200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
