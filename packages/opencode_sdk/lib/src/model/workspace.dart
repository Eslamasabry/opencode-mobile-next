//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union019.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Workspace {
  /// Returns a new [Workspace] instance.
  Workspace({
    required this.id,

    required this.type,

    required this.name,

    this.branch,

    this.directory,

    this.extra,

    required this.projectID,

    required this.timeUsed,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'type', required: true, includeIfNull: false)
  final String type;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  @JsonKey(name: r'directory', required: false, includeIfNull: false)
  final String? directory;

  @JsonKey(name: r'extra', required: false, includeIfNull: false)
  final Object? extra;

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  @JsonKey(name: r'timeUsed', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion019 timeUsed;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Workspace &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, name, branch, directory, extra, projectID, timeUsed],
              [
                other.id,
                other.type,
                other.name,
                other.branch,
                other.directory,
                other.extra,
                other.projectID,
                other.timeUsed,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        type,
        name,
        branch,
        directory,
        extra,
        projectID,
        timeUsed,
      ]);

  factory Workspace.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceToJson(this);

  String toString() {
    return toJson().toString();
  }
}
