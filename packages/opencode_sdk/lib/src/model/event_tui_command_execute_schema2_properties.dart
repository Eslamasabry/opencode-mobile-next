//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union019.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_command_execute_schema2_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiCommandExecuteSchema2Properties {
  /// Returns a new [EventTuiCommandExecuteSchema2Properties] instance.
  EventTuiCommandExecuteSchema2Properties({required this.command});

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion019 command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiCommandExecuteSchema2Properties &&
            runtimeType == other.runtimeType &&
            equals([command], [other.command]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([command]);

  factory EventTuiCommandExecuteSchema2Properties.fromJson(
    Map<String, dynamic> json,
  ) => _$EventTuiCommandExecuteSchema2PropertiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventTuiCommandExecuteSchema2PropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
