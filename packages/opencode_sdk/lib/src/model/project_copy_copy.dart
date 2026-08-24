//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_copy_copy.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectCopyCopy {
  /// Returns a new [ProjectCopyCopy] instance.
  ProjectCopyCopy({required this.directory});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectCopyCopy &&
            runtimeType == other.runtimeType &&
            equals([directory], [other.directory]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([directory]);

  factory ProjectCopyCopy.fromJson(Map<String, dynamic> json) =>
      _$ProjectCopyCopyFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectCopyCopyToJson(this);

  String toString() {
    return toJson().toString();
  }
}
