import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/models.dart';
import '../domain/agent_handoff.dart';
import 'agent_handoff_controller.dart';
import 'agent_handoff_links.dart';

/// Why a "Continue with…" choice cannot be used now. The sheet shows the
/// reason in words beside the choice; none of them is a dead end.
enum ContinueWithBlock {
  /// The agent is installed but signed out: the existing sign-in flow.
  needsSignIn,
  limitReached,
  notReady,

  /// The two conversations are not provably on the same files.
  notSameProject,
  noRecordWriter,
}

/// One row of the sheet: a model of the current server (a switch) or an
/// agent's model (a new conversation).
@immutable
final class ContinueWithChoice {
  const ContinueWithChoice({
    required this.key,
    required this.agentName,
    required this.modelName,
    this.iconKey = '',
    this.target,
    this.block,
    this.agentId,
  });

  /// Stable within one plan; also the widget key suffix.
  final String key;
  final String agentName;
  final String modelName;
  final String iconKey;

  /// Null for a row that cannot be used ([block] says why).
  final HandoffTarget? target;
  final ContinueWithBlock? block;

  /// The catalog id of the agent, for the sign-in flow.
  final String? agentId;

  bool get usable => block == null && target != null;

  /// A model of the conversation's own server: the conversation stays.
  bool get native => target != null && !target!.startNewAgent;
}

@immutable
final class ContinueWithPlan {
  const ContinueWithPlan({
    required this.sourceTitle,
    required this.project,
    required this.models,
    required this.agents,
    this.currentModel,
  });

  final String sourceTitle;

  /// The project folder's name.
  final String project;
  final List<ContinueWithChoice> models;
  final List<ContinueWithChoice> agents;
  final ModelRef? currentModel;

  Iterable<ContinueWithChoice> get all => [...models, ...agents];
}

/// What the sheet needs from the app. The production implementation reads the
/// connection (see `ConnectionContinueWithHost`); tests supply a fake. The
/// sheet never reaches OpenCode 1, OpenCode 2 or Paseo classes (AGENTS.md).
abstract interface class ContinueWithHost {
  /// Reads the choices. May throw; the sheet then offers Try again.
  Future<ContinueWithPlan> load();

  /// "Working", "Waiting for you", "Failed" or "Unknown" in plain words.
  String get lastState;

  AgentHandoffController controllerFor(
    ContinueWithChoice choice, {
    required bool Function() isCurrent,
    required VoidCallback onChanged,
  });

  /// Selects [choice]'s model for this conversation only
  /// (`selectModelForSession`). Throws when it is refused.
  Future<void> switchModel(ContinueWithChoice choice);

  /// Called with the id [AgentHandoffController.send] returned, before the
  /// conversation is opened, so the app finds the right connection for it.
  /// Returns the id to open it by: the one the Conversations list uses.
  Future<String> adopt(ContinueWithChoice choice, String navigationSessionId);
}

enum ContinueWithPhase {
  loading,
  loadFailed,
  choosing,

  /// A model of this server was picked: nothing to prepare.
  native,
  switching,
  preparing,
  ready,
  starting,
  linkFailed,
  done,
}

/// What went wrong, as the sheet words it. Never an exception's text.
enum ContinueWithProblem {
  noWriter,
  project,
  target,
  source,
  scope,
  read,
  tooLarge,
  file,
  unknown,
  link,
  message,
  model,
  generic,
}

/// The sheet's state: one choice at a time, one [AgentHandoffController] per
/// prepared choice (it is a single attempt). Preparing only reads; nothing
/// exists on any server until [start].
class ContinueWithFlow extends ChangeNotifier {
  ContinueWithFlow(this.host, {this.isActive});

  final ContinueWithHost host;

  /// The app's own view of the source (profile, location and selection); the
  /// sheet's lifetime is added to it.
  final bool Function()? isActive;

  ContinueWithPhase phase = ContinueWithPhase.loading;
  ContinueWithPlan? plan;
  ContinueWithChoice? selected;
  AgentHandoffController? _controller;
  AgentHandoffDraft? draft;
  ContinueWithProblem? problem;

  /// Technical words for [problem] (which check or step), for Details.
  String? problemDetail;

  /// The finer phase of an attempt under way (saving, starting, linking).
  AgentHandoffPhase? attempt;

  /// Where to open once the attempt is done; set with [ContinueWithPhase.done]
  /// and, when only the link failed, with [ContinueWithPhase.linkFailed].
  String? openSessionId;
  bool _closed = false;

