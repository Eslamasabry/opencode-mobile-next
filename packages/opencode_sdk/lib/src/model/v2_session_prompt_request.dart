//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/prompt_input.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_prompt_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPromptRequest {
  /// Returns a new [V2SessionPromptRequest] instance.
  V2SessionPromptRequest({
    this.id,

    required this.prompt,

    this.delivery,

    this.resume,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'prompt', required: true, includeIfNull: false)
  final PromptInput prompt;

  @JsonKey(
    name: r'delivery',
    required: false,
    includeIfNull: false,
    unknownEnumValue: V2SessionPromptRequestDeliveryEnum.unknownDefaultOpenApi,
  )
  final V2SessionPromptRequestDeliveryEnum? delivery;

  @JsonKey(name: r'resume', required: false, includeIfNull: false)
  final bool? resume;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPromptRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, prompt, delivery, resume],
              [other.id, other.prompt, other.delivery, other.resume],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, prompt, delivery, resume]);

  factory V2SessionPromptRequest.fromJson(Map<String, dynamic> json) =>
      _$V2SessionPromptRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2SessionPromptRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum V2SessionPromptRequestDeliveryEnum {
  @JsonValue(r'steer')
  steer(r'steer'),
  @JsonValue(r'queue')
  queue(r'queue'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const V2SessionPromptRequestDeliveryEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
