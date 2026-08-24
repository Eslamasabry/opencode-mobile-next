//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class File {
  /// Returns a new [File] instance.
  File({
    required this.path,

    required this.added,

    required this.removed,

    required this.status,
  });

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  // minimum: 0
  @JsonKey(name: r'added', required: true, includeIfNull: false)
  final int added;

  // minimum: 0
  @JsonKey(name: r'removed', required: true, includeIfNull: false)
  final int removed;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileStatusEnum.unknownDefaultOpenApi,
  )
  final FileStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is File &&
            runtimeType == other.runtimeType &&
            equals(
              [path, added, removed, status],
              [other.path, other.added, other.removed, other.status],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([path, added, removed, status]);

  factory File.fromJson(Map<String, dynamic> json) => _$FileFromJson(json);

  Map<String, dynamic> toJson() => _$FileToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileStatusEnum {
  @JsonValue(r'added')
  added(r'added'),
  @JsonValue(r'deleted')
  deleted(r'deleted'),
  @JsonValue(r'modified')
  modified(r'modified'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
