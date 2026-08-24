//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_commands.dart';
import 'package:opencode_sdk/src/model/project_icon.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_update_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectUpdateRequest {
  /// Returns a new [ProjectUpdateRequest] instance.
  ProjectUpdateRequest({this.name, this.icon, this.commands});

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'icon', required: false, includeIfNull: false)
  final ProjectIcon? icon;

  @JsonKey(name: r'commands', required: false, includeIfNull: false)
  final ProjectCommands? commands;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectUpdateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [name, icon, commands],
              [other.name, other.icon, other.commands],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, icon, commands]);

  factory ProjectUpdateRequest.fromJson(Map<String, dynamic> json) =>
      _$ProjectUpdateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectUpdateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
