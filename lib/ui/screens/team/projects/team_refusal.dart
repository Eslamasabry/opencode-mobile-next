// Plain words for a refused project command, in one place.
//
// The engine answers a refused command with a short technical code
// ([TeamCommandResult.code]) and the phone engine adapter can throw a
// `PhoneEngineException` with one. The person reads a sentence, a way
// forward and, under Details, the code. To add a code: one line in
// [_known] and its two strings in app_en.arb (teamRefusal<Name> and
// teamRefusal<Name>Next). An unknown code never reads as a raw code: it
// reads "The team couldn't <what you asked>" and keeps the code in Details.
import '../../../../domain/team_project_gateway.dart' show TeamProjectAction;
import '../../../../l10n/app_localizations.dart';

class TeamRefusal {
  const TeamRefusal({
    required this.message,
    required this.next,
    required this.code,
  });
  final String message, next, code;
}

typedef _Say = String Function(AppLocalizations l);

final Map<String, (_Say, _Say)> _known = {
  'repository_empty': (
    (l) => l.teamRefusalRepositoryEmpty,
    (l) => l.teamRefusalRepositoryEmptyNext,
  ),
  'repoPathInvalid': (
    (l) => l.teamRefusalRepoPathInvalid,
    (l) => l.teamRefusalRepoPathInvalidNext,
  ),
  'invalid_proot_object_link': (
    (l) => l.teamRefusalRepositoryLink,
    (l) => l.teamRefusalRepositoryLinkNext,
  ),
  'repository_object_hash_mismatch': (
    (l) => l.teamRefusalRepositoryDamaged,
    (l) => l.teamRefusalRepositoryDamagedNext,
  ),
  'planTaskTitleRequired': (
    (l) => l.teamRefusalPlanTaskName,
    (l) => l.teamRefusalPlanTaskNameNext,
  ),
  'planPhaseInvalid': (
    (l) => l.teamRefusalPlanPhase,
    (l) => l.teamRefusalPlanPhaseNext,
  ),
  'unsupportedCommand': (
    (l) => l.teamRefusalUnsupportedCommand,
    (l) => l.teamRefusalUnsupportedCommandNext,
  ),
  'boundaryUnverified': (
    (l) => l.teamRefusalBoundaryUnverified,
    (l) => l.teamRefusalBoundaryUnverifiedNext,
  ),
  'protocolUnverified': (
    (l) => l.teamRefusalProtocolUnverified,
    (l) => l.teamRefusalProtocolUnverifiedNext,
  ),
  'engineUnavailable': (
    (l) => l.teamRefusalEngineUnavailable,
    (l) => l.teamRefusalEngineUnavailableNext,
  ),
  'transportUncertain': (
    (l) => l.teamRefusalTransportUncertain,
    (l) => l.teamRefusalTransportUncertainNext,
  ),
  'busy': ((l) => l.teamRefusalBusy, (l) => l.teamRefusalBusyNext),
  'saveFailed': (
    (l) => l.teamRefusalSaveFailed,
    (l) => l.teamRefusalSaveFailedNext,
  ),
  'readOnly': ((l) => l.teamRefusalReadOnly, (l) => l.teamRefusalReadOnlyNext),
  'closed': ((l) => l.teamRefusalClosed, (l) => l.teamRefusalClosedNext),
  'commandRefused': (
    (l) => l.teamRefusalCommandRefused,
    (l) => l.teamRefusalCommandRefusedNext,
  ),
  'importFailed': (
    (l) => l.teamRefusalImportFailed,
    (l) => l.teamRefusalImportFailedNext,
  ),
  'payloadInvalid': (
    (l) => l.teamRefusalPayloadInvalid,
    (l) => l.teamRefusalPayloadInvalidNext,
  ),
  'schemaUnsupported': (
    (l) => l.teamRefusalSchemaUnsupported,
    (l) => l.teamRefusalSchemaUnsupportedNext,
  ),
  'engineClosed': (
    (l) => l.teamRefusalEngineClosed,
    (l) => l.teamRefusalEngineClosedNext,
  ),
  // Job and step reasons the engine records while planning or working.
  'sessionFailed': (
    (l) => l.teamRefusalSessionFailed,
    (l) => l.teamRefusalSessionFailedNext,
  ),
  'modelNotConfigured': (
    (l) => l.teamRefusalModelNotConfigured,
    (l) => l.teamRefusalModelNotConfiguredNext,
  ),
  'modelUnavailable': (
    (l) => l.teamRefusalModelUnavailable,
    (l) => l.teamRefusalModelUnavailableNext,
  ),
  'modelInvalid': (
    (l) => l.teamRefusalModelUnavailable,
    (l) => l.teamRefusalModelUnavailableNext,
  ),
  'invalid_model': (
    (l) => l.teamRefusalModelUnavailable,
    (l) => l.teamRefusalModelUnavailableNext,
  ),
  'authentication_failed': (
    (l) => l.teamRefusalAuthFailed,
    (l) => l.teamRefusalAuthFailedNext,
  ),
  'cloneFailed': (
    (l) => l.teamRefusalCloneFailed,
    (l) => l.teamRefusalCloneFailedNext,
  ),
  'sessionUncertain': (
    (l) => l.teamRefusalSessionUncertain,
    (l) => l.teamRefusalSessionUncertainNext,
  ),
  'sessionUnknown': (
    (l) => l.teamRefusalSessionUncertain,
    (l) => l.teamRefusalSessionUncertainNext,
  ),
  'promptUncertain': (
    (l) => l.teamRefusalSessionUncertain,
    (l) => l.teamRefusalSessionUncertainNext,
  ),
  'sessionCreateUncertain': (
    (l) => l.teamRefusalSessionUncertain,
    (l) => l.teamRefusalSessionUncertainNext,
  ),
  'usageUncertain': (
    (l) => l.teamRefusalSessionUncertain,
    (l) => l.teamRefusalSessionUncertainNext,
  ),
  'invalidPlan': (
    (l) => l.teamRefusalPlanInvalid,
    (l) => l.teamRefusalPlanInvalidNext,
  ),
  'checkerAnswerUnreadable': (
    (l) => l.teamRefusalCheckerUnreadable,
    (l) => l.teamRefusalCheckerUnreadableNext,
  ),
  'structuredOutputInvalid': (
    (l) => l.teamRefusalPlanInvalid,
    (l) => l.teamRefusalPlanInvalidNext,
  ),
  'needsAnswer': (
    (l) => l.teamRefusalNeedsAnswer,
    (l) => l.teamRefusalNeedsAnswerNext,
  ),
  'recoveryNeedsReview': (
    (l) => l.teamRefusalRecoveryReview,
    (l) => l.teamRefusalRecoveryReviewNext,
  ),
  'restartNeedsReconciliation': (
    (l) => l.teamRefusalAppStopped,
    (l) => l.teamRefusalAppStoppedNext,
  ),
  'pauseNeedsReconciliation': (
    (l) => l.teamRefusalAppStopped,
    (l) => l.teamRefusalAppStoppedNext,
  ),
  'serverRestarted': (
    (l) => l.teamRefusalServerRestarted,
    (l) => l.teamRefusalServerRestartedNext,
  ),
  'restartResend': (
    (l) => l.teamRefusalRestartResend,
    (l) => l.teamRefusalRestartResendNext,
  ),
  'chatBusy': ((l) => l.teamRefusalChatBusy, (l) => l.teamRefusalChatBusyNext),
  'budgetReached': (
    (l) => l.teamRefusalBudgetReached,
    (l) => l.teamRefusalBudgetReachedNext,
  ),
  'transport_unavailable': (
    (l) => l.teamRefusalEngineUnavailable,
    (l) => l.teamRefusalEngineUnavailableNext,
  ),
  'serverOffline': (
    (l) => l.teamRefusalEngineUnavailable,
    (l) => l.teamRefusalEngineUnavailableNext,
  ),
};

