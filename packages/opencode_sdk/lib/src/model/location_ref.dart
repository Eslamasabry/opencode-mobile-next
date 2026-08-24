//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'location_ref.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class LocationRef {
  /// Returns a new [LocationRef] instance.
  LocationRef({required this.directory, this.workspaceID});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'workspaceID', required: false, includeIfNull: false)
  final String? workspaceID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is LocationRef &&
            runtimeType == other.runtimeType &&
            equals(
              [directory, workspaceID],
              [other.directory, other.workspaceID],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([directory, workspaceID]);

  factory LocationRef.fromJson(Map<String, dynamic> json) =>
      _$LocationRefFromJson(json);

  Map<String, dynamic> toJson() => _$LocationRefToJson(this);

  String toString() {
    return toJson().toString();
  }
}
