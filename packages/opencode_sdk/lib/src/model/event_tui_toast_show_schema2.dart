//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tui_show_toast_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_toast_show_schema2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiToastShowSchema2 {
  /// Returns a new [EventTuiToastShowSchema2] instance.
  EventTuiToastShowSchema2({
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
    unknownEnumValue: EventTuiToastShowSchema2TypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiToastShowSchema2TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final TuiShowToastRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiToastShowSchema2 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventTuiToastShowSchema2.fromJson(Map<String, dynamic> json) =>
      _$EventTuiToastShowSchema2FromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiToastShowSchema2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiToastShowSchema2TypeEnum {
  @JsonValue(r'tui.toast.show')
  tuiPeriodToastPeriodShow(r'tui.toast.show'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiToastShowSchema2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
