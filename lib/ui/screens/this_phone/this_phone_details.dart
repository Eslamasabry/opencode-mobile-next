part of '../this_phone_screen.dart';

/// The details half of This phone (log, reply speed, performance values,
/// installed list), split out to keep the screen file small.
extension _ThisPhoneDetails on _ThisPhoneScreenState {
  Future<void> _readInstalled() async {
    final engine = _ThisPhoneScreenState._engine();
    if (engine == null) return;
    // Reads can overlap (the page's first read, then one when a job ends):
    // only the newest answer counts, or an old slow read puts back a list
    // from before the tools were added.
    final read = ++_installedReads;
    // One more try after a moment: the check shares the phone's task slot,
    // and right after setup ends (a Reset, an added tool) its last step can
    // still hold it, which showed "Could not check" on a working Linux.
    for (var attempt = 1; attempt <= 2; attempt++) {
      try {
        final installed = await engine.installedOptional();
        if (mounted && read == _installedReads) {
          _installedOptional = installed;
          _installedFailed = false;
          _changed();
        }
        return;
      } catch (_) {
        if (!mounted || read != _installedReads) return;
        if (attempt < 2) {
          await Future<void>.delayed(const Duration(milliseconds: 1500));
          if (!mounted || read != _installedReads) return;
          continue;
        }
        // Keep the previous inventory; a failed probe does not remove tools.
        _installedFailed = true;
        _changed();
      }
    }
  }

  /// The technical truth, last and folded (KIT-33): the server's log in
  /// the one log view (KIT-31), titled by the server it comes from so it
  /// does not repeat "Details".
  Widget _logFold(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final running = _host.state == PhoneHostState.running;
    final version = _host.version;
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: KitDetailsFold(
        foldKey: const ValueKey('this-phone-details'),
        expanded: _detailsOpen,
        onExpansionChanged: _details,
        // What the host said went wrong, word for word (redacted by the
        // fold); the status row says it in plain words.
        text: _removing ? null : _host.failure ?? _history?.error,
        textKey: const ValueKey('this-phone-failure-details'),
        values: _performanceValues(l10n),
        child: KitLogPanel(
          lines: _log,
          panelKey: const ValueKey('this-phone-log'),
          title: version == null
              ? _runtimeName(_host.runtime)
              : l10n.phoneServerCardVersion(version),
          emptyText: l10n.phoneServerCardLogEmpty,
          live: running,
          onRefresh: running ? _readLog : null,
          ended: running ? null : const KitLogEnd(),
        ),
      ),
    );
  }

  /// How fast the last reply on this phone's server came, in plain words;
  /// left out until one was timed since the app opened.
  Widget? _replySpeed(BuildContext context, AppLocalizations l10n) {
    final last = _replies.lastInApp;
    if (last == null) return null;
    final first = last.firstToken;
    return KitRow(
      key: const ValueKey('this-phone-reply-speed'),
      leading: KitRow.icon(context, AppIconography.speed),
      title: l10n.replySpeedTitle,
      supporting: TextSpan(
        text: first == null
            ? l10n.replySpeedNoWords(_seconds(l10n, last.total))
            : l10n.replySpeedLast(
                _seconds(l10n, first),
                _seconds(l10n, last.total),
              ),
      ),
      supportingKey: const ValueKey('this-phone-reply-speed-detail'),
      supportingMaxLines: 2,
    );
  }

  /// Performance, technical and folded under Details: how proot runs the
  /// server, whether the phone is kept awake for a reply, and the last
  /// reply's first-words wait split between the app and the server.
  List<KitTechnicalValue> _performanceValues(AppLocalizations l10n) {
    final performance = _performance;
    final last = _replies.lastInApp;
    final first = last?.firstToken;
    final server = last?.serverFirstToken;
    return [
      if (performance != null) ...[
        KitTechnicalValue(
          l10n.perfDetailLinuxMode,
          switch (performance.prootMode) {
            BuiltinProotMode.seccomp => l10n.perfLinuxModeFast,
            BuiltinProotMode.ptrace => l10n.perfLinuxModeSlow,
            BuiltinProotMode.unknown => l10n.perfLinuxModeUnknown,
          },
          key: const ValueKey('this-phone-perf-mode'),
          copyable: false,
        ),
        KitTechnicalValue(
          l10n.perfDetailAwake,
          performance.workHeld ? l10n.perfAwakeNow : l10n.perfAwakeWhenWorking,
          key: const ValueKey('this-phone-perf-awake'),
          copyable: false,
        ),
      ],
      if (first != null)
        KitTechnicalValue(
          l10n.perfDetailFirstWords,
          server == null
              ? _seconds(l10n, first)
              : l10n.perfFirstWordsSplit(
                  _seconds(l10n, first),
                  _seconds(l10n, server),
                ),
          key: const ValueKey('this-phone-perf-first-words'),
        ),
      if (last?.model case final model?)
        KitTechnicalValue(
          l10n.perfDetailModel,
          model,
          key: const ValueKey('this-phone-perf-model'),
        ),
    ];
  }

  /// What is installed on this phone, folded: the parts setup put there,
  /// on either host. What every setup installs, then the optional tools the
  /// host's checks find (Python, AI Team, voice typing and more).
  Widget _installed(BuildContext context, AppLocalizations l10n) {
    if (_installedOptional == null) {
      if (_installedFailed) {
        return KitNotice(
          message: l10n.e7SetupCheckInstallFailed,
          actions: [KitAction(label: l10n.commonRetry, onPressed: _load)],
        );
      }
      return KitRow(
        title: l10n.thisPhoneInstalled,
        supporting: TextSpan(text: l10n.phoneSetupStartChecking),
      );
    }
    final names = <String>[];
    final engine = _ThisPhoneScreenState._engine();
    final optional = _installedOptional ?? const <String>{};
    for (final component
        in engine == null
            ? const <SetupComponent>[]
            : installableComponents(engine.registry)) {
      if (component.required || optional.contains(component.id)) {
        names.add(component.shortTitle);
      }
    }
    if (names.isEmpty) {
      names.add(l10n.phoneSetupLinuxTitle);
    }
    return KitExpandRow(
      key: const ValueKey('this-phone-installed'),
      headerKey: const ValueKey('this-phone-installed-header'),
      leading: KitRow.icon(context, AppIconography.check),
      title: l10n.thisPhoneInstalled,
      supporting: TextSpan(text: joinSetupNames(l10n, names)),
      supportingMaxLines: 2,
      children: [
        for (final name in names)
          KitRow(
            title: name,
            leading: const KitStatusMark(state: KitMarkState.done),
          ),
      ],
    );
  }
}
