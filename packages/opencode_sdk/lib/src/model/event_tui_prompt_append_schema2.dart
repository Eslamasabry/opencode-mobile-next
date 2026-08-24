//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/find_text200_response_inner_path.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_prompt_append_schema2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiPromptAppendSchema2 {
  /// Returns a new [EventTuiPromptAppendSchema2] instance.
  EventTuiPromptAppendSchema2({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTuiPromptAppendSchema2TypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiPromptAppendSchema2TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final FindText200ResponseInnerPath properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiPromptAppendSchema2 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventTuiPromptAppendSchema2.fromJson(Map<String, dynamic> json) =>
      _$EventTuiPromptAppendSchema2FromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiPromptAppendSchema2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiPromptAppendSchema2TypeEnum {
  @JsonValue(r'tui.prompt.append')
  tuiPeriodPromptPeriodAppend(r'tui.prompt.append'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiPromptAppendSchema2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
