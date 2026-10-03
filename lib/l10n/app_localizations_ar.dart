// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get servicesTitle => 'خدمات التطوير';

  @override
  String get servicesCopy => 'نسخ الأمر';

  @override
  String get servicesSubtitle => 'أوامر المشروع والسجلات وروابط المعاينة';

  @override
  String get servicesIntro =>
      'اجمع أوامر تطوير مشروعك وروابط معاينته في مكان واحد. حفظ الخدمة لا يشغّلها.';

  @override
  String get servicesAdd => 'إضافة خدمة';

  @override
  String get servicesName => 'اسم الخدمة';

  @override
  String get servicesCommand => 'أمر التطوير';

  @override
  String get servicesCommandHint =>
      'استخدم أمرًا يعمل في المقدمة، مثل npm run dev. لا يمكن تتبّع الأوامر التي تعمل في الخلفية أو منفصلة عن الجلسة.';

  @override
  String get servicesUrl => 'رابط المعاينة (اختياري)';

  @override
  String get servicesUrlHint =>
      'استخدم عنوانًا يمكن لهذا الهاتف الوصول إليه. يشير localhost إلى هذا الهاتف. لا يُفتح أي منفذ ولا يُعاد توجيهه تلقائيًا.';

  @override
  String get servicesSave => 'حفظ الخدمة';

  @override
  String get servicesUnavailable =>
      'لا يتيح هذا الخادم تشغيل أوامر التطوير وتتبّعها. يمكنك حفظ الأوامر ومراجعة روابط معاينتها هنا.';

  @override
  String get servicesScopeChanged =>
      'تغيّر الخادم أو المشروع. افتح خدمات التطوير مجددًا من المشروع المطلوب.';

  @override
  String get servicesNotStarted => 'لم يبدأ التشغيل';

  @override
  String get servicesRunning => 'الأمر قيد التشغيل';

  @override
  String get servicesStopped => 'متوقفة';

  @override
  String get servicesUnknown => 'الحالة غير معروفة';

  @override
  String get servicesStatusHint =>
      'حالة الأمر لا تؤكد أن تطبيقك جاهز أو يمكن الوصول إليه.';

  @override
  String get servicesStart => 'تشغيل';

  @override
  String get servicesStop => 'إيقاف';

  @override
  String get servicesRestart => 'إعادة التشغيل';

  @override
  String get servicesLogs => 'السجلات';

  @override
  String get servicesVisit => 'فتح الرابط';

  @override
  String get servicesRemove => 'إزالة الإعدادات';

  @override
  String get servicesStopHint =>
      'هل تريد إيقاف أمر هذه الخدمة المتتبَّع؟ سيزيل الخادم سجلاته المحفوظة أيضًا. لن تتأثر الأوامر الأخرى.';

  @override
  String get servicesRestartHint =>
      'هل تريد إيقاف هذا الأمر المتتبَّع وإزالة سجله من الخادم، ثم تشغيل الأمر المحفوظ مجددًا؟';

  @override
  String get servicesForget => 'مسح سجل التشغيل الأخير';

  @override
  String get servicesForgetHint =>
      'هل تريد مسح سجل التشغيل المحلي؟ لن تتوقف أي عملية على الخادم. قد يؤدي التشغيل مجددًا إلى إنشاء نسخة مكررة إذا كان الأمر السابق لا يزال يعمل.';

  @override
  String get servicesUnknownHint =>
      'تعذّر تأكيد حالة التشغيل الأخير. حدّث الحالة للتحقق منها قبل التشغيل مجددًا.';

  @override
  String get servicesLogEmpty => 'لا توجد مخرجات مسجّلة بعد.';

  @override
  String get servicesWorking => 'جارٍ تحديث الخدمة…';

  @override
  String servicesExit(int code) {
    return 'رمز الخروج المسجّل: $code';
  }

  @override
  String get servicesRefresh => 'تحديث الحالة';

  @override
  String get isolatedTaskScopeChanged =>
      'The server or project changed while this was open. Close it and start again from the project you want.';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryModelsAgentsTitle => 'النماذج والوكلاء';

  @override
  String get libraryProvidersTitle => 'مزوّدو الخدمة';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => 'الأوامر والأدوات';

  @override
  String get libraryTerminalTitle => 'الطرفية';

  @override
  String get librarySettingsTitle => 'الإعدادات';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'بصمة شهادة التوقيع SHA-256';

  @override
  String get modelSwitchSession => 'تغيير نموذج هذه المحادثة';

  @override
  String get modelNextFavorite => 'النموذج المفضّل التالي';

  @override
  String get modelChooseTitle => 'اختيار نموذج';

  @override
  String get modelTitleCompact => 'النماذج';

  @override
  String get modelSearchHint => 'البحث في النماذج';

  @override
  String get modelAll => 'كل النماذج';

  @override
  String get modelFavorites => 'المفضّلة';

  @override
  String get modelRecent => 'الأخيرة';

  @override
  String get modelOptions => 'الخيارات';

  @override
  String get modelThinkingMode => 'وضع التفكير';

  @override
  String get modelSessionScopeNote =>
      'يسري على الرسائل التالية في هذه المحادثة.';

  @override
  String get modelSelectionLoading => 'جارٍ تحميل اختيار المحادثة…';

  @override
  String get modelServerDefault => 'إعداد الخادم الافتراضي';

  @override
  String get modelSelectionSaving => 'جارٍ حفظ اختيار المحادثة…';

  @override
  String get modelUnavailableSelection =>
      'نموذج المحادثة غير متاح في هذا الدليل. حدّث النماذج أو اختر نموذجًا آخر.';

  @override
  String get modelScopeChanged =>
      'تغيّر الخادم. افتح قائمة اختيار النموذج مجددًا للمتابعة.';

  @override
  String get commonClearSearch => 'مسح البحث';

  @override
  String get workTitle => 'المهام';

  @override
  String get workRefresh => 'تحديث';

  @override
  String get workRetry => 'إعادة المحاولة';

  @override
  String get workCancel => 'إلغاء';

  @override
  String get workRunning => 'قيد التشغيل';

  @override
  String get workFinished => 'مكتملة';

  @override
  String get workTimedOut => 'انتهت المهلة';

  @override
  String get workStopped => 'متوقفة';

  @override
  String get workUnknown => 'الحالة غير متاحة';

  @override
  String get workOutput => 'مخرجات الأمر';

  @override
  String get workNoOutput => 'بانتظار المخرجات…';

  @override
  String get workNoFinalOutput => 'لم يُنتج هذا الأمر أي مخرجات.';

  @override
  String get workCopied => 'نُسخت المخرجات';

  @override
  String get workMoreOutput => 'تحميل المزيد من المخرجات';

  @override
  String get workTrimmed => 'تُعرض أحدث المخرجات. اقتُطع النص الأقدم.';

  @override
  String get workStop => 'إيقاف الأمر';

  @override
  String get workStopTitle => 'هل تريد إيقاف هذا الأمر؟';

  @override
  String get workStopDescription =>
      'سيتوقف الأمر وتُحذف مخرجاته المحفوظة من الخادم. سيبقى النص المحمّل هنا ظاهرًا حتى تغلقه.';

  @override
  String get workTimeout => 'تغيير المهلة';

  @override
  String get workTimeoutDescription => 'تبدأ المهلة الجديدة الآن.';

  @override
  String get workTimeoutOneMinute => 'دقيقة واحدة';

  @override
  String get workTimeoutFiveMinutes => '5 دقائق';

  @override
  String get workTimeoutFifteenMinutes => '15 دقيقة';

  @override
  String get workTimeoutOneHour => 'ساعة واحدة';

  @override
  String get workTimeoutNone => 'بلا مهلة';

  @override
  String get workUnavailable =>
      'لم يعد هذا الأمر متاحًا. ربما أُزيل أو أُلغي عند إعادة تشغيل الخادم.';

  @override
  String get workRestarted =>
      'أُعيد تشغيل الخادم ولم يعد هذا الأمر متاحًا. تظهر مخرجاته المحمّلة أدناه.';

  @override
  String get workDisconnected =>
      'جارٍ إعادة الاتصال. ستُحدّث المخرجات عندما يتاح الخادم.';

  @override
  String get workContextChanged =>
      'تغيّر الخادم أو المشروع. أغلق هذا العرض وافتح المهام الجارية مجددًا.';

  @override
  String workCount(int count) {
    return 'المهام · قيد التشغيل: $count';
  }

  @override
  String workExitCode(int code) {
    return 'رمز الخروج $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => 'مسح نص المسودة';

  @override
  String get composerClearTextSubtitle => 'تبقى المرفقات · يمكن التراجع';

  @override
  String get composerDraftCleared => 'مُسح نص المسودة';

  @override
  String get composerReuseTitle => 'إعادة استخدام طلب';

  @override
  String get queueSaveFailed =>
      'تعذّر حفظ المسودة المنتظرة على هذا الجهاز. لا يزال النص هنا. تحقّق من مساحة التخزين المتاحة وحاول مجددًا.';

  @override
  String get fileSave => 'حفظ';

  @override
  String get fileReload => 'تحديث';

  @override
  String get queueRemoveFailed =>
      'تعذّرت إزالة هذه المسودة من مساحة تخزين الجهاز. لا تزال في قائمة الانتظار. تحقّق من مساحة التخزين المتاحة وحاول مجددًا.';

  @override
  String get composerReuseSubtitle =>
      'إعادة استخدام نص من هذه المحادثة والطلبات المرسلة مؤخرًا';

  @override
  String get composerReuseSearch => 'البحث في الطلبات الأخيرة';

  @override
  String get composerReuseEmpty => 'لا توجد طلبات مطابقة';

  @override
  String get backgroundWorkTitle => 'نقل العمل الجاري إلى الخلفية';

  @override
  String get backgroundWorkShortcut =>
      'متابعة هذا العمل أثناء استخدام المحادثة · Ctrl+B';

  @override
  String get backgroundWorkNoop =>
      'لا يوجد وكلاء فرعيون في المقدمة لنقلهم إلى الخلفية.';

  @override
  String get backgroundWorkPromoted =>
      'يواصل الوكلاء الفرعيون العمل في الخلفية.';

  @override
  String get libraryNoModel => 'لم يُحدّد نموذج';

  @override
  String get chatAttachmentUnsupported =>
      'يمكن إرفاق الصور وملفات PDF والملفات النصية وملفات Excel وWord ‏(‎.xlsx و‎.docx).';

  @override
  String get termuxRestartTitle => 'هل تريد إعادة تشغيل الخادم المحلي؟';

  @override
  String get termuxRestartMessage =>
      'سيتعذّر استخدام OpenCode لفترة قصيرة. سيحتفظ التطبيق بمشروعك الحالي ويعيد الاتصال تلقائيًا.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تولّد $count محادثة ردودًا. ستقاطعها إعادة التشغيل.',
      many: 'تولّد $count محادثة ردودًا. ستقاطعها إعادة التشغيل.',
      few: 'تولّد $count محادثات ردودًا. ستقاطعها إعادة التشغيل.',
      two: 'تولّد محادثتان ردودًا. ستقاطعهما إعادة التشغيل.',
      one: 'تولّد محادثة واحدة ردًا. ستقاطعها إعادة التشغيل.',
      zero: 'لا توجد محادثات تولّد ردودًا.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'إعادة التشغيل';

  @override
  String get chatCopyCompleteReply => 'نسخ الرد كاملًا';

  @override
  String get chatCopyReplySoFar => 'نسخ الرد حتى الآن';

  @override
  String commandRunTitle(String command) {
    return 'تشغيل /$command';
  }

  @override
  String get commandDestination => 'المحادثة';

  @override
  String get commandNewChat => 'محادثة جديدة';

  @override
  String get commandUntitledChat => 'محادثة بلا عنوان';

  @override
  String get commandArguments => 'المعاملات (اختياري)';

  @override
  String get commandRun => 'تشغيل';

  @override
  String get commandRunning => 'جارٍ البدء…';

  @override
  String get commandLocationChanged =>
      'تغيّر الخادم أو المشروع. أغلق هذا الحوار وافتح الأمر مجددًا.';

  @override
  String get refreshFailed => 'تعذّر التحديث';

  @override
  String get refreshRetry => 'إعادة المحاولة';

  @override
  String get filesProjectRoot => 'المجلد الجذر للمشروع';

  @override
  String get globalSessionsLoadMore => 'تحميل المزيد من المحادثات';

  @override
  String get globalSessionsRefreshFailed => 'تعذّر تحديث المحادثات.';

  @override
  String get workspaceSearchAllSessions => 'البحث في كل المحادثات';

  @override
  String get workspaceProjectListUnavailable => 'قائمة المشاريع غير متاحة';

  @override
  String get workspaceRetryProjects => 'إعادة المحاولة';

  @override
  String get historyLoadOlder => 'تحميل الرسائل الأقدم';

  @override
  String get historyReload => 'تحديث السجل الحديث';

  @override
  String get historyCursorExpired =>
      'تغيّر السجل الأقدم أو انتهت صلاحيته. حدّث السجل الحديث للمتابعة.';

  @override
  String get historyRefreshed =>
      'حُدّث السجل. لا تزال الرسائل الأقدم متاحة أعلاه.';

  @override
  String get historyLoadedOnly =>
      'تُضمّن الرسائل المحمّلة فقط. حمّل السجل الأقدم لتضمين المزيد.';

  @override
  String get historyLoadedTotals => 'الاستخدام والسجل المحمّل';

  @override
  String get historyCopyLoadedReply => 'نسخ الرد المحمّل';

  @override
  String get historyLoadedMessages => 'الرسائل المحمّلة';

  @override
  String get historyLoadedCost => 'تكلفة الرسائل المحمّلة';

  @override
  String get historyServerTotalsNote =>
      'تشمل الصفوف الموسومة «أبلغ عنها الخادم» المحادثة كاملة. أما أعداد الرسائل والتقديرات الأخرى فتشمل السجل المحمّل.';

  @override
  String get sessionsDetailsUnavailable =>
      'تعذّر تحميل تفاصيل المحادثة. حاول مجددًا.';

  @override
  String get sessionsReload => 'تحديث المحادثات الأخيرة';

  @override
  String get revertReviewChanged =>
      'تغيّرت هذه المحادثة أو التراجع المبدئي فيها. راجع أحدث حالة قبل المتابعة.';

  @override
  String get revertReviewLatest => 'مراجعة أحدث حالة';

  @override
  String get revertBusy => 'انتظر حتى ينتهي الإجراء الحالي في المحادثة.';

  @override
  String get revertClearAction => 'إلغاء التراجع المبدئي';

  @override
  String get revertPreviewUnavailable =>
      'لم يقدّم الخادم معاينة للملفات. لا يثبت ذلك ما إذا كانت الملفات قد تغيّرت.';

  @override
  String get revertStaged => 'طُبّق التراجع مبدئيًا';

  @override
  String get revertReview => 'مراجعة';

  @override
  String get revertFromHere => 'التراجع بدءًا من هذا الطلب';

  @override
  String get revertUndoDescription =>
      'تطبيق تراجع مبدئي ومراجعة الملفات المتأثرة';

  @override
  String get revertClearShortDescription => 'مراجعة التراجع المبدئي وإلغاؤه';

  @override
  String get revertPromptUnavailable =>
      'تعذّر تحميل الطلب الذي يبدأ منه التراجع.';

  @override
  String get revertPromptLoading => 'جارٍ تحميل الطلب الذي يبدأ منه التراجع…';

  @override
  String get revertAttachmentPrompt => 'طلب يتضمّن مرفقات فقط';

  @override
  String get revertResolveBeforeSending =>
      'راجع التراجع المبدئي، ثم ألغِه أو ثبّته نهائيًا قبل الإرسال. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteTitle => 'ملاحظة للوكيل';

  @override
  String get sessionNoteDescription =>
      'احفظ توجيهًا قصيرًا لهذه المحادثة. يسري حفظه أو حذفه في خطوة الوكيل التالية ويظهر عندها في سجل المحادثة. لا يؤدي ذلك إلى بدء التشغيل.';

  @override
  String get sessionNoteHint =>
      'مثلًا: اختصر الشرح ونفّذ الفحوص ذات الصلة قبل الانتهاء.';

  @override
  String get sessionNoteSave => 'حفظ الملاحظة';

  @override
  String get sessionNoteRemove => 'حذف الملاحظة المحفوظة';

  @override
  String get sessionNoteSaved => 'حُفظت الملاحظة';

  @override
  String get sessionNoteRemoved => 'حُذفت الملاحظة';

  @override
  String get sessionNotePending => 'تسري في خطوة الوكيل التالية.';

  @override
  String get sessionInstructionsUpdated => 'حُدّثت التعليمات';

  @override
  String get sessionInstructionsApplied =>
      'حُدّثت تعليمات الوكيل الخاصة بالمحادثة لهذه الخطوة.';

  @override
  String get sessionNoteUnsupported => 'لا يدعم هذا الخادم ملاحظات المحادثة.';

  @override
  String get sessionNoteAuthorization =>
      'تحقّق من كلمة مرور هذا الخادم وأذوناته، ثم حاول مجددًا. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteChanged =>
      'تغيّرت المحادثة أو تعليماتها. حدّث الملاحظة المحفوظة قبل الحفظ مجددًا. ستبقى مسودتك محفوظة.';

  @override
  String get sessionNoteInvalid =>
      'صيغة الملاحظة المحفوظة لا تتيح لهذا المحرّر تعديلها بأمان.';

  @override
  String get sessionNoteTooLarge =>
      'اختصر الملاحظة لتناسب حد الحجم المسموح به على الخادم.';

  @override
  String get sessionNoteBusy =>
      'جارٍ حفظ تغيير في الملاحظة بالفعل. حاول مجددًا بعد انتهائه.';

  @override
  String get sessionNoteRefresh => 'تحديث الملاحظة المحفوظة';

  @override
  String get sessionNoteSavedVersion =>
      'الملاحظة المحفوظة حاليًا — راجعها قبل استبدالها';

  @override
  String get sessionNoteNone => 'لا توجد ملاحظة محفوظة';

  @override
  String get sessionNoteDiscard => 'هل تريد تجاهل تغييرات الملاحظة؟';

  @override
  String get sessionNoteKeepEditing => 'متابعة التحرير';

  @override
  String get sessionNoteDiscardAction => 'تجاهل التغييرات';

  @override
  String get sessionNoteDiscardDetail =>
      'ستضيع التعديلات التي أجريتها على هذه الملاحظة. لا يمكن التراجع عن ذلك.';

  @override
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit بايت';
  }

  @override
  String get usageTitle => 'الاستخدام والتكلفة';

  @override
  String get usageDescription =>
      'النشاط الذي سجّله خادم OpenCode هذا عبر محادثاتك.';

  @override
  String get usageRefresh => 'تحديث الاستخدام';

  @override
  String get usageToday => 'اليوم';

  @override
  String get usageThirtyDays => '30 يومًا';

  @override
  String get usageYear => 'هذا العام';

  @override
  String get usageAllTime => 'كل الفترات';

  @override
  String get usageScope => 'نطاق المشاريع';

  @override
  String get usageAllProjects => 'كل المشاريع';

  @override
  String get usageCurrentProject => 'المشروع الحالي';

  @override
  String get usageLoading => 'جارٍ تحميل الاستخدام';

  @override
  String get usageUnsupported =>
      'لا يدعم هذا الخادم إحصاءات الاستخدام المجمّعة.';

  @override
  String get usageProjectUnavailable =>
      'تعذّر تحديد المشروع الحالي. اختر «كل المشاريع» أو افتح مشروعًا أولًا.';

  @override
  String get usageTimezoneUnavailable =>
      'تعذّرت قراءة المنطقة الزمنية لهذا الجهاز. أعد المحاولة لتحميل الاستخدام بتواريخ صحيحة.';

  @override
  String get usageRefreshInterrupted =>
      'تغيّر الخادم أثناء تحميل الاستخدام. حدّث لإعادة المحاولة.';

  @override
  String get usageInvalidResponse =>
      'أعاد الخادم بيانات استخدام غير مكتملة. حدّث لإعادة المحاولة.';

  @override
  String get usageAuthorization =>
      'تحقّق من كلمة مرور هذا الخادم وأذوناته، ثم حدّث.';

  @override
  String get usagePreviousResult => 'تُعرض النتيجة السابقة لهذه المرشّحات.';

  @override
  String get usageLocationChanged =>
      'تغيّر الخادم النشط أو المشروع. افتح الاستخدام مجددًا من الإعدادات.';

  @override
  String get usageTinyCost => 'أقل من \$0.000001';

  @override
  String get usageSessions => 'المحادثات';

  @override
  String get usageSubagents => 'محادثات الوكلاء الفرعيين';

  @override
  String get usagePrompts => 'الطلبات';

  @override
  String get usageSteps => 'خطوات الوكيل';

  @override
  String get usageActiveDays => 'أيام النشاط';

  @override
  String get usageStreak => 'أطول فترة متواصلة · أيام';

  @override
  String get usageEmpty =>
      'لا يوجد نشاط في هذه الفترة. جرّب فترة أوسع أو «كل المشاريع».';

  @override
  String get usageTokens => 'الرموز';

  @override
  String get usageTotalTokens => 'الإجمالي';

  @override
  String get usageInput => 'الإدخال';

  @override
  String get usageOutput => 'الإخراج';

  @override
  String get usageReasoning => 'الاستدلال';

  @override
  String get usageCacheRead => 'قراءة ذاكرة التخزين المؤقت';

  @override
  String get usageCacheWrite => 'الكتابة في ذاكرة التخزين المؤقت';

  @override
  String get usageModels => 'استخدام النماذج';

  @override
  String get usageNoModels => 'لم يُسجّل استخدام للنماذج في هذه الفترة.';

  @override
  String get usageToolReliability => 'موثوقية الأدوات';

  @override
  String get usageToolsUnavailable =>
      'لا يتضمّن هذا الرد بيانات موثوقية الأدوات.';

  @override
  String get usageNoTools => 'لم تُسجّل استدعاءات للأدوات في هذه الفترة.';

  @override
  String get usageNoFinishedTools => 'لا توجد استدعاءات أدوات منتهية بعد.';

  @override
  String get usageToolCalls => 'الاستدعاءات';

  @override
  String get usageSucceeded => 'ناجحة';

  @override
  String get usageFailed => 'فاشلة';

  @override
  String get usageUnfinished => 'غير منتهية';

  @override
  String get usageCostDisclosure =>
      'التكاليف تقديرات يُبلّغ عنها OpenCode وليست فاتورة من مزوّد الخدمة. تُستبعد استدعاءات الأدوات غير المنتهية من نسبة النجاح.';

  @override
  String usagePeriod(String from, String to) {
    return '$from – $to';
  }

  @override
  String usageTimezone(String timezone) {
    return 'المنطقة الزمنية: $timezone';
  }

  @override
  String usageModelSteps(String steps) {
    return 'الخطوات: $steps';
  }

  @override
  String usageModelTokens(String tokens) {
    return 'الرموز: $tokens';
  }

  @override
  String usageSuccessRate(String rate) {
    return 'نجحت نسبة $rate من الاستدعاءات المنتهية';
  }

  @override
  String usageUpdated(String time) {
    return 'آخر تحديث في $time';
  }

  @override
  String get mcpRuntimeTitle => 'حتى إعادة تشغيل الخادم';

  @override
  String get mcpDefaultLocation => 'المجلد الافتراضي لخادم OpenCode';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return 'البيئة السحابية: $workspace';
  }

  @override
  String get mcpLocationChanged =>
      'تغيّر الخادم أو المشروع. لا تزال مسودتك هنا؛ افتح الإعداد مجددًا في المشروع المطلوب قبل الإضافة.';

  @override
  String get mcpAdding => 'جارٍ إضافة خادم MCP';

  @override
  String get mcpAdd => 'إضافة خادم MCP';

  @override
  String get mcpRuntimeEmpty =>
      'أضف أدوات للمشروع الحالي حتى إعادة تشغيل OpenCode.';

  @override
  String get mcpRuntimeAdded => 'أُضيف خادم MCP لهذا المشروع';

  @override
  String get mcpHeaderName => 'اسم الترويسة';

  @override
  String get mcpHeaderValue => 'قيمة الترويسة';

  @override
  String get mcpAddHeader => 'إضافة ترويسة أخرى';

  @override
  String get mcpRemoveHeader => 'إزالة الترويسة';

  @override
  String get sessionUnread => 'نتيجة غير مقروءة';

  @override
  String get shareSessionViewsTitle => 'مزامنة حالة القراءة';

  @override
  String get shareSessionViewsOn =>
      'إعلام تطبيقات OpenCode الأخرى لديك بالنتائج المكتملة التي اطّلعت عليها.';

  @override
  String get shareSessionViewsOff =>
      'تبقى حالة القراءة خاصة بهذا الجهاز. تُحدّد النتائج غير المقروءة من سجل القراءة المحلي.';

  @override
  String get shareSessionViewsSaveError =>
      'تعذّر حفظ هذا التفضيل. مشاركة حالة القراءة معطّلة على هذا الجهاز حاليًا.';

  @override
  String get exportTitle => 'تصدير المحادثة';

  @override
  String get exportDescription => 'اختر صيغة لحفظ هذه المحادثة على جهازك.';

  @override
  String get exportJson => 'المحادثة كاملة · JSON';

  @override
  String get exportJsonDescription =>
      'تنزيل المحادثة كاملة من الخادم، بما فيها الرسائل الأقدم.';

  @override
  String get exportMarkdown => 'سجل سهل القراءة · Markdown';

  @override
  String get exportMarkdownDescription =>
      'حفظ الرسائل المحمّلة حاليًا في هذه المحادثة. حمّل الرسائل الأقدم أولًا إن أردت تضمينها.';

  @override
  String get exportRedact => 'حجب البيانات الحساسة';

  @override
  String get exportUnredacted =>
      'قد يحتوي الملف غير المحجوب على أسرار ومسارات محلية ومخرجات أدوات خاصة.';

  @override
  String get exportCancel => 'إلغاء التنزيل';

  @override
  String get exportDownloading => 'جارٍ تنزيل المحادثة كاملة…';

  @override
  String get exportSaving => 'جارٍ حفظ الملف…';

  @override
  String get exportSaved => 'حُفظت المحادثة';

  @override
  String get exportChanged =>
      'تغيّر الخادم أو المشروع. افتح التصدير مجددًا من المحادثة المطلوبة.';

  @override
  String get exportUnsupported =>
      'لا يدعم هذا الخادم التصدير بصيغة JSON. لا يزال بإمكانك حفظ السجل المحمّل بصيغة Markdown.';

  @override
  String get exportAuthorization =>
      'رفض الخادم الوصول. تحقّق من بيانات اعتماد الخادم وحاول مجددًا.';

  @override
  String get exportMissing =>
      'لم تعد هذه المحادثة موجودة على الخادم. لا يزال بإمكانك حفظ السجل المحمّل بصيغة Markdown.';

  @override
  String get exportFailed =>
      'تعذّر تصدير المحادثة. تحقّق من الاتصال ومساحة التخزين، ثم حاول مجددًا.';

  @override
  String get importTitle => 'استيراد محادثة';

  @override
  String get importDescription =>
      'استعد ملفًا مصدّرًا بصيغة JSON إلى خادم OpenCode هذا. اختر ملفًا، ثم راجع وجهة استيراده.';

  @override
  String get importChoose => 'اختيار ملف JSON';

  @override
  String get importChooseAnother => 'اختيار ملف آخر';

  @override
  String get importAction => 'استيراد المحادثة';

  @override
  String get importUntitled => 'محادثة بلا عنوان';

  @override
  String get importRedacted =>
      'يحتوي هذا الملف على نصوص بديلة لبيانات محجوبة. لا يمكن للاستيراد استعادة النص الأصلي؛ استخدم ملفًا مصدّرًا دون حجب إذا كنت تحتاجه.';

  @override
  String importParent(String id) {
    return 'يجب أن تكون المحادثة الأم $id موجودة على هذا الخادم. استورد المحادثة الأم أولًا.';
  }

  @override
  String get importArchived =>
      'هذه المحادثة مؤرشفة. ستبقى مؤرشفة بعد الاستيراد.';

  @override
  String get importDestination => 'الاستيراد إلى';

  @override
  String get importChooseDestination => 'اختيار مشروع على هذا الخادم';

  @override
  String get importNoDestinations =>
      'لا تتوفر مشاريع. افتح مشروعًا على هذا الخادم، ثم حاول مجددًا.';

  @override
  String get importDestinationFailed =>
      'تعذّر تحميل مشاريع الوجهة أو بيئاتها السحابية. حاول مجددًا؛ لا يزال ملفك محدّدًا.';

  @override
  String get importPreserves =>
      'لن يتغيّر الملف المصدر. لن تُستبدل أي محادثة موجودة، ولن يبدأ تشغيل وكيل بسبب الاستيراد.';

  @override
  String get importReading => 'جارٍ تجهيز الاستيراد…';

  @override
  String get importSending => 'جارٍ استيراد المحادثة…';

  @override
  String get importSucceeded => 'استُوردت المحادثة';

  @override
  String get importOpen => 'فتح المحادثة';

  @override
  String get importOpenFailed =>
      'استُوردت المحادثة، لكن تعذّر فتحها. ابحث عنها في «كل المحادثات» على خادم الوجهة.';

  @override
  String get importChanged =>
      'تغيّر الخادم أو المشروع. لا يزال ملفك هنا. افتح الاستيراد مجددًا على الخادم المطلوب قبل المتابعة.';

  @override
  String get importUnsupported => 'لا يدعم هذا الخادم الاستيراد بصيغة JSON.';

  @override
  String get importInvalidFile =>
      'اختر ملف OpenCode صالحًا مصدّرًا بصيغة JSON ويحتوي على معلومات المحادثة وسجلات الرسائل. لا يمكن استيراد سجلات Markdown.';

  @override
  String get importTooLarge =>
      'يتجاوز هذا الملف حد الاستيراد على الهاتف البالغ 128 MiB. لم يُرفع الملف ولم يُقتطع منه شيء. انقله باستخدام حاسوب أو خادم.';

  @override
  String get importConflict =>
      'توجد محادثة بهذا المعرّف على هذا الخادم بالفعل. لم يُستبدل شيء. ابحث عنها في «كل المحادثات»، أو استورد هذا الملف على خادم آخر.';

  @override
  String get importAuthorization =>
      'رفض الخادم الوصول. تحقّق من بيانات اعتماد الخادم. لا يزال ملفك محدّدًا.';

  @override
  String get importParentMissing =>
      'المحادثة الأم غير موجودة على هذا الخادم. استورد المحادثة الأم أولًا، ثم أعد محاولة استيراد هذا الملف.';

  @override
  String get importRejected =>
      'رفض الخادم صيغة هذا الملف المصدّر. لا يزال ملفك محدّدًا؛ تحقّق من أنه صادر عن خادم OpenCode متوافق.';

  @override
  String get importUnconfirmed =>
      'تعذّر تأكيد الاستيراد. تحقّق من «كل المحادثات» قبل إعادة المحاولة؛ فقد يكون الخادم قد استلمه. لم يتغيّر الملف المصدر.';

  @override
  String get sessionPin => 'تثبيت على هذا الجهاز';

  @override
  String get sessionUnpin => 'إلغاء التثبيت';

  @override
  String get sessionPinFailed =>
      'تعذّر حفظ التثبيت. تحقّق من مساحة تخزين الجهاز ومن أن مشروع المحادثة لم يتغيّر، ثم حاول مجددًا.';

  @override
  String get sessionPinsLoadFailed =>
      'تعذّر تحميل بعض المحادثات المثبّتة. حدّث لإعادة المحاولة.';

  @override
  String get promptStashSaveFailed =>
      'تعذّر حفظ هذا الطلب. لم يتغيّر محتوى محرّر الرسالة. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get promptOriginalDraft => 'استعادة المسودة الأصلية';

  @override
  String get promptStashTitle => 'الطلبات المحفوظة';

  @override
  String get promptStashSearch => 'البحث في الطلبات المحفوظة';

  @override
  String get promptStashDeleteFailed =>
      'تعذّر حذف هذا الطلب المحفوظ. حاول مجددًا.';

  @override
  String get promptStashDelete => 'حذف';

  @override
  String get promptStashFull =>
      'لديك 50 طلبًا محفوظًا. احذف طلبًا محفوظًا لتوفير مساحة؛ لم يتغيّر طلبك الحالي.';

  @override
  String promptStashAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مرفق',
      many: '$count مرفقًا',
      few: '$count مرفقات',
      two: 'مرفقان',
      one: 'مرفق واحد',
      zero: 'لا توجد مرفقات',
    );
    return '$_temp0';
  }

  @override
  String promptStashReferences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مرجع',
      many: '$count مرجعًا',
      few: '$count مراجع',
      two: 'مرجعان',
      one: 'مرجع واحد',
      zero: 'لا توجد مراجع',
    );
    return '$_temp0';
  }

  @override
  String get promptHistorySaveFailed =>
      'أُرسل الطلب، لكن تعذّر حفظه في السجل على هذا الجهاز.';

  @override
  String get promptStashMigrationPending =>
      'تعذّر نقل بعض المرفقات المحفوظة إلى تخزين المرفقات المحلي حتى الآن. احتُفظ بمحتواك المحفوظ. وفّر مساحة على الجهاز وأعد المحاولة.';

  @override
  String get commonRetry => 'إعادة المحاولة';

  @override
  String get shareWaitingForServer =>
      'اتصل بخادم لفتح النص المشترك في محادثة جديدة.';

  @override
  String get webSourcesDisclosure =>
      'لا يتاح البحث في الويب عبر بوابة التطبيق لهذا الخادم. الصق رابطًا عامًا، ويمكنك إضافة مقتطف تريد تضمينه. لن تُجلب أي صفحة، ولن يُرسل شيء إلى النموذج هنا.';

  @override
  String get webSourcesScopeChanged =>
      'تغيّر الخادم. أغلق «إضافة مصدر ويب» وافتحه مجددًا.';

  @override
  String get webSourcesUrl => 'رابط عام';

  @override
  String get webSourcesLabel => 'العنوان (اختياري)';

  @override
  String get webSourcesExcerpt => 'مقتطف ملصق (اختياري)';

  @override
  String get webSourcesExcerptHint =>
      'نص يقدّمه المستخدم، وليس محتوى صفحة جرى التحقق منه.';

  @override
  String get digestStatusUnverified =>
      'أبلغ الخادم عن حالة خمول. لم يُتحقَّق من النجاح أو الفشل.';

  @override
  String get digestChangedFilesUnknown => 'الملفات المتغيّرة: غير معروفة.';

  @override
  String digestChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count ملف متغيّر في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
      many:
          '$count ملفًا متغيّرًا في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
      few:
          '$count ملفات متغيّرة في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
      two:
          'ملفان متغيّران في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
      one:
          'ملف واحد متغيّر في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
      zero:
          'لا توجد ملفات متغيّرة في إجمالي المحادثة؛ أما هذا التشغيل فحالته غير معروفة.',
    );
    return '$_temp0';
  }

  @override
  String get digestPendingDecisionsUnknown => 'القرارات المنتظرة: غير معروفة.';

  @override
  String digestPendingDecisions(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قرار منتظر في البيانات المخزّنة مؤقتًا حاليًا.',
      many: '$count قرارًا منتظرًا في البيانات المخزّنة مؤقتًا حاليًا.',
      few: '$count قرارات منتظرة في البيانات المخزّنة مؤقتًا حاليًا.',
      two: 'قراران منتظران في البيانات المخزّنة مؤقتًا حاليًا.',
      one: 'قرار واحد منتظر في البيانات المخزّنة مؤقتًا حاليًا.',
      zero: 'لا توجد قرارات منتظرة في البيانات المخزّنة مؤقتًا حاليًا.',
    );
    return '$_temp0';
  }

  @override
  String get digestOutcomesUnknown =>
      'نتائج الأدوات والمهام المتبقية: غير معروفة.';

  @override
  String get digestProvenance =>
      'بيانات وصفية مخزّنة مؤقتًا من الخادم فقط. لا يوجد ملخّص ذكاء اصطناعي أو استدعاء للنموذج. افتح المحادثة للتحقق من النتائج ومراجعة التغييرات أو المهام.';

  @override
  String get digestOpenConversation => 'فتح المحادثة';

  @override
  String get digestReview => 'مراجعة الخطوات التالية';

  @override
  String get digestCopy => 'نسخ الملخّص';

  @override
  String get digestCopySucceeded => 'نُسخ الملخّص';

  @override
  String get digestCopyFailed => 'تعذّر نسخ الملخّص';

  @override
  String get digestDismiss => 'إخفاء';

  @override
  String get digestRunResults => 'نتائج التشغيل';

  @override
  String get runResultsScopeChanged =>
      'تغيّر الخادم أو المشروع. أغلق هذا العرض وافتح نتائج التشغيل مجددًا من المشروع المطلوب.';

  @override
  String get runResultsTitle => 'نتائج التشغيل';

  @override
  String get runResultsEmpty =>
      'لم تبدأ أي خطوة للمساعد في التبادل الأخير بعد، لذلك لا يوجد ما يُعرض.';

  @override
  String runResultsStarted(String time) {
    return 'بدأ في $time';
  }

  @override
  String get runResultsStartedUnknown => 'لم يُسجّل وقت البدء';

  @override
  String runResultsFinished(String time) {
    return 'انتهى في $time';
  }

  @override
  String get runResultsFinishedUnknown => 'لم يُسجّل وقت الانتهاء';

  @override
  String get runResultsPartialHistory =>
      'لم يُعثر في السجل المحمّل على الرسالة التي بدأت هذا التشغيل. الأعداد هنا حدود دنيا، ومعرّف التشغيل يمثّل أقدم خطوة محمّلة فقط.';

  @override
  String get runResultsOutcomeCompleted => 'مكتمل';

  @override
  String get runResultsOutcomeCutOff => 'قطعه مزوّد الخدمة';

  @override
  String get runResultsOutcomeFailed => 'فشل';

  @override
  String get runResultsOutcomeAborted => 'أُلغي';

  @override
  String get runResultsOutcomeRunning => 'لا يزال يعمل';

  @override
  String get runResultsOutcomeNotReported => 'لم يُبلّغ عن النتيجة';

  @override
  String runResultsFinishReason(String finish) {
    return 'سبب الانتهاء لدى مزوّد الخدمة: $finish';
  }

  @override
  String get runResultsFinishReasonMissing =>
      'لم يقدّم مزوّد الخدمة سببًا للانتهاء.';

  @override
  String runResultsEarlierErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أبلغت $count خطوة سابقة عن أخطاء؛ تحدّد أحدث خطوة النتيجة.',
      many: 'أبلغت $count خطوة سابقة عن أخطاء؛ تحدّد أحدث خطوة النتيجة.',
      few: 'أبلغت $count خطوات سابقة عن أخطاء؛ تحدّد أحدث خطوة النتيجة.',
      two: 'أبلغت خطوتان سابقتان عن أخطاء؛ تحدّد أحدث خطوة النتيجة.',
      one: 'أبلغت خطوة سابقة عن خطأ؛ تحدّد أحدث خطوة النتيجة.',
      zero: 'لم تُبلّغ الخطوات السابقة عن أخطاء؛ تحدّد أحدث خطوة النتيجة.',
    );
    return '$_temp0';
  }

  @override
  String get runResultsObservedLive =>
      'تلقّى هذا الهاتف إشعار اكتمال أحدث خطوة مباشرة.';

  @override
  String get runResultsFromHistory =>
      'استُعيدت البيانات من سجل الخادم. لم يرصد هذا الهاتف اكتمال أحدث خطوة مباشرة.';

  @override
  String get runResultsNoToolEvidence =>
      'لم يُسجّل هذا التشغيل استدعاءات أدوات، لذلك لا يوجد دليل من الملفات أو الأوامر. لا يعني ذلك عدم وجود تغييرات.';

  @override
  String get runResultsChangedFilesTitle => 'الملفات المتغيّرة';

  @override
  String get runResultsChangedFilesSource =>
      'من أدوات التحرير والكتابة والترقيع المكتملة في هذا التشغيل. لا يمثّل ذلك مقارنة موثّقة لتغييرات شجرة العمل.';

  @override
  String get runResultsNoChangedFiles =>
      'لا توجد أداة مكتملة غيّرت ملفات في هذا التشغيل.';

  @override
  String get runResultsChangeEdited => 'عُدّل';

  @override
  String get runResultsChangeWritten => 'كُتب';

  @override
  String get runResultsChangePatched => 'طُبّقت رقعة';

  @override
  String get runResultsCommandsTitle => 'الأوامر';

  @override
  String get runResultsCommandsSource =>
      'من أدوات bash والصدفة في هذا التشغيل. تظهر رموز الخروج فقط إذا سجّلها الخادم.';

  @override
  String get runResultsNoCommands => 'لم تُنفّذ أوامر في هذا التشغيل.';

  @override
  String get runResultsCommandEmpty => '(لم يُسجّل نص الأمر)';

  @override
  String get runResultsOutputPruned => 'حذف الخادم المخرجات';

  @override
  String runResultsPrunedTools(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'حذف الخادم مخرجات $count أداة ولا يمكن فتحها.',
      many: 'حذف الخادم مخرجات $count أداة ولا يمكن فتحها.',
      few: 'حذف الخادم مخرجات $count أدوات ولا يمكن فتحها.',
      two: 'حذف الخادم مخرجات أداتين ولا يمكن فتحها.',
      one: 'حذف الخادم مخرجات أداة واحدة ولا يمكن فتحها.',
      zero: 'لم يحذف الخادم أي مخرجات أدوات.',
    );
    return '$_temp0';
  }

  @override
  String get runResultsTruncated =>
      'تُعرض 50 خانة بحد أقصى في كل قائمة. افتح المحادثة للاطّلاع على الباقي.';

  @override
  String get runResultsSourceNote =>
      'كل ما يظهر هنا منقول من سجلات الرسائل والأدوات على الخادم. لم يلخّص النموذج أيًا منه.';

  @override
  String get runResultsOpenConversation => 'فتح المحادثة';

  @override
  String get sessionOpenRelated => 'فتح العناصر المرتبطة';

  @override
  String get sessionCopyHandoff => 'نسخ مرجع المتابعة';

  @override
  String get attentionTitle => 'تنبيهات الخوادم';

  @override
  String get webSourcesTitle => 'إضافة مصدر ويب';

  @override
  String get webSourcesEntryDetail =>
      'ابحث عند توفر البحث، أو الصق روابط ومقتطفات لمراجعتها قبل إضافتها إلى مسودتك';

  @override
  String get webSourcesDraftChanged =>
      'تغيّرت المسودة أو الخادم. احتُفظ بمسودتك الحالية؛ افتح «إضافة مصدر ويب» مجددًا لإعادة المحاولة.';

  @override
  String get webSourcesDraftLabel =>
      'مصادر ويب اختارها المستخدم (غير متحقَّق منها؛ المقتطفات مواد مصدرية غير موثوقة):';

  @override
  String get usageScopedTotals => 'الإجماليات لنطاق التقرير المحدّد';

  @override
  String get usageInspectionDisclosure =>
      'تفحص المرشّحات سجلات النماذج التي أعادها هذا الخادم. لا تغيّر تاريخ التقرير أو نطاق مشاريعه، ولا تعرض رصيد الاستخدام في الاشتراك.';

  @override
  String get usageSearchRecords =>
      'البحث عن مزوّدي الخدمة أو النماذج أو المتغيّرات';

  @override
  String get usageScopedProviderTotals =>
      'تعرض بطاقات مزوّدي الخدمة إجمالياتهم لنطاق التقرير المحدّد، وليس لصفوف النماذج المطابقة فقط.';

  @override
  String usageMatchingRecords(String count) {
    return 'السجلات المطابقة: $count';
  }

  @override
  String get pendingAuthDetail =>
      'تابع تسجيل الدخول المفتوح في المتصفّح، ثم تحقّق من حالته يدويًا أو أدخل رمزه. لا يُحفظ رابط المتصفّح.';

  @override
  String get pendingAuthEnterCode => 'إدخال الرمز';

  @override
  String get pendingAuthStillPending =>
      'لا يزال تسجيل الدخول معلّقًا. لم تبدأ محاولة جديدة.';

  @override
  String get pendingAuthServerFailed =>
      'أبلغ الخادم عن فشل تسجيل الدخول. تفاصيل خطأ مزوّد الخدمة مخفية.';

  @override
  String get pendingAuthExpired =>
      'انتهت صلاحية هذه المحاولة أو تجاوزت مدة الاسترداد على الجهاز. إلغاء المحاولة إجراء منفصل على الخادم.';

  @override
  String get pendingAuthFailed =>
      'تعذّر تأكيد الإجراء. تحقّق من عمليات تسجيل الدخول المعلّقة قبل المحاولة مجددًا. لم يبدأ تسجيل دخول جديد.';

  @override
  String get pendingAuthSaveUncertain =>
      'تعذّر حفظ بيانات الاسترداد بشكل موثوق. أبقِ هذا التطبيق مفتوحًا وأعد محاولة الحفظ؛ فقد تضيع هذه المحاولة عند إعادة التشغيل. إذا لم تُفتح صفحة في المتصفّح، فألغِ المحاولة قبل البدء مجددًا.';

  @override
  String get pendingAuthRetrySave => 'إعادة محاولة حفظ بيانات الاسترداد';

  @override
  String get pendingAuthForget => 'إزالة السجل من هذا الجهاز';

  @override
  String get pendingAuthUnsupported =>
      'لا يتيح هذا الخادم استرداد عمليات تسجيل الدخول السابقة. تعمل عمليات تسجيل الدخول القديمة فقط ما دامت الشاشة والاتصال الأصليان متاحين.';

  @override
  String get pendingAuthOtherSource =>
      'تتبع عمليات تسجيل الدخول المعلّقة الأخرى خادمًا أو مشروعًا آخر. ارجع إلى مصدرها الأصلي لإدارتها.';

  @override
  String get connectionHelpGuideTip =>
      'أبقِ الخادم بعيدًا عن الإنترنت العام. استخدم HTTPS خاصًا أو نفقًا مشفّرًا ينتهي على الجهاز الذي يشغّل هذا التطبيق. يشير localhost على حاسوبك إلى غير ما يشير إليه على هاتفك. افتح مساعدة الاتصال أعلاه للاطّلاع على الخطوات والأمثلة.';

  @override
  String get voiceConversationTitle => 'محادثة صوتية';

  @override
  String get voiceConversationDescription =>
      'استمع وراجع، ثم أرسل. لا يبدأ الاستماع تلقائيًا؛ تُقرأ الردود بصوت عالٍ فقط إذا فعّلت ذلك.';

  @override
  String get voiceConversationReplyReviewNeeded =>
      'اكتمل الرد، لكن تعذّر التأكد من ارتباطه برسالتك. يمكنك قراءته إن أردت.';

  @override
  String get voiceConversationReplyInterrupted =>
      'احتاج الرد إلى قرار على الشاشة، لذلك لم يُقرأ تلقائيًا.';

  @override
  String get voiceConversationReplyNoProse =>
      'لا يتضمّن الرد نصًا لقراءته. لا تُقرأ الشيفرة ولا تفاصيل الأدوات بصوت عالٍ.';

  @override
  String get voiceConversationReplyFailed => 'تعذّرت قراءة الرد بصوت عالٍ.';

  @override
  String get voiceConversationPausedDetail =>
      'المحادثة الصوتية متوقفة مؤقتًا. أعد الاتصال أو انتظر الرد أو راجع القرارات المنتظرة على الشاشة.';

  @override
  String get voiceConversationDraftFirst =>
      'أرسل مسودتك الحالية أو احفظها أو امسحها قبل بدء المحادثة الصوتية.';

  @override
  String get voiceConversationCommandsOnly =>
      'استخدم محرّر الرسالة النصي للأوامر التي تبدأ بشرطة مائلة.';

  @override
  String get voiceInputUnavailable =>
      'الإدخال الصوتي غير متاح. تحقّق من إعدادات النموذج المحلي والميكروفون.';

  @override
  String get desktopDropFailedTitle => 'تعذّر إرفاق الملفات المُسقطة';

  @override
  String get desktopDropFailedRecovery =>
      'تحقّق من المرفقات المضافة بالفعل قبل المحاولة مجددًا. يمكنك أيضًا استخدام لوحة المفاتيح لفتح «إضافة» ثم «إرفاق ملف».';

  @override
  String get commandAuthMethodHint =>
      'تشغيل طريقة تسجيل الدخول الخاصة بمزوّد الخدمة على الخادم المحدّد، وليس على هذا الهاتف. قد تحتاج إلى إتمام خطوات تفاعلية على الخادم.';

  @override
  String get commandAuthStart => 'بدء تسجيل الدخول على الخادم';

  @override
  String get commandAuthPending =>
      'تسجيل الدخول معلّق على الخادم. أكمل أي تفاعل مطلوب على الخادم، ثم تحقّق من حالته. إغلاق هذه اللوحة لا يلغيه.';

  @override
  String get commandAuthCancel => 'إلغاء تسجيل الدخول';

  @override
  String get commandAuthFailed =>
      'تعذّر إتمام تسجيل الدخول على الخادم أو تأكيده. تحقّق من المحاولة الحالية قبل بدء أخرى.';

  @override
  String get commandAuthComplete =>
      'أبلغ الخادم عن اكتمال تسجيل الدخول. حدّث مزوّدي الخدمة للاطّلاع على الاتصالات الحالية.';

  @override
  String get commandAuthExpired =>
      'انتهت صلاحية محاولة تسجيل الدخول هذه. يمكنك بدء محاولة جديدة.';

  @override
  String get commandAuthScopeChanged =>
      'تغيّر الخادم أو المشروع. ارجع إلى المشروع الأصلي وافتح تسجيل الدخول مجددًا لإدارة محاولته.';

  @override
  String get commandAuthUncertainStart =>
      'ربما بدأ الخادم تسجيل الدخول، لكن تعذّر على التطبيق استرداد محاولته بأمان. تحقّق على الخادم قبل إعادة المحاولة؛ مُنع البدء التلقائي مجددًا لتجنّب تكرار العمليات.';

  @override
  String get readAloudAction => 'قراءة نص الرد بصوت عالٍ';

  @override
  String get readAloudStop => 'إيقاف القراءة بصوت عالٍ';

  @override
  String get readAloudOtherVoice => 'القراءة بصوت آخر';

  @override
  String get readAloudChooseVoice => 'اختيار صوت للقراءة';

  @override
  String get readAloudConsentTitle => 'هل تريد استخدام محرّك النطق في النظام؟';

  @override
  String get readAloudConsentDetail =>
      'سيُرسل نص الرد المحمّل إلى محرّك النطق في نظامك. تُعرض فقط الأصوات الموسومة بأنها تعمل دون اتصال، لكن المحرّك برنامج مستقل وتسري ممارسات الخصوصية الخاصة به. تُستبعد كتل الشيفرة وتفاصيل الأدوات. قد يسمع الآخرون الصوت. تتوقف القراءة عند فتح شاشة تغطي هذه المحادثة أو انتقال التطبيق إلى الخلفية.';

  @override
  String get readAloudContinue => 'اختيار صوت';

  @override
  String get readAloudUnsupported =>
      'القراءة بصوت عالٍ غير متاحة على هذه المنصة.';

  @override
  String get readAloudNoVoice =>
      'لا يتوفر صوت مثبّت موسوم بأنه يعمل دون اتصال. أعدّ صوتًا يعمل دون اتصال في إعدادات النطق بالنظام وحاول مجددًا.';

  @override
  String get readAloudUnavailable =>
      'تعذّر على محرّك النطق قراءة هذا الرد. حاول مجددًا أو اختر صوتًا آخر.';

  @override
  String get readAloudTooLong =>
      'هذا الرد أطول من أن يُقرأ بصوت عالٍ. اختر ردًا أقصر.';

  @override
  String get readAloudBusy =>
      'لا تتاح القراءة الصوتية أثناء التقاط الصوت أو مقاطعة صوتية أخرى.';

  @override
  String get readAloudNoProse =>
      'لا يوجد نص رد لقراءته. لا تُقرأ الشيفرة ولا تفاصيل الأدوات بصوت عالٍ.';

  @override
  String get credentialMetadataOnly =>
      'تُعرض تسميات الحسابات المحفوظة فقط. تبقى مفاتيح API ورموز تسجيل الدخول على خادمك.';

  @override
  String get credentialActiveUpdated => 'حُدّث الحساب النشط من الخادم.';

  @override
  String get credentialSwitchRequested =>
      'طُلب تبديل الحساب. لم يؤكَّد هذا الطلب بحدث من الخادم بعد.';

  @override
  String get credentialActive => 'نشط';

  @override
  String get credentialRename => 'إعادة تسمية الحساب';

  @override
  String get credentialLabel => 'تسمية الحساب';

  @override
  String get credentialSave => 'حفظ التسمية';

  @override
  String get credentialScopeChanged =>
      'تغيّر الخادم أو المشروع. أغلق إدارة الحسابات وافتحها مجددًا قبل إجراء تغييرات.';

  @override
  String get credentialProviderMissing =>
      'لم يعد مزوّد الخدمة هذا في قائمة تكاملات الخادم.';

  @override
  String get credentialLoadFailed =>
      'تعذّر تحديث الحسابات المحفوظة. حاول مجددًا.';

  @override
  String get credentialMutationFailed =>
      'تعذّر تأكيد تغيير الحساب. حدّث قبل إعادة المحاولة؛ فقد يكون الخادم قد طبّقه بالفعل.';

  @override
  String get credentialRefresh => 'تحديث الحسابات';

  @override
  String get credentialEmpty => 'لم يُبلّغ عن حسابات محفوظة لمزوّد الخدمة هذا.';

  @override
  String get credentialEnvironment => 'تديره بيئة الخادم. لا يمكن إزالته هنا.';

  @override
  String credentialUnnamed(int index) {
    return 'حساب محفوظ $index';
  }

  @override
  String get mcpRemoveFailed =>
      'تعذّر تأكيد إزالة MCP. حدّث القائمة قبل المحاولة مجددًا؛ فقد يكون الخادم قد طبّق التغيير بالفعل.';

  @override
  String get mcpLoadFailed => 'تعذّر تحديث بيانات MCP. حاول مجددًا.';

  @override
  String get mcpSavedStatus => 'محفوظ في OpenCode';

  @override
  String get mcpRetryReconnect => 'إعادة محاولة الاتصال';

  @override
  String get mcpReconnecting => 'جارٍ إعادة الاتصال';

  @override
  String get mcpStillDisconnected => 'لا يزال OpenCode غير متصل. حاول مجددًا.';

  @override
  String get mcpScopeChanged =>
      'تغيّر الخادم أو المشروع. حدّث لتحميل خوادم MCP الخاصة به قبل إجراء تغييرات.';

  @override
  String get promptStashRestoreFailed =>
      'تعذّر إتمام استعادة الطلب. لا تزال النسخ المحفوظة متاحة؛ تحقّق من محرّر الرسالة قبل المحاولة مجددًا.';

  @override
  String get promptStashContextOnly => 'المرفقات والمراجع';

  @override
  String get promptDefaultLocation => 'المجلد الافتراضي للخادم';

  @override
  String get promptStashed => 'أُضيف الطلب إلى طلباتك المحفوظة.';

  @override
  String get promptStashedDraftPending =>
      'أُضيف الطلب إلى طلباتك المحفوظة. لا تزال مسودة محرّر الرسالة بحاجة إلى الحفظ؛ أعد المحاولة من تنبيه المسودة.';

  @override
  String get promptStashReadFailed =>
      'تعذّرت قراءة الطلبات المحفوظة. احتُفظ ببياناتها المخزّنة.';

  @override
  String get promptStashDescription =>
      'حفظ النص والمرفقات والمراجع لاستخدامها لاحقًا';

  @override
  String get promptRestored => 'Saved prompt restored';

  @override
  String promptStashLocation(String directory) {
    return 'يشير هذا الطلب إلى ملفات في $directory. انتقل إلى مشروعه الأصلي قبل استعادته.';
  }

  @override
  String get promptStashScopeChanged =>
      'تغيّر الخادم أو المشروع. أغلق الطلبات المحفوظة وافتحها مجددًا.';

  @override
  String get transcriptFindHint => 'البحث في المحادثة';

  @override
  String get transcriptFindClose => 'إغلاق البحث';

  @override
  String get transcriptFindPrevious => 'التطابق السابق';

  @override
  String get transcriptFindNext => 'التطابق التالي';

  @override
  String get transcriptFindNone => 'لا توجد تطابقات';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: 'التطابق $current من $total',
      many: 'التطابق $current من $total',
      few: 'التطابق $current من $total',
      two: 'التطابق $current من $total',
      one: 'تطابق واحد',
      zero: 'لا توجد تطابقات',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'الرسائل المحمّلة فقط. حمّل الرسائل الأقدم لتوسيع البحث.';

  @override
  String get transcriptFindReasoning => 'الاستدلال';

  @override
  String get transcriptFindTool => 'بيانات الأدوات';

  @override
  String get transcriptFindFile => 'اسم الملف';

  @override
  String get transcriptFindAll => 'البحث في السجل كاملًا';

  @override
  String get skillUse => 'إضافة إلى المحادثة';

  @override
  String get skillActivationHelp =>
      'تُضاف تعليمات هذه المهارة إلى المحادثة. تبقى مسودتك غير المرسلة في محرّر الرسالة.';

  @override
  String get skillRunNow => 'تشغيل الوكيل الآن';

  @override
  String get skillRunHelp => 'عطّل هذا الخيار لإضافة المهارة دون بدء رد آخر.';

  @override
  String get skillLocationChanged =>
      'تغيّر الخادم أو المشروع. افتح المهارات مجددًا من المحادثة.';

  @override
  String get skillUnsupported =>
      'تفعيل المهارات غير متاح على هذا الخادم. لا يزال بإمكانك معاينتها.';

  @override
  String get skillStaged => 'احسم التراجع المبدئي في المحادثة قبل إضافة مهارة.';

  @override
  String get skillBusy => 'جارٍ إضافة مهارة إلى هذه المحادثة بالفعل.';

  @override
  String get skillUncertain =>
      'لم يؤكّد الخادم النتيجة. قد تكون المهارة قد أُضيفت. أغلق هذه اللوحة وتحقّق من المحادثة قبل المحاولة مجددًا.';

  @override
  String get skillApplied => 'أُضيفت المهارة إلى هذه المحادثة.';

  @override
  String get skillAppliedOriginal =>
      'أُضيفت المهارة إلى المحادثة الأصلية. أغلق هذه اللوحة للعودة.';

  @override
  String get activeContextTitle => 'السياق النشط';

  @override
  String get activeContextSubtitle => 'فحص الرسائل بعد اختصار السياق';

  @override
  String get activeContextRefresh => 'تحديث السياق النشط';

  @override
  String get activeContextSearch => 'البحث في الرسائل النشطة';

  @override
  String get activeContextEmpty => 'لم يُعد الخادم أي رسائل سياق نشط.';

  @override
  String get activeContextNoText => 'لا يوجد محتوى نصي مدعوم في هذا العنصر.';

  @override
  String get activeContextUnsupported =>
      'فحص السياق النشط غير متاح على هذا الخادم.';

  @override
  String get activeContextChanged =>
      'تغيّر الخادم أو المشروع أو المحادثة. افتح أداة الفحص هذه مجددًا من المحادثة.';

  @override
  String get activeContextInvalid =>
      'أعاد الخادم لقطة سياق غير صالحة. حدّث لإعادة المحاولة.';

  @override
  String activeContextRefreshFailed(String error) {
    return 'تُعرض اللقطة السابقة. تعذّر التحديث: $error';
  }

  @override
  String get activeContextContentHelp =>
      'لقطة لمحتوى الرسائل المتاح. لا تُعرض محتويات المرفقات الثنائية أو الروابط أو البيانات الوصفية الداخلية. لا يمثّل ذلك الطلب الكامل المرسل إلى مزوّد الخدمة.';

  @override
  String get activeContextUser => 'طلب المستخدم';

  @override
  String get activeContextAssistant => 'المساعد';

  @override
  String get activeContextSystem => 'تعليمات النظام';

  @override
  String get activeContextSynthetic => 'رسالة مُنشأة آليًا';

  @override
  String get activeContextSkill => 'مهارة';

  @override
  String get activeContextShell => 'الصدفة';

  @override
  String get activeContextCompaction => 'اختصار السياق';

  @override
  String get activeContextChange => 'تغيير المحادثة';

  @override
  String get activeContextText => 'نص';

  @override
  String get activeContextToolInput => 'مدخلات الأداة';

  @override
  String get activeContextToolOutput => 'مخرجات الأداة';

  @override
  String get activeContextFile => 'ملف مرفق';

  @override
  String get activeContextNotice => 'إشعار من الخادم';

  @override
  String get activeContextPruned => 'حذف الخادم المحتوى';

  @override
  String get activeContextTruncated => 'اقتطع الخادم المخرجات';

  @override
  String get draftSaveFailed => 'لم تُحفظ المسودة. انسخ نصك أو أعد المحاولة.';

  @override
  String get draftStorageFull =>
      'مساحة تخزين المسودات ممتلئة. انسخ نصك قبل المغادرة.';

  @override
  String get draftProfileRemoved =>
      'أُزيل الخادم الأصلي. انسخ مسودتك للاحتفاظ بها.';

  @override
  String get draftRetrySave => 'إعادة محاولة حفظ المسودة';

  @override
  String get draftClearFailed =>
      'تعذّر مسح المسودة المحفوظة. أعد المحاولة قبل المغادرة.';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => 'تعذّر حفظ المسودة';

  @override
  String get draftLeaveMessage =>
      'تابع التحرير لنسخ نصك أو إعادة محاولة الحفظ. قد تؤدي المغادرة الآن إلى فقدان تغييراتك غير المحفوظة.';

  @override
  String get draftLeaveAction => 'مغادرة دون حفظ';

  @override
  String get draftKeepEditing => 'متابعة التحرير';

  @override
  String get draftUnsaved => 'غير محفوظة';

  @override
  String get draftAttachmentsLocal =>
      'تُحفظ المرفقات مع هذه المسودة على هذا الجهاز.';

  @override
  String get draftAttachmentsFailed =>
      'تحتاج المرفقات إلى استرداد أو تعذّر حفظها. أعد المحاولة قبل الإرسال.';

  @override
  String get draftAttachmentRecoveryTitle => 'بعض المرفقات بحاجة إلى مراجعة';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return 'هذه المرفقات المحفوظة مفقودة أو غير قابلة للقراءة أو تتبع مشروعًا آخر: $names. استخدم المرفقات المتاحة وأزل هذه من المسودة، أو احتفظ بالمسودة المحفوظة وحاول مجددًا لاحقًا.';
  }

  @override
  String get draftUseAvailableAttachments => 'استخدام المرفقات المتاحة';

  @override
  String get draftKeepSavedAttachments => 'الاحتفاظ بالمسودة المحفوظة';

  @override
  String get photoLibraryAction => 'مكتبة الصور';

  @override
  String get photoLibraryDescription => 'اختيار صورة أو لقطة شاشة';

  @override
  String get photoCameraAction => 'التقاط صورة';

  @override
  String get photoTooLarge => 'اختر صورة أصغر من 10 MB.';

  @override
  String get photoStorageFailed =>
      'تعذّر حفظ الصورة على هذا الجهاز. وفّر بعض المساحة وأعد المحاولة.';

  @override
  String get photoPendingOther =>
      'A photo is still waiting for another conversation. Add or discard it there, then try again.';

  @override
  String get photoUnavailable =>
      'تعذّر فتح الصورة. حاول إضافتها مجددًا من مكتبة الصور أو عبر التقاط صورة.';

  @override
  String get photoPermissionDenied =>
      'رُفض الوصول إلى الصور. اسمح بالوصول إلى الكاميرا أو الصور في إعدادات تطبيق Android، ثم حاول مجددًا.';

  @override
  String get photoPendingTitle => 'صورة منتظرة';

  @override
  String get photoDiscard => 'تجاهل الصورة المنتظرة';

  @override
  String get photoAddToDraft => 'إضافة الصورة المستردّة إلى المسودة';

  @override
  String get photoOtherLocation =>
      'ارجع إلى الخادم والمشروع الأصليين للصورة قبل إضافتها.';

  @override
  String get photoDraftFull =>
      'أزل مرفقًا أولًا. تتسع المسودة لما يصل إلى 5 ملفات بإجمالي 20 MB.';

  @override
  String get quotaTitle => 'رصيد الاستخدام المتبقي';

  @override
  String quotaSourceTitle(String profile, String provider) {
    return '$profile · $provider';
  }

  @override
  String get quotaUnknownSource => 'لا يوجد خادم محفوظ';

  @override
  String get quotaSourceChanged =>
      'تغيّر الخادم أو المشروع، أو تجري إزالة بياناته المحلية. افتح رصيد الاستخدام المتبقي مجددًا لمراجعة المصدر.';

  @override
  String get quotaSetupDescription =>
      'Once it’s installed, confirm you trust it, then read. Provider tokens stay on the server.';

  @override
  String get quotaSetupNeeded =>
      'استخدم خادمًا محفوظًا بكلمة مرور وHTTPS، أو عنوان الاسترجاع المحلي للهاتف. حدّث إعدادات اتصاله قبل فحص جامع البيانات.';

  @override
  String get quotaConsent => 'ثبّتُّ جامع البيانات هذا على هذا الخادم وأثق به.';

  @override
  String get quotaRead => 'قراءة الاستخدام المتبقي';

  @override
  String get quotaRefresh => 'تحديث الاستخدام المتبقي';

  @override
  String get quotaLoading => 'جارٍ قراءة الاستخدام المتبقي';

  @override
  String get quotaCollectorAuth =>
      'لم يقبل مسار جامع البيانات بيانات دخول هذا الخادم. اطلب من مسؤول الخادم التحقق من إعدادات المصادقة.';

  @override
  String get quotaUnavailable =>
      'تعذّر تحديث الاستخدام المتبقي. تحقّق من الاتصال وجامع البيانات، ثم أعد المحاولة.';

  @override
  String get quotaInvalidResponse =>
      'أعاد جامع البيانات قراءة غير صالحة أو غير مدعومة. لا يُعرض رصيد استخدام جديد.';

  @override
  String get quotaUnconfigured =>
      'لم يُضبط مصدر حساب مصرّح به لجامع البيانات. اطلب من مسؤوله إكمال الإعداد.';

  @override
  String get quotaProviderUnsupported =>
      'لا يدعم جامع البيانات هذا تسجيل دخول OAuth المحدد أو مسار استخدام مزوّد الخدمة.';

  @override
  String get quotaProviderAuth =>
      'سجّل الدخول مجددًا بأداة تسجيل الدخول الحالية لمزوّد الخدمة على الخادم. لا يقرأ هذا التطبيق بيانات ذلك الدخول ولا يجدّدها.';

  @override
  String get quotaRateLimited =>
      'قيّد مزوّد الخدمة فحوص الحصة. انتظر قبل التحديث؛ فهذا لا يؤكد نفاد رصيد البرمجة لديك.';

  @override
  String get quotaAccountUnverified =>
      'لم يتمكن جامع البيانات من التحقق من الحساب المحدد. لا يُعرض رصيد استخدام. تحقّق من مصدر تسجيل الدخول على الخادم.';

  @override
  String get quotaStale =>
      'This is the last reading. Refresh to see the latest.';

  @override
  String get quotaUseBlocked =>
      'يفيد مزوّد الخدمة بأن استخدام Codex المعتاد محظور حاليًا. نسب الفترات وحدها لا تحدد إمكانية الاستخدام.';

  @override
  String quotaUsed(String percent) {
    return 'المستخدم $percent';
  }

  @override
  String get quotaSourceDisclosure =>
      'قراءة فقط من جامع البيانات الاختياري عبر نقطة نهاية داخلية لمزوّد الخدمة. لا تشمل أرصدة المنتجات الأخرى أو حدود النماذج أو الاعتمادات أو أهلية الاستخدام. البيانات المفقودة مجهولة، وليست بلا حدود.';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

  @override
  String get quotaClaudeUnavailable =>
      'استخدام اشتراك Claude غير متاح هنا إلى حين توفر تكامل مدعوم ومسموح به. لا يتضمن OpenCode الحالي تسجيل دخول Claude Pro/Max. لن يقرأ التطبيق بيانات دخول هذا الاشتراك أو يعيد استخدامها.';

  @override
  String get iosRemoteSummary =>
      'عميل اتصال بخادم OpenCode الذي تختاره. استضافة الخادم على الجهاز والمراقبة في الخلفية غير متاحتين في إصدار iOS هذا.';

  @override
  String get iosKeychainGuide =>
      'تُحفظ كلمات مرور الخوادم في Keychain على هذا الجهاز، ولا تُخزَّن في تفضيلات التطبيق العادية.';

  @override
  String get platformSecureStorageGuide =>
      'تُحفظ كلمات مرور الخوادم في مخزن بيانات الاعتماد الآمن لهذه المنصة، ولا تُخزَّن في تفضيلات التطبيق العادية.';

  @override
  String get quotaSourceBound =>
      'مرتبط بتسجيل دخول Claude المضبوط في جامع البيانات. استجابة الاستخدام لا تحدد الحساب بشكل مستقل.';

  @override
  String get usageProviders => 'مزوّدو الخدمة';

  @override
  String get usageProviderScope =>
      'إجماليات سجلات النماذج التي أعادها هذا الخادم للنطاق المحدد. لا تمثل فوترة مزوّد الخدمة أو أرصدة الاشتراكات.';

  @override
  String usageProviderModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نموذج',
      many: '$count نموذجًا',
      few: '$count نماذج',
      two: 'نموذجان',
      one: 'نموذج واحد',
      zero: 'لا نماذج',
    );
    return '$_temp0';
  }

  @override
  String get usageProviderCostUnavailable => 'الإجمالي الفرعي للتكلفة غير متاح';

  @override
  String usageProviderCostShare(String percent) {
    return '$percent من التكلفة المبلّغ عنها';
  }

  @override
  String get setupOutputWaiting => 'بانتظار مخرجات Termux…';

  @override
  String get uncertainAuthDetail =>
      'ربما بدأ الخادم تسجيل الدخول، لكن لم يصل معرّف المحاولة. تحقّق على الخادم قبل البدء مجددًا.';

  @override
  String get uncertainAuthCloseHint =>
      'أغلق هذه اللوحة واستخدم صف تسجيل الدخول غير المؤكد لإزالة منع إعادة المحاولة المحلي بعد التحقق من الخادم.';

  @override
  String get pluginsUnsupported => 'لا يدعم هذا الخادم فحص الإضافات.';

  @override
  String get pluginsDisconnected => 'اتصل بخادم لفحص إضافاته.';

  @override
  String get pluginsEmpty => 'لم يُبلّغ عن إضافات لهذا المشروع.';

  @override
  String get pluginsLoadFailed => 'تعذّر تحميل الإضافات. أعد المحاولة.';

  @override
  String get pluginsRefresh => 'تحديث الإضافات';

  @override
  String get pluginsRetry => 'إعادة المحاولة';

  @override
  String get pluginsUnnamed => 'إضافة بلا معرّف';

  @override
  String get pluginsStatusActive => 'نشطة';

  @override
  String get pluginsStatusUnknown => 'حالة غير معروفة';

  @override
  String get pluginsSourceBuiltin => 'مدمجة';

  @override
  String get pluginsSourcePackage => 'حزمة';

  @override
  String get pluginsSourceLocal => 'ملف محلي (المسار مخفي)';

  @override
  String get pluginsSourceSdk => 'SDK';

  @override
  String get pluginsSourceUnknown => 'مصدر غير معروف';

  @override
  String get pluginsTerminalUi => 'تعلن عن واجهة طرفية';

  @override
  String get pluginsFailureDetail =>
      'تفاصيل الفشل مخفية لأنها قد تتضمن بيانات اعتماد.';

  @override
  String get demoReviewChanges => 'مراجعة التغييرات';

  @override
  String get demoSetUpServer => 'إعداد خادمك الخاص';

  @override
  String get handoffCopyCommand => 'نسخ الأمر';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get quotaMiniMaxSourceBound =>
      'مرتبط بمفتاح MiniMax Subscription Key المضبوط في جامع البيانات. استجابة الحصة لا تحدد الحساب بشكل مستقل. تُعرض فقط نسب الرصيد العام المبلّغ عنها؛ وقد تنطبق حدود أخرى.';

  @override
  String get managedHealthLifetime =>
      'قد يوقف Android أيًا من التطبيقين. إبقاء اتصال تطبيق الهاتف نشطًا لا يضمن استمرار خادم Termux طوال الليل.';

  @override
  String get quotaBudgetOff => 'متوقف';

  @override
  String quotaBudgetPercent(String percent) {
    return 'تم استخدام $percent%';
  }

  @override
  String get quotaBudgetSaveFailed =>
      'تعذّر حفظ تغيير الميزانية. تظل آخر إعدادات محفوظة سارية.';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get usageBudgetTitle => 'ميزانيات الاستهلاك الشخصية';

  @override
  String get usageBudgetDescription =>
      'تستخدم الميزانيات كل الاستهلاك المبلّغ عنه للخادم والمشروع والمنطقة الزمنية وبداية الفترة المحددة. لا تغيّرها مرشحات النماذج. تحتاج بداية فترة جديدة إلى ميزانية جديدة. لا تغيّر هذه الميزانيات أرصدة الاشتراك ولا توقف الطلبات.';

  @override
  String get usageBudgetUsd => 'تحديد ميزانية بالدولار الأمريكي';

  @override
  String get usageBudgetTokens => 'تحديد ميزانية الرموز';

  @override
  String get usageBudgetAmount => 'قيمة الميزانية';

  @override
  String get usageBudgetRemove => 'إزالة الميزانية';

  @override
  String usageBudgetProgress(String used, String limit, String unit) {
    return '$used من $limit $unit';
  }

  @override
  String get usageBudgetTokenUnit => 'رمز';

  @override
  String get usageBudgetReached => 'بُلغت الميزانية الشخصية في هذه القراءة.';

  @override
  String get usageBudgetPrevious =>
      'بلغت القراءة السابقة هذه الميزانية. حدّث للتحقق من الاستهلاك الحالي.';

  @override
  String get usageBudgetClearAll => 'مسح ميزانيات الاستهلاك المحفوظة';

  @override
  String get usageBudgetClearDescription =>
      'هل تريد إزالة جميع ميزانيات الاستهلاك الحالية والسابقة لهذا الخادم المحفوظ؟ ستبقى حدود مزوّدي الخدمة.';

  @override
  String get usageBudgetClearTitle => 'مسح ميزانيات الاستهلاك؟';

  @override
  String get monitorScope =>
      'تشمل الأعداد آخر مشروع محدد لكل خادم، وليس جميع مشاريعه.';

  @override
  String get monitorDisclosure =>
      'Monitoring is off until you enable it for a server. Checks run about once a minute while this app is open. Background checks run no more often than every five minutes, only while Stay connected in the background is on and Android’s service is running. Android can stop that service; no remaining runtime is promised.';

  @override
  String get monitorOptIn => 'مراقبة هذا الخادم';

  @override
  String get monitorOptInDetail =>
      'فحص الأذونات والأسئلة والنماذج المعلّقة في آخر مشروع محدد له.';

  @override
  String get monitorNotifications => 'الإشعار عند الحاجة إلى انتباه';

  @override
  String get monitorWifiDetail =>
      'تتوقف الفحوص مؤقتًا ما لم يرصد Android شبكة Wi-Fi نشطة. قد تؤدي شبكة VPN أو عدم توفر معلومات الشبكة إلى إيقاف الفحوص مؤقتًا.';

  @override
  String get monitorQuiet => 'ساعات الهدوء';

  @override
  String get monitorQuietStart => 'بداية ساعات الهدوء';

  @override
  String get monitorQuietEnd => 'نهاية ساعات الهدوء';

  @override
  String get monitorSaveFailed => 'تعذّر حفظ إعدادات المراقبة. أعد المحاولة.';

  @override
  String get monitorOpenFailed =>
      'تغيّر هذا الطلب أو مشروعه. حدّث صندوق الوارد وأعد المحاولة.';

  @override
  String get monitorSession => 'محادثة';

  @override
  String get monitorPermission => 'يلزم إذن';

  @override
  String get monitorQuestion => 'تلزم إجابة';

  @override
  String get monitorForm => 'تلزم تعبئة النموذج';

  @override
  String get monitorUnknown => 'غير معروف';

  @override
  String get monitorLastChecked => 'آخر تحقق';

  @override
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  ) {
    return '$profile · $kind\n$lastChecked: $time';
  }

  @override
  String get monitorCheckIn => 'متابعة العمليات الطويلة';

  @override
  String get monitorCheckInDetail =>
      'يظهر تذكير عندما تمتد الفحوص التي ترصد انشغالًا للمدة المختارة. قد يتوقف العمل أو يُعاد تشغيله بين الفحوص. تُجرى محاولة إشعار واحدة كحد أقصى لكل فترة مرصودة، أثناء تفعيل «إبقاء الاتصال نشطًا».';

  @override
  String get monitorCheckInDetailForeground =>
      'يظهر صف تذكير عندما تمتد الفحوص التي ترصد انشغالًا للمدة المختارة. قد يتوقف العمل أو يُعاد تشغيله بين الفحوص. لا يستطيع هذا الجهاز إرسال تذكيرات في الخلفية.';

  @override
  String get monitorCheckInAfter => 'التذكير بالمتابعة بعد';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes دقيقة',
      many: '$minutes دقيقة',
      few: '$minutes دقائق',
      two: 'دقيقتين',
      one: 'دقيقة واحدة',
      zero: '$minutes دقيقة',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => 'حان وقت المتابعة';

  @override
  String get quotaBudgetClearAll => 'مسح حدود مزوّدي الخدمة المحفوظة';

  @override
  String managedRecoveryAttempts(int attempts) {
    return 'المحاولات المستخدمة: $attempts من 3. يبقى الحد محفوظًا بعد إعادة تشغيل التطبيق.';
  }

  @override
  String get managedRecoveryExhausted =>
      'بُلغ حد الاستعادة. افحص الخادم وشغّله يدويًا قبل إعادة ضبط عدد المحاولات.';

  @override
  String get managedRecoveryBackground =>
      'تنتظر الاستعادة أثناء وجود التطبيق في الخلفية.';

  @override
  String get managedRecoveryChecking =>
      'جارٍ فحص عملية الاستعادة التي يديرها التطبيق…';

  @override
  String managedRecoveryNext(String time) {
    return 'لن تبدأ محاولة الاستعادة التالية قبل $time.';
  }

  @override
  String get managedRecoveryCheck => 'التحقق من حالة الاستعادة';

  @override
  String get managedRecoveryReset => 'إعادة ضبط عدد المحاولات';

  @override
  String get managedRecoverySaveFailed =>
      'تعذّر حفظ إعدادات الاستعادة. أعد المحاولة.';

  @override
  String get managedRecoveryRevokeFailed =>
      'تعذّر حفظ الاستعادة أو إلغاؤها. احتفظ بهذا الخادم وأعد المحاولة قبل إزالته.';

  @override
  String get managedRecoverySettingsUnreadable =>
      'تعذّرت قراءة إعدادات الاستعادة. افحص الخادم قبل تفعيل الاستعادة.';

  @override
  String get managedRecoveryEnableFailed =>
      'تعذّر تفعيل الاستعادة. شغّل الخادم الذي يديره التطبيق، ثم أعد المحاولة.';

  @override
  String get managedRecoveryOwnershipChanged =>
      'تغيّرت العملية التي يديرها التطبيق. افحص الخادم قبل تفعيل الاستعادة مجددًا.';

  @override
  String get managedRecoveryUncertain =>
      'توقفت الاستعادة مؤقتًا لأن Termux لم يؤكد النتيجة. اضغط «تحديث» للمتابعة.';

  @override
  String get managedRecoveryRetryDisable => 'إعادة محاولة تعطيل الاستعادة';

  @override
  String get pluginMappingUnavailable =>
      'لم تعد هذه الإضافة أو هذا الأمر متاحًا هنا. حدّث وراجع روابطك.';

  @override
  String get mobileTasksUnfinished => 'إظهار غير المكتملة فقط';

  @override
  String get mobileTasksNoUnfinished => 'لا مهام غير مكتملة في هذه القائمة.';

  @override
  String get mobileTaskPending => 'معلّقة';

  @override
  String get mobileTaskInProgress => 'قيد التنفيذ';

  @override
  String get mobileTaskCompleted => 'مكتملة';

  @override
  String get mobileTaskCancelled => 'ملغاة';

  @override
  String mobileTasksProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get mobileTasksCopyAll => 'نسخ جميع المهام';

  @override
  String get mobileTaskPriorityHigh => 'أولوية عالية';

  @override
  String get mobileTaskPriorityMedium => 'أولوية متوسطة';

  @override
  String get mobileTaskPriorityLow => 'أولوية منخفضة';

  @override
  String get quotaMonitorTitle => 'مراقبة الحصص';

  @override
  String get quotaMonitorRuntime =>
      'تُفحص المصادر بالتناوب، ثلاثة مصادر كحد أقصى في الدورة؛ وتحتاج القوائم الأطول إلى عدة دورات. تتطلب القراءات في الخلفية أن تكون خدمة الاتصال الحالية نشطة؛ وقد يوقفها Android. تنتهي صلاحية القراءات المعروضة وفقًا لجامع البيانات. تسجل تنبيهات الجهاز قراءات سابقة بلغت الحد، ولا تعرض الرصيد المتبقي الحالي. لا تبدّل هذه الصفحة خادمك النشط مطلقًا.';

  @override
  String get quotaMonitorDisabled => 'المراقبة متوقفة.';

  @override
  String get quotaMonitorWaiting => 'بانتظار قراءة حديثة.';

  @override
  String get quotaMonitorChecking => 'Checking now…';

  @override
  String get quotaMonitorPaused =>
      'Paused. Checks start again when the app is open or Stay connected in the background is on.';

  @override
  String get quotaMonitorWifiRequired => 'Waiting for Wi-Fi to check again.';

  @override
  String get quotaMonitorSourceChanged =>
      'The account on this server changed, so checks stopped. Open Remaining usage on that server and read it again.';

  @override
  String get quotaMonitorSaveFailed =>
      'تعذّر حفظ مراقبة الحصص. عند فشل التعطيل تبقى المراقبة متوقفة مؤقتًا في هذا التطبيق؛ أعد المحاولة قبل إغلاقه.';

  @override
  String quotaMonitorDisable(String provider, String server) {
    return 'تعطيل مراقبة الحصص';
  }

  @override
  String get webSearchDisclosure =>
      'يرسل البحث استعلامك إلى مزوّد البحث المحدد على هذا الخادم. راجع النتائج قبل إضافتها إلى مسودتك القابلة للتعديل. لا يُرسل شيء إلى النموذج هنا.';

  @override
  String get webSearchManual => 'أو الصق مصدرًا';

  @override
  String get webSearchUnavailable =>
      'البحث على الويب غير متاح. اضبط مزوّد بحث على هذا الخادم، ثم حدّث مزوّدي الخدمة. لا يزال بإمكانك لصق مصدر أدناه.';

  @override
  String get webSearchAuthentication =>
      'لم يسمح الخادم بالبحث على الويب. تحقّق من بيانات اعتماد هذا الخادم.';

  @override
  String get webSearchInvalidResponse =>
      'لم تطابق استجابة البحث هذا الخادم أو التنسيق المدعوم. حدّث مزوّدي الخدمة أو الصق مصدرًا.';

  @override
  String get webSearchFailed =>
      'تعذّر إكمال البحث على الويب. أعد المحاولة أو الصق مصدرًا.';

  @override
  String get webSearchRefresh => 'تحديث مزوّدي الخدمة';

  @override
  String get webSearchProvider => 'مزوّد البحث';

  @override
  String get webSearchQuery => 'استعلام البحث';

  @override
  String get webSearchSubmit => 'بحث';

  @override
  String get webSearchEmpty => 'لا نتائج صالحة لهذا الاستعلام.';

  @override
  String get webSearchOmitted =>
      'أُخفيت بعض النتائج لأن روابطها أو مقتطفاتها تجاوزت حدود المراجعة.';

  @override
  String get queueStorageUnreadable =>
      'تعذّرت قراءة الطلبات المحفوظة في قائمة الانتظار. لا يمكن إضافة طلبات جديدة إلى القائمة حتى تُمسح بيانات الجهاز هذه.';

  @override
  String get queueStorageDiscardUnreadable =>
      'يحذف هذا نهائيًا الطلبات غير المقروءة في قائمة الانتظار ومرفقاتها من هذا الجهاز. محتواها وعددها غير معروفين. لا يتأثر أي شيء على الخادم.';

  @override
  String get filesViewerScopeChanged =>
      'تغيّر الخادم. أغلق هذا الملف وافتحه مجددًا.';

  @override
  String get queueStorageCountUnknown =>
      'تعذّرت قراءة بيانات قائمة الانتظار المحفوظة. عدد الطلبات في القائمة غير معروف.';

  @override
  String get codexConnectionVerified =>
      'تم التحقق من الاتصال. احفظ واتصل للمتابعة.';

  @override
  String get codexApprovalRecoveryNotice =>
      'بعد إعادة الاتصال، راجع أي موافقات معلّقة على الكمبيوتر.';

  @override
  String get connectionTokenRejected =>
      'رُفض رمز الاتصال. حدّثه لإعادة الاتصال.';

  @override
  String connectionPasswordUnreadable(String server) {
    return 'Can\'t read the saved password for $server';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return 'Can\'t read the saved token for $server';
  }

  @override
  String get connectionEnterPassword => 'Enter the password';

  @override
  String get connectionEnterToken => 'Enter the token';

  @override
  String get connectionPasswordUnreadableDetails =>
      'This phone\'s secure storage couldn\'t open the password saved for this server. That can happen after the phone is restored from a backup or its screen lock is changed. The password itself was not changed: enter it again to connect.';

  @override
  String get connectionTokenUnreadableDetails =>
      'This phone\'s secure storage couldn\'t open the token saved for this server. That can happen after the phone is restored from a backup or its screen lock is changed. The token itself was not changed: enter it again to connect.';

  @override
  String get updateConnectionToken => 'تحديث الرمز';

  @override
  String get codexDraftReconnectNotice =>
      'تبقى مسودة المراجعة هنا؛ ولا يُرسل شيء تلقائيًا.';

  @override
  String get codexTextOnlyPrompt =>
      'يدعم هذا الخادم النص فقط. أزل المرفقات قبل الإرسال.';

  @override
  String get codexOfflineDraftSaved =>
      'أعد الاتصال قبل الإرسال. مسودتك محفوظة على هذا الجهاز.';

  @override
  String get codexReconnectBeforeSending => 'أعد الاتصال قبل الإرسال.';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 أو wss://paseo.example';

  @override
  String get paseoPasswordLabel => 'كلمة مرور الخدمة (اختيارية)';

  @override
  String get paseoPasswordHelp =>
      'عيّنها بالأمر \"paseo daemon set-password\". تُحفظ في التخزين الآمن لهذا الجهاز.';

  @override
  String get paseoSetupNotice =>
      'شغّل \"paseo start --no-relay\" على الحاسوب الذي ثُبّت عليه Claude Code أو Pi. لا يستخدم هذا التطبيق مرحّل Paseo أبدًا: اتصل من هذا الجهاز أو عبر شبكتك الخاصة.';

  @override
  String get connectionDisplayName => 'الاسم المعروض (اختياري)';

  @override
  String get connectionDisplayNameHint => 'يُستخدم اسم مضيف الخادم افتراضيًا';

  @override
  String get connectionServerAddress => 'عنوان الخادم';

  @override
  String get codexAddressHint => 'wss://codex.example أو ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => 'مجلد المشروع على الخادم';

  @override
  String get codexTokenReentry => 'أعد إدخال رمز الاتصال';

  @override
  String get codexTokenLabel => 'رمز الاتصال';

  @override
  String get codexTokenStorageHelp =>
      'يُحفظ بأمان على هذا الجهاز، ويُرسل إلى خادم Codex هذا فقط.';

  @override
  String get projectConfiguredFolder => 'المجلد المضبوط';

  @override
  String get termuxGuideTitle => 'ربط Termux مرة واحدة';

  @override
  String get termuxGuideOpenFailed =>
      'تم نسخ الأمر، لكن تعذّر فتح Termux. افتحه بنفسك أو حاول «نسخ وفتح Termux» مجددًا.';

  @override
  String get termuxGuideCopyOpenFailed => 'تعذّر نسخ الأمر أو فتح Termux.';

  @override
  String get termuxPermissionDenied =>
      'رفض Android إذن أوامر Termux. اسمح به في إعدادات تطبيق OpenCode.';

  @override
  String get launchShortcutWaiting =>
      'جارٍ الاتصال بالخادم المحفوظ. تُفتح المحادثة الجديدة عندما يصبح جاهزًا.';

  @override
  String get launchShortcutNoServer => 'اختر خادمًا، ثم ابدأ محادثة جديدة.';

  @override
  String get launchShortcutReentry =>
      'أدخل بيانات اعتماد الخادم المحفوظ، ثم ابدأ محادثة جديدة.';

  @override
  String get launchShortcutConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. اختر خادمًا أو أصلح اتصاله، ثم ابدأ محادثة جديدة.';

  @override
  String get launchUiPinnedUntitled => 'محادثة بلا عنوان';

  @override
  String get launchUiSessionWaiting =>
      'جارٍ الاتصال بالخادم المحفوظ. ستُفتح المحادثة عندما يصبح جاهزًا.';

  @override
  String get launchUiSessionNoServer =>
      'اختر خادمًا، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionReentry =>
      'أدخل بيانات اعتماد الخادم المحفوظ، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. اختر خادمًا أو أصلحه، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiSessionOtherServer =>
      'هذا الاختصار يخص خادمًا آخر. اتصل بذلك الخادم، ثم افتح المحادثة من قائمته.';

  @override
  String get launchUiActivityNoServer =>
      'اختر خادمًا لترى ما يحتاج إلى انتباهك.';

  @override
  String get queuedResendTitle => 'إرسال هذه المسودة مجددًا؟';

  @override
  String get queuedResendMessage =>
      'ربما وصلت إلى OpenCode بالفعل. قد يكررها الإرسال مجددًا.';

  @override
  String get queuedResendConfirm => 'إرسال مجددًا';

  @override
  String get queuedKeepForReview => 'الاحتفاظ للمراجعة';

  @override
  String get queuedDiscardUnconfirmedMessage =>
      'لم يُؤكَّد إرسالها السابق؛ ربما توجد في المحادثة بالفعل.';

  @override
  String get setupRuntimeOne => 'OpenCode 1';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      many: '$count مسودة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      few: '$count مسودات بإرسال غير مؤكد تحتاج إلى مراجعة.',
      two: 'مسودتان بإرسال غير مؤكد تحتاجان إلى مراجعة.',
      one: 'مسودة واحدة بإرسال غير مؤكد تحتاج إلى مراجعة.',
      zero: 'لا مسودات بإرسال غير مؤكد تحتاج إلى مراجعة.',
    );
    return '$_temp0';
  }

  @override
  String get isolatedTaskTitle => 'Start in a separate copy';

  @override
  String get isolatedTaskIntro =>
      'Works on its own branch, so it can\'t clash with your other conversations.';

  @override
  String get isolatedTaskNameLabel => 'Name of the copy (optional)';

  @override
  String get isolatedTaskNameHelper =>
      'Leave it empty and a name is chosen for you.';

  @override
  String get isolatedTaskStart => 'Start';

  @override
  String get isolatedTaskCreating => 'Making the copy…';

  @override
  String get isolatedTaskCreatingHint =>
      'If you stop waiting, the copy may still be made. You\'ll find it under Project › Worktrees.';

  @override
  String isolatedTaskPreparing(String name) {
    return 'Setting up $name…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name is ready. Opening the conversation…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name is ready, but the conversation didn\'t open.';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return '$name is made, but its setup hasn\'t reported back.';
  }

  @override
  String get isolatedTaskUnconfirmedHint =>
      'Setup may still be running. Keep waiting, or start in it now.';

  @override
  String isolatedTaskFailed(String name) {
    return 'Setup failed in $name';
  }

  @override
  String get isolatedTaskCreateFailed => 'Couldn\'t make the copy';

  @override
  String get isolatedTaskCancelled => 'Stopped waiting.';

  @override
  String isolatedTaskOpening(String name) {
    return 'Opening the conversation in $name…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return 'The conversation in $name is ready.';
  }

  @override
  String get isolatedTaskStopWaiting => 'Stop waiting';

  @override
  String get isolatedTaskKeepWaiting => 'Keep waiting';

  @override
  String get isolatedTaskRetryOpen => 'إعادة المحاولة';

  @override
  String get isolatedTaskClose => 'إغلاق';

  @override
  String get returnBriefStatusUnknown => 'حالة المراجعة غير معروفة';

  @override
  String get returnBriefReview => 'مراجعة النتائج';

  @override
  String get returnBriefContinue => 'متابعة';

  @override
  String get capsuleError => 'خطأ';

  @override
  String get capsuleRemove => 'إزالة';

  @override
  String get markdownWrapCode => 'التفاف الأسطر';

  @override
  String get markdownScrollCode => 'تمرير الأسطر';

  @override
  String get markdownReaderTitle => 'قارئ الشيفرة';

  @override
  String get tailscaleTitle => 'الاتصال عبر Tailscale';

  @override
  String get tailscaleIntro =>
      'اتصل بـ OpenCode على كمبيوتر آخر عبر شبكة Tailscale الخاصة بك. تتحكم في تسجيل الدخول والوصول عبر VPN من تطبيق Tailscale الرسمي.';

  @override
  String get tailscaleAppStep => '1. افتح شبكتك الخاصة';

  @override
  String get tailscaleChecking => 'جارٍ التحقق من تطبيق Tailscale…';

  @override
  String get tailscaleInstalled => 'Tailscale مثبّت. اتصال VPN غير مؤكد.';

  @override
  String get tailscaleMissing =>
      'Tailscale غير مثبّت. ثبّت التطبيق الرسمي، ثم عد وتحقّق مجددًا.';

  @override
  String get tailscaleUnknown =>
      'تعذّر فحص التطبيق. أعد المحاولة، أو افتح Tailscale من هاتفك.';

  @override
  String get tailscaleUnsupported =>
      'لا يستطيع هذا الجهاز فتح تطبيق Android. اضبط Tailscale على هذا الجهاز بنفسك، ثم راجع عنوان HTTPS أدناه.';

  @override
  String get tailscaleVpnHandoff =>
      'في Tailscale، سجّل الدخول إلى الشبكة التي تصل إلى خادمك، ووافق على طلب VPN من Android إن ظهر، ثم فعّل الاتصال. لا يستطيع OpenCode رؤية حالة VPN أو تغييرها.';

  @override
  String get tailscaleReturned =>
      'مرحبًا بعودتك. أُعيد التحقق من وجود التطبيق؛ استخدم «اختبار الاتصال» في الشاشة التالية لفحص خادمك.';

  @override
  String get tailscaleOpen => 'فتح Tailscale';

  @override
  String get tailscaleCheckAgain => 'فحص التطبيق مجددًا';

  @override
  String get tailscaleAddressStep => '2. راجع عنوان خادمك';

  @override
  String get tailscaleAddressLabel => 'عنوان خادم HTTPS الخاص';

  @override
  String get tailscaleAddressDetail =>
      'استخدم أصل HTTPS الكامل الذي يعرضه Tailscale Serve، مثل https://computer.tailnet-name.ts.net. احتفظ بأي منفذ HTTPS يعرضه. قد لا يوفّر اسم جهاز مختصر أو منفذ HTTP مباشر شهادة صالحة.';

  @override
  String get tailscaleAddressError =>
      'أدخل أصل HTTPS بمنفذ صالح (1–65535). أزل المسارات وبيانات الاعتماد ونص الاستعلام والأجزاء الملحقة. استخدم العنوان الكامل من Serve؛ ولا تستبدل https بـ http.';

  @override
  String get tailscaleReviewDetail =>
      'تابع فقط بعنوان تعرفه. تراجع الشاشة التالية بيانات اعتماد خادمك قبل أن تختبره أو تحفظه صراحةً. لا يستطيع التطبيق تأكيد خصوصية عنوان من اسمه وحده.';

  @override
  String get tailscaleContinue => 'المتابعة إلى المصادقة';

  @override
  String get tailscaleHelp => 'إعداد Tailscale واستعادة الاتصال';

  @override
  String get tailscaleServeHelp =>
      'على كمبيوتر الخادم، يستطيع Tailscale Serve توفير HTTPS خاص لمنفذ OpenCode محلي. استخدم Serve، وليس Funnel العام. تظل قواعد الوصول إلى شبكة tailnet سارية. ينشر تفعيل HTTPS اسمي الجهاز وشبكة tailnet في سجل شهادات عام، مع بقاء الوصول خاصًا. راجع الدليل الرسمي قبل تغيير خادمك.';

  @override
  String get tailscaleServeDocs => 'قراءة دليل Serve الرسمي';

  @override
  String get tailscaleAndroidDocs => 'قراءة دليل Android الرسمي';

  @override
  String get tailscaleRecovery =>
      'إذا تعذّر الوصول إلى الخادم، فتحقّق من Tailscale على الجهازين، واسم HTTPS الكامل ومنفذه، وServe على الخادم، وقواعد وصول شبكتك. قد يمنع تعارض VPN أو DNS الوصول أيضًا. أبقِ HTTPS مفعّلًا. صحّح كلمة مرور الخادم إذا رُفضت المصادقة، ثم أعد «اختبار الاتصال».';

  @override
  String get tailscaleEditorDetail =>
      'تُدار شبكة اتصالك في Tailscale. يفحص «اختبار الاتصال» خادم OpenCode هذا، لا شبكة VPN. أدخل هنا اسم مستخدم الخادم وكلمة مروره، لا بيانات دخول Tailscale. تحافظ مساعدة الإعداد على هذه الحقول.';

  @override
  String get a2aDraftSaveError =>
      'تعذّر حفظ تعديلات المسودة. أبقِ هذه الشاشة مفتوحة وأعد المحاولة قبل المغادرة.';

  @override
  String get a2aRetryDraftSave => 'إعادة محاولة حفظ المسودة';

  @override
  String get a2aSavingDraft => 'جارٍ حفظ تعديلات المسودة…';

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · مهام نصية';

  @override
  String get a2aTitle => 'الوكلاء الخارجيون';

  @override
  String get a2aAdd => 'إضافة وكيل';

  @override
  String get a2aDeleteLocal => 'إزالة من هذا الهاتف';

  @override
  String get a2aAddress => 'عنوان الوكيل';

  @override
  String get a2aUnsupported =>
      'غير متاح: لا تعلن هذه البطاقة عن المجموعة المدعومة من A2A 1.0 وJSON-RPC والنص والمصادقة على الأصل نفسه، أو تتطلب امتدادًا غير مدعوم. لا يمكن إرسال أي مهمة.';

  @override
  String get a2aBearer => 'بيانات اعتماد bearer للوكيل';

  @override
  String get a2aSkills => 'المهارات المعلنة';

  @override
  String get a2aTaskPrompt => 'نص المهمة';

  @override
  String get a2aDeliveryUnconfirmed => 'التسليم غير مؤكد';

  @override
  String get a2aDraft => 'لم تُرسل';

  @override
  String get a2aCancelDetail =>
      'اطلب من هذا الوكيل إيقاف هذه المهمة. ربما اكتمل العمل بالفعل؛ ويقرر الوكيل ما إذا كان الإيقاف ممكنًا.';

  @override
  String get a2aYourReply => 'ردك';

  @override
  String get a2aAgentOutput => 'مخرجات الوكيل';

  @override
  String get a2aBlockedLink => 'رابط غير مدعوم';

  @override
  String get a2aReviewLink => 'مراجعة الرابط الخارجي';

  @override
  String get a2aOmittedContent =>
      'حُجب بعض المخرجات. يعرض هذا المشهد نصًا وروابط ضمن حدود محددة؛ ولا تُنزَّل المخرجات الثنائية أو المنظّمة ولا تُنفَّذ.';

  @override
  String get a2aSubmitted => 'تم تقديمها';

  @override
  String get a2aWorking => 'قيد التنفيذ';

  @override
  String get a2aInputRequired => 'يلزم ردك';

  @override
  String get a2aAuthRequired => 'الوكيل يتطلب المصادقة';

  @override
  String get a2aCompleted => 'مكتملة';

  @override
  String get a2aFailed => 'فشلت';

  @override
  String get a2aCanceled => 'ملغاة';

  @override
  String get a2aRejected => 'مرفوضة';

  @override
  String get a2aUnknown => 'حالة مهمة غير مدعومة';

  @override
  String get a2aAddressError =>
      'استخدم أصل HTTPS أو رابط بطاقة وكيل عامة دون بيانات اعتماد أو استعلام أو جزء ملحق. يُدعم HTTP على عنوان الاسترجاع المحلي لهذا الجهاز فقط.';

  @override
  String get a2aAuthenticationError =>
      'رفض الوكيل بيانات الاعتماد هذه أو تعذّر عليه استخدامها. ارجع إلى الوكيل لتحديثها، ثم أعد فتح المهمة المحفوظة.';

  @override
  String get a2aUnavailable =>
      'تعذّر الوصول إلى الوكيل أو رفض هذه العملية. حدّث مهمة معروفة للتحقق من حالتها.';

  @override
  String get a2aInvalidResponse =>
      'أعاد الوكيل استجابة غير مدعومة أو أكبر من الحد أو غير مطابقة. لم تُستبدل المهمة المحفوظة.';

  @override
  String get a2aUncertain =>
      'ربما استلم الوكيل هذه الرسالة. لن تُرسل مجددًا. إذا تأكد معرّف المهمة، فحدّث للتحقق من التقدم؛ وإلا فتحقّق مع الوكيل قبل بدء مهمة أخرى.';

  @override
  String get a2aStorageError =>
      'تعذّر حفظ البيانات المحلية أو إزالتها. تحقّق من مساحة الجهاز وأعد العملية المحلية. لا تُرسل رسالة دون حفظ علامة تسليم لها.';

  @override
  String get a2aScopeError =>
      'تغيّر هذا الوكيل أو بيانات اعتماده أو المهمة المحفوظة. أغلق هذا العرض وأعد فتح الوكيل للمتابعة.';

  @override
  String get a2aCancelUnconfirmed =>
      'الإلغاء غير مؤكد. لا يزال الوكيل يبلّغ عن مهمة نشطة؛ حدّث للتحقق مجددًا.';

  @override
  String get a2aAuthRequiredDetail =>
      'طلب هذا الوكيل مسار مصادقة إضافيًا لا يدعمه هذا العميل. لن يحدث تسجيل دخول أو استئناف للمهمة تلقائيًا.';

  @override
  String get a2aUnknownDetail =>
      'حالة هذه المهمة غير مدعومة. يمكنك تحديث السجل المحلي أو نسيانه؛ بينما يظل الإرسال والإلغاء غير متاحين.';

  @override
  String get fileTable => 'جدول';

  @override
  String get fileSource => 'المصدر';

  @override
  String fileLineOutsidePreview(int line) {
    return 'السطر $line خارج هذه المعاينة. احفظ الملف الأصلي لقراءة ذلك الموضع.';
  }

  @override
  String get fileTableMalformed =>
      'علامات الاقتباس في هذا الملف غير مكتملة أو غير متسقة. اقرأ مصدره بدلًا من ذلك.';

  @override
  String get fileTableTooLarge =>
      'تدعم معاينة الجدول ملفات حتى 256 KB. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileTableTooWide =>
      'يحتوي هذا الملف على أكثر من 32 عمودًا. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileTableFieldTooLong =>
      'تتجاوز إحدى الخلايا 4,096 حرفًا. اقرأ المصدر أو احفظ الملف الأصلي.';

  @override
  String get fileImage => 'صورة';

  @override
  String get fileSvgUnsupported =>
      'لا يمكن عرض SVG هذا كصورة محلية ثابتة. اقرأ مصدره أو احفظ الملف الأصلي. لا تُدعم الموارد الخارجية أو الحركة أو ميزات SVG المعقدة.';

  @override
  String get filePdfEncrypted =>
      'يتطلب PDF هذا كلمة مرور أو يستخدم حماية غير مدعومة. احفظ الملف الأصلي لفتحه في تطبيق PDF.';

  @override
  String get filePdfLimit =>
      'تدعم معاينة PDF ملفات حتى 10 MB وأول 200 صفحة. احفظ الملف الأصلي لقراءة المستند كاملًا.';

  @override
  String get filePdfUnavailable =>
      'عرض PDF متاح على Android 10 أو أحدث. لا يزال بإمكانك حفظ الملف الأصلي.';

  @override
  String get filePdfCancelled =>
      'أُلغي تحميل PDF. أعد المحاولة عندما تكون جاهزًا.';

  @override
  String get filePdfFailed =>
      'تعذّر عرض صفحة PDF هذه. أعد المحاولة أو احفظ الملف الأصلي.';

  @override
  String get agentAccountTitle => 'حساب Codex';

  @override
  String get agentAccountScopeLost =>
      'تغيّر هذا الخادم. ارجع إلى «الخوادم» وافتح الحساب للخادم المتصل.';

  @override
  String get agentAccountRefresh => 'تحديث الحساب';

  @override
  String get agentAccountLoading => 'جارٍ فحص حساب المضيف';

  @override
  String get agentAccountUnavailable => 'لوحة الحساب غير متاحة';

  @override
  String get agentAccountReadFailed => 'تعذّرت قراءة الحساب';

  @override
  String get agentAccountDisconnected => 'انقطع الاتصال';

  @override
  String get agentAccountConnected => 'تم تسجيل الدخول على المضيف';

  @override
  String get agentAccountSignedOut => 'جاهز لتسجيل الدخول';

  @override
  String get agentAccountInProgress => 'جارٍ تسجيل الدخول';

  @override
  String get agentAccountNeedsAttention => 'تسجيل الدخول يحتاج إلى انتباه';

  @override
  String get agentAccountNoAuth => 'المضيف لا يتطلب تسجيل دخول';

  @override
  String get agentAccountApiKey => 'مفتاح API';

  @override
  String get agentAccountHostAuth => 'مصادقة المضيف';

  @override
  String get agentAccountHostNote =>
      'تحتفظ بيئة Codex الرسمية ببيانات اعتماد مزوّد الخدمة. تنطبق تغييرات الحساب على هذا المضيف، بما في ذلك الخوادم المحفوظة الأخرى المتصلة به.';

  @override
  String get agentAccountUnsupportedDetail =>
      'تم التحقق من هذه اللوحة مع Codex 0.153.4. قد لا تدعم بيئة التشغيل المتصلة طرق الحساب هذه.';

  @override
  String get agentAccountReconnectDetail =>
      'مُسحت بيانات الحساب ورمز تسجيل الدخول. أعد الاتصال للتحديث. لن يُعاد بدء تسجيل الدخول تلقائيًا.';

  @override
  String get agentAccountSignIn => 'تسجيل الدخول باستخدام ChatGPT';

  @override
  String get agentAccountSignInNote =>
      'ابدأ تسجيل دخول رسميًا برمز جهاز على هذا المضيف. أكمله في متصفحك؛ ولا يتلقى التطبيق رموز مزوّد الخدمة مطلقًا.';

  @override
  String get agentAccountLimits => 'حدود معدل الطلبات';

  @override
  String get agentAccountLimitsUnavailable =>
      'حدود معدل الطلبات غير متاحة لهذا الحساب أو المضيف.';

  @override
  String get agentAccountUsage => 'استخدام الرموز';

  @override
  String get agentAccountUsageUnavailable =>
      'استخدام الرموز غير متاح لهذا الحساب أو المضيف.';

  @override
  String get agentAccountLifetimeTokens => 'إجمالي الرموز منذ البدء';

  @override
  String get agentAccountPeakTokens => 'أعلى استخدام يومي للرموز';

  @override
  String get agentAccountUsageNote =>
      'يبلّغ المضيف عن القيم. القيم المفقودة مجهولة، وليست صفرًا. لا تمثل أعداد الرموز فاتورة أو رصيد رسائل متبقيًا.';

  @override
  String agentAccountUpdated(String time) {
    return 'آخر تحقق $time';
  }

  @override
  String get agentAccountStarting => 'جارٍ طلب رمز تسجيل الدخول';

  @override
  String get agentAccountWaiting => 'أكمل تسجيل الدخول في متصفحك';

  @override
  String get agentAccountCancelling => 'جارٍ إلغاء تسجيل الدخول';

  @override
  String get agentAccountCancelled => 'أُلغي تسجيل الدخول';

  @override
  String get agentAccountLoginFailed =>
      'لم يكتمل تسجيل الدخول. تحقّق من المضيف وأعد المحاولة.';

  @override
  String get agentAccountLoginUncertain =>
      'تعذّر على المضيف تأكيد تسجيل الدخول أو الإلغاء. ربما لا يزال ينتظر. افحص بيئة التشغيل الرسمية على المضيف قبل البدء مجددًا.';

  @override
  String get agentAccountLoginCompleted =>
      'اكتمل تسجيل الدخول. جارٍ فحص الحساب.';

  @override
  String get agentAccountCodeHint =>
      'أدخل هذا الرمز الذي يُستخدم مرة واحدة في صفحة تسجيل الدخول الرسمية. احتفظ به سريًا.';

  @override
  String get agentAccountOpenSignIn => 'فتح تسجيل الدخول الرسمي';

  @override
  String get agentAccountCancel => 'إلغاء تسجيل الدخول';

  @override
  String get agentAccountAllowance => 'رصيد الاستخدام المبلّغ عنه';

  @override
  String agentAccountPercentUsed(int percent) {
    return 'تم استخدام $percent%';
  }

  @override
  String get agentAccountWindowUnknown => 'مدة الفترة غير متاحة';

  @override
  String agentAccountWindowMinutes(int minutes) {
    return 'مدة الفترة بالدقائق: $minutes';
  }

  @override
  String agentAccountWindowHours(int hours) {
    return 'مدة الفترة بالساعات: $hours';
  }

  @override
  String agentAccountWindowDays(int days) {
    return 'مدة الفترة بالأيام: $days';
  }

  @override
  String get agentAccountResetUnknown => 'موعد التجديد غير متاح';

  @override
  String get projectFolderChooserTitle => 'اختر مجلد مشروع';

  @override
  String get projectFolderCreate => 'إنشاء مجلد جديد';

  @override
  String get projectFolderOpen => 'فتح مجلد مشروع';

  @override
  String get projectFolderNoCreateHint =>
      'لا يستطيع هذا الخادم إنشاء مجلدات من التطبيق. أنشئ المجلد على ذلك الجهاز، ثم افتحه هنا باستخدام مساره.';

  @override
  String get projectFolderNameLabel => 'اسم المجلد';

  @override
  String get projectFolderNameHint => 'my-app';

  @override
  String get projectFolderCreateAction => 'إنشاء';

  @override
  String get projectFolderCancel => 'إلغاء';

  @override
  String get projectFolderOpenMessage =>
      'أدخل المسار الكامل لمجلد على الخادم. لا يمكن استخدام المجلد الرئيسي نفسه؛ اختر مشروعًا داخله.';

  @override
  String get projectFolderPathLabel => 'مسار المجلد';

  @override
  String projectFolderPathHint(String directory) {
    return '$directory/my-app';
  }

  @override
  String get projectFolderOpenAction => 'فتح';

  @override
  String projectFolderCreateSubtitle(String directory) {
    return 'في $directory على هذا الجهاز';
  }

  @override
  String get projectFolderOpenSubtitle => 'أدخل المسار الكامل لمجلد على الخادم';

  @override
  String get globalSessionsTitle => 'جميع المحادثات';

  @override
  String get globalSessionsSearchLabel => 'البحث في عناوين المحادثات';

  @override
  String get globalSessionsArchivedShort => 'مؤرشفة';

  @override
  String get globalSessionsUnknownLocation => 'مشروع غير معروف';

  @override
  String get globalSessionsEmptyTitle => 'لا محادثات بعد';

  @override
  String get globalSessionsEmptyMessage =>
      'ستظهر هنا المحادثات من كل مشاريع هذا الخادم.';

  @override
  String get globalSessionsNoMatchTitle => 'لا محادثات مطابقة';

  @override
  String get globalSessionsNoMatchMessage =>
      'جرّب البحث بعنوان أقصر أو تضمين المحادثات المؤرشفة.';

  @override
  String get globalSessionsRefresh => 'تحديث';

  @override
  String get globalSessionsLoadMoreFailed => 'تعذّر تحميل المزيد من المحادثات';

  @override
  String get globalSessionsOpen => 'فتح';

  @override
  String get globalSessionsContinueHere => 'المتابعة هنا';

  @override
  String get globalSessionsActions => 'إجراءات المحادثة';

  @override
  String get globalSessionsWorking => 'قيد التنفيذ';

  @override
  String get globalSessionsUntitled => 'محادثة بلا عنوان';

  @override
  String get workspaceNewSession => 'محادثة جديدة';

  @override
  String get workspaceDismissNotice => 'تجاهل';

  @override
  String get workspaceManageProjectHint =>
      'تبديل المشروع، وأشجار العمل، وحالة المشروع';

  @override
  String get reviewCopyFile => 'نسخ الملف المحدّث';

  @override
  String get reviewCopyPatch => 'نسخ الرقعة';

  @override
  String get onboardingValueTitle => 'واصل تقدّم عملك.';

  @override
  String get onboardingValueBody =>
      'اطلب تغييرًا من وكيل البرمجة، وراجع النتيجة، وتابع من حيث توقفت.';

  @override
  String get onboardingDemoNote => 'محادثة محاكاة. لا تحتاج إلى خادم.';

  @override
  String get onboardingPrivateNetwork => 'الوصول إلى خادم عبر شبكتك الخاصة';

  @override
  String get onboardingSetupGuide => 'دليل الإعداد';

  @override
  String get onboardingSaveConnect => 'حفظ واتصال';

  @override
  String get onboardingSaveChanges => 'حفظ التغييرات';

  @override
  String get onboardingTermuxSetup => 'على هذا الهاتف';

  @override
  String get activityClearHere => 'لا شيء يحتاج إلى انتباه هنا';

  @override
  String get activityStatusIncomplete => 'الحالة غير مكتملة';

  @override
  String get activityUnknownStatusDetail =>
      'لم تُحمّل طلبات. لا يزال بعض نشاط الخوادم غير معروف.';

  @override
  String get activityCheckAgain => 'إعادة المحاولة';

  @override
  String get activitySavedServers => 'الخوادم المحفوظة';

  @override
  String get demoTaskTitle => 'جرّب تغييرًا بسيطًا';

  @override
  String get demoTaskInstruction =>
      'أرسل الطلب النموذجي أدناه، ثم راجع التعديل المقترح.';

  @override
  String get reviewTitle => 'مراجعة';

  @override
  String get modelChoiceReloadProviders => 'إعادة تحميل مزوّدي الخدمة';

  @override
  String get modelChoiceDone => 'تم';

  @override
  String get modelChoicePartialSaveError =>
      'حُفظ النموذج. لم يُؤكَّد اختيار الوكيل. أعد المحاولة.';

  @override
  String get modelChoiceModelSaveError =>
      'تعذّر تأكيد اختيار النموذج. تحقّق من اختيارك وأعد المحاولة.';

  @override
  String get workIdle => 'خامل';

  @override
  String get workStartedInBackground => 'بدأ في الخلفية';

  @override
  String get workBackgroundPending => 'جارٍ طلب العمل في الخلفية…';

  @override
  String get workBackgroundRequested =>
      'طُلب العمل في الخلفية. ستُحدّث الحالة عندما يبلّغ عنها الخادم.';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String setupSwitchConfirmTitle(String runtime) {
    return 'التبديل إلى $runtime؟';
  }

  @override
  String get setupSwitchConfirmDetail =>
      'يوقف خادم هذا الهاتف والمهام الجارية. تبقى المحادثات وإعدادات مزوّدي الخدمة وبيانات الاعتماد منفصلة؛ بينما تُشارك ملفات المشروع وإعداداته. يمكنك العودة إلى الإصدار السابق.';

  @override
  String setupSwitchConfirm(String runtime) {
    return 'التبديل إلى $runtime';
  }

  @override
  String get setupSwitchPending =>
      'لم يكتمل تبديل بيئة التشغيل. أعد محاولة تشغيل البيئة المحددة أو ارجع إلى السابقة. تُحفظ بيانات بيئات التشغيل لديك.';

  @override
  String setupSwitchReturn(String runtime) {
    return 'العودة إلى $runtime';
  }

  @override
  String setupSwitchRetry(String runtime) {
    return 'إعادة محاولة $runtime';
  }

  @override
  String get setupSwitchFailed =>
      'تعذّر إكمال تبديل بيئة التشغيل. افحص مخرجات الإعداد، ثم أعد المحاولة أو ارجع إلى بيئة التشغيل السابقة.';

  @override
  String get setupSwitchLegacyTwo =>
      'يحتفظ تثبيت OpenCode 2 هذا ببياناته الحالية. لا يتاح تبديله إلى OpenCode 1.';

  @override
  String setupSwitchProfileName(String runtime) {
    return 'هذا الهاتف · $runtime';
  }

  @override
  String get setupSwitchMissingCredential =>
      'بيانات الاعتماد المحفوظة لبيئة التشغيل السابقة غير متاحة. تبقى بياناتها محفوظة؛ استعد الخادم المحفوظ قبل العودة.';

  @override
  String setupSwitchProgressTitle(String runtime) {
    return 'جارٍ التبديل إلى $runtime';
  }

  @override
  String e7ConnectionFailure1(int attempts) {
    return 'عدد المحاولات: $attempts. لن تؤدي إعادة المحاولة إلى تشغيل خادم متوقف.';
  }

  @override
  String get e7ConnectionFailure2 => 'يلزم رمز اتصال';

  @override
  String get e7ConnectionFailure3 =>
      'يتطلب خادم Codex هذا رمز اتصال قبل أن يتمكن التطبيق من الاتصال به.';

  @override
  String get e7ConnectionFailure4 =>
      'افتح إعدادات الخادم وأدخل رمز اتصال Codex.';

  @override
  String get e7ConnectionFailure5 => 'رُفض رمز الاتصال';

  @override
  String get e7ConnectionFailure6 =>
      'استجاب خادم Codex، لكنه لم يقبل رمز الاتصال المحفوظ.';

  @override
  String get e7ConnectionFailure7 =>
      'افتح إعدادات الخادم وأدخل رمز اتصال Codex ساريًا.';

  @override
  String get e7ConnectionFailure8 => 'خدمة استقبال اتصالات Codex غير متاحة';

  @override
  String get e7ConnectionFailure9 => 'تعذّر الوصول إلى نقطة اتصال Codex';

  @override
  String get e7ConnectionFailure12 =>
      'شغّل خدمة استقبال اتصالات Codex على هذا الجهاز.';

  @override
  String get e7ConnectionFailure13 =>
      'إذا كان الاتصال عبر نفق، فأبقِ النفق قيد التشغيل وتحقّق من نقطة اتصاله المحلية.';

  @override
  String get e7ConnectionFailure14 =>
      'استخدم نقطة اتصال Codex عبر wss:// أو نفقًا آمنًا نشطًا.';

  @override
  String get e7ConnectionFailure15 =>
      'تأكّد من إمكانية الوصول إلى خدمة استقبال اتصالات Codex البعيدة من هذا الجهاز.';

  @override
  String get e7ConnectionFailure16 => 'رُفضت كلمة المرور';

  @override
  String get e7ConnectionFailure17 =>
      'استجاب الخادم، لكنه لم يقبل كلمة المرور المحفوظة. يحدث هذا عند إعادة تشغيل الخادم بكلمة مرور جديدة.';

  @override
  String get e7ConnectionFailure18 =>
      'شغّل opencode2 pair على الكمبيوتر والصق الرمز الجديد.';

  @override
  String get e7ConnectionFailure19 =>
      'إذا ضبطت OPENCODE_SERVER_PASSWORD يدويًا، فانسخه مجددًا.';

  @override
  String get e7ConnectionFailure20 => 'الشهادة غير موثوقة';

  @override
  String get e7ConnectionFailure21 =>
      'الخادم موجود، لكن هذا الجهاز لا يثق بشهادة HTTPS الخاصة به، لذلك رفض التطبيق إرسال كلمة المرور.';

  @override
  String get e7ConnectionFailure22 =>
      'استخدم شهادة من جهة موثوقة، أو عنوان Tailscale Serve.';

  @override
  String get e7ConnectionFailure23 =>
      'إذا كانت الشهادة موقّعة ذاتيًا، فثبّتها على هذا الجهاز أولًا.';

  @override
  String get e7ConnectionFailure24 => 'لا خدمة تستقبل الاتصالات على هذا الجهاز';

  @override
  String get e7ConnectionFailure26 =>
      'هل تشغّل OpenCode في Termux؟ افتح Termux وتأكّد من أن الخادم ما زال قيد التشغيل.';

  @override
  String get e7ConnectionFailure27 =>
      'هل تستخدم adb reverse أو إعادة توجيه SSH؟ تأكّد من أن النفق ما زال متصلًا، ثم أعد المحاولة.';

  @override
  String get e7ConnectionFailure28 =>
      'هل تريد الاتصال بكمبيوتر آخر؟ غيّر عنوان الخادم إلى عنوان HTTPS الخاص به أو أعد الاقتران.';

  @override
  String get e7ConnectionFailure29 => 'لم يستجب الخادم في الوقت المحدد';

  @override
  String get e7ConnectionFailure31 =>
      'هل أنت متصل بالشبكة نفسها أو بشبكة VPN نفسها (مثل Tailscale) التي يتصل بها الكمبيوتر؟';

  @override
  String e7ConnectionFailure32(int port) {
    return 'هل يحجب جدار حماية أو بوابة تسجيل دخول الشبكة المنفذ $port؟';
  }

  @override
  String get e7ConnectionFailure33 => 'تعذّر الوصول إلى الخادم';

  @override
  String get e7ConnectionFailure35 =>
      'هل ما زال opencode serve قيد التشغيل على الكمبيوتر؟';

  @override
  String get e7ConnectionFailure36 =>
      'هل أنت متصل بالشبكة نفسها أو بشبكة VPN نفسها التي يتصل بها الكمبيوتر؟';

  @override
  String get e7ConnectionFailure37 =>
      'هل تغيّر العنوان؟ أعد الاقتران للحصول على العنوان الجديد.';

  @override
  String get e7ConnectionFailure38 => 'استجاب الخادم بخطأ';

  @override
  String get e7ConnectionFailure39 =>
      'الخادم قيد التشغيل، لكنه أبلغ عن خلل في حالته. سيوضح سجله السبب.';

  @override
  String get e7ConnectionFailure40 => 'أعد تشغيل opencode serve وراقب مخرجاته.';

  @override
  String get e7ConnectionFailure41 =>
      'تأكّد من أن هذا التطبيق يدعم إصدار الخادم.';

  @override
  String get e7ConnectionFailure42 => 'تعذّر الاتصال';

  @override
  String get e7ConnectionFailure44 =>
      'هل خادم الوكيل يعمل، وهل هذا هو العنوان الصحيح؟';

  @override
  String get e7ConnectionFailure45 =>
      'هل opencode serve قيد التشغيل، وهل هذا هو العنوان الصحيح؟';

  @override
  String get e7PermissionAction1 => 'تشغيل أمر في الطرفية';

  @override
  String get e7PermissionAction2 => 'تعديل ملف';

  @override
  String get e7PermissionAction3 => 'قراءة ملف';

  @override
  String get e7PermissionAction4 => 'الوصول إلى مجلد خارجي';

  @override
  String get e7PermissionAction5 => 'المتابعة بعد إخفاقات متكررة';

  @override
  String get e7PermissionAction6 => 'يلزم إذن';

  @override
  String e7PermissionAction7(String permission) {
    return 'استخدام $permission';
  }

  @override
  String get e7GlossaryMcpExplanation =>
      'بروتوكول سياق النموذج (Model Context Protocol). خوادم إضافية صغيرة تمنح الوكيل أدوات أخرى، مثل متصفح أو قاعدة بيانات أو أداة تصميم. تربطها مرة واحدة ويمكن لكل محادثة استخدامها.';

  @override
  String get e7GlossaryWorktreeExplanation =>
      'نسخة عمل منفصلة من المستودع نفسه. استخدمها عندما تريد أن يجرّب الوكيل شيئًا على فرع خاص به دون المساس بالشيفرة التي تعمل عليها.';

  @override
  String get e7GlossaryGotIt => 'فهمت';

  @override
  String get e7BannerReconnectPassword =>
      'تغيّرت كلمة مرور الخادم — أعد الاتصال.';

  @override
  String get e7BannerUpdatePassword => 'تحديث كلمة المرور';

  @override
  String get e7BannerLost => 'فُقد الاتصال';

  @override
  String get e7BannerRetrying => 'جارٍ إعادة المحاولة';

  @override
  String get e7BannerDetails => 'التفاصيل';

  @override
  String e7BannerReconnectPasswordNote(String note) {
    return 'تغيّرت كلمة مرور الخادم — أعد الاتصال.\n$note';
  }

  @override
  String e7BannerReconnectingServer(String server) {
    return 'جارٍ إعادة الاتصال بـ $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return 'جارٍ إعادة الاتصال بـ $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      'يبقى المحتوى المعروض متاحًا أثناء فحص OpenCode. تُستأنف التحديثات المباشرة تلقائيًا.';

  @override
  String get e7BannerStaleExplanation =>
      'قد يكون المحتوى المعروض قديمًا حتى يصبح OpenCode متاحًا مجددًا.';

  @override
  String get e7SharedThreeStepsToYourFirstSession =>
      'ثلاث خطوات لبدء أول محادثة';

  @override
  String get e7SharedOpenCodeRunsOnYourComputerThisApp =>
      'يعمل OpenCode على حاسوبك، وهذا التطبيق هو جهاز التحكم عن بُعد. الاقتران يربط بينهما بأمر واحد، دون كتابة عناوين أو كلمات مرور.';

  @override
  String get e7SharedOnYourComputerRunOneCommand =>
      'على حاسوبك، شغّل أمرًا واحدًا';

  @override
  String get e7SharedInATerminalOnTheComputerWhere =>
      'في طرفية على الحاسوب المثبَّت عليه OpenCode:';

  @override
  String get e7SharedItStartsTheServerAndPrintsA =>
      'يبدأ الخادم ويطبع رمز اقتران، بالإضافة إلى رمز QR يمكنك مسحه.';

  @override
  String get e7SharedScanTheQROrPasteTheCode =>
      'امسح رمز QR أو الصق الرمز في هذا التطبيق';

  @override
  String get e7SharedPasteTheCodeInThisApp => 'الصق الرمز في هذا التطبيق';

  @override
  String get e7SharedStartTalking => 'ابدأ الحديث';

  @override
  String get e7SharedPickAProjectAndSendYourFirst =>
      'اختر مشروعًا وأرسل رسالتك الأولى. يجري العمل على حاسوبك، ويعرضه هذا التطبيق ويتيح لك توجيهه.';

  @override
  String get e7SharedAdvanced => 'خيارات متقدمة';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServersTermuxInternals =>
      'HTTPS وأنفاق SSH والخوادم الأقدم وتفاصيل Termux';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServers =>
      'HTTPS وأنفاق SSH والخوادم الأقدم';

  @override
  String get e7SharedReachAServerOverHTTPSOrA =>
      'الوصول إلى خادم عبر HTTPS أو نفق';

  @override
  String get e7SharedPairingWorksWhenTheAddressTheServer =>
      'يعمل الاقتران عندما يكون العنوان الذي يطبعه الخادم قابلًا للوصول من هذا الجهاز. وإن لم يكن كذلك، فاعرض الخادم عبر وكيل عكسي HTTPS أو نفق مشفّر، ثم أضف عنوان https:// الناتج يدويًا. HTTP البعيد محظور عمدًا.';

  @override
  String get e7SharedOlderServersWithoutPairing => 'الخوادم الأقدم بدون اقتران';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      'الخوادم التي تُشغَّل بالأمر «opencode serve» لا تطبع رمز اقتران. شغّلها على loopback مع كلمة مرور:';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      'ثم أضف الخادم يدويًا باسم المستخدم opencode وكلمة المرور تلك.';

  @override
  String get e7SharedOnDeviceViaTermuxAutomated =>
      'على الجهاز عبر Termux (آلي)';

  @override
  String get e7SharedUseTheOnDeviceTermuxCardOn =>
      'استخدم بطاقة «على الجهاز (Termux)» في شاشة الخوادم. يثبّت التطبيق Termux، ويفتح الجسر، ويجهّز opencode، ويشغّل الخادم ويتصل، كل ذلك بإرشاد خطوة بخطوة.';

  @override
  String get e7SharedOnlyTwoTapsNeedYouPersonallyDownloading =>
      'خطوتان فقط تحتاجان إليك شخصيًا: تنزيل حزمة APK الخاصة بـ Termux، ولصق سطر فتح واحد داخل Termux مرة واحدة. كلاهما يفرضه نموذج أمان Android، لا هذا التطبيق.';

  @override
  String get e7SharedPreferManualInsideTermuxRun =>
      'تفضّل الطريقة اليدوية؟ داخل Termux شغّل:';

  @override
  String get e7SharedTheChrootSharesTheNetworkStackSo =>
      'يتشارك chroot مكدّس الشبكة، لذا يعمل http://127.0.0.1:4096 من هذا التطبيق. شغّل `termux-wake-lock` لإبقائه نشطًا.';

  @override
  String get e7SharedSecurityNotes => 'ملاحظات أمنية';

  @override
  String get e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding =>
      'اضبط OPENCODE_SERVER_PASSWORD دائمًا عند الربط خارج localhost.';

  @override
  String get e7SharedPasswordsAreStoredInTheAndroidKeystore =>
      'تُخزَّن كلمات المرور في Android Keystore على هذا الجهاز فقط.';

  @override
  String get e7SharedTheServerCanExecuteCommandsOnIts =>
      'يستطيع الخادم تنفيذ أوامر على مضيفه، فتعامل مع الوصول إليه كما تتعامل مع وصول SSH.';

  @override
  String get e7SharedOpenCodeIsReconnectingTryAgain =>
      'يعيد OpenCode الاتصال. حاول مرة أخرى.';

  @override
  String get e7SharedSessionContext => 'سياق المحادثة';

  @override
  String get e7SharedRefreshContext => 'تحديث السياق';

  @override
  String get e7SharedNoContextUsageYet => 'لا يوجد استخدام للسياق بعد';

  @override
  String get e7SharedSendAPromptAndWaitForAn =>
      'أرسل طلبًا وانتظر رد المساعد. سيبلّغ OpenCode بعدها عن استخدام الرموز في هذه المحادثة.';

  @override
  String get e7SharedEstimatedInputMakeup => 'التركيب التقديري للمدخلات';

  @override
  String get e7SharedSessionTotals => 'إجماليات المحادثة';

  @override
  String get e7SharedUsageComesFromTheLatestCompletedAssistant =>
      'يُؤخذ الاستخدام من آخر رسالة مكتملة للمساعد. التركيب تقدير مبني على نص الطلب والرد والأدوات الظاهر؛ ويشمل «أخرى» تعليمات النظام وتعريفات الأدوات وحمل المزوّد الإضافي.';

  @override
  String get e7SharedModelUnavailable => 'النموذج غير متاح';

  @override
  String get e7SharedContextLimitUnavailable => 'حد السياق غير متاح';

  @override
  String get e7SharedLatestAssistantRequestIncludingCacheActivity =>
      'آخر طلب للمساعد، بما في ذلك نشاط ذاكرة التخزين المؤقت';

  @override
  String get e7SharedContextLimit => 'حد السياق';

  @override
  String get e7SharedUnavailable => 'غير متاح';

  @override
  String get e7SharedMessages => 'الرسائل';

  @override
  String get e7SharedAccumulatedCostReportedByServer =>
      'التكلفة المتراكمة · حسب تقرير الخادم';

  @override
  String get e7SharedAccumulatedCost => 'التكلفة المتراكمة';

  @override
  String get e7SharedSessionTokensReportedByServer =>
      'رموز المحادثة · حسب تقرير الخادم';

  @override
  String get e7SharedUserPrompts => 'طلبات المستخدم';

  @override
  String get e7SharedAssistantText => 'نص المساعد';

  @override
  String get e7SharedToolCallsAndResults => 'استدعاءات الأدوات ونتائجها';

  @override
  String get e7SharedOtherContext => 'سياق آخر';

  @override
  String get e7SharedSessionLocationChangedCloseAndReopenThis =>
      'تغيّر مشروع المحادثة. أغلق هذه الورقة وأعد فتحها.';

  @override
  String get e7SharedOpenCodeIsReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get e7SharedTheSessionProjectIsNotAvailableOn =>
      'مشروع المحادثة غير متاح على هذا الخادم.';

  @override
  String get e7SharedLocalProject => 'مشروع محلي';

  @override
  String get e7SharedTheAppCouldNotInspectWorkingChanges =>
      'تعذّر على التطبيق فحص تغييرات العمل. للسلامة، ستتم المتابعة دون نقل التغييرات.';

  @override
  String get e7SharedMoveSession => 'نقل المحادثة';

  @override
  String get e7SharedChooseAnotherDirectoryInThisProject =>
      'اختر مجلدًا آخر في هذا المشروع.';

  @override
  String get e7SharedChooseAConnectedWorkspaceOrReturnTo =>
      'اختر بيئة سحابية متصلة، أو عُد إلى المشروع المحلي.';

  @override
  String get e7SharedFilterDestinations => 'تصفية الوجهات';

  @override
  String get e7SharedCurrent => 'الحالي';

  @override
  String get e7SharedSwitchOrganization => 'تبديل المؤسسة؟';

  @override
  String get e7SharedSwitchOrganization462 => 'تبديل المؤسسة';

  @override
  String get e7SharedNoSwitchableOpenCodeConsoleOrganizationsWereReturned =>
      'لم تُرجَع أي مؤسسات قابلة للتبديل في OpenCode Console.';

  @override
  String get e7SharedSessionLocationChangedReturnAndReopenRelated =>
      'تغيّر مشروع المحادثة. عُد وأعد فتح المحادثات المرتبطة.';

  @override
  String get e7SharedSessionIsNoLongerRelatedToThis =>
      'لم تعد تلك المحادثة مرتبطة بهذه المحادثة.';

  @override
  String get e7SharedSessionLocationChangedReturnAndTryAgain =>
      'تغيّر مشروع المحادثة. عُد وحاول مرة أخرى.';

  @override
  String get e7SharedSessionUnavailableOrLocationChangedReturnOr =>
      'المحادثة غير متاحة أو تغيّر مشروعها. عُد أو حدّث للمحاولة مرة أخرى.';

  @override
  String get e7SharedCouldNotUpdateThePinReturnAnd =>
      'تعذّر تحديث التثبيت. عُد وحاول مرة أخرى.';

  @override
  String get e7SharedRefreshSubagentSessions =>
      'تحديث محادثات الوكلاء الفرعيين';

  @override
  String get e7SharedParentSession => 'المحادثة الأصل';

  @override
  String get e7SharedNoSubagentSessionsYet =>
      'لا توجد محادثات وكلاء فرعيين بعد';

  @override
  String get e7SharedDelegatedWorkWillAppearHereWithoutMixing =>
      'سيظهر العمل المُفوَّض هنا دون خلط محادثات الوكلاء الفرعيين بقائمتك الرئيسية.';

  @override
  String get e7SharedOpenInsecureHTTPLink => 'فتح رابط HTTP غير آمن؟';

  @override
  String get e7SharedOpenExternalLink => 'فتح رابط خارجي؟';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP غير مشفّر. قد تتمكن أجهزة أخرى على الشبكة من قراءة ما ترسله وتستقبله أو تغييره.';

  @override
  String get e7SharedOpenHTTPLink => 'فتح رابط HTTP';

  @override
  String get e7SharedOpenLink => 'فتح الرابط';

  @override
  String get e7SharedNoAppCouldOpenThisLink =>
      'لم يتمكن أي تطبيق من فتح هذا الرابط.';

  @override
  String get e7SharedRequired => 'مطلوب';

  @override
  String get e7SharedDoesNotMatchTheExpectedFormat =>
      'لا يطابق التنسيق المتوقع';

  @override
  String get e7SharedEnterAWholeNumber => 'أدخل عددًا صحيحًا';

  @override
  String get e7SharedEnterANumber => 'أدخل رقمًا';

  @override
  String get e7SharedDismissThisRequest => 'تجاهل هذا الطلب؟';

  @override
  String get e7SharedTheAgentContinuesWithoutYourAnswers =>
      'سيواصل الوكيل دون إجاباتك.';

  @override
  String get e7SharedAskedByAnMCPServer => 'سؤال من خادم MCP';

  @override
  String get e7SharedAskedByTheAgentInThisSession =>
      'سؤال من الوكيل في هذه المحادثة';

  @override
  String get e7SharedInputRequested => 'مطلوب إدخال';

  @override
  String get e7SharedOther => 'أخرى…';

  @override
  String get e7SharedYourAnswer => 'إجابتك';

  @override
  String get e7SharedAddYourOwn => 'أضف إجابتك';

  @override
  String get e7SharedAddAnswer => 'إضافة إجابة';

  @override
  String get e7SharedThisServerSentALinkThisApp =>
      'أرسل هذا الخادم رابطًا لن يفتحه هذا التطبيق.';

  @override
  String get e7SharedSendAnswers => 'إرسال الإجابات';

  @override
  String e7SharedDetail307(int step) {
    return 'الخطوة $step من 3';
  }

  @override
  String e7SharedDetail385(String count, String limit) {
    return '$count من $limit رمزًا';
  }

  @override
  String e7SharedDetail386(String count) {
    return '$count رمزًا · الحد غير متاح';
  }

  @override
  String e7SharedDetail429(String destination) {
    return 'Move conversation to $destination?';
  }

  @override
  String e7SharedDetail514(String error) {
    return 'فشل التحديث: $error';
  }

  @override
  String e7SharedDetail714(int count) {
    return 'يجب ألا يقل عن $count حرفًا';
  }

  @override
  String e7SharedDetail715(int count) {
    return 'يجب ألا يزيد عن $count حرفًا';
  }

  @override
  String e7SharedDetail721(String minimum, String maximum) {
    return 'يجب أن يكون بين $minimum و$maximum';
  }

  @override
  String e7SharedDetail722(String minimum) {
    return 'يجب ألا يقل عن $minimum';
  }

  @override
  String e7SharedDetail723(String maximum) {
    return 'يجب ألا يزيد عن $maximum';
  }

  @override
  String e7SharedDetail726(int count) {
    return 'اختر $count على الأكثر';
  }

  @override
  String e7SharedDetail753(int minimum, int maximum) {
    return 'اختر $minimum–$maximum';
  }

  @override
  String e7SharedDetail754(int count) {
    return 'اختر $count على الأقل';
  }

  @override
  String e7SharedDetail755(int count) {
    return 'اختر حتى $count';
  }

  @override
  String e7SharedDetail756(String range, int count) {
    return '$range · تم تحديد $count';
  }

  @override
  String e7SharedDetail764(String host) {
    return 'يفتح $host في متصفحك';
  }

  @override
  String e7SharedDetail765(String field) {
    return 'أرسل هذا الخادم نوع حقل لا يفهمه هذا التطبيق (\"$field\").';
  }

  @override
  String get e7LocaleUiLanguage => 'اللغة';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => 'استخدام لغة النظام';

  @override
  String get e7LocaleUiClose => 'إغلاق';

  @override
  String get e7LocaleUiDescription =>
      'اختر لغة التطبيق. تبقى رسائل الخادم والنصوص التي تكتبها بلغتها الأصلية.';

  @override
  String get e7LocaleUiSaving => 'جارٍ حفظ اللغة…';

  @override
  String get e7LocaleUiSaveFailed =>
      'تعذّر حفظ اللغة. لا يزال اختيارك السابق مفعّلًا. اختر لغة للمحاولة مجددًا.';

  @override
  String get e7LocaleUiNewSession => 'محادثة جديدة';

  @override
  String get e7LocaleUiNewSessionHint => 'ابدأ محادثة في المشروع الحالي';

  @override
  String get e7LocaleUiWorkspace => 'العمل';

  @override
  String get e7LocaleUiWorkspaceHint => 'المحادثات الأخيرة والمشروع الحالي';

  @override
  String get e7LocaleUiFiles => 'المشروع';

  @override
  String get e7LocaleUiFilesHint =>
      'الملفات والتغييرات والطرفية وأدوات المشروع الأخرى';

  @override
  String get e7LocaleUiActivity => 'الوارد';

  @override
  String get e7LocaleUiActivityHint => 'الأذونات والأسئلة والنماذج';

  @override
  String get e7LocaleUiMoreHint =>
      'النماذج ومزوّدو الخدمة والإشعارات والإعدادات';

  @override
  String get e7LocaleUiSettings => 'الإعدادات';

  @override
  String get e7LocaleUiKeyboardShortcuts => 'اختصارات لوحة المفاتيح';

  @override
  String get e7LocaleUiRefreshSessions => 'تحديث المحادثات';

  @override
  String get e7LocaleUiDiagnostics => 'التشخيص';

  @override
  String get e7LocaleUiDiagnosticsHint => 'الأخطاء الأخيرة وتفاصيل الاتصال';

  @override
  String get e7LocaleUiCommandLauncher => 'قائمة الأوامر';

  @override
  String get e7LocaleUiFindSurface => 'البحث في هذه الصفحة';

  @override
  String get e7LocaleUiDestinations => 'العمل، الوارد، المشروع، الإعدادات';

  @override
  String get e7LocaleUiTerminal => 'الطرفية';

  @override
  String get e7LocaleUiCloseScreen => 'إغلاق هذه الصفحة';

  @override
  String get e7LocaleUiSendPrompt => 'إرسال الطلب';

  @override
  String get e7LocaleUiCopyTranscript => 'نسخ النص المحدّد من المحادثة';

  @override
  String get e7LocaleUiRecentModel =>
      'النموذج الأخير التالي / السابق في هذه المحادثة';

  @override
  String get e7LocaleUiThisList => 'هذه القائمة';

  @override
  String get e7LocaleUiCloseOverlay => 'إغلاق لوحة أو مربع حوار أو قائمة';

  @override
  String get e7LocaleUiContextActions => 'إجراءات الرسائل والملفات والمحادثات';

  @override
  String get e7LocaleUiContextKeys => 'نقرة بالزر الأيمن / Shift + F10 / Menu';

  @override
  String get e7LocaleUiConnectionChanged => 'تغيّر الخادم.';

  @override
  String get e7AppearanceFollowAndroid => 'اتباع إعداد Android';

  @override
  String get e7AppearanceFollowSystem => 'اتباع إعداد النظام';

  @override
  String get e7AppearanceLight => 'فاتح';

  @override
  String get e7AppearanceDark => 'داكن';

  @override
  String get e7AppearanceFollowPhoneDescription =>
      'مطابقة إعداد المظهر الفاتح أو الداكن لهذا الهاتف';

  @override
  String get e7AppearanceFollowDeviceDescription =>
      'مطابقة إعداد المظهر الفاتح أو الداكن لهذا الجهاز';

  @override
  String get e7AppearanceLightDescription => 'استخدام المظهر الفاتح';

  @override
  String get e7AppearanceDarkDescription => 'استخدام المظهر الداكن';

  @override
  String get e7AppearanceTitle => 'المظهر';

  @override
  String get e7AppearancePreviewHint =>
      'عاين المظهر أولاً. لن يتغيّر إلا عند تطبيقه.';

  @override
  String get e7AppearanceDynamicUnavailable =>
      'ألوان Material You غير متاحة على هذا الجهاز.';

  @override
  String e7AppearanceUsesMode(String mode) {
    return 'يبقى إعداد المظهر الفاتح أو الداكن كما هو: $mode.';
  }

  @override
  String get e7AppearanceSaveFailed =>
      'تعذّر حفظ المظهر. لم يتغيّر إعدادك السابق. حاول مجددًا.';

  @override
  String get e7AppearanceSaving => 'جارٍ الحفظ…';

  @override
  String get e7AppearanceApply => 'تطبيق';

  @override
  String get e7AppearanceCurrent => 'المظهر الحالي';

  @override
  String get e7AppearanceClose => 'إغلاق';

  @override
  String get e7AppearancePreviewTitle => 'النص وعناصر التحكّم';

  @override
  String get e7AppearancePreviewBody =>
      'شاهد تناسق النص والشيفرة والإجراءات المحدّدة.';

  @override
  String get e7AppearanceSelection => 'خيار محدّد';

  @override
  String get e7AppearanceTryControl => 'جرّب التحكّم';

  @override
  String get e7AppearanceSampleHint =>
      'تغيّر عناصر التحكّم التجريبية هذه المعاينة فقط.';

  @override
  String get e7SettingsUi1 => 'الخادم';

  @override
  String get e7SettingsUi8 => 'قطع الاتصال';

  @override
  String get e7SettingsUi9 => 'خادم OpenCode';

  @override
  String get e7SettingsUi11 => 'جارٍ فحص حالة الخادم…';

  @override
  String get e7SettingsUi12 => 'أوقفه Android';

  @override
  String get e7SettingsUi14 => 'مفعّل · يعمل الآن';

  @override
  String get e7SettingsUi15 => 'مفعّل · جارٍ البدء';

  @override
  String get e7SettingsUi16 => 'هذا الخادم';

  @override
  String get e7SettingsUi17 => 'غير معروف';

  @override
  String get e7SettingsUi18 => 'جارٍ إعادة الاتصال بـ OpenCode.';

  @override
  String get e7SettingsUi19 => 'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا.';

  @override
  String get e7SettingsUi22 => 'لم يفعّل Android وضع الخلفية.';

  @override
  String get e7SettingsUi23 => 'أوقف Android الاتصال المباشر';

  @override
  String get e7SettingsUi24 =>
      'تم استنفاد الحد اليومي لمزامنة البيانات في الخلفية، فتوقف الوضع المباشر تلقائيًا. فعّله لإعادة الاتصال؛ يتجدد الحد خلال 24 ساعة.';

  @override
  String get e7SettingsUi25 => 'البقاء متصلاً في الخلفية';

  @override
  String get e7SettingsUi26 =>
      'يواصل تحديث المهام عند إغلاق التطبيق ويُشعرك عندما تتطلب إحداها تدخلك. يستهلك طاقة إضافية ويعرض إشعارًا دائمًا.';

  @override
  String get e7SettingsUi27 => 'استخدام البطارية دون قيود مسموح';

  @override
  String get e7SettingsUi28 => 'السماح باستخدام البطارية دون قيود';

  @override
  String get e7SettingsUi29 =>
      'قد يستمر Android في تطبيق الحد الزمني لخدمة المقدّمة.';

  @override
  String get e7SettingsUi30 =>
      'اختياري. يساعد على إبقاء الاتصال المباشر أثناء وضع السكون. يحدّ Android 15 والإصدارات الأحدث مزامنة البيانات في الخلفية بست ساعات لكل 24 ساعة.';

  @override
  String get e7SettingsUi31 => 'أوقفه Android — اضغط لإعادة التشغيل';

  @override
  String get e7SettingsUi32 => 'يعمل الآن';

  @override
  String get e7SettingsUi34 =>
      'Android stops this after 6 hours a day. The app will tell you when it does.';

  @override
  String get e7SettingsUi35 => 'الصدفة الافتراضية';

  @override
  String get e7SettingsUi36 =>
      'تستخدمها الطرفيات الجديدة وأوامر الصدفة المتوافقة على خادم OpenCode هذا.';

  @override
  String get e7SettingsUi37 =>
      'للطرفية فقط؛ يستخدم OpenCode بديلاً متوافقًا لأدوات الصدفة.';

  @override
  String get e7SettingsUi39 => 'تلقائي (إعداد الخادم الافتراضي)';

  @override
  String get e7SettingsUi41 => 'جارٍ تحميل الصدف من OpenCode…';

  @override
  String get e7SettingsUi46 => 'أعد تشغيل OpenCode على الجهاز المضيف';

  @override
  String get e7SettingsUi48 => 'تحديث OpenCode البعيد؟';

  @override
  String get e7SettingsUi49 => 'تغيّر الخادم النشط قبل اكتمال التحديث';

  @override
  String get e7SettingsUi50 => 'تحديث OpenCode المُدار';

  @override
  String get e7SettingsUi51 =>
      'تثبيت أحدث إصدار مستقر للخادم وتحديث النماذج وإعادة التشغيل بأمان ثم إعادة الاتصال.';

  @override
  String get e7SettingsUi52 => 'الإصدار السابق';

  @override
  String get e7SettingsUi53 => 'إصدار غير معروف';

  @override
  String get e7SettingsUi56 => 'غير متصل';

  @override
  String get e7SettingsUi57 => 'فحص حالة الخادم';

  @override
  String get e7SettingsUi58 => 'جارٍ الاستعلام عن حالة الخادم';

  @override
  String get e7SettingsUi59 => 'الخادم يعمل جيدًا';

  @override
  String get e7SettingsUi60 => 'حالة الخادم غير متاحة';

  @override
  String get e7SettingsUi62 => 'لم تُحفظ كلمة مرور للخادم';

  @override
  String get e7SettingsUi65 => 'التشغيل كخدمة Linux';

  @override
  String get e7SettingsUi66 =>
      'أبقِ OpenCode قيد التشغيل على حاسوبك بعد إغلاق الطرفية؛ انسخ أوامر الإعداد والحالة وإعادة التشغيل والسجلات والتحديث';

  @override
  String get e7SettingsUi67 => 'تحديثات الخادم';

  @override
  String get e7SettingsUi68 => 'رقِّ من الجهاز الذي يشغّل الخادم';

  @override
  String get e7SettingsUi69 => 'فاتح أو داكن';

  @override
  String get e7SettingsUi70 => 'السمة';

  @override
  String get e7SettingsUi74 => 'الإجراءات المسموح بها دائمًا';

  @override
  String get e7SettingsUi76 => 'على هذا الجهاز';

  @override
  String get e7SettingsUi77 => 'مساحة التخزين المستخدمة';

  @override
  String get e7SettingsUi78 => 'حذف الطلبات في قائمة الانتظار';

  @override
  String get e7SettingsUi79 => 'لا يوجد شيء ينتظر الإرسال';

  @override
  String get e7SettingsUi80 => 'حذف الطلبات في قائمة الانتظار؟';

  @override
  String get e7SettingsUi81 => 'تم حذف الطلبات في قائمة الانتظار';

  @override
  String get e7SettingsUi82 =>
      'تعذّر حذف الطلبات في قائمة الانتظار. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get e7SettingsUi83 => 'حذف المسودات';

  @override
  String get e7SettingsUi84 => 'لا يوجد نص محفوظ في محرّر الرسائل';

  @override
  String get e7SettingsUi85 => 'حذف المسودات؟';

  @override
  String get e7SettingsUi86 => 'تم حذف المسودات';

  @override
  String get e7SettingsUi87 =>
      'تعذّر حذف المسودات. تحقّق من مساحة تخزين الجهاز وحاول مجددًا.';

  @override
  String get e7SettingsUi88 => 'تشخيص التطبيق';

  @override
  String get e7SettingsUi92 => 'الخصوصية واستخدام البيانات';

  @override
  String get e7SettingsUi93 =>
      'الخوادم والمزوّدون والصوت والملفات وTermux والتحديثات';

  @override
  String get e7SettingsUi94 => 'تراخيص الصوت ومصادره';

  @override
  String get e7SettingsUi95 =>
      'نماذج Whisper وsherpa-onnx وONNX Runtime وrecord';

  @override
  String get e7SettingsUi96 => 'حول التطبيق وإشعارات المصادر المفتوحة';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return 'قطع الاتصال بـ $server؟';
  }

  @override
  String e7SettingsHealthError(String error) {
    return 'حالة الخادم غير متاحة — $error';
  }

  @override
  String e7SettingsHealthVersion(String version) {
    return 'الخادم يعمل جيدًا · $version';
  }

  @override
  String e7SettingsVersion(String version) {
    return 'الإصدار $version';
  }

  @override
  String get e7AppearancePackOpencode => 'أخضر الطرفية، السمة الافتراضية';

  @override
  String get e7AppearancePackCatppuccin =>
      'Mocha وLatte بدرجات البنفسجي الفاتح';

  @override
  String get e7AppearancePackGruvbox => 'مظهر كلاسيكي دافئ بدرجات البرتقالي';

  @override
  String get e7AppearancePackSolarized =>
      'لوحة الألوان الثنائية الكلاسيكية بدرجات الأزرق';

  @override
  String get e7AppearancePackDynamic => 'ألوان Material You لهذا الهاتف';

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
      other: '$queued طلب في قائمة الانتظار',
      many: '$queued طلبًا في قائمة الانتظار',
      few: '$queued طلبات في قائمة الانتظار',
      two: 'طلبان في قائمة الانتظار',
      one: 'طلب واحد في قائمة الانتظار',
      zero: 'لا طلبات في قائمة الانتظار',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts مسودة',
      many: '$drafts مسودة',
      few: '$drafts مسودات',
      two: 'مسودتان',
      one: 'مسودة واحدة',
      zero: 'لا مسودات',
    );
    return '$total من العمل غير المرسل — $_temp0 ($queueBytes) و$_temp1 ($draftBytes). تُحذف الطلبات في قائمة الانتظار بعد $days يومًا.';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    return 'يحذف كل الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    return 'يحذف هذا الإجراء الطلبات غير المرسلة وعددها $count ومرفقاتها، لكل الخوادم. لن تُرسل أبدًا. لا يتأثر أي شيء على الخادم.';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    return 'يحذف نص محرّر الرسائل المحفوظ في $count محادثة';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    return 'يحذف هذا الإجراء نص محرّر الرسائل المحفوظ في $count محادثة. لا يتأثر أي شيء على الخادم.';
  }

  @override
  String e7SettingsRestartBody(String version, String current) {
    return 'تم تثبيت OpenCode $version، لكن عملية الخادم هذه ما زالت تشغّل $current. أعد تشغيل العملية على الجهاز المضيف؛ سيعيد التطبيق الاتصال ويتحقق من الإصدار الجاري تشغيله.';
  }

  @override
  String e7SettingsInstallVersion(String version) {
    return 'تثبيت $version';
  }

  @override
  String e7SettingsInstalledVersion(String version) {
    return 'تم تثبيت OpenCode $version. أعد تشغيل عملية الخادم لاستخدامه.';
  }

  @override
  String e7SettingsRestartVersion(String version) {
    return 'أعد تشغيل OpenCode لاستخدام $version';
  }

  @override
  String e7SettingsRetryError(String error) {
    return '$error اضغط للمحاولة مجددًا.';
  }

  @override
  String e7SettingsUpdateVersion(String version) {
    return 'تحديث OpenCode إلى $version';
  }

  @override
  String get e7SettingsDetailUi17 => 'الخصوصية';

  @override
  String get e7SettingsDetailUi18 => 'المصادر المفتوحة';

  @override
  String get e7SettingsDetailUi19 => 'حول هذا الإصدار';

  @override
  String get e7SettingsDetailUi22 =>
      'تطبيق محمول للاتصال بخادم OpenCode. يعمل التعرّف على الصوت محليًا بعد تنزيل النماذج الاختيارية.';

  @override
  String get e7SettingsDetailUi23 => 'تطبيق حاسوب للاتصال بخادم OpenCode.';

  @override
  String get e7SettingsDetailUi25 => 'الأدوات';

  @override
  String get e7SettingsDetailUi26 => 'المهارات';

  @override
  String get e7SettingsDetailUi27 => 'المراجع';

  @override
  String get e7SettingsAlphaBody =>
      'بُني هذا التطبيق المستقل بمساعدة كبيرة من الذكاء الاصطناعي. أندرويد هو المنصة الأساسية المدعومة. إصدارات الحاسوب تجريبية ولم تُختبر على أجهزة فعلية. أبلغ عن الأعطال للمساعدة في تحسين التطبيق.';

  @override
  String get e7SettingsNonAffiliation =>
      'OpenCode Mobile مشروع مجتمعي مستقل. لم يُنشئه فريق OpenCode الرسمي ولا يتولّى صيانته أو يؤيّده، وليس مرتبطًا به.';

  @override
  String e7SettingsDiagnosticOccurrences(int count) {
    return 'عدد مرات الحدوث: $count';
  }

  @override
  String get e7ProjectProjectsReconnect =>
      'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا بعد قليل.';

  @override
  String e7ProjectProjectRenameFailed(String error) {
    return 'تعذّر تغيير اسم المشروع: $error';
  }

  @override
  String get e7ProjectProjectDefaultDirectory => 'المجلد الافتراضي للخادم';

  @override
  String get e7ProjectProjectSwitchUnavailableDetail =>
      'يستخدم هذا الخادم المجلد المحدد للمحادثات. ابدأ محادثة جديدة من «العمل» للمتابعة.';

  @override
  String get e7ProjectProjectsTitle => 'المشاريع';

  @override
  String get e7ProjectProjectsRefresh => 'تحديث المشاريع';

  @override
  String get e7ProjectProjectsSearch => 'البحث عن مشروع أو مسار';

  @override
  String get e7ProjectProjectsOpened => 'المشاريع المفتوحة';

  @override
  String get e7ProjectProjectsEmpty => 'لا توجد مشاريع مفتوحة';

  @override
  String get e7ProjectProjectsEmptyDetail =>
      'تظهر هنا المشاريع المفتوحة على هذا الخادم. اختر مشروعًا للمحادثات والملفات والطرفيات وأدوات البرمجة. أنشئ مجلدًا أو افتحه بمساره من الخيارات أعلاه، أو افتح مشروعًا على خادم OpenCode هذا ثم حدّث القائمة.';

  @override
  String get e7ProjectProjectsRefreshFailed => 'تعذّر تحديث المشاريع';

  @override
  String e7ProjectProjectWorktrees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count شجرة عمل',
      many: '$count شجرة عمل',
      few: '$count أشجار عمل',
      two: 'شجرتا عمل',
      one: 'شجرة عمل واحدة',
      zero: 'لا توجد أشجار عمل',
    );
    return '$_temp0';
  }

  @override
  String e7ProjectProjectRenameAction(String name) {
    return 'تغيير اسم $name';
  }

  @override
  String get e7ProjectProjectRenameTitle => 'تغيير اسم المشروع';

  @override
  String get e7ProjectProjectNameLabel => 'اسم المشروع';

  @override
  String get e7ProjectProjectNameHint =>
      'امسح الاسم لاستخدام اسم مجلد المشروع.';

  @override
  String get e7ProjectProjectSave => 'حفظ';

  @override
  String get e7ProjectMonitorUnsupported =>
      'متابعة الطلبات في الخلفية غير متاحة لهذا الخادم. افتح المحادثة لمراجعة الطلبات الحالية.';

  @override
  String get readerUiDisconnected => 'الخادم غير متصل.';

  @override
  String get readerUiIndicatorsUnavailable =>
      'مؤشرات تغييرات الملفات غير متاحة على هذا الخادم.';

  @override
  String get readerUiIndicatorsFailed => 'تعذّر تحديث مؤشرات تغييرات الملفات.';

  @override
  String get readerUiReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get readerUiCommentAdded =>
      'أُضيف تعليق المراجعة. عُد إلى المحادثة للمتابعة.';

  @override
  String get readerUiCommentCopied => 'نُسخ تعليق المراجعة. ألصقه في محادثة.';

  @override
  String get readerUiSearchSymbols => 'البحث عن الرموز';

  @override
  String get readerUiSearchFiles => 'البحث عن الملفات';

  @override
  String get readerUiSelectFile => 'اختر ملفًا لمعاينته';

  @override
  String get readerUiEmptyFolder => 'المجلد فارغ';

  @override
  String get readerUiNoFiles => 'لم يتم العثور على ملفات';

  @override
  String get readerUiPullRefresh => 'اسحب للأسفل لتحديث هذا المجلد.';

  @override
  String get readerUiTryFileName => 'جرّب اسم ملف آخر.';

  @override
  String get readerUiAttachPrompt => 'إرفاق بالطلب';

  @override
  String get readerUiAddReference => 'إضافة كمرجع';

  @override
  String get readerUiOpenReview => 'فتح في المراجعة';

  @override
  String get readerUiCopyPath => 'نسخ المسار';

  @override
  String get readerUiWorkspaceSymbols => 'البحث عن رموز المشروع';

  @override
  String get readerUiSymbolsHint =>
      'ابحث عن الأصناف والدوال ودوال الأعضاء والمتغيرات بالاسم.';

  @override
  String get readerUiNoSymbols => 'لم يتم العثور على رموز';

  @override
  String get readerUiSymbolsUnavailable =>
      'جرّب اسمًا آخر. بعض خدمات اللغات لا تدعم البحث عن الرموز في المشروع بأكمله.';

  @override
  String get readerUiFiles => 'الملفات';

  @override
  String get readerUiSymbols => 'الرموز';

  @override
  String get readerUiChanges => 'التغييرات';

  @override
  String get readerUiRefreshChanges => 'تحديث التغييرات';

  @override
  String get readerUiEntireChange => 'تغيير الملف بالكامل';

  @override
  String get readerUiWorkingTree => 'غير مثبّت';

  @override
  String get readerUiSessionScopeHint => 'الملفات التي غيّرتها هذه المحادثة.';

  @override
  String get readerUiWorkingScopeHint =>
      'كل ما لم يُثبَّت بعد، أيًا كان من غيّره.';

  @override
  String get readerUiBranchScopeHint =>
      'كل ما في هذا الفرع، مقارنةً بالفرع الرئيسي.';

  @override
  String get readerUiSaveDevice => 'حفظ على الجهاز';

  @override
  String get readerUiPreviewUnavailable => 'المعاينة غير متاحة';

  @override
  String get readerUiRendered => 'معاينة';

  @override
  String get readerUiRaw => 'المصدر';

  @override
  String get readerUiSaveFailed => 'تعذّر حفظ تفضيلات القراءة. حاول مجددًا.';

  @override
  String get readerUiSourceFirst => 'ملفات المصدر أولًا';

  @override
  String get readerUiServerOrder => 'الترتيب الافتراضي';

  @override
  String readerUiLine(int number) {
    return 'السطر $number';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return 'أُضيف $label إلى الطلب';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label موجود بالفعل في الطلب';
  }

  @override
  String readerUiReferenceFull(int count) {
    return 'عدد المراجع الموجودة في الطلب: $count';
  }

  @override
  String readerUiAttached(String name) {
    return 'تم إرفاق $name.';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف متغير',
      many: '$count ملفًا متغيرًا',
      few: '$count ملفات متغيرة',
      two: 'ملفان متغيران',
      one: 'ملف واحد متغير',
      zero: 'لا توجد ملفات متغيرة',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return 'تم إرفاق $name. عُد إلى المحادثة لإضافة تعليقك.';
  }

  @override
  String readerUiSaveNamed(String name) {
    return 'حفظ $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return 'تم حفظ $name على جهازك.';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · السطر $line';
  }

  @override
  String readerUiOnPrompt(int count) {
    return '$count في الطلب';
  }

  @override
  String readerUiNewLines(String label) {
    return 'بعد التغيير: $label';
  }

  @override
  String readerUiOldLines(String label) {
    return 'قبل التغيير: $label';
  }

  @override
  String readerUiLineRange(int first, int last) {
    return 'الأسطر $first–$last';
  }

  @override
  String readerUiReviewPrompt(String path) {
    return 'راجع `$path`';
  }

  @override
  String readerUiViewedCount(int viewed, int files) {
    return 'تمت معاينة $viewed من $files';
  }

  @override
  String readerUiSaved(String name) {
    return 'تم حفظ $name.';
  }

  @override
  String get readerUiSymbolFile => 'ملف';

  @override
  String get readerUiSymbolModule => 'وحدة';

  @override
  String get readerUiSymbolNamespace => 'نطاق أسماء';

  @override
  String get readerUiSymbolPackage => 'حزمة';

  @override
  String get readerUiSymbolClass => 'صنف';

  @override
  String get readerUiSymbolMethod => 'دالة عضو';

  @override
  String get readerUiSymbolProperty => 'خاصية';

  @override
  String get readerUiSymbolField => 'حقل';

  @override
  String get readerUiSymbolConstructor => 'دالة إنشاء';

  @override
  String get readerUiSymbolEnum => 'تعداد';

  @override
  String get readerUiSymbolInterface => 'واجهة';

  @override
  String get readerUiSymbolFunction => 'دالة';

  @override
  String get readerUiSymbolVariable => 'متغير';

  @override
  String get readerUiSymbolConstant => 'ثابت';

  @override
  String get readerUiSymbolEnummember => 'عنصر تعداد';

  @override
  String get readerUiSymbolStruct => 'بنية';

  @override
  String get readerUiSymbolEvent => 'حدث';

  @override
  String get readerUiSymbolOperator => 'عامل';

  @override
  String get readerUiSymbolTypeparameter => 'معامل نوع';

  @override
  String get readerUiSymbolSymbol => 'رمز';

  @override
  String get readerUiAdded => 'مضاف';

  @override
  String get readerUiDeleted => 'محذوف';

  @override
  String get readerUiModified => 'معدّل';

  @override
  String get readerUiChanged => 'متغير';

  @override
  String get readerUiSession => 'هذه المحادثة';

  @override
  String get readerUiBranch => 'الفرع كله';

  @override
  String get readerUiAttachmentMissing =>
      'محتوى المرفق غير موجود في هذه الرسالة.';

  @override
  String get readerUiRemoteAttachment => 'معاينة المرفقات البعيدة غير متاحة.';

  @override
  String get readerUiAttachmentInvalid => 'تعذّر قراءة بيانات المرفق.';

  @override
  String get e7WorkspaceDisconnected => 'الخادم غير متصل.';

  @override
  String get e7WorkspaceNoFolder => 'لم يُحدَّد مجلد للمشروع';

  @override
  String get e7WorkspaceNoProjects => 'لا توجد مشاريع مفتوحة';

  @override
  String get e7WorkspaceServerNoProjects => 'لم يُرجع الخادم أي مشاريع.';

  @override
  String get e7WorkspaceChooseProject => 'اختر مشروعًا';

  @override
  String get e7WorkspaceNeedsYou => 'بانتظارك';

  @override
  String get e7WorkspaceNoRecent => 'لا توجد محادثات حديثة';

  @override
  String get e7WorkspaceChooseFolderToStart => 'اختر مجلد مشروع لبدء محادثة.';

  @override
  String get e7WorkspaceStartInWorkspace => 'ابدأ محادثة في المشروع المحدد.';

  @override
  String get e7WorkspaceNoProjectSelected => 'لم يُحدَّد مشروع';

  @override
  String get e7WorkspaceSwitchProject => 'تبديل المشروع';

  @override
  String get e7WorkspaceThisComputer => 'هذا الكمبيوتر';

  @override
  String get e7WorkspaceNoShareLink => 'لم يُرجع الخادم رابطًا للمشاركة.';

  @override
  String get e7WorkspaceShareCopied => 'نُسخ رابط المشاركة';

  @override
  String get e7WorkspaceReconnectingShortly =>
      'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا بعد قليل.';

  @override
  String get e7WorkspaceRenameSession => 'إعادة تسمية المحادثة';

  @override
  String get e7WorkspaceTitle => 'العنوان';

  @override
  String get e7WorkspaceShareConfirm => 'مشاركة هذه المحادثة؟';

  @override
  String get e7WorkspaceDeleteConfirm => 'حذف المحادثة؟';

  @override
  String get e7WorkspaceArchive => 'أرشفة';

  @override
  String get e7WorkspaceShareSession => 'مشاركة المحادثة';

  @override
  String get e7WorkspaceCompacting => 'جارٍ اختصار السياق…';

  @override
  String get e7WorkspaceStopSharing => 'إيقاف المشاركة';

  @override
  String get e7WorkspaceDisconnect => 'قطع الاتصال';

  @override
  String get e7WorkspaceBackExit => 'اضغط رجوع مرة أخرى للخروج';

  @override
  String get e7WorkspaceConnected => 'متصل';

  @override
  String get e7WorkspaceConnecting => 'جارٍ الاتصال';

  @override
  String get e7WorkspaceOffline => 'غير متصل';

  @override
  String get e7WorkspaceReconnectingAgain =>
      'جارٍ إعادة الاتصال بـ OpenCode. حاول مجددًا.';

  @override
  String get e7WorkspaceRefreshFailed => 'تعذّر التحديث';

  @override
  String get e7WorkspacePermissionRequired => 'يلزم إذن';

  @override
  String get e7WorkspaceAssistantQuestion => 'سؤال من المساعد';

  @override
  String get e7WorkspaceInputRequested => 'مطلوب إدخال';

  @override
  String get e7WorkspaceMcpAsked => 'طلب من خادم MCP';

  @override
  String get e7WorkspaceDismissRequest => 'تجاهل هذا الطلب؟';

  @override
  String get e7WorkspaceDismissDetail =>
      'سيتابع OpenCode دون إجابات عن هذه الأسئلة.';

  @override
  String get e7WorkspaceNeedsInput => 'يحتاج OpenCode إلى إدخال';

  @override
  String get e7WorkspaceSendAnswers => 'إرسال الإجابات';

  @override
  String get e7WorkspaceReferenceRetry =>
      'مرجع المحادثة غير متاح. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationRetry =>
      'مشروع المحادثة غير متاح. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationChangedReturn =>
      'تغيّر مشروع المحادثة. ارجع وحاول مجددًا.';

  @override
  String get e7WorkspaceLocationChangedRetry =>
      'تغيّر مشروع المحادثة. حدّث وحاول مجددًا.';

  @override
  String get e7WorkspaceReferenceUnavailable => 'مرجع المحادثة غير متاح.';

  @override
  String get e7WorkspacePaginationStuck =>
      'تعذّر تحميل الصفحة التالية من المحادثات. حدّث القائمة للمتابعة.';

  @override
  String e7WorkspaceCreateFailed(String error) {
    return 'تعذّر إنشاء محادثة: $error';
  }

  @override
  String get e7WorkspaceNoProjectsSearch =>
      'لم يُرجع الخادم أي مشاريع. ابحث في جميع المحادثات للعثور على أعمالك السابقة.';

  @override
  String e7WorkspaceActiveDirectory(String directory) {
    return 'محادثة تعمل في هذا المجلد · $directory';
  }

  @override
  String e7WorkspaceOpenProjectCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مشروع مفتوح على هذا الخادم',
      many: '$count مشروعًا مفتوحًا على هذا الخادم',
      few: '$count مشاريع مفتوحة على هذا الخادم',
      two: 'مشروعان مفتوحان على هذا الخادم',
      one: 'مشروع واحد مفتوح على هذا الخادم',
      zero: 'لا توجد مشاريع مفتوحة على هذا الخادم',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceArchivedToast(String title) {
    return 'أُرشفت «$title»';
  }

  @override
  String e7WorkspaceDeleteDetail(String title) {
    return 'ستُحذف «$title» وسجلّها نهائيًا.';
  }

  @override
  String e7WorkspaceShareDetail(String title) {
    return 'يمكن لأي شخص لديه الرابط عرض «$title»، بما في ذلك المحادثة والسياق المشترك. لا تشارك أسرارًا أو بيانات اعتماد أو ملفات خاصة.';
  }

  @override
  String e7WorkspaceRequestFor(String title) {
    return 'لـ $title';
  }

  @override
  String e7WorkspaceQuestionCount(int count, String title) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سؤال · $title',
      many: '$count سؤالًا · $title',
      few: '$count أسئلة · $title',
      two: 'سؤالان · $title',
      one: 'سؤال واحد · $title',
      zero: 'لا توجد أسئلة · $title',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count وكيل فرعي',
      many: '$count وكيلًا فرعيًا',
      few: '$count وكلاء فرعيين',
      two: 'وكيلان فرعيان',
      one: 'وكيل فرعي واحد',
      zero: 'لا يوجد وكلاء فرعيون',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceSessionId(String id) {
    return 'المحادثة $id';
  }

  @override
  String get e7WorkspaceUnknownProject => 'مشروع غير معروف';

  @override
  String get e7WorkspaceJustNow => 'الآن';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return 'قبل $count د';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return 'قبل $count س';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return 'قبل $count ي';
  }

  @override
  String e7WorkspaceFileCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف',
      many: '$count ملفًا',
      few: '$count ملفات',
      two: 'ملفان',
      one: 'ملف واحد',
      zero: 'لا توجد ملفات',
    );
    return '$_temp0';
  }

  @override
  String get chatUiAllMatchingRequests => '(كل الطلبات المطابقة)';

  @override
  String get chatUiNoOutput => '(لا توجد مخرجات)';

  @override
  String get chatUiNoResult => '(لا توجد نتيجة)';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen =>
      'يُضاف مرجع واحد كنص عند الإرسال. لا يُحفظ مع مسودتك.';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      'إضافة مرجع لمشروع OpenCode إلى هذا الطلب';

  @override
  String get chatUiAddAnImageOrFileToThe => 'إضافة صورة أو ملف إلى الطلب';

  @override
  String get chatUiAgent => 'الوكيل';

  @override
  String get chatUiAllowOnce => 'السماح مرة واحدة';

  @override
  String get chatUiAlreadyAnsweredElsewhere => 'أُجيب عنه في مكان آخر بالفعل';

  @override
  String get chatUiAlreadyDelivered => 'سُلّم بالفعل';

  @override
  String get chatUiAlwaysAllow => 'السماح دائمًا';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover => 'سيشمل السماح الدائم أيضًا';

  @override
  String get chatUiAnswerWasCutOffByTheLength => 'قُطع الرد بسبب حد الطول';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      'يمكن لأي شخص لديه الرابط الاطّلاع على هذه المحادثة وسياقها المشترك. لا تشارك محادثات تتضمّن أسرارًا أو بيانات اعتماد أو ملفات خاصة.';

  @override
  String get chatUiAppDiagnostics => 'تشخيصات التطبيق';

  @override
  String get chatUiAppearance => 'المظهر';

  @override
  String get chatUiApplyPatch => 'تطبيق رقعة';

  @override
  String get chatUiAskOpenCode => 'اسأل OpenCode…';

  @override
  String get chatUiAttachFile => 'إرفاق ملف';

  @override
  String get chatUiAttachToPrompt => 'إرفاق بالطلب';

  @override
  String get chatUiAttachment => 'مرفق';

  @override
  String get chatUiAttachmentLimitReached => 'بلغت الحد الأقصى للمرفقات';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 =>
      'يجب ألا يتجاوز إجمالي حجم المرفقات 20 MB.';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes =>
      'متاح عند انتهاء التشغيل الحالي';

  @override
  String get chatUiBrowseProjectAndGlobalSkills =>
      'تصفّح مهارات المشروع والمهارات العامة';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      'تصفّح ملفات المشروع ومعاينتها وتنزيلها وإرفاقها';

  @override
  String get chatUiCancelAndReturnToTheComposer =>
      'الإلغاء والعودة إلى محرّر الرسالة';

  @override
  String get chatUiCancelMessage => 'إلغاء الرسالة';

  @override
  String get chatUiCancelThisPendingMessage =>
      'هل تريد إلغاء هذه الرسالة المنتظرة؟';

  @override
  String get chatUiChangeTheActiveOpenCodeConsoleOrganization =>
      'تغيير المؤسسة النشطة في OpenCode Console';

  @override
  String get chatUiChangeTheTitleShownInTheSession =>
      'تغيير العنوان الظاهر في قائمة المحادثات';

  @override
  String get chatUiChangeThisSessionSExperimentalWorkspace =>
      'تغيير البيئة السحابية التجريبية لهذه المحادثة';

  @override
  String get chatUiChangedFile => 'ملف متغيّر';

  @override
  String get chatUiChanges => 'التغييرات';

  @override
  String get chatUiChooseAServerModelByProviderAnd =>
      'اختيار نموذج على الخادم بحسب مزوّد الخدمة والقدرات';

  @override
  String get chatUiChooseAnotherModelInThePickerTo =>
      'اختر نموذجًا آخر من القائمة لإضافته إلى نماذجك الأخيرة.';

  @override
  String get chatUiChooseModel => 'اختيار نموذج';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent => 'اختيار وكيل OpenCode النشط';

  @override
  String get chatUiChooseTheCurrentModelVariantOrReasoning =>
      'اختيار متغيّر النموذج الحالي أو مستوى الاستدلال';

  @override
  String get chatUiCollapseReasoning => 'طيّ الاستدلال';

  @override
  String get chatUiCommandMap => 'دليل الأوامر';

  @override
  String get chatUiCompactContext => 'اختصار السياق';

  @override
  String get chatUiCompactSession => 'اختصار المحادثة';

  @override
  String get chatUiCompactingConversation => 'جارٍ اختصار المحادثة…';

  @override
  String get chatUiCompactionFailed => 'فشل اختصار السياق';

  @override
  String get chatUiCompactionStarted => 'بدأ اختصار السياق';

  @override
  String get chatUiCompose => 'كتابة رسالة';

  @override
  String get chatUiConnectProvider => 'ربط مزوّد خدمة';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      'حالة الاتصال وإصدار الخادم والوضع المباشر';

  @override
  String get chatUiContextAdded => 'أُضيف السياق';

  @override
  String get chatUiContextCompacted => 'لُخّصت الرسائل السابقة لتوفير المساحة';

  @override
  String get chatUiCopiedPasteItIntoTheComposer =>
      'نُسخ النص. الصقه في محرّر الرسالة';

  @override
  String get chatUiCopyMessageText => 'نسخ نص الرسالة';

  @override
  String get chatUiCopyShareLink => 'نسخ رابط المشاركة';

  @override
  String get chatUiCopyTheRenderedConversationAsMarkdown =>
      'نسخ المحادثة المعروضة بصيغة Markdown';

  @override
  String get chatUiCopyTranscript => 'نسخ سجل المحادثة';

  @override
  String get chatUiCreateOrCopyAPublicSessionLink =>
      'إنشاء رابط عام للمحادثة أو نسخه';

  @override
  String get chatUiCurrentSession => 'المحادثة الحالية';

  @override
  String get chatUiDelegate => 'تفويض';

  @override
  String get chatUiDelegateThisPrompt => 'تفويض هذا الطلب';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent =>
      'تفويض هذا الطلب إلى وكيل فرعي على الخادم';

  @override
  String get chatUiDelegatedSession => 'محادثة مفوّضة';

  @override
  String get chatUiDeleteMessage => 'حذف الرسالة';

  @override
  String get chatUiDeleteThisMessage => 'هل تريد حذف هذه الرسالة؟';

  @override
  String get chatUiDetails => 'التفاصيل';

  @override
  String get chatUiDirectory => 'المجلد';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink =>
      'تعطيل الرابط العام الحالي للمحادثة';

  @override
  String get chatUiDiscardDraft => 'تجاهل المسودة';

  @override
  String get chatUiDiscardPromptChanges => 'هل تريد تجاهل تغييرات الطلب؟';

  @override
  String get chatUiDiscardQueuedDraft =>
      'هل تريد تجاهل المسودة في قائمة الانتظار؟';

  @override
  String get chatUiDismissPromptError => 'إخفاء خطأ الطلب';

  @override
  String get chatUiEachAttachmentMustBe10MBOr =>
      'يجب ألا يتجاوز حجم كل مرفق 10 MB.';

  @override
  String get chatUiEdit => 'تحرير';

  @override
  String get chatUiEditDraft => 'تحرير المسودة';

  @override
  String get chatUiEditTheCurrentPromptInAFocused =>
      'تحرير الطلب الحالي في عرض مخصّص بملء الشاشة';

  @override
  String get chatUiErrorDetails => 'تفاصيل الخطأ';

  @override
  String get chatUiExpandReasoning => 'توسيع الاستدلال';

  @override
  String get chatUiExportSessionTranscript => 'تصدير سجل المحادثة';

  @override
  String get chatUiExportTranscript => 'تصدير سجل المحادثة';

  @override
  String get chatUiFetchPage => 'جلب صفحة';

  @override
  String get chatUiFiles => 'الملفات';

  @override
  String get chatUiFilesAreUnavailableInThisPreview =>
      'الملفات غير متاحة في هذه المعاينة.';

  @override
  String get chatUiFindACommandOrAction => 'البحث عن أمر أو إجراء';

  @override
  String get chatUiFindAMessageJumpToItOr =>
      'البحث عن رسالة أو الانتقال إليها أو إنشاء فرع من طلب';

  @override
  String get chatUiFindASubagent => 'البحث عن وكيل فرعي';

  @override
  String get chatUiFindFiles => 'البحث عن ملفات';

  @override
  String get chatUiFindSessionsAcrossEveryOpenCodeProject =>
      'البحث عن محادثات في كل مشاريع OpenCode';

  @override
  String get chatUiFollowAndroidOrChooseTheNativeLight =>
      'اتباع Android أو اختيار المظهر الفاتح أو الداكن للنظام';

  @override
  String get chatUiForkFromThisPrompt => 'إنشاء فرع من هذا الطلب';

  @override
  String get chatUiForkSession => 'إنشاء فرع من المحادثة';

  @override
  String get chatUiGeneratedFile => 'ملف مُنشأ';

  @override
  String get chatUiHideTimestamps => 'إخفاء الطوابع الزمنية';

  @override
  String get chatUiImageDataIsUnavailable => 'بيانات الصورة غير متاحة.';

  @override
  String get chatUiInputRequested => 'مطلوب إدخال';

  @override
  String get chatUiInspectGitLanguageServicesAndFormattersFor =>
      'فحص Git وخدمات اللغة وأدوات التنسيق لهذا المشروع';

  @override
  String get chatUiInspectMCPStatusAuthenticationAndResources =>
      'فحص حالة MCP والمصادقة والموارد';

  @override
  String get chatUiInspectCurrentTokensCacheCostAndContext =>
      'فحص الرموز الحالية وذاكرة التخزين المؤقت والتكلفة واستخدام السياق';

  @override
  String get chatUiInspectToolsCallableByTheActiveProvider =>
      'فحص الأدوات التي يمكن لمزوّد الخدمة والنموذج النشطين استدعاؤها';

  @override
  String get chatUiItsTextReturnsToTheComposerAs =>
      'يعود نصها إلى محرّر الرسالة كمسودة.';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      'انتقل إلى أي موضع. يستعيد إنشاء الفرع طلبًا لتحريره.';

  @override
  String get chatUiKeepItPending => 'إبقاؤها منتظرة';

  @override
  String get chatUiKeepItQueued => 'إبقاؤها في قائمة الانتظار';

  @override
  String get chatUiLanguageServer => 'خادم اللغة';

  @override
  String get chatUiList => 'عرض قائمة';

  @override
  String get chatUiLoadingSubagents => 'جارٍ تحميل الوكلاء الفرعيين…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript =>
      'تفاصيل الاستدلال الطويلة مطوية في سجل المحادثة';

  @override
  String get chatUiMCPServers => 'خوادم MCP';

  @override
  String get chatUiManageProviderAndIntegrationAuthentication =>
      'إدارة مصادقة مزوّدي الخدمة والتكاملات';

  @override
  String get chatUiMessage => 'الرسالة';

  @override
  String get chatUiMessageTimeline => 'التسلسل الزمني للرسائل';

  @override
  String get chatUiMessageTimestampsHidden => 'أُخفيت الطوابع الزمنية للرسائل';

  @override
  String get chatUiMessageTimestampsShown => 'أُظهرت الطوابع الزمنية للرسائل';

  @override
  String get chatUiModel => 'النموذج';

  @override
  String get chatUiModelAndAgent => 'النموذج والوكيل';

  @override
  String get chatUiMoveSession => 'نقل المحادثة';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory =>
      'نقل هذه المحادثة إلى مشروع آخر';

  @override
  String get chatUiMoved => 'نُقلت';

  @override
  String get chatUiNavigate => 'التنقّل';

  @override
  String get chatUiNoAnswer => 'لا توجد إجابة';

  @override
  String get chatUiNoMatchingMessages => 'لا توجد رسائل مطابقة';

  @override
  String get chatUiNoShareLinkWasReturned => 'لم يُعد رابط مشاركة';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer =>
      'لا يتوفر وكلاء فرعيون من هذا الخادم';

  @override
  String get chatUiNotConnectedToTheServerRightNow =>
      'غير متصل بالخادم حاليًا.';

  @override
  String get chatUiOpenParentSession => 'فتح المحادثة الأم';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals =>
      'فتح جلسات الطرفية الدائمة للمشروع';

  @override
  String get chatUiOpenProviders => 'فتح مزوّدي الخدمة';

  @override
  String get chatUiOpenSubagentSession => 'فتح محادثة الوكيل الفرعي';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      'أوامر OpenCode غير متاحة دون اتصال.';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt =>
      'تعذّر على OpenCode إتمام هذا الطلب.';

  @override
  String get chatUiOpenCodeIsReconnecting => 'جارٍ إعادة اتصال OpenCode.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا بعد قليل.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا عندما يتصل الخادم.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain =>
      'جارٍ إعادة اتصال OpenCode. حاول مجددًا.';

  @override
  String get chatUiOpenCodeNeedsInput => 'يحتاج OpenCode إلى إدخال';

  @override
  String get chatUiOpenCodeServerCommand => 'أمر خادم OpenCode';

  @override
  String get chatUiOutputPruned => 'حُذفت المخرجات';

  @override
  String get chatUiPendingChange => 'تغيير منتظر';

  @override
  String get chatUiProjectFiles => 'ملفات المشروع';

  @override
  String get chatUiProjectHealth => 'حالة المشروع';

  @override
  String get chatUiProjectReference => 'مرجع مشروع';

  @override
  String get chatUiProjectReferences => 'مراجع المشاريع';

  @override
  String get chatUiProjectsAndWorkspaces => 'المشاريع وأشجار العمل';

  @override
  String get chatUiPromptEditor => 'محرّر الطلب';

  @override
  String get chatUiPromptFromParentAgent => 'طلب من الوكيل الأب';

  @override
  String get chatUiPromptTools => 'أدوات الطلب';

  @override
  String get chatUiQuestion => 'سؤال';

  @override
  String get chatUiQuestions => 'الأسئلة';

  @override
  String get chatUiQueuedRunsAfterThisTurn =>
      'في قائمة الانتظار · يُشغّل بعد هذا التبادل';

  @override
  String get chatUiQueuedWillSendWhenReconnected =>
      'في قائمة الانتظار — سيُرسل عند إعادة الاتصال';

  @override
  String get chatUiRead => 'قراءة';

  @override
  String get chatUiReasoningExpandedInTheTranscript =>
      'تفاصيل الاستدلال موسّعة في سجل المحادثة';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice =>
      'التسجيل وتحويل الصوت إلى نص على هذا الجهاز';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      'احتُفظ بالمرجع لطلبك التالي — لا تتضمّنه الأوامر.';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      'احتُفظ بالمراجع لطلبك التالي — لا تتضمّنها الأوامر.';

  @override
  String get chatUiReject => 'رفض';

  @override
  String get chatUiReloadMessages => 'تحديث الرسائل';

  @override
  String get chatUiRename => 'إعادة تسمية';

  @override
  String get chatUiRenameSession => 'إعادة تسمية المحادثة';

  @override
  String get chatUiRestoreRevertedPrompt => 'استعادة الطلب المتراجَع عنه';

  @override
  String get chatUiRestoreTheCurrentlyRevertedSessionState =>
      'استعادة حالة المحادثة المتراجَع عنها حاليًا';

  @override
  String get chatUiRetryLastPrompt => 'إعادة محاولة الطلب الأخير';

  @override
  String get chatUiRevertLastPrompt => 'التراجع عن الطلب الأخير';

  @override
  String get chatUiReviewCommentAddedToThePrompt =>
      'أُضيف تعليق المراجعة إلى الطلب';

  @override
  String get chatUiReviewHandledAppErrorsAndSendA =>
      'مراجعة أخطاء التطبيق المعالَجة وإرسال تقرير ببيانات حساسة محجوبة';

  @override
  String get chatUiReviewTheActualDiffForThisSession =>
      'مراجعة الفروق الفعلية لهذه المحادثة';

  @override
  String get chatUiRunOnYourComputer => 'التشغيل على حاسوبك';

  @override
  String get chatUiRunShellCommand => 'تشغيل أمر صدفة';

  @override
  String get chatRunShellLabel => 'Command';

  @override
  String get chatRunShellHint => 'npm test';

  @override
  String get chatRunShellHelper =>
      'The agent runs it in this project, and its output joins the conversation.';

  @override
  String get chatRunShellEmpty => 'Type a command to run.';

  @override
  String get chatRenameEmpty => 'Type a title.';

  @override
  String get chatUiSaveTheConversationAsAMarkdownFile =>
      'حفظ المحادثة كملف Markdown';

  @override
  String get chatUiScope => 'النطاق';

  @override
  String get chatUiSearchMessages => 'البحث في الرسائل';

  @override
  String get chatUiSearchMobileActionsAndServerProvidedCommands =>
      'البحث في إجراءات الهاتف والأوامر التي يقدّمها الخادم';

  @override
  String get chatUiSearchText => 'البحث عن نص';

  @override
  String get chatUiSelectAModelBeforeCompactingThisSession =>
      'اختر نموذجًا قبل اختصار هذه المحادثة.';

  @override
  String get chatUiSendNowAndSteerInstead =>
      'الإرسال الآن والتوجيه بدلًا من ذلك';

  @override
  String get chatUiServerCommands => 'أوامر الخادم';

  @override
  String get chatUiServerMessage => 'رسالة من الخادم';

  @override
  String get chatUiServerStatus => 'حالة الخادم';

  @override
  String get chatUiSessionChanges => 'تغييرات المحادثة';

  @override
  String get chatUiSessionContext => 'سياق المحادثة';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      'شُوركت المحادثة. انسخ الرابط الظاهر يدويًا.';

  @override
  String get chatUiShareLinkCopied => 'نُسخ رابط المشاركة';

  @override
  String get chatUiShareSession => 'مشاركة المحادثة';

  @override
  String get chatUiShareThisSession => 'هل تريد مشاركة هذه المحادثة؟';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView =>
      'مشتركة: يمكن لأي شخص لديه الرابط الاطّلاع عليها';

  @override
  String get chatUiShowAllSubagentSessions => 'عرض كل محادثات الوكلاء الفرعيين';

  @override
  String get chatUiShowTimestamps => 'عرض الطوابع الزمنية';

  @override
  String get chatUiSkill => 'مهارة ·';

  @override
  String get chatUiSkills => 'المهارات';

  @override
  String get chatUiSlashCommandsAndAgents =>
      'الأوامر ذات الشرطة المائلة والوكلاء';

  @override
  String get chatUiStartACleanSessionInThisWorkspace =>
      'بدء محادثة جديدة خالية من السياق في هذا المشروع';

  @override
  String get chatUiStopSharing => 'إيقاف المشاركة';

  @override
  String get chatUiSubagent => 'وكيل فرعي';

  @override
  String get chatUiSubagentFailed => 'فشل الوكيل الفرعي.';

  @override
  String get chatUiSubagentWorking => 'الوكيل الفرعي يعمل…';

  @override
  String get chatUiSubagentsCouldNotBeLoaded => 'تعذّر تحميل الوكلاء الفرعيين';

  @override
  String get chatUiSummarizeTheSessionUsingTheSelectedModel =>
      'تلخيص المحادثة باستخدام النموذج المحدّد';

  @override
  String get chatUiSwitchOrganization => 'تبديل المؤسسة';

  @override
  String get chatUiSwitchProjectDirectoryOrWorktree =>
      'تبديل المشروع أو المجلد أو شجرة العمل';

  @override
  String get chatUiSystemUpdate => 'تحديث النظام';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo =>
      'أخبر الوكيل بالسبب أو بما ينبغي فعله بدلًا من ذلك (اختياري)';

  @override
  String get chatUiThatMessageIsNoLongerInThis =>
      'لم تعد تلك الرسالة في هذه المحادثة.';

  @override
  String get chatUiTheFileHasNoContentToAttach =>
      'لا يحتوي الملف على محتوى لإرفاقه.';

  @override
  String get chatUiTheFileHasNoContentToSave =>
      'لا يحتوي الملف على محتوى لحفظه.';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe =>
      'تغيّر النموذج أو المشروع. افتح الطلب الحالي مجددًا.';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom =>
      'الملف المُنشأ غير متاح من هذا الخادم.';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      'ستُحذف الرسالة وكل أجزائها نهائيًا من المحادثة، ولن تكون متاحة للردود اللاحقة. لن يُتراجع عن تغييرات الملفات التي أجرتها.';

  @override
  String get chatUiTheServerReturnedEmptyImageData =>
      'أعاد الخادم بيانات صورة فارغة.';

  @override
  String get chatUiThisDraftHasNotBeenSentTo =>
      'لم تُرسل هذه المسودة إلى OpenCode.';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      'هذه المسودة أكبر من أن تُضاف إلى قائمة الانتظار، أو أن القائمة ممتلئة بمسودات أحدث. أزل مرفقًا أو امسح الطلبات في قائمة الانتظار من الإعدادات.';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn =>
      'لا يمكن استعادة هذا الطلب لأن أحد المرفقات غير متاح.';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn =>
      'لا يمكن إعادة محاولة هذا الطلب لأن أحد المرفقات غير متاح.';

  @override
  String get chatUiTimeline => 'التسلسل الزمني';

  @override
  String get chatUiTimestampsUsage => 'الطوابع الزمنية والاستخدام';

  @override
  String get chatUiTitle => 'العنوان';

  @override
  String get chatUiTodos => 'المهام';

  @override
  String get chatUiToggleCreationTimesBesideTranscriptEntries =>
      'إظهار أوقات الإنشاء أو إخفاؤها بجانب عناصر سجل المحادثة';

  @override
  String get chatUiToggleLongReasoningDetailsAcrossTheTranscript =>
      'إظهار تفاصيل الاستدلال الطويلة أو إخفاؤها في سجل المحادثة';

  @override
  String get chatUiToolFailed => 'فشلت الأداة.';

  @override
  String get chatUiToolsAndCapabilities => 'الأدوات والقدرات';

  @override
  String get chatUiTranscriptCopiedAsMarkdown =>
      'نُسخ سجل المحادثة بصيغة Markdown';

  @override
  String get chatUiTranscriptDisplay => 'عرض سجل المحادثة';

  @override
  String get chatUiTranscriptSaved => 'حُفظ سجل المحادثة';

  @override
  String get chatUiVoiceConversationWasInterrupted =>
      'انقطعت المحادثة الصوتية.';

  @override
  String get chatUiVoiceInput => 'الإدخال الصوتي';

  @override
  String get chatUiVoiceInputIsUnavailable => 'الإدخال الصوتي غير متاح.';

  @override
  String get chatUiWaitForThisRunInstead => 'انتظار هذا التشغيل بدلًا من ذلك';

  @override
  String get chatUiWebSearch => 'البحث في الويب';

  @override
  String get chatUiWrite => 'كتابة';

  @override
  String get chatUiWriteYourOpenCodePrompt => 'اكتب طلبك إلى OpenCode…';

  @override
  String get chatUiYou => 'أنت';

  @override
  String get chatUiYourOriginalComposerDraftAndAttachmentsWill =>
      'ستبقى مسودتك الأصلية في محرّر الرسالة ومرفقاتها دون تغيير.';

  @override
  String get chatUiInThisChat => 'في هذه المحادثة';

  @override
  String get chatUiNewFile => 'ملف جديد';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return 'في قائمة الانتظار، سيُرسَل عند إعادة الاتصال. $detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '‎/$command غير متاح حاليًا.';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return 'يمكنك إرفاق ما يصل إلى $count ملفات.';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'أُرسل $count طلب من قائمة الانتظار',
      many: 'أُرسل $count طلبًا من قائمة الانتظار',
      few: 'أُرسلت $count طلبات من قائمة الانتظار',
      two: 'أُرسل طلبان من قائمة الانتظار',
      one: 'أُرسل طلب واحد من قائمة الانتظار',
      zero: 'لم يُرسَل أي طلب من قائمة الانتظار',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة تنتظر خوادم أخرى',
      many: '$count مسودة تنتظر خوادم أخرى',
      few: '$count مسودات تنتظر خوادم أخرى',
      two: 'مسودتان تنتظران خوادم أخرى',
      one: 'مسودة واحدة تنتظر خوادم أخرى',
      zero: 'لا مسودات تنتظر خوادم أخرى',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return 'ستستخدم الأدوار التالية في هذه المحادثة $model.';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '‎@$name موجود بالفعل في الطلب';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return 'تم إرفاق $filename. أضف تعليقك.';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return 'حفظ $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return 'تم حفظ $filename على جهازك.';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      many: '$count مسودة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      few: '$count مسودات في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      two: 'مسودتان في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      one: 'مسودة واحدة في قائمة الانتظار للإرسال عند إعادة الاتصال.',
      zero: 'لا مسودات في قائمة الانتظار.',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count مسودة تنتظر خوادم أخرى.',
      many: '$count مسودة تنتظر خوادم أخرى.',
      few: '$count مسودات تنتظر خوادم أخرى.',
      two: 'مسودتان تنتظران خوادم أخرى.',
      one: 'مسودة واحدة تنتظر خوادم أخرى.',
      zero: 'لا مسودات تنتظر خوادم أخرى.',
    );
    return '$_temp0';
  }

  @override
  String chatUiQuestionCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سؤال',
      many: '$count سؤالًا',
      few: '$count أسئلة',
      two: 'سؤالان',
      one: 'سؤال واحد',
      zero: 'لا أسئلة',
    );
    return '$_temp0';
  }

  @override
  String chatUiPermissionNeeded(Object title) {
    return 'إذن مطلوب: $title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return 'سؤال: $title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سؤال',
      many: '$count سؤالًا',
      few: '$count أسئلة',
      two: 'سؤالان',
      one: 'سؤال واحد',
      zero: 'لا أسئلة',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return 'تم تجاوز حد المعدل. تجري إعادة المحاولة$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return 'تم تجاوز حد المعدل. إعادة المحاولة$attempt بعد $time';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يُضاف $count مرجع كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      many: 'يُضاف $count مرجعًا كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      few: 'تُضاف $count مراجع كنص عند الإرسال. لا تُحفظ مع مسودتك.',
      two: 'يُضاف مرجعان كنص عند الإرسال. لا يُحفظان مع مسودتك.',
      one: 'يُضاف مرجع واحد كنص عند الإرسال. لا يُحفظ مع مسودتك.',
      zero: 'لا تُضاف مراجع عند الإرسال.',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '$count مرفق';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count رسالة سابقة',
      many: '$count رسالة سابقة',
      few: '$count رسائل سابقة',
      two: 'رسالتان سابقتان',
      one: 'رسالة سابقة واحدة',
      zero: 'لا رسائل سابقة',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count رمز';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '$position من $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return 'وكيل فرعي · $count';
  }

  @override
  String chatUiToolsSummary(Object tools) {
    return 'الأدوات: $tools';
  }

  @override
  String chatUiFromLine(Object line) {
    return 'من السطر $line';
  }

  @override
  String chatUiLineCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count سطر',
      many: '$count سطرًا',
      few: '$count أسطر',
      two: 'سطران',
      one: 'سطر واحد',
      zero: 'لا أسطر',
    );
    return '$_temp0';
  }

  @override
  String chatUiLineRange(Object start, Object end) {
    return '‎L$start–$end';
  }

  @override
  String chatUiEntryCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر',
      many: '$count عنصرًا',
      few: '$count عناصر',
      two: 'عنصران',
      one: 'عنصر واحد',
      zero: 'لا عناصر',
    );
    return '$_temp0';
  }

  @override
  String chatUiFoundCount(Object count) {
    return 'عُثر على $count';
  }

  @override
  String chatUiMatchCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تطابق',
      many: '$count تطابقًا',
      few: '$count تطابقات',
      two: 'تطابقان',
      one: 'تطابق واحد',
      zero: 'لا تطابقات',
    );
    return '$_temp0';
  }

  @override
  String chatUiFileCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ملف',
      many: '$count ملفًا',
      few: '$count ملفات',
      two: 'ملفان',
      one: 'ملف واحد',
      zero: 'لا ملفات',
    );
    return '$_temp0';
  }

  @override
  String chatUiProviderSearch(Object provider) {
    return 'بحث $provider';
  }

  @override
  String chatUiResultCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نتيجة',
      many: '$count نتيجة',
      few: '$count نتائج',
      two: 'نتيجتان',
      one: 'نتيجة واحدة',
      zero: 'لا نتائج',
    );
    return '$_temp0';
  }

  @override
  String chatUiCompletedCount(Object done, Object total) {
    return 'اكتمل $done/$total';
  }

  @override
  String chatUiAnsweredCount(Object count) {
    return 'تمت الإجابة عن $count';
  }

  @override
  String chatUiAskedCount(Object count) {
    return 'طُرح $count';
  }

  @override
  String chatUiDurationMinutesSeconds(Object minutes, Object seconds) {
    return '$minutes د $seconds ث';
  }

  @override
  String chatUiDurationSeconds(Object seconds) {
    return '$seconds ث';
  }

  @override
  String chatUiFileLoadFailed(Object error) {
    return 'تعذّر تحميل هذا الملف من خادم OpenCode: $error';
  }

  @override
  String chatUiMoreEntries(Object total) {
    return '$total إجمالًا · المزيد متاح';
  }

  @override
  String chatUiEntryTotal(Object total) {
    return '$total عنصر';
  }

  @override
  String chatUiAnsweredDetail(Object answer) {
    return 'الإجابة: $answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return 'جارٍ تحميل $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return 'معاينة الصورة المُنشأة $filename';
  }

  @override
  String chatUiParentSession(Object title) {
    return 'المحادثة الأصل · $title';
  }

  @override
  String chatUiChooseOption(Object option) {
    return 'اختيار: $option';
  }

  @override
  String get chatUiMainSession => 'المحادثة الرئيسية';

  @override
  String get chatUiTodo => 'المهام';

  @override
  String get chatUiBackgroundResult => 'نتيجة العمل في الخلفية';

  @override
  String get chatUiBackgroundComplete => 'اكتمل';

  @override
  String get chatUiBackgroundError => 'فشل';

  @override
  String get chatUiBackgroundCancelled => 'أُلغي';

  @override
  String get chatUiResultSourceDetails => 'تفاصيل رسالة الخادم';

  @override
  String get chatUiResultOpenChild => 'فتح محادثة الوكيل الفرعي';

  @override
  String get chatUiNoResultText => 'لم يُرجع الخادم أي نص للنتيجة.';

  @override
  String get chatUiTimedOut => 'انتهت المهلة';

  @override
  String get chatUiKilled => 'أُوقف';

  @override
  String get chatUiTruncated => 'مقتطع';

  @override
  String get chatUiUpdated => 'محدَّث';

  @override
  String get chatUiError => 'خطأ';

  @override
  String get chatUiAssistant => 'المساعد';

  @override
  String get chatUiUser => 'المستخدم';

  @override
  String get chatUiOpenCodeSession => 'محادثة OpenCode';

  @override
  String get chatUiTool => 'أداة';

  @override
  String get chatUiFile => 'ملف';

  @override
  String get e7LibraryReportABug => 'الإبلاغ عن مشكلة';

  @override
  String get e7LibraryKeyboardShortcuts => 'اختصارات لوحة المفاتيح';

  @override
  String get e7LibraryHTTPHeader => 'ترويسة HTTP';

  @override
  String get e7LibraryEnvironmentVariable => 'متغير بيئة';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      'يعيد OpenCode الاتصال. حاول مجددًا بعد قليل.';

  @override
  String get e7LibraryWhatIsMCP => 'ما هو MCP؟';

  @override
  String get e7LibrarySavingConfiguration => 'جارٍ حفظ الإعدادات';

  @override
  String get e7LibrarySaveMCPServer => 'حفظ خادم MCP';

  @override
  String get e7LibraryThisProject => 'هذا المشروع';

  @override
  String e7LibraryWritesOnlyTo(String detail1) {
    return 'يكتب في $detail1 فقط.';
  }

  @override
  String get e7LibraryWritesToThisOpenCodeServerSGlobal =>
      'يكتب في الإعدادات العامة لخادم OpenCode هذا.';

  @override
  String get e7LibraryServerName => 'اسم الخادم';

  @override
  String get e7LibraryDocsOrBrowserTools => 'مثل docs أو browser-tools';

  @override
  String get e7LibraryUniqueWithinTheSelectedConfiguration =>
      'اسم فريد ضمن الإعدادات المحددة.';

  @override
  String get e7LibraryEnterAServerName => 'أدخل اسمًا للخادم';

  @override
  String get e7LibraryRemoteURL => 'رابط بعيد';

  @override
  String get e7LibraryLocalCommand => 'أمر محلي';

  @override
  String get e7LibraryOptional => 'اختياري';

  @override
  String get e7LibraryEnterAValueGreaterThanZero => 'أدخل قيمة أكبر من صفر';

  @override
  String get e7LibraryMCPEndpointURL => 'رابط نقطة اتصال MCP';

  @override
  String get e7LibraryHTTPIsAcceptedForLocalDevelopmentServers =>
      'يمكن استخدام HTTP مع خوادم التطوير المحلية.';

  @override
  String get e7LibraryEnterAValidHTTPOrHTTPSURL =>
      'أدخل رابط HTTP أو HTTPS صالحًا لا يحتوي على بيانات اعتماد';

  @override
  String get e7LibraryDetectOAuthAutomatically => 'اكتشاف OAuth تلقائيًا';

  @override
  String get e7LibraryTurnThisOffWhenTheServerUses =>
      'أوقف هذا الخيار إذا كان الخادم يستخدم الترويسات ولا ينبغي له بدء OAuth مطلقًا.';

  @override
  String get e7LibraryCommandAndArguments => 'الأمر والوسائط';

  @override
  String get e7LibraryRunsOnTheOpenCodeServerNotThis =>
      'يُنفَّذ على خادم OpenCode وليس على هذا الهاتف. أدخل وسيطة واحدة في كل سطر.';

  @override
  String get e7LibraryEnterACommand => 'أدخل أمرًا';

  @override
  String get e7LibraryWorkingDirectory => 'مجلد العمل';

  @override
  String get e7LibraryOptionalServerPath => 'مسار اختياري على الخادم';

  @override
  String get e7LibraryEnvironmentVariables => 'متغيرات البيئة';

  @override
  String e7LibraryInvalidOnLineUseKEYVALUE(String detail1, String detail2) {
    return 'قيمة $detail1 غير صالحة في السطر $detail2. استخدم KEY=VALUE.';
  }

  @override
  String e7LibraryInvalidNameOnLine(String detail1, String detail2) {
    return 'اسم $detail1 غير صالح في السطر $detail2.';
  }

  @override
  String e7LibraryDuplicateName(String detail1, String detail2) {
    return 'اسم $detail1 مكرر: «$detail2».';
  }

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgain =>
      'يعيد OpenCode الاتصال. حاول مجددًا.';

  @override
  String get e7LibraryRevokeAlwaysAllowedAction => 'إلغاء الإذن؟';

  @override
  String get e7LibraryAction => 'الإجراء';

  @override
  String get e7LibraryResource => 'المورد';

  @override
  String get e7LibraryRevokeAccess => 'إلغاء الإذن';

  @override
  String get e7LibraryAlwaysAllowedActionRevoked =>
      'أُلغي السماح الدائم للإجراء';

  @override
  String get e7LibraryAlwaysAllowedActions => 'الإجراءات المسموح بها دائمًا';

  @override
  String get e7LibraryNoAlwaysAllowedActions =>
      'لا توجد إجراءات مسموح بها دائمًا';

  @override
  String get e7LibraryTheLastActionFailed => 'فشل الإجراء الأخير';

  @override
  String e7LibraryRevokeAccess2(String detail1) {
    return 'إلغاء الإذن لإجراء $detail1';
  }

  @override
  String get e7LibraryToolsAndCapabilities => 'الأدوات والإمكانات';

  @override
  String get e7LibraryRefreshTools => 'تحديث الأدوات';

  @override
  String get e7LibraryOpenCodeToolsDependOnTheProviderAnd =>
      'تعتمد أدوات OpenCode على مزوّد الخدمة والنموذج المستخدمَين في المحادثة النشطة.';

  @override
  String get e7LibraryChooseModel => 'اختيار النموذج';

  @override
  String e7LibrarySearchTools2(String detail1) {
    return 'البحث في الأدوات ($detail1)';
  }

  @override
  String get e7LibraryRegisteredInventoryUnavailable =>
      'قائمة الأدوات المسجّلة غير متاحة';

  @override
  String get e7LibraryServerCapabilityUnavailable => 'إمكانات الخادم غير متاحة';

  @override
  String get e7LibraryNoToolsForThisModel => 'لا توجد أدوات لهذا النموذج';

  @override
  String get e7LibraryNoDescriptionReturnedByOpenCode =>
      'لم يُرجع OpenCode وصفًا';

  @override
  String get e7LibraryCopyParameterSchema => 'نسخ مخطط المعلمات';

  @override
  String get e7LibraryNoProjectSelected => 'لم يُحدَّد مشروع';

  @override
  String get e7LibraryNoProjectFolderIsOpenChooseOne =>
      'لا يوجد مجلد مشروع مفتوح. اختر مجلدًا من «العمل».';

  @override
  String get e7LibrarySwitchProject => 'تبديل المشروع';

  @override
  String get e7LibraryWorktrees => 'أشجار العمل';

  @override
  String get e7LibraryManagedWorkspaces => 'البيئات السحابية';

  @override
  String get e7LibraryProjectHealth => 'حالة المشروع';

  @override
  String get e7LibraryOpenCodeIsReconnecting => 'يعيد OpenCode الاتصال.';

  @override
  String get e7LibraryCloudEnvironments => 'البيئات السحابية';

  @override
  String get e7LibraryDiscoverExistingEnvironments => 'اكتشاف البيئات الموجودة';

  @override
  String get e7LibraryNewEnvironment => 'بيئة جديدة';

  @override
  String get e7LibraryEnvironments => 'البيئات';

  @override
  String get e7LibraryNoCloudEnvironments => 'لا توجد بيئات سحابية';

  @override
  String get e7LibraryEnvironmentRefreshFailed => 'فشل تحديث البيئات';

  @override
  String get e7LibraryRetryCloudEnvironments => 'إعادة المحاولة';

  @override
  String get e7LibraryAdapterRefreshFailed => 'فشل تحديث المهايئات';

  @override
  String get e7LibraryConnected => 'متصل';

  @override
  String get e7LibraryConnecting => 'جارٍ الاتصال';

  @override
  String get e7LibraryDisconnected => 'غير متصل';

  @override
  String get e7LibraryEnvironmentActions => 'إجراءات البيئة';

  @override
  String get e7LibraryOpenAgain => 'فتح مجددًا';

  @override
  String get e7LibraryNewManagedWorkspace => 'بيئة سحابية جديدة';

  @override
  String get e7LibraryCreateAndOpen => 'إنشاء وفتح';

  @override
  String e7LibraryRemove(String detail1) {
    return 'حذف $detail1؟';
  }

  @override
  String get e7LibraryRemovePermanently => 'حذف نهائي';

  @override
  String e7LibraryIsReady(String detail1) {
    return 'أصبح $detail1 جاهزًا';
  }

  @override
  String get e7LibraryOpenCodeCouldNotPrepareThisWorktree =>
      'تعذّر على OpenCode تجهيز شجرة العمل هذه.';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return 'أُنشئ $detail1. لم تُؤكّد حالة إعداده بعد.';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis =>
      'انتظر حتى ينتهي OpenCode من تجهيز شجرة العمل هذه.';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations =>
      'لم يغيّر OpenCode المشروع.';

  @override
  String e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
    String detail1,
    String detail2,
  ) {
    return 'تعذّر التحقق من $detail1 قبل هذا الإجراء الذي يحذف البيانات: $detail2';
  }

  @override
  String e7LibraryResetToTheDefaultBranch(String detail1) {
    return 'أُعيد $detail1 إلى الفرع الافتراضي';
  }

  @override
  String e7LibraryAndItsBranchWereRemoved(String detail1) {
    return 'حُذف $detail1 وفرعه';
  }

  @override
  String e7LibraryReset(String detail1) {
    return 'إعادة ضبط $detail1؟';
  }

  @override
  String get e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes =>
      'سيؤدي هذا إلى تجاهل التغييرات المتتبَّعة نهائيًا وحذف جميع الملفات غير المتتبَّعة والمتجاهَلة. تُعاد الوحدات الفرعية أيضًا إلى حالتها الأصلية وتُنظَّف. لا يمكن التراجع عن هذا الإجراء.';

  @override
  String get e7LibraryResetWorktree => 'إعادة ضبط شجرة العمل';

  @override
  String get e7LibraryRefreshWorktrees => 'تحديث أشجار العمل';

  @override
  String get e7LibraryNewWorktree => 'شجرة عمل جديدة';

  @override
  String get e7LibraryNoIsolatedWorktreesYet => 'لا توجد أشجار عمل معزولة بعد';

  @override
  String get e7LibraryPreparingFilesAndProjectTasks =>
      'جارٍ تجهيز الملفات ومهام المشروع…';

  @override
  String get e7LibraryWorktreeActions => 'إجراءات شجرة العمل';

  @override
  String get e7LibraryReset2 => 'إعادة ضبط';

  @override
  String get e7LibraryNameOptional => 'الاسم (اختياري)';

  @override
  String get e7LibraryTheWorktreeDirectoryAndItsGitBranch =>
      'سيُحذف مجلد شجرة العمل وفرع Git الخاص به نهائيًا. تبقى المحادثات السابقة في السجل، لكن مجلد عملها لن يعود موجودًا.';

  @override
  String get e7LibraryInitializeGitRepository => 'تهيئة مستودع Git؟';

  @override
  String get e7LibraryOpenCodeWillRunGitInitInThe =>
      'سينفّذ OpenCode الأمر git init في المشروع الحالي. لن تتغير الملفات الموجودة ولن تُثبَّت في Git. يتيح هذا استخدام الفروع وتغييرات شجرة العمل وميزات المراجعة.';

  @override
  String get e7LibraryInitializeGit => 'تهيئة Git';

  @override
  String get e7LibraryGitRepositoryInitialized => 'تمت تهيئة مستودع Git';

  @override
  String get e7LibraryVersionControl => 'التحكم بالإصدارات';

  @override
  String get e7LibraryLanguageServices => 'خدمات اللغات';

  @override
  String get e7LibraryFormatters => 'أدوات التنسيق';

  @override
  String get e7LibraryGitIsNotInitialized => 'لم تتم تهيئة Git';

  @override
  String get e7LibraryInitializeThisProjectToEnableBranchesWorking =>
      'هيّئ هذا المشروع لاستخدام الفروع وتغييرات شجرة العمل والمراجعة.';

  @override
  String get e7LibraryRunGitInitFromATerminal => 'نفّذ `git init` من الطرفية';

  @override
  String get e7LibraryGitInitializationFailed => 'فشلت تهيئة Git';

  @override
  String get e7LibraryNoActiveBranch => 'لا يوجد فرع نشط';

  @override
  String e7LibraryDefaultBranch(String detail1) {
    return 'الفرع الافتراضي: $detail1';
  }

  @override
  String get e7LibraryWorkingTreeIsClean => 'شجرة العمل خالية من التغييرات';

  @override
  String e7LibraryChangedFiles(String detail1) {
    return 'الملفات المتغيرة: $detail1';
  }

  @override
  String get e7LibraryNoUncommittedChanges => 'لا توجد تغييرات غير مثبَّتة';

  @override
  String get e7LibraryNoActiveLanguageServices => 'لا توجد خدمات لغات نشطة';

  @override
  String get e7LibraryOpenCodeActivatesThemWhileItInspectsSupported =>
      'يفعّلها OpenCode أثناء فحص ملفات المصدر المدعومة خلال البرمجة.';

  @override
  String get e7LibraryNoFormattersConfigured => 'لا توجد أدوات تنسيق مضبوطة';

  @override
  String get e7LibraryEnabled => 'مفعّل';

  @override
  String get e7LibraryDisabled => 'معطّل';

  @override
  String e7LibraryLoading(String detail1) {
    return 'جارٍ تحميل $detail1';
  }

  @override
  String get e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe =>
      'التقاط رابط العودة تلقائيًا غير متاح. ألصق رابط العودة أو رمز التفويض.';

  @override
  String get e7LibraryThePhoneIsSecurelyListeningForThis =>
      'ينتظر الهاتف رابط العودة لهذا التفويض بأمان. يمكنك إدخاله يدويًا أيضًا.';

  @override
  String get e7LibraryCallbackURLOrCode => 'رابط العودة أو الرمز';

  @override
  String get e7LibraryUpdating => 'جارٍ التحديث…';

  @override
  String get e7LibraryNotConnected => 'غير متصل';

  @override
  String get e7LibraryServerEnvironment => 'بيئة الخادم';

  @override
  String get e7LibraryServerManaged => 'يديره الخادم';

  @override
  String get e7LibraryConnect => 'اتصال';

  @override
  String get e7LibraryAuthenticationFailed => 'فشلت المصادقة';

  @override
  String get e7LibraryAuthenticationAttemptExpired =>
      'انتهت صلاحية محاولة المصادقة';

  @override
  String get e7LibraryAuthenticationComplete => 'اكتملت المصادقة';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe =>
      'عُد من المتصفح وأدخل رمز التفويض.';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      'أكمل المصادقة في المتصفح، ثم تحقّق من حالتها.';

  @override
  String get e7LibraryAuthorizationCode => 'رمز التفويض';

  @override
  String get e7LibrarySelectAnOption => 'اختر خيارًا';

  @override
  String get e7LibraryEnterAValue => 'أدخل قيمة';

  @override
  String get e7LibraryTheServerReturnedAnUnsafeAuthorizationLink =>
      'أرجع الخادم رابط تفويض غير آمن. يُسمح فقط بروابط HTTPS ذات مضيف صالح والخالية من بيانات اعتماد مضمّنة.';

  @override
  String get e7LibraryCouldNotLoadThisSection => 'تعذّر تحميل هذا القسم';

  @override
  String get e7LibrarySkills => 'المهارات';

  @override
  String get e7LibraryNoSkillsAvailable => 'لا توجد مهارات متاحة';

  @override
  String get e7LibraryModelsAndAgents => 'النماذج والوكلاء';

  @override
  String get e7LibraryUnavailable => 'غير متاح';

  @override
  String get e7LibraryFinishOrCancelTheCurrentMCPAuthorization =>
      'أكمل تفويض MCP الحالي أو ألغِه أولًا.';

  @override
  String get e7LibraryCouldNotOpenTheAuthorizationPage =>
      'تعذّر فتح صفحة التفويض';

  @override
  String e7LibraryAuthenticated(String detail1) {
    return 'تمت مصادقة $detail1';
  }

  @override
  String get e7LibraryCouldNotConfirmMCPAuthentication =>
      'تعذّر تأكيد مصادقة MCP';

  @override
  String get e7LibraryMCPServerSavedInOpenCode => 'حُفظ خادم MCP في OpenCode';

  @override
  String get e7LibraryMCPUnavailable => 'MCP غير متاح';

  @override
  String get e7LibraryMCPAndIntegrations => 'MCP وعمليات التكامل';

  @override
  String get e7LibraryCouldNotSaveSignInRecovery =>
      'تعذّر حفظ معلومات استعادة تسجيل الدخول.';

  @override
  String get e7LibraryNoProviderConnectionsAvailable =>
      'لا توجد اتصالات متاحة بمزوّدي الخدمة';

  @override
  String get e7LibraryThisServerDidNotReturnAnyProvider =>
      'لم يُرجع هذا الخادم أي تكاملات مع مزوّدي الخدمة.';

  @override
  String get e7LibrarySearchProvidersOrModels =>
      'البحث في مزوّدي الخدمة أو النماذج';

  @override
  String get e7LibraryNoMCPServersConfigured => 'لا توجد خوادم MCP مضبوطة';

  @override
  String get e7LibrarySaveOneForThisProjectOrEvery =>
      'احفظ خادمًا لهذا المشروع أو لجميع المشاريع على الخادم.';

  @override
  String get e7LibraryAddAnMCPServer => 'إضافة خادم MCP';

  @override
  String get e7LibraryAuthorizing => 'جارٍ التفويض';

  @override
  String get e7LibraryResources => 'الموارد';

  @override
  String get e7LibraryNoResourcesAvailable => 'لا توجد موارد متاحة';

  @override
  String get e7LibraryConnectedMCPServersHaveNotExposedAny =>
      'لم تُتِح خوادم MCP المتصلة أي موارد.';

  @override
  String get e7LibraryOpenBrowser => 'فتح المتصفح';

  @override
  String get e7LibraryConnectedAndToolsAreAvailable => 'متصل والأدوات متاحة';

  @override
  String get e7LibraryConnectionFailed => 'فشل الاتصال';

  @override
  String get e7LibraryAuthenticationRequired => 'المصادقة مطلوبة';

  @override
  String get e7LibraryClientRegistrationRequired => 'تسجيل العميل مطلوب';

  @override
  String e7LibraryStoredCredential(String detail1) {
    return 'بيانات اعتماد محفوظة: $detail1';
  }

  @override
  String get e7LibraryNoConnectionMethodsAvailable => 'لا توجد طرق اتصال متاحة';

  @override
  String get e7LibraryConfiguredOnTheServer => 'مُعدّ على الخادم';

  @override
  String e7LibraryDisconnect2(String detail1) {
    return 'قطع اتصال $detail1؟';
  }

  @override
  String e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
    String detail1,
  ) {
    return 'حُذفت بيانات اعتماد $detail1؛ وتبقى بيئة الخادم نشطة';
  }

  @override
  String e7LibraryDisconnected2(String detail1) {
    return 'قُطع اتصال $detail1';
  }

  @override
  String e7LibraryConnect2(String detail1) {
    return 'توصيل $detail1';
  }

  @override
  String get e7LibraryAuthorizationWasNotOpenedThePendingAttempt =>
      'لم تُفتح صفحة التفويض. تبقى المحاولة المعلّقة محفوظة.';

  @override
  String get e7LibraryCouldNotOpenOAuth => 'تعذّر فتح OAuth';

  @override
  String e7LibraryIsConnected(String detail1) {
    return '$detail1 متصل';
  }

  @override
  String get e7LibraryTheSignInSourceChanged => 'تغيّر مصدر تسجيل الدخول.';

  @override
  String get e7LibraryCouldNotConfirmAuthenticationReturnToThe =>
      'تعذّر تأكيد المصادقة. عُد إلى المصدر الأصلي وحاول مجددًا.';

  @override
  String get e7LibraryNoServerCommandsFound => 'لم يُعثر على أوامر للخادم';

  @override
  String get e7LibraryCommandsFromYourProjectAndSkillsAppear =>
      'تظهر هنا الأوامر من مشروعك ومهاراتك.';

  @override
  String get e7LibraryNoDescription => 'لا يوجد وصف';

  @override
  String get e7LibraryServerCommands => 'أوامر الخادم';

  @override
  String get e7LibraryReferences => 'المراجع';

  @override
  String get e7LibraryNoReferencesConfigured => 'لا توجد مراجع مضبوطة';

  @override
  String e7LibraryChangedFilesDetected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'اكتُشف $count ملف متغيّر.',
      many: 'اكتُشف $count ملفًا متغيّرًا.',
      few: 'اكتُشفت $count ملفات متغيرة.',
      two: 'اكتُشف ملفان متغيّران.',
      one: 'اكتُشف ملف متغيّر واحد.',
      zero: 'لم تُكتشف ملفات متغيرة.',
    );
    return '$_temp0';
  }

  @override
  String get e7LibraryEnvironmentRemainsAfterDisconnect =>
      'يستخدم مزوّد الخدمة هذا أيضًا بيئة الخادم، التي لا يستطيع تطبيق الهاتف إزالتها وستبقى نشطة.';

  @override
  String get e7LibrarySearchImportAliases =>
      'backup restore transfer JSON conversation session chat جلسة نسخ احتياطي استعادة نقل محادثة';

  @override
  String get e7LibrarySearchShortcutsAliases =>
      'hotkeys help desktop اختصارات مفاتيح مساعدة حاسوب';

  @override
  String get e7SetupApiKeyHint => 'لصق مفتاح API';

  @override
  String get e7SetupBrowserHint =>
      'يفتح المتصفح. إذا تعذّرت إعادة التوجيه إلى OpenCode، الصق عنوان URL للعودة هنا.';

  @override
  String get e7SetupDeviceCodeHint =>
      'يستخدم رمزًا لمرة واحدة. يعمل من الهاتف.';

  @override
  String get e7SetupAccountHint => 'تسجيل الدخول بحسابك';

  @override
  String get e7SetupNewTerminalDetail => 'ابدأ جلسة طرفية في المشروع الحالي.';

  @override
  String get e7SetupShowPassword => 'إظهار كلمة مرور الخادم';

  @override
  String get e7SetupAuthFailed => 'بدأ تشغيل الخادم، لكن المصادقة فشلت.';

  @override
  String get e7SetupScanInstruction =>
      'وجّه الكاميرا نحو رمز QR الذي يعرضه الأمر opencode2 pair.';

  @override
  String get e7SetupRightKey => 'مفتاح السهم لليمين';

  @override
  String get e7SetupRestartReconnectFailed =>
      'أُعيد تشغيل الخادم المحلي، لكن تعذّر على التطبيق الاتصال مجددًا.';

  @override
  String get e7SetupInstallingOpenCode => 'جارٍ تثبيت OpenCode';

  @override
  String get e7SetupNoOutput => 'لا توجد مخرجات في الطرفية بعد.';

  @override
  String get e7SetupFollowLog => 'متابعة سجل الخادم';

  @override
  String get e7SetupOpenSetupGuide => 'فتح دليل الإعداد';

  @override
  String get e7SetupDownKey => 'مفتاح السهم لأسفل';

  @override
  String get e7SetupVerifyContinue => 'التحقق والمتابعة';

  @override
  String get e7SetupAccessibleTerminal => 'استخدام سجل وإدخال ميسّرين';

  @override
  String get e7SetupCameraFailedDetail =>
      'قد يستخدم تطبيق آخر الكاميرا. يمكنك لصق رمز الاقتران في جميع الأحوال.';

  @override
  String get e7SetupTermuxNoAnswer =>
      'لم يستجب Termux. افتحه، ونفّذ أمر السماح بالتحكم، ثم تحقّق مجددًا.';

  @override
  String get e7SetupCopyOpenTermux => 'النسخ وفتح Termux';

  @override
  String get e7SetupStopTerminalDetail =>
      'ستتوقف العملية الجارية والعمليات التابعة لها.';

  @override
  String get e7SetupEdit => 'تعديل';

  @override
  String get e7SetupServerPassword => 'كلمة مرور الخادم';

  @override
  String get e7SetupHttpsHint =>
      '‏https://‏ للأجهزة الأخرى، و‏http://‏ على هذا الجهاز أو شبكة خاصة فقط.';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      'الخادم المحلي جاهز، لكن تعذّر حفظ الإصدار الذي رصده التطبيق. حدّث الإعداد للمحاولة مجددًا.';

  @override
  String get e7SetupPasteInstead => 'لصق الرمز بدلًا من مسحه';

  @override
  String get e7SetupConfirmUpdate => 'تحديث OpenCode الذي يديره التطبيق؟';

  @override
  String get e7SetupInstallServiceDetail =>
      'أداة التثبيت الرسمية مع خدمة مستخدم systemd تستمر بعد إغلاق الطرفية وإعادة التشغيل.';

  @override
  String get e7SetupUpdateHost => 'تحديث OpenCode على الكمبيوتر المضيف';

  @override
  String get e7SetupServiceStatus => 'حالة الخدمة';

  @override
  String get e7SetupKeepAfterLogout => 'متابعة التشغيل بعد تسجيل الخروج';

  @override
  String get e7SetupGetTermux => 'الحصول على Termux';

  @override
  String get e7SetupRestartServer => 'إعادة تشغيل الخادم';

  @override
  String get e7SetupResumeSetup =>
      'المحاولة مجددًا — يستأنف الإعداد من حيث توقف';

  @override
  String get e7SetupHidePassword => 'إخفاء كلمة مرور الخادم';

  @override
  String get e7SetupControlKeys =>
      'مفاتيح التحكم في الطرفية. اسحب أفقيًا لعرض المزيد.';

  @override
  String get e7SetupEndInputKey => 'نهاية الإدخال، Control D';

  @override
  String get e7SetupGuidanceSaveFailed =>
      'تعذّر حفظ إرشادات الاتصال. حاول الحفظ مجددًا.';

  @override
  String get e7SetupServerOperation => 'جارٍ تنفيذ إجراء على الخادم';

  @override
  String get e7SetupNewTerminal => 'طرفية جديدة';

  @override
  String get e7SetupUnsavedProfile => 'لم يُحفظ الخادم.';

  @override
  String get e7SetupCheckingInstall => 'جارٍ التحقق من التثبيت الحالي…';

  @override
  String get e7SetupInspectTermuxFailed =>
      'تعذّر على Android التحقق من Termux.';

  @override
  String get e7SetupUsername => 'اسم المستخدم (اختياري)';

  @override
  String get e7SetupFirstSetupDuration =>
      'قد يستغرق الإعداد الأول من 10 إلى 15 دقيقة. يمكنك مغادرة هذه الشاشة والعودة إليها؛ سيستمر الإعداد.';

  @override
  String get e7SetupNoTerminals => 'لا توجد عمليات في الطرفية';

  @override
  String get e7SetupEscapeKey => 'مفتاح Escape';

  @override
  String get e7SetupLeftKey => 'مفتاح السهم لليسار';

  @override
  String get e7SetupInstallService => 'تثبيت OpenCode كخدمة في الخلفية';

  @override
  String get e7SetupSaveToFinish => 'تم الاتصال — احفظ لإكمال الإعداد.';

  @override
  String get e7SetupPasswordStartupHint =>
      'تظهر عند تشغيل الخادم. اتركها فارغة إن لم تكن له كلمة مرور.';

  @override
  String get e7SetupInstallTermuxDetail =>
      'ثبّت الإصدار الحالي من Termux عبر F-Droid، ثم عُد إلى هنا.';

  @override
  String get e7SetupStopBeforeUpdate =>
      'أوقف التوليد الجاري قبل تحديث OpenCode.';

  @override
  String get e7SetupRestartingLocal => 'جارٍ إعادة تشغيل الخادم المحلي';

  @override
  String get e7SetupAddServer => 'إضافة خادم';

  @override
  String get e7SetupInteractiveTerminal => 'استخدام الطرفية التفاعلية';

  @override
  String get e7SetupInstallingUbuntu => 'جارٍ إعداد Ubuntu';

  @override
  String get e7SetupInterruptKey => 'مقاطعة، Control C';

  @override
  String get e7SetupServerUrl => 'عنوان URL للخادم';

  @override
  String get e7SetupUsbAccess => 'الاتصال من هذا الهاتف عبر USB';

  @override
  String get e7SetupWaitingTermux =>
      'في انتظار استجابة Termux. قد يستغرق ذلك بعض الوقت.';

  @override
  String get e7SetupDiscardChanges => 'تجاهل تعديلات الخادم؟';

  @override
  String get e7SetupPasswordRequired => 'يلزم إدخال كلمة المرور مجددًا';

  @override
  String get e7SetupPairing => 'جارٍ الاقتران…';

  @override
  String get e7SetupCommandInput => 'إدخال أمر للطرفية';

  @override
  String get e7SetupCameraFailed => 'تعذّر فتح الكاميرا';

  @override
  String get e7SetupPastePassword => 'لصق كلمة مرور الخادم';

  @override
  String get e7SetupSavingLocal => 'جارٍ حفظ إعدادات الخادم المحلي';

  @override
  String get e7SetupCopyFailureReport => 'نسخ تقرير الخطأ';

  @override
  String get e7SetupCameraDisabled => 'الوصول إلى الكاميرا معطّل';

  @override
  String get e7SetupPastePairing => 'لصق رمز الاقتران';

  @override
  String get e7SetupServers => 'الخوادم';

  @override
  String get e7SetupStartingSetup => 'جارٍ بدء الإعداد في Termux';

  @override
  String get e7SetupSetupNotStarted =>
      'فُتح Termux لكن الإعداد لم يبدأ. حاول مجددًا؛ وإذا تكررت المشكلة، انسخ تقرير الخطأ.';

  @override
  String get e7SetupThisDevice => 'هذا الجهاز (Termux)';

  @override
  String get e7SetupTransportReconnecting => 'جارٍ إعادة الاتصال بالخادم.';

  @override
  String get e7SetupStepUnavailable => 'غير متاح بعد';

  @override
  String get e7SetupUpdateHostDetail =>
      'عندما يعلن الخادم عن تحديث، تتيح الإعدادات تحديثه مباشرةً. هذا الأمر ينفّذ التحديث نفسه على الكمبيوتر المضيف.';

  @override
  String get e7SetupMissingPasswordShort =>
      'كلمة المرور المحفوظة غير متاحة. أدخلها مجددًا، أو اتركها فارغة فقط إذا لم يعد الخادم يتطلبها.';

  @override
  String get e7SetupTesting => 'جارٍ الاختبار…';

  @override
  String get e7SetupScanPairing => 'مسح رمز الاقتران';

  @override
  String get e7SetupRestartUnconfirmed =>
      'تعذّر تأكيد إعادة التشغيل. حدّث حالة التقدّم قبل المحاولة مجددًا.';

  @override
  String get e7SetupExistingMissingCredential =>
      'يوجد خادم محلي، لكن بيانات دخوله المحفوظة غير متاحة. أعد الإعداد لاستبدالها بأمان.';

  @override
  String get e7SetupPreparingModels => 'جارٍ تجهيز النماذج';

  @override
  String get e7SetupHostInstructions =>
      'تُشغّل هذه الأوامر على الكمبيوتر الذي يستضيف الخادم؛ لا يستطيع التطبيق تنفيذها نيابةً عنك. انسخ كل أمر إلى الطرفية على ذلك الكمبيوتر.';

  @override
  String get e7SetupTranscript => 'سجل الطرفية';

  @override
  String get e7SetupHostFirstSetup => 'الإعداد لأول مرة — نفّذ على الكمبيوتر';

  @override
  String get e7SetupNoCameraDetail =>
      'شغّل الأمر opencode2 pair على الخادم، ثم انسخ الرمز الذي يعرضه والصقه في محرر الخادم.';

  @override
  String get e7SetupDiscard => 'تجاهل';

  @override
  String get e7SetupConnectionClosed => 'أُغلق الاتصال';

  @override
  String get e7SetupUpKey => 'مفتاح السهم لأعلى';

  @override
  String get e7SetupV1Limited =>
      'صُمّم هذا التطبيق لـ OpenCode 2؛ بعض الميزات غير متاحة على خوادم الإصدار الأول.';

  @override
  String get e7SetupConnect => 'اتصال';

  @override
  String get e7SetupRemoveTerminalDetail => 'ستتم إزالة سجل هذه الطرفية.';

  @override
  String get e7SetupInputDisconnected =>
      'الإدخال غير متاح أثناء انقطاع الاتصال.';

  @override
  String get e7SetupHostCopied =>
      'تم النسخ. شغّل الأمر على الكمبيوتر الذي يستضيف الخادم.';

  @override
  String get e7SetupCameraPrivacy =>
      'تُستخدم الكاميرا فقط لقراءة رمز QR الذي يعرضه الأمر opencode2 pair، أثناء فتح هذه الشاشة. يمكنك لصق الرمز بدلًا من مسحه للحصول على النتيجة نفسها.';

  @override
  String get e7SetupIsV2 => 'هذا خادم OpenCode 2.';

  @override
  String get e7SetupResumeLive => 'استئناف المتابعة المباشرة';

  @override
  String get e7SetupTabKey => 'مفتاح Tab';

  @override
  String get e7SetupCloseScanner => 'إغلاق الماسح';

  @override
  String get e7SetupChooseContinue => 'اختر طريقة المتابعة';

  @override
  String get e7SetupTermuxOutdated =>
      'إصدار Termux هذا قديم ولا يستطيع التطبيق التحكم فيه. ثبّت الإصدار الحالي من F-Droid أو GitHub، ثم تحقّق مجددًا.';

  @override
  String get e7SetupCameraNeeded => 'يلزم السماح بالكاميرا لمسح الرمز';

  @override
  String get e7SetupTerminalActions => 'إجراءات الطرفية';

  @override
  String get e7SetupReadPassword => 'عرض كلمة مرور الخادم لهذا التطبيق';

  @override
  String get e7SetupStartInstalled => 'تشغيل OpenCode المثبّت؟';

  @override
  String get e7SetupTestConnection => 'اختبار الاتصال';

  @override
  String get e7SetupCheckingTermux => 'جارٍ التحقق من الاتصال بـ Termux';

  @override
  String get e7SetupCheckingTermuxShort => 'جارٍ التحقق من Termux…';

  @override
  String get e7SetupDefaultServer => 'خادم OpenCode';

  @override
  String get e7SetupSaving => 'جارٍ الحفظ…';

  @override
  String get e7SetupLinuxService => 'التشغيل كخدمة في Linux';

  @override
  String get e7SetupPairingDesktopHint =>
      'تحقّق من تشغيل الخادم وإمكانية وصول هذا الجهاز إلى العنوان الذي عرضه.';

  @override
  String get e7SetupAboutNotices => 'حول التطبيق وإشعارات المصدر المفتوح';

  @override
  String get e7SetupSetupLost => 'تعذّرت متابعة الإعداد الجاري في Termux';

  @override
  String get e7SetupReportCopied => 'تم نسخ تقرير الخطأ.';

  @override
  String get e7SetupAppSettings => 'إعدادات التطبيق';

  @override
  String get e7SetupNoCamera => 'هذا الجهاز لا يحتوي على كاميرا';

  @override
  String get e7SetupServerDisconnected => 'الخادم غير متصل.';

  @override
  String get e7SetupEmptyPasswordHint =>
      'اتركها فارغة فقط إذا لم يعد هذا الخادم يستخدم كلمة مرور.';

  @override
  String get e7SetupAndroidOnly =>
      'الإعداد على هذا الهاتف متاح على Android فقط';

  @override
  String get e7SetupEditServer => 'تعديل الخادم';

  @override
  String get e7SetupReenterPassword => 'إدخال كلمة المرور مجددًا';

  @override
  String get e7SetupMissingCredential =>
      'بيانات الدخول المحفوظة لهذا الخادم غير متاحة. أعد الإعداد لاستبدالها بأمان.';

  @override
  String get e7SetupReconnect => 'إعادة الاتصال';

  @override
  String get e7SetupSwitchNotStarted => 'لم يبدأ تبديل الإصدار.';

  @override
  String get e7SetupNoUbuntu => 'لا يوجد تثبيت Ubuntu يديره التطبيق.';

  @override
  String get e7SetupKeyUnavailable => 'غير متاح عندما تكون الطرفية غير متصلة';

  @override
  String get e7SetupCopyTerminal => 'نسخ النص المحدد أو سجل الطرفية';

  @override
  String get e7SetupCommandHint => 'اكتب أمرًا';

  @override
  String get e7SetupCheckInstallFailed => 'تعذّر التحقق من التثبيت الحالي.';

  @override
  String get e7SetupConnectionFailed => 'فشل الاتصال.';

  @override
  String get e7SetupOpenAppSettings => 'فتح إعدادات التطبيق';

  @override
  String get e7SetupFullWalkthrough => 'الدليل الكامل (يفتح في المتصفح)';

  @override
  String get e7SetupPairingPhoneHint =>
      'لا يمكن لهذا الهاتف الوصول إلى خادم يستمع على 127.0.0.1 فقط دون توجيه الاتصال: استخدم «adb reverse tcp:PORT tcp:PORT» عبر USB أو نفق SSH. وللوصول عبر الشبكة، استخدم HTTPS.';

  @override
  String get e7SetupOutputCopied => 'تم نسخ مخرجات الإعداد.';

  @override
  String get e7SetupMissingPasswordLong =>
      'كلمة المرور المحفوظة غير متاحة. أدخلها مجددًا، أو اتركها فارغة فقط إذا لم يعد هذا الخادم يتطلب كلمة مرور.';

  @override
  String get e7SetupNoServerGuide =>
      'لا يوجد خادم بعد؟ يوضّح دليل الإعداد كيفية تشغيله.';

  @override
  String get e7SetupStartingLocal => 'جارٍ تشغيل الخادم المحلي';

  @override
  String get e7SetupTerminalSemantics =>
      'طرفية تفاعلية. استخدم زر تسهيل الاستخدام لعرض سجل مقروء وحقل إدخال واضح.';

  @override
  String get e7SetupRestartingLocalStage => 'جارٍ إعادة تشغيل الخادم المحلي';

  @override
  String get e7SetupEmptyPairClipboard =>
      'الحافظة فارغة. شغّل «opencode2 pair» على الخادم وانسخ الرمز الذي يعرضه.';

  @override
  String get e7SetupRestartActiveChanged =>
      'أُعيد تشغيل الخادم المحلي، لكن الخادم النشط تغيّر. أعد الاتصال عندما تكون مستعدًا.';

  @override
  String get e7SetupTokenRequired => 'يلزم إدخال رمز الاتصال مجددًا';

  @override
  String get e7SetupPairingInstructions =>
      'شغّل «opencode2 pair» على حاسوبك، ثم الصق الرمز الذي يعرضه أو امسحه بالكاميرا.';

  @override
  String get e7SetupHostDaily => 'الاستخدام اليومي — نفّذ على الكمبيوتر';

  @override
  String get e7SetupStopLocal => 'إيقاف الخادم المحلي';

  @override
  String get e7SetupReadingProgress => 'جارٍ قراءة تقدّم الإعداد';

  @override
  String get e7SetupCameraSettingsDetail =>
      'لن يطلب Android الإذن مجددًا. فعّل الكاميرا من إعدادات التطبيق ثم عُد إلى هنا. يمكنك لصق الرمز الآن دون أي إذن.';

  @override
  String get e7SetupVerifyTermuxFailed => 'تعذّر التحقق من الاتصال بـ Termux.';

  @override
  String get e7SetupUbuntuOnly => 'Ubuntu مثبّت، لكن OpenCode لم يُثبّت بعد.';

  @override
  String get e7SetupRenameTerminal => 'إعادة تسمية الطرفية';

  @override
  String get e7SetupInputUnavailable => 'إدخال الطرفية غير متاح';

  @override
  String get e7SetupUpdateInterruption =>
      'سيتوقف الخادم لفترة قصيرة. أوقف التوليد الجاري أولًا.';

  @override
  String get e7SetupRunningOnPhone => 'يعمل OpenCode على هذا الهاتف.';

  @override
  String get e7SetupLocalStopped =>
      'الخادم المحلي متوقف. ملفاته المثبتة محفوظة.';

  @override
  String get e7SetupDidNotConnect => 'لم يتصل الخادم.';

  @override
  String get e7SetupSendCommand => 'إرسال الأمر إلى الطرفية';

  @override
  String get e7SetupUnsupportedSetup =>
      'يتطلب الإعداد على الجهاز تطبيق Termux على Android. على هذا الكمبيوتر، شغّل `opencode serve` ثم أضفه كخادم.';

  @override
  String get e7SetupSendKey => 'يرسل هذا المفتاح إلى الطرفية';

  @override
  String get e7SetupRemoveTerminal => 'إزالة الطرفية؟';

  @override
  String e7SetupTerminalNumber(int number) {
    return 'الطرفية $number';
  }

  @override
  String e7SetupCopyCommandLabel(String label) {
    return 'نسخ الأمر: $label';
  }

  @override
  String e7SetupConnectFailedDetail(String name, String detail) {
    return 'تعذّر الاتصال بـ $name. $detail تحقّق من عنوان الخادم وبيانات الدخول، ثم حاول مجددًا.';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return 'حُفظ $name، لكن تعذّر الاتصال به. تحقّق من عنوان الخادم وبيانات الدخول، ثم حاول مجددًا. ($detail)';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return 'تعذّر حفظ $name. لم يتغيّر الخادم الحالي. تحقّق من مساحة تخزين الجهاز وحاول مجددًا. ($detail)';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return 'إزالة $name؟';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return 'أُزيل $name، لكن تعذّر إغلاق اتصاله بالكامل. أعد تشغيل التطبيق قبل الاتصال بخادم آخر. ($detail)';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return 'تعذّرت إزالة $name. بقي الخادم المحفوظ والاتصال الحالي. تحقّق من مساحة تخزين الجهاز وحاول مجددًا. ($detail)';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return 'تم الاقتران بـ $host — اختير من بين $count عناوين في الرمز.';
  }

  @override
  String e7SetupPaired(String host) {
    return 'تم الاقتران بـ $host.';
  }

  @override
  String e7SetupStartFailed(String detail) {
    return 'تعذّر حفظ الإعداد المحلي أو بدؤه: $detail';
  }

  @override
  String e7SetupRestartFailed(String detail) {
    return 'تعذّرت إعادة تشغيل الخادم المحلي: $detail';
  }

  @override
  String e7SetupStopFailed(String detail) {
    return 'تعذّر إيقاف الخادم المحلي: $detail';
  }

  @override
  String e7SetupElapsedSeconds(int seconds) {
    return 'انقضت $seconds ث';
  }

  @override
  String e7SetupElapsedMinutes(int minutes, int seconds) {
    return 'انقضت $minutes د و$seconds ث';
  }

  @override
  String e7SetupDeleteDisclosure(int queued, int drafts) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: 'سيُحذف $queued طلب من قائمة الانتظار.',
      many: 'سيُحذف $queued طلبًا من قائمة الانتظار.',
      few: 'ستُحذف $queued طلبات من قائمة الانتظار.',
      two: 'سيُحذف طلبان من قائمة الانتظار.',
      one: 'سيُحذف طلب واحد من قائمة الانتظار.',
      zero: '',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: 'ستُحذف $drafts مسودة غير مرسلة.',
      many: 'ستُحذف $drafts مسودة غير مرسلة.',
      few: 'ستُحذف $drafts مسودات غير مرسلة.',
      two: 'ستُحذف مسودتان غير مرسلتين.',
      one: 'ستُحذف مسودة واحدة غير مرسلة.',
      zero: '',
    );
    return 'يحذف هذا كل ما حفظه الجهاز لهذا الخادم: كلمة المرور، والنموذج والوكيل المحددين، والمشروع المختار، والمحادثات المعروضة في أداة الشاشة الرئيسية.\n\n$_temp0 $_temp1\n\nلن يُحذف أي شيء على الخادم نفسه أو لدى مزوّدي الذكاء الاصطناعي.';
  }

  @override
  String e7SetupPairingFailed(String detail, String hint) {
    return 'لم يستجب أي عنوان في رمز الاقتران:\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String e7SetupProbeV1(String version) {
    return 'OpenCode 1 · $version — ميزات محدودة';
  }

  @override
  String get e7SetupPairNone =>
      'لا يوجد رمز اقتران هنا. شغّل «opencode2 pair» على الخادم وامسح الرمز الذي يعرضه أو انسخه.';

  @override
  String get e7SetupPairLong =>
      'هذا النص أطول من رمز اقتران. انسخ فقط السطر الذي يعرضه «opencode2 pair» أو امسح رمز QR الخاص به.';

  @override
  String get e7SetupPairInvalid =>
      'هذا ليس رمز اقتران. شغّل «opencode2 pair» على الخادم وامسح الرمز الذي يعرضه أو انسخه.';

  @override
  String get e7SetupPairShape =>
      'بنية رمز الاقتران غير صحيحة؛ يجب أن يكون كائن JSON يحتوي على «urls» و«username» و«password».';

  @override
  String get e7SetupPairNoUrls =>
      'رمز الاقتران لا يحتوي على حقل «urls»، لذا لا يوجد عنوان للاتصال به.';

  @override
  String get e7SetupPairUrlsType =>
      'حقل «urls» في رمز الاقتران ليس قائمة عناوين.';

  @override
  String get e7SetupPairTooMany =>
      'يحتوي رمز الاقتران على عناوين أكثر مما يحاول التطبيق الاتصال به. اربط الخادم بواجهة شبكة واحدة وأعد الاقتران.';

  @override
  String get e7SetupPairAddressType => 'يحتوي رمز الاقتران على عنوان ليس نصًا.';

  @override
  String get e7SetupPairAddressLong =>
      'يحتوي رمز الاقتران على عنوان أطول من عنوان URL مقبول للخادم.';

  @override
  String get e7SetupPairAddressMissing =>
      'رمز الاقتران لا يحتوي على عنوان خادم. تحقّق من أن الخادم يستقبل الاتصالات، ثم شغّل «opencode2 pair» مجددًا.';

  @override
  String get e7SetupPairUsernameType =>
      'حقل «username» في رمز الاقتران ليس نصًا.';

  @override
  String get e7SetupPairPasswordMissing =>
      'رمز الاقتران لا يحتوي على حقل «password». ربما لم يُنسخ كاملًا؛ امسح الرمز أو انسخه بالكامل.';

  @override
  String get e7SetupPairPasswordType =>
      'حقل «password» في رمز الاقتران ليس نصًا.';

  @override
  String get e7SetupPairTestFailed =>
      'فشل اختبار الاتصال قبل التحقق من الخادم. جرّب عنوانًا آخر.';

  @override
  String get e7SetupNotOpenCode =>
      'لم يستجب العنوان كخادم OpenCode. تحقّق من العنوان وحاول مجددًا.';

  @override
  String get e7SetupNoServerAnswer =>
      'لم يستجب الخادم. تحقّق من تشغيل opencode serve على ذلك العنوان.';

  @override
  String get e7SetupPairPasswordRejected =>
      'رُفضت كلمة المرور. تحقّق من رمز الاقتران وحاول مجددًا.';

  @override
  String get e7SetupPairAddressUnusable =>
      'يحتوي رمز الاقتران على عنوان خادم غير صالح للاستخدام.';

  @override
  String get e7SetupInvalidAddress => '<عنوان غير صالح>';

  @override
  String get e7SetupEnterUrl => 'أدخل عنوان URL للخادم.';

  @override
  String get e7SetupIncludeScheme =>
      'أضف https://. يعمل http:// العادي فقط على هذا الجهاز أو على عنوان شبكة خاصة.';

  @override
  String get e7SetupCompleteUrl =>
      'أدخل عنوان URL كاملًا للخادم، مثل https://server.example:4096.';

  @override
  String get e7SetupUrlScheme =>
      'يجب أن تستخدم عناوين الخوادم https://، أو http:// للخادم المحلي.';

  @override
  String get e7SetupTermuxUrlScheme =>
      'يجب أن تستخدم عناوين الخوادم https://، أو http:// لـ Termux المحلي.';

  @override
  String get e7SetupUrlCredentials =>
      'لا تضع بيانات الدخول في عنوان URL. استخدم الحقول أدناه.';

  @override
  String get e7SetupUrlQuery =>
      'أزل معاملات الاستعلام والجزء الذي يبدأ بـ # من عنوان URL للخادم.';

  @override
  String get e7SetupUrlPath =>
      'أزل المسار من عنوان URL للخادم. أدخل البروتوكول والمضيف والمنفذ فقط.';

  @override
  String get e7SetupRequireHttps =>
      'يلزم HTTPS خارج هذا الجهاز. يجب عدم إرسال بيانات المصادقة الأساسية عبر HTTP.';

  @override
  String get e7SetupLocalHttp =>
      'يُسمح بـ HTTP فقط مع هذا الجهاز أو عنوان شبكة خاصة مثل 192.168.x.x. للخوادم الأخرى استخدم HTTPS أو Tailscale.';

  @override
  String get e7SetupRefused =>
      'رُفض الاتصال. هل يعمل opencode serve على ذلك المضيف والمنفذ؟';

  @override
  String get e7SetupTimeout =>
      'انتهت مهلة الاتصال. تحقّق من العنوان وإمكانية الوصول إلى الخادم من هذا الهاتف.';

  @override
  String get e7SetupDns =>
      'تعذّر العثور على اسم المضيف. تحقّق من كتابة العنوان.';

  @override
  String get e7SetupCertificate =>
      'رُفضت شهادة TLS للخادم. استخدم شهادة يثق بها هذا الهاتف.';

  @override
  String get e7SetupUnhealthy =>
      'استجاب الخادم لكنه أبلغ عن مشكلة في حالته. راجع سجلاته ثم حاول مجددًا.';

  @override
  String get e7SetupServerStarting =>
      'الخادم قيد التشغيل. حاول مجددًا بعد قليل.';

  @override
  String get e7SetupPasswordNeeded => 'يتطلب هذا الخادم كلمة مرور التشغيل.';

  @override
  String get e7SetupPasswordRejected =>
      'رُفضت كلمة المرور. انسخ سطر «server password» الحالي من مخرجات الخادم؛ يتغيّر عند كل إعادة تشغيل ما لم يُضبط OPENCODE_PASSWORD.';

  @override
  String get e7SetupCredentialsRefused =>
      'رفض الخادم بيانات الدخول. تحقّق من اسم المستخدم وكلمة المرور.';

  @override
  String get e7SetupCodexUrl => 'أدخل عنوان URL لخادم Codex.';

  @override
  String get e7SetupCodexCompleteUrl => 'أدخل عنوان URL كاملًا لخادم Codex.';

  @override
  String get e7SetupCodexScheme =>
      'يجب أن تستخدم عناوين خادم Codex البروتوكول wss://، أو ws:// للخادم المحلي.';

  @override
  String get e7SetupCodexCredentials =>
      'لا تضع بيانات الدخول في عنوان URL لـ Codex.';

  @override
  String get e7SetupCodexQuery =>
      'أزل معاملات الاستعلام والجزء الذي يبدأ بـ # من عنوان URL لـ Codex.';

  @override
  String get e7SetupCodexPath => 'أزل المسار من عنوان URL لخادم Codex.';

  @override
  String get e7SetupCodexPlain =>
      'يُسمح باتصال WebSocket غير المشفّر لخادم Codex المحلي فقط.';

  @override
  String get e7SetupCodexDirectory => 'أدخل مسارًا مطلقًا لمجلد مشروع Codex.';

  @override
  String get e7SetupCodexToken => 'أدخل رمز اتصال صالحًا لـ Codex.';

  @override
  String get e7SetupRefreshPackages => 'جارٍ تحديث قائمة حزم Termux';

  @override
  String get e7SetupRepairPackages => 'جارٍ إصلاح حزم Termux';

  @override
  String get e7SetupInstallDependencies => 'جارٍ تثبيت متطلبات Termux';

  @override
  String get e7SetupPrepareTermux => 'جارٍ تجهيز Termux';

  @override
  String get e7SetupInstallUbuntu => 'جارٍ تثبيت Ubuntu';

  @override
  String get e7SetupRefreshModels => 'جارٍ تحديث قائمة نماذج OpenCode';

  @override
  String get e7SetupStartLocalServer => 'جارٍ تشغيل الخادم المحلي';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode جاهز';

  @override
  String get e7SetupPrepareRuntime => 'جارٍ تجهيز إصدار OpenCode المحدد';

  @override
  String get e7SetupSwitchLocal =>
      'جارٍ تبديل الخادم المحلي الذي يديره التطبيق';

  @override
  String get e7SetupCheckRestart =>
      'جارٍ التحقق من الخادم المحلي قبل إعادة تشغيله';

  @override
  String get e7SetupStoppingLocal => 'جارٍ إيقاف الخادم المحلي';

  @override
  String get e7SetupStoppedLocal => 'توقف الخادم المحلي';

  @override
  String get e7SetupUnknownSetup => 'حالة الإعداد غير معروفة';

  @override
  String get e7SetupUnexpectedStop =>
      'توقف خادم OpenCode المحلي بشكل غير متوقع';

  @override
  String get e7SetupSetupInterrupted =>
      'توقف الإعداد بشكل غير متوقع؛ راجع المخرجات المباشرة للتفاصيل';

  @override
  String get e7SetupRecoveryDisabled => 'الاستعادة التلقائية معطّلة';

  @override
  String get e7SetupRecoveryWasDisabled => 'عُطّلت الاستعادة التلقائية';

  @override
  String get e7SetupPortBusy =>
      'منفذ الخادم المحلي ما زال مستخدمًا؛ لم يُشغّل خادم بديل';

  @override
  String get e7SetupNoReturnData =>
      'لا يحتوي تثبيت OpenCode 2 هذا على بيانات منفصلة لـ OpenCode 1 للعودة إليها';

  @override
  String get e7SetupCredentialMismatch =>
      'تختلف بيانات الدخول المحفوظة عن بيانات هذا الإصدار؛ استعد بياناته الأصلية المحفوظة قبل العودة';

  @override
  String get e7SetupUbuntuUnavailable =>
      'تثبيت Ubuntu الذي يديره التطبيق غير متاح';

  @override
  String get e7SetupRuntimeUnavailable => 'أمر OpenCode المحدد غير متاح';

  @override
  String get e7SetupIdentityMismatch =>
      'العملية المتعقبة ليست خادم OpenCode الذي يديره التطبيق';

  @override
  String get e7SetupPasswordMissing => 'كلمة مرور الخادم المحلي مفقودة';

  @override
  String get e7SetupVersionMissing => 'ثُبّت OpenCode لكنه لم يُبلغ عن إصداره';

  @override
  String get e7SetupModelsRefreshFailed =>
      'حُدّث OpenCode، لكن تعذّر تحديث قائمة نماذجه';

  @override
  String get e7SetupStartupExited => 'توقف خادم OpenCode أثناء بدء التشغيل';

  @override
  String get e7SetupReadinessTimeout =>
      'لم يصبح خادم OpenCode جاهزًا مع مصادقة ناجحة خلال 30 ثانية';

  @override
  String get e7SetupUnreadableData => 'تعذّرت قراءة سجل موقع بيانات OpenCode 2';

  @override
  String get e7SetupUnreadablePrevious => 'تعذّرت قراءة سجل الإصدار السابق';

  @override
  String get e7SetupReadManagerFailed => 'تعذّرت قراءة حالة مدير الإعداد';

  @override
  String get e7SetupMissingManager => 'مدير الإعداد غير موجود';

  @override
  String get e7SetupRemoveInterruptedFailed =>
      'تعذّرت إزالة تثبيت Ubuntu غير المكتمل الذي يملكه التطبيق';

  @override
  String get e7SetupCheckStorageFailed =>
      'تعذّر التحقق من مساحة التخزين المتاحة قبل الإعداد';

  @override
  String get e7SetupReadStorageFailed =>
      'تعذّرت قراءة مساحة التخزين المتاحة قبل الإعداد';

  @override
  String get e7SetupRepositoryFailed => 'تعذّر اختيار مستودع حزم Termux الرسمي';

  @override
  String get e7SetupRepositoryRefreshFailed =>
      'تعذّر تحديث packages.termux.dev؛ تحقّق من الشبكة وحاول مجددًا';

  @override
  String get e7SetupRepairFailed => 'تعذّر إصلاح عملية حزم Termux غير المكتملة';

  @override
  String get e7SetupUpgradeFailed => 'تعذّر إكمال الترقية الآمنة لحزم Termux';

  @override
  String get e7SetupDependenciesFailed => 'تعذّر تثبيت متطلبات Termux';

  @override
  String get e7SetupDependenciesUnusable =>
      'متطلبات Termux ما زالت غير صالحة للاستخدام بعد إصلاح الحزم';

  @override
  String get e7SetupUbuntuUnusable =>
      'تثبيت Ubuntu الحالي غير صالح للاستخدام؛ لن يحذفه الإعداد';

  @override
  String get e7SetupExtractionFailed =>
      'لم ينتج عن فك ضغط Ubuntu Base تثبيت صالح للاستخدام';

  @override
  String get e7SetupLockFailed => 'تعذّر على مدير الإعداد حجز عملية تشغيله';

  @override
  String get e7SetupSetupGroupFailed =>
      'لم يبدأ مدير الإعداد ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupSwitchGroupFailed =>
      'لم يبدأ مدير التبديل ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupServerGroupFailed =>
      'لم يبدأ الخادم المُدار ضمن مجموعة عمليات مستقلة';

  @override
  String get e7SetupRecordIdentityFailed =>
      'تعذّر تسجيل هوية عملية الخادم المُدار';

  @override
  String e7SetupConnectingProfile(String name) {
    return 'جارٍ الاتصال بـ $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return 'جارٍ الاتصال مجددًا (المحاولة $attempt)';
  }

  @override
  String get e7SetupOpeningWorkspace => 'جارٍ فتح المشروع المحفوظ.';

  @override
  String get e7SetupWhatToCheck => 'ما يجب التحقق منه';

  @override
  String get e7SetupUpdatePassword => 'تحديث كلمة المرور';

  @override
  String e7SetupLastSetupDetail(String detail) {
    return 'آخر مخرجات الإعداد: $detail';
  }

  @override
  String e7SetupBridgeDetail(String detail) {
    return 'تفاصيل الاتصال بـ Termux: $detail';
  }

  @override
  String e7SetupDiagnosticsUnavailable(String detail) {
    return 'التشخيص غير متاح: $detail';
  }

  @override
  String e7SetupProbeHttp(String status) {
    return 'استجاب العنوان، لكن ليس كخادم OpenCode (HTTP $status). تحقّق من أن عنوان URL يشير إلى opencode serve.';
  }

  @override
  String e7SetupProbeError(String detail) {
    return 'فشل اختبار الاتصال: $detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'توقف خادم OpenCode (الرمز $code)';
  }

  @override
  String get e7SetupCheckTermux => 'على هذا الهاتف';

  @override
  String get e7SetupCommandFailed => 'فشل تنفيذ الأمر في Termux.';

  @override
  String get e7SetupUnexpectedBridge =>
      'أعاد Termux استجابة غير متوقعة للتحقق من الاتصال.';

  @override
  String get e7SetupSetupQueued => 'الإعداد في قائمة الانتظار';

  @override
  String get e7SetupNoSetup => 'لم يبدأ أي إعداد بعد';

  @override
  String get e7SetupManagerMissingAfterLaunch =>
      'مدير الإعداد غير موجود بعد التشغيل';

  @override
  String get e7SetupBootstrapCleared => 'مُسحت حالة الإعداد الأولي';

  @override
  String get e7SetupInstallingBeta => 'جارٍ تثبيت OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => 'فشلت المصادقة';

  @override
  String get e7SetupUnknownVersion => 'إصدار غير معروف';

  @override
  String get e7ModelUiClose => 'إغلاق اختيار النموذج';

  @override
  String get e7ModelUiClearSearch => 'مسح البحث عن النماذج';

  @override
  String get e7ModelUiLoadFailed => 'تعذّر تحميل النماذج';

  @override
  String get e7ModelUiRetry => 'إعادة المحاولة';

  @override
  String get e7ModelUiBasicCatalog =>
      'أرسل هذا الخادم قائمة أساسية بالنماذج. تفاصيل الإمكانات وسعة السياق غير متاحة.';

  @override
  String get e7ModelUiEditFilters => 'تعديل مرشّحات النماذج';

  @override
  String get e7ModelUiFilterModels => 'تصفية النماذج';

  @override
  String get e7ModelUiFiltered => 'تمت التصفية';

  @override
  String get e7ModelUiFilters => 'المرشّحات';

  @override
  String get e7ModelUiRefresh => 'تحديث النماذج';

  @override
  String get e7ModelUiAnyCapability => 'كل الإمكانات';

  @override
  String get e7ModelUiFastModes => 'الأوضاع السريعة';

  @override
  String get e7ModelUiReasoning => 'الاستدلال';

  @override
  String get e7ModelUiLargestContext => 'أكبر سعة سياق';

  @override
  String get e7ModelUiNoneAvailable => 'لا توجد نماذج متاحة';

  @override
  String get e7ModelUiFavoritesEmpty => 'احتفظ بنماذجك المفضّلة هنا';

  @override
  String get e7ModelUiRecentEmpty => 'اختيارك التالي يبدأ هنا';

  @override
  String get e7ModelUiNoMatches => 'لا توجد نماذج مطابقة';

  @override
  String get e7ModelUiConfigureProvider =>
      'أعِدّ مزوّد خدمة على خادم OpenCode، ثم حدّث القائمة.';

  @override
  String get e7ModelUiFavoritesHint =>
      'اضغط على النجمة بجانب أي نموذج لتجده هنا.';

  @override
  String get e7ModelUiRecentHint =>
      'ستظهر النماذج التي تستخدمها هنا، بدءًا بالأحدث.';

  @override
  String get e7ModelUiNoFastModes =>
      'لا يعلن أي نموذج عن وضع سريع أو وضع ذي جهد استدلال منخفض.';

  @override
  String get e7ModelUiNoMatchesHint =>
      'جرّب بحثًا آخر أو مزوّدًا أو مرشّح إمكانات مختلفًا.';

  @override
  String get e7ModelUiClearFilters => 'مسح المرشّحات';

  @override
  String get e7ModelUiBrowseAll => 'تصفّح كل النماذج';

  @override
  String get e7ModelUiAgent => 'الوكيل';

  @override
  String get e7ModelUiServerDefault => 'إعداد الخادم الافتراضي';

  @override
  String get e7ModelUiProvider => 'مزوّد الخدمة';

  @override
  String get e7ModelUiAllProviders => 'كل مزوّدي الخدمة';

  @override
  String get e7ModelUiCurrent => 'النموذج الحالي';

  @override
  String get e7ModelUiUnavailable => 'غير متاح';

  @override
  String get e7ModelUiDeprecated => 'متقادم';

  @override
  String get e7ModelUiPreview => 'تجريبي';

  @override
  String get e7ModelUiFavoritesFailed => 'تعذّر حفظ المفضّلة. حاول مجددًا.';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return 'استخدام النموذج والوضع';
  }

  @override
  String get e7ModelUiUseSession => 'استخدام في هذه المحادثة';

  @override
  String get e7ModelUiUseNewSessions => 'استخدام في المحادثات الجديدة';

  @override
  String get e7ModelUiTools => 'الأدوات';

  @override
  String get e7ModelUiAttachments => 'المرفقات';

  @override
  String get e7ModelUiDefault => 'الافتراضي';

  @override
  String get e7ModelUiSelectionGone =>
      'لم يعد هذا الخيار متاحًا. حدّث النماذج وحاول مجددًا.';

  @override
  String get e7VoiceUiLocalInput => 'إدخال صوتي محلي';

  @override
  String get e7VoiceUiChooseModel => 'اختر نموذج Whisper INT8 متعدد اللغات';

  @override
  String get e7VoiceUiPrivacyDownload =>
      'يبقى الصوت على هذا الجهاز. يُحوَّل الكلام إلى نص محليًا ويُحذف الصوت بعد الاستخدام. يتطلب تنزيل النموذج مرة واحدة اتصالًا بالإنترنت.';

  @override
  String get e7VoiceUiNoBuiltInMic =>
      'يشير Android إلى عدم وجود ميكروفون مدمج. قد يعمل الإدخال الصوتي باستخدام ميكروفون سلكي أو USB.';

  @override
  String get e7VoiceUiLanguage => 'لغة تحويل الكلام إلى نص';

  @override
  String get e7VoiceUiVerifying => 'جارٍ التحقّق من النموذج المنزّل';

  @override
  String get e7VoiceUiCancelDownload => 'إلغاء التنزيل';

  @override
  String get e7VoiceUiNotNow => 'ليس الآن';

  @override
  String get e7VoiceUiUseModel => 'استخدام النموذج';

  @override
  String get e7VoiceUiDownload => 'تنزيل';

  @override
  String get e7VoiceUiKeep => 'احتفاظ';

  @override
  String get e7VoiceUiDelete => 'حذف';

  @override
  String get e7VoiceUiDefaultBadge => 'افتراضي';

  @override
  String get e7VoiceUiOptionalBadge => 'اختياري';

  @override
  String get e7VoiceUiInstalledBadge => 'مثبّت';

  @override
  String get e7VoiceUiNotInstalledBadge => 'غير مثبّت';

  @override
  String get e7VoiceUiSetupBusy => 'غير متاح أثناء إعداد النموذج';

  @override
  String get e7VoiceUiSelected => 'محدّد';

  @override
  String get e7VoiceUiSelectHint => 'اضغط مرتين للاختيار';

  @override
  String get e7VoiceUiDefault => 'الافتراضي';

  @override
  String get e7VoiceUiOptional => 'اختياري';

  @override
  String get e7VoiceUiInstalled => 'مثبّت';

  @override
  String get e7VoiceUiRedownload => 'إعادة التنزيل';

  @override
  String get e7VoiceUiOpenSettings => 'فتح إعدادات التطبيق';

  @override
  String get e7VoiceUiRetry => 'إعادة المحاولة';

  @override
  String get e7VoiceUiStartListening => 'بدء الاستماع';

  @override
  String get e7VoiceUiDraftReady => 'النص جاهز للمراجعة';

  @override
  String get e7VoiceUiNeedsAttention => 'الإدخال الصوتي يحتاج إلى انتباهك';

  @override
  String get e7VoiceUiModelRequired => 'يلزم نموذج محلي';

  @override
  String e7ModelUiCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count نموذج',
      many: '$count نموذجًا',
      few: '$count نماذج',
      two: 'نموذجان',
      one: 'نموذج واحد',
      zero: 'لا توجد نماذج',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiContext(String count) {
    return 'سعة سياق $count';
  }

  @override
  String e7ModelUiOutput(String count) {
    return 'حد إخراج $count';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return 'إضافة $model إلى المفضّلة';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return 'إزالة $model من المفضّلة';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · جهد الاستدلال: $effort';
  }

  @override
  String get e7ModelUiLoading => 'جارٍ تحميل قائمة النماذج';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input إدخال · $output إخراج / مليون';
  }

  @override
  String e7VoiceUiDownloadPercent(int percent) {
    return 'جارٍ تنزيل النموذج الصوتي: $percent بالمئة';
  }

  @override
  String e7VoiceUiDownloadProgress(String received, String total) {
    return '$received من $total';
  }

  @override
  String e7VoiceUiSetupFailed(String error) {
    return 'تعذّر إعداد النموذج: $error';
  }

  @override
  String e7VoiceUiDeletePack(String model) {
    return 'هل تريد حذف $model؟';
  }

  @override
  String e7VoiceUiDeleteDetail(String size) {
    return 'سيؤدي ذلك إلى إزالة $size من مساحة تخزين التطبيق الخاصة. يمكنك تنزيله مجددًا لاحقًا.';
  }

  @override
  String e7VoiceUiDownloadSize(String size) {
    return 'حجم التنزيل: $size';
  }

  @override
  String e7VoiceUiPackSemantics(
    String model,
    String size,
    String badges,
    String description,
  ) {
    return '$model، $size، $badges. $description';
  }

  @override
  String get e7VoiceUiLicenses => 'تراخيص الصوت ومصادره';

  @override
  String get e7VoiceUiNoticesFailed => 'تعذّر تحميل تراخيص الصوت. حاول مجددًا.';

  @override
  String get e7VoiceUiAuto => 'اكتشاف تلقائي';

  @override
  String get e7VoiceUiEnglish => 'الإنجليزية';

  @override
  String get e7VoiceUiArabic => 'العربية';

  @override
  String get e7VoiceUiBalanced => 'متوازن';

  @override
  String get e7VoiceUiBalancedDetail =>
      'توازن موصى به بين الجودة ومساحة التخزين والسرعة.';

  @override
  String get e7VoiceUiAccurate => 'دقة عالية';

  @override
  String get e7VoiceUiAccurateDetail =>
      'جودة أعلى اختيارية؛ تتطلب ذاكرة أكبر بكثير.';

  @override
  String get e7VoiceUiCompact => 'بديل صغير الحجم';

  @override
  String get e7VoiceUiCompactDetail =>
      'الأسرع والأصغر حجمًا؛ تقل دقته مع الصوت غير الواضح.';

  @override
  String get e7VoiceUiUnsupportedAbi =>
      'لا يدعم أي محرّك صوتي مضمّن البنية الثنائية لهذا الجهاز.';

  @override
  String get e7VoiceUiPermissionBlocked =>
      'الوصول إلى الميكروفون محظور. اسمح به في إعدادات تطبيق Android.';

  @override
  String get e7VoiceUiPermissionRequired =>
      'يلزم إذن الميكروفون للإدخال الصوتي المحلي.';

  @override
  String get e7VoiceUiDeviceUnavailable =>
      'الإدخال الصوتي المحلي غير متاح. أوقف التشغيل، وتحقّق من إعدادات الميكروفون، ثم حاول مجددًا.';

  @override
  String get e7VoiceUiInputUnavailable =>
      'الإدخال الصوتي المحلي غير متاح على هذه المنصّة.';

  @override
  String get e7VoiceUiNoAudio => 'لم يُلتقط أي صوت.';

  @override
  String get e7VoiceUiInterrupted => 'انقطع التسجيل.';

  @override
  String get e7VoiceUiMicrophoneError =>
      'أبلغ الميكروفون عن خطأ. تحقّق من إعداداته وحاول مجددًا.';

  @override
  String get e7VoiceUiInputFailed => 'تعذّر إكمال الإدخال الصوتي. حاول مجددًا.';

  @override
  String get e7VoiceUiTechnicalDetails => 'التفاصيل التقنية';

  @override
  String get e7VoiceUiHttpsRequired =>
      'لا يمكن تنزيل النماذج الصوتية إلا عبر HTTPS.';

  @override
  String get e7VoiceUiTransportClosed => 'أُغلق اتصال تنزيل النموذج الصوتي.';

  @override
  String get e7VoiceUiInvalidRedirect =>
      'أعاد تنزيل النموذج الصوتي توجيهًا غير صالح.';

  @override
  String get e7VoiceUiUnsafeRedirect =>
      'أُعيد توجيه تنزيل النموذج الصوتي إلى عنوان لا يستخدم HTTPS.';

  @override
  String get e7VoiceUiNoResponse => 'لم يستجب خادم النماذج.';

  @override
  String get e7VoiceUiDownloadTimeout =>
      'انتهت مهلة تنزيل النموذج. تحقّق من الاتصال وحاول مجددًا.';

  @override
  String get e7VoiceUiChecksumFailed =>
      'لم يجتز النموذج المنزّل فحص البصمة. أعد تنزيله.';

  @override
  String get e7VoiceUiVerificationFailed =>
      'لم يجتز النموذج التحقّق النهائي. أعد تنزيله.';

  @override
  String get e7VoiceUiHttpFailed => 'رفض خادم النماذج التنزيل. حاول مجددًا.';

  @override
  String get e7VoiceUiLengthFailed =>
      'حجم النموذج المنزّل غير متوقّع. أعد تنزيله.';

  @override
  String get e7VoiceUiIncomplete => 'تنزيل النموذج غير مكتمل. حاول مجددًا.';

  @override
  String get e7VoiceUiDownloadFailed =>
      'تعذّر تنزيل النموذج الصوتي. حاول مجددًا.';

  @override
  String e7VoiceUiMemory(String model, int required, int available) {
    return 'يحتاج $model إلى هاتف بذاكرة تبلغ نحو $required ميغابايت؛ ذاكرة هذا الهاتف $available ميغابايت.';
  }

  @override
  String e7VoiceUiStorage(String model, String size) {
    return 'يتطلب $model مساحة خالية قدرها $size، تشمل هامشًا احتياطيًا.';
  }

  @override
  String get e7ModelUiProviderFallback => 'مزوّد خدمة';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first و$last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first، و$last';
  }

  @override
  String get e7ModelUiListSeparator => '، ';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'سجّل OpenCode الدخول إلى $providers، لكنه لم يحمّل بيانات الخدمة بعد. لذلك يتعذّر استخدام النماذج وتظهر رسالة «Model not found». أعد التحميل لتفعيل تسجيل الدخول.',
      one:
          'سجّل OpenCode الدخول إلى $providers، لكنه لم يحمّل بيانات الخدمة بعد. لذلك يتعذّر استخدام النماذج وتظهر رسالة «Model not found». أعد التحميل لتفعيل تسجيل الدخول.',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain =>
      'تعذّر الوصول إلى OpenCode. حاول مرة أخرى.';

  @override
  String get approvalsUiMenu => 'الموافقات';

  @override
  String get approvalsUiTitle => 'موافقات هذه المحادثة';

  @override
  String get approvalsUiAskDetail => 'ينتظرك كل طلب إذن.';

  @override
  String get approvalsUiAutoTitle => 'الموافقة تلقائيًا أثناء الاتصال';

  @override
  String get approvalsUiAutoDetail =>
      'يرد هذا الهاتف على كل طلب إذن بخيار «السماح مرة واحدة» فور وصوله. لا يُحفظ أي شيء كمسموح دائمًا.';

  @override
  String get approvalsUiInheritTitle => 'الوكلاء الفرعيون يرثون هذا الخيار';

  @override
  String get approvalsUiInheritDetail =>
      'تتبع محادثات الوكلاء الفرعيين التي تبدأها هذه المحادثة الخيار نفسه ما لم يكن لها خيار خاص بها.';

  @override
  String get approvalsUiInheritUnavailable =>
      'يتاح عند تفعيل الموافقة التلقائية.';

  @override
  String get approvalsUiInheritedFrom => 'موروث من المحادثة الأصل';

  @override
  String get approvalsUiInheritedDetail =>
      'تتبع هذه المحادثة موافقات المحادثة الأصل. تجاوز ذلك لتختار لهذه المحادثة فقط.';

  @override
  String get approvalsUiOverride => 'تجاوز لهذه المحادثة';

  @override
  String get approvalsUiFollowParent => 'اتبع المحادثة الأصل مجددًا';

  @override
  String get approvalsUiIndicatorOn => 'الموافقة تلقائيًا';

  @override
  String approvalsUiAutoApproved(String action) {
    return 'تمت الموافقة تلقائيًا · $action';
  }

  @override
  String get approvalsUiFailedDetail =>
      'فشلت الموافقة التلقائية. راجع هذا الطلب.';

  @override
  String approvalsUiSaveFailed(String error) {
    return 'تعذّر حفظ إعداد الموافقة: $error';
  }

  @override
  String get approvalsUiOpenSettings => 'فتح إعدادات الموافقة';

  @override
  String get approvalsUiIndicatorPaused => 'الموافقة التلقائية متوقفة مؤقتًا';

  @override
  String approvalsUiRecordTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تمت الموافقة تلقائيًا على $count طلبًا على هذا الخادم',
      few: 'تمت الموافقة تلقائيًا على $count طلبات على هذا الخادم',
      two: 'تمت الموافقة تلقائيًا على طلبين على هذا الخادم',
      one: 'تمت الموافقة تلقائيًا على طلب واحد على هذا الخادم',
      zero: 'لم تتم الموافقة تلقائيًا على أي طلب على هذا الخادم بعد',
    );
    return '$_temp0';
  }

  @override
  String get handoffUiComputerTitle => 'المتابعة على الحاسوب';

  @override
  String handoffUiComputerIntro(String binary) {
    return 'شغّل هذا الأمر في طرفية على الحاسوب الذي يعمل عليه هذا الخادم. سيفتح المحادثة نفسها في واجهة $binary. لن يُرسل أي شيء حتى تكتب بنفسك.';
  }

  @override
  String get handoffUiComputerDirectoryNote =>
      'تنتمي المحادثات إلى مجلد مشروع، لذلك ينتقل الأمر أولًا إلى مجلد هذه المحادثة.';

  @override
  String handoffUiComputerVerify(String verified, String binary) {
    return 'تم التحقق من الصيغة مع $verified. إذا كان الإصدار المثبّت لديك مختلفًا، فراجع $binary --help للتأكد من خيار --session.';
  }

  @override
  String get handoffUiUnavailableDirectory =>
      'لم يُبلغ الخادم عن مجلد مشروع لهذه المحادثة، لذلك لا يوجد مجلد لفتحها فيه. أعد تحميل المحادثة وحاول مرة أخرى.';

  @override
  String get handoffUiUnavailableWorkspace =>
      'تعمل هذه المحادثة داخل بيئة سحابية. مجلدها يخص مضيف البيئة، لذلك لا يمكن لأمر طرفية عادي فتحها. صدّر المحادثة ثم استوردها بدلًا من ذلك.';

  @override
  String get handoffUiUnavailableReference =>
      'لا يمكن وضع مرجع هذه المحادثة في أمر بشكل آمن.';

  @override
  String get handoffUiPhoneTitle => 'الفتح على هاتف آخر';

  @override
  String get handoffUiPhoneIntro =>
      'امسح هذا الرمز بتطبيق OpenCode Mobile على الهاتف الآخر. لا يحمل الرمز سوى معرّف هذا الخادم المحفوظ ومعرّف المحادثة: لا رسائل ولا عنوان ولا كلمة مرور. يجب أن يكون هذا الخادم محفوظًا مسبقًا على الهاتف الآخر.';

  @override
  String get handoffUiPhoneQrLabel => 'رمز QR يفتح هذه المحادثة على هاتف آخر';

  @override
  String get handoffUiPhoneLinkLabel => 'الرابط';

  @override
  String get handoffUiPhoneCopyLink => 'نسخ الرابط';

  @override
  String get handoffUiPhoneUnavailable =>
      'تعذّر إنشاء رابط لهذه المحادثة. أعد تحميل المحادثة وحاول مرة أخرى.';

  @override
  String get handoffUiLinkServerMissing =>
      'هذا الخادم غير محفوظ على هذا الهاتف. أضفه من قسم الخوادم، ثم امسح الرمز مرة أخرى.';

  @override
  String get handoffUiLinkDismiss => 'تجاهل';

  @override
  String get handoffUiLinkWaiting => 'سيتم فتح المحادثة بمجرد اتصال الخادم…';

  @override
  String get handoffUiLinkReentry =>
      'أدخل كلمة مرور هذا الخادم مرة أخرى، ثم امسح الرمز مجددًا.';

  @override
  String get handoffUiLinkConnectionFailed =>
      'تعذّر الاتصال بالخادم المحفوظ. تحقق منه في قسم الخوادم، ثم امسح الرمز مرة أخرى.';

  @override
  String get teamUiAccessControls => 'القرارات والتحكم';

  @override
  String get teamUiAccessReadOnly => 'للقراءة فقط';

  @override
  String get teamUiAddAddressHint => 'http://100.x.x.x:8373';

  @override
  String get teamUiAddAddressLabel => 'العنوان';

  @override
  String get teamUiAddManually => 'إضافة يدويًا';

  @override
  String get teamUiAddSubmit => 'اختبار وتشغيل';

  @override
  String get teamUiAddTesting => 'جارٍ فحص العنوان…';

  @override
  String get teamUiAddTitle => 'إضافة مضيف فريق الذكاء الاصطناعي';

  @override
  String get teamUiAddressRequired => 'أدخل عنوان المضيف.';

  @override
  String get teamUiChange => 'تغيير';

  @override
  String get teamUiDisclaimerComputer => 'يعمل بسرعة حاسوبك؛ أبقِه مستيقظًا';

  @override
  String get teamUiDisclaimerPhone =>
      'قد يوقفه أندرويد عند انطفاء الشاشة؛ وهو أبطأ من الحاسوب';

  @override
  String get teamUiEditorBody =>
      'إذا كان هذا الحاسوب يشغّل Gas City، فسيعثر عليه التطبيق تلقائيًا.';

  @override
  String teamUiEditorConfigured(String url) {
    return 'مضيف فريق الذكاء الاصطناعي: $url';
  }

  @override
  String get teamUiEditorTitle => 'فريق الذكاء الاصطناعي (اختياري)';

  @override
  String get teamUiHostGuideIntro =>
      'كل شيء يبقى داخل شبكة Tailscale الخاصة بك؛ ولا يُنشر أي شيء على الإنترنت.';

  @override
  String get teamUiHostGuideStep1 =>
      'Install Gas City\'s three tools, gc, bd and dolt, on your PATH; the full guide has each download with its checksum. Then check that all three are found:';

  @override
  String get teamUiHostGuideStep2 =>
      'Save the team file from the full guide in a folder next to your project. Then set up the team and add your project, folder first:';

  @override
  String get teamUiHostGuideStep3 =>
      'Start the team and check that it answers:';

  @override
  String get teamUiHostGuideStep4 =>
      'Download the front that lets this phone in over Tailscale, check it and start it, with your own Tailscale login after --allow. Then add it here: the computer\'s Tailscale address with the port in the command, and the team\'s name.';

  @override
  String get teamUiHostGuideTitle => 'شغّل فريق ذكاء اصطناعي على حاسوبك';

  @override
  String get teamUiHostModeComputer => 'حاسوب';

  @override
  String get teamUiHostModePhone => 'هذا الهاتف';

  @override
  String get teamUiHow => 'كيف';

  @override
  String get teamUiKeep => 'إبقاء';

  @override
  String get teamUiLabelAccess => 'الصلاحية';

  @override
  String get teamUiLabelAddress => 'العنوان';

  @override
  String get teamUiLabelCity => 'المدينة';

  @override
  String get teamUiLabelHost => 'المضيف';

  @override
  String get teamUiLabelProvider => 'المزوّد';

  @override
  String get teamUiLabelVersion => 'الإصدار';

  @override
  String get teamUiLearnHow => 'تعرّف على الطريقة';

  @override
  String get teamUiNoServer => 'اتصل بخادم لاستخدام الإضافات.';

  @override
  String get teamUiPluginsTitle => 'الإضافات';

  @override
  String get teamUiReadOnlyBody =>
      'يمكنك متابعة هذا الفريق من الهاتف. أما الإجابة والتوجيه فيحتاجان إلى الواجهة الأمامية على الحاسوب.';

  @override
  String get teamUiReasonCityNotRunning => 'مضيف الفريق قيد البدء';

  @override
  String get teamUiReasonNotGasCity => 'لم يُعثر على فريق ذكاء اصطناعي';

  @override
  String get teamUiReasonPlainHttp => 'العنوان ليس على شبكة Tailscale';

  @override
  String get teamUiReasonReadFailed => 'فشلت آخر قراءة';

  @override
  String get teamUiReasonUnreachable => 'تعذّر الوصول إلى المضيف';

  @override
  String get teamUiRefresh => 'تحديث';

  @override
  String get teamUiRowConnecting => 'مفعّل · جارٍ الاتصال…';

  @override
  String get teamUiRowNotAvailable => 'غير متاح على هذا الخادم';

  @override
  String teamUiRowNotAvailableReason(String reason) {
    return 'غير متاح على هذا الخادم · $reason';
  }

  @override
  String get teamUiRowOff => 'متوقف';

  @override
  String teamUiRowOn(String server) {
    return 'مفعّل · $server';
  }

  @override
  String teamUiRowOnReadOnly(String server) {
    return 'مفعّل · $server · للعرض فقط';
  }

  @override
  String get teamUiRowReconnecting => 'مفعّل · جارٍ إعادة الاتصال…';

  @override
  String get teamUiRowTitle => 'فريق الذكاء الاصطناعي · Gas City';

  @override
  String teamUiRowUnreachable(String minutes) {
    return 'مفعّل · تعذّر الوصول إلى المضيف منذ $minutes دقيقة';
  }

  @override
  String get teamUiTailnetRequired =>
      'يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale الخاصة بك أو على هذا الجهاز. استخدم عنوان Tailscale الخاص بالحاسوب (100.x.x.x أو name.ts.net).';

  @override
  String get teamUiTechnicalDetails => 'التفاصيل التقنية';

  @override
  String get teamUiTechnicalLastAnswer => 'آخر استجابة من المضيف';

  @override
  String get teamUiTermAgent => 'وكيل · polecat';

  @override
  String get teamUiTermProject => 'مشروع · rig';

  @override
  String get teamUiTermRun => 'تشغيل · convoy';

  @override
  String get teamUiTermTeam => 'فريق · city';

  @override
  String get teamUiTermWork => 'عمل · bead';

  @override
  String get teamUiTermsHeading => 'المصطلحات';

  @override
  String get teamUiTurnOffBody =>
      'يزيل بطاقته وعناصر الانتباه وبيانات الفريق المخزّنة مؤقتًا من هذا الهاتف. لا يتغير شيء على المضيف.';

  @override
  String get teamUiTurnOffConfirm => 'Turn off AI Team';

  @override
  String teamUiTurnOffTitle(String server) {
    return 'هل تريد إيقاف فريق الذكاء الاصطناعي لخادم $server؟';
  }

  @override
  String get teamUiVerdictCityNotRunning =>
      'مضيف الفريق قيد البدء. حاول مجددًا بعد قليل.';

  @override
  String get teamUiVerdictNotGasCity =>
      'لا يشغّل هذا الخادم فريق ذكاء اصطناعي بعد. جهّز واحدًا على الحاسوب — يستغرق ذلك بضع دقائق.';

  @override
  String get teamUiVerdictUnreachable =>
      'لا استجابة من هذا العنوان. تحقق منه، ومن أن الحاسوب مستيقظ ومتصل بشبكة Tailscale الخاصة بك.';

  @override
  String get teamUiVersionUnknown => 'غير معروف';

  @override
  String teamUiCardAgentsSummary(
    int total,
    int working,
    int waiting,
    int idle,
    int stopped,
  ) {
    return '$total وكلاء: $working يعملون، $waiting ينتظرون، $idle خاملون، $stopped متوقفون';
  }

  @override
  String get teamUiCardEmptyHint => 'ابدأ المهام من الحاسوب في الوقت الحالي.';

  @override
  String get teamUiCardEmptyTitle => 'لا توجد مهام حديثة';

  @override
  String get teamUiCardErrorCityNotRunning =>
      'مضيف الفريق قيد البدء. حاول مرة أخرى بعد لحظات.';

  @override
  String get teamUiCardErrorNotGasCity =>
      'هذا الخادم لا يشغّل فريق ذكاء اصطناعي بعد. جهّز واحدًا على الحاسوب، فالأمر يستغرق بضع دقائق.';

  @override
  String get teamUiCardErrorPlainHttp =>
      'يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale لديك أو على هذا الجهاز. استخدم tailscale serve على الحاسوب ثم حاول مرة أخرى.';

  @override
  String get teamUiCardErrorUnreachable =>
      'تعذّر الوصول إلى مضيف الفريق. يعمل فريق الذكاء الاصطناعي عبر شبكة Tailscale لديك أو على هذا الجهاز.';

  @override
  String get teamUiStateUnreachableTitle => 'تعذّر الوصول إلى مضيف الفريق';

  @override
  String get teamUiStateNotGasCityTitle =>
      'لا يوجد فريق ذكاء اصطناعي على هذا الخادم';

  @override
  String get teamUiStateStartingTitle => 'مضيف الفريق قيد البدء';

  @override
  String get teamUiStatePlainHttpTitle =>
      'لا يمكن لفريق الذكاء الاصطناعي استخدام هذا العنوان';

  @override
  String get teamUiStateNotAnsweringTitle => 'مضيف الفريق لا يستجيب';

  @override
  String get teamUiCardLoading => 'جارٍ الاتصال بمضيف الفريق…';

  @override
  String teamUiCardRefreshFailed(String time) {
    return 'فشل آخر تحديث · تُعرض بيانات من $time';
  }

  @override
  String get teamUiCardRetry => 'إعادة المحاولة';

  @override
  String get teamUiCardRunStateBlocked => 'معطّل';

  @override
  String get teamUiCardRunStateCancelled => 'أُلغي';

  @override
  String get teamUiCardRunStateCompleted => 'مكتمل';

  @override
  String get teamUiCardRunStateFailed => 'فشل';

  @override
  String get teamUiCardRunStatePlanning => 'قيد التخطيط';

  @override
  String get teamUiCardRunStateUnknown => 'غير معروف';

  @override
  String get teamUiCardRunStateWaiting => 'بانتظار عامل';

  @override
  String get teamUiCardRunStateWorking => 'قيد العمل';

  @override
  String get teamUiCardRunStateWaitingMerge => 'قيد المراجعة';

  @override
  String get teamUiCardRunStateMerged => 'مكتمل · دُمج';

  @override
  String teamUiCardStale(String time) {
    return 'تُعرض بيانات من $time · تعذّر الوصول إلى المضيف';
  }

  @override
  String get teamUiHomeAgentNoWork => 'لا عمل حالي';

  @override
  String get teamUiHomeAgentStateBlocked => 'معطّل';

  @override
  String get teamUiHomeAgentStateCrashed => 'تعطّل';

  @override
  String get teamUiHomeAgentStateIdle => 'خامل';

  @override
  String get teamUiHomeAgentStateStopped => 'متوقف';

  @override
  String get teamUiHomeAgentStateUnknown => 'غير معروف';

  @override
  String get teamUiHomeAgentStateWaiting => 'بانتظارك';

  @override
  String get teamUiHomeAgentStateWorking => 'قيد العمل';

  @override
  String get teamUiHomeFilterActive => 'النشطة';

  @override
  String get teamUiHomeFilterAll => 'الكل';

  @override
  String get teamUiHomeFilterBlocked => 'المعطّلة';

  @override
  String get teamUiHomeFilterCompleted => 'المكتملة';

  @override
  String get teamUiHomeGateAnswerOnComputer =>
      'أجب عن هذا على الحاسوب. يمكن للهاتف المتابعة فقط في الوقت الحالي.';

  @override
  String get teamUiHomeGateAnswerOnPhone =>
      'أجب عن هذا في المضيف على هذا الهاتف. يمكن للتطبيق المتابعة فقط في الوقت الحالي.';

  @override
  String get teamUiHomeGateKindChoice => 'قرار';

  @override
  String get teamUiHomeGateKindConfirmation => 'موافقة';

  @override
  String get teamUiHomeGateKindFreeText => 'سؤال';

  @override
  String get teamUiHomeGateKindGateBead => 'بوابة';

  @override
  String get teamUiHomeGateKindReviewReady => 'جاهز للمراجعة';

  @override
  String get teamUiHomeGateKindRunFailed => 'فشل التشغيل';

  @override
  String get teamUiHomeGateKindUnknown => 'يحتاجك';

  @override
  String teamUiHomeGateLinkAgent(String name) {
    return 'الوكيل $name';
  }

  @override
  String teamUiHomeGateLinkRun(String title) {
    return 'التشغيل $title';
  }

  @override
  String teamUiHomeGateLinkWork(String title) {
    return 'العمل $title';
  }

  @override
  String get teamUiHomeGateOptions => 'الخيارات';

  @override
  String get teamUiHomeHostRawHeading => 'القيم الخام';

  @override
  String get teamUiHomeRunNeedsYou => 'يحتاجك';

  @override
  String teamUiHomeRunProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get teamUiHomeRunsEmptyFiltered => 'لا توجد مهام مطابقة.';

  @override
  String get teamUiHomeRunsEmptyHint => 'جرّب عامل تصفية آخر أو امسح البحث.';

  @override
  String get teamUiHomeSearchHint => 'ابحث في المهام';

  @override
  String get teamUiHomeTitle => 'فريق الذكاء الاصطناعي';

  @override
  String get teamUiRunBack => 'رجوع';

  @override
  String teamUiRunBlockedByDeps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'ينتظر $count خطوة أخرى',
      many: 'ينتظر $count خطوة أخرى',
      few: 'ينتظر $count خطوات أخرى',
      two: 'ينتظر خطوتين أخريين',
      one: 'ينتظر خطوة أخرى',
      zero: 'لا ينتظر شيئًا',
    );
    return '$_temp0';
  }

  @override
  String teamUiRunElapsedDays(int count) {
    return '$count ي';
  }

  @override
  String teamUiRunElapsedHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String teamUiRunElapsedMinutes(int count) {
    return '$count د';
  }

  @override
  String teamUiRunSinceHandoff(String elapsed) {
    return '$elapsed منذ التسليم';
  }

  @override
  String get teamUiRunLabelFormula => 'الصيغة';

  @override
  String get teamUiRunLabelId => 'معرّف التشغيل';

  @override
  String get teamUiRunLabelKind => 'النوع';

  @override
  String get teamUiRunLabelLastError => 'آخر خطأ';

  @override
  String get teamUiRunLabelProject => 'المشروع';

  @override
  String get teamUiRunLabelRawState => 'حالة المزوّد';

  @override
  String get teamUiRunLabelStarted => 'بدأ';

  @override
  String get teamUiRunLabelTrackedWork => 'العمل المتتبَّع';

  @override
  String get teamUiRunLabelUpdated => 'حُدّث';

  @override
  String get teamUiRunMissingHint =>
      'ربما أُغلق أو أُزيل. حدّث للتحقق مرة أخرى.';

  @override
  String get teamUiRunMissingTitle => 'لم تعد هذه المهمة موجودة على المضيف';

  @override
  String get teamUiRunTermBatch => 'مهمة · convoy';

  @override
  String get teamUiRunTermFormula => 'مهمة · formula';

  @override
  String get teamUiRunTermUnknown => 'مهمة';

  @override
  String teamUiRunTimelineAgentStopped(String name) {
    return 'توقف $name';
  }

  @override
  String teamUiRunTimelineAgentWoke(String name) {
    return 'بدأ $name';
  }

  @override
  String teamUiRunTimelineGateOpened(String title) {
    return 'يحتاجك: $title';
  }

  @override
  String teamUiRunTimelineGateResolved(String title) {
    return 'أُجيب: $title';
  }

  @override
  String teamUiRunTimelineRunChanged(String state) {
    return 'التشغيل الآن $state';
  }

  @override
  String teamUiRunTimelineWorkClosed(String title) {
    return 'أُغلق $title';
  }

  @override
  String teamUiRunTimelineWorkCreated(String title) {
    return 'أُضيف $title';
  }

  @override
  String teamUiRunTimelineWorkUpdated(String title) {
    return 'حُدّث $title';
  }

  @override
  String teamUiAgentContextSemantics(int percent) {
    return 'استُخدم $percent% من السياق';
  }

  @override
  String teamUiAgentContextShort(int percent) {
    return 'السياق $percent%';
  }

  @override
  String get teamUiAgentLabelBranch => 'الفرع';

  @override
  String get teamUiAgentLabelHarness => 'البيئة';

  @override
  String get teamUiAgentLabelModel => 'النموذج';

  @override
  String get teamUiAgentLabelPack => 'الحزمة';

  @override
  String get teamUiAgentLabelPool => 'المجمّع';

  @override
  String get teamUiAgentLabelSessionAge => 'عمر الجلسة';

  @override
  String get teamUiAgentLabelSessionId => 'الجلسة';

  @override
  String get teamUiAgentLabelSessionName => 'اسم الجلسة';

  @override
  String get teamUiAgentLabelWorkDir => 'مجلد العمل';

  @override
  String get teamUiAgentMissingHint => 'ربما أُعيد تدويره. حدّث للتحقق.';

  @override
  String get teamUiAgentMissingTitle => 'هذا الوكيل لم يعد على المضيف';

  @override
  String get teamUiAgentNeedsYou => 'بحاجة إليك';

  @override
  String get teamUiAgentOutputConnecting => 'جارٍ الاتصال بالجلسة…';

  @override
  String get teamUiAgentOutputCopy => 'نسخ المخرجات';

  @override
  String get teamUiAgentOutputEnded =>
      'انتهت الجلسة · المخرجات لم تعد على المضيف';

  @override
  String get teamUiAgentOutputJump => 'الانتقال إلى الأحدث';

  @override
  String get teamUiAgentOutputLive => 'مباشر';

  @override
  String get teamUiAgentOutputTitle => 'المخرجات المباشرة';

  @override
  String get teamUiAgentOutputUnavailable =>
      'المخرجات المباشرة غير متاحة لهذا الوكيل';

  @override
  String get teamUiAgentRecyclingSoon =>
      'إعادة التدوير قريبًا · السياق شبه ممتلئ';

  @override
  String teamUiAgentSessionAge(String age) {
    return 'الجلسة منذ $age';
  }

  @override
  String teamUiAgentTermSession(String id) {
    return 'وكيل · جلسة $id';
  }

  @override
  String get teamUiAgentValueUnknown => 'غير مُبلَّغ عنه';

  @override
  String get teamUiWorkLabelAssignee => 'المكلَّف';

  @override
  String get teamUiWorkLabelClosedReason => 'سبب الإغلاق';

  @override
  String get teamUiWorkLabelDependsOn => 'يعتمد على (معرّفات)';

  @override
  String get teamUiWorkLabelId => 'معرّف العمل';

  @override
  String get teamUiWorkLabelLabels => 'الوسوم';

  @override
  String get teamUiWorkLabelParent => 'الأصل';

  @override
  String get teamUiWorkLabelProject => 'المشروع';

  @override
  String get teamUiWorkLabelRawState => 'حالة المزوّد';

  @override
  String get teamUiWorkLabelRun => 'معرّف التشغيل';

  @override
  String get teamUiWorkLabelSession => 'معرّف الجلسة';

  @override
  String get teamUiWorkLabelSessionName => 'اسم الجلسة';

  @override
  String get teamUiWorkLabelType => 'النوع';

  @override
  String get teamUiWorkOwnerNone => 'غير مُسنَد';

  @override
  String get teamUiWorkSheetBlocking => 'يعطّل';

  @override
  String get teamUiWorkSheetBranch => 'الفرع';

  @override
  String get teamUiWorkSheetClosed => 'أُغلق';

  @override
  String get teamUiWorkSheetCreated => 'أُنشئ';

  @override
  String get teamUiWorkSheetDependencies => 'يعتمد على';

  @override
  String get teamUiWorkSheetDescription => 'الوصف';

  @override
  String get teamUiWorkSheetMissing => 'عنصر العمل هذا لم يعد على المضيف.';

  @override
  String get teamUiWorkSheetNoTimestamps => 'لم يرسل المضيف طوابع زمنية.';

  @override
  String get teamUiWorkSheetOpenSession => 'فتح الجلسة';

  @override
  String get teamUiWorkSheetOutput => 'المخرجات';

  @override
  String teamUiWorkSheetStamp(String date, String clock, String age) {
    return '$date · $clock ($age)';
  }

  @override
  String get teamUiWorkSheetTarget => 'هدف الدمج';

  @override
  String get teamUiWorkSheetTimestamps => 'الطوابع الزمنية';

  @override
  String get teamUiWorkSheetUpdated => 'حُدّث';

  @override
  String get teamUiWorkSheetValidation => 'التحقق';

  @override
  String get teamUiWorkSheetValidationFailed => 'فشل';

  @override
  String get teamUiWorkSheetValidationPassed => 'نجح';

  @override
  String get teamUiWorkSheetValidationUnknown => 'سُجّلت نتيجة';

  @override
  String get teamUiWorkSheetWorktree => 'شجرة العمل';

  @override
  String get teamUiWorkStateBlocked => 'معطّل';

  @override
  String get teamUiWorkStateCancelled => 'أُلغي';

  @override
  String get teamUiWorkStateCompleted => 'مكتمل';

  @override
  String get teamUiWorkStateFailed => 'فشل';

  @override
  String get teamUiWorkStateNeedsInput => 'يحتاج مدخلات';

  @override
  String get teamUiWorkStateQueued => 'في الانتظار';

  @override
  String get teamUiWorkStateReady => 'جاهز';

  @override
  String get teamUiWorkStateReview => 'مراجعة';

  @override
  String get teamUiWorkStateUnknown => 'غير معروف';

  @override
  String get teamUiWorkStateWaiting => 'منتظر';

  @override
  String get teamUiWorkStateWorking => 'قيد العمل';

  @override
  String teamUiUsageCostEstimated(String cost) {
    return '$cost تقديريًا';
  }

  @override
  String teamUiUsageTokens(String count) {
    return '$count رمز';
  }

  @override
  String get teamUiGateAnswerOnHost =>
      'أجب عن هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateAnswerOnHostPhone =>
      'أجب عن هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateCloseOnHost =>
      'أغلق هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateCloseOnHostPhone =>
      'أغلق هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateDestructive => 'إجراء مدمّر';

  @override
  String get teamUiGateFailureActionAgent =>
      'أعد تشغيل الوكيل على المضيف؛ سيستأنف عنصر العمل من جديد.';

  @override
  String get teamUiGateFailureActionAuthentication =>
      'سجّل الدخول مجددًا على المضيف (مفتاح المزوّد أو الرمز)، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionContext =>
      'أعد تشغيل الوكيل بسياق جديد على المضيف؛ سيستأنف من عنصر العمل.';

  @override
  String get teamUiGateFailureActionDependency =>
      'ثبّت الاعتمادية الناقصة أو حدّثها على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionExecution =>
      'اقرأ سجل الخطوة على المضيف، وأصلح الأمر، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionInfrastructure =>
      'تحقق من المضيف وخدماته، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionMergeConflict =>
      'حُل التعارض في شجرة العمل على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionTest =>
      'أصلح الاختبارات الفاشلة على المضيف، ثم أعد محاولة التشغيل.';

  @override
  String get teamUiGateFailureActionUnknown =>
      'اقرأ الخطأ على المضيف وقرر هناك؛ لا يستطيع الهاتف التصرف فيه بعد.';

  @override
  String get teamUiGateFailureAffectedNone =>
      'لا عنصر عمل مفتوحًا في هذا التشغيل.';

  @override
  String get teamUiGateFailureAffectedWork => 'العمل المتأثر';

  @override
  String get teamUiGateFailureClassAgent => 'الوكيل';

  @override
  String get teamUiGateFailureClassAuthentication => 'المصادقة';

  @override
  String get teamUiGateFailureClassContext => 'السياق';

  @override
  String get teamUiGateFailureClassDependency => 'الاعتماديات';

  @override
  String get teamUiGateFailureClassExecution => 'التنفيذ';

  @override
  String get teamUiGateFailureClassInfrastructure => 'البنية التحتية';

  @override
  String get teamUiGateFailureClassMergeConflict => 'تعارض دمج';

  @override
  String get teamUiGateFailureClassTest => 'الاختبارات';

  @override
  String get teamUiGateFailureClassUnknown => 'غير معروف';

  @override
  String get teamUiGateFailureClassification => 'التصنيف';

  @override
  String get teamUiGateFailureErrorNone => 'لم يرسل المضيف نص خطأ.';

  @override
  String get teamUiGateGone =>
      'لم يعد هذا بانتظارك؛ أُجيب عنه أو أُغلق على المضيف.';

  @override
  String get teamUiGateKindAgentBlocked => 'وكيل معطَّل';

  @override
  String get teamUiGateLabelKind => 'نوع المزوّد';

  @override
  String get teamUiGateLabelRequestId => 'معرّف الطلب';

  @override
  String get teamUiGateLabelRunId => 'معرّف التشغيل';

  @override
  String get teamUiGateLabelSessionId => 'معرّف الجلسة';

  @override
  String get teamUiGateLabelWorkId => 'معرّف العمل';

  @override
  String get teamUiGateNoDescription => 'لم يرسل المضيف وصفًا.';

  @override
  String get teamUiGateReviewOnHost =>
      'راجع هذا على المضيف. الهاتف يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateReviewOnHostPhone =>
      'راجع هذا داخل المضيف على هذا الهاتف. التطبيق يكتفي بالمشاهدة حاليًا.';

  @override
  String get teamUiGateUnblocks => 'يفتح الطريق أمام';

  @override
  String get teamUiGateUnblocksNone => 'لا شيء ينتظر هذا بعد.';

  @override
  String get teamUiHostKindDesktop => 'حاسوب مكتبي';

  @override
  String get teamUiHostKindDisclaimerLaptop =>
      'النوم وإغلاق الغطاء يوقفان الفريق مؤقتًا؛ وتستأنف التشغيلات عند الاستيقاظ';

  @override
  String get teamUiHostKindDisclaimerWsl =>
      'النوم وإغلاق الغطاء يوقفان الفريق مؤقتًا؛ وتستأنف التشغيلات عند الاستيقاظ. كما يتوقف WSL عند إغلاق آخر نافذة طرفية له.';

  @override
  String get teamUiHostKindHint => 'لا يغيّر سوى التنبيه المعروض مع الفريق.';

  @override
  String get teamUiHostKindLabel => 'نوع الحاسوب';

  @override
  String get teamUiHostKindLaptop => 'حاسوب محمول';

  @override
  String get teamUiHostKindWsl => 'Windows (WSL)';

  @override
  String get teamUiReceiptSent => 'أُرسل · بانتظار تأكيد المضيف';

  @override
  String get teamUiReceiptAnswered => 'تمت الإجابة';

  @override
  String get teamUiReceiptUnconfirmed =>
      'أُرسل دون تأكيد — تحقق على المضيف قبل إعادة الإرسال';

  @override
  String get teamUiGateAnswerSend => 'إرسال';

  @override
  String get teamUiGateAnswerApprove => 'موافقة';

  @override
  String get teamUiGateAnswerDeny => 'رفض';

  @override
  String get teamUiGateAnswerMarkDone => 'تم الإنجاز';

  @override
  String get teamUiGateAnswerHint => 'اكتب إجابتك';

  @override
  String teamUiGateAnswerRunRetry(String work, String agent) {
    return 'إعادة المحاولة';
  }

  @override
  String get teamUiGateAnswerRunLogs => 'عرض السجلات';

  @override
  String get teamUiGateAnswerRunCancel => 'إيقاف العمل';

  @override
  String teamUiGateAnswerRejected(String message) {
    return 'لم يُقبل: $message';
  }

  @override
  String get teamUiGateAnswerRejectedNoMessage => 'لم يقبل المضيف هذه الإجابة.';

  @override
  String get teamUiGateAnswerConfirmApproveTitle =>
      'الموافقة على هذا الإجراء المدمّر؟';

  @override
  String get teamUiGateAnswerConfirmApproveBody =>
      'يصنّف المضيف هذا الإجراء مدمّرًا. لا يمكن التراجع عنه من الهاتف.';

  @override
  String get teamUiGateAnswerConfirmCancelRunTitle => 'إيقاف هذا العمل؟';

  @override
  String get teamUiGateAnswerConfirmCancelRunBody =>
      'يتوقف التشغيل ويبقى عمله المفتوح كما هو.';

  @override
  String get teamUiControlMessage => 'رسالة';

  @override
  String get teamUiControlNudge => 'تنبيه';

  @override
  String get teamUiControlPause => 'إيقاف مؤقت';

  @override
  String get teamUiControlResume => 'استئناف';

  @override
  String get teamUiControlStop => 'إيقاف';

  @override
  String get teamUiControlRestart => 'إعادة تشغيل';

  @override
  String get teamUiControlReassign => 'إعادة إسناد العمل…';

  @override
  String get teamUiControlCreateWork => 'أُرسلت المهمة إلى وكيل';

  @override
  String teamUiControlStopConfirmTitle(String agent) {
    return 'إيقاف $agent؟';
  }

  @override
  String get teamUiControlStopConfirmBody =>
      'تنتهي جلسته الآن. يبقى عمله حيث هو؛ ويمكن للمضيف إيقاظه لاحقًا.';

  @override
  String teamUiControlRestartConfirmTitle(String agent) {
    return 'إعادة تشغيل $agent؟';
  }

  @override
  String get teamUiControlRestartConfirmBody =>
      'تتوقف جلسته ثم تبدأ من جديد. يفقد الوكيل ما كان في سياقه ويستأنف عمله من المضيف.';

  @override
  String get teamUiControlKeep => 'متابعة';

  @override
  String get teamUiControlReceiptSent => 'أُرسل';

  @override
  String get teamUiControlReceiptConfirmed => 'مؤكد';

  @override
  String get teamUiControlReceiptUnconfirmed => 'غير مؤكد';

  @override
  String get teamUiControlReceiptRefused => 'مرفوض';

  @override
  String teamUiControlReceiptLine(String control, String state) {
    return '$control · $state';
  }

  @override
  String get teamUiControlCancelRun => 'إيقاف التشغيل';

  @override
  String get teamUiStartRunFab => 'كلّف الفريق بمهمة';

  @override
  String get teamUiStartRunTitle => 'كلّف الفريق بمهمة';

  @override
  String get teamUiStartRunObjectiveLabel => 'الهدف';

  @override
  String get teamUiStartRunObjectiveHint =>
      'ما الذي ينبغي أن يحققه الفريق؟ نتيجة واحدة، بكلماتك.';

  @override
  String get teamUiStartRunObjectiveEmpty => 'اكتب هدفًا أولًا.';

  @override
  String get teamUiStartRunProjectLabel => 'المشروع';

  @override
  String get teamUiStartRunProjectAny => 'دع المخطِّط يختار';

  @override
  String get teamUiStartRunSupervisionLabel => 'الإشراف';

  @override
  String get teamUiStartRunSupervisionHigh => 'عالٍ';

  @override
  String get teamUiStartRunSupervisionHighHint =>
      'يسأل الفريق قبل كل قرار، وقبل الاختبارات التي تغيّر الحالة، وقبل أي دمج.';

  @override
  String get teamUiStartRunSupervisionBalanced => 'متوازن';

  @override
  String get teamUiStartRunSupervisionBalancedHint =>
      'يقرر الفريق الأمور الروتينية بنفسه ويسأل قبل الدمج وعند الإخفاقات وفي خيارات التصميم.';

  @override
  String get teamUiStartRunSupervisionAutonomous => 'مستقل';

  @override
  String get teamUiStartRunSupervisionAutonomousHint =>
      'يعمل الفريق حتى الإنجاز ضمن حدود المضيف ولا يسأل إلا عندما يتعذر عليه المتابعة.';

  @override
  String get teamUiStartRunPlannerLabel => 'المخطِّط';

  @override
  String get teamUiStartRunPlannerMayor => 'العمدة (Mayor)';

  @override
  String teamUiStartRunSend(String planner) {
    return 'إرسال إلى المخطِّط';
  }

  @override
  String get teamUiStartRunHostGuide => 'دليل المضيف';

  @override
  String get teamUiStartRunWaking => 'جارٍ إيقاظ المخطِّط…';

  @override
  String get teamUiStartRunDirectIntro =>
      'المخطِّط متوقف على هذا المضيف. أعطِ مهمة واحدة مباشرةً إلى وكيل المشروع.';

  @override
  String get teamUiStartRunDirectTitle => 'المهمة';

  @override
  String get teamUiStartRunDirectTitleHint =>
      'سطر واحد: ماذا ينبغي أن يفعل الوكيل؟';

  @override
  String get teamUiStartRunDirectTitleRequired => 'اكتب مهمة أولًا.';

  @override
  String get teamUiStartRunDirectDetails => 'التفاصيل (اختياري)';

  @override
  String get teamUiStartRunDirectSend => 'إرسال إلى وكيل';

  @override
  String teamUiStartRunRefused(String reason) {
    return 'رفض المضيف الهدف: $reason';
  }

  @override
  String get teamUiMergeTitleReady => 'جاهز للدمج';

  @override
  String get teamUiMergeTitleNotReady => 'غير جاهز للدمج';

  @override
  String get teamUiMergeTitleMerged => 'تم الدمج';

  @override
  String teamUiMergeRequest(String id) {
    return 'طلب الدمج $id';
  }

  @override
  String get teamUiMergeLineWork => 'بنود العمل';

  @override
  String get teamUiMergeLineTests => 'الاختبارات';

  @override
  String get teamUiMergeLineBuild => 'البناء';

  @override
  String get teamUiMergeLineReview => 'المراجعة';

  @override
  String get teamUiMergeLineConflicts => 'لا تعارضات';

  @override
  String get teamUiMergeLineAcceptance => 'معايير القبول';

  @override
  String teamUiMergeFiles(int files, int additions, int deletions) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files ملف · +$additions / −$deletions',
      many: '$files ملفًا · +$additions / −$deletions',
      few: '$files ملفات · +$additions / −$deletions',
      two: 'ملفان · +$additions / −$deletions',
      one: 'ملف واحد · +$additions / −$deletions',
      zero: 'لا تغييرات في الملفات',
    );
    return '$_temp0';
  }

  @override
  String get teamUiMergeReviewChanges => 'مراجعة التغييرات';

  @override
  String get teamUiMergeApprove => 'اعتماد الطلب';

  @override
  String teamUiMergeApprovedBy(String login) {
    return 'اعتمده $login';
  }

  @override
  String get teamUiMergeMerge => 'دمج';

  @override
  String teamUiMergeConfirmTitle(String branch) {
    return 'الدمج في $branch؟';
  }

  @override
  String get teamUiMergeConfirmMessage => 'لا يمكن التراجع عن هذا من الهاتف';

  @override
  String teamUiMergeConfirmAction(String branch) {
    return 'دمج في $branch';
  }

  @override
  String teamUiMergeDisabledReason(String line, String detail) {
    return 'الدمج متوقف: $line — $detail';
  }

  @override
  String teamUiMergeBoundary(String text) {
    return 'حدود المضيف: $text';
  }

  @override
  String teamUiMergeRefused(String text) {
    return 'رفض المضيف: $text';
  }

  @override
  String teamUiMergeMerged(String branch, String commit) {
    return 'تم الدمج في $branch · $commit';
  }

  @override
  String teamUiMergeAlready(String branch) {
    return 'موجود بالفعل على $branch';
  }

  @override
  String teamUiMergeUnavailable(String reason) {
    return 'جاهزية الدمج غير متاحة: $reason';
  }

  @override
  String get teamUiMergeNoRoles => 'لا يملك المضيف أدوار دمج لهذا التشغيل';

  @override
  String get teamUiMergeLoading => 'جارٍ التحقق من جاهزية الدمج…';

  @override
  String get teamUiMergePending => 'يعمل على المضيف';

  @override
  String get teamUiMergeChangesTitle => 'التغييرات';

  @override
  String get teamUiMergeChangesEmpty => 'لم يُبلغ المضيف عن تغييرات في الملفات';

  @override
  String get teamUiMergeChangesWork => 'بنود العمل';

  @override
  String get teamUiMergeSent => 'أُرسل · بانتظار تأكيد المضيف';

  @override
  String get teamUiMergeApproveConfirmed => 'تم تسجيل الاعتماد';

  @override
  String teamUiPolicySupervision(String level) {
    return 'الإشراف · $level';
  }

  @override
  String get teamUiPolicyBoundariesLabel => 'الحدود';

  @override
  String get teamUiPolicyBoundariesNone => 'لا حدود مضبوطة على المضيف';

  @override
  String get teamUiPolicyFromHost => 'مضبوطة على المضيف · للقراءة فقط هنا';

  @override
  String teamUiPolicyRig(String rig) {
    return 'لـ $rig';
  }

  @override
  String teamUiPolicySemantics(String level, String boundaries) {
    return 'الإشراف $level. الحدود: $boundaries';
  }

  @override
  String get teamUiRunLabelRawTitle => 'عنوان المزوّد';

  @override
  String get termuxStorageTitle => 'التخزين على هذا الهاتف';

  @override
  String termuxStorageRowUsed(String size) {
    return '$size مستخدمة';
  }

  @override
  String get termuxStorageRowNotScanned => 'لم يُفحص بعد';

  @override
  String get termuxStorageRowScanning => 'جارٍ القياس…';

  @override
  String get termuxStorageScanAction => 'فحص التخزين';

  @override
  String get termuxStorageRescanAction => 'إعادة الفحص';

  @override
  String get termuxStorageIntro =>
      'اعرض مساحة التخزين التي يستخدمها Termux، بما فيها الخادم المحلي والأدوات الأخرى. وسّع أي فئة للاطلاع على تفاصيلها. يمكن تنظيف ملفات تخزين مؤقت محددة قابلة لإعادة الإنشاء فقط؛ وتبقى المشاريع وبيانات الفريق وتسجيلات الدخول وسجل المحادثات محفوظة.';

  @override
  String get termuxStorageScanning => 'جارٍ قياس التخزين';

  @override
  String get termuxStorageScanningDetail =>
      'قد تستغرق الذاكرات المؤقتة الكبيرة دقيقة أو دقيقتين. يمكنك مغادرة هذه الشاشة؛ سيستمر الفحص.';

  @override
  String get termuxStorageCancel => 'إيقاف الفحص';

  @override
  String get termuxStorageCancelled => 'أُوقف الفحص';

  @override
  String get termuxStorageFailed => 'لم يكتمل الفحص. حاول مرة أخرى.';

  @override
  String termuxStorageTotal(String size) {
    return 'تم قياس $size في Termux';
  }

  @override
  String termuxStorageDeletableTotal(String size) {
    return 'يمكن تنظيف $size';
  }

  @override
  String get termuxStorageScannedJustNow => 'فُحص للتو';

  @override
  String termuxStorageScannedMinutesAgo(int minutes) {
    return 'فُحص قبل $minutes دقيقة';
  }

  @override
  String termuxStorageScannedHoursAgo(int hours) {
    return 'فُحص قبل $hours ساعة';
  }

  @override
  String get termuxStorageCatBuildCaches => 'ذاكرات البناء المؤقتة';

  @override
  String get termuxStorageNoteBuildCaches =>
      'ملفات Gradle المؤقتة وذاكرة المحتوى المنزّل لـ npm فقط. قد يلزم تنزيل الملفات مجددًا، مما قد يؤثر في البناء دون اتصال. أوقف عمليات البناء وتثبيت الحزم قبل التنظيف.';

  @override
  String get termuxStorageCatAgentScratch => 'مجلدات عمل الوكلاء المؤقتة';

  @override
  String get termuxStorageNoteAgentScratch =>
      'قد تحتوي المجلدات المؤقتة على عمل غير مكتمل أو ملفات تستخدمها أدوات أخرى. تُعرض أحجامها للاطلاع فقط ولا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatProjectBuildOutputs =>
      'مجلدات بأسماء مرتبطة بالبناء';

  @override
  String get termuxStorageNoteProjectBuildOutputs =>
      'قد تحتوي المجلدات المسماة build أو .dart_tool أو node_modules أو target على ملفاتك أيضًا. لا يكفي الاسم لإثبات إمكانية حذفها، لذلك لا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatToolchains => 'أدوات البناء';

  @override
  String get termuxStorageNoteToolchains =>
      'تثبيتات Android SDK وJava وFlutter. قد تستخدمها مشاريع أخرى ولا يمكن حذفها من هنا.';

  @override
  String get termuxStorageCatAiTeam => 'فريق الذكاء الاصطناعي';

  @override
  String get termuxStorageNoteAiTeam =>
      'قد تحتوي مجلدات الفريق وقواعد البيانات والأدوات على عمل تحتاج إلى الاحتفاظ به. لا يمكن حذفها من هنا، حتى عند إيقاف الفريق.';

  @override
  String get termuxStorageCatOpenCode => 'OpenCode نفسه';

  @override
  String get termuxStorageNoteOpenCode =>
      'الخادم وتسجيلات دخوله وسجل المحادثات. لا تُحذف من هنا؛ فللمحادثات شاشتها الخاصة.';

  @override
  String get termuxStorageCatProjects => 'المشاريع (ملفاتك)';

  @override
  String get termuxStorageNoteProjects =>
      'مُدرجة لترى حجمها. لا تُحذف من هنا أبدًا.';

  @override
  String get termuxStorageWillRemove => 'ما الذي يحذفه التنظيف';

  @override
  String get termuxStorageNothingHere => 'لا شيء هنا';

  @override
  String termuxStorageCleanConfirmTitle(String size, String category) {
    return 'هل تريد حذف $size من $category؟';
  }

  @override
  String get termuxStorageCleanConfirmBody =>
      'هل تريد حذف ملفات Gradle المؤقتة وذاكرة محتوى npm المعروضة فقط؟ قد يلزم تنزيل الملفات مجددًا وقد يتأثر البناء دون اتصال. أوقف عمليات البناء وتثبيت الحزم أولًا، ثم أعد الفحص لتحديث الأحجام المقاسة.';

  @override
  String termuxStorageCleanConfirm(String size) {
    return 'حذف $size';
  }

  @override
  String get termuxStorageKeep => 'إبقاء';

  @override
  String get termuxStorageCleaning => 'جارٍ الحذف…';

  @override
  String termuxStorageFreed(String size) {
    return 'تم حذف $size';
  }

  @override
  String get termuxStorageFreedNothing => 'لم يُحذف شيء';

  @override
  String termuxStorageInUse(String process) {
    return 'قيد الاستخدام بواسطة $process. أوقفه أولًا من «يعمل الآن».';
  }

  @override
  String termuxStorageRefusedCount(int count) {
    return 'تُركت $count مسارات في مكانها';
  }

  @override
  String termuxStorageProjectBuild(String size, String build) {
    return '$size · $build في مجلدات مرتبطة بالبناء';
  }

  @override
  String get termuxStorageOpenRunning => 'فتح «يعمل الآن»';

  @override
  String termuxStorageBytesGb(String value) {
    return '$value غ.ب';
  }

  @override
  String termuxStorageBytesMb(String value) {
    return '$value م.ب';
  }

  @override
  String termuxStorageBytesKb(String value) {
    return '$value ك.ب';
  }

  @override
  String termuxStorageBytesB(String value) {
    return '$value بايت';
  }

  @override
  String get termuxProcsTitle => 'يعمل الآن';

  @override
  String get termuxProcsRowLoading => 'جارٍ التحقق…';

  @override
  String get termuxProcsAutoRefresh => 'يتحدّث كل 10 ثوانٍ أثناء فتح الشاشة';

  @override
  String termuxProcsStopSemantics(String name) {
    return 'إيقاف $name';
  }

  @override
  String termuxProcsStopOneTitle(String name) {
    return 'هل تريد إيقاف $name؟';
  }

  @override
  String get termuxProcsStopOneBody =>
      'تتلقى إيقافًا لطيفًا، ثم إيقافًا قسريًا بعد 5 ثوانٍ.';

  @override
  String get termuxProcsProtected => 'محمية · افتح «على هذا الهاتف»';

  @override
  String termuxProcsOrphanParentGone(String elapsed) {
    return 'العملية الأم غير موجودة · تعمل منذ $elapsed';
  }

  @override
  String termuxProcsOrphanCpu(String cpu) {
    return '$cpu من وقت المعالج دون مالك';
  }

  @override
  String get termuxProcsStopping => 'جارٍ الإيقاف…';

  @override
  String termuxProcsStopped(int count) {
    return 'أُوقفت $count';
  }

  @override
  String termuxProcsStoppedForced(int count, int forced) {
    return 'أُوقفت $count (احتاجت $forced إلى إيقاف قسري)';
  }

  @override
  String termuxProcsRemaining(int count) {
    return '$count لم تتوقف';
  }

  @override
  String termuxProcsRefused(int count) {
    return '$count محمية، لم تُوقف';
  }

  @override
  String get termuxProcsEmpty => 'لا شيء يعمل في خادم الهاتف';

  @override
  String get termuxProcsCommand => 'الأمر';

  @override
  String get termuxProcsFolder => 'المجلد';

  @override
  String termuxProcsDurationSeconds(int seconds) {
    return '$seconds ث';
  }

  @override
  String termuxProcsDurationMinutes(int minutes) {
    return '$minutes د';
  }

  @override
  String termuxProcsDurationHours(int hours, int minutes) {
    return '$hours س $minutes د';
  }

  @override
  String termuxProcsMemoryMb(int mb) {
    return '$mb م.ب';
  }

  @override
  String get teamUiPhoneOptionalTag => 'اختياري · تجريبي';

  @override
  String get teamUiPhoneChooseProjectTitle => 'اختر مشروعًا';

  @override
  String get teamUiPhoneContinue => 'متابعة';

  @override
  String get teamUiPhoneSuccessTitle =>
      'فريق الذكاء الاصطناعي يعمل على هذا الهاتف';

  @override
  String get teamUiPhoneRetry => 'إعادة المحاولة';

  @override
  String teamUiPhoneFailedChecksum(String name) {
    return 'الملف المنزَّل $name لم يطابق المجموع الاختباري المثبّت في هذا الإصدار، فلم يُثبَّت. لم يُحتفظ بشيء منه؛ تحقّق من الشبكة وحاول مجددًا.';
  }

  @override
  String get teamUiPhoneFailedUnsupportedArch =>
      'معالج هذا الهاتف ليس ARM بنظام 64 بت، وهو ما يحتاجه وقت تشغيل الفريق.';

  @override
  String get teamUiPhoneFailedDownload =>
      'لم يكتمل التنزيل. تحقّق من الاتصال وحاول مجددًا.';

  @override
  String teamUiPhoneFailedDownloadDns(String host) {
    return 'تعذّر على الهاتف العثور على خادم التنزيل $host. تأكّد من اتصال الهاتف بالإنترنت ومن أن DNS الخاص أو مانع الإعلانات لا يحجبه، ثم حاول مجددًا.';
  }

  @override
  String teamUiPhoneFailedDownloadConnect(String host) {
    return 'تعذّر على الهاتف الوصول إلى خادم التنزيل $host. تحقّق من الاتصال ثم حاول مجددًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String teamUiPhoneFailedDownloadTimeout(String host) {
    return 'استغرق خادم التنزيل $host وقتًا طويلًا للرد. حاول مجددًا على اتصال أكثر ثباتًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String teamUiPhoneFailedDownloadTls(String host) {
    return 'تعذّر إنشاء اتصال آمن مع $host. تأكّد من صحة تاريخ الهاتف ووقته ومن عدم وجود وكيل (proxy) يعترض الاتصال، ثم حاول مجددًا.';
  }

  @override
  String teamUiPhoneFailedDownloadHttp(String host, String code) {
    return 'رفض خادم التنزيل $host الملف (HTTP $code). حاول مجددًا لاحقًا؛ وإن تكرر ذلك فتنزيل فريق الذكاء الاصطناعي غير متاح لهذا الإصدار من التطبيق.';
  }

  @override
  String teamUiPhoneFailedDownloadInterrupted(String host) {
    return 'انقطع الاتصال مع $host أثناء التنزيل. حاول مجددًا؛ يُستأنف التنزيل من حيث توقف.';
  }

  @override
  String get teamUiPhoneFailedDownloadWrite =>
      'تعذّر حفظ التنزيل على هذا الهاتف. حرّر بعض المساحة ثم حاول مجددًا.';

  @override
  String teamUiPhoneFailedDownloadOther(String host, String code) {
    return 'فشل التنزيل من $host (الخطأ $code). تحقّق من الاتصال ثم حاول مجددًا.';
  }

  @override
  String get teamUiPhoneFailedPackages =>
      'لم يتمكن Ubuntu من تثبيت المتطلبات (tmux وjq وlsof وprocps). يوضح المخرج أدناه أيها.';

  @override
  String get teamUiPhoneFailedProject =>
      'مجلد المشروع مفقود أو ليس مستودع git.';

  @override
  String get teamUiPhoneFailedCity =>
      'لم تتمكن Gas City من إنشاء المدينة. يوضح المخرج أدناه السبب.';

  @override
  String get teamUiPhoneFailedSupervisorExited =>
      'توقف المشرف مباشرة بعد بدئه. يوضح المخرج أدناه السبب.';

  @override
  String teamUiPhoneFailedHealth(String url) {
    return 'بدأ المشرف لكنه لم يُجب قط على $url.';
  }

  @override
  String get teamUiPhoneFailedInterrupted =>
      'توقف الإعداد قبل اكتماله. ربما أوقف أندرويد Termux أثناء غياب التطبيق؛ لا يُفقد شيء.';

  @override
  String teamUiPhoneFailedReason(String reason) {
    return 'السبب: $reason';
  }

  @override
  String get teamUiPhoneDispatchFailed =>
      'لم يبدأ Termux الخطوة. افتح Termux مرة واحدة ثم حاول مجددًا.';

  @override
  String get teamUiPhoneSectionTitle => 'على هذا الهاتف';

  @override
  String get teamUiPhoneStatusChecking => 'جارٍ التحقق…';

  @override
  String get teamUiPhoneStatusNotInstalled => 'غير مثبّت';

  @override
  String get teamUiPhoneStatusInstalled => 'مثبّت · لا مدينة بعد';

  @override
  String get teamUiPhoneStatusStopped => 'متوقف';

  @override
  String get teamUiPhoneStatusStarting => 'جارٍ البدء…';

  @override
  String get teamUiPhoneStatusStopping => 'جارٍ الإيقاف…';

  @override
  String teamUiPhoneStatusWorking(String verb) {
    return 'جارٍ العمل… ($verb)';
  }

  @override
  String teamUiPhoneStatusRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'يعمل · $count وكيل',
      many: 'يعمل · $count وكيلًا',
      few: 'يعمل · $count وكلاء',
      two: 'يعمل · وكيلان',
      one: 'يعمل · وكيل واحد',
      zero: 'يعمل',
    );
    return '$_temp0';
  }

  @override
  String get teamUiPhoneStatusFailed => 'لا يعمل · فشلت الخطوة الأخيرة';

  @override
  String teamUiPhoneStatusUnreachable(String url) {
    return 'بدأ، لكنه لا يُجيب على $url';
  }

  @override
  String get teamUiPhoneStatusUnknown => 'تعذّرت قراءة الحالة';

  @override
  String teamUiPhoneVersions(String gc, String bd, String dolt) {
    return 'gc $gc · bd $bd · dolt $dolt';
  }

  @override
  String get teamUiPhoneStopTitle => 'إيقاف الفريق على هذا الهاتف؟';

  @override
  String get teamUiPhoneStopBody =>
      'يتوقف الوكلاء العاملون حيث هم. لا يُفقد شيء؛ وتُستأنف التشغيلات عند بدئه من جديد.';

  @override
  String get teamUiPhoneKilled =>
      'أوقف أندرويد الفريق أثناء غياب التطبيق. لم يُفقد شيء.';

  @override
  String get teamUiPhoneKeepRunningTitle => 'أبقِه يعمل';

  @override
  String get teamUiPhoneKeepRunningSubtitle =>
      'قفل الاستيقاظ وإعداد البطارية وقاتل العمليات الشبحية';

  @override
  String get teamUiPhoneTipsIntro =>
      'يوقف أندرويد أعمال الخلفية التي يعدّها مفرطة، والفريق كذلك تمامًا: عشرات عمليات gc وbd القصيرة ووكيل يستهلك المعالج بالكامل. ثلاثة أمور تبقيه حيًّا.';

  @override
  String get teamUiPhoneTipWakeLock =>
      'أبقِ Termux في المقدمة، أو فعّل قفل الاستيقاظ فيه: شغّل termux-wake-lock داخل Termux، أو انقر Acquire wakelock في إشعاره. إطفاء الشاشة من دونه ينهي التشغيل.';

  @override
  String get teamUiPhoneTipBattery =>
      'الإعدادات › التطبيقات › Termux › البطارية › غير مقيّد، وأوقف التنظيف التلقائي الخاص بالشركة المصنّعة لهاتفك عن Termux.';

  @override
  String get teamUiPhoneTipPhantom =>
      'لا يزال أندرويد 12 وما بعده يقتل العمليات الفرعية لتطبيق في الخلفية (قاتل العمليات الشبحية). أوقف ذلك مرة واحدة من Termux نفسه عبر تصحيح الأخطاء اللاسلكي؛ لا حاجة إلى حاسوب:';

  @override
  String get teamUiPhoneTipsCopy => 'نسخ الأوامر';

  @override
  String get teamUiPhoneRemoveTitle =>
      'حذف فريق الذكاء الاصطناعي من هذا الهاتف؟';

  @override
  String teamUiPhoneActionFailed(String reason) {
    return 'لم ينجح ذلك: $reason';
  }

  @override
  String get teamUiPhoneNotAvailable =>
      'غير متاح على هذا الهاتف. يحتاج تشغيل فريق إلى بيئة لينكس 64 بت؛ ولا يستطيع هذا الجهاز أو هذا الإصدار توفيرها.';

  @override
  String teamUiPhoneFailedNoSpace(String detail) {
    return 'لا توجد مساحة كافية على هذا الهاتف. $detail حرّر بعض المساحة (يمكن لقسم «التخزين على هذا الهاتف» تنظيف ذاكرة البناء المؤقتة)، ثم حاول مجددًا.';
  }

  @override
  String get phoneServerConnect => 'اتصال';

  @override
  String get phoneServerOpen => 'فتح';

  @override
  String get phoneServerStart => 'تشغيل';

  @override
  String get phoneServerStop => 'إيقاف';

  @override
  String get phoneServerMore => 'إجراءات أخرى للخادم';

  @override
  String get phoneServerForget => 'نسيان بيانات الدخول المحفوظة';

  @override
  String get phoneServerStartFailed =>
      'لم يبدأ تشغيل الخادم. افتح «إدارة الإعداد» لمعرفة السبب.';

  @override
  String get phoneServerRestartFailed =>
      'لم تتم إعادة تشغيل الخادم. افتح «إدارة الإعداد» لمعرفة السبب.';

  @override
  String get phoneServerStopFailed => 'تعذّر إيقاف الخادم. أعد المحاولة.';

  @override
  String get termuxStorageCatSharedCaches => 'ملفات مؤقتة وبيانات حزم أخرى';

  @override
  String get termuxStorageNoteSharedCaches =>
      'قد تستخدم أدوات أخرى الملفات المؤقتة المشتركة والحزم المثبتة ومجلدات التنزيل، أو قد تحتوي على ملفات تحتاج إلى الاحتفاظ بها. لا يمكن حذفها من هنا.';

  @override
  String get termuxStorageRescanRequired =>
      'فحص سابق · أعد الفحص قبل تنظيف المزيد';

  @override
  String get safetyStopSharingTitle => 'إيقاف مشاركة هذه المحادثة؟';

  @override
  String get safetyStopSharingBody =>
      'يتوقف الرابط عن العمل لدى كل من يملكه. لا يتغير شيء في المحادثة نفسها.';

  @override
  String get safetyStopSharingKeep => 'مواصلة المشاركة';

  @override
  String get safetyStopLocalServerTitle => 'إيقاف الخادم المحلي؟';

  @override
  String get safetyStopLocalServerBody =>
      'يتوقف كل ما ينفذه الوكيل على هذا الهاتف، وينقطع اتصال التطبيق بالخادم. تبقى مشاريعك ومحادثاتك على الهاتف؛ شغّل الخادم مرة أخرى للمتابعة.';

  @override
  String get safetyStopLocalServerKeep => 'إبقاؤه قيد التشغيل';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return 'قطع الاتصال بـ $server؟';
  }

  @override
  String get safetyMcpDisconnectBody =>
      'تفقد الوكلاء أدواته حتى تعيد توصيله، وقد يفشل استدعاء أداة قيد التنفيذ. تبقى إعداداته محفوظة.';

  @override
  String get safetyMcpDisconnectKeep => 'البقاء متصلًا';

  @override
  String get safetyStopOrphanBody =>
      'لا شيء ينتظرها، لكن ما كانت تنفذه يضيع. تتلقى إيقافًا لطيفًا، ثم إيقافًا قسريًا بعد 5 ثوانٍ.';

  @override
  String get settingsHubGroupNotifications => 'الإشعارات والخلفية';

  @override
  String get settingsHubGroupUsage => 'الاستخدام';

  @override
  String get settingsHubGroupHelp => 'المساعدة';

  @override
  String get settingsHubThisServer => 'هذا الخادم';

  @override
  String get settingsHubAccounts => 'الحسابات';

  @override
  String get settingsHubModelAndMode => 'النموذج والوضع';

  @override
  String get settingsHubVoice => 'الصوت';

  @override
  String get settingsHubPrivacyRow => 'الخصوصية والبيانات المحلية';

  @override
  String get settingsHubSearchServerAliases =>
      'server host url address password profile connection health status version update service خادم مضيف عنوان كلمة مرور ملف اتصال حالة إصدار تحديث خدمة';

  @override
  String get settingsHubSearchSavedServersAliases =>
      'servers profiles host url password switch add edit remove خوادم ملفات مضيف عنوان كلمة مرور تبديل إضافة تعديل إزالة profile connection';

  @override
  String get settingsHubSearchPhoneAliases =>
      'phone termux local on-device on device android install setup storage services هاتف محلي جهاز أندرويد تثبيت إعداد تخزين خدمات';

  @override
  String get settingsHubSearchAccountsAliases =>
      'account codex sign in login logout حساب تسجيل دخول خروج';

  @override
  String get settingsHubSearchExternalAgentsAliases =>
      'a2a external agents remote وكلاء خارجيون';

  @override
  String get settingsHubSearchTailscaleAliases =>
      'tailscale vpn network remote private شبكة خاصة بعيد';

  @override
  String get settingsHubSearchDisconnectAliases =>
      'disconnect leave server قطع الاتصال مغادرة خادم';

  @override
  String get settingsHubSearchModelModeAliases =>
      'model mode agent variant thinking default selected نموذج وضع وكيل تفكير افتراضي session chat conversation جلسة دردشة محادثة';

  @override
  String get settingsHubSearchShellAliases =>
      'shell terminal bash zsh command صدفة طرفية أوامر';

  @override
  String get settingsHubSearchPermissionsAliases =>
      'permissions approvals always allow allowed revoke أذونات موافقات سماح دائم إلغاء';

  @override
  String get settingsHubSearchTranscriptAliases =>
      'transcript display thinking reasoning timestamps usage سجل عرض تفكير استدلال طوابع زمنية استخدام';

  @override
  String get settingsHubSearchVoiceAliases =>
      'voice speech microphone dictation model صوت كلام ميكروفون إملاء نموذج';

  @override
  String get settingsHubSearchNotificationsAliases =>
      'notifications alerts quiet hours background check-in check in wi-fi wifi monitor saved servers finished runs approvals questions quota thresholds إشعارات تنبيهات ساعات الهدوء خلفية متابعة واي فاي مراقبة خوادم محفوظة العمليات المنتهية موافقات أسئلة حدود الحصص';

  @override
  String get settingsHubSearchAppearanceAliases =>
      'appearance theme dark light language arabic english colors مظهر سمة داكن فاتح لغة عربية إنجليزية ألوان';

  @override
  String get settingsHubSearchModelsAliases =>
      'models agents provider AI reasoning favorites recent نماذج وكلاء مزود ذكاء اصطناعي استدلال مفضلة';

  @override
  String get settingsHubSearchProvidersAliases =>
      'provider api key keys authentication connect مزود مفتاح مفاتيح مصادقة اتصال';

  @override
  String get settingsHubSearchMcpAliases =>
      'mcp integrations servers tools تكاملات خوادم أدوات';

  @override
  String get settingsHubSearchCommandsAliases =>
      'commands tools skills references slash capabilities أوامر أدوات مهارات مراجع إمكانات';

  @override
  String get settingsHubSearchPluginsAliases =>
      'plugins plugin installed source status AI Team Gas City إضافات مثبت مصدر حالة فريق';

  @override
  String get settingsHubSearchUsageAliases =>
      'usage cost tokens budget quota limit spent remaining threshold quota monitoring provider استخدام تكلفة رموز ميزانية حصة حد منفق متبقي مراقبة الحصص مزوّد';

  @override
  String get settingsHubSearchPrivacyAliases =>
      'privacy drafts queue queued prompts read state storage clear delete خصوصية مسودات قائمة انتظار حالة القراءة تخزين مسح حذف';

  @override
  String get settingsHubSearchGuideAliases =>
      'help guide connect tutorial start مساعدة دليل اتصال بدء';

  @override
  String get settingsHubSearchBugAliases =>
      'bug feedback issue support report خطأ ملاحظات مشكلة دعم بلاغ';

  @override
  String get settingsHubSearchDiagnosticsAliases =>
      'diagnostics debug errors log تشخيص أخطاء سجل';

  @override
  String get settingsHubSearchAboutAliases =>
      'about version licenses open source notices privacy data حول إصدار تراخيص مفتوح المصدر إشعارات خصوصية بيانات';

  @override
  String get pluginsSectionInApp => 'في هذا التطبيق';

  @override
  String get pluginsSectionOnServer => 'على الخادم';

  @override
  String get notifySectionWhat => 'ما الذي يُشعرني';

  @override
  String get notifyFinishedRuns => 'العمليات المنتهية';

  @override
  String get notifyFinishedRunsDetail =>
      'عند انتهاء عملية على الخادم المتصل أو فشلها.';

  @override
  String get notifyRequests => 'الموافقات والأسئلة';

  @override
  String get notifyRequestsDetail =>
      'عندما ينتظر الوكيل إجابتك، على أي خادم مُراقَب.';

  @override
  String get notifyQuotaAlerts => 'حدود الحصص';

  @override
  String get notifyQuotaAlertsDetail =>
      'عندما يتجاوز مزوّد مُراقَب الحد الذي ضبطته في الاستخدام. يسجّل التنبيه قراءة سابقة، لا المتبقي الآن.';

  @override
  String get notifyBlockedTitle => 'الإشعارات مُعطَّلة لهذا التطبيق';

  @override
  String get notifyBlockedMessage =>
      'لا يسمح نظام Android لتطبيق OpenCode بإشعارك، لذا لن تصل تنبيهات المهام المنتهية والموافقات وحدود الحصص إلى أن يُصلَح هذا.';

  @override
  String get notifySendTest => 'إرسال إشعار تجريبي';

  @override
  String get notifySendTestDetail =>
      'يتحقق مما إذا كان Android يُسلِّم إشعارات هذا التطبيق فعليًا الآن.';

  @override
  String get notifyQuietDetail =>
      'لا إشعارات خلال هذه الأوقات المحلية، لكل الخوادم ولتنبيهات الحصص. تستمر عمليات التحقق.';

  @override
  String get notifySectionBackground => 'الخلفية';

  @override
  String get notifySectionServers => 'الخوادم المحفوظة';

  @override
  String get notifyWifiOnly => 'التحقق في الخلفية عبر Wi-Fi فقط';

  @override
  String notifyHubBackgroundSummary(String state) {
    return 'الخلفية: $state';
  }

  @override
  String get usageSectionSpent => 'المصروف';

  @override
  String get usageSectionRemaining => 'المتبقي';

  @override
  String get shellTabWork => 'العمل';

  @override
  String get shellTabInbox => 'الوارد';

  @override
  String get shellTabProject => 'المشروع';

  @override
  String get serverSwitcherManage => 'إدارة الخوادم';

  @override
  String get serverSwitcherOpen => 'تبديل الخادم';

  @override
  String get discoverSearchGoTo => 'انتقل إلى';

  @override
  String discoverSearchIn(String parent) {
    return 'في $parent';
  }

  @override
  String get discoverWorkAliases =>
      'work home conversations sessions chats recent pinned العمل الرئيسية محادثات الأخيرة المثبتة محادثة جديدة';

  @override
  String get discoverInboxAliases =>
      'inbox activity approvals permissions questions forms الوارد النشاط بانتظارك موافقات أذونات أسئلة نماذج قيد التشغيل منتهية';

  @override
  String get discoverProjectAliases =>
      'project tools code folder المشروع أدوات الشيفرة مجلد ملفات تغييرات طرفية';

  @override
  String get discoverFilesAliases =>
      'files browse folder tree preview ملفات تصفح مجلد شجرة معاينة شيفرة';

  @override
  String get discoverChangesAliases =>
      'changes review diff git تغييرات مراجعة التغييرات فروقات غير مودعة تعديلات';

  @override
  String get discoverTerminalAliases =>
      'terminal shell console طرفية صدفة سطر الأوامر';

  @override
  String get discoverHealthAliases =>
      'project health branch lsp صحة المشروع فرع ملفات متغيرة خدمات اللغة منسقات';

  @override
  String get discoverWorktreesAliases =>
      'worktrees branches git أشجار العمل فروع معزولة';

  @override
  String get discoverSearchFilesAliases =>
      'search files find بحث في الملفات اعثر على ملف اسم';

  @override
  String get discoverAllConversationsAliases =>
      'all conversations sessions chats history كل المحادثات السجل كل المشاريع بحث';

  @override
  String get discoverTeamAliases =>
      'ai team agents runs فريق الذكاء الاصطناعي وكلاء عمليات بانتظارك';

  @override
  String get discoverNotifyServersTitle => 'إشعارات الخوادم المحفوظة';

  @override
  String get discoverNotifyWhatAliases =>
      'finished approvals questions check-in quota العمليات المنتهية موافقات أسئلة متابعة تنبيهات الحصص ما الذي ينبهني';

  @override
  String get discoverNotifyQuietAliases =>
      'quiet hours do not disturb mute ساعات الهدوء عدم الإزعاج ليل كتم جدول';

  @override
  String get discoverNotifyBackgroundAliases =>
      'background keep alive الخلفية البقاء متصلا خدمة';

  @override
  String get discoverNotifyServersAliases =>
      'monitor saved servers wi-fi wifi مراقبة الخوادم المحفوظة انتباه واي فاي فحص في الخلفية';

  @override
  String get discoverAppearanceModeAliases =>
      'light dark mode system فاتح داكن الوضع النظام ليلي';

  @override
  String get discoverLanguageAliases =>
      'language arabic english locale اللغة العربية الإنجليزية ترجمة';

  @override
  String get discoverThemeAliases => 'theme colors palette السمة ألوان لوحة';

  @override
  String get discoverSpentAliases =>
      'spent cost tokens usage المصروف التكلفة الرموز الاستخدام إحصاءات';

  @override
  String get discoverRemainingAliases =>
      'remaining quota limit provider المتبقي الحصة الحد المزود الخطة';

  @override
  String get discoverBudgetAliases =>
      'budget usd token ميزانية ميزانيات دولار رموز حد الإنفاق';

  @override
  String get discoverQuotaMonitorAliases =>
      'quota monitoring threshold alert مراقبة الحصص حد تنبيه منخفض';

  @override
  String get discoverCommandsAliases =>
      'commands slash أوامر أوامر الخادم تشغيل';

  @override
  String get discoverToolsAliases =>
      'tools capabilities inventory أدوات الأدوات والإمكانات';

  @override
  String get discoverSkillsAliases => 'skills skill مهارات مهارة تعليمات';

  @override
  String get discoverReferencesAliases =>
      'references docs sources مراجع مرجع مستندات مصادر سياق';

  @override
  String get discoverRunningNowAliases =>
      'running processes termux يعمل الآن عمليات خدمات إيقاف على هذا الهاتف';

  @override
  String get discoverStorageAliases =>
      'storage disk space termux التخزين على هذا الهاتف مساحة القرص تنظيف';

  @override
  String get discoverMonitorAliases =>
      'monitor attention انتباه الخوادم المحفوظة مراقبة خوادم أخرى بانتظارك';

  @override
  String get discoverConnectionHelpAliases =>
      'connection help troubleshooting مساعدة الاتصال تعذر الاتصال استكشاف الأخطاء الشبكة';

  @override
  String gestureEquivFileRowActions(String name) {
    return 'إجراءات $name';
  }

  @override
  String get gestureEquivShortcutFindMatch =>
      'التطابق التالي / السابق أثناء البحث في المحادثة';

  @override
  String get gestureEquivShortcutPromptHistory =>
      'الطلب الأقدم / الأحدث، والمؤشر في بداية مربع الرسالة أو نهايته';

  @override
  String get emptyTeachInboxMessage =>
      'لا شيء يحتاج إليك. تظهر هنا طلبات الموافقة والأسئلة من الأعمال الجارية.';

  @override
  String get emptyTeachWorkTitle => 'لا توجد محادثات بعد';

  @override
  String get emptyTeachWorkMessage =>
      'تظهر هنا المحادثات التي تبدؤها في هذا المشروع، وفي مقدمتها ما يحتاج إليك. ابدأ واحدة من «محادثة جديدة».';

  @override
  String get emptyTeachChangesTitle => 'لا توجد تغييرات بعد';

  @override
  String get emptyTeachChangesMessage =>
      'تظهر هنا التعديلات التي يجريها الوكيل لتراجعها.';

  @override
  String get emptyTeachWorktreesMessage =>
      'شجرة العمل نسخة منفصلة من هذا المشروع على فرع خاص بها، فلا تختلط الأعمال المتوازية. تظهر هنا أشجار عمل هذا المشروع.';

  @override
  String get emptyTeachAllowedMessage =>
      'عندما تختار «السماح دائمًا» في طلب موافقة ضمن هذا المشروع، يظهر هنا لتتمكن من التراجع عنه.';

  @override
  String get emptyTeachTeamRunsMessage =>
      'اكتب ما تحتاج إليه، فيقسّمه الفريق إلى خطوات ويعرض تقدّمه هنا.';

  @override
  String get emptyTeachSkillsMessage =>
      'المهارات تعليمات قابلة لإعادة الاستخدام يتبعها الوكيل. تظهر هنا مهارات هذا المشروع وهذا الخادم.';

  @override
  String get emptyTeachToolsMessage =>
      'تظهر هنا الأدوات التي يستطيع الوكيل استدعاءها مع هذا النموذج. لا أدوات لهذا النموذج.';

  @override
  String get capabilityScreenTitle => 'المتاح على هذا الخادم';

  @override
  String get capabilityScreenAliases =>
      'available supported missing feature capabilities المتاح مدعوم غير متاح ميزة مفقودة مخفية لماذا إمكانات الخادم';

  @override
  String capabilityScreenIntro(String server) {
    return 'يحدد $server ما يظهر في هذا التطبيق. ما لا يستطيعه يُحذف من القوائم وعلامات التبويب بدل عرضه معطلا.';
  }

  @override
  String get capabilityAllAvailable => 'يدعم هذا الخادم كل ما يقدمه التطبيق.';

  @override
  String get capabilityFiles => 'الملفات';

  @override
  String get capabilityFilesDetail => 'تصفح ملفات المشروع وابحث فيها وعاينها';

  @override
  String get capabilityChanges => 'التغييرات';

  @override
  String get capabilityChangesDetail => 'راجع ما عدّله الوكيل';

  @override
  String get capabilityTerminal => 'الطرفية';

  @override
  String get capabilityTerminalDetail => 'شغّل أوامر داخل المشروع';

  @override
  String get capabilityShell => 'الصدفة الافتراضية';

  @override
  String get capabilityShellDetail =>
      'اختر الصدفة التي تستخدمها الأوامر والطرفيات';

  @override
  String get capabilityAttachments => 'المرفقات';

  @override
  String get capabilityAttachmentsDetail => 'أرسل ملفات وصورا مع الطلب';

  @override
  String get capabilitySubagents => 'التفويض إلى وكيل فرعي';

  @override
  String get capabilitySubagentsDetail => 'اذكر وكيلا بعلامة @ في الطلب';

  @override
  String get capabilityCompact => 'الضغط';

  @override
  String get capabilityCompactDetail => 'لخّص محادثة طويلة لتحرير السياق';

  @override
  String get capabilityShare => 'المشاركة';

  @override
  String get capabilityShareDetail => 'انشر رابطا لمحادثة';

  @override
  String get capabilityFork => 'التفريع';

  @override
  String get capabilityForkDetail => 'فرّع محادثة من رسالة سابقة';

  @override
  String get capabilityRevert => 'التراجع';

  @override
  String get capabilityRevertDetail => 'تراجع عن طلب وعن التعديلات التي أجراها';

  @override
  String get capabilityArchive => 'الأرشفة';

  @override
  String get capabilityArchiveDetail => 'ضع المحادثات المنتهية جانبا دون حذفها';

  @override
  String get capabilityTodos => 'المهام';

  @override
  String get capabilityTodosDetail => 'اطلع على قائمة مهام الوكيل للمحادثة';

  @override
  String get capabilityNotes => 'ملاحظة للوكيل';

  @override
  String get capabilityNotesDetail => 'احتفظ بتعليمات دائمة مع المحادثة';

  @override
  String get capabilityImportExport => 'الاستيراد والتصدير';

  @override
  String get capabilityImportExportDetail => 'انقل محادثة بين الخوادم في ملف';

  @override
  String get capabilitySearchAll => 'كل المحادثات';

  @override
  String get capabilitySearchAllDetail => 'ابحث في المحادثات عبر كل المشاريع';

  @override
  String get capabilityAlwaysAllow => 'الإجراءات المسموح بها دائما';

  @override
  String get capabilityAlwaysAllowDetail =>
      'تذكّر موافقة حتى لا يُسأل عنها مجددا';

  @override
  String get capabilityModels => 'النماذج والمزودون';

  @override
  String get capabilityModelsDetail =>
      'تصفح النماذج وسجّل الدخول إلى المزودين من التطبيق';

  @override
  String get capabilitySkills => 'المهارات والأوامر';

  @override
  String get capabilitySkillsDetail => 'اعرض مهارات الخادم وأوامره ومراجعه';

  @override
  String get capabilityMcp => 'MCP';

  @override
  String get capabilityMcpDetail => 'اعرض خوادم MCP واتصل بها';

  @override
  String get capabilityPlugins => 'الإضافات';

  @override
  String get capabilityPluginsDetail => 'اعرض الإضافات المثبتة على الخادم';

  @override
  String get capabilityCloud => 'البيئات السحابية';

  @override
  String get capabilityCloudDetail => 'شغّل مشروعا في بيئة مُدارة';

  @override
  String get capabilityProjects => 'المشاريع';

  @override
  String get capabilityProjectsDetail => 'بدّل بين المشاريع وافحص صحة المشروع';

  @override
  String get capabilityWorktrees => 'أشجار العمل';

  @override
  String get capabilityWorktreesDetail => 'امنح المهمة فرعا معزولا خاصا بها';

  @override
  String get capabilityUsage => 'الاستخدام';

  @override
  String get capabilityUsageDetail => 'اطلع على تكلفة المحادثات';

  @override
  String get capabilityOfflineQueue => 'الإرسال لاحقا';

  @override
  String get capabilityOfflineQueueDetail =>
      'ضع الطلب في قائمة الانتظار دون اتصال وأرسله عند عودة الاتصال';

  @override
  String get capabilityContinueOnComputer => 'المتابعة على الحاسوب';

  @override
  String get capabilityContinueOnComputerDetail =>
      'احصل على أمر يعيد فتح المحادثة على مكتبك';

  @override
  String get capabilityServerUpdates => 'تحديثات الخادم';

  @override
  String get capabilityServerUpdatesDetail => 'حدّث الخادم من التطبيق';

  @override
  String get capabilityBackgroundNotifications => 'الإشعارات في الخلفية';

  @override
  String get capabilityBackgroundNotificationsDetail =>
      'تلقَّ إشعارا عند انتهاء العمل أو حاجته إليك والتطبيق مغلق';

  @override
  String get capabilityOnThisPhone => 'على هذا الهاتف';

  @override
  String get capabilityOnThisPhoneDetail => 'شغّل خادم الوكيل على هذا الجهاز';

  @override
  String get capabilityVoice => 'الصوت';

  @override
  String get capabilityVoiceDetail =>
      'أملِ الطلبات بنماذج كلام تعمل على الجهاز';

  @override
  String get discoverShowTipsAgain => 'إظهار التلميحات مجددا';

  @override
  String get discoverShowTipsSubtitle =>
      'ستظهر التلميحات التي تُعرض مرة واحدة من جديد في وقتها';

  @override
  String get discoverShowTipsAliases =>
      'tips hints nudges تلميحات نصائح مساعدة إعادة تعيين إظهار مجددا';

  @override
  String get discoverShowTipsDone => 'ستظهر التلميحات مجددا.';

  @override
  String nudgeApprovals(String action) {
    return 'طُلب «$action» 3 مرات: يمكن لهذه المحادثة الموافقة على الطلبات نيابةً عنك.';
  }

  @override
  String get nudgeReviewChanges =>
      'غيّر هذا التشغيل ملفات: راجع ما تغيّر قبل أن تتابع.';

  @override
  String get nudgeLeave =>
      'يمكنك المغادرة: سيخبرك هذا الهاتف عند انتهاء التشغيل.';

  @override
  String nudgeCompact(String percent) {
    return 'السياق ممتلئ بنسبة $percent%: اختصره لتتابع.';
  }

  @override
  String get nudgePin =>
      'ثبّت المحادثات التي تعود إليها من قائمتها؛ ستبقى في أعلى العمل.';

  @override
  String get nudgeDismiss => 'إخفاء التلميح';

  @override
  String get firstRunWhereQuestion => 'أين يعمل وكيل البرمجة لديك؟';

  @override
  String get firstRunOnComputer => 'على حاسوبي';

  @override
  String get firstRunOnComputerDetail => 'اتصل بوكيل يعمل هناك.';

  @override
  String get firstRunOnPhoneDetail => 'جهّز وكيلًا هنا. لا تحتاج إلى حاسوب.';

  @override
  String get firstRunJustShowMe => 'أرني فقط';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => 'على حاسوبك، شغّل:';

  @override
  String get firstRunPairingNextScan =>
      'سيعرض رمزًا. امسحه، أو انسخه والصقه هنا.';

  @override
  String get firstRunPairingNextPaste => 'ثم الصق الرمز الذي يعرضه.';

  @override
  String get firstRunNotSameNetwork => 'لست على الشبكة نفسها؟';

  @override
  String get firstRunShowCommands => 'عرض الأوامر';

  @override
  String get firstRunCommandsPaseoNetwork =>
      'للوصول إليه من هذا الهاتف عبر شبكتك الخاصة، استمع على ذلك العنوان وعيّن كلمة مرور:';

  @override
  String get firstRunCommandsCodexToken => 'أنشئ رمز الاتصال قبل تشغيله:';

  @override
  String get firstRunCommandsCodexUsb => 'يصل إليه هاتف موصول بكابل USB عبر:';

  @override
  String get firstRunNotifyTitle => 'هل نُعلمك عندما يجهز الرد؟';

  @override
  String get firstRunNotifyBody =>
      'غادر التطبيق بينما يعمل الوكيل. يصلك إشعار عندما ينتهي أو يحتاج إليك. يعرض Android إشعارًا صغيرًا دائمًا ما دام الاتصال قائمًا.';

  @override
  String get firstRunNotifyAccept => 'أشعِرني';

  @override
  String get firstRunNotifyDecline => 'ليس الآن';

  @override
  String get localAgentTitle => 'Claude Code على هذا الهاتف';

  @override
  String get localAgentOfferBody =>
      'شغّل Claude Code هنا من دون حاسوب. يثبّت التطبيق Node.js وخدمة Paseo وClaude Code داخل Ubuntu الذي يديره أصلًا، ويصل إليها من هذا الهاتف فقط.';

  @override
  String get localAgentOfferSize =>
      'نحو 60 م.ب لـ Node.js إضافةً إلى الحزم؛ ونحو 1 غ.ب بعد التثبيت. يلزم 2 غ.ب خالية.';

  @override
  String get localAgentOfferWarning =>
      'قد يوقف أندرويد Termux في الخلفية. إعدادات البطارية التي تُبقي خادم OpenCode يعمل تُبقي هذا يعمل أيضًا.';

  @override
  String get localAgentSetUp => 'إعداد Claude Code';

  @override
  String get localAgentNotNow => 'ليس الآن';

  @override
  String get localAgentNeedsUbuntuBody =>
      'يعمل Claude Code داخل Ubuntu الذي يعدّه هذا التطبيق. أكمل إعداد «على هذا الهاتف» أولًا ثم عد إلى هنا.';

  @override
  String get localAgentOpenSetup => 'فتح إعداد الهاتف';

  @override
  String get localAgentNeedsTermuxBody =>
      'يحتاج Claude Code إلى Termux حاليًا؛ لينكس المدمج في التطبيق لا يشغّله بعد. أعدّ Termux لاستخدامه هنا.';

  @override
  String get localAgentSetUpWithTermux => 'الإعداد عبر Termux';

  @override
  String get localAgentStepNode => 'Node.js';

  @override
  String get localAgentStepPaseo => 'خدمة Paseo';

  @override
  String get localAgentStepClaude => 'Claude Code';

  @override
  String get localAgentStepSignIn => 'تسجيل الدخول إلى Claude';

  @override
  String get localAgentStepStart => 'التشغيل على هذا الهاتف';

  @override
  String get localAgentInstalling => 'جارٍ إعداد Claude Code';

  @override
  String get localAgentLeaveNote =>
      'يمكنك مغادرة هذه الشاشة. يستمر التثبيت في Termux.';

  @override
  String get localAgentSignInBody =>
      'يفتح Termux ويعرض Claude Code رابطًا. وافق عليه في متصفحك، ثم الصق الرمز في Termux وعد إلى هنا. لا يرى هذا التطبيق تسجيل دخولك إلى Claude ولا يحفظه.';

  @override
  String get localAgentSignInAlready => 'سجّلت الدخول بالفعل';

  @override
  String localAgentSignInOpenFailed(String command) {
    return 'تعذّر فتح Termux. افتح Termux بنفسك وشغّل: $command';
  }

  @override
  String get localAgentSignInMissing =>
      'لم يُعثر على تسجيل دخول إلى Claude بعد.';

  @override
  String get localAgentReadyTitle => 'يعمل Claude Code على هذا الهاتف';

  @override
  String get localAgentReadyBody =>
      'يستمع على هذا الهاتف فقط (127.0.0.1)، خلف كلمة مرور يحتفظ بها هذا التطبيق.';

  @override
  String localAgentVersions(String claude, String paseo, String node) {
    return 'Claude Code $claude · Paseo $paseo · Node.js $node';
  }

  @override
  String get localAgentInstalledTitle => 'Claude Code مثبّت ومتوقف';

  @override
  String get localAgentKilled =>
      'أوقف أندرويد Claude Code أثناء غياب التطبيق. لم يُفقد شيء.';

  @override
  String get localAgentFailedTitle => 'توقف إعداد Claude Code';

  @override
  String localAgentFailedNoSpace(String detail) {
    return 'لا توجد مساحة كافية على هذا الهاتف. $detail أفرغ بعض المساحة ثم حاول مجددًا.';
  }

  @override
  String get localAgentFailedDownload =>
      'تعذّر تنزيل Node.js. تحقق من الشبكة ثم حاول مجددًا.';

  @override
  String get localAgentFailedChecksum =>
      'لم يطابق تنزيل Node.js بصمته المثبّتة، فلم يُثبَّت شيء. حاول مجددًا؛ وإن تكرر ذلك فهناك ما يغيّر الملف على الشبكة.';

  @override
  String get localAgentFailedNativeBuild =>
      'تحتاج إحدى الحزم إلى وحدة أصلية لا يتوفر لها بناء جاهز لهذا الهاتف. لم يُبنَ شيء؛ والمخرجات أدناه تسمّيها.';

  @override
  String get localAgentFailedPackages =>
      'تعذّر تثبيت الحزم. تحقق من الشبكة ثم حاول مجددًا.';

  @override
  String get localAgentFailedPortInUse =>
      'المنفذ 6767 على هذا الهاتف يستخدمه برنامج آخر. أوقف ذلك البرنامج ثم حاول مجددًا.';

  @override
  String get localAgentFailedTimeout =>
      'لم يستجب Claude Code خلال دقيقتين. تعرض المخرجات أدناه ما طبعه.';

  @override
  String get localAgentFailedInterrupted =>
      'أوقف أندرويد الخطوة قبل اكتمالها. حاول مجددًا؛ وستُستكمل من حيث توقفت.';

  @override
  String get localAgentFailedUnsupported => 'يحتاج Claude Code إلى هاتف 64 بت.';

  @override
  String get localAgentFailedDaemon =>
      'توقف Claude Code أو لا يستجيب. شغّله من جديد.';

  @override
  String localAgentFailedReason(String detail) {
    return 'توقف: $detail';
  }

  @override
  String get localAgentProjectTitle => 'اختر مجلد مشروع';

  @override
  String get localAgentProjectBody =>
      'يعمل Claude Code داخل مجلد واحد في Ubuntu على هذا الهاتف.';

  @override
  String get localAgentProjectPathLabel => 'أو اكتب مسارًا داخل Ubuntu';

  @override
  String get localAgentProjectPathInvalid =>
      'أدخل مسارًا كاملًا، مثل ‎/root/projects/my-app.';

  @override
  String localAgentConnectFailed(String detail) {
    return 'تعذّر الاتصال بـ Claude Code على هذا الهاتف. $detail';
  }

  @override
  String localAgentCardActionFailed(String detail) {
    return 'لم ينجح ذلك. $detail';
  }

  @override
  String get localAgentRestartTitle =>
      'إعادة تشغيل Claude Code على هذا الهاتف؟';

  @override
  String get localAgentRestartBody =>
      'يتوقف Claude Code لحظات ويُقاطَع ما يفعله الآن. تبقى محادثاتك على الهاتف.';

  @override
  String get localAgentStopTitle => 'إيقاف Claude Code على هذا الهاتف؟';

  @override
  String get localAgentStopBody =>
      'يُقاطَع كل ما يفعله Claude Code على هذا الهاتف، وينقطع اتصال هذا التطبيق به. تبقى مشاريعك ومحادثاتك وتسجيل دخولك إلى Claude؛ شغّله من جديد للمتابعة.';

  @override
  String get localAgentRemove => 'إزالة من هذا الهاتف';

  @override
  String get localAgentRemoveTitle => 'إزالة Claude Code من هذا الهاتف؟';

  @override
  String get localAgentRemoveBody =>
      'يوقفه ويحذف Node.js وPaseo وClaude Code من Ubuntu، نحو 1 غ.ب. تبقى مشاريعك وتسجيل دخولك إلى Claude.';

  @override
  String get localAgentRemoveKeep => 'إبقاؤه';

  @override
  String get localAgentMore => 'إجراءات أخرى لـ Claude Code';

  @override
  String get firstRunAgentsSideBySide =>
      'تعمل جنبًا إلى جنب على الحاسوب نفسه. ابدأ بواحد، وأضف البقية في أي وقت من اسم الخادم في الأعلى.';

  @override
  String get firstRunPaseoTitle => 'الوكلاء عبر Paseo';

  @override
  String get approvalsUiEverythingTitle => 'الموافقة على كل شيء في هذا الخادم';

  @override
  String get approvalsUiEverythingDetail =>
      'خطِر. تتم الموافقة تلقائيًا على كل محادثة في هذا الخادم، بما فيها المحادثات الجديدة والوكلاء الفرعيون، ما دام التطبيق متصلًا. المحادثة المضبوطة على «اسأل كل مرة» تبقى تسأل.';

  @override
  String get approvalsUiEverythingConfirmBody =>
      'سينفّذ الوكلاء في هذا الخادم الأوامر ويغيّرون الملفات دون سؤالك، في كل محادثة. فعّل هذا فقط لخادم ومشاريع تتحمّل تعطّلها.';

  @override
  String get approvalsUiEverythingActive =>
      'تتبع إعداد «الموافقة على كل شيء في هذا الخادم»';

  @override
  String get approvalsUiServerRulesNoteEverything =>
      'تبقى قواعد الرفض في الخادم سارية، وتتوقف الموافقة التلقائية عند انقطاع اتصال التطبيق.';

  @override
  String get agentErrorConnectionDropped => 'انقطع الاتصال بالنموذج.';

  @override
  String get agentErrorConnectionDroppedHint =>
      'غالبًا ما يكون ذلك مؤقتًا، ويعيد OpenCode المحاولة تلقائيًا. إذا تكرر، فتحقق من الإنترنت على الجهاز الذي يشغّل OpenCode.';

  @override
  String get agentErrorTimedOut => 'استغرق النموذج وقتًا طويلًا للرد.';

  @override
  String get agentErrorTimedOutHint =>
      'يعيد OpenCode المحاولة تلقائيًا. قد يكون طلب أصغر أو نموذج آخر أسرع.';

  @override
  String get agentErrorProviderUnreachable =>
      'تعذّر على الخادم الوصول إلى مزوّد النموذج.';

  @override
  String get agentErrorProviderUnreachableHint =>
      'تحقق من اتصال الإنترنت على الجهاز الذي يشغّل OpenCode ثم حاول مجددًا.';

  @override
  String get agentErrorRequestTooLarge =>
      'كان هذا الطلب أكبر من أن يُرسل إلى النموذج.';

  @override
  String get agentErrorRequestTooLargeHint =>
      'أرسل مرفقات أقل أو أصغر، أو اضغط المحادثة، ثم حاول مجددًا.';

  @override
  String get agentErrorRateLimited => 'يحدّ مزوّد النموذج من سرعة الإرسال.';

  @override
  String get agentErrorRateLimitedHint =>
      'تُعاد المحاولة تلقائيًا بعد انتظار قصير.';

  @override
  String get agentErrorProviderBusy => 'مزوّد النموذج مثقل بالطلبات الآن.';

  @override
  String get agentErrorProviderBusyHint =>
      'تُعاد المحاولة تلقائيًا. قد يرد نموذج آخر أسرع.';

  @override
  String get agentErrorOutOfCredit => 'نفد رصيد حساب المزوّد أو حصته.';

  @override
  String get agentErrorOutOfCreditHint =>
      'اشحن الحساب، أو انتقل إلى مزوّد أو نموذج آخر.';

  @override
  String get agentErrorServerDiskFull =>
      'نفدت مساحة القرص على الجهاز الذي يشغّل OpenCode.';

  @override
  String get agentErrorServerDiskFullHint =>
      'حرّر بعض المساحة هناك ثم حاول مجددًا.';

  @override
  String get agentErrorRecovered => 'تابع الوكيل عمله بعد ذلك.';

  @override
  String chatUiRetryingSoon(String attempt) {
    return 'إعادة المحاولة$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return 'إعادة المحاولة$attempt خلال $time';
  }

  @override
  String get chatUiCompactAgain => 'اضغط المحادثة مجددًا';

  @override
  String get chatUiCompactionFailedHint =>
      'ما زالت المحادثة أطول مما يسمح به النموذج، لذا ستُعاد المحاولة في الدور التالي.';

  @override
  String get chatStripContextPending => 'سياق قيد الانتظار';

  @override
  String get chatStripAutoApprove => 'موافقة تلقائية';

  @override
  String get chatStripAutoApprovePaused => 'الموافقة التلقائية متوقفة';

  @override
  String get chatStripBackground => 'في الخلفية';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return 'محتويات $name، قُرئت على الهاتف: قيم ونص فقط، دون تنسيق أو مخططات أو صيغ.';
  }

  @override
  String get chatAttachmentOfficeTruncated =>
      'الملف أكبر مما يتسع له الطلب، لذا اقتُطع أدناه.';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return 'تعذّرت قراءة $name. قد يكون محميًا بكلمة مرور أو تالفًا أو بصيغة Excel أو Word القديمة؛ احفظه بصيغة ‎.xlsx أو ‎.docx أو CSV ثم حاول مجددًا.';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return 'لا يحتوي $name على ما يمكن إرفاقه.';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return 'أُرفق $name كنص.';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets ورقة',
      few: '$sheets أوراق',
      two: 'ورقتان',
      one: 'ورقة واحدة',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows صفًا',
      few: '$rows صفوف',
      two: 'صفان',
      one: 'صف واحد',
    );
    return 'أُرفق $name كنص · $_temp0، $_temp1';
  }

  @override
  String get otherProjectsForget => 'إزالة من المشاريع الأخيرة';

  @override
  String get otherProjectsUntitled => 'محادثة بلا عنوان';

  @override
  String get localAgentUpdate => 'تحديث Claude Code';

  @override
  String get localAgentUpdateAvailable =>
      'يضيف التحديث أحدث نماذج Claude، مثل Opus 5.5.';

  @override
  String get localAgentUpdateNow => 'تحديث';

  @override
  String get modelNewBadge => 'جديد';

  @override
  String get modelEffortNone => 'بلا تفكير';

  @override
  String get modelEffortMinimal => 'أدنى';

  @override
  String get modelEffortLow => 'منخفض';

  @override
  String get modelEffortMedium => 'متوسط';

  @override
  String get modelEffortHigh => 'مرتفع';

  @override
  String get modelEffortExtraHigh => 'مرتفع جدًا';

  @override
  String get modelEffortMax => 'أقصى';

  @override
  String get setupStoppedByRestart =>
      'أُعيد تشغيل الهاتف فتوقف الخادم المحلي. شغّله مجددًا عند الحاجة.';

  @override
  String get phoneServerStoppedTitle => 'الخادم على هذا الهاتف متوقف';

  @override
  String get phoneServerStoppedBody =>
      'يتوقف عند إعادة تشغيل الهاتف أو عندما يغلق Android تطبيق Termux لتوفير البطارية. محادثاتك محفوظة؛ شغّله مجددًا للمتابعة.';

  @override
  String get phoneServerStartAndConnect => 'تشغيل واتصال';

  @override
  String get otherServersTitle => 'على خوادمك الأخرى';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count قيد العمل',
      one: 'محادثة قيد العمل',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return 'اسأل $agent…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض $countString سطرًا سابقًا',
      one: 'عرض سطر سابق',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'فتح كل الأسطر ($countString)';
  }

  @override
  String get setupProgressViewOverallLabel => 'تقدّم الإعداد';

  @override
  String get setupProgressViewGettingStarted => 'جارٍ البدء…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'بقيت $minutes دقيقة تقريبًا',
      few: 'بقيت $minutes دقائق تقريبًا',
      two: 'بقيت دقيقتان تقريبًا',
      one: 'بقيت دقيقة تقريبًا',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => 'أقل من دقيقة';

  @override
  String get setupProgressViewDone => 'اكتمل كل شيء';

  @override
  String get setupProgressViewFailedTitle => 'لم يكتمل الإعداد';

  @override
  String get setupProgressViewInterrupted => 'توقّف الإعداد. ما اكتمل محفوظ.';

  @override
  String get setupProgressViewCancelled =>
      'أُوقف الإعداد. ما اكتمل يبقى مثبّتًا.';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done من $total';
  }

  @override
  String setupProgressViewPercent(int percent) {
    return '$percent٪';
  }

  @override
  String setupProgressViewStageMeasured(String stage, String measured) {
    return '$stage · $measured';
  }

  @override
  String get setupProgressViewChecking => 'جارٍ الفحص';

  @override
  String get setupProgressViewStarting => 'جارٍ البدء';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return 'توقّف أثناء: $stage';
  }

  @override
  String get setupProgressViewFailedUnknown =>
      'حدث خطأ. اعرض التفاصيل لرؤية السجل.';

  @override
  String get setupProgressViewNoInternet =>
      'لا يوجد اتصال بالإنترنت — تابع عند عودة الاتصال';

  @override
  String get setupProgressViewContinue => 'متابعة الإعداد';

  @override
  String get setupProgressViewCancel => 'إلغاء';

  @override
  String get setupProgressViewNoLog => 'لا يوجد سجل بعد.';

  @override
  String get phoneSetupProgressTitle => 'جارٍ إعداد OpenCode على هذا الهاتف';

  @override
  String get phoneSetupProgressLeaveHint =>
      'يمكنك مغادرة التطبيق. سنُعلمك عندما يصبح جاهزًا.';

  @override
  String get phoneSetupProgressStopTitle => 'إيقاف الإعداد؟';

  @override
  String get phoneSetupProgressStopMessage => 'ما اكتمل يبقى مثبّتًا.';

  @override
  String get phoneSetupProgressStopConfirm => 'إيقاف الإعداد';

  @override
  String get phoneSetupProgressKeepGoing => 'متابعة';

  @override
  String get builtinServerChooseRuntime => 'أي إصدار من OpenCode';

  @override
  String get builtinServerStopped => 'متوقف.';

  @override
  String builtinServerStartFailed(String reason) {
    return 'لم يستجب OpenCode: $reason. افتح السجل لمعرفة السبب.';
  }

  @override
  String get builtinServerExited => 'توقف الخادم';

  @override
  String builtinServerTimedOut(int seconds) {
    return 'لا استجابة خلال $seconds ثانية';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return 'تعذّر الاتصال: $reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return 'هذا الهاتف، مدمج ($runtime)';
  }

  @override
  String get phoneSetupProfileName => 'هذا الهاتف';

  @override
  String get phoneSetupLinuxTitle => 'قاعدة لينكس';

  @override
  String get phoneSetupLinuxWhy => 'كل ما عداها يعمل داخلها.';

  @override
  String get phoneSetupEssentialsTitle => 'Git وSSH والشهادات';

  @override
  String get phoneSetupEssentialsShort => 'Git وSSH';

  @override
  String get phoneSetupEssentialsWhy =>
      'يستخدم الوكلاء Git وSSH للعمل على مشاريعك.';

  @override
  String get phoneSetupNodeWhy => 'يعمل OpenCode على Node.js.';

  @override
  String get phoneSetupOpenCodeWhy => 'وكيل البرمجة نفسه.';

  @override
  String get phoneSetupStartTitle => 'تشغيل OpenCode';

  @override
  String get phoneSetupStartWhy => 'ينتهي الإعداد وOpenCode يعمل.';

  @override
  String get phoneSetupStageDownloadingLinux => 'جارٍ تنزيل قاعدة لينكس';

  @override
  String get phoneSetupStageUnpackingLinux => 'جارٍ فك قاعدة لينكس';

  @override
  String get phoneSetupStageStarting => 'جارٍ تشغيل OpenCode';

  @override
  String get phoneSetupNotificationChannel => 'إعداد الهاتف';

  @override
  String get phoneSetupNotificationTitle =>
      'جارٍ إعداد OpenCode على هذا الهاتف';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return 'اكتمل $percent٪';
  }

  @override
  String get phoneSetupNotificationDone => 'OpenCode جاهز على هذا الهاتف';

  @override
  String get phoneSetupNotificationStopped =>
      'توقف الإعداد. افتح التطبيق للمتابعة.';

  @override
  String get phoneSetupErrorNoInternet =>
      'لا يوجد اتصال بالإنترنت. تابع عندما يعود الاتصال.';

  @override
  String phoneSetupErrorOffline(String name) {
    return 'تعذّر تنزيل $name: لا يوجد اتصال بالإنترنت';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return 'تعذّر تثبيت $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return 'تلف تنزيل $name. تابع لتنزيله من جديد.';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'OpenCode was downloaded, but its program was not in the download. Continue to fetch it again.';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'OpenCode was downloaded, but its program does not run on this phone. Details show what it said.';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'OpenCode was installed, but it did not start. Continue to try again; Details show what it said.';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return 'لا توجد مساحة كافية لتثبيت $name';
  }

  @override
  String phoneSetupErrorStart(String reason) {
    return 'تعذّر تشغيل OpenCode: $reason';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'تم تثبيت OpenCode، لكن التطبيق لم يتمكن من تشغيله هنا.';

  @override
  String get builtinServerLogTitle => 'سجل الخادم';

  @override
  String get phoneSetupReadyTitle => 'OpenCode جاهز';

  @override
  String get phoneSetupReadyNameTitle => 'سمِّ مشروعك الأول';

  @override
  String get phoneSetupReadyNameLabel => 'اسم المشروع';

  @override
  String get phoneSetupReadyNameHelp => 'أحرف لاتينية وأرقام و - _ .';

  @override
  String get phoneSetupReadyCreating => 'جارٍ إنشاء المشروع';

  @override
  String get phoneSetupReadyCreateOpen => 'إنشاء وفتح';

  @override
  String get phoneSetupReadyOpenFolderInstead => 'فتح مجلد بدلًا من ذلك';

  @override
  String get phoneSetupReadyNameEmpty => 'اكتب اسمًا.';

  @override
  String get phoneSetupReadyNameOneFolder =>
      'استخدم اسمًا واحدًا بلا شرطات مائلة.';

  @override
  String get phoneSetupReadyNameInvalid =>
      'استخدم أحرفًا لاتينية وأرقامًا و - _ . وابدأ بحرف أو رقم (حتى 64).';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return 'تعذّر إنشاء المشروع: $reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return 'تعذّر فتح المشروع: $reason';
  }

  @override
  String get phoneServerCardTitle => 'هذا الهاتف';

  @override
  String get phoneServerCardRunning => 'يعمل';

  @override
  String get phoneServerCardStopped => 'متوقف';

  @override
  String get phoneServerCardStarting => 'جارٍ التشغيل';

  @override
  String get phoneServerCardStopping => 'جارٍ الإيقاف';

  @override
  String get phoneServerCardRemoving => 'جارٍ الإزالة';

  @override
  String get phoneServerCardChecking => 'جارٍ التحقق';

  @override
  String get phoneServerCardNotSetUp => 'غير مُعَدّ';

  @override
  String get phoneServerCardSettingUp => 'جارٍ الإعداد';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => 'إعداد';

  @override
  String get phoneServerCardShowProgress => 'عرض التقدم';

  @override
  String get phoneServerCardContinueSetup => 'متابعة الإعداد';

  @override
  String get phoneServerCardLogTitle => 'السجل';

  @override
  String get phoneServerCardLogEmpty => 'لا شيء في السجل بعد.';

  @override
  String get phoneServerCardMore => 'المزيد';

  @override
  String phoneServerCardSwitchTo(String runtime) {
    return 'التبديل إلى $runtime';
  }

  @override
  String get phoneServerCardAddTools => 'إضافة أدوات (Python وAI Team…)';

  @override
  String get phoneServerCardUpdate => 'تحديث OpenCode';

  @override
  String get phoneServerCardRemove => 'إزالة من هذا الهاتف…';

  @override
  String get phoneServerCardRemoveTitle => 'إزالة OpenCode من هذا الهاتف؟';

  @override
  String phoneServerCardActionFailed(String reason) {
    return 'لم ينجح ذلك: $reason';
  }

  @override
  String get serverEditorMoreOptions => 'خيارات أخرى';

  @override
  String get inAppServerStoppedTitle => 'OpenCode داخل التطبيق متوقف';

  @override
  String get inAppServerStoppedBody =>
      'يتوقف عند إغلاق التطبيق لفترة أو تحديثه. محادثاتك محفوظة؛ شغّله مجددًا للمتابعة.';

  @override
  String get inAppServerNotRespondingTitle => 'OpenCode داخل التطبيق لا يستجيب';

  @override
  String get inAppServerNotRespondingBody =>
      'إعادة تشغيله تحل هذا عادةً. محادثاتك محفوظة.';

  @override
  String get inAppServerStartFailedTitle => 'لم يبدأ OpenCode داخل التطبيق';

  @override
  String get inAppServerStartFailedBody =>
      'افتح الإعداد لترى سجل الخادم، أو حاول تشغيله مرة أخرى.';

  @override
  String get inAppServerStarting => 'جارٍ تشغيل OpenCode داخل التطبيق…';

  @override
  String get inAppServerStartingBody => 'يستغرق هذا بضع ثوانٍ.';

  @override
  String get inAppServerOpenSetup => 'فتح الإعداد';

  @override
  String get projectFolderInAppTitle => 'فتح مشروع';

  @override
  String get projectFolderNewProject => 'مشروع جديد';

  @override
  String get projectFolderProjectNameLabel => 'اسم المشروع';

  @override
  String projectFolderNewProjectHelp(String directory) {
    return 'ينشئ التطبيق المجلد في $directory ويفتحه.';
  }

  @override
  String get projectFolderEnterPath => 'إدخال مسار';

  @override
  String get projectFolderMissing => 'هذا المجلد غير موجود بعد.';

  @override
  String get projectFolderCreateIt => 'إنشاؤه';

  @override
  String projectFolderCreateFailed(String reason) {
    return 'تعذّر إنشاء المجلد: $reason';
  }

  @override
  String projectFolderCheckFailed(String reason) {
    return 'تعذّر التحقق من المجلد: $reason';
  }

  @override
  String get folderBrowserUp => 'إلى المجلد الأعلى';

  @override
  String folderBrowserCurrent(String path) {
    return 'المجلد الحالي: $path';
  }

  @override
  String folderBrowserOpen(String name) {
    return 'فتح $name';
  }

  @override
  String get folderBrowserGit => 'مستودع Git';

  @override
  String get folderBrowserProject => 'مشروع OpenCode';

  @override
  String folderBrowserShowInside(String name) {
    return 'عرض المجلدات داخل $name';
  }

  @override
  String get folderBrowserProjectsHere =>
      'مشاريعك هنا. اضغط على مشروع لفتحه، أو ابدأ مشروعًا جديدًا.';

  @override
  String get folderBrowserHomeHere =>
      'لا يمكن أن يكون المجلد الرئيسي أو الجذر مشروعًا. افتح مجلدًا بداخله.';

  @override
  String get folderBrowserNoProjectsTitle => 'لا توجد مشاريع بعد';

  @override
  String get folderBrowserNoProjectsBody => 'ابدأ مشروعًا لإنشائه هنا.';

  @override
  String get folderBrowserEmptyTitle => 'لا توجد مجلدات هنا';

  @override
  String get folderBrowserEmptyBody =>
      'أنشئ فيه مشروعًا جديدًا بالأسفل، أو انتقل إلى المجلد الأعلى.';

  @override
  String get folderBrowserErrorTitle => 'تعذّر عرض هذا المجلد';

  @override
  String get folderBrowserErrorNotInstalled =>
      'لم يُثبَّت Ubuntu في التطبيق بعد.';

  @override
  String get folderBrowserErrorMissing => 'لم يعد موجودًا.';

  @override
  String get folderBrowserErrorDenied => 'لا يُسمح للتطبيق بقراءته.';

  @override
  String get folderBrowserErrorLinked => 'هذا رابط. أدخل مساره بدلًا من ذلك.';

  @override
  String get folderBrowserErrorFailed =>
      'حاول مرة أخرى، أو أدخل مساره بدلًا من ذلك.';

  @override
  String get folderBrowserErrorTimedOut =>
      'استغرق الرد وقتًا طويلًا. حاول مرة أخرى.';

  @override
  String get folderBrowserRetry => 'إعادة المحاولة';

  @override
  String get phoneSetupStartScreenTitle => 'على هذا الهاتف';

  @override
  String get phoneSetupStartHeadline => 'شغّل وكيل برمجة هنا مباشرة';

  @override
  String phoneSetupStartPromise(String time, String size) {
    return 'لا حاجة إلى حاسوب أو تطبيقات أخرى. $time و~$size في المرة الأولى.';
  }

  @override
  String phoneSetupStartPromiseNoSize(String time) {
    return 'لا حاجة إلى حاسوب أو تطبيقات أخرى. $time في المرة الأولى.';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'نحو $minutes دقيقة',
      few: 'نحو $minutes دقائق',
      two: 'نحو دقيقتين',
      one: 'نحو دقيقة',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartMegabytes(String value) {
    return '$value ميغابايت';
  }

  @override
  String phoneSetupStartGigabytes(String value) {
    return '$value غيغابايت';
  }

  @override
  String get phoneSetupStartSetUp => 'ابدأ الإعداد';

  @override
  String phoneSetupStartIncludes(String tools) {
    return 'يشمل $tools.';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first و$last';
  }

  @override
  String get phoneSetupStartListSeparator => '، ';

  @override
  String get phoneSetupStartCustomize => 'تخصيص';

  @override
  String get phoneSetupStartOtherWays => 'طرق أخرى';

  @override
  String get phoneSetupStartUseTermux => 'استخدام Termux بدلًا من ذلك';

  @override
  String get phoneSetupStartByAddress => 'الاتصال بحاسوب عبر عنوانه';

  @override
  String get phoneSetupStartSetUpHere => 'إعداده داخل هذا التطبيق بدلًا من ذلك';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return 'اكتمل الإعداد بنسبة $percent٪';
  }

  @override
  String get phoneSetupStartRunningBody =>
      'يستمر العمل وأنت تستخدم تطبيقات أخرى.';

  @override
  String get phoneSetupStartStoppedBody =>
      'توقّف الإعداد قبل أن يكتمل. المتابعة تبدأ من حيث توقّف.';

  @override
  String get phoneSetupStartContinue => 'متابعة الإعداد';

  @override
  String get phoneSetupStartReadyHeadline => 'OpenCode جاهز على هذا الهاتف';

  @override
  String get phoneSetupStartReadyBody => 'افتحه لبدء محادثة.';

  @override
  String get phoneSetupStartOpen => 'فتح';

  @override
  String get phoneSetupStartTermuxHeadline => 'OpenCode مُعدّ مسبقًا في Termux';

  @override
  String get phoneSetupStartTermuxBody =>
      'أعددته عبر Termux من قبل. اتصل لمواصلة استخدامه.';

  @override
  String get phoneSetupStartConnect => 'اتصال';

  @override
  String phoneSetupStartFailed(String reason) {
    return 'لم ينجح ذلك: $reason';
  }

  @override
  String get phoneSetupStartEntryDetail =>
      'شغّل وكيل برمجة هنا مباشرة. لا تحتاج إلى حاسوب.';

  @override
  String get phoneSetupStartCustomizeTitle => 'اختر ما تريد تثبيته';

  @override
  String get phoneSetupStartAddTitle => 'إضافة أدوات';

  @override
  String get phoneSetupStartInstalled => 'مثبّت';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => 'لم تختر شيئًا بعد';

  @override
  String get phoneSetupStartDone => 'تم';

  @override
  String get phoneSetupStartAdd => 'إضافة';

  @override
  String get phoneSetupStartChecking => 'جارٍ التحقق مما هو مثبّت…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline =>
      'هذا الهاتف لا يمكنه تشغيله';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return 'لينكس هذا التطبيق يعمل فقط على هاتف Arm أو Intel بمعمارية 64 بت؛ هذا الهاتف يُبلّغ عن $abi.';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline =>
      'This phone doesn\'t have enough memory';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'OpenCode needs a phone with at least $minimumString MB of memory; this one has $actualString MB. Run it on a computer instead and connect this phone to it.';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return 'It may be slow on this phone, which has $memoryString MB of memory.';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline => 'لا توجد مساحة كافية';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return 'حرّر نحو $size على هذا الهاتف، ثم عد لإكمال الإعداد.';
  }

  @override
  String get phoneSetupPreflightOpenStorage => 'فتح إعدادات التخزين';

  @override
  String phoneSetupOpenWelcomeRunning(int percent) {
    return 'جارٍ إعداد OpenCode على هذا الهاتف · $percent٪';
  }

  @override
  String phoneSetupOpenWelcomeStopped(int percent) {
    return 'اكتمل الإعداد على هذا الهاتف بنسبة $percent٪';
  }

  @override
  String get phoneSetupOpenWelcomeStoppedDetail =>
      'المتابعة تبدأ من حيث توقّف.';

  @override
  String get phoneSetupOpenWelcomeShowProgress => 'عرض التقدّم';

  @override
  String get phoneSetupOpenWelcomeContinue => 'متابعة';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => 'أنشئ صفحة ويب صغيرة';

  @override
  String get chatStartPythonScript => 'اكتب سكربت بايثون…';

  @override
  String get chatStartNodeProject => 'ابدأ مشروع Node.js';

  @override
  String get chatStartReadme => 'أنشئ ملف README';

  @override
  String get chatStartExplainProject => 'اشرح هذا المشروع';

  @override
  String get chatStartWhatChanged => 'ما الذي تغيّر مؤخرًا؟';

  @override
  String get chatStartFindBug => 'اعثر على خطأ وأصلحه';

  @override
  String get chatStartAddTests => 'أضف اختبارات';

  @override
  String get chatStartListFolder => 'اعرض محتويات هذا المجلد';

  @override
  String get chatStartEmptyFolder => 'مجلد فارغ';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count عنصر',
      many: '$count عنصرًا',
      few: '$count عناصر',
      two: 'عنصران',
      one: 'عنصر واحد',
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
      other: '$count تغيير',
      many: '$count تغييرًا',
      few: '$count تغييرات',
      two: 'تغييران',
      one: 'تغيير واحد',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => 'جارٍ فحص المجلد…';

  @override
  String get chatStartServerFolder => 'مجلد الخادم';

  @override
  String get chatStartTip =>
      'اكتب / للأوامر · اضغط مطولًا على رسالة لإجراءاتها';

  @override
  String get chatLoadingConversation => 'جارٍ تحميل المحادثة';

  @override
  String get chatLoadFailedTitle => 'تعذّر فتح هذه المحادثة';

  @override
  String get chatLoadFailedBody =>
      'لم يضِع شيء. حاول مرة أخرى عندما يستجيب OpenCode.';

  @override
  String get chatSendFailed => 'لم تُرسَل رسالتك';

  @override
  String get chatSendFailedKept => 'أُعيدت إلى مربع الرسالة.';

  @override
  String get chatStartSuggestionsLabel => 'طرق للبدء';

  @override
  String get perfTraceTitle => 'الأداء';

  @override
  String get perfTraceBody =>
      'كم استغرقت كل خطوة منذ فتح التطبيق: الاتصال والتحميل وكل طلب إلى الخادم. يُحفظ في الذاكرة فقط ويُمسح عند إغلاق التطبيق. يحتوي التقرير على الأسماء والأوقات فقط، ولا يحتوي أبدًا على الرسائل أو كلمات المرور.';

  @override
  String get perfTraceCopy => 'نسخ التقرير';

  @override
  String get perfTraceCopied => 'تم نسخ تقرير الأداء';

  @override
  String get perfTraceEmpty => 'لم يُقَس شيء بعد.';

  @override
  String get perfTraceSlowest => 'أبطأ الخطوات';

  @override
  String get perfTraceRecent => 'أحدث الخطوات';

  @override
  String perfTraceStatLine(int count, String p50, String p95, String max) {
    return '$count× · المعتاد $p50 · البطيء $p95 · الأطول $max';
  }

  @override
  String perfTraceFailed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'فشلت $count مرات',
      one: 'فشلت مرة واحدة',
    );
    return '$_temp0';
  }

  @override
  String perfTraceAt(String time) {
    return 'عند $time';
  }

  @override
  String perfTraceWithin(String parent) {
    return 'ضمن $parent';
  }

  @override
  String get aiteamComponentTitle => 'الفريق الذكي';

  @override
  String aiteamComponentStageDownloading(String index, String total) {
    return 'تنزيل الفريق الذكي · $index من $total';
  }

  @override
  String get aiteamComponentStagePreparing => 'تجهيز الفريق الذكي';

  @override
  String aiteamComponentAddingTitle(String names) {
    return 'إضافة $names';
  }

  @override
  String get aiteamComponentNotice =>
      'OpenCode والفريق الذكي يعملان على هذا الهاتف';

  @override
  String get aiteamComponentSectionTitle => 'الفريق الذكي على هذا الهاتف';

  @override
  String aiteamComponentOfferBody(String size) {
    return 'عدة وكلاء يتقاسمون العمل على مشروع واحد، هنا على الهاتف. التنزيل نحو $size.';
  }

  @override
  String get aiteamComponentAdd => 'إضافة الفريق الذكي';

  @override
  String aiteamComponentTurnOn(String project) {
    return 'تشغيل الفريق الذكي لـ $project';
  }

  @override
  String get aiteamComponentTurnOnBody =>
      'يعمل الفريق على فروعه الخاصة ويحتفظ بنسخة من سجل المشروع على هذا الهاتف.';

  @override
  String get aiteamComponentNoProject =>
      'افتح مشروعًا أولًا، ثم شغّل الفريق الذكي له.';

  @override
  String get aiteamComponentStageTeam => 'تجهيز الفريق';

  @override
  String aiteamComponentStageProject(String project) {
    return 'إضافة $project';
  }

  @override
  String get aiteamComponentStageStarting => 'بدء الفريق الذكي';

  @override
  String get aiteamComponentStageWaiting => 'انتظار رد الفريق الذكي';

  @override
  String get aiteamComponentTurnOnExpectation =>
      'يستغرق هذا من 5 إلى 10 دقائق في المرة الأولى. يمكنك مغادرة هذه الشاشة؛ يستمر العمل.';

  @override
  String get aiteamComponentStartExpectation =>
      'يستغرق هذا بضع دقائق. يمكنك مغادرة هذه الشاشة؛ يستمر العمل.';

  @override
  String aiteamComponentStageSoFar(String time) {
    return '$time حتى الآن';
  }

  @override
  String aiteamComponentStageTook(String time) {
    return 'استغرق $time';
  }

  @override
  String get aiteamComponentRunning => 'الفريق الذكي · يعمل';

  @override
  String get aiteamComponentStopped => 'الفريق الذكي · متوقف';

  @override
  String aiteamComponentProjects(String projects) {
    return 'يعمل على $projects';
  }

  @override
  String get aiteamComponentStart => 'بدء الفريق الذكي';

  @override
  String aiteamComponentFailed(String reason) {
    return 'تعذّر بدء الفريق الذكي: $reason';
  }

  @override
  String get aiteamComponentFailedExited => 'توقف من تلقاء نفسه';

  @override
  String get aiteamComponentFailedTimeout => 'لم يرد في الوقت المحدد';

  @override
  String get aiteamComponentShowDetails => 'عرض التفاصيل';

  @override
  String get aiteamComponentChildProcesses =>
      'يوقف أندرويد البرامج الإضافية للتطبيق حين يشغّل كثيرًا منها معًا، والفريق يشغّل عدة برامج. إذا توقف الفريق أثناء العمل، فعّل خيارات المطوّرين › تعطيل قيود العمليات الفرعية.';

  @override
  String get workUnreviewed => 'غير مراجَعة';

  @override
  String get workMarkReviewed => 'وضع علامة كمراجَعة';

  @override
  String get workMarkReviewedFailed =>
      'تعذّر وضع علامة المراجعة. حاول مرة أخرى.';

  @override
  String get workOtherProjects => 'مشاريع أخرى';

  @override
  String get workAllProjects => 'كل المشاريع';

  @override
  String workOpenLiveConversation(String title) {
    return 'فتح «$title»';
  }

  @override
  String workRunningCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'قيد التشغيل · $count',
      one: 'قيد التشغيل',
    );
    return '$_temp0';
  }

  @override
  String get workLoadingLabel => 'جارٍ التحميل';

  @override
  String get workServerNotAnsweringPhone => 'OpenCode على هذا الهاتف لا يستجيب';

  @override
  String workServerNotAnswering(String server) {
    return '$server لا يستجيب';
  }

  @override
  String get workServerKeepsTrying => 'يواصل التطبيق المحاولة في الخلفية.';

  @override
  String get workServerRestart => 'إعادة التشغيل';

  @override
  String get workServerRestartTitle => 'إعادة تشغيل OpenCode على هذا الهاتف؟';

  @override
  String get workServerRestartBody =>
      'سيتوقف أي دور قيد التشغيل للوكيل. تبقى محادثاتك محفوظة.';

  @override
  String get workStale => 'قد لا يكون هذا محدّثًا';

  @override
  String workRunaway(String duration) {
    return 'OpenCode مشغول منذ $duration دون عمل ينتظره';
  }

  @override
  String workRunawayInProject(String project, String duration) {
    return 'OpenCode مشغول في $project منذ $duration دون عمل ينتظره';
  }

  @override
  String get connectStartingPhone => 'جارٍ تشغيل OpenCode على هذا الهاتف…';

  @override
  String get connectStartingBody =>
      'تبقى محادثاتك محفوظة. قد يستغرق هذا دقيقة.';

  @override
  String aiteamBringInDone(String project, String commit) {
    return 'أصبح أحدث عمل للفريق ($commit) في $project.';
  }

  @override
  String aiteamBringInDirty(String commit, String project, String files) {
    return 'لم يُدخَل عمل الفريق ($commit) إلى $project بعد: في $project تغييرات خاصة به ($files)، لذا تُرك كما هو.';
  }

  @override
  String aiteamBringInDiverged(String commit, String project) {
    return 'لم يُدخَل عمل الفريق ($commit) إلى $project: في $project إيداعات خاصة به. ادمج الاثنين باستخدام git.';
  }

  @override
  String aiteamBringInFailed(String project, String reason) {
    return 'تعذّر إدخال عمل الفريق إلى $project: $reason';
  }

  @override
  String aiteamBringInAction(String project) {
    return 'أدخل عمل الفريق إلى $project';
  }

  @override
  String get teamUiHostPhrasePhone => 'على هذا الهاتف';

  @override
  String teamUiHostPhraseComputerNamed(String name) {
    return 'على $name';
  }

  @override
  String get teamUiHostPhraseComputer => 'على حاسوبك';

  @override
  String get teamUiHostPhrasePaused => 'متوقف مؤقتًا';

  @override
  String get teamUiHostPhraseNotAnswering => 'لا يستجيب';

  @override
  String teamUiTaskSteps(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: 'اكتملت $done من $total خطوة',
      many: 'اكتملت $done من $total خطوة',
      few: 'اكتملت $done من $total خطوات',
      two: 'اكتملت $done من خطوتين',
      one: 'اكتملت $done من خطوة واحدة',
    );
    return '$_temp0';
  }

  @override
  String teamUiTaskDoneAgo(String when) {
    return 'اكتملت $when';
  }

  @override
  String teamUiTaskMergedAgo(String when) {
    return 'اكتملت · دُمجت $when';
  }

  @override
  String teamUiTaskCancelledAgo(String when) {
    return 'أُلغيت $when';
  }

  @override
  String teamUiHomeDoneMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'عرض $count مهمة أخرى',
      many: 'عرض $count مهمة أخرى',
      few: 'عرض $count مهام أخرى',
      two: 'عرض مهمتين أخريين',
      one: 'عرض مهمة أخرى',
    );
    return '$_temp0';
  }

  @override
  String get teamUiHomeSearchClose => 'إغلاق البحث';

  @override
  String get teamUiHomeNeedsYouAnswer => 'أجب';

  @override
  String get teamUiHomeNeedsYouFallbackTitle => 'لدى الفريق سؤال';

  @override
  String teamUiHomeNeedsYouAnnouncement(String question) {
    return 'يحتاجك: $question';
  }

  @override
  String get teamUiAgentRoleWorker => 'عامل';

  @override
  String get teamUiAgentRoleReviewer => 'مراجع';

  @override
  String get teamUiAgentRolePlanner => 'مخطِّط';

  @override
  String get teamUiAgentRoleSupervisor => 'مشرف';

  @override
  String get teamUiAgentRoleHelper => 'مساعد';

  @override
  String get teamUiAgentRoleOther => 'وكيل';

  @override
  String get teamUiRunStageWaiting => 'في الانتظار';

  @override
  String get teamUiRunStageWorking => 'قيد العمل';

  @override
  String get teamUiRunStageReviewing => 'قيد المراجعة';

  @override
  String get teamUiRunStageDone => 'مكتملة';

  @override
  String teamUiRunStageSemantics(int position, String stage) {
    return 'المرحلة $position من 4: $stage';
  }

  @override
  String get teamUiRunStepsHeading => 'الخطوات';

  @override
  String get teamUiRunDetailsUsage => 'الاستخدام';

  @override
  String get serverRowConnected => 'متصل';

  @override
  String phoneServerRowStatus(String runtime, String state) {
    return '$runtime · $state';
  }

  @override
  String get phoneServerRowRestarting => 'جارٍ إعادة التشغيل';

  @override
  String get phoneServerRowNotAnswering => 'لا يستجيب';

  @override
  String get phoneServerRowNotRunning => 'لا يعمل';

  @override
  String get phoneServerRowDetails => 'التفاصيل';

  @override
  String get localAgentRowOptional => 'اختياري';

  @override
  String get localAgentPageTitle => 'Claude Code';

  @override
  String get managedRecoveryRowTitle => 'إعادة التشغيل بعد التعطل';

  @override
  String get managedRecoveryRowDetail =>
      'حتى 3 محاولات، وفقط أثناء فتح التطبيق. لا يثبّت شيئًا ولا يحدّثه.';

  @override
  String get pluginsTeamRowTitle => 'فريق الذكاء الاصطناعي';

  @override
  String pluginsTeamRowFound(String server) {
    return 'موجود على $server';
  }

  @override
  String get pluginsLoading => 'جارٍ تحميل الإضافات';

  @override
  String get pluginsBuiltinGroup => 'مدمجة';

  @override
  String pluginsBuiltinActive(int count) {
    return '$count نشطة';
  }

  @override
  String pluginsBuiltinFailed(int count) {
    return 'تعذّر تحميل $count';
  }

  @override
  String get pluginsStatusFailedToLoad => 'تعذّر التحميل';

  @override
  String get pluginsDetailsId => 'المعرّف';

  @override
  String get localTerminalSourcePhone => 'هذا الهاتف';

  @override
  String get localTerminalSourceServer => 'خادم OpenCode';

  @override
  String localTerminalShellName(int number) {
    return 'طرفية $number';
  }

  @override
  String localTerminalShellEnded(String name) {
    return '$name · انتهت';
  }

  @override
  String get localTerminalNewShell => 'طرفية جديدة';

  @override
  String get localTerminalStopShell => 'إيقاف هذه الطرفية';

  @override
  String get localTerminalCloseShell => 'إغلاق هذه الطرفية';

  @override
  String get localTerminalStopBody => 'تتوقف البرامج التي تعمل فيها أيضًا.';

  @override
  String get localTerminalStop => 'إيقاف';

  @override
  String get localTerminalStarting => 'جارٍ بدء الطرفية';

  @override
  String get localTerminalNotSetUpTitle => 'لم يُجهَّز لينكس على هذا الهاتف';

  @override
  String get localTerminalNotSetUpBody =>
      'تعمل الطرفية داخل لينكس الذي يثبّته إعداد الهاتف.';

  @override
  String get localTerminalEndedTitle => 'انتهت الطرفية';

  @override
  String localTerminalEndedBody(int code) {
    return 'انتهت برمز الخروج $code.';
  }

  @override
  String get localTerminalRestart => 'إعادة التشغيل';

  @override
  String get localTerminalFailedTitle => 'لم تبدأ الطرفية';

  @override
  String get localTerminalFailedBody =>
      'حاول مرة أخرى. إن تكرر الفشل، فستجد السبب في «التفاصيل».';

  @override
  String get localTerminalTryAgain => 'إعادة المحاولة';

  @override
  String localTerminalCost(int perShell, int limit) {
    return 'عدد البرامج لكل طرفية: $perShell. مع تشغيل فريق الذكاء الاصطناعي، قد يوقف أندرويد برامج التطبيق إذا تجاوز عددها $limit.';
  }

  @override
  String localTerminalCostNow(int perShell, int count, int limit) {
    return 'عدد البرامج لكل طرفية: $perShell. مع تشغيل فريق الذكاء الاصطناعي، يشغّل التطبيق الآن $count، وقد يوقفها أندرويد إذا تجاوز عددها $limit.';
  }

  @override
  String get localTerminalKeysLabel => 'مفاتيح الطرفية';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => 'صفحة لأعلى';

  @override
  String get localTerminalKeyPageDown => 'صفحة لأسفل';

  @override
  String get localTerminalSemantics =>
      'الطرفية على هذا الهاتف. اضغط للكتابة، واضغط مطولًا لتحديد النص.';

  @override
  String get phoneServerTermuxTitle => 'هذا الهاتف · Termux';

  @override
  String get workNotAnsweringListTitle => 'ستعود محادثاتك';

  @override
  String get workNotAnsweringListBody =>
      'تظهر هنا من جديد بمجرد أن يستجيب الخادم.';

  @override
  String get globalSessionsLoadFailedTitle => 'تعذّر تحميل المحادثات';

  @override
  String get filesLoadFailedTitle => 'تعذّر فتح هذا المجلد';

  @override
  String get filesSymbolsFailedTitle => 'تعذّر البحث في الرموز';

  @override
  String get terminalListFailedTitle => 'تعذّر عرض الطرفيات';

  @override
  String get addServerConnectTo => 'الاتصال بـ';

  @override
  String get addServerTypeOpenCode => 'OpenCode على حاسوب';

  @override
  String get addServerTypeOpenCodeDetail => 'اقترن برمز، أو أدخل عنوانه';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail => 'Codex app-server على حاسوبك';

  @override
  String get addServerTypePaseo => 'Claude Code أو Pi';

  @override
  String get addServerTypePaseoDetail => 'عبر Paseo على حاسوبك';

  @override
  String get addServerScan => 'امسح الرمز';

  @override
  String get addServerPaste => 'الصق الرمز';

  @override
  String get addServerManual => 'أدخل العنوان بدلًا من ذلك';

  @override
  String get addServerChecking => 'جارٍ فحص الاتصال…';

  @override
  String addServerCheckingHost(String host) {
    return 'جارٍ فحص $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return 'جارٍ الاتصال بـ $host…';
  }

  @override
  String get addServerSaveAnyway => 'احفظ على أي حال';

  @override
  String get appearanceDisplaySection => 'الشاشة';

  @override
  String get effectsSection => 'المؤثرات';

  @override
  String get effectsAnimations => 'الحركة';

  @override
  String get effectsMotionFull => 'كاملة';

  @override
  String get effectsMotionFullHint =>
      'تتحرك الرسوم وتتنفس شاشات الانتظار وتُحتفل اللحظات المكتملة';

  @override
  String get effectsMotionCalm => 'هادئة';

  @override
  String get effectsMotionCalmHint =>
      'تظهر الرسوم ولا يبقى شيء متحركًا ولا احتفالات';

  @override
  String get effectsMotionOff => 'متوقفة';

  @override
  String get effectsMotionOffHint => 'يظهر كل شيء فورًا';

  @override
  String get effectsMotionSystemOff =>
      'خيار «إزالة الرسوم المتحركة» مفعّل في هاتفك، لذا لا يتحرك شيء أيًّا كان اختيارك هنا';

  @override
  String get effectsActivityGlow => 'حدّ متوهّج أثناء الرد';

  @override
  String get effectsActivityGlowHint =>
      'وهج ناعم يدور حول مربع الرسالة أثناء كتابة الرد';

  @override
  String get effectsSaveFailed =>
      'تعذّر حفظ هذا الاختيار على هذا الجهاز. حاول مرة أخرى.';

  @override
  String get teamDiscoverEntryTitle => 'كلّف فريقًا بمهمة أكبر';

  @override
  String get teamDiscoverIntroBody =>
      'صِف ما تريد إنجازه. يقسّمه فريق من الوكلاء إلى خطوات، ويعمل عليها جنبًا إلى جنب، ثم يضيف العمل المنجز إلى مشروعك.';

  @override
  String get teamDiscoverHowHeading => 'كيف يعمل';

  @override
  String get teamDiscoverStepPlanTitle => 'يخطّط';

  @override
  String get teamDiscoverStepPlanBody => 'يقسّم المخطِّط مهمتك إلى خطوات.';

  @override
  String get teamDiscoverStepWorkTitle => 'يعمل';

  @override
  String get teamDiscoverStepWorkBody =>
      'يتولّى العاملون الخطوات، كلٌّ على نسخته من المشروع.';

  @override
  String get teamDiscoverStepCheckTitle => 'يراجع';

  @override
  String get teamDiscoverStepCheckBody => 'يراجع المراجِع عمل كل خطوة.';

  @override
  String get teamDiscoverStepMergeTitle => 'يدمج';

  @override
  String get teamDiscoverStepMergeBody =>
      'يصل العمل المنجز إلى مشروعك. وحين يحتاج إلى قرار يسألك.';

  @override
  String get teamDiscoverNeedsPhone => 'ما يحتاجه على هذا الهاتف';

  @override
  String teamDiscoverNeedsServer(String server) {
    return 'ما يحتاجه على $server';
  }

  @override
  String teamDiscoverDownloadTitle(String size) {
    return 'تنزيل بنحو $size';
  }

  @override
  String get teamDiscoverInAppDownloadBody =>
      'يُثبَّت مرة واحدة بجوار OpenCode على هذا الهاتف.';

  @override
  String get teamDiscoverTermuxDownloadBody =>
      'يُثبَّت داخل Termux بجوار OpenCode.';

  @override
  String get teamDiscoverBatteryTitle => 'بطارية أكثر أثناء عمله';

  @override
  String get teamDiscoverInAppBatteryBody =>
      'يعمل عدة وكلاء معًا، وقد يوقف Android بعضهم إن كثروا.';

  @override
  String get teamDiscoverTermuxBatteryBody =>
      'أبقِ Termux مفتوحًا أثناء عمله؛ قد يوقفه Android في الخلفية. لا يضيع شيء.';

  @override
  String get teamDiscoverProjectTitle => 'أنت تختار المشاريع';

  @override
  String get teamDiscoverProjectBody => 'شغّله لكل مشروع تريده أن يعمل عليه.';

  @override
  String teamDiscoverComputerTitle(String server) {
    return 'يعمل على $server';
  }

  @override
  String get teamDiscoverComputerBody =>
      'ثبّت Gas City هناك مرة واحدة، وسيعثر عليه التطبيق بنفسه.';

  @override
  String get teamDiscoverSpeedTitle => 'بسرعة حاسوبك';

  @override
  String get teamDiscoverSpeedBody => 'أبقِه مستيقظًا أثناء عمل الفريق.';

  @override
  String teamDiscoverLooking(String server) {
    return 'جارٍ البحث عنه على $server…';
  }

  @override
  String get teamDiscoverFoundBody => 'إنه جاهز للتشغيل.';

  @override
  String get teamDiscoverEnterAddress => 'أدخِل عنوانه';

  @override
  String get teamDiscoverOnComputer => 'شغّله على حاسوب';

  @override
  String get teamDiscoverTurningOn => 'جارٍ التشغيل…';

  @override
  String get teamDiscoverComputerChoiceTitle => 'فريق على حاسوب';

  @override
  String get teamDiscoverComputerChoiceBody =>
      'استخدم Gas City على حاسوب بدلًا من ذلك';

  @override
  String get teamNowChecksEveryMinute => 'يتفقّد الفريق كل دقيقة';

  @override
  String teamNowChecksEvery(String minutes) {
    return 'يتفقّد الفريق كل $minutes د';
  }

  @override
  String get teamNowNextCheck => 'يبدأ عامل عند تفقّد الفريق التالي';

  @override
  String get teamNowNoWorkerStarted => 'لم يبدأ أي عامل';

  @override
  String get teamNowPausedLine =>
      'الفريق موقوف مؤقتًا · لن يبدأ شيء حتى تستأنفه';

  @override
  String get teamNowStartWorker => 'شغّل عاملًا';

  @override
  String get teamNowWhy => 'لماذا؟';

  @override
  String get teamAgentDidNotStartTitle => 'لم يبدأ العامل';

  @override
  String get teamAgentDidNotStartBody =>
      'هناك مهمة تنتظر، لكن هذا العامل لا يعمل.';

  @override
  String get teamAgentStartIt => 'شغّله';

  @override
  String teamOutputNotRunning(String name) {
    return '$name لا يعمل، لذا لا مخرجات';
  }

  @override
  String get teamOutputSilent => 'لا مخرجات بعد · قد يستغرق البدء دقيقة';

  @override
  String teamAgentTitle(String role, String name) {
    return '$role · $name';
  }

  @override
  String teamAgentWorksOn(String title) {
    return 'على «$title»';
  }

  @override
  String teamOutputStartingPhone(String age) {
    return 'يبدأ التشغيل · منذ $age · قد يستغرق ذلك بضع دقائق على الهاتف';
  }

  @override
  String get teamNewModeSolo => 'منفرد';

  @override
  String get teamNewModeTeam => 'فريق';

  @override
  String get teamNewTask => 'مهمة فريق جديدة';

  @override
  String get teamTaskMark => 'فريق';

  @override
  String get chatWatchEmptyTitle => 'لا شيء هنا بعد';

  @override
  String get chatWatchEmptyBody => 'تمتلئ هذه المحادثة بينما يعمل الوكيل.';

  @override
  String teamWatchBanner(String name, String role, String state) {
    return 'تشاهد $name · $role · فريق الذكاء';
  }

  @override
  String teamWatchBannerRole(String role, String state) {
    return 'تشاهد $role · فريق الذكاء';
  }

  @override
  String get teamWatchFallbackUnreadable =>
      'لا يمكن قراءة محادثته من الخادم المتصل به التطبيق، لذا هذا هو الناتج المباشر للفريق.';

  @override
  String get teamWatchFallbackNotFound =>
      'محادثته ليست بعد على الخادم المتصل به التطبيق (قد يكون ما زال يبدأ، أو أن الفريق يعمل على حاسوب آخر)، لذا هذا هو الناتج المباشر للفريق.';

  @override
  String get teamOpenConversation => 'افتح المحادثة';

  @override
  String teamOpenConversationHint(String name) {
    return 'شاهد عمل $name في المحادثة';
  }

  @override
  String get teamOpenConversationFinding => 'جارٍ العثور على محادثته…';

  @override
  String get teamChatUntitled => 'مهمة الفريق';

  @override
  String teamChatSubtitle(String host) {
    return 'فريق الذكاء · $host';
  }

  @override
  String get teamChatOpenTeam => 'فريق الذكاء';

  @override
  String get teamChatTaskDetails => 'تفاصيل المهمة';

  @override
  String get teamChatStopTask => 'إيقاف المهمة';

  @override
  String get teamChatStopConfirmTitle => 'هل تريد إيقاف هذه المهمة؟';

  @override
  String teamChatStopConfirmBody(String task) {
    return 'ستتوقف «$task» على جهاز الفريق. يتوقف الآن العاملون الذين ما زالوا يعملون عليها، ويبقى العمل المنجز كما هو. لا يمكن التراجع عن ذلك من الهاتف.';
  }

  @override
  String get teamChatStopKeepRunning => 'دعها تعمل';

  @override
  String get teamChatLoading => 'جارٍ تحميل المهمة';

  @override
  String get teamChatLeadName => 'الفريق';

  @override
  String get teamChatLeadSent => 'أُرسلت إلى الفريق';

  @override
  String get teamChatLeadNothingYet =>
      'لا شيء بعد. لم يخطط الفريق لهذه المهمة.';

  @override
  String teamChatLeadPlanned(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'خُططت $count خطوة',
      many: 'خُططت $count خطوة',
      few: 'خُططت $count خطوات',
      two: 'خُططت خطوتان',
      one: 'خُططت خطوة واحدة',
      zero: 'لا خطوات',
    );
    return '$_temp0';
  }

  @override
  String teamChatLeadRouted(String title) {
    return 'أُرسلت «$title» إلى العمال';
  }

  @override
  String teamChatLeadStarting(String title) {
    return 'بدأ عامل على «$title»';
  }

  @override
  String teamChatLeadClaimedWorker(String title) {
    return 'تولّى عامل «$title»';
  }

  @override
  String teamChatLeadPushed(String title) {
    return 'تغييرات «$title» على فرع';
  }

  @override
  String teamChatLeadReview(String title) {
    return 'سُلّمت «$title» للمراجعة';
  }

  @override
  String teamChatLeadMerged(String title) {
    return 'دُمجت «$title»';
  }

  @override
  String teamChatLeadStepFailed(String title) {
    return 'فشلت «$title»';
  }

  @override
  String teamChatLeadStepCancelled(String title) {
    return 'أُلغيت «$title»';
  }

  @override
  String teamChatLeadNeedsYou(String question) {
    return 'يحتاجك: $question';
  }

  @override
  String get teamChatLeadTaskMerged => 'دُمجت. انتهت المهمة.';

  @override
  String get teamChatLeadTaskFinished => 'انتهت المهمة.';

  @override
  String get teamChatLeadTaskFailed => 'فشلت المهمة.';

  @override
  String get teamChatLeadTaskCancelled => 'أُلغيت المهمة.';

  @override
  String get teamChatAWorker => 'عامل';

  @override
  String teamChatStepsSummary(int count, int done) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count خطوة',
      many: '$count خطوة',
      few: '$count خطوات',
      two: 'خطوتان',
      one: 'خطوة واحدة',
      zero: 'لا خطوات',
    );
    return '$_temp0 · $done منتهية';
  }

  @override
  String get teamChatComposerHint => 'راسل الفريق…';

  @override
  String teamChatComposerGoesTo(String name) {
    return 'رسالتك تصل إلى $name';
  }

  @override
  String get teamChatComposerNobody =>
      'لا يوجد وكيل على هذه المهمة لمراسلته بعد.';

  @override
  String get teamChatComposerCannot => 'لا يمكن مراسلة هذا الفريق من هنا.';

  @override
  String get teamBoardTitle => 'اللوحة';

  @override
  String get teamBoardOpenTooltip => 'اللوحة';

  @override
  String get teamBoardColumnBacklog => 'قائمة الانتظار';

  @override
  String get teamBoardColumnReady => 'جاهزة';

  @override
  String get teamBoardColumnWorking => 'قيد العمل';

  @override
  String get teamBoardColumnReview => 'المراجعة';

  @override
  String get teamBoardColumnDone => 'منجزة';

  @override
  String get teamBoardEmptyBacklogTitle => 'لا شيء بالانتظار';

  @override
  String get teamBoardEmptyBacklogBody =>
      'المهام التي تضيفها ولم تبدأها تنتظر هنا.';

  @override
  String get teamBoardEmptyReadyTitle => 'لا شيء في الدور';

  @override
  String get teamBoardEmptyReadyBody =>
      'المهام التي أُعطيت للفريق تنتظر هنا عاملًا.';

  @override
  String get teamBoardEmptyWorkingTitle => 'لا أحد يعمل الآن';

  @override
  String get teamBoardEmptyWorkingBody =>
      'تنتقل المهمة إلى هنا عندما يتولاها عامل.';

  @override
  String get teamBoardEmptyReviewTitle => 'لا شيء للمراجعة';

  @override
  String get teamBoardEmptyReviewBody => 'العمل المكتمل ينتظر هنا فحصه ودمجه.';

  @override
  String get teamBoardEmptyDoneTitle => 'لم يكتمل شيء هذا الأسبوع';

  @override
  String get teamBoardEmptyDoneBody =>
      'تظهر هنا المهام المدمجة والمنجزة والملغاة في آخر 7 أيام.';

  @override
  String get teamBoardEmptyTitle => 'لا مهام بعد';

  @override
  String get teamBoardEmptyBody =>
      'تظهر هنا المهام التي تعطيها للفريق، حسب مرحلتها.';

  @override
  String get teamBoardPriorityUrgent => 'عاجلة';

  @override
  String get teamBoardPriorityHigh => 'عالية';

  @override
  String get teamBoardPriorityNormal => 'عادية';

  @override
  String get teamBoardPriorityLow => 'منخفضة';

  @override
  String get teamBoardPrioritySomeday => 'يومًا ما';

  @override
  String get teamBoardTypeBug => 'خلل';

  @override
  String get teamBoardTypeFeature => 'ميزة';

  @override
  String get teamBoardTypeEpic => 'مهمة كبرى';

  @override
  String get teamBoardTypeChore => 'مهمة روتينية';

  @override
  String teamBoardEpicProgress(int done, int total) {
    return 'اكتمل $done من $total';
  }

  @override
  String get teamBoardFlagNeedsYou => 'يحتاج إليك';

  @override
  String get teamBoardFlagBlocked => 'متوقفة';

  @override
  String teamBoardFlagBlockedBy(String title) {
    return 'متوقفة حتى تنتهي «$title»';
  }

  @override
  String teamBoardFlagBlockedByMore(String title, int count) {
    return 'متوقفة حتى تنتهي «$title» و$count غيرها';
  }

  @override
  String get teamBoardFlagFailed => 'توقفت بسبب خطأ';

  @override
  String teamBoardFlagInEpic(String epic) {
    return 'ضمن «$epic»';
  }

  @override
  String teamBoardFlagMoving(String column) {
    return 'جارٍ النقل إلى $column…';
  }

  @override
  String get teamBoardFlagCancelled => 'ملغاة';

  @override
  String get teamBoardMoveMenuTooltip => 'نقل أو تغيير';

  @override
  String teamBoardMoveSheetWhere(String column) {
    return 'في $column';
  }

  @override
  String get teamBoardMoveStartNow => 'ابدأ الآن';

  @override
  String get teamBoardMoveStartNowHint => 'أعطها لعمّال الفريق';

  @override
  String get teamBoardMoveBackToBacklog => 'أعدها إلى قائمة الانتظار';

  @override
  String get teamBoardMoveBackToBacklogHint => 'لن يتولاها الفريق حتى تبدأها';

  @override
  String get teamBoardMovePriority => 'الأولوية';

  @override
  String get teamBoardMoveCancel => 'ألغِ المهمة';

  @override
  String get teamBoardMoveCancelHint => 'تنقلها إلى المنجزة كمهمة ملغاة';

  @override
  String get teamBoardMoveReopen => 'أعدها إلى قائمة الانتظار';

  @override
  String get teamBoardMoveReopenHint => 'لن يعمل عليها أحد حتى تبدأها';

  @override
  String get teamBoardOpenConversation => 'افتح المحادثة';

  @override
  String get teamBoardOpenDetails => 'افتح التفاصيل';

  @override
  String get teamBoardTeamMoves =>
      'الفريق هو من ينقل هذه المهمة. افتح محادثتها لمراسلة الفريق أو إيقافه.';

  @override
  String get teamBoardReadOnlyNote =>
      'هذا المضيف لا يسمح للتطبيق بتغيير المهام، لذا اللوحة للقراءة فقط هنا.';

  @override
  String get teamBoardStatusReadOnly =>
      'للقراءة فقط هنا · هذا المضيف لا يسمح للتطبيق بتغيير المهام';

  @override
  String teamBoardCancelTitle(String title) {
    return 'إلغاء «$title»؟';
  }

  @override
  String get teamBoardCancelBody =>
      'لن يعمل الفريق عليها. ستنتقل إلى المنجزة كمهمة ملغاة، ويمكنك إعادتها إلى قائمة الانتظار لاحقًا.';

  @override
  String get teamBoardCancelKeep => 'أبقِها';

  @override
  String teamBoardMoveFailedTitle(String title) {
    return 'تعذّر تغيير «$title»';
  }

  @override
  String get teamBoardMoveFailedBody =>
      'رفض مضيف الفريق الطلب، لذا بقيت في مكانها.';

  @override
  String get teamBoardPriorityTitle => 'الأولوية';

  @override
  String get teamBoardAddFailed => 'تعذّرت الإضافة. رفض مضيف الفريق الطلب.';

  @override
  String get teamBoardProjectTooltip => 'اختر المشروع';

  @override
  String appExitForceStopped(String time) {
    return 'أغلق أندرويد OpenCode Mobile $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'أغلق أندرويد OpenCode Mobile $time لتحرير الذاكرة';
  }

  @override
  String appExitCrashed(String time) {
    return 'توقّف OpenCode Mobile على نحو غير متوقع $time';
  }

  @override
  String appExitKilled(String time) {
    return 'أوقف أندرويد OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what. توقّف OpenCode على هاتفك معه، وهو يبدأ من جديد.';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what. توقّف OpenCode على هاتفك وفريق الذكاء الاصطناعي معه، وهما يبدآن من جديد.';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what. Your phone\'s OpenCode stopped with it. Start it again when you\'re ready.';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what. Your phone\'s OpenCode and the AI Team stopped with it. Start them again when you\'re ready.';
  }

  @override
  String appExitServerBack(String what) {
    return '$what. Your phone\'s OpenCode stopped with it and is running again.';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what. Your phone\'s OpenCode and the AI Team stopped with it; OpenCode is running again.';
  }

  @override
  String appExitAtTime(String time) {
    return 'عند $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return 'يوم $day عند $time';
  }

  @override
  String get appExitKeepRunning => 'أبقِه يعمل';

  @override
  String get keepRunningTitle => 'الاستمرار في العمل في الخلفية';

  @override
  String get keepRunningRowSubtitle =>
      'ما يجب السماح به كي لا يغلق هاتفك التطبيق';

  @override
  String keepRunningIntro(String maker) {
    return 'يعمل OpenCode وفريق الذكاء الاصطناعي على هاتفك داخل هذا التطبيق، لذا يتوقفان عندما يغلقه أندرويد. على هاتف $maker هذا، اسمح بما يلي:';
  }

  @override
  String get keepRunningSwipeWarning =>
      'يغلق هذا الهاتف التطبيق الذي تسحبه بعيدًا من التطبيقات الحديثة حتى وهو يعمل. اقفله هناك بدلًا من سحبه.';

  @override
  String get keepRunningBatteryTitle => 'عدم تحسين البطارية';

  @override
  String get keepRunningBatteryDetail =>
      'يتيح للتطبيق الاستمرار في العمل أثناء استخدامك تطبيقات أخرى.';

  @override
  String get keepRunningBatteryDone => 'مسموح';

  @override
  String get keepRunningLockTitle => 'اقفله في التطبيقات الحديثة';

  @override
  String get keepRunningLockNubia =>
      'افتح التطبيقات الحديثة واسحب بطاقة OpenCode Mobile للأسفل حتى يظهر القفل.';

  @override
  String get keepRunningLockSamsung =>
      'افتح التطبيقات الحديثة، واضغط أيقونة OpenCode Mobile فوق بطاقته واختر إبقاء مفتوحًا.';

  @override
  String get keepRunningLockOther =>
      'افتح التطبيقات الحديثة، واضغط مطولًا على بطاقة OpenCode Mobile ثم اضغط القفل.';

  @override
  String get keepRunningAutostartTitle => 'السماح بالتشغيل التلقائي';

  @override
  String get keepRunningAutostartDetail =>
      'فعّله لتطبيق OpenCode Mobile كي لا يوقفه الهاتف في الخلفية.';

  @override
  String get keepRunningAutostartHuawei =>
      'في تشغيل التطبيقات، اضبط OpenCode Mobile على الإدارة يدويًا وفعّل المفاتيح الثلاثة.';

  @override
  String get keepRunningBackgroundTitle => 'السماح بالنشاط في الخلفية';

  @override
  String get keepRunningBackgroundXiaomi =>
      'في معلومات التطبيق › موفّر البطارية، اختر بلا قيود.';

  @override
  String get keepRunningBackgroundOppo =>
      'في معلومات التطبيق › استخدام البطارية، اسمح بالنشاط في الخلفية.';

  @override
  String get keepRunningBackgroundVivo =>
      'في معلومات التطبيق › البطارية، اسمح بالاستهلاك العالي للطاقة في الخلفية.';

  @override
  String get keepRunningBackgroundSamsung =>
      'في معلومات التطبيق › البطارية، اختر غير مقيّد، وأبقِ التطبيق خارج التطبيقات النائمة.';

  @override
  String get keepRunningBackgroundOther =>
      'في معلومات التطبيق › البطارية، اختر غير مقيّد أو اسمح بالعمل في الخلفية.';

  @override
  String get keepRunningOpen => 'فتح';

  @override
  String get keepRunningOpenFailed =>
      'لا توجد هذه الشاشة على هذا الهاتف. افتح الإعدادات › التطبيقات › OpenCode Mobile بدلًا من ذلك.';

  @override
  String get keepRunningFootnote =>
      'إذا أغلق أندرويد التطبيق رغم ذلك، فسيعيد تشغيل OpenCode على هاتفك في المرة التالية التي تفتحه فيها.';

  @override
  String get keepRunningThisPhone => 'الهاتف';

  @override
  String teamAgentLastStep(String step) {
    return 'آخر خطوة: $step';
  }

  @override
  String teamAgentLastActive(String elapsed) {
    return 'آخر نشاط قبل $elapsed';
  }

  @override
  String teamChatLeadEarlier(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count تحديثات سابقة',
      one: 'تحديث سابق واحد',
    );
    return '$_temp0';
  }

  @override
  String get thermalPausedNotice =>
      'هاتفك ساخن — أوقفنا فريق الذكاء الاصطناعي مؤقتًا ليبرد. سيستأنف عمله من تلقاء نفسه.';

  @override
  String get thermalStoppedNotice =>
      'هاتفك ساخن جدًا — أوقفنا فريق الذكاء الاصطناعي لحمايته. عمله محفوظ، وسيبدأ من جديد عندما يبرد الهاتف.';

  @override
  String get thermalResumedNotice =>
      'استأنف فريق الذكاء الاصطناعي عمله — لقد برد هاتفك.';

  @override
  String get thermalGuardSetting =>
      'إيقاف فريق الذكاء الاصطناعي مؤقتًا عندما يسخن الهاتف';

  @override
  String get thermalGuardSettingDetail =>
      'يتوقف الفريق مؤقتًا مع حفظ عمله ويستأنف من تلقاء نفسه عندما يبرد الهاتف.';

  @override
  String get kitSheetClose => 'إغلاق';

  @override
  String get kitSheetDismiss => 'إخفاء';

  @override
  String get kitSheetLoading => 'جارٍ التحميل';

  @override
  String get kitConfirmCancel => 'إلغاء';

  @override
  String get kitConfirmKeepRunning => 'مواصلة التشغيل';

  @override
  String get kitConfirmKeepEditing => 'متابعة التحرير';

  @override
  String kitConfirmTypeName(String name) {
    return 'اكتب $name للتأكيد';
  }

  @override
  String get kitConfirmTypeNameReason =>
      'اكتب الاسم تمامًا كما يظهر لتفعيل هذا الزر.';

  @override
  String get kitConfirmFailed => 'لم يكتمل ذلك. يمكنك إعادة المحاولة.';

  @override
  String get kitTryAgain => 'إعادة المحاولة';

  @override
  String get kitDetails => 'التفاصيل';

  @override
  String get kitDiscardTitle => 'هل تريد تجاهل تغييراتك؟';

  @override
  String get kitDiscardBody =>
      'ما غيّرته هنا غير محفوظ، ولا يمكن التراجع عن تجاهله.';

  @override
  String get kitDiscardConfirm => 'تجاهل التغييرات';

  @override
  String get kitCopied => 'تم النسخ';

  @override
  String get kitMore => 'المزيد';

  @override
  String kitChipRemove(String label) {
    return 'إزالة $label';
  }

  @override
  String get kitCopy => 'نسخ';

  @override
  String get kitWorking => 'قيد التنفيذ';

  @override
  String get kitImageUnavailable => 'يتعذر عرض هذه الصورة';

  @override
  String get kitZoomIn => 'تكبير';

  @override
  String get kitZoomOut => 'تصغير';

  @override
  String get kitZoomReset => 'إعادة ضبط التكبير';

  @override
  String get kitZoomFit => 'ملائمة الشاشة';

  @override
  String get kitZoomAtStart => 'في العرض الكامل بالفعل';

  @override
  String get kitZoomAtMax => 'أقصى تكبير';

  @override
  String kitZoomLevel(String percent) {
    return '$percent٪';
  }

  @override
  String kitZoomShortcut(String action, String key) {
    return '$action · Ctrl+$key';
  }

  @override
  String get kitMenu => 'قائمة';

  @override
  String get kitCopyDetails => 'نسخ التفاصيل';

  @override
  String get kitReportProblem => 'الإبلاغ عن مشكلة';

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

    return 'الخطوة $stepString من $ofString';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: 'بقيت $secondsString ثانية تقريبًا',
      many: 'بقيت $secondsString ثانية تقريبًا',
      few: 'بقيت $secondsString ثوانٍ تقريبًا',
      two: 'بقيت ثانيتان تقريبًا',
      one: 'بقيت ثانية تقريبًا',
      zero: 'بقي أقل من ثانية',
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
      other: 'بقيت $minutesString دقيقة تقريبًا',
      many: 'بقيت $minutesString دقيقة تقريبًا',
      few: 'بقيت $minutesString دقائق تقريبًا',
      two: 'بقيت دقيقتان تقريبًا',
      one: 'بقيت دقيقة تقريبًا',
      zero: 'بقي أقل من دقيقة',
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
      other: 'بقيت $hoursString ساعة تقريبًا',
      many: 'بقيت $hoursString ساعة تقريبًا',
      few: 'بقيت $hoursString ساعات تقريبًا',
      two: 'بقيت ساعتان تقريبًا',
      one: 'بقيت ساعة تقريبًا',
      zero: 'بقي أقل من ساعة',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong =>
      'This is too long for a QR code. Copy the link instead.';

  @override
  String kitSinceStillWaiting(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    return 'لا يزال الانتظار مستمرًا بعد $secondsString ث';
  }

  @override
  String kitSinceWaitingFor(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'جارٍ الانتظار منذ $minutesString دقيقة',
      many: 'جارٍ الانتظار منذ $minutesString دقيقة',
      few: 'جارٍ الانتظار منذ $minutesString دقائق',
      two: 'جارٍ الانتظار منذ دقيقتين',
      one: 'جارٍ الانتظار منذ دقيقة واحدة',
      zero: 'جارٍ الانتظار منذ أقل من دقيقة',
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
      other: '$minutesString دقيقة',
      many: '$minutesString دقيقة',
      few: '$minutesString دقائق',
      two: 'دقيقتين',
      one: 'دقيقة واحدة',
      zero: 'أقل من دقيقة',
    );
    return '$_temp0';
  }

  @override
  String get kitMarkWaiting => 'بانتظار';

  @override
  String get kitMarkWorking => 'يعمل';

  @override
  String get kitMarkDone => 'انتهى';

  @override
  String get kitMarkFailed => 'فشل';

  @override
  String get kitMarkPaused => 'متوقف مؤقتًا';

  @override
  String get kitTaskNeedsYou => 'يحتاجك';

  @override
  String get kitTaskStopped => 'متوقف';

  @override
  String get kitSwatchInUse => 'قيد الاستخدام';

  @override
  String get kitThemePreviewTitle => 'إصلاح خطأ تسجيل الدخول';

  @override
  String get kitThemePreviewWorking => 'قيد التنفيذ · دقيقتان';

  @override
  String get kitThemePreviewNeedsYou => 'يحتاجك';

  @override
  String get kitThemePreviewPrimary => 'إرسال';

  @override
  String get kitThemePreviewSecondary => 'إرفاق';

  @override
  String get kitThemePreviewSegment => 'مفعّل';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => 'مفتاح الشرطة المائلة';

  @override
  String get kitTerminalViewKeyDash => 'مفتاح الشرطة';

  @override
  String get kitTerminalViewKeyPipe => 'مفتاح الخط العمودي';

  @override
  String get kitTerminalViewKeyTilde => 'مفتاح التلدة';

  @override
  String get kitTerminalViewKeyHome => 'مفتاح Home';

  @override
  String get kitTerminalViewKeyEnd => 'مفتاح End';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تُعرض آخر $countString من الأسطر',
      one: 'يُعرض آخر سطر فقط',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => 'تراجع';

  @override
  String kitUndoFailed(String message) {
    return 'تعذّر التراجع. $message';
  }

  @override
  String get kitUndoWorking => 'جارٍ التراجع';

  @override
  String get kitTermHint => 'يتوفر شرح';

  @override
  String get kitTermShow => 'إظهار الشرح';

  @override
  String get kitTermClose => 'إغلاق الشرح';

  @override
  String kitFieldShowNamed(String label) {
    return 'Show $label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return 'Hide $label';
  }

  @override
  String get kitFieldPaste => 'Paste';

  @override
  String get kitFieldSaved => 'Saved';

  @override
  String get kitFieldReplace => 'Replace';

  @override
  String get kitFieldChecking => 'Checking…';

  @override
  String kitFieldStillChecking(int seconds) {
    return 'Still checking after $seconds s';
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
      other: '$countString of $maxString',
      one: '1 of $maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => 'Limit reached';

  @override
  String get kitFieldErrorLabel => 'Error';

  @override
  String get kitTappableShowActions => 'Show actions';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'read $_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count times',
      one: 'once',
    );
    return 'searched $_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count folders',
      one: '1 folder',
    );
    return 'listed $_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'edited $_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count commands',
      one: '1 command',
    );
    return 'ran $_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pages',
      one: '1 page',
    );
    return 'fetched $_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
    );
    return 'delegated $_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count other steps',
      one: '1 other step',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count not run',
      one: '1 not run',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => 'Waiting for you';

  @override
  String get kitWorkStopped => 'Stopped';

  @override
  String get kitWorkDidntFinish => 'Didn\'t finish';

  @override
  String get kitWorkWorking => 'Working';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier steps',
      one: '1 earlier step',
    );
    return 'Show $_temp0';
  }

  @override
  String get kitReceiptSending => 'Sending…';

  @override
  String get kitReceiptSent => 'Sent';

  @override
  String get kitReceiptConfirmed => 'Done';

  @override
  String get kitReceiptNotConfirmed => 'Not confirmed yet';

  @override
  String get kitReceiptRefused => 'Not accepted';

  @override
  String kitReceiptRefusedReason(String reason) {
    return 'Not accepted: $reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return 'Answered on $where';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown => 'Answered on another device';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act: $reason';
  }

  @override
  String kitReceiptAt(String time) {
    return 'at $time';
  }

  @override
  String get kitDetailsHide => 'Hide details';

  @override
  String get kitCopyAll => 'Copy all';

  @override
  String kitCopyValue(String label) {
    return 'Copy $label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show all $count lines',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label: $value';
  }

  @override
  String get kitProgressRowLoading => 'Loading';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString percent';
  }

  @override
  String get kitProgressRowNearLimit => 'Near limit';

  @override
  String get kitProgressRowAtLimit => 'Limit reached';

  @override
  String kitProgressRowAsOf(String time) {
    return 'as of $time';
  }

  @override
  String get kitProgressRowOther => 'Other';

  @override
  String get kitModelServerDefault => 'Server default';

  @override
  String get kitModelSignIn => 'Sign in to a model';

  @override
  String get kitModelChoose => 'Choose a model';

  @override
  String get kitModelChange => 'Change model';

  @override
  String get kitModelActions => 'Model shortcuts';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String get kitModelContextFull => 'Context almost full';

  @override
  String kitModelContextLabel(String percent) {
    return 'Context $percent % full';
  }

  @override
  String kitAttachmentOpen(String label) {
    return 'Preview $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return 'Image, $label';
  }

  @override
  String kitAttachmentFile(String label) {
    return 'File, $label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return 'Folder, $label';
  }

  @override
  String kitAttachmentReference(String label) {
    return 'Reference, $label';
  }

  @override
  String get kitSuggestionsShowAll => 'Show all';

  @override
  String get kitSuggestionsLabel => 'Suggestions';

  @override
  String get kitNeedsYouReasonDecision => 'Needs your decision';

  @override
  String get kitNeedsYouReasonBlocked => 'Stuck: needs you';

  @override
  String get kitNeedsYouReasonConsent => 'Needs your OK';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString need you · ',
      one: 'Needs you · ',
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
      other: ', $countString need you',
      one: ', 1 need you',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return 'waiting $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'waiting $minutesString minutes',
      one: 'waiting 1 minute',
      zero: 'waiting less than a minute',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$who on $server';
  }

  @override
  String get kitWorkGraph => 'Work graph';

  @override
  String get kitWorkGraphEmpty => 'No work items yet';

  @override
  String kitWorkGraphNode(String title, String state) {
    return '$title, $state';
  }

  @override
  String kitWorkGraphNeeds(String title) {
    return 'needs $title';
  }

  @override
  String kitWorkGraphNeedsMore(String title, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: '1',
    );
    return 'needs $title and $_temp0 more';
  }

  @override
  String get kitJumpLatest => 'Jump to latest';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new · Jump to latest',
      one: '1 new · Jump to latest',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => 'Current';

  @override
  String get kitChoiceRecommended => 'Recommended';

  @override
  String get kitChoiceOtherSend => 'Send answer';

  @override
  String kitChoiceSelectedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count selected',
      one: '1 selected',
      zero: 'None selected',
    );
    return '$_temp0';
  }

  @override
  String get kitComposerField => 'Message';

  @override
  String get kitComposerSend => 'Send';

  @override
  String get kitComposerSending => 'Sending';

  @override
  String get kitComposerSendOffline => 'Send when back online';

  @override
  String get kitComposerSendAfter => 'Send after this reply';

  @override
  String get kitComposerAddToTurn => 'Add to this turn';

  @override
  String get kitComposerStop => 'Stop the reply';

  @override
  String get kitComposerSendAfterShort => 'Send after';

  @override
  String get kitComposerAddToTurnShort => 'Add to this turn';

  @override
  String get kitComposerDeliveryLabel => 'When to send';

  @override
  String get kitComposerSendsAfter => 'Sends after this reply';

  @override
  String get kitComposerCannotSendYet =>
      'You can send when this reply finishes';

  @override
  String get kitComposerOffline => 'Offline · sends when you\'re back online';

  @override
  String get kitComposerTools => 'Attach and more';

  @override
  String get kitComposerVoice => 'Talk instead of typing';

  @override
  String get kitComposerEditor => 'Open full-screen editor';

  @override
  String get kitVoiceLeave => 'Leave voice mode';

  @override
  String get kitVoiceStarting => 'Getting the microphone ready…';

  @override
  String get kitVoiceListening => 'Listening…';

  @override
  String get kitVoiceTranscribing => 'Writing down what you said…';

  @override
  String get kitVoiceWaitingReply => 'Waiting for the reply…';

  @override
  String get kitVoiceSpeaking => 'Reading the reply aloud';

  @override
  String get kitVoiceReplyReady => 'The reply is ready';

  @override
  String get kitVoicePaused => 'Paused · the agent needs you';

  @override
  String get kitVoiceMicDenied => 'The microphone is off for this app';

  @override
  String get kitVoiceFailed => 'Voice stopped';

  @override
  String get kitVoiceSend => 'Send';

  @override
  String get kitVoiceDone => 'Done';

  @override
  String get kitVoiceStopReading => 'Stop reading';

  @override
  String get kitVoiceReadReply => 'Read it aloud';

  @override
  String get kitVoiceListen => 'Listen';

  @override
  String get kitVoiceReadAloud => 'Read replies aloud';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => 'Clear search';

  @override
  String get kitSearchFilter => 'Filter';

  @override
  String kitSearchFilterActive(String name) {
    return 'Filter: $name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results',
      one: '1 result',
      zero: 'No results',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return '$count loaded · searching the server…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return 'Nothing matches $query';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return 'Nothing in $what matches $query';
  }

  @override
  String get kitTopBarBack => 'Back';

  @override
  String get kitTopBarClose => 'Close';

  @override
  String get kitTopBarSearch => 'Search';

  @override
  String get kitTopBarSwitchServer => 'Switch server';

  @override
  String get kitTopBarSwitchProject => 'Switch project';

  @override
  String get kitTopBarMore => 'More actions';

  @override
  String get kitAgentStripLabel => 'Agents on this task';

  @override
  String kitAgentOpen(String name) {
    return 'Open $name\'s conversation';
  }

  @override
  String kitAgentLabel(String hasRole, String name, String role, String state) {
    String _temp0 = intl.Intl.selectLogic(hasRole, {
      'yes': '$name, $role, $state',
      'other': '$name, $state',
    });
    return '$_temp0';
  }

  @override
  String get kitBreadcrumb => 'Folder path';

  @override
  String kitBreadcrumbOpen(String folder) {
    return 'Open folder $folder';
  }

  @override
  String kitBreadcrumbOpenRoot(String root) {
    return 'Open $root';
  }

  @override
  String kitBreadcrumbCurrent(String folder) {
    return 'Current folder: $folder';
  }

  @override
  String kitBreadcrumbMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more folders',
      one: '1 more folder',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeCopyCode => 'Copy code';

  @override
  String get kitCodeCopyCommand => 'Copy command';

  @override
  String get kitCodeCopyOutput => 'Copy output';

  @override
  String get kitCodeCopyFailedCode => 'Could not copy code. Try again.';

  @override
  String get kitCodeCopyFailedCommand =>
      'Could not copy the command. Try again.';

  @override
  String get kitCodeCopyFailedOutput => 'Could not copy the output. Try again.';

  @override
  String kitCodeShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show all $count lines',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => 'Open full output';

  @override
  String get kitWrapLines => 'Wrap lines';

  @override
  String kitCodeChanges(int added, int removed) {
    return '$added added, $removed removed';
  }

  @override
  String get kitCodeEmpty => 'Empty';

  @override
  String kitTabLabel(String label, int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$label, $countString';
  }

  @override
  String kitQueuedTitle(int count) {
    return 'Waiting to send · $count';
  }

  @override
  String get kitQueuedOffline => 'Sends when you\'re back online';

  @override
  String get kitQueuedWaiting => 'Waiting to send';

  @override
  String get kitQueuedReachedServer => 'Reached the server';

  @override
  String get kitQueuedAfterReply => 'Sends after this reply';

  @override
  String get kitQueuedAddToTurn => 'Adds to this turn';

  @override
  String get kitQueuedUpdate => 'Update waiting';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count attachments',
      one: '1 attachment',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return 'Waiting message $index of $count: $text. $state';
  }

  @override
  String get kitQueuedActions => 'Message actions';

  @override
  String get kitLogTitle => 'Output';

  @override
  String get kitLogShowOutput => 'Show output';

  @override
  String get kitLogLive => 'Live';

  @override
  String kitLogQuiet(String age) {
    return 'Last line $age ago';
  }

  @override
  String kitLogQuietSeconds(int seconds) {
    return 'Last line $seconds s ago';
  }

  @override
  String get kitLogEnded => 'Ended';

  @override
  String kitLogEndedExit(String code) {
    return 'Ended · exit $code';
  }

  @override
  String get kitLogFailed => 'Failed';

  @override
  String kitLogFailedExit(String code) {
    return 'Failed · exit $code';
  }

  @override
  String get kitLogEmpty => 'No output yet';

  @override
  String kitLogNewLines(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new lines',
      one: '1 new line',
    );
    return '$_temp0';
  }

  @override
  String kitLogDropped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier lines not shown',
      one: '1 earlier line not shown',
    );
    return '$_temp0';
  }

  @override
  String get kitLogReadFailed => 'Couldn\'t read the output';

  @override
  String kitLogWarningLine(String line) {
    return 'Warning: $line';
  }

  @override
  String kitLogErrorLine(String line) {
    return 'Error: $line';
  }

  @override
  String get kitUntilOff => 'Until I turn it off';

  @override
  String get kitUntilConversation => 'For this conversation';

  @override
  String get kitUntilHour => 'For an hour';

  @override
  String get kitRiskTurnOn => 'Turn on';

  @override
  String get kitRiskNotNow => 'Not now';

  @override
  String get kitRiskTurnOff => 'Turn off';

  @override
  String get safetyDisconnectBody =>
      'Live updates stop and you return to the server list. The server keeps running and nothing on it changes.';

  @override
  String get safetyDisconnectBodyPhone =>
      'Live updates stop and you return to the server list. OpenCode keeps running on this phone, using battery, until you stop it.';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count messages waiting to send stay on this phone until you connect again.',
      one:
          '1 message waiting to send stays on this phone until you connect again.',
    );
    return '$_temp0';
  }

  @override
  String get quotaMonitorThreshold => 'Alert when used reaches';

  @override
  String get quotaMonitorSaving => 'Saving…';

  @override
  String get folderBrowserSlowTitle => 'Still reading this folder';

  @override
  String get folderBrowserSlowBody =>
      'Folders on this phone can take up to 15 seconds to list.';

  @override
  String get folderBrowserFirstProject => 'Name your first project';

  @override
  String kitChecklistNext(String step) {
    return 'next: $step';
  }

  @override
  String kitChecklistNeedsYou(String action) {
    return 'needs you, $action';
  }

  @override
  String get kitChecklistShowSteps => 'Show steps';

  @override
  String get kitChecklistHideSteps => 'Hide steps';

  @override
  String get kitRequestAllowOnce => 'Allow once';

  @override
  String get kitRequestReject => 'Reject';

  @override
  String get kitRequestApprove => 'Approve';

  @override
  String get kitRequestSendBack => 'Send back';

  @override
  String get kitRequestAnswer => 'Answer';

  @override
  String get kitRequestSend => 'Send';

  @override
  String get kitRequestReplyEmptyReason => 'Type a reply first.';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString more answers',
      one: '1 more answer',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => 'Expired · the agent stopped waiting';

  @override
  String kitRequestAge(String age) {
    return 'waiting $age';
  }

  @override
  String kitDiffFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
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

    return 'Change $indexString of $countString';
  }

  @override
  String get kitDiffPreviousChange => 'Previous change';

  @override
  String get kitDiffNextChange => 'Next change';

  @override
  String kitDiffLines(int start, int end) {
    final intl.NumberFormat startNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String startString = startNumberFormat.format(start);
    final intl.NumberFormat endNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String endString = endNumberFormat.format(end);

    return 'Lines $startString–$endString';
  }

  @override
  String kitDiffShowUnchanged(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $count unchanged lines',
      one: 'Show 1 unchanged line',
    );
    return '$_temp0';
  }

  @override
  String get kitDiffHideUnchanged => 'Hide unchanged lines';

  @override
  String kitDiffUnchangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unchanged lines',
    );
    return '$_temp0';
  }

  @override
  String get kitDiffNoChanges => 'No changes';

  @override
  String get kitDiffBinary => 'Binary file · not shown';

  @override
  String kitDiffRenamed(String path) {
    return 'Renamed from $path';
  }

  @override
  String get kitDiffAddedFile => 'New file';

  @override
  String get kitDiffDeletedFile => 'Deleted';

  @override
  String kitDiffTooBig(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Showing $shownString of $totalString lines';
  }

  @override
  String get kitDiffOpenAll => 'Open all';

  @override
  String kitDiffLineAdded(int number) {
    return 'Line $number added';
  }

  @override
  String kitDiffLineRemoved(int number) {
    return 'Line $number removed';
  }

  @override
  String get kitDiffComment => 'Comment';

  @override
  String get kitDiffAddToPrompt => 'Add to prompt';

  @override
  String get kitDiffCopyLines => 'Copy lines';

  @override
  String get kitDiffClearSelection => 'Clear selection';

  @override
  String kitDiffSelected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines selected',
      one: '1 line selected',
    );
    return '$_temp0';
  }

  @override
  String kitDiffCounts(int added, int removed) {
    return '$added added, $removed removed';
  }

  @override
  String get kitDiffLoadFailed => 'Couldn\'t load the changes';

  @override
  String kitDiffLine(int number) {
    return 'Line $number';
  }

  @override
  String kitBoardLane(String column, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'no tasks',
    );
    return '$column, $_temp0';
  }

  @override
  String kitBoardLaneLoading(String column) {
    return 'Loading $column';
  }

  @override
  String get kitMarkdownOpenFile => 'Open file';

  @override
  String kitMarkdownTable(int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: 'Table, $rows rows',
      one: 'Table, 1 row',
    );
    return '$_temp0';
  }

  @override
  String get kitToolNotRun => 'Not run';

  @override
  String get kitToolWaiting => 'Waiting';

  @override
  String get kitToolRunning => 'Running';

  @override
  String get kitToolWaitingForYou => 'Waiting for you';

  @override
  String get kitToolDone => 'Done';

  @override
  String get kitToolFailed => 'Failed';

  @override
  String get kitToolStopped => 'Stopped';

  @override
  String get kitToolBackground => 'Started in the background';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString seconds',
      one: '1 second',
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
      other: '$countString minutes',
      one: '1 minute',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => 'Open its conversation';

  @override
  String get kitViewerFind => 'Find in file';

  @override
  String kitViewerFindCount(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$indexString of $countString';
  }

  @override
  String get kitViewerFindNone => 'No matches';

  @override
  String get kitViewerFindPrevious => 'Previous match';

  @override
  String get kitViewerFindNext => 'Next match';

  @override
  String get kitViewerFindClose => 'Close find';

  @override
  String get kitViewerCopyContents => 'Copy contents';

  @override
  String get kitViewerShowSource => 'Show source';

  @override
  String get kitViewerEmpty => 'This file is empty';

  @override
  String kitViewerTruncated(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Showing the first $shownString of $totalString lines';
  }

  @override
  String get kitViewerPartial => 'Showing part of this file';

  @override
  String get kitViewerOpenAll => 'Open all';

  @override
  String get kitViewerCantShow => 'Can\'t show this file';

  @override
  String kitViewerCantShowBody(String type, String size) {
    return '$type · $size';
  }

  @override
  String get kitViewerUnknownType => 'Unknown type';

  @override
  String get kitViewerUnknownSize => 'size unknown';

  @override
  String kitViewerLoadFailed(String name) {
    return 'Couldn\'t open $name';
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

    return 'Page $pageString of $countString';
  }

  @override
  String kitViewerPageFailed(int page) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);

    return 'Couldn\'t show page $pageString';
  }

  @override
  String get kitCapServerAnyTitle => 'Server to work on';

  @override
  String get kitCapServerAnyWhy =>
      'There\'s no server yet. Set one up on this phone or connect a computer.';

  @override
  String get kitCapServerAnyEnable => 'Add a server';

  @override
  String get kitCapServerAnyOffer =>
      'Add a server to start working with an agent.';

  @override
  String get kitCapServerOc1Title => 'OpenCode 1 server';

  @override
  String get kitCapServerOc1Why =>
      'Needs this phone\'s own server or a computer running OpenCode 1.';

  @override
  String get kitCapServerOc2Title => 'OpenCode 2 server';

  @override
  String get kitCapServerOc2Why =>
      'Needs a server running OpenCode 2. This phone\'s server can switch to it.';

  @override
  String get kitCapServerOc2Enable => 'Switch to OpenCode 2';

  @override
  String get kitCapServerOc2Offer =>
      'This needs OpenCode 2. Switch this phone\'s server to it?';

  @override
  String get kitCapServerCodexTitle => 'Codex server';

  @override
  String get kitCapServerCodexWhy => 'Needs a computer running Codex.';

  @override
  String get kitCapServerCodexEnable => 'Connect Codex';

  @override
  String get kitCapServerCodexOffer =>
      'Connect a computer running Codex to use it here.';

  @override
  String get kitCapServerPaseoTitle => 'Claude Code or Pi';

  @override
  String get kitCapServerPaseoWhy =>
      'Needs Paseo, on a computer or on this phone.';

  @override
  String get kitCapServerPaseoEnable => 'Connect Paseo';

  @override
  String get kitCapServerPaseoOffer =>
      'Work with Claude Code or Pi. Connect Paseo?';

  @override
  String get kitCapPhoneBuiltinTitle => 'Server on this phone';

  @override
  String get kitCapPhoneBuiltinWhy =>
      'This phone has no server of its own yet.';

  @override
  String get kitCapPhoneBuiltinEnable => 'Set up this phone';

  @override
  String get kitCapPhoneBuiltinOffer =>
      'Run agents right on this phone. Set it up?';

  @override
  String get kitCapPhoneTermuxTitle => 'Server in Termux';

  @override
  String get kitCapPhoneTermuxWhy => 'Needs Termux on this phone.';

  @override
  String get kitCapPhoneTermuxEnable => 'Set up with Termux';

  @override
  String get kitCapPhoneTermuxOffer =>
      'Run this phone\'s server in Termux instead?';

  @override
  String get kitCapPhoneAnyTitle => 'Server on this phone';

  @override
  String get kitCapPhoneAnyWhy => 'Needs a server running on this phone.';

  @override
  String get kitCapModelAuthTitle => 'Model sign-in';

  @override
  String get kitCapModelAuthWhy =>
      'Sign in to a model provider so the agent can reply.';

  @override
  String get kitCapModelAuthEnable => 'Sign in to a model';

  @override
  String get kitCapModelAuthOffer =>
      'The agent needs a model to reply. Sign in to one?';

  @override
  String get kitCapTeamOnTitle => 'AI Team';

  @override
  String get kitCapTeamOnWhy => 'AI Team is off on this server.';

  @override
  String get kitCapTeamOnEnable => 'Turn on AI Team';

  @override
  String get kitCapTeamOnOffer =>
      'This server can also run an AI team. Turn it on?';

  @override
  String get kitCapTeamPhoneTitle => 'AI Team on this phone';

  @override
  String get kitCapTeamPhoneWhy => 'Runs only on this phone\'s own server.';

  @override
  String get kitCapTeamControlTitle => 'Team controls';

  @override
  String get kitCapTeamControlWhy =>
      'Answer this on the computer that runs the team.';

  @override
  String get kitCapTeamControlEnable => 'See how to set it up';

  @override
  String get kitCapTeamControlOffer =>
      'Control the team from here once the computer is set up. See how?';

  @override
  String get kitCapClaudeLocalTitle => 'Claude Code on this phone';

  @override
  String get kitCapClaudeLocalWhy =>
      'Needs Termux on this phone and a Claude subscription.';

  @override
  String get kitCapClaudeLocalEnable => 'Add Claude Code';

  @override
  String get kitCapClaudeLocalOffer =>
      'Add Claude Code to this phone? It needs a Claude subscription.';

  @override
  String get kitCapVoiceModelTitle => 'Voice typing';

  @override
  String get kitCapVoiceModelWhy => 'Needs a voice model on this phone.';

  @override
  String get kitCapVoiceModelEnable => 'Download voice model';

  @override
  String get kitCapVoiceModelOffer =>
      'Type by voice on this phone. Download a voice model?';

  @override
  String get kitCapMcpAnyTitle => 'Extra tools';

  @override
  String get kitCapMcpAnyWhy =>
      'This server can\'t add extra tools from the app.';

  @override
  String get kitCapMcpAnyEnable => 'Add a tool';

  @override
  String get kitCapMcpAnyOffer => 'Give the agent more tools. Add one?';

  @override
  String get kitCapProjectOpenTitle => 'Project';

  @override
  String get kitCapProjectOpenWhy => 'Choose a folder to work in first.';

  @override
  String get kitCapProjectOpenEnable => 'Choose a project';

  @override
  String get kitCapProjectOpenOffer =>
      'Choose a project folder to start working.';

  @override
  String get kitCapProjectGitTitle => 'Git project';

  @override
  String get kitCapProjectGitWhy => 'This folder isn\'t a Git project yet.';

  @override
  String get kitCapProjectGitEnable => 'Make this a Git project';

  @override
  String get kitCapProjectGitOffer =>
      'This needs a Git project. Make this folder one?';

  @override
  String get kitCapPermNotificationsTitle => 'Notifications';

  @override
  String get kitCapPermNotificationsWhy =>
      'Notifications are off for this app.';

  @override
  String get kitCapPermNotificationsEnable => 'Allow notifications';

  @override
  String get kitCapPermNotificationsOffer =>
      'Hear when an agent needs you or finishes. Allow notifications?';

  @override
  String get kitCapPermBatteryTitle => 'Running in the background';

  @override
  String get kitCapPermBatteryWhy =>
      'Android may stop the app while it\'s in the background.';

  @override
  String get kitCapPermBatteryEnable => 'Allow background running';

  @override
  String get kitCapPermBatteryOffer =>
      'Keep agents running when the app is closed?';

  @override
  String get kitCapPermCameraTitle => 'Camera';

  @override
  String get kitCapPermCameraWhy => 'Camera access is off for this app.';

  @override
  String get kitCapPermCameraEnable => 'Allow camera';

  @override
  String get kitCapPermCameraOffer =>
      'Scan pairing codes and add photos. Allow the camera?';

  @override
  String get kitCapPermMicTitle => 'Microphone';

  @override
  String get kitCapPermMicWhy => 'Microphone access is off for this app.';

  @override
  String get kitCapPermMicEnable => 'Allow microphone';

  @override
  String get kitCapPermMicOffer => 'Speak your prompts. Allow the microphone?';

  @override
  String get kitCapNetworkTailscaleTitle => 'Reach from anywhere';

  @override
  String get kitCapNetworkTailscaleWhy =>
      'Your phone and computer aren\'t on the same network.';

  @override
  String get kitCapNetworkTailscaleEnable => 'Set up Tailscale';

  @override
  String get kitCapNetworkTailscaleOffer =>
      'Reach your computer from anywhere with Tailscale. Set it up?';

  @override
  String get kitCapQuotaCollectorTitle => 'Remaining usage';

  @override
  String get kitCapQuotaCollectorWhy =>
      'This server doesn\'t report what\'s left of your plan.';

  @override
  String get kitCapQuotaCollectorEnable => 'See how to add it';

  @override
  String get kitCapQuotaCollectorOffer =>
      'See what\'s left of your plan here. Add it on the server?';

  @override
  String get kitCapAgentA2aTitle => 'Other agents';

  @override
  String get kitCapAgentA2aWhy => 'No other agents are added yet.';

  @override
  String get kitCapAgentA2aEnable => 'Add an agent';

  @override
  String get kitCapAgentA2aOffer =>
      'Work with agents from other apps. Add one?';

  @override
  String get kitCapFlagFileBrowsingTerminalTitle => 'Files and terminal';

  @override
  String get kitCapFlagFileBrowsingTerminalWhy =>
      'This server doesn\'t share its files or terminal.';

  @override
  String get kitCapFlagSessionDiffTitle => 'Review changes';

  @override
  String get kitCapFlagSessionDiffWhy =>
      'This server doesn\'t show the changes an agent made.';

  @override
  String get kitCapFlagServerCatalogTitle => 'Server settings';

  @override
  String get kitCapFlagServerCatalogWhy =>
      'This server doesn\'t share its providers, tools or commands.';

  @override
  String get kitCapFlagUsageStatisticsTitle => 'Spending';

  @override
  String get kitCapFlagUsageStatisticsWhy =>
      'This server doesn\'t report what was spent.';

  @override
  String get kitCapFlagStagedRevertSessionNotesTitle =>
      'Notes and step-by-step undo';

  @override
  String get kitCapFlagStagedRevertSessionNotesWhy =>
      'This server can\'t take notes for the agent or undo step by step.';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle =>
      'Isolated tasks and sharing';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy =>
      'This server can\'t run isolated tasks or share conversations.';

  @override
  String get kitCapFlagDevelopmentServicesTitle => 'Development services';

  @override
  String get kitCapFlagDevelopmentServicesWhy =>
      'This server can\'t start or stop development services.';

  @override
  String get kitCapFlagRemoteUpgradeTitle => 'Updating the server';

  @override
  String get kitCapFlagRemoteUpgradeWhy =>
      'This server can\'t be updated from the app.';

  @override
  String get kitCapFlagPromptAttachmentsTitle => 'Attach photos and files';

  @override
  String get kitCapFlagPromptAttachmentsWhy =>
      'This server can\'t take photos or files with a prompt.';

  @override
  String get kitHostThisPhone => 'this phone';

  @override
  String get kitHostTermux => 'Termux';

  @override
  String get kitHostOpenCode1 => 'computers with OpenCode 1';

  @override
  String get kitHostOpenCode2 => 'computers with OpenCode 2';

  @override
  String get kitHostCodex => 'Codex';

  @override
  String get kitHostPaseo => 'Paseo';

  @override
  String get kitHostDemo => 'the offline demo';

  @override
  String get kitHostOpenCode => 'computers with OpenCode';

  @override
  String kitCapNotOnHost(int count, String feature, String host) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$feature aren\'t available on $host',
      one: '$feature isn\'t available on $host',
    );
    return '$_temp0';
  }

  @override
  String kitCapWorksOn(String hosts) {
    return 'Works on $hosts';
  }

  @override
  String kitCapWhyElsewhere(String notHere, String worksOn) {
    return '$notHere. $worksOn.';
  }

  @override
  String kitCapAnd(String first, String last) {
    return '$first and $last';
  }

  @override
  String kitCapComma(String first, String next) {
    return '$first, $next';
  }

  @override
  String kitCapServerOnHost(String server, String host) {
    return '$server ($host)';
  }

  @override
  String get kitCapNotNow => 'Not now';

  @override
  String get desktopDropHint => 'Drop to attach';

  @override
  String get searchClaudeCodeGateTitle => 'Not on this device';

  @override
  String get searchClaudeCodeGateDevice =>
      'Claude Code runs on a phone only through Termux, which this device doesn\'t have. Run it on a computer with Paseo and add that computer as a server.';

  @override
  String get searchClaudeCodeGateDesktop =>
      'Claude Code on this phone is for Android phones with Termux. On a computer, run Claude Code with Paseo and add it as a server.';

  @override
  String get searchClaudeCodeGateServers => 'Open servers';

  @override
  String get activityDigestHidden => 'Digest hidden';

  @override
  String get activityOpenFailedTitle => 'Couldn\'t open conversation';

  @override
  String get activityLoading => 'Loading the Inbox';

  @override
  String get activityPickRequest => 'Pick a request';

  @override
  String get activityPickRequestDetail =>
      'Choose one from the list to answer it here.';

  @override
  String activityAllowOnceFailed(String reason) {
    return 'Not sent: $reason';
  }

  @override
  String get activitySendOffline => 'Reconnect to the server to answer.';

  @override
  String get activityLastSeenRunning => 'Last seen running';

  @override
  String get activityIfIgnored => 'The agent waits; nothing is lost.';

  @override
  String activityPermissionAnnouncement(String title) {
    return 'Permission needed: $title';
  }

  @override
  String activityFormAnnouncement(String title) {
    return 'Input requested: $title';
  }

  @override
  String get activityAnswerEveryQuestion => 'Answer every question first.';

  @override
  String get activitySending => 'Sending…';

  @override
  String activityQuestionProgress(int index, int total) {
    return 'Question $index of $total';
  }

  @override
  String get activityOwnAnswer => 'Or write your own answer';

  @override
  String get shortcutsPaletteSearch => 'Search commands and settings';

  @override
  String get shortcutsHelpAnywhere => 'Anywhere';

  @override
  String get shortcutsHelpConversation => 'In a conversation';

  @override
  String get homeShellProjectUnavailable => 'Project isn\'t available';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return '$server doesn\'t offer files, changes or code search. Connect to an OpenCode server to use them.';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return '$server has no project tools.';
  }

  @override
  String get workspaceDetailEmptyTitle => 'Choose a conversation';

  @override
  String get workspaceDetailEmptyBody =>
      'Open a conversation from the list to read and reply here.';

  @override
  String workspaceContextOn(String server) {
    return 'On $server';
  }

  @override
  String get workspaceContextCurrent => 'In use';

  @override
  String get workspaceContextNewProject => 'New project';

  @override
  String get workspaceContextRunsOn => 'Runs on';

  @override
  String get workspaceContextFolder => 'Folder';

  @override
  String get workspaceSessionSharedLink => 'Shared link';

  @override
  String workspaceArchiveFailed(String title) {
    return 'Couldn\'t archive “$title”. It is back in the list.';
  }

  @override
  String get workspaceShareCopiesLink =>
      'The link is copied once sharing starts.';

  @override
  String get workspaceDeleteSharedLink => 'Its shared link stops working.';

  @override
  String get workspaceChooserEnterPath => 'Enter a folder path';

  @override
  String get workspaceChooserRecentProjects => 'Open a project you used before';

  @override
  String get workspaceChooserLoadFailedTitle => 'Couldn\'t load your projects';

  @override
  String get workspaceChooserLoadFailedBody =>
      'You can still open a folder by its path.';

  @override
  String get managedWorkspacesRefresh => 'Refresh';

  @override
  String get managedWorkspacesDiscovered => 'Discovery finished';

  @override
  String get managedWorkspacesDiscoverFailed =>
      'Couldn’t discover environments';

  @override
  String get managedWorkspacesCreateFailed => 'Couldn’t create the environment';

  @override
  String managedWorkspacesOpenFailed(String name) {
    return 'Couldn’t open $name';
  }

  @override
  String managedWorkspacesRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String managedWorkspacesRemoveBody(String provider) {
    return 'The server asks $provider to delete this environment and what is in it.';
  }

  @override
  String get managedWorkspacesRemoveLeavesFirst =>
      'It is open now, so the app goes back to the project folder first.';

  @override
  String get managedWorkspacesRemoveHistoryStays =>
      'Conversations stay in history but can no longer open it.';

  @override
  String get managedWorkspacesRemoveAction => 'Remove';

  @override
  String managedWorkspacesRemoved(String name) {
    return '$name was removed';
  }

  @override
  String get managedWorkspacesProvider => 'Provider';

  @override
  String get managedWorkspacesProvidersFailed => 'Couldn’t load providers';

  @override
  String get managedWorkspacesNoProviderTitle => 'No provider set up';

  @override
  String get managedWorkspacesNoProviderBody =>
      'This server has no cloud environment provider. Add one to OpenCode’s config on the server, then refresh.';

  @override
  String managedWorkspacesEmptyBody(String project) {
    return 'Environments for $project appear here. Create one, or discover the ones a provider already has.';
  }

  @override
  String get managedWorkspacesLoadFailed => 'Couldn’t load cloud environments';

  @override
  String get managedWorkspacesCreating => 'Creating a cloud environment';

  @override
  String get managedWorkspacesCreatingBody =>
      'This usually takes a few minutes. It opens here when it’s ready.';

  @override
  String get managedWorkspacesCreateTakes =>
      'Creating one usually takes a few minutes. It opens here when it’s ready.';

  @override
  String get managedWorkspacesBranchLabel => 'Branch';

  @override
  String get managedWorkspacesBranchHelper =>
      'Leave empty to use the provider’s default branch.';

  @override
  String get managedWorkspacesInUse => 'In use';

  @override
  String get managedWorkspacesCopyId => 'Copy ID';

  @override
  String get projectHealthGitInitSupporting =>
      'Runs git init here. Nothing is committed.';

  @override
  String get projectHealthSetUp => 'Set up';

  @override
  String get projectHealthRunning => 'Running';

  @override
  String get projectHealthNotRunning => 'Not running';

  @override
  String projectHealthLineCounts(int added, int removed) {
    return '$added lines added, $removed removed';
  }

  @override
  String projectFolderCreateHelper(String directory) {
    return 'Made in $directory on this phone and opened as the project.';
  }

  @override
  String get projectFolderMissingTitle => 'Create this folder?';

  @override
  String get projectFolderCreateFailedTitle => 'Couldn’t create the folder';

  @override
  String get projectFolderOpenFailedTitle => 'Couldn’t open the folder';

  @override
  String get projectsOneFolderTitle => 'Server uses one folder';

  @override
  String servicesStarted(String name) {
    return '$name started';
  }

  @override
  String servicesRemoved(String name) {
    return '$name removed';
  }

  @override
  String servicesStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String servicesRestartTitle(String name) {
    return 'Restart $name?';
  }

  @override
  String servicesForgetTitle(String name) {
    return 'Forget $name\'s last run?';
  }

  @override
  String servicesRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get servicesRemoveRunningHint =>
      'Its command keeps running on the server, and this app can no longer stop it. Stop it first to end it.';

  @override
  String get servicesEmptyTitle => 'No dev commands yet';

  @override
  String get servicesOffline =>
      'The server is not answering. Commands cannot be started or checked until it reconnects.';

  @override
  String get servicesLogFailed => 'Could not read the log.';

  @override
  String get servicesProjectFolder => 'Project folder';

  @override
  String get servicesWorkspace => 'Environment';

  @override
  String get servicesNameRequired => 'Enter a name.';

  @override
  String get servicesDuplicateName =>
      'A service with this name already exists.';

  @override
  String get servicesCommandRequired => 'Enter a command, such as npm run dev.';

  @override
  String get servicesUrlInvalid =>
      'Enter an http or https address without a user name or password.';

  @override
  String get isolatedTaskProjectFolder => 'Project folder';

  @override
  String get isolatedTaskStageCreate => 'Making the copy';

  @override
  String get isolatedTaskStagePrepare => 'Running the project setup';

  @override
  String get isolatedTaskStageOpen => 'Opening the conversation';

  @override
  String get isolatedTaskUsually => 'Usually 1–3 minutes';

  @override
  String get savedPermissionsIntro =>
      'Actions the agent may take in this project without asking you first. Revoke one and the agent asks again.';

  @override
  String get savedPermissionsLoadFailed =>
      'Could not load the always allowed actions';

  @override
  String get savedPermissionsRevokeBody =>
      'The agent will ask you again the next time it wants to do this. Work that is already running keeps going.';

  @override
  String savedPermissionsRevokedDetail(String action) {
    return '$action now asks you first again.';
  }

  @override
  String get savedPermissionsDismiss => 'Dismiss';

  @override
  String get savedPermissionsCopyPattern => 'Copy pattern';

  @override
  String get savedPermissionsBusy => 'Wait for the current change to finish';

  @override
  String get savedPermissionsLoading => 'Loading always allowed actions';

  @override
  String get savedPermissionsAllResources =>
      'Anything this kind of action touches';

  @override
  String get settingsHubDetailEmpty => 'Choose a group of settings';

  @override
  String get notifyQuietStartPicker => 'Set when quiet hours start';

  @override
  String get notifyQuietEndPicker => 'Set when quiet hours end';

  @override
  String get notifyQuietSet => 'Set';

  @override
  String get notifyQuietAllDay =>
      'Start and end are the same, so notifications stay quiet all day.';

  @override
  String get notifySendingTest => 'Sending a test notification…';

  @override
  String get notifyNoServersTitle => 'No servers to watch';

  @override
  String get notifyNoServersDetail =>
      'Servers you save can be watched from here, so a request on one reaches you.';

  @override
  String get notifyDismiss => 'Dismiss';

  @override
  String get notifySaving => 'Saving';

  @override
  String get notifyMonitorDetails => 'How watching servers works';

  @override
  String get notifyRestartBackground => 'Restart the live connection';

  @override
  String get appearanceModeSystem => 'System';

  @override
  String get privacySharedSection => 'Shared with your server';

  @override
  String get privacySaving => 'Saving…';

  @override
  String get privacyDeleting => 'Deleting';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count queued prompts',
      one: 'Delete 1 queued prompt',
      zero: 'Delete queued prompts',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count drafts',
      one: 'Delete 1 draft',
      zero: 'Delete drafts',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => 'You said';

  @override
  String get kitMessageThinking => 'Thinking…';

  @override
  String get kitMessageThought => 'Thought';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count seconds',
      one: '1 second',
    );
    return 'Thought for $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutes',
      one: '1 minute',
    );
    return 'Thought for $_temp0';
  }

  @override
  String get kitMessageActions => 'Message actions';

  @override
  String get kitMessageNoticeFailed => 'Failed';

  @override
  String get kitRequestChooseOneReason => 'Choose at least one answer.';

  @override
  String get kitRequestSendAnswers => 'Send answers';

  @override
  String get serversRemoveBody =>
      'This phone forgets the server: its password, chosen model and agent, project and widget conversations.';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unsent drafts will be deleted',
      one: '1 unsent draft will be deleted',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext =>
      'You are connected to it: the app disconnects and shows your servers';

  @override
  String get serversRemoveServerKeeps =>
      'Nothing is deleted on the server or at your AI providers';

  @override
  String get guideStepTwoScan =>
      'Tap Add server, then Scan code and point the camera at the QR, or Paste code.';

  @override
  String get guideStepTwoPaste =>
      'Copy the printed code, then tap Add server and Paste code.';

  @override
  String get guidePhonePathTitle => 'Use this phone instead';

  @override
  String get guidePhonePathBody =>
      'Install OpenCode on this phone and use it here, no computer needed';

  @override
  String get pairingScannerAllowCamera => 'Allow camera';

  @override
  String get pairingScannerStarting => 'Opening the camera…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return 'A run is going on $current. Switching shows $target in this app; the run on $current keeps going.';
  }

  @override
  String get profileMonitorOpenFailedTitle => 'Couldn\'t open it';

  @override
  String get profileMonitorIfIgnored => 'The agent waits until you answer';

  @override
  String get serverSettingsRestartCommandLabel =>
      'Set up with the Linux service script?';

  @override
  String get serverSettingsRestartedIt => 'I restarted it';

  @override
  String serverSettingsUpgradeBody(
    String target,
    String server,
    String current,
  ) {
    return 'Installs OpenCode $target on $server (now $current) with the server’s own installer.';
  }

  @override
  String serverSettingsUpgradeKeepsRunning(String current) {
    return 'The server keeps running $current while it installs';
  }

  @override
  String serverSettingsUpgradeRestartAfter(String target) {
    return 'Restart the OpenCode process on its computer to use $target';
  }

  @override
  String get serverSettingsUpgradeKeepsData => 'Server data stays in place';

  @override
  String serverSettingsCopyUpdateCommands(String server) {
    return 'Copy update commands for $server';
  }

  @override
  String get serverSettingsAddressLabel => 'Address';

  @override
  String get tailscaleSetupAppTitle => 'Tailscale on this phone';

  @override
  String get tailscaleSetupVpnTitle => 'Sign in and connect';

  @override
  String get tailscaleSetupVpnSupporting =>
      'Sign in and connect. OpenCode can’t check this.';

  @override
  String get tailscaleSetupOpenFailed =>
      'Tailscale didn’t open. Open it from your launcher, then come back.';

  @override
  String get tailscaleSetupAddressHelper =>
      'Paste the HTTPS address Tailscale Serve printed.';

  @override
  String get tailscaleSetupGetApp => 'Get Tailscale';

  @override
  String get tailscaleSetupContinueReason =>
      'Enter your server’s address first.';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return 'Partly translated ($percent %)';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return 'Preview of $name';
  }

  @override
  String get appearancePickerPreviewIn => 'Preview in';

  @override
  String get appearancePickerInUse => 'In use now';

  @override
  String appearancePickerThemeApplied(String name) {
    return 'Theme set to $name';
  }

  @override
  String get teamDiscoveryCardTurnOnFailed =>
      'Could not turn the AI team on. Nothing changed. Try again.';

  @override
  String get teamDiscoveryCardTurningOn => 'Turning the AI team on…';

  @override
  String get serverSwitcherTitle => 'Servers';

  @override
  String get serverSwitcherCurrentMenu => 'Server actions';

  @override
  String get localAgentEntryStillStarting =>
      'Still starting · this can take a minute';

  @override
  String get localAgentEntryDidNotStart => 'Didn\'t start';

  @override
  String get localAgentEntryRemoving => 'Removing';

  @override
  String localAgentEntrySignedOut(String state) {
    return '$state · Not signed in to Claude';
  }

  @override
  String get formRendererFinishLater => 'Finish later';

  @override
  String get formRendererSending => 'Sending your answers…';

  @override
  String get formRendererChoose => 'Choose';

  @override
  String get formRendererChooseDate => 'Choose a date';

  @override
  String get formRendererChooseDateTime => 'Choose a date and time';

  @override
  String formRendererDateAndTime(String date, String time) {
    return '$date at $time';
  }

  @override
  String get formRendererUseDate => 'Use date';

  @override
  String get formRendererUseTime => 'Use time';

  @override
  String get filePreviewPdfIsolated =>
      'PDF pages don\'t render in this isolated view. Save the file to read it in a PDF app.';

  @override
  String get filePreviewCopyOriginal => 'Copy original file';

  @override
  String get filePreviewOpenInFiles => 'Open in Files';

  @override
  String get filePreviewViewMode => 'Show file as';

  @override
  String get filePreviewAttachFailed => 'Couldn\'t attach file';

  @override
  String get filePreviewSaveFailed => 'Couldn\'t save file';

  @override
  String get kitTurnStarting => 'Starting the model…';

  @override
  String kitTurnStillStarting(int seconds) {
    return 'Still waiting for the model · $seconds s';
  }

  @override
  String get kitTurnStopped => 'You stopped this reply.';

  @override
  String get kitTurnInterrupted =>
      'The connection dropped before this reply finished.';

  @override
  String get kitTurnCopy => 'Copy reply';

  @override
  String get kitTurnMore => 'More for this reply';

  @override
  String get kitTurnActions => 'Reply actions';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return 'Disconnect from $serverName';
  }

  @override
  String serverSettingsDisconnectDetail(String serverName) {
    return 'Stops live updates from $serverName. Conversations stay on $serverName; unsent messages stay on this phone until you reconnect.';
  }

  @override
  String get kitScannerStarting => 'Opening the camera…';

  @override
  String get kitScannerSlow => 'Still opening the camera';

  @override
  String get kitScannerPaused => 'Camera paused';

  @override
  String get kitScannerPreview => 'Camera view';

  @override
  String get kitDateSet => 'Set date';

  @override
  String get kitTimeSet => 'Set time';

  @override
  String get kitDateTimeSet => 'Set';

  @override
  String get kitDateType => 'Type a date';

  @override
  String get kitDateCalendar => 'Show calendar';

  @override
  String kitDateFormatHint(String example) {
    return 'e.g. $example';
  }

  @override
  String get kitDateField => 'Date';

  @override
  String get kitDateInvalid => 'Not a date';

  @override
  String kitDateOutOfRange(String first, String last) {
    return 'Pick a date between $first and $last';
  }

  @override
  String get kitTimeHour => 'Hour';

  @override
  String get kitTimeMinute => 'Minute';

  @override
  String get kitTimePeriod => 'Morning or afternoon';

  @override
  String get kitTimeInvalid => 'Not a time';

  @override
  String get kitDateTimeNotSet => 'Not set';

  @override
  String kitDateTimeClear(String title) {
    return 'Clear $title';
  }

  @override
  String get kitDateUnavailable => 'That day can’t be chosen';

  @override
  String get filesLoadingFolder => 'Opening folder…';

  @override
  String get filesSearching => 'Searching…';

  @override
  String get filesShowHidden => 'Show hidden files';

  @override
  String get filesOnlyHidden =>
      'This folder has only hidden files and folders.';

  @override
  String get filesCopyName => 'Copy name';

  @override
  String globalSessionsMoveTitle(String project) {
    return 'Move to $project?';
  }

  @override
  String globalSessionsMoveBody(String title, String from, String to) {
    return '“$title” moves from $from to $to through the server’s sync system.';
  }

  @override
  String get globalSessionsMoveWhileWorking =>
      'It is working now. Moving it may interrupt the current step.';

  @override
  String globalSessionsMoveBack(String project) {
    return 'To move it back, open $project and choose Continue here in All conversations.';
  }

  @override
  String get globalSessionsFilterLabel => 'Show';

  @override
  String get globalSessionsFilterActive => 'Active';

  @override
  String get globalSessionsArchivedNoMatchMessage =>
      'No archived conversation has that title. Try a shorter search.';

  @override
  String get globalSessionsArchivedEmptyTitle => 'No archived conversations';

  @override
  String get globalSessionsArchivedEmptyMessage =>
      'Conversations you archive in Work appear here.';

  @override
  String get globalSessionsShowActive => 'Show active conversations';

  @override
  String globalSessionsProjectInUse(String project) {
    return '$project · In use';
  }

  @override
  String get globalSessionsCopyFolder => 'Copy folder path';

  @override
  String get worktreesStartConversation => 'New conversation here';

  @override
  String get worktreesCreateHelper =>
      'OpenCode makes a separate branch and folder and runs the project’s startup tasks. Spaces become dashes.';

  @override
  String get worktreesFolder => 'Folder';

  @override
  String get worktreesMainCopy => 'Main copy';

  @override
  String get worktreesCopyFolder => 'Copy folder path';

  @override
  String get worktreesLoadFailedTitle => 'Couldn\'t load worktrees';

  @override
  String get worktreesSetupFailedWord => 'Setup failed';

  @override
  String get importNeedsFile => 'Choose a JSON file first.';

  @override
  String get importNeedsDestination => 'Choose where to import it first.';

  @override
  String get importFileLabel => 'File';

  @override
  String get importPreviewLabel => 'Conversation';

  @override
  String importMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count messages',
      one: '1 message',
    );
    return '$_temp0';
  }

  @override
  String get importNoDestinationsTitle => 'Nowhere to import';

  @override
  String importOnServer(String server) {
    return 'On $server';
  }

  @override
  String get importChangeDestinationShort => 'Change';

  @override
  String get importConversationId => 'Conversation ID';

  @override
  String get importParentId => 'Parent conversation ID';

  @override
  String get importFolder => 'Folder';

  @override
  String get importEnvironmentId => 'Cloud environment ID';

  @override
  String get phoneSetupStartUseTermuxOne => 'Use the one in Termux';

  @override
  String get phoneSetupStartUseTermuxOneDetail =>
      'OpenCode is also set up in Termux. Connect to it instead.';

  @override
  String get phoneSetupStartTermuxNotAllowed =>
      'Termux is installed but hasn\'t let this app in yet. Finish its setup.';

  @override
  String get phoneSetupCustomizeAllInstalled =>
      'Every optional tool is already on this phone.';

  @override
  String get phoneSetupCustomizeIncluded => 'Required';

  @override
  String get termuxProcsLoadFailedTitle => 'Couldn\'t read what\'s running';

  @override
  String get termuxProcsEmptyBody =>
      'When OpenCode, the AI Team or a build runs here, it shows up in this list.';

  @override
  String get termuxProcsNotStoppedTitle => 'Not everything stopped';

  @override
  String get termuxProcsCopyCommand => 'Copy command';

  @override
  String get termuxProcsOpenControls => 'Open This phone';

  @override
  String get termuxProcsProcessId => 'Process ID';

  @override
  String get termuxProcsParentId => 'Parent process ID';

  @override
  String get termuxProcsAboutOpenCode =>
      'Part of the OpenCode server on this phone.';

  @override
  String get termuxProcsAboutAiTeam =>
      'Part of the AI Team. Stopping it stops the work the team is doing.';

  @override
  String get termuxProcsAboutBuild =>
      'A build helper. The next build starts it again when it needs it.';

  @override
  String get termuxProcsAboutOrphan =>
      'Nothing is waiting on it, so stopping it is safe.';

  @override
  String get termuxProcsAboutOther =>
      'Started by something else on this phone.';

  @override
  String get termuxProcsNoRestart => 'It can\'t be started again from here.';

  @override
  String get termuxProcsStopGroupTeamLost =>
      'Any task the team is working on stops too.';

  @override
  String get termuxProcsStopGroupTeamRestart =>
      'You can start the team again from AI Team.';

  @override
  String get phoneSetupProgressStopContinueLater =>
      'Continue any time from On this phone.';

  @override
  String teamMergeConfirmTask(String title) {
    return 'Task: $title';
  }

  @override
  String get teamMergeFailedNext =>
      'Nothing was merged. Fix what the host says, then try again, or review the changes.';

  @override
  String get teamStartRunRefusedKept =>
      'Your task is still here. Edit it and send it again.';

  @override
  String get transcriptTogglesReasoningOn =>
      'When on, the model\'s reasoning opens under each answer.';

  @override
  String get transcriptTogglesUsageOn =>
      'When on, each message shows its time, tokens and cost.';

  @override
  String get transcriptTogglesScope =>
      'These apply to every conversation on this device.';

  @override
  String get handoffSheetCopyCommand => 'Copy command';

  @override
  String get handoffSheetReloadConversation => 'Try again';

  @override
  String get handoffSheetPhoneServerNote =>
      'If the other phone does not have this server saved yet, it says so and offers to open Servers so you can add it.';

  @override
  String get modelPickerChooseFirst => 'Choose a model first.';

  @override
  String get modelPickerThinking => 'Thinking';

  @override
  String get modelPickerAgentBuild => 'Edits files and runs commands';

  @override
  String get modelPickerAgentPlan => 'Reads and plans; does not change files';

  @override
  String modelPickerDetailsOutput(String count) {
    return 'Up to $count tokens per answer';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '$input per million tokens read, $output per million written';
  }

  @override
  String get modelPickerCanThink => 'Thinks before answering';

  @override
  String get modelPickerCanUseTools => 'Uses tools';

  @override
  String get modelPickerCanReadAttachments =>
      'Reads images and files you attach';

  @override
  String get modelPickerCopyId => 'Copy model id';

  @override
  String get modelPickerInUse => 'In use';

  @override
  String get modelPickerUnavailableReason =>
      'Not available on this server right now.';

  @override
  String get modelPickerCollections => 'Which models to show';

  @override
  String get modelPickerSignInTitle => 'Provider sign-in needed';

  @override
  String get modelPickerSignInBody =>
      'No provider on this server has models yet. Sign in to one, then come back to choose a model.';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $count more models',
      one: 'Show 1 more model',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'Build';

  @override
  String get modelPickerAgentPlanName => 'Plan';

  @override
  String phoneServerCardDisconnect(String server) {
    return 'Disconnect from $server';
  }

  @override
  String get phoneServerCardStartOpenCode => 'Start OpenCode';

  @override
  String get phoneServerCardStopOpenCode => 'Stop OpenCode on this phone';

  @override
  String get phoneServerCardShowServerLog => 'Show server log';

  @override
  String get phoneServerCardOpenTerminal => 'Open terminal';

  @override
  String get phoneServerCardFailedTitle => 'Could not finish';

  @override
  String get phoneServerRestartFailedTitle => 'Restart failed';

  @override
  String get setupTerminalTitle => 'Setup output';

  @override
  String get teamPhoneStopTeam => 'Stop the team';

  @override
  String get teamPhoneStartTeam => 'Start the team';

  @override
  String get teamPhoneStartTeamAgain => 'Start the team again';

  @override
  String get teamPhoneDeleteTeam => 'Delete the team from this phone';

  @override
  String get teamPhoneRemoveBody =>
      'The team stops, and the AI Team turns off for this server.';

  @override
  String get teamPhoneRemoveLost =>
      'The team\'s programs, its files and its task list are deleted';

  @override
  String get teamPhoneRemoveKept =>
      'Your project files and their git history stay';

  @override
  String teamPhoneRemoveFrees(int size) {
    return 'Frees about $size MB';
  }

  @override
  String get teamPhoneRemoveConfirm => 'Delete the team';

  @override
  String get productStatesActionFailedTitle => 'Couldn\'t finish that';

  @override
  String get productStatesSwitchServer => 'Switch server';

  @override
  String get externalLinkBlockedTitle => 'Link blocked';

  @override
  String get externalLinkBlockedBody =>
      'This app opens only https:// links, and http:// links after you confirm.';

  @override
  String externalLinkOpensHost(String host) {
    return 'Opens $host outside this app.';
  }

  @override
  String get externalLinkDontOpen => 'Don\'t open';

  @override
  String get externalLinkCopy => 'Copy link';

  @override
  String get externalLinkAddress => 'Full address';

  @override
  String get externalLinkOpenFailedTitle => 'Couldn\'t open link';

  @override
  String get runCommandReconnecting =>
      'OpenCode is reconnecting. Try again in a moment.';

  @override
  String runCommandArgumentsHelper(String command) {
    return 'Text passed to /$command. Leave it empty if the command takes none.';
  }

  @override
  String get runCommandRunsIn => 'Runs in';

  @override
  String runCommandFailedTitle(String command) {
    return 'Couldn\'t run /$command';
  }

  @override
  String get teamNowWakeRefusedNoReason => 'The host didn\'t say why.';

  @override
  String get teamHostFormTeamLabel => 'Team name (optional)';

  @override
  String get teamHostFormTeamHelper =>
      'Leave it empty to use the team the computer runs.';

  @override
  String get teamHostFormHowAction => 'How to set up the computer';

  @override
  String get teamHostFormCancelTest => 'Cancel test';

  @override
  String get teamHostFormSaveAnyway => 'Save the address anyway';

  @override
  String get teamHostFormSaveAnywayNote =>
      'AI Team shows the team as not answering until the computer answers.';

  @override
  String get teamHostFormConnectionDetails => 'Connection details';

  @override
  String teamAgentScreenPause(String agent) {
    return 'Pause $agent';
  }

  @override
  String teamAgentScreenPaused(String agent) {
    return 'Paused $agent';
  }

  @override
  String teamAgentScreenResume(String agent) {
    return 'Start $agent again';
  }

  @override
  String teamAgentScreenNudge(String agent) {
    return 'Nudge $agent';
  }

  @override
  String teamAgentScreenRestart(String agent) {
    return 'Restart $agent';
  }

  @override
  String teamAgentScreenStop(String agent) {
    return 'Stop $agent';
  }

  @override
  String teamAgentScreenStopBody(String agent, String task) {
    return '$agent stops working on “$task” now. The task stays on the host, and you can start $agent again from this page.';
  }

  @override
  String teamAgentScreenStoppedTitle(String agent) {
    return '$agent is stopped';
  }

  @override
  String get teamAgentScreenStoppedBody =>
      'Its work stays where it is. Start it again when you want it back.';

  @override
  String teamAgentScreenCrashedTitle(String agent) {
    return '$agent stopped unexpectedly';
  }

  @override
  String get teamAgentScreenCrashedBody =>
      'Its session ended on its own. Start it again to pick its work up from the host.';

  @override
  String get teamAgentScreenRecyclingBody =>
      'It starts a fresh session soon and picks its work up from the host.';

  @override
  String teamAgentScreenModelFrom(String model, String provider) {
    return '$model from $provider';
  }

  @override
  String teamAgentScreenGateIfIgnored(String agent) {
    return '$agent waits until you answer';
  }

  @override
  String teamAgentScreenControlsElsewhere(String agent) {
    return 'This phone can\'t pause, stop or message $agent on this host yet. Run the team\'s host front on the computer to control it from here.';
  }

  @override
  String get gateSheetDestructiveBody =>
      'The host marks this action as destructive. Approving it can\'t be undone from the phone.';

  @override
  String get gateSheetAnswerLabel => 'Your answer';

  @override
  String get gateSheetAfterAnswer =>
      'The team carries on as soon as the host confirms your answer.';

  @override
  String get gateSheetFixIt => 'Ask the team to fix it';

  @override
  String gateSheetFixItDetail(String agent) {
    return 'Sends the error to $agent and asks it to find the cause and carry on.';
  }

  @override
  String gateSheetFixRequest(String task, String error) {
    return 'The task “$task” failed with this error:\n$error\nPlease find the cause, fix it and carry on.';
  }

  @override
  String gateSheetOpenAgent(String agent) {
    return 'Open $agent\'s page';
  }

  @override
  String get teamIntroTurnOnPhone => 'Turn on AI Team on this phone';

  @override
  String get teamIntroInstalledTitle => 'Installed on this phone';

  @override
  String get teamIntroInstalledBody =>
      'It is not turned on yet. Turning it on starts the team for your project; nothing more to download.';

  @override
  String get teamIntroSetUpPhone => 'Set up AI Team on this phone';

  @override
  String teamIntroSetUpOn(String server) {
    return 'Set up AI Team on $server';
  }

  @override
  String teamIntroTurnOn(String server) {
    return 'Turn on AI Team on $server';
  }

  @override
  String get teamIntroCostTitle => 'Before you set it up';

  @override
  String get teamIntroCostTime => 'About 8–10 minutes the first time';

  @override
  String get teamIntroCostMemory => 'About 550 MB of memory for each worker';

  @override
  String get teamAgentScreenLabelId => 'Agent id';

  @override
  String get gateSheetSendNeedsText => 'Type an answer first';

  @override
  String teamAgentsChecked(String age) {
    return 'checked $age ago';
  }

  @override
  String get teamWorkSheetMissingTitle => 'Work item gone';

  @override
  String get teamWorkSheetMissingBody =>
      'It may have been finished or removed. Close this sheet to see the task as it is now.';

  @override
  String get teamWorkSheetNotOnHost => 'No longer listed';

  @override
  String get teamWorkSheetOpenStepConversation =>
      'Open this step\'s conversation';

  @override
  String teamWorkSheetOpenAgentConversation(String name) {
    return 'Open $name\'s conversation';
  }

  @override
  String get usageRangeLabel => 'Time range';

  @override
  String get usageAboutNumbers => 'About these numbers';

  @override
  String get usageBudgetHelperUsd =>
      'In US dollars for this range. You’re told when the report reaches it; nothing is stopped.';

  @override
  String get usageBudgetHelperTokens =>
      'Whole tokens for this range. You’re told when the report reaches it; nothing is stopped.';

  @override
  String get usageBudgetClearConfirm => 'Clear budgets';

  @override
  String get usageBudgetNotSet => 'Not set';

  @override
  String get usageBudgetWaitReason => 'Available once usage has loaded.';

  @override
  String get usageBudgetUsdTitle => 'USD budget';

  @override
  String get usageBudgetTokensTitle => 'Token budget';

  @override
  String get agentAccountScopeLostTitle => 'This server changed';

  @override
  String get agentAccountBackToServers => 'Back to Servers';

  @override
  String get agentAccountNotConnected =>
      'Connect to this server to see its Codex account.';

  @override
  String get agentAccountSignInMethod => 'Signed in with';

  @override
  String get agentAccountPlanTitle => 'Plan';

  @override
  String get agentAccountCopyCode => 'Copy sign-in code';

  @override
  String agentAccountLimitReached(String reset) {
    return 'You’ve reached a Codex limit. $reset';
  }

  @override
  String get agentAccountResetDue => 'Resets any moment';

  @override
  String agentAccountResetInDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Resets in $days days',
      one: 'Resets in 1 day',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInHours(int hours) {
    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Resets in $hours h',
      one: 'Resets in 1 h',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Resets in $minutes min',
      one: 'Resets in 1 min',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetWhen(String relative, String time) {
    return '$relative ($time)';
  }

  @override
  String get reviewWorkspaceScopes => 'Changes to show';

  @override
  String get reviewWorkspaceRefreshFailed => 'Couldn\'t refresh the changes';

  @override
  String get reviewWorkspaceSlowTitle => 'Still reading the changes';

  @override
  String get reviewWorkspaceSlowBody =>
      'The server runs git to compare the files. A big project can take a minute.';

  @override
  String get reviewWorkspaceAllViewedTitle => 'You\'ve seen every file';

  @override
  String reviewWorkspaceAllViewedMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count notes are on the prompt, ready to send from the conversation.',
      one: '1 note is on the prompt, ready to send from the conversation.',
    );
    return '$_temp0';
  }

  @override
  String get reviewWorkspaceBackToChat => 'Back to the conversation';

  @override
  String reviewWorkspaceCommentOnFile(String file) {
    return 'Comment on $file';
  }

  @override
  String reviewWorkspaceAddFileToPrompt(String file) {
    return 'Add $file to the prompt';
  }

  @override
  String get reviewWorkspaceAddComment => 'Add comment to prompt';

  @override
  String get reviewWorkspaceCommentEmpty => 'Type a comment first.';

  @override
  String get reviewWorkspaceCommentLabel => 'Your comment';

  @override
  String get reviewWorkspaceCommentHint =>
      'What should the agent check or change?';

  @override
  String get reviewWorkspaceCommentHelper =>
      'Kept if you close this, until you add it.';

  @override
  String get integrationsMcpTitle => 'MCP servers';

  @override
  String get integrationsMcpServersLabel => 'MCP servers';

  @override
  String integrationsModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count models',
      one: '1 model',
    );
    return '$_temp0';
  }

  @override
  String integrationsProviderActions(String name) {
    return '$name actions';
  }

  @override
  String integrationsManageAccounts(String name) {
    return 'Manage $name accounts';
  }

  @override
  String integrationsServerSignIn(String name) {
    return 'Sign in to $name on the server';
  }

  @override
  String get integrationsServerSignInUnavailable =>
      'This server can\'t run a sign-in command from the app.';

  @override
  String integrationsDisconnectNamed(String name) {
    return 'Disconnect $name';
  }

  @override
  String integrationsDisconnectBody(String name) {
    return 'Removes the $name key from this server. A reply already running finishes first.';
  }

  @override
  String get integrationsConnectMethodSubtitle => 'Choose how to connect';

  @override
  String get integrationsKeyHelper =>
      'The key is stored on this server. The app never shows it again.';

  @override
  String get integrationsKeyEmpty => 'Paste the key first.';

  @override
  String get integrationsKeyRejected =>
      'The server didn\'t accept this key. Check it and try again.';

  @override
  String integrationsSignInAtHost(String host) {
    return 'Sign in at $host?';
  }

  @override
  String get integrationsSignInBody =>
      'Approve access in your browser, then come back to this app.';

  @override
  String integrationsSignInInstructions(String instructions) {
    return 'The server says: $instructions';
  }

  @override
  String get integrationsFinishSignInTitle => 'Finish signing in';

  @override
  String get integrationsFinishSignInAction => 'Finish signing in';

  @override
  String get integrationsFinishSignInEmpty => 'Paste the code first.';

  @override
  String get integrationsFinishSignInMcpHelper =>
      'Paste the address your browser ended on after you approved access, or the code it showed.';

  @override
  String get integrationsFinishSignInProviderHelper =>
      'Paste the code the sign-in page showed after you approved access.';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return 'Open $name sign-in';
  }

  @override
  String get integrationsCancelSignIn => 'Cancel sign-in';

  @override
  String get integrationsPendingNotRecoverable =>
      'Keep this screen open until you finish: this server can\'t resume a sign-in after you leave.';

  @override
  String integrationsMcpActions(String name) {
    return '$name actions';
  }

  @override
  String integrationsMcpSignIn(String name) {
    return 'Sign in to $name';
  }

  @override
  String integrationsMcpReconnect(String name) {
    return 'Reconnect $name';
  }

  @override
  String integrationsMcpSigningIn(String name) {
    return 'Signing in to $name';
  }

  @override
  String get integrationsMcpSignInOnServer =>
      'Sign in on the server\'s computer; this server can\'t do it from the app.';

  @override
  String integrationsMcpRemoveUntilRestart(String name) {
    return 'Remove $name until restart';
  }

  @override
  String integrationsMcpRemoveTitle(String name) {
    return 'Remove $name until restart?';
  }

  @override
  String get integrationsMcpRemoveBody =>
      'Its tools stop working in this project now. If it\'s in the server\'s configuration, it comes back when the server restarts.';

  @override
  String get integrationsMcpRemoveConfirm => 'Remove until restart';

  @override
  String get integrationsCopyResourceAddress => 'Copy address';

  @override
  String get terminalScreenSourceLabel => 'Where the shell runs';

  @override
  String get terminalScreenNameLabel => 'Name';

  @override
  String get terminalScreenRenameConfirm => 'Rename';

  @override
  String get terminalScreenNameEmpty => 'Type a name.';

  @override
  String terminalScreenStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get terminalScreenStopBody =>
      'The program and everything it started stop, and the terminal goes away. Its output can\'t be brought back.';

  @override
  String terminalScreenRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get terminalScreenRemoveBody =>
      'The terminal and its output go away. This can\'t be undone.';

  @override
  String get terminalScreenStopConfirm => 'Stop terminal';

  @override
  String get terminalScreenRemoveConfirm => 'Remove terminal';

  @override
  String get terminalScreenCreateFailed => 'Couldn\'t start a terminal';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove $count ended terminals',
      one: 'Remove 1 ended terminal',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove $count ended terminals?',
      one: 'Remove 1 ended terminal?',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody =>
      'Their output goes away too. Running terminals stay.';

  @override
  String get terminalScreenUsePhone => 'Use this phone\'s terminal';

  @override
  String terminalScreenRowRunning(String command) {
    return 'Running · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return 'Ended · code $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return 'Ended · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return 'Actions for $name';
  }

  @override
  String terminalScreenOpen(String name) {
    return 'Open $name';
  }

  @override
  String terminalScreenRename(String name) {
    return 'Rename $name';
  }

  @override
  String terminalScreenStop(String name) {
    return 'Stop $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return 'Remove $name';
  }

  @override
  String get terminalScreenLoading => 'Loading terminals';

  @override
  String get terminalScreenPaused =>
      'Paused while the app is in the background';

  @override
  String get terminalScreenConnecting => 'Connecting to the terminal';

  @override
  String get terminalScreenCopy => 'Copy output';

  @override
  String terminalScreenPaste(String name) {
    return 'Paste into $name';
  }

  @override
  String get terminalScreenDetails => 'Terminal details';

  @override
  String terminalScreenDetailsTitle(String name) {
    return '$name details';
  }

  @override
  String get terminalScreenDetailCommand => 'Command';

  @override
  String get terminalScreenDetailFolder => 'Folder';

  @override
  String get terminalScreenDetailPid => 'Process id';

  @override
  String get terminalScreenDetailExit => 'Exit code';

  @override
  String localTerminalStopNamedTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String localTerminalPasteNamed(String name) {
    return 'Paste into $name';
  }

  @override
  String get localTerminalCopySelection => 'Copy selection';

  @override
  String defaultShellOnlyOne(String name) {
    return '$name · the only shell this server offers';
  }

  @override
  String defaultShellSaveFailed(String error) {
    return 'Couldn\'t change the shell. $error Tap to try again.';
  }

  @override
  String get terminalScreenReadableMode => 'Show as readable text';

  @override
  String get terminalScreenLiveMode => 'Show as live terminal';

  @override
  String get localTerminalSetUpLinux => 'Set up Linux on this phone';

  @override
  String get messageViewSendAgain => 'Send this message again';

  @override
  String get messageViewContinueReply => 'Continue this reply';

  @override
  String get reviewRunResultsLoadingTitle => 'Loading run results';

  @override
  String get reviewRunResultsErrorTitle => 'Couldn\'t load run results';

  @override
  String get reviewRunResultsErrorBody =>
      'The server didn\'t send this run\'s history.';

  @override
  String get reviewRunResultsEmptyTitle => 'Nothing to show yet';

  @override
  String get reviewRunResultsScopeChangedTitle => 'The project changed';

  @override
  String get reviewRunResultsCloseAction => 'Close run results';

  @override
  String get reviewRunResultsRunningNotice =>
      'Still running. This shows what it has done so far; pull down for the latest.';

  @override
  String get reviewRunResultsRefreshFailed =>
      'Couldn\'t refresh. This is what was loaded before.';

  @override
  String get reviewRunResultsReviewChanges => 'Review changed files';

  @override
  String get reviewRevertSheetTitle => 'Undo from this prompt?';

  @override
  String get reviewRevertSheetBody =>
      'This prompt and everything after it are hidden while you review. Nothing is final until you choose.';

  @override
  String get reviewRevertPromptLabel => 'From this prompt';

  @override
  String get reviewRevertFilesToggle => 'Put files back too';

  @override
  String get reviewRevertFilesToggleHint =>
      'Files go back to how they were before this prompt.';

  @override
  String get reviewRevertSheetAction => 'Undo and review';

  @override
  String get reviewRevertStageFailed =>
      'Couldn\'t set up the undo. Nothing was hidden.';

  @override
  String get reviewRevertScreenTitle => 'Review the undo';

  @override
  String get reviewRevertScreenIntro =>
      'This prompt and everything after it are hidden. Nothing is final until you choose below.';

  @override
  String get reviewRevertFilesLabel => 'Files in this undo';

  @override
  String get reviewRevertNoFiles => 'No files change with this undo.';

  @override
  String reviewRevertFileLines(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String reviewRevertFileSupporting(String folder, String lines) {
    return '$folder · $lines';
  }

  @override
  String get reviewRevertRestoreTitle => 'Put everything back';

  @override
  String get reviewRevertKeepTitle => 'Delete the hidden messages';

  @override
  String get reviewRevertKeepConfirmTitle => 'Delete hidden messages forever?';

  @override
  String get reviewRevertKeepConfirmBody => 'This can\'t be undone.';

  @override
  String get reviewRevertKeepConfirmAction => 'Delete hidden messages';

  @override
  String get reviewRevertKeepConsequenceMessages =>
      'The hidden prompt and every message after it are deleted';

  @override
  String get reviewRevertKeepConsequenceFiles => 'Files stay as they are now';

  @override
  String get reviewRevertRestoreConfirmTitle => 'Put everything back?';

  @override
  String get reviewRevertRestoreConfirmBody =>
      'The hidden messages come back, and the files in this undo return to how they were when you set it up. You can undo from a prompt again later.';

  @override
  String get reviewRevertRestoreConsequenceMessages =>
      'The hidden messages come back';

  @override
  String reviewRevertRestoreConsequenceFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files are replaced, with any edits made since',
      one: '1 file is replaced, with any edits made since',
    );
    return '$_temp0';
  }

  @override
  String get reviewRevertRestoreConsequenceUnknownFiles =>
      'Files in this undo are replaced, with any edits made since';

  @override
  String get reviewRevertStaleTitle => 'The undo changed';

  @override
  String get reviewRevertNoneTitle => 'Nothing to review';

  @override
  String get reviewRevertNoneBody =>
      'There\'s no undo waiting in this conversation.';

  @override
  String get reviewRevertBackAction => 'Back to the conversation';

  @override
  String get reviewRevertKeptTitle => 'Undo kept';

  @override
  String get reviewRevertKeptBody =>
      'The hidden messages are deleted. Files stay as they are.';

  @override
  String get reviewRevertRestoredTitle => 'Everything is back';

  @override
  String get reviewRevertRestoredBody =>
      'The messages and files are back as they were.';

  @override
  String get reviewRevertFailed =>
      'That didn\'t finish. Check the conversation, then try again.';

  @override
  String get perfTraceClearTimings => 'Clear timings';

  @override
  String appDiagnosticsClearTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Clear $count errors?',
      one: 'Clear 1 error?',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'The $count errors kept on this phone are removed, also from the saved report. This can\'t be undone.',
      one:
          'The error kept on this phone is removed, also from the saved report. This can\'t be undone.',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Clear $count errors',
      one: 'Clear 1 error',
    );
    return '$_temp0';
  }

  @override
  String get capabilityStateHere => 'Works here';

  @override
  String get capabilityStateNotServer => 'Not on this server';

  @override
  String get capabilityStateNotDevice => 'Not on this device';

  @override
  String get capabilityNeedsAndroid => 'Needs the Android app';

  @override
  String capabilityAvailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count features work here',
      one: '1 feature works here',
    );
    return '$_temp0';
  }

  @override
  String get capabilityAvailableCountDetail =>
      'Show what this server and device can do';

  @override
  String get capabilityAddServer => 'Add a server that has these';

  @override
  String get capabilityAddServerDetail =>
      'Connect another computer or set one up on this phone, then switch to it';

  @override
  String get keepRunningAllSetTitle => 'You\'re set';

  @override
  String get keepRunningAllSetBody =>
      'Android leaves the app running in the background. There is nothing else to allow on this phone.';

  @override
  String get keepRunningDailyLimit =>
      'On Android 15 and newer, Android allows background syncing for about 6 hours a day, even with everything here allowed. After that the app pauses in the background until you open it.';

  @override
  String get aboutTitle => 'About';

  @override
  String get aboutCopyVersion => 'Copy version';

  @override
  String get aboutCheckUpdates => 'Check for updates';

  @override
  String get aboutUpdateIdle => 'Looks for a newer version of this app';

  @override
  String get aboutUpdateChecking => 'Checking…';

  @override
  String get aboutUpdateCurrent => 'You have the latest version';

  @override
  String get aboutUpdateDownloading => 'Downloading the update…';

  @override
  String get aboutUpdateReady =>
      'Update ready. Close and reopen the app to use it.';

  @override
  String get aboutUpdateCannot =>
      'This build can\'t update itself. Install the newest release instead.';

  @override
  String get aboutUpdateFailed =>
      'Couldn\'t check for updates. Check your connection and try again.';

  @override
  String get aboutAllLicences => 'All package licenses';

  @override
  String get aboutAllLicencesDetail =>
      'The license text of every library bundled in this build';

  @override
  String get aboutPackageId => 'Package id';

  @override
  String get providerQuotaProviderLabel => 'Provider';

  @override
  String get providerQuotaRouteLabel => 'Collector route';

  @override
  String get usageHubUnavailableTitle => 'No usage to show';

  @override
  String get usageHubUnavailableBody =>
      'Connect to a saved server to see what it spent and what your provider accounts have left.';

  @override
  String get voiceSetupSubtitle =>
      'Download a speech model once. After that, voice input runs on this phone without the internet.';

  @override
  String voiceSetupDownloadPack(String model, String size) {
    return 'Download $model ($size)';
  }

  @override
  String voiceSetupUsePack(String model) {
    return 'Use $model';
  }

  @override
  String voiceSetupRedownloadPack(String model) {
    return 'Download $model speech model again';
  }

  @override
  String voiceSetupDeletePack(String model, String size) {
    return 'Delete $model speech model ($size)';
  }

  @override
  String voiceSetupKeepPack(String model) {
    return 'Keep $model';
  }

  @override
  String voiceSetupDownloadingPack(String model) {
    return 'Downloading $model';
  }

  @override
  String get voiceSetupModelLabel => 'Speech model';

  @override
  String get voiceNoticesTitle => 'Voice licenses';

  @override
  String get voiceNoticesIntro =>
      'Voice input is built on these open-source parts. Open one to read its license.';

  @override
  String voiceNoticesMadeBy(String maker, String license) {
    return '$maker · $license';
  }

  @override
  String voiceNoticesOpenWebsite(String name) {
    return 'Open the $name website';
  }

  @override
  String get voiceNoticesWhisper => 'Whisper speech models';

  @override
  String get voiceSetupBusyReason => 'Available after the download';

  @override
  String get shorebirdUpdateReadyTitle => 'App update ready';

  @override
  String get shorebirdUpdateReadyBody =>
      'It takes effect when you fully close the app and open it again.';

  @override
  String desktopReleaseAvailable(String tag) {
    return 'Update $tag is available';
  }

  @override
  String get desktopReleaseWhatChanged =>
      'The release page lists what changed and has the downloads.';

  @override
  String get desktopReleaseOpenPage => 'Open release page';

  @override
  String get runningWorkTitle => 'Work in this conversation';

  @override
  String get runningWorkFailed => 'Failed';

  @override
  String runningWorkAgentState(String state) {
    return 'Agent · $state';
  }

  @override
  String runningWorkCommandState(String state) {
    return 'Command · $state';
  }

  @override
  String get runningWorkOffline =>
      'Reconnecting. Try again once the server answers.';

  @override
  String runningWorkStopAgent(String title) {
    return 'Stop “$title”';
  }

  @override
  String runningWorkStopAgentTitle(String title) {
    return 'Stop “$title”?';
  }

  @override
  String get runningWorkStopAgentBody =>
      'The agent stops where it is. Its conversation and the files it changed are kept.';

  @override
  String get runningWorkStopAgentConfirm => 'Stop agent';

  @override
  String get runningWorkScopeChangedTitle => 'Server or project changed';

  @override
  String get runningWorkAgentsFailed =>
      'Couldn\'t load this conversation\'s agents.';

  @override
  String get runningWorkCommandsFailed =>
      'Couldn\'t load this conversation\'s commands.';

  @override
  String get runningWorkEmptyTitle => 'Nothing running';

  @override
  String get runningWorkEmptyBody =>
      'Agents and commands this conversation starts show here while they run and after they end.';

  @override
  String get runningWorkBackgroundBody =>
      'The work keeps running on the server and its results come back here.';

  @override
  String get runningWorkBackgroundAction => 'Keep chatting while it runs';

  @override
  String get shellOutputCopyFirst => 'Copy output first';

  @override
  String get shellOutputLimitTitle => 'Stop it after…';

  @override
  String shellOutputStopsIn(String time) {
    return 'stops in $time';
  }

  @override
  String get shellOutputNoLimit => 'no time limit';

  @override
  String shellOutputAboutToStop(String time) {
    return 'It stops in $time. Change timeout to give it longer.';
  }

  @override
  String get shellOutputReadFailed => 'Couldn\'t read the output.';

  @override
  String get shellOutputLimitFailed => 'Couldn\'t change the time limit.';

  @override
  String get shellOutputDetailCommand => 'Command as typed';

  @override
  String get shellOutputDetailFolder => 'Folder';

  @override
  String get shellOutputDetailExit => 'Exit code';

  @override
  String get shellOutputDetailId => 'Command ID';

  @override
  String get shellOutputReading => 'Reading output';

  @override
  String get sessionDestinationWarpTitle => 'Move to the cloud';

  @override
  String get sessionDestinationSeparateCopy => 'Separate copy';

  @override
  String sessionDestinationCloudKind(String state) {
    return 'Cloud machine · $state';
  }

  @override
  String get sessionDestinationConnected => 'Connected';

  @override
  String get sessionDestinationNotConnected => 'Not connected';

  @override
  String get sessionDestinationNotConnectedWhy =>
      'Not connected. It can be picked once it connects.';

  @override
  String sessionDestinationChangesGo(String destination) {
    return 'With changes, they go with it to $destination.';
  }

  @override
  String sessionDestinationChangesCopied(String destination) {
    return 'With changes, a copy goes with it to $destination.';
  }

  @override
  String sessionDestinationChangesStay(String place) {
    return 'Without changes, they stay in $place.';
  }

  @override
  String sessionDestinationMoveWithout(String destination) {
    return 'Move to $destination without changes';
  }

  @override
  String get sessionDestinationMoveFailed => 'Couldn\'t move the conversation.';

  @override
  String get sessionDestinationLoadFailed =>
      'Couldn\'t load the places to move to';

  @override
  String get sessionDestinationNoneTitle => 'Nowhere to move it';

  @override
  String get sessionDestinationNoneMoveBody =>
      'This project has only this folder. A separate copy of the project shows here once it exists.';

  @override
  String get sessionDestinationNoneWarpBody =>
      'This project has no cloud machine yet.';

  @override
  String get consoleOrganizationWhatChanges =>
      'Models, providers and billing follow the organization you pick.';

  @override
  String consoleOrganizationSwitchBody(String organization) {
    return '$organization becomes the organization for models, providers and billing. Models reload; nothing running is stopped.';
  }

  @override
  String consoleOrganizationSwitchConfirm(String organization) {
    return 'Switch to $organization';
  }

  @override
  String get consoleOrganizationLoadFailed =>
      'Couldn\'t load your organizations';

  @override
  String get consoleOrganizationNoneTitle => 'No organizations';

  @override
  String get consoleOrganizationOnlyOne =>
      'This is your only organization, so there is nothing to switch to.';

  @override
  String get sessionContextLoading => 'Loading context';

  @override
  String get sessionContextMovedTitle => 'This conversation moved';

  @override
  String get sessionContextLoadFailed => 'Couldn\'t load the context';

  @override
  String get sessionContextRefreshFailed =>
      'Couldn\'t refresh. The numbers below are from the last read.';

  @override
  String sessionContextVerdictPlenty(String percent) {
    return '$percent% used · plenty left';
  }

  @override
  String sessionContextVerdictUsed(String percent) {
    return '$percent% used';
  }

  @override
  String sessionContextVerdictNear(String percent) {
    return '$percent% used';
  }

  @override
  String sessionContextVerdictFull(String percent) {
    return '$percent% used · at the limit';
  }

  @override
  String get sessionContextNearLimitTitle => 'Near the limit';

  @override
  String get sessionContextNearLimitBody =>
      'Older details may be dropped from what the model sees. Compact the conversation to keep going, or start a new one.';

  @override
  String get sessionContextCompactAction => 'Compact this conversation';

  @override
  String get sessionContextCompactTitle => 'Compact this conversation?';

  @override
  String get sessionContextCompactBody =>
      'OpenCode summarizes the conversation so far and continues from the summary, so it takes less of the model\'s limit.';

  @override
  String get sessionContextCompactKept => 'Every message stays in the history.';

  @override
  String get sessionContextCompactConfirm => 'Compact conversation';

  @override
  String get sessionContextCompactStarted =>
      'Compacting started. The numbers update when it finishes.';

  @override
  String get sessionContextCompactBusy => 'Wait for the reply to finish.';

  @override
  String get sessionContextMakeupTitle => 'Latest request input';

  @override
  String sessionContextTokens(String count) {
    return '$count tokens';
  }

  @override
  String get sessionContextModelId => 'Model ID';

  @override
  String get demoScreenTitle => 'Try it offline';

  @override
  String get demoScreenSimulated => 'Simulated · nothing is saved';

  @override
  String get demoScreenFinished =>
      'That\'s the whole loop: a prompt, a reply and a reviewed edit.';

  @override
  String sessionContextPercent(String percent) {
    return '$percent %';
  }

  @override
  String sessionDestinationChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files are present.',
      one: '1 changed file is present.',
    );
    return '$_temp0';
  }

  @override
  String get activeContextLoading => 'Reading the active context…';

  @override
  String activeContextAllCount(int count) {
    return 'All messages · $count';
  }

  @override
  String get activeContextChangedTitle => 'This view is outdated';

  @override
  String get activeContextFailedTitle => 'Couldn\'t read the context';

  @override
  String get activeContextIntro =>
      'What the model reads on its next turn, after the latest summary.';

  @override
  String get activeContextEmptyDetail =>
      'Nothing is kept for the next turn yet. Pull down to check again.';

  @override
  String get activeContextWhat => 'active context';

  @override
  String activeContextRowMenu(String type) {
    return 'Actions for $type';
  }

  @override
  String activeContextOpenMessage(String type) {
    return 'Open $type';
  }

  @override
  String activeContextCopyMessage(String type) {
    return 'Copy $type text';
  }

  @override
  String get activeContextMessageId => 'Message id';

  @override
  String activeContextCopyPart(String part) {
    return 'Copy $part';
  }

  @override
  String get sessionNoteDeleting => 'Deleting the note…';

  @override
  String get sessionNoteSaving => 'Saving the note…';

  @override
  String get sessionNoteLoading => 'Reading the saved note…';

  @override
  String get sessionNoteLoadFailed => 'Couldn\'t read the note';

  @override
  String get sessionNoteSaveFailed => 'Couldn\'t save the note';

  @override
  String get sessionNoteFieldLabel => 'Note';

  @override
  String get sessionNoteFieldLocked => 'Refresh the saved note before editing.';

  @override
  String sessionNoteTooLong(int over, int limit) {
    return '$over bytes too long. A note can be up to $limit bytes.';
  }

  @override
  String get sessionNoteWriteFirst => 'Write a note to save it.';

  @override
  String get sessionNoteEmptyUseDelete =>
      'To remove the note, use Delete saved note.';

  @override
  String get sessionRelationsTitle => 'Subagents';

  @override
  String sessionRelationsStopTitle(String title) {
    return 'Stop $title?';
  }

  @override
  String get sessionRelationsStopBody =>
      'The subagent stops its current step. What it already did stays in its conversation.';

  @override
  String get sessionRelationsStopConfirm => 'Stop subagent';

  @override
  String get sessionRelationsFailedTitle => 'Couldn\'t load the subagents';

  @override
  String get sessionRelationsLoading => 'Loading subagents…';

  @override
  String get sessionRelationsStartedFrom => 'Started from';

  @override
  String sessionRelationsSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count subagents',
      one: '1 subagent',
    );
    return '$_temp0';
  }

  @override
  String get sessionRelationsOpenToAnswer => 'open to answer';

  @override
  String get sessionRelationsIdle => 'Idle';

  @override
  String get sessionRelationsThisConversation => 'This conversation';

  @override
  String get sessionRelationsOpening => 'Opening…';

  @override
  String sessionRelationsRowMenu(String title) {
    return 'Actions for $title';
  }

  @override
  String sessionRelationsOpen(String title) {
    return 'Open $title';
  }

  @override
  String sessionRelationsCopyHandoff(String title) {
    return 'Continue $title on computer';
  }

  @override
  String sessionRelationsPin(String title) {
    return 'Pin $title';
  }

  @override
  String sessionRelationsUnpin(String title) {
    return 'Unpin $title';
  }

  @override
  String sessionRelationsStop(String title) {
    return 'Stop $title';
  }

  @override
  String get webSourcesInvalidUrl =>
      'Enter an HTTP or HTTPS address without a user name or password.';

  @override
  String get webSearchFailedTitle => 'Search didn\'t finish';

  @override
  String get webSearchTryAgain => 'Search again';

  @override
  String get webSearchBusy => 'Wait for the search to finish.';

  @override
  String get webSearchQueryHint => 'For example: flutter golden tests';

  @override
  String get webSearchNeedsProvider =>
      'Set up a search provider on this server first.';

  @override
  String get webSearchEmptyDetail => 'Try other words, or paste a link below.';

  @override
  String webSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results',
      one: '1 result',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesAdded => 'Added';

  @override
  String webSourcesAddNamed(String title) {
    return 'Add $title to prompt';
  }

  @override
  String webSourcesRowMenu(String title) {
    return 'Actions for $title';
  }

  @override
  String webSourcesOpenHost(String host) {
    return 'Open $host in browser';
  }

  @override
  String get webSourcesAddLink => 'Add link to prompt';

  @override
  String get webSourcesPasteDetail =>
      'A public address, with an optional excerpt';

  @override
  String webSourcesRemoveNamed(String title) {
    return 'Remove $title from prompt';
  }

  @override
  String get webSearchSearching => 'Searching…';

  @override
  String get webSearchFindingProviders => 'Finding search providers…';

  @override
  String webSourcesDone(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Add $count sources to prompt',
      one: 'Add 1 source to prompt',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesScopeChangedTitle => 'The server changed';

  @override
  String get sessionExportFormatLabel => 'Format';

  @override
  String get sessionExportJsonUnavailable =>
      'This server can\'t send a complete copy. Save the readable transcript instead.';

  @override
  String get sessionExportPrivacyLabel => 'Privacy';

  @override
  String get sessionExportRedactKeeps =>
      'Keeps who wrote each message; the words become placeholders. Not a backup.';

  @override
  String get sessionExportRedactBusy =>
      'Wait until the file is saved to change this.';

  @override
  String get sessionExportRedactChanged =>
      'Open export again from the conversation to change this.';

  @override
  String get sessionExportSaveJson => 'Save complete conversation';

  @override
  String get sessionExportSaveMarkdown => 'Save readable transcript';

  @override
  String get sessionExportSaveFailed =>
      'Couldn\'t write the file on this device. Nothing changed on the server. Try again, or choose another folder.';

  @override
  String get capabilitiesToolsMissingTitle => 'Tools aren\'t listed';

  @override
  String capabilitiesToolsMissingOnServer(String server) {
    return '$server doesn\'t list its tools';
  }

  @override
  String get mcpSetupWhere => 'Where it goes';

  @override
  String get mcpSetupHowItRuns => 'How it runs';

  @override
  String get mcpSetupHeaders => 'Headers';

  @override
  String get mcpSetupAdvanced => 'Advanced';

  @override
  String get mcpSetupAdvancedRemote => 'Sign-in detection and timeout';

  @override
  String get mcpSetupAdvancedLocal => 'Working folder and timeout';

  @override
  String get mcpSetupNoProject => 'Open a project first';

  @override
  String get mcpSetupRuntimeNote =>
      'It connects now and is gone when OpenCode restarts. For a lasting setup, edit the server configuration.';

  @override
  String mcpSetupSaveNamed(String name) {
    return 'Save $name';
  }

  @override
  String mcpSetupAddNamed(String name) {
    return 'Add $name';
  }

  @override
  String get mcpSetupLocationChangedShort => 'The server or project changed';

  @override
  String get mcpSetupSaveFailed => 'Couldn\'t add the MCP server';

  @override
  String get mcpSetupDiscardTitle => 'Discard this MCP server?';

  @override
  String get mcpSetupDiscardBody =>
      'What you typed here isn\'t saved and will be lost.';

  @override
  String get mcpSetupDiscardConfirm => 'Discard server';

  @override
  String get externalAgentsEmptyTitle => 'No outside agents yet';

  @override
  String get externalAgentsEmptyBody =>
      'Add one by its web address. You see what it says about itself before anything is saved.';

  @override
  String get externalAgentsBoundary =>
      'Only the text you send reaches an outside agent. Your projects, files and other conversations stay on this phone.';

  @override
  String get externalAgentsRemovalIncomplete =>
      'Removal didn\'t finish · tap to try again';

  @override
  String externalAgentsRemoveNamed(String name) {
    return 'Remove $name from this phone';
  }

  @override
  String externalAgentsRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get externalAgentsRemoveBody =>
      'Its saved tasks and key leave this phone. Work it already started carries on, and what it keeps stays with it.';

  @override
  String get externalAgentsBusy => 'Wait for the current step to finish';

  @override
  String get externalAgentsAddressHelper =>
      'Its web address, or the address of its Agent Card.';

  @override
  String get externalAgentsCheck => 'Check agent';

  @override
  String get externalAgentsCheckNeedsAddress => 'Type the agent address first';

  @override
  String get externalAgentsStopChecking => 'Stop checking';

  @override
  String get externalAgentsCheckFailedTitle => 'Couldn\'t check this agent';

  @override
  String externalAgentsSaveNamed(String name) {
    return 'Save $name';
  }

  @override
  String get externalAgentsSaveNeedsKey => 'Enter the agent key first';

  @override
  String get externalAgentsAboutLabel => 'What it says about itself';

  @override
  String get externalAgentsUnverified =>
      'The agent describes itself. This app hasn\'t verified who runs it, what it can do or what it costs.';

  @override
  String get externalAgentsUnsupportedTitle => 'Agent not supported';

  @override
  String get externalAgentsUnsupportedBody =>
      'It doesn\'t take text tasks the way this app sends them, or it asks for a sign-in this app doesn\'t support.';

  @override
  String get externalAgentsKeyLabel => 'Agent key';

  @override
  String get externalAgentsKeyHelper =>
      'The key its owner gave you. It stays in this phone\'s secure storage and is sent only to this agent.';

  @override
  String get externalAgentsNoKey =>
      'This agent asks for no key. Don\'t send private information unless you trust it.';

  @override
  String get externalAgentsDetailCard => 'Agent Card';

  @override
  String get externalAgentsDetailEndpoint => 'Endpoint';

  @override
  String get externalAgentsDetailVersion => 'Version';

  @override
  String get externalAgentsDetailConnection => 'Connection';

  @override
  String externalAgentsNewTaskNamed(String name) {
    return 'New task for $name';
  }

  @override
  String externalAgentsReplaceKeyNamed(String name) {
    return 'Replace key for $name';
  }

  @override
  String externalAgentsReplaceKeyTitle(String name) {
    return 'Replace key for $name';
  }

  @override
  String get externalAgentsSaveKey => 'Save key';

  @override
  String get externalAgentsTasksLabel => 'Tasks';

  @override
  String get externalAgentsNoTasksTitle => 'No tasks yet';

  @override
  String get externalAgentsNoTasksBody =>
      'Write a task and read it over before it\'s sent. Opening a sent task checks on it; it is never sent twice.';

  @override
  String get externalAgentsUntitledTask => 'New task';

  @override
  String externalAgentsSendNamed(String name) {
    return 'Send to $name';
  }

  @override
  String externalAgentsReplyNamed(String name) {
    return 'Reply to $name';
  }

  @override
  String get externalAgentsSendNeedsText => 'Write the task first';

  @override
  String get externalAgentsReplyNeedsText => 'Write your reply first';

  @override
  String get externalAgentsSendNote =>
      'Only this text is sent. The agent may use its own services and charge for them; check its terms.';

  @override
  String externalAgentsCheckedAt(String age) {
    return 'Checked with the agent $age ago';
  }

  @override
  String get externalAgentsPullToCheck =>
      'Saved on this phone · pull down to check with the agent';

  @override
  String externalAgentsStopMenu(String name) {
    return 'Ask $name to stop this task';
  }

  @override
  String get externalAgentsStopUnavailable =>
      'Check with the agent first; pull down to refresh';

  @override
  String get externalAgentsForgetMenu => 'Forget this task on this phone';

  @override
  String get externalAgentsForgetTitle => 'Forget this task?';

  @override
  String get externalAgentsForgetBody =>
      'It leaves this phone. Work the agent already started carries on, and its own copy stays with it.';

  @override
  String get externalAgentsForgetConfirm => 'Forget task';

  @override
  String mcpSetupSavedNamed(String name) {
    return 'Saved $name on this server';
  }

  @override
  String mcpSetupSavedNotConnectedBody(String reason) {
    return 'The app didn\'t reconnect afterwards. $reason';
  }

  @override
  String get mcpSetupSavedElsewhere =>
      'The server or project changed after saving, so this page can\'t reconnect for it. Close it and check MCP servers.';

  @override
  String get mcpSetupUnavailableTitle => 'Can\'t add MCP servers';

  @override
  String get mcpSetupUnavailableBody =>
      'It doesn\'t accept new MCP servers from the app. Add them in its configuration on the computer; they then show under MCP servers.';

  @override
  String get commandAuthSheetWorking => 'Asking the server…';

  @override
  String get credentialSheetLoading => 'Reading saved accounts…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return 'Sign in to $provider again from Providers to add an account.';
  }

  @override
  String credentialSheetActions(String label) {
    return 'Actions for $label';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return 'Use $label';
  }

  @override
  String get credentialSheetInUse => 'Already in use';

  @override
  String credentialSheetRenameNamed(String label) {
    return 'Rename $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return 'Remove $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return 'Removes $label from this server. Projects that use it will need another $provider account.';
  }

  @override
  String credentialSheetRenamed(String label) {
    return 'Renamed to $label.';
  }

  @override
  String get credentialSheetLabelEmpty => 'Give the account a name.';

  @override
  String get credentialSheetLabelInvalid =>
      'Use up to 128 characters, without line breaks or control characters.';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return 'Forget the $integration sign-in?';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      'The app stops tracking it on this device. Nothing is cancelled on the server; an unfinished sign-in there expires on its own.';

  @override
  String get toolsScreenLoadFailed => 'Couldn\'t load this model\'s tools';

  @override
  String get toolsScreenSearchWhat => 'tools';

  @override
  String get toolsScreenRegisteredOnly =>
      'Registered on this project · this model can’t call it';

  @override
  String get toolsDetailTakes => 'Takes';

  @override
  String get toolsDetailTakesNothing => 'Takes nothing.';

  @override
  String get toolsDetailRequired => 'required';

  @override
  String get toolsDetailOptional => 'optional';

  @override
  String get toolsDetailTypeText => 'text';

  @override
  String get toolsDetailTypeNumber => 'number';

  @override
  String get toolsDetailTypeYesNo => 'yes or no';

  @override
  String get toolsDetailTypeList => 'list';

  @override
  String get toolsDetailTypeGroup => 'group of values';

  @override
  String get toolsDetailTypeAny => 'any value';

  @override
  String commandsScreenRunsWith(String agent) {
    return 'Runs with $agent';
  }

  @override
  String get commandsScreenMenuLabel => 'Command actions';

  @override
  String commandsScreenCopy(String command) {
    return 'Copy $command';
  }

  @override
  String get referencesScreenLoading => 'Loading references';

  @override
  String get referencesScreenLoadFailed => 'Couldn’t load references';

  @override
  String get referencesScreenIntro =>
      'Folders this project points its agents to. Add one to a prompt and the agent can read what it holds.';

  @override
  String get referencesScreenEmptyBody =>
      'A reference is a folder the project’s agents can read. References set up for this project appear here.';

  @override
  String get referencesScreenMenuLabel => 'Reference actions';

  @override
  String referencesScreenAdd(String mention) {
    return 'Add $mention to the prompt';
  }

  @override
  String referencesScreenShowDetails(String name) {
    return 'Show $name details';
  }

  @override
  String referencesScreenCopyMention(String mention) {
    return 'Copy $mention';
  }

  @override
  String get referencesScreenCopyPath => 'Copy path';

  @override
  String referencesScreenSheetBody(String mention) {
    return 'Write $mention in a prompt and the agent reads this folder for that reply.';
  }

  @override
  String get referencesScreenPathLabel => 'Path';

  @override
  String get skillsScreenLoading => 'Loading skills';

  @override
  String get skillsScreenLoadFailed => 'Couldn’t load skills';

  @override
  String get skillSheetViewLabel => 'How to show the skill';

  @override
  String get skillSheetLocation => 'File';

  @override
  String skillSheetCopyCommand(String command) {
    return 'Copy $command';
  }

  @override
  String get skillSheetCheckConversation =>
      'Check the conversation before trying again.';

  @override
  String get skillSheetSending => 'Adding the skill…';

  @override
  String toolCardDelegatedTo(String agent) {
    return 'Delegated to $agent';
  }

  @override
  String get toolCardExitPassed => 'Passed · exit code 0';

  @override
  String toolCardExitFailed(int code) {
    return 'Failed · exit code $code';
  }

  @override
  String get toolCardRunCommandAgain => 'Run this command again';

  @override
  String get toolCardCopyCommand => 'Copy command';

  @override
  String toolCardLoadImageAgain(String name) {
    return 'Load $name again';
  }

  @override
  String toolCardChangesIn(String file) {
    return 'Changes in $file';
  }

  @override
  String mobileTasksShowAll(int count) {
    return 'Show all $count tasks';
  }

  @override
  String get composerBusyReason => 'Getting your prompt ready…';

  @override
  String get composerToolsTextOnly => 'This server takes text only';

  @override
  String get composerToolCommandsTitle => 'Commands and agents';

  @override
  String get composerToolSavedSubtitle =>
      'Put a prompt you saved back in the draft';

  @override
  String get composerToolSaveForLater => 'Save prompt for later';

  @override
  String get composerToolNothingToSave => 'Type or attach something first';

  @override
  String get composerToolsMore => 'More tools';

  @override
  String get composerReturnedToDraft => 'Returned to your draft';

  @override
  String get promptHistoryIntro => 'Tap a prompt to add it to your draft.';

  @override
  String get promptEditorDiscardChanges => 'Discard changes';

  @override
  String get promptEditorDone => 'Use in draft';

  @override
  String get promptEditorFieldLabel => 'Prompt';

  @override
  String get promptStashDeleted => 'Saved prompt deleted';

  @override
  String get promptStashIntro => 'Newest first · kept on this device';

  @override
  String get promptStashEmptyTitle => 'No saved prompts yet';

  @override
  String get promptStashEmptyBody =>
      'Choose Save prompt for later in the + menu to keep a prompt here.';

  @override
  String get promptStashBusy => 'Wait for the current step to finish';

  @override
  String get promptStashRowActions => 'Saved prompt actions';

  @override
  String get promptStashRestoreToDraft => 'Restore to draft';

  @override
  String get promptStashDeleteAction => 'Delete saved prompt';

  @override
  String get modelShortcutsNextRecent => 'Next recent model';

  @override
  String get modelShortcutsPreviousRecent => 'Previous recent model';

  @override
  String get modelShortcutsNoRecent =>
      'Use another model first to cycle back to it';

  @override
  String get modelShortcutsNoFavorite =>
      'Mark a model as a favorite in the model picker first';

  @override
  String get composerDraftBlockedReason =>
      'Answer the question about this draft first';

  @override
  String get commandLauncherSubtitle =>
      'Run an action in this conversation, or a command from this server';

  @override
  String get teamChatRefusedTitle => 'Task not taken';

  @override
  String get teamChatRefusedRetry => 'Send the task again';

  @override
  String get teamChatGoneTitle => 'Task no longer listed';

  @override
  String get teamChatGoneBody =>
      'It may have been removed on the team\'s computer. The AI Team page lists the tasks it has now.';

  @override
  String get teamChatGoneOpenTeam => 'Open AI Team page';

  @override
  String activityFinishedRow(String when) {
    return 'Finished · $when';
  }

  @override
  String get activityOfflineRequests =>
      'Requests can\'t load while you\'re offline.';

  @override
  String connectionReconnectTo(String server) {
    return 'Reconnect to $server';
  }

  @override
  String get workspaceIsolatedTaskRowDetail =>
      'Works on a separate copy so your main folder stays untouched.';

  @override
  String get workspaceSearchAllDetail =>
      'Every project on this server, archived ones too';

  @override
  String serverDisconnectFrom(String server) {
    return 'Disconnect from $server';
  }

  @override
  String get localAgentStopNamed => 'Stop Claude Code';

  @override
  String get localAgentStartNamed => 'Start Claude Code';

  @override
  String monitorSwitchToTitle(String server) {
    return 'Switch to $server?';
  }

  @override
  String monitorSwitchTo(String server) {
    return 'Switch to $server';
  }

  @override
  String servicesStartNamed(String service) {
    return 'Start $service';
  }

  @override
  String servicesStopNamed(String service) {
    return 'Stop $service';
  }

  @override
  String serverSettingsChangeSignIn(String server) {
    return 'Change sign-in for $server';
  }

  @override
  String serverSettingsAuthBasic(String user) {
    return 'Basic authentication as $user';
  }

  @override
  String get serverSettingsUpdateHint =>
      'Uses OpenCode\'s official installer; restart the server afterwards.';

  @override
  String get settingsHubModelRow => 'Model';

  @override
  String get notifyTurnOnInAndroid => 'Turn on notifications in Android';

  @override
  String get serversAddOtherWays => 'Or connect another way';

  @override
  String get libraryImportAConversation => 'Import a conversation';

  @override
  String runResultsStepsShort(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0';
  }

  @override
  String runResultsStepsShortAtLeast(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'At least $count steps',
      one: 'At least 1 step',
    );
    return '$_temp0';
  }

  @override
  String get runResultsUnderAMinute => 'under a minute';

  @override
  String runResultsMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String runResultsHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String get runResultsHowMade => 'How this was put together';

  @override
  String get runResultsRunIdLabel => 'Run id';

  @override
  String get runResultsAgentLabel => 'Agent';

  @override
  String runResultsCommandFailedExit(int code) {
    return 'Failed · exit $code';
  }

  @override
  String runResultsCommandPassedExit(int code) {
    return 'Passed · exit $code';
  }

  @override
  String get runResultsCommandFailedNoExit => 'Failed · exit not recorded';

  @override
  String get runResultsExitNotRecorded => 'Exit not recorded';

  @override
  String get projectHubHealthSubtitle =>
      'Branch, language services and formatters';

  @override
  String projectHubChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files changed',
      one: '1 file changed',
      zero: 'No changes',
    );
    return '$_temp0';
  }

  @override
  String projectHubTerminalsRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running',
      one: '1 running',
    );
    return '$_temp0';
  }

  @override
  String get projectHubCopyFolderPath => 'Copy folder path';

  @override
  String get terminalScreenNoTerminalThisServer =>
      'This server doesn\'t share a terminal';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return '$server doesn\'t share a terminal';
  }

  @override
  String get terminalScreenNoTerminalWhy =>
      'Terminals open here only on servers that share them.';

  @override
  String get integrationsSignInWaiting => 'Sign-in waiting';

  @override
  String get integrationsSignInMayNotHaveStarted =>
      'Sign-in may not have started';

  @override
  String get integrationsSignInExpired => 'Sign-in expired';

  @override
  String get integrationsSignInFailed => 'Sign-in failed';

  @override
  String get integrationsSignInComplete => 'Signed in · tap to finish';

  @override
  String integrationsFinishSigningIn(String provider) {
    return 'Finish signing in to $provider';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return 'Enter code for $provider';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return 'Cancel $provider sign-in';
  }

  @override
  String get integrationsForgetSignInOnPhone =>
      'Forget this sign-in on this phone';

  @override
  String integrationsSignInActions(String provider) {
    return 'Sign-in actions for $provider';
  }

  @override
  String integrationsAccountCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count accounts',
      one: '1 account',
    );
    return '$_temp0';
  }

  @override
  String get toolsScreenNoBackgroundSubagents => 'no background subagents';

  @override
  String get usageRefreshSpending => 'Refresh spending';

  @override
  String get quotaSetupTrustNote =>
      'Your server operator must install and protect this route at the same origin as OpenCode. Reading it uses this saved server\'s sign-in. Confirm only if you installed or trust that deployment.';

  @override
  String get quotaAlertsRowTitle => 'Quota alerts';

  @override
  String get quotaAlertsRowSupporting => 'Sound, Wi-Fi only and quiet hours';

  @override
  String modelPickerUseModel(String model) {
    return 'Use $model';
  }

  @override
  String get modelPickerUseChosenModel => 'Use model';

  @override
  String get handoffUiComputerCommandLabel => 'Terminal command';

  @override
  String get formRendererDecline => 'Decline this request';

  @override
  String get perfTraceActions => 'Timing report actions';

  @override
  String voiceSetupNotDownloaded(String size) {
    return 'Not downloaded · $size';
  }

  @override
  String get voiceSetupDone => 'Done';

  @override
  String get voiceAllowMicInSettings => 'Allow microphone in Android settings';

  @override
  String sessionContextMessagesSplit(String count, String yours, String agent) {
    return '$count ($yours yours, $agent agent)';
  }

  @override
  String get webSourcesClose => 'Close';

  @override
  String get webSourcesPastedLinks => 'Links you added';

  @override
  String get thisPhoneHostInApp => 'In the app';

  @override
  String get thisPhoneHostTermux => 'In Termux';

  @override
  String get thisPhoneNeedsAttention => 'Needs you';

  @override
  String get thisPhoneSetUp => 'Set up OpenCode';

  @override
  String get thisPhoneStart => 'Start the server';

  @override
  String get thisPhoneStop => 'Stop the server';

  @override
  String get thisPhoneUpdate => 'Update OpenCode';

  @override
  String thisPhoneUpdateDetail(String version) {
    return 'Installs version $version';
  }

  @override
  String get thisPhoneAddTools => 'Add tools';

  @override
  String get thisPhoneInstalled => 'Installed';

  @override
  String get thisPhoneTerminal => 'Open a terminal';

  @override
  String get thisPhoneStorage => 'Storage';

  @override
  String get thisPhoneConnect => 'Connect';

  @override
  String get thisPhoneRemove => 'Remove OpenCode';

  @override
  String get thisPhoneBusy => 'Wait for the current step to finish';

  @override
  String get phoneSetupTermuxAllowHow =>
      'In Termux, paste the copied line and press Enter.';

  @override
  String get phoneSetupTermuxUpdatingTitle => 'Updating this phone';

  @override
  String get phoneSetupTermuxStartingTitle => 'Starting the server';

  @override
  String get phoneSetupTermuxConnecting => 'Connecting';

  @override
  String get phoneSetupTermuxLeaveHint =>
      'You can leave the app. Termux keeps working and this list picks up where it is when you come back.';

  @override
  String get phoneSetupTermuxCost =>
      'About 10–15 minutes the first time, in Termux\'s storage';

  @override
  String removeFromPhoneKeepBody(String size) {
    return 'OpenCode and its tools are removed, freeing about $size.';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured =>
      'OpenCode and its tools are removed.';

  @override
  String get removeFromPhoneKeepConfirm => 'Remove OpenCode, keep my projects';

  @override
  String get removeFromPhoneDeleteAll => 'Delete everything';

  @override
  String get removeFromPhoneDeleteTitle => 'Delete OpenCode and projects?';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return 'OpenCode, its tools and every project on this phone are deleted, freeing about $size. This cannot be undone.';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      'OpenCode, its tools and every project on this phone are deleted. This cannot be undone.';

  @override
  String get thisPhoneManage => 'Manage This phone';

  @override
  String get chatRequestWho => 'The agent';

  @override
  String get chatRequestIfIgnored =>
      'The agent waits until you answer. Nothing is lost.';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more requests are waiting.',
      one: '1 more request is waiting.',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection =>
      'Not connected to the server, so this can’t be answered here.';

  @override
  String get chatRequestAlwaysTitle => 'Always allow these requests';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return 'From now on, $patterns runs without asking you, $context. You can take this back in Settings under Always allowed actions.';
  }

  @override
  String chatRequestAlwaysConfirm(String what) {
    return 'السماح دائمًا بـ $what في هذا المشروع؟';
  }

  @override
  String get chatRequestAlwaysInProject => 'في هذا المشروع';

  @override
  String get chatRequestAlwaysOn => 'Always allowed';

  @override
  String get chatRequestDetailTool => 'Tool';

  @override
  String get chatRequestDetailPatterns => 'Requested patterns';

  @override
  String get chatRequestOtherAnswer => 'Something else';

  @override
  String get chatRequestOtherField => 'Your answer';

  @override
  String get formFlowAnsweredElsewhereBody =>
      'This form was answered on another device, so nothing was sent from this phone.';

  @override
  String get approvalsUiPausedDetail =>
      'This phone is not connected. Automatic approval resumes when it reconnects.';

  @override
  String get teamUiHomeRunReviewNext => 'a reviewer checks it next';

  @override
  String teamUiGateRunStoppedTitle(String title) {
    return '$title stopped';
  }

  @override
  String get termuxProcsKindParentGone => 'Parent gone';

  @override
  String get termuxProcsKindNoOwner => 'No owner';

  @override
  String termuxProcsStopOrphans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop $count orphaned helpers',
      one: 'Stop 1 orphaned helper',
    );
    return '$_temp0';
  }

  @override
  String termuxProcsStopOrphansTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop $count orphaned helpers?',
      one: 'Stop the orphaned helper?',
    );
    return '$_temp0';
  }

  @override
  String get teamPhoneStopTeamRow => 'Stop the team on this phone';

  @override
  String get teamPhoneStopTeamRowSupporting =>
      'Agents stop where they are; nothing is lost';

  @override
  String teamUiPhoneWorkingOn(String name) {
    return 'Working on $name';
  }

  @override
  String get teamUiPhoneVersionsLabel => 'Engine versions';

  @override
  String get teamUiPhoneProjectLabel => 'Project folder';

  @override
  String get phoneServerNameInSentence => 'this phone';

  @override
  String get teamAgentWorkUnblockedShort => 'nothing blocking it';

  @override
  String get teamAgentWorkBlockedShort => 'blocked';

  @override
  String get teamAgentStepCommand => 'Ran a command';

  @override
  String get teamAgentStepTest => 'Ran the tests';

  @override
  String get teamAgentStepRead => 'Read a file';

  @override
  String get teamAgentStepEdit => 'Edited a file';

  @override
  String get teamAgentStepSearch => 'Searched the code';

  @override
  String teamAgentStepTool(String tool) {
    return 'Used $tool';
  }

  @override
  String get teamAgentLastCommandLabel => 'Last command';

  @override
  String get termuxStorageOnlyBuildCaches =>
      'Only build caches can be cleaned here';

  @override
  String get termuxStorageWhereItIs => 'Where it is';

  @override
  String termuxStorageCleanBuildCaches(String size) {
    return 'Clean build caches ($size)';
  }

  @override
  String get monitorBackgroundChecks => 'Background checks';

  @override
  String get settingsTryDemo => 'Try the demo';

  @override
  String quotaMonitorCheckNow(String provider, String server) {
    return 'Check $provider on $server now';
  }

  @override
  String get searchArchivedConversations => 'Archived conversations';

  @override
  String get readAloudConsentEngine =>
      'Only offline voices are offered, but the speech engine is separate software with its own privacy terms.';

  @override
  String get readAloudConsentHeard =>
      'People near you may hear it. Reading stops when you leave this conversation or the app.';

  @override
  String get transcriptFindStopSearchingAll => 'Stop searching older messages';

  @override
  String nudgeReviewChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'OpenCode changed $count files. Look them over before you go on.',
      one: 'OpenCode changed 1 file. Look it over before you go on.',
    );
    return '$_temp0';
  }

  @override
  String get voiceComponentTitle => 'Voice typing';

  @override
  String get voiceComponentSummary => 'Speak instead of typing, even offline';

  @override
  String get voiceComponentRemove => 'Remove voice typing';

  @override
  String get voiceComponentRemoveTitle => 'Remove voice typing?';

  @override
  String voiceComponentRemoveBody(String size) {
    return 'Deletes the speech model and frees $size. Voice typing stops working until you add it here again.';
  }

  @override
  String get setupAppStageDownloading => 'Downloading';

  @override
  String get setupAppStageVerifying => 'Checking the download';

  @override
  String kitDiffFilePosition(int index, int count) {
    return '$index of $count';
  }

  @override
  String kitDiffFilePositionSpoken(int index, int count) {
    return 'file $index of $count';
  }

  @override
  String get kitDiffViewed => 'Viewed';

  @override
  String get kitDiffSelectHunk => 'Select these lines';

  @override
  String get kitCapFlagTerminalTitle => 'Terminal';

  @override
  String get kitCapFlagTerminalWhy =>
      'This server doesn\'t open a terminal for you.';

  @override
  String get kitCapFlagToolInventoryTitle => 'Tool list';

  @override
  String get kitCapFlagToolInventoryWhy =>
      'This server doesn\'t list the tools its agent can use.';

  @override
  String get demoScreenReset => 'Reset demo';

  @override
  String get demoScreenLeave => 'Leave demo';

  @override
  String get demoScreenDisclosure =>
      'Everything here is simulated on this device. No server, provider, or files are accessed.';

  @override
  String capabilityScreenIntroWithGaps(String server) {
    return '$server decides what appears in this app. Anything it cannot do is left out of the menus and tabs instead of being shown greyed out. Missing features work on other OpenCode servers.';
  }

  @override
  String activeContextMessageTitle(String role) {
    String _temp0 = intl.Intl.selectLogic(role, {
      'user': 'User message',
      'assistant': 'Assistant message',
      'system': 'System message',
      'synthetic': 'Synthetic message',
      'skill': 'Skill message',
      'shell': 'Shell message',
      'compaction': 'Summary message',
      'change': 'Conversation change',
      'other': 'Message',
    });
    return '$_temp0';
  }

  @override
  String get newConversationLastUsed => 'Last used';

  @override
  String get newConversationSoloDetail => 'You and the assistant';

  @override
  String newConversationSoloDetailIn(String project) {
    return 'You and the assistant, in $project';
  }

  @override
  String get newConversationTeamDetail =>
      'The AI Team plans the work and shares it out';

  @override
  String get newConversationTeamOffDetail =>
      'Off on this server · opens the AI Team to set it up';

  @override
  String newConversationCopyTitle(String project) {
    return 'Separate copy of $project';
  }

  @override
  String newConversationCloudTitle(String machine) {
    return 'On $machine';
  }

  @override
  String get newConversationCloudDetail => 'A cloud machine for this project';

  @override
  String get chatDraftCopy => 'Copy draft';

  @override
  String get reportProblemIntro =>
      'Say what went wrong. You see the whole report before anything leaves this phone.';

  @override
  String get reportProblemDescribeLabel => 'What happened?';

  @override
  String get reportProblemDescribeHint =>
      'What you did, what you expected, what you got instead';

  @override
  String get reportProblemDescribeFirst => 'Say what happened first';

  @override
  String reportProblemAttached(String title) {
    return 'Attached: $title';
  }

  @override
  String get reportProblemIncludeDiagnostics => 'Include recent diagnostics';

  @override
  String reportProblemIncludeDiagnosticsBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count events from this phone',
      one: '1 event from this phone',
    );
    return '$_temp0, with keys, passwords and server addresses removed';
  }

  @override
  String get reportProblemReview => 'Review report';

  @override
  String get reportProblemReviewHint =>
      'Then open it on GitHub, copy it or share it. Screenshots can be added on the GitHub form.';

  @override
  String reportProblemErrorsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count recent errors',
      one: '1 recent error',
    );
    return '$_temp0';
  }

  @override
  String get reportProblemClearFailed =>
      'Couldn\'t clear the saved report. Try again.';

  @override
  String get reportProblemPreviewSubtitle => 'This is exactly what is sent';

  @override
  String get reportProblemPublicNotice =>
      'GitHub issues are public. Nothing is filed until you submit the form there.';

  @override
  String get reportProblemOpenGitHub => 'Open GitHub form';

  @override
  String get reportProblemLinkCopiesDiagnostics =>
      'The diagnostics are too long for the link. Opening the form copies them, so paste them into its Diagnostics field.';

  @override
  String get reportProblemLinkCopiesWhole =>
      'The report is too long for the link. Opening the form copies it, so paste it into the form.';

  @override
  String get reportProblemCopy => 'Copy report';

  @override
  String get reportProblemCopied => 'Report copied';

  @override
  String get reportProblemDiagnosticsCopied =>
      'Diagnostics copied: paste them into the form';

  @override
  String get reportProblemShare => 'Share report';

  @override
  String get reportProblemShareFallback =>
      'Sharing didn\'t open, so the report is copied';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count errors kept',
      one: '1 error kept',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail =>
      'Python, AI Team, voice typing and more';

  @override
  String get phoneSetupTermuxOtherRuntime =>
      'Termux already runs the other OpenCode. Switch it on This phone, then continue setup.';

  @override
  String get undoFromHereNowAction => 'Undo now';

  @override
  String undoFromHereBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'This prompt and the $count messages after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.',
      one:
          'This prompt and the message after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.',
      zero:
          'This prompt is removed, and files go back to how they were before it. You can put it back until you send another prompt.',
    );
    return '$_temp0';
  }

  @override
  String get undoFromHereBodyUnknown =>
      'This prompt and everything after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.';

  @override
  String get undoFromHereFilesLabel => 'Files the agent edited after it';

  @override
  String get undoFromHereNoEdits =>
      'The agent reported no file edits after this prompt.';

  @override
  String get undoneStatus => 'Undone from a prompt';

  @override
  String get undonePutBack => 'Put back';

  @override
  String reviewRevertScreenIntroCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'This prompt and the $count messages after it are hidden. Nothing is final until you choose below.',
      one:
          'This prompt and the message after it are hidden. Nothing is final until you choose below.',
      zero:
          'This prompt is hidden; nothing came after it. Nothing is final until you choose below.',
    );
    return '$_temp0';
  }

  @override
  String reviewRevertKeepConsequenceCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'The hidden prompt and the $count messages after it are deleted',
      one: 'The hidden prompt and the message after it are deleted',
      zero: 'The hidden prompt is deleted',
    );
    return '$_temp0';
  }

  @override
  String addServerConnectedHost(String host) {
    return 'Connected to $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return '$host has not answered yet. A slow network can take a while.';
  }

  @override
  String get addServerCheckCancel => 'Stop checking';

  @override
  String get addServerRemoteHttpAdvice =>
      'A computer on your network needs an https:// address. Tailscale gives it a private one that only your devices can reach.';

  @override
  String get addServerUseTailscale => 'Use Tailscale';

  @override
  String get addServerStepsLabel => 'Add server progress';

  @override
  String get addServerStepKind => 'What runs there';

  @override
  String get addServerStepTailscale => 'Tailscale on this phone';

  @override
  String get addServerStepPair => 'Pair or enter the address';

  @override
  String get addServerStepAddress => 'Address and sign-in';

  @override
  String get addServerStepCheck => 'Checking';

  @override
  String get addServerStepReady => 'Ready';

  @override
  String addServerReadyTitle(String name) {
    return '$name is connected';
  }

  @override
  String get addServerReadyBody =>
      'Its conversations open next. Start one, or pick up one already there.';

  @override
  String addServerReadyOpen(String name) {
    return 'Open $name';
  }

  @override
  String get handoffUiLinkAddTitle => 'Add this server?';

  @override
  String get handoffUiLinkAddServer => 'Add server';

  @override
  String get failedJobReport => 'Report this failure';

  @override
  String get reportProblemJobLog => 'Log of the failed job';

  @override
  String get reportProblemJobLogNone =>
      'No log was kept for this job, so none is attached.';

  @override
  String get sessionsOlderLoadFailed => 'Could not load older conversations.';

  @override
  String get sessionsLoadFailed => 'Could not load your conversations.';

  @override
  String get sessionsListChanged =>
      'The conversation list changed on the server. Refresh it to see older conversations.';

  @override
  String get handoffUiComputerUnsupported =>
      'This server can’t give a command that continues a conversation on a computer.';

  @override
  String get handoffUiComputerChanged =>
      'This conversation moved or its server changed. Go back and try again.';

  @override
  String commandAuthSheetIntro(String provider) {
    return 'Runs this sign-in on your server, not on this phone. Start it only if you trust the server and $provider. You may need to finish steps on the server.';
  }

  @override
  String commandAuthCheckNamed(String provider) {
    return 'Check $provider sign-in now';
  }

  @override
  String credentialRemoveAccountTitle(String provider, String name) {
    return 'Remove $provider account “$name”?';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return 'Remove “$name”';
  }

  @override
  String quotaMonitorOffer(String provider, String server) {
    return 'Alert me about $provider on $server';
  }

  @override
  String quotaMonitorOfferDetail(String percent) {
    return 'Keeps checking in the background, including after a restart, and alerts when use reaches $percent. You can change the percentage once it’s on.';
  }

  @override
  String workspaceChooserBody(String server) {
    return 'Conversations run inside a folder on $server.';
  }

  @override
  String get discoverServicesAliases =>
      'services dev server preview logs run commands processes';

  @override
  String get discoverCloudEnvironmentsAliases =>
      'cloud environments managed workspaces remote sandbox';

  @override
  String promptRestoredWithout(String names) {
    return 'Restored without $names; attach them again before sending';
  }

  @override
  String get promptStashOlderDraftsWaiting =>
      'Some older drafts have not moved here yet. They are kept on this device.';

  @override
  String get promptStashOlderDraftsFull =>
      'Older drafts are waiting to move here. Delete saved prompts to make room.';

  @override
  String quotaAnswerLeft(String percent) {
    return 'About $percent left';
  }

  @override
  String quotaAnswerLeftWeek(String percent) {
    return 'About $percent left this week';
  }

  @override
  String quotaAnswerLeftDays(String percent, int days) {
    return 'About $percent left in this $days-day window';
  }

  @override
  String quotaAnswerLeftHours(String percent, int hours) {
    return 'About $percent left in this $hours-hour window';
  }

  @override
  String quotaAnswerResetsAt(String time) {
    return 'resets at $time';
  }

  @override
  String quotaAnswerResetsOn(String day) {
    return 'resets $day';
  }

  @override
  String get quotaAnswerResetPassed => 'reset time passed, refresh to check';

  @override
  String quotaAnswerFromCodex(String server) {
    return 'From your Codex account on $server';
  }

  @override
  String get quotaAnswerAgeNow => 'Last known reading, from just now';

  @override
  String quotaAnswerAgeMinutes(int minutes) {
    return 'Last known reading, from $minutes min ago';
  }

  @override
  String quotaAnswerAgeHours(int hours) {
    return 'Last known reading, from $hours h ago';
  }

  @override
  String quotaAnswerAgeDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Last known reading, from $days days ago',
      one: 'Last known reading, from yesterday',
    );
    return '$_temp0';
  }

  @override
  String quotaAnswerAlert(String percent) {
    return 'Alert me at $percent used';
  }

  @override
  String get quotaAnswerAlertDetail =>
      'Says so here when a fresh reading reaches it.';

  @override
  String get quotaAnswerAlertSaveFailed =>
      'Couldn’t save this. The alert stays as it was.';

  @override
  String quotaAnswerAttention(String percent) {
    return 'You’ve used $percent or more of a Codex limit.';
  }

  @override
  String quotaAnswerNotConnected(String server) {
    return 'Connect to $server to see what’s left on its Codex account.';
  }

  @override
  String quotaAnswerSignIn(String server) {
    return 'Sign in to Codex on $server';
  }

  @override
  String get quotaAnswerSignInDetail =>
      'What’s left shows here once you’re signed in with ChatGPT.';

  @override
  String get quotaAnswerUnsupported =>
      'This Codex sign-in has no plan limits to show. They show for ChatGPT sign-ins, not API keys.';

  @override
  String get quotaAnswerUnavailable =>
      'Couldn’t read the Codex limits. Check the connection, then refresh.';

  @override
  String get quotaAnswerInvalid =>
      'Codex sent limits this app can’t read. Nothing new is shown.';

  @override
  String get quotaAnswerNoWindows =>
      'Codex reported no limits for this account.';

  @override
  String get quotaAnswerCodexNote =>
      'Read from the Codex account on this server. Other limits, credits and model-specific caps are not included. Missing data is unknown, not unlimited.';

  @override
  String quotaNeedsCollector(String server) {
    return 'Needs the quota collector on $server';
  }

  @override
  String get quotaCollectorHowTo => 'How to get it';

  @override
  String quotaCollectorStepInstall(String server) {
    return 'Ask whoever runs $server to install the quota collector. It needs Node 20 or later.';
  }

  @override
  String get quotaCollectorStepRoute =>
      'They keep the provider sign-in on the server and put the collector behind the same HTTPS address and password as OpenCode.';

  @override
  String get quotaCollectorStepRetry => 'Then come back here and read again.';

  @override
  String get quotaCollectorGuide => 'Open the collector guide';

  @override
  String quotaCollectorFrom(String provider, String server) {
    return '$provider, from the quota collector on $server';
  }

  @override
  String quotaCollectorNoWindows(String provider, String server) {
    return 'The quota collector on $server reported no limits for $provider.';
  }

  @override
  String quotaStopCollector(String server) {
    return 'Stop using the quota collector on $server';
  }

  @override
  String get quotaStopCollectorDetail =>
      'The reading goes away, and Remaining asks you again before the next read.';

  @override
  String get quotaCollectorAddressLabel => 'Collector address';

  @override
  String get quotaPlanLabel => 'Plan';

  @override
  String get quotaReadAtLabel => 'Read at';

  @override
  String get usageSpentToday => 'Spent today';

  @override
  String get usageSpentThirtyDays => 'Spent in the last 30 days';

  @override
  String get usageSpentYear => 'Spent this year';

  @override
  String get usageSpentAllTime => 'Spent in total';

  @override
  String usageSpentPeriod(String period) {
    return 'Spent · $period';
  }

  @override
  String get automationTitle => 'What runs by itself';

  @override
  String get automationSearchAliases =>
      'automation automatic supervision auto approve approvals always allow permissions background watch monitor team level';

  @override
  String get automationSaveFailed =>
      'This choice wasn\'t saved on this phone. The level above is still the one in use; try again.';

  @override
  String get automationSaving => 'Saving…';

  @override
  String get automationTeamLabel => 'How much the AI Team decides alone';

  @override
  String get automationTeamFootnote =>
      'New team tasks start at this level. You can pick another level for one task when you start it.';

  @override
  String get automationWithoutAskingLabel => 'Without asking you';

  @override
  String get automationSavedRulesDetail =>
      'What the agent may run here without asking you.';

  @override
  String phoneSetupStartTermuxProgressHeadline(int percent) {
    return 'Setup in Termux is $percent% done';
  }

  @override
  String get teamPhoneReadyChooseTitle => 'Choose the team\'s project';

  @override
  String get teamPhoneReadyTurningOnTitle => 'Turning on AI Team';

  @override
  String get teamPhoneReadyFailedTitle => 'AI Team didn\'t start';

  @override
  String teamPhoneReadyBody(String project) {
    return 'Give it a first task. It plans the work, shares it between its agents and brings the result back into $project.';
  }

  @override
  String get teamPhoneReadyFirstTask => 'Give the team a first task';

  @override
  String get teamUiStateNotAnsweringPhone =>
      'The app keeps trying while the team starts on this phone.';

  @override
  String get teamUiStateNotAnsweringComputer =>
      'The app keeps trying. Check that your computer is on and online.';

  @override
  String teamUiStateNotAnsweringComputerNamed(String computer) {
    return 'The app keeps trying. Check that $computer is on and online.';
  }

  @override
  String get teamHomeChangeAddress => 'Change address';

  @override
  String get teamHomeTurnOffFailed =>
      'Couldn’t stop the team on this phone, so it is still on. Try again.';

  @override
  String get teamHomeHostStopped => 'Stopped';

  @override
  String get teamHomeHostCooling => 'Cooling down';

  @override
  String get teamHomeHostStoppedForHeat => 'Stopped to cool down';

  @override
  String teamHomeHeatPausedLine(String time) {
    return 'The phone got hot at $time, so the team paused. It carries on by itself once the phone has cooled.';
  }

  @override
  String teamHomeHeatStoppedLine(String time) {
    return 'The phone got very hot at $time, so the team stopped. Its work is kept, and it starts again once the phone has cooled.';
  }

  @override
  String get teamHomePhoneControls => 'Keep it running, stop it or remove it';

  @override
  String teamHomeSpentToday(String usage) {
    return 'Today · $usage';
  }

  @override
  String get teamHomeSpentHint =>
      'The whole team since midnight where it runs, estimated. The server doesn’t report what each task cost.';

  @override
  String get teamHomeSpentPartial =>
      'Some of today’s use has no price yet, so it cost more than this.';

  @override
  String teamIntroNotFound(String server) {
    return 'No AI Team found on $server';
  }

  @override
  String get pluginsTeamOpenPage => 'See the team’s tasks';

  @override
  String get chatErrorModelNotFound => 'The server doesn\'t have this model.';

  @override
  String get chatErrorContextOverflow =>
      'This conversation is too long for the model.';

  @override
  String get chatErrorProviderAuth =>
      'The model provider needs you to sign in again.';

  @override
  String get chatErrorOutputLength =>
      'The reply reached the model\'s length limit.';

  @override
  String get chatErrorContentFilter =>
      'The provider\'s safety filter stopped this reply.';

  @override
  String get chatErrorUnknown => 'The agent stopped because of an error.';

  @override
  String modelPickerThinkingChip(String level) {
    return 'Thinking: $level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return 'Agent: $agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count queued prompts move to Saved prompts',
      one: '1 queued prompt moves to Saved prompts',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count of them may already have been sent',
      one: '1 of them may already have been sent',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove and delete $count queued prompts',
      one: 'Remove and delete the queued prompt',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return 'The queued prompts for $name changed, so nothing was removed. Remove it again to see the new count.';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return 'Could not move the queued prompts for $name to Saved prompts, so nothing was removed. Delete some saved prompts or free up storage, then try again.';
  }

  @override
  String get settingsHubGroupAgent => 'Agent';

  @override
  String get settingsHubGroupConversations => 'Conversations';

  @override
  String get settingsHubGroupThisApp => 'This app';

  @override
  String get settingsHubProvidersRow => 'Providers and accounts';

  @override
  String get settingsHubToolsRow => 'Tools';

  @override
  String get settingsHubShowReasoning => 'Show reasoning';

  @override
  String get settingsHubShowTimestamps => 'Show timestamps and usage';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count settings aren\'t available on this server',
      one: '1 setting isn\'t available on this server',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => 'Why';

  @override
  String get toolsHubMcpSubtitle => 'Servers that give the agent more tools';

  @override
  String get toolsHubCatalogSubtitle =>
      'Slash commands, skills, the model\'s tools and references';

  @override
  String get toolsHubExternalAgentsSubtitle =>
      'Agents on other services you can hand work to';

  @override
  String get privacyPolicyTitle => 'Privacy policy';

  @override
  String get aboutHelpSection => 'Tips and shortcuts';

  @override
  String get settingsHubSearchToolsAliases =>
      'tools mcp integrations commands skills references slash capabilities plugins external agents a2a';

  @override
  String get whileAwayActReconnected => 'Reconnected by itself';

  @override
  String get whileAwayActRestarted => 'Restarted by itself';

  @override
  String get whileAwayActHeatPaused => 'AI Team paused while the phone was hot';

  @override
  String get whileAwayActHeatStopped =>
      'AI Team stopped while the phone was hot';

  @override
  String get whileAwayActHeatResumed => 'AI Team resumed once the phone cooled';

  @override
  String get whileAwayActUpdated => 'Update downloaded by itself';

  @override
  String get whileAwayActAllowed => 'Allowed a request by itself';

  @override
  String get whileAwayActQueuedSent => 'Sent your queued message by itself';

  @override
  String get whileAwayActOther => 'Done automatically';

  @override
  String whileAwayUndoFailed(String act) {
    return '$act · Undo didn\'t go through';
  }

  @override
  String get whileAwayDismiss => 'Dismiss';

  @override
  String get whileAwayHistoryUnreadable =>
      'The list of what ran by itself couldn\'t be read, so earlier automatic actions aren\'t shown.';

  @override
  String get whileAwayHistoryUnsaved =>
      'An automatic action couldn\'t be saved to this list. It happened, but it may not be listed.';

  @override
  String whileAwayActUndone(String act) {
    return '$act · Undone';
  }

  @override
  String whileAwayUndoUnconfirmed(String act) {
    return '$act · Undo not confirmed';
  }

  @override
  String whileAwayDismissed(String what) {
    return 'Dismissed “$what”';
  }

  @override
  String get whileAwayMark => 'Done by itself';

  @override
  String get aiteamComponentTurnOff => 'Turn off AI Team';

  @override
  String get aiteamComponentTurnOffTitle => 'Turn off AI Team?';

  @override
  String get aiteamComponentTurnOffBody =>
      'The team stops and stays off until you turn it on again.';

  @override
  String get aiteamComponentTurnOffKept =>
      'Its tasks and settings, and your projects, stay';

  @override
  String get aiteamComponentTurnOffFailed =>
      'AI Team could not be turned off. Try again, or restart the app.';

  @override
  String thisPhoneRemoveTool(String tool) {
    return 'Remove $tool';
  }

  @override
  String thisPhoneRemoveToolTitle(String tool) {
    return 'Remove $tool?';
  }

  @override
  String thisPhoneRemoveToolBody(String size) {
    return 'About $size comes back. You can add it again from Add tools.';
  }

  @override
  String get thisPhoneRemoveToolBodyUnmeasured =>
      'You can add it again from Add tools.';

  @override
  String thisPhoneRemoveToolNeededBy(String tools) {
    return 'Needed by $tools';
  }

  @override
  String get thisPhoneRemoveToolDetail =>
      'Deletes it from this phone. Your projects stay.';

  @override
  String get thisPhoneRemovePythonDetail =>
      'Deletes pip and venv. Python and your projects stay.';

  @override
  String get thisPhoneRemovePythonLost =>
      'pip and venv are deleted, with the packages only they used';

  @override
  String get thisPhoneRemovePythonKept =>
      'Python itself and your projects stay';

  @override
  String get thisPhoneRemoveTeamDetail =>
      'Deletes the team\'s programs, tasks and settings. Projects stay.';

  @override
  String get thisPhoneRemoveTeamLost =>
      'The team\'s programs, tasks and settings are deleted';

  @override
  String get thisPhoneRemoveTeamLostWork =>
      'Team work not yet brought into your projects is lost';

  @override
  String get thisPhoneRemoveTeamKept =>
      'Your project files and their git history stay';

  @override
  String get thisPhoneRemoveVoiceDetail =>
      'Deletes the speech model. Voice typing stops until you add it again.';

  @override
  String get thisPhoneRemoveVoiceLost =>
      'Voice typing stops until you add it again';

  @override
  String get thisPhoneRemoveToolKept => 'Your projects stay';

  @override
  String get removeFromPhoneKeepLost =>
      'Conversations and settings inside OpenCode are deleted';

  @override
  String get removeFromPhoneKeepKept =>
      'Your projects stay and come back when you set up again';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return 'Your projects ($size) stay and come back when you set up again';
  }

  @override
  String get removeFromPhoneDeleteLost =>
      'Project files not saved anywhere else are lost for good';

  @override
  String get productErrorTimedOut =>
      'The server took too long to answer. Try again.';

  @override
  String get productErrorCertificate =>
      'The server\'s security certificate isn\'t trusted, so the app stopped. Check the server address.';

  @override
  String get productErrorSignIn =>
      'The server didn\'t accept the sign-in. Check the password in the server\'s settings.';

  @override
  String get productErrorNotFound =>
      'The server couldn\'t find it. It may have been moved or deleted.';

  @override
  String get productErrorConflict =>
      'It changed on the server in the meantime. Refresh, then try again.';

  @override
  String get productErrorBusy =>
      'The server is busy. Wait a moment, then try again.';

  @override
  String get productErrorRejected =>
      'The server didn\'t accept the request. Try again, or report the problem.';

  @override
  String get productErrorUnknown =>
      'That didn\'t work. Details show what happened. Try again, or report the problem.';

  @override
  String get productErrorUnexpected =>
      'The server\'s answer didn\'t make sense to the app. Try again, or report the problem.';

  @override
  String get productErrorDevice =>
      'Something on this device didn\'t work. Try again.';

  @override
  String get productErrorStorage =>
      'The app couldn\'t read or save a file on this device.';

  @override
  String get productErrorTermux =>
      'Termux didn\'t finish that. Check that Termux is installed and open, then try again.';

  @override
  String get productErrorDetailsLabel => 'Error details';

  @override
  String productErrorServer(int code) {
    return 'The server had a problem (error $code). Try again in a moment.';
  }

  @override
  String get usageBudgetInvalidUsd => 'Enter an amount above 0, like 2.50';

  @override
  String get usageBudgetInvalidTokens =>
      'Enter a whole number of tokens above 0';

  @override
  String get usageBudgetSaveUsd => 'Save USD budget';

  @override
  String get usageBudgetSaveTokens => 'Save token budget';

  @override
  String sessionDestinationMoveWithChanges(String destination) {
    return 'Move to $destination with changes';
  }

  @override
  String sessionDestinationWarpWithChanges(String destination) {
    return 'Move to $destination with a copy of changes';
  }

  @override
  String sessionDestinationMoveTo(String destination) {
    return 'Move to $destination';
  }

  @override
  String sessionDestinationNoChanges(String place) {
    return 'No working changes in $place, so only the conversation moves.';
  }

  @override
  String get settingsBackgroundOffFailed =>
      'Android did not turn background mode off.';

  @override
  String defaultProjectOnlyNotice(String project) {
    return 'Opened $project, the only project on this server.';
  }

  @override
  String defaultProjectLastUsedNotice(String project) {
    return 'Opened $project, the project worked on most recently.';
  }

  @override
  String get defaultProjectChange => 'Choose another project';

  @override
  String defaultReviewScopeNotice(String scope) {
    return 'Showing $scope: it is the view with changes.';
  }

  @override
  String defaultModelNotice(String model) {
    return 'Using $model, this server\'s default model.';
  }

  @override
  String get defaultModelChange => 'Choose another model';

  @override
  String teamControlReceiptSending(String control) {
    return '$control · Sending…';
  }

  @override
  String get teamGateCardRunFailedOpen => 'Choose what to do';

  @override
  String get teamGateCardIfIgnored =>
      'The team waits until you answer. Nothing is lost.';

  @override
  String get teamGateCardIfIgnoredFailed =>
      'The task stays stopped until someone acts on it.';

  @override
  String get teamGateCardIfIgnoredReview =>
      'The work waits for review. Nothing is lost.';

  @override
  String termuxProcsBudget(int count, int limit) {
    return '$count of $limit background processes';
  }

  @override
  String termuxProcsBudgetNote(int limit) {
    return 'Android 12 and later may stop the oldest ones when all apps together run more than $limit.';
  }

  @override
  String termuxProcsBudgetOver(int limit) {
    return 'More than $limit: Android may stop the oldest of these at any time.';
  }

  @override
  String get termuxProcsLoadFailedBody =>
      'Termux did not answer. Open Termux, then try again.';

  @override
  String get termuxProcsRefreshFailed =>
      'Couldn\'t read the list again, so it shows the last reading.';

  @override
  String get termuxProcsStopFailed =>
      'Couldn\'t stop it. Try again, or stop it from Termux.';

  @override
  String get termuxProcsKindOpenCode => 'OpenCode server';

  @override
  String get termuxProcsKindAiTeam => 'AI Team';

  @override
  String get termuxProcsKindClaudeCode => 'Claude Code';

  @override
  String get termuxProcsKindDevService => 'Dev service';

  @override
  String get termuxProcsKindTerminal => 'Terminal';

  @override
  String get termuxProcsKindHelper => 'Helper';

  @override
  String get termuxProcsKindHostApp => 'Termux app';

  @override
  String get termuxProcsBusy => 'Busy';

  @override
  String get termuxProcsIdle => 'Idle';

  @override
  String termuxProcsRunningFor(String elapsed) {
    return 'running $elapsed';
  }

  @override
  String termuxProcsStopKindBody(String names) {
    return '$names: each gets a polite stop, then a forced one after 5 seconds.';
  }

  @override
  String termuxProcsStopKind(int count, String things) {
    return 'Stop all $count $things';
  }

  @override
  String termuxProcsStopKindTitle(int count, String things) {
    return 'Stop all $count $things?';
  }

  @override
  String get termuxProcsKindsAiTeam => 'AI Team processes';

  @override
  String get termuxProcsKindsClaudeCode => 'Claude Code processes';

  @override
  String get termuxProcsKindsDevServices => 'dev services';

  @override
  String get termuxProcsKindsTerminals => 'terminals';

  @override
  String get termuxProcsKindsHelpers => 'helpers';

  @override
  String get termuxProcsStopDevRestart =>
      'The next build starts them again when it needs them.';

  @override
  String get termuxProcsAboutClaudeCode =>
      'Claude Code, the coding agent. Stopping it ends the answer it is writing.';

  @override
  String get termuxProcsAboutTerminal =>
      'A terminal. Stopping it closes it and whatever runs in it.';

  @override
  String get termuxProcsAboutHostApp =>
      'The Termux app itself. It is not stopped from here.';

  @override
  String get termuxProcsAverageCpu => 'Average processor use';

  @override
  String get termuxProcsCpuTime => 'Processor time';

  @override
  String get consentBatteryTitle => 'Keep the server running?';

  @override
  String get consentBatteryBody =>
      'Android may stop the server on this phone while the app is closed. Allow background running and Android asks you to confirm.';

  @override
  String get consentBatteryAllow => 'Allow background running';

  @override
  String get consentMakerTitle => 'Restart the server automatically?';

  @override
  String consentMakerBody(String maker) {
    return '$maker phones stop apps that aren\'t allowed to start by themselves, and the server then stays off. Turn on auto-start for this app on the screen that opens.';
  }

  @override
  String get consentMakerBodyUnnamed =>
      'Some phones stop apps that aren\'t allowed to start by themselves, and the server then stays off. Turn on auto-start for this app on the screen that opens.';

  @override
  String get consentMakerAllow => 'Open auto-start settings';

  @override
  String get consentNotNow => 'Not now';

  @override
  String get consentSaveFailed =>
      'Your answer couldn\'t be saved on this phone, so nothing was changed. Try again.';

  @override
  String get consentStorageFailed =>
      'Your earlier answers on this server couldn\'t be read, so the app won\'t ask them again for now. Reopen this page to try again.';

  @override
  String get consentGroupLabel => 'Your answers';

  @override
  String get consentRowBattery => 'Background running';

  @override
  String get consentRowMaker => 'Start again by itself';

  @override
  String get consentRowNeedsYou => 'Tell me when the agent needs me';

  @override
  String get consentRowAlwaysAllow => 'Always allow offers';

  @override
  String get consentWhyBattery =>
      'Android may stop the server on this phone while the app is closed.';

  @override
  String get consentWhyMaker =>
      'This phone may not start the server again after it stops.';

  @override
  String get consentWhyNeedsYou =>
      'You won\'t get a notification when the agent waits for your answer.';

  @override
  String get consentWhyUnfinished =>
      'The question closed before you answered. Tap to answer now.';

  @override
  String get consentAllowedSystem =>
      'The phone\'s own setting decides. Tap to check it or turn it off.';

  @override
  String get consentAllowedNeedsYou => 'Tap to change it in Notifications.';

  @override
  String get consentWhyAlwaysAllow =>
      'Still asked each time. Tap to be offered Always allow again.';

  @override
  String get consentValueAllowed => 'Allowed';

  @override
  String get consentValueDeclined => 'Declined';

  @override
  String get consentValueUnanswered => 'Not answered';

  @override
  String consentValueDeclinedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count declined',
      one: '1 declined',
    );
    return '$_temp0';
  }

  @override
  String get consentAlwaysAgainTitle => 'Ask to always allow?';

  @override
  String get consentAlwaysAgainBody =>
      'After 3 more identical asks, the app offers to always allow them again. Nothing is allowed until you say so.';

  @override
  String get consentAlwaysAgainConfirm => 'Offer again';

  @override
  String consentAlwaysAllowQuestion(String what) {
    return 'Asked 3 times. Always allow $what?';
  }

  @override
  String get consentAlwaysAllowDecline => 'Keep asking';

  @override
  String get consentAlwaysAllowFailed =>
      'The server didn\'t save Always allow. The request is still waiting; try again or answer it once.';

  @override
  String get consentAlwaysAllowTitle => 'Always allow this request?';

  @override
  String get consentNeedsYouAllow => 'Turn on notifications';

  @override
  String get bootstrapOpeningTitle => 'Opening…';

  @override
  String get bootstrapOpeningBody => 'Reading your saved servers.';

  @override
  String get bootstrapFailedTitle => 'Can\'t read saved servers';

  @override
  String get bootstrapFailedBody =>
      'If your phone just restarted, unlock it, then try again.';

  @override
  String get shareFailedLine =>
      'Shared text saved · couldn\'t open a conversation';

  @override
  String get shareFailedAgainLine =>
      'Still couldn\'t open a conversation · shared text saved';

  @override
  String get shareFailedCopy => 'Copy shared text';

  @override
  String get shareFailedDiscard => 'Discard shared text';

  @override
  String get shareDiscarded => 'Shared text discarded';

  @override
  String get shareConnectionChanged =>
      'The server or project changed while it opened. Try again.';

  @override
  String get appNewConversationFailed => 'Couldn\'t start a new conversation';

  @override
  String get rootPhoneServerStartFailed =>
      'OpenCode on this phone didn\'t start';

  @override
  String get connectionFailureLocalCodexBody =>
      'A local Codex listener should answer on this phone, but nothing did.';

  @override
  String get connectionFailureRemoteCodexBody =>
      'Nothing answered at the Codex endpoint.';

  @override
  String get connectionFailureLoopbackBody =>
      'The app looked for a server running on this phone and got no answer. Start that server, or reconnect the tunnel that brings one here, then try again.';

  @override
  String get connectionFailureTimedOutBody =>
      'Something is at that address, but it did not reply. Usually the network in between, not the server.';

  @override
  String get connectionFailureNothingAnsweredBody =>
      'Nothing answered. Either the server is not running, or this phone cannot reach its address.';

  @override
  String get connectionFailureUnknownBody =>
      'The connection failed. What went wrong is under Details.';

  @override
  String get connectionFailureTailnetCheck =>
      'This is a Tailscale address: is Tailscale on, on this phone and on the server?';

  @override
  String get teamHomeSpentHistoryMissing =>
      'Part of today’s history is missing, so it cost more than this.';

  @override
  String get teamHomeSpentNotRecording =>
      'The team isn’t counting new use right now.';

  @override
  String get teamRunCostUnreported =>
      'Not reported for one task. The AI Team page shows today’s estimate for the whole team.';

  @override
  String get teamHomeUpkeepTitle => 'Team upkeep';

  @override
  String get teamHomeUpkeepPatrol => 'Patrol';

  @override
  String get teamHomeUpkeepChore => 'Chore';

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
  String get teamAgentLooksAfterTeam => 'whole team';

  @override
  String get teamAgentLooksAfterWatchdog => 'watchdog';

  @override
  String get teamAgentLooksAfterWorkers => 'workers';

  @override
  String get servicesStopConfirm => 'Stop service';

  @override
  String get servicesRestartConfirm => 'Restart service';

  @override
  String get managedWorkspacesRemoveConfirm => 'Remove environment';

  @override
  String managedWorkspacesCreateIn(String provider) {
    return 'In $provider';
  }

  @override
  String get voiceAutoSetupTitle => 'Voice typing';

  @override
  String get voiceAutoSetupChecking => 'Checking what this phone can run';

  @override
  String get voiceAutoSetupOffer =>
      'Speak instead of typing. Speech turns into text on this phone, even offline, and audio never leaves it. It needs a one-time download.';

  @override
  String voiceAutoSetupPicked(String model) {
    return '$model speech model, picked for this phone\'s memory';
  }

  @override
  String get voiceAutoSetupMobileData =>
      'You\'re on mobile data. This download counts against your data plan.';

  @override
  String get voiceAutoSetupMaybeMetered =>
      'This connection may count against a data plan.';

  @override
  String voiceAutoSetupDownload(String size) {
    return 'Download $size';
  }

  @override
  String voiceAutoSetupDownloadMobile(String size) {
    return 'Download $size on mobile data';
  }

  @override
  String get voiceAutoSetupOtherModel => 'Choose another speech model';

  @override
  String get voiceAutoSetupNotified =>
      'Progress also shows in your notifications. Listening starts when it\'s done.';

  @override
  String get voiceAutoSetupStartsAfter => 'Listening starts when it\'s done.';

  @override
  String get voiceAutoSetupReady => 'The speech model is on this phone.';

  @override
  String get voiceAutoSetupChooseModel => 'Choose a speech model';

  @override
  String get voiceAutoSetupUnknownMemory =>
      'This phone didn\'t say how much memory it has, so no speech model was picked.';

  @override
  String get voiceAutoSetupOffline =>
      'No internet connection. Connect, then try again.';

  @override
  String get voiceAutoSetupBusy => 'A speech model is already downloading.';

  @override
  String get voiceAutoSetupShowDownload => 'Show the download';

  @override
  String get voiceAutoSetupNoCapture =>
      'This phone can\'t record speech for voice typing.';

  @override
  String get voiceAutoSetupDetailFiles => 'Files';

  @override
  String get voiceAutoSetupDetailSize => 'Exact size';

  @override
  String get voiceAutoSetupDetailMemory => 'Memory';

  @override
  String voiceAutoSetupDetailMemoryValue(int required, int available) {
    return 'Needs $required MB; this phone has $available MB';
  }

  @override
  String get terminalScreenEmptyTitle => 'No terminals yet';

  @override
  String terminalScreenEmptyBody(String project) {
    return 'Start one in $project.';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => 'Start one in this project.';

  @override
  String get integrationsProvidersExplanation =>
      'The model providers this server can use. Connect one to start chatting.';

  @override
  String get integrationsResourcesExplanation =>
      'Files and data that connected MCP servers give the agent.';

  @override
  String externalAgentsStopTaskTitle(String task) {
    return 'Stop “$task”?';
  }

  @override
  String externalAgentsStopTaskConfirm(String agent) {
    return 'Ask $agent to stop';
  }

  @override
  String get externalAgentsStopTaskKeep => 'Keep running';

  @override
  String toolsDetailMenu(String tool) {
    return '$tool actions';
  }

  @override
  String kitDurationHours(int hours) {
    return '$hours h';
  }

  @override
  String kitDurationHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String kitDurationDays(int days) {
    return '$days d';
  }

  @override
  String kitDurationDaysHours(int days, int hours) {
    return '$days d $hours h';
  }

  @override
  String kitToolFor(String duration) {
    return 'for $duration';
  }

  @override
  String kitSinceWaitingForLong(String duration) {
    return 'Waiting $duration';
  }

  @override
  String get teamChatLeadRoutedIt => 'Sent it to the workers';

  @override
  String get teamChatLeadStartingIt => 'Worker started';

  @override
  String get teamChatLeadClaimedWorkerIt => 'Worker took the task';

  @override
  String get teamChatLeadPushedIt => 'Its changes are on a branch';

  @override
  String get teamChatLeadReviewIt => 'Handed it to review';

  @override
  String get teamChatLeadMergedIt => 'Merged it';

  @override
  String get teamChatLeadStepFailedIt => 'It failed';

  @override
  String get teamChatLeadStepCancelledIt => 'It was cancelled';

  @override
  String teamChatNowNoProgress(String elapsed) {
    return 'No progress for $elapsed';
  }

  @override
  String teamChatNoProgressBody(String name, String time) {
    return '$name hasn\'t moved this task since $time. Nudge it to carry on, restart it, or report the problem.';
  }

  @override
  String teamChatNoProgressBodyNoControls(String name, String time) {
    return '$name hasn\'t moved this task since $time. This server can\'t nudge or restart it from here; report the problem or check the team\'s computer.';
  }

  @override
  String get teamChatNoProgressReport => 'Report the problem';

  @override
  String teamChatNoProgressReportTitle(String elapsed) {
    return 'No progress for $elapsed';
  }

  @override
  String get teamTaskDetailsReported => 'What the server reported';

  @override
  String get kitToolOpenDetails => 'Open its details';

  @override
  String get teamStartRunKeepInBacklog => 'Keep in backlog';

  @override
  String workRunawayStopped(String helper) {
    return 'Stopped $helper';
  }

  @override
  String workRunawayStopFailed(String helper) {
    return 'Couldn\'t stop $helper. Try again, or stop it from Termux.';
  }

  @override
  String get serverSettingsUpdateCommandsDetail =>
      'Run them in a terminal on the server\'s computer; this app can\'t update it.';

  @override
  String get serverSettingsUpdateCommandsCopied =>
      'Copied. Run them in a terminal on the server\'s computer.';

  @override
  String hostServiceTitle(String server) {
    return 'Linux service for $server';
  }

  @override
  String hostServiceIntro(String server) {
    return 'These commands run on $server\'s computer; copy each into a terminal there.';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return 'To do · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList =>
      'OpenCode can’t list the devices on your tailnet.';

  @override
  String get productErrorStagedRevert =>
      'Review the staged revert before sending this queued prompt.';

  @override
  String teamWatchComposerHint(String name) {
    return 'Message $name…';
  }

  @override
  String get teamWatchComposerHintWorker => 'Message the worker…';

  @override
  String get teamWatchComposerHintAgent => 'Message this agent…';

  @override
  String teamWatchAbout(String name) {
    return 'About $name';
  }

  @override
  String teamWatchAboutRole(String role) {
    return 'About the $role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return 'The queued prompts for $name cannot be read. The server and its queued prompts were kept. Try removing it again after the queue can be read.';
  }

  @override
  String get bootstrapStartFresh => 'Start fresh';

  @override
  String get bootstrapStartFreshTitle => 'Remove saved sign-ins?';

  @override
  String get bootstrapStartFreshBody =>
      'This removes saved passwords and connection tokens from this phone and clears the selected server. Your saved servers, queued prompts and drafts are kept.';

  @override
  String get bootstrapStartFreshConfirm => 'Remove saved sign-ins';

  @override
  String get bootstrapResettingTitle => 'Removing saved sign-ins…';

  @override
  String get bootstrapResettingBody => 'Keep the app open while this finishes.';

  @override
  String get bootstrapResetFailedTitle => 'Sign-in reset failed';

  @override
  String get bootstrapResetFailedBody =>
      'Some saved sign-ins could not be removed. Try again.';

  @override
  String get workStalled => 'Stalled';

  @override
  String get teamNowActivityPlanning => 'Waiting for a plan';

  @override
  String get teamNowActivityWaitingForWorker => 'Waiting for a worker';

  @override
  String get teamNowActivityStartingWorker => 'Starting a worker';

  @override
  String get teamNowActivityWorking => 'Working on your task';

  @override
  String get teamNowActivityReviewing => 'Reviewing the changes';

  @override
  String get teamNowActivityNeedsYou => 'Waiting for your answer';

  @override
  String get teamNowActivityDelayed => 'Taking longer than expected';

  @override
  String get teamNowActivityUnconfirmed => 'Request not confirmed';

  @override
  String get teamNowActivityRefused => 'Request not accepted';

  @override
  String get teamNowActivityUnavailable => 'The team isn\'t answering';

  @override
  String get teamNowActivityCompleted => 'Finished';

  @override
  String get teamNowActivityFailed => 'Could not finish';

  @override
  String get teamNowActivityCancelled => 'Stopped';

  @override
  String get teamNowReasonNoPlanReported =>
      'No plan has been reported yet. The reason is unknown.';

  @override
  String get teamNowReasonNoWorkerReported =>
      'No worker has been reported yet.';

  @override
  String get teamNowReasonWorkerStarting =>
      'The worker has started but hasn\'t begun the task.';

  @override
  String teamModelRowTitle(String model) {
    return 'Workers use $model';
  }

  @override
  String get teamModelDefault => 'Same as this phone\'s OpenCode';

  @override
  String get teamModelDefaultHint =>
      'Uses the model this phone\'s OpenCode is set to.';

  @override
  String get teamModelChange =>
      'Change. Takes effect the next time a worker starts.';

  @override
  String get teamModelSheetTitle => 'Model for the workers';

  @override
  String get teamModelSheetNote =>
      'Only models this phone\'s OpenCode can use. A worker that is already running keeps its model.';

  @override
  String get teamModelNoneLoaded =>
      'This phone\'s models have not loaded yet. Close this and try again in a moment.';

  @override
  String get teamModelFailed =>
      'Could not change the model. The team keeps the one it had.';

  @override
  String get teamNowReasonWorkerPreparing =>
      'The worker is being set up: its folder is made and its program is starting.';

  @override
  String get teamNowReasonWorkerRunning =>
      'The worker\'s program is running. The team has not reported the task reaching it yet.';

  @override
  String get teamNowReasonWorkerTaskDelivered =>
      'The task has reached the worker. It is reading it before it begins.';

  @override
  String teamNowLastStart(String duration) {
    return 'took $duration last time';
  }

  @override
  String get teamUiHostPhraseBusyStartingWorker => 'Busy starting a worker';

  @override
  String get teamNowReasonWorkInProgress => 'The task is being worked on.';

  @override
  String get teamNowReasonReviewPending =>
      'Review or completion is still pending.';

  @override
  String get teamNowReasonAnswerNeeded =>
      'The team is waiting for your answer.';

  @override
  String get teamNowReasonWorkerCouldNotStart =>
      'The worker couldn\'t stay running.';

  @override
  String get teamNowReasonProviderLimit =>
      'The AI service reported a usage limit.';

  @override
  String get teamNowReasonWorkTakingLonger =>
      'The work is taking longer than expected.';

  @override
  String get teamNowReasonConfirmationMissing =>
      'We can\'t confirm the request arrived. Check before sending it again.';

  @override
  String get teamNowReasonRequestRefused => 'The request was not accepted.';

  @override
  String get teamNowReasonConnectionUnavailable =>
      'Progress can\'t be checked while disconnected.';

  @override
  String get teamNowReasonCauseUnknown =>
      'The reason is unknown. Check what the team is doing.';

  @override
  String get teamNowWhyPlanning =>
      'The planner turns your task into steps. This conversation follows the task as soon as the team lists them. Stopping following it here doesn\'t cancel it on the team\'s computer.';

  @override
  String get teamNowWhyWaitingForWorker =>
      'The team looks for new work regularly and starts a worker for it when one is free.';

  @override
  String get teamNowWhyStartingWorker =>
      'A new worker makes its own copy of the project and starts its program before it reads the task. That is the slow part on a phone, and the stage above is what the team reports.';

  @override
  String get teamNowWhyWorking =>
      'The worker makes the changes on its own copy, then hands them to review.';

  @override
  String get teamNowWhyReviewing =>
      'A reviewer checks the changes before they are merged.';

  @override
  String get teamNowWhyUnconfirmed =>
      'The app sent the task but didn\'t hear back. Sending it again could start it twice, so look at the planner first.';

  @override
  String get teamNowWhyWorkerCouldNotStart =>
      'The worker stopped while it was starting. Its conversation may say why.';

  @override
  String get teamNowWhyProviderLimit =>
      'The AI service limits how much can be used in a period. Work continues when the limit resets, or you can stop the task.';

  @override
  String get teamNowWhyWorkTakingLonger =>
      'Large tasks can take a while. Watching the worker shows whether it is still moving.';

  @override
  String get teamNowWhyCauseUnknown =>
      'What the team reports doesn\'t say why it is waiting.';

  @override
  String get teamNowWhyHide => 'Hide';

  @override
  String get teamNowNextPlan => 'Next: the team lists the steps';

  @override
  String get teamNowNextWorker => 'Next: a worker starts';

  @override
  String get teamNowNextWork => 'Next: the worker begins the task';

  @override
  String get teamNowNextReview => 'Next: the changes are reviewed';

  @override
  String get teamNowNextFinish => 'Next: the task finishes';

  @override
  String get teamNowWatchPlanner => 'Watch the planner';

  @override
  String get teamNowDismissRequest => 'Stop following this request';

  @override
  String teamNowUsuallyWithin(String duration) {
    return 'usually within $duration';
  }

  @override
  String teamNowWatchAgent(String name) {
    return 'Watch $name';
  }

  @override
  String get teamNowNotStartingLine => 'The team isn\'t starting a worker';

  @override
  String get aiSetupTitle => 'AI setup';

  @override
  String get aiSetupEntryDetail =>
      'Models, tools and suggestions for this server';

  @override
  String get aiSetupRefresh => 'Read this server\'s setup again';

  @override
  String get aiSetupLoading => 'Reading this server\'s setup…';

  @override
  String get aiSetupReviewOnly =>
      'Review only. Changes are made on the server for now.';

  @override
  String get aiSetupUnsupportedTitle => 'AI setup isn\'t available';

  @override
  String get aiSetupUnsupportedBody =>
      'This server doesn\'t share its configuration with the app. Set up its models and tools on the server itself.';

  @override
  String get aiSetupSignInTitle => 'Sign-in needed';

  @override
  String aiSetupSignInBody(String server) {
    return '$server didn\'t accept the saved sign-in, so its setup can\'t be read.';
  }

  @override
  String get aiSetupErrorTitle => 'Couldn\'t read setup';

  @override
  String get aiSetupErrorBody =>
      'The server didn\'t answer as expected. Try again, or check the server on its settings page.';

  @override
  String get aiSetupTryAgain => 'Try again';

  @override
  String get aiSetupOfflineTitle => 'You\'re offline';

  @override
  String aiSetupOfflineBody(String server) {
    return 'Reconnect to $server to read its setup.';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return 'Offline. This is $server\'s setup as last read; it updates when you reconnect.';
  }

  @override
  String get aiSetupEmptyTitle => 'Nothing set up yet';

  @override
  String get aiSetupEmptyBody =>
      'This server runs on its defaults, with no model chosen and no tool servers. Changes are made on the server for now.';

  @override
  String get aiSetupSuggestionsLabel => 'Suggestions';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return 'Sign in to $name';
  }

  @override
  String get aiSetupSuggestSignInDetail =>
      'Its tools stay off until someone signs in to it on the server.';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return 'Check $name\'s settings';
  }

  @override
  String get aiSetupSuggestFixDetail =>
      'It failed to start. Fix its entry in the server\'s configuration, then restart the server.';

  @override
  String get aiSetupSuggestModelTitle => 'Choose a default model';

  @override
  String get aiSetupSuggestModelDetail =>
      'No model is set, so new conversations use the server\'s own pick. Set “model” in the server\'s configuration.';

  @override
  String get aiSetupSuggestToolsTitle => 'Add tool servers';

  @override
  String get aiSetupSuggestToolsDetail =>
      'No MCP servers are set up. Add one in the server\'s configuration to give the agent more tools.';

  @override
  String get aiSetupToolsLabel => 'Tool servers';

  @override
  String get aiSetupToolsTerm =>
      'MCP servers give the agent extra tools. Each shows whether it is working now.';

  @override
  String get aiSetupToolConnected => 'Connected';

  @override
  String get aiSetupToolWaiting => 'Waiting';

  @override
  String get aiSetupToolOff => 'Off';

  @override
  String get aiSetupToolFailed => 'Failed';

  @override
  String get aiSetupToolNeedsSignIn => 'Needs sign-in';

  @override
  String get aiSetupToolUnknown => 'Unknown';

  @override
  String get aiSetupEffectiveLabel => 'Settings in effect';

  @override
  String get aiSetupEffectiveTerm =>
      'What this server\'s conversations use, after combining its configuration files.';

  @override
  String get aiSetupModel => 'Model';

  @override
  String get aiSetupServerDefault => 'Not set: the server picks';

  @override
  String get aiSetupSmallModel => 'Small model';

  @override
  String get aiSetupDefaultAgent => 'Default agent';

  @override
  String get aiSetupProviders => 'Providers';

  @override
  String get aiSetupPermissions => 'Permissions';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules',
      one: '1 rule',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => 'All settings';

  @override
  String get aiSetupSourcesLabel => 'Configuration sources';

  @override
  String get aiSetupSourcesTerm =>
      'Listed from lowest to highest priority, as the server reports them. The app doesn\'t combine them.';

  @override
  String get aiSetupNoSources => 'No configuration files';

  @override
  String get aiSetupNoSourcesDetail => 'This server runs on its defaults.';

  @override
  String aiSetupSourceUnnamed(String type) {
    return 'Source without a file ($type)';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position. Sets $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position. Sets nothing';
  }

  @override
  String get aiSetupAllSources => 'All sources';

  @override
  String get integrationsPageLoadFailed => 'Could not load this page';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · Refreshing';
  }

  @override
  String get kitLastKnownHint =>
      'Saved from last time. They open once the live list loads.';

  @override
  String get kitTranscriptExcerptHint =>
      'Saved from last time. The conversation opens fully once it loads.';

  @override
  String get lastKnownUpdatedJustNow => 'Updated just now';

  @override
  String lastKnownUpdatedAgo(String ago) {
    return 'Updated $ago';
  }

  @override
  String serverRowQueuedWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts waiting to send',
      one: '1 prompt waiting to send',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Move $count waiting prompts to $destination',
      one: 'Move 1 waiting prompt to $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveTitle => 'Move queued prompts';

  @override
  String queuedMoveSubtitle(String source) {
    return 'From $source';
  }

  @override
  String get queuedMovePromptsLabel => 'Prompts';

  @override
  String get queuedMoveConversationLabel => 'Conversation';

  @override
  String get queuedMoveNewConversation => 'New conversation';

  @override
  String queuedMoveQueuedAt(String time) {
    return 'Queued $time';
  }

  @override
  String queuedMoveFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveBlockedUncertain(String source) {
    return 'May already have been sent. Check it on $source first.';
  }

  @override
  String queuedMoveBlockedFile(String source) {
    return 'Has a file only $source can open';
  }

  @override
  String get queuedMoveBlockedMentions =>
      'Hiding a password in it would break its agent mentions';

  @override
  String queuedMoveHidesSecrets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Passwords and keys in $count prompts stay hidden',
      one: 'Passwords and keys in 1 prompt stay hidden',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUsesCurrentModel(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts use the model chosen on $destination',
      one: '1 prompt uses the model chosen on $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveAction(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Move $count prompts to $destination',
      one: 'Move 1 prompt to $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveChooseOne => 'Choose at least one prompt';

  @override
  String queuedMoveNoneLeft(String source) {
    return 'Nothing waits for $source any more';
  }

  @override
  String queuedMoveFailedDisconnected(String destination) {
    return '$destination disconnected, so nothing moved. Connect to it and try again.';
  }

  @override
  String queuedMoveFailedConversationGone(String destination) {
    return 'That conversation is no longer on $destination, so nothing moved. Choose another one.';
  }

  @override
  String queuedMoveFailedNothing(String source) {
    return 'These prompts no longer wait for $source, so nothing moved.';
  }

  @override
  String queuedMoveFailedNewConversation(String destination) {
    return 'Could not start a new conversation on $destination, so nothing moved. Try again or choose an existing conversation.';
  }

  @override
  String queuedMoveFailedNotSaved(String source) {
    return 'Could not save the move, so nothing moved. The prompts still wait for $source.';
  }

  @override
  String queuedMoveDone(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts moved to $destination',
      one: '1 prompt moved to $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveDonePartial(int moved, int total, String destination) {
    return 'Moved $moved of $total prompts to $destination. The rest no longer waited.';
  }

  @override
  String queuedMoveUndoNone(String destination) {
    return 'The prompts already started sending on $destination, so they stay there.';
  }

  @override
  String queuedMoveUndoPartial(int count, String destination, String source) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count prompts already started sending on $destination and stay there. The rest wait for $source again.',
      one:
          '1 prompt already started sending on $destination and stays there. The rest wait for $source again.',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUndoFailed(String destination) {
    return 'Could not put the prompts back. They stay on $destination.';
  }

  @override
  String workStalledSince(String time) {
    return 'Stalled since $time';
  }

  @override
  String attentionOnServer(String server) {
    return 'on $server';
  }

  @override
  String get attentionTeamTask => 'Team task';

  @override
  String attentionChecksOff(String servers) {
    return 'Not checking $servers';
  }

  @override
  String get attentionChecksOffDetail =>
      'Their requests don\'t show here. Turn on checks in Notifications.';

  @override
  String attentionUnchecked(String server) {
    return 'Couldn\'t check $server';
  }

  @override
  String get attentionUncheckedDetail =>
      'Requests waiting there may be missing here.';

  @override
  String attentionUncheckedSince(String time) {
    return 'Last checked $time. Requests waiting there may be missing here.';
  }

  @override
  String attentionWaitsForWifi(String server) {
    return '$server is checked on Wi-Fi only';
  }

  @override
  String attentionChecksPaused(String server) {
    return 'Checks on $server are paused';
  }

  @override
  String get sessionAddressInclude => 'Include this server’s address';

  @override
  String get sessionAddressDisclosure =>
      'The link then shows this address and the conversation ID, never a password: the other phone still needs its own access. Screenshots, messages and the clipboard can keep it.';

  @override
  String get sessionAddressIntro =>
      'Scan with OpenCode Mobile on the other phone. The code holds this server’s address and the conversation ID.';

  @override
  String get sessionAddressUnsupportedHost =>
      'Only a private HTTPS address ending in .ts.net can go in a link.';

  @override
  String get sessionAddressOpenTitle => 'Open a shared conversation';

  @override
  String get sessionAddressConsentSaved => 'Open on this saved server?';

  @override
  String get sessionAddressConsentNew => 'Add this server?';

  @override
  String get sessionAddressNotSaved => 'Not saved on this phone';

  @override
  String get sessionAddressConsentNote =>
      'The link grants no access. Checking only asks the server which installation it is; nothing signs in and no password is sent.';

  @override
  String get sessionAddressCheck => 'Check server';

  @override
  String sessionAddressChecking(String host) {
    return 'Checking $host…';
  }

  @override
  String get sessionAddressAddBody =>
      'This server is not saved on this phone. Add it with your own sign-in; the link does not carry one.';

  @override
  String get sessionAddressAddServer => 'Add server';

  @override
  String get sessionAddressChooseBody =>
      'More than one saved server uses this address. Choose the one to open the conversation on.';

  @override
  String sessionAddressVerifyBody(String name) {
    return 'Confirm that $name is the server this link came from. The phone remembers this for $name; it does not sign in or share a password.';
  }

  @override
  String get sessionAddressVerify => 'Verify server';

  @override
  String sessionAddressReadyBody(String name) {
    return '$name matches this link.';
  }

  @override
  String sessionAddressSignInBody(String name) {
    return 'Sign in to $name with your own account first, then open the conversation.';
  }

  @override
  String get sessionAddressSignIn => 'Sign in';

  @override
  String get sessionAddressOpen => 'Open conversation';

  @override
  String get sessionAddressOpening => 'Opening the conversation…';

  @override
  String get sessionAddressReason => 'Reason';

  @override
  String get sessionAddressFailUnavailable =>
      'Conversation links with a server address are not available yet.';

  @override
  String get sessionAddressFailInvalidLink =>
      'This conversation link is not valid. Scan or copy it again.';

  @override
  String get sessionAddressFailTooLarge =>
      'This link is too long. Ask the sender for a new link.';

  @override
  String get sessionAddressFailCredentials =>
      'This link contains private sign-in information and cannot be used.';

  @override
  String get sessionAddressFailConsentRequired =>
      'Choose whether to include this server’s address first.';

  @override
  String get sessionAddressFailPrivateRouteRequired =>
      'This server cannot be reached through the required private connection. Check your connection.';

  @override
  String get sessionAddressFailUnreachable =>
      'The server could not be reached. Check your connection and try again.';

  @override
  String get sessionAddressFailTimedOut =>
      'The server did not answer in time. Try again.';

  @override
  String get sessionAddressFailTlsRejected =>
      'The server’s secure connection could not be verified, so the link was not opened.';

  @override
  String get sessionAddressFailRedirectsRejected =>
      'This server tried to send the request somewhere else. The link was not opened.';

  @override
  String get sessionAddressFailAccessDenied =>
      'Your access to this server or conversation was refused.';

  @override
  String get sessionAddressFailInvalidDescriptor =>
      'This server did not provide the information needed to open this link.';

  @override
  String get sessionAddressFailInstanceMismatch =>
      'This link and the saved server do not identify the same installation.';

  @override
  String get sessionAddressFailBindingRequired =>
      'Verify this saved server before opening the conversation.';

  @override
  String get sessionAddressFailAmbiguousProfile =>
      'Choose which saved server to use.';

  @override
  String get sessionAddressFailProfileMissing =>
      'This saved server is no longer available.';

  @override
  String get sessionAddressFailStorage =>
      'The server verification could not be saved or read. Try again after restarting the app.';

  @override
  String get sessionAddressFailSignInRequired =>
      'Sign in to this server with your own account before continuing.';

  @override
  String get sessionAddressFailUnsafeLookup =>
      'This server has not been verified for private conversation links.';

  @override
  String get sessionAddressFailSessionMissing =>
      'This conversation is not available on this server.';

  @override
  String get sessionAddressFailCancelled => 'Opening this link was cancelled.';

  @override
  String get removeFromPhoneDeleteAllChoice => 'Delete everything…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return 'Delete everything, freeing about $size…';
  }

  @override
  String get phoneServerCardErrorDetail => 'Error';

  @override
  String get sessionMenuGoTo => 'Go to';

  @override
  String get sessionMenuDo => 'Do';

  @override
  String get sessionMenuFind => 'Find';

  @override
  String get sessionMenuSubagents => 'Subagents';

  @override
  String get sessionMenuDetails => 'Details';

  @override
  String get sessionMenuShareHint => 'Anyone with the link can read it';

  @override
  String get sessionMenuStopSharingHint => 'The public link stops working';

  @override
  String get sessionMenuCompactHint =>
      'Summarizes it so the agent has room again';

  @override
  String get sessionMenuForkHint => 'Opens a copy you can take another way';

  @override
  String get sessionMenuContinueComputerHint =>
      'Shows the command that resumes it there';

  @override
  String get sessionMenuContinuePhoneHint =>
      'Shows a code the app on that phone opens';

  @override
  String get sessionMenuNeedsPrompt => 'Available after the first prompt';

  @override
  String commandSheetServerGroup(String server) {
    return 'Commands from $server';
  }

  @override
  String commandSheetAgentMissingTitle(String agent) {
    return '$agent commands unavailable';
  }

  @override
  String commandSheetAgentMissingWhy(String agent) {
    return '$agent doesn\'t share its own commands with the app yet, so the app can\'t list them, run them, or run ! shell commands. The app\'s own actions still work.';
  }

  @override
  String commandSheetAgentCommandNotSent(String command, String agent) {
    return '$command wasn\'t sent: $agent doesn\'t share its commands with the app yet. Remove the / to send it as a message.';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return '$command wasn\'t sent: shell commands can\'t run on $agent from the app. Remove the ! to send it as a message.';
  }

  @override
  String get commandSheetShellDescription =>
      'Or start a message with ! to run it from the composer';

  @override
  String get commandSheetRetryDescription => 'Sends your last prompt again';

  @override
  String get commandSheetNoteDescription =>
      'A note the agent keeps in mind for this conversation';

  @override
  String get commandSheetApprovalsDescription =>
      'What this conversation may do without asking';

  @override
  String get commandSheetReloadDescription =>
      'Reads this conversation from the server again';

  @override
  String get commandSheetLibrarySubtitle =>
      'Pick a command, then the conversation it runs in';

  @override
  String get commandSheetAgentFallback => 'This agent';

  @override
  String get commandSheetPlanDescription =>
      'Opens the agent\'s latest plan in the conversation';

  @override
  String get chatUiSessionMenu => 'Conversation menu';

  @override
  String get commandsScreenLoadFailed => 'Couldn’t load commands';

  @override
  String get commandSheetSubtitleAppOnly =>
      'Run one of the app\'s actions in this conversation';

  @override
  String get voiceModeMicAsk =>
      'Voice typing needs the microphone. Tap Allow microphone, then choose Allow.';

  @override
  String get voiceModeMicAllow => 'Allow microphone';

  @override
  String get voiceModeMicBlocked =>
      'Android blocks the microphone for this app. Turn it on in Android settings, then come back here.';

  @override
  String get voiceModeNothingHeard =>
      'Nothing was heard. Tap the mic and try again.';

  @override
  String get teamDispatchCreating => 'Creating your task…';

  @override
  String get teamDispatchSending => 'Task created · sending it to the team…';

  @override
  String get teamDispatchAwaitingWorker =>
      'Task sent to the team · waiting for a worker';

  @override
  String get teamDispatchWorkerStarted => 'A worker started your task';

  @override
  String get teamDispatchCreateRefused =>
      'The task wasn’t made. Change it and send it again.';

  @override
  String get teamDispatchAssignRefused =>
      'Task created, but it could not be sent to the team';

  @override
  String get teamDispatchAssignRefusedHint =>
      'The task stays on the board, given to no one.';

  @override
  String get teamDispatchCreateUnconfirmed =>
      'Couldn’t confirm whether the task was created';

  @override
  String get teamDispatchDispatchUnconfirmed =>
      'Task created · couldn’t confirm it reached the team';

  @override
  String get teamDispatchCheckBoard =>
      'Check the board before sending it again. Your words are kept.';

  @override
  String get teamDispatchUnknown =>
      'Task sent · the team can’t be reached, so whether a worker started is unknown';

  @override
  String get teamDispatchCheckAgain => 'Check the team again';

  @override
  String get teamDispatchTaskId => 'Task ID';

  @override
  String get teamDispatchHostWords => 'The team’s reply';

  @override
  String get teamUiHostGuideOpen => 'Open the full guide';

  @override
  String hostServiceInstallChecked(String release) {
    return 'Downloads the script from release $release and checks its SHA-256 checksum first. If the file was changed, nothing runs.';
  }

  @override
  String get hostServiceWhatThisDoes => 'What this does';

  @override
  String get hostServiceWhatLinux =>
      'Needs Linux with systemd, such as Ubuntu. It does not run on macOS or Windows.';

  @override
  String get hostServiceWhatInstall =>
      'Installs OpenCode with its official installer if it is not there yet.';

  @override
  String get hostServiceWhatService =>
      'Adds a service for your account that keeps OpenCode running after reboots and closed terminals. It listens on that computer only.';

  @override
  String get hostServiceWhatPassword =>
      'Makes a password for the server and keeps it in a file only your account can read.';

  @override
  String get hostServicePinnedCommit => 'Script version';

  @override
  String get hostServiceChecksum => 'SHA-256 checksum';

  @override
  String get mcpAddBrowseTitle => 'Browse the catalogue';

  @override
  String get mcpAddBrowseDetail =>
      'Servers from the public MCP registry, turned on with a switch';

  @override
  String get mcpAddBrowseNone =>
      'No catalogue for this server: it doesn\'t accept new MCP servers from the app.';

  @override
  String get mcpAddManualTitle => 'Enter manually';

  @override
  String get mcpAddManualDetail =>
      'Type its address, or the command that starts it';

  @override
  String get mcpCatalogTitle => 'MCP catalogue';

  @override
  String get mcpCatalogConsentTitle => 'Load the MCP registry?';

  @override
  String get mcpCatalogConsentBody =>
      'The app asks registry.modelcontextprotocol.io for its list of MCP servers. It sends only what you search for, nothing about you or your servers.';

  @override
  String get mcpCatalogConsentLoad => 'Load the list';

  @override
  String get mcpCatalogForget => 'Stop using the registry';

  @override
  String get mcpCatalogForgetFailed =>
      'Couldn\'t forget the saved registry list. Try again.';

  @override
  String get mcpCatalogSearch => 'Search the registry';

  @override
  String get mcpCatalogInventoryFailed =>
      'Couldn\'t read this server\'s MCP servers';

  @override
  String get mcpCatalogInventoryFailedBody =>
      'The switches need to know what is already on. Check the connection, then try again.';

  @override
  String get mcpCatalogFailed => 'Couldn\'t load the public MCP registry';

  @override
  String get mcpCatalogSearchFailed =>
      'Couldn\'t search the public MCP registry';

  @override
  String get mcpCatalogFailedBody =>
      'Check the phone\'s internet connection, then try again. You can still enter a server by hand.';

  @override
  String get mcpCatalogEmpty => 'The registry listed no servers';

  @override
  String mcpCatalogNoMatch(String query) {
    return 'Nothing in the registry matches “$query”';
  }

  @override
  String get mcpCatalogEmptyBody =>
      'Try other words, or enter the server by hand.';

  @override
  String get mcpCatalogStale =>
      'Couldn\'t refresh the list from the registry. These are the listings loaded earlier.';

  @override
  String get mcpCatalogPriceNote =>
      'The registry lists no prices. A hosted server\'s owner may charge for it or ask for an account.';

  @override
  String get mcpCatalogAdding => 'Adding…';

  @override
  String get mcpCatalogRemoving => 'Removing…';

  @override
  String get mcpCatalogCannotRemove =>
      'On. This server keeps it in its configuration, and the app can\'t remove it.';

  @override
  String get mcpCatalogNeedsDocker =>
      'Runs in Docker. To add it anyway, use Enter manually.';

  @override
  String get mcpCatalogNoEndpoint =>
      'Lists nothing the app can start. To add it anyway, use Enter manually.';

  @override
  String mcpCatalogHostedBy(String host) {
    return 'Hosted by $host';
  }

  @override
  String get mcpCatalogNeedsNode => 'Needs Node on the server';

  @override
  String get mcpCatalogNeedsNodePhone => 'Needs Node on this phone';

  @override
  String get mcpCatalogNeedsPython => 'Needs Python with uv on the server';

  @override
  String get mcpCatalogNeedsKey => 'Needs an API key';

  @override
  String get mcpCatalogNeedsSettings => 'Needs extra settings';

  @override
  String mcpCatalogNodeTitle(String title) {
    return '$title runs with Node';
  }

  @override
  String get mcpCatalogNodeAdd => 'Add Node to this phone';

  @override
  String get mcpCatalogNodeAddDetail =>
      'Opens This phone. Choose Add tools, then Node, and turn this on again once it\'s added.';

  @override
  String get mcpCatalogNodeHave => 'Node is already on this phone';

  @override
  String get mcpCatalogNodeHaveDetail => 'Check the details and add it';

  @override
  String mcpSetupFromCatalog(String listing, String server) {
    return 'Filled in from “$listing” in the public MCP registry. Check it before you add it: $server will run or connect to what is here.';
  }

  @override
  String get mcpSetupThisServer => 'this server';

  @override
  String mcpSetupValueRequired(String name) {
    return 'Enter a value for $name';
  }

  @override
  String get mcpSetupNameFromCatalog => 'The registry listing needs this one';

  @override
  String get mcpVariableName => 'Variable name';

  @override
  String get mcpVariableValue => 'Variable value';

  @override
  String get mcpAddVariable => 'Add another variable';

  @override
  String get mcpRemoveVariable => 'Remove variable';

  @override
  String get mcpSetupTimeoutSeconds => 'Timeout in seconds';

  @override
  String get isolatedTaskPromptLabel => 'What should it work on?';

  @override
  String get isolatedTaskPromptHelper =>
      'Sent once the copy is ready. Leave it empty to write it in the conversation.';

  @override
  String get isolatedTaskOptions => 'Options';

  @override
  String get isolatedTaskPreparingHint =>
      'If you stop waiting, the copy stays. You\'ll find it under Project › Worktrees.';

  @override
  String get isolatedTaskFailedBody =>
      'The copy is made, but its setup didn\'t finish. Start in it anyway, or remove it.';

  @override
  String isolatedTaskSending(String name) {
    return 'Sending your task to $name…';
  }

  @override
  String get isolatedTaskSendFailed => 'Couldn\'t send your task';

  @override
  String get isolatedTaskSendFailedBody =>
      'It\'s waiting in the conversation\'s message box, ready to send.';

  @override
  String get isolatedTaskSendFailedLost =>
      'Copy your task below and send it in the conversation.';

  @override
  String get isolatedTaskOpenConversation => 'Open the conversation';

  @override
  String get isolatedTaskStartAnyway => 'Start anyway';

  @override
  String get isolatedTaskRemove => 'Remove the copy';

  @override
  String isolatedTaskRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get isolatedTaskRemoveBody =>
      'Its folder and branch are deleted. Your project itself is not touched.';

  @override
  String isolatedTaskRemoved(String name) {
    return 'Removed $name. You can start again.';
  }

  @override
  String get isolatedTaskSetupOutput => 'What the setup reported';

  @override
  String get isolatedTaskCopyFolder => 'Folder of the copy';

  @override
  String get isolatedTaskBranchLabel => 'Branch';

  @override
  String get isolatedTaskStageSend =>
      'Opening the conversation and sending your task';

  @override
  String get teamStartRunBlockedTitle => 'Team can\'t take tasks';

  @override
  String get teamStartRunPlannerOff => 'The planner is switched off';

  @override
  String get teamStartRunPlannerOffWakeBody =>
      'The planner turns each task into steps for the team. Wake it to give the team your task.';

  @override
  String get teamStartRunPlannerOffHostBody =>
      'The planner turns each task into steps for the team, and this app can\'t switch it on. Switch it on where the team runs, then try again.';

  @override
  String get teamStartRunNoPlanner => 'This team has no planner';

  @override
  String get teamStartRunNoPlannerBody =>
      'A planner turns each task into steps for the team. Add one where the team runs, then try again.';

  @override
  String get teamStartRunNoProject => 'This team has no project yet';

  @override
  String get teamStartRunNoProjectBody =>
      'Tasks go straight to a project\'s worker. Add a project to the team, then try again.';

  @override
  String get teamStartRunWake => 'Wake the planner';

  @override
  String get teamStartRunWakeAsked =>
      'Waking the planner. The task form opens as soon as it\'s awake.';

  @override
  String get teamStartRunStillOff => 'The planner is still switched off.';

  @override
  String get teamStartRunStillNoProject => 'The team still has no project.';

  @override
  String get teamStartRunWakeRefused => 'Couldn\'t wake the planner';

  @override
  String get teamStartRunWakeRefusedNext =>
      'Try again, or switch it on where the team runs.';

  @override
  String get addServerTailscaleNext => 'Enter the address';

  @override
  String get phoneSetupTermuxGetCurrent => 'Get the current Termux';

  @override
  String get phoneSetupUnsupportedTitle => 'Connect a server';

  @override
  String get phoneSetupUnsupportedBody =>
      'Setting up on the device itself works only on Android phones. On your computer, run this command, then add the server here with the code it prints.';

  @override
  String get termuxStorageStageTotal => 'The whole Termux install';

  @override
  String get setupProgressViewFailedStep =>
      'This step didn\'t finish. What went wrong is under Details.';

  @override
  String setupProgressViewFailedAt(String name) {
    return 'Stopped at $name. What went wrong is under Details.';
  }

  @override
  String workRunawayHelper(String helper, String duration) {
    return 'A leftover $helper process has been busy for $duration with nothing to do';
  }

  @override
  String workRunawayHelperInProject(
    String helper,
    String project,
    String duration,
  ) {
    return 'A leftover $helper process in $project has been busy for $duration with nothing to do';
  }

  @override
  String get workRunawaySeeRunning => 'See what\'s running';

  @override
  String thisPhoneUpToDate(String version) {
    return 'Up to date · $version';
  }

  @override
  String thisPhoneUpdateTitle(String runtime) {
    return 'Update $runtime?';
  }

  @override
  String thisPhoneUpdateBody(String version) {
    return 'Installs version $version, restarts the server on this phone and connects again.';
  }

  @override
  String get thisPhoneUpdateKept =>
      'Your conversations are kept. The server is away for a minute while it restarts.';

  @override
  String get thisPhoneUpdateBusy =>
      'A reply is still being written. Stop it or let it finish, then update.';

  @override
  String thisPhoneStartFailed(String runtime) {
    return '$runtime didn\'t start. Start it again; Details below says what went wrong.';
  }

  @override
  String get thisPhoneStartAgain => 'Start again';

  @override
  String thisPhoneInstallFailed(String runtime) {
    return 'Installing $runtime didn\'t finish. Install it again; your conversations are kept.';
  }

  @override
  String get thisPhoneInstallAgain => 'Install again';

  @override
  String thisPhoneStopFailed(String runtime) {
    return '$runtime didn\'t stop. Try stopping it again.';
  }

  @override
  String thisPhoneCheckFailed(String runtime) {
    return 'This phone couldn\'t check on $runtime. Try again in a moment.';
  }

  @override
  String thisPhoneSwitchStopped(String runtime) {
    return '$runtime didn\'t start after the switch. Your conversations are kept.';
  }

  @override
  String get addServerCheckFailedPlain =>
      'The server could not be checked. Check the address and this phone’s connection, then try again.';

  @override
  String serverRowDetailsTitle(String name) {
    return '$name details';
  }

  @override
  String get pluginsTeamRowTurnOn => 'Turn on';

  @override
  String get teamUiHostGuideEnterAddress => 'Enter the address';

  @override
  String get commandAuthStartFailed => 'Sign-in didn\'t start.';

  @override
  String get commandAuthCheckFailed =>
      'Couldn\'t check the sign-in. Try again.';

  @override
  String get commandAuthTryAgain => 'Try again';

  @override
  String get draftLeaveMessageNoText =>
      'Try saving again. If you leave without saving, your latest changes to this draft may be lost.';

  @override
  String get draftLeaveCopyAction => 'Copy draft and leave';

  @override
  String get draftLeaveRetry => 'Try saving again';

  @override
  String get draftLeaveStillFailing =>
      'Still not saved. Copy your text before you leave.';

  @override
  String get queuedRetry => 'Try again';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Try all $count again',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return 'Use $model and resend';
  }

  @override
  String get chatUiChooseAnotherModel => 'Choose another model';

  @override
  String get chatUiSendPromptAgain => 'Send again';

  @override
  String get chatUiPromptNotAnswered => 'Not answered';

  @override
  String get chatWatchEndedTitle => 'This conversation has ended';

  @override
  String get chatWatchEndedBody =>
      'It ended before the worker wrote anything here.';

  @override
  String get chatWatchBackToTask => 'Back to the task';

  @override
  String get chatWatchBackToWorker => 'Back to the worker';

  @override
  String get migrationTitle => 'Move from Termux';

  @override
  String get migrationChecking => 'Checking Termux and phone storage…';

  @override
  String get migrationReviewIntro =>
      'Your projects are copied into OpenCode inside this app. Nothing in Termux is changed or removed.';

  @override
  String get migrationGroupMoves => 'Copied and ready to use';

  @override
  String get migrationGroupExports => 'Saved privately, not turned on';

  @override
  String get migrationGroupNotMoved => 'Not moved';

  @override
  String get migrationItemProjects => 'Projects';

  @override
  String get migrationItemConfig => 'MCP and agent settings';

  @override
  String get migrationItemSessions => 'Conversation history (backup copy)';

  @override
  String get migrationItemGitConfig => 'Git settings';

  @override
  String get migrationItemShellFiles => 'Shell settings';

  @override
  String get migrationItemAiTeam => 'AI Team';

  @override
  String get migrationItemProjectsWhat =>
      'Into a new folder on the in-app server. Nothing there is overwritten.';

  @override
  String get migrationItemConfigWhat =>
      'To review before using: commands and paths may only work in Termux.';

  @override
  String get migrationItemSessionsWhat =>
      'May contain your sign-ins, and the app doesn\'t open it. Termux keeps your usable history.';

  @override
  String get migrationItemGitConfigWhat =>
      'Your Git name, email and options, to review.';

  @override
  String get migrationItemShellFilesWhat =>
      'Keeps .bashrc, .zshrc and your other shell start files; they never run.';

  @override
  String get migrationItemAiTeamWhat =>
      'The team\'s saved state. Setup installs its tools again.';

  @override
  String migrationItemSize(String size, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$size · $_temp0';
  }

  @override
  String get migrationSizeUnknown => 'Size unknown';

  @override
  String get migrationExportsNote =>
      'Private copies stay on this phone inside the in-app Linux. Nothing in them runs or turns on by itself.';

  @override
  String get migrationNotMovedSignIn =>
      'Sign-ins to AI providers: sign in again after the move';

  @override
  String migrationNotMovedSignInNamed(String names) {
    return 'Sign-ins to $names: sign in again after the move';
  }

  @override
  String get migrationNotMovedKeys => 'SSH keys and saved Git passwords';

  @override
  String get migrationNotMovedTools =>
      'Installed tools and caches: setup installs them again';

  @override
  String get migrationTermuxKept =>
      'Termux stays as it is, and its server keeps working until you remove it';

  @override
  String get migrationKeepOpen =>
      'Keep the app open while copying. Android stops it when you leave the app, and it picks up from here when you resume.';

  @override
  String get migrationStart => 'Copy to the in-app server';

  @override
  String get migrationChooseOne => 'Choose at least one item to copy.';

  @override
  String get migrationPacking => 'Preparing your files in Termux…';

  @override
  String get migrationCopying => 'Copying files to this app…';

  @override
  String get migrationUnpacking => 'Importing your files…';

  @override
  String get migrationVerifying => 'Checking the copied files…';

  @override
  String get migrationSwitching => 'Connecting to the in-app server…';

  @override
  String get migrationStepConnect => 'Connect to the in-app server';

  @override
  String get migrationStop => 'Stop copying';

  @override
  String get migrationStopTitle => 'Stop copying?';

  @override
  String get migrationStopBody => 'You can resume later from This phone.';

  @override
  String get migrationStopKept => 'What was copied so far is kept';

  @override
  String get migrationKeepGoing => 'Keep copying';

  @override
  String get migrationCancelled => 'Copy stopped. You can resume later.';

  @override
  String get migrationCancelledBody =>
      'What was copied is kept, and Termux isn\'t changed.';

  @override
  String get migrationStoppedLeaving =>
      'It stopped because the app left the screen: Android doesn\'t let it run in the background. What was copied is kept.';

  @override
  String get migrationResume => 'Resume copying';

  @override
  String get migrationNeedsSpace => 'More free space is needed before copying.';

  @override
  String migrationNeedsSpaceBody(String needed, String free) {
    return 'Needs about $needed, and $free is free. Free up space on this phone, or copy fewer items.';
  }

  @override
  String migrationNeedsSpaceBodyUnknown(String needed) {
    return 'Needs about $needed, and the free space couldn\'t be read. Free up space on this phone, or copy fewer items.';
  }

  @override
  String get migrationChooseFewer => 'Choose fewer items';

  @override
  String get migrationNeedsBuiltin => 'Set up the in-app server first.';

  @override
  String migrationNeedsBuiltinBody(String runtime) {
    return 'Your projects move into OpenCode inside this app, so it needs setting up. Setup installs Linux and $runtime, then this continues here.';
  }

  @override
  String get migrationSetUpBuiltin => 'Set up the in-app server';

  @override
  String get migrationSetupFailed =>
      'Setup couldn\'t start. Try again, or set it up from This phone.';

  @override
  String get migrationTermuxNotAnswering => 'Termux isn\'t answering';

  @override
  String get migrationTermuxUnavailable => 'Open Termux, then try again.';

  @override
  String get migrationOpenTermux => 'Open Termux';

  @override
  String get migrationFailedTitle => 'The move stopped';

  @override
  String get migrationSourceChanged =>
      'Files changed during copying. Try again when Termux is idle.';

  @override
  String get migrationSourceBusy =>
      'Termux is finishing the previous step. Try again shortly.';

  @override
  String get migrationUnsupportedFiles =>
      'This item contains files that cannot be copied safely.';

  @override
  String get migrationUnsupportedFilesFix =>
      'Links, sockets and Git worktrees can\'t be copied. Remove them in Termux and try again, or copy that project by hand.';

  @override
  String get migrationTooLarge =>
      'This item exceeds the migration size or file limit.';

  @override
  String get migrationTooLargeFix =>
      'Each item can hold up to 512 MB and 20,000 files. Delete build folders such as node_modules in Termux, then try again.';

  @override
  String get migrationVerificationFailed =>
      'The copy could not be verified. Your Termux files are unchanged.';

  @override
  String get migrationDestinationChanged =>
      'Imported files changed. They will not be overwritten.';

  @override
  String get migrationDestinationChangedFix =>
      'The files on the in-app server stay as you left them.';

  @override
  String get migrationStorageFailed =>
      'The copy could not be saved. Check phone storage and try again.';

  @override
  String get migrationTimedOut =>
      'This step took too long. Keep the app open and resume.';

  @override
  String get migrationSelectionChanged =>
      'Use the saved migration selection to resume.';

  @override
  String get migrationConnectionFailed =>
      'Files are copied, but the in-app server could not connect.';

  @override
  String get migrationFailureCode => 'Reason';

  @override
  String get migrationFailureItem => 'Item';

  @override
  String get migrationOpenThisPhone => 'Open This phone';

  @override
  String get migrationDoneTitle => 'Moved from Termux';

  @override
  String get migrationDone =>
      'Files copied. Your Termux server is still available.';

  @override
  String get migrationSignInAgain => 'Sign in to your AI providers again';

  @override
  String migrationSignInAgainNamed(String names) {
    return '$names appear in your Termux settings. Sign in here to use them.';
  }

  @override
  String get migrationSignInAgainAny =>
      'Sign-ins never move from Termux. Until you sign in here, replies use OpenCode\'s free model, which is slower.';

  @override
  String get migrationProjectsWhere => 'Your projects';

  @override
  String migrationProjectsWhereBody(String folder) {
    return 'In the folder $folder on the in-app server';
  }

  @override
  String get migrationExportsWhere => 'Private copies';

  @override
  String migrationExportsWhereBody(String items) {
    return '$items: saved inside the in-app Linux, not turned on';
  }

  @override
  String get migrationRemoveTermux =>
      'Remove the Termux server when you\'re ready';

  @override
  String get migrationRemoveTermuxBody =>
      'Nothing is removed for you. Until then it keeps working, and you can switch back to it on Servers.';

  @override
  String get migrationOpenBuiltin => 'Open the in-app server';

  @override
  String get migrationDetailProjects => 'Projects folder';

  @override
  String get migrationDetailExports => 'Private copies folder';

  @override
  String get migrationUnfinishedTitle => 'The move didn\'t finish';

  @override
  String get migrationUnfinishedBody =>
      'Resume to carry on where it stopped. What was already copied is kept, and Termux isn\'t changed.';

  @override
  String get migrationUnavailableTitle => 'The move can\'t start';

  @override
  String get migrationUnavailableBody =>
      'The app couldn\'t prepare its private storage for the copy. Try again, and if it keeps happening, restart the app.';

  @override
  String get migrationRowBody =>
      'Copy your projects into the in-app server. Termux stays as it is.';

  @override
  String get migrationRowResume => 'Resume moving to the in-app server';

  @override
  String get migrationRowResumeBody =>
      'Stopped before it finished. What was copied is kept.';

  @override
  String get migrationRowRunning => 'Moving to the in-app server';

  @override
  String get migrationRowDoneBody =>
      'Remove the Termux server when you\'re ready.';

  @override
  String get migrationOffer =>
      'Move your Termux projects into this app? Termux stays as it is.';

  @override
  String get migrationOfferAction => 'Review what moves';

  @override
  String get integrationsSignInUncertainNext =>
      'Check the server before you start again';

  @override
  String teamHomeLastKnownTasks(String time) {
    return 'Tasks as of $time';
  }

  @override
  String teamHomeLastKnownAgents(String time) {
    return 'Agents as of $time';
  }

  @override
  String get teamHomeStoppedStartFirst =>
      'Start the team again to give it a task or open one.';

  @override
  String get inAppServerStartExitedBody =>
      'OpenCode closed by itself while it was starting. Open setup to see its log, or start it again.';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'OpenCode did not answer within $seconds seconds. The phone may be busy or short on memory; close other apps, then start it again.';
  }

  @override
  String get inAppServerStartInterruptedBody =>
      'The start stopped because the app left the screen. Start it again to continue.';

  @override
  String get inAppServerStartPasswordBody =>
      'The app could not set up OpenCode\'s sign-in on this phone. Start it again; if this repeats, open setup.';

  @override
  String get inAppServerStartRefusedBody =>
      'The phone did not let the app start OpenCode just now. Start it again; if this repeats, restart the phone.';

  @override
  String get integrationsConnectWithKey => 'Add an API key';

  @override
  String get integrationsConnectOnServer => 'Set up on the server';

  @override
  String integrationsProviderDetails(String name) {
    return '$name details';
  }

  @override
  String get integrationsEnvironmentVariable => 'Server environment variable';

  @override
  String integrationsEnvironmentNote(String name) {
    return 'To connect $name without the app, set this where the server runs, then restart the server.';
  }

  @override
  String get termuxProblemAccessHeard =>
      'OpenCode is running in Termux, but this app can\'t reach Termux yet. Allow access and it connects.';

  @override
  String get termuxProblemAccessNeeded =>
      'This app can\'t reach Termux yet. Allow access so it can find OpenCode there and connect.';

  @override
  String get termuxProblemAccessBlocked =>
      'Android blocked Termux access for this app. In this app\'s permissions, turn on “Run commands in Termux environment”.';

  @override
  String get termuxProblemOtherAppsOff =>
      'Termux doesn\'t take commands from other apps yet. One line in Termux allows it.';

  @override
  String get termuxProblemAsleep =>
      'Termux didn\'t answer. Android may have put it to sleep. Open Termux to wake it.';

  @override
  String termuxProblemNotAnswering(String runtime) {
    return '$runtime is set up in Termux but isn\'t answering. A restart usually brings it back.';
  }

  @override
  String get termuxProblemNotInstalled =>
      'Termux isn\'t on this phone. Install it again, or set up the in-app server instead.';

  @override
  String get termuxProblemOutdated =>
      'This Termux is too old for the app to use. Install the current Termux from F-Droid.';

  @override
  String termuxProblemUnknown(String runtime) {
    return 'This phone couldn\'t check on $runtime in Termux. Try again in a moment.';
  }

  @override
  String get termuxFixAllowAccess => 'Allow access to Termux';

  @override
  String get termuxFixOpenPermissions => 'Open this app\'s permissions';

  @override
  String get termuxFixAllowOtherApps => 'Allow other apps in Termux';

  @override
  String get termuxFixOpenTermux => 'Open Termux';

  @override
  String termuxFixRestart(String runtime) {
    return 'Restart $runtime in Termux';
  }

  @override
  String get termuxFixGetTermux => 'Get Termux';

  @override
  String get termuxFixGetCurrentTermux => 'Get the current Termux';

  @override
  String get termuxOtherAppsTitle => 'Allow other apps';

  @override
  String get termuxOtherAppsBody =>
      'Paste this line in Termux and press Enter, then come back here. Open Termux copies it for you.';

  @override
  String get termuxLeadRunning => 'OpenCode is running in Termux';

  @override
  String get termuxLeadAccessLine =>
      'This app can\'t reach Termux yet. Allow access and it connects to your conversations.';

  @override
  String get termuxLeadSetUp => 'OpenCode is set up in Termux';

  @override
  String get termuxLeadTermuxOnly => 'Termux is on this phone';

  @override
  String get termuxLeadRunningBody => 'Connect to pick up your conversations.';

  @override
  String get termuxLeadStoppedBody =>
      'It\'s stopped. Start it to pick up your conversations.';

  @override
  String get termuxLeadConnect => 'Connect to the server in Termux';

  @override
  String get termuxLeadStart => 'Start the server in Termux';

  @override
  String get termuxInAppInstead => 'Set up the in-app server instead';

  @override
  String get termuxInAppInsteadDetail =>
      'A fresh start that runs inside this app. No Termux needed.';

  @override
  String get termuxInAppInsteadBlocked =>
      'A fresh start inside this app. To bring your projects from Termux, fix Termux access first.';

  @override
  String get aboutBundledComponents => 'Bundled components';

  @override
  String get aboutBundledComponentsDetail =>
      'Icons, fonts and other parts shipped inside this app';

  @override
  String get manageSpaceTitle => 'Clear this app\'s storage';

  @override
  String get manageSpaceMeasuring => 'Measuring what is stored…';

  @override
  String get manageSpaceIntro =>
      'Clearing deletes everything OpenCode Mobile keeps on this phone, and it cannot be undone. Export your projects first if you want to keep them.';

  @override
  String get manageSpaceExportFirst => 'Export projects first';

  @override
  String get manageSpaceClearCache => 'Clear the app\'s cache only';

  @override
  String manageSpaceClearCacheDetail(String size) {
    return 'Frees $size. Projects, servers and settings stay.';
  }

  @override
  String get manageSpaceClearCacheKeeps =>
      'Projects, servers and settings stay.';

  @override
  String manageSpaceCacheCleared(String size) {
    return 'Cache cleared. $size freed.';
  }

  @override
  String get manageSpaceCacheFailed => 'Could not clear the cache. Try again.';

  @override
  String get manageSpaceTryAgain => 'Try again';

  @override
  String get manageSpaceDeleteAll => 'Delete everything';

  @override
  String get manageSpaceDeleteAllDetail =>
      'Deletes all of the list below and closes the app';

  @override
  String get manageSpaceDeleteTitle => 'Delete everything?';

  @override
  String get manageSpaceDeleteBody =>
      'OpenCode Mobile then starts again as if it were new. This cannot be undone.';

  @override
  String get manageSpaceLostServer => 'The in-app server and its conversations';

  @override
  String manageSpaceLostProjects(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count projects ($size)',
      one: '1 project ($size)',
    );
    return '$_temp0';
  }

  @override
  String manageSpaceLostSettings(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count saved servers and all settings',
      one: '1 saved server and all settings',
      zero: 'All settings',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptAll =>
      'Termux, your computers and anything pushed to git stay';

  @override
  String get manageSpaceWaitForExport => 'Wait for the export to finish';

  @override
  String get manageSpaceDeletedLabel => 'Clearing deletes';

  @override
  String get manageSpaceServer => 'The in-app server';

  @override
  String get manageSpaceServerDetail =>
      'Ubuntu, OpenCode, its sign-ins and its conversations';

  @override
  String get manageSpaceSettings => 'Saved servers and settings';

  @override
  String manageSpaceSavedServers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count saved servers',
      one: '1 saved server',
      zero: 'No saved servers',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptLabel => 'Stays';

  @override
  String get manageSpaceKeptTermux => 'Termux and the projects in it';

  @override
  String get manageSpaceKeptComputers => 'Your computers and their servers';

  @override
  String get manageSpaceKeptGit => 'Anything you pushed to git';

  @override
  String projectExportDetail(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count projects, $size, as one zip file where you choose',
      one: '1 project, $size, as one zip file where you choose',
    );
    return '$_temp0';
  }

  @override
  String get projectExportNoProjects => 'No projects on the in-app server yet';

  @override
  String get projectExportSave => 'Save as a zip file';

  @override
  String get projectExportRunning => 'Exporting projects';

  @override
  String get projectExportPreparing => 'Listing files…';

  @override
  String projectExportProgress(String done, String total) {
    return '$done of $total';
  }

  @override
  String get projectExportStop => 'Stop the export';

  @override
  String get projectExportStopDetail => 'The half-written file is deleted';

  @override
  String get projectExportPrivate => 'Include sign-ins and conversations';

  @override
  String get projectExportPrivateDetail =>
      'Private: anyone with the file can use your accounts';

  @override
  String projectExportDone(String size, int files) {
    return 'Projects exported: $size in $files files.';
  }

  @override
  String get projectExportDonePrivate =>
      'This file holds sign-ins. Keep it private.';

  @override
  String projectExportDoneLeftOut(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files with sign-ins or keys were left out.',
      one: '1 file with sign-ins or keys was left out.',
    );
    return '$_temp0';
  }

  @override
  String get projectExportStopped => 'Export stopped. Nothing was saved.';

  @override
  String get projectExportFailedDestination =>
      'Could not write to the place you chose. Try again, or pick another place.';

  @override
  String get projectExportFailedSpace =>
      'The place you chose is full. Free some space there or pick another place.';

  @override
  String get projectExportFailedSource =>
      'A project file could not be read. Try again.';

  @override
  String get projectExportFailed =>
      'The export stopped before it finished. Try again.';

  @override
  String get projectExportProjectsLabel => 'Projects';

  @override
  String get thisPhoneExportProjects => 'Export projects';

  @override
  String get thisPhoneExportProjectsDetail =>
      'Save them as a zip file, to keep or move';

  @override
  String get demoNoCommands =>
      'The demo has no commands — send the sample prompt to see a change reviewed.';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Signed in to $providers, but this server could not load those sign-ins even after a reload, so their models cannot answer. Browser sign-ins for some providers, such as Anthropic and Google, do not load on this server. Add an API key under Providers instead, or pick another model.',
      one:
          'Signed in to $providers, but this server could not load that sign-in even after a reload, so its models cannot answer. Browser sign-ins for some providers, such as Anthropic and Google, do not load on this server. Add an API key under Providers instead, or pick another model.',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'The reload waits for $count running replies to finish, because reloading would stop them.',
      one:
          'The reload waits for 1 running reply to finish, because reloading would stop it.',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      'Using OpenCode\'s free model — it\'s slower. Add an API key from your provider to use your own.';

  @override
  String get freeModelSignIn => 'Add an API key';

  @override
  String get replySpeedTitle => 'Reply speed';

  @override
  String replySpeedLast(String first, String total) {
    return 'Last reply: first words after $first, finished after $total';
  }

  @override
  String replySpeedNoWords(String total) {
    return 'Last reply: ended after $total before any words came';
  }

  @override
  String replySpeedSeconds(String seconds) {
    return '$seconds s';
  }

  @override
  String get perfDetailLinuxMode => 'Linux speed mode';

  @override
  String get perfLinuxModeFast => 'Fast: proot with seccomp';

  @override
  String get perfLinuxModeSlow => 'Slow: proot without seccomp';

  @override
  String get perfLinuxModeUnknown => 'Not known while OpenCode is stopped';

  @override
  String get perfDetailAwake => 'Phone kept awake';

  @override
  String get perfAwakeNow => 'Now, while a reply runs';

  @override
  String get perfAwakeWhenWorking => 'Only while a reply runs';

  @override
  String get perfDetailFirstWords => 'First words, last reply';

  @override
  String perfFirstWordsSplit(String app, String server) {
    return '$app in the app · $server on the server';
  }

  @override
  String get perfDetailModel => 'Model, last reply';

  @override
  String get manageSpaceIntroNothingToExport =>
      'Clearing deletes everything OpenCode Mobile keeps on this phone, and it cannot be undone.';

  @override
  String integrationsKeyOnlyHelper(String name) {
    return '$name does not allow browser sign-in from other apps, so use an API key. It is billed separately from any subscription. The key is stored on this server and never shown again.';
  }

  @override
  String integrationsGetKey(String name) {
    return 'Get a key from $name';
  }

  @override
  String integrationsKeySavedReady(String name) {
    return '$name key saved. Pick one of its models in the model picker.';
  }

  @override
  String integrationsKeySavedWaiting(String name) {
    return '$name key saved. It loads once the running replies finish.';
  }

  @override
  String integrationsKeySavedUnusable(String name) {
    return '$name key saved, but this server could not load it after a refresh. Check the key, or try Reload providers in the model picker.';
  }

  @override
  String integrationsKeySavedPending(String name) {
    return '$name key saved. The server has not loaded it yet.';
  }

  @override
  String migrationReviewSpace(String needed, String free) {
    return 'Needs about $needed · $free free';
  }

  @override
  String migrationReviewSpaceUnknown(String needed) {
    return 'Needs about $needed · Free space unknown';
  }

  @override
  String get migrationReviewSpaceShort =>
      'Not enough free space for this. Choose fewer items, or free up space on this phone.';

  @override
  String get migrationDiscard => 'Discard saved copy';

  @override
  String get migrationDiscardTitle => 'Discard this saved copy?';

  @override
  String get migrationDiscardBody =>
      'Temporary copy files will be removed. Files already imported and everything in Termux will stay.';

  @override
  String get migrationDiscardFailed =>
      'The saved copy couldn\'t be removed. Try again in a moment.';

  @override
  String get migrationStopping => 'Stopping…';

  @override
  String get pickerConnectProvider => 'Connect a provider';

  @override
  String get pickerConnectProviderHint =>
      'Add an API key or sign in to use its models';

  @override
  String pickerAddKeyFor(String name) {
    return 'Add an API key for $name';
  }

  @override
  String get pickerAddKeyNotConnectedHint =>
      'Its models are not in this list yet';

  @override
  String pickerSignInTo(String name) {
    return 'Sign in to $name';
  }

  @override
  String get pickerSignInHint => 'Opens the sign-in choices for this server';

  @override
  String get pickerFreeOnlyNote =>
      'Only OpenCode\'s free model is available — it\'s slower.';

  @override
  String pickerProviderReady(String name) {
    return '$name is ready. Its models are in the list.';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return '$name is saved, but the server has not loaded it yet.';
  }

  @override
  String get integrationsSignedInUnusable =>
      'Signed in, but this server can\'t use it';

  @override
  String get chatUiCompactConfirmTitle => 'Compact this conversation?';

  @override
  String get chatUiCompactConfirmBody =>
      'Compact replaces earlier messages with a short summary to save space. It can\'t be undone.';

  @override
  String get chatUiCompactConfirmAction => 'Compact conversation';

  @override
  String get kitTurnReconnecting =>
      'Connection lost. Reconnecting to get the rest of this reply.';

  @override
  String get kitTurnLiveSending => 'Sending';

  @override
  String get kitTurnLiveWaitingForServer => 'Waiting for the server';

  @override
  String get kitTurnLiveServerQuiet => 'The server has not answered yet';

  @override
  String get kitTurnLiveThinking => 'Thinking';

  @override
  String get kitTurnLiveFirstWordSlow => 'Waiting for the model\'s first word';

  @override
  String get kitTurnLiveFirstWordSlowTeam =>
      'AI Team is also working on this phone, so replies may be slower';

  @override
  String get kitTurnLiveWriting => 'Writing';

  @override
  String get kitTurnLiveWorking => 'Working';

  @override
  String get kitTurnLiveWaitingForYou => 'Waiting for you';

  @override
  String get kitTurnLiveStop => 'Stop reply';

  @override
  String get kitTurnLiveStopping => 'Stopping…';

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
    return '$seconds s';
  }

  @override
  String kitTurnLiveMinutes(int minutes, int seconds) {
    return '$minutes min $seconds s';
  }

  @override
  String get chatNoReplyCameBack => 'No reply came back';

  @override
  String get composerFieldLabel => 'رسالة إلى الوكيل';

  @override
  String get e7WorkspaceYesterday => 'أمس';

  @override
  String get teamStripTitle => 'فريق الذكاء الاصطناعي';

  @override
  String get teamStripIdle => 'فريق الذكاء الاصطناعي · لا شيء قيد التشغيل';

  @override
  String teamStripWorking(int count) {
    return '$count قيد العمل';
  }

  @override
  String teamStripNeedsYou(int count) {
    return '$count يحتاج إليك';
  }

  @override
  String teamProgressOne(String title) {
    return 'فريق الذكاء الاصطناعي: $title';
  }

  @override
  String teamProgressStep(String title, int done, int total) {
    return 'فريق الذكاء الاصطناعي: $title · الخطوة $done من $total';
  }

  @override
  String teamProgressMany(int count) {
    return 'فريق الذكاء الاصطناعي: $count مهام قيد العمل';
  }

  @override
  String get teamSettingsTitle => 'إعدادات الفريق';

  @override
  String get teamSettingsOpenTooltip => 'إعدادات الفريق';

  @override
  String get teamSettingsTurnOff => 'إيقاف فريق الذكاء الاصطناعي';

  @override
  String teamChatWorkerNumbered(String role, int n) {
    return '$role $n';
  }

  @override
  String get teamRoleNameGeneral => 'General';

  @override
  String get teamRoleNameProduct => 'Product';

  @override
  String get teamRoleNameFrontend => 'Frontend';

  @override
  String get teamRoleNameBackend => 'Backend';

  @override
  String get teamRoleNameTester => 'Tester';

  @override
  String get teamRolePurposeGeneral => 'Any task, done the plain way';

  @override
  String get teamRolePurposeProduct =>
      'Turns an idea into clear requirements and a plan';

  @override
  String get teamRolePurposeFrontend =>
      'Screens, layout and how it feels to use';

  @override
  String get teamRolePurposeBackend =>
      'Servers, data and the code behind the screens';

  @override
  String get teamRolePurposeTester =>
      'Finds what breaks and shows that it works';

  @override
  String get teamRolesTitle => 'Agents';

  @override
  String get teamRolesNew => 'New role';

  @override
  String get teamRolesEmpty => 'No roles yet';

  @override
  String teamRoleWorkingOn(String task, String age) {
    return 'Working on “$task” · $age';
  }

  @override
  String teamRoleUses(String model) {
    return 'Uses $model';
  }

  @override
  String get teamRoleUsesTeamModel => 'Uses the team\'s model';

  @override
  String get teamRoleUsesComputerModel => 'Uses the computer\'s model';

  @override
  String teamRoleTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'No tasks yet',
    );
    return '$_temp0';
  }

  @override
  String teamSettingsAgentsRow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Agents · $count roles',
      one: 'Agents · 1 role',
    );
    return '$_temp0';
  }

  @override
  String get teamSettingsAgentsHint =>
      'Who does the work, and how each one works';

  @override
  String get teamRoleNewTitle => 'New role';

  @override
  String get teamRoleFieldName => 'Name';

  @override
  String get teamRoleFieldPurpose => 'What it\'s for';

  @override
  String get teamRoleFieldPurposeHint =>
      'One line, for example: writes the guides';

  @override
  String get teamRoleFieldInstructions => 'Instructions';

  @override
  String get teamRoleFieldInstructionsHint =>
      'How this role should work, in your own words';

  @override
  String get teamRoleNameRequired => 'Give the role a name';

  @override
  String get teamRoleModelRow => 'Model';

  @override
  String get teamRoleTeamModel => 'Team\'s model';

  @override
  String get teamRoleComputerModel => 'The computer\'s model';

  @override
  String get teamRoleModelSheetDefault => 'Team\'s model';

  @override
  String get teamRoleModelSheetDefaultHint =>
      'Uses whatever model the whole team uses';

  @override
  String teamRoleWorkingNow(String task) {
    return 'Working on “$task”';
  }

  @override
  String get teamRoleOpenConversation => 'Open its conversation';

  @override
  String get teamRoleRecentTasks => 'Recent tasks';

  @override
  String teamRoleNoTasks(String role) {
    return 'Nothing given to $role yet';
  }

  @override
  String teamRoleGiveTask(String role) {
    return 'Give $role a task';
  }

  @override
  String get teamRoleSave => 'Save';

  @override
  String get teamRoleCreate => 'Create role';

  @override
  String teamRoleReset(String role) {
    return 'Reset $role';
  }

  @override
  String teamRoleResetTitle(String role) {
    return 'Reset $role?';
  }

  @override
  String get teamRoleResetBody =>
      'Its name, purpose, instructions and model go back to how they shipped.';

  @override
  String teamRoleDelete(String role) {
    return 'Delete $role';
  }

  @override
  String teamRoleDeleteTitle(String role) {
    return 'Delete $role?';
  }

  @override
  String teamRoleDeleteBody(String role) {
    return '$role is removed from this team. Tasks it already did keep its name.';
  }

  @override
  String get teamRoleWorkerName => 'Worker name';

  @override
  String get teamRoleExamplesLabel => 'Start from an example';

  @override
  String get teamRoleExampleDocs => 'Docs writer';

  @override
  String get teamRoleExampleDocsPurpose => 'Writes and updates the guides';

  @override
  String get teamRoleExampleDocsInstructions =>
      'You write and update documentation. Keep it short, accurate and in plain words. Check every command and path you mention before writing it down.';

  @override
  String get teamRoleExampleSecurity => 'Security reviewer';

  @override
  String get teamRoleExampleSecurityPurpose =>
      'Looks for ways the code could be abused';

  @override
  String get teamRoleExampleSecurityInstructions =>
      'You review code for security problems: secrets in code or logs, unchecked input, unsafe links, and missing permission checks. Report what you find with the file and line, and fix only what the task asks for.';

  @override
  String get teamRoleExampleDesigner => 'Designer';

  @override
  String get teamRoleExampleDesignerPurpose =>
      'Makes it clear, consistent and pleasant';

  @override
  String get teamRoleExampleDesignerInstructions =>
      'You improve how the product looks and reads. Reuse the parts and words already in the app, keep one design language, and check small screens and large text.';

  @override
  String get teamRoleStarterInstructions =>
      'You are the ___ on this team.\nFocus on: ___\nAlways: ___\nNever: ___';

  @override
  String get teamStartRunWho => 'Who';

  @override
  String get teamStartRunWhoSuggested => 'Suggested from your words';

  @override
  String get teamStartRunWhoChange => 'Change';

  @override
  String get teamStartRunWhoTitle => 'Who should take this?';

  @override
  String teamChatLeadStartingRole(String role, String title) {
    return '$role started on “$title”';
  }

  @override
  String teamChatLeadClaimedRole(String role, String title) {
    return '$role took “$title”';
  }

  @override
  String teamChatLeadStartingItRole(String role) {
    return '$role started';
  }

  @override
  String teamChatLeadClaimedItRole(String role) {
    return '$role took the task';
  }

  @override
  String get teamRolesSearchAliases =>
      'roles personas agents team frontend backend tester product designer instructions';

  @override
  String get teamUiStatePhoneStoppedTitle => 'AI Team stopped';

  @override
  String get teamUiStatePhoneStoppedBody =>
      'AI Team on this phone isn’t running. Start it to continue your tasks.';

  @override
  String get teamStartStepService => 'تشغيل خدمة الفريق';

  @override
  String get teamStartStepAnswering => 'انتظار رد الفريق';

  @override
  String get teamStartStepStore => 'فتح مخزن المهام';

  @override
  String get teamStartStepAgents => 'تجهيز الوكلاء';

  @override
  String get teamStartSlow => 'يستغرق وقتًا أطول من المعتاد، الهاتف مشغول';

  @override
  String get teamStartAgain => 'ابدأ من جديد';

  @override
  String get teamUiHostPhraseStarting => 'قيد البدء';

  @override
  String get teamUiStartOnPhone => 'Start AI Team on this phone';

  @override
  String get chatCollapseAllSteps => 'طي كل الخطوات';

  @override
  String chatWatchTeamInstructions(int count, String time) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'تعليمات من الفريق · $countString كلمة · $time',
      one: 'تعليمات من الفريق · كلمة واحدة · $time',
    );
    return '$_temp0';
  }

  @override
  String get chatWatchEmptyStartingTitle => 'جارٍ البدء';

  @override
  String get chatWatchEmptyStartingBody => 'تظهر خطواته هنا أثناء عمله.';

  @override
  String chatWatchEmptyWorkingOn(String task) {
    return 'يعمل على «$task». تظهر خطواته هنا أثناء عمله.';
  }

  @override
  String chatWatchEmptyReviewing(String task) {
    return 'يراجع تغييرات «$task». تظهر خطواته هنا أثناء عمله.';
  }

  @override
  String get chatWatchEmptyIdleTitle => 'في الانتظار';

  @override
  String get chatWatchEmptyIdleBody =>
      'ينتظر مهمته التالية. راسله بالأسفل ليطلب شيئًا.';

  @override
  String get teamUiAgentLabelSessionTitle => 'عنوان الجلسة';

  @override
  String get kitComposerPillNoAnswer => 'No answer yet';

  @override
  String get kitComposerRailRetry => 'Try again';

  @override
  String get teamProjectHome => 'AI Team';

  @override
  String get teamProjectDemo => 'Demo';

  @override
  String get teamProjectNew => 'New project';

  @override
  String get teamProjectQuick => 'Give a quick task';

  @override
  String get teamProjectSettings => 'Project settings';

  @override
  String get teamProjectRoles => 'Roles and agents';

  @override
  String get teamProjectEmpty => 'Give your team a goal to start a project.';

  @override
  String get teamProjectSelect => 'Select a project';

  @override
  String get teamProjectSelectTask =>
      'Select a task to follow its conversation.';

  @override
  String get teamProjectLoad => 'Loading projects';

  @override
  String get teamProjectRetry => 'Try again';

  @override
  String get teamProjectError =>
      'The project could not be updated. Your saved work is still available.';

  @override
  String get teamProjectSpec => 'Open spec';

  @override
  String get teamProjectPlan => 'Review plan';

  @override
  String get teamProjectBoard => 'Board';

  @override
  String get teamProjectGraph => 'Dependencies';

  @override
  String get teamProjectTimeline => 'Timeline';

  @override
  String get teamProjectServers => 'Servers';

  @override
  String get teamProjectMilestones => 'Milestones';

  @override
  String get teamProjectLanes => 'Lanes';

  @override
  String get teamProjectMerge => 'Merge queue';

  @override
  String get teamProjectCost => 'Cost';

  @override
  String get teamProjectDecisions => 'Recent decisions';

  @override
  String get teamProjectPause => 'Pause project';

  @override
  String get teamProjectResume => 'Resume project';

  @override
  String get teamProjectStopConfirmTitle => 'Stop this project?';

  @override
  String get teamProjectStop => 'Stop project';

  @override
  String get teamProjectStopBody =>
      'Running tasks will stop. Their work and project history will be kept.';

  @override
  String get teamProjectAdvance => 'Advance demo';

  @override
  String get teamProjectDigest => 'Since you were away';

  @override
  String get teamProjectDigestRead => 'Mark as read';

  @override
  String get teamProjectAnswer => 'Answer';

  @override
  String get teamProjectAnswerLabel => 'Your answer';

  @override
  String get teamProjectAll => 'Everything';

  @override
  String get teamProjectMerges => 'Merges';

  @override
  String get teamProjectProblems => 'Problems';

  @override
  String get teamProjectMilestoneFilter => 'Milestone';

  @override
  String get teamProjectRepoFilter => 'Repo';

  @override
  String get teamProjectServerFilter => 'Server';

  @override
  String get teamProjectBacklog => 'Backlog';

  @override
  String get teamProjectReady => 'Ready';

  @override
  String get teamProjectWorking => 'Working';

  @override
  String get teamProjectReview => 'Review';

  @override
  String get teamProjectDone => 'Done';

  @override
  String get teamProjectNoTasks => 'No tasks in this view.';

  @override
  String get teamProjectMove => 'Move task';

  @override
  String get teamProjectMoveTo => 'Move to server';

  @override
  String get teamProjectHandoff => 'Hand-off note';

  @override
  String get teamProjectPaused => 'Paused';

  @override
  String get teamProjectStopped => 'Stopped';

  @override
  String get teamProjectFailed => 'Stopped unexpectedly';

  @override
  String get teamProjectStalled => 'No recent progress';

  @override
  String get teamProjectPlanning => 'Shaping the spec';

  @override
  String get teamProjectPlanWaiting => 'Plan ready to review';

  @override
  String get teamProjectNeedsYou => 'Needs your decision';

  @override
  String get teamProjectWaiting => 'Waiting for dependencies';

  @override
  String get teamProjectOnline => 'Reachable';

  @override
  String get teamProjectOffline => 'Not reachable · last known tasks';

  @override
  String get teamProjectNoLimit => 'No limit';

  @override
  String get teamProjectUnknown => 'Not reported';

  @override
  String get teamProjectAcceptMilestoneConfirmTitle => 'Accept this milestone?';

  @override
  String get teamProjectAccept => 'Accept milestone';

  @override
  String get teamProjectMergeConfirmTitle => 'Merge into dev?';

  @override
  String get teamProjectMergeNext => 'Merge checked work into dev';

  @override
  String get teamProjectCostDemo =>
      'Demo figures are simulated; device memory, battery, heat and conversation speed have not been measured.';

  @override
  String teamProjectProgress(int done, int total, int working) {
    return '$done of $total tasks complete · $working working';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy of $total lanes busy';
  }

  @override
  String teamProjectSpend(
    String today,
    String daily,
    String spent,
    String total,
  ) {
    return 'Today: $today / $daily. Total: $spent / $total.';
  }

  @override
  String get teamProjectEditorNewProject => 'New project';

  @override
  String get teamProjectEditorQuickTask => 'Quick task';

  @override
  String get teamProjectEditorSpec => 'Living spec';

  @override
  String get teamProjectEditorPlan => 'Review plan';

  @override
  String get teamProjectEditorSettings => 'Project settings';

  @override
  String get teamProjectEditorRoles => 'Roles and agents';

  @override
  String get teamProjectEditorStartPlanning => 'Start planning';

  @override
  String get teamProjectEditorStartTask => 'Start task';

  @override
  String get teamProjectEditorApproveSpec => 'Approve spec';

  @override
  String get teamProjectEditorApprovePlan => 'Approve and start';

  @override
  String get teamProjectEditorSave => 'Save changes';

  @override
  String get teamProjectEditorSaveDraft => 'Save draft';

  @override
  String get teamProjectEditorName => 'Project name';

  @override
  String get teamProjectEditorGoal => 'Goal';

  @override
  String get teamProjectEditorRepos => 'Repos';

  @override
  String get teamProjectEditorRepoName => 'Repo name';

  @override
  String get teamProjectEditorRepoPath => 'Repo folder';

  @override
  String get teamProjectEditorServer => 'Server';

  @override
  String get teamProjectEditorRemove => 'Remove';

  @override
  String get teamProjectEditorAddRepo => 'Add repo';

  @override
  String get teamProjectEditorRole => 'Role';

  @override
  String get teamProjectEditorPlanFirst => 'Plan first';

  @override
  String get teamProjectEditorMode => 'Execution mode';

  @override
  String get teamProjectEditorSingle => 'Single lane';

  @override
  String get teamProjectEditorParallel => 'Parallel agents';

  @override
  String get teamProjectEditorMaxLanes => 'Maximum lanes';

  @override
  String teamProjectEditorCostMeasured(String host, String memory) {
    return 'على $host: نحو $memory ميغابايت من الذاكرة لكل مسار، مقيسة. البطارية وسرعة المحادثة لم تُقَس بعد.';
  }

  @override
  String teamProjectEditorCostNotMeasured(String host) {
    return 'لم تُقَس على $host بعد. محادثتك تبقى أولًا.';
  }

  @override
  String get teamProjectEditorCostNoHost =>
      'اختر مكان تشغيل العمل لترى كلفة المسار هناك.';

  @override
  String get teamProjectEditorThisPhone => 'هذا الهاتف';

  @override
  String get teamProjectEditorGoalRequired => 'أضف هدفًا.';

  @override
  String get teamProjectEditorRepoMissing => 'أضف مستودعًا واحدًا على الأقل.';

  @override
  String get teamProjectEditorRepoIncomplete =>
      'أكمل المستودع: اسم ومجلد ومكان التشغيل.';

  @override
  String get teamProjectEditorNoFallback => 'بلا نموذج احتياطي';

  @override
  String get teamProjectEditorNoFallbackHint =>
      'ينتظر العمل النموذج الأساسي بدل التبديل.';

  @override
  String get teamProjectEditorReadOnlyRole =>
      'للقراءة فقط: يقرأ هذا الوكيل المشروع ولا يغيّره.';

  @override
  String get teamProjectEditorReadOnlyShort => 'للقراءة فقط';

  @override
  String get teamProjectEditorCharging => 'Only while charging';

  @override
  String get teamProjectEditorReview => 'Review level';

  @override
  String get teamProjectEditorMilestonesRisk => 'Milestones and risky points';

  @override
  String get teamProjectEditorEveryStep => 'Every step';

  @override
  String get teamProjectEditorBudget => 'Budget';

  @override
  String get teamProjectEditorSetLimits => 'Set limits';

  @override
  String get teamProjectEditorNoLimit => 'No limit';

  @override
  String get teamProjectEditorDailyBudget => 'Per day (USD)';

  @override
  String get teamProjectEditorTotalBudget => 'Total (USD)';

  @override
  String get teamProjectEditorTaskTokens => 'Token limit per task (optional)';

  @override
  String get teamProjectEditorAutoFix => 'Fix findings automatically';

  @override
  String get teamProjectEditorMaxRounds => 'Maximum fix rounds';

  @override
  String get teamProjectEditorConstraints => 'Constraints';

  @override
  String get teamProjectEditorDecisions => 'Decisions';

  @override
  String get teamProjectEditorOutOfScope => 'Out of scope';

  @override
  String get teamProjectEditorMilestones => 'Milestones';

  @override
  String get teamProjectEditorMilestoneTitle => 'Milestone title';

  @override
  String get teamProjectEditorCriteria => 'Acceptance criteria (one per line)';

  @override
  String get teamProjectEditorMoveUp => 'Move up';

  @override
  String get teamProjectEditorMoveDown => 'Move down';

  @override
  String get teamProjectEditorAddMilestone => 'Add milestone';

  @override
  String get teamProjectEditorHistory => 'Version history';

  @override
  String get teamProjectEditorVersion => 'Version';

  @override
  String get teamProjectEditorPlanHelp =>
      'Review the tasks and their acceptance criteria. Changes here are included when you approve the plan.';

  @override
  String get teamProjectEditorRisky => 'نقطة مراجعة · محفوفة بالمخاطر';

  @override
  String get teamProjectEditorTaskTitle => 'Task title';

  @override
  String get teamProjectEditorRepo => 'Repo';

  @override
  String get teamProjectEditorDependencies => 'Depends on';

  @override
  String get teamProjectEditorRemoveTask => 'Remove task';

  @override
  String get teamProjectEditorRemoteModel => 'The computer\'s model';

  @override
  String get teamProjectEditorAddRole => 'Add role';

  @override
  String get teamProjectEditorRoleName => 'Role name';

  @override
  String get teamProjectEditorInstructions => 'Instructions';

  @override
  String get teamProjectEditorModel => 'Model';

  @override
  String get teamProjectEditorFallback => 'Fallback model';

  @override
  String get teamProjectEditorAllRoles => 'All roles';

  @override
  String get teamProjectEditorChooseMode =>
      'Choose Single lane or Parallel agents.';

  @override
  String get teamProjectEditorPositiveLanes =>
      'Enter a lane limit from 1 to 32.';

  @override
  String get teamProjectEditorChooseBudget =>
      'Set a budget or choose No limit.';

  @override
  String get teamProjectEditorPositiveBudget =>
      'Enter a limit per day and a total limit, each above zero.';

  @override
  String get teamProjectEditorSaveFailed =>
      'Changes could not be saved. Your edits are still here; try saving again.';

  @override
  String get teamProjectEditorRequired => 'Add a goal and at least one repo.';

  @override
  String get teamProjectEditorChooseRoleServer =>
      'Choose a role and a server for this task.';

  @override
  String get teamProjectEditorRepoRequired =>
      'Choose a server and enter the repo name and folder.';

  @override
  String get teamProjectEditorSpecRequired =>
      'Add a goal and at least one milestone with a title and acceptance criteria.';

  @override
  String get teamProjectEditorDraftFailed =>
      'The draft could not be kept on this device. Keep this screen open and try saving again.';

  @override
  String get teamProjectEditorChangedElsewhere =>
      'This project changed while you were editing. Close this sheet and review the latest project before approving changes.';

  @override
  String get teamProjectConversation => 'Task conversation';

  @override
  String get teamProjectTaskMissing => 'This task is no longer available';

  @override
  String get teamProjectRefreshTask => 'Refresh task';

  @override
  String get teamProjectTaskSaveFailed =>
      'The change was not saved. Refresh and try again; your message is still here.';

  @override
  String get teamProjectTaskMessage => 'Message the team…';

  @override
  String get teamProjectTaskInstructions => 'Instructions from the team';

  @override
  String get teamProjectTaskPlan => 'Plan';

  @override
  String get teamProjectTaskApprovePlan => 'Approve and start';

  @override
  String get teamProjectTaskReview => 'Review required';

  @override
  String get teamProjectTaskAccepted => 'Accepted';

  @override
  String get teamProjectTaskAcceptPhase => 'Accept phase';

  @override
  String get teamProjectTaskFindings => 'Verification findings';

  @override
  String get teamProjectTaskFix => 'Fix selected';

  @override
  String get teamProjectTaskRecheck => 'Re-check task';

  @override
  String get teamProjectTaskIgnore => 'Ignore selected finding';

  @override
  String get teamProjectTaskIgnoreReason =>
      'Why is this finding safe to ignore?';

  @override
  String get teamProjectTaskReasonRequired =>
      'Enter a reason to keep with this decision.';

  @override
  String get teamProjectTaskCritical => 'Critical';

  @override
  String get teamProjectTaskMajor => 'Major';

  @override
  String get teamProjectTaskMinor => 'Minor';

  @override
  String get teamProjectTaskMerge => 'Merge queue to dev';

  @override
  String get teamProjectTaskMergeRun => 'Check and merge to dev';

  @override
  String get teamProjectTaskPromoteConfirmTitle => 'Promote dev to main?';

  @override
  String get teamProjectTaskPromote => 'Promote dev to main';

  @override
  String get teamProjectTaskPromoteBody =>
      'This updates protected main to the dev commit you reviewed. The engine will check both commits again before changing main.';

  @override
  String get teamProjectTaskPromotion => 'Protected branch';

  @override
  String get teamProjectTaskDiff => 'View changes';

  @override
  String get teamProjectTaskPause => 'Pause task';

  @override
  String get teamProjectTaskResume => 'Resume task';

  @override
  String get teamProjectTaskStopConfirmTitle => 'Stop this task?';

  @override
  String get teamProjectTaskStop => 'Stop task';

  @override
  String get teamProjectTaskStopBody =>
      'Stop this task and keep its conversation and changes for review.';

  @override
  String get teamProjectTaskRestart => 'Start task again';

  @override
  String get teamProjectTaskAnswer => 'Send answer';

  @override
  String get teamProjectTaskAnswerLabel => 'Your answer';

  @override
  String get teamProjectTaskRunning => 'Working';

  @override
  String get teamProjectTaskWaiting => 'Waiting';

  @override
  String get teamProjectTaskDone => 'Done';

  @override
  String get teamProjectTaskFailed => 'Task stopped before finishing';

  @override
  String get teamProjectTaskStale => 'Last known state';

  @override
  String get teamProjectTaskCollapse => 'Collapse all';

  @override
  String get teamProjectTaskWork => 'Work completed';

  @override
  String get teamProjectTaskEmpty =>
      'The task is queued. Its replies and checks will appear here.';

  @override
  String get teamProjectTaskReceipt => 'Promotion receipt';

  @override
  String get teamProjectTaskVerify => 'Verify task';

  @override
  String get teamProjectTryDemo => 'Try AI Team demo';

  @override
  String get teamProjectLoadFailure =>
      'The demo could not be opened. Your saved project data has been kept.';

  @override
  String get teamProjectInboxOpen => 'Review project decision';

  @override
  String get teamProjectDemoDisclosure =>
      'Simulated projects. No agents run and no repositories change.';

  @override
  String get teamProjectOff => 'Leave demo';

  @override
  String get teamProjectEditorFixRoundsRange =>
      'Enter a fix-round limit from 0 to 3.';

  @override
  String get teamProjectEditorPositiveTokens =>
      'Enter a positive token limit or leave it empty.';

  @override
  String get teamProjectEditorReloadConfirmTitle => 'Refresh this project?';

  @override
  String get teamProjectEditorReload => 'Refresh latest project';

  @override
  String get teamProjectEditorDiscardDraft =>
      'This replaces your unsaved edits with the latest project. Your saved project is kept.';

  @override
  String get teamProjectEditorRoleRequired => 'Enter a name for this role.';

  @override
  String get teamProjectEditorDefaults => 'New project defaults';

  @override
  String get teamProjectEditorApplyPlan => 'Apply updated plan';

  @override
  String get teamProjectEditorContextFiles => 'Files to read first';

  @override
  String get teamProjectEditorContextFilesHelp =>
      'Optional. One path per line. The team reads these before it plans. The demo does not read or upload files.';

  @override
  String get teamProjectEditorScreenOff => 'Keep working with the screen off';

  @override
  String get teamProjectEditorScreenOffHelp =>
      'This preference is saved for the project. Background work remains subject to the server and system limits.';

  @override
  String get teamProjectEditorDraftApproval =>
      'Draft changes need your approval before they become the project spec.';

  @override
  String get teamProjectEditorChangeRequest =>
      'What should the planner change?';

  @override
  String get teamProjectEditorAskChange => 'Ask to change';

  @override
  String get teamProjectEditorChangeRequired =>
      'Add a goal and describe the change you want.';

  @override
  String get teamProjectTaskApprovedPlan => 'Approved plan';

  @override
  String get teamProjectTaskCriteria => 'Acceptance criteria';

  @override
  String get teamProjectTaskOpenFindings => 'Open findings';

  @override
  String get teamProjectTaskFindingsAddressed => 'Findings addressed';

  @override
  String get teamProjectMergeConfirmBody =>
      'Checked task branches will merge into dev, followed by combined checks. Main stays unchanged.';

  @override
  String get teamProjectEditorDraftClearFailed =>
      'Changes were saved, but the local draft could not be cleared. Close this sheet and review the project before trying again.';

  @override
  String get teamProjectRestartElsewhereConfirmTitle => 'Start over elsewhere?';

  @override
  String get teamProjectRestartElsewhere => 'Start over elsewhere';

  @override
  String get teamProjectRestartElsewhereBody =>
      'Start a new attempt on this server. The previous branch stays on its original server.';

  @override
  String get teamProjectWaitForServer => 'Wait for the original server';

  @override
  String get teamProjectBudgetNear =>
      'Approaching your budget. New work pauses at your chosen limit.';

  @override
  String get teamProjectDemoPlanFailure => 'Demo: unreadable plan';

  @override
  String get teamProjectTaskReviewFindings => 'Select open findings';

  @override
  String get teamProjectTaskResolveAgent => 'Resolve with agent';

  @override
  String get teamProjectTaskResolveManually => 'I’ll resolve';

  @override
  String get teamProjectTaskRecheckResolution => 'Re-check resolution';

  @override
  String get teamProjectTaskVerificationResults => 'Verification results';

  @override
  String get teamProjectTaskCriterionMet => 'Met';

  @override
  String get teamProjectTaskCriterionUnmet => 'Unmet';

  @override
  String get teamProjectTaskCriterionNotApplicable => 'Not applicable';

  @override
  String get teamProjectTaskDemoConflict => 'Demo: create a conflict';

  @override
  String get teamProjectTaskDemoCommit => 'Demo: add a manual commit';

  @override
  String get teamProjectEditorNoOptions =>
      'No options are available yet. Return to AI Team to add a server or role.';

  @override
  String get teamProjectEditorUnknownDate => 'Date unavailable';

  @override
  String get teamProjectEditorYou => 'You';

  @override
  String get teamProjectEditorApprovedBy => 'Approved by';

  @override
  String get teamProjectEditorPlanFailed =>
      'The planner did not return a usable plan. Keep the goal as one task, or ask for a new plan.';

  @override
  String get teamProjectEditorUseAsTask => 'Use as one task';

  @override
  String get teamProjectEditorAskAgain => 'Ask again';

  @override
  String get teamProjectDemoChip => 'Demo';

  @override
  String teamProjectHeadlineMilestone(int current, int total, int working) {
    return 'Milestone $current of $total · $working working';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return 'All $total milestones done';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    return 'Spec approved $age · $milestones milestones · $repos repos';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    return 'Draft spec · $milestones milestones · $repos repos';
  }

  @override
  String get teamProjectOpenSpec => 'Open spec';

  @override
  String teamProjectRequestWhere(String role, String server) {
    return '$role on $server';
  }

  @override
  String teamProjectRequestBlocks(String role, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks after it wait too',
      one: '1 task after it waits too',
    );
    return '$role waits; $_temp0';
  }

  @override
  String teamProjectMilestoneTasks(int done, int total) {
    return '$done of $total tasks';
  }

  @override
  String teamProjectMilestoneWaits(int number) {
    return 'Waits on $number';
  }

  @override
  String get teamProjectMilestoneNoTasks => 'No tasks yet';

  @override
  String teamProjectLanesTitle(int busy, int total) {
    return 'Lanes $busy/$total busy';
  }

  @override
  String get teamProjectLanesChange => 'Change';

  @override
  String teamProjectLaneRunning(String server, String elapsed) {
    return '$server · $elapsed';
  }

  @override
  String teamProjectLaneWaiting(String server) {
    return '$server · waiting for a free lane';
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
      other: '$count lanes',
      one: '1 lane',
    );
    return 'Parallel · $_temp0';
  }

  @override
  String get teamProjectLaneNoteSingle => 'Single lane';

  @override
  String teamProjectLaneNoteDemo(String note) {
    return '$note · figures are simulated';
  }

  @override
  String teamProjectElapsedSeconds(int count) {
    return '$count s';
  }

  @override
  String teamProjectElapsedMinutes(int count) {
    return '$count min';
  }

  @override
  String teamProjectElapsedHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String teamProjectCostToday(String amount) {
    return '$amount today';
  }

  @override
  String teamProjectCostTodayOf(String amount, String limit) {
    return '$amount of $limit today';
  }

  @override
  String teamProjectCostTotal(String amount) {
    return '$amount total';
  }

  @override
  String teamProjectCostTotalOf(String amount, String limit) {
    return '$amount of $limit total';
  }

  @override
  String get teamProjectCostNoLimit => 'No limit set';

  @override
  String get teamProjectCostNotReported => 'Not reported yet';

  @override
  String teamProjectBoardSummary(int tasks, int milestones) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tasks',
      one: '1 task',
    );
    String _temp1 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones milestones',
      one: '1 milestone',
    );
    return '$_temp0 across $_temp1';
  }

  @override
  String get teamProjectBoardEmpty => 'No tasks yet';

  @override
  String teamProjectTimelineSummary(int count, String age) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count events',
      one: '1 event',
    );
    return '$_temp0 · latest $age';
  }

  @override
  String get teamProjectTimelineEmpty => 'Nothing has happened yet';

  @override
  String teamProjectServersSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servers',
      one: '1 server',
    );
    return '$_temp0';
  }

  @override
  String teamProjectSettingsSummaryParallel(int count) {
    return 'Parallel · up to $count lanes';
  }

  @override
  String get teamProjectSettingsSummarySingle => 'Single lane';

  @override
  String get teamProjectMenu => 'Project menu';

  @override
  String get teamProjectTaskMenu => 'Task menu';

  @override
  String teamProjectDecisionBy(String who, String age) {
    return '$who · $age';
  }

  @override
  String get teamProjectYou => 'You';

  @override
  String teamProjectPlanFor(int number) {
    return 'Plan for milestone $number · waiting for you';
  }

  @override
  String get teamProjectPlanForProject => 'Plan · waiting for you';

  @override
  String teamProjectPlanSummary(int phases, int tasks, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      phases,
      locale: localeName,
      other: '$phases phases',
      one: '1 phase',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tasks',
      one: '1 task',
    );
    String _temp2 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return '$_temp0 · $_temp1 · $_temp2';
  }

  @override
  String teamProjectPlanPhase(int number, String title) {
    return 'Phase $number · $title';
  }

  @override
  String get teamProjectPlanReviewPoint => 'نقطة مراجعة · محفوفة بالمخاطر';

  @override
  String teamProjectPlanRepo(String name) {
    return '$name repo';
  }

  @override
  String teamProjectPlanAfter(int number) {
    return 'after $number';
  }

  @override
  String teamProjectPlanCriteria(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count criteria',
      one: '1 criterion',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPlanWho(String role, String server) {
    return '$role · $server';
  }

  @override
  String teamProjectPlanMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '… $count more tasks',
      one: '… 1 more task',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPlanEdit => 'Edit plan';

  @override
  String get teamProjectPlanAsk => 'Ask to change';

  @override
  String get teamProjectPlanNotYet => 'ليس الآن';

  @override
  String teamProjectPlanServerTitle(String task) {
    return 'تشغيل «$task» على';
  }

  @override
  String get teamProjectPlanServerFixed =>
      'كل مهمة تعمل على الجهاز الذي خُطط لها عليه. هذا الفريق لا يستطيع نقل المهام إلى جهاز آخر بعد.';

  @override
  String teamProjectMergeEffectDev(String repo, int tasks) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks مهام مفحوصة',
      one: 'مهمة مفحوصة واحدة',
    );
    return '$_temp0 من $repo ستُدمج في dev.';
  }

  @override
  String get teamProjectMergeEffectMain => 'لن يتغير main.';

  @override
  String teamProjectReceiptMerged(String repo) {
    return 'دُمج في dev · $repo';
  }

  @override
  String teamProjectReceiptPromoted(String repo) {
    return 'رُقّي إلى main · $repo';
  }

  @override
  String teamProjectTimelineRepeated(String text, int count) {
    return '$text · $count مرات';
  }

  @override
  String get teamProjectPromoteTitle => 'Promote dev → main';

  @override
  String teamProjectPromoteStatus(String repo) {
    return '$repo repo · main is protected. Only you can promote.';
  }

  @override
  String teamProjectPromoteMilestone(int number, String title) {
    return 'Milestone $number · $title';
  }

  @override
  String teamProjectPromoteMerged(int done, int total) {
    return '$done of $total tasks merged';
  }

  @override
  String teamProjectPromoteDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count days',
      one: '1 day',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteChecks => 'Checks after merge';

  @override
  String get teamProjectPromoteChecksPassed =>
      'Every check passed after the last merge';

  @override
  String get teamProjectPromoteReview => 'Review';

  @override
  String teamProjectPromoteAccepted(int number) {
    return 'You accepted milestone $number';
  }

  @override
  String get teamProjectPromoteNoReview => 'No review was needed for this work';

  @override
  String teamProjectPromoteChanges(int commits) {
    String _temp0 = intl.Intl.pluralLogic(
      commits,
      locale: localeName,
      other: '$commits commits',
      one: '1 commit',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPromoteFiles(int files) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteSeeChanges => 'See changes';

  @override
  String get teamProjectPromoteNotYet => 'Not yet';

  @override
  String teamProjectFindingsTitle(String role, String summary) {
    return 'Checked by $role · $summary';
  }

  @override
  String get teamProjectEditorGoalLabel => 'What should the team achieve?';

  @override
  String get teamProjectEditorWhereRuns => 'Where it runs';

  @override
  String get teamProjectEditorMoreOptions => 'Optional details';

  @override
  String get teamProjectEditorNameHelp =>
      'Optional. Leave empty to use the start of the goal.';

  @override
  String get teamProjectEditorBudgetHelp =>
      'The team pauses when the day or the whole project reaches its limit.';

  @override
  String teamProjectFindingsCritical(int count) {
    return '$count critical';
  }

  @override
  String teamProjectFindingsMajor(int count) {
    return '$count major';
  }

  @override
  String teamProjectFindingsMinor(int count) {
    return '$count minor';
  }

  @override
  String get phoneTeamSetupTitle => 'تشغيل فريق الذكاء الاصطناعي';

  @override
  String get phoneTeamStepReply => 'انتظر انتهاء ردّك الحالي';

  @override
  String get phoneTeamReplyWaiting => 'ينهي ردّك الحالي…';

  @override
  String get phoneTeamStepStop => 'إيقاف OpenCode وإغلاق الطرفيات';

  @override
  String get phoneTeamStepCheck => 'التحقق من أن الهاتف آمن للفريق';

  @override
  String get phoneTeamStepServer => 'تشغيل OpenCode من جديد بحماية';

  @override
  String get phoneTeamNotNeeded => 'غير مطلوب';

  @override
  String get phoneTeamStopTitle => 'إيقاف OpenCode لحظة؟';

  @override
  String get phoneTeamStopBody =>
      'يوقف خادم OpenCode على هذا الهاتف لنحو دقيقة ثم يشغّله من جديد بحماية. تُغلق الطرفيات المفتوحة.';

  @override
  String get phoneTeamStopConfirm => 'أوقف وتابع';

  @override
  String get phoneTeamStopCancel => 'ليس الآن';

  @override
  String get phoneTeamStopWaiting => 'بانتظار ردّك';

  @override
  String get phoneTeamDoneTitle => 'فريق الذكاء الاصطناعي جاهز';

  @override
  String get phoneTeamDoneBody => 'أعطه هدفًا فيخطط الفريق وينفذ ويراجع العمل.';

  @override
  String get phoneTeamFailUnsafeTitle => 'يبقى الفريق متوقفًا';

  @override
  String get phoneTeamFailUnsafeBody =>
      'لا يستطيع هذا الهاتف فصل نسخة الفريق من شيفرتك عن الوكلاء، لذلك يبقى الفريق متوقفًا.';

  @override
  String get phoneTeamFailEngineTitle => 'لم يبدأ فريق الذكاء الاصطناعي';

  @override
  String get phoneTeamFailEngineBody => 'لم يبدأ محرك الفريق على هذا الهاتف.';

  @override
  String get phoneTeamFailStopTitle => 'لم يتوقف الخادم';

  @override
  String get phoneTeamFailStopBody =>
      'لم يُغلق OpenCode أو إحدى الطرفيات، فلا يمكن إجراء الفحص بأمان بعد.';

  @override
  String get phoneTeamFailServerTitle => 'لم يعد الخادم';

  @override
  String get phoneTeamFailServerBody =>
      'اجتاز هذا الهاتف الفحص لكن OpenCode لم يبدأ من جديد. ابدأ من جديد للإنهاء.';

  @override
  String get phoneTeamFailNotReadyTitle => 'ليس جاهزًا بعد';

  @override
  String get phoneTeamFailNotReadyBody =>
      'عاد OpenCode لكن الفريق لا يستطيع بدء العمل بعد.';

  @override
  String get phoneTeamFailDeclinedTitle => 'لم يتغير شيء';

  @override
  String get phoneTeamFailDeclinedBody =>
      'يبقى OpenCode كما هو، لذلك يبقى الفريق متوقفًا. ابدأ من جديد حين تكون مستعدًا لإعادة تشغيله.';

  @override
  String get phoneTeamFailNoServerTitle => 'أضف خادمًا أولًا';

  @override
  String get phoneTeamFailNoServerBody =>
      'يعمل الفريق مع OpenCode على هذا الهاتف ولا يوجد واحد بعد.';

  @override
  String get phoneTeamReasonNotPackaged =>
      'هذه النسخة من التطبيق لا تتضمن أدوات أمان الفريق.';

  @override
  String get phoneTeamStateBackOn => 'عاد OpenCode للعمل.';

  @override
  String get phoneTeamStateStillOff =>
      'ما زال OpenCode متوقفًا. ابدأ مجددًا أو أعد تشغيله من هذا الهاتف.';

  @override
  String get phoneTeamStateNotStopped => 'لم يتم إيقاف OpenCode.';

  @override
  String get phoneTeamStateTerminalsClosed => 'أُغلقت الطرفيات المفتوحة.';

  @override
  String get phoneTeamWhyUnsafe =>
      'لم ينجح الفحص الذي يفصل نسخة الفريق من شيفرتك عن الوكلاء.';

  @override
  String get phoneTeamWhyEngine => 'توقف محرك الفريق أو لم يستجب أثناء بدئه.';

  @override
  String get phoneTeamWhyStop =>
      'لم يُغلق OpenCode أو إحدى الطرفيات عند الطلب.';

  @override
  String get phoneTeamWhyServer => 'لم يستجب OpenCode بعد تشغيله مجددًا.';

  @override
  String get phoneTeamWhyNotReady =>
      'استجاب محرك الفريق لكنه قال إنه لا يستطيع تشغيل العمل بعد.';

  @override
  String get phoneTeamDetails => 'التفاصيل';

  @override
  String get phoneTeamOffTitle => 'فريق الذكاء الاصطناعي متوقف';

  @override
  String get phoneTeamOffBody =>
      'يحتاج إلى فحص أمان سريع، مثلًا بعد تحديث التطبيق.';

  @override
  String get phoneTeamBlocked =>
      'لا يستطيع الفريق بدء العمل قبل فحص هذا الهاتف.';

  @override
  String get phoneTeamStripChecking =>
      'فحص فريق الذكاء الاصطناعي على هذا الهاتف';

  @override
  String get phoneTeamStripWaiting => 'يحتاج الفريق إلى إعادة تشغيل OpenCode';

  @override
  String get phoneTeamStripReview => 'افتح الفحص لتختار الوقت';

  @override
  String get phoneTeamStripFailed => 'تعذر تشغيل فريق الذكاء الاصطناعي';

  @override
  String phoneTeamStripStep(int step, int total) {
    return 'الخطوة $step من $total';
  }

  @override
  String get phoneTeamBlockedTitle => 'افحص الهاتف أولًا؟';

  @override
  String get teamMigrationTitle => 'تغيّر فريق الذكاء الاصطناعي';

  @override
  String get teamMigrationBody =>
      'الفريق الجديد يخطط لمشاريع كاملة وينفذها على هذا الهاتف. فريقك القديم يواصل المهام السريعة فقط ولا يخطط للمشاريع، والتبديل يسألك قبل إيقاف أي شيء.';

  @override
  String get teamMigrationSwitch =>
      'التبديل إلى فريق الذكاء الاصطناعي الجديد على هذا الهاتف';

  @override
  String get teamMigrationKeep => 'إبقاء الفريق القديم حاليًا';

  @override
  String get teamMigrationMenu => 'ما الجديد في فريق الذكاء الاصطناعي';

  @override
  String get teamProjectPages => 'صفحات المشروع';

  @override
  String get teamProjectMergeReady => 'جاهز للدمج';

  @override
  String get teamProjectMergeChecking => 'بانتظار فحوصاته';

  @override
  String get teamRefusalUnsupportedCommand =>
      'This version of the team can\'t do that yet.';

  @override
  String get teamRefusalUnsupportedCommandNext =>
      'Update the app, then try again.';

  @override
  String get teamRefusalBoundaryUnverified =>
      'The team can\'t work until this phone\'s protection has been checked.';

  @override
  String get teamRefusalBoundaryUnverifiedNext =>
      'Open AI Team and turn it on again.';

  @override
  String get teamRefusalProtocolUnverified =>
      'The team is waiting for OpenCode to restart.';

  @override
  String get teamRefusalProtocolUnverifiedNext => 'Try again in a minute.';

  @override
  String get teamRefusalEngineUnavailable =>
      'The team isn\'t answering right now.';

  @override
  String get teamRefusalEngineUnavailableNext => 'Try again in a moment.';

  @override
  String get teamRefusalTransportUncertain =>
      'The team may not have received that.';

  @override
  String get teamRefusalTransportUncertainNext =>
      'Check the project list before you try again.';

  @override
  String get teamRefusalBusy => 'Another change is still being saved.';

  @override
  String get teamRefusalBusyNext => 'Try again in a moment.';

  @override
  String get teamRefusalSaveFailed =>
      'The change couldn\'t be saved on this phone.';

  @override
  String get teamRefusalSaveFailedNext =>
      'Your edits are still here. Try again.';

  @override
  String get teamRefusalReadOnly => 'The team is read-only right now.';

  @override
  String get teamRefusalReadOnlyNext =>
      'Turn on AI Team on this phone to make changes.';

  @override
  String get teamRefusalClosed => 'The team has been closed.';

  @override
  String get teamRefusalClosedNext => 'Open AI Team again to continue.';

  @override
  String get teamRefusalCommandRefused => 'The team turned this request down.';

  @override
  String get teamRefusalCommandRefusedNext =>
      'Check the goal and the repository folder, then try again.';

  @override
  String get teamRefusalImportFailed =>
      'The team couldn\'t read the repository folder.';

  @override
  String get teamRefusalImportFailedNext =>
      'Check the folder name, then try again.';

  @override
  String get teamRefusalPayloadInvalid =>
      'The team answered in a way this app doesn\'t understand.';

  @override
  String get teamRefusalPayloadInvalidNext => 'Update the app, then try again.';

  @override
  String get teamRefusalSchemaUnsupported =>
      'The team and this app are on different versions.';

  @override
  String get teamRefusalSchemaUnsupportedNext =>
      'Update the app, then try again.';

  @override
  String get teamRefusalEngineClosed => 'The team is shutting down.';

  @override
  String get teamRefusalEngineClosedNext =>
      'Turn on AI Team again to continue.';

  @override
  String get teamRefusalSessionFailed =>
      'The planner stopped before it answered.';

  @override
  String get teamRefusalSessionFailedNext =>
      'Check the model in Team settings › Model, then approve the spec again.';

  @override
  String get teamRefusalModelNotConfigured => 'The team needs a model.';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      'Pick one in Team settings › Model.';

  @override
  String get teamRefusalModelUnavailable =>
      'The chosen model isn\'t available.';

  @override
  String get teamRefusalModelUnavailableNext =>
      'Pick another model in Team settings › Model.';

  @override
  String get teamRefusalAuthFailed =>
      'The model\'s provider didn\'t accept the sign-in.';

  @override
  String get teamRefusalAuthFailedNext =>
      'Check the provider\'s key, then approve the spec again.';

  @override
  String get teamRefusalCloneFailed =>
      'The team couldn\'t copy the project to work on it.';

  @override
  String get teamRefusalCloneFailedNext =>
      'Check the project\'s repository, then approve the spec again.';

  @override
  String get teamRefusalSessionUncertain =>
      'The team isn\'t sure how far its last run got.';

  @override
  String get teamRefusalSessionUncertainNext =>
      'Resume if you can, or approve the spec again.';

  @override
  String get teamRefusalPlanInvalid =>
      'The plan that came back couldn\'t be used.';

  @override
  String get teamRefusalPlanInvalidNext =>
      'Approve the spec again and the team will plan again.';

  @override
  String get teamRefusalNeedsAnswer => 'The planner has a question for you.';

  @override
  String get teamRefusalNeedsAnswerNext => 'Open the spec and answer it.';

  @override
  String get teamRefusalRecoveryReview =>
      'The work stopped part way and needs a look.';

  @override
  String get teamRefusalRecoveryReviewNext =>
      'Check the project, then approve the spec again.';

  @override
  String get teamRefusalAppStopped =>
      'The app closed before the team finished.';

  @override
  String get teamRefusalAppStoppedNext => 'Resume to check where it got to.';

  @override
  String get teamRefusalChatBusy =>
      'The team is waiting for your conversation to finish replying.';

  @override
  String get teamRefusalChatBusyNext => 'It carries on by itself afterwards.';

  @override
  String get teamRefusalBudgetReached =>
      'The project reached its spending limit.';

  @override
  String get teamRefusalBudgetReachedNext =>
      'Raise the limit in the project\'s settings to go on.';

  @override
  String get teamRefusalModelNotConfiguredAction => 'Pick a model';

  @override
  String get teamProjectPlanFailedTitle => 'The plan wasn\'t made';

  @override
  String get teamProjectApproveAgain => 'Approve the spec again';

  @override
  String get teamProjectApproveAgainNote => 'Approve the spec again to retry.';

  @override
  String get teamProjectTaskWorkLive => 'Work so far';

  @override
  String get teamProjectTaskWorkLog => 'Work log';

  @override
  String get teamRefusalDidPlan => 'start planning';

  @override
  String get teamRefusalDidQuick => 'start that task';

  @override
  String get teamRefusalDidApprove => 'start the work';

  @override
  String get teamRefusalDidSpec => 'approve the spec';

  @override
  String get teamRefusalDidPromote => 'promote the work';

  @override
  String get teamRefusalDidStop => 'stop the project';

  @override
  String get teamRefusalDidPause => 'pause the project';

  @override
  String get teamRefusalDidResume => 'resume the project';

  @override
  String get teamRefusalDidSave => 'save your changes';

  @override
  String teamRefusalUnknown(String action) {
    return 'The team couldn\'t $action.';
  }

  @override
  String get teamRefusalUnknownNext =>
      'Try again. If it keeps happening, open Details for the code.';

  @override
  String get teamRefusalCode => 'Code';

  @override
  String get phoneTeamProtectedProot =>
      'Protected by this phone\'s Linux sandbox';

  @override
  String get phoneTeamProtectedLandlock =>
      'Protected by Android\'s file protection';

  @override
  String get teamRefusalRepositoryEmpty =>
      'This repository has no commits yet.';

  @override
  String get teamRefusalRepositoryEmptyNext =>
      'Make a first commit in it, then start planning again.';

  @override
  String get teamRefusalRepositoryLink =>
      'The team couldn\'t safely copy this repository.';

  @override
  String get teamRefusalRepositoryLinkNext =>
      'Try again. If it keeps happening, report the problem.';

  @override
  String get teamRefusalRepositoryDamaged =>
      'The repository copy didn\'t match the original.';

  @override
  String get teamRefusalRepositoryDamagedNext =>
      'Try again. If it keeps happening, report the problem.';

  @override
  String get teamRefusalPlanTaskName => 'A task in the plan has no name.';

  @override
  String get teamRefusalPlanTaskNameNext =>
      'Give every task a name, then approve again.';

  @override
  String get teamRefusalPlanPhase => 'A phase in the plan isn\'t complete.';

  @override
  String get teamRefusalPlanPhaseNext =>
      'Check each phase has tasks and criteria, then approve again.';

  @override
  String get teamServerPhoneFailed =>
      'AI Team on this phone isn\'t answering, so its work can\'t be reached.';

  @override
  String get teamServerPhoneNotReady =>
      'AI Team on this phone isn\'t ready: its safety check hasn\'t passed.';

  @override
  String get teamServerPhoneNoAnswer =>
      'OpenCode on this phone didn\'t answer the last check.';

  @override
  String get phoneTeamOffReview => 'راجع واختر الوقت';

  @override
  String teamProjectInterruptedRow(String name) {
    return '$name: توقف العمل، اضغط للاستئناف';
  }

  @override
  String get teamProjectResumeUnavailable =>
      'هذا الإصدار من فريق الذكاء الاصطناعي لا يستطيع استئناف العمل المتوقف بعد. يمكنك إيقاف المشروع وبدءه من جديد.';

  @override
  String get teamProjectInterrupted => 'توقف العمل، جاهز للاستئناف';

  @override
  String get teamProjectEditorContextFilesHelpReal =>
      'اختياري. مسار واحد في كل سطر. يقرأ الفريق هذه الملفات قبل أن يخطط.';

  @override
  String get addServerCleartextWarning =>
      'هذا العنوان يستخدم HTTP عادي. على هذه الشبكة قد يتمكن آخرون من قراءة كلمة المرور ومحادثاتك. استخدم Tailscale أو HTTPS إن أمكن.';

  @override
  String get addServerCleartextConfirm => 'استخدمه على أي حال';

  @override
  String get addServerCleartextConfirmed =>
      'تم تفعيل HTTP العادي لهذا العنوان. يمكن لأي شخص على هذه الشبكة قراءة ما ترسله.';

  @override
  String get storageAccessTitle => 'السماح بالوصول إلى الملفات؟';

  @override
  String get storageAccessBody =>
      'هذا المجلد في التخزين المشترك بهاتفك. يخفي أندرويد ملفاته عن التطبيقات ما لم تسمح بالوصول إلى كل الملفات.';

  @override
  String get storageAccessWhyScope =>
      'يقرأ التطبيق الملفات ويعدلها فقط في المجلدات التي تفتحها كمشاريع.';

  @override
  String get storageAccessWhyAgent =>
      'يحتاجه الوكيل ليعمل في مجلدك مباشرة. بدونه سترى العناصر المخفية فقط مثل .git.';

  @override
  String get storageAccessWhyOff =>
      'يمكنك إيقافه في أي وقت من إعدادات أندرويد، ضمن الوصول إلى كل الملفات.';

  @override
  String get storageAccessAllow => 'السماح بالوصول إلى الملفات';

  @override
  String get storageAccessNotNow => 'ليس الآن';

  @override
  String get storageAccessUseAppSpace => 'استخدام مساحة مشاريع التطبيق';

  @override
  String get storageAccessRefusedTitle => 'لم يُفتح المجلد';

  @override
  String get storageAccessRefusedBody =>
      'بدون الوصول إلى الملفات لا يمكن عرض ملفات هذا المجلد، لذلك لم يُفتح. اسمح بالوصول أو اختر مجلدًا في مساحة مشاريع التطبيق.';

  @override
  String get storageTermuxTitle => 'السماح بتخزين Termux؟';

  @override
  String get storageTermuxBody =>
      'خادمك يعمل في Termux، ولا يستطيع Termux قراءة التخزين المشترك بهاتفك بعد. بدون ذلك يعرض هذا المجلد العناصر المخفية فقط مثل .git.';

  @override
  String get storageTermuxAllow => 'فتح Termux';

  @override
  String get storageTermuxStillTitle => 'اسمح بالتخزين في Termux';

  @override
  String get storageTermuxStillBody =>
      'في Termux، اسمح بالتخزين عندما يسأل أندرويد (الأمر هو termux-setup-storage)، ثم افتح المجلد مرة أخرى.';

  @override
  String get filesAccessNeededTitle => 'الملفات مخفية';

  @override
  String get filesAccessNeededBody =>
      'هذا المجلد في التخزين المشترك بهاتفك ولا يستطيع التطبيق قراءة معظم ملفاته بعد. تظهر العناصر المخفية فقط.';

  @override
  String get filesAccessNeededTermuxBody =>
      'هذا المجلد في التخزين المشترك بهاتفك ولا يستطيع Termux قراءة معظم ملفاته بعد. تظهر العناصر المخفية فقط.';

  @override
  String get storageRestartTitle => 'إعادة التشغيل لفتح المجلد؟';

  @override
  String get storageRestartBody =>
      'يجب إعادة تشغيل OpenCode على هذا الهاتف قبل أن يرى هذا المجلد. يستغرق ذلك نحو 30 ثانية.';

  @override
  String get storageRestartPause =>
      'الردود الجارية وعمل فريق الذكاء الاصطناعي يتوقفان مؤقتًا ثم يتابعان.';

  @override
  String get storageRestartBusy =>
      'هناك رد أو مهمة لفريق الذكاء الاصطناعي قيد التنفيذ الآن. ستتوقف مؤقتًا أثناء إعادة التشغيل.';

  @override
  String get storageRestartConfirm => 'أعد التشغيل وافتح';

  @override
  String get storageRestartFailedTitle => 'لم تكتمل إعادة التشغيل';

  @override
  String get storageRestartFailedBody =>
      'لم يُفتح المجلد. حاول مرة أخرى، أو شغّل OpenCode من هذا الهاتف.';

  @override
  String get kitQueuedChecking => 'جارٍ التحقق من وصول رسالتك…';

  @override
  String get kitQueuedUncertain => 'لم نتمكن من التأكد من وصول رسالتك.';

  @override
  String kitQueuedLastChecked(String time) {
    return 'آخر تحقق $time.';
  }

  @override
  String get kitQueuedStorageFull =>
      'لا يستطيع هذا الهاتف تسجيل الإرسال بأمان الآن، لذا لم تُرسل رسالتك. وهي محفوظة هنا.';

  @override
  String get queuedReceiptConfirmed => 'وصلت الرسالة.';

  @override
  String get queuedReceiptStillUncertain =>
      'ما زلنا لا نستطيع التأكد من وصولها. لم يُرسل شيء مرة أخرى.';

  @override
  String get queuedReceiptStorageProblem =>
      'لا يستطيع هذا الهاتف تسجيل الإرسال أو التحقق منه بأمان الآن. حرّر بعض مساحة التخزين وحاول مرة أخرى.';

  @override
  String get queuedReceiptDetailSentAt => 'وقت الإرسال';

  @override
  String get queuedReceiptDetailCommand => 'معرّف الأمر';

  @override
  String get queuedReceiptDetailReceipt => 'معرّف الإيصال';

  @override
  String get queuedReceiptDetailNote =>
      'انقطع الاتصال قبل أن نتمكن من تأكيد الاستلام.';

  @override
  String get kitToolRetry => 'أعد المحاولة';

  @override
  String get folderBrowserPlaceLabel => 'المكان';

  @override
  String get folderBrowserPlaceProjects => 'مساحة المشاريع';

  @override
  String get folderBrowserPlacePhone => 'هذا الهاتف';

  @override
  String get folderBrowserInternalStorage => 'التخزين الداخلي';

  @override
  String get folderBrowserOpenedBefore => 'فُتحت سابقًا';

  @override
  String get folderBrowserShowHidden => 'إظهار المجلدات المخفية';

  @override
  String get folderBrowserHintEmpty => 'فارغ';

  @override
  String folderBrowserHintMore(String names, int count) {
    return '$names، و$count أخرى';
  }

  @override
  String get folderBrowserEmptyPhoneBody =>
      'افتحه، أو أنشئ فيه مجلدًا جديدًا، أو اصعد مستوى واحدًا.';

  @override
  String get remoteFolderRecent => 'مجلدات حديثة';

  @override
  String get remoteFolderProjects => 'مشاريع هذا الخادم';

  @override
  String folderBrowserNewProjectIn(String name) {
    return 'في $name';
  }

  @override
  String folderBrowserNewProjectCreates(String path) {
    return 'ينشئ $path';
  }

  @override
  String get folderBrowserCreateAndOpen => 'أنشئ وافتح';

  @override
  String get folderBrowserFoldersLabel => 'المجلدات';

  @override
  String get folderBrowserPhoneRefused =>
      'تخزين الهاتف متوقف. اسمح بالوصول لتصفحه هنا.';
}
