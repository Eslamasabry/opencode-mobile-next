//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_directories_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectDirectoriesUpdatedData {
  /// Returns a new [ProjectDirectoriesUpdatedData] instance.
  ProjectDirectoriesUpdatedData({required this.projectID});

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectDirectoriesUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([projectID], [other.projectID]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([projectID]);

  factory ProjectDirectoriesUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$ProjectDirectoriesUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectDirectoriesUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
