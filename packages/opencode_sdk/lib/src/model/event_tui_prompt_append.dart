//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/find_text200_response_inner_path.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_prompt_append.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiPromptAppend {
  /// Returns a new [EventTuiPromptAppend] instance.
  EventTuiPromptAppend({required this.type, required this.properties});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTuiPromptAppendTypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiPromptAppendTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final FindText200ResponseInnerPath properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiPromptAppend &&
            runtimeType == other.runtimeType &&
            equals([type, properties], [other.type, other.properties]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, properties]);

  factory EventTuiPromptAppend.fromJson(Map<String, dynamic> json) =>
      _$EventTuiPromptAppendFromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiPromptAppendToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiPromptAppendTypeEnum {
  @JsonValue(r'tui.prompt.append')
  tuiPeriodPromptPeriodAppend(r'tui.prompt.append'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiPromptAppendTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
