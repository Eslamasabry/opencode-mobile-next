//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_part_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_command_request_parts_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionCommandRequestPartsInner {
  /// Returns a new [SessionCommandRequestPartsInner] instance.
  SessionCommandRequestPartsInner({
    this.id,

    required this.type,

    required this.mime,

    this.filename,

    required this.url,

    this.source_,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SessionCommandRequestPartsInnerTypeEnum.unknownDefaultOpenApi,
  )
  final SessionCommandRequestPartsInnerTypeEnum type;

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
        other is SessionCommandRequestPartsInner &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, mime, filename, url, source_],
              [
                other.id,
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
      mapPropsToHashCode([id, type, mime, filename, url, source_]);

  factory SessionCommandRequestPartsInner.fromJson(Map<String, dynamic> json) =>
      _$SessionCommandRequestPartsInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionCommandRequestPartsInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionCommandRequestPartsInnerTypeEnum {
  @JsonValue(r'file')
  file(r'file'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionCommandRequestPartsInnerTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
