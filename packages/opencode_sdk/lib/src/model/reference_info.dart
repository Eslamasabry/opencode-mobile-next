//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/reference_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'reference_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ReferenceInfo {
  /// Returns a new [ReferenceInfo] instance.
  ReferenceInfo({
    required this.name,

    required this.path,

    this.description,

    this.hidden,

    required this.source_,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  @JsonKey(name: r'source', required: true, includeIfNull: false)
  final ReferenceSource source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ReferenceInfo &&
            runtimeType == other.runtimeType &&
            equals(
              [name, path, description, hidden, source_],
              [
                other.name,
                other.path,
                other.description,
                other.hidden,
                other.source_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, path, description, hidden, source_]);

  factory ReferenceInfo.fromJson(Map<String, dynamic> json) =>
      _$ReferenceInfoFromJson(json);

  Map<String, dynamic> toJson() => _$ReferenceInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
