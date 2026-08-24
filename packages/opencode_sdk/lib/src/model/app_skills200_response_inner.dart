//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'app_skills200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AppSkills200ResponseInner {
  /// Returns a new [AppSkills200ResponseInner] instance.
  AppSkills200ResponseInner({
    required this.name,

    this.description,

    required this.location,

    required this.content,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final String location;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final String content;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AppSkills200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals(
              [name, description, location, content],
              [other.name, other.description, other.location, other.content],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, description, location, content]);

  factory AppSkills200ResponseInner.fromJson(Map<String, dynamic> json) =>
      _$AppSkills200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() => _$AppSkills200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
