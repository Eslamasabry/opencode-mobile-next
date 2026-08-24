//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_tui_command_execute_schema2_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_command_execute_schema2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiCommandExecuteSchema2 {
  /// Returns a new [EventTuiCommandExecuteSchema2] instance.
  EventTuiCommandExecuteSchema2({
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
        EventTuiCommandExecuteSchema2TypeEnum.unknownDefaultOpenApi,
  )
  final EventTuiCommandExecuteSchema2TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventTuiCommandExecuteSchema2Properties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiCommandExecuteSchema2 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventTuiCommandExecuteSchema2.fromJson(Map<String, dynamic> json) =>
      _$EventTuiCommandExecuteSchema2FromJson(json);

  Map<String, dynamic> toJson() => _$EventTuiCommandExecuteSchema2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTuiCommandExecuteSchema2TypeEnum {
  @JsonValue(r'tui.command.execute')
  tuiPeriodCommandPeriodExecute(r'tui.command.execute'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTuiCommandExecuteSchema2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