  /// A result is unconfirmed: no new conversation may start from this sheet
  /// until the target conversations have been checked (contract: unknown
  /// delivery never retries). Choosing a model of this server still works.
  bool locked = false;
  int _generation = 0;

  bool get closed => _closed;

  /// Whether work started for the generation [g] still matters.
  bool _current(int g) =>
      !_closed && g == _generation && (isActive?.call() ?? true);

  void _set(void Function() change) {
    if (_closed) return;
    change();
    notifyListeners();
  }

  Future<void> load() async {
    final g = ++_generation;
    _set(() {
      phase = ContinueWithPhase.loading;
      problem = null;
      problemDetail = null;
      selected = null;
      draft = null;
    });
    try {
      final loaded = await host.load();
      if (!_current(g)) return;
      _set(() {
        plan = loaded;
        phase = ContinueWithPhase.choosing;
      });
    } catch (_) {
      if (!_current(g)) return;
      _set(() => phase = ContinueWithPhase.loadFailed);
    }
  }

  /// Picks a row. A model of this server needs nothing more; an agent's
  /// model starts preparing the record (a read).
  Future<void> select(ContinueWithChoice choice) async {
    if (!choice.usable || (locked && !choice.native)) return;
    if (phase == ContinueWithPhase.switching ||
        phase == ContinueWithPhase.starting ||
        phase == ContinueWithPhase.done) {
      return;
    }
    final g = ++_generation;
    _controller = null;
    _set(() {
      selected = choice;
      draft = null;
      problem = null;
      problemDetail = null;
      attempt = null;
      phase = choice.native
          ? ContinueWithPhase.native
          : ContinueWithPhase.preparing;
    });
    if (choice.native) return;
    await _prepare(choice, g);
  }

  Future<void> _prepare(ContinueWithChoice choice, int g) async {
    final controller = host.controllerFor(
      choice,
      isCurrent: () => _current(g),
      onChanged: () {
        if (_current(g)) _set(() => attempt = _controller?.phase);
      },
    );
    _controller = controller;
    try {
      final prepared = await controller.prepare(lastState: host.lastState);
      if (!_current(g)) return;
      _set(() {
        draft = prepared;
        phase = ContinueWithPhase.ready;
      });
    } catch (error) {
      if (!_current(g)) return;
      _set(() {
        problem = _problemOf(error);
        problemDetail = controller.failureDetail;
        _controller = null;
        // A stable state: the same row can be tried again.
        phase = ContinueWithPhase.choosing;
      });
    }
  }

  /// Prepares the same choice again (the conversation changed, or reading failed).
  Future<void> prepareAgain() async {
    final choice = selected;
    if (choice == null) return;
    phase = ContinueWithPhase.choosing;
    await select(choice);
  }

  /// "Use this model" on a model of this server.
  Future<bool> useModel() async {
    final choice = selected;
    if (choice == null || !choice.native || phase != ContinueWithPhase.native) {
      return false;
    }
    final g = _generation;
    _set(() {
      phase = ContinueWithPhase.switching;
      problem = null;
      problemDetail = null;
    });
    try {
      await host.switchModel(choice);
      if (_closed || g != _generation) return false;
      _set(() => phase = ContinueWithPhase.done);
      return true;
    } catch (_) {
      if (_closed || g != _generation) return false;
      _set(() {
        phase = ContinueWithPhase.native;
        problem = ContinueWithProblem.model;
      });
      return false;
    }
  }

  /// "Start and continue": sends the reviewed first message. Returns the
  /// session to open, or null when it failed (see [problem]).
  Future<String?> start(String editedMessage) async {
    final controller = _controller;
    final choice = selected;
    if (controller == null ||
        choice == null ||
        phase != ContinueWithPhase.ready) {
      return null;
    }
    _set(() {
      phase = ContinueWithPhase.starting;
      problem = null;
      problemDetail = null;
      attempt = AgentHandoffPhase.writingFile;
    });
    try {
      final id = await host.adopt(choice, await controller.send(editedMessage));
      if (_closed) return id;
      _set(() {
        openSessionId = id;
        phase = ContinueWithPhase.done;
      });
      return id;
    } catch (error) {
      if (_closed) return null;
      final failure = controller.failure;
      _set(() {
        problem = _problemOf(failure ?? error);
        problemDetail = controller.failureDetail;
        locked = locked || problem == ContinueWithProblem.unknown;
        openSessionId = controller.navigationSessionId;
        phase = switch (failure?.kind) {
          HandoffFailureKind.linkFailed => ContinueWithPhase.linkFailed,
          // The attempt is used up; only a new preparation can go on.
          HandoffFailureKind.invalidDraft => ContinueWithPhase.ready,
          _ => ContinueWithPhase.choosing,
        };
        if (phase == ContinueWithPhase.choosing) {
          _controller = null;
          draft = null;
        }
      });
      return null;
    }
  }

