//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tui_select_session_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_session_select_schema2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiSessionSelectSchema2 {
  /// Returns a new [EventTuiSessionSelectSchema2] instance.
  EventTuiSessionSelectSchema2({
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
    unknownEnumValue:
        EventTuiSessionSelectSchema2TypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiSessionSelectSchema2TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final TuiSelectSessionRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiSessionSelectSchema2 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventTuiSessionSelectSchema2.fromJson(Map<String, dynamic> json) =>
      _$EventTuiSessionSelectSchema2FromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiSessionSelectSchema2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiSessionSelectSchema2TypeEnum {
  @JsonValue(r'tui.session.select')
  tuiPeriodSessionPeriodSelect(r'tui.session.select'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiSessionSelectSchema2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
