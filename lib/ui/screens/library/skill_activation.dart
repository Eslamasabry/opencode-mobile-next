part of '../library_screen.dart';

class _SkillActivationSheet extends StatefulWidget {
  const _SkillActivationSheet({
    required this.controller,
    required this.sessionID,
    required this.skill,
    required this.location,
  });
  final ConnectionController controller;
  final String sessionID;
  final SkillInfo skill;
  final int location;
  @override
  State<_SkillActivationSheet> createState() => _SkillActivationSheetState();
}

class _SkillActivationSheetState extends State<_SkillActivationSheet> {
  late final String? _sessionTitle =
      widget.controller.sessionsById[widget.sessionID]?.title;
  bool _resume = true;
  bool _sending = false;
  bool _uncertain = false;
  bool _appliedElsewhere = false;
  String? _error;

  Future<void> _activate() async {
    if (_sending || _uncertain || _appliedElsewhere) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await widget.controller.activateSessionSkill(
        widget.sessionID,
        widget.skill.id ?? widget.skill.name,
        resume: _resume,
        expectedLocation: widget.location,
      );
      if (!mounted) return;
      if (widget.controller.locationRevision == widget.location) {
        Navigator.pop(context, true);
      } else {
        setState(() {
          _appliedElsewhere = true;
          _error = l10n.skillAppliedOriginal;
        });
      }
    } on SessionSkillException catch (error) {
      if (!mounted) return;
      setState(() {
        _uncertain = error.failure == SessionSkillFailure.uncertain;
        _error = switch (error.failure) {
          SessionSkillFailure.unsupported => l10n.skillUnsupported,
          SessionSkillFailure.changed => l10n.skillLocationChanged,
          SessionSkillFailure.staged => l10n.skillStaged,
          SessionSkillFailure.busy => l10n.skillBusy,
          SessionSkillFailure.uncertain => l10n.skillUncertain,
        };
      });
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final title = _sessionTitle;
    return PopScope(
      canPop: !_sending,
      child: SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .9,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              ListTile(
                title: Text(
                  widget.skill.name,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                subtitle: Text(
                  title?.isNotEmpty == true ? title! : l10n.commandUntitledChat,
                ),
                trailing: IconButton(
                  tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
                  onPressed: _sending ? null : () => Navigator.pop(context),
                  icon: const Icon(Icons.close_rounded),
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: FilePreviewBody(
                  key: const Key('skill-activation-preview'),
                  data: FilePreviewData(
                    name: 'SKILL.md',
                    mimeType: 'text/markdown',
                    text: widget.skill.content,
                  ),
                ),
              ),
              const Divider(height: 1),
              Flexible(
                flex: 0,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxHeight: MediaQuery.sizeOf(context).height * .42,
                  ),
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(l10n.skillActivationHelp),
                        CheckboxListTile(
                          key: const ValueKey('skill-resume'),
                          contentPadding: EdgeInsets.zero,
                          title: Text(l10n.skillRunNow),
                          subtitle: Text(l10n.skillRunHelp),
                          value: _resume,
                          onChanged: _sending || _uncertain || _appliedElsewhere
                              ? null
                              : (value) => setState(() => _resume = value!),
                        ),
                        if (_error != null)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Semantics(
                              liveRegion: true,
                              child: Text(
                                _error!,
                                style: TextStyle(
                                  color: Theme.of(context).colorScheme.error,
                                ),
                              ),
                            ),
                          ),
                        FilledButton.icon(
                          key: const ValueKey('skill-activate'),
                          onPressed: _sending || _uncertain || _appliedElsewhere
                              ? null
                              : _activate,
                          icon: _sending
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.extension_outlined),
                          label: Text(l10n.skillUse),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
