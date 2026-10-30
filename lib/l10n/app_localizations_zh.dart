// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get servicesTitle => '开发服务';

  @override
  String get servicesCopy => '复制命令';

  @override
  String get servicesSubtitle => '项目命令、日志和预览链接';

  @override
  String get servicesIntro => '把项目的开发命令和预览链接集中管理。保存服务不会启动它。';

  @override
  String get servicesAdd => '注册服务';

  @override
  String get servicesName => '服务名称';

  @override
  String get servicesCommand => '开发命令';

  @override
  String get servicesCommandHint => '请使用前台命令，例如 npm run dev。后台运行或分离运行的命令无法被跟踪。';

  @override
  String get servicesUrl => '预览网址（可选）';

  @override
  String get servicesUrlHint => '请使用此手机能够访问的地址。localhost 指向此手机。不会为你开放或转发任何端口。';

  @override
  String get servicesSave => '保存服务';

  @override
  String get servicesUnavailable => '此服务器无法启动和跟踪开发命令。你可以在此保存命令并查看它们的预览链接。';

  @override
  String get servicesScopeChanged => '服务器或项目已变更。请从原定项目中重新打开“开发服务”。';

  @override
  String get servicesNotStarted => '未启动';

  @override
  String get servicesRunning => '正在运行命令';

  @override
  String get servicesStopped => '已停止';

  @override
  String get servicesUnknown => '状态未知';

  @override
  String get servicesStatusHint => '命令状态并不能确认你的应用已就绪或可访问。';

  @override
  String get servicesStart => '启动';

  @override
  String get servicesStop => '停止';

  @override
  String get servicesRestart => '重启';

  @override
  String get servicesLogs => '日志';

  @override
  String get servicesVisit => '访问';

  @override
  String get servicesRemove => '移除配置';

  @override
  String get servicesStopHint => '停止此服务所跟踪的命令？服务器还会移除它保留的日志。其他命令不受影响。';

  @override
  String get servicesRestartHint => '停止此跟踪的命令，移除它的服务器日志，然后重新启动已保存的命令？';

  @override
  String get servicesForget => '忘记上次运行';

  @override
  String get servicesForgetHint =>
      '清除本地运行记录？这不会停止任何服务器进程。如果上一条命令仍在运行，再次启动可能会产生重复进程。';

  @override
  String get servicesUnknownHint => '无法确认上次运行。请先刷新核对，再启动。';

  @override
  String get servicesLogEmpty => '尚无可显示的输出。';

  @override
  String get servicesWorking => '正在更新服务…';

  @override
  String servicesExit(int code) {
    return '记录的退出码：$code';
  }

  @override
  String get servicesRefresh => '刷新状态';

  @override
  String get isolatedTaskScopeChanged => '打开期间服务器或项目已变更。请关闭它，并从你需要的项目重新开始。';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryModelsAgentsTitle => '模型与智能体';

  @override
  String get libraryProvidersTitle => '提供商';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => '命令与工具';

  @override
  String get libraryTerminalTitle => '终端';

  @override
  String get librarySettingsTitle => '设置';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => '签名证书 SHA-256';

  @override
  String get modelSwitchSession => '为此对话切换模型';

  @override
  String get modelNextFavorite => '下一个收藏的模型';

  @override
  String get modelChooseTitle => '选择模型';

  @override
  String get modelTitleCompact => '模型';

  @override
  String get modelSearchHint => '搜索模型';

  @override
  String get modelAll => '所有模型';

  @override
  String get modelFavorites => '收藏';

  @override
  String get modelRecent => '最近';

  @override
  String get modelOptions => '选项';

  @override
  String get modelThinkingMode => '思考模式';

  @override
  String get modelServerDefault => '服务器默认';

  @override
  String get modelSelectionSaving => '正在保存对话的选择…';

  @override
  String get modelUnavailableSelection => '此对话的模型在该目录中不可用。请刷新模型或选择其他模型。';

  @override
  String get modelScopeChanged => '服务器已更改。请重新打开模型选择器后继续。';

  @override
  String get commonClearSearch => '清除搜索';

  @override
  String get workTitle => '任务';

  @override
  String get workRefresh => '刷新';

  @override
  String get workRetry => '重试';

  @override
  String get workCancel => '取消';

  @override
  String get workRunning => '运行中';

  @override
  String get workFinished => '已完成';

  @override
  String get workTimedOut => '已超时';

  @override
  String get workStopped => '已停止';

  @override
  String get workUnknown => '状态不可用';

  @override
  String get workOutput => '命令输出';

  @override
  String get workNoOutput => '正在等待输出…';

  @override
  String get workNoFinalOutput => '此命令没有产生任何输出。';

  @override
  String get workMoreOutput => '加载更多输出';

  @override
  String get workTrimmed => '显示的是最近的输出，较早的内容已被裁剪。';

  @override
  String get workStop => '停止命令';

  @override
  String get workStopTitle => '停止此命令？';

  @override
  String get workStopDescription =>
      '这会停止该命令，并从服务器删除它保存的输出。已加载到此处的文本会保持可见，直到你关闭它。';

  @override
  String get workTimeout => '更改超时';

  @override
  String get workTimeoutDescription => '新的超时从现在开始计算。';

  @override
  String get workTimeoutOneMinute => '1 分钟';

  @override
  String get workTimeoutFiveMinutes => '5 分钟';

  @override
  String get workTimeoutFifteenMinutes => '15 分钟';

  @override
  String get workTimeoutOneHour => '1 小时';

  @override
  String get workTimeoutNone => '不超时';

  @override
  String get workUnavailable => '此命令已不可用。它可能在服务器重启时被移除或取消。';

  @override
  String get workRestarted => '服务器已重启，此命令已不可用。下方显示的是已加载的输出。';

  @override
  String get workDisconnected => '正在重新连接。服务器可用后输出会刷新。';

  @override
  String get workContextChanged => '服务器或项目已更改。请关闭此视图并重新打开“运行中的任务”。';

  @override
  String workCount(int count) {
    return '任务 · $count 个运行中';
  }

  @override
  String workExitCode(int code) {
    return '退出码 $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => '清除草稿文字';

  @override
  String get composerClearTextSubtitle => '保留附件 · 可撤销';

  @override
  String get composerDraftCleared => '草稿文字已清除';

  @override
  String get composerReuseTitle => '重用提示词';

  @override
  String get queueSaveFailed => '无法将排队的草稿保存到本设备。你的文字仍在这里。请检查可用存储空间后重试。';

  @override
  String get fileSave => '保存';

  @override
  String get fileReload => '刷新';

  @override
  String get queueRemoveFailed => '无法从设备存储中移除这份草稿，它仍在排队。请检查可用存储空间后重试。';

  @override
  String get composerReuseSubtitle => '重用此对话和最近发送过的文字';

  @override
  String get composerReuseSearch => '搜索最近的提示词';

  @override
  String get composerReuseEmpty => '没有匹配的提示词';

  @override
  String get backgroundWorkTitle => '将正在运行的工作移到后台';

  @override
  String get backgroundWorkShortcut => '在使用对话的同时继续此任务 · Ctrl+B';

  @override
  String get backgroundWorkNoop => '没有可转入后台的前台子智能体。';

  @override
  String get backgroundWorkPromoted => '子智能体正在后台继续运行。';

  @override
  String get libraryNoModel => '未选择模型';

  @override
  String get chatAttachmentUnsupported =>
      '可附加图片、PDF、文本文件以及 Excel 或 Word 文件（.xlsx、.docx）。';

  @override
  String get termuxRestartTitle => '重启本地服务器？';

  @override
  String get termuxRestartMessage => 'OpenCode 会短暂不可用。应用会保留你当前的项目并自动重新连接。';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '有 $count 个对话正在生成。重启会中断它们。',
      one: '有 1 个对话正在生成。重启会中断它。',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => '重启';

  @override
  String get chatCopyCompleteReply => '复制完整回复';

  @override
  String get chatCopyReplySoFar => '复制目前的回复';

  @override
  String commandRunTitle(String command) {
    return '运行 /$command';
  }

  @override
  String get commandDestination => '对话';

  @override
  String get commandNewChat => '新对话';

  @override
  String get commandUntitledChat => '未命名对话';

  @override
  String get commandArguments => '参数（可选）';

  @override
  String get commandRun => '运行';

  @override
  String get commandRunning => '正在启动…';

  @override
  String get commandLocationChanged => '服务器或项目已变更。请关闭此对话框并重新打开该命令。';

  @override
  String get refreshFailed => '无法刷新';

  @override
  String get refreshRetry => '重试';

  @override
  String get globalSessionsLoadMore => '加载更多对话';

  @override
  String get globalSessionsRefreshFailed => '无法刷新对话。';

  @override
  String get workspaceRetryProjects => '重试';

  @override
  String get historyLoadOlder => '加载更早的消息';

  @override
  String get historyReload => '刷新最近的历史记录';

  @override
  String get historyCursorExpired => '较早的历史记录已变更或过期。请刷新最近的历史记录后继续。';

  @override
  String get historyLoadedOnly => '仅包含已加载的消息。加载更早的历史记录可包含更多。';

  @override
  String get historyLoadedTotals => '用量与已加载的历史';

  @override
  String get historyCopyLoadedReply => '复制已加载的回复';

  @override
  String get historyLoadedMessages => '已加载的消息';

  @override
  String get historyLoadedCost => '已加载消息的费用';

  @override
  String get historyServerTotalsNote => '标记为“服务器上报”的行覆盖整个对话。消息数和其他估算只覆盖已加载的历史。';

  @override
  String get sessionsReload => '刷新最近对话';

  @override
  String get revertReviewChanged => '此对话或其待定撤销已变更。继续前请查看最新状态。';

  @override
  String get revertReviewLatest => '查看最新状态';

  @override
  String get revertBusy => '请等待当前对话操作完成。';

  @override
  String get revertClearAction => '清除待定撤销';

  @override
  String get revertPreviewUnavailable => '服务器未提供文件预览。这并不能确定文件是否已更改。';

  @override
  String get revertStaged => '已暂存撤销';

  @override
  String get revertReview => '查看';

  @override
  String get revertFromHere => '从此处撤销';

  @override
  String get revertUndoDescription => '撤销最后一条提示词及其之后的所有内容';

  @override
  String get revertClearShortDescription => '查看并清除待定撤销';

  @override
  String get revertPromptUnavailable => '无法加载分界提示词。';

  @override
  String get revertPromptLoading => '正在加载分界提示词…';

  @override
  String get revertAttachmentPrompt => '仅含附件的提示词';

  @override
  String get revertResolveBeforeSending => '发送前请查看已暂存的撤销，并将其清除或设为永久。你的草稿会保留。';

  @override
  String get sessionNoteTitle => '给智能体的笔记';

  @override
  String get sessionNoteDescription =>
      '为此对话保留一条简短指令。保存或删除后会在智能体的下一步生效，并在那时出现在对话记录中。它不会启动一次运行。';

  @override
  String get sessionNoteHint => '例如：说明保持简短，并在完成前运行相关检查。';

  @override
  String get sessionNoteSave => '保存笔记';

  @override
  String get sessionNoteRemove => '删除已保存的笔记';

  @override
  String get sessionNoteSaved => '笔记已保存';

  @override
  String get sessionNoteRemoved => '备注已删除';

  @override
  String get sessionNotePending => '将在智能体的下一步生效。';

  @override
  String get sessionInstructionsUpdated => '指令已更新';

  @override
  String get sessionInstructionsApplied => '已为此步骤更新智能体的对话指令。';

  @override
  String get sessionNoteUnsupported => '此服务器不支持对话笔记。';

  @override
  String get sessionNoteAuthorization => '请检查此服务器的密码和权限后重试。你的草稿已保留。';

  @override
  String get sessionNoteChanged => '对话或其指令已变更。请先刷新已保存的笔记再保存。你的草稿已保留。';

  @override
  String get sessionNoteInvalid => '已保存的笔记格式此编辑器无法安全修改。';

  @override
  String get sessionNoteTooLarge => '请缩短笔记以符合服务器的大小限制。';

  @override
  String get sessionNoteBusy => '另一次笔记更改正在保存。请待其完成后再试。';

  @override
  String get sessionNoteRefresh => '刷新已保存的笔记';

  @override
  String get sessionNoteSavedVersion => '当前已保存的笔记 — 覆盖前请先查看';

  @override
  String get sessionNoteNone => '没有已保存的笔记';

  @override
  String get sessionNoteDiscard => '放弃你的笔记更改？';

  @override
  String get sessionNoteKeepEditing => '继续编辑';

  @override
  String get sessionNoteDiscardAction => '放弃更改';

  @override
  String get sessionNoteDiscardDetail => '你对此笔记所做的更改将丢失。此操作无法撤销。';

  @override
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit 字节';
  }

  @override
  String get usageTitle => '用量与费用';

  @override
  String get usageDescription => '此 OpenCode 服务器记录的、跨你的对话的活动。';

  @override
  String get usageRefresh => '刷新用量';

  @override
  String get usageToday => '今天';

  @override
  String get usageThirtyDays => '30 天';

  @override
  String get usageYear => '今年';

  @override
  String get usageAllTime => '全部时间';

  @override
  String get usageScope => '项目范围';

  @override
  String get usageAllProjects => '所有项目';

  @override
  String get usageCurrentProject => '当前项目';

  @override
  String get usageLoading => '正在加载用量';

  @override
  String get usageUnsupported => '此服务器不支持汇总用量。';

  @override
  String get usageProjectUnavailable => '无法确定当前项目。请选择“所有项目”，或先打开一个项目。';

  @override
  String get usageTimezoneUnavailable => '无法读取本设备的时区。请重试以加载日期正确的用量。';

  @override
  String get usageRefreshInterrupted => '加载用量期间服务器已变更。请刷新重试。';

  @override
  String get usageInvalidResponse => '服务器返回的用量数据不完整。请刷新重试。';

  @override
  String get usageAuthorization => '请检查此服务器的密码和权限，然后刷新。';

  @override
  String get usagePreviousResult => '正在显示这些筛选条件下的上一次结果。';

  @override
  String get usageLocationChanged => '活动服务器或项目已变更。请从设置中重新打开“用量”。';

  @override
  String get usageTinyCost => '低于 \$0.000001';

  @override
  String get usageSessions => '对话';

  @override
  String get usageSubagents => '子智能体对话';

  @override
  String get usagePrompts => '提示词';

  @override
  String get usageSteps => '智能体步骤';

  @override
  String get usageActiveDays => '活跃天数';

  @override
  String get usageStreak => '最长连续 · 天';

  @override
  String get usageEmpty => '此范围内没有活动。请尝试更大的范围或“所有项目”。';

  @override
  String get usageTokens => 'Tokens';

  @override
  String get usageTotalTokens => '总计';

  @override
  String get usageInput => '输入';

  @override
  String get usageOutput => '输出';

  @override
  String get usageReasoning => '推理';

  @override
  String get usageCacheRead => '缓存读取';

  @override
  String get usageCacheWrite => '缓存写入';

  @override
  String get usageModels => '模型用量';

  @override
  String get usageNoModels => '此范围内未记录任何模型用量。';

  @override
  String get usageToolReliability => '工具可靠性';

  @override
  String get usageToolsUnavailable => '此响应不包含工具可靠性。';

  @override
  String get usageNoTools => '此范围内未记录任何工具调用。';

  @override
  String get usageNoFinishedTools => '尚无已完成的工具调用。';

  @override
  String get usageToolCalls => '调用';

  @override
  String get usageSucceeded => '成功';

  @override
  String get usageFailed => '失败';

  @override
  String get usageUnfinished => '未完成';

  @override
  String get usageCostDisclosure =>
      '费用为 OpenCode 报告的估算值，而非提供商账单。未完成的工具调用不计入成功率。';

  @override
  String usagePeriod(String from, String to) {
    return '$from – $to';
  }

  @override
  String usageTimezone(String timezone) {
    return '时区：$timezone';
  }

  @override
  String usageModelSteps(String steps) {
    return '$steps 步';
  }

  @override
  String usageModelTokens(String tokens) {
    return '$tokens tokens';
  }

  @override
  String usageSuccessRate(String rate) {
    return '已完成调用中有 $rate 成功';
  }

  @override
  String usageUpdated(String time) {
    return '更新于 $time';
  }

  @override
  String get mcpRuntimeTitle => '直到服务器重启';

  @override
  String get mcpDefaultLocation => 'OpenCode 服务器的默认目录';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return '云环境：$workspace';
  }

  @override
  String get mcpLocationChanged => '服务器或项目已变更。你的草稿仍在这里；请在原定项目中重新打开设置，然后再添加它。';

  @override
  String get mcpAdding => '正在添加 MCP 服务器';

  @override
  String get mcpAdd => '添加 MCP 服务器';

  @override
  String get mcpRuntimeEmpty => '为当前项目添加工具，直到 OpenCode 重启。';

  @override
  String get mcpRuntimeAdded => '已为此项目添加 MCP 服务器';

  @override
  String get mcpHeaderName => '请求头名称';

  @override
  String get mcpHeaderValue => '请求头值';

  @override
  String get mcpAddHeader => '再添加一个请求头';

  @override
  String get mcpRemoveHeader => '移除请求头';

  @override
  String get sessionUnread => '未读的结果';

  @override
  String get shareSessionViewsTitle => '同步已读状态';

  @override
  String get shareSessionViewsSaveError => '无法保存此偏好设置。此设备暂时关闭已读状态共享。';

  @override
  String get exportTitle => '导出对话';

  @override
  String get exportDescription => '选择一种格式，将此对话保存到你的设备。';

  @override
  String get exportJson => '完整对话 · JSON';

  @override
  String get exportJsonDescription => '从服务器下载完整对话，包括较早的消息。';

  @override
  String get exportMarkdown => '可读记录 · Markdown';

  @override
  String get exportMarkdownDescription => '保存当前已在此对话中加载的消息。如需包含较早的消息，请先加载它们。';

  @override
  String get exportRedact => '遮蔽敏感数据';

  @override
  String get exportUnredacted => '未遮蔽的文件可能包含密钥、本地路径和私密的工具输出。';

  @override
  String get exportCancel => '取消下载';

  @override
  String get exportDownloading => '正在下载完整对话…';

  @override
  String get exportSaving => '正在保存文件…';

  @override
  String get exportSaved => '对话已保存';

  @override
  String get exportChanged => '服务器或项目已变更。请从原定对话重新打开导出。';

  @override
  String get exportUnsupported => '此服务器不支持 JSON 导出。你仍可保存已加载的 Markdown 记录。';

  @override
  String get exportAuthorization => '服务器拒绝访问。请检查服务器凭据后重试。';

  @override
  String get exportMissing => '此对话在服务器上已不存在。你仍可保存已加载的 Markdown 记录。';

  @override
  String get exportFailed => '无法导出对话。请检查网络和存储后重试。';

  @override
  String get importTitle => '导入对话';

  @override
  String get importDescription => '把 JSON 导出恢复到本 OpenCode 服务器。选择文件，然后确认导入位置。';

  @override
  String get importChoose => '选择 JSON 文件';

  @override
  String get importChooseAnother => '选择其他文件';

  @override
  String get importAction => '导入对话';

  @override
  String get importUntitled => '未命名对话';

  @override
  String get importRedacted => '此文件包含被遮蔽的占位符。导入无法恢复原始文本；如需原文，请使用未经遮蔽的导出文件。';

  @override
  String get importParent => '此对话由另一个对话发起，后者必须已存在于此服务器上。请先导入那个对话。';

  @override
  String get importArchived => '此对话已归档。导入后会保持归档状态。';

  @override
  String get importDestination => '导入到';

  @override
  String get importChooseDestination => '选择此服务器上的项目';

  @override
  String get importNoDestinations => '没有可用的项目。请在此服务器上打开一个项目，然后重试。';

  @override
  String get importDestinationFailed => '无法加载目标项目或云环境。请重试，你的文件仍处于选中状态。';

  @override
  String get importPreserves => '你的源文件保持不变。已有对话绝不会被替换，导入也不会启动智能体运行。';

  @override
  String get importReading => '正在准备导入…';

  @override
  String get importSending => '正在导入对话…';

  @override
  String get importSucceeded => '对话已导入';

  @override
  String get importOpen => '打开对话';

  @override
  String get importOpenFailed => '对话已导入，但无法打开。请在目标服务器的“所有对话”中找到它。';

  @override
  String get importChanged => '服务器或项目已变更。你的文件仍在这里。请先在原定服务器上重新打开导入。';

  @override
  String get importUnsupported => '此服务器不支持 JSON 导入。';

  @override
  String get importInvalidFile =>
      '请选择包含对话信息和消息记录的有效 OpenCode JSON 导出文件。Markdown 对话记录无法导入。';

  @override
  String get importTooLarge =>
      '此文件超出手机端 128 MiB 的导入上限。它没有被上传或截断。请改用桌面端或服务器传输此文件。';

  @override
  String get importConflict =>
      '此服务器上已存在相同 ID 的对话。未替换任何内容。请在“所有对话”中找到它，或在其他服务器上导入此文件。';

  @override
  String get importAuthorization => '服务器拒绝访问。请检查你的服务器凭据。你的文件仍处于选中状态。';

  @override
  String get importParentMissing => '此服务器上缺少父对话。请先导入父对话，然后重试此文件。';

  @override
  String get importRejected =>
      '服务器拒绝了此导出格式。你的文件仍处于选中状态；请确认它来自兼容的 OpenCode 服务器。';

  @override
  String get importUnconfirmed => '无法确认导入结果。重试前请先查看“所有对话”：服务器可能已经收到。你的源文件未变。';

  @override
  String get sessionPinsLoadFailed => '部分已置顶的对话无法加载。请刷新重试。';

  @override
  String get promptStashSaveFailed => '无法保存此提示词。你的输入框未变更。请检查设备存储后重试。';

  @override
  String get promptOriginalDraft => '恢复原始草稿';

  @override
  String get promptStashTitle => '已保存的提示词';

  @override
  String get promptStashSearch => '搜索已保存的提示词';

  @override
  String get promptStashDeleteFailed => '无法删除这条已保存的提示词。请重试。';

  @override
  String get promptStashDelete => '删除';

  @override
  String get promptStashFull => '你的暂存区已有 50 条提示词。请删除一条已保存的提示词以腾出空间；当前提示词未变更。';

  @override
  String promptStashAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个附件',
      one: '1 个附件',
    );
    return '$_temp0';
  }

  @override
  String promptStashReferences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个引用',
      one: '1 个引用',
    );
    return '$_temp0';
  }

  @override
  String get promptHistorySaveFailed => '提示词已发送，但未能在此设备上保存它的历史记录。';

  @override
  String get promptStashMigrationPending =>
      '部分已保存的附件暂时无法移动到本地附件存储。你保存的内容已保留。请释放设备存储后重试。';

  @override
  String get commonRetry => '重试';

  @override
  String get shareWaitingForServer => '连接服务器后，共享的文本会在新对话中打开。';

  @override
  String get webSourcesDisclosure =>
      '此服务器无法搜索网络。请粘贴一个公开链接，也可以附上摘录。页面不会被打开，在你发送消息之前不会发送任何内容。';

  @override
  String get webSourcesScopeChanged => '服务器已变更。请关闭并重新打开“添加网页来源”。';

  @override
  String get webSourcesUrl => '公开网址';

  @override
  String get webSourcesLabel => '标题（可选）';

  @override
  String get webSourcesExcerpt => '粘贴的摘录（可选）';

  @override
  String get webSourcesExcerptHint => '由用户提供的文本，并非经过核实的页面内容。';

  @override
  String get digestOpenConversation => '打开对话';

  @override
  String get runResultsCommandsTitle => '命令';

  @override
  String get sessionOpenRelated => '打开相关项';

  @override
  String get sessionCopyHandoff => '在电脑上继续';

  @override
  String get attentionTitle => '服务器需要处理';

  @override
  String get webSourcesTitle => '添加网络来源';

  @override
  String get webSourcesEntryDetail => '可用时搜索，或粘贴链接和摘录，审阅后再添加到草稿';

  @override
  String get webSourcesDraftChanged => '草稿或服务器已变化。当前草稿已保留；请重新打开“添加网页来源”再试。';

  @override
  String get webSourcesDraftLabel => '用户选择的网页来源（未验证；摘录属于不可信的来源材料）：';

  @override
  String get usageScopedTotals => '总计';

  @override
  String get usageInspectionDisclosure =>
      '筛选仅作用于此服务器返回的模型记录。它不会改变报告的日期或项目范围，也不显示订阅额度。';

  @override
  String get usageSearchRecords => '搜索提供商、模型或变体';

  @override
  String get usageScopedProviderTotals => '提供商卡片显示所选报告范围内的总计，而不只是匹配的模型行。';

  @override
  String usageMatchingRecords(String count) {
    return '$count 条匹配记录';
  }

  @override
  String get pendingAuthDetail => '请在浏览器中完成登录，然后返回这里完成。浏览器链接不会被保存。';

  @override
  String get pendingAuthEnterCode => '输入代码';

  @override
  String get pendingAuthStillPending => '登录仍在等待中。未发起新的尝试。';

  @override
  String get pendingAuthServerFailed => '服务器报告登录失败。服务商的错误详情已隐藏。';

  @override
  String get pendingAuthExpired => '此登录已过期。请重新发起，或忽略这一个。';

  @override
  String get pendingAuthFailed => '无法确认该操作。重试前请先查看待完成的登录。未发起新的登录。';

  @override
  String get pendingAuthSaveUncertain => '此手机无法保存登录信息以便稍后继续。请保持应用打开直到完成。';

  @override
  String get pendingAuthRetrySave => '再次尝试保存恢复信息';

  @override
  String get pendingAuthForget => '忽略此登录';

  @override
  String get pendingAuthUnsupported => '此服务器无法恢复之前的登录。旧版登录只有在原界面和原连接仍然可用时才有效。';

  @override
  String get pendingAuthOtherSource => '其他待处理的登录属于另一台服务器或项目。请回到它们原本的来源处管理。';

  @override
  String get connectionHelpGuideTip =>
      '请勿让服务器暴露在公网。请通过 Tailscale 的私有 HTTPS 访问它，或以运行本应用的设备为终点的加密隧道。你电脑上的 localhost 并不是你手机上的 localhost。';

  @override
  String get voiceConversationTitle => '语音对话';

  @override
  String get voiceConversationDescription =>
      '说完后点按“发送”：你说的内容会发给智能体。仅在你开启后才会朗读回复。';

  @override
  String get voiceConversationReplyReviewNeeded =>
      '回复已完成，但无法确定它对应你的哪条消息。如需查看请自行阅读。';

  @override
  String get voiceConversationReplyInterrupted => '该回复需要在屏幕上做出决定，因此没有自动朗读。';

  @override
  String get voiceConversationReplyNoProse => '该回复没有可朗读的正文。代码和工具细节不会被朗读。';

  @override
  String get voiceConversationReplyFailed => '无法朗读该回复。';

  @override
  String get voiceConversationPausedDetail =>
      '语音对话已暂停。请重新连接、等待回复，或在屏幕上处理待决定的事项。';

  @override
  String get voiceConversationDraftFirst => '开始语音对话前，请先发送、保存或清除当前草稿。';

  @override
  String get voiceConversationCommandsOnly => '斜杠命令请使用文字输入框。';

  @override
  String get voiceInputUnavailable => '语音输入不可用。请检查本地模型和麦克风设置。';

  @override
  String get desktopDropFailedTitle => '无法附加拖入的文件';

  @override
  String get desktopDropFailedRecovery =>
      '再次尝试前，请先检查已添加的附件。你也可以用键盘打开“添加”，再选择“附加文件”。';

  @override
  String get commandAuthMethodHint =>
      '会在你所选的服务器上运行提供商的登录方式，而不是在此手机上。你可能需要在服务器上完成交互步骤。';

  @override
  String get commandAuthStart => '开始服务器登录';

  @override
  String get commandAuthPending => '正在服务器上登录…请在那里完成它要求的步骤。关闭此处不会中止登录。';

  @override
  String get commandAuthCancel => '取消登录';

  @override
  String get commandAuthFailed => '登录未完成。';

  @override
  String get commandAuthComplete => '已登录。';

  @override
  String get commandAuthExpired => '登录在完成前超时。';

  @override
  String get commandAuthScopeChanged => '你已切换到其他服务器或项目。请回到那里查看此次登录。';

  @override
  String get commandAuthUncertainStart => '服务器可能已经开始登录。再次尝试前请先在服务器上确认。';

  @override
  String get readAloudAction => '朗读回复正文';

  @override
  String get readAloudStop => '停止朗读';

  @override
  String get readAloudOtherVoice => '用其他声音朗读';

  @override
  String get readAloudChooseVoice => '选择朗读声音';

  @override
  String get readAloudConsentTitle => '朗读回复？';

  @override
  String get readAloudConsentDetail => '手机的语音引擎会朗读回复。代码和工具细节会被跳过。此手机会记住你的选择。';

  @override
  String get readAloudContinue => '朗读';

  @override
  String get readAloudUnsupported => '此平台不支持朗读。';

  @override
  String get readAloudNoVoice => '没有可用的已安装离线声音。请在系统语音设置中配置离线声音后重试。';

  @override
  String get readAloudUnavailable => '语音引擎无法朗读此回复。请重试或选择其他声音。';

  @override
  String get readAloudTooLong => '此回复过长，无法朗读。请选择较短的回复。';

  @override
  String get readAloudBusy => '录音或其他音频中断进行时无法播放语音。';

  @override
  String get readAloudNoProse => '此回复没有可朗读的正文。代码和工具细节不会被朗读。';

  @override
  String get credentialMetadataOnly => '密钥仅保留在你的服务器上。';

  @override
  String get credentialActiveUpdated => '已从服务器更新当前账号。';

  @override
  String get credentialSwitchRequested => '已请求切换。服务器事件尚未确认此请求。';

  @override
  String get credentialActive => '使用中';

  @override
  String get credentialRename => '重命名账号';

  @override
  String get credentialLabel => '账号标签';

  @override
  String get credentialSave => '保存标签';

  @override
  String get credentialScopeChanged => '服务器或项目已更改。请关闭并重新打开账号管理后再进行更改。';

  @override
  String get credentialProviderMissing => '此服务商已不在服务器的集成列表中。';

  @override
  String get credentialLoadFailed => '无法刷新已保存的账号。请重试。';

  @override
  String get credentialMutationFailed => '无法确认账号变更。请先刷新再重试；服务器可能已经应用了该变更。';

  @override
  String get credentialRefresh => '刷新账号';

  @override
  String get credentialEmpty => '此服务商没有已保存的账号。';

  @override
  String get credentialEnvironment => '由服务器环境管理，无法在此移除。';

  @override
  String credentialUnnamed(int index) {
    return '已保存的账号 $index';
  }

  @override
  String get mcpRemoveFailed => '无法确认 MCP 已被移除。请刷新列表后重试；服务器可能已经应用了该更改。';

  @override
  String get mcpLoadFailed => '无法刷新 MCP 数据。请重试。';

  @override
  String get mcpSavedStatus => '已保存在 OpenCode 中';

  @override
  String get mcpRetryReconnect => '再次尝试重新连接';

  @override
  String get mcpReconnecting => '正在重新连接';

  @override
  String get mcpStillDisconnected => 'OpenCode 仍处于断开状态。请重试。';

  @override
  String get mcpScopeChanged => '服务器或项目已变更。请刷新以加载其 MCP 服务器后再做更改。';

  @override
  String get promptStashRestoreFailed => '未能完成提示词恢复。已保存的副本仍然可用；重试前请检查输入框。';

  @override
  String get promptStashContextOnly => '附件与引用';

  @override
  String get promptDefaultLocation => '服务器默认目录';

  @override
  String get promptStashed => '提示词已保存到暂存区。';

  @override
  String get promptStashedDraftPending => '提示词已保存到暂存区。编辑框中的草稿仍需要保存；请从草稿警告中重试。';

  @override
  String get promptStashReadFailed => '无法读取已保存的提示词。其存储的数据已保留。';

  @override
  String get promptStashDescription => '保存文字、附件和引用以备后用';

  @override
  String get promptRestored => '已恢复保存的提示词';

  @override
  String promptStashLocation(String directory) {
    return '此提示词引用了 $directory 中的文件。请先切换到它原本所属的项目再恢复。';
  }

  @override
  String get promptStashScopeChanged => '服务器或项目已变更。请关闭并重新打开“已保存的提示词”。';

  @override
  String get transcriptFindHint => '搜索对话';

  @override
  String get transcriptFindClose => '关闭搜索';

  @override
  String get transcriptFindPrevious => '上一个匹配项';

  @override
  String get transcriptFindNext => '下一个匹配项';

  @override
  String get transcriptFindNone => '无匹配项';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '第 $current / 共 $total 个匹配项',
      one: '1 个匹配项',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial => '仅限已加载的消息。加载更早的消息可扩大搜索范围。';

  @override
  String get transcriptFindReasoning => '推理';

  @override
  String get transcriptFindTool => '工具数据';

  @override
  String get transcriptFindFile => '文件名';

  @override
  String get transcriptFindAll => '搜索全部历史记录';

  @override
  String get skillUse => '添加到对话';

  @override
  String get skillActivationHelp => '将这些技能说明添加到此对话。你尚未发送的草稿会留在编辑框中。';

  @override
  String get skillRunNow => '立即运行智能体';

  @override
  String get skillRunHelp => '关闭后只添加技能，不启动新的回复。';

  @override
  String get skillLocationChanged => '服务器或项目已变更。请从对话中重新打开“技能”。';

  @override
  String get skillUnsupported => '此服务器不支持激活技能。你仍然可以预览技能。';

  @override
  String get skillStaged => '添加技能前，请先在对话中处理待定的撤销。';

  @override
  String get skillBusy => '已有技能正在添加到此对话。';

  @override
  String get skillUncertain => '服务器未确认结果。技能可能已添加。请关闭此面板，确认对话后再重试。';

  @override
  String get skillApplied => '已将技能添加到此对话。';

  @override
  String get skillAppliedOriginal => '技能已添加到原对话。关闭此面板即可返回。';

  @override
  String get activeContextTitle => '活动上下文';

  @override
  String get activeContextSubtitle => '查看压缩后的消息';

  @override
  String get activeContextRefresh => '刷新活动上下文';

  @override
  String get activeContextSearch => '搜索活动消息';

  @override
  String get activeContextEmpty => '服务器未返回任何活动上下文消息。';

  @override
  String get activeContextNoText => '此条目中没有受支持的文本内容。';

  @override
  String get activeContextUnsupported => '此服务器不支持查看活动上下文。';

  @override
  String get activeContextChanged => '服务器、项目或对话已变更。请从对话中重新打开此查看器。';

  @override
  String get activeContextInvalid => '服务器返回了无效的上下文快照。请刷新重试。';

  @override
  String activeContextRefreshFailed(String error) {
    return '显示的是先前的快照。无法刷新：$error';
  }

  @override
  String get activeContextContentHelp =>
      '可用消息内容的快照。不显示二进制附件正文、网址和内部元数据。这不是提供商收到的完整请求。';

  @override
  String get activeContextUser => '用户提示词';

  @override
  String get activeContextAssistant => '助手';

  @override
  String get activeContextSystem => '系统指示';

  @override
  String get activeContextSynthetic => '合成消息';

  @override
  String get activeContextSkill => '技能';

  @override
  String get activeContextShell => 'Shell';

  @override
  String get activeContextCompaction => '压缩';

  @override
  String get activeContextChange => '对话变更';

  @override
  String get activeContextText => '文本';

  @override
  String get activeContextToolInput => '工具输入';

  @override
  String get activeContextToolOutput => '工具输出';

  @override
  String get activeContextFile => '文件附件';

  @override
  String get activeContextNotice => '服务器通知';

  @override
  String get activeContextPruned => '内容已被服务器清理';

  @override
  String get activeContextTruncated => '输出已被服务器截断';

  @override
  String get draftSaveFailed => '草稿未保存。请复制文字或重试。';

  @override
  String get draftStorageFull => '草稿存储空间已满。请先复制你的文字再离开。';

  @override
  String get draftProfileRemoved => '原服务器已被移除。请复制你的草稿以保留。';

  @override
  String get draftRetrySave => '重试保存草稿';

  @override
  String get draftClearFailed => '无法清除已保存的草稿。请先重试再离开。';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => '你的草稿尚未保存';

  @override
  String get draftLeaveMessage => '复制你的文字以保留，或再次尝试保存。如果不保存就离开，最近的修改可能会丢失。';

  @override
  String get draftLeaveAction => '不保存并离开';

  @override
  String get draftKeepEditing => '继续编辑';

  @override
  String get draftUnsaved => '未保存';

  @override
  String get draftAttachmentsLocal => '附件会随这份草稿一起保存在本设备上。';

  @override
  String get draftAttachmentsFailed => '附件需要恢复或无法保存。请先重试再发送。';

  @override
  String get draftAttachmentRecoveryTitle => '部分附件需要处理';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return '这些已保存的附件缺失、无法读取或属于其他项目：$names。你可以使用可用的附件并从草稿中移除这些，或保留已保存的草稿，稍后重试。';
  }

  @override
  String get draftUseAvailableAttachments => '使用可用的附件';

  @override
  String get draftKeepSavedAttachments => '保留已保存的草稿';

  @override
  String get photoLibraryAction => '照片库';

  @override
  String get photoLibraryDescription => '选择照片或截图';

  @override
  String get photoCameraAction => '拍照';

  @override
  String get photoTooLarge => '请选择小于 10 MB 的照片。';

  @override
  String get photoStorageFailed => '无法将照片保存到本设备。请释放一些空间后重试。';

  @override
  String get photoPendingOther => '另一个对话中还有一张照片在等待处理。请先在那里添加或丢弃，然后重试。';

  @override
  String get photoUnavailable => '无法打开这张照片。请从“照片库”或“拍照”重新添加。';

  @override
  String get photoPermissionDenied => '照片访问被拒绝。请在 Android 应用设置中允许访问相机或照片，然后重试。';

  @override
  String get photoPendingTitle => '待处理的照片';

  @override
  String get photoDiscard => '丢弃待处理的照片';

  @override
  String get photoAddToDraft => '将恢复的照片加入草稿';

  @override
  String get photoOtherLocation => '请先回到这张照片所属的服务器和项目，再添加它。';

  @override
  String get photoDraftFull => '请先移除一个附件。一份草稿最多可含 5 个文件，总计 20 MB。';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

  @override
  String get iosRemoteSummary =>
      '这是你所选 OpenCode 服务器的远程客户端。此 iOS 版本不提供设备端服务器托管和后台监控。';

  @override
  String get iosKeychainGuide => '服务器密码使用此设备的钥匙串保存，不会以明文形式写入应用偏好设置。';

  @override
  String get platformSecureStorageGuide => '服务器密码使用此平台的安全凭据存储，不会以明文形式写入应用偏好设置。';

  @override
  String get usageProviders => '提供商';

  @override
  String get usageProviderScope => '所选范围内、此服务器返回的模型记录总计。不是提供商账单或订阅额度。';

  @override
  String usageProviderModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个模型',
      one: '1 个模型',
    );
    return '$_temp0';
  }

  @override
  String get usageProviderCostUnavailable => '费用小计不可用';

  @override
  String usageProviderCostShare(String percent) {
    return '占已报告费用的 $percent';
  }

  @override
  String get setupOutputWaiting => '正在等待 Termux 输出…';

  @override
  String get uncertainAuthDetail => '服务器可能已开始此次登录但未确认。重新开始前请先在服务器上检查。';

  @override
  String get uncertainAuthCloseHint => '若要重新开始，请关闭此页面，并从该提供商所在行清除未确认的登录。';

  @override
  String get pluginsUnsupported => '此服务器不支持查看插件。';

  @override
  String get pluginsDisconnected => '连接到服务器以查看其插件。';

  @override
  String get pluginsEmpty => '此项目未报告任何插件。';

  @override
  String get pluginsLoadFailed => '无法加载插件。请重试。';

  @override
  String get pluginsRetry => '重试';

  @override
  String get pluginsUnnamed => '无 ID 的插件';

  @override
  String get pluginsStatusActive => '启用中';

  @override
  String get pluginsStatusUnknown => '状态未知';

  @override
  String get pluginsSourceBuiltin => '内置';

  @override
  String get pluginsSourcePackage => '软件包';

  @override
  String get pluginsSourceLocal => '本地文件（路径已隐藏）';

  @override
  String get pluginsSourceSdk => 'SDK';

  @override
  String get pluginsSourceUnknown => '未知来源';

  @override
  String get pluginsTerminalUi => '声明了终端界面';

  @override
  String get pluginsFailureDetail => '失败详情已隐藏，因为其中可能包含凭据。';

  @override
  String get demoReviewChanges => '查看更改';

  @override
  String get demoSetUpServer => '设置你自己的服务器';

  @override
  String get handoffCopyCommand => '复制命令';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get managedHealthLifetime =>
      'Android 可能会停止任一应用。保持手机连接活跃，并不能保证 Termux 服务器整夜持续运行。';

  @override
  String get quotaBudgetOff => '关闭';

  @override
  String get quotaBudgetSaveFailed => '无法保存此预算更改。上次保存的设置仍然有效。';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get usageBudgetTitle => '预算';

  @override
  String get usageBudgetDescription =>
      '预算基于所选服务器、项目、时区和日期窗口起点的全部已报告消耗。模型筛选不影响预算。更改窗口起点需要重新设置预算。预算不会改变订阅额度，也不会中止请求。';

  @override
  String get usageBudgetUsd => '设置美元预算';

  @override
  String get usageBudgetTokens => '设置 token 预算';

  @override
  String get usageBudgetAmount => '预算金额';

  @override
  String get usageBudgetRemove => '移除预算';

  @override
  String usageBudgetProgress(String used, String limit, String unit) {
    return '$used / $limit $unit';
  }

  @override
  String get usageBudgetTokenUnit => 'tokens';

  @override
  String get usageBudgetReached => '本次读取已达到个人预算。';

  @override
  String get usageBudgetPrevious => '上次读取已达到此预算。请刷新以查看当前消耗。';

  @override
  String get usageBudgetClearAll => '清除两个预算';

  @override
  String get usageBudgetClearDescription => '移除此已保存服务器的所有当前及历史消耗预算？提供商的阈值会保留。';

  @override
  String get usageBudgetClearTitle => '清除消耗预算？';

  @override
  String get monitorScope => '计数涵盖每个服务器上次所选的项目，而非该服务器上的所有项目。';

  @override
  String get monitorDisclosure =>
      '在你为某个服务器启用之前，监控处于关闭状态。应用打开时，大约每分钟检查一次。后台检查的间隔不少于五分钟，且仅在“在后台保持连接”已开启并且 Android 服务正在运行时进行。Android 可能会停止该服务，不保证剩余运行时间。';

  @override
  String get monitorOptIn => '监控此服务器';

  @override
  String get monitorOptInDetail => '检查其上次所选项目中待处理的权限、问题和表单。';

  @override
  String get monitorNotifications => '需要关注时通知';

  @override
  String get monitorWifiDetail => '没有 Wi-Fi 时暂停检查。';

  @override
  String get monitorQuiet => '静默时段';

  @override
  String get monitorQuietStart => '静默时段开始';

  @override
  String get monitorQuietEnd => '静默时段结束';

  @override
  String get monitorSaveFailed => '无法保存监控设置。请重试。';

  @override
  String get monitorOpenFailed => '此请求或其项目已变更。请刷新收件箱后重试。';

  @override
  String get monitorSession => '对话';

  @override
  String get monitorUnknown => '未知';

  @override
  String get monitorLastChecked => '上次检查';

  @override
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  ) {
    return '$profile · $kind\n$lastChecked：$time';
  }

  @override
  String get monitorCheckIn => '长时间工作时提醒';

  @override
  String get monitorCheckInDetail => '到你设定的时间后提醒。';

  @override
  String get monitorCheckInDetailForeground => '到你设定的时间后显示提醒行。';

  @override
  String get monitorCheckInAfter => '查看时间';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes 分钟',
      one: '1 分钟',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => '该检查了';

  @override
  String managedRecoveryAttempts(int attempts) {
    return '已用尝试：$attempts/3。该上限在应用重启后仍然有效。';
  }

  @override
  String get managedRecoveryExhausted => '已达恢复上限。请先检查服务器并手动启动它，然后再重置重试额度。';

  @override
  String get managedRecoveryBackground => '应用处于后台时，恢复会等待。';

  @override
  String get managedRecoveryChecking => '正在检查托管恢复操作…';

  @override
  String managedRecoveryNext(String time) {
    return '下次恢复尝试不早于 $time。';
  }

  @override
  String get managedRecoveryCheck => '检查恢复状态';

  @override
  String get managedRecoveryReset => '重置重试额度';

  @override
  String get managedRecoverySaveFailed => '无法保存恢复设置。请重试。';

  @override
  String get managedRecoveryRevokeFailed => '无法保存或撤销恢复。请保留此服务器并重试，然后再移除它。';

  @override
  String get managedRecoverySettingsUnreadable => '无法读取恢复设置。启用恢复前请先检查服务器。';

  @override
  String get managedRecoveryEnableFailed => '无法启用恢复。请启动托管服务器后重试。';

  @override
  String get managedRecoveryOwnershipChanged => '托管操作已变更。再次启用恢复前请先检查服务器。';

  @override
  String get managedRecoveryUncertain => 'Termux 未确认结果，恢复已暂停。请刷新以继续。';

  @override
  String get managedRecoveryRetryDisable => '再次尝试禁用恢复';

  @override
  String get pluginMappingUnavailable => '此插件或命令在此已不可用。请刷新并检查你的关联。';

  @override
  String get mobileTasksUnfinished => '仅显示未完成';

  @override
  String get mobileTasksNoUnfinished => '此列表中没有未完成的任务。';

  @override
  String get mobileTaskPending => '待处理';

  @override
  String get mobileTaskInProgress => '进行中';

  @override
  String get mobileTaskCompleted => '已完成';

  @override
  String get mobileTaskCancelled => '已取消';

  @override
  String mobileTasksProgress(int done, int total) {
    return '已完成 $done/$total';
  }

  @override
  String get mobileTasksCopyAll => '复制所有任务';

  @override
  String get mobileTaskPriorityHigh => '高优先级';

  @override
  String get mobileTaskPriorityMedium => '中优先级';

  @override
  String get mobileTaskPriorityLow => '低优先级';

  @override
  String get usageWidgetTitle => '用量小组件';

  @override
  String get usageWidgetIntro => '在桌面“用量”小组件中显示套餐剩余量。应用运行时更新。';

  @override
  String get usageWidgetPhoneTitle => '本机';

  @override
  String get usageWidgetSearchAliases =>
      '用量 小组件 桌面 claude codex 套餐 上限 剩余 paseo';

  @override
  String get quotaMonitorSourceChanged =>
      '此服务器上的账号已变更，因此检查已停止。请在该服务器上打开“剩余用量”并重新读取。';

  @override
  String get webSearchDisclosure =>
      '搜索会把你的查询发送给此服务器选定的搜索提供商。把结果加入可编辑草稿之前请先核对。此处不会向模型发送任何内容。';

  @override
  String get webSearchManual => '或粘贴一个来源';

  @override
  String get webSearchUnavailable =>
      '网络搜索不可用。请在此服务器上配置搜索提供商，然后刷新提供商。你仍可在下方粘贴来源。';

  @override
  String get webSearchAuthentication => '服务器未授权网络搜索。请检查此服务器的凭据。';

  @override
  String get webSearchInvalidResponse => '搜索结果与此服务器或支持的格式不匹配。请刷新提供商或粘贴来源。';

  @override
  String get webSearchFailed => '网络搜索未能完成。请重试或粘贴来源。';

  @override
  String get webSearchRefresh => '刷新提供商';

  @override
  String get webSearchProvider => '搜索提供商';

  @override
  String get webSearchQuery => '搜索关键词';

  @override
  String get webSearchSubmit => '搜索';

  @override
  String get webSearchEmpty => '此查询没有可用的结果。';

  @override
  String get webSearchOmitted => '部分结果因链接或摘录超出审核限制而被省略。';

  @override
  String get queueStorageUnreadable => '无法读取已保存的排队提示词。在清除此设备数据之前，无法将新的提示词加入队列。';

  @override
  String get queueStorageDiscardUnreadable =>
      '这将从此设备永久删除无法读取的排队提示词及其附件。其内容和数量均未知。服务器上的内容不受影响。';

  @override
  String get filesViewerScopeChanged => '服务器已更改。请关闭并重新打开此文件。';

  @override
  String get queueStorageCountUnknown => '无法读取已保存的排队数据。排队提示词的数量未知。';

  @override
  String get codexConnectionVerified => '连接已验证。保存并连接以继续。';

  @override
  String get codexApprovalRecoveryNotice => '重新连接后，请在你的电脑上检查待处理的批准。';

  @override
  String connectionPasswordUnreadable(String server) {
    return '无法读取已保存的 $server 密码';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return '无法读取已保存的 $server 令牌';
  }

  @override
  String get connectionEnterPassword => '输入密码';

  @override
  String get connectionEnterToken => '输入令牌';

  @override
  String get connectionPasswordUnreadableDetails =>
      '此手机的安全存储无法打开为该服务器保存的密码。从备份恢复手机或更改屏幕锁后可能出现这种情况。密码本身没有改变：请重新输入以连接。';

  @override
  String get connectionTokenUnreadableDetails =>
      '此手机的安全存储无法打开为该服务器保存的令牌。从备份恢复手机或更改屏幕锁后可能出现这种情况。令牌本身没有改变：请重新输入以连接。';

  @override
  String get updateConnectionToken => '更新令牌';

  @override
  String get codexTextOnlyPrompt => '此服务器仅支持文字。发送前请移除附件。';

  @override
  String get codexOfflineDraftSaved => '请先重新连接再发送。草稿保存在本设备上。';

  @override
  String get codexReconnectBeforeSending => '请先重新连接再发送。';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 或 wss://paseo.example';

  @override
  String get paseoPasswordLabel => '守护进程密码（可选）';

  @override
  String get paseoPasswordHelp =>
      '用 \"paseo daemon set-password\" 设置。保存在本设备的安全存储中。';

  @override
  String get paseoSetupNotice =>
      '在安装了 Claude Code 或 Pi 的电脑上运行 \"paseo start --no-relay\"。本应用从不使用 Paseo 中继：请在此设备上连接，或通过你自己的私有网络连接。';

  @override
  String get connectionDisplayName => '显示名称（可选）';

  @override
  String get connectionDisplayNameHint => '默认为服务器主机';

  @override
  String get connectionServerAddress => '服务器地址';

  @override
  String get codexAddressHint => 'wss://codex.example 或 ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => '服务器上的项目文件夹';

  @override
  String get codexTokenReentry => '重新输入连接令牌';

  @override
  String get codexTokenLabel => '连接令牌';

  @override
  String get codexTokenStorageHelp => '安全保存在本设备上，仅发送给此 Codex 服务器。';

  @override
  String get projectConfiguredFolder => '已配置的文件夹';

  @override
  String get termuxGuideTitle => '连接一次 Termux';

  @override
  String get termuxGuideOpenFailed =>
      '命令已复制，但无法打开 Termux。请自行打开 Termux，或再试一次“复制并打开 Termux”。';

  @override
  String get termuxGuideCopyOpenFailed => '无法复制命令或打开 Termux。';

  @override
  String get termuxPermissionDenied => 'Android 不允许本应用在 Termux 中运行命令。';

  @override
  String get launchShortcutWaiting => '正在连接已保存的服务器。就绪后会打开新对话。';

  @override
  String get launchShortcutNoServer => '请选择一个服务器，然后开始新对话。';

  @override
  String get launchShortcutReentry => '请输入已保存服务器的凭据，然后开始新对话。';

  @override
  String get launchShortcutConnectionFailed =>
      '无法连接到已保存的服务器。请选择或修复服务器，然后开始新对话。';

  @override
  String get launchUiPinnedUntitled => '未命名对话';

  @override
  String get launchUiSessionWaiting => '正在连接已保存的服务器。就绪后会打开该对话。';

  @override
  String get launchUiSessionNoServer => '请选择一个服务器，然后从其列表中打开该对话。';

  @override
  String get launchUiSessionReentry => '请输入已保存服务器的凭据，然后从其列表中打开该对话。';

  @override
  String get launchUiSessionConnectionFailed =>
      '无法连接到已保存的服务器。请选择或修复服务器，然后从其列表中打开该对话。';

  @override
  String get launchUiSessionOtherServer =>
      '该快捷方式属于另一个服务器。请连接到该服务器，然后从其列表中打开对话。';

  @override
  String get launchUiActivityNoServer => '选择一个服务器，查看需要你关注的事项。';

  @override
  String get launchUiUsageNoServer => '保存一个服务器，即可查看你的套餐还剩多少额度。';

  @override
  String get queuedResendTitle => '要再次发送这份草稿吗？';

  @override
  String get queuedResendMessage => '它可能已经到达 OpenCode，再次发送可能会造成重复。';

  @override
  String get queuedResendConfirm => '重新发送';

  @override
  String get queuedKeepForReview => '保留以便查看';

  @override
  String get queuedDiscardUnconfirmedMessage => '它之前的发送一直没有得到确认，可能已经在对话里了。';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 份草稿的发送尚未确认，需要查看。',
      one: '1 份草稿的发送尚未确认，需要查看。',
    );
    return '$_temp0';
  }

  @override
  String get isolatedTaskTitle => '在独立副本中开始';

  @override
  String get isolatedTaskIntro => '它在自己的分支上工作，因此不会与其他对话冲突。';

  @override
  String get isolatedTaskNameLabel => '副本名称（可选）';

  @override
  String get isolatedTaskNameHelper => '留空即可自动命名。';

  @override
  String get isolatedTaskStart => '开始';

  @override
  String get isolatedTaskCreating => '正在创建副本…';

  @override
  String get isolatedTaskCreatingHint =>
      '如果你停止等待，副本可能仍会被创建。你可以在“项目 › 工作树”下找到它。';

  @override
  String isolatedTaskPreparing(String name) {
    return '正在设置 $name…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name 已就绪。正在打开对话…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name 已就绪，但对话未能打开。';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return '$name 已创建，但其设置尚未返回结果。';
  }

  @override
  String get isolatedTaskUnconfirmedHint => '设置可能仍在进行。你可以继续等待，或立即在它上面开始。';

  @override
  String isolatedTaskFailed(String name) {
    return '在 $name 中的设置失败';
  }

  @override
  String get isolatedTaskCreateFailed => '无法创建副本';

  @override
  String get isolatedTaskCancelled => '已停止等待。';

  @override
  String isolatedTaskOpening(String name) {
    return '正在 $name 中打开对话…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return '$name 中的对话已就绪。';
  }

  @override
  String get isolatedTaskStopWaiting => '停止等待';

  @override
  String get isolatedTaskKeepWaiting => '继续等待';

  @override
  String get isolatedTaskRetryOpen => '重试';

  @override
  String get isolatedTaskClose => '关闭';

  @override
  String get returnBriefContinue => '继续';

  @override
  String get capsuleError => '错误';

  @override
  String get capsuleRemove => '移除';

  @override
  String get markdownWrapCode => '自动换行';

  @override
  String get markdownScrollCode => '滚动显示行';

  @override
  String get markdownReaderTitle => '代码阅读器';

  @override
  String get tailscaleTitle => '通过 Tailscale 连接';

  @override
  String get tailscaleIntro => '通过你自己的 Tailscale 网络访问另一台电脑上的 OpenCode。';

  @override
  String get tailscaleAppStep => '1. 打开你的私有网络';

  @override
  String get tailscaleChecking => '正在检查 Tailscale 应用…';

  @override
  String get tailscaleInstalled => 'Tailscale 已安装。';

  @override
  String get tailscaleMissing => '未安装 Tailscale。请安装官方应用，然后返回再次检查。';

  @override
  String get tailscaleUnknown => '无法检查该应用。请重试，或从手机上打开 Tailscale。';

  @override
  String get tailscaleUnsupported =>
      '此设备无法打开 Android 应用。请自行在此设备上设置 Tailscale，然后检查下方的 HTTPS 地址。';

  @override
  String get tailscaleVpnHandoff =>
      '在 Tailscale 中登录可访问你服务器的网络，如果 Android 弹出 VPN 请求请予以允许，然后开启连接。OpenCode 无法查看或更改该 VPN 的状态。';

  @override
  String get tailscaleReturned => '欢迎回来。已重新检查应用是否存在；请在下一屏使用“测试连接”来检查你的服务器。';

  @override
  String get tailscaleOpen => '打开 Tailscale';

  @override
  String get tailscaleCheckAgain => '再次检查 Tailscale';

  @override
  String get tailscaleAddressStep => '2. 检查服务器地址';

  @override
  String get tailscaleAddressLabel => '私有服务器地址';

  @override
  String get tailscaleAddressDetail =>
      '粘贴 Tailscale Serve 提供的完整 HTTPS 地址，或你的服务器在 Tailscale 上的 HTTP 地址和端口。HTTP 会在连接前征求你的许可。';

  @override
  String get tailscaleAddressError =>
      '请输入完整的 HTTP 或 HTTPS 服务器地址，必要时带端口，后面不要加路径。';

  @override
  String get tailscaleReviewDetail =>
      '仅在地址是你认识的情况下继续。下一个页面会在你明确测试或保存之前检查服务器凭据。本应用无法仅凭名称确认某个地址是私有的。';

  @override
  String get tailscaleContinue => '继续登录';

  @override
  String get tailscaleHelp => 'Tailscale 设置与恢复';

  @override
  String get tailscaleServeHelp =>
      '在服务器所在的计算机上，Tailscale Serve 可以为本地 OpenCode 端口提供私有 HTTPS。请使用 Serve，不要使用公开的 Funnel。你的 tailnet 访问规则仍然适用。启用 HTTPS 会把证书中的设备名和 tailnet 名发布到公开的证书日志中，但访问仍是私有的。更改服务器之前，请先阅读官方指南。';

  @override
  String get tailscaleServeDocs => '阅读 Serve 官方指南';

  @override
  String get tailscaleAndroidDocs => '阅读 Android 官方指南';

  @override
  String get tailscaleRecovery =>
      '如果无法访问服务器，请检查两台设备上的 Tailscale、地址和端口，以及网络访问规则。若使用 Serve 地址，还要检查服务器上的 Serve。若认证被拒绝，请更正密码，然后重新测试连接。';

  @override
  String get tailscaleEditorDetail =>
      '你的网络连接由 Tailscale 管理。“测试连接”检查的是此 OpenCode 服务器，而不是 VPN。请在此输入服务器自己的用户名和密码，而不是 Tailscale 登录信息。查看设置帮助时这些字段会保留。';

  @override
  String get a2aDraftSaveError => '无法保存草稿更改。请保持此页面打开，在离开前重试。';

  @override
  String get a2aRetryDraftSave => '再次尝试保存草稿';

  @override
  String get a2aSavingDraft => '正在保存草稿更改…';

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · 文本任务';

  @override
  String get a2aTitle => '外部智能体';

  @override
  String get a2aAdd => '添加智能体';

  @override
  String get a2aDeleteLocal => '从此手机移除';

  @override
  String get a2aAddress => '智能体地址';

  @override
  String get a2aUnsupported =>
      '不可用：此卡片在同一源上未声明受支持的 A2A 1.0 JSON-RPC、文本和身份验证组合，或要求不受支持的扩展。无法发送任何任务。';

  @override
  String get a2aBearer => '智能体 bearer 凭据';

  @override
  String get a2aSkills => '已声明的技能';

  @override
  String get a2aTaskPrompt => '任务文本';

  @override
  String get a2aDeliveryUnconfirmed => '送达未确认';

  @override
  String get a2aDraft => '未发送';

  @override
  String get a2aCancelDetail => '请此智能体停止该任务。工作可能已经完成，是否能停止由智能体决定。';

  @override
  String get a2aYourReply => '你的回复';

  @override
  String get a2aAgentOutput => '智能体输出';

  @override
  String get a2aBlockedLink => '不支持的链接';

  @override
  String get a2aReviewLink => '查看外部链接';

  @override
  String get a2aOmittedContent =>
      '部分输出已被省略。此视图只显示长度受限的文本和链接；二进制或结构化产物不会被下载或执行。';

  @override
  String get a2aSubmitted => '已提交';

  @override
  String get a2aWorking => '处理中';

  @override
  String get a2aInputRequired => '需要你提供信息';

  @override
  String get a2aAuthRequired => '智能体要求身份验证';

  @override
  String get a2aCompleted => '已完成';

  @override
  String get a2aFailed => '失败';

  @override
  String get a2aCanceled => '已取消';

  @override
  String get a2aRejected => '已拒绝';

  @override
  String get a2aUnknown => '不支持的任务状态';

  @override
  String get a2aAddressError =>
      '请使用不含凭据、查询参数或片段的 HTTPS 源，或公开 Agent Card 网址。HTTP 仅支持本设备的回环地址。';

  @override
  String get a2aAuthenticationError =>
      '智能体拒绝了此凭据，或无法使用它。请回到该智能体更新凭据，然后重新打开已保存的任务。';

  @override
  String get a2aUnavailable => '无法连接该智能体，或它拒绝了此操作。请刷新一个已知任务以检查其状态。';

  @override
  String get a2aInvalidResponse => '智能体返回了不受支持、过大或不匹配的响应。已保存的任务未被替换。';

  @override
  String get a2aUncertain =>
      '智能体可能已收到此消息，它不会被重发。如果已确认任务 ID，请刷新查看进度；否则请先与该智能体核对，再开始新任务。';

  @override
  String get a2aStorageError => '无法保存或删除本地数据。请检查设备存储并重试本地操作。没有保存送达标记的消息不会被发送。';

  @override
  String get a2aScopeError => '此智能体、凭据或已保存的任务已变更。请关闭此视图并重新打开该智能体以继续。';

  @override
  String get a2aCancelUnconfirmed => '取消尚未确认。智能体仍报告任务处于活动状态；请刷新再次检查。';

  @override
  String get a2aAuthRequiredDetail =>
      '此智能体要求额外的身份验证流程，本客户端不支持。不会自动登录，也不会自动继续任务。';

  @override
  String get a2aUnknownDetail => '不支持此任务状态。你可以刷新或忘记本地记录；发送和取消仍不可用。';

  @override
  String get fileTable => '表格';

  @override
  String get fileSource => '源文件';

  @override
  String fileLineOutsidePreview(int line) {
    return '第 $line 行不在此预览范围内。请保存原文件以查看该位置。';
  }

  @override
  String get fileTableMalformed => '此文件的引号不完整或不一致。请改为查看源文件。';

  @override
  String get fileTableTooLarge => '表格预览支持最大 256 KB 的文件。请改为查看源文件或保存原文件。';

  @override
  String get fileTableTooWide => '此文件超过 32 列。请改为查看源文件或保存原文件。';

  @override
  String get fileTableFieldTooLong => '某个单元格超过 4,096 个字符。请改为查看源文件或保存原文件。';

  @override
  String get fileImage => '图片';

  @override
  String get fileSvgUnsupported =>
      '此 SVG 无法显示为本地静态图片。请改为查看源文件或保存原文件。不支持外部资源、动画和复杂的 SVG 特性。';

  @override
  String get filePdfEncrypted => '此 PDF 需要密码，或使用了不受支持的加密方式。请保存原文件后用 PDF 应用打开。';

  @override
  String get filePdfLimit => 'PDF 预览支持最大 10 MB、最多前 200 页。请保存原文件以阅读完整文档。';

  @override
  String get filePdfUnavailable => 'PDF 查看需要 Android 10 或更高版本。你仍然可以保存原文件。';

  @override
  String get filePdfCancelled => 'PDF 加载已取消。准备好后可重试。';

  @override
  String get filePdfFailed => '无法显示此 PDF 页面。请重试或保存原文件。';

  @override
  String get agentAccountTitle => 'Codex 账号';

  @override
  String get agentAccountScopeLost => '此服务器已变更。请返回“服务器”，并打开已连接服务器的账号。';

  @override
  String get agentAccountRefresh => '刷新账号';

  @override
  String get agentAccountLoading => '正在检查主机账号';

  @override
  String get agentAccountUnavailable => '账号面板不可用';

  @override
  String get agentAccountReadFailed => '无法读取账号';

  @override
  String get agentAccountDisconnected => '连接已中断';

  @override
  String get agentAccountConnected => '已在主机上登录';

  @override
  String get agentAccountSignedOut => '可以登录';

  @override
  String get agentAccountInProgress => '正在登录';

  @override
  String get agentAccountNeedsAttention => '登录需要处理';

  @override
  String get agentAccountNoAuth => '主机无需登录';

  @override
  String get agentAccountApiKey => 'API 密钥';

  @override
  String get agentAccountHostAuth => '主机身份验证';

  @override
  String get agentAccountHostNote =>
      '登录信息保存在服务器的电脑上，而非本应用中。在此登录或退出，会同时改变该电脑上所有已保存服务器的状态。';

  @override
  String get agentAccountUnsupportedDetail =>
      '此面板已通过 Codex 0.153.4 验证。已连接的运行时可能不支持这些账号方法。';

  @override
  String get agentAccountReconnectDetail => '账号数据和登录码已清除。请重新连接以刷新。登录不会自动重启。';

  @override
  String get agentAccountSignIn => '使用 ChatGPT 登录';

  @override
  String get agentAccountSignInNote =>
      '使用你的 ChatGPT 账号登录。你在浏览器中完成操作，本应用不会看到你的密码。';

  @override
  String get agentAccountLimits => '速率限制';

  @override
  String get agentAccountLimitsUnavailable => '此账号或主机无法提供速率限制。';

  @override
  String get agentAccountUsage => 'Token 用量';

  @override
  String get agentAccountUsageUnavailable => '此账号或主机无法提供 Token 用量。';

  @override
  String get agentAccountLifetimeTokens => '累计 Token';

  @override
  String get agentAccountPeakTokens => '单日峰值 Token';

  @override
  String get agentAccountUsageNote =>
      '数值由主机上报。缺失的值表示未知，而非零。Token 数量既不等于账单，也不代表剩余消息额度。';

  @override
  String agentAccountUpdated(String time) {
    return '上次检查 $time';
  }

  @override
  String get agentAccountStarting => '正在请求登录码';

  @override
  String get agentAccountWaiting => '请在浏览器中完成登录';

  @override
  String get agentAccountCancelling => '正在取消登录';

  @override
  String get agentAccountCancelled => '已取消登录';

  @override
  String get agentAccountLoginFailed => '登录未完成。请检查主机后重试。';

  @override
  String get agentAccountLoginUncertain =>
      '主机无法确认登录或取消。它可能仍在等待。再次开始前，请先查看官方主机运行时的状态。';

  @override
  String get agentAccountLoginCompleted => '登录已完成。正在检查账号。';

  @override
  String get agentAccountCodeHint => '请在官方登录页面输入此一次性验证码。请妥善保密。';

  @override
  String get agentAccountOpenSignIn => '打开官方登录';

  @override
  String get agentAccountCancel => '取消登录';

  @override
  String get agentAccountAllowance => '上报的额度';

  @override
  String agentAccountPercentUsed(int percent) {
    return '已用 $percent%';
  }

  @override
  String get agentAccountWindowUnknown => '窗口时长不可用';

  @override
  String agentAccountWindowMinutes(int minutes) {
    return '$minutes 分钟窗口';
  }

  @override
  String agentAccountWindowHours(int hours) {
    return '$hours 小时窗口';
  }

  @override
  String agentAccountWindowDays(int days) {
    return '$days 天窗口';
  }

  @override
  String get agentAccountResetUnknown => '重置时间不可用';

  @override
  String get projectFolderCreate => '新建文件夹';

  @override
  String get projectFolderOpen => '打开项目文件夹';

  @override
  String get projectFolderNameLabel => '文件夹名称';

  @override
  String get projectFolderNameHint => 'my-app';

  @override
  String get projectFolderCreateAction => '创建';

  @override
  String get projectFolderCancel => '取消';

  @override
  String get projectFolderOpenMessage =>
      '请输入服务器上文件夹的完整路径。不能使用主目录本身，请在其中选择一个项目。';

  @override
  String get projectFolderPathLabel => '文件夹路径';

  @override
  String projectFolderPathHint(String directory) {
    return '$directory/my-app';
  }

  @override
  String get projectFolderOpenAction => '打开';

  @override
  String projectFolderCreateSubtitle(String directory) {
    return '位于此设备的 $directory 中';
  }

  @override
  String get projectFolderOpenSubtitle => '请输入服务器上文件夹的完整路径';

  @override
  String get globalSessionsTitle => '所有对话';

  @override
  String get globalSessionsSearchLabel => '搜索对话标题';

  @override
  String get globalSessionsArchivedShort => '已归档';

  @override
  String get globalSessionsUnknownLocation => '未知项目';

  @override
  String get globalSessionsEmptyTitle => '还没有对话';

  @override
  String get globalSessionsEmptyMessage => '此服务器上所有项目的对话都会显示在这里。';

  @override
  String get globalSessionsNoMatchTitle => '没有匹配的对话';

  @override
  String get globalSessionsNoMatchMessage => '请尝试更短的标题关键词，或包含已归档的对话。';

  @override
  String get globalSessionsRefresh => '刷新';

  @override
  String get globalSessionsLoadMoreFailed => '无法加载更多对话';

  @override
  String get globalSessionsOpen => '打开';

  @override
  String get globalSessionsContinueHere => '在此继续';

  @override
  String get globalSessionsActions => '对话操作';

  @override
  String get globalSessionsWorking => '正在工作';

  @override
  String get globalSessionsUntitled => '未命名对话';

  @override
  String get workspaceNewSession => '新对话';

  @override
  String get workspaceDismissNotice => '关闭';

  @override
  String get reviewCopyFile => '复制更新后的文件';

  @override
  String get reviewCopyPatch => '复制补丁';

  @override
  String get onboardingPrivateNetwork => '通过私有网络访问服务器';

  @override
  String get onboardingSetupGuide => '设置指南';

  @override
  String get onboardingSaveConnect => '保存并连接';

  @override
  String get onboardingSaveChanges => '保存更改';

  @override
  String get onboardingTermuxSetup => '在这部手机上';

  @override
  String get activitySavedServers => '已保存的服务器';

  @override
  String get demoTaskTitle => '试试一个小改动';

  @override
  String get demoTaskInstruction => '发送下方的示例提示词，然后查看建议的编辑。';

  @override
  String get reviewTitle => '审查';

  @override
  String get modelChoiceReloadProviders => '重新加载提供商';

  @override
  String get modelChoiceDone => '完成';

  @override
  String get modelChoicePartialSaveError => '模型已保存，但所选智能体未得到确认。请重试。';

  @override
  String get modelChoiceModelSaveError => '无法确认所选模型。请检查你的选择后重试。';

  @override
  String get workIdle => '空闲';

  @override
  String get workStartedInBackground => '已在后台启动';

  @override
  String get workBackgroundPending => '正在请求后台工作…';

  @override
  String get workBackgroundRequested => '已请求后台运行。服务器报告后状态将更新。';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String get setupSwitchPending => '升级到 OpenCode 2 尚未完成。已保存的对话会保留。请重试。';

  @override
  String setupSwitchRetry(String runtime) {
    return '再次尝试 $runtime';
  }

  @override
  String get setupSwitchFailed => '无法完成升级到 OpenCode 2。已保存的对话会保留。请重试。';

  @override
  String setupSwitchProfileName(String runtime) {
    return '此手机 · $runtime';
  }

  @override
  String get setupSwitchMissingCredential =>
      '无法读取已保存的服务器密码。你的对话和文件会保留。请恢复对已保存密码的访问，然后重试。';

  @override
  String setupSwitchProgressTitle(String runtime) {
    return '正在切换到 $runtime';
  }

  @override
  String e7ConnectionFailure1(int attempts) {
    return '已尝试 $attempts 次。重试不会启动未运行的服务器。';
  }

  @override
  String get e7ConnectionFailure2 => '需要连接令牌';

  @override
  String get e7ConnectionFailure3 => '此 Codex 服务器需要连接令牌，应用才能连接。';

  @override
  String get e7ConnectionFailure4 => '打开服务器设置，输入 Codex 连接令牌。';

  @override
  String get e7ConnectionFailure5 => '连接令牌被拒绝';

  @override
  String get e7ConnectionFailure6 => 'Codex 服务器有响应，但不接受已保存的连接令牌。';

  @override
  String get e7ConnectionFailure7 => '打开服务器设置，输入有效的 Codex 连接令牌。';

  @override
  String get e7ConnectionFailure8 => 'Codex 监听器不可用';

  @override
  String get e7ConnectionFailure9 => '无法访问 Codex 端点';

  @override
  String get e7ConnectionFailure12 => '在此设备上启动 Codex 监听器。';

  @override
  String get e7ConnectionFailure13 => '如果它位于隧道之后，请保持隧道运行并检查其本地端点。';

  @override
  String get e7ConnectionFailure14 => '使用 Codex 的 wss:// 端点或可用的安全隧道。';

  @override
  String get e7ConnectionFailure15 => '检查此设备能否访问远程 Codex 监听器。';

  @override
  String get e7ConnectionFailure16 => '密码被拒绝';

  @override
  String get e7ConnectionFailure17 => '服务器有响应，但不接受已保存的密码。服务器使用新密码重启后会出现这种情况。';

  @override
  String get e7ConnectionFailure18 => '在计算机上运行 opencode2 pair，并粘贴新代码。';

  @override
  String get e7ConnectionFailure19 =>
      '如果你手动设置了 OPENCODE_SERVER_PASSWORD，请重新复制。';

  @override
  String get e7ConnectionFailure20 => '证书不受信任';

  @override
  String get e7ConnectionFailure21 => '服务器存在，但此设备不信任其 HTTPS 证书，因此应用拒绝发送密码。';

  @override
  String get e7ConnectionFailure22 => '使用受信任机构签发的证书，或 Tailscale Serve 地址。';

  @override
  String get e7ConnectionFailure23 => '如果是自签名证书，请编辑此服务器并先核对其指纹再信任。';

  @override
  String get e7ConnectionFailure24 => '此手机上没有响应';

  @override
  String get e7ConnectionFailure26 =>
      '在 Termux 中运行 OpenCode？请打开 Termux，检查服务器是否仍在运行。';

  @override
  String get e7ConnectionFailure27 =>
      '使用了 adb reverse 或 SSH 转发？请检查隧道是否仍然连通，然后重试。';

  @override
  String get e7ConnectionFailure28 => '要连接另一台计算机？请把服务器改为它的 HTTPS 地址，或重新配对。';

  @override
  String get e7ConnectionFailure29 => '服务器未及时响应';

  @override
  String get e7ConnectionFailure31 => '你与计算机在同一网络或 VPN（例如 Tailscale）中吗？';

  @override
  String e7ConnectionFailure32(int port) {
    return '防火墙或强制门户是否拦截了端口 $port？';
  }

  @override
  String get e7ConnectionFailure33 => '无法访问服务器';

  @override
  String get e7ConnectionFailure35 => '计算机上的 opencode serve 仍在运行吗？';

  @override
  String get e7ConnectionFailure36 => '你与计算机在同一网络或 VPN 中吗？';

  @override
  String get e7ConnectionFailure37 => '地址变了吗？请重新配对以获取新地址。';

  @override
  String get e7ConnectionFailure38 => '服务器返回了错误';

  @override
  String get e7ConnectionFailure39 => '服务器在运行，但报告自身状态异常。其日志会说明原因。';

  @override
  String get e7ConnectionFailure40 => '重启 opencode serve 并查看其输出。';

  @override
  String get e7ConnectionFailure41 => '检查此应用是否支持该服务器版本。';

  @override
  String get e7ConnectionFailure42 => '无法连接';

  @override
  String get e7ConnectionFailure44 => '智能体服务器在运行吗？这个地址对吗？';

  @override
  String get e7ConnectionFailure45 => 'opencode serve 在运行吗？这个地址对吗？';

  @override
  String get e7PermissionAction1 => '运行 shell 命令';

  @override
  String get e7PermissionAction2 => '编辑文件';

  @override
  String get e7PermissionAction3 => '读取文件';

  @override
  String get e7PermissionAction4 => '访问外部目录';

  @override
  String get e7PermissionAction5 => '在多次失败后继续';

  @override
  String get e7PermissionAction6 => '需要权限';

  @override
  String e7PermissionAction7(String permission) {
    return '使用 $permission';
  }

  @override
  String get e7GlossaryMcpExplanation =>
      'Model Context Protocol。小型附加服务器，为智能体提供额外工具，例如浏览器、数据库或设计工具。连接一次后，所有对话都能使用。';

  @override
  String get e7GlossaryWorktreeExplanation =>
      '同一仓库的另一份独立检出。当你想让智能体在自己的分支上尝试改动、又不碰你正在编写的代码时，就使用它。';

  @override
  String get e7GlossaryGotIt => '知道了';

  @override
  String get e7BannerUpdatePassword => '更新密码';

  @override
  String get e7BannerLost => '连接已断开';

  @override
  String get e7BannerDetails => '详情';

  @override
  String e7BannerReconnectingServer(String server) {
    return '正在重新连接到 $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return '正在重新连接 $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      '检查 OpenCode 期间，你看到的内容仍可使用。实时更新会自动恢复。';

  @override
  String get e7BannerStaleExplanation => '在 OpenCode 恢复可访问之前，你看到的内容可能已过时。';

  @override
  String e7BannerCheckingExplanationAgent(String agent) {
    return '检查 $agent 期间，你看到的内容仍然可用。实时更新会自行恢复。';
  }

  @override
  String e7BannerStaleExplanationAgent(String agent) {
    return '$agent 恢复可达之前，你看到的内容可能已过期。';
  }

  @override
  String get e7SharedThreeStepsToYourFirstSession => '三步开始你的第一次对话';

  @override
  String get e7SharedOpenCodeRunsOnYourComputerThisApp =>
      'OpenCode 在你的电脑上运行，本应用是远程端。配对只需一条命令即可连接两者 — 无需输入地址或密码。';

  @override
  String get e7SharedOnYourComputerRunOneCommand => '在你的电脑上运行一条命令';

  @override
  String get e7SharedInATerminalOnTheComputerWhere =>
      '在已安装 OpenCode 的那台电脑上打开终端：';

  @override
  String get e7SharedItStartsTheServerAndPrintsA =>
      '它会启动服务器并打印配对码 — 以及一个可扫描的二维码。';

  @override
  String get e7SharedScanTheQROrPasteTheCode => '扫描二维码，或在本应用里粘贴配对码';

  @override
  String get e7SharedPasteTheCodeInThisApp => '在本应用里粘贴配对码';

  @override
  String get e7SharedStartTalking => '开始对话';

  @override
  String get e7SharedPickAProjectAndSendYourFirst =>
      '选择一个项目并发送第一条消息。工作在电脑上完成，本应用负责展示并让你随时干预。';

  @override
  String get e7SharedAdvanced => '高级';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServersTermuxInternals =>
      'HTTPS、SSH 隧道、旧版服务器、Termux 内部细节';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServers => 'HTTPS、SSH 隧道、旧版服务器';

  @override
  String get e7SharedReachAServerOverHTTPSOrA => '通过 HTTPS 或隧道连接服务器';

  @override
  String get e7SharedPairingWorksWhenTheAddressTheServer =>
      '配对的前提是本设备能访问服务器地址。使用远程 HTTP 地址时，应用会在发送密码前征求你的同意。建议优先使用 HTTPS 或 Tailscale 私有连接。';

  @override
  String get e7SharedOlderServersWithoutPairing => '不支持配对的旧版服务器';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      '如需本地连接，请在这台电脑上启动 OpenCode 2。使用它输出的密码：';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      '然后手动添加服务器，用户名填 opencode，密码填上面的密码。';

  @override
  String get e7SharedOnDeviceViaTermuxAutomated => '通过 Termux 在设备上运行（自动）';

  @override
  String get e7SharedUseTheOnDeviceTermuxCardOn =>
      '使用“服务器”页面上的“本机（Termux）”卡片。应用会安装 Termux、解锁桥接、配置 opencode、启动服务器并连接 — 全程有引导。';

  @override
  String get e7SharedOnlyTwoTapsNeedYouPersonallyDownloading =>
      '只有两步需要你亲自操作：下载 Termux APK，以及在 Termux 中粘贴一次解锁命令 — 这两步是 Android 安全模型的要求，而非本应用的要求。';

  @override
  String get e7SharedPreferManualInsideTermuxRun => '想手动操作？在 Termux 中运行：';

  @override
  String get e7SharedTheChrootSharesTheNetworkStackSo =>
      'chroot 与主机共用网络栈，因此本应用可以访问 http://127.0.0.1:4096。运行 `termux-wake-lock` 可保持其存活。';

  @override
  String get e7SharedSecurityNotes => '安全须知';

  @override
  String get e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding =>
      '在 localhost 之外绑定端口时，务必设置 OPENCODE_SERVER_PASSWORD。';

  @override
  String get e7SharedPasswordsAreStoredInTheAndroidKeystore =>
      '密码仅保存在本设备的 Android 密钥库中。';

  @override
  String get e7SharedTheServerCanExecuteCommandsOnIts =>
      '服务器可在其主机上执行命令 — 请像对待 SSH 访问一样对待它的访问权限。';

  @override
  String get e7SharedOpenCodeIsReconnectingTryAgain => 'OpenCode 正在重新连接。请重试。';

  @override
  String get e7SharedSessionContext => '对话上下文';

  @override
  String get e7SharedRefreshContext => '刷新上下文';

  @override
  String get e7SharedNoContextUsageYet => '尚无上下文用量';

  @override
  String get e7SharedSendAPromptAndWaitForAn =>
      '发送一条提示词并等待助手回复，OpenCode 随后会报告此对话的 token 用量。';

  @override
  String get e7SharedEstimatedInputMakeup => '输入构成估算';

  @override
  String get e7SharedSessionTotals => '对话总计';

  @override
  String get e7SharedUsageComesFromTheLatestCompletedAssistant =>
      '用量来自最近一条已完成的助手消息。构成是根据可见的提示词、回复和工具文本估算的；“其他”包含系统指令、工具定义和提供商开销。';

  @override
  String get e7SharedModelUnavailable => '模型不可用';

  @override
  String get e7SharedContextLimitUnavailable => '上下文上限不可用';

  @override
  String get e7SharedLatestAssistantRequestIncludingCacheActivity =>
      '最近一次助手请求，包含缓存活动';

  @override
  String get e7SharedContextLimit => '上下文上限';

  @override
  String get e7SharedUnavailable => '不可用';

  @override
  String get e7SharedMessages => '消息';

  @override
  String get e7SharedAccumulatedCostReportedByServer => '累计费用 · 由服务器报告';

  @override
  String get e7SharedAccumulatedCost => '累计费用';

  @override
  String get e7SharedSessionTokensReportedByServer => '对话 token · 由服务器报告';

  @override
  String get e7SharedUserPrompts => '用户提示词';

  @override
  String get e7SharedAssistantText => '助手文本';

  @override
  String get e7SharedToolCallsAndResults => '工具调用与结果';

  @override
  String get e7SharedOtherContext => '其他上下文';

  @override
  String get e7SharedSessionLocationChangedCloseAndReopenThis =>
      '该对话的项目已更改。请关闭并重新打开此面板。';

  @override
  String get e7SharedOpenCodeIsReconnecting => 'OpenCode 正在重新连接。';

  @override
  String get e7SharedTheSessionProjectIsNotAvailableOn => '该对话的项目在此服务器上不可用。';

  @override
  String get e7SharedLocalProject => '本地项目';

  @override
  String get e7SharedTheAppCouldNotInspectWorkingChanges =>
      '应用无法检查未提交的改动。为安全起见，本次继续但不传输改动。';

  @override
  String get e7SharedMoveSession => '移动对话';

  @override
  String get e7SharedChooseAnotherDirectoryInThisProject => '在此项目中选择另一个目录。';

  @override
  String get e7SharedChooseAConnectedWorkspaceOrReturnTo =>
      '选择一个已连接的云环境，或返回本地项目。';

  @override
  String get e7SharedFilterDestinations => '筛选目标';

  @override
  String get e7SharedCurrent => '当前';

  @override
  String get e7SharedSwitchOrganization => '切换组织？';

  @override
  String get e7SharedSwitchOrganization462 => '切换组织';

  @override
  String get e7SharedNoSwitchableOpenCodeConsoleOrganizationsWereReturned =>
      '未返回可切换的 OpenCode Console 组织。';

  @override
  String get e7SharedSessionLocationChangedReturnAndReopenRelated =>
      '该对话的项目已更改。请返回并重新打开相关对话。';

  @override
  String get e7SharedSessionIsNoLongerRelatedToThis => '该对话已不再与此对话相关联。';

  @override
  String get e7SharedSessionLocationChangedReturnAndTryAgain =>
      '该对话的项目已更改。请返回并重试。';

  @override
  String get e7SharedSessionUnavailableOrLocationChangedReturnOr =>
      '对话不可用或其项目已更改。请返回或刷新后重试。';

  @override
  String get e7SharedCouldNotUpdateThePinReturnAnd => '无法更新置顶。请返回后重试。';

  @override
  String get e7SharedRefreshSubagentSessions => '刷新子智能体对话';

  @override
  String get e7SharedParentSession => '父对话';

  @override
  String get e7SharedNoSubagentSessionsYet => '尚无子智能体对话';

  @override
  String get e7SharedDelegatedWorkWillAppearHereWithoutMixing =>
      '委派的工作会出现在这里，子智能体对话不会混入你的主列表。';

  @override
  String get e7SharedOpenInsecureHTTPLink => '打开不安全的 HTTP 链接？';

  @override
  String get e7SharedOpenExternalLink => '打开外部链接？';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP 未加密。网络上的其他设备可能读取或修改你发送和接收的内容。';

  @override
  String get e7SharedOpenHTTPLink => '打开 HTTP 链接';

  @override
  String get e7SharedOpenLink => '打开链接';

  @override
  String get e7SharedNoAppCouldOpenThisLink => '没有应用可以打开此链接。';

  @override
  String get e7SharedRequired => '必填';

  @override
  String get e7SharedDoesNotMatchTheExpectedFormat => '与预期格式不符';

  @override
  String get e7SharedEnterAWholeNumber => '请输入整数';

  @override
  String get e7SharedEnterANumber => '请输入数字';

  @override
  String get e7SharedDismissThisRequest => '拒绝此请求？';

  @override
  String get e7SharedTheAgentContinuesWithoutYourAnswers =>
      '智能体将在没有你的答案的情况下继续。';

  @override
  String get e7SharedAskedByAnMCPServer => '由 MCP 服务器请求';

  @override
  String get e7SharedAskedByTheAgentInThisSession => '由此对话中的智能体请求';

  @override
  String get e7SharedInputRequested => '需要输入';

  @override
  String get e7SharedOther => '其他…';

  @override
  String get e7SharedYourAnswer => '你的答案';

  @override
  String get e7SharedAddYourOwn => '添加自定义答案';

  @override
  String get e7SharedAddAnswer => '添加答案';

  @override
  String get e7SharedThisServerSentALinkThisApp => '此服务器发送了本应用不会打开的链接。';

  @override
  String get e7SharedSendAnswers => '发送答案';

  @override
  String e7SharedDetail307(int step) {
    return '第 $step 步，共 3 步';
  }

  @override
  String e7SharedDetail385(String count, String limit) {
    return '$count / $limit tokens';
  }

  @override
  String e7SharedDetail386(String count) {
    return '$count tokens · 上限不可用';
  }

  @override
  String e7SharedDetail429(String destination) {
    return '将对话移动到 $destination？';
  }

  @override
  String e7SharedDetail514(String error) {
    return '刷新失败：$error';
  }

  @override
  String e7SharedDetail714(int count) {
    return '至少 $count 个字符';
  }

  @override
  String e7SharedDetail715(int count) {
    return '最多 $count 个字符';
  }

  @override
  String e7SharedDetail721(String minimum, String maximum) {
    return '必须介于 $minimum 和 $maximum 之间';
  }

  @override
  String e7SharedDetail722(String minimum) {
    return '至少 $minimum';
  }

  @override
  String e7SharedDetail723(String maximum) {
    return '至多 $maximum';
  }

  @override
  String e7SharedDetail726(int count) {
    return '最多选 $count 项';
  }

  @override
  String e7SharedDetail753(int minimum, int maximum) {
    return '请选择 $minimum–$maximum 项';
  }

  @override
  String e7SharedDetail754(int count) {
    return '至少选 $count 项';
  }

  @override
  String e7SharedDetail755(int count) {
    return '最多选 $count 项';
  }

  @override
  String e7SharedDetail756(String range, int count) {
    return '$range · 已选 $count 项';
  }

  @override
  String e7SharedDetail764(String host) {
    return '在浏览器中打开 $host';
  }

  @override
  String e7SharedDetail765(String field) {
    return '此服务器发送了本应用无法识别的字段类型（“$field”）。';
  }

  @override
  String get e7LocaleUiLanguage => '语言';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => '使用系统语言';

  @override
  String get e7LocaleUiClose => '关闭';

  @override
  String get e7LocaleUiDescription => '选择整个应用使用的语言。服务器消息和你输入的文字将保持原样。';

  @override
  String get e7LocaleUiSaving => '正在保存语言…';

  @override
  String get e7LocaleUiSaveFailed => '无法保存语言设置。之前的选择仍然有效。请重新选择语言再试一次。';

  @override
  String get e7LocaleUiNewSession => '新建对话';

  @override
  String get e7LocaleUiNewSessionHint => '在当前项目中开始对话';

  @override
  String get e7LocaleUiWorkspaceHint => '所有项目中的全部对话';

  @override
  String get e7LocaleUiFilesHint => '文件、改动、终端及其他项目工具';

  @override
  String get e7LocaleUiMoreHint => '模型、提供商、通知、设置';

  @override
  String get e7LocaleUiSettings => '设置';

  @override
  String get e7LocaleUiKeyboardShortcuts => '键盘快捷键';

  @override
  String get e7LocaleUiRefreshSessions => '刷新对话';

  @override
  String get e7LocaleUiDiagnostics => '诊断';

  @override
  String get e7LocaleUiDiagnosticsHint => '最近的错误与连接详情';

  @override
  String get e7LocaleUiCommandLauncher => '命令启动器';

  @override
  String get e7LocaleUiFindSurface => '在此屏幕中查找';

  @override
  String get e7LocaleUiDestinations => '切换标签页';

  @override
  String get e7LocaleUiTerminal => '终端';

  @override
  String get e7LocaleUiCloseScreen => '关闭此屏幕';

  @override
  String get e7LocaleUiSendPrompt => '发送提示词';

  @override
  String get e7LocaleUiCopyTranscript => '复制选中的对话记录文字';

  @override
  String get e7LocaleUiRecentModel => '此对话中下一个/上一个最近使用的模型';

  @override
  String get e7LocaleUiThisList => '此列表';

  @override
  String get e7LocaleUiCloseOverlay => '关闭面板、对话框或菜单';

  @override
  String get e7LocaleUiContextActions => '消息、文件与对话操作';

  @override
  String get e7LocaleUiContextKeys => '右键 / Shift + F10 / 菜单键';

  @override
  String get e7LocaleUiConnectionChanged => '服务器已更改。';

  @override
  String get e7AppearanceFollowAndroid => '跟随 Android';

  @override
  String get e7AppearanceFollowSystem => '跟随系统';

  @override
  String get e7AppearanceLight => '浅色';

  @override
  String get e7AppearanceDark => '深色';

  @override
  String get e7AppearanceFollowPhoneDescription => '跟随本机当前的浅色或深色设置';

  @override
  String get e7AppearanceFollowDeviceDescription => '跟随本设备当前的浅色或深色设置';

  @override
  String get e7AppearanceLightDescription => '使用明亮的编辑风格主题';

  @override
  String get e7AppearanceDarkDescription => '使用专注的低光主题';

  @override
  String get e7AppearanceTitle => '外观';

  @override
  String get e7AppearancePreviewHint => '先预览。只有点击应用后，外观才会改变。';

  @override
  String get e7AppearanceDynamicUnavailable => '此设备不支持 Material You 配色。';

  @override
  String e7AppearanceUsesMode(String mode) {
    return '你的浅色或深色设置仍为$mode。';
  }

  @override
  String get e7AppearanceSaveFailed => '无法保存外观。之前的设置未改变。请重试。';

  @override
  String get e7AppearanceSaving => '正在保存…';

  @override
  String get e7AppearanceApply => '应用';

  @override
  String get e7AppearanceCurrent => '当前外观';

  @override
  String get e7AppearanceClose => '关闭';

  @override
  String get e7AppearancePreviewTitle => '文字与控件';

  @override
  String get e7AppearancePreviewBody => '查看阅读、代码与选中操作如何协同。';

  @override
  String get e7AppearanceSelection => '已选项';

  @override
  String get e7AppearanceTryControl => '试用控件';

  @override
  String get e7AppearanceSampleHint => '示例控件只会改变此预览。';

  @override
  String get e7SettingsUi1 => '服务器';

  @override
  String get e7SettingsUi8 => '断开连接';

  @override
  String get e7SettingsUi9 => 'OpenCode 服务器';

  @override
  String get e7SettingsUi11 => '正在检查服务器健康状态…';

  @override
  String get e7SettingsUi12 => '已被 Android 停止';

  @override
  String get e7SettingsUi14 => '已开启 · 正在运行';

  @override
  String get e7SettingsUi15 => '已开启 · 正在启动';

  @override
  String get e7SettingsUi16 => '此服务器';

  @override
  String get e7SettingsUi17 => '未知';

  @override
  String get e7SettingsUi18 => 'OpenCode 正在重新连接。';

  @override
  String get e7SettingsUi19 => 'OpenCode 正在重新连接。请重试。';

  @override
  String get e7SettingsUi22 => 'Android 未启用后台模式。';

  @override
  String get e7SettingsUi23 => 'Android 已停止实时连接';

  @override
  String get e7SettingsUi24 =>
      '其每日后台数据同步额度已用完，因此实时模式已自动关闭。重新开启即可重新连接；额度将在 24 小时内重置。';

  @override
  String get e7SettingsUi25 => '在后台保持连接';

  @override
  String get e7SettingsUi26 => '应用关闭后仍保持运行状态更新，并在某次运行需要你时通知你。会增加耗电，并显示一条常驻通知。';

  @override
  String get e7SettingsUi27 => '已允许不受限制的电池使用';

  @override
  String get e7SettingsUi28 => '允许不受限制的电池使用';

  @override
  String get e7SettingsUi29 => 'Android 仍可能应用其前台服务时间限制。';

  @override
  String get e7SettingsUi30 =>
      '可选。有助于在 Doze 期间保持实时连接。Android 15+ 将后台数据同步任务限制为每 24 小时六小时。';

  @override
  String get e7SettingsUi31 => '已被 Android 停止，点按以重启';

  @override
  String get e7SettingsUi32 => '正在运行';

  @override
  String get e7SettingsUi34 => 'Android 在每天运行 6 小时后会将其停止。停止时应用会通知你。';

  @override
  String get e7SettingsUi35 => '默认 Shell';

  @override
  String get e7SettingsUi36 => '用于新的终端，以及此 OpenCode 服务器上兼容的 Shell 命令。';

  @override
  String get e7SettingsUi37 => '仅终端；OpenCode 的 Shell 工具会使用兼容的替代方案。';

  @override
  String get e7SettingsUi39 => '自动（服务器默认）';

  @override
  String get e7SettingsUi41 => '正在从 OpenCode 加载 Shell…';

  @override
  String get e7SettingsUi46 => '在其主机上重启 OpenCode';

  @override
  String get e7SettingsUi48 => '更新远程 OpenCode？';

  @override
  String get e7SettingsUi49 => '升级完成前活动服务器已更改';

  @override
  String get e7SettingsUi50 => '更新受管理的 OpenCode';

  @override
  String get e7SettingsUi51 => '安装最新的稳定版服务器、刷新模型、安全重启并重新连接。';

  @override
  String get e7SettingsUi52 => '上一个版本';

  @override
  String get e7SettingsUi53 => '未知版本';

  @override
  String get e7SettingsUi56 => '未连接';

  @override
  String get e7SettingsUi57 => '检查服务器健康状态';

  @override
  String get e7SettingsUi58 => '正在询问服务器状态';

  @override
  String get e7SettingsUi59 => '服务器正常';

  @override
  String get e7SettingsUi60 => '健康状态不可用';

  @override
  String get e7SettingsUi62 => '未保存服务器密码';

  @override
  String get e7SettingsUi65 => '设置 OpenCode 2';

  @override
  String get e7SettingsUi66 => '关闭终端后让 OpenCode 继续运行。';

  @override
  String get e7SettingsUi67 => '服务器更新';

  @override
  String get e7SettingsUi68 => '在运行服务器的机器上升级';

  @override
  String get e7SettingsUi69 => '浅色或深色';

  @override
  String get e7SettingsUi70 => '主题';

  @override
  String get e7SettingsUi74 => '始终允许的操作';

  @override
  String get e7SettingsUi76 => '在此设备上';

  @override
  String get e7SettingsUi77 => '已用存储空间';

  @override
  String get e7SettingsUi78 => '清除排队的提示词';

  @override
  String get e7SettingsUi79 => '没有等待发送的内容';

  @override
  String get e7SettingsUi80 => '要删除排队的提示词吗？';

  @override
  String get e7SettingsUi81 => '已删除排队的提示词';

  @override
  String get e7SettingsUi82 => '无法删除排队的提示词。请检查设备存储空间后重试。';

  @override
  String get e7SettingsUi83 => '清除草稿';

  @override
  String get e7SettingsUi84 => '没有已保存的输入框文字';

  @override
  String get e7SettingsUi85 => '要删除草稿吗？';

  @override
  String get e7SettingsUi86 => '已删除草稿';

  @override
  String get e7SettingsUi87 => '无法删除草稿。请检查设备存储空间后重试。';

  @override
  String get e7SettingsUi88 => '应用诊断';

  @override
  String get e7SettingsUi92 => '隐私与数据使用';

  @override
  String get e7SettingsUi93 => '服务器、服务商、语音、文件、Termux 和更新';

  @override
  String get e7SettingsUi94 => '语音许可与来源';

  @override
  String get e7SettingsUi96 => '关于与开源声明';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return '断开与 $server 的连接？';
  }

  @override
  String e7SettingsVersion(String version) {
    return '版本 $version';
  }

  @override
  String get e7AppearancePackOpencode => '终端绿，默认方案';

  @override
  String get e7AppearancePackCatppuccin => 'Catppuccin 的 Mocha 与 Latte，以淡紫为主';

  @override
  String get e7AppearancePackGruvbox => 'Gruvbox 暖色复古，以橙色为主';

  @override
  String get e7AppearancePackSolarized => 'Solarized 经典双色板，以蓝色为主';

  @override
  String get e7AppearancePackDynamic => '本机的 Material You 配色';

  @override
  String e7SettingsStorageSummary(
    String total,
    int queued,
    String queueBytes,
    int drafts,
    String draftBytes,
    int days,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued 条排队的提示词',
      one: '1 条排队的提示词',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts 份草稿',
      one: '1 份草稿',
    );
    return '未发送内容共 $total：$_temp0（$queueBytes）和 $_temp1（$draftBytes）。排队的提示词在 $days 天后会被丢弃。';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除所有服务器上全部 $count 条未发送提示词及其附件',
      one: '删除所有服务器上的这条未发送提示词及其附件',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '这将删除所有服务器上的 $count 条未发送提示词及其附件。它们将永远不会被发送。服务器上的内容不受影响。',
      one: '这将删除所有服务器上的这条未发送提示词及其附件。它将永远不会被发送。服务器上的内容不受影响。',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除为 $count 个对话保存的输入框文字',
      one: '删除为 1 个对话保存的输入框文字',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '这将删除为 $count 个对话保存的输入框文字。服务器上的内容不受影响。',
      one: '这将删除为 1 个对话保存的输入框文字。服务器上的内容不受影响。',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsRestartBody(String version, String current) {
    return 'OpenCode $version 已安装，但服务器仍在运行 $current。请按你当初启动的方式在那台电脑上重启它，应用随后会再次检查。';
  }

  @override
  String e7SettingsInstallVersion(String version) {
    return '安装 $version';
  }

  @override
  String e7SettingsInstalledVersion(String version) {
    return '已安装 OpenCode $version。重启其服务器进程后即可使用。';
  }

  @override
  String e7SettingsRestartVersion(String version) {
    return '重启 OpenCode 以使用 $version';
  }

  @override
  String e7SettingsRetryError(String error) {
    return '$error 点按重试。';
  }

  @override
  String e7SettingsUpdateVersion(String version) {
    return '将 OpenCode 更新到 $version';
  }

  @override
  String get e7SettingsDetailUi17 => '隐私';

  @override
  String get e7SettingsDetailUi18 => '开源';

  @override
  String get e7SettingsDetailUi19 => '关于此版本';

  @override
  String get e7SettingsDetailUi22 => 'OpenCode 服务器的移动客户端。下载可选模型后，语音识别在本机运行。';

  @override
  String get e7SettingsDetailUi23 => 'OpenCode 服务器的桌面客户端。';

  @override
  String get e7SettingsDetailUi25 => 'Built-in';

  @override
  String get e7SettingsDetailUi26 => '技能';

  @override
  String get e7SettingsDetailUi27 => '引用';

  @override
  String get e7SettingsAlphaBody => 'Android 是受支持的平台；桌面版仍为实验性。';

  @override
  String get e7SettingsNonAffiliation =>
      'OpenCode Mobile 是独立的社区项目，并非由 OpenCode 官方团队开发、维护、认可或与之关联。';

  @override
  String e7SettingsDiagnosticOccurrences(int count) {
    return '出现 $count 次';
  }

  @override
  String get e7ProjectProjectsReconnect => 'OpenCode 正在重新连接。请稍后再试。';

  @override
  String e7ProjectProjectRenameFailed(String error) {
    return '无法重命名项目：$error';
  }

  @override
  String get e7ProjectProjectDefaultDirectory => '服务器的默认目录';

  @override
  String get e7ProjectProjectSwitchUnavailableDetail =>
      '此服务器会为对话固定使用所配置的文件夹。请从“工作”页面新建对话以继续。';

  @override
  String get e7ProjectProjectsTitle => '项目';

  @override
  String get e7ProjectProjectsRefresh => '刷新项目';

  @override
  String get e7ProjectProjectsSearch => '搜索项目或路径';

  @override
  String get e7ProjectProjectsOpened => '已打开的项目';

  @override
  String get e7ProjectProjectsEmpty => '尚未打开项目';

  @override
  String get e7ProjectProjectsEmptyDetail => '你打开或创建的项目会出现在这里。';

  @override
  String get e7ProjectProjectsRefreshFailed => '项目刷新失败';

  @override
  String e7ProjectProjectWorktrees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个工作树',
      one: '1 个工作树',
    );
    return '$_temp0';
  }

  @override
  String e7ProjectProjectRenameAction(String name) {
    return '重命名 $name';
  }

  @override
  String get e7ProjectProjectRenameTitle => '重命名项目';

  @override
  String get e7ProjectProjectNameLabel => '项目名称';

  @override
  String get e7ProjectProjectNameHint => '清空名称则使用项目文件夹名。';

  @override
  String get e7ProjectProjectSave => '保存';

  @override
  String get e7ProjectMonitorUnsupported => '此服务器不支持后台关注。请打开对话查看当前请求。';

  @override
  String get readerUiDisconnected => '服务器未连接。';

  @override
  String get readerUiIndicatorsUnavailable => '此服务器不支持文件变更标记。';

  @override
  String get readerUiIndicatorsFailed => '无法刷新文件变更标记。';

  @override
  String get readerUiReconnecting => 'OpenCode 正在重新连接。';

  @override
  String get readerUiCommentAdded => '已添加审查评论。返回对话以继续。';

  @override
  String get readerUiCommentCopied => '已复制审查评论。请粘贴到对话中。';

  @override
  String get readerUiSearchSymbols => '搜索符号';

  @override
  String get readerUiSearchFiles => '搜索文件';

  @override
  String get readerUiSelectFile => '选择文件以预览';

  @override
  String get readerUiEmptyFolder => '文件夹为空';

  @override
  String get readerUiNoFiles => '未找到文件';

  @override
  String get readerUiPullRefresh => '下拉可刷新此文件夹。';

  @override
  String get readerUiTryFileName => '试试其他文件名。';

  @override
  String get readerUiAttachPrompt => '附加到提示词';

  @override
  String get readerUiOpenReview => '在审查中打开';

  @override
  String get readerUiCopyPath => '复制路径';

  @override
  String get readerUiWorkspaceSymbols => '搜索项目符号';

  @override
  String get readerUiSymbolsHint => '按名称查找类、函数、方法和变量。';

  @override
  String get readerUiNoSymbols => '未找到符号';

  @override
  String get readerUiSymbolsUnavailable => '试试其他名称。部分语言服务不支持项目范围的符号搜索。';

  @override
  String get readerUiFiles => '文件';

  @override
  String get readerUiSymbols => '符号';

  @override
  String get readerUiChanges => '更改';

  @override
  String get readerUiRefreshChanges => '刷新更改';

  @override
  String get readerUiEntireChange => '整个文件的变更';

  @override
  String get readerUiWorkingTree => '未提交';

  @override
  String get readerUiSessionScopeHint => '此对话更改过的文件。';

  @override
  String get readerUiWorkingScopeHint => '所有尚未提交的更改，无论由谁修改。';

  @override
  String get readerUiBranchScopeHint => '此分支上的所有内容，与主分支比较。';

  @override
  String get readerUiSaveDevice => '保存到设备';

  @override
  String get readerUiPreviewUnavailable => '无法预览';

  @override
  String get readerUiRendered => '渲染结果';

  @override
  String get readerUiRaw => '原始内容';

  @override
  String get readerUiSaveFailed => '无法保存阅读器偏好设置。请重试。';

  @override
  String get readerUiSourceFirst => '源码优先';

  @override
  String get readerUiServerOrder => '默认顺序';

  @override
  String readerUiLine(int number) {
    return '第 $number 行';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return '已将 $label 添加到提示词';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label 已在提示词中';
  }

  @override
  String readerUiReferenceFull(int count) {
    return '提示词已包含 $count 个引用';
  }

  @override
  String readerUiAttached(String name) {
    return '已附加 $name。';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个已更改的文件',
      one: '$count 个已更改的文件',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return '已附加 $name。返回对话以添加你的评论。';
  }

  @override
  String readerUiSaveNamed(String name) {
    return '保存 $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return '$name 已保存到你的设备。';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · 第 $line 行';
  }

  @override
  String readerUiOnPrompt(int count) {
    return '提示词中有 $count 条';
  }

  @override
  String readerUiNewLines(String label) {
    return '新增 $label';
  }

  @override
  String readerUiOldLines(String label) {
    return '删除 $label';
  }

  @override
  String readerUiLineRange(int first, int last) {
    return '第 $first–$last 行';
  }

  @override
  String readerUiReviewPrompt(String path) {
    return '审查 `$path`';
  }

  @override
  String readerUiSaved(String name) {
    return '已保存 $name。';
  }

  @override
  String get readerUiSymbolFile => '文件';

  @override
  String get readerUiSymbolModule => '模块';

  @override
  String get readerUiSymbolNamespace => '命名空间';

  @override
  String get readerUiSymbolPackage => '包';

  @override
  String get readerUiSymbolClass => '类';

  @override
  String get readerUiSymbolMethod => '方法';

  @override
  String get readerUiSymbolProperty => '属性';

  @override
  String get readerUiSymbolField => '字段';

  @override
  String get readerUiSymbolConstructor => '构造函数';

  @override
  String get readerUiSymbolEnum => '枚举';

  @override
  String get readerUiSymbolInterface => '接口';

  @override
  String get readerUiSymbolFunction => '函数';

  @override
  String get readerUiSymbolVariable => '变量';

  @override
  String get readerUiSymbolConstant => '常量';

  @override
  String get readerUiSymbolEnummember => '枚举成员';

  @override
  String get readerUiSymbolStruct => '结构体';

  @override
  String get readerUiSymbolEvent => '事件';

  @override
  String get readerUiSymbolOperator => '运算符';

  @override
  String get readerUiSymbolTypeparameter => '类型参数';

  @override
  String get readerUiSymbolSymbol => '符号';

  @override
  String get readerUiAdded => '已添加';

  @override
  String get readerUiDeleted => '已删除';

  @override
  String get readerUiModified => '已修改';

  @override
  String get readerUiChanged => '已更改';

  @override
  String get readerUiSession => '此对话';

  @override
  String get readerUiBranch => '整个分支';

  @override
  String get readerUiAttachmentMissing => '此消息不包含附件内容。';

  @override
  String get readerUiRemoteAttachment => '无法预览远程附件。';

  @override
  String get readerUiAttachmentInvalid => '无法解码附件数据。';

  @override
  String get e7WorkspaceDisconnected => '服务器未连接。';

  @override
  String get e7WorkspaceNeedsYou => '需要你处理';

  @override
  String get e7WorkspaceStopSharing => '停止分享';

  @override
  String get e7WorkspaceDisconnect => '断开连接';

  @override
  String get e7WorkspaceBackExit => '再按一次返回即可退出';

  @override
  String get e7WorkspaceConnected => '已连接';

  @override
  String get e7WorkspaceConnecting => '正在连接';

  @override
  String get e7WorkspaceOffline => '离线';

  @override
  String get e7WorkspaceReconnectingAgain => 'OpenCode 正在重新连接。请重试。';

  @override
  String get e7WorkspaceDismissRequest => '要关闭这个请求吗？';

  @override
  String get e7WorkspaceDismissDetail => 'OpenCode 将在没有这些问题答案的情况下继续。';

  @override
  String get e7WorkspaceNeedsInput => 'OpenCode 需要你的输入';

  @override
  String activityAgentNeedsInput(String agent) {
    return '$agent 需要你的输入';
  }

  @override
  String get e7WorkspaceSendAnswers => '发送回答';

  @override
  String get e7WorkspaceReferenceRetry => '对话引用不可用。请刷新后重试。';

  @override
  String get e7WorkspaceLocationRetry => '该对话的项目不可用。请刷新后重试。';

  @override
  String get e7WorkspaceLocationChangedReturn => '该对话的项目已更改。请返回后重试。';

  @override
  String get e7WorkspaceLocationChangedRetry => '该对话的项目已更改。请刷新后重试。';

  @override
  String get e7WorkspaceReferenceUnavailable => '对话引用不可用。';

  @override
  String get e7WorkspacePaginationStuck => '对话分页无法继续。请刷新列表以继续。';

  @override
  String e7WorkspaceSessionId(String id) {
    return '对话 $id';
  }

  @override
  String get e7WorkspaceUnknownProject => '未知项目';

  @override
  String get e7WorkspaceJustNow => '刚刚';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return '$count 分钟前';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return '$count 小时前';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return '$count 天前';
  }

  @override
  String e7WorkspaceFileCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件',
      one: '1 个文件',
    );
    return '$_temp0';
  }

  @override
  String get chatUiAllMatchingRequests => '（所有匹配的请求）';

  @override
  String get chatUiNoOutput => '（无输出）';

  @override
  String get chatUiNoResult => '（无结果）';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen => '发送时会将 1 条引用作为文字添加。不会随草稿保存。';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      '为此提示词添加 OpenCode 项目引用';

  @override
  String get chatUiAddAnImageOrFileToThe => '向提示词添加图片或文件';

  @override
  String get chatUiAgent => '智能体';

  @override
  String get chatUiAlreadyAnsweredElsewhere => '已在别处答复';

  @override
  String get chatUiAlreadyDelivered => '已送达';

  @override
  String get chatUiAlwaysAllow => '始终允许';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover => '“始终允许”还将涵盖';

  @override
  String get chatUiAnswerWasCutOffByTheLength => '回答因长度上限被截断';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      '任何拥有链接的人都可以查看此对话及其共享的上下文。请勿分享包含机密、凭据或私有文件的对话。';

  @override
  String get chatUiAppDiagnostics => '应用诊断';

  @override
  String get chatUiAppearance => '外观';

  @override
  String get chatUiApplyPatch => '应用补丁';

  @override
  String get chatUiAskOpenCode => '向 OpenCode 提问…';

  @override
  String get chatUiAttachFile => '添加文件';

  @override
  String get chatUiAttachToPrompt => '附加到提示词';

  @override
  String get chatUiAttachment => '附件';

  @override
  String get chatUiAttachmentLimitReached => '已达附件上限';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 => '附件总大小不能超过 20 MB。';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes => '当前运行结束后可用';

  @override
  String get chatUiBrowseProjectAndGlobalSkills => '浏览项目和全局技能';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      '浏览、预览、下载并附加项目文件';

  @override
  String get chatUiCancelAndReturnToTheComposer => '取消并返回输入框';

  @override
  String get chatUiCancelMessage => '取消消息';

  @override
  String get chatUiCancelThisPendingMessage => '要取消这条待发送的消息吗？';

  @override
  String get chatUiChangeTheActiveOpenCodeConsoleOrganization =>
      '更改活动的 OpenCode Console 组织';

  @override
  String get chatUiChangeTheTitleShownInTheSession => '更改对话列表中显示的标题';

  @override
  String get chatUiChangeThisSessionSExperimentalWorkspace => '更改此对话的实验性云环境';

  @override
  String get chatUiChangedFile => '已更改的文件';

  @override
  String get chatUiChanges => '更改';

  @override
  String get chatUiChooseAServerModelByProviderAnd => '按提供商和能力选择服务器模型';

  @override
  String get chatUiChooseAnotherModelInThePickerTo => '在选择器中选择其他模型，即可建立最近使用列表。';

  @override
  String get chatUiChooseModel => '选择模型';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent => '选择活动的 OpenCode 智能体';

  @override
  String chatUiChooseAgentMode(String agent) {
    return '选择 $agent 的工作方式：其模式';
  }

  @override
  String get chatUiChooseTheCurrentModelVariantOrReasoning => '选择当前模型变体或推理强度';

  @override
  String get chatUiCollapseReasoning => '折叠推理过程';

  @override
  String get chatUiCommandMap => '命令地图';

  @override
  String get chatUiCompactContext => '压缩上下文';

  @override
  String get chatUiCompactSession => '压缩对话';

  @override
  String get chatUiCompactingConversation => '正在压缩对话…';

  @override
  String get chatUiCompactionFailed => '压缩失败';

  @override
  String get chatUiCompactionStarted => '已开始压缩';

  @override
  String get chatUiCompose => '撰写';

  @override
  String get chatUiConnectProvider => '连接提供商';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      '连接健康状态、服务器版本和实时模式';

  @override
  String get chatUiContextAdded => '已添加上下文';

  @override
  String get chatUiContextCompacted => '为节省空间，较早的消息已被概括';

  @override
  String get chatUiCopiedPasteItIntoTheComposer => '已复制。请粘贴到输入框';

  @override
  String get chatUiCopyMessageText => '复制消息文字';

  @override
  String get chatUiCopyShareLink => '复制分享链接';

  @override
  String get chatUiCopyTheRenderedConversationAsMarkdown =>
      '将渲染后的对话复制为 Markdown';

  @override
  String get chatUiCopyTranscript => '复制对话记录';

  @override
  String get chatUiCreateOrCopyAPublicSessionLink => '创建或复制公开对话链接';

  @override
  String get chatUiCurrentSession => '当前对话';

  @override
  String get chatUiDelegate => '委派';

  @override
  String get chatUiDelegateThisPrompt => '委派这条提示词';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent => '将此提示词委派给服务器上的子智能体';

  @override
  String get chatUiDelegatedSession => '已委派的对话';

  @override
  String get chatUiDeleteMessage => '删除消息';

  @override
  String get chatUiDeleteThisMessage => '删除此消息？';

  @override
  String get chatUiDetails => '详情';

  @override
  String get chatUiDirectory => '目录';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink => '停用当前公开对话链接';

  @override
  String get chatUiDiscardDraft => '丢弃草稿';

  @override
  String get chatUiDiscardPromptChanges => '放弃提示词更改？';

  @override
  String get chatUiDiscardQueuedDraft => '要丢弃排队中的草稿吗？';

  @override
  String get chatUiDismissPromptError => '关闭提示词错误';

  @override
  String get chatUiEachAttachmentMustBe10MBOr => '每个附件不能超过 10 MB。';

  @override
  String get chatUiEdit => '编辑';

  @override
  String get chatUiEditDraft => '编辑草稿';

  @override
  String get chatUiEditTheCurrentPromptInAFocused => '在专注的全屏视图中编辑当前提示词';

  @override
  String get chatUiErrorDetails => '错误详情';

  @override
  String get chatUiExpandReasoning => '展开推理过程';

  @override
  String get chatUiExportSessionTranscript => '导出对话记录';

  @override
  String get chatUiExportTranscript => '导出对话记录';

  @override
  String get chatUiFetchPage => '抓取页面';

  @override
  String get chatUiFiles => '文件';

  @override
  String get chatUiFilesAreUnavailableInThisPreview => '此预览中无法使用文件。';

  @override
  String get chatUiFindACommandOrAction => '查找命令或操作';

  @override
  String get chatUiFindAMessageJumpToItOr => '查找消息、跳转到消息，或从某条提示词分叉';

  @override
  String get chatUiFindASubagent => '查找子智能体';

  @override
  String get chatUiFindFiles => '查找文件';

  @override
  String get chatUiFindSessionsAcrossEveryOpenCodeProject =>
      '在所有 OpenCode 项目中查找对话';

  @override
  String get chatUiFollowAndroidOrChooseTheNativeLight =>
      '跟随 Android，或选择原生浅色或深色主题';

  @override
  String get chatUiForkFromThisPrompt => '从此提示词分叉';

  @override
  String get chatUiForkSession => '分叉对话';

  @override
  String get chatUiGeneratedFile => '生成的文件';

  @override
  String get chatUiHideTimestamps => '隐藏时间戳';

  @override
  String get chatUiImageDataIsUnavailable => '图片数据不可用。';

  @override
  String get chatUiInputRequested => '需要输入';

  @override
  String get chatUiInspectGitLanguageServicesAndFormattersFor =>
      '查看此项目的 Git、语言服务和格式化工具';

  @override
  String get chatUiInspectMCPStatusAuthenticationAndResources =>
      '查看 MCP 状态、认证和资源';

  @override
  String get chatUiInspectCurrentTokensCacheCostAndContext =>
      '查看当前 token、缓存、费用和上下文用量';

  @override
  String get chatUiInspectToolsCallableByTheActiveProvider =>
      '查看当前提供商和模型可调用的工具';

  @override
  String get chatUiItsTextReturnsToTheComposerAs => '其文字会作为草稿回到输入框。';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      '跳转到任意位置。分叉会还原一条提示词以便编辑。';

  @override
  String get chatUiKeepItPending => '保持待发送';

  @override
  String get chatUiKeepItQueued => '保持排队';

  @override
  String get chatUiLanguageServer => '语言服务器';

  @override
  String get chatUiList => '列表';

  @override
  String get chatUiLoadingSubagents => '正在加载子智能体…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript => '长推理在记录中已折叠';

  @override
  String get chatUiMCPServers => 'MCP 服务器';

  @override
  String get chatUiManageProviderAndIntegrationAuthentication => '管理提供商和集成认证';

  @override
  String get chatUiMessage => '消息';

  @override
  String get chatUiMessageTimeline => '消息时间线';

  @override
  String get chatUiMessageTimestampsHidden => '消息时间戳已隐藏';

  @override
  String get chatUiMessageTimestampsShown => '消息时间戳已显示';

  @override
  String get chatUiModel => '模型';

  @override
  String get chatUiModelAndAgent => '模型与智能体';

  @override
  String get chatUiMoveSession => '移动对话';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory => '将此对话移动到另一个项目';

  @override
  String get chatUiMoved => '已移动';

  @override
  String get chatUiNavigate => '导航';

  @override
  String get chatUiNoAnswer => '无回答';

  @override
  String get chatUiNoMatchingMessages => '没有匹配的消息';

  @override
  String get chatUiNoShareLinkWasReturned => '未返回分享链接';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer => '此服务器没有可用的子智能体';

  @override
  String get chatUiNotConnectedToTheServerRightNow => '当前未连接到服务器。';

  @override
  String get chatUiOpenParentSession => '打开上级对话';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals => '打开常驻的项目终端';

  @override
  String get chatUiOpenProviders => '打开服务商';

  @override
  String get chatUiOpenSubagentSession => '打开子智能体对话';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      '离线时无法使用 OpenCode 命令。';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt => 'OpenCode 无法完成此提示词。';

  @override
  String get chatUiOpenCodeIsReconnecting => 'OpenCode 正在重新连接。';

  @override
  String chatUiAgentIsReconnecting(String agent) {
    return '$agent 正在重新连接。';
  }

  @override
  String chatUiAgentIsReconnectingTryAgainShortly(String agent) {
    return '$agent 正在重新连接。请稍后再试。';
  }

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'OpenCode 正在重新连接。请稍后再试。';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'OpenCode 正在重新连接。服务器在线后请重试。';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain => 'OpenCode 正在重新连接。请重试。';

  @override
  String get chatUiOpenCodeNeedsInput => 'OpenCode 需要你的输入';

  @override
  String get chatUiOpenCodeServerCommand => 'OpenCode 服务器命令';

  @override
  String get chatUiOutputPruned => '输出已裁剪';

  @override
  String get chatUiPendingChange => '待处理的更改';

  @override
  String get chatUiProjectFiles => '项目文件';

  @override
  String get chatUiProjectHealth => '项目健康状态';

  @override
  String get chatUiProjectReference => '项目引用';

  @override
  String get chatUiProjectReferences => '项目引用';

  @override
  String get chatUiProjectsAndWorkspaces => '项目与工作树';

  @override
  String get chatUiPromptEditor => '提示词编辑器';

  @override
  String get chatUiPromptFromParentAgent => '来自父智能体的提示词';

  @override
  String get chatUiPromptTools => '提示词工具';

  @override
  String get chatUiQuestion => '问题';

  @override
  String get chatUiQuestions => '问题';

  @override
  String get chatUiQueuedRunsAfterThisTurn => '已排队 · 本轮结束后运行';

  @override
  String get chatUiQueuedWillSendWhenReconnected => '已排队 — 重新连接后发送';

  @override
  String get chatUiRead => '读取';

  @override
  String get chatUiReasoningExpandedInTheTranscript => '推理在记录中已展开';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice => '在本设备上录音并转写';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      '引用已保留给你的下一条提示词：命令不会携带它。';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      '引用已保留给你的下一条提示词：命令不会携带它们。';

  @override
  String get chatUiReloadMessages => '刷新消息';

  @override
  String get chatUiRename => '重命名';

  @override
  String get chatUiRenameSession => '重命名对话';

  @override
  String get chatUiRestoreRevertedPrompt => '恢复已还原的提示词';

  @override
  String get chatUiRestoreTheCurrentlyRevertedSessionState => '恢复当前已还原的对话状态';

  @override
  String get chatUiRetryLastPrompt => '重试上一条提示词';

  @override
  String get chatUiRevertLastPrompt => '撤销上一条提示词';

  @override
  String get chatUiReviewCommentAddedToThePrompt => '审查评论已添加到提示词';

  @override
  String get chatUiReviewHandledAppErrorsAndSendA => '查看已处理的应用错误并发送脱敏报告';

  @override
  String get chatUiReviewTheActualDiffForThisSession => '查看此对话的实际差异';

  @override
  String get chatUiRunOnYourComputer => '在你的电脑上运行';

  @override
  String get chatUiRunShellCommand => '运行 Shell 命令';

  @override
  String get chatRunShellLabel => '命令';

  @override
  String get chatRunShellHint => 'npm test';

  @override
  String get chatRunShellHelper => '智能体会在此项目中运行它，其输出会加入对话。';

  @override
  String get chatRunShellEmpty => '请输入要运行的命令。';

  @override
  String get chatRenameEmpty => '请输入标题。';

  @override
  String get chatUiSaveTheConversationAsAMarkdownFile => '将对话保存为 Markdown 文件';

  @override
  String get chatUiScope => '范围';

  @override
  String get chatUiSearchMessages => '搜索消息';

  @override
  String get chatUiSearchMobileActionsAndServerProvidedCommands =>
      '搜索移动端操作和服务器提供的命令';

  @override
  String get chatUiSearchText => '搜索文本';

  @override
  String get chatUiSelectAModelBeforeCompactingThisSession => '压缩此对话前请先选择模型。';

  @override
  String get chatUiServerCommands => '服务器命令';

  @override
  String get chatUiServerMessage => '服务器消息';

  @override
  String get chatUiServerStatus => '服务器状态';

  @override
  String get chatUiSessionChanges => '对话更改';

  @override
  String get chatUiSessionContext => '对话上下文';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      '对话已分享。请手动复制可见的链接。';

  @override
  String get chatUiShareLinkCopied => '分享链接已复制';

  @override
  String get chatUiShareSession => '分享对话';

  @override
  String get chatUiShareThisSession => '分享此对话？';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView => '已分享：任何拥有链接的人都可以查看';

  @override
  String get chatUiShowAllSubagentSessions => '显示所有子智能体对话';

  @override
  String get chatUiShowTimestamps => '显示时间戳';

  @override
  String get chatUiSkill => '技能 ·';

  @override
  String get chatUiSkills => '技能';

  @override
  String get chatUiSlashCommandsAndAgents => '斜杠命令和智能体';

  @override
  String get chatUiStartACleanSessionInThisWorkspace => '在此项目中开始全新对话';

  @override
  String get chatUiStopSharing => '停止分享';

  @override
  String get chatUiSubagent => '子智能体';

  @override
  String get chatUiSubagentFailed => '子智能体失败。';

  @override
  String get chatUiSubagentWorking => '子智能体工作中…';

  @override
  String get chatUiSubagentsCouldNotBeLoaded => '无法加载子智能体';

  @override
  String get chatUiSummarizeTheSessionUsingTheSelectedModel => '使用所选模型总结对话';

  @override
  String get chatUiSwitchOrganization => '切换组织';

  @override
  String get chatUiSwitchProjectDirectoryOrWorktree => '切换项目、目录或工作树';

  @override
  String get chatUiSystemUpdate => '系统更新';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo => '告诉智能体原因，或让它改做什么（可选）';

  @override
  String get chatUiThatMessageIsNoLongerInThis => '该消息已不在此对话中。';

  @override
  String get chatUiTheFileHasNoContentToAttach => '该文件没有可附加的内容。';

  @override
  String get chatUiTheFileHasNoContentToSave => '该文件没有可保存的内容。';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe => '表单或项目已变更。请重新打开当前请求。';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom => '此服务器上没有该生成的文件。';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      '该消息及其所有部分将从对话中永久移除，之后的回复将不再看到它们。它所做的文件更改不会被撤销。';

  @override
  String get chatUiTheServerReturnedEmptyImageData => '服务器返回了空的图片数据。';

  @override
  String get chatUiThisDraftHasNotBeenSentTo => '这份草稿还没有发送给 OpenCode。';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      '这份草稿太大，无法排队，或者队列已被更新的草稿占满。请移除一个附件，或在设置中清除排队的提示词。';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn => '无法恢复此提示词，因为有附件不可用。';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn => '无法重试此提示词，因为有附件不可用。';

  @override
  String get chatUiTimeline => '时间线';

  @override
  String get chatUiTimestampsUsage => '时间戳与用量';

  @override
  String get chatUiTitle => '标题';

  @override
  String get chatUiTodos => '任务';

  @override
  String get chatUiToggleCreationTimesBesideTranscriptEntries =>
      '切换对话记录条目旁创建时间的显示';

  @override
  String get chatUiToggleLongReasoningDetailsAcrossTheTranscript =>
      '切换整个对话记录中长推理详情的显示';

  @override
  String get chatUiToolFailed => '工具失败。';

  @override
  String get chatUiToolsAndCapabilities => '工具与能力';

  @override
  String get chatUiTranscriptCopiedAsMarkdown => '记录已复制为 Markdown';

  @override
  String get chatUiTranscriptDisplay => '对话记录显示';

  @override
  String get chatUiTranscriptSaved => '记录已保存';

  @override
  String get chatUiVoiceConversationWasInterrupted => '语音对话已中断。';

  @override
  String get chatUiVoiceInput => '语音输入';

  @override
  String get chatUiVoiceInputIsUnavailable => '语音输入不可用。';

  @override
  String get chatUiLoadTools => '加载工具';

  @override
  String chatUiToolFrom(String tool, String server) {
    return '$tool · $server';
  }

  @override
  String get chatUiWebSearch => '网页搜索';

  @override
  String get chatUiWrite => '写入';

  @override
  String get chatUiWriteYourOpenCodePrompt => '写下你的 OpenCode 提示词…';

  @override
  String get chatUiYou => '你';

  @override
  String get chatUiYourOriginalComposerDraftAndAttachmentsWill =>
      '你原来的输入框草稿和附件将保持不变。';

  @override
  String get chatUiInThisChat => '在此对话中';

  @override
  String get chatUiNewFile => '新文件';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return '已排队 — 重新连接后发送。$detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '现在无法使用 /$command。';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return '最多可附加 $count 个文件。';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已发送 $count 条排队的提示词',
      one: '已发送 1 条排队的提示词',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 份草稿正在等待其他服务器',
      one: '1 份草稿正在等待其他服务器',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return '此对话接下来的回复将使用 $model。';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '@$name 已在提示词中';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return '已附加 $filename。请添加你的评论。';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return '保存 $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return '$filename 已保存到你的设备。';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '有 $count 份草稿在排队，重新连接后发送。',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 份草稿正在等待其他服务器。',
      one: '1 份草稿正在等待其他服务器。',
    );
    return '$_temp0';
  }

  @override
  String chatUiQuestionCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个问题',
      one: '1 个问题',
    );
    return '$_temp0';
  }

  @override
  String chatUiPermissionNeeded(Object title) {
    return '需要权限：$title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return '问题：$title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个问题',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return '已触发速率限制。正在重试$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return '已触发速率限制。$time 后重试$attempt';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '发送时会将 $count 条引用作为文字添加。不会随草稿保存。',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '已添加 $count 个附件';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条更早的消息',
      one: '1 条更早的消息',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count tok';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '第 $position / 共 $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return '已委派的对话 · $count';
  }

  @override
  String chatUiToolsSummary(Object tools) {
    return '工具：$tools';
  }

  @override
  String chatUiFromLine(Object line) {
    return '来自 $line';
  }

  @override
  String chatUiLineCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行',
      one: '1 行',
    );
    return '$_temp0';
  }

  @override
  String chatUiLineRange(Object start, Object end) {
    return 'L$start–$end';
  }

  @override
  String chatUiEntryCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条',
      one: '1 条',
    );
    return '$_temp0';
  }

  @override
  String chatUiFoundCount(Object count) {
    return '找到 $count 个';
  }

  @override
  String chatUiMatchCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个匹配',
      one: '1 个匹配',
    );
    return '$_temp0';
  }

  @override
  String chatUiFileCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件',
      one: '1 个文件',
    );
    return '$_temp0';
  }

  @override
  String chatUiProviderSearch(Object provider) {
    return '$provider 搜索';
  }

  @override
  String chatUiResultCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个结果',
      one: '1 个结果',
    );
    return '$_temp0';
  }

  @override
  String chatUiAnsweredCount(Object count) {
    return '已答复 $count 个';
  }

  @override
  String chatUiAskedCount(Object count) {
    return '已提问 $count 个';
  }

  @override
  String chatUiDurationMinutesSeconds(Object minutes, Object seconds) {
    return '$minutes分 $seconds秒';
  }

  @override
  String chatUiDurationSeconds(Object seconds) {
    return '$seconds秒';
  }

  @override
  String chatUiFileLoadFailed(Object error) {
    return '无法从 OpenCode 服务器加载此文件：$error';
  }

  @override
  String chatUiMoreEntries(Object total) {
    return '共 $total 条 · 还有更多';
  }

  @override
  String chatUiEntryTotal(Object total) {
    return '$total 条';
  }

  @override
  String chatUiAnsweredDetail(Object answer) {
    return '已回答：$answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return '正在加载 $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return '预览生成的图片 $filename';
  }

  @override
  String chatUiParentSession(Object title) {
    return '父级 · $title';
  }

  @override
  String chatUiChooseOption(Object option) {
    return '选择：$option';
  }

  @override
  String get chatUiMainSession => '主对话';

  @override
  String get chatUiTodo => '待办';

  @override
  String get chatUiBackgroundResult => '后台结果';

  @override
  String get chatUiBackgroundComplete => '已完成';

  @override
  String get chatUiBackgroundError => '失败';

  @override
  String get chatUiBackgroundCancelled => '已取消';

  @override
  String get chatUiResultSourceDetails => '服务器消息详情';

  @override
  String get chatUiResultOpenChild => '打开子智能体的对话';

  @override
  String get chatUiNoResultText => '服务器未返回结果文本。';

  @override
  String get chatUiTimedOut => '已超时';

  @override
  String get chatUiKilled => '已停止';

  @override
  String get chatUiTruncated => '已截断';

  @override
  String get chatUiUpdated => '已更新';

  @override
  String get chatUiError => '错误';

  @override
  String get chatUiAssistant => '助手';

  @override
  String get chatUiUser => '用户';

  @override
  String get chatUiOpenCodeSession => 'OpenCode 对话';

  @override
  String get chatUiTool => '工具';

  @override
  String get chatUiFile => '文件';

  @override
  String get e7LibraryReportABug => '报告问题';

  @override
  String get e7LibraryKeyboardShortcuts => '键盘快捷键';

  @override
  String get e7LibraryHTTPHeader => 'HTTP 请求头';

  @override
  String get e7LibraryEnvironmentVariable => '环境变量';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      '服务器正在重新连接。请稍后再试。';

  @override
  String get e7LibraryWhatIsMCP => '什么是 MCP？';

  @override
  String get e7LibrarySavingConfiguration => '正在保存配置';

  @override
  String get e7LibrarySaveMCPServer => '保存 MCP 服务器';

  @override
  String get e7LibraryThisProject => '此项目';

  @override
  String e7LibraryWritesOnlyTo(String detail1) {
    return '仅写入 $detail1。';
  }

  @override
  String get e7LibraryWritesToThisOpenCodeServerSGlobal =>
      '写入此 OpenCode 服务器的全局配置。';

  @override
  String get e7LibraryServerName => '服务器名称';

  @override
  String get e7LibraryDocsOrBrowserTools => 'docs 或 browser-tools';

  @override
  String get e7LibraryUniqueWithinTheSelectedConfiguration => '在所选配置中必须唯一。';

  @override
  String get e7LibraryEnterAServerName => '输入服务器名称';

  @override
  String get e7LibraryRemoteURL => '远程 URL';

  @override
  String get e7LibraryLocalCommand => '本地命令';

  @override
  String get e7LibraryOptional => '可选';

  @override
  String get e7LibraryEnterAValueGreaterThanZero => '请输入大于零的值';

  @override
  String get e7LibraryMCPEndpointURL => 'MCP 端点 URL';

  @override
  String get e7LibraryHTTPIsAcceptedForLocalDevelopmentServers =>
      '本地开发服务器允许使用 HTTP。';

  @override
  String get e7LibraryEnterAValidHTTPOrHTTPSURL =>
      '请输入不含凭据的有效 HTTP 或 HTTPS URL';

  @override
  String get e7LibraryDetectOAuthAutomatically => '自动检测 OAuth';

  @override
  String get e7LibraryTurnThisOffWhenTheServerUses =>
      '当服务器使用请求头、且绝不应启动 OAuth 时，请关闭此选项。';

  @override
  String get e7LibraryCommandAndArguments => '命令与参数';

  @override
  String get e7LibraryRunsOnTheOpenCodeServerNotThis =>
      '在 OpenCode 服务器上运行，而非本机。每行输入一个参数。';

  @override
  String get e7LibraryEnterACommand => '输入命令';

  @override
  String get e7LibraryWorkingDirectory => '工作目录';

  @override
  String get e7LibraryOptionalServerPath => '可选的服务器路径';

  @override
  String get e7LibraryEnvironmentVariables => '环境变量';

  @override
  String e7LibraryInvalidOnLineUseKEYVALUE(String detail1, String detail2) {
    return '第 $detail2 行的 $detail1 无效。请使用 KEY=VALUE 格式。';
  }

  @override
  String e7LibraryInvalidNameOnLine(String detail1, String detail2) {
    return '第 $detail2 行的 $detail1 名称无效。';
  }

  @override
  String e7LibraryDuplicateName(String detail1, String detail2) {
    return '$detail1 名称重复：“$detail2”。';
  }

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgain => '服务器正在重新连接。请重试。';

  @override
  String get e7LibraryRevokeAlwaysAllowedAction => '撤销访问权限？';

  @override
  String get e7LibraryAction => '操作';

  @override
  String get e7LibraryResource => '资源';

  @override
  String get e7LibraryRevokeAccess => '撤销访问权限';

  @override
  String get e7LibraryAlwaysAllowedActionRevoked => '已撤销“始终允许”的操作';

  @override
  String get e7LibraryAlwaysAllowedActions => '始终允许的操作';

  @override
  String get e7LibraryNoAlwaysAllowedActions => '没有始终允许的操作';

  @override
  String get e7LibraryTheLastActionFailed => '上一次操作失败';

  @override
  String e7LibraryRevokeAccess2(String detail1) {
    return '撤销 $detail1 的访问权限';
  }

  @override
  String get e7LibraryToolsAndCapabilities => '工具与能力';

  @override
  String get e7LibraryRefreshTools => '刷新工具';

  @override
  String get e7LibraryOpenCodeToolsDependOnTheProviderAnd =>
      'OpenCode 的工具取决于当前对话所使用的提供商和模型。';

  @override
  String get e7LibraryChooseModel => '选择模型';

  @override
  String e7LibrarySearchTools2(String detail1) {
    return '搜索 $detail1 工具';
  }

  @override
  String get e7LibraryRegisteredInventoryUnavailable => '已注册清单不可用';

  @override
  String get e7LibraryServerCapabilityUnavailable => '服务器能力不可用';

  @override
  String get e7LibraryNoToolsForThisModel => '此模型没有可用工具';

  @override
  String get e7LibraryNoDescriptionReturnedByOpenCode => 'OpenCode 未返回描述';

  @override
  String get e7LibraryCopyParameterSchema => '复制参数架构';

  @override
  String get e7LibraryNoProjectSelected => '未选择项目';

  @override
  String get e7LibraryNoProjectFolderIsOpenChooseOne =>
      '未打开任何项目。请在“文件”顶部的列表中选择一个。';

  @override
  String get e7LibrarySwitchProject => '切换项目';

  @override
  String get e7LibraryWorktrees => '工作树';

  @override
  String get e7LibraryManagedWorkspaces => '云环境';

  @override
  String get e7LibraryProjectHealth => '项目健康状态';

  @override
  String get e7LibraryOpenCodeIsReconnecting => 'OpenCode 正在重新连接。';

  @override
  String get e7LibraryCloudEnvironments => '云环境';

  @override
  String get e7LibraryDiscoverExistingEnvironments => '发现现有环境';

  @override
  String get e7LibraryNewEnvironment => '新建环境';

  @override
  String get e7LibraryEnvironments => '环境';

  @override
  String get e7LibraryNoCloudEnvironments => '没有云环境';

  @override
  String get e7LibraryEnvironmentRefreshFailed => '环境刷新失败';

  @override
  String get e7LibraryRetryCloudEnvironments => '重试';

  @override
  String get e7LibraryAdapterRefreshFailed => '适配器刷新失败';

  @override
  String get e7LibraryConnected => '已连接';

  @override
  String get e7LibraryConnecting => '正在连接';

  @override
  String get e7LibraryDisconnected => '已断开';

  @override
  String get e7LibraryEnvironmentActions => '环境操作';

  @override
  String get e7LibraryOpenAgain => '重新打开';

  @override
  String get e7LibraryNewManagedWorkspace => '新建云环境';

  @override
  String get e7LibraryCreateAndOpen => '创建并打开';

  @override
  String e7LibraryRemove(String detail1) {
    return '删除 $detail1？';
  }

  @override
  String get e7LibraryRemovePermanently => '永久删除';

  @override
  String e7LibraryIsReady(String detail1) {
    return '$detail1 已就绪';
  }

  @override
  String get e7LibraryOpenCodeCouldNotPrepareThisWorktree => '此工作树无法准备完成。';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return '$detail1 已创建，但设置状态尚未确认。';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis => '请等待此工作树完成准备。';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations => '服务器未切换项目。';

  @override
  String e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
    String detail1,
    String detail2,
  ) {
    return '此破坏性操作前无法验证 $detail1：$detail2';
  }

  @override
  String e7LibraryResetToTheDefaultBranch(String detail1) {
    return '$detail1 已重置到默认分支';
  }

  @override
  String e7LibraryAndItsBranchWereRemoved(String detail1) {
    return '$detail1 及其分支已删除';
  }

  @override
  String e7LibraryReset(String detail1) {
    return '重置 $detail1？';
  }

  @override
  String get e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes =>
      '此操作会永久丢弃已跟踪的改动，并删除所有未跟踪和被忽略的文件。子模块也会被重置和清理。此操作无法撤销。';

  @override
  String get e7LibraryResetWorktree => '重置工作树';

  @override
  String get e7LibraryRefreshWorktrees => '刷新工作树';

  @override
  String get e7LibraryNewWorktree => '新建工作树';

  @override
  String get e7LibraryNoIsolatedWorktreesYet => '尚无隔离的工作树';

  @override
  String get e7LibraryPreparingFilesAndProjectTasks => '正在准备文件与项目任务…';

  @override
  String get e7LibraryWorktreeActions => '工作树操作';

  @override
  String get e7LibraryReset2 => '重置';

  @override
  String get e7LibraryNameOptional => '名称（可选）';

  @override
  String get e7LibraryTheWorktreeDirectoryAndItsGitBranch =>
      '工作树目录及其 Git 分支将被永久删除。已有对话会保留在历史中，但其工作目录将不再存在。';

  @override
  String get e7LibraryInitializeGitRepository => '初始化 Git 仓库？';

  @override
  String get e7LibraryOpenCodeWillRunGitInitInThe =>
      'OpenCode 将在当前项目中运行 git init。现有文件不会被修改或提交。这会启用分支、工作树改动和代码审查功能。';

  @override
  String get e7LibraryInitializeGit => '初始化 Git';

  @override
  String get e7LibraryGitRepositoryInitialized => 'Git 仓库已初始化';

  @override
  String get e7LibraryVersionControl => '版本控制';

  @override
  String get e7LibraryLanguageServices => '语言服务';

  @override
  String get e7LibraryFormatters => '格式化工具';

  @override
  String get e7LibraryGitIsNotInitialized => 'Git 尚未初始化';

  @override
  String get e7LibraryInitializeThisProjectToEnableBranchesWorking =>
      '初始化此项目以启用分支、工作树改动和代码审查。';

  @override
  String get e7LibraryRunGitInitFromATerminal => '从终端运行 `git init`';

  @override
  String get e7LibraryGitInitializationFailed => 'Git 初始化失败';

  @override
  String get e7LibraryNoActiveBranch => '没有活动分支';

  @override
  String e7LibraryDefaultBranch(String detail1) {
    return '默认分支：$detail1';
  }

  @override
  String get e7LibraryWorkingTreeIsClean => '工作树干净';

  @override
  String e7LibraryChangedFiles(String detail1) {
    return '$detail1 个已更改文件';
  }

  @override
  String get e7LibraryNoUncommittedChanges => '没有未提交的改动';

  @override
  String get e7LibraryNoActiveLanguageServices => '没有活动的语言服务';

  @override
  String get e7LibraryOpenCodeActivatesThemWhileItInspectsSupported =>
      'OpenCode 会在编码过程中检查支持的源文件时启用它们。';

  @override
  String get e7LibraryNoFormattersConfigured => '未配置格式化工具';

  @override
  String get e7LibraryEnabled => '已启用';

  @override
  String get e7LibraryDisabled => '已禁用';

  @override
  String e7LibraryLoading(String detail1) {
    return '正在加载 $detail1';
  }

  @override
  String get e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe =>
      '无法自动捕获回调。请粘贴回调 URL 或授权码。';

  @override
  String get e7LibraryThePhoneIsSecurelyListeningForThis =>
      '本机正在安全地监听此授权回调。你也可以手动输入。';

  @override
  String get e7LibraryCallbackURLOrCode => '回调 URL 或授权码';

  @override
  String get e7LibraryUpdating => '正在更新…';

  @override
  String get e7LibraryNotConnected => '未连接';

  @override
  String get e7LibraryServerEnvironment => '服务器环境';

  @override
  String get e7LibraryServerManaged => '由服务器管理';

  @override
  String get e7LibraryConnect => '连接';

  @override
  String get e7LibraryAuthenticationFailed => '认证失败';

  @override
  String get e7LibraryAuthenticationAttemptExpired => '认证尝试已过期';

  @override
  String get e7LibraryAuthenticationComplete => '认证完成';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe => '从浏览器返回，并输入授权码。';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      '请在浏览器中完成认证，然后检查状态。';

  @override
  String get e7LibraryAuthorizationCode => '授权码';

  @override
  String get e7LibrarySelectAnOption => '选择一个选项';

  @override
  String get e7LibraryEnterAValue => '输入值';

  @override
  String get e7LibraryTheServerReturnedAnUnsafeAuthorizationLink =>
      '服务器返回了不安全的授权链接。仅允许使用主机有效且不含内嵌凭据的 HTTPS 链接。';

  @override
  String get e7LibraryCouldNotLoadThisSection => '无法加载此部分';

  @override
  String get e7LibrarySkills => '技能';

  @override
  String get e7LibraryNoSkillsAvailable => '没有可用的技能';

  @override
  String get e7LibraryModelsAndAgents => '模型与智能体';

  @override
  String get e7LibraryUnavailable => '不可用';

  @override
  String get e7LibraryFinishOrCancelTheCurrentMCPAuthorization =>
      '请先完成或取消当前的 MCP 授权。';

  @override
  String get e7LibraryCouldNotOpenTheAuthorizationPage => '无法打开授权页面';

  @override
  String e7LibraryAuthenticated(String detail1) {
    return '$detail1 已通过验证';
  }

  @override
  String get e7LibraryCouldNotConfirmMCPAuthentication => '无法确认 MCP 认证';

  @override
  String get e7LibraryMCPServerSavedInOpenCode => 'MCP 服务器已保存到 OpenCode';

  @override
  String get e7LibraryMCPUnavailable => 'MCP 不可用';

  @override
  String get e7LibraryMCPAndIntegrations => '提供商与 MCP';

  @override
  String get e7LibraryCouldNotSaveSignInRecovery => '无法保存登录恢复信息。';

  @override
  String get e7LibraryNoProviderConnectionsAvailable => '没有可用的提供商连接';

  @override
  String get e7LibraryThisServerDidNotReturnAnyProvider => '此服务器未返回任何提供商集成。';

  @override
  String get e7LibrarySearchProvidersOrModels => '搜索提供商或模型';

  @override
  String get e7LibraryNoMCPServersConfigured => '未配置 MCP 服务器';

  @override
  String get e7LibrarySaveOneForThisProjectOrEvery => '为此项目或服务器上的所有项目保存一个。';

  @override
  String get e7LibraryAddAnMCPServer => '添加 MCP 服务器';

  @override
  String get e7LibraryAuthorizing => '正在授权';

  @override
  String get e7LibraryResources => '资源';

  @override
  String get e7LibraryNoResourcesAvailable => '没有可用的资源';

  @override
  String get e7LibraryConnectedMCPServersHaveNotExposedAny =>
      '已连接的 MCP 服务器未对外提供任何资源。';

  @override
  String get e7LibraryOpenBrowser => '打开浏览器';

  @override
  String get e7LibraryConnectedAndToolsAreAvailable => '已连接，工具可用';

  @override
  String get e7LibraryConnectionFailed => '连接失败';

  @override
  String get e7LibraryAuthenticationRequired => '需要认证';

  @override
  String get e7LibraryClientRegistrationRequired => '需要注册客户端';

  @override
  String e7LibraryStoredCredential(String detail1) {
    return '已存储凭据：$detail1';
  }

  @override
  String get e7LibraryNoConnectionMethodsAvailable => '没有可用的连接方式';

  @override
  String get e7LibraryConfiguredOnTheServer => '已在服务器上配置';

  @override
  String e7LibraryDisconnect2(String detail1) {
    return '断开 $detail1？';
  }

  @override
  String e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
    String detail1,
  ) {
    return '已移除 $detail1 凭据；服务器环境仍然有效';
  }

  @override
  String e7LibraryDisconnected2(String detail1) {
    return '$detail1 已断开';
  }

  @override
  String e7LibraryConnect2(String detail1) {
    return '连接 $detail1';
  }

  @override
  String get e7LibraryAuthorizationWasNotOpenedThePendingAttempt =>
      '授权页面未打开。待处理的授权尝试已保留。';

  @override
  String get e7LibraryCouldNotOpenOAuth => '无法打开 OAuth';

  @override
  String e7LibraryIsConnected(String detail1) {
    return '$detail1 已连接';
  }

  @override
  String get e7LibraryTheSignInSourceChanged => '登录来源已更改。';

  @override
  String get e7LibraryCouldNotConfirmAuthenticationReturnToThe =>
      '无法确认认证。请返回最初的页面重试。';

  @override
  String get e7LibraryNoServerCommandsFound => '未找到服务器命令';

  @override
  String get e7LibraryCommandsFromYourProjectAndSkillsAppear =>
      '来自你的项目和技能的命令会出现在这里。';

  @override
  String get e7LibraryNoDescription => '没有描述';

  @override
  String get e7LibraryServerCommands => '服务器命令';

  @override
  String get e7LibraryReferences => '引用';

  @override
  String get e7LibraryNoReferencesConfigured => '未配置引用';

  @override
  String e7LibraryChangedFilesDetected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '检测到 $count 个已更改文件。',
      one: '检测到 1 个已更改文件。',
    );
    return '$_temp0';
  }

  @override
  String get e7LibraryEnvironmentRemainsAfterDisconnect =>
      '此提供商还使用服务器环境，移动端无法移除，该环境将保持有效。';

  @override
  String get e7LibrarySearchImportAliases => '备份 恢复 传输 JSON 对话 会话 聊天';

  @override
  String get e7LibrarySearchShortcutsAliases => '快捷键 帮助 桌面';

  @override
  String get e7SetupApiKeyHint => '粘贴 API 密钥';

  @override
  String get e7SetupBrowserHint => '会打开浏览器。如果重定向无法到达 OpenCode，请在此粘贴回调 URL。';

  @override
  String get e7SetupDeviceCodeHint => '使用一次性代码。可在手机上完成。';

  @override
  String get e7SetupAccountHint => '使用你的账号登录';

  @override
  String get e7SetupNewTerminalDetail => '在当前项目中启动一个 shell。';

  @override
  String get e7SetupShowPassword => '显示服务器密码';

  @override
  String get e7SetupAuthFailed => '服务器已启动，但身份验证失败。';

  @override
  String get e7SetupScanInstruction => '将相机对准 opencode2 pair 输出的二维码。';

  @override
  String get e7SetupRightKey => '向右箭头键';

  @override
  String get e7SetupRestartReconnectFailed => '本地服务器已重启，但应用无法重新连接。';

  @override
  String get e7SetupInstallingOpenCode => '正在安装 OpenCode';

  @override
  String get e7SetupNoOutput => '终端暂无输出。';

  @override
  String get e7SetupFollowLog => '查看服务器日志';

  @override
  String get e7SetupOpenSetupGuide => '打开设置指南';

  @override
  String get e7SetupDownKey => '向下箭头键';

  @override
  String get e7SetupVerifyContinue => '验证并继续';

  @override
  String get e7SetupAccessibleTerminal => '使用无障碍文本记录与输入';

  @override
  String get e7SetupCameraFailedDetail => '可能有其他应用正在占用相机。粘贴配对码同样有效。';

  @override
  String get e7SetupTermuxNoAnswer =>
      'Termux 没有响应。请点按“复制并打开 Termux”，在 Termux 中粘贴那一行并按回车，然后回到这里。';

  @override
  String get e7SetupCopyOpenTermux => '复制并打开 Termux';

  @override
  String get e7SetupStopTerminalDetail => '正在运行的进程及其子进程将被终止。';

  @override
  String get e7SetupEdit => '编辑';

  @override
  String get e7SetupServerPassword => '服务器密码';

  @override
  String get e7SetupHttpsHint => '优先使用 HTTPS。远程 HTTP 需要你为每台服务器单独授权。';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      '本地服务器已就绪，但无法保存观测到的版本。请刷新设置后重试。';

  @override
  String get e7SetupPasteInstead => '改为粘贴';

  @override
  String get e7SetupConfirmUpdate => '更新托管的 OpenCode？';

  @override
  String get e7SetupInstallServiceDetail =>
      '官方安装程序加一个 systemd 用户服务，关闭终端或重启后仍可运行。';

  @override
  String get e7SetupUpdateHost => '在主机上更新 OpenCode';

  @override
  String get e7SetupKeepAfterLogout => '退出登录后保持运行';

  @override
  String get e7SetupGetTermux => '获取 Termux';

  @override
  String get e7SetupRestartServer => '重启服务器';

  @override
  String get e7SetupResumeSetup => '重试 — 从设置中断处继续';

  @override
  String get e7SetupHidePassword => '隐藏服务器密码';

  @override
  String get e7SetupControlKeys => '终端控制键。左右滑动查看更多。';

  @override
  String get e7SetupEndInputKey => '输入结束，Control D';

  @override
  String get e7SetupGuidanceSaveFailed => '无法保存连接指引。请重试保存。';

  @override
  String get e7SetupServerOperation => '服务器操作进行中';

  @override
  String get e7SetupNewTerminal => '新建终端';

  @override
  String get e7SetupUnsavedProfile => '服务器尚未保存。';

  @override
  String get e7SetupCheckingInstall => '正在检查已安装的环境...';

  @override
  String get e7SetupInspectTermuxFailed => 'Android 无法检查 Termux。';

  @override
  String get e7SetupUsername => '用户名（可选）';

  @override
  String get e7SetupFirstSetupDuration =>
      '首次设置可能需要 10–15 分钟。你可以离开此页面稍后再回来，设置会继续运行。';

  @override
  String get e7SetupNoTerminals => '没有终端进程';

  @override
  String get e7SetupEscapeKey => 'Esc 键';

  @override
  String get e7SetupLeftKey => '向左箭头键';

  @override
  String get e7SetupInstallService => '将 OpenCode 安装为后台服务';

  @override
  String get e7SetupSaveToFinish => '已连接：保存即可完成。';

  @override
  String get e7SetupPasswordStartupHint => '服务器启动时会显示。如果没有密码则留空。';

  @override
  String get e7SetupInstallTermuxDetail => '安装最新的 F-Droid 版 Termux，然后回到这里。';

  @override
  String get e7SetupStopBeforeUpdate => '更新 OpenCode 前请先停止正在进行的生成。';

  @override
  String get e7SetupRestartingLocal => '正在重启本地服务器';

  @override
  String get e7SetupAddServer => '添加服务器';

  @override
  String get e7SetupInteractiveTerminal => '使用交互式终端';

  @override
  String get e7SetupInstallingUbuntu => '正在设置 Ubuntu';

  @override
  String get e7SetupInterruptKey => '中断，Control C';

  @override
  String get e7SetupServerUrl => '服务器 URL';

  @override
  String get e7SetupUsbAccess => '通过 USB 从这部手机访问';

  @override
  String get e7SetupWaitingTermux => '正在等待 Termux 响应。这可能需要一些时间。';

  @override
  String get e7SetupDiscardChanges => '要放弃服务器的更改吗？';

  @override
  String get e7SetupPasswordRequired => '无法读取已保存的密码';

  @override
  String get e7SetupPairing => '正在配对…';

  @override
  String get e7SetupCommandInput => '终端命令输入';

  @override
  String get e7SetupCameraFailed => '无法打开相机';

  @override
  String get e7SetupPastePassword => '粘贴服务器密码';

  @override
  String get e7SetupSavingLocal => '正在保存本地服务器设置';

  @override
  String get e7SetupCopyFailureReport => '复制故障报告';

  @override
  String get e7SetupCameraDisabled => '相机访问已关闭';

  @override
  String get e7SetupPastePairing => '粘贴配对码';

  @override
  String get e7SetupServers => '服务器';

  @override
  String get e7SetupStartingSetup => '正在 Termux 中开始设置';

  @override
  String get e7SetupSetupNotStarted =>
      'Termux 已打开，但设置没有开始。请重试一次；如果仍然如此，请复制故障报告。';

  @override
  String get e7SetupThisDevice => '此设备（Termux）';

  @override
  String get e7SetupTransportReconnecting => '服务器传输正在重新连接。';

  @override
  String get e7SetupStepUnavailable => '暂不可用';

  @override
  String get e7SetupUpdateHostDetail => '当服务器报告有更新时，设置会优先提供原生升级；这是主机端的等效操作。';

  @override
  String get e7SetupMissingPasswordShort =>
      '已保存的密码不可用。请重新输入；仅当此服务器不再需要密码时才可留空。';

  @override
  String get e7SetupTesting => '正在测试…';

  @override
  String get e7SetupScanPairing => '扫描配对码';

  @override
  String get e7SetupRestartUnconfirmed => '无法确认此次重启。重试前请先刷新其进度。';

  @override
  String get e7SetupExistingMissingCredential =>
      '本地服务器已存在，但其保存的凭据不可用。请重新运行设置以安全地替换它。';

  @override
  String get e7SetupPreparingModels => '正在准备模型';

  @override
  String get e7SetupHostInstructions =>
      '这些命令需在托管此服务器的电脑上运行，应用无法代你执行。请逐条复制到那台机器的终端中。';

  @override
  String get e7SetupTranscript => '终端记录';

  @override
  String get e7SetupHostFirstSetup => '首次设置：在你的电脑上运行';

  @override
  String get e7SetupNoCameraDetail =>
      '没有可用于扫描的相机。请在服务器上运行 opencode2 pair，复制它输出的代码，并粘贴到服务器编辑器中。';

  @override
  String get e7SetupDiscard => '放弃';

  @override
  String get e7SetupConnectionClosed => '连接已关闭';

  @override
  String get e7SetupUpKey => '向上箭头键';

  @override
  String get e7SetupConnect => '连接';

  @override
  String get e7SetupRemoveTerminalDetail => '此终端记录将被移除。';

  @override
  String get e7SetupInputDisconnected => '断开连接时无法输入。';

  @override
  String get e7SetupHostCopied => '已复制。请在服务器所在的电脑上运行。';

  @override
  String get e7SetupCameraPrivacy =>
      '相机仅用于读取 opencode2 pair 输出的二维码，且仅在此界面打开时使用。你也可以改为粘贴代码，效果完全相同。';

  @override
  String get e7SetupIsV2 => '这是一个 OpenCode 2 服务器。';

  @override
  String get e7SetupResumeLive => '恢复实时视图';

  @override
  String get e7SetupTabKey => 'Tab 键';

  @override
  String get e7SetupCloseScanner => '关闭扫描器';

  @override
  String get e7SetupChooseContinue => '选择继续方式';

  @override
  String get e7SetupTermuxOutdated =>
      '这个版本的 Termux 太旧，应用无法使用。请安装最新版，然后点按“继续设置”。';

  @override
  String get e7SetupCameraNeeded => '扫描需要相机访问权限';

  @override
  String get e7SetupTerminalActions => '终端操作';

  @override
  String get e7SetupReadPassword => '为此应用读取服务器密码';

  @override
  String get e7SetupStartInstalled => '启动已安装的 OpenCode？';

  @override
  String get e7SetupTestConnection => '测试连接';

  @override
  String get e7SetupCheckingTermux => '正在检查 Termux 连接';

  @override
  String get e7SetupCheckingTermuxShort => '正在检查 Termux…';

  @override
  String get e7SetupDefaultServer => 'OpenCode 服务器';

  @override
  String get e7SetupSaving => '正在保存…';

  @override
  String get e7SetupLinuxService => '设置 OpenCode 2';

  @override
  String get e7SetupPairingDesktopHint => '请确认服务器正在运行，且它输出的地址可从本机访问。';

  @override
  String get e7SetupAboutNotices => '关于与开源声明';

  @override
  String get e7SetupSetupLost => '丢失了在 Termux 中运行的设置进程';

  @override
  String get e7SetupReportCopied => '已复制故障报告。';

  @override
  String get e7SetupAppSettings => '在设置中允许该权限';

  @override
  String get e7SetupNoCamera => '此设备没有相机';

  @override
  String get e7SetupServerDisconnected => '服务器未连接。';

  @override
  String get e7SetupEmptyPasswordHint => '仅当此服务器不再使用密码时才留空。';

  @override
  String get e7SetupAndroidOnly => '在此手机上设置仅支持 Android';

  @override
  String get e7SetupEditServer => '编辑服务器';

  @override
  String get e7SetupReenterPassword => '重新输入密码';

  @override
  String get e7SetupMissingCredential => '此托管服务器已保存的凭据不可用。请重新运行设置以安全地替换它。';

  @override
  String get e7SetupReconnect => '重新连接';

  @override
  String get e7SetupSwitchNotStarted => '运行时切换未启动。';

  @override
  String get e7SetupNoUbuntu => '未找到托管的 Ubuntu 安装。';

  @override
  String get e7SetupKeyUnavailable => '终端断开连接时不可用';

  @override
  String get e7SetupCopyTerminal => '复制终端选区或记录';

  @override
  String get e7SetupCommandHint => '输入命令';

  @override
  String get e7SetupCheckInstallFailed => '无法检查已安装的环境。';

  @override
  String get e7SetupConnectionFailed => '连接失败。';

  @override
  String get e7SetupOpenAppSettings => '打开应用设置';

  @override
  String get e7SetupFullWalkthrough => '完整指南（在浏览器中打开）';

  @override
  String get e7SetupPairingPhoneHint =>
      '绑定在自身 127.0.0.1 上的服务器，在你搭好桥接之前无法从这部手机访问：通过 USB 使用“adb reverse tcp:PORT tcp:PORT”，或使用 SSH 转发。若要改为通过网络访问，请将其置于 HTTPS 之后。';

  @override
  String get e7SetupOutputCopied => '已复制设置输出。';

  @override
  String get e7SetupMissingPasswordLong => '已保存的密码不可用。请重新输入；仅当此服务器不再需要密码时才可留空。';

  @override
  String get e7SetupNoServerGuide => '那里还没有服务器？设置指南会告诉你如何启动一个。';

  @override
  String get e7SetupStartingLocal => '正在启动本地服务器';

  @override
  String get e7SetupTerminalSemantics => '交互式终端。使用无障碍按钮可查看易读的记录和带标签的输入。';

  @override
  String get e7SetupRestartingLocalStage => '正在重启本地服务器';

  @override
  String get e7SetupEmptyPairClipboard =>
      '剪贴板为空。请在服务器上运行“opencode2 pair”，并复制它输出的代码。';

  @override
  String get e7SetupRestartActiveChanged => '本地服务器已重启，但当前服务器已更改。准备好后请重新连接。';

  @override
  String get e7SetupTokenRequired => '无法读取已保存的令牌';

  @override
  String get e7SetupPairingInstructions =>
      '在你的电脑上运行“opencode2 pair”，然后粘贴或扫描它输出的配对码。';

  @override
  String get e7SetupHostDaily => '日常使用 — 在你的电脑上运行';

  @override
  String get e7SetupStopLocal => '停止本地服务器';

  @override
  String get e7SetupReadingProgress => '正在读取设置进度';

  @override
  String get e7SetupCameraSettingsDetail =>
      'Android 不会再次询问，因此需要在应用设置中更改：开启相机后再返回。粘贴代码无需任何权限，现在就能用。';

  @override
  String get e7SetupVerifyTermuxFailed => 'Termux 桥接验证失败。';

  @override
  String get e7SetupUbuntuOnly => 'Ubuntu 已安装，OpenCode 尚未安装。';

  @override
  String get e7SetupRenameTerminal => '重命名终端';

  @override
  String get e7SetupInputUnavailable => '终端输入不可用';

  @override
  String get e7SetupUpdateInterruption => '服务器会短暂不可用。应先停止正在进行的生成。';

  @override
  String get e7SetupRunningOnPhone => 'OpenCode 正在此手机上运行。';

  @override
  String get e7SetupLocalStopped => '本地服务器已停止。已安装的文件会保留。';

  @override
  String get e7SetupDidNotConnect => '服务器未连接。';

  @override
  String get e7SetupSendCommand => '向终端发送命令';

  @override
  String get e7SetupUnsupportedSetup =>
      '在设备本机上设置仅适用于 Android 手机。在这台电脑上，请自行启动 OpenCode 并将其添加为服务器。';

  @override
  String get e7SetupSendKey => '向终端发送此按键';

  @override
  String get e7SetupRemoveTerminal => '移除终端？';

  @override
  String e7SetupTerminalNumber(int number) {
    return '终端 $number';
  }

  @override
  String e7SetupCopyCommandLabel(String label) {
    return '复制命令：$label';
  }

  @override
  String e7SetupConnectFailedDetail(String name, String detail) {
    return '无法连接到 $name。$detail 请检查服务器地址和凭据后重试。';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return '$name 已保存，但无法连接。请检查服务器地址和凭据后重试。（$detail）';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return '无法保存 $name。现有服务器保持不变。请检查设备存储空间后重试。（$detail）';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return '要移除 $name 吗？';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return '$name 已移除，但其连接未能正常关闭。连接其他服务器前，请重启应用。（$detail）';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return '无法移除 $name。已保留已保存的服务器和当前连接。请检查设备存储空间后重试。（$detail）';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return '已与 $host 配对，选自代码中的 $count 个地址。';
  }

  @override
  String e7SetupPaired(String host) {
    return '已与 $host 配对。';
  }

  @override
  String e7SetupStartFailed(String detail) {
    return '无法保存或启动本地设置：$detail';
  }

  @override
  String e7SetupRestartFailed(String detail) {
    return '无法重启本地服务器：$detail';
  }

  @override
  String e7SetupStopFailed(String detail) {
    return '无法停止本地服务器：$detail';
  }

  @override
  String e7SetupElapsedSeconds(int seconds) {
    return '已用时 $seconds 秒';
  }

  @override
  String e7SetupElapsedMinutes(int minutes, int seconds) {
    return '已用时 $minutes 分 $seconds 秒';
  }

  @override
  String e7SetupDeleteDisclosure(int queued, int drafts) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued 条排队中的提示词将被删除。',
      one: '1 条排队中的提示词将被删除。',
      zero: '',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts 条未发送的草稿将被删除。',
      one: '1 条未发送的草稿将被删除。',
      zero: '',
    );
    return '这会删除本设备为此服务器保存的全部内容：密码、所选模型和智能体、项目选择，以及桌面小组件中显示的对话。\n\n$_temp0 $_temp1\n\n服务器本身和你的 AI 提供商处不会删除任何内容。';
  }

  @override
  String e7SetupPairingFailed(String detail, String hint) {
    return '该配对码中没有任何地址响应：\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String get e7SetupPairNone => '这里没有配对码。请在服务器上运行“opencode2 pair”，然后扫描或复制其输出。';

  @override
  String get e7SetupPairLong =>
      '内容过长，不可能是配对码。请只复制“opencode2 pair”输出的那一行，或扫描其二维码。';

  @override
  String get e7SetupPairInvalid =>
      '这不是配对码。请在服务器上运行“opencode2 pair”，然后扫描或复制其输出。';

  @override
  String get e7SetupPairShape =>
      '该配对码格式不正确 — 它应是包含“urls”、“username”和“password”的 JSON 对象。';

  @override
  String get e7SetupPairNoUrls => '该配对码没有“urls”字段，因此没有可连接的地址。';

  @override
  String get e7SetupPairUrlsType => '该配对码的“urls”字段不是地址列表。';

  @override
  String get e7SetupPairTooMany => '该配对码列出的地址超过应用会尝试的数量。请将服务器绑定到单个网络接口，然后重新配对。';

  @override
  String get e7SetupPairAddressType => '该配对码中的地址不是文本。';

  @override
  String get e7SetupPairAddressLong => '该配对码中的地址过长，不可能是服务器 URL。';

  @override
  String get e7SetupPairAddressMissing =>
      '该配对码不含服务器地址。请确认服务器正在监听，然后再次运行“opencode2 pair”。';

  @override
  String get e7SetupPairUsernameType => '该配对码的“username”字段不是文本。';

  @override
  String get e7SetupPairPasswordMissing =>
      '该配对码没有“password”字段，可能被截断了 — 请扫描或复制完整的配对码。';

  @override
  String get e7SetupPairPasswordType => '该配对码的“password”字段不是文本。';

  @override
  String get e7SetupPairTestFailed => '连接测试在检查服务器之前就失败了。请尝试其他地址。';

  @override
  String get e7SetupNotOpenCode => '该地址的响应不像 OpenCode 服务器。请检查地址后重试。';

  @override
  String get e7SetupNoServerAnswer => '服务器没有响应。请检查它是否正在运行，以及地址是否正确。';

  @override
  String get e7SetupPairPasswordRejected => '密码被拒绝。请检查配对码后重试。';

  @override
  String get e7SetupPairAddressUnusable => '该配对码包含无法使用的服务器地址。';

  @override
  String get e7SetupInvalidAddress => '<无效地址>';

  @override
  String get e7SetupEnterUrl => '请输入服务器 URL。';

  @override
  String get e7SetupIncludeScheme => '请包含 https:// 或 http://。远程 HTTP 需要你的许可。';

  @override
  String get e7SetupCompleteUrl =>
      '请输入完整的服务器 URL，例如 https://server.example:4096。';

  @override
  String get e7SetupUrlScheme => '服务器 URL 必须使用 https:// 或 http://。';

  @override
  String get e7SetupTermuxUrlScheme =>
      '服务器 URL 必须使用 https://，本地 Termux 可用 http://。';

  @override
  String get e7SetupUrlCredentials => '不要在 URL 中包含凭据。请使用下方的字段。';

  @override
  String get e7SetupUrlQuery => '请从服务器 URL 中移除查询参数和片段。';

  @override
  String get e7SetupUrlPath => '请从服务器 URL 中移除路径，只输入其源地址。';

  @override
  String get e7SetupRequireHttps =>
      '密码只会通过 https:// 发送到其他电脑。请使用该电脑的 https:// 地址、使用配对码，或通过 Tailscale 连接。';

  @override
  String get e7SetupLocalHttp =>
      'http:// 地址仅适用于此手机或 192.168.x.x 这类私有网络地址。其他情况请使用配对码、其 https:// 地址，或通过 Tailscale 连接。';

  @override
  String get e7SetupRefused => '电脑拒绝了连接。请检查服务器是否在那里运行，以及地址和端口是否正确。';

  @override
  String get e7SetupTimeout => '连接超时。请检查地址，以及此手机能否访问该服务器。';

  @override
  String get e7SetupDns => '找不到该主机名。请检查地址拼写。';

  @override
  String get e7SetupCertificate => '该证书不受信任。请先核对其指纹，再决定是否信任。';

  @override
  String get e7SetupUnhealthy => '服务器有响应，但报告自身状态不健康。请查看其日志后重试。';

  @override
  String get e7SetupServerStarting => '服务器正在启动。请稍后再试。';

  @override
  String get e7SetupPasswordNeeded => '此服务器需要其 serve 密码。';

  @override
  String get e7SetupPasswordRejected =>
      '密码被拒绝。请从服务器输出中复制当前的“server password”那一行 — 除非设置了 OPENCODE_PASSWORD，否则每次重启都会变化。';

  @override
  String get e7SetupCredentialsRefused => '服务器拒绝了凭据。请检查用户名和密码。';

  @override
  String get e7SetupCodexUrl => '请输入 Codex 服务器 URL。';

  @override
  String get e7SetupCodexCompleteUrl => '请输入完整的 Codex 服务器 URL。';

  @override
  String get e7SetupCodexScheme => 'Codex 服务器 URL 必须使用 wss://，本地服务器可用 ws://。';

  @override
  String get e7SetupCodexCredentials => '不要在 Codex URL 中包含凭据。';

  @override
  String get e7SetupCodexQuery => '请从 Codex URL 中移除查询参数和片段。';

  @override
  String get e7SetupCodexPath => '请从 Codex 服务器 URL 中移除路径。';

  @override
  String get e7SetupCodexPlain => 'ws:// 仅适用于此手机上的服务器。其他电脑请使用 wss:// 地址。';

  @override
  String get e7SetupCodexDirectory => '请输入 Codex 项目的绝对路径。';

  @override
  String get e7SetupCodexToken => '请输入有效的 Codex 连接令牌。';

  @override
  String get e7SetupRefreshPackages => '正在刷新 Termux 软件包';

  @override
  String get e7SetupRepairPackages => '正在修复 Termux 软件包集';

  @override
  String get e7SetupInstallDependencies => '正在安装 Termux 依赖';

  @override
  String get e7SetupPrepareTermux => '正在准备 Termux';

  @override
  String get e7SetupInstallUbuntu => '正在安装 Ubuntu 环境';

  @override
  String get e7SetupRefreshModels => '正在刷新 OpenCode 模型目录';

  @override
  String get e7SetupStartLocalServer => '正在启动本地服务器';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode 已就绪';

  @override
  String get e7SetupPrepareRuntime => '正在准备所选的 OpenCode 运行时';

  @override
  String get e7SetupSwitchLocal => '正在切换托管的本地服务器';

  @override
  String get e7SetupCheckRestart => '重启前正在检查本地服务器';

  @override
  String get e7SetupStoppingLocal => '正在停止本地服务器';

  @override
  String get e7SetupStoppedLocal => '本地服务器已停止';

  @override
  String get e7SetupUnknownSetup => '未知的设置状态';

  @override
  String get e7SetupUnexpectedStop => '本地 OpenCode 服务器意外停止';

  @override
  String get e7SetupSetupInterrupted => '设置意外停止；详情请查看实时输出';

  @override
  String get e7SetupRecoveryDisabled => '自动恢复已关闭';

  @override
  String get e7SetupRecoveryWasDisabled => '自动恢复曾被关闭';

  @override
  String get e7SetupPortBusy => '本地服务器端口仍被占用；未启动替代进程';

  @override
  String get e7SetupCredentialMismatch => '已保存的服务器凭据与此运行时不一致；返回前请先恢复其原始保存的凭据';

  @override
  String get e7SetupUbuntuUnavailable => '托管的 Ubuntu 环境不可用';

  @override
  String get e7SetupRuntimeUnavailable => '所选的 OpenCode 命令不可用';

  @override
  String get e7SetupIdentityMismatch => '被跟踪的进程不是托管的 OpenCode 服务器';

  @override
  String get e7SetupPasswordMissing => '缺少本地服务器密码';

  @override
  String get e7SetupVersionMissing => 'OpenCode 已安装，但未报告版本';

  @override
  String get e7SetupModelsRefreshFailed => 'OpenCode 已更新，但无法刷新其模型目录';

  @override
  String get e7SetupStartupExited => 'OpenCode 服务器在启动期间退出';

  @override
  String get e7SetupReadinessTimeout => 'OpenCode 服务器未在 30 秒内完成认证并就绪';

  @override
  String get e7SetupUnreadableData => '无法读取 OpenCode 2 的数据位置记录';

  @override
  String get e7SetupUnreadablePrevious => '无法读取之前的运行时记录';

  @override
  String get e7SetupReadManagerFailed => '无法读取设置管理器状态';

  @override
  String get e7SetupMissingManager => '找不到设置管理器';

  @override
  String get e7SetupRemoveInterruptedFailed => '无法移除被中断的应用自有 Ubuntu 安装';

  @override
  String get e7SetupCheckStorageFailed => '设置前无法检查可用存储空间';

  @override
  String get e7SetupReadStorageFailed => '设置前无法读取可用存储空间';

  @override
  String get e7SetupRepositoryFailed => '无法选择 Termux 官方软件包仓库';

  @override
  String get e7SetupRepositoryRefreshFailed =>
      '无法刷新 packages.termux.dev；请检查网络后重试';

  @override
  String get e7SetupRepairFailed => '无法修复被中断的 Termux 软件包事务';

  @override
  String get e7SetupUpgradeFailed => '无法完成 Termux 软件包的安全升级';

  @override
  String get e7SetupDependenciesFailed => '无法安装 Termux 依赖';

  @override
  String get e7SetupDependenciesUnusable => '软件包修复后 Termux 依赖仍无法使用';

  @override
  String get e7SetupUbuntuUnusable => '现有的 Ubuntu 容器无法使用；设置不会删除它';

  @override
  String get e7SetupExtractionFailed => 'Ubuntu Base 解压后未生成可用的容器';

  @override
  String get e7SetupLockFailed => '设置管理器无法获取其启动锁';

  @override
  String get e7SetupSetupGroupFailed => '设置管理器未在隔离的进程组中启动';

  @override
  String get e7SetupSwitchGroupFailed => '切换管理器未在隔离的进程组中启动';

  @override
  String get e7SetupServerGroupFailed => '托管服务器未在隔离的进程组中启动';

  @override
  String get e7SetupRecordIdentityFailed => '无法记录托管服务器进程的标识';

  @override
  String e7SetupConnectingProfile(String name) {
    return '正在连接 $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return '正在重新连接（第 $attempt 次尝试）';
  }

  @override
  String get e7SetupOpeningWorkspace => '正在打开你保存的项目。';

  @override
  String get e7SetupWhatToCheck => '需要检查的内容';

  @override
  String get e7SetupUpdatePassword => '更新密码';

  @override
  String e7SetupLastSetupDetail(String detail) {
    return '上次设置的输出：$detail';
  }

  @override
  String e7SetupBridgeDetail(String detail) {
    return '桥接详情：$detail';
  }

  @override
  String e7SetupDiagnosticsUnavailable(String detail) {
    return '诊断信息不可用：$detail';
  }

  @override
  String e7SetupProbeHttp(String status) {
    return '该地址有响应，但不像 OpenCode 服务器（HTTP $status）。请检查 URL 是否指向 opencode serve。';
  }

  @override
  String e7SetupProbeError(String detail) {
    return '连接测试失败：$detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'OpenCode 服务器已退出（代码 $code）';
  }

  @override
  String get e7SetupCheckTermux => '在此手机上';

  @override
  String get e7SetupCommandFailed => 'Termux 命令执行失败。';

  @override
  String get e7SetupUnexpectedBridge => 'Termux 返回了意外的桥接响应。';

  @override
  String get e7SetupSetupQueued => '设置已排队';

  @override
  String get e7SetupNoSetup => '尚未开始任何设置';

  @override
  String get e7SetupManagerMissingAfterLaunch => '启动后找不到设置管理器';

  @override
  String get e7SetupBootstrapCleared => '已清除 bootstrap 状态';

  @override
  String get e7SetupInstallingBeta => '正在安装 OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => '认证失败';

  @override
  String get e7SetupUnknownVersion => '未知版本';

  @override
  String get e7ModelUiClose => '关闭模型选择器';

  @override
  String get e7ModelUiClearSearch => '清除模型搜索';

  @override
  String get e7ModelUiLoadFailed => '无法加载模型';

  @override
  String get e7ModelUiRetry => '重试';

  @override
  String get e7ModelUiBasicCatalog => '此服务器返回的是基础目录，没有能力和上下文的详细信息。';

  @override
  String get e7ModelUiEditFilters => '编辑模型筛选条件';

  @override
  String get e7ModelUiFilterModels => '筛选模型';

  @override
  String get e7ModelUiFiltered => '已筛选';

  @override
  String get e7ModelUiFilters => '筛选条件';

  @override
  String get e7ModelUiRefresh => '刷新模型';

  @override
  String get e7ModelUiAnyCapability => '任意能力';

  @override
  String get e7ModelUiFastModes => '快速模式';

  @override
  String get e7ModelUiReasoning => '推理';

  @override
  String get e7ModelUiLargestContext => '上下文最大';

  @override
  String get e7ModelUiNoneAvailable => '没有可用的模型';

  @override
  String get e7ModelUiFavoritesEmpty => '把常用模型放在这里';

  @override
  String get e7ModelUiRecentEmpty => '你的下一个选择会出现在这里';

  @override
  String get e7ModelUiNoMatches => '没有匹配的模型';

  @override
  String get e7ModelUiConfigureProvider => '请在 OpenCode 服务器上配置提供商，然后刷新。';

  @override
  String get e7ModelUiFavoritesHint => '点按模型旁的星标，即可在这里找到它。';

  @override
  String get e7ModelUiRecentHint => '你用过的模型会按最近使用顺序出现在这里。';

  @override
  String get e7ModelUiNoFastModes => '没有模型声明明确的快速或低强度模式。';

  @override
  String get e7ModelUiNoMatchesHint => '试试其他搜索词、服务商或能力筛选。';

  @override
  String get e7ModelUiClearFilters => '清除筛选';

  @override
  String get e7ModelUiBrowseAll => '浏览所有模型';

  @override
  String get e7ModelUiAgent => '智能体';

  @override
  String get e7ModelUiServerDefault => '服务器默认';

  @override
  String get e7ModelUiProvider => '提供商';

  @override
  String get e7ModelUiAllProviders => '所有服务商';

  @override
  String get e7ModelUiCurrent => '当前模型';

  @override
  String get e7ModelUiUnavailable => '不可用';

  @override
  String get e7ModelUiDeprecated => '已弃用';

  @override
  String get e7ModelUiPreview => '预览版';

  @override
  String get e7ModelUiFavoritesFailed => '无法保存收藏。请重试。';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return '使用 $model · $agent';
  }

  @override
  String get e7ModelUiUseSession => '用于此对话';

  @override
  String get e7ModelUiUseNewSessions => '用于新对话';

  @override
  String get e7ModelUiTools => '工具';

  @override
  String get e7ModelUiAttachments => '附件';

  @override
  String get e7ModelUiDefault => '默认';

  @override
  String get e7ModelUiSelectionGone => '此选项已不可用。请刷新模型后重试。';

  @override
  String get e7VoiceUiLocalInput => '本地语音输入';

  @override
  String get e7VoiceUiChooseModel => '选择多语言 Whisper INT8 模型';

  @override
  String get e7VoiceUiPrivacyDownload => '音频不会离开本机。';

  @override
  String get e7VoiceUiNoBuiltInMic =>
      'Android 报告没有内置麦克风。语音输入仍可通过有线或 USB 麦克风工作。';

  @override
  String get e7VoiceUiLanguage => '转写语言';

  @override
  String get e7VoiceUiVerifying => '正在校验已下载的模型';

  @override
  String get e7VoiceUiCancelDownload => '取消下载';

  @override
  String get e7VoiceUiNotNow => '暂不';

  @override
  String get e7VoiceUiUseModel => '使用模型';

  @override
  String get e7VoiceUiDownload => '下载';

  @override
  String get e7VoiceUiKeep => '保留';

  @override
  String get e7VoiceUiDelete => '删除';

  @override
  String get e7VoiceUiDefaultBadge => '默认';

  @override
  String get e7VoiceUiOptionalBadge => '可选';

  @override
  String get e7VoiceUiInstalledBadge => '已安装';

  @override
  String get e7VoiceUiNotInstalledBadge => '未安装';

  @override
  String get e7VoiceUiSetupBusy => '模型设置进行中，暂不可用';

  @override
  String get e7VoiceUiSelected => '已选择';

  @override
  String get e7VoiceUiSelectHint => '双击以选择';

  @override
  String get e7VoiceUiDefault => '默认';

  @override
  String get e7VoiceUiOptional => '可选';

  @override
  String get e7VoiceUiInstalled => '已安装';

  @override
  String get e7VoiceUiRedownload => '重新下载';

  @override
  String get e7VoiceUiOpenSettings => '打开应用设置';

  @override
  String get e7VoiceUiRetry => '重试';

  @override
  String get e7VoiceUiStartListening => '开始聆听';

  @override
  String get e7VoiceUiDraftReady => '转写已就绪，可查看';

  @override
  String get e7VoiceUiNeedsAttention => '语音输入需要处理';

  @override
  String get e7VoiceUiModelRequired => '需要本地模型';

  @override
  String e7ModelUiCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个模型',
      one: '1 个模型',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiContext(String count) {
    return '$count 上下文';
  }

  @override
  String e7ModelUiOutput(String count) {
    return '$count 输出';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return '收藏 $model';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return '从收藏中移除 $model';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · $effort 强度';
  }

  @override
  String get e7ModelUiLoading => '正在加载模型目录';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input 输入 · $output 输出 /1M';
  }

  @override
  String e7VoiceUiDownloadPercent(int percent) {
    return '正在下载语音模型 $percent%';
  }

  @override
  String e7VoiceUiDownloadProgress(String received, String total) {
    return '$received / $total';
  }

  @override
  String e7VoiceUiSetupFailed(String error) {
    return '模型设置失败：$error';
  }

  @override
  String e7VoiceUiDeletePack(String model) {
    return '删除 $model 语音模型？';
  }

  @override
  String e7VoiceUiDeleteDetail(String size) {
    return '这会从应用私有存储中移除 $size。你之后可以重新下载。';
  }

  @override
  String e7VoiceUiDownloadSize(String size) {
    return '$size 下载';
  }

  @override
  String e7VoiceUiPackSemantics(
    String model,
    String size,
    String badges,
    String description,
  ) {
    return '$model，$size，$badges。$description';
  }

  @override
  String get e7VoiceUiLicenses => '语音许可与来源';

  @override
  String get e7VoiceUiNoticesFailed => '无法加载语音许可。请重试。';

  @override
  String get e7VoiceUiAuto => '自动检测';

  @override
  String get e7VoiceUiEnglish => '英语';

  @override
  String get e7VoiceUiArabic => '阿拉伯语';

  @override
  String get e7VoiceUiBalanced => '均衡';

  @override
  String get e7VoiceUiBalancedDetail => '推荐的质量、存储与速度平衡方案。';

  @override
  String get e7VoiceUiAccurate => '高精度';

  @override
  String get e7VoiceUiAccurateDetail => '可选的最佳质量；需要明显更多内存。';

  @override
  String get e7VoiceUiCompact => '精简备选';

  @override
  String get e7VoiceUiCompactDetail => '最快、体积最小；在困难音频中精度较低。';

  @override
  String get e7VoiceUiUnsupportedAbi => '没有随附的语音运行时支持此设备的 ABI。';

  @override
  String get e7VoiceUiPermissionBlocked => '麦克风访问被阻止。请在 Android 应用设置中允许。';

  @override
  String get e7VoiceUiPermissionRequired => '本地语音输入需要麦克风权限。';

  @override
  String get e7VoiceUiDeviceUnavailable => '本地语音输入不可用。请停止播放、检查麦克风设置后重试。';

  @override
  String get e7VoiceUiInputUnavailable => '此平台不支持本地语音输入。';

  @override
  String get e7VoiceUiNoAudio => '未捕获到音频。';

  @override
  String get e7VoiceUiInterrupted => '录制被中断。';

  @override
  String get e7VoiceUiMicrophoneError => '麦克风报告了错误。请检查其设置后重试。';

  @override
  String get e7VoiceUiInputFailed => '语音输入未能完成。请重试。';

  @override
  String get e7VoiceUiTechnicalDetails => '技术详情';

  @override
  String get e7VoiceUiHttpsRequired => '语音模型只能通过 HTTPS 下载。';

  @override
  String get e7VoiceUiTransportClosed => '语音下载通道已关闭。';

  @override
  String get e7VoiceUiInvalidRedirect => '语音模型下载返回了无效的重定向。';

  @override
  String get e7VoiceUiUnsafeRedirect => '语音模型下载被重定向到非 HTTPS 地址。';

  @override
  String get e7VoiceUiNoResponse => '模型服务器未返回响应。';

  @override
  String get e7VoiceUiDownloadTimeout => '模型下载超时。请检查连接后重试。';

  @override
  String get e7VoiceUiChecksumFailed => '已下载的模型未通过校验和验证。请重新下载。';

  @override
  String get e7VoiceUiVerificationFailed => '模型未通过最终验证。请重新下载。';

  @override
  String get e7VoiceUiHttpFailed => '模型服务器拒绝了此次下载。请重试。';

  @override
  String get e7VoiceUiLengthFailed => '模型下载的大小异常。请重新下载。';

  @override
  String get e7VoiceUiIncomplete => '模型下载不完整。请重试。';

  @override
  String get e7VoiceUiDownloadFailed => '语音模型无法下载。请重试。';

  @override
  String e7VoiceUiMemory(String model, int required, int available) {
    return '$model 需要约 $required MB 内存的手机；本机只有 $available MB。';
  }

  @override
  String e7VoiceUiStorage(String model, String size) {
    return '$model 需要 $size 可用空间，已包含安全余量。';
  }

  @override
  String get e7ModelUiProviderFallback => '某个提供商';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first 和 $last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first 和 $last';
  }

  @override
  String get e7ModelUiListSeparator => '、';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已登录 $providers，但服务器尚未加载它们，因此其模型无法回答。',
      one: '已登录 $providers，但服务器尚未加载它，因此其模型无法回答。',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain => '无法访问 OpenCode。请重试。';

  @override
  String get approvalsUiMenu => '批准';

  @override
  String get approvalsUiInheritedFrom => '沿用自上级对话';

  @override
  String get approvalsUiFollowParent => '重新沿用上级设置';

  @override
  String get approvalsUiIndicatorOn => '正在自动批准';

  @override
  String approvalsUiAutoApproved(String action) {
    return '已自动批准 · $action';
  }

  @override
  String get approvalsUiFailedDetail => '自动批准失败。请查看此请求。';

  @override
  String approvalsUiSaveFailed(String error) {
    return '无法保存审批设置：$error';
  }

  @override
  String get approvalsUiIndicatorPaused => '自动批准已暂停';

  @override
  String get handoffUiComputerTitle => '在电脑上继续';

  @override
  String handoffUiComputerIntro(String binary) {
    return '在运行此服务器的电脑上的终端中执行此命令。它会在 $binary 界面中打开同一个对话。在你输入之前，不会发送任何内容。';
  }

  @override
  String get handoffUiComputerDirectoryNote =>
      '对话属于某个项目文件夹，因此命令会先切换到该对话所在的文件夹。';

  @override
  String handoffUiComputerVerify(String verified, String binary) {
    return '已针对 $verified 验证。如果你安装的版本不同，请查看 $binary --help 中的 --session 参数。';
  }

  @override
  String get handoffUiUnavailableDirectory =>
      '服务器未报告此对话所属的项目文件夹，因此没有可用于打开它的文件夹。请重新加载对话后重试。';

  @override
  String get handoffUiUnavailableWorkspace =>
      '此对话运行在云环境中。它的文件夹属于该环境的主机，普通终端命令无法打开它。请改用导出和导入对话。';

  @override
  String get handoffUiUnavailableReference => '此对话的引用无法安全地放入命令中。';

  @override
  String get handoffUiPhoneTitle => '在另一部手机上打开';

  @override
  String get handoffUiPhoneIntro =>
      '在另一部手机上用 OpenCode Mobile 扫描。该码只包含服务器和对话 ID。';

  @override
  String get handoffUiPhoneQrLabel => '可在另一部手机上打开此对话的二维码';

  @override
  String get handoffUiPhoneLinkLabel => '链接';

  @override
  String get handoffUiPhoneCopyLink => '复制链接';

  @override
  String get handoffUiPhoneUnavailable => '无法为此对话生成链接。请重新加载对话后重试。';

  @override
  String get handoffUiLinkServerMissing => '该对话所在的服务器尚未在此手机上保存。请先在此添加，然后再次扫描。';

  @override
  String get handoffUiLinkDismiss => '关闭';

  @override
  String get handoffUiLinkWaiting => '服务器连接后将打开对话…';

  @override
  String get handoffUiLinkReentry => '请重新输入此服务器的密码，然后再次扫描。';

  @override
  String get handoffUiLinkConnectionFailed => '无法连接到已保存的服务器。请在“服务器”中检查，然后再次扫描。';

  @override
  String get teamUiAccessControls => '决策与控制';

  @override
  String get teamUiAccessReadOnly => '只读';

  @override
  String get teamUiAddAddressHint => 'http://100.x.x.x:8373';

  @override
  String get teamUiAddAddressLabel => '地址';

  @override
  String get teamUiAddManually => '手动添加';

  @override
  String get teamUiAddSubmit => '测试并开启';

  @override
  String get teamUiAddTesting => '正在检查地址…';

  @override
  String get teamUiAddTitle => '添加 AI 团队主机';

  @override
  String get teamUiAddressRequired => '请输入主机地址。';

  @override
  String get teamUiChange => '更改';

  @override
  String get teamUiDisclaimerComputer => '速度取决于你的电脑；请保持其唤醒';

  @override
  String get teamUiDisclaimerPhone => '熄屏后 Android 可能会停止它；速度比电脑慢';

  @override
  String get teamUiEditorBody => '如果这台电脑运行 Gas City，应用可以自动发现它。';

  @override
  String teamUiEditorConfigured(String url) {
    return 'AI Team 主机：$url';
  }

  @override
  String get teamUiEditorTitle => 'AI Team（可选）';

  @override
  String get teamUiHostGuideIntro => '全部内容都留在你的 Tailscale 网络内；不会发布到互联网。';

  @override
  String get teamUiHostGuideStep1 =>
      '将 Gas City 的三个工具 gc、bd 和 dolt 安装到 PATH 中；完整指南列出了每个下载项及其校验和。然后确认三者都能被找到：';

  @override
  String get teamUiHostGuideStep2 =>
      '把完整指南中的团队文件保存到项目旁边的文件夹里。然后初始化团队并添加你的项目，先指定文件夹：';

  @override
  String get teamUiHostGuideStep3 => '启动团队并确认它能应答：';

  @override
  String get teamUiHostGuideStep4 =>
      '下载让本机通过 Tailscale 接入的前端，校验并启动它，在 --allow 之后填上你自己的 Tailscale 登录名。然后在此添加：电脑的 Tailscale 地址加命令中的端口，以及团队名称。';

  @override
  String get teamUiHostGuideTitle => '在你的电脑上运行 AI 团队';

  @override
  String get teamUiHostModeComputer => '电脑';

  @override
  String get teamUiHostModePhone => '本机';

  @override
  String get teamUiHow => '方式';

  @override
  String get teamUiKeep => '保留';

  @override
  String get teamUiLabelAccess => '访问权限';

  @override
  String get teamUiLabelAddress => '地址';

  @override
  String get teamUiLabelCity => 'City';

  @override
  String get teamUiLabelHost => '主机';

  @override
  String get teamUiLabelProvider => '提供商';

  @override
  String get teamUiLabelVersion => '版本';

  @override
  String get teamUiLearnHow => '了解方法';

  @override
  String get teamUiPluginsTitle => '插件';

  @override
  String get teamUiReadOnlyBody => '你可以从手机上观看这个团队。应答和干预需要在电脑上运行前端。';

  @override
  String get teamUiReasonCityNotRunning => '团队主机正在启动';

  @override
  String get teamUiReasonNotGasCity => '未找到 AI 团队';

  @override
  String get teamUiReasonPlainHttp => '地址不在 Tailscale 网络上';

  @override
  String get teamUiReasonReadFailed => '上次读取失败';

  @override
  String get teamUiReasonUnreachable => '主机不可达';

  @override
  String get teamUiRefresh => '刷新';

  @override
  String get teamUiRowConnecting => '开启 · 连接中…';

  @override
  String get teamUiRowNotAvailable => '此服务器上不可用';

  @override
  String teamUiRowNotAvailableReason(String reason) {
    return '此服务器上不可用 · $reason';
  }

  @override
  String get teamUiRowOff => '关闭';

  @override
  String teamUiRowOn(String server) {
    return '开启 · $server';
  }

  @override
  String teamUiRowOnReadOnly(String server) {
    return '开启 · $server · 仅查看';
  }

  @override
  String get teamUiRowReconnecting => '开启 · 重新连接中…';

  @override
  String get teamUiRowTitle => 'AI 团队 · Gas City';

  @override
  String teamUiRowUnreachable(String minutes) {
    return '开启 · 主机已 $minutes 分钟不可达';
  }

  @override
  String get teamUiTailnetRequired =>
      'AI 团队通过你的 Tailscale 网络或本设备运行。请使用电脑的 Tailscale 地址（100.x.x.x 或 name.ts.net）。';

  @override
  String get teamUiTechnicalDetails => '技术详情';

  @override
  String get teamUiTechnicalLastAnswer => '主机的最后一次应答';

  @override
  String get teamUiTermAgent => '智能体 · polecat';

  @override
  String get teamUiTermProject => '项目 · rig';

  @override
  String get teamUiTermRun => '运行 · convoy';

  @override
  String get teamUiTermTeam => '团队 · city';

  @override
  String get teamUiTermWork => '工作项 · bead';

  @override
  String get teamUiTermsHeading => '术语';

  @override
  String get teamUiTurnOffBody => '会从本机移除其卡片、待处理项和缓存的团队数据。主机上不会有任何变化。';

  @override
  String get teamUiTurnOffConfirm => '关闭 AI 团队';

  @override
  String teamUiTurnOffTitle(String server) {
    return '为 $server 关闭 AI 团队？';
  }

  @override
  String get teamUiVerdictCityNotRunning => '团队主机正在启动。请稍后再试。';

  @override
  String get teamUiVerdictNotGasCity => '此服务器尚未运行 AI 团队。请在电脑上设置一个 — 只需几分钟。';

  @override
  String get teamUiVerdictUnreachable =>
      '此地址没有应答。请检查地址，并确认电脑已唤醒且在你的 Tailscale 网络上。';

  @override
  String get teamUiVersionUnknown => '未知';

  @override
  String teamUiCardAgentsSummary(
    int total,
    int working,
    int waiting,
    int idle,
    int stopped,
  ) {
    return '$total 个智能体：$working 工作中，$waiting 等待中，$idle 空闲，$stopped 已停止';
  }

  @override
  String get teamUiCardEmptyHint => '目前请先在电脑上启动任务。';

  @override
  String get teamUiCardEmptyTitle => '没有最近的任务';

  @override
  String get teamUiCardErrorCityNotRunning => '团队主机正在启动。请稍后再试。';

  @override
  String get teamUiCardErrorNotGasCity => '此服务器尚未运行 AI 团队。请在电脑上设置一个 — 只需几分钟。';

  @override
  String get teamUiCardErrorPlainHttp =>
      'AI 团队通过你的 Tailscale 网络或本设备运行。请在电脑上使用 tailscale serve，然后重试。';

  @override
  String get teamUiCardErrorUnreachable =>
      '无法连接团队主机。AI 团队通过你的 Tailscale 网络或本设备运行。';

  @override
  String get teamUiStateUnreachableTitle => '无法连接团队主机';

  @override
  String get teamUiStateNotGasCityTitle => '此服务器上没有 AI 团队';

  @override
  String get teamUiStateStartingTitle => '团队主机正在启动';

  @override
  String get teamUiStatePlainHttpTitle => 'AI 团队无法使用此地址';

  @override
  String get teamUiStateNotAnsweringTitle => '团队没有应答';

  @override
  String get teamUiCardLoading => '正在连接团队主机…';

  @override
  String teamUiCardRefreshFailed(String time) {
    return '上次刷新失败 · 显示 $time 的数据';
  }

  @override
  String get teamUiCardRetry => '重试';

  @override
  String get teamUiCardRunStateBlocked => '已阻塞';

  @override
  String get teamUiCardRunStateCancelled => '已取消';

  @override
  String get teamUiCardRunStateCompleted => '完成';

  @override
  String get teamUiCardRunStateFailed => '失败';

  @override
  String get teamUiCardRunStatePlanning => '规划中';

  @override
  String get teamUiCardRunStateUnknown => '未知';

  @override
  String get teamUiCardRunStateWaiting => '等待工作者';

  @override
  String get teamUiCardRunStateWorking => '工作中';

  @override
  String get teamUiCardRunStateWaitingMerge => '审查中';

  @override
  String get teamUiCardRunStateMerged => '完成 · 已合并';

  @override
  String teamUiCardStale(String time) {
    return '显示 $time 的数据 · 主机不可达';
  }

  @override
  String get teamUiHomeAgentNoWork => '当前没有工作';

  @override
  String get teamUiHomeAgentStateBlocked => '已阻塞';

  @override
  String get teamUiHomeAgentStateCrashed => '已崩溃';

  @override
  String get teamUiHomeAgentStateIdle => '空闲';

  @override
  String get teamUiHomeAgentStateStopped => '已停止';

  @override
  String get teamUiHomeAgentStateUnknown => '未知';

  @override
  String get teamUiHomeAgentStateWaiting => '等待你处理';

  @override
  String get teamUiHomeAgentStateWorking => '工作中';

  @override
  String get teamUiHomeFilterActive => '进行中';

  @override
  String get teamUiHomeFilterAll => '全部';

  @override
  String get teamUiHomeFilterBlocked => '已阻塞';

  @override
  String get teamUiHomeFilterCompleted => '完成';

  @override
  String get teamUiHomeGateAnswerOnComputer => '请在电脑上应答。手机目前只能观看。';

  @override
  String get teamUiHomeGateAnswerOnPhone => '请在本机的主机中应答。应用目前只能观看。';

  @override
  String get teamUiHomeGateKindChoice => '决策';

  @override
  String get teamUiHomeGateKindConfirmation => '批准';

  @override
  String get teamUiHomeGateKindFreeText => '提问';

  @override
  String get teamUiHomeGateKindGateBead => '关卡';

  @override
  String get teamUiHomeGateKindReviewReady => '审查就绪';

  @override
  String get teamUiHomeGateKindRunFailed => '运行失败';

  @override
  String get teamUiHomeGateKindUnknown => '需要你处理';

  @override
  String teamUiHomeGateLinkAgent(String name) {
    return '智能体 $name';
  }

  @override
  String teamUiHomeGateLinkRun(String title) {
    return '任务 $title';
  }

  @override
  String teamUiHomeGateLinkWork(String title) {
    return '工作项 $title';
  }

  @override
  String get teamUiHomeGateOptions => '选项';

  @override
  String get teamUiHomeHostRawHeading => '原始值';

  @override
  String get teamUiHomeRunNeedsYou => '需要你处理';

  @override
  String teamUiHomeRunProgress(int done, int total) {
    return '已完成 $done / $total';
  }

  @override
  String get teamUiHomeRunsEmptyFiltered => '没有匹配的任务。';

  @override
  String get teamUiHomeRunsEmptyHint => '请尝试其他筛选条件，或清除搜索。';

  @override
  String get teamUiHomeSearchHint => '搜索任务';

  @override
  String get teamUiHomeTitle => 'AI 团队';

  @override
  String get teamUiRunBack => '返回';

  @override
  String teamUiRunBlockedByDeps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '等待另外 $count 项步骤',
      one: '等待另一项步骤',
    );
    return '$_temp0';
  }

  @override
  String teamUiRunElapsedDays(int count) {
    return '$count 天';
  }

  @override
  String teamUiRunElapsedHours(int hours, int minutes) {
    return '$hours 小时 $minutes 分钟';
  }

  @override
  String teamUiRunElapsedMinutes(int count) {
    return '$count 分钟';
  }

  @override
  String teamUiRunSinceHandoff(String elapsed) {
    return '交接后已 $elapsed';
  }

  @override
  String get teamUiRunLabelFormula => 'Formula';

  @override
  String get teamUiRunLabelId => '运行 ID';

  @override
  String get teamUiRunLabelKind => '类型';

  @override
  String get teamUiRunLabelLastError => '最后一次错误';

  @override
  String get teamUiRunLabelProject => '项目';

  @override
  String get teamUiRunLabelRawState => '提供商状态';

  @override
  String get teamUiRunLabelStarted => '开始于';

  @override
  String get teamUiRunLabelTrackedWork => '跟踪的工作';

  @override
  String get teamUiRunLabelUpdated => '更新于';

  @override
  String get teamUiRunMissingHint => '它可能已被关闭或移除。请刷新后再次检查。';

  @override
  String get teamUiRunMissingTitle => '此任务已不在主机上';

  @override
  String get teamUiRunTermBatch => '任务 · convoy';

  @override
  String get teamUiRunTermFormula => '任务 · formula';

  @override
  String get teamUiRunTermUnknown => '任务';

  @override
  String teamUiRunTimelineAgentStopped(String name) {
    return '$name 已停止';
  }

  @override
  String teamUiRunTimelineAgentWoke(String name) {
    return '$name 已启动';
  }

  @override
  String teamUiRunTimelineGateOpened(String title) {
    return '需要你处理：$title';
  }

  @override
  String teamUiRunTimelineGateResolved(String title) {
    return '已答复：$title';
  }

  @override
  String teamUiRunTimelineRunChanged(String state) {
    return '运行状态现为 $state';
  }

  @override
  String teamUiRunTimelineWorkClosed(String title) {
    return '$title 已关闭';
  }

  @override
  String teamUiRunTimelineWorkCreated(String title) {
    return '$title 已添加';
  }

  @override
  String teamUiRunTimelineWorkUpdated(String title) {
    return '$title 已更新';
  }

  @override
  String teamUiAgentContextSemantics(int percent) {
    return '已使用上下文 $percent%';
  }

  @override
  String teamUiAgentContextShort(int percent) {
    return '上下文 $percent%';
  }

  @override
  String get teamUiAgentLabelBranch => '分支';

  @override
  String get teamUiAgentLabelHarness => '运行时';

  @override
  String get teamUiAgentLabelModel => '模型';

  @override
  String get teamUiAgentLabelPack => 'Pack';

  @override
  String get teamUiAgentLabelPool => '池';

  @override
  String get teamUiAgentLabelSessionAge => '会话时长';

  @override
  String get teamUiAgentLabelSessionId => '会话';

  @override
  String get teamUiAgentLabelSessionName => '会话名称';

  @override
  String get teamUiAgentLabelWorkDir => '工作目录';

  @override
  String get teamUiAgentMissingHint => '它可能已被回收。请刷新后检查。';

  @override
  String get teamUiAgentMissingTitle => '此智能体已不在主机上';

  @override
  String get teamUiAgentNeedsYou => '需要你处理';

  @override
  String get teamUiAgentOutputConnecting => '正在连接会话…';

  @override
  String get teamUiAgentOutputCopy => '复制输出';

  @override
  String get teamUiAgentOutputEnded => '会话已结束 · 输出已不在主机上';

  @override
  String get teamUiAgentOutputJump => '跳到最新';

  @override
  String get teamUiAgentOutputLive => '实时';

  @override
  String get teamUiAgentOutputTitle => '实时输出';

  @override
  String get teamUiAgentOutputUnavailable => '此智能体没有可用的实时输出';

  @override
  String get teamUiAgentRecyclingSoon => '即将回收 · 上下文接近用满';

  @override
  String teamUiAgentSessionAge(String age) {
    return '会话 $age';
  }

  @override
  String teamUiAgentTermSession(String id) {
    return '智能体 · 会话 $id';
  }

  @override
  String get teamUiAgentValueUnknown => '未报告';

  @override
  String get teamUiWorkLabelAssignee => '承办人';

  @override
  String get teamUiWorkLabelClosedReason => '关闭原因';

  @override
  String get teamUiWorkLabelDependsOn => '依赖于（ID）';

  @override
  String get teamUiWorkLabelId => '工作项 ID';

  @override
  String get teamUiWorkLabelLabels => '标签';

  @override
  String get teamUiWorkLabelParent => '父项';

  @override
  String get teamUiWorkLabelProject => '项目';

  @override
  String get teamUiWorkLabelRawState => '提供商状态';

  @override
  String get teamUiWorkLabelRun => '运行 ID';

  @override
  String get teamUiWorkLabelSession => '会话 ID';

  @override
  String get teamUiWorkLabelSessionName => '会话名称';

  @override
  String get teamUiWorkLabelType => '类型';

  @override
  String get teamUiWorkOwnerNone => '未指派';

  @override
  String get teamUiWorkSheetBlocking => '阻塞';

  @override
  String get teamUiWorkSheetBranch => '分支';

  @override
  String get teamUiWorkSheetClosed => '已关闭';

  @override
  String get teamUiWorkSheetCreated => '已创建';

  @override
  String get teamUiWorkSheetDependencies => '依赖于';

  @override
  String get teamUiWorkSheetDescription => '描述';

  @override
  String get teamUiWorkSheetMissing => '此工作项已不在主机上。';

  @override
  String get teamUiWorkSheetNoTimestamps => '主机未发送任何时间戳。';

  @override
  String get teamUiWorkSheetOpenSession => '打开会话';

  @override
  String get teamUiWorkSheetOutput => '输出';

  @override
  String teamUiWorkSheetStamp(String date, String clock, String age) {
    return '$date · $clock（$age）';
  }

  @override
  String get teamUiWorkSheetTarget => '合并目标';

  @override
  String get teamUiWorkSheetTimestamps => '时间戳';

  @override
  String get teamUiWorkSheetUpdated => '更新于';

  @override
  String get teamUiWorkSheetValidation => '校验';

  @override
  String get teamUiWorkSheetValidationFailed => '失败';

  @override
  String get teamUiWorkSheetValidationPassed => '通过';

  @override
  String get teamUiWorkSheetValidationUnknown => '已记录结果';

  @override
  String get teamUiWorkSheetWorktree => '工作树';

  @override
  String get teamUiWorkStateBlocked => '已阻塞';

  @override
  String get teamUiWorkStateCancelled => '已取消';

  @override
  String get teamUiWorkStateCompleted => '完成';

  @override
  String get teamUiWorkStateFailed => '失败';

  @override
  String get teamUiWorkStateNeedsInput => '需要输入';

  @override
  String get teamUiWorkStateQueued => '排队中';

  @override
  String get teamUiWorkStateReady => '就绪';

  @override
  String get teamUiWorkStateReview => '审查';

  @override
  String get teamUiWorkStateUnknown => '未知';

  @override
  String get teamUiWorkStateWaiting => '等待中';

  @override
  String get teamUiWorkStateWorking => '工作中';

  @override
  String teamUiUsageCostEstimated(String cost) {
    return '$cost 估算';
  }

  @override
  String teamUiUsageTokens(String count) {
    return '$count tokens';
  }

  @override
  String get teamUiGateAnswerOnHost => '请在主机上应答。手机目前只能观看。';

  @override
  String get teamUiGateAnswerOnHostPhone => '请在本机的主机中应答。应用目前只能观看。';

  @override
  String get teamUiGateCloseOnHost => '请在主机上关闭。手机目前只能观看。';

  @override
  String get teamUiGateCloseOnHostPhone => '请在本机的主机中关闭。应用目前只能观看。';

  @override
  String get teamUiGateDestructive => '破坏性操作';

  @override
  String get teamUiGateFailureActionAgent => '在主机上重启智能体；它会重新接手该工作项。';

  @override
  String get teamUiGateFailureActionAuthentication =>
      '在主机上重新登录（提供商密钥或 token），然后重试该运行。';

  @override
  String get teamUiGateFailureActionContext => '在主机上用全新的上下文重启智能体；它会从该工作项继续。';

  @override
  String get teamUiGateFailureActionDependency => '在主机上安装或更新缺失的依赖，然后重试该运行。';

  @override
  String get teamUiGateFailureActionExecution => '在主机上查看步骤日志，修正命令，然后重试该运行。';

  @override
  String get teamUiGateFailureActionInfrastructure => '检查主机及其服务，然后重试该运行。';

  @override
  String get teamUiGateFailureActionMergeConflict => '在主机上解决工作树中的冲突，然后重试该运行。';

  @override
  String get teamUiGateFailureActionTest => '在主机上修复失败的测试，然后重试该运行。';

  @override
  String get teamUiGateFailureActionUnknown => '在主机上查看这个错误并在那里决定；手机暂时无法处理。';

  @override
  String get teamUiGateFailureAffectedNone => '此运行没有进行中的工作项。';

  @override
  String get teamUiGateFailureAffectedWork => '受影响的工作';

  @override
  String get teamUiGateFailureClassAgent => '智能体';

  @override
  String get teamUiGateFailureClassAuthentication => '认证';

  @override
  String get teamUiGateFailureClassContext => '上下文';

  @override
  String get teamUiGateFailureClassDependency => '依赖';

  @override
  String get teamUiGateFailureClassExecution => '执行';

  @override
  String get teamUiGateFailureClassInfrastructure => '基础设施';

  @override
  String get teamUiGateFailureClassMergeConflict => '合并冲突';

  @override
  String get teamUiGateFailureClassTest => '测试';

  @override
  String get teamUiGateFailureClassUnknown => '未知';

  @override
  String get teamUiGateFailureClassification => '出了什么问题';

  @override
  String get teamUiGateFailureErrorNone => '主机未发送错误文本。';

  @override
  String get teamUiGateGone => '这已不再等待你处理；它已在主机上被答复或关闭。';

  @override
  String get teamUiGateLabelKind => '提供商类型';

  @override
  String get teamUiGateLabelRequestId => '请求 ID';

  @override
  String get teamUiGateLabelRunId => '运行 ID';

  @override
  String get teamUiGateLabelSessionId => '会话 ID';

  @override
  String get teamUiGateLabelWorkId => '工作项 ID';

  @override
  String get teamUiGateNoDescription => '主机未发送描述。';

  @override
  String get teamUiGateReviewOnHost => '请在主机上审查。手机目前只能观看。';

  @override
  String get teamUiGateReviewOnHostPhone => '请在本机的主机中审查。应用目前只能观看。';

  @override
  String get teamUiGateUnblocks => '解除阻塞';

  @override
  String get teamUiGateUnblocksNone => '尚无其他内容等待它。';

  @override
  String get teamUiHostKindDesktop => '台式电脑';

  @override
  String get teamUiHostKindDisclaimerLaptop => '休眠和合盖会暂停团队；唤醒后继续运行';

  @override
  String get teamUiHostKindDisclaimerWsl =>
      '休眠和合盖会暂停团队；唤醒后继续运行。WSL 在最后一个终端关闭时也会停止。';

  @override
  String get teamUiHostKindHint => '仅更改与团队一同显示的提醒。';

  @override
  String get teamUiHostKindLabel => '电脑类型';

  @override
  String get teamUiHostKindLaptop => '笔记本电脑';

  @override
  String get teamUiHostKindWsl => 'Windows（WSL）';

  @override
  String get teamUiReceiptSent => '已发送 · 等待主机确认';

  @override
  String get teamUiReceiptAnswered => '已答复';

  @override
  String get teamUiReceiptUnconfirmed => '已发送，未确认 — 重新发送前请在主机上核实';

  @override
  String get teamUiGateAnswerSend => '发送';

  @override
  String get teamUiGateAnswerApprove => '批准';

  @override
  String get teamUiGateAnswerDeny => '拒绝';

  @override
  String get teamUiGateAnswerMarkDone => '标记为完成';

  @override
  String get teamUiGateAnswerHint => '输入你的答复';

  @override
  String teamUiGateAnswerRunRetry(String work, String agent) {
    return '再次将$work发送给$agent';
  }

  @override
  String get teamUiGateAnswerRunLogs => '观察该智能体';

  @override
  String get teamUiGateAnswerRunCancel => '停止工作';

  @override
  String teamUiGateAnswerRejected(String message) {
    return '未被接受：$message';
  }

  @override
  String get teamUiGateAnswerRejectedNoMessage => '主机未接受此答复。';

  @override
  String get teamUiGateAnswerConfirmApproveTitle => '批准此破坏性操作？';

  @override
  String get teamUiGateAnswerConfirmApproveBody => '主机将其标记为破坏性操作。从手机上无法撤销。';

  @override
  String get teamUiGateAnswerConfirmCancelRunTitle => '停止此工作？';

  @override
  String get teamUiGateAnswerConfirmCancelRunBody => '运行将停止，其进行中的工作保持原状。';

  @override
  String get teamUiControlMessage => '消息';

  @override
  String get teamUiControlNudge => '催促';

  @override
  String get teamUiControlPause => '暂停';

  @override
  String get teamUiControlResume => '恢复';

  @override
  String get teamUiControlStop => '停止';

  @override
  String get teamUiControlRestart => '重启';

  @override
  String get teamUiControlReassign => '重新指派工作…';

  @override
  String get teamUiControlCreateWork => '已向智能体发送任务';

  @override
  String teamUiControlStopConfirmTitle(String agent) {
    return '停止 $agent？';
  }

  @override
  String get teamUiControlStopConfirmBody => '其会话立即结束。工作保持原状；主机稍后可以再次唤醒它。';

  @override
  String teamUiControlRestartConfirmTitle(String agent) {
    return '重启 $agent？';
  }

  @override
  String get teamUiControlRestartConfirmBody =>
      '其会话将停止并重新开始。智能体会丢失上下文中的内容，并从主机重新接手其工作。';

  @override
  String get teamUiControlKeep => '继续运行';

  @override
  String get teamUiControlReceiptSent => '已发送';

  @override
  String get teamUiControlReceiptConfirmed => '已确认';

  @override
  String get teamUiControlReceiptUnconfirmed => '未确认';

  @override
  String get teamUiControlReceiptRefused => '被拒绝';

  @override
  String teamUiControlReceiptLine(String control, String state) {
    return '$control · $state';
  }

  @override
  String get teamUiControlCancelRun => '停止运行';

  @override
  String get teamUiStartRunFab => '给团队一个任务';

  @override
  String get teamUiStartRunTitle => '给团队一个任务';

  @override
  String get teamUiStartRunObjectiveLabel => '目标';

  @override
  String get teamUiStartRunObjectiveHint => '团队应达成什么？用你自己的话描述一个结果。';

  @override
  String get teamUiStartRunObjectiveEmpty => '请先写下目标。';

  @override
  String get teamUiStartRunProjectLabel => '项目';

  @override
  String get teamUiStartRunProjectAny => '让规划器决定';

  @override
  String get teamUiStartRunSupervisionLabel => '监督级别';

  @override
  String get teamUiStartRunSupervisionHigh => '高';

  @override
  String get teamUiStartRunSupervisionHighHint =>
      '团队在每个决策前、在会改变状态的测试前、以及在任何合并前都会询问。';

  @override
  String get teamUiStartRunSupervisionBalanced => '均衡';

  @override
  String get teamUiStartRunSupervisionBalancedHint =>
      '团队自行决定日常事务，在合并前、失败时和设计选择上询问。';

  @override
  String get teamUiStartRunSupervisionAutonomous => '自主';

  @override
  String get teamUiStartRunSupervisionAutonomousHint =>
      '团队在主机的边界内自主完成工作，仅在无法继续时询问。';

  @override
  String get teamUiStartRunPlannerLabel => '规划器';

  @override
  String get teamUiStartRunPlannerMayor => 'Mayor';

  @override
  String teamUiStartRunSend(String planner) {
    return '发送给$planner';
  }

  @override
  String get teamUiStartRunHostGuide => '主机指南';

  @override
  String get teamUiStartRunWaking => '正在唤醒规划器…';

  @override
  String get teamUiStartRunDirectIntro => '此主机上的规划器已关闭。请直接把单个任务交给该项目的智能体。';

  @override
  String get teamUiStartRunDirectTitle => '任务';

  @override
  String get teamUiStartRunDirectTitleHint => '一行说明：智能体该做什么？';

  @override
  String get teamUiStartRunDirectTitleRequired => '请先写下任务。';

  @override
  String get teamUiStartRunDirectDetails => '详情（可选）';

  @override
  String get teamUiStartRunDirectSend => '发送给智能体';

  @override
  String get teamUiStartRunRefused => 'The host did not accept this task.';

  @override
  String get teamUiMergeTitleNotReady => '尚不可合并';

  @override
  String teamUiMergeRequest(String id) {
    return '合并请求 $id';
  }

  @override
  String get teamUiMergeLineWork => '工作项';

  @override
  String get teamUiMergeLineTests => '测试';

  @override
  String get teamUiMergeLineBuild => '构建';

  @override
  String get teamUiMergeLineReview => '审查';

  @override
  String get teamUiMergeLineConflicts => '无冲突';

  @override
  String get teamUiMergeLineAcceptance => '验收标准';

  @override
  String teamUiMergeFiles(int files, int additions, int deletions) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files 个文件 · +$additions / −$deletions',
      one: '1 个文件 · +$additions / −$deletions',
      zero: '没有文件更改',
    );
    return '$_temp0';
  }

  @override
  String get teamUiMergeReviewChanges => '审查更改';

  @override
  String get teamUiMergeApprove => '批准请求';

  @override
  String teamUiMergeApprovedBy(String login) {
    return '由 $login 批准';
  }

  @override
  String get teamUiMergeMerge => '合并';

  @override
  String teamUiMergeConfirmAction(String branch) {
    return '合并到 $branch';
  }

  @override
  String teamUiMergeDisabledReason(String line, String detail) {
    return '合并已关闭：$line — $detail';
  }

  @override
  String teamUiMergeBoundary(String text) {
    return '主机边界：$text';
  }

  @override
  String get teamUiMergeRefused =>
      'The host did not accept this, so nothing was merged.';

  @override
  String teamUiMergeMerged(String branch, String commit) {
    return '已合并到 $branch · $commit';
  }

  @override
  String teamUiMergeAlready(String branch) {
    return '已在 $branch 上';
  }

  @override
  String teamUiMergeUnavailable(String reason) {
    return '合并就绪状态不可用：$reason';
  }

  @override
  String get teamUiMergeNoRoles => '此运行在主机上没有合并角色';

  @override
  String get teamUiMergeLoading => '正在检查合并就绪状态…';

  @override
  String get teamUiMergePending => '正在主机上运行';

  @override
  String get teamUiMergeChangesTitle => '更改';

  @override
  String get teamUiMergeChangesEmpty => '主机未报告任何文件更改';

  @override
  String get teamUiMergeChangesWork => '工作项';

  @override
  String get teamUiMergeSent => '已发送 · 等待主机确认';

  @override
  String get teamUiMergeApproveConfirmed => '批准已记录';

  @override
  String teamUiPolicySupervision(String level) {
    return '监督 · $level';
  }

  @override
  String get teamUiPolicyBoundariesLabel => '边界';

  @override
  String get teamUiPolicyBoundariesNone => '主机未设置边界';

  @override
  String get teamUiPolicyFromHost => '在主机上设置 · 此处只读';

  @override
  String teamUiPolicyRig(String rig) {
    return '针对$rig';
  }

  @override
  String teamUiPolicySemantics(String level, String boundaries) {
    return '监督级别 $level。边界：$boundaries';
  }

  @override
  String get teamUiRunLabelRawTitle => '提供商标题';

  @override
  String get termuxStorageTitle => '本机存储';

  @override
  String termuxStorageRowUsed(String size) {
    return '已使用 $size';
  }

  @override
  String get termuxStorageRowNotScanned => '尚未扫描';

  @override
  String get termuxStorageRowScanning => '正在计算…';

  @override
  String get termuxStorageScanAction => '扫描存储';

  @override
  String get termuxStorageRescanAction => '再次扫描';

  @override
  String get termuxStorageIntro =>
      '查看 Termux 在本机占用的空间。此处只能清理会自行重建的缓存；你的项目、登录信息和对话都会保留。';

  @override
  String get termuxStorageScanning => '正在计算存储';

  @override
  String get termuxStorageScanningDetail => '大型缓存需要一两分钟。你可以离开此页面，扫描会继续。';

  @override
  String get termuxStorageCancel => '停止扫描';

  @override
  String get termuxStorageCancelled => '扫描已停止';

  @override
  String get termuxStorageFailed => '扫描未完成。请重试。';

  @override
  String termuxStorageTotal(String size) {
    return 'Termux 中已计算 $size';
  }

  @override
  String termuxStorageDeletableTotal(String size) {
    return '可清理 $size';
  }

  @override
  String get termuxStorageScannedJustNow => '刚刚扫描';

  @override
  String termuxStorageScannedMinutesAgo(int minutes) {
    return '$minutes 分钟前扫描';
  }

  @override
  String termuxStorageScannedHoursAgo(int hours) {
    return '$hours 小时前扫描';
  }

  @override
  String get termuxStorageCatBuildCaches => '构建缓存';

  @override
  String get termuxStorageNoteBuildCaches =>
      '仅包括 Gradle 缓存和 npm 下载的内容缓存。可能需要重新下载，因此离线构建会受影响。清理前请停止构建和软件包安装。';

  @override
  String get termuxStorageCatAgentScratch => '智能体临时文件';

  @override
  String get termuxStorageNoteAgentScratch =>
      '临时文件夹可能包含未完成的工作或其他工具使用的文件。此处仅显示其大小，无法在此移除。';

  @override
  String get termuxStorageCatProjectBuildOutputs => '名称与构建相关的文件夹';

  @override
  String get termuxStorageNoteProjectBuildOutputs =>
      '名为 build、.dart_tool、node_modules 或 target 的文件夹也可能包含你的文件。仅凭名称无法证明可以丢弃，因此无法在此移除。';

  @override
  String get termuxStorageCatToolchains => '工具链';

  @override
  String get termuxStorageNoteToolchains =>
      'Android SDK、Java 和 Flutter 的安装。它们可能支撑其他项目，无法在此移除。';

  @override
  String get termuxStorageCatAiTeam => 'AI 团队';

  @override
  String get termuxStorageNoteAiTeam =>
      '团队文件夹、数据库和工具可能包含你需要保留的工作。即使团队已停止，也无法在此移除。';

  @override
  String get termuxStorageCatOpenCode => 'OpenCode 本身';

  @override
  String get termuxStorageNoteOpenCode => '服务器、其登录信息和对话历史。对话有单独的页面。';

  @override
  String get termuxStorageCatProjects => '项目（你的文件）';

  @override
  String get termuxStorageNoteProjects => '列出是为了让你看到大小。绝不从此处移除。';

  @override
  String get termuxStorageWillRemove => '“清理”会移除什么';

  @override
  String get termuxStorageNothingHere => '这里没有内容';

  @override
  String termuxStorageCleanConfirmTitle(String size, String category) {
    return '删除$category的 $size？';
  }

  @override
  String get termuxStorageCleanConfirmBody =>
      '仅删除列出的 Gradle 和 npm 内容缓存？可能需要重新下载，离线构建会受影响。请先停止构建和软件包安装。之后请再次扫描以更新计算结果。';

  @override
  String termuxStorageCleanConfirm(String size) {
    return '删除 $size';
  }

  @override
  String get termuxStorageKeep => '保留';

  @override
  String get termuxStorageCleaning => '正在删除…';

  @override
  String termuxStorageFreed(String size) {
    return '已移除 $size';
  }

  @override
  String get termuxStorageFreedNothing => '没有移除任何内容';

  @override
  String termuxStorageInUse(String process) {
    return '正被$process使用。请先在“本机运行中”停止它。';
  }

  @override
  String termuxStorageRefusedCount(int count) {
    return '$count 个路径未予处理';
  }

  @override
  String termuxStorageProjectBuild(String size, String build) {
    return '$size · 构建相关文件夹中 $build';
  }

  @override
  String get termuxStorageOpenRunning => '打开“本机运行中”';

  @override
  String termuxStorageBytesGb(String value) {
    return '$value GB';
  }

  @override
  String termuxStorageBytesMb(String value) {
    return '$value MB';
  }

  @override
  String termuxStorageBytesKb(String value) {
    return '$value KB';
  }

  @override
  String termuxStorageBytesB(String value) {
    return '$value B';
  }

  @override
  String get termuxProcsTitle => '本机运行中';

  @override
  String get termuxProcsRowLoading => '正在检查…';

  @override
  String get termuxProcsAutoRefresh => '打开期间每 10 秒刷新';

  @override
  String termuxProcsStopSemantics(String name) {
    return '停止 $name';
  }

  @override
  String termuxProcsStopOneTitle(String name) {
    return '停止 $name？';
  }

  @override
  String get termuxProcsStopOneBody => '会先礼貌地停止，5 秒后强制结束。';

  @override
  String get termuxProcsProtected => '受保护 · 请从“本机”控制';

  @override
  String termuxProcsOrphanParentGone(String elapsed) {
    return '其父进程已消失 · 已运行 $elapsed';
  }

  @override
  String termuxProcsOrphanCpu(String cpu) {
    return '占用 $cpu CPU，无归属';
  }

  @override
  String get termuxProcsStopping => '正在停止…';

  @override
  String termuxProcsStopped(int count) {
    return '已停止 $count 个';
  }

  @override
  String termuxProcsStoppedForced(int count, int forced) {
    return '已停止 $count 个（$forced 个需强制结束）';
  }

  @override
  String termuxProcsRemaining(int count) {
    return '$count 个未能停止';
  }

  @override
  String termuxProcsRefused(int count) {
    return '$count 个受保护，未停止';
  }

  @override
  String get termuxProcsEmpty => '手机服务器中没有正在运行的内容';

  @override
  String get termuxProcsCommand => '命令';

  @override
  String get termuxProcsFolder => '文件夹';

  @override
  String termuxProcsDurationSeconds(int seconds) {
    return '$seconds 秒';
  }

  @override
  String termuxProcsDurationMinutes(int minutes) {
    return '$minutes 分钟';
  }

  @override
  String termuxProcsDurationHours(int hours, int minutes) {
    return '$hours 小时 $minutes 分钟';
  }

  @override
  String termuxProcsMemoryMb(int mb) {
    return '$mb MB';
  }

  @override
  String get teamUiPhoneOptionalTag => '可选 · 实验性';

  @override
  String get teamUiPhoneChooseProjectTitle => '选择一个项目';

  @override
  String get teamUiPhoneContinue => '继续';

  @override
  String get teamUiPhoneSuccessTitle => 'AI 团队已在本机运行';

  @override
  String get teamUiPhoneRetry => '重试';

  @override
  String teamUiPhoneFailedChecksum(String name) {
    return '下载的 $name 与此构建固定的校验和不符，因此未安装。其中任何内容都未保留；请检查网络后重试。';
  }

  @override
  String get teamUiPhoneFailedUnsupportedArch => '本机的处理器不是团队运行时所需的 64 位 ARM。';

  @override
  String get teamUiPhoneFailedDownload => '下载未完成。请检查连接后重试。';

  @override
  String teamUiPhoneFailedDownloadDns(String host) {
    return '手机无法找到下载服务器 $host。请确认手机已联网，且没有私有 DNS 或广告拦截器拦截，然后重试。';
  }

  @override
  String teamUiPhoneFailedDownloadConnect(String host) {
    return '手机无法连接下载服务器 $host。请检查连接后重试；下载会从中断处继续。';
  }

  @override
  String teamUiPhoneFailedDownloadTimeout(String host) {
    return '下载服务器 $host 应答超时。请在更稳定的网络下重试；下载会从中断处继续。';
  }

  @override
  String teamUiPhoneFailedDownloadTls(String host) {
    return '无法与 $host 建立安全连接。请确认手机的日期和时间正确，且没有代理干扰，然后重试。';
  }

  @override
  String teamUiPhoneFailedDownloadHttp(String host, String code) {
    return '下载服务器 $host 拒绝了该文件（HTTP $code）。请稍后重试；若持续出现，说明此应用版本的 AI 团队下载暂不可用。';
  }

  @override
  String teamUiPhoneFailedDownloadInterrupted(String host) {
    return '下载过程中与 $host 的连接中断。请重试；下载会从中断处继续。';
  }

  @override
  String get teamUiPhoneFailedDownloadWrite => '下载无法保存到本机。请腾出空间后重试。';

  @override
  String teamUiPhoneFailedDownloadOther(String host, String code) {
    return '从 $host 下载失败（错误 $code）。请检查连接后重试。';
  }

  @override
  String get teamUiPhoneFailedPackages =>
      'Ubuntu 无法安装前置依赖（tmux、jq、lsof、procps）。下方输出说明了是哪一个。';

  @override
  String get teamUiPhoneFailedProject => '项目文件夹不存在，或不是 git 仓库。';

  @override
  String get teamUiPhoneFailedCity => 'Gas City 无法创建 city。下方输出说明了原因。';

  @override
  String get teamUiPhoneFailedSupervisorExited => '监督进程启动后立即停止。下方输出说明了原因。';

  @override
  String teamUiPhoneFailedHealth(String url) {
    return '监督进程已启动，但从未在 $url 上应答。';
  }

  @override
  String get teamUiPhoneFailedInterrupted =>
      '设置在完成前停止了。应用不在时 Android 可能停止了 Termux；没有内容丢失。';

  @override
  String teamUiPhoneFailedReason(String reason) {
    return '原因：$reason';
  }

  @override
  String get teamUiPhoneDispatchFailed => 'Termux 未启动该步骤。请先打开一次 Termux，然后重试。';

  @override
  String get teamUiPhoneSectionTitle => '在本机上';

  @override
  String get teamUiPhoneStatusChecking => '正在检查…';

  @override
  String get teamUiPhoneStatusNotInstalled => '未安装';

  @override
  String get teamUiPhoneStatusInstalled => '已安装 · 尚无 city';

  @override
  String get teamUiPhoneStatusStopped => '已停止';

  @override
  String get teamUiPhoneStatusStarting => '正在启动…';

  @override
  String get teamUiPhoneStatusStopping => '正在停止…';

  @override
  String teamUiPhoneStatusWorking(String verb) {
    return '工作中…（$verb）';
  }

  @override
  String teamUiPhoneStatusRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '运行中 · $count 个智能体',
      one: '运行中 · 1 个智能体',
      zero: '运行中',
    );
    return '$_temp0';
  }

  @override
  String get teamUiPhoneStatusFailed => '未运行 · 上一步骤失败';

  @override
  String teamUiPhoneStatusUnreachable(String url) {
    return '已启动，但未在 $url 上应答';
  }

  @override
  String get teamUiPhoneStatusUnknown => '无法读取状态';

  @override
  String teamUiPhoneVersions(String gc, String bd, String dolt) {
    return 'gc $gc · bd $bd · dolt $dolt';
  }

  @override
  String get teamUiPhoneStopTitle => '停止本机上的团队？';

  @override
  String get teamUiPhoneStopBody => '正在运行的智能体会在原处停止。没有内容丢失；再次启动后运行会继续。';

  @override
  String get teamUiPhoneKilled => '应用不在时 Android 停止了团队。没有内容丢失。';

  @override
  String get teamUiPhoneKeepRunningTitle => '保持运行';

  @override
  String get teamUiPhoneKeepRunningSubtitle => '阻止 Android 在后台关闭团队';

  @override
  String get teamUiPhoneTipsIntro =>
      'Android 会停止它认为过度的后台工作，而团队正是如此：数十个短命的 gc 和 bd 进程，外加一个跑满 CPU 的智能体。以下三点能让它保持存活。';

  @override
  String get teamUiPhoneTipWakeLock =>
      '让 Termux 保持在前台，或持有其唤醒锁：在 Termux 中运行 termux-wake-lock，或在它的通知中点按‘获取唤醒锁’。没有它而熄屏会结束运行。';

  @override
  String get teamUiPhoneTipBattery =>
      '设置 › 应用 › Termux › 电池 › 不受限制，并关闭手机厂商针对 Termux 的自动清理。';

  @override
  String get teamUiPhoneTipPhantom =>
      'Android 12 及更高版本仍会杀死后台应用的子进程（幻影进程杀手）。可通过无线调试在 Termux 内部一次性关闭它，无需电脑：';

  @override
  String get teamUiPhoneTipsCopy => '复制命令';

  @override
  String get teamUiPhoneRemoveTitle => '从此手机删除 AI 团队？';

  @override
  String teamUiPhoneActionFailed(String reason) {
    return '操作失败：$reason';
  }

  @override
  String get teamUiPhoneNotAvailable =>
      '本机不可用。运行团队需要 64 位 Linux 环境；此设备或此构建无法提供。';

  @override
  String teamUiPhoneFailedNoSpace(String detail) {
    return '本机空间不足。$detail 请腾出一些空间（本机的‘存储’可以清理构建缓存），然后重试。';
  }

  @override
  String get phoneServerConnect => '连接';

  @override
  String get phoneServerOpen => '打开';

  @override
  String get phoneServerStart => '启动';

  @override
  String get phoneServerStop => '停止';

  @override
  String get phoneServerMore => '更多服务器操作';

  @override
  String get phoneServerForget => '忘记已保存的登录信息';

  @override
  String get phoneServerStartFailed => '服务器未能启动。请打开“管理设置”查看原因。';

  @override
  String get phoneServerRestartFailed => '服务器未能重启。请打开“管理设置”查看原因。';

  @override
  String get phoneServerStopFailed => '无法停止服务器。请重试。';

  @override
  String get termuxStorageCatSharedCaches => '其他缓存与软件包数据';

  @override
  String get termuxStorageNoteSharedCaches =>
      '共享缓存、软件包安装和下载文件夹可能支撑其他工具，或包含值得保留的文件。无法在此移除。';

  @override
  String get termuxStorageRescanRequired => '上次扫描 · 再次扫描后才能继续清理';

  @override
  String get safetyStopSharingTitle => '停止分享此对话？';

  @override
  String get safetyStopSharingBody => '任何持有该链接的人都将无法再使用它。对话本身不会改变。';

  @override
  String get safetyStopSharingKeep => '继续分享';

  @override
  String get safetyStopLocalServerTitle => '停止本地服务器？';

  @override
  String get safetyStopLocalServerBody =>
      '智能体在此手机上正在运行的任务会被中断，本应用也会与它断开连接。你的项目和对话会保留在手机上；重新启动服务器即可继续。';

  @override
  String get safetyStopLocalServerKeep => '继续运行';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return '断开 $server？';
  }

  @override
  String get safetyMcpDisconnectBody =>
      '在你重新连接之前，智能体将无法使用它的工具，正在进行的工具调用可能会失败。它的配置会保留。';

  @override
  String get safetyMcpDisconnectKeep => '保持连接';

  @override
  String get safetyStopOrphanBody =>
      '没有任何东西在等待它，但它正在进行的工作会丢失。它会先收到正常停止信号，5 秒后被强制停止。';

  @override
  String get settingsHubGroupNotifications => '通知与后台';

  @override
  String get settingsHubGroupUsage => '用量';

  @override
  String get settingsHubGroupHelp => '帮助';

  @override
  String get settingsHubThisServer => '此服务器';

  @override
  String get settingsHubAccounts => '账号';

  @override
  String get settingsHubModelAndMode => '模型与模式';

  @override
  String get settingsHubVoice => '语音';

  @override
  String get settingsHubPrivacyRow => '隐私与数据';

  @override
  String get settingsHubSearchServerAliases =>
      '服务器 主机 网址 地址 密码 配置 连接 健康 状态 版本 更新 服务';

  @override
  String get settingsHubSearchSavedServersAliases =>
      '服务器 配置 主机 网址 密码 切换 添加 编辑 移除 连接';

  @override
  String get settingsHubSearchPhoneAliases => '手机 termux 本地 设备端 安卓 安装 设置 存储 服务';

  @override
  String get settingsHubSearchAccountsAliases => '账号 codex 登录 退出';

  @override
  String get settingsHubSearchExternalAgentsAliases => 'a2a 外部 智能体 远程';

  @override
  String get settingsHubSearchTailscaleAliases => 'tailscale vpn 网络 远程 私有';

  @override
  String get settingsHubSearchDisconnectAliases => '断开 离开 服务器';

  @override
  String get settingsHubSearchModelModeAliases => '模型 模式 智能体 变体 思考 默认 已选 会话 对话';

  @override
  String get settingsHubSearchShellAliases => 'shell 终端 bash zsh 命令';

  @override
  String get settingsHubSearchPermissionsAliases => '权限 审批 始终 允许 已允许 撤销';

  @override
  String get settingsHubSearchTranscriptAliases => '对话记录 显示 思考 推理 时间戳 用量';

  @override
  String get settingsHubSearchVoiceAliases => '语音 说话 麦克风 听写 模型';

  @override
  String get settingsHubSearchNotificationsAliases =>
      '通知 后台 保持运行 自动运行 自动化 始终允许的操作 提醒 免打扰时段 后台检查 wi-fi wifi 监控 已保存服务器 已完成的运行 审批 提问 配额 阈值';

  @override
  String get settingsHubSearchAppearanceAliases => '外观 主题 深色 浅色 语言 阿拉伯语 英语 颜色';

  @override
  String get settingsHubSearchModelsAliases => '模型 智能体 提供商 AI 推理 收藏 最近';

  @override
  String get settingsHubSearchProvidersAliases => '提供商 api 密钥 认证 连接';

  @override
  String get settingsHubSearchMcpAliases => 'mcp 集成 服务器 工具';

  @override
  String get settingsHubSearchCommandsAliases => '命令 工具 技能 参考 斜杠 能力';

  @override
  String get settingsHubSearchPluginsAliases => '插件 已安装 来源 状态 AI Team Gas City';

  @override
  String get settingsHubSearchUsageAliases =>
      '用量 费用 Token 令牌 预算 配额 限制 已用 剩余 阈值 配额监控 提供商 套餐 订阅 支出 提醒';

  @override
  String get settingsHubSearchPrivacyAliases => '隐私 草稿 队列 排队 提示词 已读状态 存储 清除 删除';

  @override
  String get settingsHubSearchGuideAliases => '帮助 指南 连接 教程 开始';

  @override
  String get settingsHubSearchBugAliases =>
      '缺陷 反馈 问题 支持 报告 故障 崩溃 诊断 错误 日志 github';

  @override
  String get settingsHubSearchDiagnosticsAliases => '诊断 调试 错误 日志';

  @override
  String get settingsHubSearchAboutAliases => '关于 版本 许可证 开源 声明 隐私 数据';

  @override
  String get pluginsSectionOnServer => '此服务器上的插件';

  @override
  String get notifySectionWhat => '通知内容';

  @override
  String get notifyFinishedRuns => '已完成的工作';

  @override
  String get notifyFinishedRunsDetail => '多个会合并为一条通知。';

  @override
  String get notifyRequests => '等你回答的问题';

  @override
  String get notifyRequestsDetail => '直接在通知中回应。';

  @override
  String get notifyQuotaAlerts => '用量提醒';

  @override
  String get notifyQuotaAlertsDetail => '在“用量 › 提醒”中设置。';

  @override
  String get notifyBlockedTitle => '此应用的通知已关闭';

  @override
  String get notifyBlockedMessage =>
      'Android 不允许 OpenCode 通知你，所以问题和已完成的对话无法送达。';

  @override
  String get notifySendTest => '发送测试通知';

  @override
  String get notifySendTestDetail => '看看 Android 是否放行。';

  @override
  String get notifyQuietDetail => '在你选择的时段静音。';

  @override
  String get notifySectionBackground => '后台';

  @override
  String get notifySectionServers => '已保存的服务器';

  @override
  String get notifyWifiOnly => '仅在 Wi-Fi 下检查服务器';

  @override
  String get usageSectionSpent => '已花费';

  @override
  String get usageSectionRemaining => '剩余';

  @override
  String get serverSwitcherManage => '管理服务器';

  @override
  String get serverSwitcherOpen => '切换服务器';

  @override
  String get discoverSearchGoTo => '前往';

  @override
  String discoverSearchIn(String parent) {
    return '在 $parent 中';
  }

  @override
  String get discoverProjectAliases => '项目 工具 代码 文件夹 文件 更改 终端 健康 工作树';

  @override
  String get discoverFilesAliases => '文件 浏览 文件夹 目录树 预览 代码 打开文件';

  @override
  String get discoverChangesAliases => '更改 审查更改 差异 未提交 git 编辑 工作树';

  @override
  String get discoverTerminalAliases => '终端 shell 控制台 命令行 pty';

  @override
  String get discoverHealthAliases => '项目健康 分支 已更改文件 语言服务 格式化工具 lsp';

  @override
  String get discoverWorktreesAliases => '工作树 分支 隔离 git 分支';

  @override
  String get discoverSearchFilesAliases => '搜索文件 查找文件 文件名';

  @override
  String get discoverAllConversationsAliases => '所有对话 会话 聊天 历史 每个项目 搜索';

  @override
  String get discoverTeamAliases => 'ai team 智能体 运行 需要你处理 编排';

  @override
  String get discoverNotifyServersTitle => '来自已保存服务器的通知';

  @override
  String get discoverNotifyWhatAliases => '已完成的运行 审批 提问 例行检查 配额提醒 哪些会通知';

  @override
  String get discoverNotifyQuietAliases => '免打扰时段 勿扰 夜间 静音 日程';

  @override
  String get discoverNotifyBackgroundAliases => '后台连接 保持连接 保持活跃 服务';

  @override
  String get discoverNotifyServersAliases => '监控 已保存服务器 需要处理 wi-fi wifi 后台检查';

  @override
  String get discoverAppearanceModeAliases => '浅色 深色 模式 跟随系统 外观 夜间';

  @override
  String get discoverLanguageAliases => '语言 阿拉伯语 英语 语言环境 翻译 rtl';

  @override
  String get discoverThemeAliases => '主题 颜色 调色板 配色包 强调色';

  @override
  String get discoverSpentAliases => '已花费 费用 Token 令牌 用量 统计 金额';

  @override
  String get discoverRemainingAliases =>
      '套餐 订阅 剩余 配额 限制 提供商 计划 剩余额度 重置 codex claude glm minimax';

  @override
  String get discoverBudgetAliases => '预算 美元 Token 令牌 预算 支出上限';

  @override
  String get discoverQuotaMonitorAliases =>
      '用量提醒 配额监控 阈值 提醒 警告 低额度 免打扰时段 wi-fi';

  @override
  String get discoverCommandsAliases => '命令 服务器命令 斜杠 运行';

  @override
  String get discoverToolsAliases => '工具 工具与能力 清单 模型工具';

  @override
  String get discoverSkillsAliases => '技能 指令 操作手册';

  @override
  String get discoverReferencesAliases => '参考 参考资料 文档 来源 上下文';

  @override
  String get discoverRunningNowAliases =>
      '当前运行 此手机上的运行 进程 termux 服务 停止 忙碌 内存 后台';

  @override
  String get discoverStorageAliases => '存储 在此手机上 磁盘空间 清理 termux';

  @override
  String get discoverMonitorAliases => '已保存服务器 需要处理 监控 其他服务器 等待 服务器需要处理';

  @override
  String get discoverConnectionHelpAliases =>
      '添加服务器 电脑 连接 配对 配对码 连接帮助 无法连接 排查 网络 被拒绝 超时';

  @override
  String gestureEquivFileRowActions(String name) {
    return '$name 的操作';
  }

  @override
  String get gestureEquivShortcutFindMatch => '在对话中查找时切换到下一个/上一个匹配项';

  @override
  String get gestureEquivShortcutPromptHistory => '较早/较晚的提示词，光标位于消息框开头或结尾时';

  @override
  String get emptyTeachChangesTitle => '还没有更改';

  @override
  String get emptyTeachChangesMessage => '智能体所做的编辑会显示在这里，供你审查。';

  @override
  String get emptyTeachWorktreesMessage =>
      '工作树是本项目在独立分支上的副本，因此并行工作不会互相混淆。本项目的工作树会显示在这里。';

  @override
  String get emptyTeachAllowedMessage => '当你在本项目的某次审批中选择“始终允许”，它会列在这里，方便你撤销。';

  @override
  String get emptyTeachTeamRunsMessage => '说出你的需求，团队会把它拆成若干步骤，并在此显示进度。';

  @override
  String get emptyTeachSkillsMessage => '技能是智能体可以遵循的可复用指令。来自本项目和服务器的技能会显示在这里。';

  @override
  String get emptyTeachToolsMessage => '智能体使用此模型可以调用的工具会显示在这里。此模型没有可用工具。';

  @override
  String get capabilityScreenTitle => '此服务器可用的功能';

  @override
  String get capabilityScreenAliases =>
      '可用 支持 不可用 缺失 功能 隐藏 原因 无法 能力 服务器 支持 shell';

  @override
  String capabilityScreenIntro(String server) {
    return '$server 决定此应用中显示哪些内容。它做不到的功能不会出现在菜单和标签页里，也不会以灰色显示。';
  }

  @override
  String get capabilityAllAvailable => '此服务器支持本应用提供的全部功能。';

  @override
  String get capabilityFiles => '文件';

  @override
  String get capabilityFilesDetail => '浏览、搜索并预览项目文件';

  @override
  String get capabilityChanges => '更改';

  @override
  String get capabilityChangesDetail => '查看智能体所做的编辑';

  @override
  String get capabilityTerminal => '终端';

  @override
  String get capabilityTerminalDetail => '在项目中运行命令';

  @override
  String get capabilityShell => '默认 Shell';

  @override
  String get capabilityShellDetail => '选择命令和终端使用的 shell';

  @override
  String get capabilityAttachments => '附件';

  @override
  String get capabilityAttachmentsDetail => '随提示词发送文件和照片';

  @override
  String get capabilitySubagents => '委派给子智能体';

  @override
  String get capabilitySubagentsDetail => '在提示词中用 @ 提及智能体';

  @override
  String get capabilityCompact => '压缩';

  @override
  String get capabilityCompactDetail => '总结长对话以释放上下文';

  @override
  String get capabilityShare => '分享';

  @override
  String get capabilityShareDetail => '发布对话链接';

  @override
  String get capabilityFork => '分支';

  @override
  String get capabilityForkDetail => '从较早的消息分出一条对话';

  @override
  String get capabilityRevert => '回退';

  @override
  String get capabilityRevertDetail => '撤销某条提示词及其产生的编辑';

  @override
  String get capabilityArchive => '归档';

  @override
  String get capabilityArchiveDetail => '收起已结束的对话而不删除它们';

  @override
  String get capabilityTodos => '待办';

  @override
  String get capabilityTodosDetail => '查看智能体为某个对话维护的任务列表';

  @override
  String get capabilityNotes => '给智能体的备注';

  @override
  String get capabilityNotesDetail => '为某个对话保留长期有效的指示';

  @override
  String get capabilityImportExport => '导入与导出';

  @override
  String get capabilityImportExportDetail => '以文件形式在服务器之间迁移对话';

  @override
  String get capabilitySearchAll => '所有对话';

  @override
  String get capabilitySearchAllDetail => '跨所有项目搜索对话';

  @override
  String get capabilityAlwaysAllow => '始终允许的操作';

  @override
  String get capabilityAlwaysAllowDetail => '记住某次批准，之后不再询问';

  @override
  String get capabilityModels => '模型与提供商';

  @override
  String get capabilityModelsDetail => '在应用内浏览模型并登录提供商';

  @override
  String get capabilitySkills => '技能与命令';

  @override
  String get capabilitySkillsDetail => '列出服务器的技能、命令和参考资料';

  @override
  String get capabilityMcp => 'MCP';

  @override
  String get capabilityMcpDetail => '查看并连接 MCP 服务器';

  @override
  String get capabilityPlugins => '插件';

  @override
  String get capabilityPluginsDetail => '查看服务器上已安装的插件';

  @override
  String get capabilityCloud => '云环境';

  @override
  String get capabilityCloudDetail => '在托管环境中运行项目';

  @override
  String get capabilityProjects => '项目';

  @override
  String get capabilityProjectsDetail => '切换项目并检查项目健康状况';

  @override
  String get capabilityWorktrees => '工作树';

  @override
  String get capabilityWorktreesDetail => '为任务分配独立的隔离分支';

  @override
  String get capabilityUsage => '用量';

  @override
  String get capabilityUsageDetail => '查看对话产生了多少花费';

  @override
  String get capabilityOfflineQueue => '稍后发送';

  @override
  String get capabilityOfflineQueueDetail => '离线时排队提示词，重新连接后发送';

  @override
  String get capabilityContinueOnComputer => '在电脑上继续';

  @override
  String get capabilityContinueOnComputerDetail => '获取可在你桌面上重新打开该对话的命令';

  @override
  String get capabilityServerUpdates => '服务器更新';

  @override
  String get capabilityServerUpdatesDetail => '从应用中更新服务器';

  @override
  String get capabilityBackgroundNotifications => '后台通知';

  @override
  String get capabilityBackgroundNotificationsDetail =>
      '应用关闭时，在工作完成或需要你处理时收到通知';

  @override
  String get capabilityOnThisPhone => '在此手机上';

  @override
  String get capabilityOnThisPhoneDetail => '在此设备上运行智能体的服务器';

  @override
  String get capabilityVoice => '语音';

  @override
  String get capabilityVoiceDetail => '使用设备端语音模型听写提示词';

  @override
  String get discoverShowTipsAgain => '再次显示提示';

  @override
  String get discoverShowTipsSubtitle => '一次性提示会在相应时机再次出现';

  @override
  String get discoverShowTipsAliases => '提示 建议 引导 帮助 重置 再次显示 教程';

  @override
  String get discoverShowTipsDone => '提示将再次显示。';

  @override
  String nudgeApprovals(String action) {
    return '已 3 次请求“$action”：此对话可以替你批准请求。';
  }

  @override
  String get nudgeReviewChanges => 'OpenCode 更改了文件。继续之前请查看。';

  @override
  String get nudgeLeave => '你可以离开：运行完成后此手机会通知你。';

  @override
  String nudgeCompact(String percent) {
    return '上下文已用 $percent%：压缩后可继续。';
  }

  @override
  String get nudgeDismiss => '隐藏提示';

  @override
  String get firstRunWhereQuestion => '你的智能体应该在哪里运行？';

  @override
  String get firstRunOnComputer => '在我的电脑上';

  @override
  String get firstRunJustShowMe => '先看看演示';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => '在你的电脑上运行：';

  @override
  String get firstRunPairingNextScan => '它会显示一个代码。扫描它，或复制后粘贴到这里。';

  @override
  String get firstRunPairingNextPaste => '然后粘贴它输出的代码。';

  @override
  String get firstRunNotSameNetwork => '不在同一网络？';

  @override
  String get firstRunShowCommands => '显示命令';

  @override
  String get firstRunCommandsPaseoNetwork => '若要从此手机通过私有网络访问，请监听该地址并设置密码：';

  @override
  String get firstRunCommandsCodexToken => '启动前先创建连接令牌：';

  @override
  String get firstRunCommandsCodexUsb => '通过 USB 线连接的手机可用以下命令访问：';

  @override
  String get firstRunNotifyTitle => '当智能体需要你时通知你？';

  @override
  String get firstRunNotifyBody =>
      '智能体工作时可以离开应用。它需要你回答时会通知你。保持连接期间，Android 会显示一条常驻小通知。';

  @override
  String get firstRunNotifyAccept => '通知我';

  @override
  String get firstRunNotifyDecline => '暂不';

  @override
  String get localAgentTitle => '此手机上的 Claude Code';

  @override
  String get localAgentOfferBody =>
      '无需电脑，直接在这里运行 Claude Code。应用会把 Node.js、Paseo 守护进程和 Claude Code 安装到它已管理的 Ubuntu 中，且只能在此手机上访问。';

  @override
  String get localAgentOfferSize =>
      'Node.js 约需下载 60 MB，另加软件包；安装后约占 1 GB。需要 2 GB 可用空间。';

  @override
  String get localAgentOfferWarning =>
      'Android 可能会在后台停止 Termux。让 OpenCode 服务器保持运行的电池设置同样适用于它。';

  @override
  String get localAgentSetUp => '设置 Claude Code';

  @override
  String get localAgentNotNow => '暂不';

  @override
  String get localAgentNeedsUbuntuBody =>
      'Claude Code 运行在本应用设置的 Ubuntu 中。请先完成“在这部手机上”的设置，再回到这里。';

  @override
  String get localAgentOpenSetup => '打开手机设置向导';

  @override
  String get localAgentNeedsTermuxBody =>
      'Claude Code 目前需要 Termux；应用内置的 Linux 还不能运行它。请设置 Termux 以在此使用。';

  @override
  String get localAgentSetUpWithTermux => '使用 Termux 设置';

  @override
  String get localAgentStepNode => 'Node.js';

  @override
  String get localAgentStepPaseo => 'Paseo 守护进程';

  @override
  String get localAgentStepClaude => 'Claude Code';

  @override
  String get localAgentStepSignIn => '登录 Claude';

  @override
  String get localAgentStepStart => '在此手机上启动';

  @override
  String get localAgentInstalling => '正在设置 Claude Code';

  @override
  String get localAgentLeaveNote => '你可以离开此屏幕。安装会在 Termux 中继续进行。';

  @override
  String get localAgentSignInBody =>
      'Termux 会打开，Claude Code 会显示一个链接。请在浏览器中批准，将代码粘贴回 Termux，然后返回这里。本应用从不查看或保存你的 Claude 登录信息。';

  @override
  String get localAgentSignInAlready => '我已登录';

  @override
  String localAgentSignInOpenFailed(String command) {
    return '无法打开 Termux。请自行打开 Termux 并运行：$command';
  }

  @override
  String get localAgentSignInMissing => '尚未找到 Claude 登录信息。';

  @override
  String get localAgentReadyTitle => 'Claude Code 正在此手机上运行';

  @override
  String get localAgentReadyBody => '它只在此手机上监听（127.0.0.1），并由本应用保管的密码保护。';

  @override
  String localAgentVersions(String claude, String paseo, String node) {
    return 'Claude Code $claude · Paseo $paseo · Node.js $node';
  }

  @override
  String get localAgentInstalledTitle => 'Claude Code 已安装但未运行';

  @override
  String get localAgentKilled => '应用在后台时，Android 停止了 Claude Code。没有丢失任何内容。';

  @override
  String get localAgentFailedTitle => 'Claude Code 设置已停止';

  @override
  String localAgentFailedNoSpace(String detail) {
    return '此手机空间不足。$detail请释放一些空间后重试。';
  }

  @override
  String get localAgentFailedDownload => '无法下载 Node.js。请检查网络后重试。';

  @override
  String get localAgentFailedChecksum =>
      'Node.js 下载内容与固定的校验和不一致，因此未安装任何内容。请重试；如果再次发生，说明网络中有东西在篡改该文件。';

  @override
  String get localAgentFailedNativeBuild =>
      '某个软件包需要原生模块，而此手机没有现成的构建。未编译任何内容；下方输出中指出了该模块。';

  @override
  String get localAgentFailedPackages => '无法安装软件包。请检查网络后重试。';

  @override
  String get localAgentFailedPortInUse => '此手机的 6767 端口已被其他程序占用。请停止该程序后重试。';

  @override
  String get localAgentFailedTimeout => 'Claude Code 在两分钟内没有响应。下方输出显示了它打印的内容。';

  @override
  String get localAgentFailedInterrupted => 'Android 在该步骤完成前将其中断。请重试，它会从中断处继续。';

  @override
  String get localAgentFailedUnsupported => 'Claude Code 需要 64 位手机。';

  @override
  String get localAgentFailedDaemon => 'Claude Code 已停止或没有响应。请重新启动它。';

  @override
  String localAgentFailedReason(String detail) {
    return '已停止：$detail';
  }

  @override
  String get localAgentProjectTitle => '选择项目文件夹';

  @override
  String get localAgentProjectBody => 'Claude Code 在此手机 Ubuntu 的某个文件夹内工作。';

  @override
  String get localAgentProjectPathLabel => '或输入 Ubuntu 内的路径';

  @override
  String get localAgentProjectPathInvalid =>
      '请输入完整路径，例如 /root/projects/my-app。';

  @override
  String localAgentConnectFailed(String detail) {
    return '无法连接到此手机上的 Claude Code。$detail';
  }

  @override
  String localAgentCardActionFailed(String detail) {
    return '操作失败。$detail';
  }

  @override
  String get localAgentRestartTitle => '在此手机上重启 Claude Code？';

  @override
  String get localAgentRestartBody =>
      'Claude Code 会短暂不可用，它当前正在做的事情会被中断。你的对话会保留在手机上。';

  @override
  String get localAgentStopTitle => '在此手机上停止 Claude Code？';

  @override
  String get localAgentStopBody =>
      'Claude Code 在此手机上正在做的事情会被中断，本应用也会与它断开连接。你的项目、对话和 Claude 登录信息会保留；重新启动即可继续。';

  @override
  String get localAgentRemove => '从此手机移除';

  @override
  String get localAgentRemoveTitle => '从此手机移除 Claude Code？';

  @override
  String get localAgentRemoveBody =>
      '停止它，并从 Ubuntu 中删除 Node.js、Paseo 和 Claude Code，约 1 GB。你的项目和 Claude 登录信息会保留。';

  @override
  String get localAgentRemoveKeep => '保留';

  @override
  String get localAgentMore => '更多 Claude Code 操作';

  @override
  String get firstRunAgentsSideBySide =>
      '它们在同一台电脑上并行运行。先从一个开始，之后随时可从顶部的服务器名称添加其他的。';

  @override
  String get firstRunPaseoTitle => '通过 Paseo 使用智能体';

  @override
  String get agentErrorConnectionDropped => '与模型的连接已断开。';

  @override
  String get agentErrorConnectionDroppedHint =>
      '请检查运行 OpenCode 的设备的网络。如果回复已停止，请先查看对话，再重新发送。';

  @override
  String get agentErrorTimedOut => '模型响应时间过长。';

  @override
  String get agentErrorTimedOutHint => '如果回复已停止，请尝试更小的请求或其他模型。';

  @override
  String get agentErrorProviderUnreachable => '服务器无法连接到模型提供商。';

  @override
  String get agentErrorProviderUnreachableHint =>
      '请检查运行 OpenCode 的电脑的网络连接，然后重试。';

  @override
  String get agentErrorRequestTooLarge => '此请求过大，无法发送给模型。';

  @override
  String get agentErrorRequestTooLargeHint => '请减少附件数量或缩小附件，或压缩对话，然后重试。';

  @override
  String get agentErrorRateLimited => '模型提供商正在限制你的发送速度。';

  @override
  String get agentErrorRateLimitedHint => '请稍等。如果回复已停止，请重试或选择其他模型。';

  @override
  String get agentErrorProviderBusy => '模型提供商目前负载过高。';

  @override
  String get agentErrorProviderBusyHint => '如果回复已停止，请稍后重试或选择其他模型。';

  @override
  String get agentErrorOutOfCredit => '提供商账户的额度或配额已用完。';

  @override
  String get agentErrorOutOfCreditHint => '请为账户充值，或切换到其他提供商或模型。';

  @override
  String get agentErrorUsageLimit =>
      'This model\'s usage limit is reached for now.';

  @override
  String get agentErrorUsageLimitHint =>
      'Choose another model to keep going, or continue after the limit resets.';

  @override
  String get agentErrorServerDiskFull => '运行 OpenCode 的电脑磁盘空间已满。';

  @override
  String get agentErrorServerDiskFullHint => '请在那台电脑上释放一些空间，然后重试。';

  @override
  String get agentErrorRecovered => '智能体在此之后继续运行了。';

  @override
  String chatUiRetryingSoon(String attempt) {
    return '正在重试$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return '$time 后重试$attempt';
  }

  @override
  String get chatUiCompactAgain => '再次压缩';

  @override
  String get chatUiCompactionFailedHint => '对话对模型来说仍然太长，所以下一轮会再次尝试。';

  @override
  String get chatStripContextPending => '上下文待处理';

  @override
  String get chatStripAutoApprove => '自动批准';

  @override
  String get chatChipAutoApproveOn => 'Auto-approve on';

  @override
  String get chatStripAutoApprovePaused => '自动批准已暂停';

  @override
  String get approvalModeMenuLabel => '批准模式';

  @override
  String get approvalModeAskTitle => '先询问';

  @override
  String get approvalModeAutoTitle => '自动批准此对话';

  @override
  String get approvalModeAutoDetail => '请求到达时逐次允许一次。';

  @override
  String get approvalModeEverythingTitle => '批准全部';

  @override
  String get approvalModeEverythingDetail => '此服务器上的所有对话。';

  @override
  String approvalModeEverythingAgentDetail(String agent) {
    return '这部手机上 $agent 的所有对话。';
  }

  @override
  String get approvalModeSettings => '批准设置…';

  @override
  String get approvalModeConfirmEverythingTitle => '要批准全部吗？';

  @override
  String get approvalModeConfirmEverythingAction => '全部批准';

  @override
  String get approvalModeNowAsk => '此对话将先询问。';

  @override
  String get approvalModeNowAuto => '此对话将自动批准。';

  @override
  String get approvalModeNowEverything => '正在批准此服务器上的全部请求。';

  @override
  String get approvalModeChange => '更改批准模式';

  @override
  String get chatStripBackground => '后台';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return '$name 的内容，在手机上读取：仅包含数值和文字，不含格式、图表或公式。';
  }

  @override
  String get chatAttachmentOfficeTruncated => '文件超出了一条提示词可容纳的大小，因此下方内容已被截断。';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return '无法读取 $name。它可能受密码保护、已损坏，或为旧版 Excel 或 Word 格式；请另存为 .xlsx、.docx 或 CSV 后重试。';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return '$name 中没有可附加的内容。';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return '已将 $name 作为文字附加。';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets 个工作表',
      one: '1 个工作表',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows 行',
      one: '1 行',
    );
    return '已将 $name 作为文字附加 · $_temp0，$_temp1';
  }

  @override
  String get localAgentUpdate => '更新 Claude Code';

  @override
  String get localAgentUpdateAvailable => '更新后将加入最新的 Claude 模型，例如 Opus 5.5。';

  @override
  String get localAgentUpdateNow => '更新';

  @override
  String get modelNewBadge => '新';

  @override
  String get modelEffortNone => '不思考';

  @override
  String get modelEffortMinimal => '最低';

  @override
  String get modelEffortLow => '低';

  @override
  String get modelEffortMedium => '中';

  @override
  String get modelEffortHigh => '高';

  @override
  String get modelEffortExtraHigh => '极高';

  @override
  String get modelEffortMax => '最大';

  @override
  String get setupStoppedByRestart => '手机已重启，因此本地服务器已停止。需要时请再次启动。';

  @override
  String get phoneServerStoppedTitle => '此手机上的服务器已停止';

  @override
  String get phoneServerStoppedBody =>
      '手机重启或 Android 为省电关闭 Termux 时，它会停止。你的对话会保留；请重新启动以继续。';

  @override
  String get phoneServerStartAndConnect => '启动并连接';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个进行中',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return '向 $agent 提问…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示前 $countString 行',
      one: '显示前 1 行',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '打开全部 $countString 行';
  }

  @override
  String get setupProgressViewOverallLabel => '设置进度';

  @override
  String get setupProgressViewGettingStarted => '正在开始…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '剩余约 $minutes 分钟',
      one: '剩余约 1 分钟',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => '不到一分钟';

  @override
  String get setupProgressViewDone => '全部完成';

  @override
  String get setupProgressViewFailedTitle => '设置未完成';

  @override
  String get setupProgressViewInterrupted => '设置被中断。已完成的部分会保留。';

  @override
  String get setupProgressViewCancelled => '设置已停止。已完成的部分仍保持安装。';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done / $total';
  }

  @override
  String setupProgressViewPercent(int percent) {
    return '$percent%';
  }

  @override
  String setupProgressViewStageMeasured(String stage, String measured) {
    return '$stage · $measured';
  }

  @override
  String get setupProgressViewChecking => '检查中';

  @override
  String get setupProgressViewStarting => '启动中';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return '停止于：$stage。出错原因见“详情”。';
  }

  @override
  String get setupProgressViewFailedUnknown => '设置在完成前停止了。出错原因见“详情”。';

  @override
  String get setupProgressViewNoInternet => '无网络连接 — 恢复联网后再继续';

  @override
  String get setupProgressViewContinue => '继续设置';

  @override
  String get setupProgressViewCancel => '停止设置';

  @override
  String get setupProgressViewNoLog => '暂无日志。';

  @override
  String get phoneSetupProgressTitle => '正在这部手机上设置 OpenCode';

  @override
  String get phoneSetupProgressStopTitle => '要停止设置吗？';

  @override
  String get phoneSetupProgressStopMessage => '已完成的部分会保留。';

  @override
  String get phoneSetupProgressStopConfirm => '停止设置';

  @override
  String get phoneSetupProgressKeepGoing => '继续进行';

  @override
  String get builtinServerChooseRuntime => '选择哪个 OpenCode';

  @override
  String get builtinServerStopped => '未在运行。';

  @override
  String builtinServerStartFailed(String reason) {
    return 'OpenCode 没有响应：$reason。打开日志查看原因。';
  }

  @override
  String get builtinServerExited => '服务器已停止';

  @override
  String builtinServerTimedOut(int seconds) {
    return '$seconds 秒内无响应';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return '无法连接：$reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return '此手机，内置（$runtime）';
  }

  @override
  String get phoneSetupProfileName => '此手机';

  @override
  String get phoneSetupLinuxTitle => 'Linux 基础环境';

  @override
  String get phoneSetupLinuxWhy => '其他所有内容都在其中运行。';

  @override
  String get phoneSetupEssentialsTitle => 'Git、SSH 和证书';

  @override
  String get phoneSetupEssentialsShort => 'Git 和 SSH';

  @override
  String get phoneSetupEssentialsWhy => '智能体通过 Git 和 SSH 处理你的项目。';

  @override
  String get phoneSetupNodeWhy => 'OpenCode 基于 Node.js 运行。';

  @override
  String get phoneSetupOpenCodeWhy => '编程智能体本身。';

  @override
  String get phoneSetupStartTitle => '启动 OpenCode';

  @override
  String get phoneSetupStartWhy => '设置完成时 OpenCode 已在运行。';

  @override
  String get phoneSetupStageDownloadingLinux => '正在下载 Linux 基础系统';

  @override
  String get phoneSetupStageUnpackingLinux => '正在解压 Linux 基础系统';

  @override
  String get phoneSetupStageStarting => '正在启动 OpenCode';

  @override
  String get phoneSetupNotificationChannel => '手机设置';

  @override
  String get phoneSetupNotificationTitle => '正在此手机上设置 OpenCode';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return '已完成 $percent%';
  }

  @override
  String get phoneSetupNotificationDone => 'OpenCode 已在此手机上就绪';

  @override
  String get phoneSetupNotificationStopped => '设置已停止。打开应用以继续。';

  @override
  String get phoneSetupErrorNoInternet => '没有网络连接。恢复联网后再继续。';

  @override
  String phoneSetupErrorOffline(String name) {
    return '无法下载 $name：没有网络连接';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return '无法安装 $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return '$name 的下载已损坏。继续以重新下载。';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'OpenCode 已下载，但下载内容中没有它的程序。继续以重新下载。';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'OpenCode 已下载，但其程序无法在此手机上运行。详情会显示它的输出。';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'OpenCode 已安装，但没有启动。继续以重试；详情会显示它的输出。';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return '可用空间不足，无法安装 $name。请释放一些存储空间，然后继续设置。';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'OpenCode 已安装但没有响应。继续设置以重试。详情会显示停止的原因。';

  @override
  String get builtinServerLogTitle => '服务器日志';

  @override
  String get phoneSetupReadyTitle => 'OpenCode 已准备就绪';

  @override
  String get phoneSetupReadyNameTitle => '给你的第一个项目起个名字';

  @override
  String get phoneSetupReadyNameLabel => '项目名称';

  @override
  String get phoneSetupReadyNameHelp => '字母、数字、- _ .';

  @override
  String get phoneSetupReadyCreating => '正在创建项目';

  @override
  String get phoneSetupReadyCreateOpen => '创建并打开';

  @override
  String get phoneSetupReadyOpenFolderInstead => '改为打开文件夹';

  @override
  String get phoneSetupReadyNameEmpty => '请输入名称。';

  @override
  String get phoneSetupReadyNameOneFolder => '请使用一个不含斜杠的名称。';

  @override
  String get phoneSetupReadyNameInvalid =>
      '请使用字母、数字、- _ 或 .，并以字母或数字开头（最多 64 个字符）。';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return '无法创建项目：$reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return '无法打开项目：$reason';
  }

  @override
  String get phoneServerCardTitle => '应用内 Ubuntu';

  @override
  String get phoneServerCardRunning => '运行中';

  @override
  String get phoneServerCardStopped => '已停止';

  @override
  String get phoneServerCardStarting => '正在启动';

  @override
  String get phoneServerCardStopping => '正在停止';

  @override
  String get phoneServerCardRemoving => '正在移除';

  @override
  String get phoneServerCardChecking => '检查中';

  @override
  String get phoneServerCardNotSetUp => '未设置';

  @override
  String get phoneServerCardSettingUp => '正在设置';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => '设置';

  @override
  String get phoneServerCardShowProgress => '查看进度';

  @override
  String get phoneServerCardContinueSetup => '继续设置';

  @override
  String get phoneServerCardLogTitle => '日志';

  @override
  String get phoneServerCardLogEmpty => '日志中暂无内容。';

  @override
  String get phoneServerCardMore => '更多';

  @override
  String get phoneServerCardAddTools => '添加工具（Python、AI Team…）';

  @override
  String get phoneServerCardUpdate => '更新 OpenCode';

  @override
  String get phoneServerCardRemove => '从此手机移除…';

  @override
  String get phoneServerCardRemoveTitle => '从此手机移除 OpenCode？';

  @override
  String get serverEditorMoreOptions => '更多选项';

  @override
  String get inAppServerStoppedTitle => '应用内的 OpenCode 已停止';

  @override
  String get inAppServerStoppedBody => '应用关闭一段时间或更新后，它会停止。你的对话会保留；请重新启动以继续。';

  @override
  String get inAppServerNotRespondingTitle => '应用内的 OpenCode 没有响应';

  @override
  String get inAppServerNotRespondingBody => '重新启动通常即可解决。你的对话会保留。';

  @override
  String get inAppServerStartFailedTitle => '应用内的 OpenCode 未能启动';

  @override
  String get inAppServerStartFailedBody => '打开其设置查看服务器日志，或再次尝试启动。';

  @override
  String get inAppServerStarting => '正在应用内启动 OpenCode…';

  @override
  String get inAppServerStartingBody => '这需要几秒钟。';

  @override
  String get inAppServerOpenSetup => '打开设置向导';

  @override
  String get projectFolderNewProject => '新项目';

  @override
  String get projectFolderProjectNameLabel => '项目名称';

  @override
  String get projectFolderEnterPath => '输入路径';

  @override
  String get projectFolderMissing => '该文件夹尚不存在。';

  @override
  String get projectFolderCreateIt => '创建它';

  @override
  String projectFolderCreateFailed(String reason) {
    return '无法创建文件夹：$reason';
  }

  @override
  String projectFolderCheckFailed(String reason) {
    return '无法检查文件夹：$reason';
  }

  @override
  String get folderBrowserUp => '返回上一级文件夹';

  @override
  String folderBrowserCurrent(String path) {
    return '当前文件夹：$path';
  }

  @override
  String folderBrowserOpen(String name) {
    return '打开 $name';
  }

  @override
  String get folderBrowserGit => 'Git 仓库';

  @override
  String get folderBrowserProject => 'OpenCode 项目';

  @override
  String folderBrowserShowInside(String name) {
    return '显示 $name 中的文件夹';
  }

  @override
  String get folderBrowserProjectsHere => '你的项目都在这里。点按一个即可打开。';

  @override
  String get folderBrowserHomeHere => '主目录和根目录不能作为项目。请打开其中的某个文件夹。';

  @override
  String get folderBrowserNoProjectsTitle => '还没有项目';

  @override
  String get folderBrowserNoProjectsBody => '新建一个，它会出现在这里。';

  @override
  String get folderBrowserEmptyTitle => '这里没有文件夹';

  @override
  String get folderBrowserEmptyBody => '将其作为项目打开，或返回上一级文件夹。';

  @override
  String get folderBrowserErrorTitle => '无法显示此文件夹';

  @override
  String get folderBrowserErrorNotInstalled => '应用内尚未安装 Ubuntu。';

  @override
  String get folderBrowserErrorMissing => '它已不存在。';

  @override
  String get folderBrowserErrorDenied => '应用无权读取它。';

  @override
  String get folderBrowserErrorLinked => '它是一个链接。请改为输入它的路径。';

  @override
  String get folderBrowserErrorFailed => '请重试，或改为输入它的路径。';

  @override
  String get folderBrowserErrorTimedOut => '响应时间过长。请重试。';

  @override
  String get folderBrowserRetry => '重试';

  @override
  String get phoneSetupStartScreenTitle => '在这部手机上';

  @override
  String get phoneSetupStartHeadline => '直接在这里运行编程智能体';

  @override
  String phoneSetupStartPromise(String size) {
    return '无需电脑，也无需其他应用。首次需下载约 $size。';
  }

  @override
  String get phoneSetupStartPromiseNoSize => '无需电脑，也无需其他应用。';

  @override
  String phoneSetupStartSteps(String time) {
    return '安装、为项目命名、开始对话 · $time';
  }

  @override
  String phoneSetupStartStepsTime(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '约 $minutes 分钟',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '大约 $minutes 分钟',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartMegabytes(String value) {
    return '$value MB';
  }

  @override
  String phoneSetupStartGigabytes(String value) {
    return '$value GB';
  }

  @override
  String get phoneSetupStartSetUp => '在这部手机上设置 OpenCode';

  @override
  String phoneSetupStartIncludes(String tools) {
    return '包含 $tools。';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first和$last';
  }

  @override
  String get phoneSetupStartListSeparator => '、';

  @override
  String get phoneSetupStartCustomize => '选择要安装的内容';

  @override
  String get phoneSetupStartOtherWays => '其他方式';

  @override
  String get phoneSetupStartUseTermux => '改用 Termux';

  @override
  String get phoneSetupStartByAddress => '通过地址连接到电脑';

  @override
  String get phoneSetupStartSetUpHere => '改为在本应用内设置';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return '设置已完成 $percent%';
  }

  @override
  String get phoneSetupStartRunningBody => '你使用其他应用时它也会继续进行。';

  @override
  String get phoneSetupStartStoppedBody => '在完成前停止了。继续后会从中断的地方接着进行。';

  @override
  String get phoneSetupStartContinue => '继续设置';

  @override
  String get phoneSetupStartReadyHeadline => 'OpenCode 已在这部手机上准备就绪';

  @override
  String get phoneSetupStartReadyBody => '打开它，开始一次对话。';

  @override
  String get phoneSetupStartOpen => '打开';

  @override
  String get phoneSetupStartTermuxHeadline => 'OpenCode 已在 Termux 中设置好';

  @override
  String get phoneSetupStartTermuxBody => '你之前用 Termux 设置过。连接后即可继续使用。';

  @override
  String get phoneSetupStartConnect => '连接';

  @override
  String phoneSetupStartFailed(String reason) {
    return '出了点问题：$reason';
  }

  @override
  String get phoneSetupStartEntryDetail => '直接在这里运行编程智能体，无需电脑。';

  @override
  String get phoneSetupStartCustomizeTitle => '选择要安装的内容';

  @override
  String get phoneSetupStartAddTitle => '添加工具';

  @override
  String get phoneSetupStartInstalled => '已安装';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => '尚未选择任何内容';

  @override
  String get phoneSetupStartDone => '完成';

  @override
  String get phoneSetupStartAdd => '添加';

  @override
  String get phoneSetupStartChecking => '正在检查已安装的内容…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline => '这部手机无法运行';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return '本应用的 Ubuntu 只能在 64 位 Arm 或 Intel 手机上运行，而这部手机报告的是 $abi。';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline => '这部手机的内存不够';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'OpenCode 需要至少 $minimumString MB 内存的手机，而这部手机只有 $actualString MB。请改在电脑上运行，并让这部手机连接到它。';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return '这部手机只有 $memoryString MB 内存，运行可能会比较慢。';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline => '可用空间不足';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return '请先在这部手机上腾出约 $size 空间，然后回来继续设置。';
  }

  @override
  String get phoneSetupPreflightOpenStorage => '打开存储设置';

  @override
  String get phoneSetupOpenWelcomeShowProgress => '查看进度';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => '做一个小网页';

  @override
  String get chatStartPythonScript => '写一个 Python 脚本，实现……';

  @override
  String get chatStartNodeProject => '创建一个 Node.js 项目';

  @override
  String get chatStartReadme => '写一个 README';

  @override
  String get chatStartExplainProject => '解释一下这个项目';

  @override
  String get chatStartWhatChanged => '最近有什么改动？';

  @override
  String get chatStartFindBug => '找出并修复一个 bug';

  @override
  String get chatStartAddTests => '添加测试';

  @override
  String get chatStartListFolder => '列出这个文件夹里有什么';

  @override
  String get chatStartEmptyFolder => '空文件夹';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项',
    );
    return '$_temp0';
  }

  @override
  String get chatStartGit => 'Git';

  @override
  String chatStartChangeCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 处更改',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => '正在查看文件夹…';

  @override
  String get chatStartServerFolder => '服务器文件夹';

  @override
  String get chatStartTip => '输入 / 使用命令 · 长按消息查看操作';

  @override
  String get chatLoadingConversation => '正在加载对话';

  @override
  String get chatLoadFailedTitle => '无法打开这个对话';

  @override
  String get chatLoadFailedBody => '没有丢失任何内容。OpenCode 恢复响应后请重试。';

  @override
  String get chatSendFailed => '你的消息没有发送出去';

  @override
  String get chatSendFailedKept => '它已放回输入框。';

  @override
  String get chatStartSuggestionsLabel => '开始方式';

  @override
  String get perfTraceTitle => '性能';

  @override
  String get perfTraceBody =>
      '应用打开期间每个步骤的耗时：连接、加载、每次向服务器发出的请求。仅保存在内存中，应用关闭时清除。报告包含名称和耗时，绝不含消息或密码。';

  @override
  String get perfTraceCopy => '复制耗时报告';

  @override
  String get perfTraceCopied => '性能报告已复制';

  @override
  String get perfTraceEmpty => '尚未测量任何内容。';

  @override
  String get perfTraceSlowest => '最慢的步骤';

  @override
  String get perfTraceRecent => '最新步骤';

  @override
  String perfTraceStatLine(int count, String p50, String p95, String max) {
    return '$count× · 典型 $p50 · 较慢 $p95 · 最长 $max';
  }

  @override
  String perfTraceFailed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 次失败',
      one: '1 次失败',
    );
    return '$_temp0';
  }

  @override
  String perfTraceAt(String time) {
    return '于 $time';
  }

  @override
  String perfTraceWithin(String parent) {
    return '在 $parent 内';
  }

  @override
  String get aiteamComponentTitle => 'AI Team';

  @override
  String aiteamComponentStageDownloading(String index, String total) {
    return '正在下载 AI Team · 第 $index 个，共 $total 个';
  }

  @override
  String get aiteamComponentStagePreparing => '正在准备 AI Team';

  @override
  String aiteamComponentAddingTitle(String names) {
    return '正在添加 $names';
  }

  @override
  String get aiteamComponentNotice => 'OpenCode 和 AI Team 正在此手机上运行';

  @override
  String get aiteamComponentSectionTitle => '此手机上的 AI Team';

  @override
  String aiteamComponentOfferBody(String size) {
    return '多个智能体就在此手机上共同完成一个项目的工作。约需下载 $size。';
  }

  @override
  String get aiteamComponentAdd => '添加 AI Team';

  @override
  String aiteamComponentTurnOn(String project) {
    return '为 $project 开启 AI Team';
  }

  @override
  String get aiteamComponentTurnOnBody => '团队在自己的分支上工作，并在此手机上保留一份项目历史副本。';

  @override
  String get aiteamComponentNoProject => '请先打开一个项目，然后为其开启 AI Team。';

  @override
  String get aiteamComponentStageTeam => '正在准备团队';

  @override
  String aiteamComponentStageProject(String project) {
    return '正在添加 $project';
  }

  @override
  String get aiteamComponentStageStarting => '正在启动 AI Team';

  @override
  String get aiteamComponentStageWaiting => '正在等待 AI Team 响应';

  @override
  String get aiteamComponentTurnOnExpectation =>
      '首次开启大约需要 5 到 10 分钟。你可以离开此页面，它会继续进行。';

  @override
  String get aiteamComponentStartExpectation => '大约需要几分钟。你可以离开此页面，它会继续进行。';

  @override
  String aiteamComponentStageSoFar(String time) {
    return '已用时 $time';
  }

  @override
  String aiteamComponentStageTook(String time) {
    return '耗时 $time';
  }

  @override
  String get aiteamComponentRunning => 'AI Team · 运行中';

  @override
  String get aiteamComponentStopped => 'AI Team · 已停止';

  @override
  String aiteamComponentProjects(String projects) {
    return '服务于 $projects';
  }

  @override
  String get aiteamComponentStart => '启动 AI Team';

  @override
  String aiteamComponentFailed(String reason) {
    return 'AI Team 无法启动：$reason';
  }

  @override
  String get aiteamComponentFailedExited => '它自行停止了';

  @override
  String get aiteamComponentFailedTimeout => '它未能及时响应';

  @override
  String get aiteamComponentShowDetails => '显示详情';

  @override
  String get aiteamComponentChildProcesses =>
      '当应用同时运行多个额外程序时，Android 会把它们停止，而一个团队会运行多个。如果团队在工作时停止，请开启“开发者选项 › 停用子进程限制”。';

  @override
  String workOpenLiveConversation(String title) {
    return '打开“$title”';
  }

  @override
  String get workLoadingLabel => '正在加载';

  @override
  String agentNotAnsweringPhone(String agent) {
    return '此手机上的 $agent 没有响应';
  }

  @override
  String get workServerNotAnsweringPhone => '此手机上的 OpenCode 没有响应';

  @override
  String workServerNotAnswering(String server) {
    return '$server 没有响应';
  }

  @override
  String get workServerKeepsTrying => '应用会在后台继续尝试。';

  @override
  String get workServerRestart => '重启';

  @override
  String get connectionRestartAgent => 'Restart Claude Code';

  @override
  String get connectionRestartPhoneServer => 'Restart OpenCode';

  @override
  String get workServerRestartTitle => '在此手机上重启 OpenCode？';

  @override
  String get workServerRestartBody => '正在进行的智能体回合将会停止。你的对话会保留。';

  @override
  String workRunaway(String duration) {
    return 'OpenCode 已空转忙碌 $duration';
  }

  @override
  String workRunawayInProject(String project, String duration) {
    return 'OpenCode 在 $project 中已空转忙碌 $duration';
  }

  @override
  String get connectStartingPhone => '正在这部手机上启动 OpenCode…';

  @override
  String get connectStartingBody => '你的对话会保留。这可能需要一分钟。';

  @override
  String aiteamBringInDone(String project, String commit) {
    return '$project 已包含团队的最新工作（$commit）。';
  }

  @override
  String aiteamBringInDirty(String commit, String project, String files) {
    return '团队的工作（$commit）尚未进入 $project：$project 自身有更改（$files），因此保持原样。';
  }

  @override
  String aiteamBringInDiverged(String commit, String project) {
    return '团队的工作（$commit）尚未进入 $project：$project 有自己的提交。请用 git 合并两者。';
  }

  @override
  String aiteamBringInFailed(String project, String reason) {
    return '无法将团队的工作并入 $project：$reason';
  }

  @override
  String aiteamBringInAction(String project) {
    return '将团队的工作并入 $project';
  }

  @override
  String get teamUiHostPhrasePhone => '本机';

  @override
  String teamUiHostPhraseComputerNamed(String name) {
    return '在 $name 上';
  }

  @override
  String get teamUiHostPhraseComputer => '在你的电脑上';

  @override
  String get teamUiHostPhrasePaused => '已暂停';

  @override
  String get teamUiHostPhraseNotAnswering => '无应答';

  @override
  String teamUiTaskSteps(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$total 个步骤中已完成 $done 个',
      one: '1 个步骤中已完成 $done 个',
    );
    return '$_temp0';
  }

  @override
  String teamUiTaskDoneAgo(String when) {
    return '$when完成';
  }

  @override
  String teamUiTaskMergedAgo(String when) {
    return '完成 · $when合并';
  }

  @override
  String teamUiTaskCancelledAgo(String when) {
    return '$when取消';
  }

  @override
  String teamUiHomeDoneMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '再显示 $count 项',
      one: '再显示 1 项',
    );
    return '$_temp0';
  }

  @override
  String get teamUiHomeSearchClose => '关闭搜索';

  @override
  String get teamUiHomeNeedsYouAnswer => '答复';

  @override
  String teamUiHomeNeedsYouAnnouncement(String question) {
    return '需要你处理：$question';
  }

  @override
  String get teamUiAgentRoleWorker => '工作者';

  @override
  String get teamUiAgentRoleReviewer => '审查者';

  @override
  String get teamUiAgentRolePlanner => '规划器';

  @override
  String get teamUiAgentRoleSupervisor => '监督者';

  @override
  String get teamUiAgentRoleHelper => '助手';

  @override
  String get teamUiAgentRoleOther => '智能体';

  @override
  String get teamUiRunStageWaiting => '已规划';

  @override
  String get teamUiRunStageWorking => '工作中';

  @override
  String get teamUiRunStageReviewing => '审查中';

  @override
  String get teamUiRunStageDone => '已合并';

  @override
  String teamUiRunStageSemantics(int position, String stage) {
    return '第 $position 阶段，共 4 个：$stage';
  }

  @override
  String get teamUiRunStepsHeading => '步骤';

  @override
  String get teamUiRunDetailsUsage => '用量';

  @override
  String get serverRowConnected => '已连接';

  @override
  String phoneServerRowStatus(String runtime, String state) {
    return '$runtime · $state';
  }

  @override
  String get phoneServerRowRestarting => '正在重启';

  @override
  String get phoneServerRowNotAnswering => '没有响应';

  @override
  String get phoneServerRowNotRunning => '未运行';

  @override
  String get phoneServerRowDetails => '详情';

  @override
  String get localAgentRowOptional => '可选';

  @override
  String get localAgentPageTitle => 'Claude Code';

  @override
  String get managedRecoveryRowTitle => '崩溃后重启';

  @override
  String get managedRecoveryRowDetail => '最多尝试 3 次，且仅在此应用打开时进行。绝不会安装或更新任何内容。';

  @override
  String pluginsTeamRowFound(String server) {
    return '在 $server 上找到';
  }

  @override
  String get pluginsLoading => '正在加载插件';

  @override
  String get pluginsBuiltinGroup => '内置';

  @override
  String pluginsBuiltinActive(int count) {
    return '$count 个已启用';
  }

  @override
  String pluginsBuiltinFailed(int count) {
    return '$count 个加载失败';
  }

  @override
  String get pluginsStatusFailedToLoad => '加载失败';

  @override
  String get pluginsDetailsId => 'ID';

  @override
  String get localTerminalSourcePhone => '内置 Linux';

  @override
  String get localTerminalSourceServer => 'OpenCode 服务器';

  @override
  String localTerminalShellName(int number) {
    return 'Shell $number';
  }

  @override
  String get localTerminalNewShell => '新建 Shell';

  @override
  String get localTerminalStopShell => '停止此 Shell';

  @override
  String get localTerminalCloseShell => '关闭此 Shell';

  @override
  String get localTerminalStopBody => '其中运行的程序也会一起停止。';

  @override
  String get localTerminalStop => '停止';

  @override
  String get localTerminalStarting => '正在启动 shell';

  @override
  String get localTerminalNotSetUpTitle => '此手机尚未设置 Linux';

  @override
  String get localTerminalNotSetUpBody => '终端运行在手机设置所安装的 Linux 中。';

  @override
  String get localTerminalEndedTitle => 'Shell 已结束';

  @override
  String localTerminalEndedBody(int code) {
    return '它以代码 $code 退出。';
  }

  @override
  String get localTerminalRestart => '重启';

  @override
  String get localTerminalFailedTitle => 'shell 未能启动';

  @override
  String get localTerminalFailedBody => '请重试。如果仍然失败，可在“详情”中查看原因。';

  @override
  String get localTerminalTryAgain => '重试';

  @override
  String localTerminalCost(int perShell, int limit) {
    return '每个 Shell 都会运行 $perShell 个程序。开启 AI Team 后，超过 $limit 个时 Android 可能会停止应用的程序。';
  }

  @override
  String localTerminalCostNow(int perShell, int count, int limit) {
    return '每个 Shell 都会运行 $perShell 个程序。开启 AI Team 后，应用共运行 $count 个；超过 $limit 个时 Android 可能会停止它们。';
  }

  @override
  String get localTerminalKeysLabel => '终端按键';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => '向上翻页';

  @override
  String get localTerminalKeyPageDown => '向下翻页';

  @override
  String get localTerminalSemantics => '此手机上的终端。点按即可输入；长按可选择文字。';

  @override
  String get phoneServerTermuxTitle => 'Termux';

  @override
  String get globalSessionsLoadFailedTitle => '无法加载对话';

  @override
  String get filesLoadFailedTitle => '无法打开此文件夹';

  @override
  String get filesSymbolsFailedTitle => '无法搜索符号';

  @override
  String get terminalListFailedTitle => '无法列出终端';

  @override
  String get addServerConnectTo => '连接到';

  @override
  String get addServerTypeOpenCode => '电脑上的 OpenCode';

  @override
  String get addServerTypeOpenCodeDetail => '使用配对码，或输入其地址';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail => '你电脑上的 Codex app-server';

  @override
  String get addServerTypePaseo => 'Claude Code 或 Pi';

  @override
  String get addServerTypePaseoDetail => '通过你电脑上的 Paseo';

  @override
  String get addServerScan => '扫描代码';

  @override
  String get addServerPaste => '粘贴代码';

  @override
  String get addServerManual => '改为输入地址';

  @override
  String get addServerChecking => '正在检查连接…';

  @override
  String addServerCheckingHost(String host) {
    return '正在检查 $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return '正在连接到 $host…';
  }

  @override
  String get addServerSaveAnyway => '仍然保存';

  @override
  String get effectsSection => '效果';

  @override
  String get effectsAnimations => '动效';

  @override
  String get effectsMotionFull => '完整';

  @override
  String get effectsMotionFullHint => '图形会动，等待页面会有呼吸效果，完成时会有庆祝效果';

  @override
  String get effectsMotionCalm => '平静';

  @override
  String get effectsMotionCalmHint => '图形直接出现，没有持续的动效，也没有庆祝效果';

  @override
  String get effectsMotionOff => '关闭';

  @override
  String get effectsMotionOffHint => '所有内容立即显示';

  @override
  String get effectsMotionSystemOff => '你手机的“移除动画”已开启，因此无论在此如何选择，都不会有动效';

  @override
  String get effectsActivityGlow => '回复时边框发光';

  @override
  String get effectsSaveFailed => '无法在此设备上保存该选项。请重试。';

  @override
  String get teamDiscoverEntryTitle => '把更大的任务交给团队';

  @override
  String get teamDiscoverIntroBody =>
      '描述你想完成的事。一个智能体团队会把它拆成步骤、并行处理，并把完成的成果并入你的项目。';

  @override
  String get teamDiscoverHowHeading => '工作方式';

  @override
  String get teamDiscoverStepPlanTitle => '它来规划';

  @override
  String get teamDiscoverStepPlanBody => '规划器把你的任务拆成步骤。';

  @override
  String get teamDiscoverStepWorkTitle => '它来执行';

  @override
  String get teamDiscoverStepWorkBody => '工作者接手这些步骤，每一步都在项目的独立副本上进行。';

  @override
  String get teamDiscoverStepCheckTitle => '它来检查';

  @override
  String get teamDiscoverStepCheckBody => '审查者逐个查看每一步的成果。';

  @override
  String get teamDiscoverStepMergeTitle => '它来合并';

  @override
  String get teamDiscoverStepMergeBody => '完成的成果并入你的项目。需要决策时，它会来问你。';

  @override
  String get teamDiscoverNeedsPhone => '本机上的要求';

  @override
  String teamDiscoverNeedsServer(String server) {
    return '$server 上的要求';
  }

  @override
  String teamDiscoverDownloadTitle(String size) {
    return '约需下载 $size';
  }

  @override
  String get teamDiscoverInAppDownloadBody => '安装一次，位于本机 OpenCode 旁边。';

  @override
  String get teamDiscoverTermuxDownloadBody => '安装到 Termux 中，位于 OpenCode 旁边。';

  @override
  String get teamDiscoverBatteryTitle => '工作时更耗电';

  @override
  String get teamDiscoverInAppBatteryBody =>
      '会有多个智能体同时运行；如果数量过多，Android 可能会停止其中一些。';

  @override
  String get teamDiscoverTermuxBatteryBody =>
      '工作时请保持 Termux 打开；Android 可能会在后台停止它。没有内容丢失。';

  @override
  String get teamDiscoverProjectTitle => '项目由你选择';

  @override
  String get teamDiscoverProjectBody => '为你希望它参与的每个项目开启。';

  @override
  String teamDiscoverComputerTitle(String server) {
    return '在 $server 上运行';
  }

  @override
  String get teamDiscoverComputerBody => '在那里安装一次 Gas City；应用会自行找到它。';

  @override
  String get teamDiscoverSpeedTitle => '速度与你的电脑相当';

  @override
  String get teamDiscoverSpeedBody => '团队工作时请保持电脑唤醒。';

  @override
  String teamDiscoverLooking(String server) {
    return '正在 $server 上查找…';
  }

  @override
  String get teamDiscoverFoundBody => '已就绪，可以开启。';

  @override
  String get teamDiscoverEnterAddress => '输入其地址';

  @override
  String get teamDiscoverOnComputer => '在电脑上运行';

  @override
  String get teamDiscoverTurningOn => '正在开启…';

  @override
  String get teamNowChecksEveryMinute => '团队每分钟检查一次';

  @override
  String teamNowChecksEvery(String minutes) {
    return '团队每 $minutes 分钟检查一次';
  }

  @override
  String get teamNowNextCheck => '工作者将在团队下次检查时启动';

  @override
  String get teamNowNoWorkerStarted => '尚无工作者启动';

  @override
  String get teamNowPausedLine => '团队已暂停 · 在你恢复之前不会启动任何内容';

  @override
  String get teamNowStartWorker => '启动一个工作者';

  @override
  String get teamNowWhy => '为什么？';

  @override
  String get teamAgentDidNotStartTitle => '工作者未能启动';

  @override
  String get teamAgentDidNotStartBody => '有任务在等待，但这个工作者未在运行。';

  @override
  String get teamAgentStartIt => '启动它';

  @override
  String teamOutputNotRunning(String name) {
    return '$name 未在运行，因此没有输出';
  }

  @override
  String get teamOutputSilent => '尚无输出 · 启动可能需要一分钟';

  @override
  String teamAgentTitle(String role, String name) {
    return '$role · $name';
  }

  @override
  String teamAgentWorksOn(String title) {
    return '正在处理“$title”';
  }

  @override
  String teamOutputStartingPhone(String age) {
    return '正在启动 · 已 $age · 在手机上可能需要几分钟';
  }

  @override
  String get chatWatchEmptyTitle => '这里还是空的';

  @override
  String get chatWatchEmptyBody => '智能体工作时，此对话会逐步填充。';

  @override
  String teamWatchBannerRole(String role, String state) {
    return '正在观看 $role · $state';
  }

  @override
  String get teamWatchFallbackUnreadable =>
      '它的对话无法从本应用所连接的服务器读取，因此这里显示团队的实时输出。';

  @override
  String get teamWatchFallbackNotFound =>
      '它的对话尚未出现在本应用所连接的服务器上（可能仍在启动，或团队运行在另一台电脑上），因此这里显示团队的实时输出。';

  @override
  String get teamOpenConversation => '打开对话';

  @override
  String teamOpenConversationHint(String name) {
    return '在聊天中观看 $name 的工作';
  }

  @override
  String get teamOpenConversationFinding => '正在查找它的对话…';

  @override
  String get teamChatUntitled => '团队任务';

  @override
  String teamChatSubtitle(String host) {
    return 'AI 团队 · $host';
  }

  @override
  String get teamChatOpenTeam => 'AI 团队';

  @override
  String get teamChatTaskDetails => '任务详情';

  @override
  String get teamChatStopTask => '停止任务';

  @override
  String get teamChatStopConfirmTitle => '停止此任务？';

  @override
  String teamChatStopConfirmBody(String task) {
    return '“$task”将在团队的电脑上停止。仍在运行的工作者会立即停止；已完成的工作会保留。从手机上无法撤销。';
  }

  @override
  String get teamChatStopKeepRunning => '继续运行';

  @override
  String get teamChatLoading => '正在加载任务';

  @override
  String get teamChatLeadName => '团队';

  @override
  String get teamChatLeadSent => '已发送给团队';

  @override
  String get teamChatLeadNothingYet => '还没有内容。团队尚未规划此任务。';

  @override
  String teamChatLeadPlanned(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已规划 $count 个步骤',
      one: '已规划 1 个步骤',
    );
    return '$_temp0';
  }

  @override
  String teamChatLeadRouted(String title) {
    return '已将“$title”发送给工作者';
  }

  @override
  String teamChatLeadStarting(String title) {
    return '工作者已开始处理“$title”';
  }

  @override
  String teamChatLeadClaimedWorker(String title) {
    return '工作者接手了“$title”';
  }

  @override
  String teamChatLeadPushed(String title) {
    return '“$title”的更改已推送到分支';
  }

  @override
  String teamChatLeadReview(String title) {
    return '已将“$title”交给审查';
  }

  @override
  String teamChatLeadMerged(String title) {
    return '已合并“$title”';
  }

  @override
  String teamChatLeadStepFailed(String title) {
    return '“$title”失败';
  }

  @override
  String teamChatLeadStepCancelled(String title) {
    return '“$title”已取消';
  }

  @override
  String teamChatLeadNeedsYou(String question) {
    return '需要你处理：$question';
  }

  @override
  String get teamChatLeadTaskMerged => '已合并。任务完成。';

  @override
  String get teamChatLeadTaskFinished => '任务已完成。';

  @override
  String get teamChatLeadTaskFailed => '任务失败。';

  @override
  String get teamChatLeadTaskCancelled => '任务已取消。';

  @override
  String get teamChatAWorker => '一个工作者';

  @override
  String teamChatStepsSummary(int count, int done) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个步骤',
      one: '1 个步骤',
    );
    return '$_temp0 · 已完成 $done';
  }

  @override
  String get teamChatComposerHint => '给团队发消息…';

  @override
  String teamChatComposerGoesTo(String name) {
    return '你的消息将发给$name';
  }

  @override
  String get teamChatComposerNobody => '此任务上暂无可以发消息的智能体。';

  @override
  String get teamChatComposerCannot => '无法从这里给此团队发消息。';

  @override
  String get teamBoardTitle => '看板';

  @override
  String get teamBoardOpenTooltip => '看板';

  @override
  String get teamBoardColumnBacklog => '待办';

  @override
  String get teamBoardColumnReady => '就绪';

  @override
  String get teamBoardColumnWorking => '进行中';

  @override
  String get teamBoardColumnReview => '审查';

  @override
  String get teamBoardColumnDone => '完成';

  @override
  String get teamBoardEmptyBacklogTitle => '没有等待中的任务';

  @override
  String get teamBoardEmptyBacklogBody => '你添加但尚未开始的任务会在这里等待。';

  @override
  String get teamBoardEmptyReadyTitle => '没有排队中的任务';

  @override
  String get teamBoardEmptyReadyBody => '交给团队的任务会在这里等待工作者。';

  @override
  String get teamBoardEmptyWorkingTitle => '没有人在工作';

  @override
  String get teamBoardEmptyWorkingBody => '工作者接手任务后，任务会移到这里。';

  @override
  String get teamBoardEmptyReviewTitle => '没有待审查的内容';

  @override
  String get teamBoardEmptyReviewBody => '完成的工作会在这里等待检查和合并。';

  @override
  String get teamBoardEmptyDoneTitle => '本周没有已完成的任务';

  @override
  String get teamBoardEmptyDoneBody => '过去 7 天内已合并、已完成和已取消的任务会显示在这里。';

  @override
  String get teamBoardEmptyTitle => '尚无任务';

  @override
  String get teamBoardEmptyBody => '你交给团队的任务会按所处阶段显示在这里。';

  @override
  String get teamBoardPriorityUrgent => '紧急';

  @override
  String get teamBoardPriorityHigh => '高';

  @override
  String get teamBoardPriorityNormal => '普通';

  @override
  String get teamBoardPriorityLow => '低';

  @override
  String get teamBoardPrioritySomeday => '以后';

  @override
  String get teamBoardTypeBug => '缺陷';

  @override
  String get teamBoardTypeFeature => '功能';

  @override
  String get teamBoardTypeEpic => '史诗';

  @override
  String get teamBoardTypeChore => '杂项';

  @override
  String teamBoardEpicProgress(int done, int total) {
    return '已完成 $done / $total';
  }

  @override
  String get teamBoardFlagNeedsYou => '需要你处理';

  @override
  String get teamBoardFlagBlocked => '已阻塞';

  @override
  String teamBoardFlagBlockedBy(String title) {
    return '被$title阻塞';
  }

  @override
  String teamBoardFlagBlockedByMore(String title, int count) {
    return '被$title等 $count 项阻塞';
  }

  @override
  String get teamBoardFlagFailed => '因错误而停止';

  @override
  String teamBoardFlagInEpic(String epic) {
    return '在$epic中';
  }

  @override
  String teamBoardFlagMoving(String column) {
    return '正在移动到$column…';
  }

  @override
  String get teamBoardFlagCancelled => '已取消';

  @override
  String get teamBoardMoveMenuTooltip => '移动或更改';

  @override
  String teamBoardMoveSheetWhere(String column) {
    return '在$column中';
  }

  @override
  String get teamBoardMoveStartNow => '立即开始';

  @override
  String get teamBoardMoveStartNowHint => '交给团队的工作者';

  @override
  String get teamBoardMoveBackToBacklog => '移回待办';

  @override
  String get teamBoardMoveBackToBacklogHint => '在你开始之前，团队不会接手';

  @override
  String get teamBoardMovePriority => '优先级';

  @override
  String get teamBoardMoveCancel => '取消任务';

  @override
  String get teamBoardMoveCancelHint => '作为已取消移入完成';

  @override
  String get teamBoardMoveReopen => '放回待办';

  @override
  String get teamBoardMoveReopenHint => '在你开始之前，没有人会处理它';

  @override
  String get teamBoardOpenConversation => '打开对话';

  @override
  String get teamBoardOpenDetails => '打开详情';

  @override
  String get teamBoardTeamMoves => '此任务由团队移动。打开其对话可给团队发消息或停止它。';

  @override
  String get teamBoardReadOnlyNote => '此主机不允许应用更改任务，因此这里的看板为只读。';

  @override
  String get teamBoardStatusReadOnly => '此处只读 · 此主机不允许应用更改任务';

  @override
  String teamBoardCancelTitle(String title) {
    return '取消“$title”？';
  }

  @override
  String get teamBoardCancelBody => '团队将不再处理它。它会作为已取消移入完成，你之后可以把它放回待办。';

  @override
  String get teamBoardCancelKeep => '保留';

  @override
  String teamBoardMoveFailedTitle(String title) {
    return '无法更改“$title”';
  }

  @override
  String get teamBoardMoveFailedBody => '团队的主机拒绝了，因此它保持原状。';

  @override
  String get teamBoardPriorityTitle => '优先级';

  @override
  String get teamBoardAddFailed => '无法添加。团队的主机拒绝了。';

  @override
  String get teamBoardProjectTooltip => '选择项目';

  @override
  String appExitForceStopped(String time) {
    return 'OpenCode Mobile 已关闭 $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'Android 为释放内存关闭了 OpenCode Mobile $time';
  }

  @override
  String appExitCrashed(String time) {
    return 'OpenCode Mobile 意外停止 $time';
  }

  @override
  String appExitKilled(String time) {
    return 'Android 已停止 OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what。手机上的 OpenCode 随之停止，正在重新启动。';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what。手机上的 OpenCode 和 AI Team 随之停止，正在重新启动。';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what。手机上的 OpenCode 随之停止。准备好后请再次启动。';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what。手机上的 OpenCode 和 AI Team 随之停止。准备好后请再次启动它们。';
  }

  @override
  String appExitServerBack(String what) {
    return '$what。手机上的 OpenCode 随之停止，现已重新运行。';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what。手机上的 OpenCode 和 AI Team 随之停止；OpenCode 现已重新运行。';
  }

  @override
  String appExitAtTime(String time) {
    return '于 $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return '于 $day $time';
  }

  @override
  String get appExitSwitchToPhone => '切换到此手机';

  @override
  String get appExitKeepRunning => '保持运行';

  @override
  String get keepRunningTitle => '在后台保持运行';

  @override
  String get keepRunningRowSubtitle => '需要允许哪些权限，手机才不会关闭应用';

  @override
  String get keepRunningIntro =>
      '手机上的 OpenCode 和 AI Team 都在此应用内运行，因此 Android 关闭应用时它们也会停止。在这部手机上，请允许以下项：';

  @override
  String get keepRunningSwipeWarning =>
      '这部手机会关闭你从最近任务中划掉的应用，即使它正在工作。请在那里锁定它，而不是划掉。';

  @override
  String get keepRunningBatteryTitle => '不优化电池';

  @override
  String get keepRunningBatteryDetail => '让应用在你使用其他应用时继续运行。';

  @override
  String get keepRunningBatteryDone => '已允许';

  @override
  String get keepRunningLockTitle => '在最近任务中锁定';

  @override
  String get keepRunningLockNubia =>
      '打开“最近任务”，将 OpenCode Mobile 的卡片向下拉，直到出现锁图标。';

  @override
  String get keepRunningLockSamsung =>
      '打开“最近任务”，点按卡片上方的 OpenCode Mobile 图标，然后选择“保持打开”。';

  @override
  String get keepRunningLockOther => '打开“最近任务”，长按 OpenCode Mobile 的卡片，然后点按锁图标。';

  @override
  String get keepRunningAutostartTitle => '允许自启动';

  @override
  String get keepRunningAutostartDetail => '为 OpenCode Mobile 开启，避免手机在后台将其停止。';

  @override
  String get keepRunningAutostartHuawei =>
      '在“应用启动管理”中，将 OpenCode Mobile 设为“手动管理”，并开启全部三个开关。';

  @override
  String get keepRunningBackgroundTitle => '允许后台活动';

  @override
  String get keepRunningBackgroundXiaomi => '在“应用信息 › 省电策略”中，选择“无限制”。';

  @override
  String get keepRunningBackgroundOppo => '在“应用信息 › 耗电管理”中，允许后台活动。';

  @override
  String get keepRunningBackgroundVivo => '在“应用信息 › 电池”中，允许后台高耗电。';

  @override
  String get keepRunningBackgroundSamsung =>
      '在“应用信息 › 电池”中，选择“无限制”，并确保应用不在“休眠应用”中。';

  @override
  String get keepRunningBackgroundOther => '在“应用信息 › 电池”中，选择“无限制”或允许后台运行。';

  @override
  String get keepRunningOpen => '打开';

  @override
  String get keepRunningOpenFailed =>
      '这部手机没有该界面。请改为打开“设置 › 应用 › OpenCode Mobile”。';

  @override
  String get keepRunningFootnote =>
      '如果 Android 仍然关闭应用，下次打开时它会重新启动你手机上的 OpenCode。';

  @override
  String teamAgentLastStep(String step) {
    return '最后一步：$step';
  }

  @override
  String teamAgentLastActive(String elapsed) {
    return '$elapsed前活跃';
  }

  @override
  String teamChatLeadEarlier(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条更早的更新',
      one: '1 条更早的更新',
    );
    return '$_temp0';
  }

  @override
  String get appExitQuietRestarted => 'Restarted after the app closed';

  @override
  String get appExitQuietRestarting => 'Restarting after the app closed';

  @override
  String get thermalQuietPaused =>
      'Phone is hot · AI Team paused until it cools';

  @override
  String get thermalQuietStopped =>
      'Phone is very hot · AI Team stopped until it cools';

  @override
  String get thermalQuietResumed => 'Phone cooled down · AI Team resumed';

  @override
  String get thermalPausedNotice => '手机过热，已暂停 AI Team 以降温。它会自动恢复。';

  @override
  String get thermalStoppedNotice =>
      '手机非常烫，已停止 AI Team 以保护设备。其工作会保留，手机降温后会再次启动。';

  @override
  String get thermalResumedNotice => '已恢复 AI Team，手机已降温。';

  @override
  String get thermalGuardSetting => '手机过热时暂停 AI Team';

  @override
  String get thermalGuardSettingDetail => '团队会暂停并保留已完成的工作，待手机降温后自动恢复。';

  @override
  String get kitSheetClose => '关闭';

  @override
  String get kitSheetDismiss => '关闭';

  @override
  String get kitSheetLoading => '正在加载';

  @override
  String get kitConfirmCancel => '取消';

  @override
  String get kitConfirmKeepRunning => '保持运行';

  @override
  String get kitConfirmKeepEditing => '继续编辑';

  @override
  String kitConfirmTypeName(String name) {
    return '输入 $name 以确认';
  }

  @override
  String get kitConfirmTypeNameReason => '请按所示原样输入名称以启用。';

  @override
  String get kitConfirmFailed => '没有完成，你可以再试一次。';

  @override
  String get kitTryAgain => '重试';

  @override
  String get kitDetails => '详情';

  @override
  String get kitDiscardTitle => '要丢弃你的更改吗？';

  @override
  String get kitDiscardBody => '你在这里所做的更改尚未保存。丢弃后无法恢复。';

  @override
  String get kitDiscardConfirm => '丢弃更改';

  @override
  String get kitCopied => '已复制';

  @override
  String get kitMore => '更多';

  @override
  String kitChipRemove(String label) {
    return '移除 $label';
  }

  @override
  String get kitCopy => '复制';

  @override
  String get kitWorking => '处理中';

  @override
  String get kitImageUnavailable => '无法显示此图片';

  @override
  String get kitZoomIn => '放大';

  @override
  String get kitZoomOut => '缩小';

  @override
  String get kitZoomReset => '重置缩放';

  @override
  String get kitZoomFit => '适应屏幕';

  @override
  String get kitZoomAtStart => '已是完整视图';

  @override
  String get kitZoomAtMax => '已是最大缩放';

  @override
  String kitZoomLevel(String percent) {
    return '$percent %';
  }

  @override
  String kitZoomShortcut(String action, String key) {
    return '$action · Ctrl+$key';
  }

  @override
  String get kitMenu => '菜单';

  @override
  String get kitCopyDetails => '复制详情';

  @override
  String get kitReportProblem => '报告问题';

  @override
  String kitProgressStep(int step, int of) {
    final intl.NumberFormat stepNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String stepString = stepNumberFormat.format(step);
    final intl.NumberFormat ofNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String ofString = ofNumberFormat.format(of);

    return '第 $stepString 步，共 $ofString 步';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: '约剩 $secondsString 秒',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaMinutes(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '约剩 $minutesString 分钟',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: '约剩 $hoursString 小时',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong => '内容太长，无法生成二维码。请改为复制链接。';

  @override
  String kitSinceStillWaiting(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    return '已等待 $secondsString 秒，仍在等待';
  }

  @override
  String kitSinceWaitingFor(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '已等待 $minutesString 分钟',
      one: '已等待 1 分钟',
      zero: '等待不到一分钟',
    );
    return '$_temp0';
  }

  @override
  String kitSinceAge(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutesString 分钟',
      one: '1 分钟',
      zero: '不到一分钟',
    );
    return '$_temp0';
  }

  @override
  String get kitMarkWaiting => '等待中';

  @override
  String get kitMarkWorking => '工作中';

  @override
  String get kitMarkDone => '已完成';

  @override
  String get kitMarkFailed => '失败';

  @override
  String get kitMarkPaused => '已暂停';

  @override
  String get kitTaskNeedsYou => '需要你处理';

  @override
  String get kitTaskStopped => '已停止';

  @override
  String get kitSwatchInUse => '使用中';

  @override
  String get kitThemePreviewTitle => '修复登录错误';

  @override
  String get kitThemePreviewWorking => '进行中 · 2 分钟';

  @override
  String get kitThemePreviewNeedsYou => '需要你处理';

  @override
  String get kitThemePreviewPrimary => '发送';

  @override
  String get kitThemePreviewSecondary => '附加';

  @override
  String get kitThemePreviewSegment => '开';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => '斜杠键';

  @override
  String get kitTerminalViewKeyDash => '连字符键';

  @override
  String get kitTerminalViewKeyPipe => '竖线键';

  @override
  String get kitTerminalViewKeyTilde => '波浪号键';

  @override
  String get kitTerminalViewKeyHome => 'Home 键';

  @override
  String get kitTerminalViewKeyEnd => 'End 键';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示最后 $countString 行',
      one: '显示最后一行',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => '撤销';

  @override
  String kitUndoFailed(String message) {
    return '无法撤销。$message';
  }

  @override
  String get kitUndoWorking => '正在撤销';

  @override
  String get kitTermHint => '有可用说明';

  @override
  String get kitTermShow => '显示说明';

  @override
  String get kitTermClose => '关闭说明';

  @override
  String kitFieldShowNamed(String label) {
    return '显示$label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return '隐藏$label';
  }

  @override
  String get kitFieldPaste => '粘贴';

  @override
  String get kitFieldSaved => '已保存';

  @override
  String get kitFieldReplace => '替换';

  @override
  String get kitFieldChecking => '正在检查…';

  @override
  String kitFieldStillChecking(int seconds) {
    return '已检查 $seconds 秒，仍在进行';
  }

  @override
  String kitFieldCount(int count, int max) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);
    final intl.NumberFormat maxNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String maxString = maxNumberFormat.format(max);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString/$maxString',
      one: '1/$maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => '已达上限';

  @override
  String get kitFieldErrorLabel => '错误';

  @override
  String get kitTappableShowActions => '显示操作';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '读取了 $count 个文件',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '搜索了 $count 次',
    );
    return '$_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '列出了 $count 个文件夹',
    );
    return '$_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '编辑了 $count 个文件',
    );
    return '$_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '运行了 $count 条命令',
    );
    return '$_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '获取了 $count 个网页',
    );
    return '$_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '委派了 $count 个任务',
    );
    return '$_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '其他 $count 个步骤',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个未运行',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个步骤',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => '等待你处理';

  @override
  String get kitWorkStopped => '已停止';

  @override
  String get kitWorkDidntFinish => '未完成';

  @override
  String get kitWorkWorking => '工作中';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示更早的 $count 个步骤',
    );
    return '$_temp0';
  }

  @override
  String get kitReceiptSending => '正在发送…';

  @override
  String get kitReceiptSent => '已发送';

  @override
  String get kitReceiptConfirmed => '已完成';

  @override
  String get kitReceiptNotConfirmed => '尚未确认';

  @override
  String get kitReceiptRefused => '未被接受';

  @override
  String kitReceiptRefusedReason(String reason) {
    return '未被接受：$reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return '已在 $where 上回答';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown => '已在另一台设备上回答';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act：$reason';
  }

  @override
  String kitReceiptAt(String time) {
    return '于 $time';
  }

  @override
  String get kitDetailsHide => '隐藏详情';

  @override
  String get kitCopyAll => '全部复制';

  @override
  String kitCopyValue(String label) {
    return '复制$label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示全部 $count 行',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label：$value';
  }

  @override
  String get kitProgressRowLoading => '加载中';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString%';
  }

  @override
  String get kitProgressRowNearLimit => '接近上限';

  @override
  String get kitProgressRowAtLimit => '已达上限';

  @override
  String kitProgressRowAsOf(String time) {
    return '截至 $time';
  }

  @override
  String get kitProgressRowOther => '其他';

  @override
  String get kitModelServerDefault => '服务器默认';

  @override
  String get kitModelSignIn => '登录以使用模型';

  @override
  String get kitModelChoose => '选择模型';

  @override
  String get kitModelChange => '更换模型';

  @override
  String get kitModelActions => '模型快捷操作';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String kitAttachmentOpen(String label) {
    return '预览 $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return '图片，$label';
  }

  @override
  String kitAttachmentFile(String label) {
    return '文件，$label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return '文件夹，$label';
  }

  @override
  String kitAttachmentReference(String label) {
    return '引用，$label';
  }

  @override
  String get kitSuggestionsShowAll => '显示全部';

  @override
  String get kitSuggestionsLabel => '建议';

  @override
  String get kitNeedsYouReasonDecision => '需要你来决定';

  @override
  String get kitNeedsYouReasonBlocked => '卡住了：需要你处理';

  @override
  String get kitNeedsYouReasonConsent => '需要你确认';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString 项需要你处理 · ',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouBadgeSuffix(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '，$countString 项需要你处理',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return '已等待 $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '已等待 $minutesString 分钟',
      one: '已等待 1 分钟',
      zero: '已等待不到一分钟',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$server 上的 $who';
  }

  @override
  String get kitWorkGraph => '工作图';

  @override
  String get kitWorkGraphEmpty => '尚无工作项';

  @override
  String kitWorkGraphNode(String title, String state) {
    return '$title，$state';
  }

  @override
  String kitWorkGraphNeeds(String title) {
    return '需要 $title';
  }

  @override
  String kitWorkGraphNeedsMore(String title, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: '1',
    );
    return '需要 $title 等 $_temp0 项';
  }

  @override
  String get kitJumpLatest => '跳到最新';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条新内容 · 跳到最新',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => '当前';

  @override
  String get kitChoiceRecommended => '推荐';

  @override
  String get kitChoiceOtherSend => '发送回答';

  @override
  String kitChoiceSelectedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已选 $count 项',
      one: '已选 1 项',
      zero: '未选择',
    );
    return '$_temp0';
  }

  @override
  String get kitComposerField => '消息';

  @override
  String get kitComposerSend => '发送';

  @override
  String get kitComposerSending => '正在发送';

  @override
  String get kitComposerSendOffline => '联网后发送';

  @override
  String get kitComposerSendAfter => '回复结束后发送';

  @override
  String get kitComposerAddToTurn => '补充到当前任务';

  @override
  String get kitComposerStop => '停止回复';

  @override
  String get kitComposerSendsAfter => '将在回复结束后发送';

  @override
  String get kitComposerStopAndSend => '停止并发送';

  @override
  String get kitComposerStopAndSendNote => '停止当前回复，并用你的消息重新开始。';

  @override
  String get kitComposerCannotSendYet => '回复结束后即可发送';

  @override
  String get kitComposerTools => '附件及更多';

  @override
  String get kitComposerVoice => '语音输入';

  @override
  String get kitComposerEditor => '打开全屏编辑器';

  @override
  String get kitVoiceLeave => '退出语音模式';

  @override
  String get kitVoiceStarting => '正在准备麦克风…';

  @override
  String get kitVoiceListening => '正在聆听…';

  @override
  String get kitVoiceTranscribing => '正在转写你说的话…';

  @override
  String get kitVoiceWaitingReply => '正在等待回复…';

  @override
  String get kitVoiceSpeaking => '正在朗读回复';

  @override
  String get kitVoiceReplyReady => '回复已就绪';

  @override
  String get kitVoicePaused => '已暂停 · 智能体需要你';

  @override
  String get kitVoiceMicDenied => '此应用的麦克风已关闭';

  @override
  String get kitVoiceFailed => '语音已停止';

  @override
  String get kitVoiceSend => '发送';

  @override
  String get kitVoiceDone => '完成';

  @override
  String get kitVoiceStopReading => '停止朗读';

  @override
  String get kitVoiceReadReply => '朗读';

  @override
  String get kitVoiceListen => '聆听';

  @override
  String get kitVoiceReadAloud => '朗读回复';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => '清除搜索';

  @override
  String get kitSearchFilter => '筛选';

  @override
  String kitSearchFilterActive(String name) {
    return '筛选：$name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个结果',
      one: '1 个结果',
      zero: '无结果',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return '已加载 $count 项 · 正在搜索服务器…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return '没有与$query匹配的内容';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return '$what中没有与$query匹配的内容';
  }

  @override
  String get kitTopBarBack => '返回';

  @override
  String get kitErrorFallbackMessage =>
      'This part of OpenCode hit an error. Go back and try again, or open App diagnostics for details.';

  @override
  String get kitTopBarClose => '关闭';

  @override
  String get kitTopBarSearch => '搜索';

  @override
  String get kitTopBarSwitchServer => '切换服务器';

  @override
  String get kitTopBarSwitchProject => '切换项目';

  @override
  String get kitTopBarMore => '更多操作';

  @override
  String get kitAgentStripLabel => '此任务的智能体';

  @override
  String kitAgentOpen(String name) {
    return '打开 $name 的对话';
  }

  @override
  String kitAgentLabel(String hasRole, String name, String role, String state) {
    String _temp0 = intl.Intl.selectLogic(hasRole, {
      'yes': '$name，$role，$state',
      'other': '$name，$state',
    });
    return '$_temp0';
  }

  @override
  String get kitBreadcrumb => '文件夹路径';

  @override
  String kitBreadcrumbOpen(String folder) {
    return '打开文件夹 $folder';
  }

  @override
  String kitBreadcrumbOpenRoot(String root) {
    return '打开 $root';
  }

  @override
  String kitBreadcrumbCurrent(String folder) {
    return '当前文件夹：$folder';
  }

  @override
  String kitBreadcrumbMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '另有 $count 个文件夹',
      one: '另有 1 个文件夹',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeCopyCode => '复制代码';

  @override
  String get kitCodeCopyCommand => '复制命令';

  @override
  String get kitCodeCopyOutput => '复制输出';

  @override
  String get kitCodeCopyFailedCode => '无法复制代码。请重试。';

  @override
  String get kitCodeCopyFailedCommand => '无法复制命令。请重试。';

  @override
  String get kitCodeCopyFailedOutput => '无法复制输出。请重试。';

  @override
  String kitCodeShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示全部 $count 行',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => '打开完整输出';

  @override
  String get kitWrapLines => '自动换行';

  @override
  String kitCodeChanges(int added, int removed) {
    return '新增 $added，删除 $removed';
  }

  @override
  String get kitCodeEmpty => '空';

  @override
  String kitTabLabel(String label, int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$label，$countString';
  }

  @override
  String kitQueuedTitle(int count) {
    return '等待发送 · $count';
  }

  @override
  String get kitQueuedOffline => '恢复联网后发送';

  @override
  String get kitQueuedWaiting => '等待发送';

  @override
  String get kitQueuedReachedServer => '已送达服务器';

  @override
  String get kitQueuedAfterReply => '在此回复后发送';

  @override
  String get kitQueuedAddToTurn => '将加入当前轮次';

  @override
  String get kitQueuedUpdate => '更新等待中';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个附件',
      one: '1 个附件',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return '第 $index 条等待中的消息，共 $count 条：$text。$state';
  }

  @override
  String get kitQueuedActions => '消息操作';

  @override
  String get kitLogTitle => '输出';

  @override
  String get kitLogShowOutput => '显示输出';

  @override
  String get kitLogLive => '实时';

  @override
  String kitLogQuiet(String age) {
    return '最后一行为$age前';
  }

  @override
  String kitLogQuietSeconds(int seconds) {
    return '最后一行为 $seconds 秒前';
  }

  @override
  String get kitLogEnded => '已结束';

  @override
  String kitLogEndedExit(String code) {
    return '已结束 · 退出码 $code';
  }

  @override
  String get kitLogFailed => '失败';

  @override
  String kitLogFailedExit(String code) {
    return '失败 · 退出码 $code';
  }

  @override
  String get kitLogEmpty => '尚无输出';

  @override
  String kitLogNewLines(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行新增',
      one: '1 行新增',
    );
    return '$_temp0';
  }

  @override
  String kitLogDropped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '另有 $count 行未显示',
      one: '另有 1 行未显示',
    );
    return '$_temp0';
  }

  @override
  String get kitLogReadFailed => '无法读取输出';

  @override
  String kitLogWarningLine(String line) {
    return '警告：$line';
  }

  @override
  String kitLogErrorLine(String line) {
    return '错误：$line';
  }

  @override
  String get kitUntilOff => '直到我关闭';

  @override
  String get kitUntilConversation => '本次对话期间';

  @override
  String get kitUntilHour => '一小时';

  @override
  String get kitRiskTurnOn => '开启';

  @override
  String get kitRiskNotNow => '暂不';

  @override
  String get kitRiskTurnOff => '关闭';

  @override
  String get safetyDisconnectBody => '实时更新将停止，你会返回服务器列表。服务器继续运行，其上的内容不会有任何改变。';

  @override
  String get safetyDisconnectBodyPhone =>
      '实时更新将停止，你会返回服务器列表。OpenCode 会继续在此手机上运行并消耗电量，直到你停止它。';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '有 $count 条待发送的消息会保留在此手机上，直到你重新连接。',
      one: '有 1 条待发送的消息会保留在此手机上，直到你重新连接。',
    );
    return '$_temp0';
  }

  @override
  String get folderBrowserSlowTitle => '仍在读取此文件夹';

  @override
  String get folderBrowserSlowBody => '列出此手机上的文件夹最多可能需要 15 秒。';

  @override
  String get folderBrowserFirstProject => '为你的第一个项目命名';

  @override
  String kitChecklistNext(String step) {
    return '下一步：$step';
  }

  @override
  String kitChecklistNeedsYou(String action) {
    return '需要你处理，$action';
  }

  @override
  String get kitChecklistShowSteps => '显示步骤';

  @override
  String get kitChecklistHideSteps => '隐藏步骤';

  @override
  String get kitRequestAllowOnce => '允许一次';

  @override
  String get kitRequestReject => '拒绝';

  @override
  String get kitRequestApprove => '批准';

  @override
  String get kitRequestSendBack => '退回';

  @override
  String get kitRequestAnswer => '回答';

  @override
  String get kitRequestSend => '发送';

  @override
  String get kitRequestReplyEmptyReason => '请先输入回复。';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '还有 $countString 个回答',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => '已过期 · 智能体不再等待';

  @override
  String kitRequestAge(String age) {
    return '已等待 $age';
  }

  @override
  String kitDiffFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件',
      one: '1 个文件',
    );
    return '$_temp0';
  }

  @override
  String kitDiffChangeOf(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '第 $indexString 处更改，共 $countString 处';
  }

  @override
  String get kitDiffPreviousChange => '上一处更改';

  @override
  String get kitDiffNextChange => '下一处更改';

  @override
  String kitDiffLines(int start, int end) {
    final intl.NumberFormat startNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String startString = startNumberFormat.format(start);
    final intl.NumberFormat endNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String endString = endNumberFormat.format(end);

    return '第 $startString–$endString 行';
  }

  @override
  String get kitDiffHideUnchanged => '隐藏未更改的行';

  @override
  String get kitDiffNoChanges => '无更改';

  @override
  String get kitDiffBinary => '二进制文件 · 不予显示';

  @override
  String kitDiffRenamed(String path) {
    return '重命名自 $path';
  }

  @override
  String get kitDiffAddedFile => '新文件';

  @override
  String get kitDiffDeletedFile => '已删除';

  @override
  String kitDiffTooBig(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return '显示 $shownString / $totalString 行';
  }

  @override
  String get kitDiffOpenAll => '全部打开';

  @override
  String kitDiffLineAdded(int number) {
    return '已添加第 $number 行';
  }

  @override
  String kitDiffLineRemoved(int number) {
    return '已删除第 $number 行';
  }

  @override
  String kitDiffLinesAdded(int start, int end) {
    return 'Lines $start–$end added';
  }

  @override
  String kitDiffLinesRemoved(int start, int end) {
    return 'Lines $start–$end removed';
  }

  @override
  String get kitDiffShowSource => 'Source';

  @override
  String get kitDiffShowFormatted => 'Formatted';

  @override
  String get kitDiffComment => '评论';

  @override
  String get kitDiffAddToPrompt => '添加到提示词';

  @override
  String get kitDiffCopyLines => '复制这些行';

  @override
  String get kitDiffClearSelection => '清除选择';

  @override
  String kitDiffSelected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已选 $count 行',
      one: '已选 1 行',
    );
    return '$_temp0';
  }

  @override
  String kitDiffCounts(int added, int removed) {
    return '新增 $added，删除 $removed';
  }

  @override
  String get kitDiffLoadFailed => '无法加载更改';

  @override
  String kitDiffLine(int number) {
    return '第 $number 行';
  }

  @override
  String kitBoardLane(String column, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个任务',
      one: '1 个任务',
      zero: '无任务',
    );
    return '$column，$_temp0';
  }

  @override
  String kitBoardLaneLoading(String column) {
    return '正在加载$column';
  }

  @override
  String get kitMarkdownOpenFile => '打开文件';

  @override
  String kitMarkdownTable(int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '表格，$rows 行',
      one: '表格，1 行',
    );
    return '$_temp0';
  }

  @override
  String get kitToolNotRun => '未运行';

  @override
  String get kitToolWaiting => '等待中';

  @override
  String get kitToolRunning => '运行中';

  @override
  String get kitToolWaitingForYou => '等待你操作';

  @override
  String get kitToolDone => '完成';

  @override
  String get kitToolFailed => '失败';

  @override
  String get kitToolDeclined => '你已拒绝';

  @override
  String get kitToolStopped => '已停止';

  @override
  String get kitToolBackground => '已在后台启动';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString 秒',
      one: '1 秒',
    );
    return '$_temp0';
  }

  @override
  String kitToolTookMinutes(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString 分钟',
      one: '1 分钟',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => '打开其对话';

  @override
  String get kitViewerFind => '在文件中查找';

  @override
  String kitViewerFindCount(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '第 $indexString 项，共 $countString 项';
  }

  @override
  String get kitViewerFindNone => '无匹配项';

  @override
  String get kitViewerFindPrevious => '上一个匹配';

  @override
  String get kitViewerFindNext => '下一个匹配';

  @override
  String get kitViewerFindClose => '关闭查找';

  @override
  String get kitViewerCopyContents => '复制内容';

  @override
  String get kitViewerShowSource => '显示源码';

  @override
  String get kitViewerEmpty => '此文件为空';

  @override
  String kitViewerTruncated(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return '显示前 $shownString / $totalString 行';
  }

  @override
  String get kitViewerPartial => '正在显示此文件的一部分';

  @override
  String get kitViewerOpenAll => '全部打开';

  @override
  String get kitViewerCantShow => '无法显示此文件';

  @override
  String kitViewerCantShowBody(String type, String size) {
    return '$type · $size';
  }

  @override
  String get kitViewerUnknownType => '未知类型';

  @override
  String get kitViewerUnknownSize => '大小未知';

  @override
  String kitViewerLoadFailed(String name) {
    return '无法打开 $name';
  }

  @override
  String kitViewerPage(int page, int count) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '第 $pageString 页，共 $countString 页';
  }

  @override
  String kitViewerPageFailed(int page) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);

    return '无法显示第 $pageString 页';
  }

  @override
  String get kitCapServerAnyTitle => '要使用的服务器';

  @override
  String get kitCapServerAnyWhy => '还没有服务器。请在本机设置一个，或连接一台电脑。';

  @override
  String get kitCapServerAnyEnable => '添加服务器';

  @override
  String get kitCapServerAnyOffer => '添加服务器后即可与智能体开始工作。';

  @override
  String get kitCapServerOc2Title => 'OpenCode 2 服务器';

  @override
  String get kitCapServerOc2Why => '需要运行 OpenCode 2 的服务器。';

  @override
  String get kitCapServerOc2Enable => '连接 OpenCode 2';

  @override
  String get kitCapServerOc2Offer => '这需要 OpenCode 2。要将本机的服务器切换过去吗？';

  @override
  String get kitCapServerCodexTitle => 'Codex 服务器';

  @override
  String get kitCapServerCodexWhy => '需要运行 Codex 的电脑。';

  @override
  String get kitCapServerCodexEnable => '连接 Codex';

  @override
  String get kitCapServerCodexOffer => '连接一台运行 Codex 的电脑，即可在此使用。';

  @override
  String get kitCapServerPaseoTitle => 'Claude Code 或 Pi';

  @override
  String get kitCapServerPaseoWhy => '需要 Paseo，可在电脑上或本机上。';

  @override
  String get kitCapServerPaseoEnable => '连接 Paseo';

  @override
  String get kitCapServerPaseoOffer => '使用 Claude Code 或 Pi。要连接 Paseo 吗？';

  @override
  String get kitCapPhoneBuiltinTitle => '本机上的服务器';

  @override
  String get kitCapPhoneBuiltinWhy => '本机尚无自己的服务器。';

  @override
  String get kitCapPhoneBuiltinEnable => '设置本机';

  @override
  String get kitCapPhoneBuiltinOffer => '直接在本机运行智能体。要设置吗？';

  @override
  String get kitCapPhoneTermuxTitle => 'Termux 中的服务器';

  @override
  String get kitCapPhoneTermuxWhy => '需要本机安装 Termux。';

  @override
  String get kitCapPhoneTermuxEnable => '使用 Termux 设置';

  @override
  String get kitCapPhoneTermuxOffer => '改为在 Termux 中运行本机的服务器？';

  @override
  String get kitCapPhoneAnyTitle => '本机上的服务器';

  @override
  String get kitCapPhoneAnyWhy => '需要在本机上运行的服务器。';

  @override
  String get kitCapModelAuthTitle => '模型登录';

  @override
  String get kitCapModelAuthWhy => '登录模型提供商，智能体才能回复。';

  @override
  String get kitCapModelAuthEnable => '登录模型';

  @override
  String get kitCapModelAuthOffer => '智能体需要模型才能回复。要登录一个吗？';

  @override
  String get kitCapTeamOnTitle => 'AI 团队';

  @override
  String get kitCapTeamOnWhy => '此服务器上的 AI 团队已关闭。';

  @override
  String get kitCapTeamOnEnable => '开启 AI 团队';

  @override
  String get kitCapTeamOnOffer => '此服务器还可以运行 AI 团队。要开启吗？';

  @override
  String get kitCapTeamPhoneTitle => '本机的 AI 团队';

  @override
  String get kitCapTeamPhoneWhy => '仅在本机自己的服务器上运行。';

  @override
  String get kitCapTeamControlTitle => '团队控制';

  @override
  String get kitCapTeamControlWhy => '请在运行团队的电脑上应答。';

  @override
  String get kitCapTeamControlEnable => '查看如何设置';

  @override
  String get kitCapTeamControlOffer => '电脑设置完成后即可从这里控制团队。要查看方法吗？';

  @override
  String get kitCapClaudeLocalTitle => '本机的 Claude Code';

  @override
  String get kitCapClaudeLocalWhy => '需要本机安装 Termux 并订阅 Claude。';

  @override
  String get kitCapClaudeLocalEnable => '添加 Claude Code';

  @override
  String get kitCapClaudeLocalOffer => '要在本机添加 Claude Code 吗？需要 Claude 订阅。';

  @override
  String get kitCapVoiceModelTitle => '语音输入';

  @override
  String get kitCapVoiceModelWhy => '需要本机有语音模型。';

  @override
  String get kitCapVoiceModelEnable => '下载语音模型';

  @override
  String get kitCapVoiceModelOffer => '在本机用语音输入。要下载语音模型吗？';

  @override
  String get kitCapMcpAnyTitle => '额外工具';

  @override
  String get kitCapMcpAnyWhy => '此服务器无法通过应用添加额外工具。';

  @override
  String get kitCapMcpAnyEnable => '添加工具';

  @override
  String get kitCapMcpAnyOffer => '给智能体更多工具。要添加吗？';

  @override
  String get kitCapProjectOpenTitle => '项目';

  @override
  String get kitCapProjectOpenWhy => '请先选择要工作的文件夹。';

  @override
  String get kitCapProjectOpenEnable => '选择项目';

  @override
  String get kitCapProjectOpenOffer => '选择项目文件夹以开始工作。';

  @override
  String get kitCapProjectGitTitle => 'Git 项目';

  @override
  String get kitCapProjectGitWhy => '此文件夹还不是 Git 项目。';

  @override
  String get kitCapProjectGitEnable => '将此设为 Git 项目';

  @override
  String get kitCapProjectGitOffer => '这需要 Git 项目。要把此文件夹设为 Git 项目吗？';

  @override
  String get kitCapPermNotificationsTitle => '通知';

  @override
  String get kitCapPermNotificationsWhy => '本应用的通知已关闭。';

  @override
  String get kitCapPermNotificationsEnable => '允许通知';

  @override
  String get kitCapPermNotificationsOffer => '在智能体需要你或完成时收到提醒。要允许通知吗？';

  @override
  String get kitCapPermBatteryTitle => '后台运行';

  @override
  String get kitCapPermBatteryWhy => '应用在后台时，Android 可能会停止它。';

  @override
  String get kitCapPermBatteryEnable => '允许后台运行';

  @override
  String get kitCapPermBatteryOffer => '应用关闭后仍让智能体继续运行？';

  @override
  String get kitCapPermCameraTitle => '相机';

  @override
  String get kitCapPermCameraWhy => '本应用的相机访问已关闭。';

  @override
  String get kitCapPermCameraEnable => '允许相机';

  @override
  String get kitCapPermCameraOffer => '扫描配对码并添加照片。要允许使用相机吗？';

  @override
  String get kitCapPermMicTitle => '麦克风';

  @override
  String get kitCapPermMicWhy => '本应用的麦克风访问已关闭。';

  @override
  String get kitCapPermMicEnable => '允许麦克风';

  @override
  String get kitCapPermMicOffer => '用语音说出提示词。要允许使用麦克风吗？';

  @override
  String get kitCapNetworkTailscaleTitle => '随处访问';

  @override
  String get kitCapNetworkTailscaleWhy => '你的手机和电脑不在同一网络。';

  @override
  String get kitCapNetworkTailscaleEnable => '设置 Tailscale';

  @override
  String get kitCapNetworkTailscaleOffer => '使用 Tailscale 可从任何地方访问你的电脑。要设置吗？';

  @override
  String get kitCapQuotaCollectorTitle => '剩余用量';

  @override
  String get kitCapQuotaCollectorWhy => '此服务器不报告套餐的剩余量。';

  @override
  String get kitCapQuotaCollectorEnable => '查看如何添加';

  @override
  String get kitCapQuotaCollectorOffer => '在此查看套餐的剩余量。要在服务器上添加吗？';

  @override
  String get kitCapAgentA2aTitle => '其他智能体';

  @override
  String get kitCapAgentA2aWhy => '尚未添加其他智能体。';

  @override
  String get kitCapAgentA2aEnable => '添加智能体';

  @override
  String get kitCapAgentA2aOffer => '与其他应用中的智能体协作。要添加吗？';

  @override
  String get kitCapFlagFileBrowsingTerminalTitle => '文件与终端';

  @override
  String get kitCapFlagFileBrowsingTerminalWhy => '此服务器不共享其文件或终端。';

  @override
  String get kitCapFlagSessionDiffTitle => '审查更改';

  @override
  String get kitCapFlagSessionDiffWhy => '此服务器不显示智能体所做的更改。';

  @override
  String get kitCapFlagServerCatalogTitle => '服务器设置';

  @override
  String get kitCapFlagServerCatalogWhy => '此服务器不共享其提供商、工具或命令。';

  @override
  String get kitCapFlagUsageStatisticsTitle => '花费';

  @override
  String get kitCapFlagUsageStatisticsWhy => '此服务器不报告已花费的金额。';

  @override
  String get kitCapFlagStagedRevertSessionNotesTitle => '笔记与逐步撤销';

  @override
  String get kitCapFlagStagedRevertSessionNotesWhy => '此服务器无法为智能体记录笔记，也无法逐步撤销。';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle =>
      '隔离任务与分享';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy =>
      '此服务器无法运行隔离任务或分享对话。';

  @override
  String get kitCapFlagDevelopmentServicesTitle => '开发服务';

  @override
  String get kitCapFlagDevelopmentServicesWhy => '此服务器无法启动或停止开发服务。';

  @override
  String get kitCapFlagRemoteUpgradeTitle => '更新服务器';

  @override
  String get kitCapFlagRemoteUpgradeWhy => '此服务器无法从应用中更新。';

  @override
  String get kitCapFlagPromptAttachmentsTitle => '附加照片和文件';

  @override
  String get kitCapFlagPromptAttachmentsWhy => '此服务器无法随提示词附加照片或文件。';

  @override
  String get kitHostThisPhone => '本机';

  @override
  String get kitHostTermux => 'Termux';

  @override
  String get kitHostOpenCode2 => '装有 OpenCode 2 的电脑';

  @override
  String get kitHostCodex => 'Codex';

  @override
  String get kitHostPaseo => 'Paseo';

  @override
  String get kitHostDemo => '离线演示';

  @override
  String get kitHostOpenCode => '装有 OpenCode 的电脑';

  @override
  String kitCapNotOnHost(int count, String feature, String host) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$feature在$host上不可用',
      one: '$feature在$host上不可用',
    );
    return '$_temp0';
  }

  @override
  String kitCapWorksOn(String hosts) {
    return '适用于$hosts';
  }

  @override
  String kitCapWhyElsewhere(String notHere, String worksOn) {
    return '$notHere。$worksOn。';
  }

  @override
  String kitCapAnd(String first, String last) {
    return '$first和$last';
  }

  @override
  String kitCapComma(String first, String next) {
    return '$first、$next';
  }

  @override
  String kitCapServerOnHost(String server, String host) {
    return '$server（$host）';
  }

  @override
  String get kitCapNotNow => '暂不';

  @override
  String get desktopDropHint => '拖入以附加';

  @override
  String get searchClaudeCodeGateTitle => '此设备上不可用';

  @override
  String get searchClaudeCodeGateDevice =>
      'Claude Code 只能通过 Termux 在手机上运行，而此设备没有 Termux。请在装有 Paseo 的电脑上运行，并把那台电脑添加为服务器。';

  @override
  String get searchClaudeCodeGateDesktop =>
      '此手机上的 Claude Code 适用于装有 Termux 的 Android 手机。在电脑上请用 Paseo 运行 Claude Code，并将其添加为服务器。';

  @override
  String get searchClaudeCodeGateServers => '打开服务器';

  @override
  String get activitySendOffline => '请重新连接服务器后再回答。';

  @override
  String get activityAnswerEveryQuestion => '请先回答所有问题。';

  @override
  String get activityQuestionOptional => '可选。可以留空。';

  @override
  String get activitySending => '正在发送…';

  @override
  String activityQuestionProgress(int index, int total) {
    return '问题 $index/$total';
  }

  @override
  String get activityOwnAnswer => '或者写下你自己的回答';

  @override
  String get shortcutsPaletteSearch => 'Search or go to…';

  @override
  String get shortcutsHelpAnywhere => '任意位置';

  @override
  String get shortcutsHelpConversation => '在对话中';

  @override
  String get homeShellProjectUnavailable => '文件不可用';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return '$server 不提供文件、变更或代码搜索。请连接到 OpenCode 服务器以使用这些功能。';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return '$server 没有项目工具。';
  }

  @override
  String get workspaceContextFolder => '文件夹';

  @override
  String get workspaceSessionSharedLink => '共享链接';

  @override
  String get managedWorkspacesRefresh => '刷新';

  @override
  String get managedWorkspacesDiscovered => '发现完成';

  @override
  String get managedWorkspacesDiscoverFailed => '无法发现环境';

  @override
  String get managedWorkspacesCreateFailed => '无法创建环境';

  @override
  String managedWorkspacesOpenFailed(String name) {
    return '无法打开 $name';
  }

  @override
  String managedWorkspacesRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String managedWorkspacesRemoveBody(String provider) {
    return '服务器会要求 $provider 删除此环境及其中的内容。';
  }

  @override
  String get managedWorkspacesRemoveLeavesFirst => '它当前正被打开，因此应用会先返回项目文件夹。';

  @override
  String get managedWorkspacesRemoveHistoryStays => '对话仍保留在历史记录中，但无法再打开该环境。';

  @override
  String get managedWorkspacesRemoveAction => '移除';

  @override
  String managedWorkspacesRemoved(String name) {
    return '$name 已被移除';
  }

  @override
  String get managedWorkspacesProvider => '提供商';

  @override
  String get managedWorkspacesProvidersFailed => '无法加载提供商';

  @override
  String get managedWorkspacesNoProviderTitle => '未设置提供商';

  @override
  String get managedWorkspacesNoProviderBody =>
      '此服务器没有云环境提供商。请在服务器上的 OpenCode 配置中添加一个，然后刷新。';

  @override
  String managedWorkspacesEmptyBody(String project) {
    return '$project 的环境会显示在这里。创建一个，或发现提供商已有的环境。';
  }

  @override
  String get managedWorkspacesLoadFailed => '无法加载云环境';

  @override
  String get managedWorkspacesCreating => '正在创建云环境';

  @override
  String get managedWorkspacesCreatingBody => '通常需要几分钟。就绪后会在此打开。';

  @override
  String get managedWorkspacesCreateTakes => '创建通常需要几分钟。就绪后会在此打开。';

  @override
  String get managedWorkspacesBranchLabel => '分支';

  @override
  String get managedWorkspacesBranchHelper => '留空则使用提供商的默认分支。';

  @override
  String get managedWorkspacesInUse => '使用中';

  @override
  String get managedWorkspacesCopyId => '复制 ID';

  @override
  String get projectHealthGitInitSupporting => '在此运行 git init。不会提交任何内容。';

  @override
  String get projectHealthSetUp => '设置';

  @override
  String get projectHealthRunning => '运行中';

  @override
  String get projectHealthNotRunning => '未运行';

  @override
  String projectHealthLineCounts(int added, int removed) {
    return '新增 $added 行，删除 $removed 行';
  }

  @override
  String projectFolderCreateHelper(String directory) {
    return '已在此手机的 $directory 中创建，并作为项目打开。';
  }

  @override
  String get projectFolderMissingTitle => '创建此文件夹？';

  @override
  String get projectFolderCreateFailedTitle => '无法创建文件夹';

  @override
  String get projectFolderOpenFailedTitle => '无法打开文件夹';

  @override
  String get projectsOneFolderTitle => '服务器只使用一个文件夹';

  @override
  String servicesStarted(String name) {
    return '$name 已启动';
  }

  @override
  String servicesRemoved(String name) {
    return '$name 已移除';
  }

  @override
  String servicesStopTitle(String name) {
    return '停止 $name？';
  }

  @override
  String servicesRestartTitle(String name) {
    return '重启 $name？';
  }

  @override
  String servicesForgetTitle(String name) {
    return '忘记 $name 的上次运行？';
  }

  @override
  String servicesRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String get servicesRemoveRunningHint =>
      '它的命令会继续在服务器上运行，而本应用将无法再停止它。请先停止它以结束运行。';

  @override
  String get servicesEmptyTitle => '还没有开发命令';

  @override
  String get servicesOffline => '服务器无响应。在它重新连接之前，无法启动或检查命令。';

  @override
  String get servicesLogFailed => '无法读取日志。';

  @override
  String get servicesProjectFolder => '项目文件夹';

  @override
  String get servicesWorkspace => '环境';

  @override
  String get servicesNameRequired => '请输入名称。';

  @override
  String get servicesDuplicateName => '已存在同名服务。';

  @override
  String get servicesCommandRequired => '请输入命令，例如 npm run dev。';

  @override
  String get servicesUrlInvalid => '请输入不含用户名和密码的 http 或 https 地址。';

  @override
  String get isolatedTaskProjectFolder => '项目文件夹';

  @override
  String get isolatedTaskStageCreate => '正在创建副本';

  @override
  String get isolatedTaskStagePrepare => '正在运行项目设置';

  @override
  String get isolatedTaskStageOpen => '正在打开对话';

  @override
  String get isolatedTaskUsually => '通常需要 1–3 分钟';

  @override
  String get savedPermissionsIntro => '智能体在本项目中可以不经询问就执行的操作。撤销其中一项后，智能体会再次询问你。';

  @override
  String get savedPermissionsLoadFailed => '无法加载始终允许的操作';

  @override
  String get savedPermissionsRevokeBody => '智能体下次想做这件事时会再次询问你。正在运行的工作会继续。';

  @override
  String savedPermissionsRevokedDetail(String action) {
    return '$action 现在会先询问你。';
  }

  @override
  String get savedPermissionsDismiss => '关闭';

  @override
  String get savedPermissionsCopyPattern => '复制匹配模式';

  @override
  String get savedPermissionsBusy => '请等待当前更改完成';

  @override
  String get savedPermissionsLoading => '正在加载始终允许的操作';

  @override
  String get savedPermissionsAllResources => '此类操作能触及的任何对象';

  @override
  String get settingsHubDetailEmpty => '选择一组设置';

  @override
  String get notifyQuietStartPicker => '设置静默时段的开始时间';

  @override
  String get notifyQuietEndPicker => '设置静默时段的结束时间';

  @override
  String get notifyQuietSet => '设置';

  @override
  String get notifyQuietAllDay => '开始和结束时间相同，因此通知全天保持静默。';

  @override
  String get notifySendingTest => '正在发送测试通知…';

  @override
  String get notifyNoServersTitle => '没有可监控的服务器';

  @override
  String get notifyNoServersDetail => '你保存的服务器可以在这里监控，这样其中任何一个的请求都会通知到你。';

  @override
  String get notifyDismiss => '忽略';

  @override
  String get notifySaving => '正在保存';

  @override
  String get notifyMonitorDetails => '服务器监控的工作方式';

  @override
  String get notifyRestartBackground => '重启实时连接';

  @override
  String get privacySharedSection => '与你的服务器共享';

  @override
  String get privacySaving => '正在保存…';

  @override
  String get privacyDeleting => '正在删除';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除 $count 条排队的提示词',
      one: '删除 1 条排队的提示词',
      zero: '删除排队的提示词',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除 $count 份草稿',
      one: '删除 1 份草稿',
      zero: '删除草稿',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => '你说';

  @override
  String get kitMessageThinking => '思考中…';

  @override
  String get kitMessageThought => '已思考';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 秒',
      one: '1 秒',
    );
    return '思考了 $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 分钟',
      one: '1 分钟',
    );
    return '思考了 $_temp0';
  }

  @override
  String get kitMessageActions => '消息操作';

  @override
  String get kitMessageNoticeFailed => '失败';

  @override
  String get kitRequestChooseOneReason => '请至少选择一个回答。';

  @override
  String get kitRequestSendAnswers => '发送答复';

  @override
  String get serversRemoveBody => '此手机将忘记该服务器：其密码、所选模型和智能体，以及项目和小组件对话。';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '将删除 $count 份未发送的草稿',
      one: '将删除 1 份未发送的草稿',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext => '你当前已连接到它：应用将断开连接并显示你的服务器列表';

  @override
  String get serversRemoveServerKeeps => '服务器和你的 AI 服务商处不会删除任何内容';

  @override
  String get guideStepTwoScan => '点击“添加服务器”，再点“扫描二维码”，把摄像头对准二维码；或选择“粘贴代码”。';

  @override
  String get guideStepTwoPaste => '复制打印出的代码，然后点击“添加服务器”并选择“粘贴代码”。';

  @override
  String get guidePhonePathTitle => '改用此手机';

  @override
  String get guidePhonePathBody => '在此手机上安装 OpenCode 并直接使用，无需电脑';

  @override
  String get pairingScannerAllowCamera => '允许使用相机';

  @override
  String get pairingScannerStarting => '正在打开相机…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return '$current 上正在进行一次运行。切换后本应用会显示 $target；$current 上的运行会继续。';
  }

  @override
  String get profileMonitorOpenFailedTitle => '无法打开';

  @override
  String get profileMonitorIfIgnored => '智能体会一直等到你回答';

  @override
  String get serverSettingsRestartedIt => '我已重启';

  @override
  String serverSettingsUpgradeBody(
    String target,
    String server,
    String current,
  ) {
    return '使用服务器自带的安装器在 $server 上安装 OpenCode $target（当前为 $current）。';
  }

  @override
  String serverSettingsUpgradeKeepsRunning(String current) {
    return '安装期间服务器会继续运行 $current';
  }

  @override
  String serverSettingsUpgradeRestartAfter(String target) {
    return '在其电脑上重启 OpenCode 进程即可使用 $target';
  }

  @override
  String get serverSettingsUpgradeKeepsData => '服务器数据保持不变';

  @override
  String serverSettingsCopyUpdateCommands(String server) {
    return '复制 $server 的更新命令';
  }

  @override
  String get serverSettingsAddressLabel => '地址';

  @override
  String get tailscaleSetupAppTitle => '此手机上的 Tailscale';

  @override
  String get tailscaleSetupVpnTitle => '登录并连接';

  @override
  String get tailscaleSetupVpnSupporting => '登录并连接。OpenCode 无法检查这一步。';

  @override
  String get tailscaleSetupOpenFailed => 'Tailscale 未能打开。请从启动器中打开它，然后返回。';

  @override
  String get tailscaleSetupAddressHelper => '粘贴 Tailscale Serve 输出的 HTTPS 地址。';

  @override
  String get tailscaleSetupGetApp => '获取 Tailscale';

  @override
  String get tailscaleSetupContinueReason => '请先输入服务器地址。';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return '部分已翻译（$percent%）';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return '$name 预览';
  }

  @override
  String get appearancePickerPreviewIn => '预览模式';

  @override
  String get appearancePickerInUse => '当前使用';

  @override
  String appearancePickerThemeApplied(String name) {
    return '主题已设为 $name';
  }

  @override
  String get serverSwitcherTitle => '服务器';

  @override
  String get serverSwitcherCurrentMenu => '服务器操作';

  @override
  String get localAgentEntryStillStarting => '仍在启动 · 可能需要一分钟';

  @override
  String get localAgentEntryDidNotStart => '未能启动';

  @override
  String get localAgentEntryRemoving => '正在移除';

  @override
  String localAgentEntrySignedOut(String state) {
    return '$state · 未登录 Claude';
  }

  @override
  String get formRendererSending => '正在发送你的回答…';

  @override
  String get formRendererChoose => '选择';

  @override
  String get formRendererChooseDate => '选择日期';

  @override
  String get formRendererChooseDateTime => '选择日期和时间';

  @override
  String formRendererDateAndTime(String date, String time) {
    return '$date $time';
  }

  @override
  String get formRendererUseDate => '使用日期';

  @override
  String get filePreviewPdfIsolated => 'PDF 页面无法在此隔离视图中渲染。请保存文件后用 PDF 应用阅读。';

  @override
  String get filePreviewCopyOriginal => '复制原文件';

  @override
  String get filePreviewOpenInFiles => '在“文件”中打开';

  @override
  String get filePreviewViewMode => '显示方式';

  @override
  String get filePreviewAttachFailed => '无法附加文件';

  @override
  String get filePreviewSaveFailed => '无法保存文件';

  @override
  String get kitTurnStarting => '正在启动模型…';

  @override
  String kitTurnStillStarting(int seconds) {
    return '仍在等待模型 · $seconds 秒';
  }

  @override
  String get kitTurnStopped => '你已停止此回复。';

  @override
  String get kitTurnInterrupted => '回复完成前连接中断了。';

  @override
  String get kitTurnCopy => '复制回复';

  @override
  String get kitTurnMore => '此回复的更多操作';

  @override
  String get kitTurnActions => '回复操作';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return '断开与 $serverName 的连接';
  }

  @override
  String get kitScannerStarting => '正在打开相机…';

  @override
  String get kitScannerSlow => '相机仍在打开';

  @override
  String get kitScannerPaused => '相机已暂停';

  @override
  String get kitScannerPreview => '相机画面';

  @override
  String get kitDateSet => '设置日期';

  @override
  String get kitTimeSet => '设置时间';

  @override
  String get kitDateTimeSet => '设置';

  @override
  String get kitDateType => '输入日期';

  @override
  String get kitDateCalendar => '显示日历';

  @override
  String kitDateFormatHint(String example) {
    return '例如 $example';
  }

  @override
  String get kitDateField => '日期';

  @override
  String get kitDateInvalid => '不是有效日期';

  @override
  String kitDateOutOfRange(String first, String last) {
    return '请选择 $first 到 $last 之间的日期';
  }

  @override
  String get kitTimeHour => '小时';

  @override
  String get kitTimeMinute => '分钟';

  @override
  String get kitTimePeriod => '上午或下午';

  @override
  String get kitTimeInvalid => '不是有效时间';

  @override
  String get kitDateTimeNotSet => '未设置';

  @override
  String kitDateTimeClear(String title) {
    return '清除$title';
  }

  @override
  String get kitDateUnavailable => '无法选择该日期';

  @override
  String get filesLoadingFolder => '正在打开文件夹…';

  @override
  String get filesSearching => '正在搜索…';

  @override
  String get filesShowHidden => '显示隐藏文件';

  @override
  String get filesOnlyHidden => '此文件夹只有隐藏的文件和文件夹。';

  @override
  String get filesCopyName => '复制名称';

  @override
  String globalSessionsMoveTitle(String project) {
    return '移动到 $project？';
  }

  @override
  String globalSessionsMoveBody(String title, String from, String to) {
    return '“$title”会通过服务器的同步系统从 $from 移到 $to。';
  }

  @override
  String get globalSessionsMoveWhileWorking => '它现在正在工作。移动可能会中断当前步骤。';

  @override
  String globalSessionsMoveBack(String project) {
    return '若要移回，请打开 $project 并在“所有对话”中选择“在此继续”。';
  }

  @override
  String get globalSessionsFilterLabel => '显示';

  @override
  String get globalSessionsFilterActive => '活动';

  @override
  String get globalSessionsArchivedNoMatchMessage => '没有标题匹配的已归档对话。请尝试更短的搜索词。';

  @override
  String get globalSessionsArchivedEmptyTitle => '没有已归档的对话';

  @override
  String get globalSessionsArchivedEmptyMessage => '你在“工作”中归档的对话会显示在这里。';

  @override
  String get globalSessionsShowActive => '显示活动对话';

  @override
  String globalSessionsProjectInUse(String project) {
    return '$project · 使用中';
  }

  @override
  String get globalSessionsCopyFolder => '复制文件夹路径';

  @override
  String get worktreesStartConversation => '在此新建对话';

  @override
  String get worktreesCreateHelper => '会创建一个独立分支和文件夹，并运行项目的启动任务。空格会变成短横线。';

  @override
  String get worktreesFolder => '文件夹';

  @override
  String get worktreesMainCopy => '主副本';

  @override
  String get worktreesCopyFolder => '复制文件夹路径';

  @override
  String get worktreesLoadFailedTitle => '无法加载工作树';

  @override
  String get worktreesSetupFailedWord => '设置失败';

  @override
  String get importNeedsFile => '请先选择一个 JSON 文件。';

  @override
  String get importNeedsDestination => '请先选择导入位置。';

  @override
  String get importFileLabel => '文件';

  @override
  String get importPreviewLabel => '对话';

  @override
  String importMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条消息',
      one: '1 条消息',
    );
    return '$_temp0';
  }

  @override
  String get importNoDestinationsTitle => '无处可导入';

  @override
  String get importChangeDestinationShort => '更改';

  @override
  String get importConversationId => '对话 ID';

  @override
  String get importParentId => '父对话 ID';

  @override
  String get importFolder => '文件夹';

  @override
  String get importEnvironmentId => '云环境 ID';

  @override
  String get phoneSetupStartUseTermuxOne => '使用 Termux 里的那个';

  @override
  String get phoneSetupStartUseTermuxOneDetail =>
      'Termux 中也设置了 OpenCode。改为连接到它。';

  @override
  String get phoneSetupStartTermuxNotAllowed =>
      '已安装 Termux，但它还没有允许本应用访问。请先完成它的设置。';

  @override
  String get phoneSetupCustomizeAllInstalled => '所有可选工具都已在这部手机上。';

  @override
  String get phoneSetupCustomizeIncluded => '必需';

  @override
  String get termuxProcsLoadFailedTitle => '无法读取正在运行的内容';

  @override
  String get termuxProcsEmptyBody => '当 OpenCode、AI 团队或构建在此运行时会显示在此列表中。';

  @override
  String get termuxProcsNotStoppedTitle => '并非全部已停止';

  @override
  String get termuxProcsCopyCommand => '复制命令';

  @override
  String get termuxProcsOpenControls => '打开“本机”';

  @override
  String get termuxProcsProcessId => '进程 ID';

  @override
  String get termuxProcsParentId => '父进程 ID';

  @override
  String get termuxProcsAboutOpenCode => '本机 OpenCode 服务器的一部分。';

  @override
  String get termuxProcsAboutAiTeam => 'AI 团队的一部分。停止它会停止团队正在进行的工作。';

  @override
  String get termuxProcsAboutBuild => '构建辅助程序。下次构建需要时会重新启动它。';

  @override
  String get termuxProcsAboutOrphan => '没有内容在等待它，因此停止它是安全的。';

  @override
  String get termuxProcsAboutOther => '由本机上的其他程序启动。';

  @override
  String get termuxProcsNoRestart => '无法从这里重新启动。';

  @override
  String get termuxProcsStopGroupTeamLost => '团队正在处理的任何任务也会停止。';

  @override
  String get termuxProcsStopGroupTeamRestart => '你可以从 AI 团队重新启动团队。';

  @override
  String get phoneSetupProgressStopContinueLater => '随时可以从“在这部手机上”继续。';

  @override
  String get teamMergeFailedNext => '未合并任何内容。请按主机提示修复后重试，或审查更改。';

  @override
  String get teamStartRunRefusedKept => '你的任务仍在这里。请修改后重新发送。';

  @override
  String get transcriptTogglesReasoningOn => '开启后，模型的推理会展开显示在每个回答下方。';

  @override
  String get transcriptTogglesUsageOn => '开启后，每条消息会显示时间、Token 数和费用。';

  @override
  String get transcriptTogglesScope => '这些设置适用于此设备上的所有对话。';

  @override
  String get handoffSheetCopyCommand => '复制命令';

  @override
  String get handoffSheetReloadConversation => '重试';

  @override
  String get handoffSheetPhoneServerNote =>
      '如果另一部手机尚未保存此服务器，它会提示，并提供打开“服务器”供你添加。';

  @override
  String get modelPickerChooseFirst => '请先选择一个模型。';

  @override
  String get modelPickerThinking => '思考';

  @override
  String get modelPickerAgentBuild => '编辑文件并运行命令';

  @override
  String get modelPickerAgentPlan => '只读取和规划，不修改文件';

  @override
  String modelPickerDetailsOutput(String count) {
    return '每次回答最多 $count 个 token';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '每百万 token：读取 $input，写入 $output';
  }

  @override
  String get modelPickerCanThink => '回答前会先思考';

  @override
  String get modelPickerCanUseTools => '可使用工具';

  @override
  String get modelPickerCanReadAttachments => '可读取你附加的图片和文件';

  @override
  String get modelPickerCopyId => '复制模型 id';

  @override
  String get modelPickerInUse => '使用中';

  @override
  String get modelPickerUnavailableReason => '此服务器目前不可用。';

  @override
  String get modelPickerCollections => '要显示的模型';

  @override
  String get modelPickerSignInTitle => '需要登录服务商';

  @override
  String get modelPickerSignInBody => '此服务器上还没有任何服务商提供模型。请先登录其中一个，再回来选择模型。';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '再显示 $count 个模型',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'Build';

  @override
  String get modelPickerAgentPlanName => 'Plan';

  @override
  String phoneServerCardDisconnect(String server) {
    return '断开与 $server 的连接';
  }

  @override
  String get phoneServerCardStartOpenCode => '启动 OpenCode';

  @override
  String get phoneServerCardStopOpenCode => '停止此手机上的 OpenCode';

  @override
  String get phoneServerCardShowServerLog => '显示服务器日志';

  @override
  String get phoneServerCardOpenTerminal => '打开终端';

  @override
  String get phoneServerCardFailedTitle => '未能完成';

  @override
  String get phoneServerRestartFailedTitle => '重启失败';

  @override
  String get setupTerminalTitle => '设置输出';

  @override
  String get teamPhoneStopTeam => '停止团队';

  @override
  String get teamPhoneStartTeam => '启动团队';

  @override
  String get teamPhoneStartTeamAgain => '再次启动团队';

  @override
  String get teamPhoneDeleteTeam => '从此手机删除团队';

  @override
  String get teamPhoneRemoveBody => '团队将停止，且此服务器的 AI 团队会关闭。';

  @override
  String get teamPhoneRemoveLost => '团队的程序、文件及其任务列表会被删除';

  @override
  String get teamPhoneRemoveKept => '你的项目文件及其 git 历史会保留';

  @override
  String teamPhoneRemoveFrees(int size) {
    return '释放约 $size MB';
  }

  @override
  String get teamPhoneRemoveConfirm => '删除团队';

  @override
  String get productStatesActionFailedTitle => '无法完成';

  @override
  String get productStatesSwitchServer => '切换服务器';

  @override
  String get externalLinkBlockedTitle => '链接已被阻止';

  @override
  String get externalLinkBlockedBody =>
      '本应用只打开 https:// 链接，http:// 链接需经你确认后才会打开。';

  @override
  String externalLinkOpensHost(String host) {
    return '将在本应用之外打开 $host。';
  }

  @override
  String get externalLinkDontOpen => '不打开';

  @override
  String get externalLinkCopy => '复制链接';

  @override
  String get externalLinkAddress => '完整地址';

  @override
  String get externalLinkOpenFailedTitle => '无法打开链接';

  @override
  String get runCommandReconnecting => 'OpenCode 正在重新连接。请稍后重试。';

  @override
  String runCommandArgumentsHelper(String command) {
    return '传给 /$command 的文本。若该命令不需要参数，可留空。';
  }

  @override
  String get runCommandRunsIn => '运行于';

  @override
  String runCommandFailedTitle(String command) {
    return '无法运行 /$command';
  }

  @override
  String get teamNowWakeRefusedNoReason => '主机未说明原因。';

  @override
  String get teamHostFormTeamLabel => '团队名称（可选）';

  @override
  String get teamHostFormTeamHelper => '留空则使用电脑上运行的团队。';

  @override
  String get teamHostFormHowAction => '如何设置电脑';

  @override
  String get teamHostFormCancelTest => '取消测试';

  @override
  String get teamHostFormSaveAnyway => '仍然保存该地址';

  @override
  String get teamHostFormSaveAnywayNote => '在电脑应答之前，AI 团队会将该团队显示为无应答。';

  @override
  String get teamHostFormConnectionDetails => '连接详情';

  @override
  String teamAgentScreenPause(String agent) {
    return '暂停 $agent';
  }

  @override
  String teamAgentScreenPaused(String agent) {
    return '已暂停 $agent';
  }

  @override
  String teamAgentScreenResume(String agent) {
    return '再次启动 $agent';
  }

  @override
  String teamAgentScreenNudge(String agent) {
    return '催促 $agent';
  }

  @override
  String teamAgentScreenRestart(String agent) {
    return '重启 $agent';
  }

  @override
  String teamAgentScreenStop(String agent) {
    return '停止 $agent';
  }

  @override
  String teamAgentScreenStopBody(String agent, String task) {
    return '$agent 现在停止处理“$task”。任务留在主机上，你可以从此页面再次启动 $agent。';
  }

  @override
  String teamAgentScreenStoppedTitle(String agent) {
    return '$agent 已停止';
  }

  @override
  String get teamAgentScreenStoppedBody => '其工作保持原状。需要时再启动它。';

  @override
  String teamAgentScreenCrashedTitle(String agent) {
    return '$agent 意外停止';
  }

  @override
  String get teamAgentScreenCrashedBody => '它的会话自行结束了。再次启动即可从主机接手其工作。';

  @override
  String get teamAgentScreenRecyclingBody => '它很快会启动新会话，并从主机接手其工作。';

  @override
  String teamAgentScreenModelFrom(String model, String provider) {
    return '来自$provider的$model';
  }

  @override
  String teamAgentScreenGateIfIgnored(String agent) {
    return '$agent 会等你答复后才继续';
  }

  @override
  String teamAgentScreenControlsElsewhere(String agent) {
    return '本机暂时无法在此主机上暂停、停止 $agent 或给它发消息。请在电脑上运行团队的主机前端，以便从这里控制。';
  }

  @override
  String get gateSheetDestructiveBody => '主机将此操作标记为破坏性操作。批准后无法从手机上撤销。';

  @override
  String get gateSheetAnswerLabel => '你的回答';

  @override
  String get gateSheetAfterAnswer => '主机确认你的回答后，团队会立刻继续。';

  @override
  String get gateSheetFixIt => '请团队修复';

  @override
  String gateSheetFixItDetail(String agent) {
    return '将错误发送给 $agent，请它查找原因并继续。';
  }

  @override
  String gateSheetFixRequest(String task, String error) {
    return '任务“$task”因以下错误失败：\n$error\n请查找原因、修复问题并继续。';
  }

  @override
  String gateSheetOpenAgent(String agent) {
    return '打开 $agent 页面';
  }

  @override
  String get teamIntroTurnOnPhone => '在本机开启 AI 团队';

  @override
  String get teamIntroInstalledTitle => '已安装在本机';

  @override
  String get teamIntroInstalledBody => '尚未开启。开启后将为你的项目启动团队；无需再下载任何内容。';

  @override
  String get teamIntroSetUpPhone => '在本机设置 AI 团队';

  @override
  String teamIntroSetUpOn(String server) {
    return '在 $server 上设置 AI 团队';
  }

  @override
  String teamIntroTurnOn(String server) {
    return '在 $server 上开启 AI 团队';
  }

  @override
  String get teamIntroCostTitle => '设置之前';

  @override
  String get teamIntroCostTime => '首次约需 8–10 分钟';

  @override
  String get teamIntroCostMemory => '每个工作者约需 550 MB 内存';

  @override
  String get teamAgentScreenLabelId => '智能体 ID';

  @override
  String get gateSheetSendNeedsText => '请先输入回答';

  @override
  String teamAgentsChecked(String age) {
    return '$age前检查';
  }

  @override
  String get teamWorkSheetMissingTitle => '工作项已不存在';

  @override
  String get teamWorkSheetMissingBody => '它可能已完成或被移除。关闭此面板以查看任务的当前状态。';

  @override
  String get teamWorkSheetNotOnHost => '已不再列出';

  @override
  String get teamWorkSheetOpenStepConversation => '打开此步骤的对话';

  @override
  String teamWorkSheetOpenAgentConversation(String name) {
    return '打开 $name 的对话';
  }

  @override
  String get usageRangeLabel => '时间范围';

  @override
  String get usageAboutNumbers => '关于这些数字';

  @override
  String get usageBudgetHelperUsd => '本范围的美元金额。报告达到时会通知你；不会中止任何操作。';

  @override
  String get usageBudgetHelperTokens => '本范围的 token 整数。报告达到时会通知你；不会中止任何操作。';

  @override
  String get usageBudgetClearConfirm => '清除预算';

  @override
  String get usageBudgetNotSet => '未设置';

  @override
  String get usageBudgetWaitReason => '用量加载后可用。';

  @override
  String get usageBudgetUsdTitle => '美元预算';

  @override
  String get usageBudgetTokensTitle => 'Token 预算';

  @override
  String get agentAccountScopeLostTitle => '此服务器已变更';

  @override
  String get agentAccountBackToServers => '返回“服务器”';

  @override
  String get agentAccountNotConnected => '连接此服务器以查看其 Codex 账号。';

  @override
  String get agentAccountSignInMethod => '登录方式';

  @override
  String get agentAccountPlanTitle => '套餐';

  @override
  String get agentAccountCopyCode => '复制登录码';

  @override
  String agentAccountLimitReached(String reset) {
    return '你已达到 Codex 的某项限制。$reset';
  }

  @override
  String get agentAccountResetDue => '随时重置';

  @override
  String agentAccountResetInDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days 天后重置',
      one: '1 天后重置',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInHours(int hours) {
    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: '$hours 小时后重置',
      one: '1 小时后重置',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes 分钟后重置',
      one: '1 分钟后重置',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetWhen(String relative, String time) {
    return '$relative（$time）';
  }

  @override
  String get reviewWorkspaceScopes => '要显示的更改';

  @override
  String get reviewWorkspaceRefreshFailed => '无法刷新更改';

  @override
  String get reviewWorkspaceSlowTitle => '仍在读取更改';

  @override
  String get reviewWorkspaceSlowBody => '服务器会运行 git 来比较文件。大型项目可能需要一分钟。';

  @override
  String get reviewWorkspaceAllViewedTitle => '你已查看所有文件';

  @override
  String reviewWorkspaceAllViewedMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条注释已附加到提示词上，可从对话中发送。',
      one: '1 条注释已附加到提示词上，可从对话中发送。',
    );
    return '$_temp0';
  }

  @override
  String get reviewWorkspaceBackToChat => '返回对话';

  @override
  String reviewWorkspaceCommentOnFile(String file) {
    return '评论 $file';
  }

  @override
  String reviewWorkspaceAddFileToPrompt(String file) {
    return '将 $file 添加到提示词';
  }

  @override
  String get reviewWorkspaceAddComment => '将注释添加到提示词';

  @override
  String get reviewWorkspaceCommentEmpty => '请先输入注释。';

  @override
  String get reviewWorkspaceCommentLabel => '你的注释';

  @override
  String get reviewWorkspaceCommentHint => '希望智能体检查或更改什么？';

  @override
  String get reviewWorkspaceCommentHelper => '关闭此页面后仍会保留，直到你添加它。';

  @override
  String get integrationsMcpTitle => 'MCP 服务器';

  @override
  String get integrationsMcpServersLabel => 'MCP 服务器';

  @override
  String integrationsModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个模型',
      one: '1 个模型',
    );
    return '$_temp0';
  }

  @override
  String integrationsProviderActions(String name) {
    return '$name 的操作';
  }

  @override
  String integrationsManageAccounts(String name) {
    return '管理 $name 账号';
  }

  @override
  String integrationsServerSignIn(String name) {
    return '在服务器上登录 $name';
  }

  @override
  String get integrationsServerSignInUnavailable => '此服务器无法从应用运行登录命令。';

  @override
  String integrationsDisconnectNamed(String name) {
    return '断开 $name';
  }

  @override
  String integrationsDisconnectBody(String name) {
    return '从此服务器移除 $name 的密钥。正在运行的回复会先完成。';
  }

  @override
  String get integrationsConnectMethodSubtitle => '选择连接方式';

  @override
  String get integrationsKeyHelper => '密钥保存在此服务器上。应用之后不会再显示它。';

  @override
  String get integrationsKeyEmpty => '请先粘贴密钥。';

  @override
  String get integrationsKeyRejected => '服务器未接受此密钥。请检查后重试。';

  @override
  String integrationsSignInAtHost(String host) {
    return '在 $host 登录？';
  }

  @override
  String get integrationsSignInBody => '在浏览器中批准访问，然后回到本应用。';

  @override
  String integrationsSignInInstructions(String instructions) {
    return '服务器提示：$instructions';
  }

  @override
  String get integrationsFinishSignInTitle => '完成登录';

  @override
  String get integrationsFinishSignInAction => '完成登录';

  @override
  String get integrationsFinishSignInEmpty => '请先粘贴代码。';

  @override
  String get integrationsFinishSignInMcpHelper =>
      '请粘贴你批准访问后浏览器最终停留的地址，或它显示的代码。';

  @override
  String get integrationsFinishSignInProviderHelper => '粘贴你批准访问后登录页面显示的代码。';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return '打开 $name 登录';
  }

  @override
  String get integrationsCancelSignIn => '取消登录';

  @override
  String get integrationsPendingNotRecoverable => '完成前请保持此界面打开：离开后，此服务器无法恢复登录。';

  @override
  String integrationsMcpActions(String name) {
    return '$name 的操作';
  }

  @override
  String integrationsMcpSignIn(String name) {
    return '登录 $name';
  }

  @override
  String integrationsMcpReconnect(String name) {
    return '重新连接 $name';
  }

  @override
  String integrationsMcpSigningIn(String name) {
    return '正在登录 $name';
  }

  @override
  String get integrationsMcpSignInOnServer => '请在服务器的电脑上登录；此服务器无法从应用完成。';

  @override
  String integrationsMcpRemoveUntilRestart(String name) {
    return '移除 $name（重启前有效）';
  }

  @override
  String integrationsMcpRemoveTitle(String name) {
    return '移除 $name（重启前有效）？';
  }

  @override
  String get integrationsMcpRemoveBody =>
      '它的工具会立即在此项目中停止工作。如果它存在于服务器配置中，服务器重启后会恢复。';

  @override
  String get integrationsMcpRemoveConfirm => '移除直到重启';

  @override
  String get integrationsCopyResourceAddress => '复制地址';

  @override
  String get terminalScreenSourceLabel => 'Shell 的运行位置';

  @override
  String get terminalScreenNameLabel => '名称';

  @override
  String get terminalScreenRenameConfirm => '重命名';

  @override
  String get terminalScreenNameEmpty => '请输入名称。';

  @override
  String terminalScreenStopTitle(String name) {
    return '停止 $name？';
  }

  @override
  String get terminalScreenStopBody => '程序及其启动的所有进程都会停止，终端也会消失。其输出无法找回。';

  @override
  String terminalScreenRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String get terminalScreenRemoveBody => '该终端及其输出将被删除。此操作无法撤销。';

  @override
  String get terminalScreenStopConfirm => '停止终端';

  @override
  String get terminalScreenRemoveConfirm => '移除终端';

  @override
  String get terminalScreenCreateFailed => '无法启动终端';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '移除 $count 个已结束的终端',
      one: '移除 1 个已结束的终端',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '移除 $count 个已结束的终端？',
      one: '移除 1 个已结束的终端？',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody => '它们的输出也会一并删除。运行中的终端会保留。';

  @override
  String get terminalScreenUsePhone => '使用此手机的终端';

  @override
  String terminalScreenRowRunning(String command) {
    return '运行中 · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return '已结束 · 代码 $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return '已结束 · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return '$name 的操作';
  }

  @override
  String terminalScreenOpen(String name) {
    return '打开 $name';
  }

  @override
  String terminalScreenRename(String name) {
    return '重命名 $name';
  }

  @override
  String terminalScreenStop(String name) {
    return '停止 $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return '移除 $name';
  }

  @override
  String get terminalScreenLoading => '正在加载终端';

  @override
  String get terminalScreenPaused => '应用在后台时已暂停';

  @override
  String get terminalScreenConnecting => '正在连接终端';

  @override
  String get terminalScreenCopy => '复制输出';

  @override
  String terminalScreenPaste(String name) {
    return '粘贴到 $name';
  }

  @override
  String get terminalScreenDetails => '终端详情';

  @override
  String terminalScreenDetailsTitle(String name) {
    return '$name 详情';
  }

  @override
  String get terminalScreenDetailCommand => '命令';

  @override
  String get terminalScreenDetailFolder => '文件夹';

  @override
  String get terminalScreenDetailPid => '进程 ID';

  @override
  String get terminalScreenDetailExit => '退出码';

  @override
  String localTerminalStopNamedTitle(String name) {
    return '停止 $name？';
  }

  @override
  String localTerminalPasteNamed(String name) {
    return '粘贴到 $name';
  }

  @override
  String get localTerminalCopySelection => '复制所选内容';

  @override
  String defaultShellOnlyOne(String name) {
    return '$name · 此服务器提供的唯一 shell';
  }

  @override
  String defaultShellSaveFailed(String error) {
    return '无法更改 shell。$error 点击重试。';
  }

  @override
  String get terminalScreenReadableMode => '显示为易读文本';

  @override
  String get terminalScreenLiveMode => '显示为实时终端';

  @override
  String get localTerminalSetUpLinux => '在此手机上设置 Linux';

  @override
  String get messageViewSendAgain => '重新发送这条消息';

  @override
  String get messageViewContinueReply => '继续此回复';

  @override
  String get reviewRevertSheetTitle => '从此提示词开始撤销？';

  @override
  String get reviewRevertSheetBody =>
      '审核期间，此提示词及其之后的所有内容都会被隐藏。在你做出选择之前，不会有任何最终变更。';

  @override
  String get reviewRevertPromptLabel => '从此提示词开始';

  @override
  String get reviewRevertFilesToggle => '同时还原文件';

  @override
  String get reviewRevertFilesToggleHint => '文件会回到此提示词之前的状态。';

  @override
  String get reviewRevertSheetAction => '撤销并审核';

  @override
  String get reviewRevertStageFailed => '无法准备撤销。未隐藏任何内容。';

  @override
  String get reviewRevertScreenTitle => '审核撤销';

  @override
  String get reviewRevertScreenIntro =>
      '此提示词及其之后的所有内容已被隐藏。在你于下方做出选择之前，不会有任何最终变更。';

  @override
  String get reviewRevertFilesLabel => '此次撤销涉及的文件';

  @override
  String get reviewRevertNoFiles => '此次撤销不会更改任何文件。';

  @override
  String reviewRevertFileLines(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String reviewRevertFileSupporting(String folder, String lines) {
    return '$folder · $lines';
  }

  @override
  String get reviewRevertRestoreTitle => '全部还原';

  @override
  String get reviewRevertKeepTitle => '删除已隐藏的消息';

  @override
  String reviewRevertKeepConfirmTitle(String title) {
    return '删除“$title”中的消息？';
  }

  @override
  String get reviewRevertKeepConfirmBody => '此操作无法撤销。';

  @override
  String get reviewRevertKeepConfirmAction => '删除已隐藏的消息';

  @override
  String get reviewRevertKeepConsequenceMessages => '已隐藏的提示词及其之后的所有消息都会被删除';

  @override
  String get reviewRevertKeepConsequenceFiles => '文件保持当前状态';

  @override
  String reviewRevertRestoreConfirmTitle(String title) {
    return '恢复“$title”？';
  }

  @override
  String get reviewRevertRestoreConfirmBody =>
      '已隐藏的消息会重新出现，此次撤销涉及的文件会回到你设置撤销时的状态。之后你仍可从某条提示词撤销。';

  @override
  String get reviewRevertRestoreConsequenceMessages => '已隐藏的消息会重新出现';

  @override
  String reviewRevertRestoreConsequenceFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件会被替换，此后所做的编辑会保留',
      one: '1 个文件会被替换，此后所做的编辑会保留',
    );
    return '$_temp0';
  }

  @override
  String get reviewRevertRestoreConsequenceUnknownFiles =>
      '此次撤销涉及的文件会被替换，此后所做的编辑会保留';

  @override
  String get reviewRevertStaleTitle => '撤销已变化';

  @override
  String get reviewRevertNoneTitle => '没有可审核的内容';

  @override
  String get reviewRevertNoneBody => '此对话中没有待处理的撤销。';

  @override
  String get reviewRevertBackAction => '返回对话';

  @override
  String get reviewRevertKeptTitle => '已保留撤销';

  @override
  String get reviewRevertKeptBody => '已隐藏的消息已删除。文件保持原样。';

  @override
  String get reviewRevertRestoredTitle => '已全部还原';

  @override
  String get reviewRevertRestoredBody => '消息和文件都已恢复原样。';

  @override
  String get reviewRevertFailed => '操作未完成。请检查对话后重试。';

  @override
  String get perfTraceClearTimings => '清除计时';

  @override
  String appDiagnosticsClearTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '清除 $count 条错误？',
      one: '清除 1 条错误？',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '保留在此手机上的 $count 条错误会被移除，已保存的报告中也一并移除。此操作无法撤销。',
      one: '保留在此手机上的这条错误会被移除，已保存的报告中也一并移除。此操作无法撤销。',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '清除 $count 条错误',
      one: '清除 1 条错误',
    );
    return '$_temp0';
  }

  @override
  String get capabilityStateHere => '此处可用';

  @override
  String get capabilityStateNotServer => '此服务器不支持';

  @override
  String get capabilityStateNotDevice => '此设备不支持';

  @override
  String get capabilityNeedsAndroid => '需要 Android 应用';

  @override
  String capabilityAvailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项功能在此可用',
      one: '1 项功能在此可用',
    );
    return '$_temp0';
  }

  @override
  String get capabilityAvailableCountDetail => '查看此服务器和设备能做什么';

  @override
  String get capabilityAddServer => '添加一台支持这些功能的服务器';

  @override
  String get capabilityAddServerDetail => '连接另一台电脑，或在此手机上设置一台，然后切换到它';

  @override
  String get keepRunningAllSetTitle => '已设置完成';

  @override
  String get keepRunningAllSetBody => 'Android 会让应用在后台保持运行。这部手机上无需再允许其他项。';

  @override
  String get keepRunningDailyLimit =>
      '在 Android 15 及更高版本中，即使这里的选项全部允许，Android 每天也只允许约 6 小时的后台同步。之后应用会在后台暂停，直到你再次打开它。';

  @override
  String get aboutTitle => '关于';

  @override
  String get aboutCopyVersion => '复制版本号';

  @override
  String get aboutCheckUpdates => '检查更新';

  @override
  String get aboutUpdateIdle => '查找此应用的新版本';

  @override
  String get aboutUpdateChecking => '正在检查…';

  @override
  String get aboutUpdateCurrent => '你已是最新版本';

  @override
  String get aboutUpdateDownloading => '正在下载更新…';

  @override
  String get aboutUpdateReady => '更新已就绪。请关闭并重新打开应用以使用。';

  @override
  String get aboutUpdateCannot => '此构建版本无法自行更新。请安装最新的发布版本。';

  @override
  String get aboutUpdateFailed => '无法检查更新。请检查网络连接后重试。';

  @override
  String get aboutAllLicences => '所有软件包许可';

  @override
  String get aboutAllLicencesDetail => '此构建版本中打包的每个库的许可文本';

  @override
  String get aboutPackageId => '包名';

  @override
  String get usageHubUnavailableTitle => '没有可显示的用量';

  @override
  String get usageHubUnavailableBody => '连接到已保存的服务器，即可查看其花费以及你的提供商账户剩余量。';

  @override
  String get voiceSetupSubtitle => '只需下载一次语音模型。之后语音输入就会在此手机上运行，无需联网。';

  @override
  String voiceSetupDownloadPack(String model, String size) {
    return '下载 $model（$size）';
  }

  @override
  String voiceSetupUsePack(String model) {
    return '使用 $model';
  }

  @override
  String voiceSetupRedownloadPack(String model) {
    return '重新下载 $model 语音模型';
  }

  @override
  String voiceSetupDeletePack(String model, String size) {
    return '删除 $model 语音模型（$size）';
  }

  @override
  String voiceSetupKeepPack(String model) {
    return '保留 $model';
  }

  @override
  String voiceSetupDownloadingPack(String model) {
    return '正在下载 $model';
  }

  @override
  String get voiceSetupModelLabel => '语音模型';

  @override
  String get voiceNoticesTitle => '语音许可';

  @override
  String get voiceNoticesIntro => '语音输入由这些开源组件构建。打开其中一个即可阅读其许可。';

  @override
  String voiceNoticesMadeBy(String maker, String license) {
    return '$maker · $license';
  }

  @override
  String voiceNoticesOpenWebsite(String name) {
    return '打开 $name 网站';
  }

  @override
  String get voiceNoticesWhisper => 'Whisper 语音模型';

  @override
  String get voiceSetupBusyReason => '下载完成后可用';

  @override
  String get shorebirdUpdateReadyTitle => '应用更新已就绪';

  @override
  String get shorebirdUpdateReadyBody => '完全关闭并重新打开应用后生效。';

  @override
  String desktopReleaseAvailable(String tag) {
    return '有可用更新 $tag';
  }

  @override
  String get desktopReleaseWhatChanged => '发布页面列出了变更内容并提供下载。';

  @override
  String get desktopReleaseOpenPage => '打开发布页面';

  @override
  String get runningWorkTitle => '此对话中的工作';

  @override
  String get runningWorkFailed => '失败';

  @override
  String runningWorkAgentState(String state) {
    return '智能体 · $state';
  }

  @override
  String runningWorkCommandState(String state) {
    return '命令 · $state';
  }

  @override
  String get runningWorkOffline => '正在重新连接。等服务器有响应后再试。';

  @override
  String runningWorkStopAgent(String title) {
    return '停止“$title”';
  }

  @override
  String runningWorkStopAgentTitle(String title) {
    return '停止“$title”？';
  }

  @override
  String get runningWorkStopAgentBody => '智能体会在原地停止。它的对话和已更改的文件都会保留。';

  @override
  String get runningWorkStopAgentConfirm => '停止智能体';

  @override
  String get runningWorkScopeChangedTitle => '服务器或项目已变更';

  @override
  String get runningWorkAgentsFailed => '无法加载此对话的智能体。';

  @override
  String get runningWorkCommandsFailed => '无法加载此对话的命令。';

  @override
  String get runningWorkEmptyTitle => '没有正在运行的工作';

  @override
  String get runningWorkEmptyBody => '此对话启动的智能体和命令会在此显示，运行时和结束后都在。';

  @override
  String get runningWorkBackgroundBody => '工作会在服务器上继续运行，结果会回到这里。';

  @override
  String get runningWorkBackgroundAction => '运行期间可以继续对话';

  @override
  String get shellOutputCopyFirst => '请先复制输出';

  @override
  String get shellOutputLimitTitle => '在此之后停止…';

  @override
  String shellOutputStopsIn(String time) {
    return '$time 后停止';
  }

  @override
  String get shellOutputNoLimit => '无时间限制';

  @override
  String shellOutputAboutToStop(String time) {
    return '它将在 $time 后停止。更改超时可给它更多时间。';
  }

  @override
  String get shellOutputReadFailed => '无法读取输出。';

  @override
  String get shellOutputLimitFailed => '无法更改时间限制。';

  @override
  String get shellOutputDetailCommand => '原始命令';

  @override
  String get shellOutputDetailFolder => '文件夹';

  @override
  String get shellOutputDetailExit => '退出码';

  @override
  String get shellOutputDetailId => '命令 ID';

  @override
  String get shellOutputReading => '正在读取输出';

  @override
  String get sessionDestinationWarpTitle => '移动到云端';

  @override
  String get sessionDestinationSeparateCopy => '独立副本';

  @override
  String sessionDestinationCloudKind(String state) {
    return '云机器 · $state';
  }

  @override
  String get sessionDestinationConnected => '已连接';

  @override
  String get sessionDestinationNotConnected => '未连接';

  @override
  String get sessionDestinationNotConnectedWhy => '未连接。连接后即可选择。';

  @override
  String sessionDestinationChangesGo(String destination) {
    return '带更改时，更改会随它一起移动到$destination。';
  }

  @override
  String sessionDestinationChangesCopied(String destination) {
    return '带更改时，一份副本会随它一起移动到$destination。';
  }

  @override
  String sessionDestinationChangesStay(String place) {
    return '不带更改时，它们会留在$place。';
  }

  @override
  String sessionDestinationMoveWithout(String destination) {
    return '不带更改移动到$destination';
  }

  @override
  String get sessionDestinationMoveFailed => '无法移动该对话。';

  @override
  String get sessionDestinationLoadFailed => '无法加载可移动到的位置';

  @override
  String get sessionDestinationNoneTitle => '无处可移动';

  @override
  String get sessionDestinationNoneMoveBody => '此项目只有这一个文件夹。项目有独立副本后会显示在这里。';

  @override
  String get sessionDestinationNoneWarpBody => '此项目还没有云机器。';

  @override
  String get consoleOrganizationWhatChanges => '模型、提供商和计费会跟随你选定的组织。';

  @override
  String consoleOrganizationSwitchBody(String organization) {
    return '$organization 将成为模型、提供商和计费所属的组织。模型会重新加载；正在运行的内容不会停止。';
  }

  @override
  String consoleOrganizationSwitchConfirm(String organization) {
    return '切换到 $organization';
  }

  @override
  String get consoleOrganizationLoadFailed => '无法加载你的组织';

  @override
  String get consoleOrganizationNoneTitle => '没有组织';

  @override
  String get consoleOrganizationOnlyOne => '这是你唯一的组织，因此没有可切换的对象。';

  @override
  String get sessionContextLoading => '正在加载上下文';

  @override
  String get sessionContextMovedTitle => '此对话已移动';

  @override
  String get sessionContextLoadFailed => '无法加载上下文';

  @override
  String get sessionContextRefreshFailed => '无法刷新。下方数字来自上次读取。';

  @override
  String sessionContextVerdictPlenty(String percent) {
    return '已用 $percent% · 余量充足';
  }

  @override
  String sessionContextVerdictUsed(String percent) {
    return '已用 $percent%';
  }

  @override
  String sessionContextVerdictNear(String percent) {
    return '已用 $percent%';
  }

  @override
  String sessionContextVerdictFull(String percent) {
    return '已用 $percent% · 已达上限';
  }

  @override
  String get sessionContextNearLimitTitle => '接近上限';

  @override
  String get sessionContextNearLimitBody =>
      '较早的细节可能会从模型看到的内容中被丢弃。请压缩对话以继续，或开始新对话。';

  @override
  String get sessionContextCompactAction => '压缩此对话';

  @override
  String get sessionContextCompactTitle => '压缩此对话？';

  @override
  String get sessionContextCompactBody =>
      'OpenCode 会总结截至目前的内容，并从摘要继续，因此占用模型上限更少。';

  @override
  String get sessionContextCompactKept => '每条消息仍保留在历史中。';

  @override
  String get sessionContextCompactConfirm => '压缩对话';

  @override
  String get sessionContextCompactStarted => '已开始压缩。完成后数字会更新。';

  @override
  String get sessionContextCompactBusy => '请等待回复结束。';

  @override
  String get sessionContextMakeupTitle => '最近一次请求的输入';

  @override
  String sessionContextTokens(String count) {
    return '$count tokens';
  }

  @override
  String get sessionContextModelId => '模型 ID';

  @override
  String get demoScreenTitle => '离线试用';

  @override
  String get demoScreenSimulated => '模拟 · 不保存任何内容';

  @override
  String get demoScreenFinished => '这就是完整流程：一条提示词、一次回复和一处经过审查的编辑。';

  @override
  String sessionContextPercent(String percent) {
    return '$percent %';
  }

  @override
  String sessionDestinationChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '存在 $count 个已更改文件。',
      one: '存在 1 个已更改文件。',
    );
    return '$_temp0';
  }

  @override
  String get activeContextLoading => '正在读取活动上下文…';

  @override
  String activeContextAllCount(int count) {
    return '全部消息 · $count';
  }

  @override
  String get activeContextChangedTitle => '此视图已过期';

  @override
  String get activeContextFailedTitle => '无法读取上下文';

  @override
  String get activeContextIntro => '最新摘要之后，模型在下一回合会读取的内容。';

  @override
  String get activeContextEmptyDetail => '下一回合还没有保留任何内容。下拉可再次检查。';

  @override
  String get activeContextWhat => '活动上下文';

  @override
  String activeContextRowMenu(String type) {
    return '$type 的操作';
  }

  @override
  String activeContextOpenMessage(String type) {
    return '打开$type';
  }

  @override
  String activeContextCopyMessage(String type) {
    return '复制$type文本';
  }

  @override
  String get activeContextMessageId => '消息 id';

  @override
  String activeContextCopyPart(String part) {
    return '复制$part';
  }

  @override
  String get sessionNoteDeleting => '正在删除笔记…';

  @override
  String get sessionNoteSaving => '正在保存笔记…';

  @override
  String get sessionNoteLoading => '正在读取已保存的笔记…';

  @override
  String get sessionNoteLoadFailed => '无法读取笔记';

  @override
  String get sessionNoteSaveFailed => '无法保存笔记';

  @override
  String get sessionNoteFieldLabel => '笔记';

  @override
  String get sessionNoteFieldLocked => '请先刷新已保存的笔记再编辑。';

  @override
  String sessionNoteTooLong(int over, int limit) {
    return '超出 $over 字节。笔记最多 $limit 字节。';
  }

  @override
  String get sessionNoteWriteFirst => '请先写内容再保存。';

  @override
  String get sessionNoteEmptyUseDelete => '如需移除笔记，请使用“删除已保存的笔记”。';

  @override
  String get sessionRelationsTitle => '子智能体';

  @override
  String sessionRelationsStopTitle(String title) {
    return '停止 $title？';
  }

  @override
  String get sessionRelationsStopBody => '子智能体会停止当前步骤。它已完成的内容仍留在其对话中。';

  @override
  String get sessionRelationsStopConfirm => '停止子智能体';

  @override
  String get sessionRelationsFailedTitle => '无法加载子智能体';

  @override
  String get sessionRelationsLoading => '正在加载子智能体…';

  @override
  String get sessionRelationsStartedFrom => '起始自';

  @override
  String sessionRelationsSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个子智能体',
      one: '1 个子智能体',
    );
    return '$_temp0';
  }

  @override
  String get sessionRelationsOpenToAnswer => '打开以答复';

  @override
  String get sessionRelationsIdle => '空闲';

  @override
  String get sessionRelationsThisConversation => '此对话';

  @override
  String get sessionRelationsOpening => '正在打开…';

  @override
  String sessionRelationsRowMenu(String title) {
    return '$title 的操作';
  }

  @override
  String sessionRelationsOpen(String title) {
    return '打开 $title';
  }

  @override
  String sessionRelationsCopyHandoff(String title) {
    return '在电脑上继续 $title';
  }

  @override
  String sessionRelationsPin(String title) {
    return '置顶 $title';
  }

  @override
  String sessionRelationsUnpin(String title) {
    return '取消置顶 $title';
  }

  @override
  String sessionRelationsStop(String title) {
    return '停止 $title';
  }

  @override
  String get webSourcesInvalidUrl => '请输入不含用户名和密码的 HTTP 或 HTTPS 地址。';

  @override
  String get webSearchFailedTitle => '搜索未完成';

  @override
  String get webSearchTryAgain => '重新搜索';

  @override
  String get webSearchBusy => '请等待搜索完成。';

  @override
  String get webSearchQueryHint => '例如：flutter golden tests';

  @override
  String get webSearchNeedsProvider => '请先在此服务器上设置搜索提供商。';

  @override
  String get webSearchEmptyDetail => '请换用其他关键词，或在下方粘贴链接。';

  @override
  String webSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条结果',
      one: '1 条结果',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesAdded => '已添加';

  @override
  String webSourcesAddNamed(String title) {
    return '将 $title 添加到提示词';
  }

  @override
  String webSourcesRowMenu(String title) {
    return '$title 的操作';
  }

  @override
  String webSourcesOpenHost(String host) {
    return '在浏览器中打开 $host';
  }

  @override
  String get webSourcesAddLink => '将链接添加到提示词';

  @override
  String get webSourcesPasteDetail => '一个公开地址，可附摘录';

  @override
  String webSourcesRemoveNamed(String title) {
    return '从提示词中移除 $title';
  }

  @override
  String get webSearchSearching => '正在搜索…';

  @override
  String get webSearchFindingProviders => '正在查找搜索提供商…';

  @override
  String webSourcesDone(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '将 $count 个来源添加到提示词',
      one: '将 1 个来源添加到提示词',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesScopeChangedTitle => '服务器已变更';

  @override
  String get sessionExportFormatLabel => '格式';

  @override
  String get sessionExportJsonUnavailable => '此服务器无法发送完整副本。请改为保存可读的对话记录。';

  @override
  String get sessionExportPrivacyLabel => '隐私';

  @override
  String get sessionExportRedactKeeps => '保留每条消息的发送者；文字会变成占位符。这不是备份。';

  @override
  String get sessionExportRedactBusy => '请等文件保存完成后再更改此项。';

  @override
  String get sessionExportRedactChanged => '请从对话中重新打开导出以更改此项。';

  @override
  String get sessionExportSaveJson => '保存完整对话';

  @override
  String get sessionExportSaveMarkdown => '保存可读的对话记录';

  @override
  String get sessionExportSaveFailed => '无法在本设备上写入文件。服务器上没有任何变化。请重试，或选择其他文件夹。';

  @override
  String get capabilitiesToolsMissingTitle => '未列出工具';

  @override
  String capabilitiesToolsMissingOnServer(String server) {
    return '$server 未列出它的工具';
  }

  @override
  String get mcpSetupWhere => '安装位置';

  @override
  String get mcpSetupHowItRuns => '运行方式';

  @override
  String get mcpSetupHeaders => '请求头';

  @override
  String get mcpSetupAdvanced => '高级';

  @override
  String get mcpSetupAdvancedRemote => '登录检测与超时';

  @override
  String get mcpSetupAdvancedLocal => '工作目录与超时';

  @override
  String get mcpSetupNoProject => '请先打开一个项目';

  @override
  String get mcpSetupRuntimeNote =>
      '它现在会连接，但 OpenCode 重启后就会消失。如需长期保留，请编辑服务器配置。';

  @override
  String mcpSetupSaveNamed(String name) {
    return '保存 $name';
  }

  @override
  String mcpSetupAddNamed(String name) {
    return '添加 $name';
  }

  @override
  String get mcpSetupLocationChangedShort => '服务器或项目已变更';

  @override
  String get mcpSetupSaveFailed => '无法添加 MCP 服务器';

  @override
  String get mcpSetupDiscardTitle => '丢弃此 MCP 服务器？';

  @override
  String get mcpSetupDiscardBody => '你在此输入的内容不会被保存，将会丢失。';

  @override
  String get mcpSetupDiscardConfirm => '丢弃服务器';

  @override
  String get externalAgentsEmptyTitle => '还没有外部智能体';

  @override
  String get externalAgentsEmptyBody => '通过网址添加一个。在保存任何内容之前，你可以先看它如何介绍自己。';

  @override
  String get externalAgentsBoundary => '只有你发送的文本会到达外部智能体。你的项目、文件和其他对话都留在此手机上。';

  @override
  String get externalAgentsRemovalIncomplete => '移除未完成 · 点击重试';

  @override
  String externalAgentsRemoveNamed(String name) {
    return '从此手机移除 $name';
  }

  @override
  String externalAgentsRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String get externalAgentsRemoveBody =>
      '它保存的任务和密钥会离开此手机。它已开始的工作会继续，它保留的内容仍归它所有。';

  @override
  String get externalAgentsBusy => '请等待当前步骤完成';

  @override
  String get externalAgentsAddressHelper => '它的网址，或它的 Agent Card 地址。';

  @override
  String get externalAgentsCheck => '检查智能体';

  @override
  String get externalAgentsCheckNeedsAddress => '请先输入智能体地址';

  @override
  String get externalAgentsStopChecking => '停止检查';

  @override
  String get externalAgentsCheckFailedTitle => '无法检查此智能体';

  @override
  String externalAgentsSaveNamed(String name) {
    return '保存 $name';
  }

  @override
  String get externalAgentsSaveNeedsKey => '请先输入智能体密钥';

  @override
  String get externalAgentsAboutLabel => '它的自我介绍';

  @override
  String get externalAgentsUnverified => '这些描述来自智能体自身。本应用并未核实由谁运行它、它能做什么、费用如何。';

  @override
  String get externalAgentsUnsupportedTitle => '不支持该智能体';

  @override
  String get externalAgentsUnsupportedBody =>
      '它不接受本应用发送文本任务的方式，或者它要求的登录方式本应用不支持。';

  @override
  String get externalAgentsKeyLabel => '智能体密钥';

  @override
  String get externalAgentsKeyHelper => '其所有者给你的密钥。它保存在此手机的安全存储中，只会发送给此智能体。';

  @override
  String get externalAgentsNoKey => '此智能体不要求密钥。除非你信任它，否则不要发送私密信息。';

  @override
  String get externalAgentsDetailCard => 'Agent Card';

  @override
  String get externalAgentsDetailEndpoint => '端点';

  @override
  String get externalAgentsDetailVersion => '版本';

  @override
  String get externalAgentsDetailConnection => '连接';

  @override
  String externalAgentsNewTaskNamed(String name) {
    return '为 $name 新建任务';
  }

  @override
  String externalAgentsReplaceKeyNamed(String name) {
    return '更换 $name 的密钥';
  }

  @override
  String externalAgentsReplaceKeyTitle(String name) {
    return '更换 $name 的密钥';
  }

  @override
  String get externalAgentsSaveKey => '保存密钥';

  @override
  String get externalAgentsTasksLabel => '任务';

  @override
  String get externalAgentsNoTasksTitle => '还没有任务';

  @override
  String get externalAgentsNoTasksBody =>
      '写好任务并确认后再发送。打开已发送的任务只是查询进度，任务绝不会被重复发送。';

  @override
  String get externalAgentsUntitledTask => '新任务';

  @override
  String externalAgentsSendNamed(String name) {
    return '发送给 $name';
  }

  @override
  String externalAgentsReplyNamed(String name) {
    return '回复 $name';
  }

  @override
  String get externalAgentsSendNeedsText => '请先写下任务';

  @override
  String get externalAgentsReplyNeedsText => '请先写下你的回复';

  @override
  String get externalAgentsSendNote => '只会发送这段文本。智能体可能使用它自己的服务并产生费用，请查看其条款。';

  @override
  String externalAgentsCheckedAt(String age) {
    return '$age前与此智能体核对过';
  }

  @override
  String get externalAgentsPullToCheck => '已保存在此手机上 · 下拉可与此智能体核对';

  @override
  String externalAgentsStopMenu(String name) {
    return '请 $name 停止此任务';
  }

  @override
  String get externalAgentsStopUnavailable => '请先与此智能体核对；下拉刷新';

  @override
  String get externalAgentsForgetMenu => '在此手机上忘记此任务';

  @override
  String get externalAgentsForgetTitle => '忘记此任务？';

  @override
  String get externalAgentsForgetBody => '它会离开此手机。智能体已开始的工作会继续，它自己那份副本仍留在它那里。';

  @override
  String get externalAgentsForgetConfirm => '忘记任务';

  @override
  String mcpSetupSavedNamed(String name) {
    return '已在此服务器上保存 $name';
  }

  @override
  String mcpSetupSavedNotConnectedBody(String reason) {
    return '应用之后未能重新连接。$reason';
  }

  @override
  String get mcpSetupSavedElsewhere =>
      '保存后服务器或项目发生了变更，因此此页面无法为它重新连接。请关闭页面并查看 MCP 服务器。';

  @override
  String get mcpSetupUnavailableTitle => '无法添加 MCP 服务器';

  @override
  String get mcpSetupUnavailableBody =>
      '它不接受从应用添加新的 MCP 服务器。请在电脑上的配置中添加，之后它们会显示在 MCP 服务器下。';

  @override
  String get commandAuthSheetWorking => '正在询问服务器…';

  @override
  String get credentialSheetLoading => '正在读取已保存的账号…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return '请在“服务商”中重新登录 $provider 以添加账号。';
  }

  @override
  String credentialSheetActions(String label) {
    return '$label 的操作';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return '使用 $label';
  }

  @override
  String get credentialSheetInUse => '已在使用';

  @override
  String credentialSheetRenameNamed(String label) {
    return '重命名 $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return '移除 $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return '将从此服务器移除 $label。使用它的项目需要改用另一个 $provider 账号。';
  }

  @override
  String credentialSheetRenamed(String label) {
    return '已重命名为 $label。';
  }

  @override
  String get credentialSheetLabelEmpty => '请为账号命名。';

  @override
  String get credentialSheetLabelInvalid => '最多 128 个字符，不能包含换行或控制字符。';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return '要忽略 $integration 登录吗？';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      '应用将不再在此设备上跟踪它。服务器上不会取消任何内容；未完成的登录会在那里自行过期。';

  @override
  String get toolsScreenLoadFailed => '无法加载此模型的工具';

  @override
  String get toolsScreenSearchWhat => '工具';

  @override
  String get toolsScreenRegisteredOnly => '已在此项目中注册 · 此模型无法调用它';

  @override
  String get toolsDetailTakes => '参数';

  @override
  String get toolsDetailTakesNothing => '无需参数。';

  @override
  String get toolsDetailRequired => '必填';

  @override
  String get toolsDetailOptional => '可选';

  @override
  String get toolsDetailTypeText => '文本';

  @override
  String get toolsDetailTypeNumber => '数字';

  @override
  String get toolsDetailTypeYesNo => '是或否';

  @override
  String get toolsDetailTypeList => '列表';

  @override
  String get toolsDetailTypeGroup => '一组值';

  @override
  String get toolsDetailTypeAny => '任意值';

  @override
  String commandsScreenRunsWith(String agent) {
    return '使用 $agent 运行';
  }

  @override
  String get commandsScreenMenuLabel => '命令操作';

  @override
  String commandsScreenCopy(String command) {
    return '复制$command';
  }

  @override
  String get referencesScreenLoading => '正在加载参考资料';

  @override
  String get referencesScreenLoadFailed => '无法加载参考资料';

  @override
  String get referencesScreenIntro =>
      '此项目指定给其智能体读取的文件夹。把其中一个加入提示词，智能体就能读取其中的内容。';

  @override
  String get referencesScreenEmptyBody =>
      '参考资料是项目的智能体可以读取的文件夹。为此项目设置的参考资料会显示在这里。';

  @override
  String get referencesScreenMenuLabel => '参考资料操作';

  @override
  String referencesScreenAdd(String mention) {
    return '将 $mention 添加到提示词';
  }

  @override
  String referencesScreenShowDetails(String name) {
    return '显示 $name 详情';
  }

  @override
  String referencesScreenCopyMention(String mention) {
    return '复制 $mention';
  }

  @override
  String get referencesScreenCopyPath => '复制路径';

  @override
  String referencesScreenSheetBody(String mention) {
    return '在提示词中写入 $mention，智能体就会为该次回复读取此文件夹。';
  }

  @override
  String get referencesScreenPathLabel => '路径';

  @override
  String get skillsScreenLoading => '正在加载技能';

  @override
  String get skillsScreenLoadFailed => '无法加载技能';

  @override
  String get skillSheetViewLabel => '技能的展示方式';

  @override
  String get skillSheetLocation => '文件';

  @override
  String skillSheetCopyCommand(String command) {
    return '复制$command';
  }

  @override
  String get skillSheetCheckConversation => '重试前请先查看对话。';

  @override
  String get skillSheetSending => '正在添加技能…';

  @override
  String toolCardDelegatedTo(String agent) {
    return '已委派给 $agent';
  }

  @override
  String get toolCardExitPassed => '通过 · 退出码 0';

  @override
  String toolCardExitFailed(int code) {
    return '失败 · 退出码 $code';
  }

  @override
  String get toolCardRunCommandAgain => '再次运行此命令';

  @override
  String get toolCardCopyCommand => '复制命令';

  @override
  String toolCardLoadImageAgain(String name) {
    return '重新加载 $name';
  }

  @override
  String toolCardChangesIn(String file) {
    return '$file 中的更改';
  }

  @override
  String mobileTasksShowAll(int count) {
    return '显示全部 $count 个任务';
  }

  @override
  String get composerBusyReason => '正在准备你的提示词…';

  @override
  String get composerToolsTextOnly => '这台服务器只接受文字';

  @override
  String composerToolsAgentTextOnly(String agent) {
    return '在这里 $agent 只接受文字';
  }

  @override
  String launcherConversationHint(String project, String agent) {
    return '$project 中的对话 · $agent';
  }

  @override
  String composerToolsAgentPicturesOnly(String agent) {
    return '$agent 只接受图片：请使用“照片库”或“拍照”';
  }

  @override
  String chatsNewAgentNeedsProjectsFolder(String agent) {
    return '$agent 只能在这部手机上的项目中工作。请选择其中一个。';
  }

  @override
  String get composerToolCommandsTitle => '命令和智能体';

  @override
  String get composerToolSavedSubtitle => '把保存过的提示词放回草稿';

  @override
  String get composerToolSaveForLater => '保存提示词以备后用';

  @override
  String get composerToolNothingToSave => '请先输入内容或添加附件';

  @override
  String get composerToolsMore => '更多工具';

  @override
  String get composerReturnedToDraft => '已放回草稿';

  @override
  String get promptHistoryIntro => '点击一条提示词即可加入草稿。';

  @override
  String get promptEditorDiscardChanges => '放弃更改';

  @override
  String get promptEditorDone => '用于草稿';

  @override
  String get promptEditorFieldLabel => '提示词';

  @override
  String get promptStashDeleted => '已删除保存的提示词';

  @override
  String get promptStashIntro => '最新的在前 · 保存在此设备上';

  @override
  String get promptStashEmptyTitle => '还没有保存的提示词';

  @override
  String get promptStashEmptyBody => '在 + 菜单中选择“稍后使用此提示词”，即可在此保留一条提示词。';

  @override
  String get promptStashBusy => '请等待当前步骤完成';

  @override
  String get promptStashRowActions => '已保存提示词的操作';

  @override
  String get promptStashRestoreToDraft => '恢复到草稿';

  @override
  String get promptStashDeleteAction => '删除已保存的提示词';

  @override
  String get modelShortcutsNextRecent => '下一个最近的模型';

  @override
  String get modelShortcutsPreviousRecent => '上一个最近的模型';

  @override
  String get modelShortcutsNoRecent => '请先使用另一个模型，才能切换回来';

  @override
  String get modelShortcutsNoFavorite => '请先在模型选择器中将某个模型标为收藏';

  @override
  String get composerDraftBlockedReason => '请先回答关于这份草稿的问题';

  @override
  String get commandLauncherSubtitle => '在此对话中执行一个操作，或运行此服务器上的命令';

  @override
  String get teamChatRefusedTitle => '任务未被接手';

  @override
  String get teamChatRefusedRetry => '重新发送任务';

  @override
  String get teamChatGoneTitle => '任务已不再列出';

  @override
  String get teamChatGoneBody => '它可能已在团队的电脑上被移除。AI 团队页面会列出当前的任务。';

  @override
  String get teamChatGoneOpenTeam => '打开 AI 团队页面';

  @override
  String connectionReconnectTo(String server) {
    return '重新连接到 $server';
  }

  @override
  String serverDisconnectFrom(String server) {
    return '断开与 $server 的连接';
  }

  @override
  String get localAgentStopNamed => '停止 Claude Code';

  @override
  String get localAgentStartNamed => '启动 Claude Code';

  @override
  String monitorSwitchToTitle(String server) {
    return '切换到 $server？';
  }

  @override
  String monitorSwitchTo(String server) {
    return '切换到 $server';
  }

  @override
  String servicesStartNamed(String service) {
    return '启动 $service';
  }

  @override
  String servicesStopNamed(String service) {
    return '停止 $service';
  }

  @override
  String serverSettingsChangeSignIn(String server) {
    return '更改 $server 的登录信息';
  }

  @override
  String serverSettingsAuthBasic(String user) {
    return '以 $user 进行基本身份验证';
  }

  @override
  String get serverSettingsUpdateHint => '使用 OpenCode 官方安装器；之后请重启服务器。';

  @override
  String get settingsHubModelRow => '模型';

  @override
  String get notifyTurnOnInAndroid => '在 Android 中开启通知';

  @override
  String get serversAddOtherWays => '或使用其他方式连接';

  @override
  String get libraryImportAConversation => '导入对话';

  @override
  String get projectHubHealthSubtitle => '分支、语言服务与格式化工具';

  @override
  String projectHubChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件已更改',
      one: '1 个文件已更改',
      zero: '无更改',
    );
    return '$_temp0';
  }

  @override
  String projectHubTerminalsRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个运行中',
      one: '1 个运行中',
    );
    return '$_temp0';
  }

  @override
  String get projectHubCopyFolderPath => '复制文件夹路径';

  @override
  String get terminalScreenNoTerminalThisServer => '此服务器未共享终端';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return '$server 未共享终端';
  }

  @override
  String get terminalScreenNoTerminalWhy => '只有共享终端的服务器才能在此打开终端。';

  @override
  String get integrationsSignInWaiting => '登录等待中';

  @override
  String get integrationsSignInMayNotHaveStarted => '登录可能未开始';

  @override
  String get integrationsSignInExpired => '登录已过期';

  @override
  String get integrationsSignInFailed => '登录失败';

  @override
  String get integrationsSignInComplete => '已登录 · 点按完成';

  @override
  String integrationsFinishSigningIn(String provider) {
    return '完成 $provider 登录';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return '输入 $provider 的代码';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return '取消 $provider 登录';
  }

  @override
  String get integrationsForgetSignInOnPhone => '在这部手机上忽略此登录';

  @override
  String integrationsSignInActions(String provider) {
    return '$provider 的登录操作';
  }

  @override
  String integrationsAccountCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个账号',
      one: '1 个账号',
    );
    return '$_temp0';
  }

  @override
  String get toolsScreenNoBackgroundSubagents => '无后台子智能体';

  @override
  String get usageRefreshSpending => '刷新花费';

  @override
  String modelPickerUseModel(String model) {
    return '使用 $model';
  }

  @override
  String get modelPickerUseChosenModel => '使用该模型';

  @override
  String get handoffUiComputerCommandLabel => '终端命令';

  @override
  String get formRendererDecline => '拒绝此请求';

  @override
  String get perfTraceActions => '耗时报告操作';

  @override
  String voiceSetupNotDownloaded(String size) {
    return '未下载 · $size';
  }

  @override
  String get voiceSetupDone => '完成';

  @override
  String get voiceAllowMicInSettings => '在 Android 设置中允许麦克风';

  @override
  String sessionContextMessagesSplit(String count, String yours, String agent) {
    return '$count（你的 $yours，智能体 $agent）';
  }

  @override
  String get webSourcesClose => '关闭';

  @override
  String get webSourcesPastedLinks => '你添加的链接';

  @override
  String get thisPhoneHostInApp => '应用内';

  @override
  String get thisPhoneHostTermux => 'Termux 中';

  @override
  String get thisPhoneSetUp => '设置 OpenCode';

  @override
  String get thisPhoneStart => '启动服务器';

  @override
  String get thisPhoneStop => '停止此手机上的 OpenCode';

  @override
  String get thisPhoneUpdate => '更新 OpenCode';

  @override
  String thisPhoneUpdateDetail(String version) {
    return '安装版本 $version';
  }

  @override
  String get thisPhoneAddTools => '添加工具';

  @override
  String get thisPhoneInstalled => '已安装';

  @override
  String get thisPhoneTerminal => '打开终端';

  @override
  String get thisPhoneStorage => '存储';

  @override
  String get thisPhoneRemove => '移除 OpenCode';

  @override
  String get thisPhoneBusy => '请等待当前步骤完成';

  @override
  String get phoneSetupTermuxAllowHow => '在 Termux 中粘贴复制的那一行并按回车。';

  @override
  String get phoneSetupTermuxUpdatingTitle => '正在更新这部手机';

  @override
  String get phoneSetupTermuxStartingTitle => '正在启动服务器';

  @override
  String get phoneSetupTermuxConnecting => '正在连接';

  @override
  String get phoneSetupTermuxLeaveHint =>
      '你可以离开应用。Termux 会继续工作，你回来时这个列表会显示最新进度。';

  @override
  String get phoneSetupTermuxCost => '首次约需 10–15 分钟，占用 Termux 的存储空间';

  @override
  String removeFromPhoneKeepBody(String size) {
    return '将移除 OpenCode 及其工具，释放约 $size。';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured => '将移除 OpenCode 及其工具。';

  @override
  String get removeFromPhoneKeepConfirm => '移除 OpenCode，保留我的项目';

  @override
  String get removeFromPhoneDeleteAll => '全部删除';

  @override
  String get removeFromPhoneDeleteTitle => '删除 OpenCode 和项目？';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return '将删除此手机上的 OpenCode、其工具和所有项目，释放约 $size。此操作无法撤销。';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      '将删除此手机上的 OpenCode、其工具和所有项目。此操作无法撤销。';

  @override
  String get thisPhoneManage => '管理“此手机”';

  @override
  String get chatRequestWho => '智能体';

  @override
  String get chatRequestIfIgnored => '智能体会等你回应，不会丢失任何内容。';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '还有 $count 个请求在等待。',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection => '未连接到服务器，无法在这里回应。';

  @override
  String get chatRequestAlwaysTitle => '始终允许这些请求';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return '从现在起，$context，$patterns 将不经询问直接运行。你可以随时在设置中撤销。';
  }

  @override
  String chatRequestAlwaysConfirm(String what) {
    return '在此项目中始终允许$what？';
  }

  @override
  String get chatRequestAlwaysInProject => '在此项目中';

  @override
  String get chatRequestAlwaysOn => '始终允许';

  @override
  String get chatRequestDetailTool => '工具';

  @override
  String get chatRequestDetailPatterns => '请求的模式';

  @override
  String get chatRequestOtherAnswer => '其他回答';

  @override
  String get chatRequestOtherField => '你的回答';

  @override
  String get formFlowAnsweredElsewhereBody => '此表单已在另一台设备上作答，因此本手机没有发送任何内容。';

  @override
  String get approvalsUiPausedDetail => '这部手机未连接。重新连接后将恢复自动批准。';

  @override
  String get teamUiHomeRunReviewNext => '接下来由审查者检查';

  @override
  String teamUiGateRunStoppedTitle(String title) {
    return '$title 已停止';
  }

  @override
  String get termuxProcsKindParentGone => '父进程已消失';

  @override
  String get termuxProcsKindNoOwner => '无归属';

  @override
  String termuxProcsStopOrphans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '停止 $count 个孤立辅助进程',
      one: '停止 1 个孤立辅助进程',
    );
    return '$_temp0';
  }

  @override
  String termuxProcsStopOrphansTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '停止 $count 个孤立辅助进程？',
      one: '停止该孤立辅助进程？',
    );
    return '$_temp0';
  }

  @override
  String get teamPhoneStopTeamRow => '停止本机上的团队';

  @override
  String get teamPhoneStopTeamRowSupporting => '智能体在原处停止；不会丢失任何内容';

  @override
  String teamUiPhoneWorkingOn(String name) {
    return '正在处理 $name';
  }

  @override
  String get teamUiPhoneVersionsLabel => '引擎版本';

  @override
  String get teamUiPhoneProjectLabel => '项目文件夹';

  @override
  String get phoneServerNameInSentence => '应用内的 Ubuntu';

  @override
  String get teamAgentWorkUnblockedShort => '没有阻塞';

  @override
  String get teamAgentWorkBlockedShort => '已阻塞';

  @override
  String get teamAgentStepCommand => '运行了一条命令';

  @override
  String get teamAgentStepTest => '运行了测试';

  @override
  String get teamAgentStepRead => '读取了一个文件';

  @override
  String get teamAgentStepEdit => '编辑了一个文件';

  @override
  String get teamAgentStepSearch => '搜索了代码';

  @override
  String teamAgentStepTool(String tool) {
    return '使用了 $tool';
  }

  @override
  String get teamAgentLastCommandLabel => '最后一条命令';

  @override
  String get termuxStorageOnlyBuildCaches => '此处只能清理构建缓存';

  @override
  String get termuxStorageWhereItIs => '位置';

  @override
  String termuxStorageCleanBuildCaches(String size) {
    return '清理构建缓存（$size）';
  }

  @override
  String get monitorBackgroundChecks => '后台检查';

  @override
  String get settingsTryDemo => '试用演示';

  @override
  String get searchArchivedConversations => '已归档的对话';

  @override
  String get readAloudConsentEngine => '此处仅提供离线声音，但语音引擎是独立软件，有其自己的隐私条款。';

  @override
  String get readAloudConsentHeard => '你附近的人可能会听到。离开此对话或应用时朗读会停止。';

  @override
  String get transcriptFindStopSearchingAll => '停止搜索更早的消息';

  @override
  String nudgeReviewChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'OpenCode 更改了 $count 个文件。继续之前请查看。',
      one: 'OpenCode 更改了 1 个文件。继续之前请查看。',
    );
    return '$_temp0';
  }

  @override
  String get voiceComponentTitle => '语音输入';

  @override
  String get voiceComponentSummary => '动口代替打字，离线也能用';

  @override
  String get voiceComponentRemove => '移除语音输入';

  @override
  String get voiceComponentRemoveTitle => '要移除语音输入吗？';

  @override
  String voiceComponentRemoveBody(String size) {
    return '将删除语音模型并释放 $size。在这里重新添加之前，语音输入将无法使用。';
  }

  @override
  String get setupAppStageDownloading => '正在下载';

  @override
  String get setupAppStageVerifying => '正在检查下载内容';

  @override
  String kitDiffFilePosition(int index, int count) {
    return '第 $index 项，共 $count 项';
  }

  @override
  String kitDiffFilePositionSpoken(int index, int count) {
    return '文件 $index，共 $count 个';
  }

  @override
  String get kitDiffViewed => '已查看';

  @override
  String get kitDiffSelectHunk => '选择这些行';

  @override
  String get kitCapFlagTerminalTitle => '终端';

  @override
  String get kitCapFlagTerminalWhy => '此服务器不为你打开终端。';

  @override
  String get kitCapFlagToolInventoryTitle => '工具列表';

  @override
  String get kitCapFlagToolInventoryWhy => '此服务器不列出其智能体可用的工具。';

  @override
  String get demoScreenReset => '重置演示';

  @override
  String get demoScreenLeave => '退出演示';

  @override
  String get demoScreenDisclosure => '这里的一切都在此设备上模拟。不会访问任何服务器、提供商或文件。';

  @override
  String capabilityScreenIntroWithGaps(String server) {
    return '$server 决定此应用中显示哪些内容。它做不到的功能不会出现在菜单和标签页里，也不会以灰色显示。缺失的功能在其他 OpenCode 服务器上可用。';
  }

  @override
  String activeContextMessageTitle(String role) {
    String _temp0 = intl.Intl.selectLogic(role, {
      'user': '用户消息',
      'assistant': '助手消息',
      'system': '系统消息',
      'synthetic': '合成消息',
      'skill': '技能消息',
      'shell': 'Shell 消息',
      'compaction': '摘要消息',
      'change': '对话变更',
      'other': '消息',
    });
    return '$_temp0';
  }

  @override
  String get chatDraftCopy => '复制草稿';

  @override
  String get reportProblemDescribeLabel => '发生了什么？';

  @override
  String get reportProblemDescribeHint => '你做了什么、期望什么、实际得到了什么';

  @override
  String get reportProblemDescribeFirst => '请先说明发生了什么';

  @override
  String reportProblemErrorsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条最近的错误',
      one: '1 条最近的错误',
    );
    return '$_temp0';
  }

  @override
  String get reportProblemClearFailed => '无法清除已保存的报告。请重试。';

  @override
  String get reportProblemLinkCopiesDiagnostics =>
      '诊断信息过长，无法放进链接。打开表单时会复制它们，请粘贴到其中的“诊断”字段。';

  @override
  String get reportProblemLinkCopiesWhole => '报告过长，无法放进链接。打开表单时会复制它，请粘贴到表单中。';

  @override
  String get reportProblemCopied => '报告已复制';

  @override
  String get reportProblemDiagnosticsCopied => '诊断信息已复制：请粘贴到表单中';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已保留 $count 条错误',
      one: '已保留 1 条错误',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail => 'Python、AI Team、语音输入等';

  @override
  String get phoneSetupTermuxOtherRuntime =>
      'Termux 正在运行另一个版本的 OpenCode。请先在“在这部手机上”中切换，然后继续设置。';

  @override
  String get undoFromHereNowAction => '立即撤销';

  @override
  String undoFromHereBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '此提示词及其后的 $count 条消息会被移除，文件会回到它之前的状态。在你发送下一条提示词之前，你都可以恢复。',
      one: '此提示词及其后的一条消息会被移除，文件会回到它之前的状态。在你发送下一条提示词之前，你都可以恢复。',
      zero: '此提示词会被移除，文件会回到它之前的状态。在你发送下一条提示词之前，你都可以恢复。',
    );
    return '$_temp0';
  }

  @override
  String get undoFromHereBodyUnknown =>
      '此提示词及其之后的所有内容会被移除，文件会回到它之前的状态。在你发送下一条提示词之前，你都可以恢复。';

  @override
  String get undoFromHereFilesLabel => '智能体在其后编辑的文件';

  @override
  String get undoFromHereNoEdits => '智能体报告此提示词之后没有编辑任何文件。';

  @override
  String get undoneStatus => '已从某条提示词处撤销';

  @override
  String get undonePutBack => '恢复';

  @override
  String reviewRevertScreenIntroCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '此提示词及其后的 $count 条消息已被隐藏。在你于下方做出选择之前，不会有任何最终变更。',
      one: '此提示词及其后的一条消息已被隐藏。在你于下方做出选择之前，不会有任何最终变更。',
      zero: '此提示词已被隐藏；其后没有其他内容。在你于下方做出选择之前，不会有任何最终变更。',
    );
    return '$_temp0';
  }

  @override
  String reviewRevertKeepConsequenceCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已隐藏的提示词及其后的 $count 条消息会被删除',
      one: '已隐藏的提示词及其后的一条消息会被删除',
      zero: '已隐藏的提示词会被删除',
    );
    return '$_temp0';
  }

  @override
  String addServerConnectedHost(String host) {
    return '已连接到 $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return '$host 尚未响应。网络较慢时可能需要一些时间。';
  }

  @override
  String get addServerCheckCancel => '停止检查';

  @override
  String get addServerUseTailscale => '使用 Tailscale';

  @override
  String get addServerStepsLabel => '添加服务器进度';

  @override
  String get addServerStepKind => '运行的内容';

  @override
  String get addServerStepTailscale => '此手机上的 Tailscale';

  @override
  String get addServerStepPair => '配对或输入地址';

  @override
  String get addServerStepAddress => '地址与登录';

  @override
  String get addServerStepCheck => '检查中';

  @override
  String get addServerStepReady => '就绪';

  @override
  String addServerReadyTitle(String name) {
    return '$name 已连接';
  }

  @override
  String get addServerReadyBody => '接下来会打开它的对话。你可以新建一个，或继续已有的对话。';

  @override
  String addServerReadyOpen(String name) {
    return '打开 $name';
  }

  @override
  String get handoffUiLinkAddTitle => '添加此服务器？';

  @override
  String get handoffUiLinkAddServer => '添加服务器';

  @override
  String get failedJobReport => '报告此失败';

  @override
  String get sessionsOlderLoadFailed => '无法加载较早的对话。';

  @override
  String get sessionsLoadFailed => '无法加载你的对话。';

  @override
  String get sessionsListChanged => '服务器上的对话列表已变化。请刷新以查看较早的对话。';

  @override
  String get handoffUiComputerUnsupported => '此服务器无法提供在电脑上继续该对话的命令。';

  @override
  String get handoffUiComputerChanged => '此对话已移动，或其服务器已变更。请返回后重试。';

  @override
  String commandAuthSheetIntro(String provider) {
    return '此次登录会在你的服务器上运行，而不是在此手机上。只有在你信任该服务器和 $provider 时才启动。你可能需要在服务器上完成步骤。';
  }

  @override
  String commandAuthCheckNamed(String provider) {
    return '立即检查 $provider 的登录';
  }

  @override
  String credentialRemoveAccountTitle(String provider, String name) {
    return '移除 $provider 账号“$name”？';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return '移除“$name”';
  }

  @override
  String get discoverServicesAliases => '服务 开发服务器 预览 日志 运行命令 进程';

  @override
  String get discoverCloudEnvironmentsAliases => '云环境 托管 工作区 远程 沙箱';

  @override
  String promptRestoredWithout(String names) {
    return '已恢复，但不含$names；发送前请重新附加';
  }

  @override
  String get promptStashOlderDraftsWaiting => '一些较早的草稿尚未迁移到此处。它们已保留在此设备上。';

  @override
  String get promptStashOlderDraftsFull => '较早的草稿正在等待迁移到此处。请删除已保存的提示词以腾出空间。';

  @override
  String get quotaCollectorGuide => '打开采集器指南';

  @override
  String get usageSpentToday => '今日花费';

  @override
  String get usageSpentThirtyDays => '最近 30 天花费';

  @override
  String get usageSpentYear => '今年花费';

  @override
  String get usageSpentAllTime => '累计花费';

  @override
  String usageSpentPeriod(String period) {
    return '花费 · $period';
  }

  @override
  String get automationTitle => '自动运行的内容';

  @override
  String get automationSearchAliases =>
      '自动化 自动 监督 自动批准 审批 始终允许 权限 后台 监视 监控 团队 级别';

  @override
  String get automationSaveFailed => '此选择未能保存在此手机上。上方级别仍是当前使用的级别；请重试。';

  @override
  String get automationSaving => '正在保存…';

  @override
  String get automationTeamLabel => 'AI Team 自主决定的程度';

  @override
  String get automationTeamFootnote => '新的团队任务会从这个级别开始。开始单个任务时，你可以为它另选级别。';

  @override
  String get automationWithoutAskingLabel => '无需询问你';

  @override
  String get automationSavedRulesDetail => '智能体在此处可以不经询问就运行的操作。';

  @override
  String phoneSetupStartTermuxProgressHeadline(int percent) {
    return 'Termux 中的设置已完成 $percent%';
  }

  @override
  String get teamPhoneReadyChooseTitle => '选择团队的项目';

  @override
  String get teamPhoneReadyTurningOnTitle => '正在开启 AI 团队';

  @override
  String get teamPhoneReadyFailedTitle => 'AI 团队未能启动';

  @override
  String teamPhoneReadyBody(String project) {
    return '给它第一个任务。它会规划工作、在智能体之间分工，并把成果带回$project。';
  }

  @override
  String get teamPhoneReadyFirstTask => '给团队第一个任务';

  @override
  String get teamUiStateNotAnsweringPhone => '团队在本机启动期间，应用会持续尝试。';

  @override
  String get teamUiStateNotAnsweringComputer => '应用会持续尝试。请确认你的电脑已开机并联网。';

  @override
  String teamUiStateNotAnsweringComputerNamed(String computer) {
    return '应用会持续尝试。请确认 $computer 已开机并联网。';
  }

  @override
  String get teamHomeChangeAddress => '更改地址';

  @override
  String get teamHomeTurnOffFailed => '无法停止本机上的团队，因此它仍在运行。请重试。';

  @override
  String get teamHomeHostStopped => '已停止';

  @override
  String get teamHomeHostCooling => '正在降温';

  @override
  String get teamHomeHostStoppedForHeat => '为降温而停止';

  @override
  String teamHomeHeatPausedLine(String time) {
    return '手机在 $time 过热，因此团队已暂停。手机降温后它会自行继续。';
  }

  @override
  String teamHomeHeatStoppedLine(String time) {
    return '手机在 $time 温度过高，因此团队已停止。工作已保留，手机降温后它会重新启动。';
  }

  @override
  String get teamHomePhoneControls => '保持运行、停止或移除';

  @override
  String teamHomeSpentToday(String usage) {
    return '今天 · $usage';
  }

  @override
  String get teamHomeSpentHint => '团队自当地午夜起的整体估算用量。服务器不会报告每个任务的费用。';

  @override
  String get teamHomeSpentPartial => '今天的部分用量尚无价格，因此实际费用高于此数。';

  @override
  String teamIntroNotFound(String server) {
    return '在 $server 上未找到 AI 团队';
  }

  @override
  String get chatErrorModelNotFound => '服务器上没有这个模型。';

  @override
  String get chatErrorContextOverflow => '这段对话对模型来说太长了。';

  @override
  String get chatErrorProviderAuth => '模型服务商需要你重新登录。';

  @override
  String get chatErrorOutputLength => '回复达到了模型的长度上限。';

  @override
  String get chatErrorContentFilter => '服务商的安全过滤器中止了此回复。';

  @override
  String get chatErrorUnknown => '智能体因错误而停止。';

  @override
  String modelPickerThinkingChip(String level) {
    return '思考：$level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return '智能体：$agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条排队的提示词将移至已保存的提示词',
      one: '1 条排队的提示词将移至已保存的提示词',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '其中 $count 条可能已发送',
      one: '其中 1 条可能已发送',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '移除并删除 $count 条排队的提示词',
      one: '移除并删除排队的提示词',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return '$name 的排队提示词已变化，因此未移除任何内容。请再次移除以查看新的数量。';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return '无法将 $name 的排队提示词移至已保存的提示词，因此未移除任何内容。请删除一些已保存的提示词或释放存储空间，然后重试。';
  }

  @override
  String get settingsHubGroupAgent => '智能体';

  @override
  String get settingsHubGroupConversations => '对话';

  @override
  String get settingsHubGroupThisApp => '此应用';

  @override
  String get settingsHubProvidersRow => '提供商与账号';

  @override
  String get settingsHubToolsRow => '工具';

  @override
  String get settingsHubShowReasoning => '显示推理过程';

  @override
  String get settingsHubShowTimestamps => '显示时间、Token 和费用';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '此服务器上有 $count 项设置不可用',
      one: '此服务器上有 1 项设置不可用',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => '原因';

  @override
  String get privacyPolicyTitle => '隐私政策';

  @override
  String get aboutHelpSection => '提示与快捷方式';

  @override
  String get settingsHubSearchToolsAliases =>
      '工具 mcp 集成 命令 技能 参考 斜杠 能力 插件 外部智能体 a2a';

  @override
  String get aiteamComponentTurnOff => '关闭 AI Team';

  @override
  String get aiteamComponentTurnOffTitle => '关闭 AI Team？';

  @override
  String get aiteamComponentTurnOffBody => '团队会停止并保持关闭，直到你再次开启。';

  @override
  String get aiteamComponentTurnOffKept => '它的任务和设置，以及你的项目都会保留';

  @override
  String get aiteamComponentTurnOffFailed => '无法关闭 AI Team。请重试，或重启应用。';

  @override
  String thisPhoneRemoveTool(String tool) {
    return '移除 $tool';
  }

  @override
  String thisPhoneRemoveToolTitle(String tool) {
    return '移除 $tool？';
  }

  @override
  String thisPhoneRemoveToolBody(String size) {
    return '大约会释放 $size 空间。你可以稍后从“添加工具”重新添加。';
  }

  @override
  String get thisPhoneRemoveToolBodyUnmeasured => '你可以稍后从“添加工具”重新添加。';

  @override
  String thisPhoneRemoveToolNeededBy(String tools) {
    return '被以下功能需要：$tools';
  }

  @override
  String get thisPhoneRemoveToolDetail => '从此手机删除它。你的项目会保留。';

  @override
  String get thisPhoneRemovePythonDetail => '删除 pip 和 venv。Python 与你的项目会保留。';

  @override
  String get thisPhoneRemovePythonLost => 'pip 和 venv 会被删除，连同只有它们使用的软件包';

  @override
  String get thisPhoneRemovePythonKept => 'Python 本身和你的项目会保留';

  @override
  String get thisPhoneRemoveTeamDetail => '删除团队的程序、任务和设置。项目会保留。';

  @override
  String get thisPhoneRemoveTeamLost => '团队的程序、任务和设置会被删除';

  @override
  String get thisPhoneRemoveTeamLostWork => '尚未并入项目的团队工作会丢失';

  @override
  String get thisPhoneRemoveTeamKept => '你的项目文件及其 git 历史会保留';

  @override
  String get thisPhoneRemoveVoiceDetail => '删除语音模型。语音输入会停止，直到你重新添加。';

  @override
  String get thisPhoneRemoveVoiceLost => '语音输入会停止，直到你重新添加';

  @override
  String get thisPhoneRemoveToolKept => '你的项目会保留';

  @override
  String get removeFromPhoneKeepLost => 'OpenCode 内的对话和设置将被删除';

  @override
  String get removeFromPhoneKeepKept => '你的项目会保留，重新设置后即可恢复';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return '你的项目（$size）会保留，重新设置后即可恢复';
  }

  @override
  String get removeFromPhoneDeleteLost => '未保存在其他位置的项目文件将永久丢失';

  @override
  String get productErrorTimedOut => '服务器响应时间过长。请重试。';

  @override
  String get productErrorCertificate => '服务器的安全证书不受信任，应用已停止。请检查服务器地址。';

  @override
  String get productErrorSignIn => '服务器未接受登录。请在服务器设置中检查密码。';

  @override
  String get productErrorNotFound => '服务器找不到它。它可能已被移动或删除。';

  @override
  String get productErrorConflict => '它在此期间已在服务器上发生变化。请刷新后重试。';

  @override
  String get productErrorBusy => '服务器正忙。请稍等片刻后重试。';

  @override
  String get productErrorRejected => '服务器未接受该请求。请重试，或报告该问题。';

  @override
  String get productErrorUnknown => '操作失败。“详情”会显示具体情况。请重试，或报告该问题。';

  @override
  String get productErrorUnexpected => '应用无法理解服务器的响应。请重试，或报告该问题。';

  @override
  String get productErrorDevice => '此设备上出了点问题。请重试。';

  @override
  String get productErrorStorage => '应用无法在此设备上读取或保存文件。';

  @override
  String get productErrorTermux => 'Termux 未能完成该操作。请确认 Termux 已安装并已打开，然后重试。';

  @override
  String get productErrorDetailsLabel => '错误详情';

  @override
  String productErrorServer(int code) {
    return '服务器出现问题（错误 $code）。请稍后重试。';
  }

  @override
  String get usageBudgetInvalidUsd => '请输入大于 0 的金额，例如 2.50';

  @override
  String get usageBudgetInvalidTokens => '请输入大于 0 的整数 token 数';

  @override
  String get usageBudgetSaveUsd => '保存美元预算';

  @override
  String get usageBudgetSaveTokens => '保存 token 预算';

  @override
  String sessionDestinationMoveWithChanges(String destination) {
    return '带更改移动到$destination';
  }

  @override
  String sessionDestinationWarpWithChanges(String destination) {
    return '带更改副本移动到$destination';
  }

  @override
  String sessionDestinationMoveTo(String destination) {
    return '移动到$destination';
  }

  @override
  String sessionDestinationNoChanges(String place) {
    return '$place中没有未提交的更改，因此只移动对话。';
  }

  @override
  String get settingsBackgroundOffFailed => 'Android 未能关闭后台模式。';

  @override
  String defaultReviewScopeNotice(String scope) {
    return '显示的是$scope：这是有更改的视图。';
  }

  @override
  String defaultModelNotice(String model) {
    return '正在使用 $model，这是此服务器的默认模型。';
  }

  @override
  String get defaultModelChange => '选择其他模型';

  @override
  String teamControlReceiptSending(String control) {
    return '$control · 发送中…';
  }

  @override
  String get teamGateCardRunFailedOpen => '选择要做什么';

  @override
  String get teamGateCardIfIgnored => '团队会等你答复后才继续。不会丢失任何内容。';

  @override
  String get teamGateCardIfIgnoredFailed => '在有人处理之前，任务保持停止状态。';

  @override
  String get teamGateCardIfIgnoredReview => '工作会等待审查。不会丢失任何内容。';

  @override
  String termuxProcsBudget(int count, int limit) {
    return '$count / $limit 个后台进程';
  }

  @override
  String termuxProcsBudgetNote(int limit) {
    return '所有应用合计超过 $limit 个时，Android 12 及更高版本可能会停止最旧的进程。';
  }

  @override
  String termuxProcsBudgetOver(int limit) {
    return '超过 $limit 个：Android 可能随时停止其中最旧的。';
  }

  @override
  String get termuxProcsLoadFailedBody => 'Termux 没有应答。请打开 Termux 后重试。';

  @override
  String get termuxProcsRefreshFailed => '无法再次读取列表，因此显示上次读取的结果。';

  @override
  String get termuxProcsStopFailed => '无法停止。请重试，或在 Termux 中停止它。';

  @override
  String get termuxProcsKindOpenCode => 'OpenCode 服务器';

  @override
  String get termuxProcsKindAiTeam => 'AI 团队';

  @override
  String get termuxProcsKindClaudeCode => 'Claude Code';

  @override
  String get termuxProcsKindDevService => '开发服务';

  @override
  String get termuxProcsKindTerminal => '终端';

  @override
  String get termuxProcsKindHelper => '辅助进程';

  @override
  String get termuxProcsKindHostApp => 'Termux 应用';

  @override
  String get termuxProcsBusy => '繁忙';

  @override
  String get termuxProcsIdle => '空闲';

  @override
  String termuxProcsRunningFor(String elapsed) {
    return '已运行 $elapsed';
  }

  @override
  String termuxProcsStopKindBody(String names) {
    return '$names：每个都会先礼貌停止，5 秒后强制结束。';
  }

  @override
  String termuxProcsStopKind(int count, String things) {
    return '停止全部 $count 个$things';
  }

  @override
  String termuxProcsStopKindTitle(int count, String things) {
    return '停止全部 $count 个$things？';
  }

  @override
  String get termuxProcsKindsAiTeam => 'AI 团队进程';

  @override
  String get termuxProcsKindsClaudeCode => 'Claude Code 进程';

  @override
  String get termuxProcsKindsDevServices => '开发服务';

  @override
  String get termuxProcsKindsTerminals => '终端';

  @override
  String get termuxProcsKindsHelpers => '辅助进程';

  @override
  String get termuxProcsStopDevRestart => '下次构建需要时会重新启动它们。';

  @override
  String get termuxProcsAboutClaudeCode => 'Claude Code，编程智能体。停止它会结束它正在编写的回答。';

  @override
  String get termuxProcsAboutTerminal => '一个终端。停止它会关闭它以及其中运行的内容。';

  @override
  String get termuxProcsAboutHostApp => 'Termux 应用本身。不会从此处停止。';

  @override
  String get termuxProcsAverageCpu => '平均处理器占用';

  @override
  String get termuxProcsCpuTime => '处理器时间';

  @override
  String get consentBatteryTitle => '让服务器保持运行？';

  @override
  String get consentBatteryBody =>
      '应用关闭时，Android 可能会停止此手机上的服务器。允许后台运行后，Android 会请你确认。';

  @override
  String get consentBatteryAllow => '允许后台运行';

  @override
  String get consentMakerTitle => '自动重启服务器？';

  @override
  String consentMakerBody(String maker) {
    return '$maker 手机系统会停止不允许自启动的应用，服务器随后会保持关闭。请在打开的页面中为此应用开启自启动。';
  }

  @override
  String get consentMakerBodyUnnamed =>
      '部分手机系统会停止不允许自启动的应用，服务器随后会保持关闭。请在打开的页面中为此应用开启自启动。';

  @override
  String get consentMakerAllow => '打开自启动设置';

  @override
  String get consentNotNow => '暂不';

  @override
  String get consentSaveFailed => '你的回答未能保存到此手机，因此没有做任何更改。请重试。';

  @override
  String get consentStorageFailed =>
      '无法读取你先前在此服务器上的回答，因此应用目前不会再询问这些。请重新打开此页面重试。';

  @override
  String get consentGroupLabel => '你的回答';

  @override
  String get consentRowBattery => '后台运行';

  @override
  String get consentRowMaker => '自动重新启动';

  @override
  String get consentRowNeedsYou => '智能体需要我时通知我';

  @override
  String get consentRowAlwaysAllow => '始终允许提议';

  @override
  String get consentWhyBattery => '应用关闭时，Android 可能会停止此手机上的服务器。';

  @override
  String get consentWhyMaker => '此手机在服务器停止后可能无法重新启动它。';

  @override
  String get consentWhyNeedsYou => '智能体等待你回答时，你不会收到通知。';

  @override
  String get consentAllowedSystem => '由手机自身设置决定。点击可检查或关闭。';

  @override
  String get consentAllowedNeedsYou => '点击可在“通知”中更改。';

  @override
  String get consentWhyAlwaysAllow => '每次仍会询问。点击可再次获得“始终允许”的选项。';

  @override
  String get consentValueAllowed => '已允许';

  @override
  String get consentValueDeclined => '已拒绝';

  @override
  String get consentValueUnanswered => '未回答';

  @override
  String consentValueDeclinedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已拒绝 $count 次',
      one: '已拒绝 1 次',
    );
    return '$_temp0';
  }

  @override
  String get consentAlwaysAgainTitle => '再次提供“始终允许”？';

  @override
  String get consentAlwaysAgainBody =>
      '再过 3 次相同的询问后，应用会再次提供“始终允许”。在你确认之前，不会允许任何操作。';

  @override
  String get consentAlwaysAgainConfirm => '再次提供';

  @override
  String consentAlwaysAllowQuestion(String what) {
    return '已询问 3 次。始终允许 $what？';
  }

  @override
  String get consentAlwaysAllowDecline => '继续询问';

  @override
  String get consentAlwaysAllowFailed => '服务器未保存“始终允许”。请求仍在等待；请重试，或仅这一次作答。';

  @override
  String get consentAlwaysAllowTitle => '始终允许此请求？';

  @override
  String get consentNeedsYouAllow => '开启通知';

  @override
  String get bootstrapOpeningTitle => '正在打开…';

  @override
  String get bootstrapOpeningBody => '正在读取你已保存的服务器。';

  @override
  String get bootstrapFailedTitle => '无法读取已保存的服务器';

  @override
  String get bootstrapFailedBody => '如果手机刚刚重启，请先解锁，然后重试。';

  @override
  String get shareFailedLine => '共享文本已保存 · 无法打开对话';

  @override
  String get shareFailedAgainLine => '仍无法打开对话 · 共享文本已保存';

  @override
  String get shareFailedCopy => '复制共享文本';

  @override
  String get shareFailedDiscard => '丢弃共享文本';

  @override
  String get shareDiscarded => '已丢弃共享文本';

  @override
  String get shareConnectionChanged => '打开期间服务器或项目已变更。请重试。';

  @override
  String get appNewConversationFailed => '无法开始新对话';

  @override
  String get rootPhoneServerStartFailed => 'OpenCode 未能在此手机上启动';

  @override
  String get connectionFailureLocalCodexBody => '此手机上应有本地 Codex 监听器响应，但没有任何响应。';

  @override
  String get connectionFailureRemoteCodexBody => 'Codex 端点没有响应。';

  @override
  String get connectionFailureLoopbackBody =>
      '应用在此手机上查找服务器，但没有得到响应。请启动该服务器，或重新连接把服务器带到这里的隧道，然后重试。';

  @override
  String get connectionFailureTimedOutBody =>
      '该地址上有东西，但没有回复。通常是中间的网络问题，而不是服务器。';

  @override
  String get connectionFailureNothingAnsweredBody =>
      '没有任何响应。可能是服务器未运行，或此手机无法访问其地址。';

  @override
  String get connectionFailureUnknownBody => '连接失败。具体原因见“详情”。';

  @override
  String get connectionFailureTailnetCheck =>
      '这是 Tailscale 地址：此手机和服务器上的 Tailscale 都已开启吗？';

  @override
  String get teamHomeSpentHistoryMissing => '今天的部分记录缺失，因此实际费用高于此数。';

  @override
  String get teamHomeSpentNotRecording => '团队当前未在统计新的用量。';

  @override
  String get teamRunCostUnreported => '单个任务未报告费用。AI 团队页面显示整个团队今天的估算。';

  @override
  String get teamHomeUpkeepTitle => '团队维护';

  @override
  String get teamHomeUpkeepPatrol => '巡检';

  @override
  String get teamHomeUpkeepChore => '杂务';

  @override
  String teamHomeUpkeepGroup(int count, String kind, String state) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$kind ×$count · $state',
      one: '$kind · $state',
    );
    return '$_temp0';
  }

  @override
  String get teamAgentLooksAfterTeam => '整个团队';

  @override
  String get teamAgentLooksAfterWatchdog => '看门狗';

  @override
  String get teamAgentLooksAfterWorkers => '工作者';

  @override
  String get servicesStopConfirm => '停止服务';

  @override
  String get servicesRestartConfirm => '重启服务';

  @override
  String get managedWorkspacesRemoveConfirm => '移除环境';

  @override
  String managedWorkspacesCreateIn(String provider) {
    return '在 $provider 中';
  }

  @override
  String get voiceAutoSetupTitle => '语音输入';

  @override
  String get voiceAutoSetupChecking => '正在检查此手机能运行哪些模型';

  @override
  String get voiceAutoSetupOffer =>
      '用说话代替打字。语音会在此手机上转为文字，即使离线也可以，音频绝不会离开设备。需要一次性下载。';

  @override
  String voiceAutoSetupPicked(String model) {
    return '已按此手机的内存挑选 $model 语音模型';
  }

  @override
  String get voiceAutoSetupMobileData => '你正在使用移动数据。此次下载会计入你的流量套餐。';

  @override
  String get voiceAutoSetupMaybeMetered => '此连接可能会计入流量套餐。';

  @override
  String voiceAutoSetupDownload(String size) {
    return '下载 $size';
  }

  @override
  String voiceAutoSetupDownloadMobile(String size) {
    return '使用移动数据下载 $size';
  }

  @override
  String get voiceAutoSetupOtherModel => '选择其他语音模型';

  @override
  String get voiceAutoSetupNotified => '进度也会显示在通知中。完成后即开始聆听。';

  @override
  String get voiceAutoSetupStartsAfter => '完成后即开始聆听。';

  @override
  String get voiceAutoSetupReady => '语音模型已在此手机上。';

  @override
  String get voiceAutoSetupChooseModel => '选择语音模型';

  @override
  String get voiceAutoSetupUnknownMemory => '此手机未说明内存容量，因此没有自动挑选语音模型。';

  @override
  String get voiceAutoSetupOffline => '无网络连接。请连接后重试。';

  @override
  String get voiceAutoSetupBusy => '已有语音模型正在下载。';

  @override
  String get voiceAutoSetupShowDownload => '显示下载';

  @override
  String get voiceAutoSetupNoCapture => '此手机无法为语音输入录制语音。';

  @override
  String get voiceAutoSetupDetailFiles => '文件';

  @override
  String get voiceAutoSetupDetailSize => '确切大小';

  @override
  String get voiceAutoSetupDetailMemory => '内存';

  @override
  String voiceAutoSetupDetailMemoryValue(int required, int available) {
    return '需要 $required MB；此手机有 $available MB';
  }

  @override
  String get terminalScreenEmptyTitle => '还没有终端';

  @override
  String terminalScreenEmptyBody(String project) {
    return '在 $project 中启动一个。';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => '在此项目中启动一个。';

  @override
  String get integrationsProvidersExplanation => '此服务器可用的模型提供商。连接一个即可开始对话。';

  @override
  String get integrationsResourcesExplanation => '已连接的 MCP 服务器提供给智能体的文件和数据。';

  @override
  String externalAgentsStopTaskTitle(String task) {
    return '停止“$task”？';
  }

  @override
  String externalAgentsStopTaskConfirm(String agent) {
    return '请 $agent 停止';
  }

  @override
  String get externalAgentsStopTaskKeep => '继续运行';

  @override
  String toolsDetailMenu(String tool) {
    return '$tool 的操作';
  }

  @override
  String kitDurationHours(int hours) {
    return '$hours 小时';
  }

  @override
  String kitDurationHoursMinutes(int hours, int minutes) {
    return '$hours 小时 $minutes 分钟';
  }

  @override
  String kitDurationDays(int days) {
    return '$days 天';
  }

  @override
  String kitDurationDaysHours(int days, int hours) {
    return '$days 天 $hours 小时';
  }

  @override
  String kitToolFor(String duration) {
    return '用时 $duration';
  }

  @override
  String kitSinceWaitingForLong(String duration) {
    return '已等待 $duration';
  }

  @override
  String get teamChatLeadRoutedIt => '已发送给工作者';

  @override
  String get teamChatLeadStartingIt => '工作者已启动';

  @override
  String get teamChatLeadClaimedWorkerIt => '工作者接手了任务';

  @override
  String get teamChatLeadPushedIt => '其更改已推送到分支';

  @override
  String get teamChatLeadReviewIt => '已交给审查';

  @override
  String get teamChatLeadMergedIt => '已合并';

  @override
  String get teamChatLeadStepFailedIt => '它失败了';

  @override
  String get teamChatLeadStepCancelledIt => '它被取消了';

  @override
  String teamChatNowNoProgress(String elapsed) {
    return '已 $elapsed 无进展';
  }

  @override
  String teamChatNoProgressBody(String name, String time) {
    return '$name 自 $time 起未推进此任务。你可以催促它继续、重启它，或报告问题。';
  }

  @override
  String teamChatNoProgressBodyNoControls(String name, String time) {
    return '$name 自 $time 起未推进此任务。此服务器无法从这里催促或重启它；请报告问题，或检查团队的电脑。';
  }

  @override
  String get teamChatNoProgressReport => '报告问题';

  @override
  String teamChatNoProgressReportTitle(String elapsed) {
    return '已 $elapsed 无进展';
  }

  @override
  String get teamTaskDetailsReported => '服务器报告的内容';

  @override
  String get kitToolOpenDetails => '打开详情';

  @override
  String get teamStartRunKeepInBacklog => '留在待办';

  @override
  String workRunawayStopped(String helper) {
    return '已停止 $helper';
  }

  @override
  String workRunawayStopFailed(String helper) {
    return '无法停止 $helper。请重试，或在 Termux 中停止它。';
  }

  @override
  String get serverSettingsUpdateCommandsDetail => '请在服务器所在电脑的终端中运行；本应用无法为它更新。';

  @override
  String get serverSettingsUpdateCommandsCopied => '已复制。请在服务器所在电脑的终端中运行。';

  @override
  String hostServiceTitle(String server) {
    return '$server 上的 OpenCode';
  }

  @override
  String hostServiceIntro(String server) {
    return '这些命令会在 $server 的电脑上运行；请将它们逐条粘贴到那台电脑的终端中。';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return '待办 · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList => 'OpenCode 无法列出你的 tailnet 中的设备。';

  @override
  String get productErrorStagedRevert => '发送此排队的提示词之前，请先查看已暂存的撤销。';

  @override
  String teamWatchComposerHint(String name) {
    return '给 $name 发消息…';
  }

  @override
  String teamWatchAboutRole(String role) {
    return '关于$role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return '无法读取 $name 的排队提示词。已保留该服务器及其排队提示词。待队列可读取后，再尝试移除。';
  }

  @override
  String get bootstrapStartFresh => '重新开始';

  @override
  String get bootstrapStartFreshTitle => '移除已保存的登录信息？';

  @override
  String get bootstrapStartFreshBody =>
      '这会从此手机移除已保存的密码和连接令牌，并清除所选服务器。你已保存的服务器、排队的提示词和草稿会保留。';

  @override
  String get bootstrapStartFreshConfirm => '移除已保存的登录信息';

  @override
  String get bootstrapResettingTitle => '正在移除已保存的登录信息…';

  @override
  String get bootstrapResettingBody => '请保持应用打开，直到完成。';

  @override
  String get bootstrapResetFailedTitle => '重置登录失败';

  @override
  String get bootstrapResetFailedBody => '部分已保存的登录信息无法移除。请重试。';

  @override
  String get workStalled => '已停滞';

  @override
  String get teamNowActivityPlanning => '正在等待规划';

  @override
  String get teamNowActivityWaitingForWorker => '正在等待工作者';

  @override
  String get teamNowActivityStartingWorker => '正在启动工作者';

  @override
  String get teamNowActivityWorking => '正在处理你的任务';

  @override
  String get teamNowActivityReviewing => '正在审查更改';

  @override
  String get teamNowActivityNeedsYou => '正在等待你的答复';

  @override
  String get teamNowActivityDelayed => '耗时超出预期';

  @override
  String get teamNowActivityUnconfirmed => '请求未确认';

  @override
  String get teamNowActivityRefused => '请求未被接受';

  @override
  String get teamNowActivityUnavailable => '团队没有应答';

  @override
  String get teamNowActivityCompleted => '已完成';

  @override
  String get teamNowActivityFailed => '无法完成';

  @override
  String get teamNowActivityCancelled => '已停止';

  @override
  String get teamNowReasonNoPlanReported => '尚未报告任何计划。原因未知。';

  @override
  String get teamNowReasonNoWorkerReported => '尚未报告任何工作者。';

  @override
  String get teamNowReasonWorkerStarting => '工作者已启动，但尚未开始任务。';

  @override
  String teamModelRowTitle(String model) {
    return '工作者使用 $model';
  }

  @override
  String get teamModelDefault => '与本机的 OpenCode 相同';

  @override
  String get teamModelDefaultHint => '使用本机 OpenCode 当前设定的模型。';

  @override
  String get teamModelChange => '更改。将在下次工作者启动时生效。';

  @override
  String get teamModelSheetTitle => '工作者使用的模型';

  @override
  String get teamModelSheetNote => '仅列出本机 OpenCode 可用的模型。已在运行的工作者会保持其模型。';

  @override
  String get teamModelNoneLoaded => '本机的模型尚未加载。请关闭此页，稍后重试。';

  @override
  String get teamModelFailed => '无法更改模型。团队会保留原来的模型。';

  @override
  String get teamNowReasonWorkerPreparing => '工作者正在准备：创建其文件夹并启动其程序。';

  @override
  String get teamNowReasonWorkerRunning => '工作者的程序正在运行。团队尚未报告任务已送达。';

  @override
  String get teamNowReasonWorkerTaskDelivered => '任务已送达工作者。它正在阅读，随后开始。';

  @override
  String teamNowLastStart(String duration) {
    return '上次耗时 $duration';
  }

  @override
  String get teamUiHostPhraseBusyStartingWorker => '正忙于启动工作者';

  @override
  String get teamNowReasonWorkInProgress => '任务正在处理中。';

  @override
  String get teamNowReasonReviewPending => '审查或完成仍在等待。';

  @override
  String get teamNowReasonAnswerNeeded => '团队正在等待你的答复。';

  @override
  String get teamNowReasonWorkerCouldNotStart => '工作者未能保持运行。';

  @override
  String get teamNowReasonProviderLimit => 'AI 服务报告了用量上限。';

  @override
  String get teamNowReasonWorkTakingLonger => '工作耗时超出预期。';

  @override
  String get teamNowReasonConfirmationMissing => '无法确认请求是否送达。重新发送前请先核实。';

  @override
  String get teamNowReasonRequestRefused => '请求未被接受。';

  @override
  String get teamNowReasonConnectionUnavailable => '断开连接时无法检查进展。';

  @override
  String get teamNowReasonCauseUnknown => '原因未知。请查看团队正在做什么。';

  @override
  String get teamNowWhyPlanning =>
      '规划器会把你的任务拆成步骤。团队列出步骤后，此对话就会跟进该任务。在此停止跟进并不会取消团队电脑上的任务。';

  @override
  String get teamNowWhyWaitingForWorker => '团队会定期查找新工作，并在有空闲时启动工作者。';

  @override
  String get teamNowWhyStartingWorker =>
      '新工作者会创建自己的项目副本，并在阅读任务之前启动其程序。这是手机上较慢的阶段，上方显示的是团队报告的阶段。';

  @override
  String get teamNowWhyWorking => '工作者在自己的副本上做出更改，然后交给审查。';

  @override
  String get teamNowWhyReviewing => '审查者会在合并前检查更改。';

  @override
  String get teamNowWhyUnconfirmed => '应用已发送任务但未收到回应。再次发送可能会启动两次，因此请先查看规划器。';

  @override
  String get teamNowWhyWorkerCouldNotStart => '工作者在启动过程中停止。它的对话可能说明原因。';

  @override
  String get teamNowWhyProviderLimit =>
      'AI 服务会限制一个周期内的可用量。额度重置后工作会继续，你也可以停止该任务。';

  @override
  String get teamNowWhyWorkTakingLonger => '大型任务可能需要较长时间。观看工作者可判断它是否仍在推进。';

  @override
  String get teamNowWhyCauseUnknown => '团队报告的内容没有说明它在等待什么。';

  @override
  String get teamNowWhyHide => '隐藏';

  @override
  String get teamNowNextPlan => '接下来：团队列出步骤';

  @override
  String get teamNowNextWorker => '接下来：工作者启动';

  @override
  String get teamNowNextWork => '接下来：工作者开始任务';

  @override
  String get teamNowNextReview => '接下来：更改被审查';

  @override
  String get teamNowNextFinish => '接下来：任务完成';

  @override
  String get teamNowWatchPlanner => '观看规划器';

  @override
  String get teamNowDismissRequest => '停止跟进此请求';

  @override
  String teamNowUsuallyWithin(String duration) {
    return '通常在 $duration 内';
  }

  @override
  String teamNowWatchAgent(String name) {
    return '观看 $name';
  }

  @override
  String get teamNowNotStartingLine => '团队没有在启动工作者';

  @override
  String get aiSetupTitle => 'AI 设置';

  @override
  String get aiSetupEntryDetail => '此服务器的模型、工具和建议';

  @override
  String get aiSetupRefresh => '重新读取此服务器的配置';

  @override
  String get aiSetupLoading => '正在读取此服务器的配置…';

  @override
  String get aiSetupReviewOnly => '仅供查看。目前更改需在服务器上进行。';

  @override
  String get aiSetupUnsupportedTitle => 'AI 设置不可用';

  @override
  String get aiSetupUnsupportedBody => '此服务器不会向应用共享其配置。请在服务器本身上设置模型和工具。';

  @override
  String get aiSetupSignInTitle => '需要登录';

  @override
  String aiSetupSignInBody(String server) {
    return '$server 未接受已保存的登录信息，因此无法读取其配置。';
  }

  @override
  String get aiSetupErrorTitle => '无法读取配置';

  @override
  String get aiSetupErrorBody => '服务器未按预期响应。请重试，或在其设置页面检查该服务器。';

  @override
  String get aiSetupTryAgain => '重试';

  @override
  String get aiSetupOfflineTitle => '你已离线';

  @override
  String aiSetupOfflineBody(String server) {
    return '重新连接到 $server 以读取其配置。';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return '已离线。这是上次读取的 $server 配置；重新连接后会更新。';
  }

  @override
  String get aiSetupEmptyTitle => '尚未配置任何内容';

  @override
  String get aiSetupEmptyBody => '此服务器使用默认设置，未选择模型，也没有工具服务器。目前更改需在服务器上进行。';

  @override
  String get aiSetupSuggestionsLabel => '建议';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return '登录 $name';
  }

  @override
  String get aiSetupSuggestSignInDetail => '在有人于服务器上登录它之前，其工具将保持关闭。';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return '检查 $name 的设置';
  }

  @override
  String get aiSetupSuggestFixDetail => '它启动失败。请修正服务器配置中的对应条目，然后重启服务器。';

  @override
  String get aiSetupSuggestModelTitle => '选择默认模型';

  @override
  String get aiSetupSuggestModelDetail =>
      '未设置模型，因此新对话会使用服务器自己的选择。请在服务器配置中设置“model”。';

  @override
  String get aiSetupSuggestToolsTitle => '添加工具服务器';

  @override
  String get aiSetupSuggestToolsDetail =>
      '尚未配置 MCP 服务器。在服务器配置中添加一个，可为智能体提供更多工具。';

  @override
  String get aiSetupToolsLabel => '工具服务器';

  @override
  String get aiSetupToolsTerm => 'MCP 服务器为智能体提供额外的工具。每个服务器都会显示它当前是否正常工作。';

  @override
  String get aiSetupToolConnected => '已连接';

  @override
  String get aiSetupToolWaiting => '等待中';

  @override
  String get aiSetupToolOff => '已关闭';

  @override
  String get aiSetupToolFailed => '失败';

  @override
  String get aiSetupToolNeedsSignIn => '需要登录';

  @override
  String get aiSetupToolUnknown => '未知';

  @override
  String get aiSetupEffectiveLabel => '生效的设置';

  @override
  String get aiSetupEffectiveTerm => '合并配置文件后，此服务器的对话所使用的设置。';

  @override
  String get aiSetupModel => '模型';

  @override
  String get aiSetupServerDefault => '未设置：由服务器选择';

  @override
  String get aiSetupSmallModel => '小模型';

  @override
  String get aiSetupDefaultAgent => '默认智能体';

  @override
  String get aiSetupProviders => '服务商';

  @override
  String get aiSetupPermissions => '权限';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条规则',
      one: '1 条规则',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => '所有设置';

  @override
  String get aiSetupSourcesLabel => '配置来源';

  @override
  String get aiSetupSourcesTerm => '按优先级从低到高排列，与服务器报告的一致。应用不会合并它们。';

  @override
  String get aiSetupNoSources => '没有配置文件';

  @override
  String get aiSetupNoSourcesDetail => '此服务器使用默认设置。';

  @override
  String aiSetupSourceUnnamed(String type) {
    return '无文件的来源（$type）';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position。设置了 $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position。未设置任何内容';
  }

  @override
  String get aiSetupAllSources => '所有来源';

  @override
  String get integrationsPageLoadFailed => '无法加载此页面';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · 正在刷新';
  }

  @override
  String get kitLastKnownHint => '上次保存的内容。实时列表加载后即可打开。';

  @override
  String get kitTranscriptExcerptHint => '保存自上次。加载完成后会完整打开对话。';

  @override
  String get lastKnownUpdatedJustNow => '刚刚更新';

  @override
  String lastKnownUpdatedAgo(String ago) {
    return '更新于$ago';
  }

  @override
  String serverRowQueuedWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条消息等待发送',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '将 $count 条等待中的提示词移到 $destination',
      one: '将 1 条等待中的提示词移到 $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveTitle => '移动排队的提示词';

  @override
  String queuedMoveSubtitle(String source) {
    return '来自 $source';
  }

  @override
  String get queuedMovePromptsLabel => '提示词';

  @override
  String get queuedMoveConversationLabel => '对话';

  @override
  String get queuedMoveNewConversation => '新对话';

  @override
  String queuedMoveQueuedAt(String time) {
    return '排队于 $time';
  }

  @override
  String queuedMoveFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件',
      one: '1 个文件',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveBlockedUncertain(String source) {
    return '可能已经发送。请先在 $source 上确认。';
  }

  @override
  String queuedMoveBlockedFile(String source) {
    return '含有只有 $source 才能打开的文件';
  }

  @override
  String get queuedMoveBlockedMentions => '在其中隐藏密码会破坏它的智能体提及';

  @override
  String queuedMoveHidesSecrets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条提示词中的密码和密钥会保持隐藏',
      one: '1 条提示词中的密码和密钥会保持隐藏',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUsesCurrentModel(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条提示词将使用 $destination 上选定的模型',
      one: '1 条提示词将使用 $destination 上选定的模型',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveAction(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '将 $count 条提示词移动到 $destination',
      one: '将 1 条提示词移动到 $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveChooseOne => '请至少选择一条提示词';

  @override
  String queuedMoveNoneLeft(String source) {
    return '已没有等待 $source 的内容';
  }

  @override
  String queuedMoveFailedDisconnected(String destination) {
    return '$destination 已断开，未移动任何内容。请连接后重试。';
  }

  @override
  String queuedMoveFailedConversationGone(String destination) {
    return '该对话已不在 $destination 上，未移动任何内容。请选择另一个对话。';
  }

  @override
  String queuedMoveFailedNothing(String source) {
    return '这些提示词已不再等待 $source，未移动任何内容。';
  }

  @override
  String queuedMoveFailedNewConversation(String destination) {
    return '无法在 $destination 上开始新对话，未移动任何内容。请重试或选择一个已有对话。';
  }

  @override
  String queuedMoveFailedNotSaved(String source) {
    return '无法保存此次移动，未移动任何内容。提示词仍在等待 $source。';
  }

  @override
  String queuedMoveDone(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已将 $count 条提示词移动到 $destination',
      one: '已将 1 条提示词移动到 $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveDonePartial(int moved, int total, String destination) {
    return '已将 $total 条提示词中的 $moved 条移动到 $destination。其余的不再等待。';
  }

  @override
  String queuedMoveUndoNone(String destination) {
    return '这些提示词已在 $destination 上开始发送，因此会留在那里。';
  }

  @override
  String queuedMoveUndoPartial(int count, String destination, String source) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条提示词已在 $destination 上开始发送并留在那里，其余将重新等待 $source。',
      one: '1 条提示词已在 $destination 上开始发送并留在那里，其余将重新等待 $source。',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUndoFailed(String destination) {
    return '无法将提示词移回，它们会留在 $destination。';
  }

  @override
  String get sessionAddressInclude => '包含此服务器的地址';

  @override
  String get sessionAddressDisclosure =>
      '链接随后会显示此地址和对话 ID，绝不包含密码：另一台手机仍需自己的访问权限。截图、消息和剪贴板都可能保留它。';

  @override
  String get sessionAddressIntro =>
      '用另一台手机上的 OpenCode Mobile 扫描。二维码包含此服务器的地址和对话 ID。';

  @override
  String get sessionAddressUnsupportedHost =>
      '只有以 .ts.net 结尾的私有 HTTPS 地址才能放入链接。';

  @override
  String get sessionAddressOpenTitle => '打开分享的对话';

  @override
  String get sessionAddressConsentSaved => '在此已保存的服务器上打开？';

  @override
  String get sessionAddressConsentNew => '添加此服务器？';

  @override
  String get sessionAddressNotSaved => '未保存在本机';

  @override
  String get sessionAddressConsentNote =>
      '该链接不授予任何访问权限。检查只会询问服务器是哪一个安装实例；不会登录，也不会发送密码。';

  @override
  String get sessionAddressCheck => '检查服务器';

  @override
  String sessionAddressChecking(String host) {
    return '正在检查 $host…';
  }

  @override
  String get sessionAddressAddBody => '此服务器未保存在本机。请用你自己的登录信息添加它；该链接不携带登录信息。';

  @override
  String get sessionAddressAddServer => '添加服务器';

  @override
  String get sessionAddressChooseBody => '有多个已保存的服务器使用此地址。请选择要在其上打开对话的那一个。';

  @override
  String sessionAddressVerifyBody(String name) {
    return '确认 $name 就是此链接来源的服务器。手机会为 $name 记住这一点；不会登录，也不会共享密码。';
  }

  @override
  String get sessionAddressVerify => '验证服务器';

  @override
  String sessionAddressReadyBody(String name) {
    return '$name 与此链接匹配。';
  }

  @override
  String sessionAddressSignInBody(String name) {
    return '请先用你自己的账户登录 $name，然后打开对话。';
  }

  @override
  String get sessionAddressSignIn => '登录';

  @override
  String get sessionAddressOpen => '打开对话';

  @override
  String get sessionAddressOpening => '正在打开对话…';

  @override
  String get sessionAddressReason => '原因';

  @override
  String get sessionAddressFailUnavailable => '带服务器地址的对话链接暂不可用。';

  @override
  String get sessionAddressFailInvalidLink => '此对话链接无效。请重新扫描或复制。';

  @override
  String get sessionAddressFailTooLarge => '此链接过长。请向发送者索取新链接。';

  @override
  String get sessionAddressFailCredentials => '此链接包含私密登录信息，无法使用。';

  @override
  String get sessionAddressFailConsentRequired => '请先选择是否包含此服务器的地址。';

  @override
  String get sessionAddressFailPrivateRouteRequired =>
      '无法通过所需的私有连接访问此服务器。请检查你的连接。';

  @override
  String get sessionAddressFailUnreachable => '无法连接服务器。请检查连接后重试。';

  @override
  String get sessionAddressFailTimedOut => '服务器未及时应答。请重试。';

  @override
  String get sessionAddressFailTlsRejected => '无法验证该服务器的安全连接，因此未打开链接。';

  @override
  String get sessionAddressFailRedirectsRejected => '此服务器试图将请求转发到别处。链接未打开。';

  @override
  String get sessionAddressFailAccessDenied => '你对服务器或对话的访问被拒绝。';

  @override
  String get sessionAddressFailInvalidDescriptor => '此服务器未提供打开此链接所需的信息。';

  @override
  String get sessionAddressFailInstanceMismatch => '此链接与已保存的服务器不是同一个安装实例。';

  @override
  String get sessionAddressFailBindingRequired => '打开对话前，请先验证此已保存的服务器。';

  @override
  String get sessionAddressFailAmbiguousProfile => '请选择要使用的已保存服务器。';

  @override
  String get sessionAddressFailProfileMissing => '此已保存的服务器已不可用。';

  @override
  String get sessionAddressFailStorage => '无法保存或读取服务器验证结果。请重启应用后重试。';

  @override
  String get sessionAddressFailSignInRequired => '请先用你自己的账户登录此服务器，然后继续。';

  @override
  String get sessionAddressFailUnsafeLookup => '此服务器尚未通过私有对话链接的验证。';

  @override
  String get sessionAddressFailSessionMissing => '此服务器上没有该对话。';

  @override
  String get sessionAddressFailCancelled => '打开此链接的操作已取消。';

  @override
  String get removeFromPhoneDeleteAllChoice => '全部删除…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return '全部删除，释放约 $size…';
  }

  @override
  String get phoneServerCardErrorDetail => '错误';

  @override
  String get sessionMenuGoTo => '前往';

  @override
  String get sessionMenuDo => '执行';

  @override
  String get sessionMenuFind => '查找';

  @override
  String get sessionMenuSubagents => '子智能体';

  @override
  String get sessionMenuDetails => '详情';

  @override
  String get sessionMenuShareHint => '任何持有链接的人都能读取';

  @override
  String get sessionMenuStopSharingHint => '公开链接将失效';

  @override
  String get sessionMenuCompactHint => '对其进行总结，让智能体重新获得空间';

  @override
  String get sessionMenuForkHint => '打开一个副本，你可以另作处理';

  @override
  String get sessionMenuContinueComputerHint => '显示在那里继续的命令';

  @override
  String get sessionMenuContinuePhoneHint => '显示一个由那台手机上的应用打开的二维码';

  @override
  String get sessionMenuNeedsPrompt => '首条提示词之后可用';

  @override
  String commandSheetServerGroup(String server) {
    return '来自 $server 的命令';
  }

  @override
  String commandSheetAgentMissingTitle(String agent) {
    return '$agent 的命令不可用';
  }

  @override
  String commandSheetAgentMissingWhy(String agent) {
    return '$agent 尚未与应用共享它自己的命令，因此应用无法列出或运行它们，也无法运行 ! 开头的 shell 命令。应用自身的操作仍然可用。';
  }

  @override
  String commandSheetAgentCommandNotSent(String command, String agent) {
    return '未发送 $command：$agent 尚未向应用提供其命令。删除 / 即可作为消息发送。';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return '未发送 $command：无法从应用在 $agent 上运行 shell 命令。删除 ! 即可作为消息发送。';
  }

  @override
  String get commandSheetShellDescription => '或在消息开头用 ! 从编辑框运行';

  @override
  String get commandSheetRetryDescription => '再次发送你的上一条提示词';

  @override
  String get commandSheetNoteDescription => '智能体在此对话中会记住的备注';

  @override
  String get commandSheetApprovalsDescription => '此对话可以不经询问执行哪些操作';

  @override
  String get commandSheetReloadDescription => '从服务器重新读取此对话';

  @override
  String get commandSheetLibrarySubtitle => '先选择命令，再选择它运行所在的对话';

  @override
  String get commandSheetAgentFallback => '此智能体';

  @override
  String get commandSheetPlanDescription => '在对话中打开智能体最新的计划';

  @override
  String get chatUiSessionMenu => '对话菜单';

  @override
  String get commandsScreenLoadFailed => '无法加载命令';

  @override
  String get commandSheetSubtitleAppOnly => '在此对话中运行应用的某个操作';

  @override
  String get voiceModeMicAsk => '语音输入需要麦克风。点击“允许麦克风”，然后选择“允许”。';

  @override
  String get voiceModeMicAllow => '允许麦克风';

  @override
  String get voiceModeMicBlocked =>
      'Android 已禁止此应用使用麦克风。请在 Android 设置中开启，然后回到这里。';

  @override
  String get voiceModeNothingHeard => '没有听到声音。请点击麦克风再试一次。';

  @override
  String get teamDispatchCreating => '正在创建你的任务…';

  @override
  String get teamDispatchSending => '任务已创建 · 正在发送给团队…';

  @override
  String get teamDispatchAwaitingWorker => '任务已发送给团队 · 等待工作者';

  @override
  String get teamDispatchWorkerStarted => '工作者已开始处理你的任务';

  @override
  String get teamDispatchCreateRefused => '任务未能创建。请修改后重新发送。';

  @override
  String get teamDispatchAssignRefused => '任务已创建，但无法发送给团队';

  @override
  String get teamDispatchAssignRefusedHint => '任务留在看板上，未指派给任何人。';

  @override
  String get teamDispatchCreateUnconfirmed => '无法确认任务是否已创建';

  @override
  String get teamDispatchDispatchUnconfirmed => '任务已创建 · 无法确认是否送达团队';

  @override
  String get teamDispatchCheckBoard => '重新发送前请先查看看板。你的文字已保留。';

  @override
  String get teamDispatchUnknown => '任务已发送 · 无法连接团队，因此不确定是否有工作者启动';

  @override
  String get teamDispatchCheckAgain => '再次检查团队';

  @override
  String get teamDispatchTaskId => '任务 ID';

  @override
  String get teamDispatchHostWords => '团队的回复';

  @override
  String get teamUiHostGuideOpen => '打开完整指南';

  @override
  String get mcpAddBrowseTitle => '浏览目录';

  @override
  String get mcpAddBrowseDetail => '来自公共 MCP 注册表的服务器，用开关启用';

  @override
  String get mcpAddBrowseNone => '此服务器没有目录：它不接受从应用添加新的 MCP 服务器。';

  @override
  String get mcpAddManualTitle => '手动输入';

  @override
  String get mcpAddManualDetail => '输入它的地址，或启动它的命令';

  @override
  String get mcpCatalogTitle => 'MCP 目录';

  @override
  String get mcpCatalogConsentTitle => '加载 MCP 注册表？';

  @override
  String get mcpCatalogConsentBody =>
      '应用会向 registry.modelcontextprotocol.io 请求其 MCP 服务器列表。它只发送你搜索的内容，不会发送任何关于你或你的服务器的信息。';

  @override
  String get mcpCatalogConsentLoad => '加载列表';

  @override
  String get mcpCatalogForget => '停止使用注册表';

  @override
  String get mcpCatalogForgetFailed => '无法忘记已保存的注册表列表。请重试。';

  @override
  String get mcpCatalogSearch => '搜索注册表';

  @override
  String get mcpCatalogInventoryFailed => '无法读取此服务器的 MCP 服务器';

  @override
  String get mcpCatalogInventoryFailedBody => '开关需要知道哪些已经开启。请检查连接后重试。';

  @override
  String get mcpCatalogFailed => '无法加载公共 MCP 注册表';

  @override
  String get mcpCatalogSearchFailed => '无法搜索公共 MCP 注册表';

  @override
  String get mcpCatalogFailedBody => '请检查手机的网络连接后重试。你仍然可以手动输入服务器。';

  @override
  String get mcpCatalogEmpty => '注册表中没有列出任何服务器';

  @override
  String mcpCatalogNoMatch(String query) {
    return '注册表中没有与“$query”匹配的内容';
  }

  @override
  String get mcpCatalogEmptyBody => '请换用其他关键词，或手动输入服务器。';

  @override
  String get mcpCatalogStale => '无法从注册表刷新列表。以下是先前加载的条目。';

  @override
  String get mcpCatalogPriceNote => '注册表不列出价格。托管服务器的所有者可能会收费，或要求注册账号。';

  @override
  String get mcpCatalogAdding => '正在添加…';

  @override
  String get mcpCatalogRemoving => '正在移除…';

  @override
  String get mcpCatalogCannotRemove => '已开启。此服务器将其保留在配置中，应用无法移除。';

  @override
  String get mcpCatalogNeedsDocker => '需要在 Docker 中运行。若仍要添加，请使用“手动输入”。';

  @override
  String get mcpCatalogNoEndpoint => '未列出应用可启动的内容。若仍要添加，请使用“手动输入”。';

  @override
  String mcpCatalogHostedBy(String host) {
    return '托管方：$host';
  }

  @override
  String get mcpCatalogNeedsNode => '需要服务器上有 Node';

  @override
  String get mcpCatalogNeedsNodePhone => '需要此手机上有 Node';

  @override
  String get mcpCatalogNeedsPython => '需要服务器上有带 uv 的 Python';

  @override
  String get mcpCatalogNeedsKey => '需要 API 密钥';

  @override
  String get mcpCatalogNeedsSettings => '需要额外设置';

  @override
  String mcpCatalogNodeTitle(String title) {
    return '$title 需要 Node 才能运行';
  }

  @override
  String get mcpCatalogNodeAdd => '为此手机添加 Node';

  @override
  String get mcpCatalogNodeAddDetail => '打开“此手机”。选择“添加工具”，再选 Node，添加完成后再次开启此项。';

  @override
  String get mcpCatalogNodeHave => '此手机已安装 Node';

  @override
  String get mcpCatalogNodeHaveDetail => '查看详情并添加';

  @override
  String mcpSetupFromCatalog(String listing, String server) {
    return '已根据公共 MCP 注册表中的“$listing”填入。添加前请核对：$server 将运行或连接到此处的内容。';
  }

  @override
  String get mcpSetupThisServer => '此服务器';

  @override
  String mcpSetupValueRequired(String name) {
    return '请输入 $name 的值';
  }

  @override
  String get mcpSetupNameFromCatalog => '注册表条目需要这一项';

  @override
  String get mcpVariableName => '变量名';

  @override
  String get mcpVariableValue => '变量值';

  @override
  String get mcpAddVariable => '再添加一个变量';

  @override
  String get mcpRemoveVariable => '移除变量';

  @override
  String get mcpSetupTimeoutSeconds => '超时（秒）';

  @override
  String get isolatedTaskPromptLabel => '要让它做什么？';

  @override
  String get isolatedTaskPromptHelper => '副本就绪后发送。留空即可在对话中再写。';

  @override
  String get isolatedTaskOptions => '选项';

  @override
  String get isolatedTaskPreparingHint => '如果你停止等待，副本会保留。你可以在“项目 › 工作树”下找到它。';

  @override
  String get isolatedTaskFailedBody => '副本已创建，但设置未完成。你仍可在其中开始，或移除它。';

  @override
  String isolatedTaskSending(String name) {
    return '正在将任务发送给 $name…';
  }

  @override
  String get isolatedTaskSendFailed => '无法发送你的任务';

  @override
  String get isolatedTaskSendFailedBody => '它已留在对话的消息框中，随时可以发送。';

  @override
  String get isolatedTaskSendFailedLost => '请复制下面的任务，并在对话中发送。';

  @override
  String get isolatedTaskOpenConversation => '打开对话';

  @override
  String get isolatedTaskStartAnyway => '仍然开始';

  @override
  String get isolatedTaskRemove => '移除副本';

  @override
  String isolatedTaskRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String get isolatedTaskRemoveBody => '它的文件夹和分支会被删除。你的项目本身不会受到影响。';

  @override
  String isolatedTaskRemoved(String name) {
    return '已移除 $name。你可以重新开始。';
  }

  @override
  String get isolatedTaskSetupOutput => '设置报告的内容';

  @override
  String get isolatedTaskCopyFolder => '副本所在文件夹';

  @override
  String get isolatedTaskBranchLabel => '分支';

  @override
  String get isolatedTaskStageSend => '正在打开对话并发送你的任务';

  @override
  String get teamStartRunBlockedTitle => '团队无法接任务';

  @override
  String get teamStartRunPlannerOff => '规划器已关闭';

  @override
  String get teamStartRunPlannerOffWakeBody => '规划器会把每个任务拆成团队的步骤。唤醒它即可把任务交给团队。';

  @override
  String get teamStartRunPlannerOffHostBody =>
      '规划器会把每个任务拆成团队的步骤，而本应用无法开启它。请在团队运行的位置开启，然后重试。';

  @override
  String get teamStartRunNoPlanner => '此团队没有规划器';

  @override
  String get teamStartRunNoPlannerBody =>
      '规划器会把每个任务拆成团队的步骤。请在团队运行的位置添加一个，然后重试。';

  @override
  String get teamStartRunNoProject => '此团队尚无项目';

  @override
  String get teamStartRunNoProjectBody => '任务会直接交给项目的工作者。请为团队添加一个项目，然后重试。';

  @override
  String get teamStartRunWake => '唤醒规划器';

  @override
  String get teamStartRunWakeAsked => '正在唤醒规划器。它一醒来，任务表单就会打开。';

  @override
  String get teamStartRunStillOff => '规划器仍处于关闭状态。';

  @override
  String get teamStartRunStillNoProject => '团队仍然没有项目。';

  @override
  String get teamStartRunWakeRefused => '无法唤醒规划器';

  @override
  String get teamStartRunWakeRefusedNext => '请重试，或在团队运行的位置开启它。';

  @override
  String get addServerTailscaleNext => '输入地址';

  @override
  String get phoneSetupTermuxGetCurrent => '获取最新版 Termux';

  @override
  String get phoneSetupUnsupportedTitle => '连接服务器';

  @override
  String get phoneSetupUnsupportedBody =>
      '在设备本机上设置仅适用于 Android 手机。请在你的电脑上运行此命令，然后用它输出的代码在这里添加服务器。';

  @override
  String get termuxStorageStageTotal => '整个 Termux 安装';

  @override
  String get setupProgressViewFailedStep => '此步骤未完成。出错原因见“详情”。';

  @override
  String setupProgressViewFailedAt(String name) {
    return '在 $name 处停止。出错原因见“详情”。';
  }

  @override
  String workRunawayHelper(String helper, String duration) {
    return '残留的 $helper 进程已空转忙碌 $duration';
  }

  @override
  String workRunawayHelperInProject(
    String helper,
    String project,
    String duration,
  ) {
    return '$project 中残留的 $helper 进程已空转忙碌 $duration';
  }

  @override
  String get workRunawaySeeRunning => '查看正在运行的内容';

  @override
  String thisPhoneUpToDate(String version) {
    return '已是最新 · $version';
  }

  @override
  String thisPhoneUpdateTitle(String runtime) {
    return '更新 $runtime？';
  }

  @override
  String thisPhoneUpdateBody(String version) {
    return '安装版本 $version，重启此手机上的服务器并重新连接。';
  }

  @override
  String get thisPhoneUpdateKept => '你的对话会保留。服务器重启期间会离线约一分钟。';

  @override
  String get thisPhoneUpdateBusy => '仍在写入回复。请先停止或等它完成，然后再更新。';

  @override
  String get thisPhoneStartAgain => '再次启动';

  @override
  String thisPhoneInstallFailed(String runtime) {
    return '安装 $runtime 未完成。请重新安装，你的对话会保留。';
  }

  @override
  String get thisPhoneInstallAgain => '重新安装';

  @override
  String thisPhoneStopFailed(String runtime) {
    return '$runtime 未能停止。请再次尝试停止。';
  }

  @override
  String thisPhoneCheckFailed(String runtime) {
    return '此手机无法检查 $runtime。请稍后重试。';
  }

  @override
  String thisPhoneSwitchStopped(String runtime) {
    return '切换后 $runtime 未能启动。你的对话会保留。';
  }

  @override
  String get thisPhoneHistoryMoving => '正在迁移你早先的对话…';

  @override
  String thisPhoneHistoryMovingCount(int done, int total) {
    return '正在迁移你早先的对话（$done/$total）…';
  }

  @override
  String get thisPhoneHistoryFailed => '无法迁移你之前的对话。原始数据仍完好地保留在此手机上。';

  @override
  String get addServerCheckFailedPlain => '无法检查该服务器。请检查地址和此手机的网络连接后重试。';

  @override
  String serverRowDetailsTitle(String name) {
    return '$name 详情';
  }

  @override
  String get teamUiHostGuideEnterAddress => '输入地址';

  @override
  String get commandAuthStartFailed => '登录未能启动。';

  @override
  String get commandAuthCheckFailed => '无法检查登录状态。请重试。';

  @override
  String get commandAuthTryAgain => '重试';

  @override
  String get draftLeaveMessageNoText => '请再次尝试保存。如果不保存就离开，你对此草稿的最近修改可能会丢失。';

  @override
  String get draftLeaveCopyAction => '复制草稿并离开';

  @override
  String get draftLeaveRetry => '再次尝试保存';

  @override
  String get draftLeaveStillFailing => '仍未保存。请先复制你的文字再离开。';

  @override
  String get queuedRetry => '重试';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '全部 $count 条重试',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return '使用 $model 并重新发送';
  }

  @override
  String get chatUiChooseAnotherModel => '选择其他模型';

  @override
  String get chatUiSendPromptAgain => '重新发送';

  @override
  String get chatUiPromptNotAnswered => '未答复';

  @override
  String get chatWatchEndedTitle => '此对话已结束';

  @override
  String get chatWatchEndedBody => '它在工作者写下任何内容之前就结束了。';

  @override
  String get chatWatchBackToTask => '返回任务';

  @override
  String get chatWatchBackToWorker => '返回工作者';

  @override
  String get migrationTitle => '从 Termux 迁移';

  @override
  String get migrationChecking => '正在检查 Termux 和手机存储…';

  @override
  String get migrationReviewIntro =>
      '你的项目会被复制到本应用内的 OpenCode。Termux 中的任何内容都不会被更改或删除。';

  @override
  String get migrationGroupMoves => '已复制，可直接使用';

  @override
  String get migrationGroupExports => '已私密保存，未启用';

  @override
  String get migrationGroupNotMoved => '未迁移';

  @override
  String get migrationItemProjects => '项目';

  @override
  String get migrationItemConfig => 'MCP 与智能体设置';

  @override
  String get migrationItemSessions => '对话历史（备份副本）';

  @override
  String get migrationItemGitConfig => 'Git 设置';

  @override
  String get migrationItemShellFiles => 'Shell 设置';

  @override
  String get migrationItemAiTeam => 'AI Team';

  @override
  String get migrationItemProjectsWhat =>
      'Into a new folder in On this phone. Nothing there is overwritten.';

  @override
  String get migrationItemConfigWhat => '使用前请先检查：其中的命令和路径可能只适用于 Termux。';

  @override
  String get migrationItemSessionsWhat =>
      '可能包含你的登录信息，应用不会打开它。Termux 仍保留可用的历史记录。';

  @override
  String get migrationItemGitConfigWhat => '你的 Git 名称、邮箱和选项，供你检查。';

  @override
  String get migrationItemShellFilesWhat =>
      '保留 .bashrc、.zshrc 等 shell 启动文件；它们永远不会被执行。';

  @override
  String get migrationItemAiTeamWhat => '团队保存的状态。设置过程会重新安装其工具。';

  @override
  String migrationItemSize(String size, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个文件',
      one: '1 个文件',
    );
    return '$size · $_temp0';
  }

  @override
  String get migrationSizeUnknown => '大小未知';

  @override
  String get migrationExportsNote =>
      'Private copies stay in On this phone. Nothing in them runs or turns on by itself.';

  @override
  String get migrationNotMovedSignIn => 'AI 提供商的登录：迁移后需重新登录';

  @override
  String migrationNotMovedSignInNamed(String names) {
    return '$names 的登录：迁移后需重新登录';
  }

  @override
  String get migrationNotMovedKeys => 'SSH 密钥和已保存的 Git 密码';

  @override
  String get migrationNotMovedTools => '已安装的工具和缓存：设置过程会重新安装';

  @override
  String get migrationTermuxKept => 'Termux 保持原样，其服务器会继续运行，直到你将其移除';

  @override
  String get migrationKeepOpen =>
      '复制期间请保持应用打开。离开应用时 Android 会停止它，回来后它会从当前进度继续。';

  @override
  String get migrationStart => 'Copy to On this phone';

  @override
  String get migrationChooseOne => '请至少选择一项要复制的内容。';

  @override
  String get migrationPacking => '正在 Termux 中准备你的文件…';

  @override
  String get migrationCopying => '正在将文件复制到本应用…';

  @override
  String get migrationUnpacking => '正在导入你的文件…';

  @override
  String get migrationVerifying => '正在检查已复制的文件…';

  @override
  String get migrationSwitching => 'Connecting to On this phone…';

  @override
  String get migrationStepConnect => 'Connect to On this phone';

  @override
  String get migrationStop => '停止复制';

  @override
  String get migrationStopTitle => '停止复制？';

  @override
  String get migrationStopBody => '你可以稍后从“此手机”继续。';

  @override
  String get migrationStopKept => '已复制的部分会保留';

  @override
  String get migrationKeepGoing => '继续复制';

  @override
  String get migrationCancelled => '复制已停止。你可以稍后继续。';

  @override
  String get migrationCancelledBody => '已复制的内容会保留，Termux 不会被更改。';

  @override
  String get migrationStoppedLeaving =>
      '已停止，因为应用离开了屏幕：Android 不允许它在后台运行。已复制的内容会保留。';

  @override
  String get migrationResume => '继续复制';

  @override
  String get migrationNeedsSpace => '复制前需要更多可用空间。';

  @override
  String migrationNeedsSpaceBody(String needed, String free) {
    return '需要约 $needed，当前可用 $free。请释放此手机的空间，或减少复制项。';
  }

  @override
  String migrationNeedsSpaceBodyUnknown(String needed) {
    return '需要约 $needed，但无法读取可用空间。请释放此手机的空间，或减少复制项。';
  }

  @override
  String get migrationChooseFewer => '选择更少的项';

  @override
  String get migrationNeedsBuiltin => 'Set up On this phone first.';

  @override
  String migrationNeedsBuiltinBody(String runtime) {
    return '你的项目会迁移到本应用内的 OpenCode，因此需要先完成设置。设置过程会安装 Linux 和 $runtime，之后会在这里继续。';
  }

  @override
  String get migrationSetUpBuiltin => 'Set up On this phone';

  @override
  String get migrationSetupFailed => '设置无法启动。请重试，或从“此手机”中进行设置。';

  @override
  String get migrationTermuxNotAnswering => 'Termux 无响应';

  @override
  String get migrationTermuxUnavailable => '请打开 Termux，然后重试。';

  @override
  String get migrationOpenTermux => '打开 Termux';

  @override
  String get migrationFailedTitle => '迁移已停止';

  @override
  String get migrationSourceChanged => '复制期间文件发生了变化。请在 Termux 空闲时重试。';

  @override
  String get migrationSourceBusy => 'Termux 正在完成上一步。请稍后重试。';

  @override
  String get migrationUnsupportedFiles => '此项包含无法安全复制的文件。';

  @override
  String get migrationUnsupportedFilesFix =>
      '链接、套接字和 Git 工作树无法复制。请在 Termux 中移除它们后重试，或手动复制该项目。';

  @override
  String get migrationLinkedFileUnreadable =>
      '此项目的部分 Git 历史在 Termux 中无法读取，因此无法安全迁移。';

  @override
  String get migrationLinkedFileUnreadableFix =>
      '打开 Termux，在该项目中运行 git fsck 查看缺失了什么，然后重试。你手机上的内容未被更改。';

  @override
  String get migrationTooLarge => '此项超出迁移大小或文件数量限制。';

  @override
  String get migrationTooLargeFix =>
      '每项最多 512 MB 和 20,000 个文件。请在 Termux 中删除 node_modules 等构建目录，然后重试。';

  @override
  String get migrationVerificationFailed => '无法验证副本。你的 Termux 文件未被更改。';

  @override
  String get migrationDestinationChanged => '已导入的文件发生了变化。它们不会被覆盖。';

  @override
  String get migrationDestinationChangedFix =>
      'The files in On this phone stay as you left them.';

  @override
  String get migrationStorageFailed => '无法保存副本。请检查手机存储后重试。';

  @override
  String get migrationTimedOut => '此步骤耗时过长。请保持应用打开并继续。';

  @override
  String get migrationSelectionChanged => '请使用已保存的迁移选择继续。';

  @override
  String get migrationConnectionFailed =>
      'Files are copied, but On this phone could not connect.';

  @override
  String get migrationFailureCode => '原因';

  @override
  String get migrationFailureItem => '项目';

  @override
  String get migrationOpenThisPhone => '打开“此手机”';

  @override
  String get migrationDoneTitle => '已从 Termux 迁移';

  @override
  String get migrationDone => '文件已复制。你的 Termux 服务器仍然可用。';

  @override
  String get migrationSignInAgain => '重新登录你的 AI 提供商';

  @override
  String migrationSignInAgainNamed(String names) {
    return '$names 出现在你的 Termux 设置中。在此登录即可使用它们。';
  }

  @override
  String get migrationSignInAgainAny =>
      '登录信息永远不会从 Termux 迁移过来。在你于此登录之前，回复将使用 OpenCode 的免费模型，速度较慢。';

  @override
  String get migrationProjectsWhere => '你的项目';

  @override
  String migrationProjectsWhereBody(String folder) {
    return 'In the folder $folder in On this phone';
  }

  @override
  String get migrationExportsWhere => '私有副本';

  @override
  String migrationExportsWhereBody(String items) {
    return '$items: saved in On this phone, not turned on';
  }

  @override
  String get migrationRemoveTermux => '准备好后移除 Termux 服务器';

  @override
  String get migrationRemoveTermuxBody =>
      '我们不会替你移除任何内容。在此之前它会继续工作，你可以在“服务器”中切回它。';

  @override
  String get migrationOpenBuiltin => 'Open On this phone';

  @override
  String get migrationDetailProjects => '项目文件夹';

  @override
  String get migrationDetailExports => '私有副本文件夹';

  @override
  String get migrationUnfinishedTitle => '迁移未完成';

  @override
  String get migrationUnfinishedBody => '继续即可从停止处接着进行。已复制的内容会保留，Termux 不会被更改。';

  @override
  String get migrationUnavailableTitle => '无法开始迁移';

  @override
  String get migrationUnavailableBody => '应用无法为复制准备私有存储。请重试；若反复出现，请重启应用。';

  @override
  String get migrationRowBody =>
      'Copy your projects to the server on this phone. Termux stays as it is.';

  @override
  String get migrationRowResume => 'Resume moving to On this phone';

  @override
  String get migrationRowResumeBody => '完成前已停止。已复制的内容会保留。';

  @override
  String get migrationRowRunning => 'Moving to On this phone';

  @override
  String get migrationRowDoneBody => '准备好后移除 Termux 服务器。';

  @override
  String get migrationOffer => '要把你的 Termux 项目迁移到本应用吗？Termux 保持原样。';

  @override
  String get migrationOfferAction => '查看迁移内容';

  @override
  String get integrationsSignInUncertainNext => '重新开始前请先检查服务器';

  @override
  String teamHomeLastKnownTasks(String time) {
    return '$time 时的任务';
  }

  @override
  String teamHomeLastKnownAgents(String time) {
    return '$time 时的智能体';
  }

  @override
  String get teamHomeStoppedStartFirst => '请先重新启动团队，才能给它任务或打开任务。';

  @override
  String get inAppServerStartExitedBody =>
      'OpenCode 在启动时自行退出了。打开设置查看其日志，或重新启动。';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'OpenCode 在 $seconds 秒内没有响应。手机可能繁忙或内存不足；请关闭其他应用，然后重新启动。';
  }

  @override
  String get inAppServerStartInterruptedBody => '启动因应用离开屏幕而中断。请重新启动以继续。';

  @override
  String get inAppServerStartPasswordBody =>
      '应用无法在此手机上设置 OpenCode 的登录。请重新启动；如果反复出现，请打开设置。';

  @override
  String get inAppServerStartRefusedBody =>
      '手机刚才不允许应用启动 OpenCode。请重新启动；如果反复出现，请重启手机。';

  @override
  String get integrationsConnectWithKey => '添加 API 密钥';

  @override
  String get integrationsConnectOnServer => '在服务器上设置';

  @override
  String integrationsProviderDetails(String name) {
    return '$name 详情';
  }

  @override
  String get integrationsEnvironmentVariable => '服务器环境变量';

  @override
  String integrationsEnvironmentNote(String name) {
    return '要在不使用应用的情况下连接 $name，请在服务器运行的位置设置此项，然后重启服务器。';
  }

  @override
  String get termuxProblemAccessHeard =>
      'OpenCode 正在 Termux 中运行，但此应用暂时无法访问 Termux。允许访问后即可连接。';

  @override
  String get termuxProblemAccessNeeded =>
      '此应用暂时无法访问 Termux。请允许访问，这样它才能找到 OpenCode 并连接。';

  @override
  String get termuxProblemAccessBlocked =>
      'Android 阻止了此应用访问 Termux。请在此应用的权限中开启“Run commands in Termux environment”。';

  @override
  String get termuxProblemOtherAppsOff =>
      'Termux 尚不接受来自其他应用的命令。在 Termux 中运行一行命令即可允许。';

  @override
  String get termuxProblemAsleep =>
      'Termux 没有响应。可能是 Android 将其休眠了。请打开 Termux 唤醒它。';

  @override
  String termuxProblemNotAnswering(String runtime) {
    return '$runtime 已在 Termux 中设置，但没有响应。重启通常能恢复。';
  }

  @override
  String get termuxProblemNotInstalled => '此手机上没有 Termux。请重新安装，或改为设置应用内服务器。';

  @override
  String get termuxProblemOutdated =>
      '此 Termux 版本过旧，应用无法使用。请从 F-Droid 安装最新版 Termux。';

  @override
  String termuxProblemUnknown(String runtime) {
    return '此手机无法检查 Termux 中的 $runtime。请稍后再试。';
  }

  @override
  String get termuxFixAllowAccess => '允许访问 Termux';

  @override
  String get termuxFixOpenPermissions => '打开此应用的权限';

  @override
  String get termuxFixAllowOtherApps => '在 Termux 中允许其他应用';

  @override
  String get termuxFixOpenTermux => '打开 Termux';

  @override
  String termuxFixRestart(String runtime) {
    return '在 Termux 中重启 $runtime';
  }

  @override
  String get termuxFixGetTermux => '获取 Termux';

  @override
  String get termuxFixGetCurrentTermux => '获取最新版 Termux';

  @override
  String get termuxOtherAppsTitle => '允许其他应用';

  @override
  String get termuxOtherAppsBody =>
      '在 Termux 中粘贴这一行并按回车，然后回到这里。“打开 Termux”会为你复制它。';

  @override
  String get termuxLeadRunning => 'OpenCode 正在 Termux 中运行';

  @override
  String get termuxLeadAccessLine => '此应用暂时无法访问 Termux。允许访问后即可连接到你的对话。';

  @override
  String get termuxLeadSetUp => 'OpenCode 已在 Termux 中设置';

  @override
  String get termuxLeadTermuxOnly => '本机已安装 Termux';

  @override
  String get termuxLeadRunningBody => '连接以读取你的对话。';

  @override
  String get termuxLeadStoppedBody => '它已停止。启动后即可读取你的对话。';

  @override
  String get termuxLeadConnect => '连接到 Termux 中的服务器';

  @override
  String get termuxLeadStart => '启动 Termux 中的服务器';

  @override
  String get termuxInAppInstead => '改为设置应用内服务器';

  @override
  String get termuxInAppInsteadDetail => '全新开始，在本应用内运行，无需 Termux。';

  @override
  String get termuxInAppInsteadBlocked =>
      '在此应用内重新开始。如需从 Termux 迁移项目，请先修复 Termux 访问权限。';

  @override
  String get aboutBundledComponents => '内置组件';

  @override
  String get aboutBundledComponentsDetail => '此应用内置的图标、字体及其他组成部分';

  @override
  String get manageSpaceTitle => '清除此应用的存储';

  @override
  String get manageSpaceMeasuring => '正在统计已存储的内容…';

  @override
  String get manageSpaceIntro =>
      '清除会删除 OpenCode Mobile 在此手机上保存的所有内容，且无法撤销。如需保留项目，请先导出。';

  @override
  String get manageSpaceExportFirst => '先导出项目';

  @override
  String get manageSpaceClearCache => '仅清除应用缓存';

  @override
  String manageSpaceClearCacheDetail(String size) {
    return '释放 $size。项目、服务器和设置会保留。';
  }

  @override
  String get manageSpaceClearCacheKeeps => '项目、服务器和设置会保留。';

  @override
  String manageSpaceCacheCleared(String size) {
    return '缓存已清除，释放了 $size。';
  }

  @override
  String get manageSpaceCacheFailed => '无法清除缓存。请重试。';

  @override
  String get manageSpaceTryAgain => '重试';

  @override
  String get manageSpaceDeleteAll => '删除全部内容';

  @override
  String get manageSpaceDeleteAllDetail => '删除下方列出的全部内容并关闭应用';

  @override
  String get manageSpaceDeleteTitle => '删除全部内容？';

  @override
  String get manageSpaceDeleteBody => 'OpenCode Mobile 之后会像全新安装一样重新启动。此操作无法撤销。';

  @override
  String get manageSpaceLostServer => 'On this phone and its conversations';

  @override
  String manageSpaceLostProjects(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个项目（$size）',
      one: '1 个项目（$size）',
    );
    return '$_temp0';
  }

  @override
  String manageSpaceLostSettings(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 台已保存服务器及所有设置',
      one: '1 台已保存服务器及所有设置',
      zero: '所有设置',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptAll => 'Termux、你的电脑，以及已推送到 git 的内容都会保留';

  @override
  String get manageSpaceWaitForExport => '请等待导出完成';

  @override
  String get manageSpaceDeletedLabel => '清除会删除';

  @override
  String get manageSpaceServer => 'On this phone';

  @override
  String get manageSpaceServerDetail => 'Ubuntu、OpenCode、其登录信息和对话';

  @override
  String get manageSpaceSettings => '已保存的服务器与设置';

  @override
  String manageSpaceSavedServers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 台已保存服务器',
      one: '1 台已保存服务器',
      zero: '没有已保存的服务器',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptLabel => '保留';

  @override
  String get manageSpaceKeptTermux => 'Termux 及其中的项目';

  @override
  String get manageSpaceKeptComputers => '你的电脑及其服务器';

  @override
  String get manageSpaceKeptGit => '你推送到 git 的任何内容';

  @override
  String projectExportDetail(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个项目，$size，导出为你选择的单个 zip 文件',
      one: '1 个项目，$size，导出为你选择的单个 zip 文件',
    );
    return '$_temp0';
  }

  @override
  String get projectExportNoProjects => 'No projects in On this phone yet';

  @override
  String get projectExportSave => '保存为 zip 文件';

  @override
  String get projectExportRunning => '正在导出项目';

  @override
  String get projectExportPreparing => '正在列出文件…';

  @override
  String projectExportProgress(String done, String total) {
    return '$done / $total';
  }

  @override
  String get projectExportStop => '停止导出';

  @override
  String get projectExportStopDetail => '写入一半的文件会被删除';

  @override
  String get projectExportPrivate => '包含登录信息与对话';

  @override
  String get projectExportPrivateDetail => '私密：任何拿到该文件的人都能使用你的账号';

  @override
  String projectExportDone(String size, int files) {
    return '项目已导出：$size，共 $files 个文件。';
  }

  @override
  String get projectExportDonePrivate => '此文件包含登录信息，请妥善保管。';

  @override
  String projectExportDoneLeftOut(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已排除 $count 个包含登录信息或密钥的文件。',
      one: '已排除 1 个包含登录信息或密钥的文件。',
    );
    return '$_temp0';
  }

  @override
  String get projectExportStopped => '导出已停止。未保存任何内容。';

  @override
  String get projectExportFailedDestination => '无法写入你所选的位置。请重试，或另选一个位置。';

  @override
  String get projectExportFailedSpace => '你所选的位置已满。请清理该处空间，或另选一个位置。';

  @override
  String get projectExportFailedSource => '无法读取某个项目文件。请重试。';

  @override
  String get projectExportFailed => '导出在完成前停止。请重试。';

  @override
  String get projectExportProjectsLabel => '项目';

  @override
  String get thisPhoneExportProjects => '导出项目';

  @override
  String get thisPhoneExportProjectsDetail => '保存为 zip 文件，便于留存或迁移';

  @override
  String get demoNoCommands => '演示没有命令：发送示例提示词即可查看一次更改审查。';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '已登录 $providers，但此服务器即使重新加载也无法加载这些登录信息，因此其模型无法回答。部分提供商（如 Anthropic 和 Google）的浏览器登录无法在此服务器上加载。请改为在“提供商”下添加 API 密钥，或选择其他模型。',
      one:
          '已登录 $providers，但此服务器即使重新加载也无法加载该登录信息，因此其模型无法回答。部分提供商（如 Anthropic 和 Google）的浏览器登录无法在此服务器上加载。请改为在“提供商”下添加 API 密钥，或选择其他模型。',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '重新加载会等待 $count 个正在进行的回复完成，因为重新加载会中断它们。',
      one: '重新加载会等待 1 个正在进行的回复完成，因为重新加载会中断它。',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      '正在使用 OpenCode 的免费模型，速度较慢。添加你所用服务商的 API 密钥即可使用自己的模型。';

  @override
  String get chatFreeModelNote => 'Free model, slower';

  @override
  String get chatFreeModelAddKey => 'Add your key';

  @override
  String get freeModelSignIn => '添加 API 密钥';

  @override
  String get replySpeedTitle => '回复速度';

  @override
  String replySpeedLast(String first, String total) {
    return '上次回复：$first 后出现首字，$total 后完成';
  }

  @override
  String replySpeedNoWords(String total) {
    return '上次回复：在任何文字出现前，$total 后结束';
  }

  @override
  String replySpeedSeconds(String seconds) {
    return '$seconds 秒';
  }

  @override
  String get perfDetailLinuxMode => 'Linux 速度模式';

  @override
  String get perfLinuxModeFast => '快速：带 seccomp 的 proot';

  @override
  String get perfLinuxModeSlow => '较慢：不带 seccomp 的 proot';

  @override
  String get perfLinuxModeUnknown => 'OpenCode 停止时无法确定';

  @override
  String get perfDetailAwake => '保持手机唤醒';

  @override
  String get perfAwakeNow => '现在，回复运行期间';

  @override
  String get perfAwakeWhenWorking => '仅在回复运行期间';

  @override
  String get perfDetailFirstWords => '首字时间，最近一次回复';

  @override
  String perfFirstWordsSplit(String app, String server) {
    return '$app（应用内） · $server（服务器端）';
  }

  @override
  String get perfDetailModel => '模型，最近一次回复';

  @override
  String get manageSpaceIntroNothingToExport =>
      '清除会删除 OpenCode Mobile 在此手机上保存的所有内容，且无法撤销。';

  @override
  String integrationsKeyOnlyHelper(String name) {
    return '$name 不允许其他应用进行浏览器登录，请改用 API 密钥。它独立于任何订阅单独计费。密钥保存在此服务器上，之后不会再显示。';
  }

  @override
  String integrationsGetKey(String name) {
    return '从 $name 获取密钥';
  }

  @override
  String integrationsKeySavedReady(String name) {
    return '$name 的密钥已保存。请在模型选择器中选择它的一个模型。';
  }

  @override
  String integrationsKeySavedWaiting(String name) {
    return '$name 的密钥已保存。正在运行的回复结束后它就会加载。';
  }

  @override
  String integrationsKeySavedUnusable(String name) {
    return '$name 的密钥已保存，但此服务器在刷新后无法加载它。请检查密钥，或在模型选择器中尝试“重新加载提供商”。';
  }

  @override
  String integrationsKeySavedPending(String name) {
    return '$name 的密钥已保存。服务器尚未加载它。';
  }

  @override
  String migrationReviewSpace(String needed, String free) {
    return '需要约 $needed · 可用 $free';
  }

  @override
  String migrationReviewSpaceUnknown(String needed) {
    return '需要约 $needed · 可用空间未知';
  }

  @override
  String get migrationReviewSpaceShort => '可用空间不足。请减少选择的项，或释放此手机的空间。';

  @override
  String get migrationDiscard => '丢弃已保存的副本';

  @override
  String get migrationDiscardTitle => '丢弃此已保存的副本？';

  @override
  String get migrationDiscardBody => '临时副本文件将被删除。已导入的文件和 Termux 中的所有内容都会保留。';

  @override
  String get migrationDiscardFailed => '无法删除已保存的副本。请稍后重试。';

  @override
  String get migrationStopping => '正在停止…';

  @override
  String get pickerConnectProvider => '连接服务商';

  @override
  String get pickerConnectProviderHint => '添加 API 密钥或登录后即可使用其模型';

  @override
  String pickerAddKeyFor(String name) {
    return '添加 $name 的 API 密钥';
  }

  @override
  String get pickerAddKeyNotConnectedHint => '它的模型还不在此列表中';

  @override
  String pickerSignInTo(String name) {
    return '登录 $name';
  }

  @override
  String get pickerSignInHint => '打开此服务器的登录方式';

  @override
  String get pickerFreeOnlyNote => '目前只有 OpenCode 的免费模型可用，速度较慢。';

  @override
  String pickerProviderReady(String name) {
    return '$name 已就绪，它的模型已在列表中。';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return '$name 已保存，但服务器尚未加载它。';
  }

  @override
  String get integrationsSignedInUnusable => '已登录，但此服务器无法使用';

  @override
  String get chatUiCompactConfirmTitle => '压缩此对话？';

  @override
  String get chatUiCompactConfirmBody => '压缩会用简短摘要替换较早的消息以节省空间，且无法撤销。';

  @override
  String get chatUiCompactConfirmAction => '压缩对话';

  @override
  String get kitTurnReconnecting => '连接已断开。正在重新连接以获取此回复的其余部分。';

  @override
  String get kitTurnLiveSending => '正在发送';

  @override
  String get kitTurnLiveWaitingForServer => '等待服务器';

  @override
  String get kitTurnLiveServerQuiet => '服务器还没有响应';

  @override
  String get kitTurnLiveThinking => '思考中';

  @override
  String get kitTurnLiveFirstWordSlow => '正在等待模型输出第一个字';

  @override
  String get kitTurnLiveFirstWordSlowTeam => 'AI Team 也在这部手机上工作，所以回复可能会变慢';

  @override
  String get kitTurnLiveWriting => '正在撰写';

  @override
  String get kitTurnLiveWorking => '工作中';

  @override
  String get kitTurnLiveWaitingForYou => '等待你操作';

  @override
  String get kitTurnLiveStop => '停止回复';

  @override
  String kitTurnLiveNow(String status) {
    return '$status…';
  }

  @override
  String kitTurnLiveFor(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String kitTurnLiveSeconds(int seconds) {
    return '$seconds 秒';
  }

  @override
  String kitTurnLiveMinutes(int minutes, int seconds) {
    return '$minutes 分 $seconds 秒';
  }

  @override
  String get chatNoReplyCameBack => '没有收到回复';

  @override
  String get composerFieldLabel => '发给智能体的消息';

  @override
  String get e7WorkspaceYesterday => '昨天';

  @override
  String teamProgressOne(String title) {
    return 'AI 团队：$title';
  }

  @override
  String teamProgressStep(String title, int done, int total) {
    return 'AI 团队：$title · 第 $done 步，共 $total 步';
  }

  @override
  String teamProgressMany(int count) {
    return 'AI 团队：$count 个任务进行中';
  }

  @override
  String get teamSettingsTitle => '团队设置';

  @override
  String get teamSettingsOpenTooltip => '团队设置';

  @override
  String get teamSettingsTurnOff => '关闭 AI 团队';

  @override
  String teamChatWorkerNumbered(String role, int n) {
    return '$role $n';
  }

  @override
  String get teamRoleNameGeneral => '通用';

  @override
  String get teamRoleNameProduct => '产品';

  @override
  String get teamRoleNameFrontend => '前端';

  @override
  String get teamRoleNameBackend => '后端';

  @override
  String get teamRoleNameTester => '测试';

  @override
  String get teamRolePurposeGeneral => '任何任务，按常规方式完成';

  @override
  String get teamRolePurposeProduct => '把想法变成清晰的需求和计划';

  @override
  String get teamRolePurposeFrontend => '界面、布局与使用感受';

  @override
  String get teamRolePurposeBackend => '服务器、数据以及界面背后的代码';

  @override
  String get teamRolePurposeTester => '找出哪里会坏，并证明它可用';

  @override
  String get teamRolesTitle => '智能体';

  @override
  String get teamRolesNew => '新建角色';

  @override
  String teamRoleWorkingOn(String task, String age) {
    return '正在处理“$task” · $age';
  }

  @override
  String teamRoleUses(String model) {
    return '使用 $model';
  }

  @override
  String get teamRoleUsesTeamModel => '使用团队的模型';

  @override
  String get teamRoleUsesComputerModel => '使用电脑的模型';

  @override
  String teamRoleTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个任务',
      one: '1 个任务',
      zero: '尚无任务',
    );
    return '$_temp0';
  }

  @override
  String teamSettingsAgentsRow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '智能体 · $count 个角色',
      one: '智能体 · 1 个角色',
    );
    return '$_temp0';
  }

  @override
  String get teamSettingsAgentsHint => '由谁来做，以及各自如何工作';

  @override
  String get teamRoleNewTitle => '新建角色';

  @override
  String get teamRoleFieldName => '名称';

  @override
  String get teamRoleFieldPurpose => '用途';

  @override
  String get teamRoleFieldPurposeHint => '一行说明，例如：撰写指南';

  @override
  String get teamRoleFieldInstructions => '指令';

  @override
  String get teamRoleFieldInstructionsHint => '用你自己的话说明此角色应如何工作';

  @override
  String get teamRoleNameRequired => '请为角色取一个名称';

  @override
  String get teamRoleModelRow => '模型';

  @override
  String get teamRoleTeamModel => '团队的模型';

  @override
  String get teamRoleComputerModel => '电脑的模型';

  @override
  String get teamRoleModelSheetDefault => '团队的模型';

  @override
  String get teamRoleModelSheetDefaultHint => '使用整个团队所用的模型';

  @override
  String teamRoleWorkingNow(String task) {
    return '正在处理“$task”';
  }

  @override
  String get teamRoleOpenConversation => '打开其对话';

  @override
  String get teamRoleRecentTasks => '最近的任务';

  @override
  String teamRoleNoTasks(String role) {
    return '尚未给$role任何任务';
  }

  @override
  String teamRoleGiveTask(String role) {
    return '给$role一个任务';
  }

  @override
  String get teamRoleSave => '保存';

  @override
  String get teamRoleCreate => '创建角色';

  @override
  String teamRoleReset(String role) {
    return '重置 $role';
  }

  @override
  String teamRoleResetTitle(String role) {
    return '重置 $role？';
  }

  @override
  String get teamRoleResetBody => '其名称、用途、指令和模型将恢复为出厂状态。';

  @override
  String teamRoleDelete(String role) {
    return '删除 $role';
  }

  @override
  String teamRoleDeleteTitle(String role) {
    return '删除 $role？';
  }

  @override
  String teamRoleDeleteBody(String role) {
    return '$role 将从团队中移除。它已完成的任务仍保留其名称。';
  }

  @override
  String get teamRoleWorkerName => '工作者名称';

  @override
  String get teamRoleExamplesLabel => '从示例开始';

  @override
  String get teamRoleExampleDocs => '文档撰写者';

  @override
  String get teamRoleExampleDocsPurpose => '撰写并更新指南';

  @override
  String get teamRoleExampleDocsInstructions =>
      '你负责撰写和更新文档。保持简短、准确、用词平实。写下的每条命令和路径都要先核实。';

  @override
  String get teamRoleExampleSecurity => '安全审查者';

  @override
  String get teamRoleExampleSecurityPurpose => '查找代码可能被滥用的途径';

  @override
  String get teamRoleExampleSecurityInstructions =>
      '你审查代码的安全问题：代码或日志中的密钥、未校验的输入、不安全的链接，以及缺失的权限检查。报告时给出文件和行号，只修复任务要求的内容。';

  @override
  String get teamRoleExampleDesigner => '设计师';

  @override
  String get teamRoleExampleDesignerPurpose => '让它清晰、一致且令人愉悦';

  @override
  String get teamRoleExampleDesignerInstructions =>
      '你改进产品的外观与文字表达。复用应用中已有的组件和用词，保持统一的设计语言，并检查小屏与大字号。';

  @override
  String get teamRoleStarterInstructions =>
      '你是本团队的 ___。\n关注：___\n始终：___\n绝不：___';

  @override
  String get teamStartRunWho => '谁来接手';

  @override
  String get teamStartRunWhoSuggested => '根据你的描述推荐';

  @override
  String get teamStartRunWhoChange => '更改';

  @override
  String get teamStartRunWhoTitle => '由谁来接手？';

  @override
  String teamChatLeadStartingRole(String role, String title) {
    return '$role 已开始处理“$title”';
  }

  @override
  String teamChatLeadClaimedRole(String role, String title) {
    return '$role 接手了“$title”';
  }

  @override
  String teamChatLeadStartingItRole(String role) {
    return '$role 已启动';
  }

  @override
  String teamChatLeadClaimedItRole(String role) {
    return '$role 接手了任务';
  }

  @override
  String get teamRolesSearchAliases => '角色 人设 智能体 团队 前端 后端 测试 产品 设计师 指令';

  @override
  String get teamUiStatePhoneStoppedTitle => 'AI 团队已停止';

  @override
  String get teamUiStatePhoneStoppedBody => '本机的 AI 团队未在运行。启动后即可继续你的任务。';

  @override
  String get teamStartStepService => '正在启动团队的服务';

  @override
  String get teamStartStepAnswering => '正在等待团队应答';

  @override
  String get teamStartStepStore => '正在打开任务存储';

  @override
  String get teamStartStepAgents => '正在准备智能体';

  @override
  String get teamStartSlow => '耗时超出平常，手机正忙';

  @override
  String get teamStartAgain => '重新启动';

  @override
  String get teamUiHostPhraseStarting => '正在启动';

  @override
  String get teamUiStartOnPhone => '在本机启动 AI 团队';

  @override
  String get chatCollapseAllSteps => '折叠所有步骤';

  @override
  String chatWatchTeamInstructions(int count, String time) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '来自团队的指令 · $countString 个词 · $time',
      one: '来自团队的指令 · 1 个词 · $time',
    );
    return '$_temp0';
  }

  @override
  String get chatWatchEmptyStartingTitle => '正在启动';

  @override
  String get chatWatchEmptyStartingBody => '它工作时，步骤会显示在这里。';

  @override
  String chatWatchEmptyWorkingOn(String task) {
    return '正在处理“$task”。它工作时，步骤会显示在这里。';
  }

  @override
  String chatWatchEmptyReviewing(String task) {
    return '正在审查“$task”的更改。它工作时，步骤会显示在这里。';
  }

  @override
  String get chatWatchEmptyIdleTitle => '等待中';

  @override
  String get chatWatchEmptyIdleBody => '它在等待下一个任务。在下方给它发消息即可提出请求。';

  @override
  String get teamUiAgentLabelSessionTitle => '对话标题';

  @override
  String get kitComposerPillNoAnswer => '暂无回复';

  @override
  String get kitComposerRailRetry => '重试';

  @override
  String get teamProjectHome => 'AI 团队';

  @override
  String get teamProjectDemo => '演示';

  @override
  String get teamProjectNew => '新建项目';

  @override
  String get teamProjectQuick => '给个快捷任务';

  @override
  String get teamProjectSettings => '项目设置';

  @override
  String get teamProjectRoles => '角色与智能体';

  @override
  String get teamProjectEmpty => '给你的团队一个目标，即可创建项目。';

  @override
  String get teamProjectSelect => '选择项目';

  @override
  String get teamProjectSelectTask => '选择一个任务以跟进其对话。';

  @override
  String get teamProjectLoad => '正在加载项目';

  @override
  String get teamProjectRetry => '重试';

  @override
  String get teamProjectError => '项目无法更新。你已保存的工作仍然可用。';

  @override
  String get teamProjectSpec => '打开规格';

  @override
  String get teamProjectPlan => '审查计划';

  @override
  String get teamProjectBoard => '看板';

  @override
  String get teamProjectGraph => '依赖关系';

  @override
  String get teamProjectTimeline => '时间线';

  @override
  String get teamProjectServers => '服务器';

  @override
  String get teamProjectMilestones => '里程碑';

  @override
  String get teamProjectMerge => '合并队列';

  @override
  String get teamProjectCost => '费用';

  @override
  String get teamProjectDecisions => '最近的决策';

  @override
  String get teamProjectPause => '暂停项目';

  @override
  String get teamProjectResume => '恢复项目';

  @override
  String get teamProjectStopConfirmTitle => '停止此项目？';

  @override
  String get teamProjectStop => '停止项目';

  @override
  String get teamProjectStopBody => '正在运行的任务将停止。其工作和项目历史会保留。';

  @override
  String get teamProjectAdvance => '推进演示';

  @override
  String get teamProjectDigest => '你不在时';

  @override
  String get teamProjectDigestRead => '标记为已读';

  @override
  String get teamProjectAnswer => '答复';

  @override
  String get teamProjectAnswerLabel => '你的答复';

  @override
  String get teamProjectAll => '全部';

  @override
  String get teamProjectMerges => '合并';

  @override
  String get teamProjectProblems => '问题';

  @override
  String get teamProjectMilestoneFilter => '里程碑';

  @override
  String get teamProjectRepoFilter => '仓库';

  @override
  String get teamProjectServerFilter => '服务器';

  @override
  String get teamProjectBacklog => '待办';

  @override
  String get teamProjectReady => '就绪';

  @override
  String get teamProjectWorking => '进行中';

  @override
  String get teamProjectReview => '审查';

  @override
  String get teamProjectDone => '完成';

  @override
  String get teamProjectNoTasks => '此视图中没有任务。';

  @override
  String get teamProjectMove => '移动任务';

  @override
  String get teamProjectMoveTo => '移动到服务器';

  @override
  String get teamProjectHandoff => '交接说明';

  @override
  String get teamProjectPaused => '已暂停';

  @override
  String get teamProjectStopped => '已停止';

  @override
  String get teamProjectFailed => '意外停止';

  @override
  String get teamProjectStalled => '近期无进展';

  @override
  String get teamProjectPlanning => '正在梳理规格';

  @override
  String get teamProjectPlanWaiting => '计划已就绪，待审查';

  @override
  String get teamProjectNeedsYou => '需要你决策';

  @override
  String get teamProjectWaiting => '正在等待依赖';

  @override
  String get teamProjectOnline => '可达';

  @override
  String get teamProjectOffline => '不可达 · 上次已知的任务';

  @override
  String get teamProjectNoLimit => '无限制';

  @override
  String get teamProjectUnknown => '未报告';

  @override
  String get teamProjectAcceptMilestoneConfirmTitle => '接受此里程碑？';

  @override
  String get teamProjectAccept => '接受里程碑';

  @override
  String get teamProjectMergeConfirmTitle => '合并到 dev？';

  @override
  String get teamProjectMergeNext => '将已检查的工作合并到 dev';

  @override
  String get teamProjectCostDemo => '演示数据为模拟值；设备内存、电池、发热和对话速度均未实测。';

  @override
  String teamProjectPlanningElapsed(int minutes) {
    return '正在梳理规格 · $minutes 分钟';
  }

  @override
  String teamProjectPlanningSlow(int minutes) {
    return '仍在梳理规格 · $minutes 分钟';
  }

  @override
  String teamProjectAllTasksDone(int total) {
    return '全部 $total 个任务已完成';
  }

  @override
  String teamProjectProgress(int done, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working 个进行中',
      zero: '',
    );
    return '$done / $total 个任务已完成$_temp0';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy / $total 条泳道占用中';
  }

  @override
  String get teamProjectEditorNewProject => '新建项目';

  @override
  String get teamProjectEditorQuickTask => '快捷任务';

  @override
  String get teamProjectEditorSpec => '活文档规格';

  @override
  String get teamProjectEditorPlan => '审查计划';

  @override
  String get teamProjectEditorSettings => '项目设置';

  @override
  String get teamProjectEditorRoles => '角色与智能体';

  @override
  String get teamProjectEditorStartPlanning => '开始规划';

  @override
  String get teamProjectEditorStartTask => '开始任务';

  @override
  String get teamProjectEditorApproveSpec => '批准规格';

  @override
  String get teamProjectEditorApprovePlan => '批准并开始';

  @override
  String get teamProjectEditorSave => '保存更改';

  @override
  String get teamProjectEditorSaveDraft => '保存草稿';

  @override
  String get teamProjectEditorName => '项目名称';

  @override
  String get teamProjectEditorGoal => '目标';

  @override
  String get teamProjectEditorRepos => '仓库';

  @override
  String get teamProjectEditorRepoName => '仓库名称';

  @override
  String get teamProjectEditorRepoPath => '仓库文件夹';

  @override
  String get teamProjectEditorServer => '服务器';

  @override
  String get teamProjectEditorRemove => '移除';

  @override
  String get teamProjectEditorAddRepo => '添加仓库';

  @override
  String get teamProjectEditorRole => '角色';

  @override
  String get teamProjectEditorPlanFirst => '先做计划';

  @override
  String get teamProjectEditorMode => '执行模式';

  @override
  String get teamProjectEditorSingle => '单泳道';

  @override
  String get teamProjectEditorParallel => '并行智能体';

  @override
  String get teamProjectEditorMaxLanes => '最大泳道数';

  @override
  String teamProjectEditorCostMeasured(String host, String memory) {
    return '在 $host 上：每条泳道约 $memory MB 内存，已实测。电池和对话速度尚未实测。';
  }

  @override
  String teamProjectEditorCostNotMeasured(String host) {
    return '尚未在 $host 上实测。你的对话始终优先。';
  }

  @override
  String get teamProjectEditorCostNoHost => '选择工作运行的位置，即可查看那里每条泳道的开销。';

  @override
  String get teamProjectEditorThisPhone => '本机';

  @override
  String get teamProjectEditorGoalRequired => '请添加目标。';

  @override
  String get teamProjectEditorRepoMissing => '请至少添加一个仓库。';

  @override
  String get teamProjectEditorRepoIncomplete => '请补全仓库：名称、文件夹以及运行位置。';

  @override
  String get teamProjectEditorNoFallback => '无备用模型';

  @override
  String get teamProjectEditorNoFallbackHint => '工作会等待主模型，而不是切换。';

  @override
  String get teamProjectEditorReadOnlyRole => '只读：此智能体可以读取项目，但不能修改。';

  @override
  String get teamProjectEditorReadOnlyShort => '只读';

  @override
  String get teamProjectEditorCharging => '仅充电时';

  @override
  String get teamProjectEditorReview => '审查级别';

  @override
  String get teamProjectEditorMilestonesRisk => '里程碑与风险点';

  @override
  String get teamProjectEditorEveryStep => '每一步';

  @override
  String get teamProjectEditorBudget => '预算';

  @override
  String get teamProjectEditorSetLimits => '设置上限';

  @override
  String get teamProjectEditorNoLimit => '无限制';

  @override
  String get teamProjectEditorDailyBudget => '每天（USD）';

  @override
  String get teamProjectEditorTotalBudget => '总计（USD）';

  @override
  String get teamProjectEditorTaskTokens => '每个任务的 token 上限（可选）';

  @override
  String get teamProjectEditorAutoFix => '自动修复发现的问题';

  @override
  String get teamProjectEditorMaxRounds => '最大修复轮次';

  @override
  String get teamProjectEditorConstraints => '约束';

  @override
  String get teamProjectEditorDecisions => '决策';

  @override
  String get teamProjectEditorOutOfScope => '不在范围内';

  @override
  String get teamProjectEditorMilestones => '里程碑';

  @override
  String get teamProjectEditorMilestoneTitle => '里程碑标题';

  @override
  String get teamProjectEditorCriteria => '验收标准（每行一条）';

  @override
  String get teamProjectEditorMoveUp => '上移';

  @override
  String get teamProjectEditorMoveDown => '下移';

  @override
  String get teamProjectEditorAddMilestone => '添加里程碑';

  @override
  String get teamProjectEditorHistory => '版本历史';

  @override
  String get teamProjectEditorVersion => '版本';

  @override
  String get teamProjectEditorPlanHelp => '审查任务及其验收标准。你批准计划时，此处的更改会一并纳入。';

  @override
  String get teamProjectEditorRisky => '审查关卡 · 风险';

  @override
  String get teamProjectEditorTaskTitle => '任务标题';

  @override
  String get teamProjectEditorRepo => '仓库';

  @override
  String get teamProjectEditorDependencies => '依赖于';

  @override
  String get teamProjectEditorRemoveTask => '移除任务';

  @override
  String get teamProjectEditorAddRole => '添加角色';

  @override
  String get teamProjectEditorRoleName => '角色名称';

  @override
  String get teamProjectEditorInstructions => '指令';

  @override
  String get teamProjectEditorModel => '模型';

  @override
  String get teamProjectEditorFallback => '备用模型';

  @override
  String get teamProjectEditorAllRoles => '所有角色';

  @override
  String get teamProjectEditorChooseMode => '请选择单泳道或并行智能体。';

  @override
  String get teamProjectEditorPositiveLanes => '请输入 1 到 32 之间的泳道数。';

  @override
  String get teamProjectEditorChooseBudget => '请设置预算或选择无限制。';

  @override
  String get teamProjectEditorPositiveBudget => '请分别输入每日上限和总上限，且都大于零。';

  @override
  String get teamProjectEditorSaveFailed => '更改未能保存。你的编辑仍在这里；请再次保存。';

  @override
  String get teamProjectEditorRequired => '请添加目标和至少一个仓库。';

  @override
  String get teamProjectEditorChooseRoleServer => '请为此任务选择角色和服务器。';

  @override
  String get teamProjectEditorRepoRequired => '请选择服务器，并输入仓库名称和文件夹。';

  @override
  String get teamProjectEditorSpecRequired => '请添加目标和至少一个里程碑，且包含标题与验收标准。';

  @override
  String get teamProjectEditorDraftFailed => '草稿无法保存在本设备上。请保持此页面打开并再次保存。';

  @override
  String get teamProjectEditorChangedElsewhere =>
      '此项目在你编辑期间已变化。请关闭此面板，查看最新项目后再批准更改。';

  @override
  String get teamProjectTaskMissing => '此任务已不可用';

  @override
  String get teamProjectRefreshTask => '刷新任务';

  @override
  String get teamProjectTaskSaveFailed => '更改未保存。请刷新后重试；你的消息仍在这里。';

  @override
  String get teamProjectTaskMessage => '给团队发消息…';

  @override
  String get teamProjectTaskInstructions => '来自团队的指令';

  @override
  String get teamProjectTaskApprovePlan => '批准并开始';

  @override
  String get teamProjectTaskReview => '需要审查';

  @override
  String get teamProjectTaskAccepted => '已接受';

  @override
  String get teamProjectTaskAcceptPhase => '接受阶段';

  @override
  String get teamProjectTaskFindings => '验证发现';

  @override
  String get teamProjectTaskFix => '修复所选';

  @override
  String get teamProjectTaskRecheck => '重新检查任务';

  @override
  String get teamProjectTaskIgnore => '忽略所选发现';

  @override
  String get teamProjectTaskIgnoreReason => '为什么可以安全忽略此发现？';

  @override
  String get teamProjectTaskReasonRequired => '请输入理由，随此决策一并保留。';

  @override
  String get teamProjectTaskCritical => '严重';

  @override
  String get teamProjectTaskMajor => '重要';

  @override
  String get teamProjectTaskMinor => '次要';

  @override
  String get teamProjectTaskMerge => '合并队列到 dev';

  @override
  String get teamProjectTaskMergeRun => '检查并合并到 dev';

  @override
  String get teamProjectTaskPromoteConfirmTitle => '将 dev 提升到 main？';

  @override
  String get teamProjectTaskPromote => '将 dev 提升到 main';

  @override
  String get teamProjectTaskPromoteBody =>
      '这会把受保护的 main 更新为你已审查的 dev 提交。引擎在更改 main 之前会再次检查这两个提交。';

  @override
  String get teamProjectTaskDiff => '查看更改';

  @override
  String get teamProjectTaskPause => '暂停任务';

  @override
  String get teamProjectTaskResume => '恢复任务';

  @override
  String get teamProjectTaskStopConfirmTitle => '停止此任务？';

  @override
  String get teamProjectTaskStop => '停止任务';

  @override
  String get teamProjectTaskStopBody => '停止此任务，并保留其对话和更改以供审查。';

  @override
  String get teamProjectTaskRestart => '再次启动任务';

  @override
  String get teamProjectTaskAnswer => '发送答复';

  @override
  String get teamProjectTaskAnswerLabel => '你的答复';

  @override
  String get teamProjectTaskRunning => '进行中';

  @override
  String get teamProjectTaskWaiting => '等待中';

  @override
  String get teamProjectTaskDone => '完成';

  @override
  String get teamProjectTaskFailed => '任务在完成前停止';

  @override
  String get teamProjectTaskStale => '上次已知状态';

  @override
  String get teamProjectTaskCollapse => '全部折叠';

  @override
  String get teamProjectTaskWork => '已完成的工作';

  @override
  String get teamProjectTaskEmpty => '任务已排队。其回复和检查会显示在这里。';

  @override
  String get teamProjectTaskReceipt => '提升回执';

  @override
  String get teamProjectTaskVerify => '校验任务';

  @override
  String get teamProjectTryDemo => '试用 AI Team 演示';

  @override
  String get teamProjectLoadFailure => '无法打开演示。你已保存的项目数据已保留。';

  @override
  String get teamProjectDemoDisclosure => '模拟项目。不会运行任何智能体，也不会更改任何仓库。';

  @override
  String get teamProjectOff => '退出演示';

  @override
  String get teamProjectEditorFixRoundsRange => '请输入 0 到 3 之间的修复轮次上限。';

  @override
  String get teamProjectEditorPositiveTokens => '请输入正的 token 上限，或留空。';

  @override
  String get teamProjectEditorReloadConfirmTitle => '刷新此项目？';

  @override
  String get teamProjectEditorReload => '刷新最新项目';

  @override
  String get teamProjectEditorDiscardDraft => '这会用最新项目替换你未保存的编辑。已保存的项目会保留。';

  @override
  String get teamProjectEditorRoleRequired => '请为此角色输入名称。';

  @override
  String get teamProjectEditorDefaults => '新项目默认值';

  @override
  String get teamProjectEditorApplyPlan => '应用更新后的计划';

  @override
  String get teamProjectEditorContextFiles => '优先读取的文件';

  @override
  String get teamProjectEditorContextFilesHelp =>
      '可选。每行一个路径。团队会在规划前读取这些文件。演示模式不会读取或上传文件。';

  @override
  String get teamProjectEditorScreenOff => '熄屏后继续工作';

  @override
  String get teamProjectEditorScreenOffHelp => '此偏好按项目保存。后台工作仍受服务器和系统限制。';

  @override
  String get teamProjectEditorDraftApproval => '草稿更改需经你批准后才会成为项目规格。';

  @override
  String get teamProjectEditorChangeRequest => '规划器应更改什么？';

  @override
  String get teamProjectEditorAskChange => '请求更改';

  @override
  String get teamProjectEditorChangeRequired => '请添加目标，并描述你想要的更改。';

  @override
  String get teamProjectTaskCriteria => '验收标准';

  @override
  String get teamProjectTaskOpenFindings => '未解决的发现';

  @override
  String get teamProjectTaskFindingsAddressed => '已处理的发现';

  @override
  String get teamProjectMergeConfirmBody =>
      '已检查的任务分支将合并到 dev，随后进行合并后检查。main 保持不变。';

  @override
  String get teamProjectEditorDraftClearFailed =>
      '更改已保存，但本地草稿无法清除。请关闭此面板，查看项目后再试。';

  @override
  String get teamProjectRestartElsewhereConfirmTitle => '换台服务器重来？';

  @override
  String get teamProjectRestartElsewhere => '换台服务器重来';

  @override
  String get teamProjectRestartElsewhereBody => '在此服务器上重新尝试。之前的分支仍留在原服务器上。';

  @override
  String get teamProjectWaitForServer => '等待原服务器';

  @override
  String get teamProjectBudgetNear => '接近预算。达到你设定的上限时，新工作会暂停。';

  @override
  String get teamProjectDemoPlanFailure => '演示：无法读取的计划';

  @override
  String get teamProjectTaskReviewFindings => '选择未解决的发现';

  @override
  String get teamProjectTaskResolveAgent => '由智能体解决';

  @override
  String get teamProjectTaskResolveManually => '我自行解决';

  @override
  String get teamProjectTaskRecheckResolution => '重新检查解决结果';

  @override
  String get teamProjectTaskVerificationResults => '验证结果';

  @override
  String get teamProjectTaskCriterionMet => '已满足';

  @override
  String get teamProjectTaskCriterionUnmet => '未满足';

  @override
  String get teamProjectTaskCriterionNotApplicable => '不适用';

  @override
  String get teamProjectTaskDemoConflict => '演示：制造冲突';

  @override
  String get teamProjectTaskDemoCommit => '演示：添加手动提交';

  @override
  String get teamProjectEditorNoOptions => '暂无可选项。请返回 AI 团队添加服务器或角色。';

  @override
  String get teamProjectEditorUnknownDate => '日期不可用';

  @override
  String get teamProjectEditorYou => '你';

  @override
  String get teamProjectEditorApprovedBy => '批准人';

  @override
  String get teamProjectEditorPlanFailed => '规划器未返回可用的计划。可将目标保留为单个任务，或请求新的计划。';

  @override
  String get teamProjectEditorUseAsTask => '作为单个任务';

  @override
  String get teamProjectEditorAskAgain => '再次请求';

  @override
  String get teamProjectDemoChip => '演示';

  @override
  String teamProjectHeadlineMilestone(int current, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working 个进行中',
      zero: '',
    );
    return '里程碑 $current / $total$_temp0';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return '全部 $total 个里程碑已完成';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones 个里程碑',
      one: '1 个里程碑',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos 个仓库',
      one: '1 个仓库',
    );
    return '规格批准于$age · $_temp0 · $_temp1';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones 个里程碑',
      one: '1 个里程碑',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos 个仓库',
      one: '1 个仓库',
    );
    return '规格草稿 · $_temp0 · $_temp1';
  }

  @override
  String get teamProjectOpenSpec => '打开规格';

  @override
  String teamProjectRequestWhere(String role, String server) {
    return '$role 在 $server 上';
  }

  @override
  String teamProjectRequestBlocks(String role, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '其后 $count 个任务也在等待',
      one: '其后 1 个任务也在等待',
    );
    return '$role 等待中；$_temp0';
  }

  @override
  String teamProjectMilestoneTasks(int done, int total) {
    return '$done / $total 个任务';
  }

  @override
  String teamProjectMilestoneWaits(int number) {
    return '等待 $number';
  }

  @override
  String get teamProjectMilestoneNoTasks => '尚无任务';

  @override
  String teamProjectLanesTitle(int busy, int total) {
    return '泳道 $busy/$total 占用';
  }

  @override
  String get teamProjectLanesChange => '更改';

  @override
  String teamProjectLaneRunning(String server, String elapsed) {
    return '$server · $elapsed';
  }

  @override
  String teamProjectLaneWaiting(String server) {
    return '$server · 等待空闲泳道';
  }

  @override
  String teamProjectLaneTitle(String role, String task) {
    return '$role · $task';
  }

  @override
  String teamProjectLaneNoteParallel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条泳道',
      one: '1 条泳道',
    );
    return '并行 · $_temp0';
  }

  @override
  String get teamProjectLaneNoteSingle => '单泳道';

  @override
  String teamProjectLaneNoteDemo(String note) {
    return '$note · 数据为模拟值';
  }

  @override
  String teamProjectElapsedSeconds(int count) {
    return '$count 秒';
  }

  @override
  String teamProjectElapsedMinutes(int count) {
    return '$count 分钟';
  }

  @override
  String teamProjectElapsedHours(int hours, int minutes) {
    return '$hours 小时 $minutes 分钟';
  }

  @override
  String teamProjectCostToday(String amount) {
    return '今天 $amount';
  }

  @override
  String teamProjectCostTodayOf(String amount, String limit) {
    return '今天 $amount / $limit';
  }

  @override
  String teamProjectCostTotal(String amount) {
    return '总计 $amount';
  }

  @override
  String teamProjectCostTotalOf(String amount, String limit) {
    return '总计 $amount / $limit';
  }

  @override
  String get teamProjectCostNotReported => '尚未报告';

  @override
  String teamProjectBoardSummary(int tasks, int milestones) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones 个里程碑',
      one: '1 个里程碑',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks 个任务',
      one: '1 个任务',
    );
    return '$_temp0中共 $_temp1';
  }

  @override
  String get teamProjectBoardEmpty => '尚无任务';

  @override
  String teamProjectTimelineSummary(int count, String age) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个事件',
      one: '1 个事件',
    );
    return '$_temp0 · 最新 $age';
  }

  @override
  String get teamProjectTimelineEmpty => '尚无任何事件';

  @override
  String teamProjectServersSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 台服务器',
      one: '1 台服务器',
    );
    return '$_temp0';
  }

  @override
  String teamProjectSettingsSummaryParallel(int count) {
    return '并行 · 最多 $count 条泳道';
  }

  @override
  String get teamProjectSettingsSummarySingle => '单泳道';

  @override
  String get teamProjectMenu => '项目菜单';

  @override
  String get teamProjectTaskMenu => '任务菜单';

  @override
  String teamProjectDecisionBy(String who, String age) {
    return '$who · $age';
  }

  @override
  String get teamProjectYou => '你';

  @override
  String teamProjectPlanFor(int number) {
    return '里程碑 $number 的计划 · 等待你处理';
  }

  @override
  String get teamProjectPlanForProject => '计划 · 等待你处理';

  @override
  String teamProjectPlanSummary(int phases, int tasks, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      phases,
      locale: localeName,
      other: '$phases 个阶段',
      one: '1 个阶段',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks 个任务',
      one: '1 个任务',
    );
    String _temp2 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos 个仓库',
      one: '1 个仓库',
    );
    return '$_temp0 · $_temp1 · $_temp2';
  }

  @override
  String teamProjectPlanPhase(int number, String title) {
    return '阶段 $number · $title';
  }

  @override
  String get teamProjectPlanReviewPoint => '审查关卡 · 风险';

  @override
  String teamProjectPlanRepo(String name) {
    return '$name 仓库';
  }

  @override
  String teamProjectPlanAfter(int number) {
    return '在 $number 之后';
  }

  @override
  String teamProjectPlanCriteria(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条标准',
      one: '1 条标准',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPlanMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '… 另有 $count 个任务',
      one: '… 另有 1 个任务',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPlanEdit => '编辑计划';

  @override
  String get teamProjectPlanAsk => '请求更改';

  @override
  String get teamProjectPlanNotYet => '还没有';

  @override
  String teamProjectPlanServerTitle(String task) {
    return '在何处运行“$task”';
  }

  @override
  String get teamProjectPlanServerFixed =>
      '每个任务都在其规划时指定的电脑上运行。此团队暂时无法把任务移到另一台电脑。';

  @override
  String teamProjectMergeEffectDev(String repo, int tasks) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$repo 中 $tasks 个已检查的任务',
      one: '$repo 中 1 个已检查的任务',
    );
    return '$_temp0将合并到 dev。';
  }

  @override
  String get teamProjectMergeEffectMain => 'main 不受影响。';

  @override
  String teamProjectReceiptMerged(String repo) {
    return '已合并到 dev · $repo';
  }

  @override
  String teamProjectReceiptPromoted(String repo) {
    return '已提升到 main · $repo';
  }

  @override
  String teamProjectTimelineRepeated(String text, int count) {
    return '$text · $count 次';
  }

  @override
  String get teamProjectPromoteTitle => '将 dev 提升到 main';

  @override
  String teamProjectPromoteStatus(String repo) {
    return '$repo 仓库 · main 受保护。只有你可以提升。';
  }

  @override
  String teamProjectPromoteMilestone(int number, String title) {
    return '里程碑 $number · $title';
  }

  @override
  String teamProjectPromoteMerged(int done, int total) {
    return '已合并 $done / $total 个任务';
  }

  @override
  String teamProjectPromoteDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 天',
      one: '1 天',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteChecks => '合并后检查';

  @override
  String get teamProjectPromoteChecksPassed => '上次合并后所有检查均通过';

  @override
  String get teamProjectPromoteReview => '审查';

  @override
  String teamProjectPromoteAccepted(int number) {
    return '你已接受里程碑 $number';
  }

  @override
  String get teamProjectPromoteNoReview => '此工作无需审查';

  @override
  String teamProjectPromoteChanges(int commits) {
    String _temp0 = intl.Intl.pluralLogic(
      commits,
      locale: localeName,
      other: '$commits 个提交',
      one: '1 个提交',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPromoteFiles(int files) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files 个文件',
      one: '1 个文件',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteSeeChanges => '查看更改';

  @override
  String get teamProjectPromoteNotYet => '还没有';

  @override
  String teamProjectFindingsTitle(String role, String summary) {
    return '由$role检查 · $summary';
  }

  @override
  String get teamProjectEditorGoalLabel => '团队应达成什么？';

  @override
  String get teamProjectEditorWhereRuns => '运行位置';

  @override
  String get teamProjectEditorMoreOptions => '可选详情';

  @override
  String get teamProjectEditorNameHelp => '可选。留空则使用目标的开头部分。';

  @override
  String get teamProjectEditorBudgetHelp => '当当天或整个项目达到上限时，团队会暂停。';

  @override
  String teamProjectFindingsCritical(int count) {
    return '$count 个严重';
  }

  @override
  String teamProjectFindingsMajor(int count) {
    return '$count 个重要';
  }

  @override
  String teamProjectFindingsMinor(int count) {
    return '$count 个次要';
  }

  @override
  String get phoneTeamSetupTitle => '开启 AI Team';

  @override
  String get phoneTeamStepReply => '让当前回复先完成';

  @override
  String get phoneTeamReplyWaiting => '正在完成当前回复…';

  @override
  String get phoneTeamStepStop => '停止 OpenCode 并关闭终端';

  @override
  String get phoneTeamStepCheck => '正在检查此手机是否适合运行团队';

  @override
  String get phoneTeamStepServer => '以受保护的方式重新启动 OpenCode';

  @override
  String get phoneTeamNotNeeded => '无需此步';

  @override
  String get phoneTeamStopTitle => '短暂停止 OpenCode？';

  @override
  String get phoneTeamStopBody =>
      '此手机上的 OpenCode 服务器会停止约一分钟，然后以受保护的方式重新启动。已打开的终端会关闭。';

  @override
  String get phoneTeamStopConfirm => '停止并继续';

  @override
  String get phoneTeamStopCancel => '暂不';

  @override
  String get phoneTeamStopWaiting => '等待你的回答';

  @override
  String get phoneTeamDoneTitle => 'AI Team 已就绪';

  @override
  String get phoneTeamDoneBody => '给它一个目标，团队就会规划、构建并检查工作。';

  @override
  String get phoneTeamFailUnsafeTitle => '团队保持关闭';

  @override
  String get phoneTeamFailUnsafeBody => '此手机无法把团队的代码副本与智能体隔离开，因此团队保持关闭。';

  @override
  String get phoneTeamFailEngineTitle => 'AI Team 未能启动';

  @override
  String get phoneTeamFailEngineBody => '团队的引擎未能在此手机上启动。';

  @override
  String get phoneTeamFailStopTitle => '服务器未能停止';

  @override
  String get phoneTeamFailStopBody => 'OpenCode 或某个终端未关闭，因此目前还无法安全地执行检查。';

  @override
  String get phoneTeamFailServerTitle => '服务器未能恢复';

  @override
  String get phoneTeamFailServerBody => '此手机通过了检查，但 OpenCode 未能重新启动。请再次启动以完成。';

  @override
  String get phoneTeamFailNotReadyTitle => '尚未就绪';

  @override
  String get phoneTeamFailNotReadyBody => 'OpenCode 已恢复，但团队还无法开始工作。';

  @override
  String get phoneTeamFailDeclinedTitle => '未做任何更改';

  @override
  String get phoneTeamFailDeclinedBody =>
      'OpenCode 保持原样，因此团队保持关闭。等你准备好重启它时再开始。';

  @override
  String get phoneTeamFailNoServerTitle => '请先添加服务器';

  @override
  String get phoneTeamFailNoServerBody => '团队使用此手机上的 OpenCode，但目前还没有。';

  @override
  String get phoneTeamReasonNotPackaged => '此应用副本不包含团队的安全工具。';

  @override
  String get phoneTeamStateBackOn => 'OpenCode 已恢复运行。';

  @override
  String get phoneTeamStateStillOff => 'OpenCode 仍处于停止状态。请再次启动，或从“此手机”中重启它。';

  @override
  String get phoneTeamStateNotStopped => 'OpenCode 未被停止。';

  @override
  String get phoneTeamStateTerminalsClosed => '已关闭打开的终端。';

  @override
  String get phoneTeamWhyUnsafe => '未能通过将团队的代码副本与智能体隔离的检查。';

  @override
  String get phoneTeamWhyEngine => '团队的引擎在启动过程中停止或无响应。';

  @override
  String get phoneTeamWhyStop => 'OpenCode 或某个终端在被要求关闭时没有关闭。';

  @override
  String get phoneTeamWhyServer => 'OpenCode 在重新启动后无响应。';

  @override
  String get phoneTeamWhyNotReady => '团队的引擎有响应，但表示目前无法运行工作。';

  @override
  String get phoneTeamOffTitle => 'AI Team 已关闭';

  @override
  String get phoneTeamOffBody => '它需要一次快速安全检查，例如在应用更新之后。';

  @override
  String get phoneTeamBlocked => '在检查此手机之前，团队无法开始工作。';

  @override
  String get phoneTeamStripChecking => '正在检查此手机上的 AI Team';

  @override
  String get phoneTeamStripWaiting => 'AI Team 需要重启 OpenCode';

  @override
  String get phoneTeamStripReview => '打开检查以选择时间';

  @override
  String phoneTeamStripStep(int step, int total) {
    return '第 $step 步，共 $total 步';
  }

  @override
  String get phoneTeamBlockedTitle => '先检查此手机？';

  @override
  String get teamMigrationTitle => 'AI 团队已更新';

  @override
  String get teamMigrationBody =>
      '新版 AI 团队可规划完整项目，并在本机运行。旧版团队仍可接快捷任务，但无法规划项目；切换时会先询问再停止任何内容。';

  @override
  String get teamMigrationSwitch => '切换到本机上的新版 AI 团队';

  @override
  String get teamMigrationKeep => '暂时保留旧版团队';

  @override
  String get teamMigrationMenu => 'AI 团队的新变化';

  @override
  String get teamProjectPages => '项目页面';

  @override
  String get teamProjectMergeReady => '可以合并';

  @override
  String get teamProjectMergeChecking => '等待其检查';

  @override
  String get teamRefusalUnsupportedCommand => '此版本的团队暂不支持该操作。';

  @override
  String get teamRefusalUnsupportedCommandNext => '请更新应用后重试。';

  @override
  String get teamRefusalBoundaryUnverified => '在本机的保护通过检查之前，团队无法工作。';

  @override
  String get teamRefusalBoundaryUnverifiedNext => '请打开 AI 团队并重新开启。';

  @override
  String get teamRefusalProtocolUnverified => '团队正在等待 OpenCode 重启。';

  @override
  String get teamRefusalProtocolUnverifiedNext => '请在一分钟后重试。';

  @override
  String get teamRefusalEngineUnavailable => '团队目前没有应答。';

  @override
  String get teamRefusalEngineUnavailableNext => '请稍后重试。';

  @override
  String get teamRefusalTransportUncertain => '团队可能未收到该请求。';

  @override
  String get teamRefusalTransportUncertainNext => '重试前请先查看项目列表。';

  @override
  String get teamRefusalBusy => '另一项更改仍在保存中。';

  @override
  String get teamRefusalBusyNext => '请稍后重试。';

  @override
  String get teamRefusalSaveFailed => '更改无法保存在本机。';

  @override
  String get teamRefusalSaveFailedNext => '你的编辑仍在这里。请重试。';

  @override
  String get teamRefusalReadOnly => '团队目前为只读状态。';

  @override
  String get teamRefusalReadOnlyNext => '请在本机开启 AI 团队以进行更改。';

  @override
  String get teamRefusalClosed => '团队已关闭。';

  @override
  String get teamRefusalClosedNext => '请再次打开 AI 团队以继续。';

  @override
  String get teamRefusalCommandRefused => '团队拒绝了此请求。';

  @override
  String get teamRefusalCommandRefusedNext => '请检查目标和仓库文件夹，然后重试。';

  @override
  String get teamRefusalImportFailed => '团队无法读取仓库文件夹。';

  @override
  String get teamRefusalImportFailedNext => '请检查文件夹名称，然后重试。';

  @override
  String get teamRefusalPayloadInvalid => '团队的应答格式本应用无法识别。';

  @override
  String get teamRefusalPayloadInvalidNext => '请更新应用后重试。';

  @override
  String get teamRefusalSchemaUnsupported => '团队与本应用的版本不一致。';

  @override
  String get teamRefusalSchemaUnsupportedNext => '请更新应用后重试。';

  @override
  String get teamRefusalEngineClosed => '团队正在关闭。';

  @override
  String get teamRefusalEngineClosedNext => '请再次开启 AI 团队以继续。';

  @override
  String get teamRefusalSessionFailed => '规划器在应答前停止了。';

  @override
  String get teamRefusalSessionFailedNext => '请打开规划器模型选项并检查，然后重新请求。';

  @override
  String get teamRefusalModelNotConfigured => '此任务使用的模型未接受请求。';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      '它可能需要登录，或在此处不可免费使用。请打开规划器模型选项，另选一个模型后重新请求。';

  @override
  String get teamRefusalModelUnavailable => '所选模型不可用。';

  @override
  String get teamRefusalModelUnavailableNext => '请在团队设置 › 模型中另选一个模型。';

  @override
  String get teamRefusalAuthFailed => '模型的提供商未接受登录。';

  @override
  String get teamRefusalAuthFailedNext => '请检查提供商的密钥，然后再次批准规格。';

  @override
  String get teamRefusalCloneFailed => '团队无法复制项目来开展工作。';

  @override
  String get teamRefusalCloneFailedNext => '请检查项目的仓库，然后再次批准规格。';

  @override
  String get teamRefusalSessionUncertain => '团队不确定上次运行进行到哪一步。';

  @override
  String get teamRefusalSessionUncertainNext => '如有可能请恢复，或再次批准规格。';

  @override
  String get teamRefusalPlanInvalid => '返回的计划无法使用。';

  @override
  String get teamRefusalPlanInvalidNext => '请再次批准规格，团队会重新规划。';

  @override
  String get teamRefusalNeedsAnswer => '规划器有一个问题要问你。';

  @override
  String get teamRefusalNeedsAnswerNext => '请打开规格并答复。';

  @override
  String get teamRefusalRecoveryReview => '工作中途停止，需要查看。';

  @override
  String get teamRefusalRecoveryReviewNext => '请检查项目，然后再次批准规格。';

  @override
  String get teamRefusalAppStopped => '团队完成前应用已关闭。';

  @override
  String get teamRefusalAppStoppedNext => '请恢复以检查它进行到哪一步。';

  @override
  String get teamRefusalChatBusy => '团队正在等待你的对话完成回复。';

  @override
  String get teamRefusalChatBusyNext => '之后它会自行继续。';

  @override
  String get teamRefusalBudgetReached => '项目已达到支出上限。';

  @override
  String get teamRefusalBudgetReachedNext => '请在项目设置中提高上限以继续。';

  @override
  String get teamRefusalModelNotConfiguredAction => '选择规划器模型';

  @override
  String get teamProjectPlanFailedTitle => '计划未能生成';

  @override
  String get teamProjectApproveAgain => '再次批准规格';

  @override
  String get teamProjectApproveAgainNote => '再次批准规格以重试。';

  @override
  String get teamProjectTaskWorkLive => '目前为止的工作';

  @override
  String get teamProjectTaskWorkLog => '工作日志';

  @override
  String get teamRefusalDidPlan => '开始规划';

  @override
  String get teamRefusalDidQuick => '开始该任务';

  @override
  String get teamRefusalDidApprove => '开始工作';

  @override
  String get teamRefusalDidSpec => '批准规格';

  @override
  String get teamRefusalDidPromote => '提升工作';

  @override
  String get teamRefusalDidStop => '停止项目';

  @override
  String get teamRefusalDidPause => '暂停项目';

  @override
  String get teamRefusalDidResume => '恢复项目';

  @override
  String get teamRefusalDidSave => '保存你的更改';

  @override
  String teamRefusalUnknown(String action) {
    return '团队无法$action。';
  }

  @override
  String get teamRefusalUnknownNext => '请重试。若持续出现，请打开“详情”查看错误码。';

  @override
  String get teamRefusalCode => '错误码';

  @override
  String get phoneTeamProtectedProot => '由此手机的 Linux 沙箱保护';

  @override
  String get phoneTeamProtectedLandlock => '由 Android 的文件保护机制保护';

  @override
  String get teamRefusalRepositoryEmpty => '此仓库尚无任何提交。';

  @override
  String get teamRefusalRepositoryEmptyNext => '请先在仓库中做一次提交，然后重新开始规划。';

  @override
  String get teamRefusalRepositoryLink => '团队无法安全地复制此仓库。';

  @override
  String get teamRefusalRepositoryLinkNext => '请重试。若持续出现，请报告问题。';

  @override
  String get teamRefusalRepositoryDamaged => '仓库副本与原文不一致。';

  @override
  String get teamRefusalRepositoryDamagedNext => '请重试。若持续出现，请报告问题。';

  @override
  String get teamRefusalPlanTaskName => '计划中有任务没有名称。';

  @override
  String get teamRefusalPlanTaskNameNext => '请为每个任务命名，然后再次批准。';

  @override
  String get teamRefusalPlanPhase => '计划中有阶段不完整。';

  @override
  String get teamRefusalPlanPhaseNext => '请检查每个阶段都有任务和标准，然后再次批准。';

  @override
  String get teamServerPhoneFailed => '本机的 AI 团队没有应答，因此无法访问其工作。';

  @override
  String get teamServerPhoneNotReady => '本机的 AI 团队未就绪：其安全检查尚未通过。';

  @override
  String get teamServerPhoneNoAnswer => '本机的 OpenCode 未响应上次检查。';

  @override
  String get phoneTeamOffReview => '查看并选择时间';

  @override
  String teamProjectInterruptedRow(String name) {
    return '$name：工作中断，点按以恢复';
  }

  @override
  String get teamProjectResumeUnavailable =>
      '此版本的 AI 团队暂时无法恢复中断的工作。你可以停止项目后重新启动。';

  @override
  String get teamProjectInterrupted => '已中断，可以恢复';

  @override
  String get teamProjectEditorContextFilesHelpReal =>
      '可选。每行一个路径。团队会在规划前读取这些文件。';

  @override
  String get addServerCleartextWarning =>
      '此私有网络或 Tailscale 地址使用明文 HTTP。你的密码和验证码在 HTTP 层以未加密方式传输。请仅在可信的私有网络或通过 Tailscale 使用；在公共 Wi-Fi 上有风险。';

  @override
  String get addServerCleartextConfirm => '仍然使用';

  @override
  String get addServerCleartextConfirmed =>
      '已为此地址启用明文 HTTP。此网络中的任何人都可能读取你发送的内容。';

  @override
  String get storageAccessTitle => '允许访问文件？';

  @override
  String get storageAccessBody =>
      '此文件夹位于手机的共享存储中。除非你允许“所有文件访问权限”，否则 Android 会对应用隐藏其中的文件。';

  @override
  String get storageAccessWhyScope => '应用只会在你作为项目打开的文件夹中读取和修改文件。';

  @override
  String get storageAccessWhyAgent =>
      '智能体需要在你的文件夹中就地工作。没有该权限，你只能看到 .git 之类的隐藏项。';

  @override
  String get storageAccessWhyOff => '你随时可以在 Android 设置的“所有文件访问权限”中关闭它。';

  @override
  String get storageAccessAllow => '允许访问文件';

  @override
  String get storageAccessNotNow => '暂不';

  @override
  String get storageAccessUseAppSpace => '使用应用的项目空间';

  @override
  String get storageAccessRefusedTitle => '文件夹未打开';

  @override
  String get storageAccessRefusedBody =>
      '没有文件访问权限就无法显示此文件夹中的文件，因此它没有被打开。请允许访问，或在应用的项目空间中选择一个文件夹。';

  @override
  String get storageTermuxTitle => '允许 Termux 访问存储？';

  @override
  String get storageTermuxBody =>
      '你的服务器运行在 Termux 中，而 Termux 目前还无法读取手机的共享存储。缺少该权限时，此文件夹只会显示 .git 之类的隐藏项。';

  @override
  String get storageTermuxAllow => '打开 Termux';

  @override
  String get storageTermuxStillTitle => '在 Termux 中允许访问存储';

  @override
  String get storageTermuxStillBody =>
      '在 Termux 中，当 Android 询问时允许存储访问（命令为 termux-setup-storage），然后再次打开该文件夹。';

  @override
  String get filesAccessNeededTitle => '文件已隐藏';

  @override
  String get filesAccessNeededBody => '此文件夹位于手机的共享存储中，应用暂时无法读取其中大部分文件。只显示隐藏项。';

  @override
  String get filesAccessNeededTermuxBody =>
      '此文件夹位于手机的共享存储中，Termux 暂时无法读取其中大部分文件。只显示隐藏项。';

  @override
  String get storageRestartTitle => '重启以打开文件夹？';

  @override
  String get storageRestartBody => '此手机上的 OpenCode 需要重启后才能看到此文件夹。大约需要 30 秒。';

  @override
  String get storageRestartPause => '正在运行的回复和 AI Team 工作会暂停，之后继续。';

  @override
  String get storageRestartBusy => '当前有回复或 AI Team 任务正在运行。OpenCode 重启期间它会暂停。';

  @override
  String get storageRestartConfirm => '重启并打开';

  @override
  String get storageRestartFailedTitle => '重启未完成';

  @override
  String get storageRestartFailedBody => '文件夹未能打开。请重试，或从“此手机”启动 OpenCode。';

  @override
  String get kitQueuedChecking => '正在确认你的消息是否已送达…';

  @override
  String get kitQueuedUncertain => '我们无法确认你的消息是否已送达。';

  @override
  String kitQueuedLastChecked(String time) {
    return '上次检查：$time。';
  }

  @override
  String get kitQueuedStorageFull => '此手机目前无法安全地记录发送，因此你的消息没有发出。它仍保留在这里。';

  @override
  String get queuedReceiptConfirmed => '消息已收到。';

  @override
  String get queuedReceiptStillUncertain => '仍无法确认它已送达。没有重复发送任何内容。';

  @override
  String get queuedReceiptStorageProblem => '此手机目前无法安全记录或检查发送状态。请释放一些存储空间后重试。';

  @override
  String get queuedReceiptDetailSentAt => '发送时间';

  @override
  String get queuedReceiptDetailCommand => '命令 ID';

  @override
  String get queuedReceiptDetailReceipt => '回执 ID';

  @override
  String get queuedReceiptDetailNote => '连接在我们确认收到之前已中断。';

  @override
  String get kitToolRetry => '重试';

  @override
  String get folderBrowserPlaceLabel => '位置';

  @override
  String get folderBrowserPlaceProjects => '项目空间';

  @override
  String get folderBrowserPlacePhone => '此手机';

  @override
  String get folderBrowserInternalStorage => '内部存储';

  @override
  String get folderBrowserOpenedBefore => '之前打开过';

  @override
  String get folderBrowserShowHidden => '显示隐藏文件夹';

  @override
  String get folderBrowserHintEmpty => '空';

  @override
  String folderBrowserHintMore(String names, int count) {
    return '$names，另有 $count 个';
  }

  @override
  String get folderBrowserEmptyPhoneBody => '打开它，或返回上一级。';

  @override
  String folderBrowserNewProjectCreates(String path) {
    return '将创建 $path';
  }

  @override
  String get folderBrowserCreateAndOpen => '创建并打开';

  @override
  String get folderBrowserFoldersLabel => '文件夹';

  @override
  String get folderBrowserPhoneRefused => '手机存储已关闭。请允许访问以在此查看。';

  @override
  String get phoneScanTitle => '此手机上的项目';

  @override
  String phoneScanStopped(int seconds, int count) {
    return '$seconds 秒后已停止 · 找到 $count 个';
  }

  @override
  String phoneScanFirstShown(int count) {
    return '显示前 $count 个';
  }

  @override
  String get phoneScanLookDeeper => '深入查找';

  @override
  String get phoneScanEmptyTitle => '未找到项目';

  @override
  String get phoneScanEmptyBody => '浏览文件夹，或新建项目。';

  @override
  String get phoneScanGit => 'Git';

  @override
  String get phoneScanKindDart => 'Dart';

  @override
  String get phoneScanKindNode => 'Node';

  @override
  String get phoneScanKindPython => 'Python';

  @override
  String get phoneScanKindRust => 'Rust';

  @override
  String get phoneScanKindGo => 'Go';

  @override
  String get phoneScanKindJava => 'Java';

  @override
  String get phoneScanKindRuby => 'Ruby';

  @override
  String get phoneScanKindPhp => 'PHP';

  @override
  String get phoneScanKindDotnet => '.NET';

  @override
  String get phoneScanKindCpp => 'C/C++';

  @override
  String get phoneScanKindGit => '文件夹';

  @override
  String get openProjectTitle => '打开项目';

  @override
  String get openProjectSearchPhone => '在此手机上搜索';

  @override
  String get openProjectChooseFolder => '选择文件夹';

  @override
  String get openProjectChangeFolder => '更改文件夹';

  @override
  String openProjectUseFolder(String name) {
    return '使用 $name';
  }

  @override
  String get phoneScanSearching => '正在搜索此手机';

  @override
  String phoneScanChecked(int folders, int found) {
    String _temp0 = intl.Intl.pluralLogic(
      folders,
      locale: localeName,
      other: '$folders 个文件夹',
      one: '1 个文件夹',
    );
    return '已检查 $_temp0 · 找到 $found 个';
  }

  @override
  String phoneScanDoneIn(int count, String time) {
    return '$time 内找到 $count 个';
  }

  @override
  String get phoneScanStop => '停止';

  @override
  String get openProjectChange => '更改';

  @override
  String openProjectIn(String folder) {
    return '在 $folder 中';
  }

  @override
  String get effectsGlowStyle => '样式';

  @override
  String get effectsGlowStyleClassic => '经典';

  @override
  String get effectsGlowStyleSoft => '柔和光环';

  @override
  String get effectsGlowColours => '颜色';

  @override
  String get effectsGlowSpeed => '速度';

  @override
  String get approvalModeAskDetail => '每个请求都由你来回应。';

  @override
  String get approvalModeConfirmEverythingBody =>
      '此服务器上的智能体将在所有对话中不经询问地运行命令并修改文件。';

  @override
  String get approvalsSheetSubagents => '子智能体沿用此设置';

  @override
  String approvalsSheetSetByParent(String name) {
    return '由 $name 设定';
  }

  @override
  String get approvalsSheetSetByServer => '由此服务器设定';

  @override
  String get approvalsSheetFootnote => '应用断开连接时会停止。服务器的拒绝规则仍然有效。';

  @override
  String get approvalsSheetHistory => '已自动批准';

  @override
  String get chatsHomeTitle => '对话';

  @override
  String get chatsHomeAllProjects => '所有项目';

  @override
  String get chatsHomeNeedsYou => '需要你处理';

  @override
  String get chatsHomeRunning => '运行中';

  @override
  String get chatsHomeRowWorking => '进行中';

  @override
  String get chatsHomeStopped => 'Stopped';

  @override
  String get chatsHomeDone => '已完成';

  @override
  String get chatsHomeFailed => '已失败';

  @override
  String get chatsHomeToday => '今天';

  @override
  String get chatsHomeEarlier => '更早';

  @override
  String chatsHomeOnlyProject(String project) {
    return '仅显示 $project 中的对话。此服务器无法列出所有项目。';
  }

  @override
  String get chatsHomeIncomplete => '部分对话无法加载';

  @override
  String chatsHomeIncompleteNamed(String servers) {
    return '$servers couldn\'t load';
  }

  @override
  String shellServerPlusOthers(String name, int count) {
    return '$name +$count';
  }

  @override
  String get chatSubagentReadOnly => '这个子智能体只回应它的主对话，请在那里发送消息。';

  @override
  String get chatsHomeOpening => '正在打开…';

  @override
  String chatsHomeUnreachable(String servers) {
    return '$servers 没有响应，未显示它的对话。';
  }

  @override
  String get chatsSourcesUnreachable => '没有响应';

  @override
  String get chatsSourcesAll => '所有连接';

  @override
  String chatsSourcesSome(int shown, int total) {
    return '$total 个连接中的 $shown 个';
  }

  @override
  String get chatsSourcesSheetTitle => '此列表中的连接';

  @override
  String get chatsSourcesMain => '新对话从这里开始';

  @override
  String get chatsSourcesOnPhone => '在这部手机上';

  @override
  String get chatsSourcesElsewhere => '在另一台电脑上';

  @override
  String get chatsSourcesAgents => '这部手机上的智能体';

  @override
  String get chatsSourcesAlways => '始终显示';

  @override
  String chatsHomeStillLoading(String agents) {
    return '正在加载 $agents 的对话…';
  }

  @override
  String get chatsHomeEmptyTitle => '还没有对话';

  @override
  String get chatsHomeNoMatchTitle => '没有匹配的对话';

  @override
  String get chatsHomeNoMatchBody => '没有符合所选筛选条件的内容。';

  @override
  String get chatsHomeClearFilters => '清除筛选';

  @override
  String chatsHomeProjectEmptyTitle(String project) {
    return '$project 中没有对话';
  }

  @override
  String chatsHomeStartChatIn(String project) {
    return '在 $project 中开始对话';
  }

  @override
  String get chatsHomeNewChat => '新对话';

  @override
  String get chatsHomeOpenFailed => '无法打开这个对话。下拉可刷新列表。';

  @override
  String get chatsFilterSheetTitle => '显示以下来源的对话';

  @override
  String chatsFilterChatCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个对话',
    );
    return '$_temp0';
  }

  @override
  String chatsFilterRunningCount(int running) {
    return '$running 个运行中';
  }

  @override
  String get chatsFilterNeedsYouWord => '需要你处理';

  @override
  String get chatsFilterOpenProject => '打开项目…';

  @override
  String get chatsNewSeparateCopy => '在单独的副本中';

  @override
  String get agentPersonaClaudeCodeStrength => 'Careful with big changes';

  @override
  String get agentPersonaClaudeCodeAbout =>
      'Plans before it edits; good for larger changes across files.';

  @override
  String get agentPersonaClaudeCodeNeeds => 'A Claude plan or an Anthropic key';

  @override
  String get agentPersonaCodexStrength => 'Fast edits and reviews';

  @override
  String get agentPersonaCodexAbout =>
      'Quick at focused fixes and reviewing changes.';

  @override
  String get agentPersonaCodexNeeds => 'A ChatGPT plan or an OpenAI key';

  @override
  String get agentPersonaGeminiStrength => 'Reads a lot at once';

  @override
  String get agentPersonaGeminiAbout =>
      'Handles long files and wide questions well.';

  @override
  String get agentPersonaGeminiNeeds => 'A Google account or a Gemini key';

  @override
  String get agentPersonaKiloStrength => 'Your choice of provider';

  @override
  String get agentPersonaKiloAbout =>
      'One agent that works with many model providers.';

  @override
  String get agentPersonaKiloNeeds => 'A key from any supported provider';

  @override
  String get agentPersonaClineStrength => 'Step by step, asks first';

  @override
  String get agentPersonaClineAbout =>
      'Works in small steps and checks in with you.';

  @override
  String get agentPersonaClineNeeds => 'A key from any supported provider';

  @override
  String get agentPersonaOpenCodeStrength => 'Any model, free ones too';

  @override
  String get agentPersonaOpenCodeAbout =>
      'Works with any provider, and has free models to start.';

  @override
  String get agentPersonaOpenCodeNeeds =>
      'Nothing to start; add a key for more models';

  @override
  String get agentPersonaOtherName => 'Another agent';

  @override
  String get agentMakerAnthropic => 'by Anthropic';

  @override
  String get agentMakerOpenAI => 'by OpenAI';

  @override
  String get agentMakerGoogle => 'by Google';

  @override
  String get agentMakerOpenSource => 'open source';

  @override
  String chatsNewStartWith(String agent) {
    return 'Start with $agent';
  }

  @override
  String get chatsNewSetUp => 'Set up';

  @override
  String get chatsNewPrompt => '我们要做什么？';

  @override
  String get chatsNewChooseProject => '选择项目';

  @override
  String get chatsNewNeedProject => '请选择一个项目来开始对话。';

  @override
  String get chatsNewFailed => '对话没有开始。你的消息还在这里。';

  @override
  String get chatsNewFailedTitle => '对话未能开始';

  @override
  String get chatsNewFailedReportHint => '“报告问题”会包含这些详情。';

  @override
  String get shellTabChats => '对话';

  @override
  String get shellTabFiles => '文件';

  @override
  String get discoverChatsAliases =>
      '聊天 对话 会话 工作 首页 最近 已固定 新对话 收件箱 活动 需要你处理 审批 权限 提问 表单 等待中 运行中 已完成';

  @override
  String chatProjectChipSemantics(String name) {
    return '项目 $name。打开包含其文件、终端和更改的菜单。';
  }

  @override
  String chatProjectServerChipSemantics(String name, String server) {
    return '$server 上的项目 $name。打开包含其文件、终端和更改的菜单。';
  }

  @override
  String get chatProjectMenuLabel => '项目工具';

  @override
  String get agentsChooseTitle => '选择智能体';

  @override
  String get agentsStateReady => '就绪';

  @override
  String get agentsStateCantReopen => '无法重新打开旧对话';

  @override
  String agentsStateNotInstalled(String size) {
    return '未安装 · $size';
  }

  @override
  String get agentsStateNotInstalledNoSize => '未安装';

  @override
  String get agentsStateSignInNeeded => '需要登录';

  @override
  String get agentsStateChecking => '正在检查登录状态…';

  @override
  String get agentsStatePhoneCheck => '需要检查手机';

  @override
  String get agentsStateStopped => '已在后台停止';

  @override
  String get agentsStateLimit => '已达到套餐上限';

  @override
  String get agentsStateUnavailable => '这部手机上暂不可用';

  @override
  String agentsSetupTitle(String agent, String size) {
    return '设置 $agent · $size';
  }

  @override
  String agentsSetupTitleNoSize(String agent) {
    return '设置 $agent';
  }

  @override
  String agentsSetupBody(String agent) {
    return '$agent 在这部手机上运行。安装会下载该智能体及其所需内容。';
  }

  @override
  String agentsSetupSizeNote(String size) {
    return '$size 是智能体本身的大小。共享组件可能会额外占用空间。';
  }

  @override
  String agentsInstallAction(String agent) {
    return '安装 $agent';
  }

  @override
  String get agentsCancelSetup => '取消设置';

  @override
  String agentsInstalling(String agent) {
    return '正在安装 $agent…';
  }

  @override
  String get agentsSetupInterrupted => '设置在完成前中断。请重新安装以继续。';

  @override
  String get agentsSetupFailed => '设置未完成。请重新安装再试一次。';

  @override
  String get agentsCheckTitle => '检查此手机';

  @override
  String agentsCheckRunning(String agent) {
    return '正在检查 $agent…';
  }

  @override
  String agentsCheckPassed(String agent) {
    return '$agent 已在此手机上就绪。';
  }

  @override
  String agentsCheckFailed(String agent) {
    return '$agent 未通过检查。';
  }

  @override
  String agentsCheckAction(String agent) {
    return '检查 $agent';
  }

  @override
  String get agentsStepInstall => '已安装';

  @override
  String get agentsStepVersion => '版本';

  @override
  String get agentsStepConnection => '连接';

  @override
  String get agentsStepReady => '就绪';

  @override
  String get agentsFailUnavailable => '此手机暂时还不能运行该智能体。';

  @override
  String agentsResumeFailed(String agent) {
    return '$agent 未能重新启动。点击“恢复”再试一次。若仍无法启动，请运行它的手机检查。';
  }

  @override
  String get agentsFailStorage => '无法保存此设置。请释放一些空间后重新运行。';

  @override
  String get agentsFailInstall => '智能体未能安装。请重新安装。';

  @override
  String get agentsFailInterrupted => '设置在完成前中断。请重新安装。';

  @override
  String get agentsFailVersion => '已安装的智能体未通过版本检查。';

  @override
  String get agentsFailDaemon => '智能体连接未能启动。';

  @override
  String get agentsFailHello => 'Paseo 未确认连接。请重试以重新启动并再次检查。';

  @override
  String get agentsFailArchitecture => '此智能体与本手机的处理器不匹配。';

  @override
  String get agentsFailStale => '此次检查已过期。请重新运行。';

  @override
  String get agentsFailBusy => '另一项设置正在进行。请等待其完成。';

  @override
  String agentsSignInTitle(String agent) {
    return '使用 $agent 登录';
  }

  @override
  String agentsSignInIntro(String agent) {
    return '$agent 通过自带的提示完成登录，使用你自己的 Claude 账号并遵循 Anthropic 的条款。应用不会获取你的登录信息。';
  }

  @override
  String agentsSignInTerminalIntro(String agent) {
    return '$agent 会打开其登录页面。请在那里用你的 Claude 账号登录，然后返回。如果 $agent 要求输入代码，请在页面上复制，然后选择“粘贴”。';
  }

  @override
  String agentsSignInIntroOther(String agent) {
    return '$agent 通过自带的提示完成登录，使用你在其服务商的账号。应用不会获取你的登录信息。';
  }

  @override
  String agentsSignInTerminalIntroOther(String agent) {
    return '$agent 在此通过自带的提示登录。如果它打开页面，请在那里登录后返回。如果要求输入代码或密钥，请选择“粘贴”。';
  }

  @override
  String get agentsSignInAgain => '重新登录';

  @override
  String agentsSignInTerminalNotYet(String agent) {
    return '$agent 尚未登录。准备好后请重新开始登录。';
  }

  @override
  String get agentsSignInStopFailed => '无法停止登录。请保持应用打开并重试。';

  @override
  String get agentsSignInChecking => '正在检查登录状态…';

  @override
  String agentsSignInStart(String agent) {
    return '使用 $agent 登录';
  }

  @override
  String get agentsSignedIn => '已登录';

  @override
  String agentsSignedInBody(String agent) {
    return '$agent 已可开始新的对话。';
  }

  @override
  String agentsSignInDone(String agent) {
    return '使用 $agent';
  }

  @override
  String agentsSignedInAs(String account) {
    return '已登录为 $account';
  }

  @override
  String agentsSignOutAction(String agent) {
    return '退出 $agent 登录';
  }

  @override
  String agentsSignOutTitle(String agent) {
    return '退出 $agent 登录？';
  }

  @override
  String agentsSignOutBody(String agent) {
    return '在你重新登录之前，$agent 无法开始新的对话。';
  }

  @override
  String agentsSignOutKept(String agent) {
    return '你与 $agent 的对话会保留。';
  }

  @override
  String agentsSignOutFailed(String agent) {
    return '无法确认 $agent 已退出登录。请重试。';
  }

  @override
  String agentsRemoveAction(String agent) {
    return '移除 $agent';
  }

  @override
  String agentsRemoveTitle(String agent) {
    return '移除 $agent？';
  }

  @override
  String get agentsRemoveBody => '这会从此手机上移除已安装的智能体。你的账号和对话会保留，之后可以重新安装。';

  @override
  String agentsRemoving(String agent) {
    return '正在移除 $agent…';
  }

  @override
  String agentsRemoved(String agent, String size) {
    return '已移除 $agent。释放了 $size。';
  }

  @override
  String agentsAlreadyRemoved(String agent) {
    return '$agent 已被移除。';
  }

  @override
  String get agentsRemoveUnsupported => '此智能体无法在这里移除。';

  @override
  String get agentsRemoveBusy => '此智能体正在使用。请等其任务完成后重试。';

  @override
  String get agentsRemoveUnconfirmed => '无法确认该智能体已被移除。请检查此手机后重试。';

  @override
  String get agentsDone => '完成';

  @override
  String get agentsModelTitle => '选择模型';

  @override
  String get agentsModelDefault => '默认模型';

  @override
  String get agentsModelLoading => '正在读取模型…';

  @override
  String get agentsSignInBadCode => '该代码不完整。请从浏览器复制完整的代码（中间有一个 #）并重新粘贴。';

  @override
  String get agentsSignInFailed => '登录未完成。请重新开始。';

  @override
  String agentsSignInRejected(String agent) {
    return '$agent 未接受该代码。它可能已过期或已被使用。请点按“获取新代码”。';
  }

  @override
  String get agentsSignInHostDown => '智能体未在运行。请先恢复它，再登录。';

  @override
  String get agentsSignInUnavailable => '此手机上暂时还不能登录';

  @override
  String agentsSignInLimit(String agent) {
    return '$agent 的套餐额度已用完 · 请稍后再试';
  }

  @override
  String agentsLimitReset(String agent, String time) {
    return '$agent 的套餐额度已用完 · $time重置';
  }

  @override
  String agentsLimitUnknown(String agent) {
    return '已达到 $agent 的套餐上限 · 请稍后再试';
  }

  @override
  String agentsSignedOutLine(String agent) {
    return '$agent 已退出登录';
  }

  @override
  String agentsSignInAction(String agent) {
    return '登录 $agent';
  }

  @override
  String agentsStoppedLine(String agent) {
    return '$agent 已在后台停止';
  }

  @override
  String agentsResumeAction(String agent) {
    return '恢复 $agent';
  }

  @override
  String get agentsResumeNoticeTitle => '要开始新的对话吗？';

  @override
  String agentsResumeNoticeBody(String agent) {
    return '将开始新的对话。$agent 无法重新打开这个对话，它会保持原样。';
  }

  @override
  String get agentsStartNew => '开始新对话';

  @override
  String get agentsCancel => '取消';

  @override
  String get agentsRestartLine => '请关闭并重新打开应用，以完成登录信息的清除。';

  @override
  String get agentsRestartAction => '关闭应用';

  @override
  String get agentsSectionTitle => '智能体';

  @override
  String get agentsRunOnBuiltIn => '智能体在内置服务器上运行';

  @override
  String get agentsSwitchToBuiltIn => '切换到内置服务器';

  @override
  String get agentsChecking => '正在查找这部手机上的智能体…';

  @override
  String get agentsStateNeedsArm => '需要 64 位手机';

  @override
  String get agentsStateNoDownload => '尚无已验证的下载';

  @override
  String get agentsInstallHint => '安装';

  @override
  String get agentsActionFailed => '此手机上操作未成功。打开“详情”查看原因。';

  @override
  String get chatsHomeOtherFolders => '其他文件夹';

  @override
  String get agentsSignInPaste => '粘贴';

  @override
  String get chatRequestAnswerAllowed => '已允许';

  @override
  String get chatRequestAnswerRejected => '已拒绝';

  @override
  String agentCardAsks(String agent) {
    return '$agent 在询问';
  }

  @override
  String agentCardReports(String agent) {
    return '$agent 报告';
  }

  @override
  String agentCardAsksAnnouncement(String agent, String title) {
    return '$agent 在询问：$title';
  }

  @override
  String get agentCardSend => '发送';

  @override
  String get agentCardConfirmDefault => '是，继续';

  @override
  String get agentCardCancelDefault => '取消';

  @override
  String get agentCardChoosePrompt => '选择一个选项并发送。';

  @override
  String get agentCardChooseOneOrMore => '请至少选择一个选项发送。';

  @override
  String get agentCardFormFix => '请填写必填字段后再发送。';

  @override
  String get agentCardFieldRequired => '此字段为必填项。';

  @override
  String get agentCardFieldNumber => '请输入数字。';

  @override
  String agentCardFieldMin(String min) {
    return '请输入 $min 或更大的值。';
  }

  @override
  String agentCardFieldMax(String max) {
    return '请输入 $max 或更小的值。';
  }

  @override
  String get agentCardTextNote => '智能体可以看到此内容，它会被保存在对话中。';

  @override
  String get agentCardBusy => '智能体仍在工作。等它停下来后你可以作答。';

  @override
  String get agentCardSending => '正在发送你的回答…';

  @override
  String get agentCardUnknown => '暂时无法判断此卡片是否已回答。对话更新后它会同步。';

  @override
  String get agentCardDeliveryUnknown => '无法确认你的回答已送达。再次作答前请先查看对话。';

  @override
  String get agentCardFailed => '你的回答没有发出。未发送任何内容，你可以重试。';

  @override
  String get agentCardSent => '已发送';

  @override
  String get agentCardShow => '显示卡片';

  @override
  String get agentCardNotAnswered => '未回答';

  @override
  String get agentCardUnreadable => '无法显示此卡片';

  @override
  String get agentCardDetails => '详情';

  @override
  String get agentCardComposerHint => '或输入你的回答';

  @override
  String get agentCardDangerTitle => '发送此回答？';

  @override
  String get agentCardDangerBody => '你的回答会作为一条消息发给智能体。具体如何处理由智能体决定。';

  @override
  String get agentCardPhotoTake => '拍照';

  @override
  String get agentCardPhotoChoose => '选择照片';

  @override
  String agentCardPhotoRemove(int number) {
    return '移除照片 $number';
  }

  @override
  String agentCardPhotoName(int number) {
    return '照片 $number';
  }

  @override
  String agentCardPhotoCount(int count, int max) {
    return '$count / $max';
  }

  @override
  String get agentCardPhotoMax => '此卡片最多只能添加这些照片。';

  @override
  String get agentCardPhotoNone => '请添加照片后再发送。';

  @override
  String get agentCardPhotoUnavailable => '无法从此处添加照片。请改为输入回答。';

  @override
  String get agentCardPhotoTooLarge => '单张照片最大 10 MB，合计最大 20 MB。';

  @override
  String get agentCardChartBar => '柱状图';

  @override
  String get agentCardChartLine => '折线图';

  @override
  String agentCardChartSeries(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个系列',
      one: '1 个系列',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartPoints(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个数据点',
      one: '1 个数据点',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartHighest(String where, String value) {
    return '最高 $where：$value';
  }

  @override
  String get agentCardChartNone => '无数据';

  @override
  String agentCardTableLabel(int columns, int rows) {
    return '表格，$columns 列，$rows 行';
  }

  @override
  String get agentCardDiffFile => '文件';

  @override
  String get agentCardDiffAdded => '新增';

  @override
  String get agentCardDiffRemoved => '删除';

  @override
  String agentCardDiffMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '另有 $count 个文件',
      one: '另有 1 个文件',
    );
    return '$_temp0';
  }

  @override
  String agentCardProgressValue(int percent) {
    return '$percent%';
  }

  @override
  String get cardsFromAgentsTitle => '来自智能体的卡片';

  @override
  String get cardsFromAgentsSupporting => '智能体可以用卡片向你展示选项、表单和报告，并请求你拍照。';

  @override
  String get cardsStatusOff => '关闭';

  @override
  String get cardsStatusChecking => '正在检查此手机…';

  @override
  String cardsStatusOn(String agents) {
    return '已为 $agents 开启';
  }

  @override
  String cardsStatusPartial(String agents, String reason) {
    return '已为 $agents 开启。$reason';
  }

  @override
  String cardsStatusRestart(String agents) {
    return '重启 $agents 以完成开启。';
  }

  @override
  String cardsStatusUnavailable(String reason) {
    return '不可用。$reason';
  }

  @override
  String cardsStatusFailed(String reason) {
    return '无法开启。$reason';
  }

  @override
  String get cardsSettingFailed => '无法更改此设置。请重试。';

  @override
  String get cardsAgentClaude => 'Claude Code';

  @override
  String get cardsProblemUnsupportedHost => '此服务器上的智能体无法显示卡片。';

  @override
  String get cardsProblemRuntimeMissing => '卡片所需的工具尚未安装在此手机上。';

  @override
  String get cardsProblemNotQualified => '尚未验证卡片能否与这里的智能体配合使用。';

  @override
  String get cardsProblemPermissionDenied => '此手机不允许该更改。';

  @override
  String get cardsProblemConflict => '另一个配置已使用相同名称。未做任何更改。';

  @override
  String get cardsProblemInstallationFailed => '无法安装卡片。';

  @override
  String get cardsProblemRegistrationFailed => '无法将卡片告知智能体。';

  @override
  String get cardsProblemVerificationFailed => '卡片已安装，但未通过检查。';

  @override
  String get cardsProblemRemovalFailed => '无法完全移除卡片。请在重启智能体后重试。';

  @override
  String get cardsProblemStorageFailed => '手机无法保存该设置。';

  @override
  String get cardsProblemBusy => '手机正在处理其他事情。请稍后重试。';

  @override
  String get agentCardFieldRequiredHint => '必填';

  @override
  String get agentCardYourAnswer => '你的回答';

  @override
  String get agentCardDangerConfirm => '发送回答';

  @override
  String get agentsSignInOpenPage => '打开登录页面';

  @override
  String agentsSignInCopyCode(String code) {
    return '复制代码 $code';
  }

  @override
  String cardsProblemNotQualifiedFor(int count, String agents) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '尚未验证 $agents 能否使用卡片。',
      one: '尚未验证 $agents 能否使用卡片。',
    );
    return '$_temp0';
  }

  @override
  String cardsProblemRuntimeMissingFor(String agents) {
    return 'Install $agents on this phone first.';
  }

  @override
  String cardsProblemRegistrationFailedFor(String agents) {
    return '无法将卡片告知 $agents。';
  }

  @override
  String cardsProblemVerificationFailedFor(String agents) {
    return '已为 $agents 安装卡片，但未通过检查。';
  }

  @override
  String cardsProblemRemovalFailedFor(String agents) {
    return '无法从 $agents 完全移除卡片。请在重启后重试。';
  }

  @override
  String get crashReportsLabel => '崩溃报告';

  @override
  String get crashReportsSwitch => '在此手机上保存崩溃报告';

  @override
  String get crashReportsSwitchBody => '保存在此手机上，绝不会自动发送。';

  @override
  String get crashReportsUnavailable => '崩溃报告目前不可用。请重启应用后重试。';

  @override
  String get crashReportsFailed => '无法更新崩溃报告。请重启应用后重试。';

  @override
  String get crashReportsNone => '还没有崩溃报告';

  @override
  String get crashReportsNoneBody => '如果应用关闭或停止响应，这里会出现一条。';

  @override
  String get crashKindError => '应用遇到意外错误';

  @override
  String get crashKindScreen => '某个界面无法显示';

  @override
  String get crashKindClosed => '应用意外关闭';

  @override
  String get crashKindNotResponding => '应用停止响应';

  @override
  String get crashReportPreviewBody => '仅保存在此手机上。只有你自行将已保存的错误包含在问题报告中时，它才会被发送。';

  @override
  String get crashReportDetailSource => '来源';

  @override
  String get crashReportDetailCategory => '类别';

  @override
  String crashReportsDelete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除 $count 份已保存的崩溃报告',
      one: '删除 1 份已保存的崩溃报告',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsDeleteTitle => '删除已保存的崩溃报告？';

  @override
  String get crashReportsDeleteBody => '删除已保存的崩溃报告。崩溃报告保存功能保持开启。';

  @override
  String get crashReportsDeleteConfirm => '删除崩溃报告';

  @override
  String get crashReportsNote => '只保存问题的类型和发生时间，不含错误消息、对话或密码。最多保留最近 20 份报告。';

  @override
  String crashReportsOnClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '开启后将清除上方的 $count 条已保存错误。',
      one: '开启后将清除上方的已保存错误。',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsOffDeletes => '关闭后将删除已保存的崩溃报告。';

  @override
  String crashReportsOffDeletesAndClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '关闭后将删除已保存的崩溃报告，并清除上方的 $count 条已保存错误。',
      one: '关闭后将删除已保存的崩溃报告，并清除上方的已保存错误。',
    );
    return '$_temp0';
  }

  @override
  String crashReportsOffClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '关闭后将清除上方的 $count 条已保存错误。',
      one: '关闭后将清除上方的已保存错误。',
    );
    return '$_temp0';
  }

  @override
  String crashReportsDeleteBodyWithErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '删除崩溃报告并清除上方的 $count 条已保存错误。崩溃报告保存功能保持开启。',
      one: '删除崩溃报告并清除上方的已保存错误。崩溃报告保存功能保持开启。',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsClearedToo => '已保存的崩溃报告也会一并删除。';

  @override
  String get recentErrorScreen => '某个界面无法绘制';

  @override
  String get recentErrorConnection => '与服务器的实时连接已断开';

  @override
  String get recentErrorAndroidExit => 'Android 关闭了应用';

  @override
  String get recentErrorTemperature => '手机温度发生变化';

  @override
  String get recentErrorStartup => '应用启动时遇到问题';

  @override
  String get recentErrorReportStore => '无法打开已保存的问题报告';

  @override
  String get recentErrorGeneric => '出了点问题';

  @override
  String get chatUiToolBackgroundTaskFinished => '后台任务已完成';

  @override
  String get chatUiToolBackgroundTaskFailed => '后台任务失败';

  @override
  String get chatUiToolBackgroundTaskEnded => '后台任务已结束';

  @override
  String get chatUiToolBackgroundTaskStopped => '后台任务已停止';

  @override
  String chatUiToolMessageTo(String name) {
    return 'Message to $name';
  }

  @override
  String get chatUiToolMessageToMain => 'Message to the main conversation';

  @override
  String get chatUiToolMessageToEveryone => 'Message to the whole team';

  @override
  String get chatUiToolMessageToAgent => 'Message to an agent';

  @override
  String get toolCardMessageRecipientId => 'Recipient id';

  @override
  String chatUiToolBackgroundNamedFinished(String name) {
    return '$name finished';
  }

  @override
  String chatUiToolBackgroundNamedFailed(String name) {
    return '$name failed';
  }

  @override
  String chatUiToolBackgroundNamedStopped(String name) {
    return '$name stopped';
  }

  @override
  String chatUiToolBackgroundNamedEnded(String name) {
    return '$name ended';
  }

  @override
  String chatUiToolBackgroundNamedCutOff(String name) {
    return '$name stopped when the last conversation ended';
  }

  @override
  String get chatUiToolBackgroundTaskCutOff =>
      'Background task stopped when the last conversation ended';

  @override
  String get chatUiToolShowCard => '显示卡片';

  @override
  String get chatUiToolShowedCard => '展示了一张卡片';

  @override
  String get chatUiToolCardFailed => '无法显示此卡片';

  @override
  String get chatStallModelSlow => '模型响应比平时慢。请等待，或停止回复后重试。';

  @override
  String get chatStallHelperDown => '此手机上智能体的辅助程序已停止。请停止回复并重试。';

  @override
  String get chatStallConnectionLost => '与智能体的连接已断开。请停止回复，待连接恢复后重试。';

  @override
  String get chatStallConnectionUnchecked =>
      '已有一段时间没有任何返回，且无法检查连接。请等待，或停止回复后重试。';

  @override
  String connectionSwitchingTo(String target) {
    return '正在切换到 $target…';
  }

  @override
  String get shellServerSwitching => '正在切换…';

  @override
  String get agentsStateNotCertified => '此版本尚未认证';

  @override
  String get agentsChipSignIn => '登录';

  @override
  String get agentsChipResume => '恢复';

  @override
  String get agentsChipCheck => '检查';

  @override
  String agentNamesPair(String first, String second) {
    return '$first 与 $second';
  }

  @override
  String get diagnosticsExitHistoryTitle => '最近的应用退出记录';

  @override
  String get diagnosticsExitHistoryNote => 'Android 会记录应用每次关闭。此处内容不会自动发送。';

  @override
  String get diagnosticsExitHistoryUnsupported => '此手机不支持';

  @override
  String get diagnosticsExitHistoryUnsupportedBody => 'Android 11 及更高版本会保留此记录。';

  @override
  String get diagnosticsExitHistoryFailed => '无法读取最近的应用退出记录。请重试，或重新打开应用。';

  @override
  String get diagnosticsExitNormal => '应用已关闭';

  @override
  String get diagnosticsExitUpdate => '应用已更新';

  @override
  String get diagnosticsExitForceStop => '应用被停止';

  @override
  String get diagnosticsExitLowMemory => '手机内存不足';

  @override
  String get diagnosticsExitCrash => '应用意外停止';

  @override
  String get diagnosticsExitKilled => 'Android 结束了该应用';

  @override
  String get diagnosticsExitReasonCode => '原因代码';

  @override
  String get diagnosticsExitImportance => '重要性';

  @override
  String get diagnosticsExitSummary => '摘要';

  @override
  String get diagnosticsReportOpen => '分享已保存的崩溃报告';

  @override
  String get diagnosticsReportTitle => '预览崩溃报告';

  @override
  String get diagnosticsReportPrivacy =>
      '只包含错误类别和时间，不含消息或对话。在你点击“分享报告”之前，不会有任何内容离开此手机。';

  @override
  String diagnosticsReportSize(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 份报告 · $size',
      one: '1 份报告 · $size',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsReportShare => '分享报告';

  @override
  String get diagnosticsReportStale => '已保存的详情已变更。分享前请检查新报告。';

  @override
  String get diagnosticsReportShareFailed => '无法打开分享。请重试。';

  @override
  String get diagnosticsReportCaptureOff => '崩溃报告保存已关闭。请开启以保存以后的应用错误。';

  @override
  String get diagnosticsReportEmpty => '没有可分享的已保存崩溃报告。';

  @override
  String get diagnosticsUnavailable => '这些详情不可用。请重新打开应用后重试。';

  @override
  String backgroundPauseTimeLimit(String at) {
    return '后台连接已于 $at 暂停以节省电量。';
  }

  @override
  String get backgroundPauseRestricted => '后台连接已暂停：电池使用受限。';

  @override
  String backgroundPauseUserStopped(String at) {
    return '应用于 $at 关闭时，后台连接已停止。';
  }

  @override
  String backgroundPauseAppUpdated(String at) {
    return '应用于 $at 更新时，后台连接已停止。';
  }

  @override
  String get backgroundPauseInterrupted => '后台连接因未知原因停止。';

  @override
  String get backgroundPauseDeviceRestarted => '手机重启后，后台连接已停止。';

  @override
  String get backgroundPauseResume => '恢复后台连接';

  @override
  String get backgroundPauseResumeShort => '恢复';

  @override
  String get backgroundPauseResuming => '正在恢复后台连接…';

  @override
  String get backgroundPauseResumeFailed => '无法恢复。请打开“保持运行”设置。';

  @override
  String get backgroundPauseOpenKeepRunning => '打开“保持运行”设置';

  @override
  String get backgroundPauseResumed => '后台连接已恢复。';

  @override
  String get diagnosticsExitNoProblems => '最近没有异常关闭。';

  @override
  String diagnosticsExitRoutine(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 次常规关闭（更新、你主动关闭）',
      one: '1 次常规关闭（更新、你主动关闭）',
    );
    return '$_temp0';
  }

  @override
  String diagnosticsExitShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '显示全部 $count 项',
      one: '显示全部 1 项',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsExitUnknown => '关闭原因未知';

  @override
  String diagnosticsExitToday(String time) {
    return '今天 $time';
  }

  @override
  String diagnosticsExitYesterday(String time) {
    return '昨天 $time';
  }

  @override
  String get liveToolCommand => '正在运行命令…';

  @override
  String get liveToolEdit => '正在编辑文件…';

  @override
  String get liveToolRead => '正在读取文件…';

  @override
  String get liveToolSearch => '正在搜索文件…';

  @override
  String get liveToolWeb => '正在浏览网页…';

  @override
  String get liveToolSubagent => '正在运行子智能体…';

  @override
  String get liveToolPlan => '正在规划…';

  @override
  String get liveToolWorking => '正在处理…';

  @override
  String get liveToolCard => '正在显示卡片…';

  @override
  String get liveToolBackgroundTask => '正在检查后台任务…';

  @override
  String liveToolOther(String tool) {
    return '正在运行 $tool…';
  }

  @override
  String get e7LocaleUiJapanese => '日本語';

  @override
  String get e7LocaleUiChinese => '简体中文';

  @override
  String get e7LocaleUiSpanish => 'Español';

  @override
  String get e7LocaleUiPortuguese => 'Português (Brasil)';

  @override
  String get e7LocaleUiRussian => 'Русский';

  @override
  String connectorCardSuggests(String agent) {
    return '$agent 建议连接一个连接器';
  }

  @override
  String connectorCardAnnouncement(String agent, String name) {
    return '$agent 建议连接一个连接器：$name';
  }

  @override
  String get connectorCardRuns => '运行方式';

  @override
  String get connectorCardLasts => '有效时长';

  @override
  String get connectorCardLastsValue => '在此服务器运行期间';

  @override
  String get connectorCardRunsNode => '在此服务器上，使用 Node';

  @override
  String get connectorCardRunsPython => '在此服务器上，使用 Python';

  @override
  String get connectorCardRunsDocker => '在 Docker 中运行，应用无法启动';

  @override
  String get connectorCardRunsNone => '应用无法启动其中任何内容';

  @override
  String get connectorCardRunsHosted => '托管运行';

  @override
  String get connectorCardConnect => '连接';

  @override
  String get connectorCardConnecting => '正在连接…';

  @override
  String get connectorCardChecking => '正在检查连接…';

  @override
  String get connectorCardSignInNeeded => '此连接器需要你登录。';

  @override
  String get connectorCardSignIn => '登录';

  @override
  String get connectorCardOpenSignIn => '打开登录页面';

  @override
  String get connectorCardSignInWaiting => '正在等待你在浏览器中完成登录。';

  @override
  String get connectorCardManualCode => '请粘贴浏览器返回的网址或授权码以完成登录。';

  @override
  String get connectorCardCodeLabel => '返回网址或授权码';

  @override
  String get connectorCardCodeRequired => '请先粘贴返回网址或授权码。';

  @override
  String get connectorCardFinishSignIn => '完成登录';

  @override
  String get connectorCardCheckStatus => '刷新';

  @override
  String get connectorCardCancelSignIn => '取消登录';

  @override
  String get connectorCardCancelTitle => '取消登录？';

  @override
  String connectorCardCancelBody(String name) {
    return '$name 会停止等待，其已保存的登录信息会被清除。之后需要重新登录才能使用。';
  }

  @override
  String get connectorCardKeepWaiting => '继续等待';

  @override
  String get connectorCardConnected => '已连接';

  @override
  String get connectorCardToolsReady => '工具已就绪';

  @override
  String get connectorCardLoadedTools => '已加载工具。继续此对话即可使用它们。';

  @override
  String get connectorCardConnectedUnconfirmed => '已连接，但工具是否可用尚未确认。';

  @override
  String get connectorCardFailureUnavailable => '此智能体无法从对话中连接工具。';

  @override
  String get connectorCardFailureInvalidSuggestion => '此连接器建议不可用。请在“工具”中浏览连接器。';

  @override
  String get connectorCardFailureSetupRequired => '此连接器需要先在“工具”中完成设置才能连接。';

  @override
  String get connectorCardFailureNameConflict => '已存在同名连接器。请在“工具”中查看。';

  @override
  String get connectorCardFailureSourceChanged => '此对话的连接已变更。请重新打开连接器卡片。';

  @override
  String get connectorCardFailureConnectFailed => '无法确认连接。请先检查其状态再重试。';

  @override
  String get connectorCardFailureAuthenticationFailed => '无法确认登录。请先检查其状态再重试。';

  @override
  String get connectorCardFailureOauthUnavailable => '此连接器无法从对话中登录。';

  @override
  String get connectorCardFailureNotConnected => '连接器尚未连接。请在“工具”中检查其设置。';

  @override
  String get connectorCardOpenTools => '在“工具”中打开连接器';

  @override
  String get connectorCardTryAgain => '重试';

  @override
  String get connectorCardReopen => '重新打开卡片';

  @override
  String get chatUiToolSearchedConnectors => '搜索了连接器';

  @override
  String get chatUiConnectorSearchEmpty => '已加载的目录中没有找到连接器。';

  @override
  String get chatUiConnectorSearchNotLoaded => '暂时无法加载目录。请稍后再问。';

  @override
  String get chatUiConnectorSearchOff => '目录已关闭';

  @override
  String get chatUiConnectorSearchOffBody =>
      '连接器目录是一份公开的连接器清单。开启后会在下载该清单时把你的搜索词发送到公共注册表；不会建立任何连接。';

  @override
  String get chatUiConnectorSearchTurnOn => '开启连接器目录';

  @override
  String get chatUiConnectorSearchTurnedOn => '目录已开启。请让智能体重新搜索。';

  @override
  String get chatUiConnectorSearchTurnOnFailed => '无法开启目录。请重试。';

  @override
  String get chatUiConnectorSearchUnavailable => '连接器搜索不可用。请从“工具”中重试。';

  @override
  String get chatUiConnectorSearchNotConnected => '未连接';

  @override
  String get kitMarkdownImage => '图片';

  @override
  String get toolCardPlan => '计划';

  @override
  String get toolCardWorktreeSetup => '正在设置独立副本';

  @override
  String get toolCardWhatItDid => '它做了什么';

  @override
  String get toolCardTechnicalDetails => '技术详情';

  @override
  String toolCardPageStatus(String status) {
    return '页面返回 $status';
  }

  @override
  String toolCardAsked(String prompt) {
    return '询问：$prompt';
  }

  @override
  String get chatUiAgentNote => '注意';

  @override
  String get chatUiAgentWarning => '警告';

  @override
  String get chatUiAgentProblem => '问题';

  @override
  String get chatUiCompactedAutomatically => '已自动压缩以腾出空间。';

  @override
  String get chatUiCompactedWhenAsked => '已按你的要求压缩。';

  @override
  String chatUiCompactedFrom(String tokens) {
    return '原本约有 $tokens 个 token。';
  }

  @override
  String get chatRequestTitleFetch => '打开网页';

  @override
  String get chatRequestTitleWebSearch => '搜索网络';

  @override
  String get chatRequestTitleSearch => '搜索项目';

  @override
  String get chatRequestTitleTask => '启动辅助智能体';

  @override
  String get chatRequestTitleSkill => '使用技能';

  @override
  String get chatRequestTitleMode => '切换智能体的模式';

  @override
  String get chatRequestDetailRunsIn => '运行位置';

  @override
  String get chatRequestDetailAsks => '请求内容';

  @override
  String get chatRequestDetailRange => '文件的部分内容';

  @override
  String get chatRequestDetailHelper => '辅助程序类型';

  @override
  String chatRequestReadFrom(String line) {
    return '从第 $line 行起';
  }

  @override
  String chatRequestReadLines(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行',
      one: '1 行',
    );
    return '$_temp0';
  }

  @override
  String get toolCardWhatWasSent => '发送的内容';

  @override
  String get toolCardWhatCameBack => '返回的内容';

  @override
  String get kitRequestShowMore => '展开';

  @override
  String get kitRequestShowLess => '收起';

  @override
  String paseoCheckReady(String agents) {
    return '那台电脑上已就绪：$agents。';
  }

  @override
  String get paseoCheckNoneReady => '那台电脑上还没有可用的智能体。';

  @override
  String get serverRowDetailsVersion => '版本';

  @override
  String get capabilityDevServices => '开发服务器';

  @override
  String get capabilityDevServicesDetail => '启动项目的服务器或预览，并再次将其停止';

  @override
  String get capabilityWebSearch => '网络搜索';

  @override
  String get capabilityWebSearchDetail => '让智能体在网上查找资料';

  @override
  String get capabilityDeleteMessage => '删除消息';

  @override
  String get capabilityDeleteMessageDetail => '从对话中移除一条消息';

  @override
  String get capabilitySavedPermissions => '已保存的批准';

  @override
  String get capabilitySavedPermissionsDetail => '查看并移除你始终允许的操作';

  @override
  String get capabilityMcpAdd => '添加 MCP 服务器';

  @override
  String get capabilityMcpAddDetail => '在应用中添加 MCP 服务器或更改其设置';

  @override
  String get capabilityApprovals => '操作前询问';

  @override
  String get capabilityApprovalsDetail => '选择智能体是先请求批准还是自行操作';

  @override
  String get capabilitySteer => '工作中发送';

  @override
  String get capabilitySteerDetail => '智能体忙碌时可追加消息；它会在当前回合中读到';

  @override
  String get serverSettingsAuthPasswordSaved => '密码已保存在此手机上';

  @override
  String get serverSettingsAuthTokenSaved => '连接令牌已保存在此手机上';

  @override
  String get serverSettingsAuthTokenMissing => '未保存连接令牌';

  @override
  String connectionPasswordRejectedFor(String server) {
    return '$server 的密码已更改。请更新密码以重新连接。';
  }

  @override
  String connectionTokenRejectedFor(String server) {
    return '$server 拒绝了连接令牌。请更新令牌以重新连接。';
  }

  @override
  String serverSettingsEditTitle(String server) {
    return '编辑 $server';
  }

  @override
  String get serverSettingsEditDetail => '更改其名称、地址或登录方式';

  @override
  String serverSettingsRemoveTitle(String server) {
    return '移除 $server';
  }

  @override
  String get serverSettingsRecheckAgents => '再次检查智能体';

  @override
  String get serverSettingsRecheckAgentsDetail => '在那台电脑上登录智能体后使用此功能。';

  @override
  String get serverSettingsRecheckChecking => '正在检查智能体…';

  @override
  String get serverSettingsRecheckFailed => '无法检查智能体。请稍后重试。';

  @override
  String integrationsMcpDetails(String name) {
    return '$name 的详情';
  }

  @override
  String get usageBusiestDays => '最忙的日子';

  @override
  String usageDaySteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 步',
      one: '1 步',
    );
    return '$_temp0';
  }

  @override
  String modelPickerAgentUses(String model) {
    return '使用 $model';
  }

  @override
  String get sessionMenuArchive => '归档对话';

  @override
  String get sessionMenuArchiveHint => '将其移入“已归档”；服务器会保留它';

  @override
  String get sessionMenuDelete => '删除对话';

  @override
  String get sessionMenuDeleteHint => '永久移除它及其历史';

  @override
  String sessionArchiveTitle(String title) {
    return '归档“$title”？';
  }

  @override
  String get sessionArchiveBody => '它会移到“已归档”，你仍可在那里阅读。';

  @override
  String sessionDeleteTitle(String title) {
    return '删除“$title”？';
  }

  @override
  String get sessionDeleteBody => '对话及其历史记录将被永久删除，且无法撤销。';

  @override
  String monitorUnreachableTitle(String server) {
    return '无法连接 $server';
  }

  @override
  String get monitorUnreachableDetail => '它恢复后，相关问题会显示在这里。';

  @override
  String monitorUnreachableCheck(String server) {
    return '再次检查 $server';
  }

  @override
  String agentsStateLimitReset(String time) {
    return '套餐额度已用完 · $time重置';
  }

  @override
  String get exportMarkdownUnredacted => '记录未被遮蔽。它包含的正是你所看到的内容，包括对话中的密钥或私有路径。';

  @override
  String teamUiGateAnswerConfirmCancelNamedBody(String task) {
    return '“$task”将停止。其进行中的工作保持原状。';
  }

  @override
  String teamProjectStopNamedBody(String project) {
    return '“$project”将停止。正在运行的任务会停止，其工作和历史会保留。';
  }

  @override
  String teamProjectTaskStopNamedBody(String task) {
    return '“$task”将停止。其对话和更改会保留以供审查。';
  }

  @override
  String get teamAgentUnavailableState => '无法启动';

  @override
  String get teamAgentUnavailableModelWords => '其模型未在电脑上设置';

  @override
  String get teamAgentUnavailableOtherWords => '电脑目前无法运行它';

  @override
  String teamAgentUnavailableTitle(String agent) {
    return '$agent 无法启动';
  }

  @override
  String teamAgentUnavailableBody(String reason) {
    return '$reason。请在电脑上修复，然后再次检查。';
  }

  @override
  String get teamAgentUnavailableHostSays => '电脑的说明';

  @override
  String get teamControlForceStop => '强制停止';

  @override
  String get teamProjectControlSuspendWord => '暂停项目';

  @override
  String get teamProjectControlResumeWord => '恢复项目';

  @override
  String get teamProjectControlRemoveWord => '删除项目';

  @override
  String get teamJobTurnOnWord => '开启';

  @override
  String get teamJobTurnOffWord => '关闭';

  @override
  String get teamHomeProjectsTitle => '项目';

  @override
  String get teamRigStateActive => '活动';

  @override
  String get teamRigStatePaused => '已暂停';

  @override
  String teamRigPausedTitle(String project) {
    return '$project 已暂停';
  }

  @override
  String get teamRigPausedBody => '在你恢复之前，它不会启动新工作。';

  @override
  String teamRigPause(String project) {
    return '暂停项目 $project';
  }

  @override
  String teamRigResume(String project) {
    return '恢复项目 $project';
  }

  @override
  String teamRigPauseTitle(String project) {
    return '暂停 $project？';
  }

  @override
  String teamRigPauseBody(String project) {
    return '在你恢复之前，“$project”不会启动新工作。不会删除任何内容。';
  }

  @override
  String get teamRigPauseConfirm => '暂停项目';

  @override
  String teamRigDelete(String project) {
    return '删除项目 $project';
  }

  @override
  String teamRigDeleteTitle(String project) {
    return '删除 $project？';
  }

  @override
  String teamRigDeleteBody(String project) {
    return '“$project”将从团队中移除。电脑上的文件夹和文件不受影响。';
  }

  @override
  String get teamRigDeleteConfirm => '删除项目';

  @override
  String get teamRigDeleteLost => '团队将停止处理它';

  @override
  String get teamRigDeleteKeptFiles => '电脑上的文件夹和文件';

  @override
  String get teamRigDeleteKeptHistory => '过去的任务和历史';

  @override
  String get teamRigFolderLabel => '电脑上的文件夹';

  @override
  String get teamRigNotFoundTitle => '此项目已不存在';

  @override
  String get teamRigNotFoundBody => '电脑已不再列出它。请返回团队。';

  @override
  String teamProjectDelete(String project) {
    return '删除项目 $project';
  }

  @override
  String teamProjectDeleteTitle(String project) {
    return '删除 $project？';
  }

  @override
  String teamProjectDeleteBody(String project) {
    return '“$project”将连同团队对其代码的副本一起从本机的 AI 团队中删除。此操作无法撤销。';
  }

  @override
  String get teamProjectDeleteConfirm => '删除项目';

  @override
  String get teamProjectDeleteLostPlan => '其计划、任务和历史将被删除';

  @override
  String get teamProjectDeleteLostRunning => '正在运行的工作会停止';

  @override
  String get teamProjectDeleteKept => '你添加它时所用的文件夹保持原状';

  @override
  String teamAgentForceStop(String agent) {
    return '强制停止 $agent';
  }

  @override
  String teamAgentForceStopTitle(String agent) {
    return '强制停止 $agent？';
  }

  @override
  String teamAgentForceStopBody(String agent) {
    return '$agent 会立即结束，不会完成当前操作。它尚未保存的工作可能会丢失。';
  }

  @override
  String teamAgentStopStuckTitle(String agent) {
    return '$agent 未能停止';
  }

  @override
  String get teamAgentStopStuckBody => '常规停止未能结束它。你现在可以强制结束。';

  @override
  String get teamJobsTitle => '定时任务';

  @override
  String get teamJobsWholeTeamTitle => '整个团队';

  @override
  String get teamJobsEmptyTitle => '没有定时任务';

  @override
  String get teamJobsEmptyBody => '电脑不会为此项目自行启动任何内容。';

  @override
  String get teamJobsFailedTitle => '无法读取定时任务';

  @override
  String get teamJobsFailedBody => '请检查与电脑的连接，然后重试。';

  @override
  String get teamJobsLoading => '正在读取定时任务';

  @override
  String teamJobEveryDayAt(String time) {
    return '每天 $time';
  }

  @override
  String teamJobEveryWeekdayAt(String time) {
    return '每个工作日 $time';
  }

  @override
  String teamJobEveryDowAt(String day, String time) {
    return '每$day $time';
  }

  @override
  String get teamJobEveryHour => '每小时';

  @override
  String teamJobEveryMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '每 $count 分钟',
      one: '每分钟',
    );
    return '$_temp0';
  }

  @override
  String teamJobEveryHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '每 $count 小时',
      one: '每小时',
    );
    return '$_temp0';
  }

  @override
  String teamJobEverySeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '每 $count 秒',
      one: '每秒',
    );
    return '$_temp0';
  }

  @override
  String get teamJobOnSchedule => '按计划';

  @override
  String get teamJobRepeats => '自行重复';

  @override
  String get teamJobWhenEvent => '当某事发生时';

  @override
  String get teamJobManual => '仅手动启动';

  @override
  String get teamJobWebhook => '当收到 Web 请求时';

  @override
  String get teamJobCondition => '当其检查通过时';

  @override
  String get teamJobOther => '自行启动';

  @override
  String get agentFeaturesLabel => '智能体设置';

  @override
  String agentFeaturesSheetTitle(String agent) {
    return '$agent 设置';
  }

  @override
  String agentFeatureChipOff(String name) {
    return '$name，已关闭';
  }

  @override
  String agentFeatureChipOn(String name) {
    return '$name，已开启';
  }

  @override
  String get agentFeatureFastChip => '快速';

  @override
  String get agentFeaturesLoadFailed => '无法加载此智能体的设置。';

  @override
  String get agentFeatureFastTitle => '快速模式';

  @override
  String get agentFeatureFastDetail => '支持的模型回复更快，但费用更高。';

  @override
  String get agentFeatureSaving => '正在保存…';

  @override
  String agentFeatureChangeFailed(String name) {
    return '无法更改 $name。它将保持原有设置。';
  }

  @override
  String get claudeImportTitle => '从 Claude Code 导入';

  @override
  String claudeImportIntro(String project) {
    return '选择你在 $project 的 Claude Code 中开始的一个对话。它会在此打开，方便你继续。';
  }

  @override
  String claudeImportRowDetail(String project, String when) {
    return '$project · $when';
  }

  @override
  String get claudeImportUntitled => '未命名对话';

  @override
  String get claudeImportEmptyTitle => '没有可导入的内容';

  @override
  String get claudeImportEmptyBody => '你在该项目 Claude Code 中开始的对话会显示在这里。';

  @override
  String get claudeImportLoadFailedTitle => '无法查看';

  @override
  String get claudeImportLoadFailedBody => '列表未能加载。请检查连接后重试。';

  @override
  String claudeImportAlready(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '另有 $count 个对话已在这里。',
      one: '另有 1 个对话已在这里。',
    );
    return '$_temp0';
  }

  @override
  String get claudeImportWorking => '正在导入…';

  @override
  String get claudeImportFailed => '该对话无法导入。请重试。';

  @override
  String hostUpdateRow(String computer) {
    return '更新 $computer 上的智能体';
  }

  @override
  String hostUpdateRowRunning(String version) {
    return '正在运行 $version。将安装最新的智能体软件。';
  }

  @override
  String get hostUpdateRowUnknown => '将在那台电脑上安装最新的智能体软件。';

  @override
  String get hostUpdateConfirmTitle => '更新智能体？';

  @override
  String hostUpdateConfirmBody(String computer) {
    return '这会在 $computer 上安装最新的智能体软件并重启它。';
  }

  @override
  String hostUpdateConfirmPauses(String computer) {
    return '$computer 上的对话会在重启期间暂停。';
  }

  @override
  String get hostUpdateConfirmKept => '你的对话和设置保持原样。';

  @override
  String get hostUpdateConfirmAction => '更新智能体';

  @override
  String get hostUpdateStarting => '正在开始更新…';

  @override
  String get hostUpdateDownloading => '正在下载最新版本…';

  @override
  String get hostUpdateInstalling => '正在安装…';

  @override
  String get hostUpdateFinishing => '正在收尾…';

  @override
  String hostUpdateDone(String version) {
    return '已更新到 $version。应用会自动重新连接。';
  }

  @override
  String get hostUpdateDoneNoVersion => '已更新。应用会自动重新连接。';

  @override
  String hostUpdateCurrent(String computer) {
    return '$computer 已是最新版本。';
  }

  @override
  String get hostUpdateFailed => '更新未完成。请重试，或在那台电脑上更新。';

  @override
  String get hostUpdateDropped => '更新期间连接中断。应用正在重新连接。';

  @override
  String get hostUpdateDetailsRow => '失败原因';

  @override
  String get hostUpdateDetailsTitle => '更新详情';

  @override
  String get hostUpdateDetailsMessage => '来自那台电脑的消息';

  @override
  String get filesTextSurface => '文件内文本';

  @override
  String get filesSearchText => '在文件中搜索文本';

  @override
  String filesSearchInside(String query) {
    return '在文件内容中搜索“$query”';
  }

  @override
  String get filesTextHintTitle => '在文件内搜索';

  @override
  String get filesTextHintBody => '输入单词或短语，查看包含它的每一行。';

  @override
  String get filesNoTextTitle => '无匹配项';

  @override
  String get filesNoTextBody => '没有文件包含该文本。请检查拼写，或减少关键词。';

  @override
  String get filesTextFailedTitle => '无法搜索文件';

  @override
  String filesTextLimited(int count) {
    return '显示前 $count 个匹配项。输入更多内容可缩小范围。';
  }

  @override
  String filesTextMatchLine(int line) {
    return '第 $line 行';
  }

  @override
  String get productErrorComputer => '无法连接到你的电脑。请确认它已开机并联网，然后重试。';

  @override
  String get productErrorProjectGit =>
      '无法读取此项目的 Git 历史，因此对话无法开始。请从备份恢复项目，或重新克隆。';

  @override
  String kitGalleryPosition(int current, int total) {
    return '$current / $total';
  }

  @override
  String kitGalleryPictureLabel(int current, int total, String name) {
    return '第 $current 张图片，共 $total 张：$name';
  }

  @override
  String get kitGalleryEdit => '编辑';

  @override
  String get kitGallerySend => '发送';

  @override
  String get kitGalleryLoading => '正在加载图片';

  @override
  String get kitGalleryLoadFailed => '无法加载此图片';

  @override
  String get kitGalleryLoadFailedBody => '请检查连接后重试。';

  @override
  String get kitGalleryTryAgain => '重试';

  @override
  String get kitMarkupTitle => '编辑图片';

  @override
  String get kitMarkupColours => '画笔颜色';

  @override
  String get kitMarkupRed => '红色';

  @override
  String get kitMarkupYellow => '黄色';

  @override
  String get kitMarkupWhite => '白色';

  @override
  String get kitMarkupBlack => '黑色';

  @override
  String get kitMarkupUndo => '撤销';

  @override
  String get kitMarkupClear => '清除';

  @override
  String get kitMarkupCrop => '裁剪';

  @override
  String get kitMarkupApply => '应用';

  @override
  String get kitMarkupCancel => '取消';

  @override
  String get kitMarkupDone => '完成';

  @override
  String get kitMarkupCanvas => '正在编辑的图片。用手指在上面绘制。';

  @override
  String get kitMarkupCropHandle => '裁剪角。拖动可调整大小。';

  @override
  String get kitMarkupCropArea => '裁剪区域。拖动可移动。';

  @override
  String get kitMarkupFailed => '无法生成编辑后的图片';

  @override
  String get kitMarkupFailedBody => '请重试，或返回并发送原图。';

  @override
  String kitContextRingSemantics(String percent) {
    return '已使用上下文 $percent %';
  }

  @override
  String get kitContextRingTapHint => '显示上下文详情';

  @override
  String get kitContextRingCompacting => '正在压缩';

  @override
  String get kitContextRingCompactingSemantics => '上下文，正在压缩';

  @override
  String get kitContextSheetTitle => '上下文窗口';

  @override
  String kitContextSheetTokens(String used, String limit) {
    return '$used / $limit tokens';
  }

  @override
  String get kitContextSheetModel => '模型';

  @override
  String get kitContextSheetWindow => '上下文窗口';

  @override
  String get kitContextSheetMeasured => '已测量';

  @override
  String get kitContextSheetMeasuredValue => '上次回复后';

  @override
  String get kitContextSheetExplain =>
      '上下文窗口指模型一次能读入多少对话内容：你的消息、它的回复、文件和工具结果。填满后，较早的部分必须被摘要或省略，因此在此之前先压缩能让回答更精准。';

  @override
  String get kitContextSheetChartTitle => '每次回复';

  @override
  String kitContextChartSemantics(String count, String percent) {
    return '$count 次回复后的上下文，最新一次为 $percent %';
  }

  @override
  String get kitContextChartCompacted => '在此处压缩';

  @override
  String get kitContextSheetCompactNow => '立即压缩';

  @override
  String get phraseChipsRowLabel => '快捷短语';

  @override
  String phraseChipsSend(String phrase) {
    return '发送：$phrase';
  }

  @override
  String get phraseChipsMenuLabel => '短语选项';

  @override
  String get phraseChipsEdit => '编辑';

  @override
  String get phraseChipsHide => '隐藏';

  @override
  String get settingsPhraseChipsTitle => '短语建议';

  @override
  String get settingsPhraseChipsSupporting =>
      '你的常用短语，显示在消息框上方。根据你在此手机上发送的内容自动学习。';

  @override
  String get settingsPhraseChipsShowHidden => '显示已隐藏的短语';

  @override
  String settingsPhraseChipsHiddenCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条已隐藏的短语',
      one: '1 条已隐藏的短语',
    );
    return '$_temp0';
  }

  @override
  String get kitFactToldWord => '你告诉它的';

  @override
  String get kitFactSeenWord => '来自某个来源';

  @override
  String get kitFactGuessedWord => '它推测的';

  @override
  String get kitFactCalculatedWord => '推算得出';

  @override
  String kitFactToldLine(String date) {
    return '你告诉它的 · $date';
  }

  @override
  String kitFactSeenLine(String source, String date) {
    return '来自 $source · $date';
  }

  @override
  String kitFactSeenNoSourceLine(String date) {
    return '来自某个来源 · $date';
  }

  @override
  String kitFactGuessedLine(String date) {
    return '它推测的 · $date';
  }

  @override
  String kitFactGuessedFromLine(String date, String source) {
    return '它推测的 · $date · 依据 $source';
  }

  @override
  String kitFactCalculatedLine(String date) {
    return '推算得出 · $date';
  }

  @override
  String kitFactWaitingLine(String line) {
    return '$line · 等待你确认';
  }

  @override
  String get fbSourceChat => '你保存的一段对话';

  @override
  String get fbSourceShare => '你分享的文字';

  @override
  String get fbSourceNote => '一条笔记';

  @override
  String get fbSourceImport => '粘贴的文字';

  @override
  String get fbSourceStatement => '一条陈述';

  @override
  String get fbSourceOther => '另一个来源';

  @override
  String get fbKindPerson => '人物';

  @override
  String get fbKindDocument => '文档';

  @override
  String get fbKindSubscription => '订阅';

  @override
  String get fbKindWarranty => '保修';

  @override
  String get fbKindBill => '账单';

  @override
  String get fbKindCard => '卡片';

  @override
  String get fbKindAccount => '账户';

  @override
  String get fbKindOrganization => '组织';

  @override
  String get fbKindPlace => '地点';

  @override
  String get fbKindProject => '项目';

  @override
  String get fbKindTopic => '主题';

  @override
  String get fbKindEvent => '事件';

  @override
  String get fbKindOther => '事物';

  @override
  String get fbMemoryTitle => '记忆';

  @override
  String fbMemoryFromFactsBook(int count) {
    return '$count 项待处理';
  }

  @override
  String get fbMemoryFromFactsBookNone => '全部处理完毕';

  @override
  String fbMemoryReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项更改待审查',
      one: '1 项更改待审查',
    );
    return '$_temp0';
  }

  @override
  String fbMemoryNotSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条笔记尚未保存',
      one: '1 条笔记尚未保存',
    );
    return '$_temp0';
  }

  @override
  String get fbMemoryNotSavedDetail => '保存在本机';

  @override
  String get fbMemoryReviewDetail => '需要你确认';

  @override
  String get fbMemoryRecent => '最近加入记忆';

  @override
  String get fbMemoryOpenAboutYou => '打开“关于你”';

  @override
  String get fbMemoryOpenAboutYouDetail => '搜索、浏览并查看待处理的内容';

  @override
  String get fbMemoryAddSomething => '添加内容';

  @override
  String get fbMemoryAddSomethingDetail => '一条笔记或粘贴的文字';

  @override
  String get fbAboutYouTitle => '关于你';

  @override
  String get fbStatusStale => '正在显示上次保存的内容';

  @override
  String get fbSearchLabel => '搜索你的记忆';

  @override
  String get fbEmptyTitle => '这里还是空的';

  @override
  String get fbEmptyBody => '添加一条笔记、从其他应用分享文字，或从其他助手粘贴文字。你添加的内容会连同来源一起显示在这里。';

  @override
  String get fbListAll => '全部';

  @override
  String get fbListCards => '卡片';

  @override
  String get fbListPeople => '人物';

  @override
  String get fbListDocuments => '文档';

  @override
  String get fbListLockedNote => '锁定的笔记不会显示。用指纹或 PIN 解锁后可一并纳入。';

  @override
  String fbKindLine(String kind, String detail) {
    return '$kind · $detail';
  }

  @override
  String get fbAreaMoney => '财务分区';

  @override
  String get fbAreaHealth => '健康分区';

  @override
  String fbAreaOther(String area) {
    return '$area分区';
  }

  @override
  String get fbErrorTitle => '记忆没有响应';

  @override
  String get fbErrorUnreachable => '无法连接记忆。你添加的内容会保留在本机，直到能够保存。';

  @override
  String get fbErrorLocked => '此内容在锁定文件夹中。请先解锁。';

  @override
  String get fbErrorNotFound => '记忆中已没有该内容。';

  @override
  String get fbErrorGeneric => '出了点问题。请重试。';

  @override
  String fbErrorRequest(String id) {
    return '请求 $id';
  }

  @override
  String get fbSearchTitle => '搜索';

  @override
  String fbSearchMatches(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条匹配“$query”',
      one: '1 条匹配“$query”',
      zero: '没有匹配“$query”的结果',
    );
    return '$_temp0';
  }

  @override
  String get fbSearchWordsOnly => '按字词匹配你的笔记，不按语义搜索。';

  @override
  String get fbSearchEachHit => '每条结果都会显示来源和时间。来源也可能有误。';

  @override
  String get fbSearchLocked => '锁定笔记在解锁前不会被搜索。';

  @override
  String get fbSearchNoneTitle => '没有匹配项';

  @override
  String get fbSearchNoneBody => '换个词试试。';

  @override
  String get fbThingFacts => '事实';

  @override
  String get fbThingNoFacts => '尚无事实。';

  @override
  String get fbThingLocked => '锁定文件夹';

  @override
  String get fbCaptureTitle => '添加内容';

  @override
  String get fbCaptureKindLabel => '你要添加什么';

  @override
  String get fbCaptureNote => '笔记';

  @override
  String get fbCapturePaste => '粘贴';

  @override
  String get fbCaptureNoteLabel => '要记住什么？';

  @override
  String get fbCaptureNoteHint => '下周三 10 点看牙医。带上保险卡。';

  @override
  String get fbCapturePasteLabel => '粘贴来自其他应用或助手的文字';

  @override
  String get fbCaptureSave => '保存';

  @override
  String get fbCaptureFootnote => '从其他应用分享的内容也会落到这里。暂时无法保存的笔记会留在“尚未保存”中，不会消失。';

  @override
  String get fbCaptureSaved => '已保存。记忆会检查其中的变化，供你审查。';

  @override
  String fbCaptureSavedReminder(String when) {
    return '已保存，并为 $when 设置了提醒。';
  }

  @override
  String get fbCaptureKept => '暂时无法保存。内容已保留在本机。';

  @override
  String get fbCaptureSeeNotSaved => '查看“尚未保存”';

  @override
  String get fbReminderTitle => '检查此提醒';

  @override
  String fbReminderWrote(String text) {
    return '你写的是：“$text”';
  }

  @override
  String get fbReminderDate => '日期';

  @override
  String get fbReminderTime => '时间';

  @override
  String get fbReminderZone => '时区';

  @override
  String get fbReminderZoneHome => '居住地时区';

  @override
  String get fbReminderZoneHomeDetail => '无论你在哪里，都按你所在时区的这个时间响铃。';

  @override
  String get fbReminderZoneFloating => '本机时间';

  @override
  String get fbReminderZoneFloatingDetail => '无论你在哪里，都保持同一钟点时间。';

  @override
  String get fbReminderLocale => '日期语言';

  @override
  String get fbReminderRepeat => '重复';

  @override
  String get fbRepeatNever => '从不';

  @override
  String get fbRepeatDaily => '每天';

  @override
  String get fbRepeatWeekly => '每周';

  @override
  String get fbRepeatMonthly => '每月';

  @override
  String get fbRepeatYearly => '每年';

  @override
  String get fbReminderAmbiguousDate => '我无法确定日期。请检查。';

  @override
  String get fbReminderAmbiguousTime => '我无法确定时间。请检查。';

  @override
  String get fbReminderHelp => '保存前请修改不正确的地方。';

  @override
  String get fbReminderSave => '保存提醒';

  @override
  String get fbReminderChange => '修改我写的内容';

  @override
  String get fbReminderWhat => '提醒我';

  @override
  String get fbNeedsYouTitle => '需要你处理';

  @override
  String fbNeedsYouWaiting(int count) {
    return '$count 项待处理';
  }

  @override
  String get fbNeedsYouNone => '现在没有需要你处理的内容。';

  @override
  String get fbNeedsYouNoneBody => '问题、待审查的更改和尚未保存的笔记会显示在这里。';

  @override
  String get fbNotSavedTitle => '尚未保存';

  @override
  String fbNotSavedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项保留在本机',
      one: '1 项保留在本机',
    );
    return '$_temp0';
  }

  @override
  String get fbNotSavedIntro => '在你丢弃之前会一直保留：或留在本机，或由记忆保存。目前都还不在你的云端。';

  @override
  String get fbNotSavedFooter => '它们会一直留在这里，直到你丢弃。满 30 天时记忆会询问：重试、导出还是丢弃。';

  @override
  String get fbNotSavedEmptyTitle => '全部已保存';

  @override
  String get fbNotSavedEmptyBody => '暂时无法保存的笔记会在这里等待。';

  @override
  String get fbNotSavedTryNow => '立即重试';

  @override
  String get fbNotSavedCopy => '复制文字';

  @override
  String get fbNotSavedDiscard => '丢弃笔记';

  @override
  String get fbNotSavedRetry => '重试';

  @override
  String get fbNotSavedExport => '导出';

  @override
  String get fbNotSavedThirtyDays => '此内容已保留 30 天。请重试、导出文字或丢弃。';

  @override
  String get fbNotSavedDiscardTitle => '丢弃这条笔记？';

  @override
  String get fbNotSavedDiscardBody => '它从未保存过。丢弃会将其从本机移除。';

  @override
  String get fbNotSavedSaved => '已保存。';

  @override
  String get fbNotSavedStill => '仍未保存。它会留在这里。';

  @override
  String fbNotSavedSince(String when) {
    return '自 $when 起';
  }

  @override
  String get fbReasonUnreachable => '无法连接记忆。';

  @override
  String get fbReasonStorageFull => '本机存储已满。请腾出空间后重试。';

  @override
  String get fbReasonCloudQuota => '你的云端已满。请腾出空间后重试。';

  @override
  String get fbReasonKeyLocked => '锁定文件夹处于锁定状态。请解锁后重试。';

  @override
  String get fbReasonRejected => '记忆无法接受该内容。';

  @override
  String get fbReasonClassification => '记忆暂时无法归类。请重试。';

  @override
  String get fbDismiss => '忽略';

  @override
  String get fbCaptureNeedsText => '请先写点内容。';

  @override
  String get fbAreaFamily => '家庭分区';

  @override
  String get fbAreaWork => '工作分区';

  @override
  String get fbAreaNameMoney => '财务';

  @override
  String get fbAreaNameHealth => '健康';

  @override
  String get fbAreaNameFamily => '家庭';

  @override
  String get fbAreaNameWork => '工作';

  @override
  String get fbAreasTitle => '分区与分享';

  @override
  String get fbAreasIntro => '每个分区有自己的规则。只有你允许，其他应用和智能体才能读取某个分区。';

  @override
  String get fbAreasOthersRead => '其他应用和智能体可以读取';

  @override
  String get fbAreasOthersReadDetail => '关闭后此分区仅你可见。';

  @override
  String get fbAreasLockedFolder => '锁定文件夹';

  @override
  String get fbAreasNormalStorage => '普通存储';

  @override
  String get fbAreasReviewFirst => '新事实需先经你确认。';

  @override
  String get fbAreasReviewAuto => '仅自动进行安全的清理。';

  @override
  String get fbAreasFixed => '这些分区是内置的。';

  @override
  String fbAreasShareConfirmTitle(String area) {
    return '允许他人读取$area？';
  }

  @override
  String get fbAreasShareConfirmBody => '你配对过的应用和智能体将能读取此分区中的内容。你可以随时关闭。';

  @override
  String get fbAreasShareConfirm => '允许读取';

  @override
  String get fbCaptureFull => '本机队列已满，笔记未能保留。你的文字仍在这里。请先发送或丢弃一条待处理的笔记。';

  @override
  String get fbCaptureQueuedNode => '记忆会保留它，直到能够保存。';

  @override
  String get fbCaptureReminderFailed => '提醒未能创建。请从笔记中重试。';

  @override
  String fbCareWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项等待你确认',
      one: '1 项等待你确认',
    );
    return '$_temp0';
  }

  @override
  String get fbCareApprove => '批准';

  @override
  String get fbCareReject => '拒绝';

  @override
  String get fbCareApproved => '已批准。';

  @override
  String get fbCareRejected => '已拒绝。不会再提出。';

  @override
  String get fbCareSkipped => '已跳过。它会留在这里。';

  @override
  String get fbCareStale => '提出后内容已变化，因此未应用任何改动。它已移出此列表。';

  @override
  String get fbCareNeedsOwner => '本机暂时无法批准更改。请先设置批准方式以继续。';

  @override
  String get fbCareNeedsOwnerShort => '请先设置批准方式';

  @override
  String get fbCareSetupApproval => '设置批准方式';

  @override
  String get fbCareEditTitle => '批准前编辑';

  @override
  String get fbCareEditLabel => '事实';

  @override
  String get fbCareEditSave => '按我的编辑批准';

  @override
  String get fbCareClassFactAdd => '添加一条事实';

  @override
  String get fbCareClassFactUpdate => '更改一条事实';

  @override
  String get fbCareClassFactSupersede => '替换一条事实';

  @override
  String get fbCareClassMerge => '合并两项内容';

  @override
  String get fbCareClassLink => '关联两项内容';

  @override
  String get fbCareClassArchive => '归档';

  @override
  String get fbCareClassForget => '遗忘';

  @override
  String get fbCareClassWatch => '关注某件事';

  @override
  String get fbCareClassReminder => '提醒';

  @override
  String get fbCareClassImport => '来自粘贴的文字';

  @override
  String get fbCareClassPromotion => '针对更严格分区的事实';

  @override
  String get fbCareRiskMoney => '财务';

  @override
  String get fbCareRiskHealth => '健康';

  @override
  String get fbCareRiskId => '身份';

  @override
  String get fbCareRiskContradiction => '与你之前告诉它的内容矛盾';

  @override
  String get fbCareRiskLowConfidence => '不确定';

  @override
  String get fbConnectTitle => '连接到我的服务器';

  @override
  String get fbConnectRow => '设置记忆';

  @override
  String get fbConnectRowDetail => '你的笔记，保存在本机';

  @override
  String get fbConnectIntro => '将此应用连接到家庭网络、tailnet 或本机上的记忆服务器。';

  @override
  String get fbConnectAddress => '节点地址';

  @override
  String get fbConnectAddressHint => '127.0.0.1:8787';

  @override
  String get fbConnectCode => '一次性配对码';

  @override
  String get fbConnectCodeHelp => '运行配对命令时节点会打印该码。仅可使用一次，有效期五分钟。';

  @override
  String get fbConnectOwnerTitle => '批准更改（可选）';

  @override
  String get fbConnectOwnerBody =>
      '审查提案和更改分享设置都使用本机自己的密钥签名。请把下面的公钥交给节点，然后输入它返回的所有者设备 ID。';

  @override
  String get fbConnectOwnerKey => '本机公钥';

  @override
  String get fbConnectOwnerId => '所有者设备 ID';

  @override
  String get fbConnectButton => '连接';

  @override
  String get fbConnectNeeds => '请先输入地址和配对码。';

  @override
  String get fbConnectFailedTitle => '无法连接';

  @override
  String fbDisconnect(String name) {
    return '断开 $name';
  }

  @override
  String get fbDisconnectDetail => '在本机忘记配对信息、token 和批准密钥';

  @override
  String fbDisconnectTitle(String name) {
    return '断开 $name？';
  }

  @override
  String get fbDisconnectBody =>
      '本机会忘记该节点的配对信息、token 和批准密钥。节点上的笔记仍留在原处，本机等待中的笔记会保留到你丢弃为止。';

  @override
  String get fbErrorAddress => '该地址不在私有网络上。请使用本机、家庭网络或 tailnet。';

  @override
  String get fbErrorBadResponse => '记忆的应答格式本应用无法读取。请更新应用，或更新记忆程序。';

  @override
  String get fbErrorForbidden => '本机无权执行该操作。';

  @override
  String get fbErrorNotEnabled => '该功能在此记忆中已关闭。';

  @override
  String get fbErrorOwner => '本机暂时无法批准更改。请先设置批准方式。';

  @override
  String get fbErrorPairCode => '配对码无效。请向节点索取新的配对码。';

  @override
  String get fbErrorSlow => '记忆正忙。请稍后再试。';

  @override
  String get fbErrorStale => '与你查看时相比已发生变化。请重新查看后再试一次。';

  @override
  String get fbErrorStarting => '记忆正在启动。请稍后再试。';

  @override
  String get fbErrorUnauthorized => '本机未登录记忆。请重新设置。';

  @override
  String fbErrorCode(String code) {
    return '错误码 $code';
  }

  @override
  String get fbKeyLocked => '已锁定';

  @override
  String get fbKeyUnlocked => '已解锁';

  @override
  String get fbKindInvoice => '发票';

  @override
  String get fbKindHealth => '健康';

  @override
  String get fbModuleStorage => '存储';

  @override
  String get fbModuleCloud => '云备份';

  @override
  String get fbModuleCare => '需要你处理';

  @override
  String get fbNotSavedAtNode => '由记忆保存';

  @override
  String get fbNotSavedOnPhone => '在本机';

  @override
  String get fbNotSavedNodeItem => '记忆保存的一条笔记';

  @override
  String get fbReasonModuleNotConfigured => '尚未连接任何内容';

  @override
  String get fbReasonModuleNotEnabled => '尚未开启';

  @override
  String get fbReasonOutboxFull => '本机队列已满。请发送或丢弃一条等待中的笔记。';

  @override
  String get fbReasonParserError => '记忆无法读取。请重试。';

  @override
  String get fbReminderMismatch => '记忆的理解与此不同。请检查日期、时间和时区后重试。';

  @override
  String fbReminderPause(String what) {
    return '暂停“$what”';
  }

  @override
  String fbReminderResume(String what) {
    return '恢复“$what”';
  }

  @override
  String get fbRemindersTitle => '提醒';

  @override
  String get fbRemindersActive => '进行中';

  @override
  String get fbRemindersPaused => '已暂停';

  @override
  String get fbRemindersDone => '已完成';

  @override
  String get fbRemindersEmptyTitle => '尚无提醒';

  @override
  String get fbRemindersEmptyBody => '添加一条提醒，或在添加笔记时开启“同时提醒我”，它就会出现在这里。';

  @override
  String get fbSourceSaved => '你保存的内容';

  @override
  String get fbStateReady => '就绪';

  @override
  String get fbStateWaiting => '等待中';

  @override
  String get fbStateStale => '已过期';

  @override
  String get fbStateFailed => '失败';

  @override
  String get fbStateOff => '关闭';

  @override
  String get fbStateNotSetUp => '未设置';

  @override
  String get fbStatusTitle => '状态';

  @override
  String get fbStatusSearch => '搜索';

  @override
  String get fbStatusBackup => '备份';

  @override
  String get fbStatusBackedUp => '已备份';

  @override
  String get fbStatusNoBackup => '尚无备份';

  @override
  String get fbStatusThisPhone => '本机';

  @override
  String get fbStatusApprove => '允许从本机批准更改';

  @override
  String fbStatusSince(String when) {
    return '$when更新';
  }

  @override
  String get fbThingWaitingNote => '标记为“等待你确认”的事实位于“需要你处理”中。';

  @override
  String fbWhenDaily(String time) {
    return '每天 $time';
  }

  @override
  String get fbWhenCustom => '自定义计划';

  @override
  String get fbDeveloperFactsBook => '记忆（预览）';

  @override
  String get fbDeveloperFactsBookDetail => '在对话中显示“记忆”部分。默认关闭。';

  @override
  String get fbDeveloperTitle => '开发者';

  @override
  String get fbErrorBadRequest => '记忆未接受该内容。请检查输入后重试。';

  @override
  String get fbErrorNodeFull => '记忆自身的队列已满。请保留文字，稍后再试。';

  @override
  String get fbErrorIndexRebuilding => '搜索索引正在准备。请稍后再试。';

  @override
  String get fbErrorAuthUnavailable => '本机未设置指纹、面容或屏幕锁，因此无法批准更改。请在手机设置中设置后重试。';

  @override
  String get fbErrorAuthCancelled => '批准已取消。未做任何更改。';

  @override
  String get fbErrorAuthLocked => '错误尝试次数过多。请稍等片刻后重试。';

  @override
  String get fbErrorAuthFailed => '手机无法确认你的身份。未做任何更改。';

  @override
  String fbErrorSlowAfter(int seconds) {
    return '记忆正忙。请在 $seconds 秒后重试。';
  }

  @override
  String get fbAuthReason => '在记忆中批准此更改';

  @override
  String fbWhenWeeklyOn(String days, String time) {
    return '每周 $days $time';
  }

  @override
  String fbWhenMonthLast(String time) {
    return '每月最后一天 $time';
  }

  @override
  String fbWhenMonthlyOn(int day, String time) {
    return '每月 $day 日 $time';
  }

  @override
  String fbWhenYearlyOn(String date, String time) {
    return '每年 $date $time';
  }

  @override
  String get fbReminderNeedsTitle => '请先写提醒内容。';

  @override
  String get fbReminderWhatHint => '给牙医打电话';

  @override
  String get fbReminderDayOfMonth => '每月日期';

  @override
  String get fbReminderLastDay => '最后一天';

  @override
  String get fbRemindersAdd => '添加提醒';

  @override
  String fbThingOriginally(String source, String date) {
    return '最初读取自 $source · $date';
  }

  @override
  String get fbExportTitle => '导出此笔记';

  @override
  String get fbExportBody =>
      '这是加密对象形式的笔记。它不会解锁锁定文件夹，打开它需要当初在独立设备上设置记忆时得到的恢复信息。';

  @override
  String get fbExportObject => '加密对象';

  @override
  String get fbExportCopy => '复制导出内容';

  @override
  String get fbSettingsTitle => '记忆设置';

  @override
  String get fbSettingsBrief => '晨间简报';

  @override
  String get fbSettingsBriefCap => '每日条目数';

  @override
  String get fbSettingsBriefCapDetail => '简报一天内最多显示的条目数。';

  @override
  String get fbSettingsBriefOff => '无';

  @override
  String fbSettingsBriefItems(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项',
      one: '1 项',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsBriefTime => '简报时间';

  @override
  String get fbSettingsQuietHours => '免打扰时段';

  @override
  String get fbSettingsPrivacy => '隐私';

  @override
  String get fbSettingsLockTimeout => '锁定文件夹保持打开';

  @override
  String fbSettingsMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 分钟',
      one: '1 分钟',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsAreasDetail => '还有谁可以读取各分区';

  @override
  String get fbSettingsThisNode => '此记忆';

  @override
  String get fbSettingsHomeZone => '居住地时区';

  @override
  String get fbSettingsSpending => '支出上限';

  @override
  String get fbSettingsSpendingNone => '不允许任何支出';

  @override
  String get fbSettingsFootnote => '锁定时间和居住地时区在记忆服务器上设置。在此修改需要本机批准。';

  @override
  String get fbSettingsNeedsOwner => '本机暂时无法批准更改，因此设置只能查看、不能修改。请设置批准方式后再更改。';

  @override
  String get fbModuleReplication => '复制';

  @override
  String get fbStatusChecked => '已检查';

  @override
  String get fbStatusUnknown => '未知';

  @override
  String get fbStatusHistory => '已签名历史';

  @override
  String get fbStatusHistoryNotChecked => '未检查';

  @override
  String get fbStatusHistoryVerified => '已用配对时固定的密钥验证通过';

  @override
  String get fbStatusHistoryFailed => '无法验证。在验证通过前请勿批准任何内容。';

  @override
  String get fbStatusHistoryForked => '发现两份互相竞争的历史。批准已暂停。';

  @override
  String get fbStatusNoReplication => '复制已关闭，因此你的笔记只存在于这个记忆中，也无法得知云端副本的新旧。';

  @override
  String get fbStatusNotYet => '尚未开启';

  @override
  String get fbNotYetCloud => '云同步与备份';

  @override
  String get fbNotYetUnlock => '打开锁定文件夹';

  @override
  String get fbNotYetAlarms => '本机的提醒闹钟';

  @override
  String get fbNotAvailableYet => '关闭';

  @override
  String get fbReminderClockNote => '如果某天因时钟调整而不存在该时间，则在当天实际存在的第一个时间运行。';

  @override
  String get fbCaptureReminderNotMade => '提醒尚未创建。请在笔记保存后再添加一次。';

  @override
  String get fbNotSavedDiscardBodyNode => '记忆会删除它保存的那一份。该内容从未被保存过。';

  @override
  String get fbAreaNameGeneral => '通用';

  @override
  String get fbAreaNameHome => '家庭';

  @override
  String get fbAreaNameTravel => '出行';

  @override
  String get fbAreaNameUnsorted => '未分类';

  @override
  String get fbAreaNameId => '身份';

  @override
  String get fbModuleIndex => '搜索索引';

  @override
  String get fbModuleMembership => '设备';

  @override
  String get fbTriggersTitle => '当有新内容到达时';

  @override
  String get fbTriggersSettingsDetail => '根据你分享的消息建议提醒和对话的规则';

  @override
  String get fbTriggersUnavailableTitle => '此处规则已关闭';

  @override
  String get fbTriggersUnavailableBody => '此记忆不提供规则功能。请更新后再使用。';

  @override
  String get fbTriggersEmptyTitle => '尚无规则';

  @override
  String get fbTriggersEmptyBody => '规则只监视某一类消息并提出建议。在你批准之前，不会保存或发送任何内容。';

  @override
  String get fbTriggersNew => '新建规则';

  @override
  String fbTriggersGroupShare(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条规则',
      one: '1 条规则',
    );
    return '你分享的文字 · $_temp0';
  }

  @override
  String fbTriggersGroupChat(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条规则',
      one: '1 条规则',
    );
    return '你保存的对话 · $_temp0';
  }

  @override
  String fbTriggersGroupNote(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条规则',
      one: '1 条规则',
    );
    return '你写的笔记 · $_temp0';
  }

  @override
  String get fbTriggersRecent => '最近的消息';

  @override
  String get fbTriggersFootnote =>
      '规则只提出建议。在你批准之前，不会保存或发送任何内容。被忽略的内容只会计数，不会被保留。';

  @override
  String fbRuleFinds(String template, String outcomes) {
    return '查找 $template → $outcomes';
  }

  @override
  String get fbTemplateAppointment => '一个预约';

  @override
  String get fbTemplateDelivery => '一个快递';

  @override
  String get fbTemplateBooking => '一个预订';

  @override
  String get fbTemplateSubscription => '一次订阅续费';

  @override
  String get fbTemplateBill => '一笔到期账单';

  @override
  String get fbOutcomeReminder => '建议一条提醒';

  @override
  String get fbOutcomeFact => '建议一条事实';

  @override
  String get fbOutcomeConversation => '询问是否聊聊';

  @override
  String fbRuleWeek(int proposed, int ignored) {
    return '本周：建议 $proposed 条，忽略 $ignored 条';
  }

  @override
  String fbRuleWeekOffers(int proposed, int ignored, int offers) {
    String _temp0 = intl.Intl.pluralLogic(
      offers,
      locale: localeName,
      other: '$offers 个提议',
      one: '1 个提议',
    );
    return '本周：建议 $proposed 条，忽略 $ignored 条，$_temp0';
  }

  @override
  String get fbRulePaused => '已暂停';

  @override
  String fbRulePause(String name) {
    return '暂停“$name”';
  }

  @override
  String fbRuleResume(String name) {
    return '恢复“$name”';
  }

  @override
  String get fbRulePausedDone => '规则已暂停。';

  @override
  String get fbRuleResumedDone => '规则已重新开启。';

  @override
  String get fbSourceGroupShare => '你分享的文字';

  @override
  String get fbSourceGroupChat => '你保存的对话';

  @override
  String get fbSourceGroupNote => '你写的笔记';

  @override
  String fbEventWhen(String source, String when) {
    return '$source · $when';
  }

  @override
  String get fbReasonMatched => '有规则匹配了这条消息。';

  @override
  String get fbReasonNoMatch => '没有规则匹配这条消息。';

  @override
  String get fbReasonUncertain => '记忆无法安全读取此内容，因此未保留、也未建议任何内容。';

  @override
  String get fbReasonSensitive => '这看起来是私密内容，且锁定笔记处于关闭状态，因此被搁置。';

  @override
  String get fbReasonSourceCap => '今天来自该来源的消息过多，因此这条只做了计数。';

  @override
  String get fbReasonVaultCap => '今天到达的消息过多，因此这条只做了计数。';

  @override
  String get fbReasonRuleCap => '此规则已达每日上限，因此这条只做了计数。';

  @override
  String get fbReasonStorm => '许多相似消息同时到达，已合并为一条。';

  @override
  String get fbReasonNoRule => '此来源没有开启任何规则。';

  @override
  String get fbRuleNewTitle => '新建规则';

  @override
  String get fbRuleEditTitle => '编辑规则';

  @override
  String get fbRuleNameLabel => '规则名称';

  @override
  String get fbRuleNameHint => '例如：图书馆预约';

  @override
  String get fbRuleWhen => '当';

  @override
  String get fbRuleWhenLabel => '消息来自哪里';

  @override
  String get fbSourceKindShare => '分享的文字';

  @override
  String get fbSourceKindChat => '保存的对话';

  @override
  String get fbSourceKindNote => '笔记';

  @override
  String get fbRuleSays => '内容类似';

  @override
  String get fbRuleTemplateLabel => '消息类型';

  @override
  String get fbRuleLanguageLabel => '消息语言';

  @override
  String get fbRuleLangEn => '英语';

  @override
  String get fbRuleLangAr => '阿拉伯语';

  @override
  String get fbRuleTemplateHelp =>
      '记忆会读取类似“Appointment: Library visit | 2026-10-15T09:00”这样的一行。';

  @override
  String get fbRuleWordsLabel => '必须包含的词（可选）';

  @override
  String get fbRuleWordsHelp => '最多 4 个词，用逗号分隔。纯文本，不是代码。';

  @override
  String get fbRuleThen => '然后';

  @override
  String get fbRuleOutcomeReminder => '建议一条提醒';

  @override
  String get fbRuleOutcomeReminderDetail => '由你先确认日期和时间。';

  @override
  String get fbRuleOutcomeConversation => '询问是否聊聊';

  @override
  String get fbRuleOutcomeConversationDetail => '由你决定是否打开对话。';

  @override
  String get fbRuleOutcomeFactKept => '此规则还会建议一条事实。事实在记忆服务器上设置，因此保持现状。';

  @override
  String get fbRuleAutoStart => '让这些对话自动开始';

  @override
  String get fbRuleAutoStartOff => '对话只会被建议，不会替你开始。';

  @override
  String get fbRuleOn => '此规则已开启';

  @override
  String get fbRuleOnDetail => '关闭即可暂停。它已建议的内容不会被移除。';

  @override
  String get fbRuleLimit => '每天最多';

  @override
  String fbRuleLimitValue(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '每天 $count 次',
      one: '每天 1 次',
    );
    return '$_temp0';
  }

  @override
  String get fbRuleTest => '用示例试一下';

  @override
  String get fbRuleSampleLabel => '示例消息';

  @override
  String get fbRuleSampleExample => '用一个示例';

  @override
  String get fbRuleTestRun => '测试';

  @override
  String get fbRuleTestAgain => '再测一次';

  @override
  String fbRuleTestMatch(String summary, String when) {
    return '匹配。找到：$summary，$when。';
  }

  @override
  String get fbRuleTestMatchPlain => '匹配。';

  @override
  String fbRuleTestWould(String outcomes) {
    return '将建议：$outcomes。测试不会保存任何内容。';
  }

  @override
  String get fbRuleTestNoMatch => '不匹配。';

  @override
  String get fbRuleSave => '保存规则';

  @override
  String get fbRuleSaved => '规则已保存。';

  @override
  String get fbRuleDelete => '删除规则';

  @override
  String get fbRuleDeleteTitle => '删除此规则？';

  @override
  String fbRuleDeleteBody(String name) {
    return '“$name”将停止建议。它已建议的内容仍保留，由你决定。';
  }

  @override
  String get fbRuleNeedsOwner => '本机暂时无法签署规则更改，因此规则只能查看、不能修改。请设置批准方式后再更改。';

  @override
  String get fbRuleStale => '此规则在记忆中已变化。已重新加载：请检查后再次保存。';

  @override
  String get fbRuleProblemName => '请为规则取一个不超过 80 个字母的名称。';

  @override
  String get fbRuleProblemWords => '最多 4 个词，每个词最多 40 个字母。';

  @override
  String get fbRuleProblemOutcome => '请至少选择一项供规则建议的内容。';

  @override
  String get fbRuleProblemTemplate => '此记忆不读取该类消息。';

  @override
  String get fbRuleProblemLimit => '每天的次数请选择 1 到 20。';

  @override
  String get fbEventTitle => '这里发生了什么';

  @override
  String get fbEventFrom => '来源';

  @override
  String get fbEventSender => '发送者（声明）';

  @override
  String get fbEventApp => '应用（声明）';

  @override
  String get fbEventTrust => '可信度';

  @override
  String get fbTrustUntrusted => '来自他人，未经验证';

  @override
  String get fbTrustSelected => '你选择的对话';

  @override
  String get fbTrustAuthored => '你本人写的';

  @override
  String get fbEventRule => '匹配的规则';

  @override
  String get fbEventIgnored => '其他笔记';

  @override
  String fbEventCollapsed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '另有 $count 条相似消息被合并到这条。',
      one: '另有 1 条相似消息被合并到这条。',
    );
    return '$_temp0';
  }

  @override
  String get fbEventUnverified => '发送者未经验证。记忆无法核实这段文字的真实发送者，因此未经你确认，不会更改财务类事实。';

  @override
  String get fbEventProposed => '它的建议';

  @override
  String get fbEventFootnote => '此处不会显示原始文字，也绝不会把它当作指令执行。';

  @override
  String get fbTriggerChip => '来自触发器';

  @override
  String get fbWhyFired => '这条消息经历了什么';

  @override
  String fbCareProposalWhen(String what, String when) {
    return '$what，$when';
  }

  @override
  String fbNeedsYouOffers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '来自触发器的 $count 个提议',
      one: '来自触发器的 1 个提议',
    );
    return '$_temp0';
  }

  @override
  String get fbOfferTitle => '与你的智能体讨论？';

  @override
  String fbOfferFromRule(String name) {
    return '提议 · $name';
  }

  @override
  String fbOfferSource(String source, String date) {
    return '来自 $source · $date';
  }

  @override
  String get fbOfferTalk => '聊聊这件事';

  @override
  String get fbOfferNotNow => '暂不';

  @override
  String get fbOfferExplain =>
      '将开启与你的智能体的新对话。它只会收到一段简短摘要，而非完整消息。它可以读取并提出建议，但不会自行执行操作。回复或付费前一定会先征求你的同意。';

  @override
  String get fbOfferChatLead => '记忆注意到了一些你可能想聊的内容。以下是简短摘要，不是原始消息：';

  @override
  String get fbOfferStale => '此提议已变化或已过期，因此已刷新。';

  @override
  String get fbOfferUnknown => '记忆无法确认对话已开始，因此没有打开任何内容。它不会自行重试。';

  @override
  String get fbErrorBusy => '记忆正忙。请稍后重试。';

  @override
  String get fbRuleOfferUnavailable => '此记忆不提供由规则发起的对话。';

  @override
  String get fbNodeTitle => '本机上的记忆';

  @override
  String get fbNodeRow => '使用本机上的记忆';

  @override
  String get fbNodeRowDetail =>
      'Keep your memory on this phone. Nothing leaves the phone.';

  @override
  String get fbNodeUnsupportedTitle => '此处不可用';

  @override
  String get fbNodeUnsupportedBody =>
      'Memory on this phone needs On this phone, which only Android phones have.';

  @override
  String get fbNodeLinuxTitle => '请先设置本机';

  @override
  String get fbNodeLinuxBody =>
      'Memory runs in On this phone. Set that up first, then come back.';

  @override
  String get fbNodeLinuxAction => '设置本机';

  @override
  String get fbNodeNoBuildTitle => '尚无记忆版本';

  @override
  String get fbNodeNoBuildBody =>
      'This version of the app cannot install Memory on this phone. Update the app to a newer version, or connect to a server of your own below.';

  @override
  String get fbNodeNotInstalledTitle => '记忆尚未安装';

  @override
  String get fbNodeNotInstalledBody =>
      'The app installs Memory in On this phone, starts it and connects to it by itself. It listens only inside this phone.';

  @override
  String get fbNodeRun => '在本机上设置记忆';

  @override
  String get fbNodeStepCopying => '正在安装程序';

  @override
  String get fbNodeStepPreparing => '正在创建记忆';

  @override
  String get fbNodeStepStarting => '正在启动';

  @override
  String get fbNodeStepPairing => '正在连接本应用';

  @override
  String get fbNodeRunningTitle => '记忆正在此处运行';

  @override
  String get fbNodeRunningBody =>
      'Memory runs in On this phone and is connected to this app. It listens only inside this phone.';

  @override
  String get fbNodeStoppedTitle => '记忆已停止';

  @override
  String get fbNodeStoppedBody => '你的记忆仍在本机上。启动后即可使用。';

  @override
  String get fbNodeStart => '启动记忆';

  @override
  String get fbNodeStop => '停止记忆';

  @override
  String get fbNodeRestart => '重启记忆';

  @override
  String get fbNodeStopping => '正在停止';

  @override
  String get fbNodeRemoving => '正在移除';

  @override
  String get fbNodeOpen => '打开“关于你”';

  @override
  String get fbNodeAddress => '地址';

  @override
  String get fbNodeAddressValue => '仅限本机内部';

  @override
  String get fbNodeRemove => '从本机移除记忆';

  @override
  String get fbNodeRemoveTitle => '从本机移除记忆？';

  @override
  String get fbNodeRemoveBody => '这会停止记忆，并删除它在本书机上保存的全部内容。此操作无法撤销。';

  @override
  String get fbNodeRemoveLoses1 => '记忆在此保存的所有笔记、事实和提醒';

  @override
  String get fbNodeRemoveLoses2 => '与之关联的连接和批准密钥';

  @override
  String get fbNodeRemoved => '记忆已从本机移除。';

  @override
  String get fbNodeFailInstall => '程序无法在此安装。请重试；“详情”中说明了原因。';

  @override
  String get fbNodeFailStart => '记忆未能启动。请重试。';

  @override
  String get fbNodeFailPair => '记忆正在运行，但本应用无法连接。请重试。';

  @override
  String get fbNodeFailStop => '记忆未能停止。请重试。';

  @override
  String get fbNodeFailRemove => '无法移除记忆。其部分数据可能仍在本机上。';

  @override
  String get fbNodeLog => '显示日志';

  @override
  String get fbReasonTargetStale => '此规则要更新的对象已变化，因此未提出任何建议。';

  @override
  String get fbReasonTargetGone => '此规则要更新的对象已不存在，或未对其开放，因此未提出任何建议。';

  @override
  String get fbReasonRejectedEvent => '记忆未接受此消息。';

  @override
  String get fbRuleProblemSender => '发送者名称必须为 1 到 80 个字母。';

  @override
  String get fbRuleSenderLabel => '发送者声明（可选）';

  @override
  String get fbRuleSenderHelp =>
      '仅匹配声称来自该名称的消息。任何人都可以声称任意名称，因此这只能缩小规则范围，永远无法证明真正的发送者。';

  @override
  String get fbRuleAreaLabel => '分区';

  @override
  String get fbRuleTestDraftNote => '测试使用你在此看到的内容，无论是否已保存。测试不会保存任何内容。';

  @override
  String get fbCareProposalEdited => '已更改。请检查后批准。';

  @override
  String get fbCaptureRulesLook => '允许我的规则读取此内容';

  @override
  String get fbAuthReasonRules => '允许你的规则读取这段文字';

  @override
  String get fbNodeWhatTitle => '这是什么？';

  @override
  String get fbNodeWhatBody =>
      'Memory keeps your notes, reminders and facts on this phone, private to you. It runs on this phone and never leaves it.';

  @override
  String get fbNodeOwnServer => '连接到自己的服务器';

  @override
  String get fbNodeOwnServerBody => '高级：不使用本机，而将本应用连接到你自己运行的记忆服务器。';

  @override
  String get fbMemoryStopped => '记忆已停止';

  @override
  String get fbMemoryStoppedDetail => '启动后即可查看你的笔记和提醒';

  @override
  String get fbMemoryStart => '启动';

  @override
  String get fbMemoryStarting => '正在启动记忆…';

  @override
  String get fbCareTapHint => '点按卡片可修改或跳过。';

  @override
  String fbCareApproveNamed(String claim) {
    return '批准“$claim”';
  }

  @override
  String fbCareRejectNamed(String claim) {
    return '拒绝“$claim”';
  }

  @override
  String fbCareEditNamed(String claim) {
    return '修改“$claim”';
  }

  @override
  String fbCareSkipNamed(String claim) {
    return '跳过“$claim”';
  }

  @override
  String get fbCareSkipDetail => '它会留在这里，供稍后处理';

  @override
  String get fbCareSourceRule => '由规则建议';

  @override
  String get fbCareSourceAgent => '由智能体建议';

  @override
  String get fbNodeNameThisPhone => '本机上的记忆';

  @override
  String get fbNodeNameDefault => '此记忆';

  @override
  String get fbStatusAllWorking => '一切正常';

  @override
  String fbStatusNeedsLook(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 项需要查看',
      one: '1 项需要查看',
    );
    return '$_temp0';
  }

  @override
  String fbStatusBackedUpAge(String age) {
    return '已备份 · 副本生成于$age前';
  }

  @override
  String get fbReminderNotSounded => '本机尚未响铃';

  @override
  String fbEventOutcomeProposed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '为你建议了 $count 项内容',
      one: '为你建议了 1 项内容',
    );
    return '$_temp0';
  }

  @override
  String get fbEventWaitingForOk => '等待你确认';

  @override
  String get fbCaptureAddTo => '添加到';

  @override
  String get fbCaptureAddToNone => '让记忆自行归类';

  @override
  String get fbCaptureAddToFactNote => '保存为一行，由你批准';

  @override
  String get fbCaptureArea => '分区';

  @override
  String get fbCaptureAreaAuto => '让记忆选择';

  @override
  String get fbCaptureRemindToo => '同时提醒我';

  @override
  String get fbCaptureRemindTooDetail => '保存前由你确认日期和时间。';

  @override
  String fbCaptureRulesOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 条规则已开启',
      one: '1 条规则已开启',
      zero: '没有开启的规则',
    );
    return '$_temp0';
  }

  @override
  String get fbCaptureSeeRules => '查看你的规则';

  @override
  String fbCaptureFactAdded(String thing) {
    return '已添加到$thing。';
  }

  @override
  String get fbCaptureFactProposed => '已发送待你确认。它会在“需要你处理”中等待。';

  @override
  String get fbCaptureSeeNeedsYou => '查看“需要你处理”';

  @override
  String fbThingAddFact(String thing) {
    return '向$thing添加一条事实';
  }

  @override
  String get fbThingAddFactSave => '添加事实';

  @override
  String fbThingRemind(String thing) {
    return '提醒我$thing';
  }

  @override
  String fbThingReminderMade(String thing) {
    return '已为$thing保存提醒。';
  }

  @override
  String get fbThingEditFact => '修改这条事实';

  @override
  String get fbThingEditFactSave => '发送我的修改';

  @override
  String get fbThingFactChanged => '已更改。';

  @override
  String get fbThingChangeProposed => '你的修改会在“需要你处理”中等待你确认。';

  @override
  String get fbThingOneLine => '一行。在保留之前需经你确认。';

  @override
  String get fbThingTapHint => '点按事实可查看其来源，或进行修改。';

  @override
  String fbThingExplainTold(String date) {
    return '你在 $date 告诉了记忆这一点。';
  }

  @override
  String fbThingExplainSeen(String source, String date) {
    return '记忆在 $date 从 $source 读到这一点。来源可能有误，依赖前请先核实。';
  }

  @override
  String fbThingExplainSeenNoSource(String date) {
    return '记忆在 $date 读到这一点。来源可能有误，依赖前请先核实。';
  }

  @override
  String fbThingExplainGuessed(String source, String date) {
    return '记忆在 $date 从 $source 推测出这一点。尚无人确认。';
  }

  @override
  String fbThingExplainGuessedNoSource(String date) {
    return '记忆在 $date 推测出这一点。尚无人确认。';
  }

  @override
  String fbThingExplainCalculated(String date) {
    return '记忆在 $date 根据其他事实推算得出。';
  }

  @override
  String get fbThingExplainWaiting => '它会在“需要你处理”中等待你确认。';

  @override
  String get settingsV3Servers => '服务器';

  @override
  String get settingsV3ThisPhone => '此手机';

  @override
  String get settingsV3Providers => '提供商';

  @override
  String get settingsV3Shell => '命令所用 Shell';

  @override
  String get settingsV3VoiceTyping => '语音输入';

  @override
  String get settingsV3Permissions => '权限与自动化';

  @override
  String get settingsV3Notifications => '通知';

  @override
  String get settingsV3Background => '后台';

  @override
  String get settingsV3Language => '语言';

  @override
  String get settingsV3ByServer => '按服务器';

  @override
  String get settingsV3On => '开';

  @override
  String get settingsV3Off => '关';

  @override
  String get settingsV3Connected => '已连接';

  @override
  String get settingsV3NotConnected => '未连接';

  @override
  String get settingsV3PhoneRunning => '运行中';

  @override
  String get settingsV3PhoneInstalled => '已安装';

  @override
  String get settingsV3PhoneSetUp => '设置';

  @override
  String get settingsV3ServersBlock => '其中一些可以按服务器单独更改';

  @override
  String get settingsV3ServerGone => '此服务器已被移除';

  @override
  String get settingsV3SectionConnection => '连接';

  @override
  String get settingsV3SectionInApp => '本应用内';

  @override
  String get settingsV3StatusVersion => '状态与版本';

  @override
  String get settingsV3SignIn => '登录';

  @override
  String get settingsV3SignInPassword => '密码';

  @override
  String get settingsV3SignInToken => '令牌';

  @override
  String get settingsV3SignInNone => '未设置';

  @override
  String get settingsV3NameAddress => '名称与地址';

  @override
  String get settingsV3Supports => '此服务器支持的功能';

  @override
  String get settingsV3ModelNew => '新对话使用的模型';

  @override
  String get settingsV3ServerPick => '由服务器决定';

  @override
  String get settingsV3DifferentHere => '此处不同';

  @override
  String get settingsV3Watch => '关注此服务器';

  @override
  String get settingsV3ShareRead => '共享已读状态';

  @override
  String get settingsV3Updates => '更新与服务';

  @override
  String get settingsV3UseMyDefault => '使用我的默认设置';

  @override
  String get settingsV3AgentsPhone => '在此手机上';

  @override
  String get settingsV3ModelByServerNote => '每台服务器各自选择新对话所用的模型。已打开的对话会保留原有模型。';

  @override
  String get settingsV3ProvidersByServerNote =>
      '每台服务器各自登录提供商。在一台上登录，绝不会让其他服务器也登录。';

  @override
  String get settingsV3ShellByServerNote =>
      '保存在每台服务器上。使用某台服务器的其他应用也会使用它的 shell。';

  @override
  String settingsV3ServerConnected(String name) {
    return '$name · 已连接';
  }

  @override
  String settingsV3ServersCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 台服务器',
      one: '1 台服务器',
    );
    return '$_temp0';
  }

  @override
  String settingsV3DifferentOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '在 $count 台服务器上不同',
      one: '在 1 台服务器上不同',
    );
    return '$_temp0';
  }

  @override
  String settingsV3ModelNewOn(String name) {
    return '$name 上的新对话';
  }

  @override
  String settingsV3SectionOn(String name) {
    return '在 $name 上';
  }

  @override
  String settingsV3SavedOn(String name) {
    return '已保存在 $name 上。使用 $name 的其他应用也会应用此设置。';
  }

  @override
  String settingsV3ConnectedVersion(String version) {
    return '已连接 · $version';
  }

  @override
  String settingsV3ConnectTo(String name) {
    return '连接到 $name';
  }

  @override
  String settingsV3UseMyDefaultDetail(String name) {
    return '$name 的通知、新对话模型和已读共享将恢复为你自己的选择。';
  }

  @override
  String get settingsV3ServerSettings => '服务器设置';

  @override
  String get settingsV3ShellAutomatic => '自动';

  @override
  String get settingsV3ReadStateByServerNote =>
      '每台服务器单独询问。在你为某台服务器开启之前，不会向其共享任何内容。';

  @override
  String settingsV3SharingCount(int count, int total) {
    return '$total 个中有 $count 个共享';
  }

  @override
  String get fbSettingsUsePhoneZone => '使用本机时区';

  @override
  String fbSettingsUsePhoneZoneDetail(String zone) {
    return '$zone。提醒会保持其钟点时间。';
  }

  @override
  String get fbReasonKeyLockedNotYet => '它包含密码或账号，记忆暂时无法保存这类内容。它仍留在本机。';

  @override
  String get fbListLockedNoteNotYet => '含密码或账号的笔记暂不保存。它们会在“尚未保存”中等待。';

  @override
  String get fbReminderNotSoundedYet => '本机暂时无法为提醒响铃。它已保存并显示在“提醒”中。';

  @override
  String settingsV3ReconnectTo(String name) {
    return '重新连接 $name';
  }

  @override
  String get projectToolsNeedGitCommit =>
      '工作树和云环境需要由一个 git 跟踪、且至少有一次提交的项目。请让智能体设置 git 并完成首次提交。';

  @override
  String settingsV3UseMyDefaultDone(String name, String what) {
    return '已将 $name 重置为你自己的选择：$what。';
  }

  @override
  String get settingsV3NotificationsAllServers => '所有服务器的通知';

  @override
  String get settingsV3AgentsOnPhone => '此手机上的智能体';

  @override
  String get settingsV3AgentsReady => '就绪';

  @override
  String settingsV3AgentsExternalCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已连接 $count 个',
      one: '已连接 1 个',
      zero: '未连接',
    );
    return '$_temp0';
  }

  @override
  String settingsV3NotConnectedVersion(String version) {
    return '未连接 · 上次可见 $version';
  }

  @override
  String get settingsV3ConnectToChange => '连接后可查看并更改其提供商、工具与权限。';

  @override
  String quotaInAppStepChecking(String server) {
    return '正在检查 $server';
  }

  @override
  String get quotaInAppStepCopying => '正在复制用量助手';

  @override
  String get quotaInAppStepSignIns => '正在查找你的登录';

  @override
  String get quotaInAppStepStarting => '正在启动用量助手';

  @override
  String get quotaInAppStepReading => '正在读取剩余用量';

  @override
  String quotaInAppFailLinux(String server) {
    return '$server is not ready yet. Finish setting up On this phone, then try again.';
  }

  @override
  String quotaInAppFailNoNode(String server) {
    return '$server has no Node yet, which usage needs. Run the On this phone setup again to add it, then try again.';
  }

  @override
  String quotaInAppFailNodeOld(String server) {
    return 'Node on $server is older than version 20. Run the On this phone setup again to update it, then try again.';
  }

  @override
  String quotaInAppFailPrepare(String server) {
    return '无法在 $server 上准备用量。请重试；若持续失败，请重启应用。';
  }

  @override
  String quotaInAppFailStart(String server) {
    return '$server 上的用量助手未能启动。请稍后重试。';
  }

  @override
  String quotaInAppFailRemove(String server) {
    return '无法关闭 $server 上的用量。请重试。';
  }

  @override
  String get settingsV3ToolsByServerNote =>
      '工具是智能体可以使用的操作，例如读取文件或运行命令。每台服务器各自维护一套工具。';

  @override
  String get launchUiUsageSignInServerGone => '该服务器已不再保存，因此这里显示你的用量。';

  @override
  String get launchUiUsageSignInConnectionFailed =>
      '无法连接到该服务器以登录。请在“服务器”中检查，然后重试该小组件。';

  @override
  String get settingsHubSearchReasoningAliases => '对话记录 显示 思考 推理';

  @override
  String get usageTabPlans => '套餐';

  @override
  String get usageTabSpending => '花费';

  @override
  String get usagePlansRefresh => '刷新套餐';

  @override
  String get usageEmptyTitle => '尚无订阅';

  @override
  String get usageEmptyBody => '添加套餐以查看剩余量及重置时间。';

  @override
  String get usageAddSection => '添加订阅';

  @override
  String usageLeft(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '剩余 $percentString%';
  }

  @override
  String usageUsedPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '已用 $percentString%';
  }

  @override
  String get usageWindowFiveHour => '5 小时';

  @override
  String get usageWindowWeek => '周';

  @override
  String get usageWindowMonth => '月';

  @override
  String get usageWindowTools => '工具 · 月';

  @override
  String get usageWindowOther => '上限';

  @override
  String usageOnConnection(String connection) {
    return '在$connection上';
  }

  @override
  String get usageThisPhone => '本机';

  @override
  String get usageAlertsTitle => '提醒';

  @override
  String get usageAlertsSummaryOff => '关闭';

  @override
  String get usageWidgetRow => '桌面小组件';

  @override
  String usageApiKeyOnly(String provider, String connection) {
    return '$provider API 密钥（$connection）· 按量付费，无套餐上限';
  }

  @override
  String get usagePillSignInRanOut => '登录已失效';

  @override
  String get usagePillBroken => '无法读取';

  @override
  String get usagePillCantRead => '暂时无法读取';

  @override
  String get usagePillOffline => '离线';

  @override
  String usageCantReadBody(String connection) {
    return '已在$connection上登录。应用暂时无法从那里读取其上限。';
  }

  @override
  String get usageCantReadBodyOne => '已登录，但应用暂时无法读取其上限。';

  @override
  String usageSignInRanOut(String provider, String connection) {
    return '$provider 在$connection上的登录已失效';
  }

  @override
  String usageSignInRanOutOne(String provider) {
    return '你的 $provider 登录已失效。';
  }

  @override
  String get usageNotSignedInAnywhere => '未在任何位置登录';

  @override
  String get usageNotSignedInOne => '未登录';

  @override
  String usageReaderBroken(String provider) {
    return '暂时无法读取 $provider';
  }

  @override
  String get usageNoLimits => '此套餐未报告上限';

  @override
  String usageOfflineStale(String connection, String age) {
    return '$connection 离线 · $age前读取';
  }

  @override
  String usageOfflineStaleOne(String age) {
    return '离线 · $age前读取';
  }

  @override
  String usageDurationMinutes(int minutes) {
    return '$minutes 分钟';
  }

  @override
  String usageDurationHours(int hours) {
    return '$hours 小时';
  }

  @override
  String usageDurationHoursMinutes(int hours, int minutes) {
    return '$hours 小时 $minutes 分钟';
  }

  @override
  String usageDurationDays(int days) {
    return '$days 天';
  }

  @override
  String usageDurationDaysHours(int days, int hours) {
    return '$days 天 $hours 小时';
  }

  @override
  String usageResetsIn(String time, String duration) {
    return '$time 重置，还有 $duration';
  }

  @override
  String get usageResetPassed => '重置时间已过。请刷新查看。';

  @override
  String usageResetsAt(String time) {
    return '$time 重置';
  }

  @override
  String usageFiveHourResets(String time) {
    return '5 小时额度 $time 重置';
  }

  @override
  String usageWeekResets(String time) {
    return '每周额度 $time 重置';
  }

  @override
  String usageOutUntil(String time) {
    return '耗尽至 $time';
  }

  @override
  String usageResetSince(String time) {
    return '自上次读取后于 $time 重置';
  }

  @override
  String usageReadAgo(String age) {
    return '$age前读取';
  }

  @override
  String usageCheckedAgo(String age) {
    return '$age前检查';
  }

  @override
  String usageReadFrom(String connection) {
    return '已从$connection读取用量';
  }

  @override
  String get usageSignIn => '登录';

  @override
  String get usageSignInAgain => '重新登录';

  @override
  String get usageAddKey => '添加密钥';

  @override
  String get usageTryAgain => '重试';

  @override
  String get usageSignInUnavailable => '暂时无处可登录。';

  @override
  String usageAddTitle(String provider) {
    return '添加 $provider';
  }

  @override
  String usageAddWhere(String provider) {
    return '$provider 应在哪里登录？应用会从你选择的位置读取用量。';
  }

  @override
  String get usageBest => '最佳';

  @override
  String get usageAddInAppNote => '在本机 · 用量立即可用';

  @override
  String get usageAddServerNote => '你保存的服务器';

  @override
  String usageAddPhoneNote(String provider) {
    return '在本机登录 $provider 智能体';
  }

  @override
  String usageAddNoneTitle(String provider) {
    return '暂时无法添加 $provider';
  }

  @override
  String get usageAddNoneBody =>
      'Save a server, or set up On this phone, and it will appear here.';

  @override
  String get usageReadFromTitleHere => '读取用量';

  @override
  String get usageReadFromGoneTitle => '该连接已不存在';

  @override
  String get usageReadFromGoneBody => '请返回“套餐”查看剩余量。';

  @override
  String usageReadFromSignedIn(String providers, String connection) {
    return '$providers 已在$connection上登录';
  }

  @override
  String get usageReadFromBodyInApp =>
      'To show their limits, the app starts a small reader in On this phone. It only reads how much is left, never your conversations or keys.';

  @override
  String get usageReadFromSetUpInApp => '设置用量';

  @override
  String get usageNotNow => '暂不';

  @override
  String get usageSubGoneTitle => '此套餐已不存在';

  @override
  String get usageSubGoneBody => '请返回“套餐”查看剩余量。';

  @override
  String get usageStateSignedIn => '已登录';

  @override
  String get usageStateExpired => '登录已失效';

  @override
  String get usageStateNotSignedIn => '未登录';

  @override
  String get usageConnOffline => '离线';

  @override
  String get usageConnReadsHere => '数据来自此处';

  @override
  String get usageSameAccount => '同一账户';

  @override
  String get usageSignedInOn => '已登录于';

  @override
  String get usageAlertAt => '在以下用量时提醒我';

  @override
  String usageAlertAtPlan(String provider, int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '仅适用于 $provider。其他套餐使用 $percentString%。';
  }

  @override
  String get usageAlertAtSame => '与你其他套餐相同';

  @override
  String usageAlertSameAsOthers(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '与其他套餐相同（$percentString%）';
  }

  @override
  String get usageNotifyReset => '重置时通知我';

  @override
  String get usageNotifyResetHelp => '某个耗尽的上限恢复时发送消息。';

  @override
  String get usageSaveFailed => '无法保存。保持原样。';

  @override
  String get usageDetails => '详情';

  @override
  String get usageDetailReader => '读取器';

  @override
  String get usageDetailRead => '读取';

  @override
  String get usageDetailChecked => '已检查';

  @override
  String get usageDetailAccount => '账户';

  @override
  String get usageDetailWindows => '窗口';

  @override
  String get usageYes => '是，已识别账户';

  @override
  String get usageNo => '未识别账户';

  @override
  String get usageAlertsSwitch => '用量提醒';

  @override
  String get usageAlertsDifferent => '与默认值不同';

  @override
  String get usageAlertOverrideResetOn => '重置时通知你';

  @override
  String get usageAlertOverrideResetOff => '无重置消息';

  @override
  String get usageAlertsNothingToWatch => '暂无可监视的内容。套餐可被读取后，提醒即会开始。';

  @override
  String get usageWidgetSettingsShow => '显示这些套餐';

  @override
  String get usageWidgetSettingsWindows => '显示 5 小时、每周或两者';

  @override
  String get usageWidgetWindowBoth => '两者';

  @override
  String get usageWidgetWindowFiveHour => '5 小时';

  @override
  String get usageWidgetWindowWeekly => '每周';

  @override
  String get usageWidgetSettingsSetup => '显示需要设置的行';

  @override
  String get usageWidgetSettingsSetupHelp => '登录、添加密钥并设置行';

  @override
  String get usageSources => '来源';

  @override
  String get usageNoKey => '未添加密钥';

  @override
  String get usageStateCouldntCheck => '无法检查';

  @override
  String usageViaOpenCodeNoPlan(String connection) {
    return '$connection 上的 Anthropic · 通过 OpenCode 无套餐上限';
  }

  @override
  String get usageNotReadYet => '尚未读取';

  @override
  String get usageConnectionsHeader => '连接';

  @override
  String get usageReadUsage => '读取用量';

  @override
  String usageSetupSignedInOn(String connection) {
    return '已登录于$connection';
  }

  @override
  String get usageSetupSignedIn => '已登录';

  @override
  String usageSetupRanOutOn(String connection) {
    return '$connection 上的登录已失效';
  }

  @override
  String get usageSetupRanOut => '登录已失效';

  @override
  String get usageSetupCouldntRead => '无法读取';

  @override
  String get usageNeedsSetupHeader => '需要设置';

  @override
  String get usageNoNumbersYet => '尚无套餐数据。请在下方设置。';

  @override
  String usageComputerExplain(String server) {
    return '每次刷新时，应用会通过 OpenCode 在 $server 上运行一次小型只读检查。不会安装任何内容，也不会有常驻进程。它只回传剩余量，绝不涉及你的密钥或对话。';
  }

  @override
  String get usageComputerWhatRuns => '运行内容';

  @override
  String get usageComputerCommandLabel => '命令';

  @override
  String get usageComputerReadsLabel => '读取内容';

  @override
  String usageComputerReadsValue(String server) {
    return '读取 $server 上 OpenCode 的登录文件，以及 Codex 的 Codex CLI 登录文件。只读，不写入任何内容。';
  }

  @override
  String get usageComputerTurnOn => '开启';

  @override
  String get usageComputerTurnOff => '关闭';

  @override
  String get usageComputerReadNow => '立即读取';

  @override
  String get usageComputerDone => '完成';

  @override
  String usageComputerChecking(String server) {
    return '正在检查 $server…';
  }

  @override
  String usageComputerFound(String plans, String server) {
    return '在 $server 上找到 $plans。';
  }

  @override
  String usageComputerFoundNone(String server) {
    return '检查成功，但 $server 上没有已登录的套餐。';
  }

  @override
  String usageComputerOnNote(String server) {
    return '正在通过 OpenCode 读取 $server。';
  }

  @override
  String usageComputerNeedsNode(String server) {
    return '$server 需要 Node.js 20 或更高版本。';
  }

  @override
  String usageComputerServerTooOld(String server) {
    return '$server 上的 OpenCode 版本过旧，无法完成此操作。请更新后重试。';
  }

  @override
  String usageComputerOffline(String server) {
    return '暂时无法连接 $server。';
  }

  @override
  String usageComputerNoAnswer(String server) {
    return '对 $server 的检查未完成。请重试。';
  }

  @override
  String usageComputerRowTitle(String server) {
    return '正在读取 $server';
  }

  @override
  String get usageDetailLastCheck => '上次检查';

  @override
  String get usagePhoneNotRunning => '未运行';

  @override
  String get kitRequestNeedsYouLabel => '需要你处理';

  @override
  String get kitRequestAlwaysAllowLike => '始终允许此类请求';

  @override
  String get chatRequestAlwaysAllowCommands => '始终允许此类命令';

  @override
  String get chatRequestAskCommand => '运行此命令？';

  @override
  String get chatRequestAskEdit => '编辑此文件？';

  @override
  String get chatRequestAskRead => '读取此文件？';

  @override
  String get chatRequestAskDirectory => '打开此文件夹？';

  @override
  String get chatRequestAskFetch => '打开此网页？';

  @override
  String usageBackAt(String time) {
    return '$time 后恢复';
  }

  @override
  String get usageAlertsOnWord => '开启';

  @override
  String usageWidgetPlans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个套餐',
      one: '1 个套餐',
      zero: '无套餐',
    );
    return '$_temp0';
  }

  @override
  String get usageAlertsForEvery => '适用于应用可读取的每个套餐';

  @override
  String get usageAlertsTellMe => '在以下情况通知我';

  @override
  String get usageAlertsQuietGroup => '安静';

  @override
  String get usageAlertPlanDownTo => '套餐降至';

  @override
  String get usageAlertUsedUpBack => '耗尽的套餐恢复';

  @override
  String get usageQuietSameAsOthers => '与你其他提醒相同';

  @override
  String get usageWifiOnlyShort => '仅在 Wi-Fi 下检查';

  @override
  String kitTaskChipCount(int done, int total) {
    return '$done/$total';
  }

  @override
  String kitTaskChipDone(int done, int total) {
    return '✓ $done/$total';
  }

  @override
  String kitTaskChipAdded(int done, int total) {
    return '+ $done/$total';
  }

  @override
  String kitTaskChipSpoken(int done, int total) {
    return '任务，已完成 $done / $total';
  }

  @override
  String kitTaskChipSpokenNow(int done, int total, String task) {
    return '任务，已完成 $done / $total，当前 $task';
  }

  @override
  String get toolCardUpdatedTasks => '已更新任务';

  @override
  String kitDiffFoldAbove(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行',
      one: '1 行',
    );
    return '… 上方 $_temp0';
  }

  @override
  String kitDiffFoldBelow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行',
      one: '1 行',
    );
    return '… 下方 $_temp0';
  }

  @override
  String kitDiffFoldBetween(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 行',
      one: '1 行',
    );
    return '… $_temp0';
  }

  @override
  String notifStatusNeedsYou(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个需要你处理',
      one: '1 个需要你处理',
    );
    return '$_temp0';
  }

  @override
  String notifStatusStillWorking(int count) {
    return '$count 个仍在进行';
  }

  @override
  String notifStatusChatsWorking(int count) {
    return '$count 个对话进行中';
  }

  @override
  String notifStatusMore(int count) {
    return '另有 $count 个';
  }

  @override
  String notifStatusWaitingCount(int count) {
    return '$count 个等待中';
  }

  @override
  String notifStatusTasksProgress(int done, int total) {
    return '$done/$total 个任务';
  }

  @override
  String notifStatusMinutes(int count) {
    return '$count 分钟';
  }

  @override
  String notifStatusHours(int count) {
    return '$count 小时';
  }

  @override
  String notifStatusWaitingProvider(String provider) {
    return '等待中 · $provider 限额';
  }

  @override
  String get notifStatusWaitingGeneric => '等待中 · 提供商繁忙';

  @override
  String notifStatusTriesAgainAt(String time) {
    return '将在 $time 重试';
  }

  @override
  String get notifStatusTriesAgainSoon => '稍后重试';

  @override
  String get notifStatusPausedAndroidTitle => '已暂停 · Android 后台限制';

  @override
  String get notifStatusPausedAndroidBody =>
      'Android 每天在后台运行 6 小时后会停止后台工作。打开 OpenCode 以继续。';

  @override
  String get notifStatusPausedHotTitle => '已暂停 · 手机过热';

  @override
  String get notifStatusPausedHotBody => '冷却后自动恢复';

  @override
  String get notifStatusPausedIdleTitle => '已暂停 · 没有运行中的任务';

  @override
  String get notifStatusPausedIdleBody => '点按以重新启动 OpenCode';

  @override
  String notifStatusReconnectingTitle(String server) {
    return '正在重新连接 $server';
  }

  @override
  String get notifStatusReconnectingGeneric => '正在重新连接';

  @override
  String get notifStatusReconnectingBody => '正在等待连接';

  @override
  String get notifStatusReadyTitle => '就绪 · 没有运行中的任务';

  @override
  String get notifStatusReadyBody => '随时可以开始';

  @override
  String notifStatusLastReply(String ago) {
    return '上次回复于 $ago前';
  }

  @override
  String get notifStatusLastReplyNow => '上次回复刚刚完成';

  @override
  String notifStatusSetupTitle(int step, int steps) {
    return '设置 · 第 $step 步，共 $steps 步';
  }

  @override
  String notifStatusOnServer(String server) {
    return '在 $server';
  }

  @override
  String get notifStatusOnThisPhone => '在此手机上';

  @override
  String notifStatusPublicWorking(int count) {
    return '$count 个进行中';
  }

  @override
  String get notifStatusPublicWaiting => '等待中';

  @override
  String get notifStatusPublicPaused => '已暂停';

  @override
  String get notifStatusPublicReconnecting => '正在重新连接';

  @override
  String get notifStatusPublicReady => '就绪';

  @override
  String get notifStatusPublicSetup => '正在设置';

  @override
  String get integrationsSignInCodeLabel => '你的代码';

  @override
  String get integrationsSignInCodeHint => '“打开浏览器”会为你复制代码。请将其粘贴到打开的页面上。';

  @override
  String get integrationsCodeCopied => '代码已复制';

  @override
  String get integrationsCopyCode => '复制代码';

  @override
  String get integrationsOpenBrowserAgain => '重新打开浏览器';

  @override
  String get integrationsSignInWaitingBrowser => '正在等待在浏览器中批准';

  @override
  String get integrationsSignInWaitingDetail =>
      '在浏览器中输入代码并批准。应用正在等待，你返回后会自动完成登录。';

  @override
  String get integrationsSignInEnded => '登录已结束';

  @override
  String get integrationsSignInEndedDetail =>
      '此次登录在获批前已结束。可能是代码已过期，或服务器已重启。未添加任何内容。';

  @override
  String get integrationsStartAgain => '重新开始';

  @override
  String get mentionToolTitle => '项目文件';

  @override
  String get mentionToolSupporting => '让智能体查看此项目中的文件';

  @override
  String get mentionSheetTitle => '项目文件';

  @override
  String get mentionSearchLabel => '按名称查找文件';

  @override
  String get mentionSearchHint => '输入文件名的一部分即可搜索此项目。';

  @override
  String get mentionSearchFailedTitle => '搜索未完成';

  @override
  String get mentionSearchFailedMessage => '请检查与服务器的连接后重试。';

  @override
  String get mentionNoMatchWhat => '项目文件';

  @override
  String get mentionProjectFolder => '项目文件夹';

  @override
  String get inAppServerOwnershipRecoveryBody =>
      '此手机上的服务器无法安全重启。请在应用设置中点按“强制停止”，然后重新打开应用；你的对话会保留。';

  @override
  String get liveStyleAurora => '极光流动';

  @override
  String get liveStyleMesh => '呼吸网格';

  @override
  String get liveStyleDotRipple => '点阵涟漪';

  @override
  String get liveStyleTopo => '等高线';

  @override
  String get liveStyleFlowThreads => '流动丝线';

  @override
  String get liveStyleGlowSpill => '溢光';

  @override
  String get liveStyleLightRays => '光束';

  @override
  String get liveStyleConstellation => '星座';

  @override
  String get liveStyleHalftone => '半色调';

  @override
  String get liveStyleLiquidBlobs => '液态团块';

  @override
  String get liveStyleMatrixRain => '矩阵雨';

  @override
  String get liveStyleBlueprint => '蓝图';

  @override
  String get liveStyleCircuit => '电路';

  @override
  String get liveStyleOscilloscope => '示波器';

  @override
  String get liveStyleRadar => '雷达';

  @override
  String get liveStyleHoneycomb => '蜂巢';

  @override
  String get liveStyleBrushedMetal => '拉丝金属';

  @override
  String get liveStyleSilk => '丝绸';

  @override
  String get liveStyleCaustics => '焦散';

  @override
  String get liveStyleBokeh => '焦外光斑';

  @override
  String get liveIntensitySubtle => '柔和';

  @override
  String get liveIntensityNormal => '正常';

  @override
  String get liveIntensityBold => '强烈';

  @override
  String get effectsLiveBackground => '动态背景';

  @override
  String get continueWithMenu => '继续使用…';

  @override
  String get continueWithMenuHint => '把这项工作交给另一个智能体或模型继续';

  @override
  String get continueWithTitle => '继续这项工作';

  @override
  String continueWithSubtitle(String title, String project) {
    return '$title · $project';
  }

  @override
  String get continueWithModelsHere => '此服务器上的模型';

  @override
  String get continueWithAgents => '此手机上的智能体';

  @override
  String get continueWithNothingReady => '没有其他智能体或模型可以接手。';

  @override
  String get continueWithLoadFailed => '无法加载智能体。请重试。';

  @override
  String get continueWithTryAgain => '重试';

  @override
  String get continueWithSignIn => '登录';

  @override
  String continueWithNeedsSignIn(String agent) {
    return '登录后即可使用 $agent。';
  }

  @override
  String continueWithLimitReached(String agent) {
    return '$agent 目前已达到上限。';
  }

  @override
  String continueWithNotReady(String agent) {
    return '$agent 在此手机上尚未就绪。';
  }

  @override
  String get continueWithNativeNote => '你的对话仍留在这里。';

  @override
  String get continueWithUseModel => '使用此模型';

  @override
  String get continueWithModelFailed => '无法切换模型。请重试。';

  @override
  String get continueWithNewNote => '带着这段对话的记录开始新对话。';

  @override
  String get continueWithSourceStays => '此对话仍然可以打开。';

  @override
  String get continueWithFactAgent => '智能体';

  @override
  String get continueWithFactModel => '模型';

  @override
  String get continueWithFactProject => '项目';

  @override
  String get continueWithPreparing => '正在准备对话记录…';

  @override
  String get continueWithSaving => '正在保存对话记录…';

  @override
  String get continueWithStarting => '正在开始对话…';

  @override
  String get continueWithLinking => '正在关联对话…';

  @override
  String get continueWithRecordTitle => '对话记录';

  @override
  String continueWithRecordSize(int kb) {
    return '约 $kb KB';
  }

  @override
  String get continueWithOlderLeftOut => '为控制记录大小，较早的轮次已被省略';

  @override
  String continueWithOmittedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '省略 $count 条消息',
      one: '省略 1 条消息',
    );
    return '$_temp0';
  }

  @override
  String get continueWithRedacted => '任何看似密码或密钥的内容都已隐藏。';

  @override
  String get continueWithReadPreview => '请先阅读预览。它无法识别所有隐私细节。';

  @override
  String get continueWithShowPreview => '显示预览';

  @override
  String get continueWithHidePreview => '隐藏预览';

  @override
  String continueWithRecordFile(String path) {
    return '保存为 $path';
  }

  @override
  String get continueWithFirstMessage => '第一条消息';

  @override
  String get continueWithFirstMessageHelp => '请保留文件名，以便智能体读取该记录。';

  @override
  String get continueWithMessageInvalid => '请写一条包含记录文件名的第一条消息。';

  @override
  String get continueWithStart => '开始并继续';

  @override
  String get continueWithCancel => '取消';

  @override
  String get continueWithPrepareAgain => '重新准备';

  @override
  String get continueWithSaveLinkAgain => '重新保存链接';

  @override
  String get continueWithOpenConversation => '打开对话';

  @override
  String get continueWithAnother => '改用其他智能体继续';

  @override
  String get continueWithErrNoWriter => '此智能体目前还无法在此保存对话记录。';

  @override
  String get continueWithErrProject => '这些对话没有关联到同一个项目。';

  @override
  String get continueWithErrTarget => '此模型不可用。请选择其他模型。';

  @override
  String get continueWithErrSource => '此对话已变更。请重新准备记录。';

  @override
  String get continueWithErrScope => '服务器或项目已变更。请重新开始。';

  @override
  String get continueWithErrRead => '无法读取对话。请重新准备。';

  @override
  String get continueWithErrTooLarge => '最新一轮内容过大，无法带过去。';

  @override
  String get continueWithErrFile => '无法保存记录。未发送任何消息。';

  @override
  String get continueWithErrUnknown => '结果未确认。再次尝试前请检查目标对话。';

  @override
  String get continueWithErrLink => '对话已开始，但无法保存其链接。';

  @override
  String get continueWithErrGeneric => '无法准备记录。请重试。';

  @override
  String continuedIn(String agent) {
    return '已在 $agent 中继续';
  }

  @override
  String continuedFrom(String title) {
    return '继续自 $title';
  }

  @override
  String continuedFromShort(String title) {
    return 'from $title';
  }

  @override
  String continuedInShort(String agent) {
    return 'continued in $agent';
  }

  @override
  String get continuedGoneShort => 'other conversation gone';

  @override
  String get continuedGone => '此对话已不可用。';

  @override
  String get continueWithChange => '更改';

  @override
  String get workServerBusyPhone => '此手机上的 OpenCode 正忙';

  @override
  String get workServerRestartBodyCommand => '正在运行的命令也会停止。你的对话会保留。';

  @override
  String get connectionReconnectingQuiet => '正在重新连接…';

  @override
  String get connectionTryNow => '立即重试';

  @override
  String kitComposerWaitsForAgent(String agent) {
    return '$agent 回复后发送';
  }

  @override
  String get textSizeTitle => '文字大小';

  @override
  String get textSizeSmallest => '最小';

  @override
  String get textSizeSmall => '小';

  @override
  String get textSizeDefault => '默认';

  @override
  String get textSizeLarge => '大';

  @override
  String get kitComposerAddToTurnNote => '智能体会在工作时读取这条内容。';

  @override
  String get continuedOpenFromList => '请在该服务器的对话中找到它。';

  @override
  String get teamModelsSection => '模型';

  @override
  String get teamModelsNote => '每个新任务都以其角色所选的模型启动。已保存的选择适用于新任务，不适用于已在运行的任务。';

  @override
  String get teamModelServerDefault => '服务器默认';

  @override
  String get teamModelServerDefaultHint =>
      '使用此服务器在未选择模型时的回退模型。它可能与你的对话使用的模型不同。';

  @override
  String get teamModelPlannerSaved => '规划器模型已保存。请再次请求，即可用它规划。';

  @override
  String get teamProjectEditorTypePath => '输入路径';

  @override
  String get teamProjectEditorChooseFolder => '选择文件夹';

  @override
  String get teamProjectEditorFolderNone => '尚未选择文件夹';

  @override
  String get teamProjectEditorFirstCommit => '帮我做第一次提交';

  @override
  String get teamProjectEditorFirstCommitManual =>
      '在该文件夹中打开终端并运行：git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorFirstCommitFailed =>
      '无法进行第一次提交。请在该文件夹中打开终端并运行：git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorStarting => '正在检查文件夹并启动规划器…';

  @override
  String get teamProjectEditorDraftRestored => '你未发送的草稿已恢复。';

  @override
  String get teamProjectEditorStartFresh => '从头开始';

  @override
  String get teamProjectEditorStarterNote => '已根据你的任务起草了第一个里程碑。请在批准前编辑。';

  @override
  String get teamRefusalRepoPathInvalid => '团队在本机上找不到该文件夹。';

  @override
  String get teamRefusalRepoPathInvalidNext => '请从列表中选择文件夹，而不要手动输入。';

  @override
  String effectsLookPreviewLabel(String name) {
    return '$name 预览。打开外观。';
  }

  @override
  String get effectsLookPreviewOffLabel => '动态背景已关闭。打开外观。';

  @override
  String get effectsLookPreviewOff => '关闭';

  @override
  String get teamProjectEditorFolderEmpty => '此文件夹尚无任何提交，而团队需要一次提交作为起点。';

  @override
  String get teamProjectEditorChooseModeBudget => '请选择单泳道或并行智能体，并设置预算或选择无限制。';

  @override
  String get teamProjectEditorFallbackSection => '可选';

  @override
  String get teamProjectEditorFallbackNote => '随角色保存，但团队暂时不会自动切换到它。';

  @override
  String get teamUiRowChecking => '正在检查…';

  @override
  String get teamRefusalCheckerUnreadable => '检查已完成，但无法读取其结果。';

  @override
  String get teamRefusalCheckerUnreadableNext =>
      '恢复会再次读取该结果，而不再询问模型。如果工作通过，它会合并到 dev，其后等待的任务即可开始。';

  @override
  String get teamProjectInterruptedResumeNote =>
      '恢复会再次读取已完成的检查，而不再询问模型。如果工作通过，它会合并到 dev，其后等待的任务即可开始。';

  @override
  String get teamProjectInterruptedResume => '恢复';

  @override
  String get widgetV2ConfigureTitle => '小组件样式';

  @override
  String get widgetProjectTitle => 'Show conversations from';

  @override
  String get widgetProjectUnavailable =>
      'Couldn’t save this choice. Try again.';

  @override
  String get widgetV2BackgroundLabel => '背景';

  @override
  String get widgetV2BackgroundSolid => '纯色';

  @override
  String get widgetV2BackgroundTinted => '着色';

  @override
  String get widgetV2BackgroundTransparent => '透明';

  @override
  String get widgetV2AccentLabel => '强调色';

  @override
  String get widgetV2AccentWallpaper => '壁纸';

  @override
  String get widgetV2AccentApp => '应用主题';

  @override
  String get widgetV2AccentGreen => '绿色';

  @override
  String get widgetV2AccentTeal => '青色';

  @override
  String get widgetV2AccentBlue => '蓝色';

  @override
  String get widgetV2AccentAmber => '琥珀色';

  @override
  String get widgetV2SaveStyle => '保存样式';

  @override
  String get widgetV2SteerTitle => '添加到本轮';

  @override
  String get widgetV2AfterReplyTitle => '在本次回复后发送';

  @override
  String get widgetV2VoiceTitle => '新消息';

  @override
  String get widgetV2MessageHint => '输入消息';

  @override
  String get widgetV2Dictate => '听写消息';

  @override
  String get widgetV2Send => '发送';

  @override
  String get widgetV2Sending => '正在发送…';

  @override
  String get widgetV2Accepted => '已发送';

  @override
  String get widgetV2StaleRequest => '对话已更改。你的消息仍在这里。';

  @override
  String get widgetV2Unavailable => '后台连接不可用。你的消息仍在这里。';

  @override
  String get widgetV2Unsupported => '此连接不支持该操作。';

  @override
  String get widgetV2Uncertain => '无法确认是否已发送。再次发送前请先查看对话。';

  @override
  String get widgetV2Busy => '请等待当前操作完成。';

  @override
  String get widgetV2InvalidRequest => '发送前请先输入消息。';

  @override
  String get widgetV2UnknownBehavior => '正在等待对话详情…';

  @override
  String get widgetV2Cancel => '取消';

  @override
  String get widgetV2PreviewLabel => '小组件预览';

  @override
  String get widgetV2PreviewTitle => '修复登录页面';

  @override
  String get widgetV2PreviewSecond => '撰写发行说明';

  @override
  String get widgetV2PreviewStep => '正在编辑文件';

  @override
  String get widgetV2PreviewWorking => '进行中';

  @override
  String get widgetV2PreviewStop => '停止';

  @override
  String get widgetV2PreviewSteer => '引导';

  @override
  String get widgetV2PreviewAllow => '允许';

  @override
  String get widgetV2SizeLabel => '预览大小';

  @override
  String get widgetV2SizeCompact => '小';

  @override
  String get widgetV2SizeCard => '中';

  @override
  String get widgetV2SizeList => '宽';

  @override
  String get widgetV2StyleStale => '此小组件已更改。请关闭后重试。';

  @override
  String get widgetV2StyleUnavailable => '无法保存样式。请重试。';

  @override
  String get widgetV2DictationUnavailable => '听写暂时不可用。你可以改为输入。';

  @override
  String get widgetV2RestartUnavailable => '此对话只能在应用内更改。';

  @override
  String get phoneSetupErrorPackageRepair =>
      '部分 Linux 软件包需要修复。继续设置以重试。详情会显示停止的原因。';

  @override
  String get agentsFailNeedsSignIn => 'Claude Code 需要你在此手机上登录。';

  @override
  String get agentsFailHelperVersion => '此智能体连接前需要先更新 Paseo。请更新后重试。';

  @override
  String get agentsFailTimeout => '手机完成检查耗时过长。请在其不太忙时重试。';

  @override
  String get agentsCheckRetry => '重试';

  @override
  String get agentsCheckUpdateHelper => '更新 Paseo 并重试';

  @override
  String get agentsCheckPrevious => '使用上一版本';

  @override
  String agentsCheckPreviousNote(String version) {
    return '版本 $version 仍在此手机上。使用前会先检查。你的登录状态和对话会保留。';
  }

  @override
  String get agentsCheckAgentChanged => '所需的智能体版本已变化。请重新检查此手机。';

  @override
  String get agentsCheckHelperChanged => '所需的 Paseo 版本已变化。请重新检查连接。';

  @override
  String get agentsCheckInstallChanged => '手机的设置已变化。请重新检查。';

  @override
  String agentsCheckDownload(
    String component,
    String downloaded,
    String total,
  ) {
    return '正在下载 $component · $downloaded/$total';
  }

  @override
  String agentsCheckDownloadUnknown(String component, String downloaded) {
    return '正在下载 $component · $downloaded';
  }

  @override
  String agentsCheckDownloading(String component) {
    return '正在下载 $component';
  }

  @override
  String agentsCheckPreparing(String component) {
    return '正在准备 $component';
  }

  @override
  String get agentsCheckPhoneTools => '手机工具';

  @override
  String get phoneSetupErrorInterrupted => '设置在完成前中断了。继续设置以重试。已完成的步骤会保留。';

  @override
  String get agentErrorProviderAuth => '模型提供商需要有效的登录。';

  @override
  String get agentErrorProviderAuthHint => '打开提供商设置以登录或更换 API 密钥，然后重试。';

  @override
  String get agentErrorUnknown => '请求未能完成。';

  @override
  String get agentErrorUnknownHint => '重新发送前请先查看对话。详情可能有助于说明发生了什么。';

  @override
  String get serverTrustHttpTitle => '使用未加密的 HTTP？';

  @override
  String get serverTrustHttpPublicBody =>
      '在此公共 HTTP 地址上，你的密码和验证码以未加密方式传输，网络上的其他人可以读取或篡改。在公共 Wi-Fi 上有风险；建议使用 HTTPS 或 Tailscale 私有连接。';

  @override
  String get serverTrustHttpAccept => '允许此服务器使用 HTTP';

  @override
  String get serverTrustCertificateTitle => '信任此证书？';

  @override
  String get serverTrustCertificateBody =>
      '此连接已加密，但本设备无法验证证书的颁发者或服务器名称。请通过另一个可信渠道，将此 SHA-256 指纹与你的服务器核对，仅在一致时才信任；之后的连接将要求使用这份证书。';

  @override
  String get serverTrustCertificateChangedTitle => '服务器证书已更改';

  @override
  String get serverTrustCertificateChangedBody =>
      '证书与你之前信任的不再一致，因此应用在发送密码前已阻止连接。这可能是证书更新，也可能是连接被拦截；信任新证书前，请先向服务器所有者重新核对指纹。';

  @override
  String get serverTrustCertificateInspect => '检查证书';

  @override
  String get serverTrustCertificateRecheck => '重新检查证书';

  @override
  String get serverTrustCertificateAccept => '信任此指纹';

  @override
  String get serverTrustCertificateReplace => '信任新证书';

  @override
  String get serverTrustCertificatePinned => '此服务器使用你已信任的证书指纹。若证书不同，连接将被阻止。';

  @override
  String get serverTrustCertificateFailed => '无法检查该证书。请检查地址和网络后重试。未信任任何内容。';

  @override
  String get serverTrustFingerprint => 'SHA-256 指纹';

  @override
  String get serverTrustInvalidPort => '请输入 1 到 65535 之间的服务器端口。';

  @override
  String get serverTrustHttpUnconfirmed =>
      '此服务器使用明文 HTTP，而你尚未确认。请编辑服务器以查看并批准 HTTP 警告，或改用 HTTPS。';

  @override
  String get serverTrustProbeFailed => '无法检查该服务器。请检查地址后重试。';

  @override
  String get serverTrustPasswordRejected => '密码被拒绝。请检查服务器密码后重试。';

  @override
  String serverTrustCertificateValidity(String from, String to) {
    return '有效期：$from 至 $to';
  }

  @override
  String get serverTrustCertificateValidityError =>
      '服务器证书已过期或尚未生效。请让服务器所有者续期，或在重新连接前检查设备时钟。';

  @override
  String get devPreviewTitle => '预览';

  @override
  String get devPreviewReload => '刷新页面';

  @override
  String get devPreviewOpenInBrowser => '在浏览器中打开';

  @override
  String get devPreviewCopyAddress => '复制地址';

  @override
  String get devPreviewLoading => '正在加载预览';

  @override
  String get devPreviewFailedTitle => '暂无响应';

  @override
  String devPreviewFailedBody(String port) {
    return '端口 $port 没有响应。应用可能仍在启动。';
  }

  @override
  String get devPreviewRetry => '重试';

  @override
  String devPreviewRemoteNote(String port, String host) {
    return '正在显示 $host 上的端口 $port。开发服务器必须监听 0.0.0.0（所有地址），此手机才能访问。';
  }

  @override
  String get devPreviewNoServerTitle => '请先连接服务器';

  @override
  String get devPreviewNoServerBody =>
      '指向本机的链接只在运行应用的那台机器上有意义。请连接服务器，或在浏览器中打开链接。';

  @override
  String get devPreviewNoHostTitle => '找不到该服务器';

  @override
  String get devPreviewNoHostBody => '此服务器的地址不完整，链接无法指向它。请改在浏览器中打开。';

  @override
  String get devPreviewUnsupportedTitle => '此处无法预览';

  @override
  String get devPreviewUnsupportedBody => '本设备无法在应用内显示该页面。请改用浏览器打开。';

  @override
  String get devPreviewMenu => '预览运行中的应用';

  @override
  String get devPreviewMenuHint => '打开此对话提到过的开发服务器';

  @override
  String get devPreviewSheetSubtitle => '此对话提到的服务器';

  @override
  String devPreviewPortTitle(String port) {
    return '端口 $port';
  }

  @override
  String get devPreviewOnPhone => '在此手机上';

  @override
  String devPreviewOnServer(String host) {
    return '在 $host 上';
  }

  @override
  String get openCode1UnsupportedTitle => '更新此服务器';

  @override
  String get openCode1Unsupported =>
      '此服务器运行的是 OpenCode 1，本应用已不再支持。请将其升级到 OpenCode 2。';

  @override
  String get openCode2UpgradeTitle => '正在升级到 OpenCode 2';

  @override
  String get openCode2UpgradeFailed =>
      '升级无法安全完成。你的 OpenCode 1 对话和文件仍保留在此手机上，未受影响。请在服务器空闲且可用空间充足时重试。';

  @override
  String get openCode2HostInstall => '安装并启动 OpenCode 2';

  @override
  String get openCode2HostInstallDetail =>
      '需要 Node.js 和 npm。只在此电脑上启动并打印其密码。请保持此终端打开。';

  @override
  String get schedulePhoneBusy => '有定时提示词正在使用手机服务器。请等它结束后重试。';

  @override
  String get schedulePhoneCheckFailed => '无法检查手机服务器。请重试。';

  @override
  String get scheduleTitle => '定时提示词';

  @override
  String get scheduleCreate => '安排提示词';

  @override
  String get scheduleEdit => '编辑计划';

  @override
  String get schedulePrompt => '提示词';

  @override
  String get scheduleTarget => '对话';

  @override
  String get scheduleThisConversation => '此对话';

  @override
  String get scheduleNewConversation => '每次运行新建对话';

  @override
  String get scheduleOnce => '一次';

  @override
  String get scheduleRepeat => '时间';

  @override
  String get scheduleTime => '时间';

  @override
  String get scheduleDelete => '删除计划';

  @override
  String get schedulePaused => '已暂停';

  @override
  String get scheduleFinished => '没有更多运行';

  @override
  String get scheduleDigest => '晨间摘要';

  @override
  String get scheduleEmpty => '还没有定时提示词';

  @override
  String get scheduleUnsupported => '此连接不支持定时提示词。';

  @override
  String get scheduleSaveFailed => '无法保存此更改。请重试。';

  @override
  String get scheduleNeedsYou => '打开对话以回应请求。';

  @override
  String get scheduleBusy => '对话已在处理中。';

  @override
  String get scheduleUnavailable => '无法连接到服务器。请确认它正在运行，并且这部手机可以访问它。';

  @override
  String get scheduleSignInAgain => '服务器需要重新输入密码。请在下次运行前打开服务器并登录。';

  @override
  String get scheduleAllowHttp => '此服务器使用未加密的 HTTP，且尚未获准。请在下次运行前打开服务器并允许 HTTP。';

  @override
  String get scheduleAgentFailed => '智能体因错误而停止。打开对话查看发生了什么。';

  @override
  String get scheduleCertificateChanged => '服务器的证书已更改。请在下次运行前打开服务器进行检查。';

  @override
  String get scheduleCertificateInvalid => '服务器的证书已过期或尚未生效。请在下次运行前打开服务器进行检查。';

  @override
  String get scheduleTargetChanged => '已保存的连接或目标已更改。请编辑此计划。';

  @override
  String get scheduleInterrupted => '运行被中断，未重新发送。';

  @override
  String get scheduleTimeout => '运行已达到时间上限。请查看对话。';

  @override
  String get scheduleUncertain => '无法确认是否已送达。再次发送前请先查看对话。';

  @override
  String get scheduleMissed => '已按你对错过运行的设置跳过。';

  @override
  String get scheduleDeleteBody => '将移除后续运行和此计划的历史记录。已发送的提示词仍保留在各自的对话中。';

  @override
  String scheduleToday(String time) {
    return '今天 $time';
  }

  @override
  String scheduleTomorrow(String time) {
    return '明天 $time';
  }

  @override
  String scheduleInMinutes(String minutes) {
    return '$minutes 分钟后';
  }

  @override
  String scheduleDeleteNamedTitle(String name) {
    return '要删除“$name”吗？';
  }

  @override
  String get openCode2MoveProgress => '正在将你的对话迁移到 OpenCode 2…';

  @override
  String openCode2MoveRetrying(int retry, int total) {
    return '手机仍在准备中。稍后重试（第 $retry/$total 次）。';
  }

  @override
  String get openCode2MoveFailedNotReady =>
      '此手机上的服务器尚未就绪。你的 OpenCode 1 对话和文件仍在此手机上，未受影响。请一分钟后再试。';

  @override
  String get openCode2MoveFailedSeparateHistory =>
      '此手机上的 OpenCode 2 已有自己的对话，因此迁移已停止以保护双方。未做任何更改。';

  @override
  String get openCode2MoveFailedStopped =>
      '迁移在完成前停止，通常是因为手机可用空间不足或服务器繁忙。你的 OpenCode 1 对话和文件仍在此手机上，未受影响。';

  @override
  String get openCode2MoveFailedNotConfirmed =>
      'OpenCode 2 未能确认你的对话已到达。你的 OpenCode 1 对话和文件仍在此手机上，未受影响。';

  @override
  String get terminalLinkTitle => '终端中的链接';

  @override
  String get terminalLinkOpen => '打开链接';

  @override
  String get terminalLinkCopy => '复制链接';

  @override
  String get terminalCopiedToClipboard => '已复制到剪贴板';

  @override
  String get chatRequestAskOutsideWrite => '在此项目之外写入？';

  @override
  String get chatRequestAskOutsideRead => '在此项目之外读取？';

  @override
  String get chatRequestAskOutsideRun => '在此项目之外运行命令？';

  @override
  String get phoneSetupStageRetrying => '已中断。正在重试';

  @override
  String get linuxMgrRow => 'Linux';

  @override
  String get linuxMgrRowDetail => '此应用内 Ubuntu 的运行程序、端口、存储和软件包';

  @override
  String get linuxMgrTitle => '本机上的 Linux';

  @override
  String get linuxMgrRunning => '运行中';

  @override
  String get linuxMgrRunningDetail => 'Linux 内运行的内容，以及停止它';

  @override
  String get linuxMgrPorts => '端口';

  @override
  String get linuxMgrPortsDetail => '正在监听的内容，以及预览';

  @override
  String get linuxMgrStorage => '存储';

  @override
  String get linuxMgrStorageDetail => '空间去向，以及清理缓存';

  @override
  String get linuxMgrPackages => '软件包';

  @override
  String get linuxMgrPackagesDetail => '已安装的软件：安装、移除和更新';

  @override
  String get linuxMgrRepair => '修复 Linux';

  @override
  String get linuxMgrRepairDetail =>
      '检查 Linux、OpenCode 和你的工具，并修复损坏的部分。不会影响你的项目。';

  @override
  String get linuxMgrRepairTitle => '修复 Linux？';

  @override
  String get linuxMgrRepairBody =>
      '检查 Linux、OpenCode 和你添加的工具，并重新安装任何缺失或损坏的部分。需要网络连接，可能需要几分钟。';

  @override
  String get linuxMgrRepairKept => '你的项目和登录信息不受影响';

  @override
  String get linuxMgrReset => '重置 Linux';

  @override
  String get linuxMgrResetDetail => '从全新的 Ubuntu 重新开始。你的项目会保留。';

  @override
  String get linuxMgrResetTitle => '重置 Linux？';

  @override
  String get linuxMgrResetBody => 'Linux 将被擦除并从头重新设置。此操作无法撤销。';

  @override
  String get linuxMgrResetLostTools => '已安装的软件包和工具：OpenCode、Python、AI 团队、语音输入';

  @override
  String get linuxMgrResetLostSignIns => '智能体登录及其设置';

  @override
  String get linuxMgrResetLostChats => '服务器在本机保存的对话';

  @override
  String get linuxMgrResetKept => '你的项目会留在手机上';

  @override
  String get linuxMgrResetWord => '重置';

  @override
  String get linuxMgrProblemBusy => '另一项任务正在使用 Linux。请稍等后重试。';

  @override
  String get linuxMgrProblemSetup => '设置正在进行。请等待其完成。';

  @override
  String get linuxMgrProblemJob => '有软件包任务正在运行。请等待其完成。';

  @override
  String get linuxMgrProblemUnavailable => 'Linux 未设置或没有应答。请打开“本机”进行设置。';

  @override
  String get linuxMgrProblemFailed => '操作未完成。请重试。';

  @override
  String get linuxMgrLoadFailedTitle => '无法读取 Linux';

  @override
  String get linuxMgrProcTitle => 'Linux 中运行的程序';

  @override
  String linuxMgrProcSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个程序',
      one: '1 个程序',
    );
    return '$_temp0 · $memory';
  }

  @override
  String get linuxMgrKindServer => 'OpenCode 服务器';

  @override
  String get linuxMgrKindPaseo => 'Paseo';

  @override
  String get linuxMgrKindAgent => '智能体';

  @override
  String get linuxMgrKindDev => '开发服务器';

  @override
  String get linuxMgrKindOther => '其他';

  @override
  String get linuxMgrAboutServer => '此应用所连接的 OpenCode 服务器。请从“本机”停止它，而不是在这里。';

  @override
  String get linuxMgrAboutPaseo => 'Paseo 在本机运行编程智能体。停止它会结束它启动的智能体。';

  @override
  String get linuxMgrAboutAgent => '一个编程智能体或 AI 团队的一部分。停止它会结束其进行中的工作。';

  @override
  String get linuxMgrAboutDev => '在你构建时用于提供项目服务的程序。需要时从项目重新启动它。';

  @override
  String get linuxMgrAboutOther => '在 Linux 内运行的程序。停止它会结束它；不会有任何东西重启它。';

  @override
  String get linuxMgrProtected => '从“本机”管理';

  @override
  String linuxMgrStopTitle(String name) {
    return '停止 $name？';
  }

  @override
  String get linuxMgrStopBody => '它会立即结束，且不会有任何东西重启它。其中未保存的工作会丢失。';

  @override
  String linuxMgrStopHelpers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '它启动的 $count 个程序会随之停止',
      one: '它启动的 1 个程序会随之停止',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopLabel(String name) {
    return '停止 $name';
  }

  @override
  String get linuxMgrStopping => '正在停止…';

  @override
  String linuxMgrStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已停止 $count 个程序',
      one: '已停止 1 个程序',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopForced(int count) {
    return '$count 个需要强制结束';
  }

  @override
  String linuxMgrStopRemaining(int count) {
    return '$count 个未能停止';
  }

  @override
  String linuxMgrStopChanged(int count) {
    return '$count 个已发生变化，未予处理';
  }

  @override
  String get linuxMgrNotStoppedTitle => '并非全部已停止';

  @override
  String get linuxMgrProcEmpty => 'Linux 中没有正在运行的程序';

  @override
  String get linuxMgrProcEmptyBody => 'OpenCode 和你的工具会在你使用时启动。';

  @override
  String get linuxMgrCopyCommand => '复制命令';

  @override
  String get linuxMgrPortsTitle => 'Linux 中的端口';

  @override
  String linuxMgrPortRow(int port) {
    return '端口 $port';
  }

  @override
  String linuxMgrPortUsedBy(String name) {
    return '由 $name 使用';
  }

  @override
  String get linuxMgrPortUnknown => '程序未知';

  @override
  String get linuxMgrPortLocal => '仅限本机';

  @override
  String get linuxMgrPortAll => '对网络中的其他设备开放';

  @override
  String get linuxMgrPortNotPage => '不是网页';

  @override
  String get linuxMgrPortPreview => '打开预览';

  @override
  String linuxMgrPortPreviewNamed(int port) {
    return '打开端口 $port 的预览';
  }

  @override
  String get linuxMgrPortCopy => '复制地址';

  @override
  String get linuxMgrPortsNote => '仅列出本机可以打开的端口。';

  @override
  String get linuxMgrPortsEmpty => '没有正在监听的内容';

  @override
  String get linuxMgrPortsEmptyBody => '开发服务器启动后，其端口会显示在这里。';

  @override
  String get linuxMgrStorageTitle => 'Linux 存储';

  @override
  String linuxMgrStorageTotal(String size) {
    return 'Linux 内已使用 $size';
  }

  @override
  String get linuxMgrCatBase => 'Linux 本身';

  @override
  String get linuxMgrCatBaseDetail => 'Ubuntu 及其系统文件';

  @override
  String get linuxMgrCatPackages => '软件包下载';

  @override
  String get linuxMgrCatPackagesDetail => '已下载的软件包和软件包列表';

  @override
  String get linuxMgrCatTools => 'Node 与缓存';

  @override
  String get linuxMgrCatToolsDetail => 'Node、npm 和设置下载';

  @override
  String get linuxMgrCatAgents => '智能体与 OpenCode';

  @override
  String get linuxMgrCatAgentsDetail =>
      'OpenCode、Paseo、Claude Code、AI 团队及其保存的设置';

  @override
  String get linuxMgrCatProjects => '项目';

  @override
  String get linuxMgrCatProjectsDetail => '你的工作。不会从此处清除。';

  @override
  String get linuxMgrCatTemp => '临时文件与日志';

  @override
  String get linuxMgrCatTempDetail => '临时文件和程序日志';

  @override
  String get linuxMgrFreeUp => '释放空间';

  @override
  String get linuxMgrCleanApt => '清除软件包下载';

  @override
  String get linuxMgrCleanAptDetail => '需要时 Linux 会重新下载';

  @override
  String get linuxMgrCleanTools => '清除 Node 与下载缓存';

  @override
  String get linuxMgrCleanToolsDetail => 'npm 和设置下载，需要时会重新获取';

  @override
  String get linuxMgrCleanTemp => '清除旧的临时文件与日志';

  @override
  String get linuxMgrCleanTempDetail => '一天未改动的文件和超过一周的日志';

  @override
  String get linuxMgrCleanAgents => '移除旧版智能体';

  @override
  String get linuxMgrCleanAgentsDetail => '不再使用的 Paseo 版本';

  @override
  String linuxMgrCleanBody(String size) {
    return '约可回收 $size。此操作无法撤销，你做过的任何内容都不会被触及。';
  }

  @override
  String get linuxMgrCleanKept => '项目、登录信息和设置会保留';

  @override
  String linuxMgrCleanDone(String size) {
    return '已释放 $size';
  }

  @override
  String get linuxMgrCleanNothing => '没有可清除的内容';

  @override
  String get linuxMgrCleanNone => '当前没有可清除的内容';

  @override
  String get linuxMgrMeasuring => '正在计算…';

  @override
  String get linuxMgrPkgTitle => '软件包';

  @override
  String linuxMgrPkgCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '已安装 $count 个软件包',
      one: '已安装 1 个软件包',
    );
    return '$_temp0';
  }

  @override
  String get linuxMgrPkgSearch => '搜索已安装的软件包';

  @override
  String get linuxMgrPkgNoMatch => '没有匹配的已安装软件包';

  @override
  String linuxMgrPkgShowAll(int count) {
    return '显示全部 $count 个软件包';
  }

  @override
  String get linuxMgrPkgUpdate => '更新系统';

  @override
  String get linuxMgrPkgUpdateDetail => '刷新软件包列表并安装可用更新';

  @override
  String get linuxMgrPkgUpdateTitle => '更新 Linux 软件包？';

  @override
  String get linuxMgrPkgUpdateBody =>
      '下载并安装最新更新。会使用你的网络，可能需要几分钟，并在 45 分钟后自行停止。';

  @override
  String get linuxMgrPkgInstall => '安装软件包';

  @override
  String get linuxMgrPkgInstallDetail => '按名称，例如 htop';

  @override
  String get linuxMgrPkgName => '软件包名称';

  @override
  String get linuxMgrPkgNameHelper => 'Ubuntu 软件包名称，用空格分隔';

  @override
  String get linuxMgrPkgNameInvalid => '每个名称只能包含小写字母、数字和 + - .';

  @override
  String get linuxMgrPkgCheck => '检查';

  @override
  String linuxMgrPkgAlready(String name) {
    return '$name 已安装';
  }

  @override
  String linuxMgrPkgUnknown(String name) {
    return '没有名为 $name 的软件包。“更新系统”会刷新软件包列表。';
  }

  @override
  String linuxMgrPkgInstallTitle(String names) {
    return '安装 $names？';
  }

  @override
  String get linuxMgrPkgInstallBody => '从 Ubuntu 下载并安装。会使用你的网络。';

  @override
  String linuxMgrPkgInstallLabel(String names) {
    return '安装 $names';
  }

  @override
  String linuxMgrPkgRemove(String name) {
    return '移除 $name';
  }

  @override
  String linuxMgrPkgRemoveTitle(String name) {
    return '移除 $name？';
  }

  @override
  String get linuxMgrPkgRemoveBody => '它将从 Linux 中移除。你的项目不受影响。';

  @override
  String linuxMgrPkgAlsoRemoved(String list) {
    return '同时移除：$list';
  }

  @override
  String get linuxMgrPkgLocked => 'Linux 或你的工具需要它。工具请从“本机”移除。';

  @override
  String linuxMgrPkgBlockedPlan(String list) {
    return '移除它还会移除 $list，而 Linux 需要这些。';
  }

  @override
  String get linuxMgrPkgChecking => '正在检查会移除哪些内容…';

  @override
  String linuxMgrJobInstalling(String names) {
    return '正在安装 $names';
  }

  @override
  String linuxMgrJobRemoving(String names) {
    return '正在移除 $names';
  }

  @override
  String get linuxMgrJobUpdating => '正在更新 Linux 软件包';

  @override
  String get linuxMgrJobKeepsGoing => '离开此页面也会继续。';

  @override
  String linuxMgrJobStopInstall(String names) {
    return '停止安装 $names';
  }

  @override
  String linuxMgrJobStopRemove(String names) {
    return '停止移除 $names';
  }

  @override
  String get linuxMgrJobStopUpdate => '停止更新';

  @override
  String get linuxMgrJobStopBody => '它会立即停止。未完成的软件包会在下次运行软件包任务时修复。';

  @override
  String linuxMgrJobDoneInstall(String names) {
    return '已安装 $names';
  }

  @override
  String linuxMgrJobDoneRemove(String names) {
    return '已移除 $names';
  }

  @override
  String get linuxMgrJobDoneUpdate => 'Linux 软件包已是最新';

  @override
  String get linuxMgrJobFailed => '操作未完成。你做过的任何内容都未受影响。请重试；它会从停止处继续。';

  @override
  String get linuxMgrJobTimedOut => '超过 45 分钟后已自行停止。请再次运行以继续。';

  @override
  String get linuxMgrJobInterrupted => '它在完成前停止了，例如应用被关闭。请再次运行以继续。';

  @override
  String get linuxMgrJobNoticeInstall => '正在安装 Linux 软件包';

  @override
  String get linuxMgrJobNoticeRemove => '正在移除 Linux 软件包';

  @override
  String get linuxMgrJobNoticeUpdate => '正在更新 Linux 软件包';

  @override
  String get linuxMgrJobDismiss => '完成';

  @override
  String linuxMgrJobStopInstallTitle(String names) {
    return '停止安装 $names？';
  }

  @override
  String linuxMgrJobStopRemoveTitle(String names) {
    return '停止移除 $names？';
  }

  @override
  String get linuxMgrJobStopUpdateTitle => '停止更新 Linux 软件包？';

  @override
  String get linuxMgrCleanAptAsk => '清除软件包下载？';

  @override
  String get linuxMgrCleanToolsAsk => '清除 Node 与下载缓存？';

  @override
  String get linuxMgrCleanTempAsk => '清除旧的临时文件与日志？';

  @override
  String get linuxMgrCleanAgentsAsk => '移除旧版智能体？';

  @override
  String get kitLogSearch => '搜索此输出';

  @override
  String get kitLogPause => '暂停跟随';

  @override
  String get kitLogFollow => '跟随新输出';

  @override
  String get kitLogPreviousMatch => '上一个匹配';

  @override
  String get kitLogNextMatch => '下一个匹配';

  @override
  String get servicesAlsoRunning => '同时在运行';

  @override
  String get servicesAlsoPhoneHint => '按它们监听的端口找到，无论它们是如何启动的。';

  @override
  String get servicesAlsoServerHint => '此服务器会列出它正在运行的命令。在其终端中启动的服务器不会在此显示。';

  @override
  String servicesPorts(int count, String ports) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '端口 $ports',
      one: '端口 $ports',
    );
    return '$_temp0';
  }

  @override
  String get servicesNoPortYet => '尚未发现端口';

  @override
  String get servicesOpenPreview => '打开预览';

  @override
  String servicesOpenPreviewNamed(String name) {
    return '预览 $name';
  }

  @override
  String servicesPreviewPort(String port) {
    return '预览端口 $port';
  }

  @override
  String get servicesSaveCommand => '保存为命令';

  @override
  String servicesStopFoundTitle(String name) {
    return '停止 $name？';
  }

  @override
  String get servicesStopFoundHint => '这会结束该程序所在的机器上的进程。它尚未保存的内容将会丢失。';

  @override
  String get servicesLogsUnavailable =>
      '此程序不是从本应用启动的，因此它的输出不会保存在应用能够读取的位置。请在其所在行选择“保存为命令”，停止这一个，然后在此启动已保存的命令以查看日志。';

  @override
  String devPreviewRunningNow(String name) {
    return '正在运行：$name';
  }

  @override
  String get devPreviewNoneFound => '未找到正在运行的应用';

  @override
  String get linuxMgrResetNotErasedTitle => 'Linux 未被重置';

  @override
  String get linuxMgrResetNotErasedBody =>
      '没有擦除任何内容。请关闭终端并停止 Linux 中运行的程序，然后重试。';

  @override
  String get linuxMgrResetDetailsLabel => '错误码';

  @override
  String get linuxMgrResetCleanupTitle => '正在重新设置 Linux';

  @override
  String linuxMgrResetForgetFailed(String name) {
    return '重置前保存的“$name”无法遗忘，仍然保留。';
  }

  @override
  String linuxMgrResetLeft(String name, String what) {
    return '重置前保存的“$name”仍保留：$what。';
  }

  @override
  String get linuxMgrResetLeftConnection => '其保存的连接';

  @override
  String get servicesAlsoPhoneFailed => '无法检查 Linux 中当前还在运行什么。几秒后会再次检查。';

  @override
  String linuxMgrProcGroupCount(int count) {
    return '$count 个进程';
  }

  @override
  String get gitRepairFailureWords => '无法读取 Git 历史。';

  @override
  String get gitRepairAction => '修复';

  @override
  String get gitRepairTitle => '修复 Git 历史';

  @override
  String gitRepairWorking(String project) {
    return '正在修复 $project…';
  }

  @override
  String get gitRepairLooking => '正在查找缺失的 Git 文件…';

  @override
  String gitRepairRestoring(int done, int total) {
    return '正在恢复 $done / $total 个 Git 文件…';
  }

  @override
  String gitRepairAskingTermux(int done, int total) {
    return '正在从 Termux 复制 $done / $total 个 Git 文件…';
  }

  @override
  String get gitRepairChecking => '正在用 Git 检查项目…';

  @override
  String gitRepairDoneTitle(String project) {
    return '$project 已修复';
  }

  @override
  String gitRepairDoneBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个 Git 文件已恢复，Git 可以重新读取该项目。',
      one: '1 个 Git 文件已恢复，Git 可以重新读取该项目。',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairHealthyTitle => '没有缺失的文件';

  @override
  String gitRepairHealthyBody(String project) {
    return 'Git 现在可以读取 $project。';
  }

  @override
  String get gitRepairStart => '开始对话';

  @override
  String get gitRepairTermuxNotAllowedTitle => '文件位于 Termux 中';

  @override
  String get gitRepairTermuxNotAllowedBody => '允许应用在 Termux 中运行命令，它就会把它们复制回来。';

  @override
  String get gitRepairAllow => '允许并修复';

  @override
  String get gitRepairTermuxNoAnswerTitle => 'Termux 没有应答';

  @override
  String get gitRepairTermuxNoAnswerBody => '请先打开一次 Termux 使其启动，再回到这里重新修复。';

  @override
  String get gitRepairAgain => '再次修复';

  @override
  String get gitRepairOpenTermux => '打开 Termux';

  @override
  String gitRepairCannotTitle(String project) {
    return '无法修复 $project';
  }

  @override
  String gitRepairMissingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '本机已不再有 $count 个 Git 文件。请从托管处重新克隆该项目，或恢复一份副本。',
      one: '本机已不再有 1 个 Git 文件。请从托管处重新克隆该项目，或恢复一份副本。',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairStillBrokenBody => 'Git 仍无法读取它。请从托管处重新克隆该项目，或恢复一份副本。';

  @override
  String get gitRepairNotRepositoryTitle => '无法修复此文件夹';

  @override
  String get gitRepairNotRepositoryBody =>
      'Only Git projects in On this phone\'s projects folder can be repaired here.';

  @override
  String get gitRepairClose => '关闭';

  @override
  String get gitProblemNotARepository => '此文件夹不是 Git 仓库。';

  @override
  String get gitProblemGitMissing => '项目所在位置未安装 Git。请在那里安装后重试。';

  @override
  String get gitProblemFolderMissing => '未找到项目文件夹。它可能已被移动或删除。';

  @override
  String get gitProblemEnvironmentUnavailable => '无法访问项目所在的位置。请确认其正在运行，然后重试。';

  @override
  String get gitProblemServerCannotRunCommands => '此服务器无法为应用运行 Git 命令。';

  @override
  String get gitProblemOutputTooLarge => '更改过多，无法在此读取。请提交或清理一些文件后重试。';

  @override
  String get gitProblemTimedOut => 'Git 响应超时。请重试。';

  @override
  String get gitProblemUnsafeRepository =>
      '此文件夹归属于其他用户，Git 无法在其中工作。请在该设备上修正文件夹的所有者。';

  @override
  String get gitProblemRepositoryLocked => '另一个 Git 命令正在使用此仓库。请等其完成后再试。';

  @override
  String get gitProblemNoUpstream => '此分支尚未与远程分支关联。请先推送。';

  @override
  String get gitProblemNoRemote => '此仓库没有可推送的远程。请先在该设备上添加一个。';

  @override
  String get gitProblemDetachedHead => '你不在任何分支上。请先切换到一个分支。';

  @override
  String get gitProblemCannotFastForward => '远程和此分支都有新提交，无法自动合并。请在该设备上合并或变基。';

  @override
  String get gitProblemPushRejected => '远程有此分支没有的提交。请先拉取，然后再次推送。';

  @override
  String get gitProblemLocalChangesBlock => '你未提交的更改会被覆盖。请先提交或丢弃它们。';

  @override
  String get gitProblemAuthRequired => '远程需要你登录。请在项目所在位置设置 Git 登录，然后重试。';

  @override
  String get gitProblemAuthRequiredSignIn => '远程需要你登录。请登录 GitHub 后重试。';

  @override
  String get gitProblemGitHubTokenRejected => 'GitHub 未接受你的登录。请重新登录 GitHub。';

  @override
  String get gitProblemNoPermission => '你的账户无法推送到此仓库。请向所有者申请访问权限。';

  @override
  String get gitProblemNetwork => '无法访问远程。请检查网络连接后重试。';

  @override
  String get gitProblemRemoteNotFound => '未找到远程仓库，或你的账户无权查看它。';

  @override
  String get gitProblemNothingSelected => 'Tick at least one file.';

  @override
  String get gitProblemEmptyMessage => 'Describe what changed first.';

  @override
  String get gitProblemNothingToCommit => '你所选的文件中没有可提交的内容。';

  @override
  String get gitProblemIdentityMissing => 'Git 需要你的姓名和邮箱才能提交。请填写后重试。';

  @override
  String get gitProblemSigningFailed => 'Git 无法签署该提交。请在该设备上检查提交签名设置。';

  @override
  String get gitProblemHookRejected => '此仓库中的某项检查阻止了该操作。“详情”中显示了它的说明。';

  @override
  String get gitProblemInvalidBranchName => '这不是有效的分支名称。请避免空格和 ~ ^ : ? * [ 等字符';

  @override
  String get gitProblemBranchExists => '已存在同名分支。';

  @override
  String get gitProblemBranchNotFound => '未找到该分支。';

  @override
  String get gitProblemConflicts => '部分文件存在冲突。请先在该设备上解决。';

  @override
  String get gitProblemOperationInProgress => '合并或变基尚未完成。请先在该设备上完成或取消。';

  @override
  String get gitProblemCannotDiscardConflict => '存在冲突的文件无法在此丢弃。';

  @override
  String get gitProblemUnknown => 'Git 未能完成。“详情”中显示了它的说明。';

  @override
  String get gitUnavailableNotConnected => '连接到服务器后才能查看其 Git 更改。';

  @override
  String get gitUnavailableNoProject => '打开项目后才能查看其 Git 更改。';

  @override
  String get gitUnavailableServerCannotRun => '此服务器无法为应用运行 Git。请改在那台电脑上使用 Git。';

  @override
  String get gitNotRepositoryTitle => '不是 Git 仓库';

  @override
  String get gitNotRepositoryBody => '此项目文件夹尚未使用 Git。';

  @override
  String get gitCleanBody => '所有内容均已提交。';

  @override
  String get gitDetachedHead => '不在任何分支上';

  @override
  String get gitNoCommitsYet => '尚无提交';

  @override
  String gitAhead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count 个提交待推送',
      one: '1 个提交待推送',
    );
    return '$_temp0';
  }

  @override
  String get gitOperationMerge => '合并未完成';

  @override
  String get gitOperationRebase => '变基未完成';

  @override
  String get gitOperationCherryPick => '拣选未完成';

  @override
  String get gitOperationRevert => '还原未完成';

  @override
  String get gitUsesOwnSignIn =>
      'Sending and getting latest use the Git sign-in already set up where this project lives.';

  @override
  String get gitChangeAdded => '新增';

  @override
  String get gitChangeModified => '已修改';

  @override
  String get gitChangeDeleted => '已删除';

  @override
  String get gitChangeRenamed => '已重命名';

  @override
  String get gitChangeCopied => '已复制';

  @override
  String get gitChangeTypeChanged => '类型已更改';

  @override
  String get gitChangeUntracked => '新文件';

  @override
  String get gitChangeConflicted => '冲突';

  @override
  String get gitActionPull => '拉取';

  @override
  String get gitActionPush => '推送';

  @override
  String get gitActionCommit => '提交';

  @override
  String get gitActionSwitchBranch => '切换分支';

  @override
  String get gitActionCreateBranch => '新建分支';

  @override
  String get gitActionDiscard => '丢弃更改';

  @override
  String get gitActionRefresh => '刷新';

  @override
  String get gitActionSaveIdentity => '保存姓名和邮箱';

  @override
  String get gitBusyPull => 'Getting the latest…';

  @override
  String get gitBusyPush => 'Sending…';

  @override
  String get gitBusyCommit => 'Saving…';

  @override
  String get gitBusySwitchBranch => '正在切换分支…';

  @override
  String get gitBusyCreateBranch => '正在创建分支…';

  @override
  String get gitBusyDiscard => '正在丢弃更改…';

  @override
  String get gitBusyRefresh => '正在检查更改…';

  @override
  String get gitBusySaveIdentity => '正在保存…';

  @override
  String get gitDoneUpToDate => '已是最新。';

  @override
  String get gitDonePulled => 'Got the latest changes.';

  @override
  String get gitDonePushed => 'Sent.';

  @override
  String get gitDoneSwitched => '已切换分支。';

  @override
  String get gitDoneCreated => '已创建并切换到该分支。';

  @override
  String get gitDoneDiscarded => '更改已丢弃。';

  @override
  String get gitDoneIdentitySaved => '已为此仓库保存你的姓名和邮箱。';

  @override
  String get gitCommitMessageHint => '描述改了什么';

  @override
  String get gitWriteItForMe => 'Write it for me';

  @override
  String get gitAskAgentFixConflicts => 'Ask the agent to fix conflicts';

  @override
  String gitConflictCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files have conflicts',
      one: '1 file has conflicts',
    );
    return '$_temp0';
  }

  @override
  String gitAskAgentFixConflictsPrompt(String files) {
    return 'Please resolve the merge conflicts in $files. Keep what both sides meant, then tell me what you chose.';
  }

  @override
  String get gitBranchNameHint => '分支名称';

  @override
  String get gitIdentityName => '姓名';

  @override
  String get gitIdentityEmail => '邮箱';

  @override
  String get gitHubSignIn => '登录 GitHub';

  @override
  String get gitHubSignOut => '退出 GitHub 登录';

  @override
  String get gitHubSignOutTitle => 'Sign out of GitHub?';

  @override
  String gitHubSignedInAs(String login) {
    return '已以 $login 登录 GitHub';
  }

  @override
  String get gitHubRequestingCode => '正在获取登录码…';

  @override
  String get gitHubEnterCode => '在 GitHub 上输入此码以完成登录。';

  @override
  String get gitHubOpenGitHub => '打开 GitHub';

  @override
  String get gitHubCopyCode => '复制码';

  @override
  String get gitHubWaiting => '正在等待你在 GitHub 上完成…';

  @override
  String get gitHubFinishing => '正在完成登录…';

  @override
  String get gitHubCancel => '取消';

  @override
  String get gitHubSignOutBody =>
      '应用会在此设备上忘记你的 GitHub 登录。如需彻底移除其访问权限，还请在 GitHub 上撤销。';

  @override
  String get gitHubManageAccess => '在 GitHub 上管理访问权限';

  @override
  String get gitHubPushOverHttps => '推送到 GitHub 会通过 HTTPS 使用此登录。你的 SSH 设置不变。';

  @override
  String get gitHubProblemExpired => '该码在输入前已过期。请重新登录以获取新码。';

  @override
  String get gitHubProblemDenied => '登录已在 GitHub 上取消。';

  @override
  String get gitHubProblemDisabled => '此应用尚未启用 GitHub 登录。';

  @override
  String get gitHubProblemNetwork => '无法访问 GitHub。请检查网络连接后重试。';

  @override
  String get gitHubProblemTokenRejected => '你的 GitHub 登录已结束。请重新登录。';

  @override
  String get gitHubProblemUnexpected => 'GitHub 返回了应用无法识别的应答。请重试。';

  @override
  String get gitProblemInvalidRemoteName =>
      'That isn’t a valid remote name. Use letters, numbers, dots, dashes or underscores.';

  @override
  String get gitProblemInvalidRemoteUrl =>
      'That address doesn’t look right. Use one like https://github.com/owner/name.';

  @override
  String get gitProblemRemoteAddressHasPassword =>
      'Take the password or token out of the address. Sign in to GitHub instead, so no secret is saved in the repository.';

  @override
  String get gitProblemRemoteExists =>
      'A remote with that name is already set up. Choose another name.';

  @override
  String get gitProblemInvalidFolderName =>
      'That isn’t a usable folder name. Use letters, numbers, dots, dashes or underscores.';

  @override
  String get gitProblemFolderExists =>
      'A folder with that name is already there. Choose another name.';

  @override
  String get gitActionAddRemote => 'Add remote';

  @override
  String get gitActionClone => 'Clone';

  @override
  String get gitBusyAddRemote => 'Adding the remote…';

  @override
  String get gitBusyClone => 'Cloning…';

  @override
  String get gitDoneRemoteAdded => 'Remote added.';

  @override
  String gitDoneCloned(String path) {
    return 'Cloned into $path. Open it from your projects.';
  }

  @override
  String get gitSheetTitle => 'Git';

  @override
  String gitSheetChipSemantics(String branch) {
    return 'Git, branch $branch. Opens the Git actions.';
  }

  @override
  String get gitSheetChipSemanticsPlain => 'Git. Opens the Git actions.';

  @override
  String get gitGetLatest => 'Get latest';

  @override
  String get gitSend => 'Send';

  @override
  String get gitSaveChanges => 'Save changes';

  @override
  String gitSaveChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files',
      one: '1 changed file',
      zero: 'No changed files',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveFilesLabel => 'Files to include';

  @override
  String gitSaveButton(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Save $count files',
      one: 'Save 1 file',
    );
    return '$_temp0';
  }

  @override
  String gitSaveOnBranch(String branch) {
    return 'On $branch';
  }

  @override
  String get gitSwitchRow => 'Switch branch';

  @override
  String gitSwitchRowOn(String branch) {
    return 'On $branch';
  }

  @override
  String gitBranchRemoteOnly(String remote) {
    return 'On $remote, not on this device yet';
  }

  @override
  String get gitBranchCurrent => 'Current branch';

  @override
  String gitNewBranchFrom(String branch) {
    return 'New branch from $branch';
  }

  @override
  String get gitCloneRow => 'Clone a repository';

  @override
  String get gitCloneRowHint => 'Copy a repository next to this project';

  @override
  String get gitCloneAddressLabel => 'Repository address';

  @override
  String get gitCloneFolderLabel => 'Folder name';

  @override
  String gitCloneInto(String path) {
    return 'Clones into $path';
  }

  @override
  String get gitCloneButton => 'Clone';

  @override
  String get gitRemotesRow => 'Remotes';

  @override
  String get gitRemotesRowNone => 'None yet';

  @override
  String get gitRemotesTitle => 'Remotes';

  @override
  String get gitRemotesEmpty =>
      'This project has no remote yet. Add one to send your work somewhere.';

  @override
  String get gitRemoteAddTitle => 'Add a remote';

  @override
  String get gitRemoteNameLabel => 'Name';

  @override
  String get gitRemoteAddressLabel => 'Address';

  @override
  String get gitIdentityTitle => 'Your name on saves';

  @override
  String get gitHubSignedOutHint =>
      'Sign in once with a code. There is no token to paste.';

  @override
  String get gitHubSignInTitle => 'GitHub sign-in';

  @override
  String get gitHubSignInWhy =>
      'Sending to GitHub needs your sign-in. Get a code here, enter it on GitHub, and the app stores the sign-in on this device only.';

  @override
  String get gitHubSignOutConfirm => 'Sign out';

  @override
  String gitUsesOwnSignInOn(String host) {
    return 'Sending and getting latest use the Git sign-in set up on $host.';
  }

  @override
  String gitDoneSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'Saved $_temp0.';
  }

  @override
  String get gitDoneSavedPlain => 'Saved.';

  @override
  String get gitChecking => 'Checking…';

  @override
  String get gitCheckFailed => 'Couldn’t check, tap to try';

  @override
  String gitNewCount(int count) {
    return '$count new';
  }

  @override
  String get gitIdentityWhy =>
      'Saves in this project carry this name and email, so your work is yours when you send it.';

  @override
  String get kitStepSliderLess => 'A';

  @override
  String get kitStepSliderMore => 'A';

  @override
  String get appearanceIntensityHelp =>
      'Your conversation stays on top and easy to read.';

  @override
  String get appearanceTextHelp =>
      'Applies to the whole app, on top of your phone\'s text size.';

  @override
  String kitCarouselPlace(int page, int count) {
    return '$page of $count';
  }

  @override
  String get gitPullNotOnRemote => 'Not on the remote yet. Send it first.';

  @override
  String get gitPullNoRemote => 'Add a remote below first.';

  @override
  String get gitSendNoRemote => 'No remote yet. Add one below.';

  @override
  String get chatSteerHint => 'Steer while it works…';

  @override
  String chatSteerReadsAfterCommand(String agent) {
    return '$agent reads this when the command finishes';
  }

  @override
  String chatSteerReadsAfterStep(String agent) {
    return '$agent reads this when this step finishes';
  }

  @override
  String get chatSteerAgentFallback => 'The agent';

  @override
  String get chatSteerStopCommand => 'Stop the command';

  @override
  String get chatSteerStopStep => 'Stop this step';

  @override
  String get chatSendLaterOrRepeat => 'Send later or repeat';

  @override
  String get chatSendLaterSaved => 'Scheduled. It is in Scheduled prompts.';

  @override
  String get composerSettingModel => 'Model';

  @override
  String get composerSettingPlan => 'Plan';

  @override
  String get composerSettingApprovalDetail =>
      'Allow what it asks for without asking you';

  @override
  String get chatAutoApproveOn => 'Auto-approve is on';

  @override
  String get chatAutoApproveTurnOff => 'Turn off';

  @override
  String get chatNoModelSignedIn => 'No model signed in';

  @override
  String get chatNoModelSignIn => 'Sign in';

  @override
  String gitSendTo(String place, String branch) {
    return 'Send to $place · $branch';
  }

  @override
  String get gitPlaceGitHub => 'GitHub';

  @override
  String get gitStateUpToDate => 'Up to date';

  @override
  String get gitStateNothingToSend => 'Nothing to send';

  @override
  String get gitStateFirstSend => 'First time, links this branch';

  @override
  String get gitDetailsBranch => 'Branch';

  @override
  String get gitDetailsGetsFrom => 'Get latest from';

  @override
  String get gitDetailsSendsTo => 'Send to';

  @override
  String get gitDetailsAddress => 'Remote address';

  @override
  String gitDoneGotN(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new changes',
      one: '1 new change',
    );
    return 'Got $_temp0.';
  }

  @override
  String reviewSummary(int files, int added, int removed) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files files changed',
      one: '1 file changed',
    );
    return '$_temp0 · +$added −$removed';
  }

  @override
  String get reviewAlreadyApplied =>
      'The agent already made these changes in your project.';

  @override
  String get reviewLooksGood => 'Looks good';

  @override
  String get reviewUndoAll => 'Undo all';

  @override
  String get reviewSendBackWithNote => 'Send back with a note';

  @override
  String reviewUndoTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Undo $count changed files?',
      one: 'Undo 1 changed file?',
    );
    return '$_temp0';
  }

  @override
  String get reviewUndoBody =>
      'Your project goes back to how it was before the agent started. The conversation stays.';

  @override
  String get chatsNewBuildPrompt => 'What would you like to build?';

  @override
  String get chatsNewProjectRow => 'Project';

  @override
  String get chatsNewModelRow => 'Model';

  @override
  String get chatsNewSeparateCopyDetail =>
      'Works on its own copy, so your files stay as they are.';

  @override
  String get modelPickerSheetTitle => 'Model';

  @override
  String modelPickerAllModels(int count) {
    return 'All models ($count)';
  }

  @override
  String get modelPickerReasonDefault => 'Default';

  @override
  String get modelPickerReasonRecent => 'Recently used';

  @override
  String get modelPickerReasonCheapest => 'Cheapest';

  @override
  String get modelPickerFree => 'Free';

  @override
  String get modelPickerScopeNewSessions => 'For new conversations';

  @override
  String get modelPickerScopeSession => 'For this conversation';

  @override
  String get searchRecentTitle => 'Recent';

  @override
  String chatsHomeUnreachableShort(String servers) {
    return '$servers isn\'t answering';
  }

  @override
  String chatProjectChipFilesSemantics(String name) {
    return 'Project $name. Opens its files and tools.';
  }

  @override
  String chatProjectServerChipFilesSemantics(String name, String server) {
    return 'Project $name on $server. Opens its files and tools.';
  }

  @override
  String get chatTurnAllFiles => 'All files';

  @override
  String chatTurnAllFilesMore(int count) {
    return 'All files · $count more changed';
  }

  @override
  String chatTurnFileLinesAdded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines added',
      one: '1 line added',
    );
    return '$_temp0';
  }

  @override
  String chatTurnFileLinesRemoved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines removed',
      one: '1 line removed',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubSearch => 'Search settings';

  @override
  String get settingsHubServer => 'Server';

  @override
  String get settingsHubHelp => 'Help';

  @override
  String get settingsHubServerOnPhone => 'On this phone';

  @override
  String settingsHubServerLine(String where, String state) {
    return '$where · $state';
  }

  @override
  String get settingsHubServerNone => 'Not set up yet';

  @override
  String gitSaveAndSendTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Save $count files and send to $place · $branch',
      one: 'Save 1 file and send to $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSendSavesTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Send $count saves to $place · $branch',
      one: 'Send 1 save to $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveAndSendTitle => 'Save and send';

  @override
  String get gitSaveOnly => 'Save without sending';

  @override
  String gitMessageAddOne(String file) {
    return 'Add $file';
  }

  @override
  String gitMessageUpdateOne(String file) {
    return 'Update $file';
  }

  @override
  String gitMessageUpdateTwo(String first, String second) {
    return 'Update $first and $second';
  }

  @override
  String gitMessageUpdateMany(String first, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Update $first and $count more files',
      one: 'Update $first and 1 more file',
    );
    return '$_temp0';
  }

  @override
  String gitDoneSavedAndSent(String place, String branch) {
    return 'Saved and sent to $place · $branch.';
  }

  @override
  String gitSavedNotSent(String reason) {
    return 'Saved here, not sent yet. $reason';
  }

  @override
  String get gitMoreLabel => 'Remotes, clone and details';

  @override
  String gitBusySendingTo(String place, String branch) {
    return 'Sending to $place · $branch…';
  }

  @override
  String gitGetNewFrom(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Get $count new changes from $place · $branch',
      one: 'Get 1 new change from $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSavedSignInFirst(String place) {
    return 'Saved here, not sent yet. $place needs you to sign in first.';
  }

  @override
  String get thisPhoneAnswerRunning => 'Everything is running';

  @override
  String thisPhoneAnswerMemory(String size) {
    return 'Uses about $size of memory';
  }

  @override
  String thisPhoneAnswerStorage(String size) {
    return 'Takes $size of storage';
  }

  @override
  String get thisPhoneAnswerConnect => 'Use this phone\'s server';

  @override
  String get thisPhoneAnswerHeatPaused => 'Paused, your phone is warm';

  @override
  String get thisPhoneAnswerHeatStopped => 'Stopped, your phone is hot';

  @override
  String thisPhoneAnswerHeatResumeIn(String time) {
    return 'The AI Team resumes by itself in $time';
  }

  @override
  String get thisPhoneAnswerHeatResumeCool =>
      'The AI Team resumes by itself once your phone cools';

  @override
  String get thisPhoneAnswerHeatStays =>
      'The AI Team stays paused until you resume it';

  @override
  String get thisPhoneAnswerOff => 'The server is off';

  @override
  String get thisPhoneAnswerOffLine => 'It stays off until you start it.';

  @override
  String get thisPhoneAnswerAndroidClosed => 'Android closed the server';

  @override
  String get thisPhoneAnswerStoppedItself => 'The server stopped by itself';

  @override
  String thisPhoneAnswerRestartsIn(String time) {
    return 'It restarts by itself in $time';
  }

  @override
  String get thisPhoneAnswerRestartsSoon => 'It restarts by itself in a moment';

  @override
  String get thisPhoneAnswerRestarting => 'Restarting by itself';

  @override
  String get thisPhoneAnswerGaveUp =>
      'It kept stopping, so it won\'t restart by itself.';

  @override
  String get thisPhoneAnswerRestartNow => 'Restart now';

  @override
  String get thisPhoneAnswerStartFailed => 'The server didn\'t start';

  @override
  String get thisPhoneAnswerInstallFailed => 'Setup didn\'t finish';

  @override
  String get thisPhoneAnswerCheckFailed => 'Couldn\'t check the server';

  @override
  String get thisPhoneAnswerStopFailed => 'The server didn\'t stop';

  @override
  String get thisPhoneAnswerSwitchStopped => 'The switch didn\'t finish';

  @override
  String get thisPhoneAnswerTermux => 'Can\'t reach Termux';

  @override
  String get thisPhoneAnswerRecovery => 'The server can\'t restart safely';

  @override
  String get thisPhoneAnswerNotSetUp => 'OpenCode isn\'t on this phone yet';

  @override
  String get thisPhoneAnswerSettingUp => 'Setting up the server';

  @override
  String get thisPhoneAnswerStarting => 'Starting the server';

  @override
  String get thisPhoneAnswerStopping => 'Stopping the server';

  @override
  String get thisPhoneAnswerChecking => 'Checking the server';

  @override
  String get thisPhoneAnswerRemoving => 'Removing the server';

  @override
  String get thisPhoneAnswerConnecting => 'Connecting to the server';

  @override
  String get thisPhoneRestartRow => 'Restart OpenCode on this phone';

  @override
  String get thisPhoneRestartTitle => 'Restart the server?';

  @override
  String get thisPhoneRestartBusy =>
      'A reply is being written. Restarting stops it; your conversations are kept.';

  @override
  String thisPhoneAboutSeconds(int seconds) {
    return 'about $seconds s';
  }

  @override
  String thisPhoneAboutMinutes(int minutes) {
    return 'about $minutes min';
  }

  @override
  String get thisPhoneAnswerStartFailedLine =>
      'Start it again; Details below says what went wrong.';

  @override
  String get serverSwitcherAddServer => 'Add a server';

  @override
  String get serverSwitcherNotAnswering => 'Isn\'t answering';

  @override
  String serverSwitcherChecksAgain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'checks again in $count min',
      one: 'checks again in 1 min',
    );
    return '$_temp0';
  }

  @override
  String serverSwitcherTryAgainLabel(String server) {
    return 'Try $server again';
  }

  @override
  String get serverSwitcherOpenRequest => 'Open';

  @override
  String serverSwitcherOpenRequestLabel(String target) {
    return 'Open $target';
  }

  @override
  String serverSwitcherAllowLabel(String command, String server) {
    return 'Allow $command once on $server';
  }

  @override
  String serverSwitcherUndoLabel(String server) {
    return 'Undo the answer on $server';
  }

  @override
  String kitRequestMoreTargets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '+$count more',
      one: '+1 more',
    );
    return '$_temp0';
  }

  @override
  String kitRequestChangeSize(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String chatRequestAskRunIn(String command, String project) {
    return 'Run $command in $project?';
  }

  @override
  String chatRequestAskRunNamed(String command) {
    return 'Run $command?';
  }

  @override
  String chatRequestAskRunAnyIn(String project) {
    return 'Run a command in $project?';
  }

  @override
  String chatRequestAskEditNamed(String file) {
    return 'Edit $file?';
  }

  @override
  String chatRequestAskEditFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Edit $count files?',
      one: 'Edit 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskCreateNamed(String file) {
    return 'Create $file?';
  }

  @override
  String chatRequestAskCreateFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Create $count files?',
      one: 'Create 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskDeleteNamed(String file) {
    return 'Delete $file?';
  }

  @override
  String chatRequestAskDeleteFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count files?',
      one: 'Delete 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskReadNamed(String file) {
    return 'Read $file?';
  }

  @override
  String chatRequestAlwaysAllowNamed(String what) {
    return 'Always allow $what';
  }

  @override
  String get chatRequestAlwaysAllowEdits => 'Always allow edits';

  @override
  String get chatRequestAlwaysAllowAnyCommand => 'Always allow every command';

  @override
  String get chatRequestAlwaysEditsWhat => 'edits';

  @override
  String get chatRequestAlwaysAnyCommandWhat => 'every command';

  @override
  String get filesAskAboutFile => 'Ask about this file';

  @override
  String filesMenuChanges(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Changes · $count files',
      one: 'Changes · 1 file',
      zero: 'Changes',
    );
    return '$_temp0';
  }

  @override
  String filesProjectSwitchLabel(String name) {
    return 'Project $name. Switch project';
  }

  @override
  String get scheduleNew => 'New scheduled prompt';

  @override
  String get scheduleAsk => 'What should it do?';

  @override
  String get scheduleAskHint => 'Run the tests and sum up what failed';

  @override
  String get scheduleEveryDay => 'Every day';

  @override
  String get scheduleWeekdaysShort => 'Weekdays';

  @override
  String get scheduleCustom => 'Custom';

  @override
  String scheduleRuleDaily(String time) {
    return 'Every day at $time';
  }

  @override
  String scheduleRuleWeekdays(String time) {
    return 'Every weekday at $time';
  }

  @override
  String scheduleRuleHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Every $hoursString hours',
      one: 'Every hour',
    );
    return '$_temp0';
  }

  @override
  String scheduleForTime(String time) {
    return 'Schedule for $time';
  }

  @override
  String scheduleForWhen(String when) {
    return 'Schedule for $when';
  }

  @override
  String scheduleForHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Schedule every $hoursString hours',
      one: 'Schedule every hour',
    );
    return '$_temp0';
  }

  @override
  String scheduleForToday(String time) {
    return 'Schedule for today, $time';
  }

  @override
  String scheduleForTomorrow(String time) {
    return 'Schedule for tomorrow, $time';
  }

  @override
  String scheduleDayAt(String day, String time) {
    return '$day, $time';
  }

  @override
  String scheduleSent(String when) {
    return 'Sent $when';
  }

  @override
  String scheduleNotSent(String when) {
    return 'Didn\'t go out $when';
  }

  @override
  String scheduleSkippedWhen(String when) {
    return 'Skipped $when';
  }

  @override
  String get scheduleSendingNow => 'Sending now';

  @override
  String get scheduleDayToday => 'today';

  @override
  String get scheduleDayYesterday => 'yesterday';

  @override
  String get scheduleWheelToday => 'Today';

  @override
  String get scheduleWheelTomorrow => 'Tomorrow';

  @override
  String get scheduleWheelDay => 'Day';

  @override
  String get scheduleWheelHowOften => 'How often';

  @override
  String get scheduleClosedNote =>
      'Runs even with the app closed, sometimes a little late.';

  @override
  String get scheduleNeedPrompt => 'Write what it should do first.';

  @override
  String get scheduleNeedFuture => 'Pick a time later than now.';

  @override
  String get scheduleInThisConversation => 'In this conversation';

  @override
  String get scheduleOpenRun => 'Open the conversation';

  @override
  String get scheduleDigestShort => 'A note each morning about what ran';

  @override
  String get scheduleExactNote => 'Runs may start a little late.';

  @override
  String get scheduleExactAllow => 'Allow exact timing';

  @override
  String get scheduleNotifyOff =>
      'Notifications are off, so a run won\'t tell you it went out.';

  @override
  String get inboxTitle => 'Inbox';

  @override
  String get inboxEmptyTitle => 'Nothing waits for you';

  @override
  String get inboxFinishedLabel => 'Finished, not opened yet';

  @override
  String inboxOpenLabel(String conversation) {
    return 'Open $conversation';
  }

  @override
  String inboxOpenOnServer(String server) {
    return 'Open on $server';
  }

  @override
  String get inboxWaitingForAnswer => 'Waiting for your answer';

  @override
  String get inboxAsksPermission => 'Asks for your permission';

  @override
  String get inboxAsksQuestion => 'Asks you a question';

  @override
  String inboxUnreachable(String servers) {
    return '$servers isn\'t answering. What waits there isn\'t shown.';
  }

  @override
  String get kitRequestOpen => 'Open';

  @override
  String goToNeedsYouHint(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count waiting',
      one: '1 waiting',
      zero: 'Nothing waits',
    );
    return '$_temp0';
  }

  @override
  String inboxEmptyPartialLine(String servers) {
    return '$servers isn\'t answering, so it may have more';
  }

  @override
  String get kitFieldOptional => 'Optional';

  @override
  String get reportProblemAttachedLead =>
      'The error details and app information are attached.';

  @override
  String get reportProblemJobLead =>
      'The job\'s log, the error details and app information are attached.';

  @override
  String get reportProblemJobNoLogLead =>
      'No log was kept for this job. The error details and app information are attached.';

  @override
  String reportProblemKeptLead(String when) {
    return 'Noticed $when. Its details and the app information are attached.';
  }

  @override
  String get reportProblemNothingTitle => 'No error noticed';

  @override
  String get reportProblemNothingLead =>
      'Say what went wrong. Your words and the app information go with the report.';

  @override
  String get reportProblemPrivacy =>
      'Passwords, keys and server addresses are taken out.';

  @override
  String get reportProblemAddLabel => 'Anything to add?';

  @override
  String get reportProblemAddHint => 'What were you doing when it happened?';

  @override
  String get reportProblemSendGitHub => 'Send as a GitHub issue';

  @override
  String get reportProblemSendHint =>
      'Review it on GitHub before posting. It will be public.';

  @override
  String get reportProblemCopyShort => 'Copy';

  @override
  String get reportProblemKeptLink => 'Everything this phone kept';

  @override
  String welcomePhoneCost(String time, String size) {
    return '$time · 下载 $size';
  }

  @override
  String get welcomePhoneSetUp => '在这部手机上设置';

  @override
  String welcomePhoneSettingUp(int percent) {
    return '正在设置 · $percent%';
  }

  @override
  String welcomePhoneStopped(int percent) {
    return '设置已完成 $percent%';
  }

  @override
  String get welcomePhoneContinue => '继续设置';

  @override
  String get welcomePhoneReady => '已安装，可以使用';

  @override
  String get welcomePhoneOpen => '在这部手机上开始';

  @override
  String get welcomeComputerLine => '用代码配对，或输入它的地址';

  @override
  String get welcomeComputerConnect => '连接到我的电脑';

  @override
  String get welcomeMoreWays => '其他设置方式';

  @override
  String get welcomeDemoLine => '模拟对话';

  @override
  String kitProgressRingPercent(String percent) {
    return '$percent%';
  }

  @override
  String get kitProgressRingPercentSign => '%';

  @override
  String kitProgressRingSteps(String done, String total) {
    return '$done of $total';
  }

  @override
  String get kitProgressRingFinished => 'Finished';

  @override
  String phoneSetupRingDownloading(String name) {
    return 'Downloading $name';
  }

  @override
  String phoneSetupRingUnpacking(String name) {
    return 'Unpacking $name';
  }

  @override
  String phoneSetupRingInstalling(String name) {
    return 'Installing $name';
  }

  @override
  String get phoneSetupRingChecking => 'Checking this phone';

  @override
  String get phoneSetupRingPausedTitle => 'Setup is paused';

  @override
  String get phoneSetupRingInterruptedTitle => 'Setup was interrupted';

  @override
  String get phoneSetupRingStoppedNote =>
      'Finished steps stay, and downloads pick up where they left off.';

  @override
  String phoneSetupRingMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'About $minutes min left',
      one: 'About 1 min left',
    );
    return '$_temp0';
  }

  @override
  String get phoneSetupRingUnderMinute => 'Less than a minute left';

  @override
  String get phoneSetupRingWifi => 'Wi-Fi';

  @override
  String get phoneSetupRingMobileData => 'Mobile data';

  @override
  String get phoneSetupRingSafeToLeave =>
      'Safe to leave. We\'ll let you know when it\'s ready.';

  @override
  String get phoneSetupRingSafeToLeaveQuiet =>
      'Safe to leave. Setup keeps going in the background.';

  @override
  String get phoneSetupRingPause => 'Pause setup';

  @override
  String get phoneSetupRingDetailsTitle => 'Setup details';

  @override
  String kitProgressRingOfTotal(String total) {
    return 'of $total';
  }

  @override
  String get kitProgressRingStepsDone => 'steps done';

  @override
  String get devPreviewDetails => 'Preview details';

  @override
  String get devPreviewShowDetails => 'Show details';

  @override
  String get devPreviewAddress => 'Address';

  @override
  String get devPreviewAskHint => 'Ask for a change…';

  @override
  String get devPreviewAskSend => 'Send to the conversation';

  @override
  String get devPreviewAskStopAndSend => 'Stop the agent and send';

  @override
  String get devPreviewAskVoice => 'Speak your change';

  @override
  String get devPreviewAskListening => 'Listening…';

  @override
  String get devPreviewAskDoneSpeaking => 'Done speaking';

  @override
  String get devPreviewAskSent => 'Sent to the conversation';

  @override
  String get devPreviewAskSentJoins => 'Added to what the agent is doing';

  @override
  String get devPreviewAskSentAfter => 'Sent. It runs after the current reply';

  @override
  String get devPreviewAskSentRestart =>
      'Sent. The agent started again with it';

  @override
  String get devPreviewAskNotSent => 'Didn\'t send. Your words are still here';

  @override
  String get devPreviewAskNotHeard =>
      'Didn\'t catch that. Try again, or type it';

  @override
  String teamMergeGateQuestion(int count, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Merge $count changes into $branch?',
      one: 'Merge 1 change into $branch?',
      zero: 'Merge the changes into $branch?',
    );
    return '$_temp0';
  }

  @override
  String teamMergeGateChecksPassed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'all $count checks passed',
      one: 'the check passed',
    );
    return '$_temp0';
  }

  @override
  String get teamMergeGateApprove => 'Approve the merge';

  @override
  String get teamMergeGateIfIgnored =>
      'The changes wait on their branch. Nothing is merged until you say so.';

  @override
  String teamMergeGateAnnouncement(String question) {
    return 'Ready to merge. $question';
  }

  @override
  String get teamMergeGateStale => 'Merge is off until the team answers again.';

  @override
  String teamTaskNowOn(String who, String step) {
    return 'Now: $who on $step';
  }

  @override
  String teamTaskNowWorker(String who) {
    return 'Now: $who is working';
  }

  @override
  String get teamTaskNowWorking => 'Now: working on your task';

  @override
  String teamTaskNowStarting(String who) {
    return 'Now: $who is starting';
  }

  @override
  String get teamTaskNowStartingWorker => 'Now: starting a worker';

  @override
  String get teamTaskNowWaitingWorker => 'Now: waiting for a worker';

  @override
  String get teamTaskNowPlanning => 'Now: planning the steps';

  @override
  String get teamTaskNowReviewing => 'Now: reviewing the changes';

  @override
  String get teamTaskNextReview => 'Next: review';

  @override
  String get teamTaskNextMerge => 'Next: merge';

  @override
  String get teamTaskNextWork => 'Next: the worker begins';

  @override
  String get teamTaskStepsDone => 'All steps done';

  @override
  String teamTaskFinishedAgo(String age) {
    return 'Finished $age ago';
  }

  @override
  String get kitComposerTrayClose => 'Close';

  @override
  String get composerTrayPhotos => 'Photos';

  @override
  String get composerTrayCamera => 'Camera';

  @override
  String get composerTrayFile => 'File';

  @override
  String get composerTrayMore => 'More';

  @override
  String chatLiveReading(String name) {
    return 'Reading $name';
  }

  @override
  String get chatLiveReadingAny => 'Reading a file';

  @override
  String chatLiveEditing(String name) {
    return 'Editing $name';
  }

  @override
  String get chatLiveEditingAny => 'Editing files';

  @override
  String chatLiveWriting(String name) {
    return 'Writing $name';
  }

  @override
  String get chatLiveWritingAny => 'Writing a file';

  @override
  String chatLiveRunning(String name) {
    return 'Running $name';
  }

  @override
  String get chatLiveRunningAny => 'Running a command';

  @override
  String chatLiveSearching(String name) {
    return 'Searching for $name';
  }

  @override
  String get chatLiveSearchingAny => 'Searching the project';

  @override
  String get chatLiveSearchingWeb => 'Searching the web';

  @override
  String get chatLivePlanning => 'Planning the steps';

  @override
  String get terminalSessionsLabel => 'Open terminals';

  @override
  String get terminalSessionEnded => 'ended';

  @override
  String localTerminalStopNamed(String name) {
    return 'Stop $name';
  }

  @override
  String localTerminalCloseNamed(String name) {
    return 'Close $name';
  }

  @override
  String get terminalDevServerRunning => 'Dev server running';

  @override
  String get terminalOpenPreview => 'Open preview';

  @override
  String terminalPlaceLabel(String place) {
    return 'Runs on $place';
  }

  @override
  String terminalPlaceSwitchLabel(String place) {
    return 'Runs on $place, change where it runs';
  }

  @override
  String get terminalScreenDetailState => 'State';

  @override
  String get terminalStateRunning => 'Running';

  @override
  String get terminalStateEnded => 'Ended';

  @override
  String connectionTryingAgainIn(int seconds) {
    return 'trying again in $seconds s';
  }

  @override
  String get connectionTryingAgainNow => 'trying again now…';

  @override
  String get chatsHomeNothingRunning => 'Nothing running';

  @override
  String get chatsHomeNothingRunningBody =>
      'Start a conversation, or open one to pick up where you left off.';

  @override
  String get chatsHomeShowAll => 'Show all conversations';

  @override
  String get chatsHomeEmptyCalmBody =>
      'Say what you want to build, and an agent gets to work.';

  @override
  String get chatsHomeConnectComputer => 'Connect a computer';

  @override
  String get chatsHomeProjectEmptyBody =>
      'Ask for something in this project, and it shows up here.';

  @override
  String get scheduleEmptyBody =>
      'Pick a time, and this phone sends the prompt for you.';

  @override
  String get projectsLoadFailedTitle => 'Projects didn\'t load';

  @override
  String get chatStopAfterStep => 'Stop after this step';

  @override
  String get chatStopAfterStepPrompt =>
      'Please stop after the step you are on now and wait for me before doing anything else.';

  @override
  String get appearanceModeAuto => 'Auto';

  @override
  String get appearanceModeSaver => 'Saver';

  @override
  String get appearanceSaverNote =>
      'True black, no motion. Saves battery on OLED screens.';

  @override
  String get appearanceAccentTitle => 'Accent';

  @override
  String get appearanceAccentMint => 'Mint';

  @override
  String get appearanceAccentTeal => 'Teal';

  @override
  String get appearanceAccentBlue => 'Blue';

  @override
  String get appearanceAccentViolet => 'Violet';

  @override
  String get appearanceAccentPink => 'Pink';

  @override
  String get appearanceAccentMono => 'Mono';

  @override
  String get appearanceAccentSilver => 'Silver';

  @override
  String get appearanceSurfaceSolid => 'Solid';

  @override
  String get appearanceSurfaceSoft => 'Soft';

  @override
  String get appearanceSurfaceBrutal => 'Brutal';

  @override
  String get appearanceSurfacePaper => 'Paper';

  @override
  String get signalStyleName => 'Signal';

  @override
  String get signalShow => 'Show';

  @override
  String get signalHide => 'Hide';

  @override
  String signalRowRunningCommand(String command) {
    return 'Running $command';
  }

  @override
  String get appearanceFineTuneCorners => 'Corners';

  @override
  String get appearanceFineTuneCornersRound => 'Round';

  @override
  String get appearanceFineTuneCornersSoft => 'Soft';

  @override
  String get appearanceFineTuneCornersSharp => 'Sharp';

  @override
  String get appearanceFineTuneDepth => 'Depth';

  @override
  String get appearanceFineTuneEdges => 'Edges';

  @override
  String appearanceFineTuneReset(String style) {
    return 'Reset to $style defaults';
  }

  @override
  String get kitTourNext => 'Next';

  @override
  String get kitTourSkip => 'Skip';

  @override
  String get kitTourDone => 'Done';

  @override
  String kitTourStep(int current, int total) {
    return 'Step $current of $total';
  }

  @override
  String get tipStopAfterStep => 'Hold to stop after this step';

  @override
  String get tipStopAfterStepTitle => 'Stop after this step';

  @override
  String get tipSendLater => 'Hold Send to send later or repeat';

  @override
  String get tipSendLaterTitle => 'Send later or repeat';

  @override
  String get tourAppearanceStart => 'Show me how · 30 s';

  @override
  String get tourNotNow => 'Not now';

  @override
  String get tipsTitle => 'Tips';

  @override
  String get tipsHubSubtitle => 'Hidden gestures and short tours';

  @override
  String get tipsSearchAliases =>
      'tips tutorial tour hints gestures hold long-press how to learn';

  @override
  String get tipsIntro =>
      'Each tip shows at its moment, at most twice. Replay any of them here.';

  @override
  String get tipsShowMe => 'Show me';

  @override
  String get tipsTipsLabel => 'Tips';

  @override
  String get tipsToursLabel => 'Mini tutorials';

  @override
  String get chatStopAfterStepPending => 'Stopping after this step';

  @override
  String get chatStopAfterStepUndo => 'Undo';

  @override
  String get chatStopAfterStepUndone => 'It will carry on.';

  @override
  String get chatStopAfterStepTooLate =>
      'Too late: the agent already has the note.';

  @override
  String get chatLiveShowSteps => 'Show steps';

  @override
  String get chatLiveHideSteps => 'Hide steps';

  @override
  String get kitComposerStopPending => 'stopping after this step';

  @override
  String get phraseChipsRetryStarter => 'Try again';

  @override
  String get tourMakeItYoursTitle => 'Make it yours';

  @override
  String get tourMakeItYoursOffer =>
      'Make it yours: light or dark, colours and style, in four steps';

  @override
  String get tourMakeItYoursColours =>
      'Start here: light or dark, a theme and an accent. The preview above shows every change at once.';

  @override
  String get tourMakeItYoursStyle =>
      'Pick how cards and bars feel: Solid, Soft, Brutal or Paper.';

  @override
  String get kitGuideChanges => 'Changes';

  @override
  String get kitGuideAll => 'All';

  @override
  String get kitGuideBack => 'Back to all chips';

  @override
  String get appearanceGroupColours => 'Colours';

  @override
  String get appearanceGroupStyle => 'Style';

  @override
  String get appearanceGroupBackground => 'Background';

  @override
  String get appearanceGroupComfort => 'Text & motion';

  @override
  String appearanceGroupPair(String first, String second) {
    return '$first, $second';
  }

  @override
  String get appearanceChipMode => 'Mode';

  @override
  String get appearanceChipTheme => 'Theme';

  @override
  String get appearanceChipAccent => 'Accent';

  @override
  String get appearanceChipStyle => 'Style';

  @override
  String get appearanceChipShape => 'Shape';

  @override
  String get appearanceChipLook => 'Look';

  @override
  String get appearanceChipStrength => 'Strength';

  @override
  String get appearanceChipText => 'Text size';

  @override
  String get appearanceChipMotion => 'Motion';

  @override
  String get appearanceChipGlow => 'Reply glow';

  @override
  String get appearanceAskMode => 'Light or dark?';

  @override
  String get appearanceAskTheme => 'Which colours?';

  @override
  String get appearanceAskAccent => 'Which accent colour?';

  @override
  String get appearanceAskStyle => 'How should surfaces feel?';

  @override
  String get appearanceAskShape => 'Corners and spacing';

  @override
  String get appearanceAskLook => 'What moves behind your pages?';

  @override
  String get appearanceAskStrength => 'How strong?';

  @override
  String get appearanceAskText => 'How big should text be?';

  @override
  String get appearanceAskMotion => 'How much should move?';

  @override
  String get appearanceAskGlow => 'Glow while a reply is written?';

  @override
  String get appearanceChangesMode => 'Every page, light or dark';

  @override
  String get appearanceChangesTheme => 'The colours of every page';

  @override
  String get appearanceChangesAccent =>
      'Buttons, links, the send key and the glow';

  @override
  String get appearanceChangesStyle => 'Every card, bar and message bubble';

  @override
  String get appearanceChangesShape => 'Corners and spacing of this style';

  @override
  String get appearanceChangesLook => 'The background of every page';

  @override
  String get appearanceChangesStrength =>
      'How much of the background shows through';

  @override
  String get appearanceChangesText =>
      'Text everywhere, on top of your phone\'s size';

  @override
  String get appearanceChangesMotion => 'Animations across the app';

  @override
  String get appearanceChangesGlow => 'The border of the message box';

  @override
  String get appearanceOffInSaver => 'Off in Saver';

  @override
  String get appearanceLookOff => 'Off';

  @override
  String get appearanceStrengthNoLook => 'No look';

  @override
  String appearanceShapeValue(String corners, String spacing) {
    return '$corners, $spacing';
  }

  @override
  String get appearanceShapeSpacing => 'Spacing';

  @override
  String get appearanceSpacingRoomy => 'Roomy';

  @override
  String get appearanceSpacingCompact => 'Compact';

  @override
  String appearanceShapeNote(String style, String corners, String spacing) {
    return '$style starts at $corners and $spacing. Picking another style starts these over.';
  }

  @override
  String get appearanceMotionPhraseFull => 'full motion';

  @override
  String get appearanceMotionPhraseCalm => 'calm motion';

  @override
  String get appearanceMotionPhraseOff => 'no motion';

  @override
  String get appearanceGlowOff => 'Off';

  @override
  String get appearanceGlowOffHint => 'A plain border';

  @override
  String get appearanceGlowClassicHint => 'A light that circles the box';

  @override
  String get appearanceGlowSoftHint => 'A ring that slowly breathes';

  @override
  String get appearanceModeAutoNote =>
      'Follows your phone: light by day, dark at night.';

  @override
  String appearanceSeeAllThemes(int count) {
    return 'See all $count themes';
  }

  @override
  String appearanceSeeAllLooks(int count) {
    return 'See all $count looks';
  }

  @override
  String appearanceAccentMatchNote(String theme) {
    return '“Match” uses $theme\'s own accent.';
  }

  @override
  String get appearanceStrengthNeedsLook =>
      'The background is off, so there is nothing to strengthen.';

  @override
  String get appearanceChooseLookFirst => 'Choose a look first';

  @override
  String get appearanceSaverHoldsStyle =>
      'Saver keeps cards solid and the background still, to save battery on this screen. Your style comes back when Saver is off.';

  @override
  String get appearanceSaverHoldsBackground =>
      'Saver keeps cards solid and the background still, to save battery on this screen. Your background comes back when Saver is off.';

  @override
  String get appearanceTurnOffSaver => 'Turn off Saver';

  @override
  String get appearanceSaverMotion => 'Saver keeps motion off.';

  @override
  String get appearanceGuideStart =>
      'Start with Colours, the biggest change. Every choice saves as you go, and you can tap any row at any time.';

  @override
  String get appearanceGuideSkip => 'Skip the guide';

  @override
  String get appearanceGuideResume =>
      'Pick up where you left off, or tap any chip.';

  @override
  String get appearanceGuideAllSetTitle => 'All set.';

  @override
  String get appearanceGuideAllSet =>
      'Your choices are saved. Tap any chip to change one.';

  @override
  String get appearanceGuideAgain => 'Guide me again';

  @override
  String get appearanceGuideReset => 'Reset to defaults';

  @override
  String get appearanceGuideResetConfirm =>
      'Put every Appearance choice back to the defaults? Light or dark, theme, accent, style, background, text size, motion and the reply glow all reset.';

  @override
  String get appearanceGuideResetDo => 'Reset';

  @override
  String get appearanceGuideResetKeep => 'Keep my choices';

  @override
  String appearanceGuideCountIntro(int count) {
    return '$count quick questions, all optional';
  }

  @override
  String appearanceGuideCountSeen(int seen, int total) {
    return '$seen of $total seen';
  }

  @override
  String appearanceGuideCountAt(int at, int total) {
    return '$at of $total';
  }

  @override
  String get appearanceGuideSavesAsYouGo => 'Everything saves as you go';

  @override
  String get appearanceGuideStartButton => 'Start with Colours';

  @override
  String appearanceGuideContinue(String group) {
    return 'Continue: $group';
  }

  @override
  String appearanceGuideNext(String name) {
    return 'Next: $name';
  }

  @override
  String get appearanceGuideFinish => 'Finish';

  @override
  String appearanceGuidePlace(
    String label,
    String value,
    int at,
    int count,
    String group,
  ) {
    return '$label, $value, question $at of $count in $group';
  }

  @override
  String get tourMakeItYoursBackground =>
      'Pick what moves behind your pages, or turn it off.';

  @override
  String get tourMakeItYoursComfort =>
      'Make text bigger, calm the motion and choose the glow while a reply is written.';

  @override
  String get appearanceAccentMatchShort => 'Match';

  @override
  String get chatTurnStoppedElsewhere =>
      'This reply was stopped from another app or device.';

  @override
  String get chatTurnPausedInactive =>
      'The server paused this reply after an hour with no new work.';

  @override
  String get chatTurnStoppedServerRestart =>
      'The server restarted, so this reply stopped.';

  @override
  String get chatTurnSuperseded => 'A newer run took this reply\'s place.';

  @override
  String get chatTurnStoppedEarly =>
      'This reply was stopped before it finished.';

  @override
  String get settingsV3ModelNewOnPhone => 'New conversations on this phone';

  @override
  String chatLoadAgentMissing(String agent) {
    return '$agent on this phone doesn\'t have this conversation. Go back and open it from your conversations.';
  }

  @override
  String chatLoadAgentUnreadable(String agent) {
    return '$agent on this phone answered, but this app couldn\'t read the reply. Try again, or report a problem.';
  }

  @override
  String get productErrorUnreadableReply =>
      'It answered, but this app couldn\'t read the reply. Try again, or report a problem.';

  @override
  String get settingsV3SavedOnPhone =>
      'Saved on this phone. Other apps using this phone\'s server get this too.';

  @override
  String settingsV3UseMyDefaultDonePhone(String what) {
    return 'Reset on this phone to your own choices: $what.';
  }

  @override
  String get chatStallSubagent =>
      'This sub-agent has been quiet for a while. It stops with its main conversation: stop the reply there.';

  @override
  String get serverPageThisPhonesServer => 'this phone\'s server';

  @override
  String kitBackgroundWorkOne(String task, String time) {
    return 'Still running in the background · $task · $time';
  }

  @override
  String kitBackgroundWorkMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks still running in the background',
      one: '1 task still running in the background',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkTask(String task, String time) {
    return '$task · $time';
  }

  @override
  String kitBackgroundWorkMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes min',
      zero: 'under a minute',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String get kitBackgroundWorkGoToStep => 'Shows the step that started it';

  @override
  String kitBackgroundWorkOneAfterStop(String task, String time) {
    return 'Still running after the turn stopped · $task · $time';
  }

  @override
  String kitBackgroundWorkManyAfterStop(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks still running after the turn stopped',
      one: '1 task still running after the turn stopped',
    );
    return '$_temp0';
  }

  @override
  String chatAgentTurnLostConnectionTo(String agent, String company) {
    return '$agent lost its connection to $company, so the turn stopped.';
  }

  @override
  String chatAgentTurnLostConnection(String agent) {
    return '$agent lost its connection to its model provider, so the turn stopped.';
  }

  @override
  String chatSendFailureLostConnectionTo(String agent, String company) {
    return '$agent lost its connection to $company';
  }

  @override
  String chatSendFailureLostConnection(String agent) {
    return '$agent lost its connection to its model provider';
  }

  @override
  String chatBackgroundWorkWaitingFor(String program) {
    return 'Waiting for $program';
  }

  @override
  String chatSendFailureSignIn(String agent) {
    return '$agent needs you to sign in';
  }

  @override
  String chatSendFailureNoCredit(String agent) {
    return '$agent has no credits for this model';
  }

  @override
  String get chatSendFailureModelUnavailable => 'This model isn\'t available';

  @override
  String chatSendFailureBusy(String agent) {
    return '$agent is busy';
  }

  @override
  String get chatSendFailureAgentFallback => 'The agent';

  @override
  String get chatSendFailureSignInAction => 'Sign in';

  @override
  String get chatSendFailureAccountAction => 'Open account';

  @override
  String get chatSendFailureDetailsAgent => 'Agent';

  @override
  String get chatSendFailureDetailsModel => 'Model';

  @override
  String get chatSendFailureDetailsCode => 'Error code';

  @override
  String get chatSendFailureNewConversation =>
      'This agent can\'t continue this conversation';

  @override
  String chatSendFailureNeedsAccount(String agent) {
    return 'This model needs a $agent account';
  }

  @override
  String chatSendFailureSignInToAgent(String agent) {
    return 'Sign in to $agent';
  }

  @override
  String get chatComposerNoteDismiss => 'Dismiss note';

  @override
  String get chatAnnounceReplyFinished => 'Reply finished';

  @override
  String get chatAnnounceNeedsYou => 'The agent needs you';

  @override
  String get runningPhoneTitle => 'Running on this phone';

  @override
  String runningPhoneSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running · $memory',
      one: '1 running · $memory',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneAutoRefresh =>
      'Checks again every 5 seconds while open';

  @override
  String get runningPhoneLimitTitle => 'Servers may be stopped';

  @override
  String runningPhoneLimitBody(int count, int limit) {
    return '$count of $limit background processes: above $limit, Android stops this app\'s oldest processes first, and those are the servers. Stop what was left behind or isn\'t needed, or lift the limit with Developer options › Disable child process restrictions.';
  }

  @override
  String runningPhoneGroup(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind': 'Left behind',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Agent helper',
      'aiTeam': 'AI Team',
      'terminal': 'Terminal',
      'setup': 'Setup and checks',
      'helpers': 'Helpers',
      'app': 'This app',
      'other': 'Other',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneGroupChat(String title) {
    return 'Claude Code · $title';
  }

  @override
  String runningPhoneName(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'flutterTest': 'Flutter test',
      'command': 'Command',
      'linux': 'Linux',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Agent helper',
      'signIn': 'Agent sign-in',
      'team': 'AI Team engine',
      'node': 'Node.js',
      'python': 'Python',
      'git': 'Git',
      'npm': 'npm',
      'dart': 'Dart',
      'flutter': 'Flutter',
      'gradle': 'Gradle',
      'java': 'Java',
      'search': 'Search',
      'app': 'This app',
      'other': 'Program',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneCpu(String level, String percent) {
    String _temp0 = intl.Intl.selectLogic(level, {
      'idle': 'Idle',
      'light': 'Light',
      'busy': 'Busy',
      'other': 'Very busy',
    });
    return '$_temp0 · $percent%';
  }

  @override
  String get runningPhoneMarkHeavy => 'Heavy';

  @override
  String get runningPhoneMarkStuck => 'May be stuck';

  @override
  String get runningPhoneMarkLeftBehind => 'Left behind';

  @override
  String runningPhoneFromChat(String title) {
    return 'Started by Claude Code · $title';
  }

  @override
  String get runningPhoneFromClaude => 'Started by Claude Code';

  @override
  String get runningPhoneStop => 'Stop';

  @override
  String runningPhoneStopNamed(String name) {
    return 'Stop $name';
  }

  @override
  String runningPhoneStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get runningPhoneStopBody =>
      'It gets a polite stop first, then a forced one if it keeps running.';

  @override
  String runningPhoneStopAllLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop all $count left behind',
      one: 'Stop the 1 left behind',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneStopAllDetail =>
      'Nothing else stops them, and stopping them is safe.';

  @override
  String get runningPhoneStopAllTitle => 'Stop everything left behind?';

  @override
  String runningPhoneStopAllBody(String names) {
    return '$names. Each gets a polite stop first, then a forced one if it keeps running.';
  }

  @override
  String runningPhoneStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stopped $count',
      one: 'Stopped 1',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneAlreadyEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count had already ended',
      one: '1 had already ended',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneNotStoppedTitle => 'Not everything stopped';

  @override
  String runningPhoneRefusedApp(String name) {
    return '$name: this app itself can\'t be stopped here.';
  }

  @override
  String runningPhoneRefusedServer(String name) {
    return '$name: it runs a server. Stop the server from This phone.';
  }

  @override
  String runningPhoneRefusedStillRunning(String name) {
    return '$name: it kept running after a forced stop. Try again, or restart the phone.';
  }

  @override
  String get runningPhoneStopFailed => 'Couldn\'t stop it. Try again.';

  @override
  String get runningPhoneLoadFailedTitle => 'Couldn\'t read what\'s running';

  @override
  String get runningPhoneLoadFailedBody =>
      'The phone didn\'t answer. Try again.';

  @override
  String get runningPhoneRefreshFailed =>
      'Couldn\'t check again, so this shows the last reading.';

  @override
  String get runningPhoneEmpty => 'Nothing else is running';

  @override
  String get runningPhoneEmptyBody =>
      'When OpenCode, Claude Code or a terminal runs here, it shows up in this list.';

  @override
  String runningPhoneAbout(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind':
          'Left running after its Linux ended. Nothing is waiting on it, so stopping it is safe.',
      'openCode': 'Part of the OpenCode server on this phone.',
      'claudeCode': 'Part of a Claude Code conversation.',
      'agentHelper': 'Runs Claude Code and other agents on this phone.',
      'aiTeam': 'Part of the AI Team.',
      'terminal': 'Runs in a terminal on this phone.',
      'setup': 'A setup step or a check.',
      'helpers': 'A helper service of this app.',
      'app': 'This app itself.',
      'other': 'Started by this app.',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneHeavyFor(String elapsed) {
    return 'It has used more than 80% of a processor core for $elapsed.';
  }

  @override
  String runningPhoneStuckWhy(String elapsed) {
    return 'It has run near 100% for $elapsed with no conversation waiting on it.';
  }

  @override
  String get runningPhoneServerRoot =>
      'It runs a server. Stop the server from This phone.';

  @override
  String get runningPhoneConversationId => 'Claude Code conversation ID';

  @override
  String get runningPhoneOwner => 'Belongs to';

  @override
  String runningPhoneRowNormal(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running · $memory',
      one: '1 running · $memory',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count left behind, still using the phone',
      one: '1 left behind, still using the phone',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowHeavy(int count, String name, String percent) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count heavy, $name the most · $percent%',
      one: '$name is heavy · $percent%',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowNearLimit(int count, int limit) {
    return '$count of $limit: Android may stop the servers';
  }

  @override
  String get runningPhoneRowIdle => 'See what runs and what heats the phone';

  @override
  String chatStuckCommand(String time) {
    return '$time · may be stuck';
  }

  @override
  String chatStuckMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String chatStuckHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String chatStuckUnnamed(String time) {
    return 'Still running · $time · may be stuck';
  }

  @override
  String chatStuckWaitsNote(String command) {
    return 'Waits for $command to finish. Stop it to get through now.';
  }

  @override
  String get chatStuckNeverEnds =>
      'This reply may never end. Stop and send gets your message through now.';

  @override
  String get chatStuckThisStep => 'this step';

  @override
  String get kitComposerAddsToReply => 'Adds to this reply';

  @override
  String kitComposerCantTakeMidReply(String agent) {
    return '$agent can\'t take a message mid-reply.';
  }

  @override
  String get kitTurnLiveStopShort => 'Stop';

  @override
  String get frostGlassQuestion => 'Glass';

  @override
  String get frostGlassOff => 'Off';

  @override
  String get frostGlassWeak => 'Weak';

  @override
  String get frostGlassBalanced => 'Balanced';

  @override
  String get frostGlassStrong => 'Strong';

  @override
  String get frostGlassHint =>
      'When the conversation scrolls under the message box or the header, they softly blur it. Off keeps them solid.';

  @override
  String get frostGlassHeldSaver =>
      'Saver is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldMotion =>
      'Reduced motion is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldContrast =>
      'High contrast is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldStyle =>
      'This style keeps its own look, so the message box and header stay solid.';

  @override
  String stopRowSemantics(String agent) {
    return 'Stop $agent';
  }
}
