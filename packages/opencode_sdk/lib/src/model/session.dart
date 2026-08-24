//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_share.dart';
import 'package:opencode_sdk/src/model/session_tokens.dart';
import 'package:opencode_sdk/src/model/permission_rule.dart';
import 'package:opencode_sdk/src/model/session_summary.dart';
import 'package:opencode_sdk/src/model/session_create_request_model.dart';
import 'package:opencode_sdk/src/model/session_revert.dart';
import 'package:opencode_sdk/src/model/session_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Session {
  /// Returns a new [Session] instance.
  Session({
    required this.id,

    required this.slug,

    required this.projectID,

    this.workspaceID,

    required this.directory,

    this.path,

    this.parentID,

    this.summary,

    this.cost,

    this.tokens,

    this.share,

    required this.title,

    this.agent,

    this.model,

    required this.version,

    this.metadata,

    required this.time,

    this.permission,

    this.revert,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'slug', required: true, includeIfNull: false)
  final String slug;

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  @JsonKey(name: r'workspaceID', required: false, includeIfNull: false)
  final String? workspaceID;

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'path', required: false, includeIfNull: false)
  final String? path;

  @JsonKey(name: r'parentID', required: false, includeIfNull: false)
  final String? parentID;

  @JsonKey(name: r'summary', required: false, includeIfNull: false)
  final SessionSummary? summary;

  @JsonKey(name: r'cost', required: false, includeIfNull: false)
  final num? cost;

  @JsonKey(name: r'tokens', required: false, includeIfNull: false)
  final SessionTokens? tokens;

  @JsonKey(name: r'share', required: false, includeIfNull: false)
  final SessionShare? share;

  @JsonKey(name: r'title', required: true, includeIfNull: false)
  final String title;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionCreateRequestModel? model;

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final String version;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionTime time;

  @JsonKey(name: r'permission', required: false, includeIfNull: false)
  final List<PermissionRule>? permission;

  @JsonKey(name: r'revert', required: false, includeIfNull: false)
  final SessionRevert? revert;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Session &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                slug,
                projectID,
                workspaceID,
                directory,
                path,
                parentID,
                summary,
                cost,
                tokens,
                share,
                title,
                agent,
                model,
                version,
                metadata,
                time,
                permission,
                revert,
              ],
              [
                other.id,
                other.slug,
                other.projectID,
                other.workspaceID,
                other.directory,
                other.path,
                other.parentID,
                other.summary,
                other.cost,
                other.tokens,
                other.share,
                other.title,
                other.agent,
                other.model,
                other.version,
                other.metadata,
                other.time,
                other.permission,
                other.revert,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        slug,
        projectID,
        workspaceID,
        directory,
        path,
        parentID,
        summary,
        cost,
        tokens,
        share,
        title,
        agent,
        model,
        version,
        metadata,
        time,
        permission,
        revert,
      ]);

  factory Session.fromJson(Map<String, dynamic> json) =>
      _$SessionFromJson(json);

  Map<String, dynamic> toJson() => _$SessionToJson(this);

  String toString() {
    return toJson().toString();
  }
}
