//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_tui_command_execute_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_command_execute.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiCommandExecute {
  /// Returns a new [EventTuiCommandExecute] instance.
  EventTuiCommandExecute({required this.type, required this.properties});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTuiCommandExecuteTypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiCommandExecuteTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventTuiCommandExecuteProperties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiCommandExecute &&
            runtimeType == other.runtimeType &&
            equals([type, properties], [other.type, other.properties]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, properties]);

  factory EventTuiCommandExecute.fromJson(Map<String, dynamic> json) =>
      _$EventTuiCommandExecuteFromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiCommandExecuteToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiCommandExecuteTypeEnum {
  @JsonValue(r'tui.command.execute')
  tuiPeriodCommandPeriodExecute(r'tui.command.execute'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiCommandExecuteTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
