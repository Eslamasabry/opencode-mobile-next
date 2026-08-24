//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'patch_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PatchPart {
  /// Returns a new [PatchPart] instance.
  PatchPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.hash,

    required this.files,
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
    unknownEnumValue: PatchPartTypeEnum.unknownDefaultOpenApi,
  )
  final PatchPartTypeEnum type;

  @JsonKey(name: r'hash', required: true, includeIfNull: false)
  final String hash;

  @JsonKey(name: r'files', required: true, includeIfNull: false)
  final List<String> files;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PatchPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, hash, files],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.hash,
                other.files,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, messageID, type, hash, files]);

  factory PatchPart.fromJson(Map<String, dynamic> json) =>
      _$PatchPartFromJson(json);

  Map<String, dynamic> toJson() => _$PatchPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PatchPartTypeEnum {
  @JsonValue(r'patch')
  patch_(r'patch'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PatchPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