/// Codes whose way forward is choosing a model.
const _needsModel = {
  'modelNotConfigured',
  'modelUnavailable',
  'modelInvalid',
  'invalid_model',
  'sessionFailed',
};

/// True when [code] is fixed by picking a model (offer that action).
bool teamReasonNeedsModel(String code) => _needsModel.contains(code);

/// The plain words for an engine job or step reason, or null when the code is
/// not known (the caller then keeps the engine's own sentence).
TeamRefusal? teamReasonFor(AppLocalizations l, String code) {
  final known = _known[code];
  if (known == null) return null;
  return TeamRefusal(message: known.$1(l), next: known.$2(l), code: code);
}

final _trailingCode = RegExp(r'\s*\(([A-Za-z_]+)\)\.?$');

/// The code the adapter put in parentheses at the end of a timeline row, or
/// null.
String? teamTimelineCode(String text) =>
    _trailingCode.firstMatch(text)?.group(1);

/// A timeline row as a person reads it: a known code becomes its plain
/// sentence; an unknown code is cut off (it stays out of the page).
String teamTimelineWords(AppLocalizations l, String text) {
  final code = teamTimelineCode(text);
  if (code == null) return text;
  final known = teamReasonFor(l, code);
  if (known != null) return known.message;
  return text.replaceFirst(_trailingCode, '.');
}

String _did(AppLocalizations l, TeamProjectAction action) => switch (action) {
  TeamProjectAction.createProject => l.teamRefusalDidPlan,
  TeamProjectAction.createQuickTask => l.teamRefusalDidQuick,
  TeamProjectAction.approvePlan => l.teamRefusalDidApprove,
  TeamProjectAction.approveSpec => l.teamRefusalDidSpec,
  TeamProjectAction.promote => l.teamRefusalDidPromote,
  TeamProjectAction.stopProject => l.teamRefusalDidStop,
  TeamProjectAction.pauseProject => l.teamRefusalDidPause,
  TeamProjectAction.resumeProject => l.teamRefusalDidResume,
  _ => l.teamRefusalDidSave,
};

/// The sentence, way forward and code for a refused [action].
TeamRefusal teamRefusalFor(
  AppLocalizations l,
  TeamProjectAction action,
  String code,
) {
  final known = _known[code];
  if (known != null) {
    return TeamRefusal(message: known.$1(l), next: known.$2(l), code: code);
  }
  return TeamRefusal(
    message: l.teamRefusalUnknown(_did(l, action)),
    next: l.teamRefusalUnknownNext,
    code: code,
  );
}
