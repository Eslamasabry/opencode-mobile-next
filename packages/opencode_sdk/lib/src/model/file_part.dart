//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_part_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FilePart {
  /// Returns a new [FilePart] instance.
  FilePart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.mime,

    this.filename,

    required this.url,

    this.source_,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FilePartTypeEnum.unknownDefaultOpenApi,
  )
  final FilePartTypeEnum type;

  @JsonKey(name: r'mime', required: true, includeIfNull: false)
  final String mime;

  @JsonKey(name: r'filename', required: false, includeIfNull: false)
  final String? filename;

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final FilePartSource? source_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FilePart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, mime, filename, url, source_],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.mime,
                other.filename,
                other.url,
                other.source_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        messageID,
        type,
        mime,
        filename,
        url,
        source_,
      ]);

  factory FilePart.fromJson(Map<String, dynamic> json) =>
      _$FilePartFromJson(json);

  Map<String, dynamic> toJson() => _$FilePartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FilePartTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FilePartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
