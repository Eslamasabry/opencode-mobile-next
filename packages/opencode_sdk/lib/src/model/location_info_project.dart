//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'location_info_project.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class LocationInfoProject {
  /// Returns a new [LocationInfoProject] instance.
  LocationInfoProject({required this.id, required this.directory});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is LocationInfoProject &&
            runtimeType == other.runtimeType &&
            equals([id, directory], [other.id, other.directory]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, directory]);

  factory LocationInfoProject.fromJson(Map<String, dynamic> json) =>
      _$LocationInfoProjectFromJson(json);

  Map<String, dynamic> toJson() => _$LocationInfoProjectToJson(this);

  String toString() {
    return toJson().toString();
  }
}
