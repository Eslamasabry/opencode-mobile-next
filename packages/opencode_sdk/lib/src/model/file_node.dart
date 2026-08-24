//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_node.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileNode {
  /// Returns a new [FileNode] instance.
  FileNode({
    required this.name,

    required this.path,

    required this.absolute,

    required this.type,

    required this.ignored,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'absolute', required: true, includeIfNull: false)
  final String absolute;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileNodeTypeEnum.unknownDefaultOpenApi,
  )
  final FileNodeTypeEnum type;

  @JsonKey(name: r'ignored', required: true, includeIfNull: false)
  final bool ignored;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileNode &&
            runtimeType == other.runtimeType &&
            equals(
              [name, path, absolute, type, ignored],
              [
                other.name,
                other.path,
                other.absolute,
                other.type,
                other.ignored,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, path, absolute, type, ignored]);

  factory FileNode.fromJson(Map<String, dynamic> json) =>
      _$FileNodeFromJson(json);

  Map<String, dynamic> toJson() => _$FileNodeToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileNodeTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'directory')
  directory(r'directory'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileNodeTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
