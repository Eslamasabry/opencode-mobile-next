//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'command_executed_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CommandExecutedData {
  /// Returns a new [CommandExecutedData] instance.
  CommandExecutedData({
    required this.name,

    required this.sessionID,

    required this.arguments,

    required this.messageID,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'arguments', required: true, includeIfNull: false)
  final String arguments;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CommandExecutedData &&
            runtimeType == other.runtimeType &&
            equals(
              [name, sessionID, arguments, messageID],
              [other.name, other.sessionID, other.arguments, other.messageID],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, sessionID, arguments, messageID]);

  factory CommandExecutedData.fromJson(Map<String, dynamic> json) =>
      _$CommandExecutedDataFromJson(json);

  Map<String, dynamic> toJson() => _$CommandExecutedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
