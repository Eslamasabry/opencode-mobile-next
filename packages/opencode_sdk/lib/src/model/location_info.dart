//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_info_project.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'location_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class LocationInfo {
  /// Returns a new [LocationInfo] instance.
  LocationInfo({
    required this.directory,

    this.workspaceID,

    required this.project,
  });

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'workspaceID', required: false, includeIfNull: false)
  final String? workspaceID;

  @JsonKey(name: r'project', required: true, includeIfNull: false)
  final LocationInfoProject project;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is LocationInfo &&
            runtimeType == other.runtimeType &&
            equals(
              [directory, workspaceID, project],
              [other.directory, other.workspaceID, other.project],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([directory, workspaceID, project]);

  factory LocationInfo.fromJson(Map<String, dynamic> json) =>
      _$LocationInfoFromJson(json);

  Map<String, dynamic> toJson() => _$LocationInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
