//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_skills.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigSkills {
  /// Returns a new [ConfigSkills] instance.
  ConfigSkills({this.paths, this.urls});

  @JsonKey(name: r'paths', required: false, includeIfNull: false)
  final List<String>? paths;

  @JsonKey(name: r'urls', required: false, includeIfNull: false)
  final List<String>? urls;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigSkills &&
            runtimeType == other.runtimeType &&
            equals([paths, urls], [other.paths, other.urls]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([paths, urls]);

  factory ConfigSkills.fromJson(Map<String, dynamic> json) =>
      _$ConfigSkillsFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigSkillsToJson(this);

  String toString() {
    return toJson().toString();
  }
}
