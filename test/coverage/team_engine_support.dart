// The team engine's workspace with every field set to a value of its own, so
// the coverage gate can look for each one on the screens.
import 'package:opencode_mobile/domain/team_project_gateway.dart';

const _spec = TeamSpec(
  contextFiles: ['docs/brand-guide.md'],
  version: 3,
  goal: 'Launch the shop in Arabic and English',
  constraints: 'Works offline on phones',
  decisions: 'Use one checkout page',
  outOfScope: 'Gift cards',
  milestones: [
    TeamMilestone(
      id: 'm-1',
      title: 'Checkout ready',
      criteria: ['Card payment works end to end'],
      accepted: true,
    ),
  ],
  approvedBy: 'person',
  approvedAt: '2026-10-07T09:00:00Z',
);

const _settings = TeamProjectSettings(
  keepWorkingScreenOff: true,
  mode: 'parallel',
  maxLanes: 4,
  reviewLevel: 'phases',
  chargingOnly: true,
  autoFix: true,
  maxFixRounds: 3,
  budget: TeamBudget(
    chosen: true,
    unlimited: false,
    daily: 12.5,
    total: 61.0,
    taskTokens: 90000,
  ),
);

TeamWorkspace populatedEngineWorkspace() => const TeamWorkspace(
  defaultSettings: _settings,
  schemaVersion: 1,
  revision: 41,
  simulated: false,
  servers: [
    TeamServer(
      id: 'srv-phone',
      name: 'This phone',
      phone: true,
      online: true,
      laneCap: 2,
      chatWaiting: true,
      reason: 'A chat is waiting on this computer',
      memoryMb: 1024.0,
    ),
  ],
  roles: [
    TeamProjectRole(
      id: 'role-frontend',
      name: 'Storefront builder',
      instructions: 'Build screens that match the brand guide',
      model: 'anthropic/claude-opus',
      fallbackModel: 'openai/gpt-sol',
      readOnly: true,
    ),
  ],
  projects: [
    TeamProject(
      budgetWarning: true,
      id: 'prj-shop',
      name: 'Shopfront relaunch',
      status: 'running',
      revision: 17,
      settings: _settings,
      repos: [
        TeamRepo(
          id: 'repo-web',
          name: 'web-app',
          serverId: 'srv-phone',
          path: '/work/shopfront/web',
          devCommit: 'dev1234',
          mainCommit: 'main5678',
          checkCommand: 'npm run verify',
          sharedRemote: true,
        ),
      ],
      specDraft: _spec,
      specVersions: [_spec],
      phases: [
        TeamPhase(
          id: 'ph-1',
          milestoneId: 'm-1',
          title: 'Build checkout',
          risky: true,
          accepted: true,
        ),
      ],
      tasks: [
        TeamTask(
          criterionResults: [
            TeamCriterionResult(
              criterion: 'Pay button is reachable by keyboard',
              status: 'met',
            ),
          ],
          id: 'task-pay',
          title: 'Build the pay button',
          phaseId: 'ph-1',
          roleId: 'role-frontend',
          repoId: 'repo-web',
          serverId: 'srv-phone',
          status: 'review',
          dependsOn: ['task-cart'],
          criteria: ['Pay button is reachable by keyboard'],
          branch: 'team/shop/task-pay',
          reason: 'Waiting for a reviewer to look at the keyboard order',
          changedAt: '2026-10-09T08:30:00Z',
          steps: 37,
          tokens: 48210,
          fixRounds: 2,
          affected: true,
          findings: [
            TeamFinding(
              id: 'fnd-1',
              severity: 'major',
              criterion: 'Pay button is reachable by keyboard',
              location: 'lib/checkout/pay_button.dart:42',
              text: 'The button skips the tab order',
              status: 'open',
            ),
          ],
          messages: [
            TeamMessage(
              id: 'msg-1',
              actor: 'person',
              text: 'Please keep the button green',
              at: '2026-10-09T08:10:00Z',
            ),
          ],
          diff: 'diff --git a/pay_button.dart b/pay_button.dart',
        ),
      ],
      requests: [
        TeamRequest(
          id: 'req-1',
          kind: 'question',
          title: 'Keep the cart in local storage?',
          taskId: 'task-pay',
          phaseId: 'ph-1',
          createdAt: '2026-10-09T08:20:00Z',
          answered: true,
          answer: 'Yes, keep it on the phone',
        ),
      ],
      mergeQueue: [
        TeamMergeItem(
          id: 'mrg-1',
          taskId: 'task-pay',
          repoId: 'repo-web',
          status: 'blocked',
          reason: 'Checks are still running',
          checksPassed: true,
        ),
      ],
      receipts: [
        TeamProjectReceipt(
          id: 'rcp-1',
          kind: 'promote',
          repoId: 'repo-web',
          before: 'before99',
          after: 'after88',
          at: '2026-10-09T08:40:00Z',
          actor: 'person',
        ),
      ],
      timeline: [
        TeamTimelineEvent(
          id: 'evt-1',
          kind: 'decision',
          text: 'Cart stays on the phone',
          actor: 'person',
          at: '2026-10-09T08:25:00Z',
          taskId: 'task-pay',
        ),
      ],
      planningState: TeamPlanningState(
        jobId: 'job-77',
        stage: 'completed',
        reason: 'Plan drafted in two passes',
        updatedAt: '2026-10-09T07:00:00Z',
      ),
      timelineTruncated: true,
      spent: 21.5,
      spentToday: 4.25,
      spendDay: '2026-10-09',
      digestReadAt: '2026-10-09T06:00:00Z',
      updatedAt: '2026-10-09T08:45:00Z',
      planApproved: true,
      quickTask: true,
      usageReported: true,
      simulated: false,
    ),
  ],
);
