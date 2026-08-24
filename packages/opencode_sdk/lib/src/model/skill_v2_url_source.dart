//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'skill_v2_url_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SkillV2UrlSource {
  /// Returns a new [SkillV2UrlSource] instance.
  SkillV2UrlSource({required this.type, required this.url});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SkillV2UrlSourceTypeEnum.unknownDefaultOpenApi,
  )
  final SkillV2UrlSourceTypeEnum type;

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SkillV2UrlSource &&
            runtimeType == other.runtimeType &&
            equals([type, url], [other.type, other.url]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, url]);

  factory SkillV2UrlSource.fromJson(Map<String, dynamic> json) =>
      _$SkillV2UrlSourceFromJson(json);

  Map<String, dynamic> toJson() => _$SkillV2UrlSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SkillV2UrlSourceTypeEnum {
  @JsonValue(r'url')
  url(r'url'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SkillV2UrlSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
