//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectTime {
  /// Returns a new [ProjectTime] instance.
  ProjectTime({required this.created, required this.updated, this.initialized});

  // minimum: 0
  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final int created;

  // minimum: 0
  @JsonKey(name: r'updated', required: true, includeIfNull: false)
  final int updated;

  // minimum: 0
  @JsonKey(name: r'initialized', required: false, includeIfNull: false)
  final int? initialized;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectTime &&
            runtimeType == other.runtimeType &&
            equals(
              [created, updated, initialized],
              [other.created, other.updated, other.initialized],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([created, updated, initialized]);

  factory ProjectTime.fromJson(Map<String, dynamic> json) =>
      _$ProjectTimeFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