  /// "Save link again": only the link is written; nothing is resent.
  Future<String?> retryLink() async {
    final controller = _controller;
    if (controller == null || phase != ContinueWithPhase.linkFailed) {
      return null;
    }
    _set(() => phase = ContinueWithPhase.starting);
    try {
      await controller.retryLink();
      final sent = controller.navigationSessionId;
      final choice = selected;
      final id = choice != null && sent != null
          ? await host.adopt(choice, sent)
          : sent;
      _set(() {
        problem = null;
        problemDetail = null;
        openSessionId = id;
        phase = ContinueWithPhase.done;
      });
      return id;
    } catch (_) {
      _set(() {
        problem = ContinueWithProblem.link;
        phase = ContinueWithPhase.linkFailed;
      });
      return null;
    }
  }

  /// Back to the list, keeping nothing of the prepared record.
  void backToChoices() {
    if (phase == ContinueWithPhase.starting ||
        phase == ContinueWithPhase.switching) {
      return;
    }
    _generation++;
    _controller = null;
    _set(() {
      selected = null;
      draft = null;
      problem = null;
      problemDetail = null;
      phase = ContinueWithPhase.choosing;
    });
  }

  /// The sheet closed. Work already accepted by the target is not undone.
  void close() {
    _closed = true;
    _generation++;
  }

  @override
  void dispose() {
    close();
    super.dispose();
  }

  static ContinueWithProblem _problemOf(Object error) {
    if (error is HandoffRecordTooLarge) return ContinueWithProblem.tooLarge;
    if (error is AgentHandoffHistoryFailure) return ContinueWithProblem.read;
    if (error is AgentHandoffFailure) {
      return switch (error.kind) {
        HandoffFailureKind.unavailable => ContinueWithProblem.noWriter,
        HandoffFailureKind.projectMismatch => ContinueWithProblem.project,
        HandoffFailureKind.targetUnavailable => ContinueWithProblem.target,
        HandoffFailureKind.sourceChanged => ContinueWithProblem.source,
        HandoffFailureKind.staleScope => ContinueWithProblem.scope,
        HandoffFailureKind.preparationFailed => ContinueWithProblem.read,
        HandoffFailureKind.invalidDraft => ContinueWithProblem.message,
        HandoffFailureKind.fileWriteFailed => ContinueWithProblem.file,
        HandoffFailureKind.creationUnknown ||
        HandoffFailureKind.deliveryUnknown => ContinueWithProblem.unknown,
        HandoffFailureKind.linkFailed => ContinueWithProblem.link,
      };
    }
    return ContinueWithProblem.generic;
  }
}

/// A line under a conversation that was continued, or that continues another
/// (contract "Links and deletion"). Plain data for the kit line.
@immutable
final class ContinueWithLinkLine {
  const ContinueWithLinkLine({
    required this.link,
    required this.outgoing,
    required this.agent,
    required this.title,
  });

  final HandoffLink link;

  /// True on the source ("Continued in AGENT"), false on the new one
  /// ("Continued from TITLE").
  final bool outgoing;
  final String agent;
  final String title;
}

/// The lines for one conversation, read from the link store. A read failure
/// is "no lines": the conversation itself is unaffected.
List<ContinueWithLinkLine> continueWithLinkLines(
  AgentHandoffLinks links, {
  required String profileId,
  required String sessionId,
  required HandoffOrigin origin,
}) {
  try {
    return [
      for (final link in links.forSession(profileId, sessionId, origin: origin))
        ContinueWithLinkLine(
          link: link,
          outgoing:
              link.sourceProfileId == profileId &&
              link.sourceSessionId == sessionId &&
              link.sourceOrigin == origin,
          agent: link.targetAgent,
          title: link.sourceTitle,
        ),
    ];
  } catch (_) {
    return const [];
  }
}
