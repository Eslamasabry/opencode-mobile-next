//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union017.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_tui_command_execute_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTuiCommandExecuteProperties {
  /// Returns a new [EventTuiCommandExecuteProperties] instance.
  EventTuiCommandExecuteProperties({required this.command});

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion017 command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTuiCommandExecuteProperties &&
            runtimeType == other.runtimeType &&
            equals([command], [other.command]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([command]);

  factory EventTuiCommandExecuteProperties.fromJson(
    Map<String, dynamic> json,
  ) => _$EventTuiCommandExecutePropertiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventTuiCommandExecutePropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
