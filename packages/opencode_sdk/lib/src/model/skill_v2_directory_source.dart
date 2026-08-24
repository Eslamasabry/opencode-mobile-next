//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'skill_v2_directory_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SkillV2DirectorySource {
  /// Returns a new [SkillV2DirectorySource] instance.
  SkillV2DirectorySource({required this.type, required this.path});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SkillV2DirectorySourceTypeEnum.unknownDefaultOpenApi,
  )
  final SkillV2DirectorySourceTypeEnum type;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SkillV2DirectorySource &&
            runtimeType == other.runtimeType &&
            equals([type, path], [other.type, other.path]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, path]);

  factory SkillV2DirectorySource.fromJson(Map<String, dynamic> json) =>
      _$SkillV2DirectorySourceFromJson(json);

  Map<String, dynamic> toJson() => _$SkillV2DirectorySourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SkillV2DirectorySourceTypeEnum {
  @JsonValue(r'directory')
  directory(r'directory'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SkillV2DirectorySourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
