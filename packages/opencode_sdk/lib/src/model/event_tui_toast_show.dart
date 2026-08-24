//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/tui_show_toast_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_toast_show.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiToastShow {
  /// Returns a new [EventTuiToastShow] instance.
  EventTuiToastShow({required this.type, required this.properties});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTuiToastShowTypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiToastShowTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final TuiShowToastRequest properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiToastShow &&
            runtimeType == other.runtimeType &&
            equals([type, properties], [other.type, other.properties]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, properties]);

  factory EventTuiToastShow.fromJson(Map<String, dynamic> json) =>
      _$EventTuiToastShowFromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiToastShowToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiToastShowTypeEnum {
  @JsonValue(r'tui.toast.show')
  tuiPeriodToastPeriodShow(r'tui.toast.show'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiToastShowTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
