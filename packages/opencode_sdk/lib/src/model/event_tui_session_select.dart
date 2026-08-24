//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tui_select_session_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_session_select.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiSessionSelect {
  /// Returns a new [EventTuiSessionSelect] instance.
  EventTuiSessionSelect({required this.type, required this.properties});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTuiSessionSelectTypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiSessionSelectTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final TuiSelectSessionRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiSessionSelect &&
            runtimeType == other.runtimeType &&
            equals([type, properties], [other.type, other.properties]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, properties]);

  factory EventTuiSessionSelect.fromJson(Map<String, dynamic> json) =>
      _$EventTuiSessionSelectFromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiSessionSelectToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiSessionSelectTypeEnum {
  @JsonValue(r'tui.session.select')
  tuiPeriodSessionPeriodSelect(r'tui.session.select'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiSessionSelectTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
